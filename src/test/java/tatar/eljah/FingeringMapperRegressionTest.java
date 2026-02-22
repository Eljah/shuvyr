package tatar.eljah;

public class FingeringMapperRegressionTest {
    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    public void shouldMapAllFourControlButtonsInAnyPressReleaseSequenceForSpectrogramMode() {
        int[] controls = new int[] {0, 1, 2, 3};
        int steps = 1 << controls.length;

        for (int mask = 0; mask < steps; mask++) {
            int pattern = 0;
            for (int i = 0; i < controls.length; i++) {
                if ((mask & (1 << i)) != 0) {
                    pattern |= (1 << controls[i]);
                }
            }
            assertEquals(expectedSchematicSound(pattern),
                FingeringMapper.mapPatternToSoundNumber(pattern, true),
                "Unexpected mapping for control mask=" + Integer.toBinaryString(mask));
        }

        // Press/release every control button in all pairwise orders from empty state.
        for (int a = 0; a < controls.length; a++) {
            for (int b = 0; b < controls.length; b++) {
                if (a == b) {
                    continue;
                }
                int pattern = 0;
                pattern |= (1 << controls[a]); // press a
                assertEquals(expectedSchematicSound(pattern), FingeringMapper.mapPatternToSoundNumber(pattern, true),
                    "after press a=" + a + " b=" + b);
                pattern |= (1 << controls[b]); // press b
                assertEquals(expectedSchematicSound(pattern), FingeringMapper.mapPatternToSoundNumber(pattern, true),
                    "after press b=" + a + " b=" + b);
                pattern &= ~(1 << controls[a]); // release a
                assertEquals(expectedSchematicSound(pattern), FingeringMapper.mapPatternToSoundNumber(pattern, true),
                    "after release a=" + a + " b=" + b);
                pattern &= ~(1 << controls[b]); // release b
                assertEquals(expectedSchematicSound(pattern), FingeringMapper.mapPatternToSoundNumber(pattern, true),
                    "after release b=" + a + " b=" + b);
            }
        }
    }

    private int expectedSchematicSound(int pattern) {
        int longClosed = 0;
        for (int i = 0; i < 4; i++) {
            if ((pattern & (1 << i)) != 0) {
                longClosed++;
            } else {
                break;
            }
        }
        if (longClosed < 4) {
            return longClosed + 1;
        }

        int shortClosed = 0;
        for (int i = 4; i < 6; i++) {
            if ((pattern & (1 << i)) != 0) {
                shortClosed++;
            } else {
                break;
            }
        }
        return Math.min(6, 4 + shortClosed);
    }

    public static void main(String[] args) {
        FingeringMapperRegressionTest test = new FingeringMapperRegressionTest();
        test.shouldMapAllFourControlButtonsInAnyPressReleaseSequenceForSpectrogramMode();
        System.out.println("fingering-mapper-regression=OK");
    }
}
