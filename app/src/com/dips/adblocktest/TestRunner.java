package com.dips.adblocktest;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import com.dips.adblocktest.probes.AdMobProbe;
import com.dips.adblocktest.probes.ChartboostProbe;
import com.dips.adblocktest.probes.InMobiProbe;
import com.dips.adblocktest.probes.IronSourceProbe;
import com.dips.adblocktest.probes.LiftoffProbe;
import com.dips.adblocktest.probes.StartIoProbe;
import com.dips.adblocktest.probes.UnityProbe;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs the enabled networks sequentially: pre-flight connectivity check,
 * then init + per-format probes for each network. All listener callbacks
 * arrive on the main thread.
 */
public class TestRunner {
    public interface Listener {
        void onPreflight(boolean internetAvailable);
        void onNetworkStart(NetworkProbe network);
        void onFormatStart(NetworkProbe network, AdFormat format);
        void onFormatResult(ProbeResult result);
        void onNetworkDone(NetworkProbe network);
        void onAllDone(List<ProbeResult> all);
    }

    private static final List<NetworkProbe> NETWORKS = new ArrayList<>();
    static {
        NETWORKS.add(new AdMobProbe());
        NETWORKS.add(new UnityProbe());
        NETWORKS.add(new IronSourceProbe());
        NETWORKS.add(new InMobiProbe());
        NETWORKS.add(new ChartboostProbe());
        NETWORKS.add(new StartIoProbe());
        NETWORKS.add(new LiftoffProbe());
    }

    public static List<NetworkProbe> networks() { return NETWORKS; }

    public static NetworkProbe byId(String id) {
        for (NetworkProbe n : NETWORKS) if (n.getId().equals(id)) return n;
        return null;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;
    private volatile Runnable pendingInitTimeout;

    public void cancel() {
        cancelled = true;
        Runnable t = pendingInitTimeout;
        if (t != null) main.post(t);
    }

    private static final int BANNER_HOST_ID = View.generateViewId();

    /**
     * Hidden 320x50 host for banner views. Banner SDKs require proper sizing
     * and window attachment to measure and load banners correctly.
     */
    public static FrameLayout bannerHost(Activity activity) {
        FrameLayout root = activity.findViewById(BANNER_HOST_ID);
        if (root == null) {
            root = new FrameLayout(activity);
            root.setId(BANNER_HOST_ID);
            root.setVisibility(View.VISIBLE);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    Ui.dp(activity, 320), Ui.dp(activity, 50));
            lp.topMargin = -10000;
            activity.addContentView(root, lp);
        } else {
            root.removeAllViews();
        }
        return root;
    }

    public void run(final Activity activity, final Map<String, Boolean> enabled,
                   final Listener listener) {
        cancelled = false;
        final List<ProbeResult> all = new ArrayList<>();
        new Thread(() -> {
            boolean internet = checkInternet();
            BaseProbe.internetAvailable = internet;
            main.post(() -> listener.onPreflight(internet));
            runNext(activity, new ArrayList<>(NETWORKS), enabled, listener, all, 0);
        }).start();
    }

    private boolean checkInternet() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(TestConfig.CONNECTIVITY_URL).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "AdBlockTest/1.0 connectivity-check");
            int code = c.getResponseCode();
            return code == 204 || code == 200;
        } catch (Throwable t) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static ProbeStatus classifyInitFailure(String code, String message) {
        String m = ((code == null ? "" : code + " ") + (message == null ? "" : message))
                .toLowerCase(Locale.US);
        boolean networkish = m.contains("network") || m.contains("connection")
                || m.contains("connect") || m.contains("timeout") || m.contains("timed out")
                || m.contains("unreachable") || m.contains("unknownhost")
                || m.contains("resolve host") || m.contains("dns") || m.contains("socket")
                || m.contains("internet") || m.contains("offline") || m.contains("ssl")
                || m.contains("econn") || m.contains("reset by peer") || m.contains("blocked");
        if ("init_timeout".equals(code) && BaseProbe.internetAvailable) return ProbeStatus.BLOCKED;
        if (networkish && BaseProbe.internetAvailable) return ProbeStatus.BLOCKED;
        return ProbeStatus.SDK_INIT_FAILED;
    }

    private void runNext(final Activity activity, final List<NetworkProbe> networks,
                        final Map<String, Boolean> enabled, final Listener listener,
                        final List<ProbeResult> all, final int index) {
        if (cancelled || index >= networks.size()) {
            main.post(() -> listener.onAllDone(all));
            return;
        }
        final NetworkProbe network = networks.get(index);
        ResultStore store = new ResultStore(activity);
        if (!store.isEnabled(network.getId())) {
            for (AdFormat f : network.getFormats()) {
                ProbeResult r = new ProbeResult(network.getId(), f, ProbeStatus.SKIPPED,
                        null, "Network disabled by user.", 0, network.getSdkVersion());
                all.add(r);
                main.post(() -> listener.onFormatResult(r));
            }
            runNext(activity, networks, enabled, listener, all, index + 1);
            return;
        }
        main.post(() -> listener.onNetworkStart(network));
        final boolean[] initDone = {false};
        final Runnable initTimeout = () -> {
            if (initDone[0]) return;
            initDone[0] = true;
            pendingInitTimeout = null;
            if (cancelled) {
                main.post(() -> listener.onAllDone(all));
                return;
            }
            for (AdFormat f : network.getFormats()) {
                ProbeResult r = new ProbeResult(network.getId(), f,
                        classifyInitFailure("init_timeout",
                            "SDK initialization did not complete within 30s."),
                        "init_timeout",
                        "SDK initialization did not complete within 30s.",
                        30000, network.getSdkVersion());
                all.add(r);
                main.post(() -> listener.onFormatResult(r));
            }
            main.post(() -> listener.onNetworkDone(network));
            runNext(activity, networks, enabled, listener, all, index + 1);
        };
        main.postDelayed(initTimeout, 30000);
        pendingInitTimeout = initTimeout;
        final long initStart = System.currentTimeMillis();
        try {
            network.initialize(activity, (ok, error) -> {
                if (initDone[0]) return;
                initDone[0] = true;
                long initLatency = System.currentTimeMillis() - initStart;
                DebugLog.logInitResult(network.getId(), ok, error, initLatency);
                main.removeCallbacks(initTimeout);
                pendingInitTimeout = null;
                if (cancelled) { main.post(() -> listener.onAllDone(all)); return; }
                if (!ok) {
                    String errMsg = error == null ? "SDK initialization failed." : error;
                    ProbeStatus st = classifyInitFailure("init", errMsg);
                    for (AdFormat f : network.getFormats()) {
                        ProbeResult r = new ProbeResult(network.getId(), f,
                                st, "init", errMsg,
                                0, network.getSdkVersion());
                        all.add(r);
                        main.post(() -> listener.onFormatResult(r));
                    }
                    main.post(() -> listener.onNetworkDone(network));
                    runNext(activity, networks, enabled, listener, all, index + 1);
                    return;
                }
                probeFormats(activity, network, enabled, listener, all, index, 0);
            });
        } catch (Throwable t) {
            initTimeout.run();
        }
    }

    private void probeFormats(final Activity activity, final NetworkProbe network,
                             final Map<String, Boolean> enabled, final Listener listener,
                             final List<ProbeResult> all, final int netIndex, final int fmtIndex) {
        List<AdFormat> formats = network.getFormats();
        if (cancelled || fmtIndex >= formats.size()) {
            main.post(() -> listener.onNetworkDone(network));
            runNext(activity, NETWORKS, enabled, listener, all, netIndex + 1);
            return;
        }
        final AdFormat format = formats.get(fmtIndex);
        ResultStore store = new ResultStore(activity);
        if (!store.isFormatEnabled(network.getId(), format)) {
            ProbeResult r = new ProbeResult(network.getId(), format, ProbeStatus.SKIPPED,
                    null, "Format disabled by user.", 0, network.getSdkVersion());
            all.add(r);
            main.post(() -> listener.onFormatResult(r));
            probeFormats(activity, network, enabled, listener, all, netIndex, fmtIndex + 1);
            return;
        }
        main.post(() -> listener.onFormatStart(network, format));
        try {
            network.probeFormat(activity, format, result -> {
                all.add(result);
                main.post(() -> listener.onFormatResult(result));
                probeFormats(activity, network, enabled, listener, all, netIndex, fmtIndex + 1);
            });
        } catch (Throwable t) {
            ProbeResult r = new ProbeResult(network.getId(), format, ProbeStatus.ERROR,
                    "exception", t.toString(), 0, network.getSdkVersion());
            all.add(r);
            main.post(() -> listener.onFormatResult(r));
            probeFormats(activity, network, enabled, listener, all, netIndex, fmtIndex + 1);
        }
    }

    /** Per-network verdict from its format results. */
    public static String verdictFor(String networkId, Map<String, List<ProbeResult>> byNetwork) {
        List<ProbeResult> rs = byNetwork.get(networkId);
        if (rs == null || rs.isEmpty()) return "Not tested";
        int loaded = 0, blocked = 0, nofill = 0, skipped = 0, failed = 0;
        for (ProbeResult r : rs) {
            switch (r.status) {
                case TEST_AD_LOADED: loaded++; break;
                case BLOCKED: blocked++; break;
                case TIMEOUT: blocked++; break;
                case NO_FILL: nofill++; break;
                case SKIPPED: skipped++; break;
                default: failed++; break;
            }
        }
        if (skipped == rs.size()) return "Disabled";
        if (blocked > 0 && loaded == 0) return "Blocked";
        if (blocked > 0 && loaded > 0) return "Partially blocked";
        if (loaded > 0 && blocked == 0) return "Ads shown";
        if (nofill > 0 && loaded + blocked + failed == 0) return "No fill";
        if (failed > 0 && loaded == 0) return "Errors";
        return "Mixed";
    }

    /** Groups results by network id, preserving network order. */
    public static Map<String, List<ProbeResult>> groupByNetwork(List<ProbeResult> all) {
        Map<String, List<ProbeResult>> map = new LinkedHashMap<>();
        for (NetworkProbe n : TestRunner.networks()) map.put(n.getId(), new ArrayList<>());
        for (ProbeResult r : all) {
            List<ProbeResult> l = map.get(r.networkId);
            if (l == null) { l = new ArrayList<>(); map.put(r.networkId, l); }
            l.add(r);
        }
        return map;
    }
}
