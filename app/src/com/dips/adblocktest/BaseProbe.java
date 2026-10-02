package com.dips.adblocktest;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import java.util.Locale;

/**
 * Shared plumbing for network probes: main-thread callbacks, a per-probe
 * watchdog, and failure classification.
 *
 * Classification policy (see also the in-app Methodology page):
 * - Each SDK's documented "no fill" signal maps to NO_FILL.
 * - Each SDK's documented network/connectivity failure maps to BLOCKED.
 * - Otherwise a generic heuristic inspects the SDK error message:
 *   network-ish wording -> BLOCKED, fill-ish wording -> NO_FILL.
 * - If the pre-flight connectivity check failed (no internet at all),
 *   every failure is BLOCKED with a note, and the UI shows a warning.
 */
public abstract class BaseProbe implements NetworkProbe {
    protected final Handler main = new Handler(Looper.getMainLooper());
    /** Set by TestRunner before a run: false when the device has no internet. */
    public static volatile boolean internetAvailable = true;

    protected long startMs;

    protected void beginProbe() {
        startMs = System.currentTimeMillis();
    }

    protected long elapsed() {
        return System.currentTimeMillis() - startMs;
    }

    protected void postResult(Activity activity, final AdFormat format, final ProbeStatus status,
                              final String errorCode, final String errorMessage) {
        final long ms = elapsed();
        final String sdk = safeSdkVersion();
        Runnable r = () -> {
            ProbeCallback cb = pendingCallback;
            pendingCallback = null;
            if (cb != null) {
                cb.onResult(new ProbeResult(getId(), format, status, errorCode,
                        withInternetNote(errorMessage), ms, sdk));
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else main.post(r);
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

    // ---- watchdog ----
    private ProbeCallback pendingCallback;
    private Runnable watchdog;

    /** Arms a watchdog that reports TIMEOUT if no result arrives in time. */
    protected void armWatchdog(final Activity activity, final AdFormat format) {
        cancelWatchdog();
        watchdog = () -> postResult(activity, format, ProbeStatus.TIMEOUT, "watchdog",
                "Probe exceeded " + TestConfig.PROBE_TIMEOUT_MS + " ms without an SDK callback.");
        main.postDelayed(watchdog, TestConfig.PROBE_TIMEOUT_MS);
    }

    protected void setPendingCallback(ProbeCallback cb) {
        this.pendingCallback = cb;
    }

    protected void cancelWatchdog() {
        if (watchdog != null) {
            main.removeCallbacks(watchdog);
            watchdog = null;
        }
    }

    protected void finishOk(Activity activity, AdFormat format) {
        cancelWatchdog();
        postResult(activity, format, ProbeStatus.TEST_AD_LOADED, null, "Test ad loaded.");
    }

    protected void finishFail(Activity activity, AdFormat format, String code, String message) {
        cancelWatchdog();
        postResult(activity, format, classify(code, message), code, message);
    }

    // ---- classification ----

    /**
     * Network-specific mapping first (override mapFailure), then generic
     * message heuristics. Returns null from mapFailure when no specific
     * mapping applies.
     */
    protected ProbeStatus classify(String code, String message) {
        ProbeStatus specific = mapFailure(code, message);
        if (specific != null) return specific;
        String m = ((code == null ? "" : code + " ") + (message == null ? "" : message))
                .toLowerCase(Locale.US);
        if (m.contains("no fill") || m.contains("no_fill") || m.contains("no ad")
                || m.contains("no ads") || m.contains("fill") && m.contains("unavailable")
                || m.contains("ad not available") || m.contains("no ad found")) {
            return ProbeStatus.NO_FILL;
        }
        if (m.contains("network") || m.contains("connection") || m.contains("connect")
                || m.contains("timeout") || m.contains("timed out") || m.contains("unreachable")
                || m.contains("unknownhost") || m.contains("resolve host") || m.contains("dns")
                || m.contains("socket") || m.contains("internet") || m.contains("offline")
                || m.contains("ssl") || m.contains("econn") || m.contains("reset by peer")
                || m.contains("nodename") || m.contains("eai_")) {
            return ProbeStatus.BLOCKED;
        }
        return ProbeStatus.ERROR;
    }

    /** Override to map this SDK's documented error codes. Return null to fall through. */
    protected ProbeStatus mapFailure(String code, String message) {
        return null;
    }

    protected void runOnMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else main.post(r);
    }
}
