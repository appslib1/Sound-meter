package com.soundmeter.decibel.noisedetector;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class AppPrefs {

    private static final String FILE = "sound_meter_prefs";

    private static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    private static final String KEY_UNIT = "unit";
    private static final String KEY_SHOW_REFERENCE = "show_reference_btn";
    private static final String KEY_THEME = "theme";
    private static final String KEY_CAL_DELTA = "cal_offset_delta";
    private static final String KEY_HAS_ASKED_MIC = "has_asked_mic";

    // Calibration is applied to the engine as `100 + delta` dB. Storing the delta
    // (instead of the absolute offset) keeps the "Reset" affordance trivial and the
    // slider range user-friendly.
    public static final int CAL_MIN_DELTA = -20;
    public static final int CAL_MAX_DELTA = 20;
    private static final double CAL_BASE = 100.0;

    public enum Unit {
        DB("dB SPL", 1.0),
        B("B SPL", 0.1);

        public final String label;
        public final double factor;

        Unit(String label, double factor) {
            this.label = label;
            this.factor = factor;
        }
    }

    public enum Theme {
        SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES);

        public final int nightMode;
        Theme(int nightMode) { this.nightMode = nightMode; }
    }

    private AppPrefs() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ================== Keep screen on ==================

    public static boolean isKeepScreenOn(Context ctx) {
        return prefs(ctx).getBoolean(KEY_KEEP_SCREEN_ON, true);
    }

    public static void setKeepScreenOn(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply();
    }

    // ================== Unit ==================

    public static Unit getUnit(Context ctx) {
        String name = prefs(ctx).getString(KEY_UNIT, Unit.DB.name());
        try {
            return Unit.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Unit.DB;
        }
    }

    public static void setUnit(Context ctx, Unit unit) {
        prefs(ctx).edit().putString(KEY_UNIT, unit.name()).apply();
    }

    // ================== Show reference button ==================

    public static boolean isShowReference(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SHOW_REFERENCE, true);
    }

    public static void setShowReference(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean(KEY_SHOW_REFERENCE, value).apply();
    }

    // ================== Theme ==================

    public static Theme getTheme(Context ctx) {
        // Default to DARK on first install — matches the meter's identity and avoids a
        // bright flash when the app launches in a low-light environment.
        String name = prefs(ctx).getString(KEY_THEME, Theme.DARK.name());
        try {
            return Theme.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Theme.SYSTEM;
        }
    }

    public static void setTheme(Context ctx, Theme theme) {
        prefs(ctx).edit().putString(KEY_THEME, theme.name()).apply();
        AppCompatDelegate.setDefaultNightMode(theme.nightMode);
    }

    /** Apply saved theme without persisting — call at Application/Activity boot. */
    public static void applySavedTheme(Context ctx) {
        AppCompatDelegate.setDefaultNightMode(getTheme(ctx).nightMode);
    }

    // ================== Calibration ==================

    public static int getCalibrationDelta(Context ctx) {
        int d = prefs(ctx).getInt(KEY_CAL_DELTA, 0);
        if (d < CAL_MIN_DELTA) return CAL_MIN_DELTA;
        if (d > CAL_MAX_DELTA) return CAL_MAX_DELTA;
        return d;
    }

    public static void setCalibrationDelta(Context ctx, int delta) {
        if (delta < CAL_MIN_DELTA) delta = CAL_MIN_DELTA;
        if (delta > CAL_MAX_DELTA) delta = CAL_MAX_DELTA;
        prefs(ctx).edit().putInt(KEY_CAL_DELTA, delta).apply();
    }

    public static double getEffectiveCalibrationOffset(Context ctx) {
        return CAL_BASE + getCalibrationDelta(ctx);
    }

    // ================== Permission ask tracking ==================

    public static boolean hasAskedMic(Context ctx) {
        return prefs(ctx).getBoolean(KEY_HAS_ASKED_MIC, false);
    }

    public static void setHasAskedMic(Context ctx, boolean value) {
        prefs(ctx).edit().putBoolean(KEY_HAS_ASKED_MIC, value).apply();
    }
}
