package com.example.arduinooscilloscope;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Professional Oscilloscope Display View
 * প্রফেশনাল Oscilloscope ডিসপ্লে
 */
public class ProfessionalOscilloscopeView extends View {
    
    private Paint gridPaint;
    private Paint majorGridPaint;
    private Paint waveformPaint;
    private Paint textPaint;
    private Paint backgroundPaint;
    
    private float[] waveformData;
    private Path waveformPath;
    
    private int width, height;
    private float voltageScale = 5.0f;
    private int gridDivisions = 10;
    private int triggerMode = 0; // 0=AUTO, 1=NORM, 2=SINGLE
    
    // Colors matching the screenshot
    private static final int BG_COLOR = Color.parseColor("#0A1E1E");
    private static final int GRID_COLOR = Color.parseColor("#1A3535");
    private static final int MAJOR_GRID_COLOR = Color.parseColor("#2A4545");
    private static final int WAVE_COLOR = Color.parseColor("#00FF88");
    private static final int TEXT_COLOR = Color.parseColor("#88FFAA");
    
    public ProfessionalOscilloscopeView(Context context) {
        super(context);
        init();
    }
    
    public ProfessionalOscilloscopeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        // Background
        backgroundPaint = new Paint();
        backgroundPaint.setColor(BG_COLOR);
        backgroundPaint.setStyle(Paint.Style.FILL);
        
        // Minor grid
        gridPaint = new Paint();
        gridPaint.setColor(GRID_COLOR);
        gridPaint.setStrokeWidth(1);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setAntiAlias(true);
        
        // Major grid
        majorGridPaint = new Paint();
        majorGridPaint.setColor(MAJOR_GRID_COLOR);
        majorGridPaint.setStrokeWidth(2);
        majorGridPaint.setStyle(Paint.Style.STROKE);
        majorGridPaint.setAntiAlias(true);
        
        // Waveform
        waveformPaint = new Paint();
        waveformPaint.setColor(WAVE_COLOR);
        waveformPaint.setStrokeWidth(3);
        waveformPaint.setStyle(Paint.Style.STROKE);
        waveformPaint.setAntiAlias(true);
        waveformPaint.setShadowLayer(10, 0, 0, WAVE_COLOR); // Glow effect
        
        // Text
        textPaint = new Paint();
        textPaint.setColor(TEXT_COLOR);
        textPaint.setTextSize(24);
        textPaint.setAntiAlias(true);
        
        waveformPath = new Path();
        setLayerType(LAYER_TYPE_SOFTWARE, null); // For shadow
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Draw background
        canvas.drawRect(0, 0, width, height, backgroundPaint);
        
        // Draw grid
        drawGrid(canvas);
        
        // Draw center lines
        drawCenterLines(canvas);
        
        // Draw voltage scale
        drawVoltageScale(canvas);
        
        // Draw waveform
        if (waveformData != null && waveformData.length > 0) {
            drawWaveform(canvas);
        }
        
        // Draw status info
        drawStatusInfo(canvas);
    }
    
    private void drawGrid(Canvas canvas) {
        float divX = width / (float) gridDivisions;
        float divY = height / (float) gridDivisions;
        
        // Vertical lines
        for (int i = 0; i <= gridDivisions; i++) {
            float x = i * divX;
            Paint paint = (i == gridDivisions / 2) ? majorGridPaint : gridPaint;
            canvas.drawLine(x, 0, x, height, paint);
            
            // Sub-divisions
            if (i < gridDivisions) {
                for (int j = 1; j < 5; j++) {
                    float subX = x + (divX / 5 * j);
                    canvas.drawLine(subX, 0, subX, height, gridPaint);
                }
            }
        }
        
        // Horizontal lines
        for (int i = 0; i <= gridDivisions; i++) {
            float y = i * divY;
            Paint paint = (i == gridDivisions / 2) ? majorGridPaint : gridPaint;
            canvas.drawLine(0, y, width, y, paint);
            
            // Sub-divisions
            if (i < gridDivisions) {
                for (int j = 1; j < 5; j++) {
                    float subY = y + (divY / 5 * j);
                    canvas.drawLine(0, subY, width, subY, gridPaint);
                }
            }
        }
    }
    
    private void drawCenterLines(Canvas canvas) {
        float centerX = width / 2.0f;
        float centerY = height / 2.0f;
        
        // Center cross marker
        Paint markerPaint = new Paint();
        markerPaint.setColor(Color.parseColor("#FF8844"));
        markerPaint.setStrokeWidth(3);
        
        float markerSize = 20;
        canvas.drawLine(centerX - markerSize, centerY, centerX + markerSize, centerY, markerPaint);
        canvas.drawLine(centerX, centerY - markerSize, centerX, centerY + markerSize, markerPaint);
    }
    
    private void drawVoltageScale(Canvas canvas) {
        float divY = height / (float) gridDivisions;
        
        textPaint.setTextSize(20);
        for (int i = 0; i <= gridDivisions; i++) {
            float y = i * divY;
            float voltage = voltageScale - (i * voltageScale / gridDivisions);
            String label = String.format("%.1f", voltage);
            canvas.drawText(label, 10, y + 20, textPaint);
        }
    }
    
    private void drawWaveform(Canvas canvas) {
        waveformPath.reset();
        
        int dataLength = waveformData.length;
        float xStep = width / (float) dataLength;
        
        // First point
        float firstY = voltageToY(waveformData[0]);
        waveformPath.moveTo(0, firstY);
        
        // Draw waveform
        for (int i = 1; i < dataLength; i++) {
            float x = i * xStep;
            float y = voltageToY(waveformData[i]);
            waveformPath.lineTo(x, y);
        }
        
        canvas.drawPath(waveformPath, waveformPaint);
    }
    
    private float voltageToY(float voltage) {
        float normalized = voltage / voltageScale;
        return height - (normalized * height);
    }
    
    private void drawStatusInfo(Canvas canvas) {
        textPaint.setTextSize(18);
        textPaint.setColor(Color.parseColor("#666666"));
        
        // Bottom left info
        canvas.drawText("0s", 10, height - 10, textPaint);
        
        // Bottom right info
        String info = waveformData != null ? 
            String.format("%.0f Sa/s  %d Sa", 10000.0, waveformData.length) : 
            "0 Sa/s";
        canvas.drawText(info, width - 200, height - 10, textPaint);
    }
    
    public void updateWaveform(float[] data) {
        this.waveformData = data;
        postInvalidate();
    }
    
    public void clearWaveform() {
        this.waveformData = null;
        postInvalidate();
    }
    
    public void setVoltageScale(float scale) {
        this.voltageScale = scale;
        postInvalidate();
    }
    
    public void setTriggerMode(int mode) {
        this.triggerMode = mode;
    }
}
