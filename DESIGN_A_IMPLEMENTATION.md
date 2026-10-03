# Design A — "Security Command Center" Implementation Guide

For AdBlock Test dashboard (`MainActivity.dashboardScreen()`).
Matches the Stitch design at https://stitch.withgoogle.com/projects/6972905634760586541

## Color Palette

Add these to your `Ui.java` or use directly:

```java
// Backgrounds
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
```

## Typography

- Headers: bold, 20-24sp, `SOC_TEXT`
- Data readouts: **monospace** (`Typeface.MONOSPACE`), 12-14sp, `SOC_TEXT_MONO`
- Labels: 12sp, uppercase, letter-spaced, `SOC_TEXT_DIM`
- Verdict: bold, 28sp

```java
TextView mono = new TextView(this);
mono.setTypeface(Typeface.MONOSPACE);
mono.setTextSize(13);
mono.setTextColor(Ui.SOC_TEXT_MONO);
```

## Dashboard Layout (top to bottom)

### 1. Header strip
```
┌─────────────────────────────────┐
│ RADAR: Active Defense Subsystem │  ← monospace, cyan, 12sp
│ ▓▓▓▓▓▓▓░░░ 2 BREACHES          │  ← red badge if leaks > 0
└─────────────────────────────────┘
```

### 2. Verdict card (glowing border)
- Border color: `NEON_GREEN` if protected, `NEON_RED` if leaks
- Add glow via `GradientDrawable` with stroke width 2dp
- Inside:
  - Large verdict: "LEAKS DETECTED" (red) or "SECURE" (green), 28sp bold
  - Monospace subline: `SINKHOLE INTEGRITY 71.4% [COMPROMISED]`
  - Monospace subline: `FILTER RULES: 142,100 ACTIVE`

### 3. Telemetry stat panels (3 columns)
Each panel: surface card, monospace label on top, large number below
```
┌────────┬────────┬────────┐
│BLOCKED │ LEAKED │AUDITED │
│  5/7   │   2    │  2m    │  ← 24sp bold, color-coded
└────────┴────────┴────────┘
```

### 4. CTA button
- Full width, 56dp height
- Background: `NEON_GREEN` (or dark green with green border for secondary)
- Text: "RUN FULL AUDIT" (bold, black text on green)
- Subtext: "inject synthetic ad probes (7 networks)" (monospace, 11sp)

### 5. Network probes list
Header: `NETWORK PROBES (7)` monospace + `2 FAILED / 5 PASSED` color-coded

Each row:
```
┌─────────────────────────────────┐
│ ● Google AdMob        [LEAKED]  │  ← dot: green/red, pill: status
│   3/3 probes bypassed           │  ← monospace dim, 12sp
└─────────────────────────────────┘
```

Status pills:
- `PASSED` (blocked): green pill, dark green text
- `FAILED` (leaked): red pill, dark red text
- `ERROR`: amber pill

## Glow effect helper

```java
public static GradientDrawable glowCard(int borderColor) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(SOC_SURFACE);
    d.setStroke(dp(2), borderColor);
    d.setCornerRadius(dp(12));
    return d;
}
```

## Bottom nav (4 tabs)

Keep existing: Home, Networks, Test, Settings
- Active tab: `NEON_GREEN` icon + label
- Inactive: `SOC_TEXT_DIM`

## Notes

- All data readouts use monospace for the terminal/SOC feel
- Labels are UPPERCASE with wide letter spacing
- Cards have subtle borders (`SOC_BORDER`) instead of shadows
- The verdict card is the only element with a glowing colored border
- Keep the existing `TestRunner`, `ProbeResult`, and toggle logic — only the
  visual presentation changes
