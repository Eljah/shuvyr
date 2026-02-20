package tatar.eljah;

import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import tatar.eljah.shuvyr.R;

public class MainActivity extends AppCompatActivity implements ShuvyrGameView.OnFingeringChangeListener {
    private SoundPool soundPool;
    private final int[] sampleIds = new int[6];
    private int loadedSamples;

    private int activeStreamId;

    private TextView noteLabel;
    private final Map<Integer, Integer> fingeringToSample = new HashMap<Integer, Integer>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        noteLabel = findViewById(R.id.current_note_label);
        ShuvyrGameView gameView = findViewById(R.id.shuvyr_view);
        gameView.setOnFingeringChangeListener(this);

        initFingeringMap();

        soundPool = createSoundPool();
        sampleIds[0] = soundPool.load(this, R.raw.shuvyr_1, 1);
        sampleIds[1] = soundPool.load(this, R.raw.shuvyr_2, 1);
        sampleIds[2] = soundPool.load(this, R.raw.shuvyr_3, 1);
        sampleIds[3] = soundPool.load(this, R.raw.shuvyr_4, 1);
        sampleIds[4] = soundPool.load(this, R.raw.shuvyr_5, 1);
        sampleIds[5] = soundPool.load(this, R.raw.shuvyr_6, 1);
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool pool, int sampleId, int status) {
                if (status == 0) {
                    loadedSamples++;
                }
            }
        });
    }

    @Override
    public void onFingeringChanged(int closedCount, int pattern) {
        int soundNumber = mapPatternToSoundNumber(pattern, closedCount);
        noteLabel.setText(getString(R.string.current_note_template, String.valueOf(soundNumber), closedCount));

        if (loadedSamples < sampleIds.length) {
            return;
        }

        int sampleId = sampleIds[soundNumber - 1];
        if (activeStreamId != 0) {
            soundPool.stop(activeStreamId);
        }
        activeStreamId = soundPool.play(sampleId, 1f, 1f, 1, -1, 1f);
    }

    private void initFingeringMap() {
        // Индексы дырочек в ShuvyrGameView: [L1, L2, L3, L4, R4, BOTTOM]
        fingeringToSample.put(0b000000, 1);
        fingeringToSample.put(0b000001, 2);
        fingeringToSample.put(0b010011, 3);
        fingeringToSample.put(0b001111, 4);
        fingeringToSample.put(0b011111, 5);
        fingeringToSample.put(0b111111, 6);
    }

    private int mapPatternToSoundNumber(int pattern, int closedCount) {
        Integer mapped = fingeringToSample.get(pattern & 0b111111);
        if (mapped != null) {
            return mapped;
        }
        return Math.max(1, Math.min(6, closedCount + 1));
    }

    private SoundPool createSoundPool() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
            return new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(audioAttributes)
                .build();
        }
        return new SoundPool(3, android.media.AudioManager.STREAM_MUSIC, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (soundPool != null) {
            soundPool.release();
        }
    }
}
