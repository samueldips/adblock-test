package com.dips.adblocktest;

/**
 * Classification of a single ad-load probe.
 */
public enum ProbeStatus {
    /** A test ad was fetched and can be shown to the user. */
    TEST_AD_LOADED("Ad shown", Ui.OK),
    /** Network-layer failure reaching the ad server: ad blocked. */
    BLOCKED("Blocked", Ui.OK),
    /** SDK reached the server but got no ad. */
    NO_FILL("No fill", Ui.WARN),
    /** The SDK failed to initialize. */
    SDK_INIT_FAILED("Init failed", Ui.WARN),
    /** Credentials missing. */
    NOT_CONFIGURED("Not configured", Ui.TEXT_DIM),
    /** Probe exceeded the watchdog timeout. */
    TIMEOUT("Timed out", Ui.WARN),
    /** Any other SDK error. */
    ERROR("Error", Ui.WARN),
    /** Probe did not run. */
    SKIPPED("Skipped", Ui.TEXT_DIM);

    public final String label;
    public final int color;
    ProbeStatus(String label, int color) { this.label = label; this.color = color; }
}
