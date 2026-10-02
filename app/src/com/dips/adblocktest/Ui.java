package com.dips.adblocktest;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

/**
 * Programmatic Material-3-ish dark UI builders (no AndroidX dependency).
 * Palette: deep navy background, teal/green accent, shield motif via text glyph.
 */
public final class Ui {
    private Ui() {}

    public static final int BG = 0xFF0B1220;
    public static final int SURFACE = 0xFF16202F;
    public static final int SURFACE2 = 0xFF1E2C42;
    public static final int ACCENT = 0xFF4DD0A6;
    public static final int ACCENT_DARK = 0xFF1B5E4B;
    public static final int TEXT = 0xFFE8EEF4;
    public static final int TEXT_DIM = 0xFF9AA8BC;
    public static final int DANGER = 0xFFFF5252;
    public static final int WARN = 0xFFFFB74D;
    public static final int OK = 0xFF4CAF50;

    public static int dp(Context c, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                c.getResources().getDisplayMetrics());
    }

    public static int sp(Context c, int sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                c.getResources().getDisplayMetrics());
    }

    public static GradientDrawable cardBg(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(28);
        d.setStroke(2, 0xFF24344D);
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
        t.setTextColor(TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static TextView body(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(TEXT_DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setLineSpacing(dp(c, 4), 1.0f);
        return t;
    }

    public static TextView bodyBright(Context c, String text) {
        TextView t = body(c, text);
        t.setTextColor(TEXT);
        return t;
    }

    public static TextView mono(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(TEXT_DIM);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    public static Button primaryButton(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(0xFF06281E);
        GradientDrawable d = new GradientDrawable();
        d.setColor(ACCENT);
        d.setCornerRadius(100);
        b.setBackground(d);
        int p = dp(c, 16);
        b.setPadding(p, p, p, p);
        return b;
    }

    public static Button ghostButton(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTextColor(ACCENT);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x00000000);
        d.setCornerRadius(100);
        d.setStroke(2, ACCENT_DARK);
        b.setBackground(d);
        int p = dp(c, 12);
        b.setPadding(p, p, p, p);
        return b;
    }

    public static TextView statusPill(Context c, String text, int color) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(0xFF0B1220);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setBackground(pillBg(color));
        int hp = dp(c, 10), vp = dp(c, 4);
        t.setPadding(hp, vp, hp, vp);
        return t;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(0xFF24344D);
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
        cb.setTextColor(TEXT);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        return cb;
    }

    /** Card container with padding. */
    public static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        l.setBackground(cardBg(SURFACE));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }
}
