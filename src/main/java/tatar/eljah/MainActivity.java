package tatar.eljah;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import tatar.eljah.shuvyr.R;

public class MainActivity extends AppCompatActivity implements ShuvyrGameView.OnFingeringChangeListener {
    private final SustainedWavPlayer[] players = new SustainedWavPlayer[6];
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

        for (int i = 0; i < resources.length; i++) {
            players[i] = new SustainedWavPlayer(this, resources[i]);
        }
    }

    @Override
    public void onFingeringChanged(int closedCount, int pattern) {
        int soundNumber = mapPatternToSoundNumber(pattern);
        noteLabel.setText(getString(R.string.current_note_template, String.valueOf(soundNumber), closedCount));

        if (soundNumber == activeSoundNumber) {
            return;
        }

        stopActive();
        players[soundNumber - 1].playSustain();
        activeSoundNumber = soundNumber;
    }

    private int mapPatternToSoundNumber(int pattern) {
        // Порядок дырок для «закрыть все до этой»: L1, L2, L3, R1, R2, R3.
        int leadingClosed = 0;
        for (int i = 0; i < 6; i++) {
            if ((pattern & (1 << i)) != 0) {
                leadingClosed++;
            } else {
                break;
            }
        }
        return Math.min(6, leadingClosed + 1);
    }

    private void stopActive() {
        if (activeSoundNumber < 1 || activeSoundNumber > 6) {
            return;
        }
        players[activeSoundNumber - 1].stop();
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
