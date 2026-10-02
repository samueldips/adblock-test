package com.dips.adblocktest;

/**
 * Classification of a single ad-load probe.
 *
 * BLOCKED is the ad-blocker signal: the SDK could not reach its ad server
 * (DNS failure, connection refused/reset, TLS failure, timeout at the network
 * layer) while the device otherwise has internet access.
 */
public enum ProbeStatus {
    /** A test ad loaded successfully: this network is NOT blocked. */
    TEST_AD_LOADED("Loaded", 0xFF4CAF50),
    /** Network-layer failure reaching the ad server: likely blocked. */
    BLOCKED("Blocked", 0xFFFF5252),
    /** SDK reached the server but got no ad (not a blocking signal). */
    NO_FILL("No fill", 0xFFFFB74D),
    /** The SDK failed to initialize (config or runtime problem). */
    SDK_INIT_FAILED("Init failed", 0xFFBA68C8),
    /** Credentials missing (should not happen; IDs ship with the app). */
    NOT_CONFIGURED("Not configured", 0xFF9E9E9E),
    /** Probe exceeded the watchdog timeout. */
    TIMEOUT("Timed out", 0xFFFFD54F),
    /** Any other SDK error. */
    ERROR("Error", 0xFFCE93D6),
    /** Probe did not run (network disabled or skipped). */
    SKIPPED("Skipped", 0xFF757575);

    public final String label;
    public final int color;
    ProbeStatus(String label, int color) { this.label = label; this.color = color; }
}
