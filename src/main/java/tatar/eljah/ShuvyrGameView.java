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
    private static final int LONG_PIPE_LAST_HOLE_INDEX = 3;
    private static final int SHORT_PIPE_FIRST_HOLE_INDEX = 4;
    private static final float NORMAL_HOLE_RADIUS_RATIO = 0.01755f;
    private static final float NORMAL_TOUCH_HALF_RATIO = 0.0756f;

    private final Paint pipePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holeOpenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holeClosedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint touchGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RectF> holeAreas = new ArrayList<RectF>();
    private final boolean[] closed = new boolean[HOLE_COUNT];

    private OnFingeringChangeListener listener;
    private DisplayMode displayMode = DisplayMode.NORMAL;
    private int bottomInsetPx = 0;
    private int highlightedSchematicHole = -1;

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
        highlightedSchematicHole = -1;
        clearFingering();
        invalidate();
    }


    public void setHighlightedSchematicHole(int holeIndex) {
        int next = (holeIndex >= 0 && holeIndex < HOLE_COUNT) ? holeIndex : -1;
        if (highlightedSchematicHole == next) {
            return;
        }
        highlightedSchematicHole = next;
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
        touchGuidePaint.setColor(Color.parseColor("#66CC66"));
        touchGuidePaint.setStyle(Paint.Style.STROKE);
        touchGuidePaint.setStrokeWidth(1.5f);
        highlightPaint.setColor(Color.parseColor("#C62828"));
        highlightPaint.setStyle(Paint.Style.FILL);
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
        float holeRadius = w * NORMAL_HOLE_RADIUS_RATIO;
        float holeDiameter = holeRadius * 2f;
        float pipeWidth = holeDiameter * 2f;
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

        drawLongPipeHoles(canvas, longPipe, 0, holeRadius, w * NORMAL_TOUCH_HALF_RATIO);
        drawShortPipeHoles(canvas, shortPipe, LONG_PIPE_HOLES, holeRadius, w * NORMAL_TOUCH_HALF_RATIO);
    }

    private void drawSchematicMode(Canvas canvas) {
        float w = getWidth();
        float h = Math.max(1f, getHeight() - bottomInsetPx);
        float holeRadius = w * NORMAL_HOLE_RADIUS_RATIO;
        float holeDiameter = holeRadius * 2f;
        float pipeWidth = holeDiameter * 2f;
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

        float longStartY = longPipe.top + longPipe.height() * 0.16f;
        float longStep = longPipe.height() * 0.18f;
        float longTouchHalfX = longPipe.width() * 0.72f;
        float longTouchHalfY = longStep * 0.55f;

        for (int i = 0; i < LONG_PIPE_HOLES; i++) {
            float cy = longStartY + i * longStep;
            RectF touchArea = new RectF(
                longPipe.centerX() - longTouchHalfX,
                cy - longTouchHalfY,
                longPipe.centerX() + longTouchHalfX,
                cy + longTouchHalfY
            );
            drawHole(canvas, longPipe.centerX(), cy, holeRadius, touchArea, i, true);
        }

        float shortStartY = shortPipe.top + shortPipe.height() * 0.16f;
        float shortStep = shortPipe.height() * 0.18f;
        float shortY1 = shortStartY + shortStep * 3f;
        float shortY2 = shortY1 + shortStep;
        float shortTouchHalfX = shortPipe.width() * 0.72f;
        float shortTouchHalfY = shortStep * 0.55f;

        RectF shortTouchArea1 = new RectF(
            shortPipe.centerX() - shortTouchHalfX,
            shortY1 - shortTouchHalfY,
            shortPipe.centerX() + shortTouchHalfX,
            shortY1 + shortTouchHalfY
        );
        RectF shortTouchArea2 = new RectF(
            shortPipe.centerX() - shortTouchHalfX,
            shortY2 - shortTouchHalfY,
            shortPipe.centerX() + shortTouchHalfX,
            shortY2 + shortTouchHalfY
        );
        drawHole(canvas, shortPipe.centerX(), shortY1, holeRadius, shortTouchArea1, LONG_PIPE_HOLES, true);
        drawHole(canvas, shortPipe.centerX(), shortY2, holeRadius, shortTouchArea2, LONG_PIPE_HOLES + 1, true);
    }

    private void drawLongPipeHoles(Canvas canvas, RectF pipe, int offset, float radius, float touchHalf) {
        float cx = pipe.centerX();
        float startY = pipe.top + pipe.height() * 0.16f;
        float step = pipe.height() * 0.18f;

        for (int i = 0; i < LONG_PIPE_HOLES; i++) {
            float cy = startY + i * step;
            drawHole(canvas, cx, cy, radius, touchHalf, offset + i);
        }
    }

    private void drawShortPipeHoles(Canvas canvas, RectF pipe, int offset, float radius, float touchHalf) {
        float cx = pipe.centerX();

        float startY = pipe.top + pipe.height() * 0.16f;
        float step = pipe.height() * 0.18f;
        float y1 = startY + step * 3f;
        float y2 = y1 + step;

        drawHole(canvas, cx, y1, radius, touchHalf, offset);
        drawHole(canvas, cx, y2, radius, touchHalf, offset + 1);
    }

    private void drawHole(Canvas canvas, float x, float y, float radius, float touchHalf, int holeIndex) {
        RectF holeArea = new RectF(x - touchHalf, y - touchHalf, x + touchHalf, y + touchHalf);
        drawHole(canvas, x, y, radius, holeArea, holeIndex, false);
    }

    private void drawHole(Canvas canvas, float x, float y, float radius, RectF holeArea, int holeIndex, boolean showGuide) {
        holeAreas.add(holeArea);
        if (showGuide) {
            canvas.drawRect(holeArea, touchGuidePaint);
        }
        if (displayMode == DisplayMode.SCHEMATIC && holeIndex == highlightedSchematicHole) {
            canvas.drawCircle(x, y, radius, highlightPaint);
        } else {
            canvas.drawCircle(x, y, radius, closed[holeIndex] ? holeClosedPaint : holeOpenPaint);
        }
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

        boolean pairClosed = nextState[LONG_PIPE_LAST_HOLE_INDEX] || nextState[SHORT_PIPE_FIRST_HOLE_INDEX];
        nextState[LONG_PIPE_LAST_HOLE_INDEX] = pairClosed;
        nextState[SHORT_PIPE_FIRST_HOLE_INDEX] = pairClosed;

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
                for (int i = 0; i <= selectedHole && i < HOLE_COUNT; i++) {
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
