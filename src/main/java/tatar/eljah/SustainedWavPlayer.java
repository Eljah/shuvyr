package tatar.eljah;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class SustainedWavPlayer {
    private static final float ATTACK_SKIP_SECONDS = 0.12f;

    private final AudioTrack track;
    private final int loopStartFrame;
    private final int endFrame;

    public SustainedWavPlayer(Context context, int rawResId) {
        byte[] wavData = readAll(context, rawResId);
        WavInfo info = parseWav(wavData);

        AudioFormat format = new AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(info.sampleRate)
            .setChannelMask(info.channelCount == 2
                ? AudioFormat.CHANNEL_OUT_STEREO
                : AudioFormat.CHANNEL_OUT_MONO)
            .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
            track = new AudioTrack(attrs, format, info.dataSize, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
        } else {
            int channelConfig = info.channelCount == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
            track = new AudioTrack(AudioManager.STREAM_MUSIC, info.sampleRate, channelConfig,
                AudioFormat.ENCODING_PCM_16BIT, info.dataSize, AudioTrack.MODE_STATIC);
        }

        int written = track.write(wavData, info.dataOffset, info.dataSize);
        int bytesPerFrame = info.channelCount * (info.bitsPerSample / 8);
        int totalFrames = Math.max(1, written / bytesPerFrame);

        endFrame = totalFrames;
        int desiredLoopStart = (int) (info.sampleRate * ATTACK_SKIP_SECONDS);
        loopStartFrame = Math.max(0, Math.min(desiredLoopStart, Math.max(0, endFrame - 1)));
        track.setLoopPoints(loopStartFrame, endFrame, -1);
    }

    public void playSustain() {
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            return;
        }
        try {
            track.pause();
            track.flush();
        } catch (IllegalStateException ignored) {
        }
        track.reloadStaticData();
        track.setPlaybackHeadPosition(loopStartFrame);
        track.play();
    }

    public void stop() {
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            return;
        }
        try {
            track.pause();
            track.flush();
        } catch (IllegalStateException ignored) {
        }
    }

    public void release() {
        stop();
        track.release();
    }

    private static byte[] readAll(Context context, int rawResId) {
        InputStream input = context.getResources().openRawResource(rawResId);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read WAV resource", e);
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
            }
            try {
                output.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static WavInfo parseWav(byte[] bytes) {
        if (bytes.length < 44 || !isTag(bytes, 0, "RIFF") || !isTag(bytes, 8, "WAVE")) {
            throw new IllegalStateException("Invalid WAV header");
        }

        int cursor = 12;
        int fmtOffset = -1;
        int dataOffset = -1;
        int dataSize = -1;

        while (cursor + 8 <= bytes.length) {
            int chunkSize = littleEndianInt(bytes, cursor + 4);
            String id = new String(bytes, cursor, 4);
            int payloadOffset = cursor + 8;
            if ("fmt ".equals(id)) {
                fmtOffset = payloadOffset;
            } else if ("data".equals(id)) {
                dataOffset = payloadOffset;
                dataSize = chunkSize;
                break;
            }
            cursor = payloadOffset + chunkSize + (chunkSize % 2);
        }

        if (fmtOffset < 0 || dataOffset < 0 || dataSize <= 0 || dataOffset + dataSize > bytes.length) {
            throw new IllegalStateException("Unsupported WAV structure");
        }

        int format = littleEndianShort(bytes, fmtOffset);
        int channels = littleEndianShort(bytes, fmtOffset + 2);
        int sampleRate = littleEndianInt(bytes, fmtOffset + 4);
        int bitsPerSample = littleEndianShort(bytes, fmtOffset + 14);

        if (format != 1 || (channels != 1 && channels != 2) || bitsPerSample != 16) {
            throw new IllegalStateException("Only PCM16 mono/stereo WAV is supported");
        }

        return new WavInfo(sampleRate, channels, bitsPerSample, dataOffset, dataSize);
    }

    private static boolean isTag(byte[] bytes, int offset, String tag) {
        return bytes[offset] == tag.charAt(0)
            && bytes[offset + 1] == tag.charAt(1)
            && bytes[offset + 2] == tag.charAt(2)
            && bytes[offset + 3] == tag.charAt(3);
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
            | ((bytes[offset + 1] & 0xFF) << 8)
            | ((bytes[offset + 2] & 0xFF) << 16)
            | ((bytes[offset + 3] & 0xFF) << 24);
    }

    private static int littleEndianShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static final class WavInfo {
        final int sampleRate;
        final int channelCount;
        final int bitsPerSample;
        final int dataOffset;
        final int dataSize;

        WavInfo(int sampleRate, int channelCount, int bitsPerSample, int dataOffset, int dataSize) {
            this.sampleRate = sampleRate;
            this.channelCount = channelCount;
            this.bitsPerSample = bitsPerSample;
            this.dataOffset = dataOffset;
            this.dataSize = dataSize;
        }
    }
}
