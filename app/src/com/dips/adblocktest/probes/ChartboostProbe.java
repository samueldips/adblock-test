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

/** Chartboost probe (dashboard test mode is ON for this app). */
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
    public void initialize(Context context, NetworkProbe.InitCallback callback) {
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
        setPendingCallback(callback);
        beginProbe();
        armWatchdog(activity, format);
        try {
            switch (format) {
                case INTERSTITIAL: {
                    Interstitial interstitial = new Interstitial(
                            TestConfig.CHARTBOOST_LOCATION, interstitialCallback(activity, format),
                            directMediation());
                    interstitial.cache();
                    break;
                }
                case REWARDED: {
                    Rewarded rewarded = new Rewarded(
                            TestConfig.CHARTBOOST_LOCATION, rewardedCallback(activity, format),
                            directMediation());
                    rewarded.cache();
                    break;
                }
                case BANNER: {
                    Banner banner = new Banner(activity, TestConfig.CHARTBOOST_LOCATION,
                            Banner.BannerSize.STANDARD, bannerCallback(activity, format),
                            directMediation());
                    TestRunner.bannerHost(activity).addView(banner,
                            new ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT));
                    banner.cache();
                    break;
                }
            }
        } catch (Throwable t) {
            finishFail(activity, format, "exception", t.toString());
        }
    }

    private com.chartboost.sdk.Mediation directMediation() {
        String v;
        try { v = Chartboost.getSDKVersion(); } catch (Throwable t) { v = "9.2.1"; }
        return new com.chartboost.sdk.Mediation("direct", v, v);
    }

    private void onCache(Activity activity, AdFormat format, CacheEvent event, CacheError error) {
        if (error == null) {
            finishOk(activity, format);
        } else {
            String code = error.getCode() == null ? "unknown" : error.getCode().name();
            String msg = error.getException() == null ? code : error.getException().toString();
            finishFail(activity, format, code, msg);
        }
    }

    private InterstitialCallback interstitialCallback(Activity activity, AdFormat format) {
        return new InterstitialCallback() {
            @Override public void onAdLoaded(CacheEvent e, CacheError err) {
                onCache(activity, format, e, err);
            }
            @Override public void onAdRequestedToShow(ShowEvent e) {}
            @Override public void onAdShown(ShowEvent e, ShowError err) {}
            @Override public void onAdClicked(ClickEvent e, ClickError err) {}
            @Override public void onImpressionRecorded(ImpressionEvent e) {}
            @Override public void onAdDismiss(DismissEvent e) {}
        };
    }

    private RewardedCallback rewardedCallback(Activity activity, AdFormat format) {
        return new RewardedCallback() {
            @Override public void onRewardEarned(RewardEvent e) {}
            @Override public void onAdLoaded(CacheEvent e, CacheError err) {
                onCache(activity, format, e, err);
            }
            @Override public void onAdRequestedToShow(ShowEvent e) {}
            @Override public void onAdShown(ShowEvent e, ShowError err) {}
            @Override public void onAdClicked(ClickEvent e, ClickError err) {}
            @Override public void onImpressionRecorded(ImpressionEvent e) {}
            @Override public void onAdDismiss(DismissEvent e) {}
        };
    }

    private BannerCallback bannerCallback(Activity activity, AdFormat format) {
        return new BannerCallback() {
            @Override public void onAdLoaded(CacheEvent e, CacheError err) {
                onCache(activity, format, e, err);
            }
            @Override public void onAdRequestedToShow(ShowEvent e) {}
            @Override public void onAdShown(ShowEvent e, ShowError err) {}
            @Override public void onAdClicked(ClickEvent e, ClickError err) {}
            @Override public void onImpressionRecorded(ImpressionEvent e) {}
        };
    }
}
