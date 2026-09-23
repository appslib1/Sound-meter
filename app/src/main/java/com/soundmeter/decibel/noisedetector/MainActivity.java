package com.soundmeter.decibel.noisedetector;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SoundMeterEngine.Listener {

    private static final int CHART_WINDOW_POINTS = 600; // ~60 s at 100 ms per sample

    private ArcGaugeView gauge;
    private TextView dbValue, dbUnitLabel;
    private TextView statMinValue, statAvgValue, statMaxValue;
    private ImageButton btnPauseResume, btnReset, btnSettings, btnReference;
    private LineChart chart;
    private LineDataSet dataSet;
    private LineData lineData;

    private SoundMeterEngine engine;
    private boolean paused = false;

    // All internal state is kept in dB. Display converts using currentUnit.factor.
    private double minDb = Double.POSITIVE_INFINITY;
    private double maxDb = Double.NEGATIVE_INFINITY;
    private double sumDb = 0.0;
    private long sampleCount = 0;
    private int xIndex = 0;
    private double lastDb = Double.NaN;

    private AppPrefs.Unit currentUnit = AppPrefs.Unit.DB;

    private final ActivityResultLauncher<String> micPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startMeter();
                } else {
                    // Show as paused; tapping play will re-request the permission.
                    paused = true;
                    showPlayIcon();
                    Toast.makeText(this, R.string.perm_mic_denied, Toast.LENGTH_LONG).show();
                }
            });

    private void showPlayIcon() {
        btnPauseResume.setImageResource(R.drawable.ic_play);
        btnPauseResume.setContentDescription(getString(R.string.btn_resume));
    }

    private void showPauseIcon() {
        btnPauseResume.setImageResource(R.drawable.ic_pause);
        btnPauseResume.setContentDescription(getString(R.string.btn_pause));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        Insets4.applyToPadding(findViewById(R.id.main));

        try {
            AdView adView = findViewById(R.id.adView);
            adView.loadAd(new AdRequest.Builder().build());
        } catch (Exception ignored) {}

        gauge = findViewById(R.id.gauge);
        dbValue = findViewById(R.id.dbValue);
        dbUnitLabel = findViewById(R.id.dbUnit);
        chart = findViewById(R.id.chart);
        btnPauseResume = findViewById(R.id.btnPauseResume);
        btnReset = findViewById(R.id.btnReset);
        btnSettings = findViewById(R.id.btnSettings);
        btnReference = findViewById(R.id.btnReference);

        View minInclude = findViewById(R.id.statMin);
        View avgInclude = findViewById(R.id.statAvg);
        View maxInclude = findViewById(R.id.statMax);
        ((TextView) minInclude.findViewById(R.id.statLabel)).setText(R.string.stat_min);
        ((TextView) avgInclude.findViewById(R.id.statLabel)).setText(R.string.stat_avg);
        ((TextView) maxInclude.findViewById(R.id.statLabel)).setText(R.string.stat_max);
        statMinValue = minInclude.findViewById(R.id.statValue);
        statAvgValue = avgInclude.findViewById(R.id.statValue);
        statMaxValue = maxInclude.findViewById(R.id.statValue);

        setupChart();

        btnPauseResume.setOnClickListener(v -> togglePause());
        btnReset.setOnClickListener(v -> resetStats());
        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        btnReference.setOnClickListener(v ->
                SoundReferenceDialog.newInstance(lastDb, currentUnit)
                        .show(getSupportFragmentManager(), "reference"));

        engine = new SoundMeterEngine(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Refresh every pref that can be changed in Settings.
        applyKeepScreenOn(AppPrefs.isKeepScreenOn(this));
        btnReference.setVisibility(AppPrefs.isShowReference(this) ? View.VISIBLE : View.GONE);
        applyUnitIfChanged(AppPrefs.getUnit(this));

        if (!paused) {
            ensurePermissionAndStart();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        engine.stop();
    }

    // ================== pref-driven behavior ==================

    private void applyKeepScreenOn(boolean on) {
        if (on) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void applyUnitIfChanged(AppPrefs.Unit unit) {
        if (unit == currentUnit) return;
        currentUnit = unit;
        dbUnitLabel.setText(unit.label);

        // Y-axis range and history become inconsistent when the unit changes mid-flight.
        // Clearing keeps the chart honest — otherwise the line would show two different scales.
        clearChartOnly();
        rerenderCurrentStats();
        if (dataSet != null) {
            dataSet.setLabel(unit == AppPrefs.Unit.DB ? "dB" : "B");
        }
        applyChartYAxisForUnit();
    }

    private void applyChartYAxisForUnit() {
        YAxis left = chart.getAxisLeft();
        if (currentUnit == AppPrefs.Unit.DB) {
            left.setAxisMinimum(20f);
            left.setAxisMaximum(130f);
            left.setGranularity(20f);
        } else {
            left.setAxisMinimum(2f);
            left.setAxisMaximum(13f);
            left.setGranularity(2f);
        }
        chart.invalidate();
    }

    private void clearChartOnly() {
        xIndex = 0;
        if (dataSet != null) {
            dataSet.clear();
            lineData.notifyDataChanged();
            chart.notifyDataSetChanged();
            chart.invalidate();
        }
    }

    private void rerenderCurrentStats() {
        if (sampleCount == 0) return;
        statMinValue.setText(fmt(minDb));
        statMaxValue.setText(fmt(maxDb));
        statAvgValue.setText(fmt(sumDb / sampleCount));
    }

    // ================== permission + engine control ==================

    private void ensurePermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startMeter();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        } else {
            // Pre-M devices grant the permission at install time; if we still see DENIED here,
            // the user disabled it in system settings — nothing we can do without leaving the app.
            paused = true;
            showPlayIcon();
        }
    }

    private void startMeter() {
        engine.start();
        showPauseIcon();
    }

    private void togglePause() {
        if (engine.isRunning()) {
            paused = true;
            engine.stop();
            showPlayIcon();
        } else {
            paused = false;
            ensurePermissionAndStart();
        }
    }

    private void resetStats() {
        minDb = Double.POSITIVE_INFINITY;
        maxDb = Double.NEGATIVE_INFINITY;
        sumDb = 0.0;
        sampleCount = 0;
        statMinValue.setText("—");
        statAvgValue.setText("—");
        statMaxValue.setText("—");
        clearChartOnly();
    }

    // ================== SoundMeterEngine.Listener ==================

    @Override
    public void onLevel(double dbSpl) {
        lastDb = dbSpl;

        double displayed = dbSpl * currentUnit.factor;
        dbValue.setText(fmt(dbSpl));
        gauge.setDb(dbSpl);

        int levelColor = colorForDb(dbSpl);
        gauge.setLevelColor(levelColor);
        dbValue.setTextColor(levelColor);

        if (dbSpl < minDb) minDb = dbSpl;
        if (dbSpl > maxDb) maxDb = dbSpl;
        sumDb += dbSpl;
        sampleCount++;

        statMinValue.setText(fmt(minDb));
        statMaxValue.setText(fmt(maxDb));
        statAvgValue.setText(fmt(sumDb / sampleCount));

        dataSet.addEntry(new Entry(xIndex++, (float) displayed));
        if (dataSet.getEntryCount() > CHART_WINDOW_POINTS) {
            dataSet.removeFirst();
        }
        lineData.notifyDataChanged();
        chart.notifyDataSetChanged();
        chart.setVisibleXRangeMaximum(CHART_WINDOW_POINTS);
        chart.moveViewToX(xIndex);
    }

    @Override
    public void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        paused = true;
        showPlayIcon();
    }

    // ================== helpers ==================

    private String fmt(double dbValue) {
        double v = dbValue * currentUnit.factor;
        return currentUnit == AppPrefs.Unit.DB
                ? String.format(Locale.US, "%.0f", v)
                : String.format(Locale.US, "%.1f", v);
    }

    private int colorForDb(double db) {
        int colorRes;
        if (db < 60) colorRes = R.color.level_low;
        else if (db < 80) colorRes = R.color.level_moderate;
        else if (db < 100) colorRes = R.color.level_high;
        else colorRes = R.color.level_extreme;
        return ContextCompat.getColor(this, colorRes);
    }

    private void setupChart() {
        Description desc = new Description();
        desc.setText("");
        chart.setDescription(desc);
        chart.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_card));
        chart.setDrawGridBackground(false);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setPinchZoom(false);

        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setDrawAxisLine(false);
        x.setDrawLabels(false);

        YAxis left = chart.getAxisLeft();
        left.setAxisMinimum(20f);
        left.setAxisMaximum(130f);
        left.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary));
        left.setGridColor(ContextCompat.getColor(this, R.color.chart_grid));
        left.setDrawAxisLine(false);
        left.setGranularity(20f);

        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setForm(Legend.LegendForm.NONE);

        dataSet = new LineDataSet(new ArrayList<>(), "dB");
        dataSet.setColor(ContextCompat.getColor(this, R.color.chart_line));
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(ContextCompat.getColor(this, R.color.chart_line));
        dataSet.setFillAlpha(50);

        lineData = new LineData(dataSet);
        chart.setData(lineData);
    }
}
