package com.soundmeter.decibel.noisedetector;

import android.app.Application;

import com.google.android.gms.ads.MobileAds;

public class SoundMeterApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Apply the user's chosen theme once at process start, so every activity
        // is created with the right night-mode from the very first frame.
        AppPrefs.applySavedTheme(this);

        MobileAds.initialize(this, status -> {});
    }
}
