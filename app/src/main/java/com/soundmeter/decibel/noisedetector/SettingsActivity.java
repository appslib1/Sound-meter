package com.soundmeter.decibel.noisedetector;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

public class SettingsActivity extends AppCompatActivity {

    private static final String PLAY_STORE_WEB =
            "https://play.google.com/store/apps/details?id=";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Insets4.applyToPadding(findViewById(R.id.settingsRoot));

        try {
            AdView adView = findViewById(R.id.adView);
            adView.loadAd(new AdRequest.Builder().build());
        } catch (Exception ignored) {}

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        bindDisplay();
        bindCalibration();
        bindShare();
    }

    // ================== Display ==================

    private void bindDisplay() {
        MaterialSwitch keepScreenOn = findViewById(R.id.switchKeepScreenOn);
        keepScreenOn.setChecked(AppPrefs.isKeepScreenOn(this));
        keepScreenOn.setOnCheckedChangeListener((btn, checked) ->
                AppPrefs.setKeepScreenOn(this, checked));

        MaterialSwitch showRef = findViewById(R.id.switchShowReference);
        showRef.setChecked(AppPrefs.isShowReference(this));
        showRef.setOnCheckedChangeListener((btn, checked) ->
                AppPrefs.setShowReference(this, checked));

        MaterialButtonToggleGroup unitGroup = findViewById(R.id.toggleUnit);
        unitGroup.check(AppPrefs.getUnit(this) == AppPrefs.Unit.DB
                ? R.id.unitDb : R.id.unitB);
        unitGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            AppPrefs.setUnit(this,
                    checkedId == R.id.unitDb ? AppPrefs.Unit.DB : AppPrefs.Unit.B);
        });

        MaterialButtonToggleGroup themeGroup = findViewById(R.id.toggleTheme);
        themeGroup.check(themeButtonId(AppPrefs.getTheme(this)));
        themeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            AppPrefs.setTheme(this, themeFromButtonId(checkedId));
            // Recreate to redraw everything with the new palette.
            recreate();
        });
    }

    private int themeButtonId(AppPrefs.Theme t) {
        switch (t) {
            case LIGHT:  return R.id.themeLight;
            case DARK:   return R.id.themeDark;
            case SYSTEM:
            default:     return R.id.themeSystem;
        }
    }

    private AppPrefs.Theme themeFromButtonId(int id) {
        if (id == R.id.themeLight) return AppPrefs.Theme.LIGHT;
        if (id == R.id.themeDark)  return AppPrefs.Theme.DARK;
        return AppPrefs.Theme.SYSTEM;
    }

    // ================== Calibration ==================

    private void bindCalibration() {
        Slider slider = findViewById(R.id.calibrationSlider);
        TextView value = findViewById(R.id.calibrationValue);
        Button reset = findViewById(R.id.btnResetCalibration);

        int initial = AppPrefs.getCalibrationDelta(this);
        slider.setValueFrom(AppPrefs.CAL_MIN_DELTA);
        slider.setValueTo(AppPrefs.CAL_MAX_DELTA);
        slider.setStepSize(1f);
        slider.setValue(initial);
        value.setText(formatCalibration(initial));

        slider.addOnChangeListener((s, v, fromUser) -> {
            int delta = Math.round(v);
            value.setText(formatCalibration(delta));
            if (fromUser) {
                AppPrefs.setCalibrationDelta(this, delta);
            }
        });

        reset.setOnClickListener(v -> {
            slider.setValue(0);
            AppPrefs.setCalibrationDelta(this, 0);
        });
    }

    private String formatCalibration(int delta) {
        if (delta == 0) return getString(R.string.settings_calibration_value_zero);
        if (delta > 0) return getString(R.string.settings_calibration_value_positive, delta);
        return getString(R.string.settings_calibration_value_negative, delta);
    }

    // ================== Share / Rate ==================

    private void bindShare() {
        View rate = findViewById(R.id.rowRate);
        View share = findViewById(R.id.rowShare);

        rate.setOnClickListener(v -> new RatingModal(this).openRatingDialog());
        share.setOnClickListener(v -> shareApp());
    }

    private void shareApp() {
        String link = PLAY_STORE_WEB + getPackageName();
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject));
        send.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text, link));
        startActivity(Intent.createChooser(send, getString(R.string.settings_share)));
    }
}
