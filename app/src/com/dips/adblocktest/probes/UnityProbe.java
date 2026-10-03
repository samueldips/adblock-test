package com.dips.adblocktest.probes;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import com.dips.adblocktest.AdFormat;
import com.dips.adblocktest.BaseProbe;
import com.dips.adblocktest.ProbeStatus;
import com.dips.adblocktest.TestConfig;
import com.dips.adblocktest.TestRunner;
import com.dips.adblocktest.Ui;
import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.services.banners.BannerErrorInfo;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;

import java.util.Arrays;
import java.util.List;

/** Unity Ads probe. */
public class UnityProbe extends BaseProbe {
    private boolean initialized;

    @Override public String getId() { return "unity"; }
    @Override public String getName() { return "Unity Ads"; }
    @Override public List<AdFormat> getFormats() {
        return Arrays.asList(AdFormat.BANNER, AdFormat.INTERSTITIAL, AdFormat.REWARDED);
    }
    @Override public String getSdkVersion() {
        try { return "unity-ads " + UnityAds.getVersion(); }
        catch (Throwable t) { return "unity-ads 4.12.5"; }
    }
    @Override public String getMethodology() {
        return "Initializes Unity Ads in test mode or live mode, then loads each placement. "
                + "NO_FILL maps to No fill; TIMEOUT maps to Blocked (a reachable ad server "
                + "answers quickly in test mode); INITIALIZE_FAILED maps to Init failed.";
    }

    @Override
    public void initialize(Context context, InitCallback callback) {
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
        long token = armProbe(activity, format, callback);
        try {
            switch (format) {
                case INTERSTITIAL:
                    probeInterstitial(token, activity, format, TestConfig.UNITY_INTERSTITIAL, false);
                    break;
                case REWARDED:
                    probeRewarded(token, activity, format, TestConfig.UNITY_REWARDED, false);
                    break;
                case BANNER:
                    probeBanner(token, activity, TestConfig.UNITY_BANNER, false);
                    break;
            }
        } catch (Throwable t) {
            finishFail(token, activity, format, "exception", t.toString());
        }
    }

    private void probeInterstitial(long token, Activity activity, AdFormat format, String placementId, boolean isRetry) {
        UnityAds.load(placementId, new IUnityAdsLoadListener() {
            @Override public void onUnityAdsAdLoaded(String pId) {
                finishOk(token, activity, format, () -> UnityAds.show(activity, pId));
            }
            @Override public void onUnityAdsFailedToLoad(String pId, UnityAds.UnityAdsLoadError error, String message) {
                if (!isRetry && error == UnityAds.UnityAdsLoadError.INVALID_ARGUMENT) {
                    probeInterstitial(token, activity, format, "interstitial", true);
                    return;
                }
                finishFail(token, activity, format, error.name(), message);
            }
        });
    }

    private void probeRewarded(long token, Activity activity, AdFormat format, String placementId, boolean isRetry) {
        UnityAds.load(placementId, new IUnityAdsLoadListener() {
            @Override public void onUnityAdsAdLoaded(String pId) {
                finishOk(token, activity, format, () -> UnityAds.show(activity, pId));
            }
            @Override public void onUnityAdsFailedToLoad(String pId, UnityAds.UnityAdsLoadError error, String message) {
                if (!isRetry && error == UnityAds.UnityAdsLoadError.INVALID_ARGUMENT) {
                    probeRewarded(token, activity, format, "rewardedVideo", true);
                    return;
                }
                finishFail(token, activity, format, error.name(), message);
            }
        });
    }

    private void probeBanner(long token, Activity activity, String placementId, boolean isRetry) {
        BannerView bannerView = new BannerView(activity, placementId, new UnityBannerSize(320, 50));
        bannerView.setListener(new BannerView.IListener() {
            @Override public void onBannerLoaded(BannerView banner) {
                finishOk(token, activity, AdFormat.BANNER, () -> Ui.showBannerDialog(activity, "Unity Banner", bannerView));
            }
            @Override public void onBannerShown(BannerView banner) {}
            @Override public void onBannerFailedToLoad(BannerView banner, BannerErrorInfo errorInfo) {
                if (!isRetry) {
                    probeBanner(token, activity, "banner", true);
                    return;
                }
                String code = errorInfo != null && errorInfo.errorCode != null ? errorInfo.errorCode.name() : "NO_FILL";
                String msg = errorInfo != null ? errorInfo.errorMessage : "Failed to load banner";
                finishFail(token, activity, AdFormat.BANNER, code, msg);
            }
            @Override public void onBannerClick(BannerView banner) {}
            @Override public void onBannerLeftApplication(BannerView banner) {}
        });
        TestRunner.bannerHost(activity).addView(bannerView,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
        bannerView.load();
    }
}
