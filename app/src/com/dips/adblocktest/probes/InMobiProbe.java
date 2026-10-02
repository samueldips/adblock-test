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
import com.dips.adblocktest.TestRunner;
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

/**
 * InMobi probe. Note: InMobi SDK 10.x has no dedicated rewarded class, so the
 * rewarded placement is probed through InMobiInterstitial (the placement
 * determines the creative type server-side).
 */
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
    public void initialize(Context context, NetworkProbe.InitCallback callback) {
        runOnMain(() -> {
            if (initialized) { callback.onComplete(true, null); return; }
            try {
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
        setPendingCallback(callback);
        beginProbe();
        armWatchdog(activity, format);
        try {
            switch (format) {
                case BANNER: {
                    InMobiBanner banner = new InMobiBanner(activity, TestConfig.INMOBI_BANNER);
                    banner.setListener(new BannerAdEventListener() {
                        @Override public void onAdLoadSucceeded(InMobiBanner b, AdMetaInfo metaInfo) {
                            finishOk(activity, format);
                        }
                        @Override public void onAdFetchFailed(InMobiBanner ad,
                                InMobiAdRequestStatus status) {
                            fail(activity, format, status);
                        }
                    });
                    TestRunner.bannerHost(activity).addView(banner,
                            new ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT));
                    banner.load();
                    break;
                }
                case INTERSTITIAL:
                    probeInterstitial(activity, format, TestConfig.INMOBI_INTERSTITIAL);
                    break;
                case REWARDED:
                    // No InMobiRewarded in SDK 10.x; the rewarded placement is served
                    // through an interstitial request.
                    probeInterstitial(activity, format, TestConfig.INMOBI_REWARDED);
                    break;
            }
        } catch (Throwable t) {
            finishFail(activity, format, "exception", t.toString());
        }
    }

    private void probeInterstitial(Activity activity, AdFormat format, long placementId) {
        try {
            InMobiInterstitial interstitial = new InMobiInterstitial(activity, placementId,
                    new InterstitialAdEventListener() {
                        @Override public void onAdReceived(InMobiInterstitial ad) {
                            finishOk(activity, format);
                        }
                        @Override public void onAdFetchFailed(InMobiInterstitial ad,
                                InMobiAdRequestStatus status) {
                            fail(activity, format, status);
                        }
                    });
            interstitial.load();
        } catch (Throwable t) {
            finishFail(activity, format, "exception", t.toString());
        }
    }

    private void fail(Activity activity, AdFormat format, InMobiAdRequestStatus status) {
        finishFail(activity, format,
                status.getStatusCode().name(),
                status.getMessage() == null ? status.getStatusCode().name() : status.getMessage());
    }
}
