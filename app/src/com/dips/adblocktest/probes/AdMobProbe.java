package com.dips.adblocktest.probes;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import com.dips.adblocktest.AdFormat;
import com.dips.adblocktest.BaseProbe;
import com.dips.adblocktest.DebugLog;
import com.dips.adblocktest.ProbeStatus;
import com.dips.adblocktest.TestConfig;
import com.dips.adblocktest.TestRunner;
import com.dips.adblocktest.Ui;
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
            // The MobileAdsInitProvider (manifest) owns SDK init. If the
            // status check throws, don't fail here — proceed and let the
            // actual ad load reveal the true state.
            try { MobileAds.getInitializationStatus(); } catch (Throwable t) { /* ignore */ }
            try {
                MobileAds.initialize(context, status -> { /* provider already did it */ });
            } catch (Throwable t) { /* ignore */ }
            initialized = true;
            callback.onComplete(true, null);
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
        // Use Google demo units first: account units may be under review and
        // hang instead of failing fast. Demo units verify the SDK works.
        switch (format) {
            case BANNER: probeBanner(activity, TestConfig.ADMOB_DEMO_BANNER, true); break;
            case INTERSTITIAL: probeInterstitial(activity, TestConfig.ADMOB_DEMO_INTERSTITIAL, true); break;
            case REWARDED: probeRewarded(activity, TestConfig.ADMOB_DEMO_REWARDED, true); break;
        }
    }

    private void probeBanner(Activity activity, String unitId, boolean isDemoRetry) {
        try {
            AdView adView = new AdView(activity);
            adView.setAdSize(AdSize.BANNER);
            adView.setAdUnitId(unitId);
            DebugLog.log("[ADMOB] Creating banner, unit=" + unitId);
            adView.setAdListener(new AdListener() {
                @Override public void onAdLoaded() {
                    DebugLog.log("[ADMOB] onAdLoaded callback fired");
                    noteUnit(adView, isDemoRetry);
                    finishOk(activity, AdFormat.BANNER);
                }
                @Override public void onAdFailedToLoad(LoadAdError e) {
                    DebugLog.log("[ADMOB] onAdFailedToLoad: code=" + e.getCode()
                            + " msg=" + e.getMessage());
                    String code = String.valueOf(e.getCode());
                    if (!isDemoRetry && shouldRetryWithDemo(code)) {
                        probeBanner(activity, TestConfig.ADMOB_DEMO_BANNER, true);
                        return;
                    }
                    finishFail(activity, AdFormat.BANNER, code,
                            prefixed(isDemoRetry, e.getMessage()));
                }
            });
            // Use a properly-sized host (320x50) instead of 1x1. AdMob may
            // require the view to have non-trivial size for callbacks to fire.
            // Position it off-screen so it's not visible to the user.
            android.widget.FrameLayout host = new android.widget.FrameLayout(activity);
            host.setVisibility(android.view.View.VISIBLE);
            android.widget.FrameLayout.LayoutParams hostParams =
                    new android.widget.FrameLayout.LayoutParams(
                            Ui.dp(activity, 320), Ui.dp(activity, 50));
            hostParams.topMargin = -10000; // off-screen
            activity.addContentView(host, hostParams);
            host.addView(adView,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
            DebugLog.log("[ADMOB] Calling loadAd, attached=" + (adView.getParent() != null));
            adView.loadAd(new AdRequest.Builder().build());
        } catch (Throwable t) {
            DebugLog.logError("AdMob banner", t);
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
