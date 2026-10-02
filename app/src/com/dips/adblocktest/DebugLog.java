package com.dips.adblocktest;

import android.content.Context;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Writes detailed diagnostic logs to app's files directory.
 * Used to diagnose why ad SDKs fail to initialize or load ads.
 */
public final class DebugLog {
    private static File logFile;
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    public static synchronized void init(Context ctx) {
        if (logFile != null) return;
        File dir = new File(ctx.getFilesDir(), "debug");
        dir.mkdirs();
        logFile = new File(dir, "adblock-test-debug.log");
        // Keep last 200KB
        if (logFile.exists() && logFile.length() > 200 * 1024) {
            logFile.delete();
        }
        log("=== AdBlock Test debug log started ===");
        log("Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + " Android " + android.os.Build.VERSION.RELEASE
                + " (API " + android.os.Build.VERSION.SDK_INT + ")");
    }

    public static synchronized void log(String msg) {
        if (logFile == null) return;
        try (FileWriter w = new FileWriter(logFile, true)) {
            w.write(FMT.format(new Date()) + " " + msg + "\n");
        } catch (IOException ignored) {}
    }

    public static void logInitStart(String networkId) {
        log("[INIT-START] " + networkId);
    }

    public static void logInitResult(String networkId, boolean ok, String error, long latencyMs) {
        log("[INIT-" + (ok ? "OK" : "FAIL") + "] " + networkId
                + " latency=" + latencyMs + "ms"
                + (error != null ? " error=" + error : ""));
    }

    public static void logLoadStart(String networkId, String format, String unitId) {
        log("[LOAD-START] " + networkId + "/" + format + " unit=" + unitId);
    }

    public static void logLoadResult(String networkId, String format, String status,
                                      String code, String message, long latencyMs) {
        log("[LOAD-" + status + "] " + networkId + "/" + format
                + " latency=" + latencyMs + "ms"
                + " code=" + code
                + (message != null ? " msg=" + message : ""));
    }

    public static void logError(String tag, Throwable t) {
        log("[ERROR] " + tag + ": " + t.toString());
        for (StackTraceElement e : t.getStackTrace()) {
            log("    at " + e.toString());
            // Limit stack trace length
            if (e.toString().contains("adblocktest")) break;
        }
    }

    public static File getLogFile() {
        return logFile;
    }

    public static String getLogPath() {
        return logFile != null ? logFile.getAbsolutePath() : null;
    }
}
