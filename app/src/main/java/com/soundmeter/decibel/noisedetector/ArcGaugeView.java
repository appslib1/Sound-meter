package com.soundmeter.decibel.noisedetector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * 240-degree arc gauge, drawn from bottom-left around the top to bottom-right.
 * Mirrors the classic SPL-meter dial style used by top-ranked competitor apps.
 */
public class ArcGaugeView extends View {

    private static final float START_ANGLE = 150f;   // starts bottom-left
    private static final float SWEEP_ANGLE = 240f;   // ends bottom-right
    private static final float STROKE_DP = 14f;

    private static final double MIN_DB = 30.0;
    private static final double MAX_DB = 130.0;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private float progress;   // 0..1
    private int levelColor;

    public ArcGaugeView(Context context) {
        this(context, null);
    }

    public ArcGaugeView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ArcGaugeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        float stroke = STROKE_DP * getResources().getDisplayMetrics().density;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeWidth(stroke);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.bg_card_stroke));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(stroke);
        levelColor = ContextCompat.getColor(context, R.color.level_low);
        progressPaint.setColor(levelColor);
    }

    public void setDb(double db) {
        double clamped = Math.max(MIN_DB, Math.min(MAX_DB, db));
        this.progress = (float) ((clamped - MIN_DB) / (MAX_DB - MIN_DB));
        invalidate();
    }

    public void setLevelColor(int color) {
        this.levelColor = color;
        this.progressPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float pad = progressPaint.getStrokeWidth() / 2f + getPaddingLeft();
        arcRect.set(pad, pad, w - pad, h - pad);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE, false, trackPaint);
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE * progress, false, progressPaint);
    }
}
