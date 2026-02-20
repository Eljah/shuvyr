package tatar.eljah;

import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;


import tatar.eljah.shuvyr.R;

public class MainActivity extends AppCompatActivity implements ShuvyrGameView.OnFingeringChangeListener {
    private static final long FADE_DURATION_MS = 140;
    private static final long FADE_FRAME_MS = 16;

    private SoundPool soundPool;
    private int sampleId;
    private boolean sampleLoaded;

    private int activeStreamId;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable fadeRunnable;

    private TextView noteLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        noteLabel = findViewById(R.id.current_note_label);
        ShuvyrGameView gameView = findViewById(R.id.shuvyr_view);
        gameView.setOnFingeringChangeListener(this);

        soundPool = createSoundPool();
        sampleId = soundPool.load(this, R.raw.ma1, 1);
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sample, int status) {
                sampleLoaded = (sample == sampleId && status == 0);
            }
        });
    }

    @Override
    public void onFingeringChanged(int closedCount, int pattern) {
        String note = mapPatternToNote(pattern);
        noteLabel.setText(getString(R.string.current_note_template, note, closedCount));

        if (!sampleLoaded) {
            return;
        }

        float targetRate = mapPatternToRate(pattern);
        crossfadeToRate(targetRate);
    }

    private float mapPatternToRate(int pattern) {
        int normalized = Math.max(0, Math.min(63, pattern));
        return 0.62f + (normalized / 63f) * 1.28f;
    }

    private String mapPatternToNote(int pattern) {
        String[] notes = {"G", "A", "B", "C", "D", "E", "F#", "G2"};
        return notes[Math.abs(pattern) % notes.length];
    }

    private void crossfadeToRate(final float targetRate) {
        if (activeStreamId == 0) {
            activeStreamId = soundPool.play(sampleId, 1f, 1f, 1, -1, targetRate);
            return;
        }

        final int oldStream = activeStreamId;
        final int newStream = soundPool.play(sampleId, 0f, 0f, 2, -1, targetRate);
        if (newStream == 0) {
            soundPool.setRate(oldStream, targetRate);
            return;
        }

        activeStreamId = newStream;
        if (fadeRunnable != null) {
            handler.removeCallbacks(fadeRunnable);
        }

        final long start = System.currentTimeMillis();
        fadeRunnable = new Runnable() {
            @Override
            public void run() {
                float t = (System.currentTimeMillis() - start) / (float) FADE_DURATION_MS;
                t = Math.max(0f, Math.min(1f, t));
                float in = t;
                float out = 1f - t;
                soundPool.setVolume(newStream, in, in);
                soundPool.setVolume(oldStream, out, out);

                if (t < 1f) {
                    handler.postDelayed(this, FADE_FRAME_MS);
                } else {
                    soundPool.stop(oldStream);
                }
            }
        };
        handler.post(fadeRunnable);
    }

    private SoundPool createSoundPool() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
            return new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build();
        }
        return new SoundPool(4, android.media.AudioManager.STREAM_MUSIC, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (soundPool != null) {
            soundPool.release();
        }
    }
}
