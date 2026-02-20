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
    private final AudioTrack track;
    private final int loopStartFrame;
    private final int loopEndFrame;

    public SustainedWavPlayer(Context context, int rawResId, float attackEndSec, float releaseStartSec) {
        byte[] wavData = readAll(context, rawResId);
        PcmData pcm = decodeToPcm16(wavData);

        AudioFormat format = new AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(pcm.sampleRate)
            .setChannelMask(pcm.channelCount == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO)
            .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
            track = new AudioTrack(attrs, format, pcm.pcm16.length, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
        } else {
            int channelConfig = pcm.channelCount == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
            track = new AudioTrack(AudioManager.STREAM_MUSIC, pcm.sampleRate, channelConfig,
                AudioFormat.ENCODING_PCM_16BIT, pcm.pcm16.length, AudioTrack.MODE_STATIC);
        }

        int written = track.write(pcm.pcm16, 0, pcm.pcm16.length);
        int bytesPerFrame = pcm.channelCount * 2;
        int totalFrames = Math.max(1, written / bytesPerFrame);

        int desiredStart = (int) (attackEndSec * pcm.sampleRate);
        int desiredEnd = (int) (releaseStartSec * pcm.sampleRate);
        loopStartFrame = Math.max(0, Math.min(desiredStart, Math.max(0, totalFrames - 2)));
        loopEndFrame = Math.max(loopStartFrame + 1, Math.min(desiredEnd, totalFrames - 1));
        track.setLoopPoints(loopStartFrame, loopEndFrame, -1);
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
        track.setLoopPoints(loopStartFrame, loopEndFrame, -1);
        track.setPlaybackHeadPosition(0);
        track.play();
    }

    public void stopWithRelease() {
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            return;
        }
        try {
            // Отключаем цикл: оставшаяся часть дойдет до конца с задним фронтом.
            track.setLoopPoints(loopStartFrame, loopEndFrame, 0);
        } catch (IllegalStateException ignored) {
        }
    }

    public void release() {
        if (track.getState() == AudioTrack.STATE_INITIALIZED) {
            try {
                track.pause();
                track.flush();
            } catch (IllegalStateException ignored) {
            }
        }
        track.release();
    }

    private static PcmData decodeToPcm16(byte[] wavBytes) {
        WavInfo info = parseWav(wavBytes);

        if (info.audioFormat == 1 && info.bitsPerSample == 16) {
            byte[] pcm = new byte[info.dataSize];
            System.arraycopy(wavBytes, info.dataOffset, pcm, 0, info.dataSize);
            return new PcmData(info.sampleRate, info.channelCount, pcm);
        }

        if (info.audioFormat == 3 && info.bitsPerSample == 32) {
            int sampleCount = info.dataSize / 4;
            byte[] pcm = new byte[sampleCount * 2];
            int in = info.dataOffset;
            int out = 0;
            for (int i = 0; i < sampleCount; i++) {
                int bits = littleEndianInt(wavBytes, in);
                float f = Float.intBitsToFloat(bits);
                if (Float.isNaN(f)) {
                    f = 0f;
                }
                if (f > 1f) {
                    f = 1f;
                } else if (f < -1f) {
                    f = -1f;
                }
                short s = (short) Math.round(f * 32767f);
                pcm[out] = (byte) (s & 0xFF);
                pcm[out + 1] = (byte) ((s >> 8) & 0xFF);
                in += 4;
                out += 2;
            }
            return new PcmData(info.sampleRate, info.channelCount, pcm);
        }

        throw new IllegalStateException("Unsupported WAV format: format=" + info.audioFormat + ", bps=" + info.bitsPerSample);
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
            int payloadOffset = cursor + 8;
            if (isTag(bytes, cursor, "fmt ")) {
                fmtOffset = payloadOffset;
            } else if (isTag(bytes, cursor, "data")) {
                dataOffset = payloadOffset;
                dataSize = chunkSize;
                break;
            }
            cursor = payloadOffset + chunkSize + (chunkSize % 2);
        }

        if (fmtOffset < 0 || dataOffset < 0 || dataSize <= 0 || dataOffset + dataSize > bytes.length) {
            throw new IllegalStateException("Unsupported WAV structure");
        }

        int audioFormat = littleEndianShort(bytes, fmtOffset);
        int channels = littleEndianShort(bytes, fmtOffset + 2);
        int sampleRate = littleEndianInt(bytes, fmtOffset + 4);
        int bitsPerSample = littleEndianShort(bytes, fmtOffset + 14);

        if (channels != 1 && channels != 2) {
            throw new IllegalStateException("Only mono/stereo WAV is supported");
        }

        return new WavInfo(audioFormat, sampleRate, channels, bitsPerSample, dataOffset, dataSize);
    }

    private static boolean isTag(byte[] bytes, int offset, String tag) {
        return offset + 3 < bytes.length
            && bytes[offset] == tag.charAt(0)
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
        final int audioFormat;
        final int sampleRate;
        final int channelCount;
        final int bitsPerSample;
        final int dataOffset;
        final int dataSize;

        WavInfo(int audioFormat, int sampleRate, int channelCount, int bitsPerSample, int dataOffset, int dataSize) {
            this.audioFormat = audioFormat;
            this.sampleRate = sampleRate;
            this.channelCount = channelCount;
            this.bitsPerSample = bitsPerSample;
            this.dataOffset = dataOffset;
            this.dataSize = dataSize;
        }
    }

    private static final class PcmData {
        final int sampleRate;
        final int channelCount;
        final byte[] pcm16;

        PcmData(int sampleRate, int channelCount, byte[] pcm16) {
            this.sampleRate = sampleRate;
            this.channelCount = channelCount;
            this.pcm16 = pcm16;
        }
    }
}
