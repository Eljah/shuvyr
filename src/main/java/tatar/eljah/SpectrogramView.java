package tatar.eljah;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpectrogramView extends View {
    private static final float MAX_SPECTROGRAM_HZ = 4000f;
    private static final long FRAME_INTERVAL_MS = 45L;
    private static final int HISTORY_WINDOW_SECONDS = 120;
    private static final int MAX_HISTORY_COLUMNS = (int) ((HISTORY_WINDOW_SECONDS * 1000L) / FRAME_INTERVAL_MS);
    private static final float[] NOTE_BASE_HZ = new float[] {160f, 98f, 538f, 496f, 469f, 96f};
    private static final float PLOT_LEFT_PADDING_PX = 64f;
    private static final float PLOT_RIGHT_PADDING_PX = 8f;
    private static final float PLOT_TOP_PADDING_PX = 8f;
    private static final float PLOT_BOTTOM_PADDING_PX = 34f;

    private final Paint spectrogramGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint heatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<float[]> spectrumHistory = new ArrayList<float[]>();

    private int lastSpectrumSampleRate = 44100;
    private int activeSoundNumber = 0;
    private boolean airOn = false;
    private float phase = 0f;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!airOn) {
                return;
            }
            pushSyntheticSpectrumFrame();
            postInvalidateOnAnimation();
            postDelayed(this, FRAME_INTERVAL_MS);
        }
    };

    public SpectrogramView(Context context) {
        super(context);
        init();
    }

    public SpectrogramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpectrogramView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setBackgroundColor(Color.parseColor("#0F1913"));

        spectrogramGridPaint.setColor(Color.parseColor("#324A37"));
        spectrogramGridPaint.setStrokeWidth(2f);

        labelPaint.setColor(Color.parseColor("#88B694"));
        labelPaint.setTextSize(20f);
    }

    public void setActiveSoundNumber(int soundNumber) {
        activeSoundNumber = Math.max(0, soundNumber);
    }

    public void setAirOn(boolean enabled) {
        if (airOn == enabled) {
            return;
        }
        airOn = enabled;
        removeCallbacks(ticker);
        if (airOn) {
            post(ticker);
        } else {
            spectrumHistory.clear();
            invalidate();
        }
    }

    private void pushSyntheticSpectrumFrame() {
        int bins = 512;
        float[] frame = new float[bins];

        float baseHz = resolveBaseFrequency(activeSoundNumber);
        for (int bin = 0; bin < bins; bin++) {
            float hz = bin * lastSpectrumSampleRate / (2f * bins);
            float harmonic1 = gaussian(hz, baseHz, 70f);
            float harmonic2 = gaussian(hz, baseHz * 2f, 110f);
            float harmonic3 = gaussian(hz, baseHz * 3f, 160f);
            float shimmer = 0.15f + 0.12f * (float) Math.sin(phase + bin * 0.28f);
            frame[bin] = Math.max(0f, harmonic1 + harmonic2 * 0.6f + harmonic3 * 0.35f + shimmer);
        }

        phase += 0.23f;
        spectrumHistory.add(frame);
        while (spectrumHistory.size() > MAX_HISTORY_COLUMNS) {
            spectrumHistory.remove(0);
        }
    }


    private float resolveBaseFrequency(int soundNumber) {
        if (soundNumber <= 0) {
            return NOTE_BASE_HZ[0];
        }
        int index = Math.min(NOTE_BASE_HZ.length - 1, soundNumber - 1);
        return NOTE_BASE_HZ[index];
    }

    private float gaussian(float x, float center, float sigma) {
        float d = (x - center) / sigma;
        return (float) Math.exp(-d * d);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(ticker);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float plotLeft = PLOT_LEFT_PADDING_PX;
        float plotRight = Math.max(plotLeft + 1f, w - PLOT_RIGHT_PADDING_PX);
        float plotTop = PLOT_TOP_PADDING_PX;
        float plotBottom = Math.max(plotTop + 1f, h - PLOT_BOTTOM_PADDING_PX);
        drawSpectrogramHeatmap(canvas, plotLeft, plotRight, plotTop, plotBottom);
        drawSpectrogramGrid(canvas, plotLeft, plotRight, plotTop, plotBottom);
    }

    private void drawSpectrogramGrid(Canvas canvas, float left, float right, float top, float bottom) {
        float plotWidth = Math.max(1f, right - left);

        int yGridLines = Math.max(1, (int) (MAX_SPECTROGRAM_HZ / 1000f));
        for (int i = 0; i <= yGridLines; i++) {
            float hz = i * 1000f;
            float y = yForFrequency(hz, top, bottom);
            canvas.drawLine(left, y, right, y, spectrogramGridPaint);
            canvas.drawText(String.format(Locale.US, "%.1f kHz", hz / 1000f), 8f, y - 4f, labelPaint);
        }

        int xGridLines = 6;
        int columns = spectrumHistory.size();
        int visibleColumns = Math.max(1, Math.min(columns, (int) Math.ceil(plotWidth)));
        float visibleSeconds = Math.max(1f, visibleColumns * FRAME_INTERVAL_MS / 1000f);
        for (int i = 0; i <= xGridLines; i++) {
            float t = i / (float) xGridLines;
            float x = left + t * plotWidth;
            canvas.drawLine(x, top, x, bottom, spectrogramGridPaint);
            float sec = -visibleSeconds + t * visibleSeconds;
            canvas.drawText(String.format(Locale.US, "%.0f s", sec), x - 14f, bottom + 24f, labelPaint);
        }

        canvas.drawLine(left, top, left, bottom, labelPaint);
        canvas.drawLine(left, bottom, right, bottom, labelPaint);
    }

    private void drawSpectrogramHeatmap(Canvas canvas, float left, float right, float top, float bottom) {
        if (spectrumHistory.isEmpty()) {
            return;
        }

        float plotWidth = Math.max(1f, right - left);
        int columns = spectrumHistory.size();
        int visibleColumns = Math.max(1, Math.min(columns, (int) Math.ceil(plotWidth)));
        int firstVisibleColumn = Math.max(0, columns - visibleColumns);
        float colW = plotWidth / visibleColumns;

        for (int x = 0; x < visibleColumns; x++) {
            float[] frame = spectrumHistory.get(firstVisibleColumn + x);
            if (frame == null || frame.length == 0) {
                continue;
            }
            int bins = frame.length;
            float frameMax = max(frame);
            if (frameMax <= 0f) {
                frameMax = 1f;
            }
            for (int bin = 0; bin < bins; bin++) {
                float hz = bin * lastSpectrumSampleRate / (2f * bins);
                if (hz > MAX_SPECTROGRAM_HZ) {
                    break;
                }
                float nextHz = (bin + 1) * lastSpectrumSampleRate / (2f * bins);
                float yTop = yForFrequency(nextHz, top, bottom);
                float yBottom = yForFrequency(hz, top, bottom);
                float intensity = normalizeMagnitude(frame[bin], frameMax);
                heatPaint.setColor(heatColor(intensity));
                heatPaint.setStyle(Paint.Style.FILL);
                float colLeft = left + x * colW;
                canvas.drawRect(colLeft, yTop, colLeft + colW + 1f, yBottom, heatPaint);
            }
        }
    }

    private float normalizeMagnitude(float value, float frameMax) {
        if (value <= 0f || frameMax <= 0f) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value / frameMax));
    }

    private int heatColor(float intensity) {
        float clamped = Math.max(0f, Math.min(1f, intensity));
        float hue = (1f - clamped) * 240f;
        return Color.HSVToColor(new float[] {hue, 1f, clamped});
    }

    private float max(float[] values) {
        float max = 0f;
        for (float value : values) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    private float yForFrequency(float hz, float top, float bottom) {
        float clamped = Math.max(0f, Math.min(MAX_SPECTROGRAM_HZ, hz));
        float norm = clamped / MAX_SPECTROGRAM_HZ;
        return bottom - norm * (bottom - top);
    }
}
