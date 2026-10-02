package com.dips.adblocktest;

/** Result of one ad-format probe, with diagnostics for the detail screens. */
public final class ProbeResult {
    public final String networkId;
    public final AdFormat format;
    public final ProbeStatus status;
    public final String errorCode;    // SDK error code, may be null
    public final String errorMessage; // SDK error message / diagnostics, may be null
    public final long latencyMs;
    public final long timestampMs;
    public final String sdkVersion;

    public ProbeResult(String networkId, AdFormat format, ProbeStatus status,
                       String errorCode, String errorMessage,
                       long latencyMs, String sdkVersion) {
        this.networkId = networkId;
        this.format = format;
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.latencyMs = latencyMs;
        this.timestampMs = System.currentTimeMillis();
        this.sdkVersion = sdkVersion;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder(status.label);
        if (errorCode != null) sb.append(" (").append(errorCode).append(")");
        sb.append(" · ").append(latencyMs).append(" ms");
        return sb.toString();
    }
}
