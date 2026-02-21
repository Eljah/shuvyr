package tatar.eljah;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class SpectrumView extends View {
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int soundNumber = 1;

    public SpectrumView(Context context) {
        super(context);
        init();
    }

    public SpectrumView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpectrumView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setColor(Color.parseColor("#72E0A2"));
        gridPaint.setColor(Color.parseColor("#2A3A2A"));
        gridPaint.setStrokeWidth(2f);
    }

    public void setSoundNumber(int soundNumber) {
        this.soundNumber = Math.max(1, Math.min(6, soundNumber));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        int bars = 24;
        float bw = w / bars;

        for (int i = 1; i < 5; i++) {
            float y = h * i / 5f;
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        float base = 0.20f + soundNumber * 0.08f;
        for (int i = 0; i < bars; i++) {
            float harmonic = (float) Math.sin((i + 1) * 0.8f + soundNumber * 0.6f);
            float env = (float) Math.exp(-i / 12f);
            float amp = Math.max(0.08f, (base + 0.18f * harmonic) * env);
            float top = h * (1f - Math.min(0.95f, amp));
            float left = i * bw + bw * 0.12f;
            float right = left + bw * 0.76f;
            canvas.drawRect(left, top, right, h, barPaint);
        }
    }
}
