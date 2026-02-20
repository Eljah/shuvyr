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

    private static final int PIPE_COUNT = 2;
    private static final int HOLES_PER_PIPE = 3;
    private static final int HOLE_COUNT = PIPE_COUNT * HOLES_PER_PIPE;

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
        float pipeWidth = w * 0.22f;
        float pipeHeight = h * 0.84f;
        float top = h * 0.08f;
        float leftPipeLeft = w * 0.15f;
        float rightPipeLeft = w - leftPipeLeft - pipeWidth;

        RectF leftPipe = new RectF(leftPipeLeft, top, leftPipeLeft + pipeWidth, top + pipeHeight);
        RectF rightPipe = new RectF(rightPipeLeft, top, rightPipeLeft + pipeWidth, top + pipeHeight);

        canvas.drawRoundRect(leftPipe, 36f, 36f, pipePaint);
        canvas.drawRoundRect(leftPipe, 36f, 36f, borderPaint);
        canvas.drawRoundRect(rightPipe, 36f, 36f, pipePaint);
        canvas.drawRoundRect(rightPipe, 36f, 36f, borderPaint);

        holeAreas.clear();
        drawPipeHoles(canvas, leftPipe, 0);
        drawPipeHoles(canvas, rightPipe, HOLES_PER_PIPE);
    }

    private void drawPipeHoles(Canvas canvas, RectF pipe, int indexOffset) {
        float cx = pipe.centerX();
        float radius = pipe.width() * 0.15f;
        float touchRadius = radius * 1.55f;
        float[] yFactors = new float[] {0.16f, 0.50f, 0.84f};

        for (int i = 0; i < HOLES_PER_PIPE; i++) {
            float cy = pipe.top + pipe.height() * yFactors[i];
            RectF holeArea = new RectF(cx - touchRadius, cy - touchRadius, cx + touchRadius, cy + touchRadius);
            holeAreas.add(holeArea);
            int holeIndex = indexOffset + i;
            canvas.drawCircle(cx, cy, radius, closed[holeIndex] ? holeClosedPaint : holeOpenPaint);
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
