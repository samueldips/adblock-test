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
import com.inmobi.ads.AdMetaInfo;
import com.inmobi.ads.InMobiAdRequestStatus;
import com.inmobi.ads.InMobiBanner;
import com.inmobi.ads.InMobiInterstitial;
import com.inmobi.ads.listeners.BannerAdEventListener;
import com.inmobi.ads.listeners.InterstitialAdEventListener;
import com.inmobi.sdk.InMobiSdk;
import com.inmobi.sdk.SdkInitializationListener;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

/** InMobi probe. */
public class InMobiProbe extends BaseProbe {
    private boolean initialized;

    @Override public String getId() { return "inmobi"; }
    @Override public String getName() { return "InMobi"; }
    @Override public List<AdFormat> getFormats() {
        return Arrays.asList(AdFormat.BANNER, AdFormat.INTERSTITIAL, AdFormat.REWARDED);
    }
    @Override public String getSdkVersion() {
        try { return "inmobi-ads " + InMobiSdk.getVersion(); }
        catch (Throwable t) { return "inmobi-ads 10.1.4"; }
    }
    @Override public String getMethodology() {
        return "Initializes InMobiSdk with the account ID, then loads each placement. "
                + "NETWORK_UNREACHABLE and REQUEST_TIMED_OUT map to Blocked; NO_FILL maps to "
                + "No fill. The rewarded placement is loaded via InMobiInterstitial because "
                + "InMobi SDK 10.x has no separate rewarded class.";
    }

    @Override
    public void initialize(Context context, InitCallback callback) {
        runOnMain(() -> {
            if (initialized) { callback.onComplete(true, null); return; }
            try {
                InMobiSdk.setLogLevel(InMobiSdk.LogLevel.DEBUG);
                InMobiSdk.init(context, TestConfig.INMOBI_ACCOUNT_ID, new JSONObject(),
                        new SdkInitializationListener() {
                            @Override public void onInitializationComplete(Error error) {
                                if (error == null) {
                                    initialized = true;
                                    callback.onComplete(true, null);
                                } else {
                                    callback.onComplete(false, error.toString());
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
        if ("NETWORK_UNREACHABLE".equals(code) || "REQUEST_TIMED_OUT".equals(code)) {
            return ProbeStatus.BLOCKED;
        }
        if ("NO_FILL".equals(code)) return ProbeStatus.NO_FILL;
        if ("MISSING_REQUIRED_DEPENDENCIES".equals(code)
                || "MONETIZATION_DISABLED".equals(code)) {
            return ProbeStatus.SDK_INIT_FAILED;
        }
        return null;
    }

    @Override
    public void probeFormat(Activity activity, AdFormat format, ProbeCallback callback) {
        long token = armProbe(activity, format, callback);
        try {
            switch (format) {
                case BANNER: {
                    InMobiBanner banner = new InMobiBanner(activity, TestConfig.INMOBI_BANNER);
                    banner.setListener(new BannerAdEventListener() {
                        @Override public void onAdFetchSuccessful(InMobiBanner b, AdMetaInfo metaInfo) {
                            finishOk(token, activity, format, () -> Ui.showBannerDialog(activity, "InMobi Banner", banner));
                        }
                        @Override public void onAdLoadSucceeded(InMobiBanner b, AdMetaInfo metaInfo) {
                            finishOk(token, activity, format, () -> Ui.showBannerDialog(activity, "InMobi Banner", banner));
                        }
                        @Override public void onAdFetchFailed(InMobiBanner ad, InMobiAdRequestStatus status) {
                            fail(token, activity, format, status);
                        }
                        @Override public void onAdLoadFailed(InMobiBanner ad, InMobiAdRequestStatus status) {
                            fail(token, activity, format, status);
                        }
                    });
                    TestRunner.bannerHost(activity).addView(banner,
                            new ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT));
                    banner.load();
                    break;
                }
                case INTERSTITIAL:
                    probeInterstitial(token, activity, format, TestConfig.INMOBI_INTERSTITIAL);
                    break;
                case REWARDED:
                    probeInterstitial(token, activity, format, TestConfig.INMOBI_REWARDED);
                    break;
            }
        } catch (Throwable t) {
            finishFail(token, activity, format, "exception", t.toString());
        }
    }

    private void probeInterstitial(long token, Activity activity, AdFormat format, long placementId) {
        try {
            InMobiInterstitial interstitial = new InMobiInterstitial(activity, placementId,
                    new InterstitialAdEventListener() {
                        @Override public void onAdFetchSuccessful(InMobiInterstitial ad, AdMetaInfo metaInfo) {
                            finishOk(token, activity, format, () -> {
                                if (ad.isReady()) ad.show();
                            });
                        }
                        @Override public void onAdLoadSucceeded(InMobiInterstitial ad, AdMetaInfo metaInfo) {
                            finishOk(token, activity, format, () -> {
                                if (ad.isReady()) ad.show();
                            });
                        }
                        @Override public void onAdFetchFailed(InMobiInterstitial ad, InMobiAdRequestStatus status) {
                            fail(token, activity, format, status);
                        }
                        @Override public void onAdLoadFailed(InMobiInterstitial ad, InMobiAdRequestStatus status) {
                            fail(token, activity, format, status);
                        }
                    });
            interstitial.load();
        } catch (Throwable t) {
            finishFail(token, activity, format, "exception", t.toString());
        }
    }

    private void fail(long token, Activity activity, AdFormat format, InMobiAdRequestStatus status) {
        finishFail(token, activity, format,
                status.getStatusCode().name(),
                status.getMessage() == null ? status.getStatusCode().name() : status.getMessage());
    }
}
