"""Trilha original do vídeo (120 BPM, lá menor), sintetizada do zero e sincronizada com promo.html.

Uso: python3 music.py saida.wav
"""
import sys

import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, fftconvolve, sosfilt

SR = 44100
DUR = 30.0
N = int(SR * DUR)
BEAT = 0.5  # 120 BPM
rng = np.random.default_rng(7)

drums = np.zeros((2, N))
music = np.zeros((2, N))
fx = np.zeros((2, N))
kicks = []


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def tt(d):
    return np.arange(int(d * SR)) / SR


def filt(x, kind, f, order=2):
    if kind == "band":
        sos = butter(order, [f[0] / (SR / 2), f[1] / (SR / 2)], btype="band", output="sos")
    else:
        sos = butter(order, min(f / (SR / 2), 0.99), btype=kind, output="sos")
    return sosfilt(sos, x)


def put(bus, sig, t, gain=1.0, pan=0.0):
    i = int(round(t * SR))
    if i >= N:
        return
    if sig.ndim == 1:
        sig = np.vstack([sig * np.sqrt(1 - pan) , sig * np.sqrt(1 + pan)])
    n = min(sig.shape[1], N - i)
    bus[:, i:i + n] += gain * sig[:, :n]


def noise(d):
    return rng.uniform(-1, 1, int(d * SR))


# ---------- instrumentos ----------
def kick():
    t = tt(0.5)
    f = 46 + 120 * np.exp(-t * 32)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 6.5)
    click = filt(noise(0.5), "high", 2000) * np.exp(-t * 400) * 0.35
    return np.tanh(1.6 * (body + click))


def clap():
    t = tt(0.4)
    env = np.zeros_like(t)
    for o in (0, 0.011, 0.022):
        env += np.where(t >= o, np.exp(-(t - o) * 90), 0)
    env += np.exp(-t * 14) * 0.35
    return filt(noise(0.4), "band", (900, 3500)) * env * 1.6


def hat(open_=False):
    d = 0.3 if open_ else 0.06
    t = tt(d)
    return filt(noise(d), "high", 7500) * np.exp(-t * (14 if open_ else 70))


def shaker():
    t = tt(0.05)
    return filt(noise(0.05), "band", (5000, 11000)) * np.exp(-t * 110)


def crash(d=2.2):
    t = tt(d)
    metal = sum(np.sin(2 * np.pi * f * t + rng.uniform(0, 6)) for f in (3120, 4377, 5230, 6713, 8101)) * 0.08
    return (filt(noise(d), "high", 4200) + metal) * np.exp(-t * 2.2)


def impact(d=1.6):
    t = tt(d)
    f = 30 + 60 * np.exp(-t * 6)
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 2.4)
    rumble = filt(noise(d), "low", 220) * np.exp(-t * 3) * 0.8
    return np.tanh(1.4 * (boom + rumble))


def riser(d, f0=300, f1=9000):
    n = int(d * SR)
    x = noise(d)
    out = np.zeros(n)
    seg = int(0.04 * SR)
    for i in range(0, n, seg):
        k = i / n
        fc = f0 * (f1 / f0) ** k
        lo, hi = fc * 0.6, min(fc * 1.6, SR / 2 - 100)
        out[i:i + seg] = filt(x[max(0, i - seg):i + seg], "band", (lo, hi))[-len(x[i:i + seg]):]
    t = np.arange(n) / SR
    tone = np.sin(2 * np.pi * np.cumsum(200 * (8 ** (t / d))) / SR) * 0.12
    return (out + tone) * (t / d) ** 2.2


def whoosh(d=0.6, up=True):
    n = int(d * SR)
    x = noise(d)
    out = np.zeros(n)
    seg = int(0.03 * SR)
    for i in range(0, n, seg):
        k = i / n
        fc = (500 * 12 ** k) if up else (6000 / 12 ** k)
        out[i:i + seg] = filt(x[max(0, i - seg):i + seg], "band", (fc * 0.7, min(fc * 1.5, 20000)))[-len(x[i:i + seg]):]
    t = np.arange(n) / SR
    return out * np.sin(np.pi * t / d) ** 2


def saw(f, t, detune=0.0):
    ph = (f * (2 ** (detune / 12))) * t + rng.uniform(0, 1)
    return 2 * (ph % 1) - 1


def pluck(note, d=0.45, bright=4200, decay=9.0):
    t = tt(d)
    f = midi(note)
    x = saw(f, t) * 0.6 + np.sign(np.sin(2 * np.pi * f * t)) * 0.25 + np.sin(2 * np.pi * f * 2 * t) * 0.2
    x = filt(x, "low", bright)
    return x * np.exp(-t * decay) * np.minimum(1, t / 0.003)


def bell(note, d=1.2):
    t = tt(d)
    f = midi(note)
    x = np.sin(2 * np.pi * f * t) + 0.4 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t * 6) + 0.25 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t * 12)
    return x * np.exp(-t * 3.2) * np.minimum(1, t / 0.002)


def bass(note, d, cutoff=700):
    t = tt(d)
    f = midi(note)
    x = saw(f, t) * 0.7 + np.sin(2 * np.pi * f * t) * 0.8
    x = filt(x, "low", cutoff)
    env = np.minimum(1, t / 0.005) * np.minimum(1, (d - t) / 0.03).clip(0, 1)
    return np.tanh(1.3 * x * env)


def pad(notes, d, cutoff=2200, attack=0.25, release=0.4):
    t = tt(d + release)
    l = np.zeros_like(t)
    r = np.zeros_like(t)
    for n in notes:
        f = midi(n)
        l += saw(f, t, -0.11) + saw(f, t, 0.05)
        r += saw(f, t, 0.11) + saw(f, t, -0.05)
    env = np.minimum(1, t / attack) * np.where(t > d, np.exp(-(t - d) / (release / 3)), 1)
    l = filt(l, "low", cutoff) * env
    r = filt(r, "low", cutoff) * env
    return np.vstack([l, r]) / (len(notes) * 2)


def stab(notes, d=0.6):
    s = sum(pluck(n, d, bright=5000, decay=5) for n in notes)
    return s / len(notes)


# ---------- harmonia ----------
AM, F, C, G = [57, 60, 64], [53, 57, 60], [55, 60, 64], [55, 59, 62]
ROOT = {"Am": 45, "F": 41, "C": 48, "G": 43}
CH = {"Am": AM, "F": F, "C": C, "G": G}


def K(t, g=1.0):
    put(drums, kick(), t, 0.95 * g)
    kicks.append(t)


# ---------- 0–4 s: abertura ----------
put(music, pad(AM, 2.0, cutoff=900, attack=1.0), 0.0, 0.8)
put(music, pad(F, 2.0, cutoff=1300, attack=0.4), 2.0, 0.8)
put(fx, impact(1.4), 0.08, 0.55)
put(fx, riser(0.8, 800, 7000), 0.0, 0.18)
for i, n in enumerate([69, 72, 76, 81, 84, 88, 91, 93]):  # cada letra de PUSHLIFE
    put(music, pluck(n, 0.5, bright=6000, decay=7), 1.0 + i * 0.125, 0.22, pan=(-0.4 if i % 2 else 0.4))
put(music, bell(76), 2.5, 0.16, -0.2)
put(music, bell(81), 2.5, 0.12, 0.2)
put(fx, riser(1.0, 400, 10000), 3.0, 0.32)
put(fx, whoosh(0.7), 3.35, 0.3)

# ---------- 4–8 s: "Can't stop scrolling?" → "Turn it into exercise." ----------
put(music, pad(AM, 2.0, cutoff=700), 4.0, 0.6)
put(music, pad(F, 1.0, cutoff=1400), 6.0, 0.6)
put(music, pad(G, 1.0, cutoff=2200), 7.0, 0.6)
for b in range(8):
    t = 4.0 + b * BEAT
    if t < 6.0:
        if b % 2 == 0:
            K(t, 0.8)
        put(drums, filt(hat(), "low", 9000), t + 0.25, 0.25)
        put(music, bass(ROOT["Am"], 0.22, cutoff=260), t + 0.25, 0.55)
    else:
        K(t, 0.9)
        put(drums, hat(), t + 0.25, 0.3)
        put(music, bass(ROOT["F"] if t < 7 else ROOT["G"], 0.22, cutoff=500), t + 0.25, 0.6)
for i in range(8):  # virada de caixa crescendo até o drop
    put(drums, clap(), 7.0 + i * 0.125, 0.12 + 0.06 * i)
put(fx, riser(2.0, 300, 12000), 6.0, 0.35)
put(fx, whoosh(0.6), 7.45, 0.35)

# ---------- 8–24 s: drop (câmera, saldo, bloqueio) ----------
put(drums, crash(), 8.0, 0.5)
put(fx, impact(1.2), 8.0, 0.5)
prog = ["Am", "F", "C", "G"] * 2
melody = {  # 16–24 s: melodia em colcheias (None = pausa)
    "Am": [76, None, 76, 74, 72, None, 74, 76],
    "F": [72, None, 72, 69, 72, None, 74, 72],
    "C": [76, None, 79, 76, 74, None, 72, 74],
    "G": [74, None, 71, 67, 74, None, 76, 74],
}
for bar, ch in enumerate(prog):
    t0 = 8.0 + bar * 2.0
    put(music, pad(CH[ch], 2.0, cutoff=2600, attack=0.05), t0, 0.42)
    for b in range(4):
        t = t0 + b * BEAT
        K(t)
        if b in (1, 3):
            put(drums, clap(), t, 0.55)
        put(drums, hat(open_=True), t + 0.25, 0.16)
        for s in range(4):
            put(drums, shaker(), t + s * 0.125, 0.10 if s % 2 else 0.05, pan=0.3)
    for e in range(8):
        n = ROOT[ch] + (12 if e in (3, 6) else 0)
        put(music, bass(n, 0.2, cutoff=900), t0 + e * 0.25, 0.55)
    tones = CH[ch]
    for s in range(16):
        n = tones[[0, 1, 2, 1][s % 4]] + 12
        put(music, pluck(n, 0.25, bright=3500, decay=14), t0 + s * 0.125, 0.07, pan=(-0.5 if s % 2 else 0.5))
    if bar >= 4:
        for e, n in enumerate(melody[ch]):
            if n:
                put(music, pluck(n + 12, 0.4, bright=5200, decay=8), t0 + e * 0.25, 0.17, pan=0.1)
# cada flexão contada (9,0 → 13,5 s): um "ding" que sobe
for i in range(10):
    put(fx, bell(76 + [0, 2, 4, 7, 9, 12, 14, 16, 19, 21][i], 0.5), 9.0 + i * 0.5, 0.16)
put(fx, whoosh(0.5), 13.55, 0.3)
put(fx, riser(1.8, 1500, 9000), 14.2, 0.08)
put(fx, bell(93, 1.4), 16.0, 0.2)
put(fx, whoosh(0.5), 17.55, 0.3)
for i in range(6):  # cadeados
    put(fx, pluck(45 + i * 2, 0.25, bright=1800, decay=18), 19.0 + i * 0.25, 0.35)
put(fx, whoosh(0.6), 20.6, 0.32)
put(fx, impact(0.8), 21.0, 0.25)
put(fx, pluck(84, 0.12, bright=8000, decay=40), 22.5, 0.3)
put(fx, whoosh(0.7), 23.4, 0.35)

# ---------- 24–27 s: recursos ----------
put(drums, crash(1.6), 24.0, 0.3)
for bar, (ch, d) in enumerate([("Am", 2.0), ("F", 1.0)]):
    t0 = 24.0 + bar * 2.0
    put(music, pad(CH[ch], d, cutoff=2600, attack=0.05), t0, 0.42)
    for b in range(int(d / BEAT)):
        t = t0 + b * BEAT
        K(t)
        if b in (1, 3):
            put(drums, clap(), t, 0.5)
        put(drums, hat(open_=True), t + 0.25, 0.16)
    for e in range(int(d / 0.25)):
        put(music, bass(ROOT[ch], 0.2, cutoff=900), t0 + e * 0.25, 0.55)
for i, n in enumerate([69, 72, 76, 79, 81, 84]):  # cada recurso que entra
    put(fx, pluck(n + 12, 0.35, bright=6000, decay=10), 24.2 + i * 0.25, 0.2, pan=(-0.3 if i % 2 else 0.3))
for i in range(8):
    put(drums, clap(), 26.0 + i * 0.125, 0.1 + 0.05 * i)
put(fx, riser(1.0, 500, 12000), 26.0, 0.35)

# ---------- 27–30 s: final ----------
put(drums, crash(2.8), 27.0, 0.5)
put(fx, impact(2.0), 27.0, 0.6)
K(27.0)
put(music, stab([57, 60, 64, 69]), 27.0, 0.5)
put(music, pad([57, 64, 67, 72], 2.4, cutoff=2000, attack=0.05, release=0.6), 27.0, 0.45)
for i, n in enumerate([69, 72, 76, 81, 84, 88, 91, 93]):
    put(music, pluck(n, 0.5, bright=6000, decay=7), 27.45 + i * 0.125, 0.18, pan=(-0.4 if i % 2 else 0.4))
for b in range(4):
    t = 27.0 + b * BEAT
    put(drums, hat(open_=True), t + 0.25, 0.12)
    put(music, bass(ROOT["Am"], 0.4, cutoff=500), t, 0.45)
K(28.0, 0.8)
K(29.0)
put(drums, crash(1.5), 29.0, 0.35)
put(music, stab([60, 64, 69, 72]), 29.0, 0.45)
put(music, pad([57, 60, 64, 71], 1.0, cutoff=1600, attack=0.05, release=0.6), 29.0, 0.45)
put(music, bell(81, 1.2), 29.0, 0.16)
put(music, bass(ROOT["Am"], 1.0, cutoff=400), 29.0, 0.5)

# ---------- mixagem ----------
t = np.arange(N) / SR
duck = np.ones(N)
for k in kicks:
    i = int(k * SR)
    seg = t[i:i + int(0.35 * SR)] - k
    duck[i:i + len(seg)] = np.minimum(duck[i:i + len(seg)], 1 - 0.55 * np.exp(-seg / 0.09))
music *= duck

ir_t = np.arange(int(1.4 * SR)) / SR
ir = rng.normal(0, 1, (2, len(ir_t))) * np.exp(-ir_t * 4.5)
ir[:, : int(0.012 * SR)] = 0
wet = np.vstack([fftconvolve(music[c] + 0.6 * fx[c], ir[c])[:N] for c in range(2)])
wet /= np.max(np.abs(wet)) + 1e-9

# eco em 3/16 nos sons de efeito e plucks
delay = np.zeros_like(fx)
dl = int(0.375 * SR)
delay[0, dl:] = fx[1, :-dl] * 0.25
delay[1, dl:] = fx[0, :-dl] * 0.25

mix = drums + music + fx + delay + 0.18 * wet * np.max(np.abs(music))
mix = np.vstack([filt(mix[c], "high", 32) for c in range(2)])
mix = np.tanh(1.3 * mix / np.max(np.abs(mix))) / np.tanh(1.3)
fade = np.ones(N)
fade[-int(0.8 * SR):] = np.linspace(1, 0, int(0.8 * SR)) ** 1.5
fade[: int(0.01 * SR)] = np.linspace(0, 1, int(0.01 * SR))
mix *= fade * 0.9
wavfile.write(sys.argv[1] if len(sys.argv) > 1 else "music.wav", SR, (mix.T * 32767).astype(np.int16))
print("ok")
