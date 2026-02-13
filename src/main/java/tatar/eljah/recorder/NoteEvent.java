package tatar.eljah.recorder;

public class NoteEvent {
    public final String noteName;
    public final int octave;
    public final String duration;
    public final int measure;

    public NoteEvent(String noteName, int octave, String duration, int measure) {
        this.noteName = noteName;
        this.octave = octave;
        this.duration = duration;
        this.measure = measure;
    }

    public String fullName() {
        return noteName + octave;
    }
}
