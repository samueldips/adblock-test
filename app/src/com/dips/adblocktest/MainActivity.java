package com.dips.adblocktest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single-activity app hosting all screens: Dashboard, Networks, Test progress,
 * Network detail, Settings (+ Privacy policy, Methodology, Diagnostics, About).
 * Design A — Security Command Center (SOC) theme.
 */
public class MainActivity extends Activity {
    private FrameLayout content;
    private LinearLayout bottomNav;
    private final Deque<Screen> backStack = new ArrayDeque<>();

    private ResultStore store;
    private final Map<String, Boolean> enabled = new LinkedHashMap<>();
    private List<ProbeResult> lastResults = new ArrayList<>();
    private Map<String, List<ProbeResult>> grouped = new LinkedHashMap<>();
    private TestRunner runner;
    private boolean testing;
    private UpdateHelper updateHelper;
    private boolean noInternet = false;
    private final List<ProbeResult> currentTestResults = new ArrayList<>();

    private static class Screen {
        final String id;
        final String arg;
        Screen(String id, String arg) { this.id = id; this.arg = arg; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DebugLog.init(this);
        DebugLog.log("MainActivity.onCreate");
        store = new ResultStore(this);
        for (NetworkProbe n : TestRunner.networks()) enabled.put(n.getId(), store.isEnabled(n.getId()));
        lastResults = store.loadResults();
        grouped = TestRunner.groupByNetwork(lastResults);
        updateHelper = new UpdateHelper(this);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(Ui.SOC_BG);
        getWindow().setNavigationBarColor(Ui.SOC_BG);

        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.SOC_BG);

        root.addView(appBar());

        content = new FrameLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(content);

        bottomNav = buildBottomNav();
        root.addView(bottomNav);

        setContentView(root);
        hideSystemUI();
        navTo("dashboard", null, false);
        updateHelper.checkForUpdate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        updateHelper.checkForUpdate();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    protected void onDestroy() {
        if (runner != null) runner.cancel();
        updateHelper.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        updateHelper.onActivityResult(requestCode, resultCode);
    }

    @Override
    public void onBackPressed() {
        if (backStack.size() > 1) {
            backStack.pop();
            Screen s = backStack.peek();
            render(s, false);
        } else {
            super.onBackPressed();
        }
    }

    // ---------- navigation ----------

    private void navTo(String id, String arg, boolean push) {
        Screen s = new Screen(id, arg);
        Screen top = backStack.peek();
        if (top != null && top.id.equals(id)
                && (arg == null ? top.arg == null : arg.equals(top.arg))) {
            render(s, true);
            return;
        }
        if (push || backStack.isEmpty()) backStack.push(s);
        else { backStack.pop(); backStack.push(s); }
        render(s, true);
    }

    private void render(Screen s, boolean fromNav) {
        View v;
        switch (s.id) {
            case "networks": v = networksScreen(); break;
            case "progress": v = progressScreen(); break;
            case "detail": v = detailScreen(s.arg); break;
            case "settings": v = settingsScreen(); break;
            case "privacy": v = textScreen("Privacy Policy", privacyPolicyText(), false); break;
            case "methodology": v = textScreen("Methodology", methodologyText(), false); break;
            case "diagnostics": v = diagnosticsScreen(); break;
            case "about": v = textScreen("About", aboutText(), true); break;
            default: v = dashboardScreen(); break;
        }
        content.removeAllViews();
        content.addView(v);
        highlightNav(s.id);
    }

    // ---------- chrome ----------

    private View appBar() {
        LinearLayout bar = Ui.hbox(this);
        bar.setBackgroundColor(Ui.SOC_SURFACE);
        int p = Ui.dp(this, 12);
        bar.setPadding(p, Ui.dp(this, 10), p, Ui.dp(this, 10));
        TextView shield = new TextView(this);
        shield.setText("\uD83D\uDEE1");
        shield.setTextSize(24);
        bar.addView(shield);
        LinearLayout titles = Ui.vbox(this);
        titles.setPadding(Ui.dp(this, 10), 0, 0, 0);
        titles.addView(Ui.title(this, "AdBlock Test", 18));
        TextView sub = Ui.mono(this, "SECURITY COMMAND CENTER");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        sub.setTextColor(Ui.NEON_CYAN);
        titles.addView(sub);
        bar.addView(titles);
        return bar;
    }

    private LinearLayout buildBottomNav() {
        LinearLayout nav = Ui.hbox(this);
        nav.setBackgroundColor(Ui.SOC_SURFACE);
        nav.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        String[] ids = {"dashboard", "networks", "settings"};
        String[] labels = {"\uD83C\uDFE0 Home", "\uD83D\uDD27 Scope", "⚙ Settings"};
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            Button b = new Button(this);
            b.setText(labels[i]);
            b.setAllCaps(false);
            b.setTag(id);
            b.setTextColor(Ui.SOC_TEXT_DIM);
            b.setBackgroundColor(0x00000000);
            b.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            final String target = id;
            b.setOnClickListener(v -> {
                if (testing && !"progress".equals(target)) {
                    navTo("progress", null, true);
                } else {
                    navTo(target, null, true);
                }
            });
            nav.addView(b);
        }
        return nav;
    }

    private void highlightNav(String id) {
        String base = id;
        if (id.equals("detail") || id.equals("progress")) base = "networks";
        if (id.equals("privacy") || id.equals("methodology")
                || id.equals("diagnostics") || id.equals("about")) base = "settings";
        for (int i = 0; i < bottomNav.getChildCount(); i++) {
            View v = bottomNav.getChildAt(i);
            if (v instanceof Button) {
                Button b = (Button) v;
                boolean sel = base.equals(b.getTag());
                b.setTextColor(sel ? Ui.NEON_GREEN : Ui.SOC_TEXT_DIM);
            }
        }
    }

    // ---------- Dashboard ----------

    private View dashboardScreen() {
        LinearLayout rootLayout = Ui.vbox(this);

        int orientation = getResources().getConfiguration().orientation;
        boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE;
        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        boolean isWide = isLandscape || screenWidthDp >= 600;

        int p = Ui.dp(this, 8);
        int pOuter = Ui.dp(this, 8);

        List<ProbeResult> activeList = testing || !currentTestResults.isEmpty() ? currentTestResults : lastResults;
        Map<String, List<ProbeResult>> activeGrouped = TestRunner.groupByNetwork(activeList);

        int leaksCount = 0;
        for (ProbeResult r : activeList) {
            if (r.status == ProbeStatus.TEST_AD_LOADED) leaksCount++;
        }

        // Left Column Content (Telemetry Stats + Active Scope Summary)
        LinearLayout leftCol = Ui.vbox(this);

        // 1. Telemetry Stat Panels (3 columns with explicit right margins)
        LinearLayout statsRow = Ui.hbox(this);
        int blockedNetCount = countBlockedNetworks(activeGrouped);
        int totalNetCount = TestRunner.networks().size();

        View statCard1 = socStatCard("BLOCKED", blockedNetCount + "/" + totalNetCount, Ui.NEON_GREEN);
        LinearLayout.LayoutParams statLp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        statLp1.setMargins(0, 0, Ui.dp(this, 6), 0);
        statCard1.setLayoutParams(statLp1);
        statsRow.addView(statCard1);

        View statCard2 = socStatCard("LEAKED", String.valueOf(leaksCount), leaksCount > 0 ? Ui.NEON_RED : Ui.NEON_GREEN);
        LinearLayout.LayoutParams statLp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        statLp2.setMargins(0, 0, Ui.dp(this, 6), 0);
        statCard2.setLayoutParams(statLp2);
        statsRow.addView(statCard2);

        View statCard3 = socStatCard("AUDITED", activeList.isEmpty() ? "0" : fmtTimeShort(store.getLastRunTimestamp()), Ui.SOC_TEXT_MONO);
        LinearLayout.LayoutParams statLp3 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        statCard3.setLayoutParams(statLp3);
        statsRow.addView(statCard3);

        leftCol.addView(statsRow);

        leftCol.addView(Ui.spacer(this, 4));

        if (noInternet && !activeList.isEmpty()) {
            TextView w = Ui.mono(this, "⚠ NO INTERNET CONNECTIVITY DETECTED DURING LAST RUN");
            w.setTextColor(Ui.NEON_AMBER);
            leftCol.addView(w);
            leftCol.addView(Ui.spacer(this, 4));
        }

        // 2. Active Scope Selection Summary Card
        leftCol.addView(activeScopeSummaryCard());

        // Right Column Content (Network Probes List)
        LinearLayout rightCol = Ui.vbox(this);

        LinearLayout monitorCard = Ui.card(this);
        monitorCard.setPadding(p, p, p, p);

        LinearLayout monitorHeader = Ui.hbox(this);
        String monitorTitle = testing ? "NETWORK PROBES (7) — LIVE" : (!activeList.isEmpty() ? "NETWORK PROBES (7)" : "NETWORK PROBES");
        TextView mTitle = Ui.mono(this, monitorTitle);
        mTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        monitorHeader.addView(mTitle);

        String badgeText = testing ? "LIVE PROBING" : (!activeList.isEmpty() ? leaksCount + " LEAKED / " + blockedNetCount + " BLOCKED" : "READY");
        int badgeBg = testing ? Ui.ACCENT_DARK : (leaksCount > 0 ? 0xFF5C0018 : Ui.ACCENT_DARK);
        int badgeTextCol = testing ? Ui.NEON_GREEN : (leaksCount > 0 ? Ui.NEON_RED : Ui.NEON_GREEN);
        monitorHeader.addView(Ui.badgePill(this, badgeText, badgeBg, badgeTextCol));

        monitorCard.addView(monitorHeader);
        monitorCard.addView(Ui.spacer(this, 4));
        monitorCard.addView(Ui.divider(this));
        monitorCard.addView(Ui.spacer(this, 4));

        for (NetworkProbe n : TestRunner.networks()) {
            monitorCard.addView(compactNetworkRow(n, activeGrouped));
        }
        rightCol.addView(monitorCard);

        // Dynamic Split Container Layout (Tablet / Widescreen / Landscape side-by-side vs Portrait single column)
        LinearLayout mainContentContainer;
        if (isWide) {
            mainContentContainer = Ui.hbox(this);
            mainContentContainer.setPadding(pOuter, pOuter, pOuter, pOuter);

            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            leftLp.setMargins(0, 0, Ui.dp(this, 8), 0);
            leftCol.setLayoutParams(leftLp);
            mainContentContainer.addView(leftCol);

            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            rightCol.setLayoutParams(rightLp);
            mainContentContainer.addView(rightCol);
        } else {
            mainContentContainer = Ui.vbox(this);
            mainContentContainer.setPadding(pOuter, pOuter, pOuter, pOuter);

            mainContentContainer.addView(leftCol);
            mainContentContainer.addView(Ui.spacer(this, 4));

            // Scroll indicator in portrait mode
            LinearLayout scrollHintBar = Ui.hbox(this);
            scrollHintBar.setGravity(Gravity.CENTER);
            scrollHintBar.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
            TextView scrollHint = Ui.badgePill(this, "📜 SCROLL DOWN FOR NETWORK PROBE BREAKDOWN ▼", Ui.SURFACE2, Ui.NEON_CYAN);
            scrollHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            scrollHintBar.addView(scrollHint);
            mainContentContainer.addView(scrollHintBar);

            mainContentContainer.addView(Ui.spacer(this, 4));
            mainContentContainer.addView(rightCol);
        }

        // ScrollView for middle content
        ScrollView scrollContent = new ScrollView(this);
        scrollContent.setFillViewport(true);
        scrollContent.setVerticalFadingEdgeEnabled(true);
        scrollContent.setFadingEdgeLength(Ui.dp(this, 16));
        scrollContent.addView(mainContentContainer);
        scrollContent.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rootLayout.addView(scrollContent);

        // Anchored CTA Control Bar
        LinearLayout bottomControlBar = Ui.vbox(this);
        bottomControlBar.setBackground(Ui.cardBg(Ui.SOC_SURFACE, Ui.SOC_BORDER, 0));
        bottomControlBar.setPadding(pOuter, Ui.dp(this, 6), pOuter, Ui.dp(this, 6));

        GradientDrawable ctaBg = new GradientDrawable();
        ctaBg.setColor(Ui.NEON_GREEN);
        ctaBg.setCornerRadius(Ui.dp(this, 10));

        LinearLayout ctaWrapper = Ui.vbox(this);
        ctaWrapper.setBackground(ctaBg);
        ctaWrapper.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        ctaWrapper.setGravity(Gravity.CENTER);
        ctaWrapper.setOnClickListener(v -> { if (!testing) startTest(); });

        TextView ctaTitle = new TextView(this);
        ctaTitle.setText(testing ? "AUDIT IN PROGRESS…" : "RUN FULL AUDIT");
        ctaTitle.setTextColor(0xFF0A0E14);
        ctaTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        ctaTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        ctaWrapper.addView(ctaTitle);

        TextView ctaSub = new TextView(this);
        ctaSub.setText("inject synthetic ad probes (7 networks)");
        ctaSub.setTextColor(0xFF0A0E14);
        ctaSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        ctaSub.setTypeface(Typeface.MONOSPACE);
        ctaWrapper.addView(ctaSub);

        bottomControlBar.addView(ctaWrapper);

        rootLayout.addView(bottomControlBar);

        return rootLayout;
    }

    private View socStatCard(String label, String value, int color) {
        LinearLayout c = Ui.vbox(this);
        c.setBackground(Ui.cardBg(Ui.SOC_SURFACE));

        int p = Ui.dp(this, 8);
        c.setPadding(p, p, p, p);

        TextView lView = Ui.mono(this, label);
        lView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        lView.setTextColor(Ui.SOC_TEXT_DIM);
        c.addView(lView);

        c.addView(Ui.spacer(this, 2));

        TextView vView = new TextView(this);
        vView.setText(value);
        vView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        vView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        vView.setTextColor(color);
        c.addView(vView);

        return c;
    }

    private View compactNetworkRow(NetworkProbe n, Map<String, List<ProbeResult>> activeGrouped) {
        LinearLayout card = Ui.vbox(this);
        card.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));

        String verdict = TestRunner.verdictFor(n.getId(), activeGrouped);

        String badgeText;
        int badgeBg;
        int badgeTextColor;
        int dotColor;

        if (verdict.equals("Blocked")) {
            badgeText = "BLOCKED";
            badgeBg = Ui.ACCENT_DARK;
            badgeTextColor = Ui.NEON_GREEN;
            dotColor = Ui.NEON_GREEN;
        } else if (verdict.equals("Partially blocked")) {
            badgeText = "PARTIAL";
            badgeBg = 0xFF5C3C00;
            badgeTextColor = Ui.NEON_AMBER;
            dotColor = Ui.NEON_AMBER;
        } else if (verdict.equals("Ads shown")) {
            badgeText = "LEAKED";
            badgeBg = 0xFF5C0018;
            badgeTextColor = Ui.NEON_RED;
            dotColor = Ui.NEON_RED;
        } else if (verdict.equals("Disabled")) {
            badgeText = "DISABLED";
            badgeBg = Ui.SURFACE2;
            badgeTextColor = Ui.SOC_TEXT_DIM;
            dotColor = Ui.SOC_TEXT_DIM;
        } else if (verdict.equals("No fill")) {
            badgeText = "NO FILL";
            badgeBg = Ui.SURFACE2;
            badgeTextColor = Ui.NEON_AMBER;
            dotColor = Ui.NEON_AMBER;
        } else {
            badgeText = "ERROR";
            badgeBg = 0xFF5C0018;
            badgeTextColor = Ui.NEON_RED;
            dotColor = Ui.NEON_RED;
        }

        // Top Header: Bullet Dot + Network Name + Status Badge + Details Button
        LinearLayout header = Ui.hbox(this);

        TextView dot = new TextView(this);
        dot.setText("● ");
        dot.setTextSize(13);
        dot.setTextColor(dotColor);
        header.addView(dot);

        TextView name = Ui.title(this, n.getName(), 15);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(name);

        TextView pill = Ui.badgePill(this, badgeText, badgeBg, badgeTextColor);
        header.addView(pill);

        TextView detailsBtn = Ui.badgePill(this, "🔍 DETAILS", Ui.SURFACE2, Ui.NEON_CYAN);
        LinearLayout.LayoutParams dtLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dtLp.setMargins(Ui.dp(this, 4), 0, 0, 0);
        detailsBtn.setLayoutParams(dtLp);
        detailsBtn.setOnClickListener(v -> {
            List<ProbeResult> rs = activeGrouped.get(n.getId());
            showNetworkDetailsDialog(n, rs);
        });
        header.addView(detailsBtn);

        card.addView(header);

        // Format breakdown list
        List<ProbeResult> rs = activeGrouped.get(n.getId());
        Map<AdFormat, ProbeResult> formatMap = new LinkedHashMap<>();
        if (rs != null) {
            for (ProbeResult r : rs) {
                formatMap.put(r.format, r);
            }
        }

        for (AdFormat f : n.getFormats()) {
            LinearLayout fRow = Ui.hbox(this);
            fRow.setPadding(Ui.dp(this, 10), Ui.dp(this, 1), 0, Ui.dp(this, 1));

            TextView fName = Ui.mono(this, "• " + f.label);
            fName.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 100), ViewGroup.LayoutParams.WRAP_CONTENT));
            fRow.addView(fName);

            ProbeResult r = formatMap.get(f);
            if (r != null) {
                TextView st = Ui.mono(this, r.status.label);
                st.setTextColor(r.status.color);
                fRow.addView(st);

                if (r.showAdAction != null) {
                    TextView showLink = new TextView(this);
                    showLink.setText(" [👁 Show Ad]");
                    showLink.setTextColor(Ui.NEON_CYAN);
                    showLink.setTypeface(Typeface.DEFAULT_BOLD);
                    showLink.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    showLink.setPadding(Ui.dp(this, 4), 0, 0, 0);
                    showLink.setOnClickListener(v -> r.showAdAction.run());
                    fRow.addView(showLink);
                }
            } else {
                TextView st = Ui.mono(this, "Not probed");
                fRow.addView(st);
            }

            card.addView(fRow);
        }

        card.addView(Ui.divider(this));
        return card;
    }

    private void showNetworkDetailsDialog(NetworkProbe n, List<ProbeResult> results) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(n.getName() + " — Technical Details");

        LinearLayout layout = Ui.vbox(this);
        int p = Ui.dp(this, 14);
        layout.setPadding(p, p, p, p);

        TextView sdkText = Ui.mono(this, "SDK Version: " + n.getSdkVersion());
        layout.addView(sdkText);
        layout.addView(Ui.spacer(this, 6));

        String verdict = TestRunner.verdictFor(n.getId(), grouped);
        TextView verdictText = Ui.title(this, "Overall Verdict: " + verdict, 15);
        verdictText.setTextColor(verdict.contains("Blocked") || verdict.contains("shown") ? Ui.NEON_GREEN : Ui.NEON_AMBER);
        layout.addView(verdictText);
        layout.addView(Ui.spacer(this, 8));
        layout.addView(Ui.divider(this));
        layout.addView(Ui.spacer(this, 6));

        layout.addView(Ui.mono(this, "FORMAT PROBE BREAKDOWN:"));
        layout.addView(Ui.spacer(this, 4));

        if (results != null && !results.isEmpty()) {
            for (ProbeResult r : results) {
                LinearLayout rRow = Ui.vbox(this);
                rRow.setBackground(Ui.cardBg(Ui.SURFACE2));
                rRow.setPadding(p, p, p, p);

                TextView fTitle = Ui.title(this, r.format.label + " — " + r.status.label, 14);
                fTitle.setTextColor(r.status.color);
                rRow.addView(fTitle);

                rRow.addView(Ui.mono(this, "Latency: " + r.latencyMs + " ms"));
                if (r.errorCode != null) {
                    rRow.addView(Ui.mono(this, "Error Code: " + r.errorCode));
                }
                if (r.errorMessage != null) {
                    rRow.addView(Ui.body(this, "Details: " + r.errorMessage));
                }

                if (r.showAdAction != null) {
                    rRow.addView(Ui.spacer(this, 6));
                    Button previewBtn = Ui.ghostButton(this, "👁 Preview Loaded " + r.format.label);
                    previewBtn.setOnClickListener(v -> r.showAdAction.run());
                    rRow.addView(previewBtn);
                }

                layout.addView(rRow);
                layout.addView(Ui.spacer(this, 6));
            }
        } else {
            layout.addView(Ui.body(this, "No probe results available for this provider."));
        }

        layout.addView(Ui.spacer(this, 6));
        layout.addView(Ui.mono(this, "PROBE METHODOLOGY:"));
        layout.addView(Ui.spacer(this, 4));
        layout.addView(Ui.body(this, n.getMethodology()));

        builder.setView(Ui.scrollWrap(this, layout));
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private View activeScopeSummaryCard() {
        LinearLayout card = Ui.card(this);
        int p = Ui.dp(this, 8);
        card.setPadding(p, p, p, p);

        int activeFmtCount = 0;
        int totalFmtCount = 0;
        for (NetworkProbe n : TestRunner.networks()) {
            for (AdFormat f : n.getFormats()) {
                totalFmtCount++;
                if (store.isFormatEnabled(n.getId(), f) && enabled.getOrDefault(n.getId(), true)) {
                    activeFmtCount++;
                }
            }
        }

        LinearLayout header = Ui.hbox(this);
        TextView title = Ui.mono(this, "ACTIVE SCOPE ( 範囲 )");
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);
        header.addView(Ui.badgePill(this, activeFmtCount + " / " + totalFmtCount + " FORMATS", Ui.ACCENT_DARK, Ui.NEON_GREEN));
        card.addView(header);

        card.addView(Ui.spacer(this, 4));

        LinearLayout row = Ui.hbox(this);
        TextView desc = Ui.body(this, "Configure providers & format chips:");
        desc.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(desc);

        Button configBtn = Ui.ghostButton(this, "⚙ Configure Scope");
        configBtn.setOnClickListener(v -> navTo("networks", null, true));
        row.addView(configBtn);

        card.addView(row);
        return card;
    }

    // ---------- Dedicated Selection & Scope Hub (Networks Tab) ----------

    private View networksScreen() {
        LinearLayout col = Ui.vbox(this);
        int p = Ui.dp(this, 10);
        col.setPadding(p, p, p, p);

        int activeFmtCount = 0;
        int totalFmtCount = 0;
        for (NetworkProbe n : TestRunner.networks()) {
            for (AdFormat f : n.getFormats()) {
                totalFmtCount++;
                if (store.isFormatEnabled(n.getId(), f) && enabled.getOrDefault(n.getId(), true)) {
                    activeFmtCount++;
                }
            }
        }

        // Header Card with Quick Presets
        LinearLayout headerCard = Ui.card(this);
        headerCard.setPadding(p, p, p, p);

        LinearLayout header = Ui.hbox(this);
        TextView title = Ui.mono(this, "TEST SELECTION SCOPE ( 範囲 )");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);
        header.addView(Ui.badgePill(this, activeFmtCount + " / " + totalFmtCount + " ACTIVE", Ui.ACCENT_DARK, Ui.NEON_GREEN));
        headerCard.addView(header);

        headerCard.addView(Ui.spacer(this, 6));

        LinearLayout presetRow = Ui.hbox(this);
        TextView presetAll = Ui.chipPill(this, "☑ All", activeFmtCount == totalFmtCount);
        presetAll.setOnClickListener(v -> setPresetScope(true, true, true));
        LinearLayout.LayoutParams lpPreset1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpPreset1.setMargins(0, 0, Ui.dp(this, 4), 0);
        presetAll.setLayoutParams(lpPreset1);
        presetAll.setGravity(Gravity.CENTER);
        presetRow.addView(presetAll);

        TextView presetNone = Ui.chipPill(this, "🔲 Clear", activeFmtCount == 0);
        presetNone.setOnClickListener(v -> setPresetScope(false, false, false));
        LinearLayout.LayoutParams lpPreset2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpPreset2.setMargins(0, 0, Ui.dp(this, 4), 0);
        presetNone.setLayoutParams(lpPreset2);
        presetNone.setGravity(Gravity.CENTER);
        presetRow.addView(presetNone);

        TextView presetBanners = Ui.chipPill(this, "🖼️ Banners", false);
        presetBanners.setOnClickListener(v -> setPresetScope(true, false, false));
        LinearLayout.LayoutParams lpPreset3 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpPreset3.setMargins(0, 0, Ui.dp(this, 4), 0);
        presetBanners.setLayoutParams(lpPreset3);
        presetBanners.setGravity(Gravity.CENTER);
        presetRow.addView(presetBanners);

        TextView presetFullscreen = Ui.chipPill(this, "🎬 Fullscreen", false);
        presetFullscreen.setOnClickListener(v -> setPresetScope(false, true, true));
        LinearLayout.LayoutParams lpPreset4 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        presetFullscreen.setLayoutParams(lpPreset4);
        presetFullscreen.setGravity(Gravity.CENTER);
        presetRow.addView(presetFullscreen);

        headerCard.addView(presetRow);
        col.addView(headerCard);

        col.addView(Ui.spacer(this, 8));

        // 7 Provider Scope Cards
        for (NetworkProbe n : TestRunner.networks()) {
            LinearLayout providerCard = Ui.card(this);
            providerCard.setPadding(p, p, p, p);

            boolean providerEnabled = enabled.getOrDefault(n.getId(), true);

            // Line 1: Checkbox + Active Format Counter Badge
            LinearLayout topRow = Ui.hbox(this);
            CheckBox providerCb = Ui.checkBox(this, n.getName(), providerEnabled);
            providerCb.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            topRow.addView(providerCb);

            int pActiveCount = 0;
            for (AdFormat f : n.getFormats()) {
                if (store.isFormatEnabled(n.getId(), f) && providerEnabled) pActiveCount++;
            }
            topRow.addView(Ui.badgePill(this, pActiveCount + " / " + n.getFormats().size() + " FORMATS", providerEnabled ? Ui.ACCENT_DARK : Ui.SURFACE2, providerEnabled ? Ui.NEON_GREEN : Ui.SOC_TEXT_DIM));

            providerCard.addView(topRow);
            providerCard.addView(Ui.spacer(this, 6));

            // Line 2: ALL 3 Selectable Format Chips
            LinearLayout chipRow = Ui.hbox(this);

            List<AdFormat> formats = n.getFormats();
            for (int i = 0; i < formats.size(); i++) {
                AdFormat format = formats.get(i);
                boolean fmtSelected = store.isFormatEnabled(n.getId(), format) && providerEnabled;

                String fullLabel;
                switch (format) {
                    case BANNER: fullLabel = "🖼 Banner"; break;
                    case INTERSTITIAL: fullLabel = "🎬 Interstitial"; break;
                    case REWARDED: fullLabel = "🎁 Rewarded"; break;
                    default: fullLabel = format.label; break;
                }

                TextView chip = Ui.chipPill(this, fullLabel, fmtSelected);
                LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (i < formats.size() - 1) {
                    chipLp.setMargins(0, 0, Ui.dp(this, 6), 0);
                }
                chip.setLayoutParams(chipLp);
                chip.setGravity(Gravity.CENTER);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                chip.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6));

                chip.setOnClickListener(v -> {
                    boolean nextState = !store.isFormatEnabled(n.getId(), format);
                    store.setFormatEnabled(n.getId(), format, nextState);

                    boolean anyFmt = false;
                    for (AdFormat f : n.getFormats()) {
                        if (store.isFormatEnabled(n.getId(), f)) { anyFmt = true; break; }
                    }
                    enabled.put(n.getId(), anyFmt);
                    store.setEnabled(n.getId(), anyFmt);

                    navTo("networks", null, false);
                });
                chipRow.addView(chip);
            }

            providerCard.addView(chipRow);

            providerCb.setOnCheckedChangeListener((v, checked) -> {
                enabled.put(n.getId(), checked);
                store.setEnabled(n.getId(), checked);
                for (AdFormat f : n.getFormats()) {
                    store.setFormatEnabled(n.getId(), f, checked);
                }
                navTo("networks", null, false);
            });

            col.addView(providerCard);
            col.addView(Ui.spacer(this, 6));
        }

        return Ui.scrollWrap(this, col);
    }

    private void setPresetScope(boolean enableBanners, boolean enableInterstitials, boolean enableRewarded) {
        for (NetworkProbe n : TestRunner.networks()) {
            boolean anyEnable = false;
            for (AdFormat f : n.getFormats()) {
                boolean enableFmt = (f == AdFormat.BANNER && enableBanners)
                        || (f == AdFormat.INTERSTITIAL && enableInterstitials)
                        || (f == AdFormat.REWARDED && enableRewarded);

                store.setFormatEnabled(n.getId(), f, enableFmt);
                if (enableFmt) anyEnable = true;
            }
            enabled.put(n.getId(), anyEnable);
            store.setEnabled(n.getId(), anyEnable);
        }
        toast("Preset scope applied");
        navTo("networks", null, false);
    }

    private View networkCard(NetworkProbe n) {
        LinearLayout card = Ui.card(this);
        LinearLayout row = Ui.hbox(this);
        LinearLayout txt = Ui.vbox(this);
        txt.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        txt.addView(Ui.title(this, n.getName(), 17));
        txt.addView(Ui.mono(this, n.getSdkVersion()));
        row.addView(txt);
        String verdict = TestRunner.verdictFor(n.getId(), grouped);
        int color = Ui.SOC_TEXT_DIM;
        if (verdict.equals("Blocked") || verdict.equals("Partially blocked") || verdict.equals("Ads shown")) color = Ui.NEON_GREEN;
        else if (verdict.equals("Mixed")) color = Ui.NEON_AMBER;
        row.addView(Ui.statusPill(this, verdict, color));
        card.addView(row);
        card.addView(Ui.spacer(this, 8));
        LinearLayout row2 = Ui.hbox(this);
        TextView sup = Ui.body(this, "Formats: ");
        row2.addView(sup);
        StringBuilder sb = new StringBuilder();
        for (AdFormat f : n.getFormats()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(f.label);
        }
        row2.addView(Ui.bodyBright(this, sb.toString()));
        row2.addView(spacerH(1));
        CheckBox cb = Ui.checkBox(this, "Enabled", enabled.get(n.getId()));
        cb.setOnCheckedChangeListener((v, checked) -> {
            enabled.put(n.getId(), checked);
            store.setEnabled(n.getId(), checked);
        });
        card.addView(row2);
        final String id = n.getId();
        card.setOnClickListener(v -> navTo("detail", id, true));
        return card;
    }

    // ---------- test progress / results ----------

    private final Map<String, LinearLayout> progressRows = new LinkedHashMap<>();

    private View progressScreen() {
        LinearLayout col = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        col.setPadding(p, p, p, p);

        List<ProbeResult> activeList = testing || !currentTestResults.isEmpty() ?
                currentTestResults : lastResults;

        String headerTitle;
        if (testing) headerTitle = "AUDIT IN PROGRESS…";
        else if (!activeList.isEmpty()) headerTitle = "AUDIT RESULTS";
        else headerTitle = "AUDIT PROGRESS";

        col.addView(Ui.mono(this, headerTitle));

        if (!activeList.isEmpty() && !testing) {
            int blockedCount = 0, adShownCount = 0;
            for (ProbeResult r : activeList) {
                if (r.status == ProbeStatus.BLOCKED) blockedCount++;
                else if (r.status == ProbeStatus.TEST_AD_LOADED) adShownCount++;
            }
            TextView summaryText = Ui.mono(this,
                    "Completed · " + blockedCount + " Blocked · " + adShownCount + " Ad Shown");
            summaryText.setTextColor(Ui.NEON_GREEN);
            col.addView(summaryText);
        }

        col.addView(Ui.spacer(this, 12));
        progressRows.clear();

        Map<String, ProbeResult> done = new LinkedHashMap<>();
        for (ProbeResult r : activeList) {
            done.put(r.networkId + "|" + r.format.name(), r);
        }

        for (NetworkProbe n : TestRunner.networks()) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.title(this, n.getName(), 16));
            card.addView(Ui.spacer(this, 6));
            for (AdFormat f : n.getFormats()) {
                LinearLayout row = Ui.hbox(this);
                TextView lbl = Ui.body(this, f.label);
                lbl.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(lbl);

                ProbeResult r = done.get(n.getId() + "|" + f.name());
                renderFormatRowContent(row, r, "…", Ui.SOC_TEXT_DIM);

                card.addView(row);
                progressRows.put(n.getId() + "|" + f.name(), row);
            }
            col.addView(card);
            col.addView(Ui.spacer(this, 10));
        }

        if (testing) {
            Button cancel = Ui.ghostButton(this, "CANCEL AUDIT");
            cancel.setOnClickListener(v -> {
                if (runner != null) runner.cancel();
            });
            col.addView(cancel);
        } else {
            LinearLayout btnRow = Ui.hbox(this);
            Button runAgain = Ui.primaryButton(this, "▶ RUN AUDIT AGAIN");
            runAgain.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            runAgain.setOnClickListener(v -> startTest());
            btnRow.addView(runAgain);
            btnRow.addView(Ui.spacer(this, 8));
            Button back = Ui.ghostButton(this, "Home Dashboard");
            back.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            back.setOnClickListener(v -> navTo("dashboard", null, true));
            btnRow.addView(back);
            col.addView(btnRow);
        }
        return Ui.scrollWrap(this, col);
    }

    private void renderFormatRowContent(LinearLayout row, ProbeResult r, String defaultText, int defaultColor) {
        while (row.getChildCount() > 1) {
            row.removeViewAt(1);
        }

        if (r != null) {
            TextView st = Ui.mono(this, r.summary());
            st.setTextColor(r.status.color);
            row.addView(st);

            if (r.showAdAction != null) {
                TextView link = new TextView(this);
                link.setText(" [👁 Show Ad]");
                link.setTextColor(Ui.NEON_CYAN);
                link.setTypeface(Typeface.DEFAULT_BOLD);
                link.setPadding(Ui.dp(this, 8), 0, 0, 0);
                link.setOnClickListener(v -> r.showAdAction.run());
                row.addView(link);
            }
        } else {
            TextView st = Ui.mono(this, defaultText);
            st.setTextColor(defaultColor);
            row.addView(st);
        }
    }

    private void startTest() {
        if (testing) return;
        testing = true;
        currentTestResults.clear();
        DebugLog.log("=== Test started ===");

        for (NetworkProbe n : TestRunner.networks()) {
            enabled.put(n.getId(), store.isEnabled(n.getId()));
        }

        navTo("progress", null, true);
        runner = new TestRunner();
        runner.run(this, enabled, new TestRunner.Listener() {
            @Override public void onPreflight(boolean internetAvailable) {
                noInternet = !internetAvailable;
                DebugLog.log("[PREFLIGHT] internet=" + internetAvailable);
            }
            @Override public void onNetworkStart(NetworkProbe network) {
                DebugLog.logInitStart(network.getId());
                markNetworkRows(network, "initializing…");
            }
            @Override public void onFormatStart(NetworkProbe network, AdFormat format) {
                setRow(network.getId(), format, "loading…", Ui.SOC_TEXT_DIM, null);
            }
            @Override public void onFormatResult(ProbeResult result) {
                currentTestResults.add(result);
                grouped = TestRunner.groupByNetwork(currentTestResults);
                DebugLog.logLoadResult(result.networkId, result.format.name(),
                        result.status.name(), result.errorCode, result.errorMessage,
                        result.latencyMs);
                setRow(result.networkId, result.format,
                        result.summary(), result.status.color, result);
            }
            @Override public void onNetworkDone(NetworkProbe network) {}
            @Override public void onAllDone(List<ProbeResult> all) {
                testing = false;
                DebugLog.log("=== Test completed: " + all.size() + " results ===");
                lastResults = new ArrayList<>(all);
                grouped = TestRunner.groupByNetwork(lastResults);
                store.saveResults(lastResults);
                ReviewHelper.maybePrompt(MainActivity.this, lastResults);
                navTo("progress", null, false);
            }
        });
    }

    private void markNetworkRows(NetworkProbe n, String text) {
        for (AdFormat f : n.getFormats()) setRow(n.getId(), f, text, Ui.SOC_TEXT_DIM, null);
    }

    private void setRow(String netId, AdFormat f, String text, int color, ProbeResult result) {
        LinearLayout row = progressRows.get(netId + "|" + f.name());
        if (row != null) {
            renderFormatRowContent(row, result, text, color);
        }
    }

    // ---------- network detail ----------

    private View detailScreen(String networkId) {
        NetworkProbe n = TestRunner.byId(networkId);
        LinearLayout col = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        col.setPadding(p, p, p, p);
        if (n == null) {
            col.addView(Ui.title(this, "Unknown network", 18));
            return Ui.scrollWrap(this, col);
        }
        col.addView(Ui.title(this, n.getName(), 22));
        col.addView(Ui.mono(this, "SDK: " + n.getSdkVersion()));
        col.addView(Ui.spacer(this, 4));
        String verdict = TestRunner.verdictFor(n.getId(), grouped);
        LinearLayout vr = Ui.hbox(this);
        vr.addView(Ui.body(this, "Verdict: "));
        int color = Ui.SOC_TEXT_DIM;
        if (verdict.equals("Blocked") || verdict.equals("Partially blocked") || verdict.equals("Ads shown")) color = Ui.NEON_GREEN;
        else if (verdict.equals("Mixed")) color = Ui.NEON_AMBER;
        vr.addView(Ui.statusPill(this, verdict, color));
        col.addView(vr);
        col.addView(Ui.spacer(this, 12));

        List<ProbeResult> rs = grouped.get(networkId);
        if (rs == null || rs.isEmpty()) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.body(this, "No results yet. Run a test from the dashboard."));
            col.addView(card);
        } else {
            for (ProbeResult r : rs) {
                col.addView(formatResultCard(r));
                col.addView(Ui.spacer(this, 10));
            }
        }
        col.addView(Ui.spacer(this, 4));
        LinearLayout meth = Ui.card(this);
        meth.addView(Ui.title(this, "Methodology", 16));
        meth.addView(Ui.spacer(this, 6));
        meth.addView(Ui.body(this, n.getMethodology()));
        col.addView(meth);
        return Ui.scrollWrap(this, col);
    }

    private View formatResultCard(ProbeResult r) {
        LinearLayout card = Ui.card(this);
        LinearLayout row = Ui.hbox(this);
        TextView name = Ui.title(this, r.format.label, 16);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);
        row.addView(Ui.statusPill(this, r.status.label, r.status.color));
        card.addView(row);
        card.addView(Ui.spacer(this, 6));
        card.addView(Ui.mono(this, "Latency: " + r.latencyMs + " ms"));
        if (r.errorCode != null) {
            card.addView(Ui.mono(this, "Error code: " + r.errorCode));
        }
        if (r.errorMessage != null) {
            TextView m = Ui.body(this, r.errorMessage);
            card.addView(m);
        }

        if (r.showAdAction != null) {
            card.addView(Ui.spacer(this, 8));
            Button showBtn = Ui.ghostButton(this, "👁 Preview Loaded Ad");
            showBtn.setOnClickListener(v -> r.showAdAction.run());
            card.addView(showBtn);
        }

        card.addView(Ui.spacer(this, 4));
        card.addView(Ui.mono(this, "SDK: " + r.sdkVersion));
        return card;
    }

    // ---------- settings & info screens ----------

    private View settingsScreen() {
        LinearLayout col = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        col.setPadding(p, p, p, p);
        col.addView(Ui.title(this, "Settings & About", 20));
        col.addView(Ui.spacer(this, 12));

        col.addView(menuButton("📄  Privacy Policy", () -> navTo("privacy", null, true)));
        col.addView(Ui.spacer(this, 10));
        col.addView(menuButton("🧪  Methodology", () -> navTo("methodology", null, true)));
        col.addView(Ui.spacer(this, 10));
        col.addView(menuButton("🔧  Technical details & diagnostics",
                () -> navTo("diagnostics", null, true)));
        col.addView(Ui.spacer(this, 10));
        col.addView(menuButton("ℹ  About", () -> navTo("about", null, true)));
        col.addView(Ui.spacer(this, 10));
        col.addView(menuButton("🔄  Check for updates", () -> {
            updateHelper.checkForUpdate();
            toast("Checking for updates…");
        }));
        col.addView(Ui.spacer(this, 16));
        LinearLayout card = Ui.card(this);
        card.addView(Ui.mono(this,
                "Test runs so far: " + store.getRunCount()
                        + "\nVersion: " + BuildConfig.VERSION_NAME
                        + " (" + BuildConfig.VERSION_CODE + ")"));
        col.addView(card);
        return Ui.scrollWrap(this, col);
    }

    private View menuButton(String label, Runnable onClick) {
        Button b = Ui.ghostButton(this, label);
        b.setLayoutParams(Ui.lpw(Ui.dp(this, 52)));
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private View textScreen(String title, String text, boolean withContactButton) {
        LinearLayout col = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        col.setPadding(p, p, p, p);
        col.addView(Ui.title(this, title, 20));
        col.addView(Ui.spacer(this, 12));
        TextView t = Ui.body(this, text);
        t.setTextColor(Ui.SOC_TEXT);
        col.addView(t);
        if (withContactButton) {
            col.addView(Ui.spacer(this, 16));
            Button mail = Ui.ghostButton(this, "✉  Contact the developer");
            mail.setOnClickListener(v -> {
                Intent i = new Intent(Intent.ACTION_SENDTO,
                        Uri.parse("mailto:bronzefloor66@gmail.com"));
                i.putExtra(Intent.EXTRA_SUBJECT, "AdBlock Test feedback");
                try { startActivity(i); } catch (Exception e) { toast("No mail app found."); }
            });
            col.addView(mail);
        }
        return Ui.scrollWrap(this, col);
    }

    private View diagnosticsScreen() {
        LinearLayout col = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        col.setPadding(p, p, p, p);
        col.addView(Ui.title(this, "Technical details", 20));
        col.addView(Ui.spacer(this, 12));

        LinearLayout sdkCard = Ui.card(this);
        sdkCard.addView(Ui.mono(this, "SDK VERSIONS:"));
        sdkCard.addView(Ui.spacer(this, 6));
        for (NetworkProbe n : TestRunner.networks()) {
            LinearLayout row = Ui.hbox(this);
            TextView name = Ui.bodyBright(this, n.getName());
            name.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(name);
            row.addView(Ui.mono(this, n.getSdkVersion()));
            sdkCard.addView(row);
            sdkCard.addView(Ui.spacer(this, 4));
        }
        col.addView(sdkCard);
        col.addView(Ui.spacer(this, 12));

        LinearLayout statCard = Ui.card(this);
        statCard.addView(Ui.mono(this, "RUN STATISTICS:"));
        statCard.addView(Ui.spacer(this, 6));
        statCard.addView(Ui.mono(this, "Completed runs: " + store.getRunCount()));
        if (!lastResults.isEmpty()) {
            int loaded = 0, blocked = 0, nofill = 0, errs = 0;
            long totalMs = 0;
            for (ProbeResult r : lastResults) {
                totalMs += r.latencyMs;
                switch (r.status) {
                    case TEST_AD_LOADED: loaded++; break;
                    case BLOCKED: blocked++; break;
                    case NO_FILL: nofill++; break;
                    default: errs++; break;
                }
            }
            statCard.addView(Ui.mono(this,
                    "Last run probes: " + lastResults.size()
                            + "\nBlocked: " + blocked + " · Ad Shown: " + loaded
                            + " · No fill: " + nofill + " · Other: " + errs
                            + "\nTotal probe time: " + totalMs + " ms"
                            + "\nLast run: " + fmtTime(store.getLastRunTimestamp())));
        } else {
            statCard.addView(Ui.mono(this, "No runs yet."));
        }
        col.addView(statCard);
        col.addView(Ui.spacer(this, 12));

        if (!lastResults.isEmpty()) {
            Button copyBtn = Ui.ghostButton(this, "Copy last run results");
            copyBtn.setOnClickListener(v -> {
                StringBuilder sb = new StringBuilder();
                sb.append("AdBlock Test results\n");
                for (ProbeResult r : lastResults) {
                    sb.append(r.networkId).append(" | ").append(r.format)
                      .append(" | ").append(r.status)
                      .append(" | ").append(r.errorCode)
                      .append(" | ").append(r.errorMessage)
                      .append(" | ").append(r.latencyMs).append("ms\n");
                }
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("AdBlock Test results", sb.toString()));
                Toast.makeText(this, "Results copied. Paste them in chat.", Toast.LENGTH_LONG).show();
            });
            col.addView(copyBtn);
            col.addView(Ui.spacer(this, 8));
        }

        Button logBtn = Ui.ghostButton(this, "Export debug log path");
        logBtn.setOnClickListener(v -> {
            String path = DebugLog.getLogPath();
            if (path == null) {
                Toast.makeText(this, "No log file yet.", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Debug log path", path));
            Toast.makeText(this, "Log path copied: " + path, Toast.LENGTH_LONG).show();
        });
        col.addView(logBtn);
        col.addView(Ui.spacer(this, 12));

        LinearLayout errCard = Ui.card(this);
        errCard.addView(Ui.mono(this, "ERROR CODE REFERENCE:"));
        errCard.addView(Ui.spacer(this, 6));
        errCard.addView(Ui.body(this, errorReferenceText()));
        col.addView(errCard);

        return Ui.scrollWrap(this, col);
    }

    private int countBlockedNetworks(Map<String, List<ProbeResult>> activeGrouped) {
        int c = 0;
        for (NetworkProbe n : TestRunner.networks()) {
            if (!enabled.getOrDefault(n.getId(), true)) continue;
            String v = TestRunner.verdictFor(n.getId(), activeGrouped);
            if (v.equals("Blocked") || v.equals("Partially blocked") || v.equals("Ads shown")) c++;
        }
        return c;
    }

    private String fmtTimeShort(long ts) {
        if (ts == 0) return "—";
        SimpleDateFormat f = new SimpleDateFormat("MMM d, h:mm a", Locale.US);
        return f.format(new Date(ts));
    }

    private String errorReferenceText() {
        return "AdMob: 2 = network error (Blocked), 3 = no fill, 0/1 = internal/invalid request.\n\n"
                + "Unity Ads: NO_FILL, TIMEOUT (treated as Blocked), INITIALIZE_FAILED, "
                + "INTERNAL_ERROR, INVALID_ARGUMENT.\n\n"
                + "ironSource LevelPlay: 509/606 = no ads to show (No fill), 520 = no internet "
                + "(Blocked), 508 = init failed.\n\n"
                + "InMobi: NETWORK_UNREACHABLE / REQUEST_TIMED_OUT (Blocked), NO_FILL, "
                + "REQUEST_INVALID, SERVER_ERROR, INTERNAL_ERROR.\n\n"
                + "Chartboost: INTERNET_UNAVAILABLE / NETWORK_FAILURE (Blocked), NO_AD_FOUND "
                + "(No fill), SESSION_NOT_STARTED, SERVER_ERROR, ASSET_DOWNLOAD_FAILURE.\n\n"
                + "Start.io: reports no error codes; any test-mode failure with working "
                + "internet is classified as Blocked.\n\n"
                + "Liftoff: AD_NO_FILL (No fill); API/ASSET request errors (Blocked); other "
                + "SDK errors shown verbatim.";
    }

    private String privacyPolicyText() {
        return "PRIVACY POLICY — AdBlock Test: Ad Blocker Check\n"
                + "Developer: boofus productions (" + "bronzefloor66@gmail.com" + ")\n"
                + "Last updated: October 2026\n\n"
                + "WHAT THIS APP DOES\n"
                + "AdBlock Test checks whether an ad blocker on your device is working. When you "
                + "tap RUN TEST, the app initializes the ad SDKs of up to 7 ad networks "
                + "(AdMob, Unity Ads, ironSource, InMobi, Chartboost, Start.io, Liftoff) and "
                + "attempts to load one TEST ad per format (banner, interstitial, rewarded). "
                + "Ads are loaded only — they are never displayed unless you tap 'Show Ad'.\n\n"
                + "IMPORTANT: THIS APP IS NOT \"100% ON-DEVICE\"\n"
                + "To test whether ad blocking works, the app must intentionally contact each "
                + "ad network's servers over the internet during a test run. Each SDK makes its "
                + "own network requests to fetch test ads. No test can work without this.\n\n"
                + "WHAT DATA LEAVES YOUR DEVICE DURING A TEST\n"
                + "Each ad SDK may transmit, as part of its normal ad-request flow: your IP "
                + "address, device model and OS version, your Google advertising ID (unless you "
                + "have opted out in Android Settings → Privacy → Ads), app package name and "
                + "version, coarse network/connection information, and the test placement "
                + "identifiers. This is the minimum each network needs to return a test ad. "
                + "How each network handles that data is governed by its own privacy policy "
                + "(Google, Unity, ironSource/Unity LevelPlay, InMobi, Chartboost, Start.io, "
                + "Liftoff).\n\n"
                + "WHAT WE COLLECT\n"
                + "Nothing. The developer collects no analytics, no accounts, no personal data. "
                + "Test results (per-network verdicts, timings, SDK error codes) are stored only "
                + "on your device, in the app's private storage, so the app can show your last "
                + "run. Nothing is uploaded to the developer.\n\n"
                + "TEST ADS ONLY\n"
                + "Every placement used by this app is a test placement or test mode. No live "
                + "production ads are ever requested, and the app generates no ad revenue.\n\n"
                + "PERMISSIONS\n"
                + "INTERNET and ACCESS_NETWORK_STATE: required to attempt the test ad loads. "
                + "AD_ID: read by the ad SDKs as part of their standard ad requests (you can "
                + "opt out device-wide in Android Settings → Privacy → Ads).\n\n"
                + "CHILDREN\n"
                + "This app is not directed to children under 13.\n\n"
                + "CHANGES\n"
                + "If this policy changes, the in-app copy will be updated with a new date.\n\n"
                + "CONTACT\n"
                + "boofus productions — " + "bronzefloor66@gmail.com";
    }

    private String methodologyText() {
        return "METHODOLOGY — how a verdict is reached\n\n"
                + "1. PRE-FLIGHT CHECK\n"
                + "Before probing, the app requests https://www.google.com/generate_204 (the "
                + "standard connectivity-check endpoint). If it is unreachable, the device has "
                + "no working internet: every subsequent failure is still reported, but the app "
                + "warns that results may reflect missing connectivity rather than an ad blocker.\n\n"
                + "2. SDK INITIALIZATION (TEST MODE)\n"
                + "Each enabled network's SDK is initialized with the developer's publisher "
                + "credentials in test mode (AdMob test units, Unity testMode=true, LevelPlay "
                + "app key, InMobi account ID, Chartboost app ID + signature with dashboard "
                + "test mode ON, Start.io setTestAdsEnabled(true), Liftoff app in Test Mode). "
                + "If initialization itself fails, every format for that network is reported as "
                + "\"Init failed\" with the SDK's error.\n\n"
                + "3. ONE TEST-AD LOAD PER FORMAT\n"
                + "For each enabled format (banner, interstitial, rewarded) the app attempts a "
                + "single ad load. A 30-second watchdog guards each probe.\n\n"
                + "4. CLASSIFICATION\n"
                + "• Ad shown — a test ad arrived and can be previewed by clicking 'Show Ad'.\n"
                + "• Blocked — the SDK reported a network-layer failure (DNS/connection/TLS "
                + "failure, timeout, unreachable host) while the device has internet.\n"
                + "• No fill — the ad server was reached but returned no ad.\n"
                + "• Init failed / Error — SDK or configuration problems.\n"
                + "• Timed out — no SDK callback within 30 seconds.\n\n"
                + "5. VERDICT\n"
                + "A network reads \"Blocked\" when at least one format was blocked and none "
                + "loaded; \"Ads shown\" when every format loaded; otherwise the most "
                + "informative mixed state. The dashboard summarizes across networks.";
    }

    private String aboutText() {
        return "AdBlock Test: Ad Blocker Check\n"
                + "Version " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n\n"
                + "A security-lab utility by boofus productions that verifies your ad blocker "
                + "by attempting test-ad loads through 7 mobile ad-network SDKs: AdMob, Unity "
                + "Ads, ironSource, InMobi, Chartboost, Start.io, and Liftoff.\n\n"
                + "All ad loads use test placements and test modes — no live ads, no revenue, "
                + "no clicks. Results stay on your device.\n\n"
                + "Developer: boofus productions\n"
                + "Contact: " + "bronzefloor66@gmail.com" + "\n"
                + "Package: com.dips.adblocktest";
    }

    // ---------- helpers ----------

    private View spacerH(float weight) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, weight));
        return v;
    }

    private String fmtTime(long ms) {
        if (ms <= 0) return "never";
        return new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(new Date(ms));
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
