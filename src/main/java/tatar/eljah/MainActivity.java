package tatar.eljah;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
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
    private int lastPattern = 0;

    private TextView noteLabel;
    private ShuvyrGameView gameView;
    private SpectrogramView spectrogramView;
    private Button modeToggle;
    private ShuvyrGameView.DisplayMode displayMode = ShuvyrGameView.DisplayMode.NORMAL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        noteLabel = findViewById(R.id.current_note_label);
        gameView = findViewById(R.id.shuvyr_view);
        spectrogramView = findViewById(R.id.spectrogram_view);
        modeToggle = findViewById(R.id.mode_toggle);
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

        bindUiActions();
    }

    private void bindUiActions() {
        final ImageButton lipsButton = findViewById(R.id.lips_button);
        lipsButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    playSound(1, getString(R.string.current_note_zero));
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    stopAllSounds();
                    updateByPattern(lastPattern);
                    return true;
                }
                return false;
            }
        });

        modeToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                displayMode = displayMode == ShuvyrGameView.DisplayMode.NORMAL
                    ? ShuvyrGameView.DisplayMode.SCHEMATIC
                    : ShuvyrGameView.DisplayMode.NORMAL;
                gameView.setDisplayMode(displayMode);
                boolean schematic = displayMode == ShuvyrGameView.DisplayMode.SCHEMATIC;
                spectrogramView.setVisibility(schematic ? View.VISIBLE : View.GONE);
                modeToggle.setText(schematic
                    ? getString(R.string.main_mode_schematic)
                    : getString(R.string.main_mode_normal));
                stopAllSounds();
                updateByPattern(0);
            }
        });
    }

    @Override
    public void onFingeringChanged(int closedCount, int pattern) {
        lastPattern = pattern;
        updateByPattern(pattern);
        int soundNumber = mapPatternToSoundNumber(pattern);
        noteLabel.setText(getString(R.string.current_note_template, String.valueOf(soundNumber), closedCount));
    }

    private void updateByPattern(int pattern) {
        int soundNumber = mapPatternToSoundNumber(pattern);
        playSound(soundNumber, String.valueOf(soundNumber));
    }

    private void playSound(int soundNumber, String shownNote) {
        int closedCount = countClosed(lastPattern);
        noteLabel.setText(getString(R.string.current_note_template, shownNote, closedCount));
        spectrogramView.setActiveSoundNumber(soundNumber);

        if (soundNumber == activeSoundNumber) {
            return;
        }
        stopPreviousReleaseIfAny();
        moveActiveToRelease();

        SustainedWavPlayer next = players[soundNumber - 1];
        next.playSustain();
        activeSoundNumber = soundNumber;
    }

    private int countClosed(int pattern) {
        int count = 0;
        for (int i = 0; i < SOUND_COUNT; i++) {
            if ((pattern & (1 << i)) != 0) {
                count++;
            }
        }
        return count;
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

    private void stopAllSounds() {
        for (int i = 0; i < players.length; i++) {
            if (players[i] != null) {
                players[i].hardStop();
            }
        }
        activeSoundNumber = -1;
        releasingSoundNumber = -1;
        spectrogramView.setActiveSoundNumber(0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAllSounds();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAllSounds();
        for (int i = 0; i < players.length; i++) {
            if (players[i] != null) {
                players[i].release();
            }
        }
    }
}
