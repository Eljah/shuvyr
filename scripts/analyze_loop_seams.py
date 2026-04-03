#!/usr/bin/env python3
"""
Спектральный анализ шва цикла для верхних/нижних нот на базе текущей логики приложения.

Метрика:
  seam/base ratio в низкой полосе (30..90 Гц) и средней полосе (140..300 Гц),
  где seam = окна рядом со стыком цикла, base = остальные окна.
"""

import math
import statistics
import struct
import wave

CFG = {
    # note: (stable_start_ms, stable_end_trim_ms, max_loop_ms, base_hz)
    1: (875, 200, 220, 160),
    2: (575, 340, 260, 98),
    3: (450, 480, 300, 538),
    4: (280, 0, 0, 496),
    5: (310, 0, 0, 469),
    6: (300, 0, 0, 96),
}


def load_mono(note):
    path = f"src/main/res/raw/shuvyr_{note}.wav"
    with wave.open(path, "rb") as w:
        sample_rate = w.getframerate()
        channels = w.getnchannels()
        raw = w.readframes(w.getnframes())
    data = list(struct.unpack("<" + "h" * (len(raw) // 2), raw))
    if channels == 2:
        data = [(data[i] + data[i + 1]) // 2 for i in range(0, len(data), 2)]
    return sample_rate, data


def detect_loop(segment, sample_rate, max_loop_ms):
    n = len(segment)
    if n < 4:
        return 0, max(1, n - 1)

    window = max(512, sample_rate // 12)
    window = min(window, n)
    step = max(64, window // 5)

    best_center = n // 2
    best_energy = -1.0
    for center in range(max(window // 2, n // 5), min(n - window // 2 - 1, n * 4 // 5) + 1, step):
        start = center - window // 2
        end = min(n, start + window)
        energy = math.sqrt(sum(x * x for x in segment[start:end]) / max(1, end - start))
        if energy > best_energy:
            best_energy = energy
            best_center = center

    min_period = max(12, sample_rate // 1400)
    max_period = max(min_period + 1, sample_rate // 70)
    analysis = min(sample_rate // 8, n // 2)
    start = max(0, best_center - analysis // 2)
    end = min(n, start + analysis)
    best_period = max(24, sample_rate // 220)
    if end - start >= max_period * 2:
        best_corr = -1e100
        for lag in range(min_period, max_period + 1):
            corr = 0
            for i in range(start, end - lag):
                corr += segment[i] * segment[i + lag]
            if corr > best_corr:
                best_corr = corr
                best_period = lag

    periods = max(16, min(48, sample_rate // max(1, best_period)))
    loop_len = max(best_period * periods, window)
    raw_start = max(0, best_center - loop_len // 2)
    raw_end = min(n - 1, raw_start + loop_len)

    search = max(24, best_period * 2)

    def nearest_zero(frame):
        best = max(0, min(n - 1, frame))
        best_abs = 10**18
        for delta in range(-search, search + 1):
            f = frame + delta
            if f < 1 or f >= n - 1:
                continue
            prev, cur = segment[f - 1], segment[f]
            if (prev <= 0 <= cur) or (prev >= 0 >= cur):
                score = abs(prev) + abs(cur)
                if score < best_abs:
                    best_abs = score
                    best = f
        return best

    loop_start = nearest_zero(raw_start)
    min_loop = max(best_period * 8, sample_rate // 8)
    max_loop = max(min_loop + best_period * 2, sample_rate)
    if max_loop_ms > 0:
        max_loop = min(max_loop, max(min_loop + 1, int(sample_rate * max_loop_ms / 1000)))

    target_end = max(loop_start + min_loop, raw_end)
    candidate_min = max(loop_start + min_loop, target_end - max_loop // 2)
    candidate_max = min(n - 1, loop_start + max_loop)
    compare = max(48, best_period * 2)
    candidate_step = max(1, best_period // 2)

    best_end = max(loop_start + min_loop, candidate_min)
    best_score = 10**30
    for frame in range(candidate_min, candidate_max + 1, candidate_step):
        candidate = nearest_zero(frame)
        loop_size = candidate - loop_start
        if candidate <= loop_start + 1 or candidate >= n - 1:
            continue
        if loop_size < min_loop or loop_size > max_loop:
            continue
        tail_start = max(loop_start, candidate - compare)
        length = max(1, candidate - tail_start)
        score = 0
        for i in range(length):
            d = segment[loop_start + i] - segment[tail_start + i]
            score += d * d
        if score < best_score:
            best_score = score
            best_end = candidate

    return loop_start, best_end


def smooth_loop_boundary(segment, sample_rate, loop_start, loop_end):
    segment = segment[:]
    loop_length = loop_end - loop_start
    if loop_length < 8:
        return segment
    fade = min(loop_length // 3, max(96, sample_rate // 40))
    if fade < 8:
        return segment

    tail_start = loop_end - fade
    dc = round(sum(segment[loop_start:loop_start + fade]) / fade - sum(segment[tail_start:tail_start + fade]) / fade)
    for i in range(fade):
        segment[tail_start + i] = max(-32768, min(32767, segment[tail_start + i] + dc))

    for i in range(fade):
        t = (i + 1.0) / (fade + 1.0)
        tail = segment[tail_start + i]
        head = segment[loop_start + i]
        blended = round(((tail * (1.0 - t) + head * t) + (head * (1.0 - t) + tail * t)) * 0.5)
        blended = max(-32768, min(32767, blended))
        segment[tail_start + i] = blended
        segment[loop_start + i] = blended
    return segment


def goertzel(frame, sample_rate, freq_hz):
    n = len(frame)
    k = int(0.5 + n * freq_hz / sample_rate)
    omega = 2.0 * math.pi * k / n
    coeff = 2.0 * math.cos(omega)
    s0 = s1 = s2 = 0.0
    for x in frame:
        s0 = x + coeff * s1 - s2
        s2 = s1
        s1 = s0
    return max(0.0, s1 * s1 + s2 * s2 - coeff * s1 * s2)


def seam_band_ratio(note):
    stable_start_ms, end_trim_ms, max_loop_ms, _ = CFG[note]
    sample_rate, pcm = load_mono(note)

    start = max(0, min(len(pcm) - 2, int(stable_start_ms * sample_rate / 1000)))
    end = max(start + 2, len(pcm) - int(end_trim_ms * sample_rate / 1000))
    region = pcm[start:end]

    loop_start, loop_end = detect_loop(region, sample_rate, max_loop_ms)
    region = smooth_loop_boundary(region, sample_rate, loop_start, loop_end)
    loop = region[loop_start:loop_end]

    repeated = (loop * 20)[: sample_rate * 8]
    frame = 1024
    hop = 256
    seams = [k * len(loop) for k in range(1, max(2, len(repeated) // len(loop)))]

    low_base = []
    low_seam = []
    mid_base = []
    mid_seam = []

    for i in range(0, len(repeated) - frame, hop):
        center = i + frame // 2
        is_seam = min(abs(center - s) for s in seams) < hop * 2
        low = sum(goertzel(repeated[i:i + frame], sample_rate, f) for f in (30, 40, 50, 60, 70, 80, 90))
        mid = sum(goertzel(repeated[i:i + frame], sample_rate, f) for f in (140, 160, 180, 200, 220, 260, 300))
        (low_seam if is_seam else low_base).append(low)
        (mid_seam if is_seam else mid_base).append(mid)

    low_ratio = (statistics.mean(low_seam) + 1e-9) / (statistics.mean(low_base) + 1e-9)
    mid_ratio = (statistics.mean(mid_seam) + 1e-9) / (statistics.mean(mid_base) + 1e-9)
    return low_ratio, mid_ratio


def main():
    upper = []
    lower = []
    for note in (1, 2, 3, 4, 5, 6):
        low_ratio, mid_ratio = seam_band_ratio(note)
        row = (note, low_ratio, mid_ratio, low_ratio - mid_ratio)
        if note <= 3:
            upper.append(row)
        else:
            lower.append(row)
        print(f"note{note}: low={low_ratio:.3f} mid={mid_ratio:.3f} delta={low_ratio - mid_ratio:+.3f}")

    avg_upper = sum(r[1] for r in upper) / len(upper)
    avg_lower = sum(r[1] for r in lower) / len(lower)
    print(f"upper_avg_low_ratio={avg_upper:.3f}")
    print(f"lower_avg_low_ratio={avg_lower:.3f}")


if __name__ == "__main__":
    main()
