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
import com.vungle.ads.BannerAd;
import com.vungle.ads.BannerAdSize;
import com.vungle.ads.BaseAd;
import com.vungle.ads.BaseAdListener;
import com.vungle.ads.AdConfig;
import com.vungle.ads.InitializationListener;
import com.vungle.ads.InterstitialAd;
import com.vungle.ads.RewardedAd;
import com.vungle.ads.VungleAds;
import com.vungle.ads.VungleError;

import java.util.Arrays;
import java.util.List;

/** Liftoff (Vungle) probe — the app's placements are in Test Mode ("show test ads only"). */
public class LiftoffProbe extends BaseProbe {
    private boolean initialized;

    @Override public String getId() { return "liftoff"; }
    @Override public String getName() { return "Liftoff"; }
    @Override public List<AdFormat> getFormats() {
        return Arrays.asList(AdFormat.BANNER, AdFormat.INTERSTITIAL, AdFormat.REWARDED);
    }
    @Override public String getSdkVersion() { return "vungle-ads 7.7.9"; }
    @Override public String getMethodology() {
        return "Initializes VungleAds with the application ID, then loads each placement "
                + "(all placements are in dashboard Test Mode, so test ads are served). "
                + "AD_NO_FILL maps to No fill; API/ASSET request errors map to Blocked.";
    }

    @Override
    public void initialize(Context context, NetworkProbe.InitCallback callback) {
        runOnMain(() -> {
            if (initialized) { callback.onComplete(true, null); return; }
            try {
                VungleAds.init(context, TestConfig.LIFTOFF_APP_ID, new InitializationListener() {
                    @Override public void onSuccess() {
                        initialized = true;
                        callback.onComplete(true, null);
                    }
                    @Override public void onError(VungleError error) {
                        callback.onComplete(false,
                                error.getCode() + ": " + error.getErrorMessage());
                    }
                });
            } catch (Throwable t) {
                callback.onComplete(false, t.toString());
            }
        });
    }

    @Override
    protected ProbeStatus mapFailure(String code, String message) {
        String m = (message == null ? "" : message).toUpperCase();
        if (m.contains("NO_FILL") || "AD_NO_FILL".equals(code)) return ProbeStatus.NO_FILL;
        if (m.contains("API_REQUEST") || m.contains("ASSET_REQUEST")
                || m.contains("INVALID_ADS_ENDPOINT") || m.contains("NETWORK")) {
            return ProbeStatus.BLOCKED;
        }
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
                    InterstitialAd ad = new InterstitialAd(activity,
                            TestConfig.LIFTOFF_INTERSTITIAL, new AdConfig());
                    ad.setAdListener(listener(activity, format));
                    ad.load();
                    break;
                }
                case REWARDED: {
                    RewardedAd ad = new RewardedAd(activity,
                            TestConfig.LIFTOFF_REWARDED, new AdConfig());
                    ad.setAdListener(listener(activity, format));
                    ad.load();
                    break;
                }
                case BANNER: {
                    BannerAd ad = new BannerAd(activity, TestConfig.LIFTOFF_BANNER,
                            BannerAdSize.BANNER);
                    ad.setAdListener(new BaseAdListener() {
                        @Override public void onAdLoaded(BaseAd baseAd) {
                            try {
                                TestRunner.bannerHost(activity).addView(
                                        ((BannerAd) baseAd).getBannerView(),
                                        new ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                                ViewGroup.LayoutParams.WRAP_CONTENT));
                            } catch (Throwable ignored) {}
                            finishOk(activity, format);
                        }
                        @Override public void onAdFailedToLoad(BaseAd baseAd, VungleError error) {
                            fail(activity, format, error);
                        }
                        @Override public void onAdStart(BaseAd baseAd) {}
                        @Override public void onAdImpression(BaseAd baseAd) {}
                        @Override public void onAdEnd(BaseAd baseAd) {}
                        @Override public void onAdClicked(BaseAd baseAd) {}
                        @Override public void onAdLeftApplication(BaseAd baseAd) {}
                        @Override public void onAdFailedToPlay(BaseAd baseAd, VungleError error) {}
                    });
                    ad.load();
                    break;
                }
            }
        } catch (Throwable t) {
            finishFail(activity, format, "exception", t.toString());
        }
    }

    private BaseAdListener listener(Activity activity, AdFormat format) {
        return new BaseAdListener() {
            @Override public void onAdLoaded(BaseAd baseAd) {
                finishOk(activity, format);
            }
            @Override public void onAdFailedToLoad(BaseAd baseAd, VungleError error) {
                fail(activity, format, error);
            }
            @Override public void onAdStart(BaseAd baseAd) {}
            @Override public void onAdImpression(BaseAd baseAd) {}
            @Override public void onAdEnd(BaseAd baseAd) {}
            @Override public void onAdClicked(BaseAd baseAd) {}
            @Override public void onAdLeftApplication(BaseAd baseAd) {}
            @Override public void onAdFailedToPlay(BaseAd baseAd, VungleError error) {}
        };
    }

    private void fail(Activity activity, AdFormat format, VungleError error) {
        finishFail(activity, format,
                String.valueOf(error.getCode()), error.getErrorMessage());
    }
}
