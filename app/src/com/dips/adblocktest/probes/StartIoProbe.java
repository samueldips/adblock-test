package com.dips.adblocktest.probes;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.dips.adblocktest.AdFormat;
import com.dips.adblocktest.BaseProbe;
import com.dips.adblocktest.NetworkProbe;
import com.dips.adblocktest.ProbeStatus;
import com.dips.adblocktest.TestConfig;
import com.dips.adblocktest.TestRunner;
import com.startapp.sdk.ads.banner.Banner;
import com.startapp.sdk.ads.banner.BannerListener;
import com.startapp.sdk.adsbase.Ad;
import com.startapp.sdk.adsbase.StartAppAd;
import com.startapp.sdk.adsbase.StartAppSDK;
import com.startapp.sdk.adsbase.adlisteners.AdEventListener;

import java.util.Arrays;
import java.util.List;

/**
 * Start.io probe. The SDK reports no error codes on load failure, so any
 * failure in test mode is classified as BLOCKED when the device has internet
 * (test ads should always fill) — documented in the methodology.
 */
public class StartIoProbe extends BaseProbe {
    private boolean initialized;

    @Override public String getId() { return "startio"; }
    @Override public String getName() { return "Start.io"; }
    @Override public List<AdFormat> getFormats() {
        return Arrays.asList(AdFormat.BANNER, AdFormat.INTERSTITIAL, AdFormat.REWARDED);
    }
    @Override public String getSdkVersion() {
        try { return "inapp-sdk " + StartAppSDK.getVersion(); }
        catch (Throwable t) { return "inapp-sdk 5.3.2"; }
    }
    @Override public String getMethodology() {
        return "Initializes StartAppSDK with the app ID and enables SDK test ads "
                + "(setTestAdsEnabled(true)), then loads one ad per format. Start.io reports "
                + "no error codes on failure: because test ads should always fill, any load "
                + "failure while the device has internet access is classified as Blocked.";
    }

    @Override
    public void initialize(Context context, NetworkProbe.InitCallback callback) {
        runOnMain(() -> {
            if (initialized) { callback.onComplete(true, null); return; }
            try {
                StartAppSDK.init(context, TestConfig.STARTIO_APP_ID);
                StartAppSDK.setTestAdsEnabled(true);
                initialized = true;
                callback.onComplete(true, null);
            } catch (Throwable t) {
                callback.onComplete(false, t.toString());
            }
        });
    }

    @Override
    protected ProbeStatus mapFailure(String code, String message) {
        // No error codes from this SDK; test-mode ads should always fill, so a
        // failure with working internet means the request never completed.
        return ProbeStatus.BLOCKED;
    }

    @Override
    public void probeFormat(Activity activity, AdFormat format, ProbeCallback callback) {
        setPendingCallback(callback);
        beginProbe();
        armWatchdog(activity, format);
        try {
            switch (format) {
                case INTERSTITIAL: {
                    StartAppAd ad = new StartAppAd(activity);
                    ad.loadAd(StartAppAd.AdMode.FULLPAGE, listener(activity, format));
                    break;
                }
                case REWARDED: {
                    StartAppAd ad = new StartAppAd(activity);
                    ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, listener(activity, format));
                    break;
                }
                case BANNER: {
                    Banner banner = new Banner(activity, new BannerListener() {
                        @Override public void onReceiveAd(View view) {
                            finishOk(activity, format);
                        }
                        @Override public void onFailedToReceiveAd(View view) {
                            finishFail(activity, format, "no-error-code",
                                    "Start.io reported failure without an error code. "
                                            + "Test ads should always fill, so this is "
                                            + "classified as blocked.");
                        }
                        @Override public void onImpression(View view) {}
                        @Override public void onClick(View view) {}
                    });
                    TestRunner.bannerHost(activity).addView(banner,
                            new ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT));
                    // Banner auto-loads on attach.
                    break;
                }
            }
        } catch (Throwable t) {
            finishFail(activity, format, "exception", t.toString());
        }
    }

    private AdEventListener listener(Activity activity, AdFormat format) {
        return new AdEventListener() {
            @Override public void onReceiveAd(Ad ad) {
                finishOk(activity, format);
            }
            @Override public void onFailedToReceiveAd(Ad ad) {
                finishFail(activity, format, "no-error-code",
                        "Start.io reported failure without an error code. "
                                + "Test ads should always fill, so this is classified as blocked.");
            }
        };
    }
}
