package android.media;

public class AudioRecord {
    public static final int STATE_INITIALIZED = 1;

    public AudioRecord(int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes) {
    }

    public static int getMinBufferSize(int sampleRateInHz, int channelConfig, int audioFormat) {
        return 1024;
    }

    public int getState() {
        return STATE_INITIALIZED;
    }

    public void startRecording() {
    }

    public int read(short[] audioData, int offsetInShorts, int sizeInShorts) {
        return 0;
    }

    public void stop() {
    }

    public void release() {
    }
}
