package tatar.eljah;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;


import tatar.eljah.audio.PitchAnalyzer;
import tatar.eljah.shuvyr.R;

public class MainActivity extends AppCompatActivity implements ShuvyrGameView.OnFingeringChangeListener {
    private static final int SOUND_COUNT = 6;
    private static final int REQUEST_RECORD_AUDIO = 3301;
    private static final float[] NOTE_BASE_HZ = new float[] {160f, 98f, 538f, 496f, 469f, 96f};

    private enum SpectroAssistMode {
        OFF,
        TUNING_MIC,
        DEMO
    }

    private final SustainedWavPlayer[] players = new SustainedWavPlayer[SOUND_COUNT];
    private int activeSoundNumber = -1;
    private int releasingSoundNumber = -1;
    private int lastPattern = 0;
    private boolean airOn = false;

    private ShuvyrGameView gameView;
    private SpectrogramView spectrogramView;
    private ImageButton modeToggle;
    private ImageButton spectroTuneToggle;
    private ImageButton spectroDemoToggle;
    private ShuvyrGameView.DisplayMode displayMode = ShuvyrGameView.DisplayMode.NORMAL;

    private final PitchAnalyzer pitchAnalyzer = new PitchAnalyzer();
    private SpectroAssistMode spectroAssistMode = SpectroAssistMode.OFF;
    private MediaPlayer demoPlayer;
    private Visualizer demoVisualizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gameView = findViewById(R.id.shuvyr_view);
        spectrogramView = findViewById(R.id.spectrogram_view);
        modeToggle = findViewById(R.id.mode_toggle);
        spectroTuneToggle = findViewById(R.id.spectro_tune_toggle);
        spectroDemoToggle = findViewById(R.id.spectro_demo_toggle);
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
                stopSpectroAssist();
                updateModeUi();
                renderSoundState();
            }
        });

        spectroTuneToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (spectroAssistMode == SpectroAssistMode.TUNING_MIC) {
                    stopSpectroAssist();
                    updateSpectroAssistUi();
                    renderSoundState();
                    return;
                }
                startMicTuningMode();
                updateSpectroAssistUi();
            }
        });

        spectroDemoToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (spectroAssistMode == SpectroAssistMode.DEMO) {
                    stopSpectroAssist();
                    updateSpectroAssistUi();
                    renderSoundState();
                    return;
                }
                startDemoMode();
                updateSpectroAssistUi();
            }
        });
    }

    private void updateModeUi() {
        boolean schematic = displayMode == ShuvyrGameView.DisplayMode.SCHEMATIC;
        spectrogramView.setVisibility(schematic ? View.VISIBLE : View.GONE);
        spectroTuneToggle.setVisibility(schematic ? View.VISIBLE : View.GONE);
        spectroDemoToggle.setVisibility(schematic ? View.VISIBLE : View.GONE);
        modeToggle.setImageResource(schematic ? R.drawable.ic_mode_bagpipe : R.drawable.ic_mode_spectrogram);
        modeToggle.setContentDescription(getString(schematic
            ? R.string.main_mode_schematic
            : R.string.main_mode_normal));

        final int bottomInset = schematic ? spectrogramView.getLayoutParams().height : 0;
        gameView.setBottomInsetPx(bottomInset);
        if (!schematic) {
            gameView.setHighlightedSchematicHole(-1);
        }
        updateSpectroAssistUi();
    }

    @Override
    public void onFingeringChanged(int closedCount, int pattern) {
        lastPattern = pattern;
        renderSoundState();
    }

    private void renderSoundState() {
        if (spectroAssistMode != SpectroAssistMode.OFF) {
            return;
        }

        int soundNumber = mapPatternToSoundNumber(lastPattern);

        if (!airOn) {
            spectrogramView.setAirOn(false);
            stopAllSoundsImmediately();
            return;
        }

        spectrogramView.setExternalFeedEnabled(false);
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

    private void startMicTuningMode() {
        if (displayMode != ShuvyrGameView.DisplayMode.SCHEMATIC) {
            return;
        }
        if (!hasMicPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            }
            return;
        }
        stopSpectroAssist();
        spectroAssistMode = SpectroAssistMode.TUNING_MIC;
        stopAllSoundsImmediately();
        spectrogramView.setAirOn(false);
        spectrogramView.setExternalFeedEnabled(true);
        pitchAnalyzer.startRealtimePitch(new PitchAnalyzer.PitchListener() {
            @Override
            public void onPitch(float pitchHz) {
                final int note = nearestSoundNumber(pitchHz);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        spectrogramView.setActiveSoundNumber(note);
                        gameView.setHighlightedSchematicHole(mapSoundToHole(note));
                    }
                });
            }
        }, new PitchAnalyzer.SpectrumListener() {
            @Override
            public void onSpectrum(final float[] magnitudes, final int sampleRate) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        spectrogramView.pushExternalSpectrumFrame(magnitudes, sampleRate);
                    }
                });
            }
        });
    }

    private void startDemoMode() {
        if (displayMode != ShuvyrGameView.DisplayMode.SCHEMATIC) {
            return;
        }
        stopSpectroAssist();
        spectroAssistMode = SpectroAssistMode.DEMO;
        stopAllSoundsImmediately();
        spectrogramView.setAirOn(false);
        spectrogramView.setExternalFeedEnabled(true);

        demoPlayer = MediaPlayer.create(this, R.raw.demo_long);
        if (demoPlayer != null) {
            demoPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stopSpectroAssist();
                    updateSpectroAssistUi();
                    renderSoundState();
                }
            });
            demoPlayer.start();
        }
        setupDemoVisualizer();
    }

    private void setupDemoVisualizer() {
        if (demoPlayer == null) {
            return;
        }
        try {
            demoVisualizer = new Visualizer(demoPlayer.getAudioSessionId());
            int captureSize = Visualizer.getCaptureSizeRange()[1];
            demoVisualizer.setCaptureSize(captureSize);
            int rate = Math.max(Visualizer.getMaxCaptureRate() / 2, 8000);
            demoVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    if (fft == null || fft.length < 4) {
                        return;
                    }
                    int bins = fft.length / 2;
                    float[] magnitudes = new float[bins];
                    float bestMag = 0f;
                    int bestBin = 1;
                    for (int i = 1; i < bins; i++) {
                        int idx = i * 2;
                        if (idx + 1 >= fft.length) {
                            break;
                        }
                        float re = fft[idx];
                        float im = fft[idx + 1];
                        float mag = (float) Math.sqrt(re * re + im * im);
                        magnitudes[i] = mag;
                        if (mag > bestMag) {
                            bestMag = mag;
                            bestBin = i;
                        }
                    }
                    final int sr = samplingRate > 0 ? samplingRate : 44100;
                    final float[] frame = magnitudes;
                    final float hz = bestBin * (sr / 2f) / Math.max(1, bins);
                    final int note = nearestSoundNumber(hz);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            spectrogramView.pushExternalSpectrumFrame(frame, sr);
                            spectrogramView.setActiveSoundNumber(note);
                            gameView.setHighlightedSchematicHole(mapSoundToHole(note));
                        }
                    });
                }
            }, rate, false, true);
            demoVisualizer.setEnabled(true);
        } catch (RuntimeException ignored) {
        }
    }

    private void stopDemoMode() {
        if (demoVisualizer != null) {
            try {
                demoVisualizer.setEnabled(false);
            } catch (RuntimeException ignored) {
            }
            demoVisualizer.release();
            demoVisualizer = null;
        }
        if (demoPlayer != null) {
            try {
                demoPlayer.stop();
            } catch (IllegalStateException ignored) {
            }
            demoPlayer.release();
            demoPlayer = null;
        }
    }

    private void stopSpectroAssist() {
        pitchAnalyzer.stop();
        stopDemoMode();
        spectroAssistMode = SpectroAssistMode.OFF;
        spectrogramView.setExternalFeedEnabled(false);
        gameView.setHighlightedSchematicHole(-1);
    }

    private void updateSpectroAssistUi() {
        boolean available = displayMode == ShuvyrGameView.DisplayMode.SCHEMATIC;
        spectroTuneToggle.setAlpha(spectroAssistMode == SpectroAssistMode.TUNING_MIC ? 1f : 0.6f);
        spectroDemoToggle.setAlpha(spectroAssistMode == SpectroAssistMode.DEMO ? 1f : 0.6f);
        spectroTuneToggle.setEnabled(available);
        spectroDemoToggle.setEnabled(available);
    }

    private boolean hasMicPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
            || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startMicTuningMode();
            updateSpectroAssistUi();
        }
    }

    private int nearestSoundNumber(float pitchHz) {
        int best = 1;
        float bestDiff = Float.MAX_VALUE;
        for (int i = 0; i < NOTE_BASE_HZ.length; i++) {
            float d = Math.abs(pitchHz - NOTE_BASE_HZ[i]);
            if (d < bestDiff) {
                bestDiff = d;
                best = i + 1;
            }
        }
        return best;
    }

    private int mapSoundToHole(int soundNumber) {
        switch (soundNumber) {
            case 2:
                return 0;
            case 3:
                return 1;
            case 4:
                return 2;
            case 5:
                return 4;
            case 6:
                return 5;
            default:
                return -1;
        }
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
        stopSpectroAssist();
        spectrogramView.setAirOn(false);
        stopAllSoundsImmediately();
    }

    @Override
    protected void onStop() {
        super.onStop();
        airOn = false;
        stopSpectroAssist();
        spectrogramView.setAirOn(false);
        stopAllSoundsImmediately();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        airOn = false;
        stopSpectroAssist();
        spectrogramView.setAirOn(false);
        stopAllSoundsImmediately();
        for (int i = 0; i < players.length; i++) {
            if (players[i] != null) {
                players[i].release();
            }
        }
    }
}
