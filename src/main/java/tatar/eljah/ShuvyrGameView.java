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
    public enum DisplayMode {
        NORMAL,
        SCHEMATIC
    }

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
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RectF> holeAreas = new ArrayList<RectF>();
    private final boolean[] closed = new boolean[HOLE_COUNT];

    private OnFingeringChangeListener listener;
    private DisplayMode displayMode = DisplayMode.NORMAL;
    private int bottomInsetPx = 0;

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

    public void setDisplayMode(DisplayMode mode) {
        if (mode == null || displayMode == mode) {
            return;
        }
        displayMode = mode;
        clearFingering();
        invalidate();
    }

    public void setBottomInsetPx(int insetPx) {
        int next = Math.max(0, insetPx);
        if (bottomInsetPx == next) {
            return;
        }
        bottomInsetPx = next;
        invalidate();
    }

    private void init() {
        pipePaint.setColor(Color.parseColor("#D5B07A"));
        borderPaint.setColor(Color.parseColor("#5E4427"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(5f);
        holeOpenPaint.setColor(Color.parseColor("#EDE0C8"));
        holeClosedPaint.setColor(Color.parseColor("#1F1F1F"));
        guidePaint.setColor(Color.parseColor("#8BC34A"));
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(3f);
        setBackgroundColor(Color.parseColor("#1A231A"));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        holeAreas.clear();
        if (displayMode == DisplayMode.SCHEMATIC) {
            drawSchematicMode(canvas);
        } else {
            drawNormalMode(canvas);
        }
    }

    private void drawNormalMode(Canvas canvas) {
        float w = getWidth();
        float h = Math.max(1f, getHeight() - bottomInsetPx);
        float pipeWidth = w * 0.135f;
        float pipeHeight = h * 0.82f;
        float top = h * 0.09f;

        float gap = 0f;
        float totalWidth = pipeWidth * 2f + gap;
        float leftPipeLeft = (w - totalWidth) / 2f;
        float rightPipeLeft = leftPipeLeft + pipeWidth + gap;

        RectF longPipe = new RectF(leftPipeLeft, top, leftPipeLeft + pipeWidth, top + pipeHeight);
        RectF shortPipe = new RectF(rightPipeLeft, top, rightPipeLeft + pipeWidth, top + pipeHeight);

        canvas.drawRoundRect(longPipe, 36f, 36f, pipePaint);
        canvas.drawRoundRect(longPipe, 36f, 36f, borderPaint);
        canvas.drawRoundRect(shortPipe, 36f, 36f, pipePaint);
        canvas.drawRoundRect(shortPipe, 36f, 36f, borderPaint);

        drawLongPipeHoles(canvas, longPipe, 0);
        drawShortPipeHoles(canvas, shortPipe, LONG_PIPE_HOLES);
    }

    private void drawSchematicMode(Canvas canvas) {
        float w = getWidth();
        float h = Math.max(1f, getHeight() - bottomInsetPx);
        float pipeWidth = w * 0.30f;
        float pipeHeight = h * 0.82f;
        float left = w * 0.35f;
        float top = h * 0.09f;

        RectF schematicPipe = new RectF(left, top, left + pipeWidth, top + pipeHeight);
        canvas.drawRoundRect(schematicPipe, 40f, 40f, pipePaint);
        canvas.drawRoundRect(schematicPipe, 40f, 40f, borderPaint);

        float cx = schematicPipe.centerX();
        float radius = schematicPipe.width() * 0.13f;
        float startY = schematicPipe.top + schematicPipe.height() * 0.12f;
        float step = schematicPipe.height() * 0.14f;
        float touchHalfX = schematicPipe.width() * 0.45f;
        float touchHalfY = step * 0.5f;

        for (int i = 0; i < HOLE_COUNT; i++) {
            float cy = startY + i * step;
            RectF touchArea = new RectF(cx - touchHalfX, cy - touchHalfY, cx + touchHalfX, cy + touchHalfY);
            holeAreas.add(touchArea);
            canvas.drawRect(touchArea, guidePaint);
            canvas.drawCircle(cx, cy, radius, closed[i] ? holeClosedPaint : holeOpenPaint);
            canvas.drawCircle(cx, cy, radius, borderPaint);
        }
    }

    private void drawLongPipeHoles(Canvas canvas, RectF pipe, int offset) {
        float cx = pipe.centerX();
        float radius = pipe.width() * 0.13f;
        float touchHalf = pipe.width() * 0.56f;
        float startY = pipe.top + pipe.height() * 0.16f;
        float step = pipe.height() * 0.18f;

        for (int i = 0; i < LONG_PIPE_HOLES; i++) {
            float cy = startY + i * step;
            drawHole(canvas, cx, cy, radius, touchHalf, offset + i);
        }
    }

    private void drawShortPipeHoles(Canvas canvas, RectF pipe, int offset) {
        float cx = pipe.centerX();
        float radius = pipe.width() * 0.13f;
        float touchHalf = pipe.width() * 0.56f;

        float startY = pipe.top + pipe.height() * 0.16f;
        float step = pipe.height() * 0.18f;
        float y1 = startY + step * 3f;
        float y2 = y1 + step;

        drawHole(canvas, cx, y1, radius, touchHalf, offset);
        drawHole(canvas, cx, y2, radius, touchHalf, offset + 1);
    }

    private void drawHole(Canvas canvas, float x, float y, float radius, float touchHalf, int holeIndex) {
        RectF holeArea = new RectF(x - touchHalf, y - touchHalf, x + touchHalf, y + touchHalf);
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

        if (displayMode == DisplayMode.SCHEMATIC) {
            return handleSchematicTouch(event, action);
        }
        return handleNormalTouch(event, action);
    }

    private boolean handleNormalTouch(MotionEvent event, int action) {
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
        return applyNextState(nextState);
    }

    private boolean handleSchematicTouch(MotionEvent event, int action) {
        boolean[] nextState = new boolean[closed.length];
        if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL && event.getPointerCount() > 0) {
            float x = event.getX(0);
            float y = event.getY(0);
            int selectedHole = -1;
            float bestDistance = Float.MAX_VALUE;
            for (int holeIndex = 0; holeIndex < holeAreas.size(); holeIndex++) {
                RectF area = holeAreas.get(holeIndex);
                if (area.contains(x, y)) {
                    float distance = Math.abs(y - area.centerY());
                    if (distance <= bestDistance) {
                        bestDistance = distance;
                        selectedHole = holeIndex;
                    }
                }
            }
            if (selectedHole >= 0) {
                for (int i = 0; i <= selectedHole && i < nextState.length; i++) {
                    nextState[i] = true;
                }
            }
        }
        return applyNextState(nextState);
    }

    public void clearFingering() {
        boolean changed = false;
        for (int i = 0; i < closed.length; i++) {
            if (closed[i]) {
                closed[i] = false;
                changed = true;
            }
        }
        if (changed) {
            notifyFingeringChanged();
        }
    }

    private boolean applyNextState(boolean[] nextState) {
        boolean changed = false;
        for (int i = 0; i < closed.length; i++) {
            if (closed[i] != nextState[i]) {
                changed = true;
                closed[i] = nextState[i];
            }
        }

        if (changed) {
            invalidate();
            notifyFingeringChanged();
        }

        return true;
    }

    private void notifyFingeringChanged() {
        if (listener == null) {
            return;
        }
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
