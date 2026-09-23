package com.soundmeter.decibel.noisedetector;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Applies safe-area padding for system bars AND display cutouts on all four edges.
 * Reused by every activity's root view. Preserves the view's original padding.
 */
final class Insets4 {

    private Insets4() {}

    static void applyToPadding(View root) {
        final int origL = root.getPaddingLeft();
        final int origT = root.getPaddingTop();
        final int origR = root.getPaddingRight();
        final int origB = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int mask = WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout();
            Insets i = insets.getInsets(mask);
            v.setPadding(origL + i.left, origT + i.top, origR + i.right, origB + i.bottom);
            return insets;
        });
    }
}
