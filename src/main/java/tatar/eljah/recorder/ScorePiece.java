package tatar.eljah.recorder;

import java.util.ArrayList;
import java.util.List;

public class ScorePiece {
    public String id;
    public String title;
    public long createdAt;
    public List<NoteEvent> notes = new ArrayList<NoteEvent>();
}
