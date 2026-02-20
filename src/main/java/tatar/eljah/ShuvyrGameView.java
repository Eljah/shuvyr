package tatar.eljah;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class ShuvyrGameView extends View {
    public interface OnFingeringChangeListener {
        void onFingeringChanged(int closedCount, int pattern);
    }

    private static final int HOLE_COUNT = 6;

    private final Paint pipePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holeOpenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holeClosedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RectF> holeAreas = new ArrayList<>();
    private final boolean[] closed = new boolean[HOLE_COUNT];

    private OnFingeringChangeListener listener;

    public ShuvyrGameView(Context context) {
        super(context);
        init();
    }

    public ShuvyrGameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ShuvyrGameView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setOnFingeringChangeListener(OnFingeringChangeListener listener) {
        this.listener = listener;
    }

    private void init() {
        pipePaint.setColor(Color.parseColor("#D5B07A"));
        borderPaint.setColor(Color.parseColor("#5E4427"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(5f);
        holeOpenPaint.setColor(Color.parseColor("#EDE0C8"));
        holeClosedPaint.setColor(Color.parseColor("#1F1F1F"));
        setBackgroundColor(Color.parseColor("#1A231A"));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float pipeWidth = w * 0.34f;
        float pipeHeight = h * 0.78f;
        float top = h * 0.12f;
        float left = w * 0.33f;

        RectF pipe = new RectF(left, top, left + pipeWidth, top + pipeHeight);
        canvas.drawRoundRect(pipe, 36f, 36f, pipePaint);
        canvas.drawRoundRect(pipe, 36f, 36f, borderPaint);

        holeAreas.clear();
        drawHoles(canvas, pipe);
    }

    private void drawHoles(Canvas canvas, RectF pipe) {
        float cx = pipe.centerX();
        float leftX = cx - pipe.width() * 0.13f;
        float rightX = cx + pipe.width() * 0.13f;
        float radius = pipe.width() * 0.12f;
        float top = pipe.top + pipe.height() * 0.14f;
        float step = pipe.height() * 0.13f;

        float[] holeXs = new float[] {
            leftX, leftX, leftX, leftX, rightX, cx
        };
        float[] holeYs = new float[] {
            top,
            top + step,
            top + step * 2f,
            top + step * 3f,
            top + step * 3f,
            top + step * 4.2f
        };

        for (int i = 0; i < HOLE_COUNT; i++) {
            float holeX = holeXs[i];
            float holeY = holeYs[i];
            RectF holeArea = new RectF(holeX - radius, holeY - radius, holeX + radius, holeY + radius);
            holeAreas.add(holeArea);
            canvas.drawCircle(holeX, holeY, radius, closed[i] ? holeClosedPaint : holeOpenPaint);
            canvas.drawCircle(holeX, holeY, radius, borderPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_UP) {
            return super.onTouchEvent(event);
        }

        boolean changed = false;
        boolean[] nextState = new boolean[closed.length];

        for (int i = 0; i < event.getPointerCount(); i++) {
            float x = event.getX(i);
            float y = event.getY(i);
            for (int holeIndex = 0; holeIndex < holeAreas.size(); holeIndex++) {
                if (holeAreas.get(holeIndex).contains(x, y)) {
                    nextState[holeIndex] = true;
                }
            }
        }

        for (int i = 0; i < closed.length; i++) {
            if (closed[i] != nextState[i]) {
                changed = true;
                closed[i] = nextState[i];
            }
        }

        if (changed) {
            invalidate();
            if (listener != null) {
                int count = 0;
                int pattern = 0;
                for (int i = 0; i < closed.length; i++) {
                    if (closed[i]) {
                        count++;
                        pattern |= (1 << i);
                    }
                }
                listener.onFingeringChanged(count, pattern);
            }
        }

        return true;
    }
}
