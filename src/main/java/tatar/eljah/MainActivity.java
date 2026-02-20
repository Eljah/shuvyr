package tatar.eljah;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import tatar.eljah.shuvyr.R;

public class MainActivity extends AppCompatActivity implements ShuvyrGameView.OnFingeringChangeListener {
    private static final int SOUND_COUNT = 6;

    private final SustainedWavPlayer[] players = new SustainedWavPlayer[SOUND_COUNT];
    private int activeSoundNumber = -1;

    private TextView noteLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        noteLabel = findViewById(R.id.current_note_label);
        ShuvyrGameView gameView = findViewById(R.id.shuvyr_view);
        gameView.setOnFingeringChangeListener(this);

        int[] resources = new int[] {
            R.raw.shuvyr_1,
            R.raw.shuvyr_2,
            R.raw.shuvyr_3,
            R.raw.shuvyr_4,
            R.raw.shuvyr_5,
            R.raw.shuvyr_6
        };

        // Тайминги найденных фронтов (сек): конец атаки и начало заднего фронта.
        float[] attackEnd = new float[] {0.28f, 0.32f, 0.24f, 0.25f, 0.32f, 0.20f};
        float[] releaseStart = new float[] {2.62f, 2.83f, 2.41f, 2.47f, 2.34f, 2.69f};

        for (int i = 0; i < resources.length; i++) {
            players[i] = new SustainedWavPlayer(this, resources[i], attackEnd[i], releaseStart[i]);
        }
    }

    @Override
    public void onFingeringChanged(int closedCount, int pattern) {
        int soundNumber = mapPatternToSoundNumber(pattern);
        noteLabel.setText(getString(R.string.current_note_template, String.valueOf(soundNumber), closedCount));

        if (soundNumber == activeSoundNumber) {
            return;
        }

        stopActiveWithRelease();

        SustainedWavPlayer next = players[soundNumber - 1];
        next.playSustain();
        activeSoundNumber = soundNumber;
    }

    private int mapPatternToSoundNumber(int pattern) {
        // Порядок дырок: L1..L5, R1..R2. Следующий звук только при закрытии всех предыдущих.
        int leadingClosed = 0;
        for (int i = 0; i < 7; i++) {
            if ((pattern & (1 << i)) != 0) {
                leadingClosed++;
            } else {
                break;
            }
        }
        return Math.min(SOUND_COUNT, leadingClosed + 1);
    }

    private void stopActiveWithRelease() {
        if (activeSoundNumber < 1 || activeSoundNumber > SOUND_COUNT) {
            return;
        }
        SustainedWavPlayer active = players[activeSoundNumber - 1];
        active.stopWithRelease();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (int i = 0; i < players.length; i++) {
            if (players[i] != null) {
                players[i].release();
            }
        }
    }
}
