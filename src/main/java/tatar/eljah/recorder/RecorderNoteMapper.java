package tatar.eljah.recorder;

import java.util.HashMap;
import java.util.Map;

public class RecorderNoteMapper {
    private static final String[][] NOTE_RANGE = {
            {"C4", "246.94"}, {"D4", "293.66"}, {"E4", "329.63"}, {"F4", "349.23"},
            {"G4", "392.00"}, {"A4", "440.00"}, {"B4", "493.88"}, {"C5", "523.25"},
            {"D5", "587.33"}, {"E5", "659.25"}, {"F5", "698.46"}, {"G5", "783.99"},
            {"A5", "880.00"}, {"B5", "987.77"}
    };

    private static final Map<String, String> FINGERINGS = new HashMap<String, String>();

    static {
        FINGERINGS.put("C5", "●●●|●●●○");
        FINGERINGS.put("D5", "●●●|●●○○");
        FINGERINGS.put("E5", "●●●|●○○○");
        FINGERINGS.put("F5", "●●●|○○○○");
        FINGERINGS.put("G5", "●●○|○○○○");
        FINGERINGS.put("A5", "●○○|○○○○");
        FINGERINGS.put("B5", "○○○|○○○○");
        FINGERINGS.put("C4", "●●●|●●●●");
        FINGERINGS.put("D4", "●●●|●●●○");
        FINGERINGS.put("E4", "●●●|●●○○");
        FINGERINGS.put("F4", "●●●|●○○○");
        FINGERINGS.put("G4", "●●●|○○○○");
        FINGERINGS.put("A4", "●●○|○○○○");
        FINGERINGS.put("B4", "●○○|○○○○");
    }

    public String fromFrequency(float hz) {
        String closest = "";
        float best = Float.MAX_VALUE;
        for (String[] pair : NOTE_RANGE) {
            float f = Float.parseFloat(pair[1]);
            float diff = Math.abs(f - hz);
            if (diff < best) {
                best = diff;
                closest = pair[0];
            }
        }
        return closest;
    }


    public float frequencyFor(String note) {
        for (String[] pair : NOTE_RANGE) {
            if (pair[0].equals(note)) {
                return Float.parseFloat(pair[1]);
            }
        }
        return 0f;
    }

    public String fingeringFor(String note) {
        if (FINGERINGS.containsKey(note)) {
            return FINGERINGS.get(note);
        }
        return "нет данных";
    }
}
