package tatar.eljah.recorder;

public final class PitchMatchUtil {
    private PitchMatchUtil() {
    }

    public static boolean samePitch(String firstFullName, String secondFullName, boolean simplifiedAccidentalsMode) {
        if (firstFullName == null || secondFullName == null) {
            return false;
        }
        if (firstFullName.equals(secondFullName)) {
            return true;
        }
        if (firstFullName.length() < 2 || secondFullName.length() < 2) {
            return false;
        }
        try {
            String firstNote = firstFullName.substring(0, firstFullName.length() - 1);
            int firstOctave = Integer.parseInt(firstFullName.substring(firstFullName.length() - 1));
            String secondNote = secondFullName.substring(0, secondFullName.length() - 1);
            int secondOctave = Integer.parseInt(secondFullName.substring(secondFullName.length() - 1));

            boolean midiEqual = MusicNotation.midiFor(firstNote, firstOctave)
                    == MusicNotation.midiFor(secondNote, secondOctave);
            if (!simplifiedAccidentalsMode) {
                return midiEqual;
            }

            // Simplified mode should be at least as permissive as regular midi comparison.
            return midiEqual
                    || (firstOctave == secondOctave
                    && baseNoteLetter(firstNote).equals(baseNoteLetter(secondNote)));
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String baseNoteLetter(String noteName) {
        if (noteName == null || noteName.length() == 0) {
            return "";
        }
        return String.valueOf(Character.toUpperCase(noteName.charAt(0)));
    }
}
