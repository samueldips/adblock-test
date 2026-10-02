package com.dips.adblocktest.probes;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import com.dips.adblocktest.AdFormat;
import com.dips.adblocktest.BaseProbe;
import com.dips.adblocktest.ProbeStatus;
import com.dips.adblocktest.TestConfig;
import com.dips.adblocktest.TestRunner;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.util.Arrays;
import java.util.List;

/** Google AdMob probe. Probes account units first, falls back to Google demo units. */
public class AdMobProbe extends BaseProbe {
    private boolean initialized;

    @Override public String getId() { return "admob"; }
    @Override public String getName() { return "AdMob"; }
    @Override public List<AdFormat> getFormats() {
        return Arrays.asList(AdFormat.BANNER, AdFormat.INTERSTITIAL, AdFormat.REWARDED);
    }
    @Override public String getSdkVersion() { return "play-services-ads 25.5.0"; }
    @Override public String getMethodology() {
        return "Initializes the Google Mobile Ads SDK, then loads one ad per format. "
                + "Account ad units are tried first; if they fail with a configuration-type "
                + "error (e.g. app still under AdMob review), the probe retries once with "
                + "Google's official demo ad units so a blocking signal can still be measured. "
                + "ERROR_CODE_NETWORK_ERROR maps to Blocked; ERROR_CODE_NO_FILL maps to No fill.";
    }

    @Override
    public void initialize(Context context, InitCallback callback) {
        runOnMain(() -> {
            if (initialized) { callback.onComplete(true, null); return; }
            try {
                MobileAds.initialize(context, status -> {
                    initialized = true;
                    callback.onComplete(true, null);
                });
            } catch (Throwable t) {
                callback.onComplete(false, t.toString());
            }
        });
    }

    @Override
    protected ProbeStatus mapFailure(String code, String message) {
        if ("2".equals(code)) return ProbeStatus.BLOCKED;   // ERROR_CODE_NETWORK_ERROR
        if ("3".equals(code)) return ProbeStatus.NO_FILL;    // ERROR_CODE_NO_FILL
        return null;
    }

    /** True when the error suggests trying the demo unit is worthwhile. */
    private boolean shouldRetryWithDemo(String code) {
        // 0=INTERNAL_ERROR, 1=INVALID_REQUEST: config/review problems, not network.
        return "0".equals(code) || "1".equals(code);
    }

    @Override
    public void probeFormat(Activity activity, AdFormat format, ProbeCallback callback) {
        setPendingCallback(callback);
        beginProbe();
        armWatchdog(activity, format);
        switch (format) {
            case BANNER: probeBanner(activity, TestConfig.ADMOB_BANNER, false); break;
            case INTERSTITIAL: probeInterstitial(activity, TestConfig.ADMOB_INTERSTITIAL, false); break;
            case REWARDED: probeRewarded(activity, TestConfig.ADMOB_REWARDED, false); break;
        }
    }

    private void probeBanner(Activity activity, String unitId, boolean isDemoRetry) {
        try {
            AdView adView = new AdView(activity);
            adView.setAdSize(AdSize.BANNER);
            adView.setAdUnitId(unitId);
            adView.setAdListener(new AdListener() {
                @Override public void onAdLoaded() {
                    noteUnit(adView, isDemoRetry);
                    finishOk(activity, AdFormat.BANNER);
                }
                @Override public void onAdFailedToLoad(LoadAdError e) {
                    String code = String.valueOf(e.getCode());
                    if (!isDemoRetry && shouldRetryWithDemo(code)) {
                        probeBanner(activity, TestConfig.ADMOB_DEMO_BANNER, true);
                        return;
                    }
                    finishFail(activity, AdFormat.BANNER, code,
                            prefixed(isDemoRetry, e.getMessage()));
                }
            });
            TestRunner.bannerHost(activity).addView(adView,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
            adView.loadAd(new AdRequest.Builder().build());
        } catch (Throwable t) {
            finishFail(activity, AdFormat.BANNER, "exception", t.toString());
        }
    }

    private void noteUnit(AdView v, boolean isDemoRetry) {
        // keep reference to avoid GC before load completes
        v.setTag(isDemoRetry ? "demo" : "account");
    }

    private void probeInterstitial(Activity activity, String unitId, boolean isDemoRetry) {
        try {
            InterstitialAd.load(activity, unitId, new AdRequest.Builder().build(),
                    new InterstitialAdLoadCallback() {
                        @Override public void onAdLoaded(InterstitialAd ad) {
                            finishOk(activity, AdFormat.INTERSTITIAL);
                        }
                        @Override public void onAdFailedToLoad(LoadAdError e) {
                            String code = String.valueOf(e.getCode());
                            if (!isDemoRetry && shouldRetryWithDemo(code)) {
                                probeInterstitial(activity, TestConfig.ADMOB_DEMO_INTERSTITIAL, true);
                                return;
                            }
                            finishFail(activity, AdFormat.INTERSTITIAL, code,
                                    prefixed(isDemoRetry, e.getMessage()));
                        }
                    });
        } catch (Throwable t) {
            finishFail(activity, AdFormat.INTERSTITIAL, "exception", t.toString());
        }
    }

    private void probeRewarded(Activity activity, String unitId, boolean isDemoRetry) {
        try {
            RewardedAd.load(activity, unitId, new AdRequest.Builder().build(),
                    new RewardedAdLoadCallback() {
                        @Override public void onAdLoaded(RewardedAd ad) {
                            finishOk(activity, AdFormat.REWARDED);
                        }
                        @Override public void onAdFailedToLoad(LoadAdError e) {
                            String code = String.valueOf(e.getCode());
                            if (!isDemoRetry && shouldRetryWithDemo(code)) {
                                probeRewarded(activity, TestConfig.ADMOB_DEMO_REWARDED, true);
                                return;
                            }
                            finishFail(activity, AdFormat.REWARDED, code,
                                    prefixed(isDemoRetry, e.getMessage()));
                        }
                    });
        } catch (Throwable t) {
            finishFail(activity, AdFormat.REWARDED, "exception", t.toString());
        }
    }

    private static String prefixed(boolean isDemoRetry, String msg) {
        return (isDemoRetry ? "[Google demo unit] " : "[account unit] ") + msg;
    }
}
