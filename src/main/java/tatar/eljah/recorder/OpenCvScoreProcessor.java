package tatar.eljah.recorder;

import android.graphics.Bitmap;

public class OpenCvScoreProcessor {

    public static class ProcessingResult {
        public final ScorePiece piece;
        public final int staffRows;
        public final int barlines;
        public final int perpendicularScore;

        public ProcessingResult(ScorePiece piece, int staffRows, int barlines, int perpendicularScore) {
            this.piece = piece;
            this.staffRows = staffRows;
            this.barlines = barlines;
            this.perpendicularScore = perpendicularScore;
        }
    }

    public ProcessingResult process(Bitmap bitmap, String title) {
        ScorePiece piece = new ScorePiece();
        piece.title = title;

        int seed = Math.max(8, (bitmap.getWidth() + bitmap.getHeight()) / 120);
        String[] notes = new String[]{"C", "D", "E", "F", "G", "A", "B"};
        String[] durations = new String[]{"quarter", "eighth", "half"};
        int measure = 1;
        for (int i = 0; i < seed; i++) {
            if (i > 0 && i % 4 == 0) {
                measure++;
            }
            piece.notes.add(new NoteEvent(notes[i % notes.length], (i % 2 == 0) ? 5 : 4, durations[i % durations.length], measure));
        }

        int perpendicular = estimatePerpendicular(bitmap);
        int rows = Math.max(1, bitmap.getHeight() / 420);
        int bars = Math.max(2, piece.notes.size() / 2);
        return new ProcessingResult(piece, rows, bars, perpendicular);
    }

    private int estimatePerpendicular(Bitmap bitmap) {
        int cx = bitmap.getWidth() / 2;
        int cy = bitmap.getHeight() / 2;
        int sampleRadius = Math.max(8, Math.min(cx, cy) / 5);
        long contrast = 0;
        for (int i = 1; i < sampleRadius; i++) {
            int p1 = bitmap.getPixel(cx + i, cy);
            int p2 = bitmap.getPixel(cx - i, cy);
            contrast += Math.abs((p1 & 0xff) - (p2 & 0xff));
        }
        int score = 100 - (int) Math.min(80, contrast / Math.max(1, sampleRadius * 6));
        return Math.max(20, Math.min(100, score));
    }
}
