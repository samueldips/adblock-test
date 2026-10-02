package com.dips.adblocktest;

/** Ad formats probed per network. */
public enum AdFormat {
    BANNER("Banner"),
    INTERSTITIAL("Interstitial"),
    REWARDED("Rewarded");

    public final String label;
    AdFormat(String label) { this.label = label; }
}
