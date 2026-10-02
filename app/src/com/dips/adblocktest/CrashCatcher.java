package com.dips.adblocktest;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Diagnostic-only provider. It is declared FIRST in the manifest so it
 * initializes before any ad-network SDK provider, and installs a global
 * uncaught-exception handler. If the app crashes during startup, the handler
 * launches CrashActivity (in a separate :crash process) to display the
 * stack trace on screen, so the cause can be read without logcat.
 */
public class CrashCatcher extends ContentProvider {
    private static final String TAG = "CrashCatcher";

    @Override
    public boolean onCreate() {
        final Context ctx = getContext().getApplicationContext();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                throwable.printStackTrace(pw);
                pw.flush();
                String trace = sw.toString();
                Log.e(TAG, "CRASH CAPTURED\n" + trace);
                try {
                    FileWriter fw = new FileWriter(new File(ctx.getFilesDir(), "last_crash.txt"));
                    fw.write(trace);
                    fw.close();
                } catch (Exception ignored) { }
                Intent i = new Intent(ctx, CrashActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                i.putExtra("trace", trace);
                ctx.startActivity(i);
                // Give the crash activity a moment to launch before we die.
                try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
            } catch (Throwable ignored) {
            } finally {
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        });
        Log.i(TAG, "crash handler installed");
        return true;
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] sa, String so) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] sa) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] sa) { return 0; }
}
