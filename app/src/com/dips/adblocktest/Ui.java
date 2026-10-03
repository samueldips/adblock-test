package com.dips.adblocktest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

/**
 * Programmatic Material-3-ish dark UI builders (no AndroidX dependency).
 * Design A — Security Command Center (SOC) theme palette.
 */
public final class Ui {
    private Ui() {}

    // ---- Design A Palette ----
    public static final int SOC_BG      = 0xFF0A0E14; // near-black
    public static final int SOC_SURFACE = 0xFF111827; // card background
    public static final int SOC_BORDER  = 0xFF1F2937; // card border

    // Accents (neon)
    public static final int NEON_GREEN = 0xFF00FF88; // blocked / OK / CTA
    public static final int NEON_RED   = 0xFFFF3355; // leaks / breached
    public static final int NEON_AMBER = 0xFFFFB800; // warnings
    public static final int NEON_CYAN  = 0xFF00D4FF; // info / radar

    // Text
    public static final int SOC_TEXT      = 0xFFE5E7EB;
    public static final int SOC_TEXT_DIM  = 0xFF6B7280;
    public static final int SOC_TEXT_MONO = 0xFF9CA3AF; // for monospace readouts

    // Backward-compatible aliases
    public static final int BG = SOC_BG;
    public static final int SURFACE = SOC_SURFACE;
    public static final int SURFACE2 = 0xFF1F2937;
    public static final int ACCENT = NEON_GREEN;
    public static final int ACCENT_DARK = 0xFF004D28;
    public static final int TEXT = SOC_TEXT;
    public static final int TEXT_DIM = SOC_TEXT_DIM;
    public static final int DANGER = NEON_RED;
    public static final int WARN = NEON_AMBER;
    public static final int OK = NEON_GREEN;
    public static final int CYAN = NEON_CYAN;

    public static int dp(Context c, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                c.getResources().getDisplayMetrics());
    }

    public static int sp(Context c, int sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                c.getResources().getDisplayMetrics());
    }

    public static GradientDrawable cardBg(int color) {
        return cardBg(color, SOC_BORDER, 12);
    }

    public static GradientDrawable cardBg(int color, int strokeColor, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp);
        d.setStroke(2, strokeColor);
        return d;
    }

    public static GradientDrawable glowCard(Context c, int borderColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(SOC_SURFACE);
        d.setStroke(dp(c, 2), borderColor);
        d.setCornerRadius(dp(c, 12));
        return d;
    }

    public static GradientDrawable pillBg(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(100);
        return d;
    }

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpw(int h) {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h);
    }

    public static TextView title(Context c, String text, int sizeSp) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(SOC_TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    public static TextView body(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(SOC_TEXT_DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setLineSpacing(dp(c, 4), 1.0f);
        return t;
    }

    public static TextView bodyBright(Context c, String text) {
        TextView t = body(c, text);
        t.setTextColor(SOC_TEXT);
        return t;
    }

    public static TextView mono(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(SOC_TEXT_MONO);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    public static Button primaryButton(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        b.setTextColor(0xFF0A0E14);
        GradientDrawable d = new GradientDrawable();
        d.setColor(NEON_GREEN);
        d.setCornerRadius(dp(c, 12));
        b.setBackground(d);
        int p = dp(c, 14);
        b.setPadding(p, p, p, p);
        return b;
    }

    public static Button ghostButton(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setTypeface(Typeface.MONOSPACE);
        b.setTextColor(NEON_GREEN);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x00000000);
        d.setCornerRadius(dp(c, 8));
        d.setStroke(dp(c, 1), 0xFF004D28);
        b.setBackground(d);
        int p = dp(c, 10);
        b.setPadding(p, p, p, p);
        return b;
    }

    public static TextView statusPill(Context c, String text, int color) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(0xFF0A0E14);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setBackground(pillBg(color));
        int hp = dp(c, 8), vp = dp(c, 3);
        t.setPadding(hp, vp, hp, vp);
        return t;
    }

    public static TextView badgePill(Context c, String text, int bgColor, int textColor) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(textColor);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setTypeface(Typeface.MONOSPACE);
        t.setBackground(pillBg(bgColor));
        int hp = dp(c, 8), vp = dp(c, 3);
        t.setPadding(hp, vp, hp, vp);
        return t;
    }

    public static TextView chipPill(Context c, String text, boolean selected) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTypeface(Typeface.create("sans-serif-medium", selected ? Typeface.BOLD : Typeface.NORMAL));
        t.setTextColor(selected ? NEON_GREEN : SOC_TEXT_DIM);

        GradientDrawable d = new GradientDrawable();
        d.setColor(selected ? SURFACE2 : SOC_SURFACE);
        d.setCornerRadius(dp(c, 8));
        d.setStroke(dp(c, 1), selected ? ACCENT_DARK : SOC_BORDER);
        t.setBackground(d);

        int hp = dp(c, 10), vp = dp(c, 5);
        t.setPadding(hp, vp, hp, vp);
        return t;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(SOC_BORDER);
        v.setLayoutParams(lpw(dp(c, 1)));
        return v;
    }

    public static Space spacer(Context c, int dpH) {
        Space s = new Space(c);
        s.setLayoutParams(lpw(dp(c, dpH)));
        return s;
    }

    public static ScrollView scrollWrap(Context c, View content) {
        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        sv.addView(content);
        return sv;
    }

    public static CheckBox checkBox(Context c, String text, boolean checked) {
        CheckBox cb = new CheckBox(c);
        cb.setText(text);
        cb.setChecked(checked);
        cb.setTextColor(SOC_TEXT);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        return cb;
    }

    /** Card container with padding. */
    public static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        l.setBackground(cardBg(SOC_SURFACE));
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static void showBannerDialog(Activity activity, String title, View bannerView) {
        if (activity == null || bannerView == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Ad Preview: " + title);

        if (bannerView.getParent() instanceof ViewGroup) {
            ((ViewGroup) bannerView.getParent()).removeView(bannerView);
        }

        FrameLayout container = new FrameLayout(activity);
        int p = dp(activity, 16);
        container.setPadding(p, p, p, p);

        // Explicit standard banner dimensions (320x50 dp) so SDK views assign layout bounds correctly
        int bWidth = dp(activity, 320);
        int bHeight = dp(activity, 50);

        bannerView.setVisibility(View.VISIBLE);
        bannerView.setMinimumWidth(bWidth);
        bannerView.setMinimumHeight(bHeight);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(bWidth, bHeight, Gravity.CENTER);
        container.addView(bannerView, lp);

        bannerView.requestLayout();
        bannerView.invalidate();

        builder.setView(container);
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
}
