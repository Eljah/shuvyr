package tatar.eljah;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;

import tatar.eljah.shuvyr.R;

public class MainActivity extends AppCompatActivity implements ShuvyrGameView.OnFingeringChangeListener {
    private static final int SOUND_COUNT = 6;

    private final SustainedWavPlayer[] players = new SustainedWavPlayer[SOUND_COUNT];
    private int activeSoundNumber = -1;
    private int releasingSoundNumber = -1;
    private int lastPattern = 0;
    private boolean airOn = false;

    private ShuvyrGameView gameView;
    private SpectrogramView spectrogramView;
    private ImageButton modeToggle;
    private ShuvyrGameView.DisplayMode displayMode = ShuvyrGameView.DisplayMode.NORMAL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        for (int i = 0; i < resources.length; i++) {
            players[i] = new SustainedWavPlayer(this, resources[i]);
        }

        bindUiActions();
        updateModeUi();
        renderSoundState();
    }

    private void bindUiActions() {
        final ImageButton lipsButton = findViewById(R.id.lips_button);
        lipsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                airOn = !airOn;
                if (!airOn) {
                    stopAirWithRelease();
                }
                lipsButton.setAlpha(airOn ? 1.0f : 0.55f);
                renderSoundState();
            }
        });
        lipsButton.setAlpha(0.55f);

        modeToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                displayMode = displayMode == ShuvyrGameView.DisplayMode.NORMAL
                    ? ShuvyrGameView.DisplayMode.SCHEMATIC
                    : ShuvyrGameView.DisplayMode.NORMAL;
                gameView.setDisplayMode(displayMode);
                stopAllSoundsImmediately();
                updateModeUi();
                renderSoundState();
            }
        });
    }


    private void updateModeUi() {
        boolean schematic = displayMode == ShuvyrGameView.DisplayMode.SCHEMATIC;
        spectrogramView.setVisibility(schematic ? View.VISIBLE : View.GONE);
        modeToggle.setImageResource(schematic ? R.drawable.ic_mode_bagpipe : R.drawable.ic_mode_spectrogram);
        modeToggle.setContentDescription(getString(schematic
            ? R.string.main_mode_schematic
            : R.string.main_mode_normal));

        final int bottomInset = schematic ? spectrogramView.getLayoutParams().height : 0;
        gameView.setBottomInsetPx(bottomInset);
    }
    @Override
    public void onFingeringChanged(int closedCount, int pattern) {
        lastPattern = pattern;
        renderSoundState();
    }

    private void renderSoundState() {
        int soundNumber = mapPatternToSoundNumber(lastPattern);

        if (!airOn) {
            spectrogramView.setAirOn(false);
            stopAllSoundsImmediately();
            return;
        }

        spectrogramView.setActiveSoundNumber(soundNumber);
        spectrogramView.setAirOn(true);
        playSound(soundNumber);
    }

    private void playSound(int soundNumber) {
        if (soundNumber == activeSoundNumber) {
            return;
        }
        SustainedWavPlayer next = players[soundNumber - 1];
        if (activeSoundNumber == -1) {
            stopPreviousReleaseIfAny();
            next.playSustain();
        } else {
            hardStopActive();
            stopPreviousReleaseIfAny();
            next.playLoopOnly();
        }
        activeSoundNumber = soundNumber;
    }

    private int mapPatternToSoundNumber(int pattern) {
        if (displayMode == ShuvyrGameView.DisplayMode.SCHEMATIC) {
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

        if (pattern == (1 << 3) || pattern == (1 << 4) || pattern == ((1 << 3) | (1 << 4))) {
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

    private void hardStopActive() {
        if (activeSoundNumber < 1 || activeSoundNumber > SOUND_COUNT) {
            return;
        }
        players[activeSoundNumber - 1].hardStop();
        activeSoundNumber = -1;
    }

    private void stopAirWithRelease() {
        stopPreviousReleaseIfAny();
        moveActiveToRelease();
        activeSoundNumber = -1;
    }

    private void stopAllSoundsImmediately() {
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
        airOn = false;
        spectrogramView.setAirOn(false);
        stopAllSoundsImmediately();
    }

    @Override
    protected void onStop() {
        super.onStop();
        airOn = false;
        spectrogramView.setAirOn(false);
        stopAllSoundsImmediately();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        airOn = false;
        spectrogramView.setAirOn(false);
        stopAllSoundsImmediately();
        for (int i = 0; i < players.length; i++) {
            if (players[i] != null) {
                players[i].release();
            }
        }
    }
}
