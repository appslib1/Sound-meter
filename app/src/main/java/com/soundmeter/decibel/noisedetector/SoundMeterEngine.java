package com.soundmeter.decibel.noisedetector;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

public final class SoundMeterEngine {

    public interface Listener {
        void onLevel(double dbSpl);
        void onError(String message);
    }

    // Chosen to be widely supported; falls back to 44100 if 48000 refused.
    private static final int PREFERRED_SAMPLE_RATE = 48000;
    private static final int FALLBACK_SAMPLE_RATE = 44100;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    // ~100 ms measurement window ("fast" IEC 61672 time weighting is 125 ms — close enough for MVP).
    private static final int WINDOW_MS = 100;

    // 16-bit PCM peak.
    private static final double FULL_SCALE = 32768.0;

    // Default calibration offset in dB. Applied as: dB_SPL = dBFS + offset.
    // Rationale: a typical Android mic reads ~ -60 dBFS in a quiet room (~40 dB SPL), so
    // offset ~= 100 lines up. Real per-device values range roughly 85..110 — the Settings
    // screen will let the user tune it. Ship with a sane default so day-1 readings are usable
    // (per-device calibration is a top-3 pain point in competitor reviews; no default = broken).
    private static final double DEFAULT_CALIBRATION_OFFSET = 100.0;

    // Clamp displayed value to a plausible SPL range.
    private static final double MIN_DB = 20.0;
    private static final double MAX_DB = 140.0;

    private final Listener listener;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private HandlerThread audioThread;
    private Handler audioHandler;
    private AudioRecord recorder;
    private volatile boolean running;
    private double calibrationOffset = DEFAULT_CALIBRATION_OFFSET;

    public SoundMeterEngine(Listener listener) {
        this.listener = listener;
    }

    public void setCalibrationOffset(double offset) {
        this.calibrationOffset = offset;
    }

    public boolean isRunning() {
        return running;
    }

    @SuppressLint("MissingPermission")
    public void start() {
        if (running) return;

        int sampleRate = PREFERRED_SAMPLE_RATE;
        int minBuf = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING);
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            sampleRate = FALLBACK_SAMPLE_RATE;
            minBuf = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING);
            if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
                postError("No supported audio configuration on this device.");
                return;
            }
        }

        int windowSamples = Math.max(sampleRate * WINDOW_MS / 1000, minBuf / 2);
        // Recorder buffer sized to at least 4 windows to absorb scheduler jitter.
        int recorderBuf = Math.max(minBuf, windowSamples * 4 * 2);

        AudioRecord ar;
        try {
            ar = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    CHANNEL,
                    ENCODING,
                    recorderBuf);
        } catch (SecurityException e) {
            postError("Microphone permission not granted.");
            return;
        } catch (IllegalArgumentException e) {
            postError("Failed to configure audio recorder.");
            return;
        }

        if (ar.getState() != AudioRecord.STATE_INITIALIZED) {
            ar.release();
            postError("Audio recorder failed to initialize.");
            return;
        }

        this.recorder = ar;
        this.running = true;

        audioThread = new HandlerThread("SoundMeter-Audio", Thread.MIN_PRIORITY + 3);
        audioThread.start();
        audioHandler = new Handler(audioThread.getLooper());

        final int loopWindowSamples = windowSamples;
        audioHandler.post(() -> readLoop(loopWindowSamples));
    }

    private void readLoop(int windowSamples) {
        try {
            recorder.startRecording();
        } catch (IllegalStateException e) {
            postError("Failed to start audio recorder.");
            return;
        }

        short[] buffer = new short[windowSamples];
        while (running) {
            int read = recorder.read(buffer, 0, buffer.length);
            if (read <= 0) {
                if (read == AudioRecord.ERROR_INVALID_OPERATION
                        || read == AudioRecord.ERROR_BAD_VALUE) {
                    postError("Audio read failed.");
                    break;
                }
                continue;
            }

            // RMS over the window in linear amplitude (0..1 relative to full scale).
            double sumSquares = 0.0;
            for (int i = 0; i < read; i++) {
                double s = buffer[i] / FULL_SCALE;
                sumSquares += s * s;
            }
            double rms = Math.sqrt(sumSquares / read);

            // Guard against log(0) when the mic is fully silent (very rare).
            if (rms < 1e-9) rms = 1e-9;

            double dbfs = 20.0 * Math.log10(rms);      // <= 0, with 0 = full scale
            double dbSpl = dbfs + calibrationOffset;
            if (dbSpl < MIN_DB) dbSpl = MIN_DB;
            if (dbSpl > MAX_DB) dbSpl = MAX_DB;

            postLevel(dbSpl);
        }
    }

    public void stop() {
        if (!running) return;
        running = false;

        // Move teardown off the UI thread — AudioRecord.stop/release can block briefly.
        if (audioHandler != null) {
            audioHandler.post(this::releaseRecorder);
        } else {
            releaseRecorder();
        }

        if (audioThread != null) {
            audioThread.quitSafely();
            audioThread = null;
        }
        audioHandler = null;
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try {
                if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop();
                }
            } catch (IllegalStateException ignored) {
            }
            recorder.release();
            recorder = null;
        }
    }

    private void postLevel(double dbSpl) {
        uiHandler.post(() -> listener.onLevel(dbSpl));
    }

    private void postError(String message) {
        running = false;
        uiHandler.post(() -> listener.onError(message));
    }
}
