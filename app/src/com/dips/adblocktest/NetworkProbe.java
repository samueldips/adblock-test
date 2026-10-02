package com.dips.adblocktest;

import android.app.Activity;
import android.content.Context;
import java.util.List;

/**
 * Common interface every ad-network integration implements.
 * Implementations must invoke callbacks on the main thread.
 */
public interface NetworkProbe {
    /** Stable id, e.g. "admob". */
    String getId();
    /** Display name, e.g. "AdMob". */
    String getName();
    /** Ad formats this network supports probing. */
    List<AdFormat> getFormats();
    /** SDK version string for diagnostics. */
    String getSdkVersion();
    /** Short methodology note shown on the network detail screen. */
    String getMethodology();

    interface InitCallback {
        void onComplete(boolean success, String error);
    }

    interface ProbeCallback {
        void onResult(ProbeResult result);
    }

    /** Initialize the SDK (test mode). Safe to call repeatedly. */
    void initialize(Context context, InitCallback callback);

    /**
     * Attempt one test-ad load for the given format. Never shows the ad.
     * The activity is used only as a host for (hidden) banner views.
     */
    void probeFormat(Activity activity, AdFormat format, ProbeCallback callback);
}
