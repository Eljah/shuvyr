package tatar.eljah.recorder;

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

public class PitchOverlayView extends View {
    private static final float MAX_SPECTROGRAM_HZ = 3000f;
    private static final float NOTE_LABEL_MIN_GAP_PX = 2f;
    private static final float STAFF_TOP_PADDING_PX = 10f;
    private static final float STAFF_BOTTOM_PADDING_PX = 12f;
    private static final float NOTE_LABEL_BLOCK_GAP_PX = 14f;
    private static final float NOTE_LABEL_ROW_GAP_PX = 18f;
    private static final float SPECTROGRAM_TOP_PADDING_PX = 24f;
    private static final int REFERENCE_NOTE_COUNT = 56;
    private static final float LEDGER_STAFF_SPAN_IN_GAPS = 7f; // 1.5 above + 4 staff + 1.5 below

    private final Paint staffPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeNotePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spectrogramGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint heatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<NoteEvent> notes = new ArrayList<NoteEvent>();
    private final List<Float> history = new ArrayList<Float>();
    private final List<float[]> spectrumHistory = new ArrayList<float[]>();
    private final List<NoteDrawInfo> noteDrawInfos = new ArrayList<NoteDrawInfo>();

    private final Paint mismatchNotePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mismatchLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint durationMismatchNotePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint noteStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private OnPlayedNoteClickListener playedNoteClickListener;
    private final List<String> mismatchActualByIndex = new ArrayList<String>();
    private final List<Boolean> matchedByIndex = new ArrayList<Boolean>();
    private final List<String> matchedActualByIndex = new ArrayList<String>();
    private final List<Boolean> durationMismatchByIndex = new ArrayList<Boolean>();

    private float expectedHz;
    private float actualHz;
    private int pointer;
    private int lastSpectrumSampleRate = 22050;
    private boolean micMode = true;

    public PitchOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        staffPaint.setColor(Color.DKGRAY);
        staffPaint.setStrokeWidth(3f);

        notePaint.setColor(Color.BLACK);
        activeNotePaint.setColor(Color.parseColor("#2E7D32"));
        mismatchNotePaint.setColor(Color.parseColor("#C62828"));

        labelPaint.setColor(Color.parseColor("#424242"));
        labelPaint.setTextSize(28f);

        activeLabelPaint.setColor(Color.parseColor("#2E7D32"));
        activeLabelPaint.setTextSize(28f);

        mismatchLabelPaint.setColor(Color.parseColor("#C62828"));
        mismatchLabelPaint.setTextSize(28f);

        durationMismatchNotePaint.setColor(Color.parseColor("#FB8C00"));

        noteStrokePaint.setColor(Color.BLACK);
        noteStrokePaint.setStrokeWidth(3f);
        noteStrokePaint.setStyle(Paint.Style.STROKE);

        stemPaint.setStyle(Paint.Style.STROKE);
        stemPaint.setStrokeCap(Paint.Cap.ROUND);
        stemPaint.setStrokeWidth(noteStrokePaint.getStrokeWidth() * 2f);

        expectedPaint.setColor(Color.parseColor("#8E24AA"));
        expectedPaint.setStrokeWidth(1.5f);

        spectrogramGridPaint.setColor(Color.LTGRAY);
        spectrogramGridPaint.setStrokeWidth(2f);
    }

    public void setNotes(List<NoteEvent> pieceNotes) { /* unchanged */
        notes.clear();
        if (pieceNotes != null) notes.addAll(pieceNotes);
        mismatchActualByIndex.clear(); matchedByIndex.clear(); matchedActualByIndex.clear(); durationMismatchByIndex.clear();
        for (int i = 0; i < notes.size(); i++) { mismatchActualByIndex.add(null); matchedByIndex.add(false); matchedActualByIndex.add(null); durationMismatchByIndex.add(false);} invalidate();
    }
    public void markMismatch(int index, String actualFullName) { if (index < 0 || index >= notes.size()) return; ensureMismatchCapacity(); mismatchActualByIndex.set(index, actualFullName); invalidate(); }
    public void clearMismatch(int index) { if (index < 0 || index >= notes.size()) return; ensureMismatchCapacity(); mismatchActualByIndex.set(index, null); invalidate(); }
    public void markMatched(int index, String actualFullName) { if (index < 0 || index >= notes.size()) return; ensureMatchedCapacity(); matchedByIndex.set(index, true); matchedActualByIndex.set(index, actualFullName); invalidate(); }
    public void clearMatched(int index) { if (index < 0 || index >= notes.size()) return; ensureMatchedCapacity(); matchedByIndex.set(index, false); matchedActualByIndex.set(index, null); invalidate(); }
    public void markDurationMismatch(int index) { if (index < 0 || index >= notes.size()) return; ensureDurationMismatchCapacity(); durationMismatchByIndex.set(index, true); invalidate(); }
    public void clearDurationMismatch(int index) { if (index < 0 || index >= notes.size()) return; ensureDurationMismatchCapacity(); durationMismatchByIndex.set(index, false); invalidate(); }
    public boolean isDurationMismatch(int index) { return hasDurationMismatch(index); }
    public void setOnPlayedNoteClickListener(OnPlayedNoteClickListener listener) { this.playedNoteClickListener = listener; }
    public void setPointer(int pointer) { this.pointer = pointer; invalidate(); }
    public void setFrequencies(float expectedHz, float actualHz) { this.expectedHz = expectedHz; this.actualHz = actualHz; history.add(actualHz); if (history.size() > 240) history.remove(0); invalidate(); }
    public void setMicMode(boolean micMode) { this.micMode = micMode; invalidate(); }
    public void setSpectrum(float[] magnitudes, int sampleRate) { if (magnitudes == null || magnitudes.length == 0) return; lastSpectrumSampleRate = sampleRate; float[] copy = new float[magnitudes.length]; System.arraycopy(magnitudes, 0, copy, 0, magnitudes.length); spectrumHistory.add(copy); if (spectrumHistory.size() > 260) spectrumHistory.remove(0); invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();

        float spectrogramBottom = h - 8f;
        float spectrogramTopLimit = h * 0.58f;
        float staffTop = STAFF_TOP_PADDING_PX;
        float staffBottom = Math.max(staffTop + 70f, spectrogramTopLimit - STAFF_BOTTOM_PADDING_PX);

        float labelsBottom = drawStaffAndNotes(canvas, w, staffTop, staffBottom);
        float spectrogramTop = Math.max(labelsBottom + SPECTROGRAM_TOP_PADDING_PX, spectrogramTopLimit);
        if (spectrogramTop >= spectrogramBottom) {
            spectrogramTop = Math.max(staffBottom + NOTE_LABEL_BLOCK_GAP_PX, spectrogramBottom - 40f);
        }
        drawSpectrogram(canvas, w, spectrogramTop, spectrogramBottom);
    }

    private float drawStaffAndNotes(Canvas canvas, float w, float staffTop, float staffBottom) {
        float drawableStaffHeight = Math.max(1f, staffBottom - staffTop);
        float lineGap = drawableStaffHeight / LEDGER_STAFF_SPAN_IN_GAPS;
        float firstLineY = staffTop + lineGap * 1.5f;
        float bottomLineY = firstLineY + lineGap * 4f;
        for (int i = 0; i < 5; i++) {
            float y = firstLineY + i * lineGap;
            canvas.drawLine(0, y, w, y, staffPaint);
        }

        if (notes.isEmpty()) {
            return bottomLineY + NOTE_LABEL_BLOCK_GAP_PX;
        }

        float leftPad = 26f;
        float rightPad = 20f;
        float available = Math.max(1f, w - leftPad - rightPad);
        float noteStep = notes.size() <= 1 ? available : available / (notes.size() - 1);
        float referenceStep = available / Math.max(1, REFERENCE_NOTE_COUNT - 1);
        float noteRadius = Math.max(8f, Math.min(lineGap * 0.58f, referenceStep * 0.48f)) * 2f;

        List<LabelLayout> labelsToDraw = new ArrayList<LabelLayout>();
        float labelStartY = bottomLineY + NOTE_LABEL_BLOCK_GAP_PX;
        float labelRowGap = Math.max(NOTE_LABEL_ROW_GAP_PX, lineGap * 0.72f);
        List<Float> lastLabelRight = new ArrayList<Float>();

        noteDrawInfos.clear();
        for (int i = 0; i < notes.size(); i++) {
            NoteEvent note = notes.get(i);
            float x = leftPad + available * ((float) i / Math.max(1, notes.size() - 1));
            int step = diatonicStepFromBottomLineE4(note.noteName, note.octave);
            float y = yForStaffStep(step, bottomLineY, lineGap);
            drawLedgerLines(canvas, x, step, lineGap, bottomLineY, noteRadius);

            boolean mismatch = hasMismatch(i);
            boolean matched = isMatched(i);
            boolean durationMismatch = hasDurationMismatch(i);
            Paint circlePaint = mismatch ? mismatchNotePaint : (durationMismatch ? durationMismatchNotePaint : ((matched || i == pointer) ? activeNotePaint : notePaint));
            boolean stemUp = stemUpForNote(i, y, firstLineY, bottomLineY, noteStep, noteRadius);
            float stemOffsetX = stemOffsetForIndex(i, noteStep, noteRadius);
            drawDurationAwareNote(canvas, note, x, y, noteRadius, stemOffsetX, stemUp, circlePaint);

            String label = MusicNotation.toEuropeanLabel(note.noteName, note.octave);
            float textWidth = labelPaint.measureText(label);
            float textLeft = Math.max(0f, Math.min(w - textWidth, x - textWidth / 2f));

            int row = selectLabelRow(textLeft, textWidth, lastLabelRight);
            while (lastLabelRight.size() <= row) {
                lastLabelRight.add(Float.NEGATIVE_INFINITY);
            }
            float minAllowedLeft = lastLabelRight.get(row) + NOTE_LABEL_MIN_GAP_PX;
            textLeft = Math.max(textLeft, minAllowedLeft);
            textLeft = Math.max(0f, Math.min(w - textWidth, textLeft));
            lastLabelRight.set(row, textLeft + textWidth);

            float textY = labelStartY + row * labelRowGap;
            labelsToDraw.add(new LabelLayout(label, textLeft, textY, i == pointer, mismatch, durationMismatch));
            noteDrawInfos.add(new NoteDrawInfo(i, x, y, Math.max(noteRadius * 2f, 28f)));
        }

        for (LabelLayout labelLayout : labelsToDraw) {
            Paint textPaint = labelLayout.mismatch ? mismatchLabelPaint : (labelLayout.durationMismatch ? durationMismatchNotePaint : (labelLayout.active ? activeLabelPaint : labelPaint));
            canvas.drawText(labelLayout.text, labelLayout.x, labelLayout.y, textPaint);
        }

        int maxRow = Math.max(0, lastLabelRight.size() - 1);
        return labelStartY + maxRow * labelRowGap;
    }

    private int selectLabelRow(float textLeft, float textWidth, List<Float> lastLabelRight) {
        for (int row = 0; row < lastLabelRight.size(); row++) {
            float right = lastLabelRight.get(row);
            if (textLeft >= right + NOTE_LABEL_MIN_GAP_PX) {
                return row;
            }
        }
        return lastLabelRight.size();
    }

    private void drawLedgerLines(Canvas canvas, float x, int step, float lineGap, float bottomLineY, float noteRadius) {
        float ledgerWidth = Math.max(16f, noteRadius * 1.8f);
        if (step <= -2) {
            for (int ledgerStep = -2; ledgerStep >= step; ledgerStep -= 2) {
                float y = yForStaffStep(ledgerStep, bottomLineY, lineGap);
                canvas.drawLine(x - ledgerWidth, y, x + ledgerWidth, y, staffPaint);
            }
        }
        if (step >= 10) {
            for (int ledgerStep = 10; ledgerStep <= step; ledgerStep += 2) {
                float y = yForStaffStep(ledgerStep, bottomLineY, lineGap);
                canvas.drawLine(x - ledgerWidth, y, x + ledgerWidth, y, staffPaint);
            }
        }
    }

    private float yForStaffStep(int step, float bottomLineY, float lineGap) {
        return bottomLineY - step * (lineGap / 2f);
    }

    private boolean hasMismatch(int index) { ensureMismatchCapacity(); return index >= 0 && index < mismatchActualByIndex.size() && mismatchActualByIndex.get(index) != null; }
    private boolean isMatched(int index) { ensureMatchedCapacity(); return index >= 0 && index < matchedByIndex.size() && matchedByIndex.get(index); }
    private boolean hasDurationMismatch(int index) { ensureDurationMismatchCapacity(); return index >= 0 && index < durationMismatchByIndex.size() && durationMismatchByIndex.get(index); }
    private void ensureMismatchCapacity() { while (mismatchActualByIndex.size() < notes.size()) mismatchActualByIndex.add(null); }
    private void ensureDurationMismatchCapacity() { while (durationMismatchByIndex.size() < notes.size()) durationMismatchByIndex.add(false); }
    private void ensureMatchedCapacity() { while (matchedByIndex.size() < notes.size()) matchedByIndex.add(false); while (matchedActualByIndex.size() < notes.size()) matchedActualByIndex.add(null); }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        if (playedNoteClickListener == null) return true;
        float touchX = event.getX(); float touchY = event.getY();
        for (NoteDrawInfo info : noteDrawInfos) {
            boolean mismatch = hasMismatch(info.index); boolean matched = isMatched(info.index); if (!mismatch && !matched) continue;
            float dx = touchX - info.cx; float dy = touchY - info.cy;
            if (dx * dx + dy * dy <= info.hitRadius * info.hitRadius) {
                String actual = mismatch ? mismatchActualByIndex.get(info.index) : matchedActualByIndex.get(info.index);
                NoteEvent expected = notes.get(info.index);
                playedNoteClickListener.onPlayedNoteClick(info.index, expected.fullName(), actual); return true;
            }
        }
        return true;
    }

    public interface OnPlayedNoteClickListener { void onPlayedNoteClick(int index, String expectedFullName, String actualFullName); }

    private void drawDurationAwareNote(Canvas canvas, NoteEvent note, float x, float y, float noteRadius, float stemOffsetX, boolean stemUp, Paint fillPaint) {
        RectF oval = new RectF(x - noteRadius, y - noteRadius * 0.75f, x + noteRadius, y + noteRadius * 0.75f);
        String duration = note == null ? null : note.duration;
        boolean whole = "whole".equals(duration); boolean half = "half".equals(duration); boolean hollow = whole || half;
        if (hollow) { noteStrokePaint.setColor(fillPaint.getColor()); canvas.drawOval(oval, noteStrokePaint); } else { canvas.drawOval(oval, fillPaint); }
        if (whole) return;
        float stemAnchorX = stemUp ? (x + noteRadius * 0.86f) : (x - noteRadius * 0.86f);
        float stemX = stemAnchorX + stemOffsetX;
        float noteEdgeY = y + (stemUp ? (-noteRadius * 0.58f) : (noteRadius * 0.58f));
        float stemEndY = y + (stemUp ? (-noteRadius * 2.6f) : (noteRadius * 2.6f));
        stemPaint.setColor(fillPaint.getColor()); canvas.drawLine(stemX, noteEdgeY, stemX, stemEndY, stemPaint);
        int flags = flagCountForDuration(duration);
        for (int f = 0; f < flags; f++) {
            float flagStartY = stemEndY + (stemUp ? 1f : -1f) * f * (noteRadius * 0.75f);
            float flagEndX = stemX + (stemUp ? 1f : -1f) * noteRadius * 1.6f;
            float flagEndY = flagStartY + (stemUp ? 1f : -1f) * noteRadius * 0.55f;
            canvas.drawLine(stemX, flagStartY, flagEndX, flagEndY, stemPaint);
        }
    }

    private boolean stemUpForNote(int index, float noteY, float topLineY, float bottomLineY, float noteStep, float noteRadius) {
        float middleLineY = (topLineY + bottomLineY) * 0.5f;
        boolean denseLayout = noteStep < noteRadius * 1.9f;
        if (denseLayout) return index % 2 == 0;
        return noteY >= middleLineY;
    }

    private float stemOffsetForIndex(int index, float noteStep, float noteRadius) {
        float minGapWithoutOffset = noteRadius * 1.9f;
        if (noteStep >= minGapWithoutOffset) return 0f;
        float overlap = minGapWithoutOffset - noteStep;
        float maxOffset = noteRadius * 0.65f;
        float computedOffset = Math.min(maxOffset, overlap * 0.5f);
        return index % 2 == 0 ? -computedOffset : computedOffset;
    }

    private static final class LabelLayout { private final String text; private final float x; private final float y; private final boolean active; private final boolean mismatch; private final boolean durationMismatch;
        private LabelLayout(String text, float x, float y, boolean active, boolean mismatch, boolean durationMismatch) { this.text = text; this.x = x; this.y = y; this.active = active; this.mismatch = mismatch; this.durationMismatch = durationMismatch; } }

    private void drawSpectrogram(Canvas canvas, float w, float top, float bottom) {
        if (bottom <= top) return;
        drawSpectrogramGrid(canvas, w, top, bottom); drawSpectrogramHeatmap(canvas, w, top, bottom);
        if (micMode && expectedHz > 0f) {
            float guideHz = Math.min(expectedHz * 2f, MAX_SPECTROGRAM_HZ);
            float expectedY = yForFrequency(guideHz, top, bottom);
            canvas.drawLine(0, expectedY, w, expectedY, expectedPaint);
        }
    }
    private void drawSpectrogramGrid(Canvas canvas, float w, float top, float bottom) {
        for (int i = 0; i <= 3; i++) { float hz = i * 1000f; float y = yForFrequency(hz, top, bottom); canvas.drawLine(0, y, w, y, spectrogramGridPaint); canvas.drawText(((int) hz) + " Hz", 8f, y - 4f, labelPaint); }
    }
    private void drawSpectrogramHeatmap(Canvas canvas, float w, float top, float bottom) {
        if (spectrumHistory.isEmpty()) return;
        int columns = spectrumHistory.size(); float colW = Math.max(1f, w / Math.max(1, columns));
        for (int x = 0; x < columns; x++) {
            float[] frame = spectrumHistory.get(x); if (frame == null || frame.length == 0) continue;
            int bins = frame.length; float frameMax = max(frame); if (frameMax <= 0f) frameMax = 1f;
            for (int bin = 0; bin < bins; bin++) {
                float hz = bin * lastSpectrumSampleRate / (2f * bins); if (hz > MAX_SPECTROGRAM_HZ) break;
                float nextHz = (bin + 1) * lastSpectrumSampleRate / (2f * bins);
                float yTop = yForFrequency(nextHz, top, bottom); float yBottom = yForFrequency(hz, top, bottom);
                float intensity = normalizeMagnitude(frame[bin], frameMax);
                heatPaint.setColor(heatColor(intensity)); heatPaint.setStyle(Paint.Style.FILL);
                float left = x * colW; canvas.drawRect(left, yTop, left + colW + 1f, yBottom, heatPaint);
            }
        }
    }
    private float normalizeMagnitude(float value, float frameMax) { if (value <= 0f || frameMax <= 0f) return 0f; return Math.max(0f, Math.min(1f, value / frameMax)); }
    private int heatColor(float intensity) { float clamped = Math.max(0f, Math.min(1f, intensity)); float hue = (1f - clamped) * 240f; return Color.HSVToColor(new float[]{hue, 1f, clamped}); }
    private float max(float[] values) { float max = 0f; for (float value : values) if (value > max) max = value; return max; }
    private int diatonicStepFromBottomLineE4(String noteName, int octave) { int letterIndex = letterIndex(noteName); int absolute = octave * 7 + letterIndex; int e4Absolute = 4 * 7 + 2; return absolute - e4Absolute; }
    private int letterIndex(String noteName) {
        if (noteName == null || noteName.length() == 0) return 0; char letter = Character.toUpperCase(noteName.charAt(0));
        if (letter == 'C') return 0; if (letter == 'D') return 1; if (letter == 'E') return 2; if (letter == 'F') return 3; if (letter == 'G') return 4; if (letter == 'A') return 5; return 6;
    }
    private int flagCountForDuration(String duration) { if ("eighth".equals(duration)) return 1; if ("16th".equals(duration)) return 2; return 0; }
    private static final class NoteDrawInfo { private final int index; private final float cx; private final float cy; private final float hitRadius; private NoteDrawInfo(int index, float cx, float cy, float hitRadius) { this.index = index; this.cx = cx; this.cy = cy; this.hitRadius = hitRadius; } }
    private float yForFrequency(float hz, float top, float bottom) { float clamped = Math.max(0f, Math.min(MAX_SPECTROGRAM_HZ, hz)); float norm = clamped / MAX_SPECTROGRAM_HZ; return bottom - norm * (bottom - top); }
}
