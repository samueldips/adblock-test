package com.dips.adblocktest;

import android.app.Activity;
import android.util.Log;

import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Non-intrusive Play in-app review prompt.
 * Only fires after a completed test run with at least one loaded test ad,
 * at most once per 30 days, max 5 times ever. Never blocks the UI.
 */
public class ReviewHelper {
    private static final String TAG = "AdBlockTest";
    private static final long COOLDOWN_MS = TimeUnit.DAYS.toMillis(30);
    private static final int MAX_PROMPTS = 5;

    public static void maybePrompt(Activity activity, List<ProbeResult> results) {
        try {
            boolean anyLoaded = false;
            for (ProbeResult r : results) {
                if (r.status == ProbeStatus.TEST_AD_LOADED) { anyLoaded = true; break; }
            }
            if (!anyLoaded) return;

            ResultStore store = new ResultStore(activity);
            long now = System.currentTimeMillis();
            if (now - store.getLastReviewPrompt() < COOLDOWN_MS) return;
            if (store.getReviewPromptCount() >= MAX_PROMPTS) return;

            store.setLastReviewPrompt(now);
            store.incrementReviewPromptCount();

            ReviewManager manager = ReviewManagerFactory.create(activity);
            manager.requestReviewFlow().addOnCompleteListener(task -> {
                try {
                    if (task.isSuccessful()) {
                        ReviewInfo info = task.getResult();
                        manager.launchReviewFlow(activity, info)
                                .addOnCompleteListener(done ->
                                        Log.i(TAG, "review flow finished"));
                    } else {
                        Log.w(TAG, "review request failed", task.getException());
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "review flow failed", t);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "Play Review unavailable", t);
        }
    }
}
