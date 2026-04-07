package tatar.eljah;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import tatar.eljah.shuvyr.R;

public class MelodyTrainerActivity extends AppCompatActivity {
    private static final int[] GOT_SOUND_SEQUENCE = new int[] {
        3, 3, 5, 2, 3, 5, 2, 3, 6, 6, 5, 2, 3, 5, 2, 3
    };
    private static final int[] GOT_STEP_DURATION_MS = new int[] {
        520, 520, 980, 420, 520, 980, 420, 520,
        700, 700, 980, 420, 520, 980, 420, 520
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable completionReset = new Runnable() {
        @Override
        public void run() {
            currentStep = 0;
            btnPlayPause.setText(R.string.trainer_start);
            textStatus.setText(getString(R.string.trainer_ready));
            gameView.setHighlightedSchematicHole(-1);
        }
    };

    private final Runnable playbackStep = new Runnable() {
        @Override
        public void run() {
            showStep(currentStep);
            int stepDurationMs = GOT_STEP_DURATION_MS[currentStep];
            currentStep++;
            if (currentStep >= GOT_SOUND_SEQUENCE.length) {
                isPlaying = false;
                handler.postDelayed(completionReset, stepDurationMs);
                return;
            }
            handler.postDelayed(this, stepDurationMs);
        }
    };

    private ShuvyrGameView gameView;
    private TextView textStatus;
    private Button btnPlayPause;
    private int currentStep = 0;
    private boolean isPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_melody_trainer);

        Spinner melodySpinner = findViewById(R.id.spinner_melodies);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        textStatus = findViewById(R.id.text_current_hint);
        gameView = findViewById(R.id.trainer_shuvyr_view);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
            this,
            R.array.trainer_melodies,
            android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        melodySpinner.setAdapter(adapter);

        gameView.setDisplayMode(ShuvyrGameView.DisplayMode.SCHEMATIC);
        gameView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                return true;
            }
        });

        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlayback();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPlayback();
    }

    private void togglePlayback() {
        if (isPlaying) {
            stopPlayback();
            return;
        }
        isPlaying = true;
        btnPlayPause.setText(R.string.trainer_pause);
        handler.removeCallbacks(playbackStep);
        handler.post(playbackStep);
    }

    private void stopPlayback() {
        isPlaying = false;
        handler.removeCallbacks(playbackStep);
        handler.removeCallbacks(completionReset);
        currentStep = 0;
        btnPlayPause.setText(R.string.trainer_start);
        textStatus.setText(getString(R.string.trainer_ready));
        gameView.setHighlightedSchematicHole(-1);
    }

    private void showStep(int stepIndex) {
        int sound = GOT_SOUND_SEQUENCE[stepIndex];
        int hole = mapSoundToHole(sound);
        gameView.setHighlightedSchematicHole(hole);

        if (sound == 1) {
            textStatus.setText(getString(R.string.trainer_step_open, stepIndex + 1));
            return;
        }
        textStatus.setText(getString(R.string.trainer_step_close, stepIndex + 1, hole + 1));
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
}
