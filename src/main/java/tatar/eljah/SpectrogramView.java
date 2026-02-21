package tatar.eljah;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class SpectrogramView extends View {
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] amplitudes = new float[16];
    private int activeSoundNumber = 0;

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
        gridPaint.setColor(Color.parseColor("#334433"));
        gridPaint.setStrokeWidth(2f);
        for (int i = 0; i < amplitudes.length; i++) {
            amplitudes[i] = 0.03f;
        }
    }

    public void setActiveSoundNumber(int soundNumber) {
        activeSoundNumber = Math.max(0, soundNumber);
        for (int i = 0; i < amplitudes.length; i++) {
            if (activeSoundNumber == 0) {
                amplitudes[i] = 0.02f;
            } else {
                float harmonic = 1f / (1f + i * 0.7f);
                float contour = (float) Math.max(0.08, Math.cos((i + activeSoundNumber) * 0.45) * 0.3 + 0.7);
                amplitudes[i] = Math.min(1f, harmonic * contour);
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();

        for (int i = 1; i < 4; i++) {
            float y = h * i / 4f;
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        float barWidth = w / amplitudes.length;
        LinearGradient gradient = new LinearGradient(
            0,
            0,
            0,
            h,
            Color.parseColor("#80FFE1"),
            Color.parseColor("#2F7D62"),
            Shader.TileMode.CLAMP
        );
        barPaint.setShader(gradient);

        for (int i = 0; i < amplitudes.length; i++) {
            float left = i * barWidth + 3f;
            float right = (i + 1) * barWidth - 3f;
            float top = h - (h * amplitudes[i]);
            canvas.drawRect(left, top, right, h, barPaint);
        }
    }
}
