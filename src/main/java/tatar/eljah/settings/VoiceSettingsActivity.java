package tatar.eljah.settings;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import tatar.eljah.R;
import tatar.eljah.tts.TtsVoicePreferences;

public class VoiceSettingsActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final Locale TARGET_LOCALE = Locale.forLanguageTag("vi-VN");

    private Spinner voiceSpinner;
    private ArrayAdapter<String> voiceAdapter;
    private final List<VoiceOption> voiceOptions = new ArrayList<>();
    private boolean isUpdatingVoices;
    private TextToSpeech textToSpeech;
    private String pendingVoiceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_settings);
        setTitle(R.string.title_voice_settings);

        RadioGroup genderGroup = findViewById(R.id.radio_voice_gender);
        String current = TtsVoicePreferences.getVoiceGender(this);
        if (TtsVoicePreferences.VOICE_GENDER_MALE.equals(current)) {
            genderGroup.check(R.id.radio_voice_gender_male);
        } else if (TtsVoicePreferences.VOICE_GENDER_FEMALE.equals(current)) {
            genderGroup.check(R.id.radio_voice_gender_female);
        } else {
            genderGroup.check(R.id.radio_voice_gender_auto);
        }

        genderGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                String selection = TtsVoicePreferences.VOICE_GENDER_AUTO;
                if (checkedId == R.id.radio_voice_gender_male) {
                    selection = TtsVoicePreferences.VOICE_GENDER_MALE;
                } else if (checkedId == R.id.radio_voice_gender_female) {
                    selection = TtsVoicePreferences.VOICE_GENDER_FEMALE;
                }
                TtsVoicePreferences.setVoiceGender(VoiceSettingsActivity.this, selection);
            }
        });

        pendingVoiceName = TtsVoicePreferences.getVoiceName(this);
        setupVoiceSpinner();
        textToSpeech = new TextToSpeech(this, this, "com.google.android.tts");
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        super.onDestroy();
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || textToSpeech == null) {
            return;
        }
        int languageStatus = textToSpeech.setLanguage(TARGET_LOCALE);
        Set<Voice> voices = textToSpeech.getVoices();
        if (voices == null || voices.isEmpty()) {
            return;
        }
        List<Voice> candidates = new ArrayList<>();
        for (Voice voice : voices) {
            if (voice == null || voice.isNetworkConnectionRequired()) {
                continue;
            }
            Locale voiceLocale = voice.getLocale();
            if (languageStatus != TextToSpeech.LANG_MISSING_DATA
                    && languageStatus != TextToSpeech.LANG_NOT_SUPPORTED
                    && voiceLocale != null
                    && TARGET_LOCALE.getLanguage().equals(voiceLocale.getLanguage())) {
                candidates.add(voice);
            }
        }
        if (candidates.isEmpty()) {
            for (Voice voice : voices) {
                if (voice == null || voice.isNetworkConnectionRequired()) {
                    continue;
                }
                candidates.add(voice);
            }
        }
        updateVoiceOptions(candidates);
    }

    private void setupVoiceSpinner() {
        voiceSpinner = findViewById(R.id.spinner_voice_selection);
        voiceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<String>());
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voiceSpinner.setAdapter(voiceAdapter);
        voiceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingVoices || position < 0 || position >= voiceOptions.size()) {
                    return;
                }
                VoiceOption option = voiceOptions.get(position);
                TtsVoicePreferences.setVoiceName(VoiceSettingsActivity.this, option.voiceName);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        updateVoiceOptions(new ArrayList<Voice>());
    }

    private void updateVoiceOptions(List<Voice> voices) {
        isUpdatingVoices = true;
        voiceOptions.clear();
        voiceOptions.add(new VoiceOption(getString(R.string.voice_selection_auto), null));
        for (Voice voice : voices) {
            voiceOptions.add(new VoiceOption(voice.getName(), voice.getName()));
        }
        voiceAdapter.clear();
        for (VoiceOption option : voiceOptions) {
            voiceAdapter.add(option.label);
        }
        voiceAdapter.notifyDataSetChanged();
        voiceSpinner.setSelection(findPreferredVoiceIndex());
        isUpdatingVoices = false;
    }

    private int findPreferredVoiceIndex() {
        if (pendingVoiceName == null) {
            return 0;
        }
        for (int i = 0; i < voiceOptions.size(); i++) {
            if (pendingVoiceName.equals(voiceOptions.get(i).voiceName)) {
                return i;
            }
        }
        return 0;
    }

    private static class VoiceOption {
        private final String label;
        private final String voiceName;

        private VoiceOption(String label, String voiceName) {
            this.label = label;
            this.voiceName = voiceName;
        }
    }
}
