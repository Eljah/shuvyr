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

    private static final int HOLES_PER_PIPE = 3;

    private final Paint pipePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holeOpenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holeClosedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RectF> holeAreas = new ArrayList<>();
    private final boolean[] closed = new boolean[HOLES_PER_PIPE * 2];

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
        float pipeWidth = w * 0.24f;
        float pipeHeight = h * 0.75f;
        float top = h * 0.12f;
        float left1 = w * 0.18f;
        float left2 = w * 0.58f;

        RectF pipe1 = new RectF(left1, top, left1 + pipeWidth, top + pipeHeight);
        RectF pipe2 = new RectF(left2, top, left2 + pipeWidth, top + pipeHeight);
        canvas.drawRoundRect(pipe1, 36f, 36f, pipePaint);
        canvas.drawRoundRect(pipe1, 36f, 36f, borderPaint);
        canvas.drawRoundRect(pipe2, 36f, 36f, pipePaint);
        canvas.drawRoundRect(pipe2, 36f, 36f, borderPaint);

        holeAreas.clear();
        drawHoles(canvas, pipe1, 0);
        drawHoles(canvas, pipe2, HOLES_PER_PIPE);
    }

    private void drawHoles(Canvas canvas, RectF pipe, int offset) {
        float cx = pipe.centerX();
        float spacing = pipe.height() / (HOLES_PER_PIPE + 1);
        float radius = pipe.width() * 0.24f;

        for (int i = 0; i < HOLES_PER_PIPE; i++) {
            float cy = pipe.top + spacing * (i + 1);
            RectF holeArea = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            holeAreas.add(holeArea);
            canvas.drawCircle(cx, cy, radius, closed[offset + i] ? holeClosedPaint : holeOpenPaint);
            canvas.drawCircle(cx, cy, radius, borderPaint);
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
