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

    private static final int LONG_PIPE_HOLES = 4;
    private static final int SHORT_PIPE_HOLES = 2;
    private static final int HOLE_COUNT = LONG_PIPE_HOLES + SHORT_PIPE_HOLES;

    private final Paint pipePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holeOpenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holeClosedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RectF> holeAreas = new ArrayList<RectF>();
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
        float pipeWidth = w * 0.24f;
        float pipeHeight = h * 0.80f;
        float top = h * 0.10f;

        float leftPipeLeft = w * 0.18f;
        float rightPipeLeft = w * 0.47f;

        RectF longPipe = new RectF(leftPipeLeft, top, leftPipeLeft + pipeWidth, top + pipeHeight);
        RectF shortPipe = new RectF(rightPipeLeft, top, rightPipeLeft + pipeWidth, top + pipeHeight);

        canvas.drawRoundRect(longPipe, 36f, 36f, pipePaint);
        canvas.drawRoundRect(longPipe, 36f, 36f, borderPaint);
        canvas.drawRoundRect(shortPipe, 36f, 36f, pipePaint);
        canvas.drawRoundRect(shortPipe, 36f, 36f, borderPaint);

        holeAreas.clear();
        drawLongPipeHoles(canvas, longPipe, 0);
        drawShortPipeHoles(canvas, shortPipe, LONG_PIPE_HOLES);
    }

    private void drawLongPipeHoles(Canvas canvas, RectF pipe, int offset) {
        float cx = pipe.centerX();
        float radius = pipe.width() * 0.15f;
        float touchRadius = radius * 2.0f;
        float startY = pipe.top + pipe.height() * 0.16f;
        float step = pipe.height() * 0.18f;

        for (int i = 0; i < LONG_PIPE_HOLES; i++) {
            float cy = startY + i * step;
            drawHole(canvas, cx, cy, radius, touchRadius, offset + i);
        }
    }

    private void drawShortPipeHoles(Canvas canvas, RectF pipe, int offset) {
        float cx = pipe.centerX();
        float radius = pipe.width() * 0.15f;
        float touchRadius = radius * 2.0f;

        float startY = pipe.top + pipe.height() * 0.16f;
        float step = pipe.height() * 0.18f;
        // Те же интервалы между дырками, что и на длинной трубке.
        float y1 = startY + step * 3f;
        float y2 = y1 + step;

        drawHole(canvas, cx, y1, radius, touchRadius, offset);
        drawHole(canvas, cx, y2, radius, touchRadius, offset + 1);
    }

    private void drawHole(Canvas canvas, float x, float y, float radius, float touchRadius, int holeIndex) {
        RectF holeArea = new RectF(x - touchRadius, y - touchRadius, x + touchRadius, y + touchRadius);
        holeAreas.add(holeArea);
        canvas.drawCircle(x, y, radius, closed[holeIndex] ? holeClosedPaint : holeOpenPaint);
        canvas.drawCircle(x, y, radius, borderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN
            && action != MotionEvent.ACTION_MOVE
            && action != MotionEvent.ACTION_UP
            && action != MotionEvent.ACTION_POINTER_DOWN
            && action != MotionEvent.ACTION_POINTER_UP
            && action != MotionEvent.ACTION_CANCEL) {
            return super.onTouchEvent(event);
        }

        boolean changed = false;
        boolean[] nextState = new boolean[closed.length];

        if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) {
            int skipPointer = action == MotionEvent.ACTION_POINTER_UP ? event.getActionIndex() : -1;
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (i == skipPointer) {
                    continue;
                }
                float x = event.getX(i);
                float y = event.getY(i);
                for (int holeIndex = 0; holeIndex < holeAreas.size(); holeIndex++) {
                    if (holeAreas.get(holeIndex).contains(x, y)) {
                        nextState[holeIndex] = true;
                    }
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
