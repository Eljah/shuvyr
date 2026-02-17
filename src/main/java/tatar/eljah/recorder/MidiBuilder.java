package tatar.eljah.recorder;

import java.io.ByteArrayOutputStream;

final class MidiBuilder {
    private final ByteArrayOutputStream file = new ByteArrayOutputStream();
    private final ByteArrayOutputStream track = new ByteArrayOutputStream();
    private int lastTick;

    void header(int format, int tracks, int division) {
        writeAscii(file, "MThd");
        writeInt(file, 6);
        writeShort(file, format);
        writeShort(file, tracks);
        writeShort(file, division);
    }

    void trackStart() {
        track.reset();
        lastTick = 0;
    }

    void tempo(int microsPerQuarter) {
        writeVar(track, 0);
        track.write(0xFF);
        track.write(0x51);
        track.write(0x03);
        track.write((microsPerQuarter >> 16) & 0xFF);
        track.write((microsPerQuarter >> 8) & 0xFF);
        track.write(microsPerQuarter & 0xFF);
    }

    void noteOn(int tick, int note, int velocity) {
        writeDelta(tick);
        track.write(0x90);
        track.write(note & 0x7F);
        track.write(velocity & 0x7F);
    }

    void noteOff(int tick, int note, int velocity) {
        writeDelta(tick);
        track.write(0x80);
        track.write(note & 0x7F);
        track.write(velocity & 0x7F);
    }

    void endOfTrack(int tick) {
        writeDelta(tick);
        track.write(0xFF);
        track.write(0x2F);
        track.write(0x00);
    }

    byte[] build() {
        byte[] trackBytes = track.toByteArray();
        writeAscii(file, "MTrk");
        writeInt(file, trackBytes.length);
        file.write(trackBytes, 0, trackBytes.length);
        return file.toByteArray();
    }

    private void writeDelta(int tick) {
        int delta = Math.max(0, tick - lastTick);
        writeVar(track, delta);
        lastTick = tick;
    }

    private static void writeAscii(ByteArrayOutputStream out, String value) {
        for (int i = 0; i < value.length(); i++) {
            out.write((byte) value.charAt(i));
        }
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeVar(ByteArrayOutputStream out, int value) {
        int buffer = value & 0x7F;
        while ((value >>= 7) > 0) {
            buffer <<= 8;
            buffer |= ((value & 0x7F) | 0x80);
        }
        while (true) {
            out.write(buffer & 0xFF);
            if ((buffer & 0x80) != 0) {
                buffer >>= 8;
            } else {
                break;
            }
        }
    }
}
