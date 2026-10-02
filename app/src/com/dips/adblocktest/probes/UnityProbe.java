package com.dips.adblocktest.probes;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import com.dips.adblocktest.AdFormat;
import com.dips.adblocktest.BaseProbe;
import com.dips.adblocktest.NetworkProbe;
import com.dips.adblocktest.ProbeStatus;
import com.dips.adblocktest.TestConfig;
import com.dips.adblocktest.TestRunner;
import com.unity3d.ads.BannerAd;
import com.unity3d.ads.BannerConfiguration;
import com.unity3d.ads.BannerShowListener;
import com.unity3d.ads.BannerSize;
import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.LoadListener;
import com.unity3d.ads.UnityAds;

import java.util.Arrays;
import java.util.List;

/** Unity Ads probe (testMode=true serves Unity test ads). */
public class UnityProbe extends BaseProbe {
    private boolean initialized;

    @Override public String getId() { return "unity"; }
    @Override public String getName() { return "Unity Ads"; }
    @Override public List<AdFormat> getFormats() {
        return Arrays.asList(AdFormat.BANNER, AdFormat.INTERSTITIAL, AdFormat.REWARDED);
    }
    @Override public String getSdkVersion() {
        try { return "unity-ads " + UnityAds.getVersion(); }
        catch (Throwable t) { return "unity-ads 4.21.0"; }
    }
    @Override public String getMethodology() {
        return "Initializes Unity Ads with testMode=true, then loads each placement. "
                + "NO_FILL maps to No fill; TIMEOUT maps to Blocked (a reachable ad server "
                + "answers quickly in test mode); INITIALIZE_FAILED maps to Init failed.";
    }

    @Override
    public void initialize(Context context, NetworkProbe.InitCallback callback) {
        runOnMain(() -> {
            if (initialized || UnityAds.isInitialized()) {
                initialized = true;
                callback.onComplete(true, null);
                return;
            }
            try {
                UnityAds.initialize(context, TestConfig.UNITY_GAME_ID, true,
                        new IUnityAdsInitializationListener() {
                            @Override public void onInitializationComplete() {
                                initialized = true;
                                callback.onComplete(true, null);
                            }
                            @Override public void onInitializationFailed(
                                    UnityAds.UnityAdsInitializationError error, String message) {
                                callback.onComplete(false, error + ": " + message);
                            }
                        });
            } catch (Throwable t) {
                callback.onComplete(false, t.toString());
            }
        });
    }

    @Override
    protected ProbeStatus mapFailure(String code, String message) {
        if ("NO_FILL".equals(code)) return ProbeStatus.NO_FILL;
        if ("TIMEOUT".equals(code)) return ProbeStatus.BLOCKED;
        if ("INITIALIZE_FAILED".equals(code)) return ProbeStatus.SDK_INIT_FAILED;
        if ("INVALID_ARGUMENT".equals(code)) return ProbeStatus.ERROR;
        return null;
    }

    @Override
    public void probeFormat(Activity activity, AdFormat format, ProbeCallback callback) {
        setPendingCallback(callback);
        beginProbe();
        armWatchdog(activity, format);
        try {
            switch (format) {
                case INTERSTITIAL:
                    UnityAds.load(TestConfig.UNITY_INTERSTITIAL, loadListener(activity, format));
                    break;
                case REWARDED:
                    UnityAds.load(TestConfig.UNITY_REWARDED, loadListener(activity, format));
                    break;
                case BANNER:
                    probeBanner(activity);
                    break;
            }
        } catch (Throwable t) {
            finishFail(activity, format, "exception", t.toString());
        }
    }

    private IUnityAdsLoadListener loadListener(Activity activity, AdFormat format) {
        return new IUnityAdsLoadListener() {
            @Override public void onUnityAdsAdLoaded(String placementId) {
                finishOk(activity, format);
            }
            @Override public void onUnityAdsFailedToLoad(String placementId,
                    UnityAds.UnityAdsLoadError error, String message) {
                finishFail(activity, format, error.name(), message);
            }
        };
    }

    private void probeBanner(Activity activity) {
        BannerConfiguration config = new BannerConfiguration.Builder(
                TestConfig.UNITY_BANNER,
                new BannerSize(320, 50),
                new BannerShowListener() {
                    @Override public void onImpression(BannerAd bannerAd) {}
                    @Override public void onClicked(BannerAd bannerAd) {}
                    @Override public void onFailedToShow(BannerAd bannerAd,
                            com.unity3d.ads.UnityAdsError error) {}
                }).build();
        BannerAd.load(config, new LoadListener<BannerAd>() {
            @Override public void onAdLoaded(BannerAd bannerAd,
                    com.unity3d.ads.UnityAdsError error) {
                if (bannerAd != null) {
                    try {
                        TestRunner.bannerHost(activity).addView(bannerAd.getView(),
                                new ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT));
                    } catch (Throwable ignored) {}
                    finishOk(activity, AdFormat.BANNER);
                } else {
                    String code = error != null ? String.valueOf(error.getCode()) : "null-ad";
                    String msg = error != null ? error.getMessage() : "BannerAd was null";
                    finishFail(activity, AdFormat.BANNER, code, msg);
                }
            }
        });
    }
}
