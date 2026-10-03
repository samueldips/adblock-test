package com.dips.adblocktest;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import java.util.Locale;

/**
 * Shared plumbing for network probes: main-thread callbacks, a per-probe
 * watchdog, and failure classification.
 */
public abstract class BaseProbe implements NetworkProbe {
    protected final Handler main = new Handler(Looper.getMainLooper());
    /** Set by TestRunner before a run: false when the device has no internet. */
    public static volatile boolean internetAvailable = true;

    protected long startMs;
    private long activeToken = 0;
    private ProbeCallback activeCallback;
    private Runnable watchdogRunnable;

    protected void beginProbe() {
        startMs = System.currentTimeMillis();
    }

    protected long elapsed() {
        return System.currentTimeMillis() - startMs;
    }

    /** Arms a probe with a watchdog. Returns a token identifying this probe run. */
    protected synchronized long armProbe(Activity activity, AdFormat format, ProbeCallback callback) {
        cancelWatchdog();
        activeToken++;
        final long token = activeToken;
        activeCallback = callback;
        beginProbe();
        watchdogRunnable = () -> {
            synchronized (BaseProbe.this) {
                if (activeToken == token) {
                    ProbeStatus timeoutStatus = internetAvailable ? ProbeStatus.BLOCKED : ProbeStatus.TIMEOUT;
                    String timeoutMsg = internetAvailable
                            ? "Ad blocker intercepted connection (30s watchdog timeout)."
                            : "Probe exceeded " + TestConfig.PROBE_TIMEOUT_MS + " ms without an SDK callback.";
                    postResultWithToken(token, activity, format, timeoutStatus, "watchdog", timeoutMsg, null);
                }
            }
        };
        main.postDelayed(watchdogRunnable, TestConfig.PROBE_TIMEOUT_MS);
        return token;
    }

    protected synchronized void cancelWatchdog() {
        if (watchdogRunnable != null) {
            main.removeCallbacks(watchdogRunnable);
            watchdogRunnable = null;
        }
    }

    protected synchronized void postResultWithToken(long token, Activity activity, AdFormat format,
                                                    ProbeStatus status, String errorCode, String errorMessage,
                                                    Runnable showAdAction) {
        if (token != activeToken) return; // Ignore late callbacks from previous probes
        cancelWatchdog();
        ProbeCallback cb = activeCallback;
        activeCallback = null;
        activeToken++; // Invalidate token to prevent duplicate callbacks

        final long ms = elapsed();
        final String sdk = safeSdkVersion();
        Runnable r = () -> {
            if (cb != null) {
                ProbeResult res = new ProbeResult(getId(), format, status, errorCode,
                        withInternetNote(errorMessage), ms, sdk);
                res.showAdAction = showAdAction;
                cb.onResult(res);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else main.post(r);
    }

    protected void finishOk(long token, Activity activity, AdFormat format) {
        finishOk(token, activity, format, null);
    }

    protected void finishOk(long token, Activity activity, AdFormat format, Runnable showAdAction) {
        postResultWithToken(token, activity, format, ProbeStatus.TEST_AD_LOADED, null, "Ad shown.", showAdAction);
    }

    protected void finishFail(long token, Activity activity, AdFormat format, String code, String message) {
        postResultWithToken(token, activity, format, classify(code, message), code, message, null);
    }

    private String withInternetNote(String msg) {
        if (!internetAvailable) {
            String note = "[No internet connectivity detected during this run; failures may reflect that, not an ad blocker.]";
            return msg == null ? note : msg + " " + note;
        }
        return msg;
    }

    private String safeSdkVersion() {
        try {
            return getSdkVersion();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    // ---- robust ad block classification ----

    protected ProbeStatus classify(String code, String message) {
        ProbeStatus specific = mapFailure(code, message);
        if (specific != null) return specific;
        String m = ((code == null ? "" : code + " ") + (message == null ? "" : message))
                .toLowerCase(Locale.US);
        if (m.contains("no fill") || m.contains("no_fill") || m.contains("no ad")
                || m.contains("no ads") || m.contains("fill") && m.contains("unavailable")
                || m.contains("ad not available") || m.contains("no ad found")
                || m.contains("ad_no_fill") || m.contains("204")) {
            return ProbeStatus.NO_FILL;
        }
        if (m.contains("network") || m.contains("connection") || m.contains("connect")
                || m.contains("timeout") || m.contains("timed out") || m.contains("unreachable")
                || m.contains("unknownhost") || m.contains("resolve host") || m.contains("dns")
                || m.contains("socket") || m.contains("internet") || m.contains("offline")
                || m.contains("ssl") || m.contains("econn") || m.contains("reset by peer")
                || m.contains("nodename") || m.contains("eai_") || m.contains("watchdog")
                || m.contains("ioexception") || m.contains("http") || m.contains("failed to connect")
                || m.contains("refused") || m.contains("cleartext") || m.contains("blocked")) {
            return internetAvailable ? ProbeStatus.BLOCKED : ProbeStatus.ERROR;
        }
        return ProbeStatus.ERROR;
    }

    protected ProbeStatus mapFailure(String code, String message) {
        return null;
    }

    protected void runOnMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else main.post(r);
    }
}
