#!/usr/bin/env python3
import math
import struct
import wave
import xml.etree.ElementTree as ET
from pathlib import Path

SR = 22050
FRAME = 1024
HOP = 512
OUT_WAV = Path('build/reference_score_synth.wav')

NOTE_INDEX = {'C':0,'C#':1,'D':2,'D#':3,'E':4,'F':5,'F#':6,'G':7,'G#':8,'A':9,'A#':10,'B':11}
FREQS = {}
for o in range(0,9):
    for n,i in NOTE_INDEX.items():
        m=(o+1)*12+i
        FREQS[f'{n}{o}']=440.0*(2**((m-69)/12))


def duration_ms(d):
    if d=='eighth': return 240
    if d=='half': return 900
    return 450


def parse_score(path: Path):
    root = ET.parse(path).getroot()
    out=[]
    for n in root.findall('.//note'):
        step=n.findtext('pitch/step')
        if not step:
            continue
        alter=n.findtext('pitch/alter')
        octv=n.findtext('pitch/octave')
        typ=n.findtext('type') or 'quarter'
        name = step + ('#' if alter=='1' else '') + str(int(octv))
        out.append((name, typ))
    return out


def synth(notes):
    samples=[]
    boundaries=[]
    for name,typ in notes:
        total = int(SR*duration_ms(typ)/1000)
        fade = min(int(SR*0.008), total//2)
        st = len(samples)
        f = FREQS[name]
        for i in range(total):
            t=i/SR
            env=1.0
            if i<fade: env=i/max(1,fade)
            left = total-i
            if left<=fade: env=min(env, left/max(1,fade))
            v=math.sin(2*math.pi*f*t)*0.38*env
            samples.append(int(max(-1,min(1,v))*32767))
        boundaries.append((st, len(samples), name, typ))
    return samples,boundaries


def estimate_pitch(buf):
    L=len(buf)
    if L<64:
        return 0.0
    mean=sum(buf)/L
    rms=math.sqrt(sum((x-mean)**2 for x in buf)/L)
    if rms<250:
        return 0.0
    min_lag=max(4, SR//2600)
    max_lag=min(L//2, SR//120)
    if max_lag<=min_lag:
        return 0.0
    corr=[0.0]*(max_lag+1)
    for lag in range(min_lag, max_lag+1):
        sm=e1=e2=0.0
        for i in range(L-lag):
            a=buf[i]-mean
            b=buf[i+lag]-mean
            sm+=a*b
            e1+=a*a
            e2+=b*b
        if e1>0 and e2>0:
            corr[lag]=sm/math.sqrt(e1*e2)
    for lag in range(min_lag+1, max_lag):
        c=corr[lag]
        if c<0.55:
            continue
        if c>corr[lag-1] and c>=corr[lag+1]:
            hz=SR/lag
            if 120<=hz<=2600:
                return hz
    return 0.0


def nearest_note(hz):
    if hz<=0:
        return None
    candidates=[k for k in FREQS.keys() if k[-1].isdigit() and 4<=int(k[-1])<=6]
    return min(candidates, key=lambda n: abs(FREQS[n]-hz))


def run_recognition(samples, score):
    pointer=0
    last_match_ms=None

    for pos in range(0, len(samples)-FRAME, HOP):
        if pointer>=len(score):
            break
        expected,dur=score[pointer]
        hz=estimate_pitch(samples[pos:pos+FRAME])
        det=nearest_note(hz)
        if det!=expected:
            continue

        now_ms=(pos*1000)//SR
        min_hold=max(110, int(duration_ms(dur)*0.45))
        if last_match_ms is not None and now_ms-last_match_ms<min_hold:
            continue
        last_match_ms=now_ms
        pointer+=1

    return pointer


def save_wav(samples, out_path: Path):
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(out_path), 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b''.join(struct.pack('<h', s) for s in samples))


if __name__ == '__main__':
    score=parse_score(Path('src/main/assets/reference_score.xml'))
    samples,_=synth(score)
    save_wav(samples, OUT_WAV)
    passed=run_recognition(samples, score)
    print(f'recognized={passed}/{len(score)} wav={OUT_WAV}')
    if passed != len(score):
        raise SystemExit(1)
