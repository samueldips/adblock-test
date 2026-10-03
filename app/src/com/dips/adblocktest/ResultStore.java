package com.dips.adblocktest;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists test results and per-network/per-format configuration in SharedPreferences.
 */
public class ResultStore {
    private static final String PREFS = "adblock_test_lab";
    private static final String KEY_RESULTS = "last_results_json";
    private static final String KEY_RUN_COUNT = "run_count";
    private static final String KEY_LAST_REVIEW_PROMPT = "last_review_prompt_ms";
    private static final String KEY_REVIEW_PROMPTS = "review_prompt_count";
    private static final String KEY_ENABLED_PREFIX = "enabled_";
    private static final String KEY_FORMAT_PREFIX = "fmt_enabled_";

    private final SharedPreferences prefs;

    public ResultStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isEnabled(String networkId) {
        return prefs.getBoolean(KEY_ENABLED_PREFIX + networkId, true);
    }

    public void setEnabled(String networkId, boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED_PREFIX + networkId, enabled).apply();
    }

    public boolean isFormatEnabled(String networkId, AdFormat format) {
        return prefs.getBoolean(KEY_FORMAT_PREFIX + networkId + "_" + format.name(), true);
    }

    public void setFormatEnabled(String networkId, AdFormat format, boolean enabled) {
        prefs.edit().putBoolean(KEY_FORMAT_PREFIX + networkId + "_" + format.name(), enabled).apply();
    }

    public void saveResults(List<ProbeResult> results) {
        try {
            JSONArray arr = new JSONArray();
            for (ProbeResult r : results) {
                JSONObject o = new JSONObject();
                o.put("network", r.networkId);
                o.put("format", r.format.name());
                o.put("status", r.status.name());
                o.put("code", r.errorCode);
                o.put("message", r.errorMessage);
                o.put("latency", r.latencyMs);
                o.put("ts", r.timestampMs);
                o.put("sdk", r.sdkVersion);
                arr.put(o);
            }
            prefs.edit()
                    .putString(KEY_RESULTS, arr.toString())
                    .putInt(KEY_RUN_COUNT, getRunCount() + 1)
                    .apply();
        } catch (Exception ignored) {}
    }

    public List<ProbeResult> loadResults() {
        List<ProbeResult> out = new ArrayList<>();
        String json = prefs.getString(KEY_RESULTS, null);
        if (json == null) return out;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new ProbeResult(
                        o.getString("network"),
                        AdFormat.valueOf(o.getString("format")),
                        ProbeStatus.valueOf(o.getString("status")),
                        o.isNull("code") ? null : o.getString("code"),
                        o.isNull("message") ? null : o.getString("message"),
                        o.getLong("latency"),
                        o.isNull("sdk") ? "unknown" : o.getString("sdk")));
            }
        } catch (Exception ignored) {}
        return out;
    }

    public int getRunCount() {
        return prefs.getInt(KEY_RUN_COUNT, 0);
    }

    public long getLastRunTimestamp() {
        List<ProbeResult> rs = loadResults();
        long max = 0;
        for (ProbeResult r : rs) max = Math.max(max, r.timestampMs);
        return max;
    }

    public long getLastReviewPrompt() {
        return prefs.getLong(KEY_LAST_REVIEW_PROMPT, 0);
    }

    public void setLastReviewPrompt(long ms) {
        prefs.edit().putLong(KEY_LAST_REVIEW_PROMPT, ms).apply();
    }

    public int getReviewPromptCount() {
        return prefs.getInt(KEY_REVIEW_PROMPTS, 0);
    }

    public void incrementReviewPromptCount() {
        prefs.edit().putInt(KEY_REVIEW_PROMPTS, getReviewPromptCount() + 1).apply();
    }
}
