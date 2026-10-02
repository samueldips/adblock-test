package com.dips.adblocktest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

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
 * Dark Material-3-inspired theme, shield motif, built programmatically.
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

    private static class Screen {
        final String id;
        final String arg;
        Screen(String id, String arg) { this.id = id; this.arg = arg; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ResultStore(this);
        for (NetworkProbe n : TestRunner.networks()) enabled.put(n.getId(), store.isEnabled(n.getId()));
        lastResults = store.loadResults();
        grouped = TestRunner.groupByNetwork(lastResults);
        updateHelper = new UpdateHelper(this);

        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);

        root.addView(appBar());

        content = new FrameLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(content);

        bottomNav = buildBottomNav();
        root.addView(bottomNav);

        setContentView(root);
        navTo("dashboard", null, false);
        updateHelper.checkForUpdate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHelper.checkForUpdate();
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
        bar.setBackgroundColor(Ui.SURFACE);
        int p = Ui.dp(this, 16);
        bar.setPadding(p, Ui.dp(this, 12), p, Ui.dp(this, 12));
        android.widget.TextView shield = new android.widget.TextView(this);
        shield.setText("\uD83D\uDEE1"); // shield emoji
        shield.setTextSize(28);
        bar.addView(shield);
        LinearLayout titles = Ui.vbox(this);
        titles.setPadding(Ui.dp(this, 12), 0, 0, 0);
        titles.addView(Ui.title(this, "AdBlock Test", 20));
        android.widget.TextView sub = Ui.body(this, "Ad Blocker Check Lab");
        titles.addView(sub);
        bar.addView(titles);
        return bar;
    }

    private LinearLayout buildBottomNav() {
        LinearLayout nav = Ui.hbox(this);
        nav.setBackgroundColor(Ui.SURFACE);
        nav.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        String[] ids = {"dashboard", "networks", "settings"};
        String[] labels = {"\uD83C\uDFE0 Home", "\uD83D\uDD27 Networks", "⚙ Settings"};
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            Button b = new Button(this);
            b.setText(labels[i]);
            b.setAllCaps(false);
            b.setTag(id);
            b.setTextColor(Ui.TEXT_DIM);
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
                b.setTextColor(sel ? Ui.ACCENT : Ui.TEXT_DIM);
            }
        }
    }

    // ---------- dashboard ----------

    private View dashboardScreen() {
        LinearLayout col = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        col.setPadding(p, p, p, p);

        // Status card
        LinearLayout status = Ui.card(this);
        LinearLayout head = Ui.hbox(this);
        head.addView(Ui.title(this, "\uD83D\uDEE1  Shield status", 18));
        head.addView(spacerH(1));
        status.addView(head);
        status.addView(Ui.spacer(this, 8));
        if (lastResults.isEmpty()) {
            status.addView(Ui.body(this,
                    "No test has been run yet. Tap RUN TEST below to check whether your "
                            + "ad blocker stops test ads from 7 major ad networks."));
        } else {
            status.addView(Ui.bodyBright(this, summaryLine()));
            status.addView(Ui.spacer(this, 4));
            status.addView(Ui.body(this, "Last run: " + fmtTime(store.getLastRunTimestamp())
                    + " · Run #" + store.getRunCount()));
        }
        if (noInternet && !lastResults.isEmpty()) {
            status.addView(Ui.spacer(this, 8));
            android.widget.TextView w = Ui.body(this,
                    "⚠ No internet connectivity was detected during the last run. "
                            + "Results may reflect that rather than an ad blocker.");
            w.setTextColor(Ui.WARN);
            status.addView(w);
        }
        col.addView(status);
        col.addView(Ui.spacer(this, 12));

        // Run button
        Button run = Ui.primaryButton(this, testing ? "TESTING…" : "▶  RUN TEST");
        run.setEnabled(!testing);
        run.setLayoutParams(Ui.lpw(Ui.dp(this, 56)));
        run.setOnClickListener(v -> startTest());
        col.addView(run);
        col.addView(Ui.spacer(this, 12));

        // Toggles card
        LinearLayout toggles = Ui.card(this);
        toggles.addView(Ui.title(this, "Networks to test", 16));
        toggles.addView(Ui.spacer(this, 4));
        for (NetworkProbe n : TestRunner.networks()) {
            final NetworkProbe net = n;
            android.widget.CheckBox cb = Ui.checkBox(this,
                    n.getName() + "  ·  " + n.getSdkVersion(), enabled.get(n.getId()));
            cb.setOnCheckedChangeListener((v, checked) -> {
                enabled.put(net.getId(), checked);
                store.setEnabled(net.getId(), checked);
            });
            toggles.addView(cb);
        }
        col.addView(toggles);
        col.addView(Ui.spacer(this, 12));

        // Verdicts card
        if (!lastResults.isEmpty()) {
            LinearLayout verdicts = Ui.card(this);
            verdicts.addView(Ui.title(this, "Latest verdicts", 16));
            verdicts.addView(Ui.spacer(this, 8));
            for (NetworkProbe n : TestRunner.networks()) {
                verdicts.addView(verdictRow(n));
                verdicts.addView(Ui.spacer(this, 6));
            }
            col.addView(verdicts);
        }

        ScrollView sv = Ui.scrollWrap(this, col);
        return sv;
    }

    private String summaryLine() {
        int blockedNets = 0, loadedNets = 0, enabledNets = 0;
        for (NetworkProbe n : TestRunner.networks()) {
            if (!enabled.getOrDefault(n.getId(), true)) continue;
            enabledNets++;
            String v = TestRunner.verdictFor(n.getId(), grouped);
            if (v.equals("Blocked") || v.equals("Partially blocked")) blockedNets++;
            else if (v.equals("Ads loading")) loadedNets++;
        }
        if (blockedNets == enabledNets && enabledNets > 0) {
            return "✅ Your ad blocker appears to be working: test ads were blocked on all "
                    + enabledNets + " enabled networks.";
        }
        if (blockedNets > 0) {
            return "⚠ Mixed result: ads blocked on " + blockedNets + " of " + enabledNets
                    + " networks, loading on " + loadedNets + ".";
        }
        return "❌ Ads loaded on all " + enabledNets
                + " networks — no ad blocking detected.";
    }

    private View verdictRow(NetworkProbe n) {
        LinearLayout row = Ui.hbox(this);
        android.widget.TextView name = Ui.bodyBright(this, n.getName());
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);
        String verdict = TestRunner.verdictFor(n.getId(), grouped);
        int color = Ui.TEXT_DIM;
        if (verdict.equals("Blocked")) color = Ui.DANGER;
        else if (verdict.equals("Ads loading")) color = Ui.OK;
        else if (verdict.equals("Partially blocked") || verdict.equals("Mixed")) color = Ui.WARN;
        row.addView(Ui.statusPill(this, verdict, color));
        final String id = n.getId();
        row.setOnClickListener(v -> navTo("detail", id, true));
        return row;
    }

    private View spacerH(float weight) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, weight));
        return v;
    }

    // ---------- networks list ----------

    private View networksScreen() {
        LinearLayout col = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        col.setPadding(p, p, p, p);
        col.addView(Ui.title(this, "Ad networks", 20));
        col.addView(Ui.spacer(this, 4));
        col.addView(Ui.body(this,
                "Tap a network for per-format results and diagnostics. "
                        + "Toggle a network off to skip it in the next test run."));
        col.addView(Ui.spacer(this, 12));
        for (NetworkProbe n : TestRunner.networks()) {
            col.addView(networkCard(n));
            col.addView(Ui.spacer(this, 10));
        }
        return Ui.scrollWrap(this, col);
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
        int color = Ui.TEXT_DIM;
        if (verdict.equals("Blocked")) color = Ui.DANGER;
        else if (verdict.equals("Ads loading")) color = Ui.OK;
        else if (verdict.equals("Partially blocked") || verdict.equals("Mixed")) color = Ui.WARN;
        row.addView(Ui.statusPill(this, verdict, color));
        card.addView(row);
        card.addView(Ui.spacer(this, 8));
        LinearLayout row2 = Ui.hbox(this);
        android.widget.TextView sup = Ui.body(this, "Formats: ");
        row2.addView(sup);
        StringBuilder sb = new StringBuilder();
        for (AdFormat f : n.getFormats()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(f.label);
        }
        row2.addView(Ui.bodyBright(this, sb.toString()));
        row2.addView(spacerH(1));
        android.widget.CheckBox cb = Ui.checkBox(this, "Enabled", enabled.get(n.getId()));
        cb.setOnCheckedChangeListener((v, checked) -> {
            enabled.put(n.getId(), checked);
            store.setEnabled(n.getId(), checked);
        });
        row2.addView(cb);
        card.addView(row2);
        final String id = n.getId();
        card.setOnClickListener(v -> navTo("detail", id, true));
        return card;
    }

    // ---------- test progress ----------

    private final Map<String, android.widget.TextView> progressRows = new LinkedHashMap<>();

    private View progressScreen() {
        LinearLayout col = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        col.setPadding(p, p, p, p);
        col.addView(Ui.title(this, testing ? "Test in progress…" : "Test", 20));
        col.addView(Ui.spacer(this, 12));
        progressRows.clear();
        for (NetworkProbe n : TestRunner.networks()) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.title(this, n.getName(), 16));
            card.addView(Ui.spacer(this, 6));
            for (AdFormat f : n.getFormats()) {
                LinearLayout row = Ui.hbox(this);
                android.widget.TextView lbl = Ui.body(this, f.label);
                lbl.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(lbl);
                android.widget.TextView st = Ui.body(this, "…");
                row.addView(st);
                card.addView(row);
                progressRows.put(n.getId() + "|" + f.name(), st);
            }
            col.addView(card);
            col.addView(Ui.spacer(this, 10));
        }
        if (testing) {
            Button cancel = Ui.ghostButton(this, "Cancel test");
            cancel.setOnClickListener(v -> {
                if (runner != null) runner.cancel();
            });
            col.addView(cancel);
        } else {
            Button back = Ui.ghostButton(this, "Back to dashboard");
            back.setOnClickListener(v -> navTo("dashboard", null, true));
            col.addView(back);
        }
        return Ui.scrollWrap(this, col);
    }

    private void startTest() {
        if (testing) return;
        testing = true;
        navTo("progress", null, true);
        runner = new TestRunner();
        final List<ProbeResult> acc = new ArrayList<>();
        runner.run(this, enabled, new TestRunner.Listener() {
            @Override public void onPreflight(boolean internetAvailable) {
                noInternet = !internetAvailable;
            }
            @Override public void onNetworkStart(NetworkProbe network) {
                markNetworkRows(network, "initializing…");
            }
            @Override public void onFormatStart(NetworkProbe network, AdFormat format) {
                setRow(network.getId(), format, "loading…", Ui.TEXT_DIM);
            }
            @Override public void onFormatResult(ProbeResult result) {
                acc.add(result);
                setRow(result.networkId, result.format,
                        result.summary(), result.status.color);
            }
            @Override public void onNetworkDone(NetworkProbe network) {}
            @Override public void onAllDone(List<ProbeResult> all) {
                testing = false;
                lastResults = new ArrayList<>(all);
                grouped = TestRunner.groupByNetwork(lastResults);
                store.saveResults(lastResults);
                ReviewHelper.maybePrompt(MainActivity.this, lastResults);
                navTo("dashboard", null, true);
            }
        });
    }

    private void markNetworkRows(NetworkProbe n, String text) {
        for (AdFormat f : n.getFormats()) setRow(n.getId(), f, text, Ui.TEXT_DIM);
    }

    private void setRow(String netId, AdFormat f, String text, int color) {
        android.widget.TextView tv = progressRows.get(netId + "|" + f.name());
        if (tv != null) {
            tv.setText(text);
            tv.setTextColor(color);
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
        int color = Ui.TEXT_DIM;
        if (verdict.equals("Blocked")) color = Ui.DANGER;
        else if (verdict.equals("Ads loading")) color = Ui.OK;
        else if (verdict.equals("Partially blocked") || verdict.equals("Mixed")) color = Ui.WARN;
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
        android.widget.TextView name = Ui.title(this, r.format.label, 16);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);
        row.addView(Ui.statusPill(this, r.status.label, r.status.color));
        card.addView(row);
        card.addView(Ui.spacer(this, 6));
        card.addView(Ui.body(this, "Latency: " + r.latencyMs + " ms"));
        if (r.errorCode != null) {
            card.addView(Ui.mono(this, "Error code: " + r.errorCode));
        }
        if (r.errorMessage != null) {
            android.widget.TextView m = Ui.body(this, r.errorMessage);
            card.addView(m);
        }
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
        card.addView(Ui.body(this,
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
        android.widget.TextView t = Ui.body(this, text);
        t.setTextColor(Ui.TEXT);
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
        sdkCard.addView(Ui.title(this, "SDK versions", 16));
        sdkCard.addView(Ui.spacer(this, 6));
        for (NetworkProbe n : TestRunner.networks()) {
            LinearLayout row = Ui.hbox(this);
            android.widget.TextView name = Ui.bodyBright(this, n.getName());
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
        statCard.addView(Ui.title(this, "Run statistics", 16));
        statCard.addView(Ui.spacer(this, 6));
        statCard.addView(Ui.body(this, "Completed runs: " + store.getRunCount()));
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
            statCard.addView(Ui.body(this,
                    "Last run probes: " + lastResults.size()
                            + "\nLoaded: " + loaded + " · Blocked: " + blocked
                            + " · No fill: " + nofill + " · Other: " + errs
                            + "\nTotal probe time: " + totalMs + " ms"
                            + "\nLast run: " + fmtTime(store.getLastRunTimestamp())));
        } else {
            statCard.addView(Ui.body(this, "No runs yet."));
        }
        col.addView(statCard);
        col.addView(Ui.spacer(this, 12));

        LinearLayout errCard = Ui.card(this);
        errCard.addView(Ui.title(this, "Error-code reference", 16));
        errCard.addView(Ui.spacer(this, 6));
        errCard.addView(Ui.body(this, errorReferenceText()));
        col.addView(errCard);

        return Ui.scrollWrap(this, col);
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

    // ---------- static texts ----------

    private String privacyPolicyText() {
        return "PRIVACY POLICY — AdBlock Test: Ad Blocker Check\n"
                + "Developer: boofus productions (" + "bronzefloor66@gmail.com" + ")\n"
                + "Last updated: October 2026\n\n"
                + "WHAT THIS APP DOES\n"
                + "AdBlock Test checks whether an ad blocker on your device is working. When you "
                + "tap RUN TEST, the app initializes the ad SDKs of up to 7 ad networks "
                + "(AdMob, Unity Ads, ironSource, InMobi, Chartboost, Start.io, Liftoff) and "
                + "attempts to load one TEST ad per format (banner, interstitial, rewarded). "
                + "Ads are loaded only — they are never displayed and never clicked.\n\n"
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
                + "single ad load. The ad is never shown and never clicked. A 30-second "
                + "watchdog guards each probe. Banner views are attached to a hidden 1×1 "
                + "container because several SDKs require window attachment before loading.\n\n"
                + "4. CLASSIFICATION\n"
                + "• Loaded — a test ad arrived: this network is NOT blocked.\n"
                + "• Blocked — the SDK reported a network-layer failure (DNS/connection/TLS "
                + "failure, timeout, unreachable host) while the device has internet. This is "
                + "the ad-blocker signal: something on the device or network stopped the ad "
                + "request.\n"
                + "• No fill — the ad server was reached but returned no ad. This is not a "
                + "blocking signal.\n"
                + "• Init failed / Error — SDK or configuration problems, shown with the raw "
                + "SDK error code and message for diagnosis.\n"
                + "• Timed out — no SDK callback within 30 seconds.\n\n"
                + "Each network documents different error codes; the per-network mapping is "
                + "listed under Settings → Technical details. When an SDK gives no usable "
                + "error (Start.io), a test-mode failure with working internet is classified "
                + "as Blocked, because test ads should always fill.\n\n"
                + "5. VERDICT\n"
                + "A network reads \"Blocked\" when at least one format was blocked and none "
                + "loaded; \"Ads loading\" when every format loaded; otherwise the most "
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

    private String fmtTime(long ms) {
        if (ms <= 0) return "never";
        return new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(new Date(ms));
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }
}
