package tatar.eljah;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DemoRecognitionSequenceTest {
    private static final int FRAME = 4096;
    private static final int HOP = 1024;
    private static final float[] NOTE_BASE_HZ = new float[] {160f, 98f, 538f, 496f, 469f, 96f};
    private static final Pattern ROW = Pattern.compile("^\\|\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\|\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\|\\s*([0-9]+(?:\\.[0-9]+)?)\\s*\\|\\s*([0-9]+)\\s*\\|(?:\\s*[-0-9]+\\s*\\|)?$");

    private static final class Segment {
        final double startSec;
        final double endSec;
        final int note;

        Segment(double startSec, double endSec, int note) {
            this.startSec = startSec;
            this.endSec = endSec;
            this.note = note;
        }
    }

    private static final class WavData {
        final int sampleRate;
        final float[] samples;

        WavData(int sampleRate, float[] samples) {
            this.sampleRate = sampleRate;
            this.samples = samples;
        }
    }

    public void recognitionShouldFollowDocumentedSegmentOrder() throws Exception {
        List<Segment> segments = readSegments(new File("docs/demo_note_segments.md"));
        WavData wav = readWavMono(new File("src/main/res/raw/demo.wav"));

        if (segments.isEmpty()) {
            throw new AssertionError("No segments found in docs/demo_note_segments.md");
        }

        int checkedNotes = 0;
        for (Segment s : segments) {
            if (s.note == 0) {
                continue;
            }
            int recognized = dominantRecognizedNoteForSegment(wav, s.startSec, s.endSec);
            if (recognized != s.note) {
                throw new AssertionError("Segment [" + s.startSec + ", " + s.endSec + "] expected note "
                        + s.note + " but recognized " + recognized);
            }

            int expectedHole = mapSoundToHole(s.note);
            int recognizedHole = mapSoundToHole(recognized);
            if (expectedHole != recognizedHole) {
                throw new AssertionError("Hole mismatch for segment note=" + s.note + " expectedHole="
                        + expectedHole + " recognizedHole=" + recognizedHole);
            }
            checkedNotes++;
        }

        if (checkedNotes < 10) {
            throw new AssertionError("Too few non-silence segments checked: " + checkedNotes);
        }
    }

    private int dominantRecognizedNoteForSegment(WavData wav, double startSec, double endSec) {
        int start = Math.max(0, (int) Math.floor(startSec * wav.sampleRate));
        int end = Math.min(wav.samples.length, (int) Math.ceil(endSec * wav.sampleRate));
        if (end - start < FRAME) {
            return 0;
        }

        int[] counts = new int[7];
        for (int pos = start; pos + FRAME <= end; pos += HOP) {
            float[] frame = new float[FRAME];
            System.arraycopy(wav.samples, pos, frame, 0, FRAME);
            double rms = rms(frame);
            if (rms < 0.01) {
                continue;
            }
            float[] magnitudes = magnitudeSpectrum(frame);
            int bestBin = 1;
            float bestMag = 0f;
            for (int i = 1; i < magnitudes.length; i++) {
                if (magnitudes[i] > bestMag) {
                    bestMag = magnitudes[i];
                    bestBin = i;
                }
            }
            float hz = bestBin * wav.sampleRate / (2f * magnitudes.length);
            int note = nearestSoundNumber(hz);
            counts[note]++;
        }

        int bestNote = 0;
        int bestCount = 0;
        for (int i = 1; i <= 6; i++) {
            if (counts[i] > bestCount) {
                bestCount = counts[i];
                bestNote = i;
            }
        }
        return bestNote;
    }

    private double rms(float[] frame) {
        double sum = 0d;
        for (float v : frame) {
            sum += v * v;
        }
        return Math.sqrt(sum / Math.max(1, frame.length));
    }

    private float[] magnitudeSpectrum(float[] frame) {
        int n = frame.length;
        double[] real = new double[n];
        double[] imag = new double[n];

        for (int i = 0; i < n; i++) {
            double w = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (n - 1));
            real[i] = frame[i] * w;
            imag[i] = 0d;
        }

        fft(real, imag);
        int bins = n / 2;
        float[] magnitudes = new float[bins];
        for (int i = 0; i < bins; i++) {
            magnitudes[i] = (float) Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
        }
        return magnitudes;
    }

    private void fft(double[] real, double[] imag) {
        int n = real.length;
        int bits = (int) (Math.log(n) / Math.log(2));
        for (int i = 0; i < n; i++) {
            int j = reverseBits(i, bits);
            if (j > i) {
                double tr = real[i];
                double ti = imag[i];
                real[i] = real[j];
                imag[i] = imag[j];
                real[j] = tr;
                imag[j] = ti;
            }
        }

        for (int size = 2; size <= n; size <<= 1) {
            int half = size / 2;
            double step = -2.0 * Math.PI / size;
            for (int i = 0; i < n; i += size) {
                for (int j = 0; j < half; j++) {
                    double angle = j * step;
                    double c = Math.cos(angle);
                    double s = Math.sin(angle);
                    int even = i + j;
                    int odd = even + half;
                    double tre = c * real[odd] - s * imag[odd];
                    double tim = s * real[odd] + c * imag[odd];
                    real[odd] = real[even] - tre;
                    imag[odd] = imag[even] - tim;
                    real[even] += tre;
                    imag[even] += tim;
                }
            }
        }
    }

    private int reverseBits(int value, int bits) {
        int r = 0;
        for (int i = 0; i < bits; i++) {
            r = (r << 1) | (value & 1);
            value >>= 1;
        }
        return r;
    }

    private int nearestSoundNumber(float hz) {
        int best = 1;
        float diff = Float.MAX_VALUE;
        for (int i = 0; i < NOTE_BASE_HZ.length; i++) {
            float d = Math.abs(hz - NOTE_BASE_HZ[i]);
            if (d < diff) {
                diff = d;
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

    private List<Segment> readSegments(File markdown) throws Exception {
        List<Segment> out = new ArrayList<Segment>();
        BufferedReader br = new BufferedReader(new FileReader(markdown));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = ROW.matcher(line.trim());
                if (!m.matches()) {
                    continue;
                }
                double start = Double.parseDouble(m.group(1));
                double end = Double.parseDouble(m.group(2));
                int note = Integer.parseInt(m.group(4));
                out.add(new Segment(start, end, note));
            }
        } finally {
            br.close();
        }
        return out;
    }

    private WavData readWavMono(File path) throws Exception {
        byte[] data = Files.readAllBytes(path.toPath());
        if (data.length < 44 || data[0] != 'R' || data[1] != 'I' || data[2] != 'F' || data[3] != 'F') {
            throw new IllegalArgumentException("Not a RIFF file: " + path);
        }

        int offset = 12;
        int formatTag = -1;
        int channels = -1;
        int sampleRate = -1;
        int bits = -1;
        int dataOffset = -1;
        int dataSize = -1;

        while (offset + 8 <= data.length) {
            String id = new String(data, offset, 4, StandardCharsets.US_ASCII);
            int size = leInt(data, offset + 4);
            int start = offset + 8;
            if ("fmt ".equals(id)) {
                formatTag = leShort(data, start);
                channels = leShort(data, start + 2);
                sampleRate = leInt(data, start + 4);
                bits = leShort(data, start + 14);
            } else if ("data".equals(id)) {
                dataOffset = start;
                dataSize = size;
                break;
            }
            offset = start + size + (size % 2);
        }

        if (channels != 1) {
            throw new IllegalArgumentException("Expected mono wav, got channels=" + channels);
        }
        if (sampleRate <= 0 || dataOffset < 0 || dataSize <= 0) {
            throw new IllegalArgumentException("Invalid wav header in " + path);
        }

        int frames;
        float[] samples;
        if (formatTag == 1 && bits == 16) {
            frames = dataSize / 2;
            samples = new float[frames];
            for (int i = 0; i < frames; i++) {
                short s = (short) leShort(data, dataOffset + i * 2);
                samples[i] = s / 32768f;
            }
        } else if (formatTag == 3 && bits == 32) {
            frames = dataSize / 4;
            samples = new float[frames];
            for (int i = 0; i < frames; i++) {
                int b = leInt(data, dataOffset + i * 4);
                float f = Float.intBitsToFloat(b);
                if (f > 1f) f = 1f;
                if (f < -1f) f = -1f;
                samples[i] = f;
            }
        } else {
            throw new IllegalArgumentException("Unsupported wav format tag=" + formatTag + " bits=" + bits);
        }

        return new WavData(sampleRate, samples);
    }

    private int leShort(byte[] data, int off) {
        return (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8);
    }

    private int leInt(byte[] data, int off) {
        return (data[off] & 0xFF)
                | ((data[off + 1] & 0xFF) << 8)
                | ((data[off + 2] & 0xFF) << 16)
                | ((data[off + 3] & 0xFF) << 24);
    }

    public static void main(String[] args) throws Exception {
        DemoRecognitionSequenceTest t = new DemoRecognitionSequenceTest();
        t.recognitionShouldFollowDocumentedSegmentOrder();
        System.out.println("demo-recognition-sequence=OK");
    }
}
