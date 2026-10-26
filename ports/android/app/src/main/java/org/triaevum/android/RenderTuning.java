package org.triaevum.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Runtime rendering options that are applied without restarting the game.
 * Currently: how strictly the 60 FPS+ frame interpolation pairs draws between
 * two game frames (stricter = fewer flickering or sliding objects, but less
 * smooth where the game cannot be matched reliably).
 */
public final class RenderTuning {
    private static final String TAG = "TriAevum";
    private static final String PREFS_NAME = "org.triaevum.android_preferences";
    public static final String PREF_KEY = "EmulationMenuSettings_InterpStrictness";
    public static final int DEFAULT_LEVEL = 1;
    public static final String[] LABELS = {
        "Normal (smoothest)",
        "Strict (fewer artifacts)",
        "Strictest (no guessing)"
    };

    public static native void nativeSetInterpolationStrictness(int level);

    private RenderTuning() {
    }

    public static int readLevel(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int level = prefs.getInt(PREF_KEY, DEFAULT_LEVEL);
        return Math.max(0, Math.min(LABELS.length - 1, level));
    }

    /** Pushes the saved level to the native runtime. */
    public static void applyFromPrefs(Context context) {
        try {
            nativeSetInterpolationStrictness(readLevel(context));
        } catch (UnsatisfiedLinkError error) {
            Log.w(TAG, "Native runtime has no interpolation option", error);
        }
    }
}
