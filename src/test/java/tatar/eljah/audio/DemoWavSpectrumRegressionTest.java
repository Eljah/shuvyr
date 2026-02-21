package tatar.eljah.audio;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class DemoWavSpectrumRegressionTest {
    private static final int FRAME_SIZE = 1024;
    private static final int HOP_SIZE = 512;
    private static final String DEMO_WAV = "src/main/res/raw/demo.wav";
    private static final String REFERENCE_BINS = "src/test/resources/demo_spectrum_reference_bins.txt";

    public void javaSpectrumShouldMatchIndependentPythonReference() throws Exception {
        PcmData pcm = readWavMono(new File(DEMO_WAV));
        final List<Integer> actualDominantBins = new ArrayList<Integer>();

        PitchAnalyzer analyzer = new PitchAnalyzer();
        analyzer.analyzePcm(pcm.samples, pcm.sampleRate, null, new PitchAnalyzer.SpectrumListener() {
            @Override
            public void onSpectrum(float[] magnitudes, int sampleRate) {
                actualDominantBins.add(argMax(magnitudes));
            }
        });

        List<Integer> expectedDominantBins = readReferenceBins(new File(REFERENCE_BINS));

        if (actualDominantBins.size() != expectedDominantBins.size()) {
            throw new AssertionError("Frame count mismatch: java=" + actualDominantBins.size()
                    + " python=" + expectedDominantBins.size());
        }

        int mismatches = 0;
        long absDiffSum = 0L;
        for (int i = 0; i < actualDominantBins.size(); i++) {
            int actual = actualDominantBins.get(i);
            int expected = expectedDominantBins.get(i);
            if (actual != expected) {
                mismatches++;
                absDiffSum += Math.abs(actual - expected);
            }
        }

        float mismatchRate = actualDominantBins.isEmpty() ? 0f : (mismatches * 1f / actualDominantBins.size());
        float meanAbsBinDiff = mismatches == 0 ? 0f : (absDiffSum * 1f / mismatches);

        if (mismatchRate > 0.02f) {
            throw new AssertionError("Too many dominant-bin mismatches: " + mismatches + "/"
                    + actualDominantBins.size() + " (rate=" + mismatchRate + ")");
        }
        if (meanAbsBinDiff > 2.0f) {
            throw new AssertionError("Mean absolute bin difference is too large: " + meanAbsBinDiff);
        }

        int expectedFrames = Math.max(0, (pcm.samples.length - FRAME_SIZE) / HOP_SIZE + 1);
        if (actualDominantBins.size() != expectedFrames) {
            throw new AssertionError("Unexpected frame count from analyzePcm: " + actualDominantBins.size()
                    + " expected=" + expectedFrames);
        }
    }

    private int argMax(float[] values) {
        if (values == null || values.length == 0) {
            return 0;
        }
        int best = 0;
        float bestValue = values[0];
        for (int i = 1; i < values.length; i++) {
            if (values[i] > bestValue) {
                bestValue = values[i];
                best = i;
            }
        }
        return best;
    }

    private List<Integer> readReferenceBins(File file) throws Exception {
        List<Integer> out = new ArrayList<Integer>();
        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                out.add(Integer.parseInt(line));
            }
        } finally {
            br.close();
        }
        return out;
    }

    private PcmData readWavMono(File wavFile) throws Exception {
        AudioInputStream stream = AudioSystem.getAudioInputStream(wavFile);
        try {
            javax.sound.sampled.AudioFormat format = stream.getFormat();
            int channels = format.getChannels();
            int sampleRate = (int) format.getSampleRate();
            int sampleSize = format.getSampleSizeInBits();
            boolean bigEndian = format.isBigEndian();

            byte[] bytes = readAllBytes(stream);
            int frameSizeBytes = format.getFrameSize();
            int totalFrames = bytes.length / frameSizeBytes;
            short[] mono = new short[totalFrames];

            for (int frame = 0; frame < totalFrames; frame++) {
                int base = frame * frameSizeBytes;
                if (sampleSize == 16) {
                    int b0 = bytes[base] & 0xFF;
                    int b1 = bytes[base + 1] & 0xFF;
                    short sample;
                    if (bigEndian) {
                        sample = (short) ((b0 << 8) | b1);
                    } else {
                        sample = (short) ((b1 << 8) | b0);
                    }
                    mono[frame] = sample;
                } else if (sampleSize == 32) {
                    int i0 = bytes[base] & 0xFF;
                    int i1 = bytes[base + 1] & 0xFF;
                    int i2 = bytes[base + 2] & 0xFF;
                    int i3 = bytes[base + 3] & 0xFF;
                    int bits;
                    if (bigEndian) {
                        bits = (i0 << 24) | (i1 << 16) | (i2 << 8) | i3;
                    } else {
                        bits = (i3 << 24) | (i2 << 16) | (i1 << 8) | i0;
                    }
                    float sampleFloat = Float.intBitsToFloat(bits);
                    if (sampleFloat > 1f) {
                        sampleFloat = 1f;
                    } else if (sampleFloat < -1f) {
                        sampleFloat = -1f;
                    }
                    mono[frame] = (short) Math.round(sampleFloat * 32767f);
                } else {
                    throw new IllegalArgumentException("Unsupported WAV sample size: " + sampleSize);
                }
            }

            if (channels != 1) {
                throw new IllegalArgumentException("Expected mono WAV, got channels=" + channels);
            }

            return new PcmData(mono, sampleRate);
        } finally {
            stream.close();
        }
    }

    private byte[] readAllBytes(AudioInputStream stream) throws Exception {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int read;
        while ((read = stream.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static class PcmData {
        final short[] samples;
        final int sampleRate;

        PcmData(short[] samples, int sampleRate) {
            this.samples = samples;
            this.sampleRate = sampleRate;
        }
    }

    public static void main(String[] args) throws Exception {
        DemoWavSpectrumRegressionTest test = new DemoWavSpectrumRegressionTest();
        test.javaSpectrumShouldMatchIndependentPythonReference();
        System.out.println("demo-spectrum-regression=OK");
    }
}
