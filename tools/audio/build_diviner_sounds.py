"""Synthesize the Transient Diviner's cinematic sounds (no source samples).

    python tools/audio/build_diviner_sounds.py [repo_dir]

Needs numpy, scipy and soundfile (libsndfile >= 1.0.28 for Ogg Vorbis), like tools/audio/analyze_records.py.
Writes src/main/resources/assets/cabinmod/sounds/diviner/*.ogg. Cinematic sounds the crafting player hears
(played with SimpleSoundInstance.forUI) are stereo; sounds placed in the world are mono so Minecraft can
position them. Timings match client/diviner/DivinerCinematic.java (20 ticks = 1 s):
    ascend          intro: no wind - the space ambience (sub, "oo" pad, distant chimes) fades in as the camera
                    climbs and swells into the choir + bells reveal (~4.4 s) as the panel and the Agartha
                    apparition appear
    space_loop      seamless choir pad, sub drone and distant chimes while the panel is open (kept as is: an
                    existing file is not rewritten, since every sound shares one random stream)
    space_swell     seamless brighter layer mixed over space_loop while you work the panel
    slot_place/take, star_tick   chimes for the input slot and each constellation star
    cabin_hum_1..8  the Cabin speaking: one low closed-mouth hum per word
    launch_world, heavens_open   (mono, world) what other players hear: the star going up, the sky opening
    launch          a result shoots up out of the panel
    craft           chime when a craft completes
    descend         the fall (exactly 4 s = RETURN_TICKS): falling Shepard tone, re-entry roar and
                    crackle, a reverse swell that cuts off on the impact
    impact_stinger  the crafter's impact: sub drop, blast, a choir hit, glass shatter, bells, long tail
    impact          (mono, world) the landing as everyone nearby hears it
    fall_world      (mono, world) the star screaming down, for players who aren't in the cinematic
"""
import os
import sys

import numpy as np
import soundfile as sf
from scipy import signal

REPO = next((a for a in sys.argv[1:] if not a.startswith("--")), os.getcwd())
OUT = os.path.join(REPO, "src/main/resources/assets/cabinmod/sounds/diviner")
SR = 44100
rng = np.random.default_rng(2026)


# ------------------------------------------------------------------ building blocks
def t_axis(sec):
    return np.arange(int(sec * SR)) / SR


def silence(sec, ch=2):
    return np.zeros((int(sec * SR), ch))


def sos(kind, f, order=4):
    f = np.atleast_1d(f) / (SR / 2)
    f = np.clip(f, 1e-4, 0.999)
    return signal.butter(order, f if len(f) > 1 else f[0], btype=kind, output="sos")


def filt(x, kind, f, order=4):
    return signal.sosfilt(sos(kind, f, order), x, axis=0)


def peak(x, f, q):
    b, a = signal.iirpeak(f / (SR / 2), q)
    return signal.lfilter(b, a, x, axis=0)


def pan(mono, p):
    """Equal-power pan; p in -1..1 (scalar or per-sample)."""
    p = np.broadcast_to(np.asarray(p, dtype=float), mono.shape)
    a = (p + 1) * np.pi / 4
    return np.stack([mono * np.cos(a), mono * np.sin(a)], axis=1)


def place(dst, src, at):
    i = int(at * SR)
    n = min(len(src), len(dst) - i)
    if n > 0:
        dst[i:i + n] += src[:n]
    return dst


def env_adsr(n, a, d, s, r, sec_total):
    t = np.arange(n) / SR
    e = np.ones(n) * s
    e[t < a] = t[t < a] / max(a, 1e-6)
    m = (t >= a) & (t < a + d)
    e[m] = 1 - (1 - s) * (t[m] - a) / max(d, 1e-6)
    rel = t > sec_total - r
    e[rel] *= np.clip((sec_total - t[rel]) / max(r, 1e-6), 0, 1)
    return e


def saw(freq, t, phase=0.0):
    """Band-limited saw by additive synthesis up to ~8 kHz; freq may vary per sample."""
    freq = np.broadcast_to(np.asarray(freq, dtype=float), t.shape)
    ph = 2 * np.pi * np.cumsum(freq) / SR + phase
    out = np.zeros_like(t)
    top = int(8000 / max(freq.max(), 1))
    for k in range(1, max(2, top)):
        out += np.sin(k * ph) / k * (freq * k < 9000)
    return out * 0.6


def vowel(x, which="ah"):
    formants = {"ah": [(800, 1.0, 9), (1150, 0.55, 11), (2900, 0.22, 20), (3300, 0.16, 22)],
                "oo": [(325, 1.0, 7), (700, 0.45, 9), (2530, 0.12, 20), (3500, 0.08, 22)],
                "eh": [(530, 1.0, 8), (1840, 0.5, 14), (2480, 0.2, 18), (3500, 0.12, 22)]}[which]
    return sum(peak(x, f, q) * g for f, g, q in formants)


def choir(notes, sec, which="ah", spread=0.9, breath=0.08, attack=0.6, release=1.5, vib=5.2):
    """A small formant 'choir': three detuned voices per note with vibrato and breath, spread in stereo."""
    t = t_axis(sec)
    out = np.zeros((len(t), 2))
    for i, f in enumerate(notes):
        for v in range(3):
            det = 1 + rng.uniform(-0.004, 0.004)
            rate = vib + rng.uniform(-0.6, 0.6)
            fm = f * det * (1 + 0.005 * np.sin(2 * np.pi * rate * t + rng.uniform(0, 6.28)))
            src = saw(fm, t, rng.uniform(0, 6.28)) + rng.standard_normal(len(t)) * breath
            voice = vowel(src, which) * env_adsr(len(t), attack * rng.uniform(0.7, 1.3), 0.3, 0.85, release, sec)
            out += pan(voice, np.clip(((i + v / 3) / max(1, len(notes)) * 2 - 1) * spread + rng.uniform(-0.15, 0.15), -1, 1))
    return out / (len(notes) * 3) ** 0.5


def bell(f, sec, decay=2.5, bright=1.0):
    """Inharmonic struck bell (church-bell partial ratios)."""
    t = t_axis(sec)
    out = np.zeros(len(t))
    for ratio, g, dk in ((0.5, 0.5, 1.4), (1.0, 1.0, 1.0), (1.19, 0.6, 0.8), (1.5, 0.4, 0.7), (2.0, 0.55, 0.6),
                         (2.74, 0.35 * bright, 0.45), (3.76, 0.25 * bright, 0.35), (5.4, 0.18 * bright, 0.25)):
        out += np.sin(2 * np.pi * f * ratio * t + rng.uniform(0, 6.28)) * g * np.exp(-t / (decay * dk))
    out *= 1 - np.exp(-t / 0.002)
    return out


def shepard(sec, f_low=40, octaves=9, rise_oct=1.5, up=True, voices=9):
    """Shepard-Risset glissando: endlessly rising (or falling) tone."""
    t = t_axis(sec)
    out = np.zeros(len(t))
    shift = (t / sec) * rise_oct * (1 if up else -1)
    for k in range(voices):
        pos = (k + shift) % octaves
        f = f_low * 2 ** pos
        amp = np.exp(-0.5 * ((pos - octaves / 2) / (octaves / 6)) ** 2)
        ph = 2 * np.pi * np.cumsum(f) / SR + k
        out += np.sin(ph) * amp
    return out / voices * 2


def noise(sec, ch=1):
    n = rng.standard_normal((int(sec * SR), ch))
    return n[:, 0] if ch == 1 else n


def sweep_noise(sec, f0, f1, curve=1.0, q=3.0, bands=24):
    """Noise whose band centre glides from f0 to f1 (bank of band-passes, crossfaded)."""
    t = t_axis(sec)
    src = noise(sec)
    centre = np.exp(np.log(f0) + (np.log(f1) - np.log(f0)) * (t / sec) ** curve)
    out = np.zeros(len(t))
    fc = np.geomspace(min(f0, f1) * 0.8, max(f0, f1) * 1.2, bands)
    for f in fc:
        lo, hi = f / (1 + 1 / q), f * (1 + 1 / q)
        b = filt(src, "bandpass", [lo, min(hi, SR / 2.2)], 2)
        w = np.exp(-0.5 * (np.log(centre / f) / 0.18) ** 2)
        out += b * w
    return out


def boom(sec, f_hi=140, f_lo=32, drop=0.09, drive=2.5):
    t = t_axis(sec)
    f = f_lo + (f_hi - f_lo) * np.exp(-t / drop)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / (sec * 0.35))
    return np.tanh(x * drive) / np.tanh(drive)


def shatter(sec, count=260, lo=1800, hi=12000, spread=1.0, until=0.6):
    out = np.zeros((int(sec * SR), 2))
    for _ in range(count):
        f = np.exp(rng.uniform(np.log(lo), np.log(hi)))
        at = rng.exponential(until / 4)
        if at > until:
            at = rng.uniform(0, until)
        d = rng.uniform(0.02, 0.35)
        n = int(min(sec - at, d * 6) * SR)
        if n <= 0:
            continue
        tt = np.arange(n) / SR
        g = np.sin(2 * np.pi * f * tt + rng.uniform(0, 6.28)) * np.exp(-tt / d) * rng.uniform(0.2, 1.0)
        g += np.sin(2 * np.pi * f * 2.76 * tt) * np.exp(-tt / (d * 0.5)) * 0.3
        place(out, pan(g, rng.uniform(-spread, spread)), at)
    return out


def sparkles(sec, count, lo, hi, start=0.0, end=None, decay=(0.08, 0.5), spread=1.0):
    end = sec if end is None else end
    out = np.zeros((int(sec * SR), 2))
    for _ in range(count):
        f = np.exp(rng.uniform(np.log(lo), np.log(hi)))
        at = rng.uniform(start, end)
        d = rng.uniform(*decay)
        n = int(min(sec - at, d * 6) * SR)
        if n <= 0:
            continue
        tt = np.arange(n) / SR
        g = np.sin(2 * np.pi * f * tt) * np.exp(-tt / d) * (1 - np.exp(-tt / 0.003)) * rng.uniform(0.3, 1.0)
        place(out, pan(g, rng.uniform(-spread, spread)), at)
    return out


def reverb(x, sec=3.0, wet=0.35, predelay=0.02, damp=6000, width=1.0):
    """Stereo convolution reverb with decorrelated exponential-noise impulse responses."""
    if x.ndim == 1:
        x = np.stack([x, x], axis=1)
    n = int(sec * SR)
    tt = np.arange(n) / SR
    irs = []
    for c in range(2):
        ir = rng.standard_normal(n) * np.exp(-tt * 6.9 / sec)
        ir = filt(ir, "lowpass", damp, 2)
        ir[:int(predelay * SR)] = 0
        irs.append(ir / np.sqrt(np.sum(ir ** 2)))
    if width < 1:
        m = (irs[0] + irs[1]) / 2
        irs = [m + (irs[0] - m) * width, m + (irs[1] - m) * width]
    y = np.stack([signal.fftconvolve(x[:, 0], irs[0]), signal.fftconvolve(x[:, 1], irs[1])], axis=1)
    out = np.zeros_like(y)
    out[:len(x)] += x * (1 - wet)
    return out + y * wet


def chorus(x, depth=0.004, rate=0.3):
    t = np.arange(len(x)) / SR
    out = np.zeros_like(x)
    for c in range(x.shape[1]):
        d = (depth * (1 + np.sin(2 * np.pi * rate * t + c * 1.7))) * SR + 10
        idx = np.clip(np.arange(len(x)) - d, 0, len(x) - 1)
        out[:, c] = 0.6 * x[:, c] + 0.5 * np.interp(idx, np.arange(len(x)), x[:, c])
    return out


def fade(x, fin=0.005, fout=0.05):
    x = x.copy()
    a, b = int(fin * SR), int(fout * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)[:, None] if x.ndim == 2 else np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)[:, None] if x.ndim == 2 else np.linspace(1, 0, b)
    return x


def master(x, peak_db=-1.5, drive=1.2):
    x = np.tanh(x / np.max(np.abs(x)) * drive) if drive else x
    return x / np.max(np.abs(x)) * 10 ** (peak_db / 20)


def write(name, x, sec=None):
    if sec is not None:
        n = int(sec * SR)
        x = x[:n] if len(x) >= n else np.concatenate([x, np.zeros((n - len(x),) + x.shape[1:])])
    os.makedirs(OUT, exist_ok=True)
    sf.write(os.path.join(OUT, name + ".ogg"), x.astype(np.float32), SR, format="OGG", subtype="VORBIS")
    print("wrote", name, f"{len(x) / SR:.2f}s", "stereo" if x.ndim == 2 else "mono")


A2, A3 = 110.0, 220.0
AMAJ9 = [A3, 277.18, 329.63, 415.30, 493.88, 659.26]          # A C# E G# B E
DMAJ9_HI = [293.66, 369.99, 440.0, 554.37, 659.26, 880.0]


# ------------------------------------------------------------------ the sounds
def ascend():
    """The climb: no wind - the space ambience fades in from the start and swells into the reveal."""
    sec = 7.0
    out = silence(sec)
    t = t_axis(sec)
    rv = 4.4
    # sub drone and an "oo" pad breathing in, the same palette as space_loop
    sub_in = np.clip(t / 2.5, 0, 1) ** 2
    drone = filt(saw(A2 / 2, t) * 0.4 + np.sin(2 * np.pi * A2 / 2 * t) * 0.8, "lowpass", 220)
    out += pan(drone * sub_in, 0) * 0.35
    pad = choir(AMAJ9, sec, "oo", attack=2.5, release=1.5, vib=4.6)
    out += pad * (0.25 + 0.75 * np.clip(t / rv, 0, 1) ** 1.5)[:, None] * 0.7
    # distant chimes, closer together as the climb goes on
    at = 0.5
    while at < rv - 0.3:
        place(out, pan(bell(rng.choice([1318.5, 1760.0, 1975.5, 2637.0]), 2.5, 1.4, 0.6), rng.uniform(-0.8, 0.8)) * 0.12, at)
        at += 0.4 + 0.9 * (1 - at / rv)
    place(out, sparkles(sec, 50, 2500, 8000, 2.2, rv, (0.1, 0.6)) * 0.14, 0)
    # the reveal: choir chord, bells, a soft sub swell, glitter
    ch = choir(AMAJ9, sec - rv, "ah", attack=0.25, release=1.8)
    place(out, ch * 0.9, rv)
    place(out, choir([A2, A2 * 1.5], sec - rv, "oo", attack=0.3, release=1.8) * 0.6, rv)
    for f, at, g in ((880, rv, 0.3), (1318.5, rv + 0.05, 0.2), (1760, rv + 0.12, 0.14), (659.26, rv, 0.25)):
        place(out, pan(bell(f, sec - at, 2.2), rng.uniform(-0.5, 0.5)) * g, at)
    sub = np.sin(2 * np.pi * 55 * t_axis(sec - rv)) * np.exp(-t_axis(sec - rv) / 1.6) * (1 - np.exp(-t_axis(sec - rv) / 0.2))
    place(out, pan(sub, 0) * 0.4, rv)
    place(out, sparkles(sec, 60, 2500, 9000, rv, sec - 0.5, (0.05, 0.4)) * 0.25, 0)
    out = chorus(reverb(out, 4.0, 0.42))
    return fade(master(out, -8.0, 0), 0.3, 0.6)


def space_loop():
    sec, xfade = 16.0, 2.0
    total = sec + xfade
    out = silence(total)
    t = t_axis(total)
    # sub drone with a slowly opening filter
    drone = saw(A2 / 2, t) * 0.5 + np.sin(2 * np.pi * A2 / 2 * t) * 0.8
    lfo = 0.5 + 0.5 * np.sin(2 * np.pi * t / sec)
    drone = filt(drone, "lowpass", 260) * (0.6 + 0.4 * lfo)
    out += pan(drone, 0) * 0.5
    # choir pad, two chords breathing in and out
    a = choir(AMAJ9, total, "oo", attack=3.0, release=3.0, vib=4.6) * 1.3
    b = choir(DMAJ9_HI, total, "ah", attack=3.0, release=3.0, vib=4.8) * 0.9
    out += a * (0.55 + 0.45 * np.cos(2 * np.pi * t / sec))[:, None]
    out += b * (0.45 - 0.45 * np.cos(2 * np.pi * t / sec))[:, None]
    # ethereal high shimmer and distant chimes
    out += sparkles(total, 60, 2000, 7000, 0, total, (0.3, 1.2)) * 0.18
    for at in np.arange(0.8, total, 2.6):
        place(out, pan(bell(rng.choice([1318.5, 1760.0, 1975.5, 2637.0]), 3.0, 1.6, 0.6), rng.uniform(-0.8, 0.8)) * 0.14, at)
    out = chorus(reverb(out, 4.0, 0.45))[:int(total * SR)]
    # seamless: overlap the tail onto the head
    n, x = int(sec * SR), int(xfade * SR)
    loop = out[:n].copy()
    ramp = np.linspace(0, 1, x)[:, None]
    loop[:x] = loop[:x] * ramp + out[n:n + x] * (1 - ramp)
    return master(loop, -3.0, 0)


def launch():
    sec = 2.2
    out = silence(sec)
    tt = t_axis(sec)
    place(out, pan(filt(noise(0.03), "highpass", 2000) * np.exp(-t_axis(0.03) / 0.006), 0) * 0.8, 0)
    zt = t_axis(0.6)
    f = 250 * np.exp(np.log(5000 / 250) * (zt / 0.6) ** 0.7)
    zap = np.sin(2 * np.pi * np.cumsum(f) / SR + 3 * np.sin(2 * np.pi * np.cumsum(f * 2.01) / SR)) * np.exp(-zt / 0.35)
    place(out, pan(zap, np.linspace(-0.6, 0.6, len(zt))) * 0.5, 0.0)
    wh = sweep_noise(0.9, 300, 8000, 0.6) * np.sin(np.linspace(0, np.pi, int(0.9 * SR))) ** 2
    place(out, pan(wh, np.linspace(-0.8, 0.8, int(0.9 * SR))) * 0.8, 0.0)
    place(out, pan(bell(1760, 1.5, 1.0), 0.2) * 0.35, 0.55)
    place(out, pan(bell(2637, 1.4, 0.8), -0.2) * 0.2, 0.62)
    place(out, sparkles(sec, 40, 3000, 11000, 0.05, 1.0, (0.03, 0.2)) * 0.5, 0)
    return fade(master(reverb(out, 2.0, 0.3), -8.0, 0.9), 0.001, 0.2)


def craft():
    sec = 2.0
    out = silence(sec)
    for f, at, g, p in ((1046.5, 0.0, 0.5, -0.3), (1318.5, 0.06, 0.4, 0.0), (1568.0, 0.12, 0.35, 0.3), (2093.0, 0.2, 0.25, 0.0)):
        place(out, pan(bell(f, sec - at, 1.1, 0.7), p) * g, at)
    place(out, sparkles(sec, 30, 3000, 9000, 0.0, 0.8, (0.03, 0.25)) * 0.4, 0)
    place(out, choir([523.25, 659.26, 783.99], 1.6, "ah", attack=0.03, release=1.0) * 0.5, 0.0)
    return fade(master(reverb(out, 2.2, 0.35), -8.0, 0.9), 0.001, 0.3)


def descend():
    sec = 4.0
    out = silence(sec)
    t = t_axis(sec)
    grow = (t / sec) ** 1.7
    # falling Shepard tone: the whole sky dropping away
    out += chorus(pan(shepard(sec, 40, rise_oct=2.0, up=False) * (0.5 + 0.5 * grow), 0)) * 0.45
    # re-entry roar, widening and getting rougher
    roar = sweep_noise(sec, 200, 2200, 1.2, q=1.5)
    roar = np.tanh(roar * (1 + grow) * 1.1) * (0.15 + 0.5 * grow)
    roar = signal.sosfilt(sos("lowpass", 2600, 2), roar)   # a roar, not hiss
    out += pan(roar, 0.35 * np.sin(2 * np.pi * 1.3 * t)) * 0.45
    rumble = filt(noise(sec), "lowpass", 90) * grow * 5
    out += pan(rumble, 0)
    whistle = np.sin(2 * np.pi * np.cumsum(3200 * np.exp(-t * 0.6) + 300) / SR) * np.clip(t / 0.4, 0, 1) * 0.16
    out += pan(whistle, -0.2)
    crack = (rng.random(len(t)) < 0.0002 + 0.0008 * grow) * rng.standard_normal(len(t)) * 1.5
    out += pan(filt(crack, "bandpass", [600, 7000], 2), rng.uniform(-1, 1, len(t)) * 0.0 + 0.3 * np.sin(t * 11)) * 0.8
    # reverse swell, cut right on the impact
    sw_len = 1.6
    sw = filt(noise(sw_len), "highpass", 1500) * np.exp(np.linspace(-7, 0, int(sw_len * SR)))
    place(out, pan(sw, 0) * 0.35, sec - sw_len)
    place(out, choir(AMAJ9, sw_len, "ah", attack=sw_len * 0.9, release=0.01) * np.linspace(0, 1, int(sw_len * SR))[:, None] ** 3 * 1.4, sec - sw_len)
    out = master(out, -8.0, 0.8)
    return fade(out, 0.01, 0.004)


def impact_layers(stereo):
    sec = 6.0
    out = silence(sec)
    t = t_axis(sec)
    place(out, pan(boom(3.0, 160, 30, 0.08, 3.0), 0) * 1.3, 0)
    snap = filt(noise(0.08), "bandpass", [1200, 9000], 2) * np.exp(-t_axis(0.08) / 0.012)
    place(out, pan(snap, 0) * 1.4, 0)
    blast = filt(noise(2.0), "lowpass", 3500) * np.exp(-t_axis(2.0) / 0.35)
    place(out, pan(np.tanh(blast * 3), 0) * 0.8, 0.005)
    place(out, shatter(sec, 320 if stereo else 220, spread=1.0 if stereo else 0.0) * 0.6, 0.01)
    metal = sum(np.sin(2 * np.pi * 420 * r * t) * g * np.exp(-t / d) for r, g, d in ((1, 0.5, 1.8), (2.76, 0.35, 1.2), (5.4, 0.2, 0.7), (8.93, 0.12, 0.4)))
    out += pan(metal, 0) * 0.22
    debris = sparkles(sec, 120, 1500, 6000, 0.3, 3.0, (0.01, 0.06), spread=1.0 if stereo else 0.0) * 0.35
    out += debris
    if stereo:
        hit = choir(AMAJ9 + [880.0], 3.5, "ah", attack=0.01, release=2.5) * 2.0
        place(out, hit, 0.02)
        place(out, choir([A2, 164.81], 3.5, "oo", attack=0.01, release=2.5) * 1.2, 0.02)
        for f, at, g in ((440, 0.0, 0.6), (659.26, 0.04, 0.4), (880, 0.1, 0.35), (1318.5, 0.18, 0.25)):
            place(out, pan(bell(f, sec - at, 3.0), rng.uniform(-0.6, 0.6)) * g, at)
    out = reverb(out, 4.5 if stereo else 3.0, 0.4)
    return out


def impact_stinger():
    return fade(master(impact_layers(True), -8.0, 1.0), 0.0005, 1.0)


def impact_world():
    x = impact_layers(False)
    return fade(master(x.mean(axis=1), -7.5, 1.0), 0.0005, 0.8)


def fall_world():
    sec = 4.0
    t = t_axis(sec)
    grow = (t / sec) ** 1.6
    x = np.tanh(sweep_noise(sec, 250, 2500, 1.0, 1.6) * (1 + 2 * grow) * 1.5) * (0.2 + grow) * 0.7
    x = signal.sosfilt(sos("lowpass", 3000, 2), x)
    x += np.sin(2 * np.pi * np.cumsum(2800 * np.exp(-t * 0.5) + 250) / SR) * 0.2 * np.clip(t / 0.3, 0, 1)
    x += filt(noise(sec), "lowpass", 100) * grow * 4
    x += shepard(sec, 50, rise_oct=1.8, up=False) * 0.3
    return fade(master(x, -8.0, 0.8), 0.05, 0.004)


# ------------------------------------------------------------------ the heavens: interaction, the Cabin's voice, other players
def chime(notes, sec=1.4, gain=0.4, falling=False):
    out = silence(sec)
    seq = notes[::-1] if falling else notes
    for i, f in enumerate(seq):
        place(out, pan(bell(f, sec - i * 0.05, 0.7, 0.5), rng.uniform(-0.4, 0.4)) * gain * (0.85 ** i), i * 0.05)
    place(out, sparkles(sec, 14, 3000, 9000, 0.0, 0.4, (0.02, 0.15)) * 0.2, 0)
    return fade(master(reverb(out, 2.0, 0.4), -12.0, 0), 0.001, 0.3)


def slot_place():
    return chime([1318.5, 1760.0, 2217.5])


def slot_take():
    return chime([1318.5, 1760.0, 2217.5], falling=True)


def star_tick():
    sec = 1.2
    out = pan(bell(1760.0, sec, 0.5, 0.5), 0) * 0.5
    out += sparkles(sec, 6, 4000, 9000, 0, 0.15, (0.02, 0.1)) * 0.2
    return fade(master(reverb(out, 1.6, 0.4), -14.0, 0), 0.001, 0.2)


def space_swell():
    """Reactive layer over space_loop: a brighter high choir and shimmer, mixed in by code while you work."""
    sec, xfade = 16.0, 2.0
    total = sec + xfade
    t = t_axis(total)
    out = choir([f * 2 for f in AMAJ9[:4]], total, "ah", attack=3.0, release=3.0, vib=5.0) * 0.8
    out += choir(DMAJ9_HI, total, "eh", attack=3.0, release=3.0, vib=5.4) * (0.5 + 0.5 * np.sin(2 * np.pi * t / sec))[:, None] * 0.5
    out += sparkles(total, 80, 3000, 10000, 0, total, (0.2, 0.9)) * 0.22
    out = chorus(reverb(out, 4.0, 0.5))[:int(total * SR)]
    n, x = int(sec * SR), int(xfade * SR)
    loop = out[:n].copy()
    ramp = np.linspace(0, 1, x)[:, None]
    loop[:x] = loop[:x] * ramp + out[n:n + x] * (1 - ramp)
    return master(loop, -6.0, 0)


def cabin_hum(i):
    """One word of the Cabin speaking: a short, low, closed-mouth hum (mm / hm / oo) with a little room."""
    hrng = np.random.default_rng(700 + i)
    sec = hrng.uniform(0.16, 0.3)
    f0 = [92, 98, 104, 110, 117, 123, 131, 139][i]
    t = t_axis(sec + 0.1)
    glide = f0 * (1 + hrng.uniform(-0.06, 0.06) * (t / sec)) * (1 + 0.006 * np.sin(2 * np.pi * 5 * t))
    src = saw(glide, t) + rng.standard_normal(len(t)) * 0.02
    which = ["oo", "oo", "ah", "eh"][i % 4]
    v = filt(vowel(src, which), "lowpass", 900 if i % 2 == 0 else 1300)    # closed mouth: dark, nasal
    v += np.sin(2 * np.pi * np.cumsum(glide) / SR) * 0.6                    # chest
    e = np.clip(t / 0.035, 0, 1) * np.clip((sec - t) / 0.08, 0, 1)
    out = reverb(v * e, 0.9, 0.22, damp=3000).mean(axis=1)[:int((sec + 0.5) * SR)]   # mono: also played at the diviner
    return fade(master(out, -9.0, 1.2), 0.002, 0.25)


def launch_world():
    sec = 2.4
    t = t_axis(sec)
    f = 300 * np.exp(np.log(3000 / 300) * (t / sec) ** 0.6)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.9) * 0.4
    x += sweep_noise(sec, 400, 6000, 0.7) * np.sin(np.linspace(0, np.pi, len(t))) ** 2 * 0.5
    x += bell(1760, sec, 1.0)[:len(t)] * 0.25
    return fade(master(reverb(x, 2.0, 0.3).mean(axis=1), -6.0, 0.9), 0.002, 0.3)


def heavens_open():
    sec = 6.0
    t = t_axis(sec)
    x = choir(AMAJ9, sec, "ah", attack=1.6, release=2.5).mean(axis=1) * 1.2
    x += np.sin(2 * np.pi * 55 * t) * np.clip(t / 1.5, 0, 1) * np.exp(-t / 3) * 0.5
    for f, at in ((880, 1.2), (1318.5, 1.5), (1760, 1.9)):
        place(x[:, None], bell(f, sec - at, 2.0)[:, None] * 0.25, at)
    return fade(master(reverb(x, 4.0, 0.5).mean(axis=1), -6.0, 0), 0.3, 1.0)



write("ascend", ascend(), 7.0)
if os.path.exists(os.path.join(OUT, "space_loop.ogg")) and "--all" not in sys.argv:
    space_loop()   # keep the random stream in step
else:
    write("space_loop", space_loop())
write("launch", launch(), 2.2)
write("craft", craft(), 2.0)
write("descend", descend(), 4.0)
write("impact_stinger", impact_stinger(), 6.0)
write("impact", impact_world(), 5.0)
write("fall_world", fall_world(), 4.0)
write("slot_place", slot_place(), 1.4)
write("slot_take", slot_take(), 1.4)
write("star_tick", star_tick(), 1.2)
write("space_swell", space_swell())
for k in range(8):
    write(f"cabin_hum_{k + 1}", cabin_hum(k))
write("launch_world", launch_world(), 2.4)
write("heavens_open", heavens_open(), 6.0)
