#!/usr/bin/env python3
import math, struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEMO = ROOT / 'src/main/res/raw/demo.wav'
OUT = ROOT / 'docs/demo_note_segments.md'
FRAME=4096
HOP=1024
NOTE_BASE=[160.0,98.0,538.0,496.0,469.0,96.0]


def read_wav(path):
    b=path.read_bytes()
    off=12
    fmt=None; data=None
    while off+8<=len(b):
        cid=b[off:off+4]; sz=struct.unpack_from('<I',b,off+4)[0]
        s=off+8; e=s+sz
        if cid==b'fmt ': fmt=struct.unpack_from('<HHIIHH',b,s)
        if cid==b'data': data=b[s:e]
        off=e+(sz%2)
    tag,ch,sr,_,_,bits=fmt
    if ch!=1: raise SystemExit('mono only')
    if tag==1 and bits==16:
        vals=struct.unpack('<'+'h'*(len(data)//2),data)
        samples=[v/32768.0 for v in vals]
    elif tag==3 and bits==32:
        vals=struct.unpack('<'+'f'*(len(data)//4),data)
        samples=[max(-1.0,min(1.0,float(v))) for v in vals]
    else: raise SystemExit(f'bad format {tag}/{bits}')
    return sr,samples


def fft_mag(frame):
    n=len(frame)
    real=[0.0]*n; imag=[0.0]*n
    for i,x in enumerate(frame):
        w=0.5-0.5*math.cos(2*math.pi*i/(n-1))
        real[i]=x*w
    bits=int(math.log2(n))
    def rev(v):
        r=0
        for _ in range(bits): r=(r<<1)|(v&1); v>>=1
        return r
    for i in range(n):
        j=rev(i)
        if j>i:
            real[i],real[j]=real[j],real[i]
            imag[i],imag[j]=imag[j],imag[i]
    size=2
    while size<=n:
        half=size//2
        step=-2*math.pi/size
        for i in range(0,n,size):
            for j in range(half):
                a=j*step; c=math.cos(a); s=math.sin(a)
                ei=i+j; oi=ei+half
                tre=c*real[oi]-s*imag[oi]
                tim=s*real[oi]+c*imag[oi]
                real[oi]=real[ei]-tre; imag[oi]=imag[ei]-tim
                real[ei]+=tre; imag[ei]+=tim
        size*=2
    bins=n//2
    mags=[(real[i]*real[i]+imag[i]*imag[i])**0.5 for i in range(bins)]
    return mags


def nearest(hz):
    best=1; d=1e9
    for i,v in enumerate(NOTE_BASE,1):
        dd=abs(hz-v)
        if dd<d: d=dd; best=i
    return best

def hole(note):
    return {2:0,3:1,4:2,5:4,6:5}.get(note,-1)

sr,s=read_wav(DEMO)
labels=[]
for st in range(0,len(s)-FRAME+1,HOP):
    fr=s[st:st+FRAME]
    rms=(sum(x*x for x in fr)/FRAME)**0.5
    if rms<0.01:
        labels.append(0); continue
    mags=fft_mag(fr)
    bi=max(range(1,len(mags)), key=lambda i:mags[i])
    hz=bi*sr/(2*len(mags))
    labels.append(nearest(hz))
# smooth
sm=labels[:]
for i in range(len(labels)):
    a=max(0,i-2); b=min(len(labels),i+3)
    local=labels[a:b]
    sm[i]=max(set(local), key=local.count)
labels=sm
# segments
segs=[]
cur=labels[0]; st=0
for i in range(1,len(labels)):
    if labels[i]!=cur:
        segs.append((cur,st,i)); cur=labels[i]; st=i
segs.append((cur,st,len(labels)))
rows=[]
for note,a,b in segs:
    t0=a*HOP/sr; t1=((b-1)*HOP+FRAME)/sr
    if t1-t0<0.12: continue
    rows.append((t0,t1,note,hole(note)))

lines=["# demo.wav note segmentation against app recognition logic","",f"- frame={FRAME}, hop={HOP}, sample_rate={sr}","- recognizer: dominant FFT bin -> nearestSoundNumber -> mapSoundToHole","","| Start (s) | End (s) | Dur (s) | Note | Hole |","|---:|---:|---:|---:|---:|"]
for t0,t1,n,h in rows:
    lines.append(f"| {t0:.3f} | {t1:.3f} | {t1-t0:.3f} | {n} | {h} |")
OUT.write_text('\n'.join(lines)+'\n', encoding='utf-8')
print('segments',len(rows))
