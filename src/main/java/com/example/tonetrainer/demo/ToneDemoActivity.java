package com.example.tonetrainer.demo;

import android.media.MediaPlayer;
import android.os.Bundle;

import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.example.tonetrainer.R;
import com.example.tonetrainer.audio.PitchAnalyzer;
import com.example.tonetrainer.model.VietnameseSyllable;
import com.example.tonetrainer.ui.ToneVisualizerView;

import java.util.ArrayList;
import java.util.List;

public class ToneDemoActivity extends AppCompatActivity {

    private ToneVisualizerView visualizerView;
    private MediaPlayer mediaPlayer;
    private PitchAnalyzer pitchAnalyzer;
    private final List<Float> currentPitch = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tone_demo);

        visualizerView = findViewById(R.id.toneVisualizerView);
        pitchAnalyzer = new PitchAnalyzer();

        RecyclerView recyclerView = findViewById(R.id.recycler_syllables);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<VietnameseSyllable> syllables = createSyllables();
        SyllableAdapter adapter = new SyllableAdapter(syllables, new SyllableAdapter.OnSyllableClickListener() {
            @Override
            public void onSyllableClick(VietnameseSyllable syllable) {
                playSyllable(syllable);
            }
        });
        recyclerView.setAdapter(adapter);
    }

    private List<VietnameseSyllable> createSyllables() {
        List<VietnameseSyllable> list = new ArrayList<>();
        list.add(new VietnameseSyllable("ma", "ngang", R.raw.ma1));
        list.add(new VietnameseSyllable("má", "sắc", R.raw.ma2));
        list.add(new VietnameseSyllable("mà", "huyền", R.raw.ma3));
        return list;
    }

    private void playSyllable(VietnameseSyllable syllable) {
        stopPlayback();

        currentPitch.clear();
        visualizerView.setReferenceData(currentPitch);
        visualizerView.setUserData(null);

        mediaPlayer = MediaPlayer.create(this, syllable.getAudioResId());
        if (mediaPlayer == null) {
            return;
        }

        pitchAnalyzer.startRealtimePitch(new PitchAnalyzer.PitchListener() {
            @Override
            public void onPitch(final float pitchHz) {
                currentPitch.add(pitchHz);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        visualizerView.setReferenceData(currentPitch);
                    }
                });
            }
        });

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                pitchAnalyzer.stop();
            }
        });

        mediaPlayer.start();
    }

    private void stopPlayback() {
        pitchAnalyzer.stop();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPlayback();
    }
}
