package tatar.eljah;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


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
    private final List<float[]> demoSpectrumFrames = new ArrayList<float[]>();
    private final List<Integer> demoSpectrumNotes = new ArrayList<Integer>();
    private int demoSpectrumSampleRate = 44100;
    private int demoSpectrumFrameDurationMs = 23;
    private int lastDemoFrameIndex = -1;
    private final Runnable demoSpectrumTicker = new Runnable() {
        @Override
        public void run() {
            if (spectroAssistMode != SpectroAssistMode.DEMO || demoPlayer == null || demoSpectrumFrames.isEmpty()) {
                return;
            }
            int frameIndex = currentDemoFrameIndex();
            if (frameIndex != lastDemoFrameIndex && frameIndex >= 0 && frameIndex < demoSpectrumFrames.size()) {
                lastDemoFrameIndex = frameIndex;
                spectrogramView.pushExternalSpectrumFrame(demoSpectrumFrames.get(frameIndex), demoSpectrumSampleRate);
                int note = demoSpectrumNotes.get(frameIndex);
                spectrogramView.setActiveSoundNumber(note);
                gameView.setHighlightedSchematicHole(mapSoundToHole(note));
            }
            spectrogramView.postDelayed(this, 16L);
        }
    };

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
        if (spectroAssistMode == SpectroAssistMode.DEMO) {
            return;
        }

        int soundNumber = mapPatternToSoundNumber(lastPattern);

        if (spectroAssistMode == SpectroAssistMode.TUNING_MIC) {
            if (!airOn) {
                stopAllSoundsImmediately();
                return;
            }
            playSound(soundNumber);
            return;
        }

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
        return FingeringMapper.mapPatternToSoundNumber(
            pattern,
            displayMode == ShuvyrGameView.DisplayMode.SCHEMATIC
        );
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
        spectrogramView.stopSyntheticFeedPreservingHistory();
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
        spectrogramView.stopSyntheticFeedPreservingHistory();
        buildDemoSpectrumFrames();

        demoPlayer = MediaPlayer.create(this, R.raw.demo);
        if (demoPlayer == null) {
            stopSpectroAssist();
            updateSpectroAssistUi();
            renderSoundState();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            demoPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        } else {
            demoPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
        }

        demoPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                stopSpectroAssist();
                updateSpectroAssistUi();
                renderSoundState();
            }
        });

        try {
            demoPlayer.start();
        } catch (IllegalStateException ignored) {
            stopSpectroAssist();
            updateSpectroAssistUi();
            renderSoundState();
            return;
        }

        boolean visualizerReady = setupDemoVisualizer();
        if (visualizerReady) {
            spectrogramView.setExternalFeedEnabled(true);
            lastDemoFrameIndex = -1;
            spectrogramView.removeCallbacks(demoSpectrumTicker);
        } else if (!demoSpectrumFrames.isEmpty()) {
            spectrogramView.setExternalFeedEnabled(true);
            lastDemoFrameIndex = -1;
            spectrogramView.removeCallbacks(demoSpectrumTicker);
            spectrogramView.post(demoSpectrumTicker);
        } else {
            spectrogramView.setExternalFeedEnabled(false);
            spectrogramView.setActiveSoundNumber(1);
            spectrogramView.setAirOn(true);
            gameView.setHighlightedSchematicHole(-1);
        }
    }

    private boolean setupDemoVisualizer() {
        if (demoPlayer == null) {
            return false;
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

                    magnitudes[0] = Math.abs(fft[0]);
                    if (bins > 1) {
                        magnitudes[1] = Math.abs(fft[1]);
                    }

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

                    final int sr = samplingRate > 0 ? (samplingRate / 1000) : 44100;
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
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void stopDemoMode() {
        spectrogramView.removeCallbacks(demoSpectrumTicker);
        lastDemoFrameIndex = -1;
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

    private int currentDemoFrameIndex() {
        int positionMs = 0;
        try {
            positionMs = Math.max(0, demoPlayer.getCurrentPosition());
        } catch (IllegalStateException ignored) {
        }
        int index = demoSpectrumFrameDurationMs > 0 ? (positionMs / demoSpectrumFrameDurationMs) : 0;
        if (index >= demoSpectrumFrames.size()) {
            return demoSpectrumFrames.size() - 1;
        }
        return index;
    }

    private void buildDemoSpectrumFrames() {
        if (!demoSpectrumFrames.isEmpty()) {
            return;
        }
        short[] samples = readMonoPcm16Wav(R.raw.demo);
        if (samples == null || samples.length == 0) {
            return;
        }

        final List<float[]> frames = new ArrayList<float[]>();
        final List<Integer> notes = new ArrayList<Integer>();
        final int sampleRate = demoSpectrumSampleRate;

        pitchAnalyzer.analyzePcm(samples, sampleRate, new PitchAnalyzer.PitchListener() {
            @Override
            public void onPitch(float pitchHz) {
                notes.add(nearestSoundNumber(pitchHz));
            }
        }, new PitchAnalyzer.SpectrumListener() {
            @Override
            public void onSpectrum(float[] magnitudes, int sampleRate) {
                float[] copy = new float[magnitudes.length];
                System.arraycopy(magnitudes, 0, copy, 0, magnitudes.length);
                frames.add(copy);
            }
        });

        if (frames.isEmpty()) {
            return;
        }

        while (notes.size() < frames.size()) {
            int fallback = notes.isEmpty() ? 1 : notes.get(notes.size() - 1);
            notes.add(fallback);
        }

        demoSpectrumFrames.clear();
        demoSpectrumFrames.addAll(frames);
        demoSpectrumNotes.clear();
        demoSpectrumNotes.addAll(notes);
        demoSpectrumFrameDurationMs = Math.max(1, Math.round(512f * 1000f / sampleRate));
    }

    private short[] readMonoPcm16Wav(int resId) {
        InputStream input = null;
        try {
            input = getResources().openRawResource(resId);
            byte[] data = readAllBytes(input);
            if (data.length < 44) {
                return null;
            }
            if (!(match(data, 0, "RIFF") && match(data, 8, "WAVE"))) {
                return null;
            }

            int offset = 12;
            int sampleRate = 0;
            int channels = 1;
            int bitsPerSample = 16;
            int dataOffset = -1;
            int dataSize = 0;

            while (offset + 8 <= data.length) {
                int chunkSize = littleEndianInt(data, offset + 4);
                int chunkDataOffset = offset + 8;
                if (chunkDataOffset + chunkSize > data.length) {
                    break;
                }
                if (match(data, offset, "fmt ")) {
                    int audioFormat = littleEndianShort(data, chunkDataOffset);
                    channels = littleEndianShort(data, chunkDataOffset + 2);
                    sampleRate = littleEndianInt(data, chunkDataOffset + 4);
                    bitsPerSample = littleEndianShort(data, chunkDataOffset + 14);
                    if (audioFormat != 1) {
                        return null;
                    }
                } else if (match(data, offset, "data")) {
                    dataOffset = chunkDataOffset;
                    dataSize = chunkSize;
                    break;
                }
                offset = chunkDataOffset + chunkSize + (chunkSize % 2);
            }

            if (sampleRate <= 0 || dataOffset < 0 || bitsPerSample != 16 || dataSize <= 0) {
                return null;
            }
            demoSpectrumSampleRate = sampleRate;

            int frameSizeBytes = channels * 2;
            int frames = dataSize / frameSizeBytes;
            short[] mono = new short[frames];
            for (int i = 0; i < frames; i++) {
                int frameOffset = dataOffset + i * frameSizeBytes;
                int sample = littleEndianShort(data, frameOffset);
                mono[i] = (short) sample;
            }
            return mono;
        } catch (IOException ignored) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean match(byte[] data, int offset, String value) {
        if (offset < 0 || offset + value.length() > data.length) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if ((byte) value.charAt(i) != data[offset + i]) {
                return false;
            }
        }
        return true;
    }

    private int littleEndianShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private int littleEndianInt(byte[] data, int offset) {
        return (data[offset] & 0xFF)
            | ((data[offset + 1] & 0xFF) << 8)
            | ((data[offset + 2] & 0xFF) << 16)
            | ((data[offset + 3] & 0xFF) << 24);
    }

    private void stopSpectroAssist() {
        pitchAnalyzer.stop();
        stopDemoMode();
        spectroAssistMode = SpectroAssistMode.OFF;
        spectrogramView.setExternalFeedEnabled(false);
        spectrogramView.stopSyntheticFeedPreservingHistory();
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
