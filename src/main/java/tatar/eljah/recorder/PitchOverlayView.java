package tatar.eljah.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class PitchOverlayView extends View {
    private final Paint expectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint actualPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float expectedHz;
    private float actualHz;

    public PitchOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        expectedPaint.setColor(Color.parseColor("#2E7D32"));
        expectedPaint.setStrokeWidth(8f);
        actualPaint.setColor(Color.parseColor("#1976D2"));
        actualPaint.setStrokeWidth(8f);
        gridPaint.setColor(Color.LTGRAY);
        gridPaint.setStrokeWidth(2f);
    }

    public void setFrequencies(float expectedHz, float actualHz) {
        this.expectedHz = expectedHz;
        this.actualHz = actualHz;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight();
        float w = getWidth();
        for (int i = 1; i < 5; i++) {
            float y = h * i / 5f;
            canvas.drawLine(0, y, w, y, gridPaint);
        }
        canvas.drawLine(0, yFor(expectedHz, h), w, yFor(expectedHz, h), expectedPaint);
        canvas.drawLine(0, yFor(actualHz, h), w, yFor(actualHz, h), actualPaint);
    }

    private float yFor(float hz, float h) {
        if (hz <= 0f) {
            return h - 4f;
        }
        float norm = Math.max(0f, Math.min(1f, (hz - 220f) / 900f));
        return h - (norm * h);
    }
}
