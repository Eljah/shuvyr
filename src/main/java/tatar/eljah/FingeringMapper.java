package tatar.eljah;

public final class FingeringMapper {
    private static final int SOUND_COUNT = 6;

    private FingeringMapper() {
    }

    public static int mapPatternToSoundNumber(int pattern, boolean schematicMode) {
        if (!schematicMode
            && (pattern == (1 << 3) || pattern == (1 << 4) || pattern == ((1 << 3) | (1 << 4)))) {
            return 5;
        }

        int longMask = pattern & 0b001111;
        int shortMask = (pattern >> 4) & 0b000011;

        int longClosed = 0;
        for (int i = 0; i < 4; i++) {
            if ((longMask & (1 << i)) != 0) {
                longClosed++;
            } else {
                break;
            }
        }

        int shortClosed = 0;
        if (longClosed == 4) {
            for (int i = 0; i < 2; i++) {
                if ((shortMask & (1 << i)) != 0) {
                    shortClosed++;
                } else {
                    break;
                }
            }
        }

        if (longClosed < 4) {
            return longClosed + 1;
        }
        return Math.min(SOUND_COUNT, 4 + shortClosed);
    }
}
