#!/usr/bin/env python3
import math
import struct
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "src/main/res/raw"
DEMO = RAW / "demo.wav"
REFS = [RAW / f"shuvyr_{i}.wav" for i in range(1, 7)]
OUT = ROOT / "docs/demo_note_segments.md"

FRAME = 4096
HOP = 1024


def read_wav_mono(path: Path):
    data = path.read_bytes()
    if data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise ValueError(f"Not RIFF/WAVE: {path}")

    offset = 12
    fmt = None
    payload = None
    while offset + 8 <= len(data):
        cid = data[offset:offset+4]
        size = struct.unpack_from('<I', data, offset + 4)[0]
        body_start = offset + 8
        body_end = body_start + size
        chunk = data[body_start:body_end]
        if cid == b'fmt ':
            fmt = struct.unpack_from('<HHIIHH', chunk, 0)
        elif cid == b'data':
            payload = chunk
        offset = body_end + (size % 2)

    if fmt is None or payload is None:
        raise ValueError(f"Missing fmt/data chunk: {path}")

    format_tag, channels, sample_rate, _, _, bits = fmt
    if channels != 1:
        raise ValueError(f"Only mono supported: {path} channels={channels}")

    if format_tag == 1 and bits == 16:
        count = len(payload) // 2
        vals = struct.unpack('<' + 'h' * count, payload)
        samples = [float(v) / 32768.0 for v in vals]
    elif format_tag == 3 and bits == 32:
        count = len(payload) // 4
        vals = struct.unpack('<' + 'f' * count, payload)
        samples = [max(-1.0, min(1.0, float(v))) for v in vals]
    else:
        raise ValueError(f"Unsupported format {format_tag}/{bits} in {path}")

    return sample_rate, samples


def precompute_fft(n: int):
    bits = int(math.log2(n))
    bitrev = [0] * n
    for i in range(n):
        x = i
        r = 0
        for _ in range(bits):
            r = (r << 1) | (x & 1)
            x >>= 1
        bitrev[i] = r

    twiddles = {}
    size = 2
    while size <= n:
        half = size // 2
        step = -2.0 * math.pi / size
        twiddles[size] = [(math.cos(j * step), math.sin(j * step)) for j in range(half)]
        size *= 2

    window = [0.5 - 0.5 * math.cos(2.0 * math.pi * i / (n - 1)) for i in range(n)]
    return bitrev, twiddles, window


BITREV, TW, WINDOW = precompute_fft(FRAME)


def dominant_bin_and_rms(frame):
    n = FRAME
    real = [0.0] * n
    imag = [0.0] * n
    s2 = 0.0

    for i in range(n):
        x = frame[BITREV[i]]
        s2 += x * x
        real[i] = x * WINDOW[BITREV[i]]

    size = 2
    while size <= n:
        half = size // 2
        tw = TW[size]
        for i in range(0, n, size):
            for j in range(half):
                c, s = tw[j]
                ei = i + j
                oi = ei + half
                tre = c * real[oi] - s * imag[oi]
                tim = s * real[oi] + c * imag[oi]
                real[oi] = real[ei] - tre
                imag[oi] = imag[ei] - tim
                real[ei] += tre
                imag[ei] += tim
        size *= 2

    best_i = 0
    best_m = -1.0
    for i in range(1, n // 2):
        mag2 = real[i] * real[i] + imag[i] * imag[i]
        if mag2 > best_m:
            best_m = mag2
            best_i = i

    return best_i, math.sqrt(s2 / n)


def frame_bins(samples):
    bins = []
    rms = []
    for start in range(0, len(samples) - FRAME + 1, HOP):
        b, r = dominant_bin_and_rms(samples[start:start + FRAME])
        bins.append(b)
        rms.append(r)
    return bins, rms


def representative_bin(samples):
    bins, rms = frame_bins(samples)
    if not bins:
        return 0
    pairs = [(b, r) for b, r in zip(bins, rms) if r > 0.01]
    if not pairs:
        return Counter(bins).most_common(1)[0][0]
    top = sorted(pairs, key=lambda x: x[1], reverse=True)[: max(10, len(pairs) // 3)]
    return Counter([b for b, _ in top]).most_common(1)[0][0]


def classify_demo(demo_samples, ref_bins):
    bins, rms = frame_bins(demo_samples)
    if not bins:
        return [], bins, rms

    sorted_r = sorted(rms)
    noise_floor = sorted_r[max(0, int(len(sorted_r) * 0.2) - 1)]
    silence_thr = max(0.004, noise_floor * 1.35)

    labels = []
    for b, r in zip(bins, rms):
        if r < silence_thr:
            labels.append(0)
            continue
        best = min(range(1, 7), key=lambda n: abs(b - ref_bins[n]))
        labels.append(best)
    # temporal smoothing to reduce frame-level jitter
    smoothed = labels[:]
    win = 5
    radius = win // 2
    for i in range(len(labels)):
        a = max(0, i - radius)
        b = min(len(labels), i + radius + 1)
        local = labels[a:b]
        smoothed[i] = Counter(local).most_common(1)[0][0]
    return smoothed, bins, rms


def segments(labels, sample_rate):
    if not labels:
        return []
    out = []
    cur = labels[0]
    start = 0
    for i in range(1, len(labels)):
        if labels[i] != cur:
            out.append((cur, start, i))
            cur = labels[i]
            start = i
    out.append((cur, start, len(labels)))

    seconds = []
    # remove very short islands by merging into previous/next dominant note
    cleaned = []
    for idx, (note, a, b) in enumerate(out):
        dur = ((b - a) * HOP) / sample_rate
        if dur < 0.12 and idx > 0 and idx + 1 < len(out):
            prev_note, pa, pb = cleaned[-1]
            next_note, na, nb = out[idx + 1]
            if prev_note == next_note:
                cleaned[-1] = (prev_note, pa, b)
                continue
        cleaned.append((note, a, b))

    for note, a, b in cleaned:
        t0 = a * HOP / sample_rate
        t1 = ((b - 1) * HOP + FRAME) / sample_rate
        if t1 - t0 >= 0.12:
            seconds.append((note, t0, t1))
    return seconds


def main():
    sr_demo, demo = read_wav_mono(DEMO)
    ref_bins = {0: 0}
    for idx, path in enumerate(REFS, start=1):
        sr, samples = read_wav_mono(path)
        if sr != sr_demo:
            raise ValueError(f"Sample rate mismatch: {path}={sr}, demo={sr_demo}")
        ref_bins[idx] = representative_bin(samples)

    labels, bins, rms = classify_demo(demo, ref_bins)
    segs = segments(labels, sr_demo)

    lines = []
    lines.append("# demo.wav note segmentation against shuvyr_1..6")
    lines.append("")
    lines.append(f"- frame={FRAME}, hop={HOP}, sample_rate={sr_demo}")
    lines.append(f"- reference dominant bins: " + ", ".join(f"{k}:{v}" for k, v in ref_bins.items() if k))
    lines.append(f"- total frames: {len(labels)}")
    lines.append("")
    lines.append("| Start (s) | End (s) | Dur (s) | Note |")
    lines.append("|---:|---:|---:|---:|")
    for note, t0, t1 in segs:
        lines.append(f"| {t0:.3f} | {t1:.3f} | {t1 - t0:.3f} | {note} |")

    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {OUT}")
    print("reference bins:", ref_bins)
    print("segments:", len(segs))


if __name__ == "__main__":
    main()
