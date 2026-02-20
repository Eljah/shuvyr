package tatar.eljah;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import tatar.eljah.shuvyr.R;

public class MainActivity extends AppCompatActivity implements ShuvyrGameView.OnFingeringChangeListener {
    private static final int SOUND_COUNT = 6;
    private static final int SILENT = 0;

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
        String labelValue = soundNumber == SILENT ? "—" : String.valueOf(soundNumber);
        noteLabel.setText(getString(R.string.current_note_template, labelValue, closedCount));

        if (soundNumber == activeSoundNumber) {
            return;
        }

        stopActiveWithRelease();

        if (soundNumber == SILENT) {
            activeSoundNumber = SILENT;
            return;
        }

        SustainedWavPlayer next = players[soundNumber - 1];
        next.playSustain();
        activeSoundNumber = soundNumber;
    }

    private int mapPatternToSoundNumber(int pattern) {
        // Эффективное зажатие: только непрерывно от первой дырки на каждой трубке.
        // Индексы: long L1..L4 => bits 0..3, short R1..R2 => bits 4..5.
        // Если на трубке есть "дырка после разрыва" (например только 2-я), это невалидно => тишина.
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

        int longExpectedMask = longClosed == 0 ? 0 : ((1 << longClosed) - 1);
        if (longMask != longExpectedMask) {
            return SILENT;
        }

        int shortClosed = 0;
        for (int i = 0; i < 2; i++) {
            if ((shortMask & (1 << i)) != 0) {
                shortClosed++;
            } else {
                break;
            }
        }

        int shortExpectedMask = shortClosed == 0 ? 0 : ((1 << shortClosed) - 1);
        if (shortMask != shortExpectedMask) {
            return SILENT;
        }

        if (longClosed < 4) {
            if (shortClosed > 0) {
                return SILENT;
            }
            // 1ст..4ст
            return longClosed + 1;
        }

        // 5ст: 4 длинные + 1 короткая, 6ст: все дырки.
        return Math.min(SOUND_COUNT, 4 + shortClosed);
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
