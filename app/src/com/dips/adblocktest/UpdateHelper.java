package com.dips.adblocktest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.IntentSender;
import android.util.Log;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;

/**
 * Flexible in-app updates via Play Core. Safe no-op when Play is unavailable
 * (e.g. sideloaded debug builds): all calls are guarded.
 */
public class UpdateHelper {
    private static final String TAG = "AdBlockTest";
    static final int REQUEST_UPDATE = 4242;

    private final Activity activity;
    private AppUpdateManager manager;
    private final InstallStateUpdatedListener listener = state -> {
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            promptRestart();
        }
    };

    public UpdateHelper(Activity activity) {
        this.activity = activity;
    }

    /** Check for an update; call from onCreate/onResume. */
    public void checkForUpdate() {
        try {
            manager = AppUpdateManagerFactory.create(activity);
            manager.registerListener(listener);
            manager.getAppUpdateInfo().addOnSuccessListener(info -> {
                try {
                    if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                            && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                        manager.startUpdateFlowForResult(info, activity,
                                AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE),
                                REQUEST_UPDATE);
                    } else if (info.installStatus() == InstallStatus.DOWNLOADED) {
                        promptRestart();
                    }
                } catch (IntentSender.SendIntentException e) {
                    Log.w(TAG, "update flow failed", e);
                } catch (Throwable t) {
                    Log.w(TAG, "update check failed", t);
                }
            }).addOnFailureListener(e -> Log.w(TAG, "update info failed", e));
        } catch (Throwable t) {
            Log.w(TAG, "Play AppUpdate unavailable", t);
        }
    }

    /** Forward from Activity.onActivityResult. */
    public void onActivityResult(int requestCode, int resultCode) {
        if (requestCode == REQUEST_UPDATE && resultCode != Activity.RESULT_OK) {
            Log.i(TAG, "in-app update cancelled or failed: " + resultCode);
        }
    }

    public void onDestroy() {
        try {
            if (manager != null) manager.unregisterListener(listener);
        } catch (Throwable ignored) {}
    }

    private void promptRestart() {
        new AlertDialog.Builder(activity)
                .setTitle("Update ready")
                .setMessage("An update has been downloaded. Restart now to apply it?")
                .setPositiveButton("Restart", (d, w) -> {
                    try { manager.completeUpdate(); }
                    catch (Throwable t) { Log.w(TAG, "completeUpdate failed", t); }
                })
                .setNegativeButton("Later", null)
                .show();
    }
}
