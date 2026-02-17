#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/target/pitch-match-test"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

cat > "$OUT_DIR/PitchMatchUtilTestMain.java" <<'JAVA'
import tatar.eljah.recorder.PitchMatchUtil;

public class PitchMatchUtilTestMain {
    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean value, String message) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        // Regular mode: enharmonics should match via MIDI.
        assertTrue(PitchMatchUtil.samePitch("C#4", "Db4", false), "Regular mode must match enharmonic MIDI equivalents");
        assertFalse(PitchMatchUtil.samePitch("C4", "D4", false), "Regular mode must reject different MIDI notes");

        // Simplified mode: accidentals ignored, but must never be stricter than regular mode.
        assertTrue(PitchMatchUtil.samePitch("C4", "C#4", true), "Simplified mode must match same base note with accidental");
        assertTrue(PitchMatchUtil.samePitch("C#4", "Db4", true), "Simplified mode must still accept enharmonic equivalents");
        assertFalse(PitchMatchUtil.samePitch("C4", "D4", true), "Simplified mode must reject different base notes");

        // Input validation.
        assertFalse(PitchMatchUtil.samePitch(null, "C4", true), "Null inputs must be rejected");
        assertFalse(PitchMatchUtil.samePitch("C4", "bad", true), "Malformed note names must be rejected");

        System.out.println("PitchMatchUtil tests passed");
    }
}
JAVA

javac -d "$OUT_DIR" \
  "$ROOT/src/main/java/tatar/eljah/recorder/MusicNotation.java" \
  "$ROOT/src/main/java/tatar/eljah/recorder/PitchMatchUtil.java" \
  "$OUT_DIR/PitchMatchUtilTestMain.java"

java -cp "$OUT_DIR" PitchMatchUtilTestMain
