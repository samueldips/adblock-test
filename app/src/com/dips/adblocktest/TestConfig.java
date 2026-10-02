package com.dips.adblocktest;

/**
 * Publisher credentials for the 7 integrated ad networks.
 * These are publisher-side identifiers (app IDs / placement IDs), not secrets;
 * every ad SDK requires them client-side to initialize. All placements are
 * TEST placements / test mode — the app never loads live production ads.
 * Source of truth: ~/workspace/adblock-test-lab/credentials/*.txt
 */
public final class TestConfig {
    private TestConfig() {}

    // ---- AdMob (account samueldips@gmail.com) ----
    public static final String ADMOB_APP_ID = "ca-app-pub-1400886349165197~3492271370";
    public static final String ADMOB_BANNER = "ca-app-pub-1400886349165197/9267342230";
    public static final String ADMOB_INTERSTITIAL = "ca-app-pub-1400886349165197/2179189708";
    public static final String ADMOB_REWARDED = "ca-app-pub-1400886349165197/4083387602";
    // Official Google demo units, used as a safe fallback for probing:
    public static final String ADMOB_DEMO_BANNER = "ca-app-pub-3940256099942544/9214589741";
    public static final String ADMOB_DEMO_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";
    public static final String ADMOB_DEMO_REWARDED = "ca-app-pub-3940256099942544/5224354917";

    // ---- Unity Ads (org bronzefloor66) ----
    public static final String UNITY_GAME_ID = "800386354";
    public static final String UNITY_BANNER = "BP_Banner_Android";
    public static final String UNITY_INTERSTITIAL = "BP_Interstitial_Android";
    public static final String UNITY_REWARDED = "BP_Rewarded_Android";

    // ---- ironSource / Unity LevelPlay ----
    public static final String IRONSOURCE_APP_KEY = "2873a7315";
    public static final String IRONSOURCE_BANNER = "rwtdi87z1v0khkp5";
    public static final String IRONSOURCE_INTERSTITIAL = "4uj8roubkxqemlrj";
    public static final String IRONSOURCE_REWARDED = "1qimeb6pgu9jacoc";

    // ---- InMobi ----
    public static final String INMOBI_ACCOUNT_ID = "fc11834b495c404f9a22a4c44a21fbe2";
    public static final long INMOBI_BANNER = 10000828468L;
    public static final long INMOBI_INTERSTITIAL = 10000828469L;
    public static final long INMOBI_REWARDED = 10000828470L;

    // ---- Chartboost ----
    public static final String CHARTBOOST_APP_ID = "6abf12285ddf923e73e5be01";
    public static final String CHARTBOOST_APP_SIGNATURE = "bd6cf89cfa68ff63fca8947c5b1e5f8e0be4eeac";
    public static final String CHARTBOOST_LOCATION = "Default";

    // ---- Start.io ----
    public static final String STARTIO_APP_ID = "209734902";

    // ---- Liftoff (Vungle) ----
    public static final String LIFTOFF_APP_ID = "6abf1a9206f5c9624d3b9508";
    public static final String LIFTOFF_INTERSTITIAL = "DEFAULT-2548108";
    public static final String LIFTOFF_BANNER = "ADBLOCK_TEST_BANNER-0566395";
    public static final String LIFTOFF_REWARDED = "ADBLOCK_TEST_REWARDED-4495910";

    // Probe watchdog: max time to wait for a single ad load before TIMEOUT.
    public static final long PROBE_TIMEOUT_MS = 30000L;
    // Pre-flight connectivity check endpoint (designed for captive-portal checks).
    public static final String CONNECTIVITY_URL = "https://www.google.com/generate_204";
}
