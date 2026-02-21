package tatar.eljah;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import tatar.eljah.shuvyr.R;

public class MainActivity extends AppCompatActivity implements ShuvyrGameView.OnFingeringChangeListener {
    private static final int SOUND_COUNT = 6;

    private final SustainedWavPlayer[] players = new SustainedWavPlayer[SOUND_COUNT];
    private int activeSoundNumber = -1;
    private int releasingSoundNumber = -1;

    private TextView noteLabel;
    private ShuvyrGameView gameView;
    private SpectrumView spectrumView;
    private Button modeButton;

    private boolean schematicMode;
    private boolean forceZeroSound;
    private int lastPattern;
    private int lastClosedCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        noteLabel = findViewById(R.id.current_note_label);
        gameView = findViewById(R.id.shuvyr_view);
        spectrumView = findViewById(R.id.spectrum_view);
        modeButton = findViewById(R.id.mode_button);
        ImageButton lipsButton = findViewById(R.id.lips_button);

        gameView.setOnFingeringChangeListener(this);

        int[] resources = new int[] {
            R.raw.shuvyr_1,
            R.raw.shuvyr_2,
            R.raw.shuvyr_3,
            R.raw.shuvyr_4,
            R.raw.shuvyr_5,
            R.raw.shuvyr_6
        };

        float[] attackEnd = new float[] {0.28f, 0.32f, 0.24f, 0.25f, 0.32f, 0.20f};
        float[] releaseStart = new float[] {2.62f, 2.83f, 2.41f, 2.47f, 2.34f, 2.69f};

        for (int i = 0; i < resources.length; i++) {
            players[i] = new SustainedWavPlayer(this, resources[i], attackEnd[i], releaseStart[i]);
        }

        modeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                schematicMode = !schematicMode;
                gameView.setSchematicMode(schematicMode);
                modeButton.setText(schematicMode ? "Схема ON" : "Схема OFF");
                spectrumView.setVisibility(schematicMode ? View.VISIBLE : View.GONE);
            }
        });

        lipsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                forceZeroSound = !forceZeroSound;
                if (!forceZeroSound) {
                    onFingeringChanged(lastClosedCount, lastPattern);
                    return;
                }
                startSound(1, lastClosedCount);
            }
        });
    }

    @Override
    public void onFingeringChanged(int closedCount, int pattern) {
        lastClosedCount = closedCount;
        lastPattern = pattern;

        if (forceZeroSound) {
            startSound(1, closedCount);
            return;
        }

        int soundNumber = mapPatternToSoundNumber(pattern);
        startSound(soundNumber, closedCount);
    }

    private void startSound(int soundNumber, int closedCount) {
        noteLabel.setText(getString(R.string.current_note_template, String.valueOf(soundNumber), closedCount));

        if (soundNumber == activeSoundNumber) {
            return;
        }

        stopPreviousReleaseIfAny();
        moveActiveToRelease();

        SustainedWavPlayer next = players[soundNumber - 1];
        next.playSustain();
        activeSoundNumber = soundNumber;
        spectrumView.setSoundNumber(soundNumber);
    }

    private int mapPatternToSoundNumber(int pattern) {
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
        return Math.min(SOUND_COUNT, 4 + shortClosed);
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

    private void moveActiveToRelease() {
        if (activeSoundNumber < 1 || activeSoundNumber > SOUND_COUNT) {
            return;
        }
        SustainedWavPlayer active = players[activeSoundNumber - 1];
        active.stopWithRelease();
        releasingSoundNumber = activeSoundNumber;
    }

    private void stopPreviousReleaseIfAny() {
        if (releasingSoundNumber < 1 || releasingSoundNumber > SOUND_COUNT) {
            return;
        }
        players[releasingSoundNumber - 1].hardStop();
        releasingSoundNumber = -1;
    }

    private void stopAllNow() {
        for (int i = 0; i < players.length; i++) {
            if (players[i] != null) {
                players[i].hardStop();
            }
        }
        activeSoundNumber = -1;
        releasingSoundNumber = -1;
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAllNow();
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
