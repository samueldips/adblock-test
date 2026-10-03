package com.dips.adblocktest.probes;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import com.chartboost.sdk.Mediation;
import com.dips.adblocktest.AdFormat;
import com.dips.adblocktest.BaseProbe;
import com.dips.adblocktest.ProbeStatus;
import com.dips.adblocktest.TestConfig;
import com.dips.adblocktest.TestRunner;
import com.dips.adblocktest.Ui;
import com.chartboost.sdk.Chartboost;
import com.chartboost.sdk.ads.Banner;
import com.chartboost.sdk.ads.Interstitial;
import com.chartboost.sdk.ads.Rewarded;
import com.chartboost.sdk.callbacks.BannerCallback;
import com.chartboost.sdk.callbacks.InterstitialCallback;
import com.chartboost.sdk.callbacks.RewardedCallback;
import com.chartboost.sdk.callbacks.StartCallback;
import com.chartboost.sdk.events.CacheError;
import com.chartboost.sdk.events.CacheEvent;
import com.chartboost.sdk.events.ClickEvent;
import com.chartboost.sdk.events.ClickError;
import com.chartboost.sdk.events.DismissEvent;
import com.chartboost.sdk.events.ImpressionEvent;
import com.chartboost.sdk.events.RewardEvent;
import com.chartboost.sdk.events.ShowEvent;
import com.chartboost.sdk.events.ShowError;
import com.chartboost.sdk.events.StartError;

import java.util.Arrays;
import java.util.List;

/** Chartboost probe. */
public class ChartboostProbe extends BaseProbe {
    private boolean initialized;

    @Override public String getId() { return "chartboost"; }
    @Override public String getName() { return "Chartboost"; }
    @Override public List<AdFormat> getFormats() {
        return Arrays.asList(AdFormat.BANNER, AdFormat.INTERSTITIAL, AdFormat.REWARDED);
    }
    @Override public String getSdkVersion() {
        try { return "chartboost-sdk " + Chartboost.getSDKVersion(); }
        catch (Throwable t) { return "chartboost-sdk 9.2.1"; }
    }
    @Override public String getMethodology() {
        return "Starts Chartboost with the app ID + signature, then caches one ad per format "
                + "on the \"Default\" location (test mode is enabled on the dashboard, so test "
                + "ads are served). INTERNET_UNAVAILABLE / NETWORK_FAILURE map to Blocked; "
                + "NO_AD_FOUND maps to No fill; SESSION_NOT_STARTED maps to Init failed.";
    }

    @Override
    public void initialize(Context context, InitCallback callback) {
        runOnMain(() -> {
            if (initialized) { callback.onComplete(true, null); return; }
            try {
                Chartboost.startWithAppId(context, TestConfig.CHARTBOOST_APP_ID,
                        TestConfig.CHARTBOOST_APP_SIGNATURE, new StartCallback() {
                            @Override public void onStartCompleted(StartError startError) {
                                if (startError == null) {
                                    initialized = true;
                                    callback.onComplete(true, null);
                                } else {
                                    callback.onComplete(false, startError.toString());
                                }
                            }
                        });
            } catch (Throwable t) {
                callback.onComplete(false, t.toString());
            }
        });
    }

    @Override
    protected ProbeStatus mapFailure(String code, String message) {
        if ("INTERNET_UNAVAILABLE".equals(code) || "NETWORK_FAILURE".equals(code)) {
            return ProbeStatus.BLOCKED;
        }
        if ("NO_AD_FOUND".equals(code)) return ProbeStatus.NO_FILL;
        if ("SESSION_NOT_STARTED".equals(code)) return ProbeStatus.SDK_INIT_FAILED;
        return null;
    }

    @Override
    public void probeFormat(Activity activity, AdFormat format, ProbeCallback callback) {
        long token = armProbe(activity, format, callback);
        try {
            switch (format) {
                case INTERSTITIAL: {
                    final Interstitial[] ref = new Interstitial[1];
                    ref[0] = new Interstitial(
                            TestConfig.CHARTBOOST_LOCATION, interstitialCallback(token, activity, format, ref),
                            directMediation());
                    ref[0].cache();
                    break;
                }
                case REWARDED: {
                    final Rewarded[] ref = new Rewarded[1];
                    ref[0] = new Rewarded(
                            TestConfig.CHARTBOOST_LOCATION, rewardedCallback(token, activity, format, ref),
                            directMediation());
                    ref[0].cache();
                    break;
                }
                case BANNER: {
                    final Banner[] ref = new Banner[1];
                    ref[0] = new Banner(activity, TestConfig.CHARTBOOST_LOCATION,
                            Banner.BannerSize.STANDARD, bannerCallback(token, activity, format, ref),
                            directMediation());
                    TestRunner.bannerHost(activity).addView(ref[0],
                            new ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT));
                    ref[0].cache();
                    break;
                }
            }
        } catch (Throwable t) {
            finishFail(token, activity, format, "exception", t.toString());
        }
    }

    private Mediation directMediation() {
        String v;
        try { v = Chartboost.getSDKVersion(); } catch (Throwable t) { v = "9.2.1"; }
        return new Mediation("direct", v, v);
    }

    private void onCache(long token, Activity activity, AdFormat format, CacheEvent event, CacheError error, Runnable showAdAction) {
        if (error == null) {
            finishOk(token, activity, format, showAdAction);
        } else {
            String code = error.getCode() == null ? "unknown" : error.getCode().name();
            String msg = error.getException() == null ? code : error.getException().toString();
            finishFail(token, activity, format, code, msg);
        }
    }

    private InterstitialCallback interstitialCallback(long token, Activity activity, AdFormat format, Interstitial[] ref) {
        return new InterstitialCallback() {
            @Override public void onAdLoaded(CacheEvent e, CacheError err) {
                onCache(token, activity, format, e, err, () -> {
                    if (ref[0] != null && ref[0].isCached()) ref[0].show();
                });
            }
            @Override public void onAdRequestedToShow(ShowEvent e) {}
            @Override public void onAdShown(ShowEvent e, ShowError err) {}
            @Override public void onAdClicked(ClickEvent e, ClickError err) {}
            @Override public void onImpressionRecorded(ImpressionEvent e) {}
            @Override public void onAdDismiss(DismissEvent e) {}
        };
    }

    private RewardedCallback rewardedCallback(long token, Activity activity, AdFormat format, Rewarded[] ref) {
        return new RewardedCallback() {
            @Override public void onRewardEarned(RewardEvent e) {}
            @Override public void onAdLoaded(CacheEvent e, CacheError err) {
                onCache(token, activity, format, e, err, () -> {
                    if (ref[0] != null && ref[0].isCached()) ref[0].show();
                });
            }
            @Override public void onAdRequestedToShow(ShowEvent e) {}
            @Override public void onAdShown(ShowEvent e, ShowError err) {}
            @Override public void onAdClicked(ClickEvent e, ClickError err) {}
            @Override public void onImpressionRecorded(ImpressionEvent e) {}
            @Override public void onAdDismiss(DismissEvent e) {}
        };
    }

    private BannerCallback bannerCallback(long token, Activity activity, AdFormat format, Banner[] ref) {
        return new BannerCallback() {
            @Override public void onAdLoaded(CacheEvent e, CacheError err) {
                onCache(token, activity, format, e, err, () -> {
                    if (ref[0] != null) {
                        try { ref[0].show(); } catch (Throwable ignored) {}
                        Ui.showBannerDialog(activity, "Chartboost Banner", ref[0]);
                    }
                });
            }
            @Override public void onAdRequestedToShow(ShowEvent e) {}
            @Override public void onAdShown(ShowEvent e, ShowError err) {}
            @Override public void onAdClicked(ClickEvent e, ClickError err) {}
            @Override public void onImpressionRecorded(ImpressionEvent e) {}
        };
    }
}
