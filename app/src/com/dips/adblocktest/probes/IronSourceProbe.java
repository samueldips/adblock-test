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
import com.unity3d.mediation.LevelPlay;
import com.unity3d.mediation.LevelPlayAdError;
import com.unity3d.mediation.LevelPlayAdInfo;
import com.unity3d.mediation.LevelPlayConfiguration;
import com.unity3d.mediation.LevelPlayInitError;
import com.unity3d.mediation.LevelPlayInitListener;
import com.unity3d.mediation.LevelPlayInitRequest;
import com.unity3d.mediation.banner.LevelPlayBannerAdView;
import com.unity3d.mediation.banner.LevelPlayBannerAdViewListener;
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAd;
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAdListener;
import com.unity3d.mediation.rewarded.LevelPlayRewardedAd;
import com.unity3d.mediation.rewarded.LevelPlayRewardedAdListener;
import com.unity3d.mediation.rewarded.LevelPlayReward;

import java.util.Arrays;
import java.util.List;

/** ironSource / Unity LevelPlay probe. */
public class IronSourceProbe extends BaseProbe {
    private boolean initialized;

    @Override public String getId() { return "ironsource"; }
    @Override public String getName() { return "ironSource"; }
    @Override public List<AdFormat> getFormats() {
        return Arrays.asList(AdFormat.BANNER, AdFormat.INTERSTITIAL, AdFormat.REWARDED);
    }
    @Override public String getSdkVersion() {
        try { return "LevelPlay " + LevelPlay.getSdkVersion(); }
        catch (Throwable t) { return "mediationsdk 9.2.0"; }
    }
    @Override public String getMethodology() {
        return "Initializes LevelPlay with the app key, then loads one ad per format. "
                + "Error 509/606 (no ads to show) map to No fill; 520 (no internet) maps to "
                + "Blocked; 508 (init failed) maps to Init failed.";
    }

    @Override
    public void initialize(Context context, NetworkProbe.InitCallback callback) {
        runOnMain(() -> {
            if (initialized) { callback.onComplete(true, null); return; }
            try {
                LevelPlay.init(context,
                        new LevelPlayInitRequest.Builder(TestConfig.IRONSOURCE_APP_KEY).build(),
                        new LevelPlayInitListener() {
                            @Override public void onInitSuccess(LevelPlayConfiguration config) {
                                initialized = true;
                                callback.onComplete(true, null);
                            }
                            @Override public void onInitFailed(LevelPlayInitError error) {
                                callback.onComplete(false, error.toString());
                            }
                        });
            } catch (Throwable t) {
                callback.onComplete(false, t.toString());
            }
        });
    }

    @Override
    protected ProbeStatus mapFailure(String code, String message) {
        // Classic IronSourceError codes wrapped by LevelPlayAdError.
        if ("509".equals(code) || "606".equals(code)) return ProbeStatus.NO_FILL;
        if ("520".equals(code)) return ProbeStatus.BLOCKED;
        if ("508".equals(code)) return ProbeStatus.SDK_INIT_FAILED;
        return null;
    }

    @Override
    public void probeFormat(Activity activity, AdFormat format, ProbeCallback callback) {
        setPendingCallback(callback);
        beginProbe();
        armWatchdog(activity, format);
        try {
            switch (format) {
                case INTERSTITIAL: {
                    LevelPlayInterstitialAd ad =
                            new LevelPlayInterstitialAd(TestConfig.IRONSOURCE_INTERSTITIAL);
                    ad.setListener(new LevelPlayInterstitialAdListener() {
                        @Override public void onAdLoaded(LevelPlayAdInfo info) {
                            finishOk(activity, format);
                        }
                        @Override public void onAdLoadFailed(LevelPlayAdError error) {
                            fail(activity, format, error);
                        }
                        @Override public void onAdDisplayed(LevelPlayAdInfo info) {}
                    });
                    ad.loadAd();
                    break;
                }
                case REWARDED: {
                    LevelPlayRewardedAd ad =
                            new LevelPlayRewardedAd(TestConfig.IRONSOURCE_REWARDED);
                    ad.setListener(new LevelPlayRewardedAdListener() {
                        @Override public void onAdLoaded(LevelPlayAdInfo info) {
                            finishOk(activity, format);
                        }
                        @Override public void onAdLoadFailed(LevelPlayAdError error) {
                            fail(activity, format, error);
                        }
                        @Override public void onAdDisplayed(LevelPlayAdInfo info) {}
                        @Override public void onAdRewarded(LevelPlayReward reward,
                                LevelPlayAdInfo info) {}
                    });
                    ad.loadAd();
                    break;
                }
                case BANNER: {
                    LevelPlayBannerAdView banner =
                            new LevelPlayBannerAdView(activity, TestConfig.IRONSOURCE_BANNER);
                    banner.setBannerListener(new LevelPlayBannerAdViewListener() {
                        @Override public void onAdLoaded(LevelPlayAdInfo info) {
                            finishOk(activity, format);
                        }
                        @Override public void onAdLoadFailed(LevelPlayAdError error) {
                            fail(activity, format, error);
                        }
                    });
                    TestRunner.bannerHost(activity).addView(banner,
                            new ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT));
                    banner.loadAd();
                    break;
                }
            }
        } catch (Throwable t) {
            finishFail(activity, format, "exception", t.toString());
        }
    }

    private void fail(Activity activity, AdFormat format, LevelPlayAdError error) {
        finishFail(activity, format,
                String.valueOf(error.getErrorCode()), error.getErrorMessage());
    }
}
