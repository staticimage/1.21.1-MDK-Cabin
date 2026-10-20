"""Build the Jehoash v2 sounds (design/JEHOASH_V2.md section 7) from real recordings, haphazardly.

Every layer is a real recording: voices, thunder, explosions, firecrackers, wind, fire, vinyl, wood and
metal. There are no oscillators, synth sweeps or pure tones anywhere. The user's rule is that nothing may
sound beepy except the kept teleport files. numpy does all the processing: tape-speed changes and speed
curves, octave-down growl layers, slow ring modulation (20-60 Hz) for an unnatural edge, formant-ish band
emphasis, soft-clip saturation, convolution reverb with a synthetic decaying-noise IR, granular
stutter/reverse, and record-scratch playback driven by a hand-motion curve. Sung and droning layers, and
any voice layer that was caught ringing (hurt_1's and hurt_3's groan bodies, roar_1's scream), also go
through detone(), which turns down any partial ringing far above its neighbours, so even a hummed or chanted
note can never come across as a pure tone.

The user asked for the sounds to be "more haphazard", so nothing lines up neatly: a drunk tape transport
makes the voices stagger in pitch, throats lurch (the speed sags and snaps back), roars and dying howls
stutter and cut out at uneven moments, other monsters' snarls and the dying demon's chokes are flung in at
random, a second demon says "WELCOME" out of step, the tail of the word falls apart into scattered,
half-backwards echoes, crackle comes in random bursts, lightning forks, a record skips, and the orbital
strike sprays secondary blasts and a firecracker barrage around the main explosion. All of that chaos comes
from fixed seeds, so the build stays deterministic. The strike's sync points (planet impacts, the 8.0 s
blast) stay exactly where design/JEHOASH_V2.md puts them: every impact is lined up on its recording's main
attack (never on a crackle that comes before the bang), lands on a short duck of everything around it, and is
levelled in the finished mix so each planet lands heavier than the last.

Run it with Blender's Python. Its audaspace module `aud` decodes the sources and encodes Ogg Vorbis:

    blender --background --factory-startup --python tools/audio/build_sounds_v2.py -- \
        <manifest.json> <repo_dir> [only=name,name] [out=<dir>] [report=<file.json>]

<manifest.json> = {"sources": {key: {"path", "pack", "author", "license", "url", "item"?, "credit_line"?}},
                   "welcome_ref_out": <path>}
The script reads only files listed in the manifest.
  only=    builds a subset (names as in OUTPUTS). Each family is still levelled against all its members.
  out=     writes previews to another folder and leaves the repo and credits.txt alone.
  report=  also writes the measurement report as JSON.

Writes into assets/cabinmod/sounds/:
    joash/ambient_1..4, hurt_1..3, death, welcome_voice, roar_1..2, laser_charge, laser_fire_1..3,
    joash/strike (stereo, exactly 15.0 s), dj_booth/needle, dj_booth/slam
It overwrites the v1 ambient_1..3, hurt_1..3 and death files and deletes the v1 files that have no
same-named replacement (joash/welcome, joash/laser_1..3). The kept files (teleport, illusions,
illusion_pop, dj_booth/scratch, music/disco, life_of_the_party_jumpscare) are never written; they are
hashed before and after the build to prove it.
It rewrites the marked "build_sounds_v2.py" block at the end of sounds/credits.txt (credits for every
non-Kenney pack used) and leaves the rest of that file untouched.

Rules applied to every output: 44.1 kHz; mono except the strike; DC removed (high-pass + mean); 10 ms
fades; sample peak <= -1 dBFS and true peak <= -1 dBTP, both measured on the decoded Ogg (cut to its exact
granule length, which is what the game plays). Loudness (BS.1770-style: K-weighted, gated mean square) is
equal inside each family: every member is mastered to the same target, and when Vorbis pushes a member's
peak over the line, only that member's limiter ceiling is lowered (its loudness stays put). The short-hit
families (hurt, laser) are matched on the mean of integrated and loudest-200 ms loudness, so both how loud
they are overall and how loud the hit lands agree within the family. The laser's limiter only shaves the top
1.2 dB of its crack, so the shot keeps a crest of 12 dB or more in its first 30 ms (it reads quieter in LUFS
than the other families, but it hits harder). Deterministic: fixed seeds, no clock, and pinned Ogg stream
serials, so a rebuild produces byte-identical files.
"""
import hashlib
import json
import math
import os
import sys
import zlib

import aud
import numpy as np

SR = 44100
ARGS = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
if len(ARGS) < 2:
    raise SystemExit(__doc__)
MANIFEST_PATH, REPO = ARGS[0], ARGS[1]
OPTS = dict(a.split("=", 1) for a in ARGS[2:] if "=" in a)
ONLY = set(filter(None, OPTS.get("only", "").split(",")))
SOUNDS_DIR = os.path.join(REPO, "src", "main", "resources", "assets", "cabinmod", "sounds")
OUT_DIR = OPTS.get("out") or SOUNDS_DIR
IN_REPO = "out" not in OPTS

with open(MANIFEST_PATH, encoding="utf-8") as fh:
    MANIFEST = json.load(fh)
SOURCES = MANIFEST["sources"]
USED = {}          # source key -> set of output names that used it
CURRENT = [None]   # output being built (for USED)
_CACHE = {}

# v1 files replaced by differently named v2 files (the same-named ones are simply overwritten)
OBSOLETE = ["joash/welcome.ogg", "joash/laser_1.ogg", "joash/laser_2.ogg", "joash/laser_3.ogg"]
KEPT = ["joash/teleport_1.ogg", "joash/teleport_2.ogg", "joash/teleport_3.ogg",
        "joash/illusions_1.ogg", "joash/illusions_2.ogg",
        "joash/illusion_pop_1.ogg", "joash/illusion_pop_2.ogg", "joash/illusion_pop_3.ogg",
        "dj_booth/scratch_1.ogg", "dj_booth/scratch_2.ogg",
        "music/disco.ogg", "life_of_the_party_jumpscare.ogg"]
BEAT = 60.0 / 114.0


# ======================================================================================
# basics
# ======================================================================================
def ns(t):
    return int(round(t * SR))


def undb(d):
    return 10.0 ** (np.asarray(d, dtype=np.float64) / 20.0)


def dbfs(v):
    return 20.0 * math.log10(max(float(v), 1e-12))


def peak(x):
    return float(np.max(np.abs(x))) if len(x) else 0.0


def rms(x):
    return float(np.sqrt(np.mean(np.square(x)))) if len(x) else 0.0


def norm(x, peak_db=0.0):
    p = peak(x)
    return x * (float(undb(peak_db)) / p) if p > 0 else x


def active_rms(x):
    """RMS over the 10 ms frames within 30 dB of the loudest one (what a layer sounds like while it plays)."""
    m = mono(x)
    w = ns(0.01)
    k = len(m) // w
    if k < 2:
        return rms(m)
    fr = np.sqrt(np.mean(m[:k * w].reshape(k, w) ** 2, axis=1))
    sel = fr >= fr.max() * float(undb(-30.0))
    return float(np.sqrt(np.mean(fr[sel] ** 2)))


def lev(x, db):
    """Scale a layer to an active-RMS level (dBFS). Mixing by RMS keeps spiky crackle audible next to
    smooth layers, which peak-normalising does not."""
    r = active_rms(x)
    return x * (float(undb(db)) / r) if r > 0 else x


def mono(x):
    return x if x.ndim == 1 else x.mean(axis=1)


def src(key, start=None, end=None, stereo=False):
    """Load a manifest source (cached), resampled to 44.1 kHz, sliced to [start, end) seconds."""
    if key not in SOURCES:
        raise SystemExit(f"manifest has no source '{key}'")
    if key not in _CACHE:
        path = SOURCES[key]["path"]
        if not os.path.isfile(path):
            raise SystemExit(f"missing source file for '{key}': {path}")
        snd = aud.Sound(path)
        if int(snd.specs[0]) != SR:
            snd = snd.resample(SR, 3)                      # 3 = highest-quality resampler
        d = np.asarray(snd.data(), dtype=np.float64)
        _CACHE[key] = d[:, None] if d.ndim == 1 else d
    USED.setdefault(key, set()).add(CURRENT[0])
    d = _CACHE[key]
    a = 0 if start is None else max(0, ns(start))
    b = len(d) if end is None else min(len(d), ns(end))
    d = d[a:b]
    if stereo:
        return (np.repeat(d[:, :1], 2, axis=1) if d.shape[1] == 1 else d[:, :2]).copy()
    return d.mean(axis=1)


def env(n, pts):
    """Piecewise-linear envelope over n samples from (seconds, value) points."""
    ts, vs = zip(*pts)
    return np.interp(np.arange(n) / SR, ts, vs)


def apply_env(x, e):
    return x * (e if x.ndim == 1 else e[:, None])


def fades(x, fin=0.01, fout=0.01):
    """Raised-cosine fade in/out (seconds); the last sample ends at exactly 0."""
    y = x.copy()
    a, b = min(ns(fin), len(y)), min(ns(fout), len(y))
    if a > 1:
        w = 0.5 - 0.5 * np.cos(np.linspace(0.0, np.pi, a))
        y[:a] = (y[:a].T * w).T
    if b > 1:
        w = 0.5 + 0.5 * np.cos(np.linspace(0.0, np.pi, b))
        y[-b:] = (y[-b:].T * w).T
    return y


def onset(x, rel_db=-20.0):
    """Sample index where the 2 ms smoothed |x| first reaches rel_db below its maximum."""
    a = np.abs(mono(x))
    w = max(1, ns(0.002))
    sm = np.convolve(a, np.ones(w) / w, mode="same")
    return int(np.argmax(sm >= sm.max() * float(undb(rel_db))))


def env_peak_index(x, smooth=0.03):
    a = np.abs(mono(x))
    w = max(1, ns(smooth))
    return int(np.argmax(np.convolve(a, np.ones(w) / w, mode="same")))


def main_attack(x, search=0.3, back=0.08, pre_s=0.03, post_s=0.01, floor_db=-50.0):
    """Index of a hit's main attack: the strongest energy-flux onset (30 ms before vs 10 ms after, 0.5 ms
    steps) among the `back` seconds that lead up to the loudest 10 ms of the first `search` seconds. A quiet
    precursor (a crackle the slice starts with) can't win, because only the rise into the bang counts."""
    p = x ** 2 if x.ndim == 1 else np.mean(x ** 2, axis=1)
    pre, post = ns(pre_s), ns(post_s)
    m = max(1, min(len(p) - post, ns(search)))
    c = np.concatenate([[0.0], np.cumsum(p), np.zeros(post)])
    idx = np.arange(0, m, max(1, ns(0.0005)))
    po = (c[idx + post] - c[idx]) / post
    pk = int(idx[np.argmax(po)])
    cand = idx[(idx >= pk - ns(back)) & (idx <= pk)]
    pr = (c[cand] - c[np.maximum(cand - pre, 0)]) / pre       # before the slice counts as silence
    fl = ((c[cand + post] - c[cand]) / post) / np.maximum(pr, float(np.max(po)) * 10.0 ** (floor_db / 10.0))
    return int(cand[np.argmax(fl)])


def to_stereo(x, pan=0.0):
    """Mono -> stereo, equal-power pan (-1 left .. +1 right; centre = 0 dB in each channel)."""
    if x.ndim == 2:
        return x
    ang = (pan + 1.0) * math.pi / 4.0
    return np.stack([x * math.cos(ang), x * math.sin(ang)], axis=1) * math.sqrt(2.0)


def pan_curve(x, pans):
    """Mono -> stereo with a per-sample pan position (array, -1..1)."""
    ang = (np.clip(pans[:len(x)], -1, 1) + 1.0) * math.pi / 4.0
    return np.stack([x * np.cos(ang), x * np.sin(ang)], axis=1) * math.sqrt(2.0)


def put(dst, sig, t, gain_db=0.0, pan=None, align=0):
    """Mix sig into dst so that sample `align` of sig lands at time t (s). Clips to dst."""
    g = float(undb(gain_db))
    s = ns(t) - int(align)
    if s < 0:
        sig, s = sig[-s:], 0
    e = min(len(dst), s + len(sig))
    if e <= s:
        return dst
    piece = sig[:e - s] * g
    if dst.ndim == 2:
        if piece.ndim == 1:
            piece = to_stereo(piece, pan or 0.0)
        elif pan:
            piece = piece * np.array([min(1.0, 1.0 - pan), min(1.0, 1.0 + pan)])
    elif piece.ndim == 2:
        piece = piece.mean(axis=1)
    dst[s:e] += piece
    return dst


# ======================================================================================
# filters
# ======================================================================================
def _fft_len(n):
    return 1 << max(1, int(math.ceil(math.log2(max(2, n)))))


def filt(x, *resps, pad=0.3):
    """Zero-phase FFT filter. resps: functions Hz -> linear gain, multiplied together."""
    n = len(x)
    N = _fft_len(n + ns(pad))
    f = np.fft.rfftfreq(N, 1.0 / SR)
    g = np.ones_like(f)
    for r in resps:
        g = g * r(f)
    X = np.fft.rfft(x, N, axis=0)
    return np.fft.irfft(X * (g[:, None] if x.ndim == 2 else g), N, axis=0)[:n]


def LP(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))


def HP(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (fc / np.maximum(f, 1e-6)) ** (2 * order))


def BELL(f0, gain_db, width_oct=1.0):
    """Log-frequency bell; width = full width (octaves) at half the dB gain."""
    def r(f):
        o = np.log2(np.maximum(f, 1.0) / f0) / width_oct
        return undb(gain_db * np.exp(-4.0 * math.log(2.0) * o * o))
    return r


def SHELF_HI(fc, gain_db, slope=1.0):
    return lambda f: undb(gain_db / (1.0 + (fc / np.maximum(f, 1e-6)) ** (2 * slope)))


def _ola(frames, hop, total):
    F, n = frames.shape
    k = n // hop
    y = np.zeros(total + 2 * n)
    for g in range(k):                                     # frames g, g+k, ... tile without overlap
        flat = frames[g::k].reshape(-1)
        y[g * hop:g * hop + len(flat)] += flat
    return y[:total]


def stft_apply(x, gain_fn, n=2048, hop=512):
    """Time-varying spectral gain. gain_fn(t (F,), f (B,)) -> (F, B); t = frame centre in seconds."""
    if x.ndim == 2:
        return np.stack([stft_apply(x[:, c], gain_fn, n, hop) for c in range(x.shape[1])], axis=1)
    win = np.hanning(n + 1)[:-1]
    L = len(x)
    xp = np.concatenate([np.zeros(n), x, np.zeros(2 * n)])
    F = (len(xp) - n) // hop + 1
    idx = hop * np.arange(F)[:, None] + np.arange(n)[None, :]
    S = np.fft.rfft(xp[idx] * win, axis=1)
    t = (hop * np.arange(F) + n / 2 - n) / SR
    f = np.fft.rfftfreq(n, 1.0 / SR)
    S = S * gain_fn(t, f)
    y = _ola(np.fft.irfft(S, n, axis=1) * win, hop, len(xp))
    return y[n:n + L] / (np.sum(win ** 2) / hop)


def detone(x, max_prom=12.0, n=4096, hop=1024, nb=25):
    """Tame ringing partials: in every STFT frame, any bin standing more than max_prom dB above the median
    of its `nb` neighbouring bins is turned down to that limit. A drone keeps its rough harmonic body, but
    no single overtone can come across as a pure tone (the user's no-beeps rule)."""
    win = np.hanning(n + 1)[:-1]
    L = len(x)
    xp = np.concatenate([np.zeros(n), x, np.zeros(2 * n)])
    F = (len(xp) - n) // hop + 1
    idx = hop * np.arange(F)[:, None] + np.arange(n)[None, :]
    S = np.fft.rfft(xp[idx] * win, axis=1)
    mag = 20.0 * np.log10(np.abs(S) + 1e-12)
    pad = nb // 2
    mp = np.pad(mag, ((0, 0), (pad, pad)), mode="edge")
    med = np.median(np.lib.stride_tricks.sliding_window_view(mp, nb, axis=1), axis=2)
    over = np.maximum(0.0, mag - med - max_prom)
    S = S * undb(-over)
    y = _ola(np.fft.irfft(S, n, axis=1) * win, hop, len(xp))
    return y[n:n + L] / (np.sum(win ** 2) / hop)


def lp_res(f, fc, res_db=3.0, order=2, width=0.5):
    """Low-pass magnitude with a gentle resonant bump, for stft_apply: f (1, B), fc (F, 1)."""
    lp = 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))
    o = np.log2(np.maximum(f, 1.0) / fc) / width
    return lp * undb(res_db * np.exp(-4.0 * math.log(2.0) * o * o))


def band(f, fc, width_oct, floor_db=-40.0):
    """Gaussian band-pass (log frequency) for stft_apply."""
    o = np.log2(np.maximum(f, 1.0) / fc) / width_oct
    return np.maximum(np.exp(-4.0 * math.log(2.0) * o * o), float(undb(floor_db)))


# ======================================================================================
# time and pitch
# ======================================================================================
def varispeed(x, rate):
    """Tape-style speed change (rate < 1: slower and lower). Band-limited FFT resampling."""
    n = len(x)
    N = _fft_len(n + 4096)
    M = int(round(N / rate))
    X = np.fft.rfft(x, N, axis=0)
    K = M // 2 + 1
    Y = np.zeros((K,) + X.shape[1:], dtype=complex)
    k = min(K, len(X))
    Y[:k] = X[:k]
    if rate > 1.0:                                         # taper just below the new Nyquist
        m = max(1, int(k * 0.06))
        taper = np.ones(k)
        taper[k - m:] = np.linspace(1.0, 0.0, m)
        Y[:k] *= taper[:, None] if Y.ndim == 2 else taper
    y = np.fft.irfft(Y, M, axis=0) * (M / N)
    return y[:int(round(n / rate))]


def interp(x, pos):
    """4-point Catmull-Rom interpolation of mono x at fractional positions (0 outside)."""
    n = len(x)
    xp = np.concatenate([[0.0], x, [0.0, 0.0]])
    valid = (pos >= 0) & (pos <= n - 1)
    p = np.where(valid, pos, 0.0)
    i = np.floor(p).astype(np.int64)
    t = p - i
    xm1, x0, x1, x2 = xp[i], xp[i + 1], xp[i + 2], xp[np.minimum(i + 3, n + 2)]
    a = -0.5 * xm1 + 1.5 * x0 - 1.5 * x1 + 0.5 * x2
    b = xm1 - 2.5 * x0 + 2.0 * x1 - 0.5 * x2
    c = -0.5 * xm1 + 0.5 * x1
    return np.where(valid, ((a * t + b) * t + c) * t + x0, 0.0)


def varispeed_curve(x, rate_of_t, max_seconds):
    """Variable tape speed; rate_of_t(output seconds array) -> speed. Stops when the source ends."""
    t = np.arange(ns(max_seconds)) / SR
    r = rate_of_t(t)
    pos = np.concatenate([[0.0], np.cumsum(r[:-1])])
    pos = pos[pos <= len(x) - 1]
    if np.max(r) > 1.0:
        x = filt(x, LP(0.45 * SR / np.max(r), 8))
    if x.ndim == 2:
        return np.stack([interp(x[:, c], pos) for c in range(x.shape[1])], axis=1)
    return interp(x, pos)


def record(x, vel, start, ref=1.5):
    """Play mono x like a record under a hand: vel = per-sample platter speed (1 = normal play,
    negative = backwards), from `start` seconds into x. A vinyl pickup is velocity-sensitive, so the
    level follows the stylus speed (|vel| / ref, compressed)."""
    vmax = float(np.max(np.abs(vel)))
    if vmax > 1.0:
        x = filt(x, LP(0.45 * SR / vmax, 8))
    pos = ns(start) + np.concatenate([[0.0], np.cumsum(vel[:-1])])
    return interp(x, pos) * np.clip(np.abs(vel) / ref, 0.0, 2.0) ** 0.8


def pitch_shift(x, ratio, grain=0.08, overlap=8, jitter=0.12, seed=1):
    """Granular pitch shift that keeps the timing (used for octave-down growl layers)."""
    G = max(64, ns(grain) // 2 * 2)
    H = max(1, G // overlap)
    win = np.hanning(G + 1)[:-1]
    n = len(x)
    rng = np.random.default_rng(seed)
    out = np.zeros(n + 3 * G)
    j = np.arange(G) - G / 2
    for c in range(0, n + H, H):
        cj = c + (rng.uniform(-1.0, 1.0) * jitter * G * 0.5 if jitter else 0.0)
        s = c - G // 2 + G
        out[s:s + G] += interp(x, cj + j * ratio) * win
    return out[G:G + n] / (overlap / 2.0)


def time_stretch(x, factor, frame=0.04, tol=0.012):
    """Pitch-preserving time stretch (WSOLA). factor > 1 = longer."""
    N = ns(frame) // 2 * 2
    Hs = N // 2
    T = ns(tol)
    win = np.hanning(N + 1)[:-1]
    n = len(x)
    n_out = int(round(n * factor))
    pad = N + T
    xp = np.concatenate([np.zeros(pad), x, np.zeros(pad + 3 * N)])
    out = np.zeros(n_out + 4 * N)
    prev, k = None, 0
    while k * Hs < n_out + N:
        nominal = pad + int(round(k * Hs / factor)) - Hs
        if prev is None:
            best = nominal
        else:
            nat = prev + Hs
            lo, hi = max(0, nominal - T), min(len(xp) - N, nominal + T)
            c = np.correlate(xp[lo:hi + N], xp[nat:nat + N], mode="valid")
            best = lo + int(np.argmax(c))
        out[k * Hs:k * Hs + N] += xp[best:best + N] * win
        prev, k = best, k + 1
    return out[Hs:Hs + n_out]


def octave_down(x, seed=1, grain=0.09):
    return pitch_shift(x, 0.5, grain=grain, overlap=8, jitter=0.15, seed=seed)


# ======================================================================================
# colour, dynamics, space
# ======================================================================================
def ringmod(x, freq, mix=0.5, phase=0.0):
    """Ring modulation by a slow sine (20-60 Hz): inharmonic sidebands, an unnatural edge. Processing
    only: the modulator is never heard on its own."""
    n = len(x)
    f = np.full(n, float(freq)) if np.isscalar(freq) else np.asarray(freq, dtype=np.float64)[:n]
    m = np.sin(2.0 * np.pi * np.cumsum(f) / SR + phase)
    return x * (1.0 - mix) + apply_env(x, m) * mix


def wander_ring(x, freq, spread, rate, seed_, mix):
    """Ring modulation whose rate wanders at random around freq (+-spread per unit of wander, at about
    `rate` Hz), so the edge it adds never settles into one steady buzz line."""
    w = np.clip(wander(len(x), rate, np.random.default_rng(seed_)), -2.0, 2.0)
    return ringmod(x, freq * (1.0 + spread * w), mix)


def saturate(x, drive=2.0, asym=0.0, mix=1.0):
    """tanh soft clip; asym adds even harmonics (the DC it makes is removed in master())."""
    y = (np.tanh(drive * x + asym) - math.tanh(asym)) / math.tanh(drive)
    return x * (1.0 - mix) + y * mix


def _smooth(a, w):
    k = np.hanning(w + 2)[1:-1]
    return np.convolve(a, k / k.sum(), mode="same")


def compress(x, thresh_db, ratio, win=0.03, makeup_db=0.0):
    p = x ** 2 if x.ndim == 1 else np.mean(x ** 2, axis=1)
    w = max(3, ns(win))
    e = np.sqrt(np.convolve(p, np.ones(w) / w, mode="same") + 1e-12)
    over = 20.0 * np.log10(e) - thresh_db
    gr = _smooth(np.where(over > 0, over * (1.0 / ratio - 1.0), 0.0), w)
    return apply_env(x, undb(gr + makeup_db))


def expand(x, thresh_db, ratio=2.0, win=0.02, floor_db=-30.0):
    """Downward expander: pushes a recording's noise floor down between words/breaths."""
    w = max(3, ns(win))
    e = np.sqrt(np.convolve(x ** 2, np.ones(w) / w, mode="same") + 1e-12)
    under = 20.0 * np.log10(e) - thresh_db
    gr = _smooth(np.maximum(np.where(under < 0, under * (ratio - 1.0), 0.0), floor_db), w)
    return x * undb(gr)


def _movmin(a, W):
    """Centred moving minimum, window W (odd), van Herk / Gil-Werman, O(n)."""
    r = W // 2
    W = 2 * r + 1
    p = np.concatenate([np.ones(r), a, np.ones(r)])
    nb = -(-len(p) // W)
    p = np.concatenate([p, np.ones(nb * W - len(p))]).reshape(nb, W)
    pre = np.minimum.accumulate(p, axis=1).reshape(-1)
    suf = np.minimum.accumulate(p[:, ::-1], axis=1)[:, ::-1].reshape(-1)
    i = np.arange(len(a))
    return np.minimum(suf[i], pre[i + W - 1])


def tp_env(x):
    """Per-sample true-peak envelope: |x| 4x oversampled (band-limited), max over channels."""
    n = len(x)
    up = np.abs(varispeed(x, 0.25)[:4 * n])
    if up.ndim == 2:
        up = up.max(axis=1)
    up = np.pad(up, (0, 4 * n - len(up))).reshape(n, 4).max(axis=1)
    return np.maximum(up, np.abs(x) if x.ndim == 1 else np.abs(x).max(axis=1))


def limit(x, ceiling_db=-1.5, look=0.004, true_peak=False):
    """Look-ahead peak limiter: the smoothed gain never exceeds what each sample (or, with true_peak,
    each 4x-oversampled inter-sample peak) needs."""
    c = float(undb(ceiling_db))
    a = tp_env(x) if true_peak else (np.abs(x) if x.ndim == 1 else np.max(np.abs(x), axis=1))
    g = np.minimum(1.0, c / np.maximum(a, 1e-12))
    if g.min() >= 1.0:
        return x
    W = 2 * max(1, ns(look)) + 1
    k = np.hanning(W + 2)[1:-1]
    gs = np.minimum(np.convolve(_movmin(g, W), k / k.sum(), mode="same"), g)
    return apply_env(x, gs)


def tame(x, crest_db=10.0, look=0.001):
    """Clamp a spiky layer's isolated peaks (crackle pops) to crest_db above its active RMS, so the layer
    can sit loud in a mix without its clicks setting the whole sound's peak level."""
    return limit(x, dbfs(active_rms(x)) + crest_db, look)


def make_ir(rt60, seed, length=None, hf=0.4, lf=1.2, predelay=0.012, er=0.0, stereo=False):
    """Synthetic reverb IR: decaying noise whose decay time falls with frequency (dark tail)."""
    if stereo:
        return np.stack([make_ir(rt60, seed + 1000 * c, length, hf, lf, predelay, er) for c in (0, 1)], axis=1)
    length = length or min(max(rt60 * 1.15, 0.3), 6.0)
    n = ns(length)
    rng = np.random.default_rng(seed)
    noise = rng.standard_normal(n)

    def decay(t, f):
        o = np.log2(np.maximum(f, 20.0) / 1000.0)
        ratio = np.where(o < 0, 1.0 + (lf - 1.0) * np.clip(-o / 3.32, 0, 1),
                         1.0 + (hf - 1.0) * np.clip(o / 3.0, 0, 1))
        rt = np.maximum(rt60 * ratio, 0.04)
        return np.exp(-6.91 * np.maximum(t, 0.0)[:, None] / rt[None, :])

    tail = stft_apply(noise, decay, n=1024, hop=256)
    k = ns(0.003)
    tail[:k] *= np.linspace(0.0, 1.0, k)
    tail /= np.sqrt(np.sum(tail ** 2))
    if er > 0:                                             # a few early reflections (rooms)
        a0 = rms(tail[:ns(0.01)])
        for _ in range(8):
            tail[ns(rng.uniform(0.003, 0.045))] += er * a0 * 5.0 * rng.uniform(0.5, 1.0) * rng.choice([-1.0, 1.0])
        tail /= np.sqrt(np.sum(tail ** 2))
    return np.concatenate([np.zeros(ns(predelay)), tail])


def convolve(x, ir):
    n = len(x) + len(ir) - 1
    N = _fft_len(n)
    X = np.fft.rfft(x, N, axis=0)
    H = np.fft.rfft(ir, N, axis=0)
    if X.ndim == 1 and H.ndim == 2:
        X = X[:, None]
    elif X.ndim == 2 and H.ndim == 1:
        H = H[:, None]
    return np.fft.irfft(X * H, N, axis=0)[:n]


def reverb(x, ir, wet_db=-10.0, dry_db=0.0):
    w = convolve(x, ir) * float(undb(wet_db))
    if w.ndim == 2 and x.ndim == 1:
        w[:len(x)] += to_stereo(x) * float(undb(dry_db))
    else:
        w[:len(x)] += x * float(undb(dry_db))
    return w


def widen(x, amount=0.5, seed=0, length=0.025):
    """Mono -> stereo with a noise decorrelator (L+R stays equal to 2x: mono-safe)."""
    rng = np.random.default_rng(seed)
    m = ns(length)
    h = rng.standard_normal(m) * np.exp(-np.arange(m) / (m / 4.0))
    d = convolve(x, h / np.sqrt(np.sum(h ** 2)))[:len(x)]
    return np.stack([x + amount * d, x - amount * d], axis=1) / (1.0 + 0.5 * amount)


def granulate(x, out_len, grain=(0.05, 0.15), density=14.0, reverse=0.4, stutter=0.25,
              rate=(0.85, 1.05), travel=1.0, seed=0):
    """Granular scatter: grains read along x (with jitter), some reversed, some stuttered."""
    rng = np.random.default_rng(seed)
    n = ns(out_len)
    y = np.zeros(n + ns(1.0))
    dur = len(x) / SR
    t = 0.0
    while t < out_len:
        g = rng.uniform(*grain)
        r = rng.uniform(*rate)
        sp = min(max(0.0, (t / out_len) * travel * max(0.0, dur - g) + rng.uniform(-0.06, 0.06)), max(0.0, dur - g * r))
        seg = x[ns(sp):ns(sp) + ns(g * r)]
        if len(seg) > 32:
            seg = varispeed(seg, r) if abs(r - 1.0) > 1e-3 else seg
            seg = seg * np.hanning(len(seg) + 2)[1:-1]
            if rng.random() < reverse:
                seg = seg[::-1]
            reps = int(rng.integers(2, 4)) if rng.random() < stutter else 1
            for k in range(reps):
                put(y, seg, t + k * g * 0.55, gain_db=-2.5 * k)
        t += rng.exponential(1.0 / density)
    return y[:n]


def sub_boom(key, a, b, r0, r1, glide, dur, lp=140.0, drive=1.6, decay=None):
    """Sub-bass drop made from a real low recording (an explosion's or a door's body, thunder), never an
    oscillator: played on a slowing tape (speed r0 -> r1, exponential glide) so its rumble sinks in pitch,
    then low-passed and soft-clipped so small speakers still hear it."""
    x = filt(norm(src(key, a, b)), HP(20, 2))
    y = varispeed_curve(x, lambda t: r1 + (r0 - r1) * np.exp(-t / max(glide, 1e-3)), dur)
    y = filt(y, LP(lp, 4), HP(24, 2))
    if decay:
        y = y * np.exp(-np.arange(len(y)) / (decay * SR))
    return saturate(norm(y), drive)


# ======================================================================================
# haphazard: seeded chaos (the user asked for "more haphazard"). Nothing here makes a tone: every
# stutter slice is >= 40 ms and repeats at uneven gaps, and every gate opens and closes at random, so no
# steady buzz or beep can emerge from the repetition.
# ======================================================================================
def hseed(*parts):
    """A fixed seed per (output, purpose), so every random choice is the same on every build."""
    return zlib.crc32("/".join(str(p) for p in parts).encode("utf-8")) & 0x7FFFFFFF


def wander(n, rate, rng):
    """Smooth random curve (about unit standard deviation) that wanders at roughly `rate` Hz."""
    step = max(2, int(SR / max(rate, 0.05)))
    k = n // step + 4
    c = np.interp(np.arange(n + step), np.arange(k) * step, rng.standard_normal(k))
    w = np.hanning(step + 2)[1:-1]
    c = np.convolve(c, w / w.sum(), mode="same")[:n]
    s = float(np.std(c))
    return c / s if s > 1e-9 else c


def wander_fn(dur, rate, seed_, clip=2.5):
    """wander() as a function of time (seconds, array) for speed and filter curves."""
    n = ns(dur) + 2
    c = np.clip(wander(n, rate, np.random.default_rng(seed_)), -clip, clip)
    return lambda t: c[np.clip((np.asarray(t) * SR).astype(np.int64), 0, n - 1)]


def drunk(x, depth, rate, seed_):
    """A drunk tape transport: the speed staggers at random around 1 (+-depth per unit of wander, at
    about `rate` Hz), so pitch and timing wobble without any regular vibrato."""
    dur = len(x) / SR / max(0.3, 1.0 - 2.5 * depth) + 0.05
    w = wander_fn(dur, rate, seed_)
    return varispeed_curve(x, lambda t: 1.0 + depth * w(t), dur)


def lurch(x, seed_, n=2, depth=(0.2, 0.4), dur=(0.07, 0.16), span=(0.15, 0.85)):
    """A hand grabs the platter, or the throat buckles: at a few random moments the speed sags and snaps
    back, so the pitch dips and the sound drags. span = (from, to) as fractions of the length."""
    rng = np.random.default_rng(seed_)
    L = len(x) / SR
    dips = sorted((float(rng.uniform(*span)) * L, float(rng.uniform(*dur)), float(rng.uniform(*depth)))
                  for _ in range(n))

    def rate(t):
        r = np.ones_like(t)
        for c, d, a in dips:
            u = (t - c) / d
            m = (u >= 0.0) & (u <= 1.0)
            r[m] -= a * np.sin(np.pi * u[m]) ** 2
        return r
    return varispeed_curve(x, rate, L * 1.6 + 0.1)


def _holes_mask(L, holes, ramp):
    """1 everywhere except 0 inside the (a, b) sample ranges, with smooth edges `ramp` samples wide."""
    m = np.ones(L)
    for a, b in holes:
        m[max(0, a):max(0, min(L, b))] = 0.0
    if ramp > 1:
        w = np.hanning(ramp + 2)[1:-1]
        m = np.convolve(m, w / w.sum(), mode="same")
    return m


def stutter(x, seed_, n=1, slice_s=(0.05, 0.1), reps=(2, 3), span=(0.2, 0.8), rev=0.3, step_db=-1.5,
            spacing=(0.8, 1.35), xf=0.004):
    """Haphazard beat-repeat: n random slices (slice_s long, starting between span = (t0, t1) seconds) are
    repeated right after themselves - some backwards, each a little quieter, at uneven gaps - over what
    would have followed. The length never changes."""
    rng = np.random.default_rng(seed_)
    L = len(x)
    xfn = max(2, ns(xf))
    holes, adds = [], []
    for _ in range(n):
        g = ns(float(rng.uniform(*slice_s)))
        s = ns(float(rng.uniform(*span)))
        k = int(rng.integers(reps[0], reps[1] + 1))
        if g < 4 * xfn or s + g >= L:
            continue
        w = np.ones(g)
        w[:xfn] = np.linspace(0.0, 1.0, xfn)
        w[-xfn:] = np.linspace(1.0, 0.0, xfn)
        piece = x[s:s + g] * (w if x.ndim == 1 else w[:, None])
        t = end = s + g
        for j in range(k - 1):
            p = piece[::-1] if rng.random() < rev else piece
            adds.append((t, p * float(undb(step_db * (j + 1)))))
            end = t + g
            t += int(g * float(rng.uniform(*spacing)))
        holes.append((s + g, end))
    y = apply_env(x, _holes_mask(L, holes, xfn)) if holes else x.copy()
    for t, p in adds:
        e = min(L, t + len(p))
        if e > t:
            y[t:e] += p[:e - t]
    return y


def chop(x, seed_, t0, t1, on=(0.04, 0.12), off=(0.012, 0.045), depth=1.0, ramp=0.004):
    """Random dropouts between t0 and t1 s: the sound cuts out (by `depth`) for off seconds after every
    on seconds, both drawn at random, so it stammers with no regular rhythm."""
    rng = np.random.default_rng(seed_)
    holes = []
    t = t0 + float(rng.uniform(*on))
    while t < t1:
        d = float(rng.uniform(*off))
        holes.append((ns(t), ns(min(t + d, t1))))
        t += d + float(rng.uniform(*on))
    m = _holes_mask(len(x), holes, max(2, ns(ramp)))
    return apply_env(x, 1.0 - depth * (1.0 - m))


def shuffle(x, seed_, t0, t1, chunk=(0.06, 0.18), reach=2, rev=0.3, drop=0.15, drop_db=-14.0, xf=0.005):
    """Jump-cut the stretch [t0, t1) s: cut it into random chunks and play them back scrambled (each slot
    takes the chunk up to `reach` places on), some backwards, some 'dropped' (they choke down to drop_db
    instead of vanishing, so it never sounds like a playback dropout). Used after the reverb the cuts stay
    audible; before it they turn into stop-start phrases in a room. The length never changes."""
    rng = np.random.default_rng(seed_)
    a, b = ns(t0), min(len(x), ns(t1))
    cuts = [a]
    while cuts[-1] < b:
        cuts.append(min(b, cuts[-1] + ns(float(rng.uniform(*chunk)))))
    k = len(cuts) - 1
    order = list(range(k))
    for i in range(k):
        j = min(k - 1, i + int(rng.integers(0, reach + 1)))
        order[i], order[j] = order[j], order[i]
    y = apply_env(x, _holes_mask(len(x), [(a, b)], max(2, ns(xf))))
    for i in range(k):
        L = cuts[i + 1] - cuts[i]
        dropped = rng.random() < drop
        s = cuts[order[i]]
        piece = x[s:s + L]
        if rng.random() < rev:
            piece = piece[::-1]
        piece = fades(piece, xf, xf) * (float(undb(drop_db)) if dropped else 1.0)
        y[cuts[i]:cuts[i] + len(piece)] += piece
    return y


def stammer(x, seed_, at=0.02, length=(0.07, 0.11), reps=(1, 2), gap=(0.03, 0.08), level_db=(-4.0, -1.0)):
    """The first syllable catches: the first `length` s of x after its onset comes out `reps` times at
    uneven gaps (from `at` s on) before the whole of x plays. Returns the longer result."""
    rng = np.random.default_rng(seed_)
    o = onset(x)
    L = ns(float(rng.uniform(*length)))
    stab = fades(x[o:o + L], 0.002, 0.03)
    k = int(rng.integers(reps[0], reps[1] + 1))
    y = np.zeros((len(x) + ns(at) + k * (L + ns(gap[1])) + ns(0.05),) + x.shape[1:])
    t = at
    for _ in range(k):
        put(y, stab, t, float(rng.uniform(*level_db)))
        t += L / SR + float(rng.uniform(*gap))
    put(y, x, t, align=o)
    return y


def flutter(x, seed_, depth=0.35, rate=6.0):
    """Irregular amplitude flutter (gusts): gain 1 + depth * wander, never below 0.15."""
    g = np.maximum(0.15, 1.0 + depth * np.clip(wander(len(x), rate, np.random.default_rng(seed_)), -2.0, 2.0))
    return apply_env(x, g)


def frags(key, a, b, count, length, seed_, hp=None, lp=None, db=-20.0, stereo=False):
    """`count` random slices (length = (min, max) s) of source `key` between a and b s, band-limited and
    RMS-levelled to db, with short fades: raw material for scatter() and bursts(). Near-silent slices are
    skipped so no noise floor gets blown up."""
    rng = np.random.default_rng(seed_)
    out = []
    tries = 0
    while len(out) < count and tries < count * 12:
        tries += 1
        L = float(rng.uniform(*length))
        s = float(rng.uniform(a, max(a, b - L)))
        x = src(key, s, s + L, stereo=stereo)
        resp = ([HP(hp, 2)] if hp else []) + ([LP(lp, 2)] if lp else [])
        if resp:
            x = filt(x, *resp)
        if len(x) < ns(0.01) or dbfs(active_rms(x)) < -48.0:
            continue
        out.append(lev(fades(x, 0.002, min(0.012, L / 4)), db))
    if not out:
        raise SystemExit(f"no usable fragments in {key} {a}-{b}")
    return out


def scatter(dst, pieces, seed_, t0, t1, count, gain_db=(-12.0, -4.0), rate=(0.75, 1.15), rev=0.35,
            pan=(-0.6, 0.6), decay=None):
    """Throw `count` random pieces into dst between t0 and t1 s: random choice, moment, speed, level and
    direction (and pan, in stereo). decay = dB per second of lateness, so the scatter thins out."""
    rng = np.random.default_rng(seed_)
    for _ in range(count):
        f = pieces[int(rng.integers(0, len(pieces)))]
        r = float(rng.uniform(*rate))
        p = varispeed(f, r) if abs(r - 1.0) > 1e-3 else f.copy()
        if rng.random() < rev:
            p = p[::-1]
        p = fades(p, 0.003, 0.012)
        t = float(rng.uniform(t0, t1))
        g = float(rng.uniform(*gain_db)) + (decay * (t - t0) if decay else 0.0)
        put(dst, p, t, g, pan=float(rng.uniform(*pan)) if dst.ndim == 2 else None)
    return dst


def bursts(dst, pieces, seed_, t0, t1, dens0, dens1, g0, g1, rate=(0.85, 1.4), rev=0.3, pan=(-0.7, 0.7),
           curve=1.0, jitter_db=3.0):
    """Random crackle bursts between t0 and t1 s. Their rate (per second) goes dens0 -> dens1 and their
    level g0 -> g1 dB (curve shapes both); every burst is a random piece at a random speed, some backwards."""
    rng = np.random.default_rng(seed_)
    span = max(1e-6, t1 - t0)
    t = t0
    while True:
        u = min(1.0, max(0.0, (t - t0) / span)) ** curve
        t += float(rng.exponential(1.0 / max(0.05, dens0 + (dens1 - dens0) * u)))
        if t >= t1:
            break
        u = ((t - t0) / span) ** curve
        f = pieces[int(rng.integers(0, len(pieces)))]
        r = float(rng.uniform(*rate))
        p = varispeed(f, r) if abs(r - 1.0) > 1e-3 else f.copy()
        if rng.random() < rev:
            p = p[::-1]
        g = g0 + (g1 - g0) * u + float(rng.uniform(-jitter_db, jitter_db))
        put(dst, fades(p, 0.002, 0.008), t, g, pan=float(rng.uniform(*pan)) if dst.ndim == 2 else None)
    return dst


# ======================================================================================
# loudness, mastering, output
# ======================================================================================
def _kweight(f):
    hp = 1.0 / np.sqrt(1.0 + (38.0 / np.maximum(f, 1e-6)) ** 4)
    return hp * undb(4.0 / (1.0 + (1700.0 / np.maximum(f, 1e-6)) ** 2))


def lufs(x):
    """Gated integrated loudness, BS.1770-style (K-weighting approximated in the FFT domain)."""
    y = filt(x, _kweight)
    p = y ** 2 if y.ndim == 1 else np.sum(y ** 2, axis=1)
    B, H = ns(0.4), ns(0.1)
    blocks = np.array([np.mean(p)]) if len(p) < B else np.array(
        [np.mean(p[i:i + B]) for i in range(0, len(p) - B + 1, H)])
    lb = -0.691 + 10.0 * np.log10(blocks + 1e-15)
    gated = blocks[lb > -70.0]
    if not len(gated):
        return -99.0
    rel = -0.691 + 10.0 * math.log10(np.mean(gated)) - 10.0
    gated = blocks[(lb > -70.0) & (lb > rel)]
    return -0.691 + 10.0 * math.log10(np.mean(gated))


def short_term(x, win=0.2):
    """Loudness of the loudest `win` seconds (K-weighted mean square, 20 ms steps)."""
    y = filt(x, _kweight)
    p = y ** 2 if y.ndim == 1 else np.sum(y ** 2, axis=1)
    B = ns(win)
    if len(p) <= B:
        return -0.691 + 10.0 * math.log10(float(np.mean(p)) + 1e-15)
    c = np.concatenate([[0.0], np.cumsum(p)])
    s = np.arange(0, len(p) - B + 1, ns(0.02))
    return -0.691 + 10.0 * math.log10(float(np.max(c[s + B] - c[s])) / B + 1e-15)


def punch_loudness(x):
    """For short hits: the mean of the integrated loudness and the loudest 200 ms, so a family of grunts or
    blasts matches both in overall loudness and in how loud the hit itself lands."""
    return 0.5 * (lufs(x) + short_term(x))


def clean(x, hp=25.0):
    """DC / rumble removal before levelling."""
    y = filt(x, HP(hp, 2))
    return y - np.mean(y, axis=0)


def master(x, lufs_target=None, ceiling=-1.5, peak_db=None, look=0.004, measure=None):
    """Level (loudness or peak), true-peak look-ahead limiting, DC removal, 10 ms fades.
    x must already be clean()ed. measure = the loudness function the target refers to (default: lufs).
    Returns (y, limiter gain reduction dB)."""
    measure = measure or lufs
    y = x
    for _ in range(16):
        if lufs_target is not None:
            y = y * float(undb(lufs_target - measure(y)))
        else:
            y = norm(y, peak_db if peak_db is not None else ceiling)
        y = limit(y, ceiling, look, true_peak=True)
        if lufs_target is None or abs(measure(y) - lufs_target) < 0.03:
            break
    # total limiting: how far the loudest true peak would have gone over the ceiling without the limiter
    gain = (lufs_target - measure(x)) if lufs_target is not None else (
        (peak_db if peak_db is not None else ceiling) - dbfs(peak(x)))
    gr = max(0.0, gain + dbfs(np.max(tp_env(x))) - ceiling)
    y = y - np.mean(y, axis=0)                             # the limiter's gain moves can leave a hair of DC
    return fades(y, 0.01, 0.01), gr


# --- Ogg writing: pinned stream serial (byte-identical rebuilds), granule-exact decode ----------------
_CRC_TABLE = []


def _ogg_crc(data):
    if not _CRC_TABLE:
        for i in range(256):
            r = i << 24
            for _ in range(8):
                r = ((r << 1) ^ 0x04C11DB7) if r & 0x80000000 else (r << 1)
            _CRC_TABLE.append(r & 0xFFFFFFFF)
    crc = 0
    tab = _CRC_TABLE
    for byte in data:
        crc = ((crc << 8) & 0xFFFFFFFF) ^ tab[((crc >> 24) ^ byte) & 0xFF]
    return crc


def pin_ogg_serial(path, serial):
    """ffmpeg's Ogg muxer picks a random stream serial each run; pin it and recompute every page CRC."""
    with open(path, "rb") as f:
        data = bytearray(f.read())
    i = 0
    while i < len(data):
        if data[i:i + 4] != b"OggS":
            raise SystemExit("unexpected Ogg layout in " + path)
        nseg = data[i + 26]
        size = 27 + nseg + sum(data[i + 27:i + 27 + nseg])
        stored = bytes(data[i + 22:i + 26])
        data[i + 22:i + 26] = b"\0\0\0\0"
        if _ogg_crc(bytes(data[i:i + size])).to_bytes(4, "little") != stored:
            raise SystemExit("Ogg CRC self-check failed in " + path)
        data[i + 14:i + 18] = serial.to_bytes(4, "little")
        data[i + 22:i + 26] = _ogg_crc(bytes(data[i:i + size])).to_bytes(4, "little")
        i += size
    with open(path, "wb") as f:
        f.write(bytes(data))


def decode(path):
    """Decoded samples (frames, channels), cut/padded to the last Ogg granule position, which is the
    length the game plays; aud's decoder can return a few samples more than that."""
    with open(path, "rb") as f:
        data = f.read()
    i = data.rfind(b"OggS")
    n = int.from_bytes(data[i + 6:i + 14], "little")
    y = np.asarray(aud.Sound(path).data(), dtype=np.float64)
    y = y[:, None] if y.ndim == 1 else y
    return np.pad(y, ((0, max(0, n - len(y))), (0, 0)))[:n]


REPORT = []


def encode(rel, x, family, bitrate=128000, out_dir=None):
    """Encode Ogg Vorbis (pinned serial), decode it back and measure what the game will play."""
    path = os.path.join(out_dir or OUT_DIR, rel + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    stereo = x.ndim == 2
    data = np.ascontiguousarray((x if stereo else x[:, None]).astype(np.float32))
    snd = aud.Sound.buffer(data, SR)
    snd.write(path, SR, aud.CHANNELS_STEREO if stereo else aud.CHANNELS_MONO, aud.FORMAT_FLOAT32,
              aud.CONTAINER_OGG, aud.CODEC_VORBIS, bitrate, 0)
    pin_ogg_serial(path, zlib.crc32(rel.encode("ascii")) & 0x7FFFFFFF)
    d = decode(path)
    m = d if stereo else d[:, 0]
    info = {"file": rel + ".ogg", "family": family, "duration_s": round(len(d) / SR, 4), "samples": int(len(d)),
            "channels": int(d.shape[1]), "rate": SR, "peak_dbfs": round(dbfs(np.max(np.abs(d))), 2),
            "true_peak_dbtp": round(dbfs(np.max(tp_env(m))), 2), "rms_dbfs": round(dbfs(rms(m)), 2),
            "active_rms_dbfs": round(dbfs(active_rms(m)), 2), "lufs": round(lufs(m), 2),
            "lufs_200ms_max": round(short_term(m), 2),
            "dc": float(f"{float(np.mean(d)):.1e}"), "bytes": os.path.getsize(path)}
    return d, info


def master_write(rel, x, family, lufs_target=None, ceiling=-1.5, peak_db=None, look=0.004, bitrate=128000,
                 note="", out_dir=None, measure=None):
    """Master, encode and check the decoded file: if Vorbis pushed a sample peak over -1.05 dBFS or a true
    peak over -1.0 dBTP, master again with that much lower a limiter ceiling at the SAME loudness target,
    so the family stays equally loud and only this member's limiter works a little harder."""
    c = ceiling
    for _ in range(10):
        y, gr = master(x, lufs_target, c, peak_db, look, measure)
        d, info = encode(rel, y, family, bitrate, out_dir)
        over = max(info["peak_dbfs"] + 1.05, info["true_peak_dbtp"] + 1.0)
        if over <= 0.0:
            info["limiter_ceiling_db"] = round(c, 2)
            info["note"] = (note + "; " if note else "") + f"limiter GR {gr:.1f} dB"
            return y, d, info
        c -= over + 0.1
    raise SystemExit(rel + ": could not keep the decoded peak under -1 dBFS / -1 dBTP")


# ======================================================================================
# shared voice chain
# ======================================================================================
def beastify(x, rate, oct_db=-9.0, ring=None, drive=2.0, seed=0, formant=True):
    """Organic source -> monster: slowed (formants drop with the pitch), throat/snarl bands,
    a saturated octave-down layer and an optional slow ring-mod edge. rate may be (r0, r1): a tape that
    slows down while it plays, so the pitch sinks (pain, dying)."""
    if isinstance(rate, tuple):
        r0, r1 = rate
        d_out = len(x) / SR / ((r0 + r1) / 2.0)
        y = varispeed_curve(norm(x), lambda t: r0 + (r1 - r0) * np.clip(t / d_out, 0, 1), d_out * 1.3 + 0.05)
    else:
        y = varispeed(norm(x), rate) if abs(rate - 1.0) > 1e-6 else norm(x)
    if formant:
        y = filt(y, HP(55, 2), BELL(420, 3.0, 1.2), BELL(1400, -2.5, 1.0), BELL(2800, 2.5, 0.8))
    if oct_db is not None:
        o = filt(saturate(norm(octave_down(y, seed=seed)), 2.6, 0.12), LP(1500, 2), HP(40, 2))
        y = norm(y) + norm(o) * float(undb(oct_db))
    if ring:
        y = ringmod(y, ring[0], ring[1])
    return saturate(norm(y), drive, 0.05)


def snarl_bits(sources, count_each, seed_, rate=(0.72, 0.9), db=-14.0):
    """Short random bits of other monsters' snarls (slowed, gritty) to fling into a voice."""
    rng = np.random.default_rng(seed_)
    out = []
    for key, a, b in sources:
        for _ in range(count_each):
            L = float(rng.uniform(0.16, 0.42))
            s = float(rng.uniform(a, max(a, b - L)))
            f = varispeed(norm(src(key, s, s + L)), float(rng.uniform(*rate)))
            f = saturate(norm(filt(f, HP(90, 2), LP(7000, 2))), 1.6, 0.05)
            out.append(lev(fades(f, 0.005, 0.05), db))
    return out


# ======================================================================================
# Jehoash's voice
# ======================================================================================
def build_welcome_voice():
    """Porkrind's 'Welcome!' turned demonic, and a bit unhinged:
    - the core: slowed to 0.72 (main), an octave-down growl locked to it and the original pitch stretched
      to the same timing quietly on top (so the word stays clear), all on a drunk tape that staggers;
    - a second demon (Tim Rockk's 'WELCOME!', slowed) says it too, out of step;
    - then 'come' falls apart: random slices of it come back slower, some backwards, into a dark tail
      that cuts in and out."""
    T = 2.35
    x = norm(src("porkrind"), -1.0)                        # 1.295 s, word onset at 0.030 s
    R = 0.72
    main = varispeed(x, R)                                 # ~1.80 s, f0 ~150-165 Hz
    main = filt(main, HP(70, 2), BELL(450, 3.5, 1.2), BELL(1300, -3.0, 1.0), BELL(2900, 3.0, 0.8))
    main = saturate(norm(main), 1.7, 0.05)
    growl = octave_down(main, seed=11)                     # ~75-80 Hz, same timing
    growl = ringmod(norm(growl), 34.0, 0.55)
    growl = filt(saturate(norm(growl), 3.0, 0.15), LP(1800, 2), HP(45, 2))
    orig = time_stretch(x, 1.0 / R)                        # original pitch, slowed timing
    orig = filt(orig, HP(350, 2), BELL(3000, 2.0, 1.0))
    n = max(len(main), len(orig), len(growl))
    core = np.zeros(n)
    put(core, norm(main), 0.0)
    put(core, norm(growl), 0.0, -5.5)
    put(core, norm(orig), 0.0, -14.0)
    core = drunk(core, 0.02, 2.5, hseed("welcome", "tape"))
    c0 = onset(core) / SR

    out = np.zeros(ns(T + 2.5))
    at = 0.02
    put(out, lev(core, -14.0), at, align=onset(core))

    rng = np.random.default_rng(hseed("welcome", "tail"))
    dbl = varispeed(norm(src("dl_welcome", 0.15, 1.7)), 0.85)
    dbl = saturate(norm(filt(dbl, HP(110, 2), LP(6500, 2), BELL(800, 2.0, 1.0))), 1.4, 0.04)
    put(out, lev(dbl, -23.0), at + float(rng.uniform(0.09, 0.12)), align=onset(dbl))

    seg = core[ns(c0 + 0.62):ns(c0 + 1.32)]                # 'come', in the core's own time
    tail = []
    for _ in range(6):
        L = ns(float(rng.uniform(0.08, 0.2)))
        a = int(rng.integers(0, max(1, len(seg) - L)))
        tail.append(lev(fades(seg[a:a + L], 0.006, 0.03), -15.0))
    scatter(out, tail, hseed("welcome", "scatter"), at + 1.28, at + 1.95, 7, gain_db=(-6.0, 0.0),
            rate=(0.58, 0.92), rev=0.45, decay=-9.0)
    ir = make_ir(1.9, seed=21, hf=0.35, lf=1.3, predelay=0.03)
    y = reverb(out, ir, wet_db=-9.0)[:ns(T)]
    y = chop(y, hseed("welcome", "chop"), 1.45, T, on=(0.05, 0.14), off=(0.015, 0.05), depth=0.8)
    y = filt(y, HP(55, 2))
    return apply_env(y, env(len(y), [(0, 1), (T - 0.5, 1), (T, 0)]))


def build_roar(i):
    """Rage roar after the music dies: a creature roar whose throat buckles (the speed sags and snaps
    back), a human scream that warbles and comes in late, an attack growl, other monsters' snarls flung in
    at random, a deep body and a sub drop (a thunder clap's or a blast's body on a slowing tape), a big dark
    tail. Then, after the reverb so the edits stay audible: roar_1's attack catches twice ('GRR- GRR-
    RAAAWR') and it breaks into a ragged gargle near the end; roar_2 jump-cuts itself to pieces in the
    middle and gargles on the way out."""
    T = 2.45
    out = np.zeros(ns(T + 2.0))
    at = 0.02
    rng = np.random.default_rng(hseed("roar", i))
    if i == 1:
        creature = beastify(src("troll_roars", 7.02, 8.76), 0.86, oct_db=-8.0, drive=2.2, seed=31)
        creature = lurch(creature, hseed("roar", i, "lurch"), n=1, depth=(0.35, 0.45), dur=(0.16, 0.24), span=(0.45, 0.6))
        scream = varispeed(norm(src("rage", 1.05, 3.45)), 0.92)   # swells to its peak ~1 s into the roar
        scream = filt(ringmod(scream, 38.0, 0.35), HP(150, 2), BELL(1800, 2.0, 1.2))
        scream = drunk(scream, 0.035, 3.0, hseed("roar", i, "scream"))
        scream = detone(scream, 15.0)                      # its ~1 kHz harmonic rang like a whistle
        attack = filt(norm(src("dl_growl4", 0.12, 0.95)), HP(80, 2))
        put(out, creature, at, 0.0, align=onset(creature))
        put(out, scream, at + float(rng.uniform(0.03, 0.09)), -3.5)
        put(out, attack, at, -7.0, align=onset(attack))
        body_rate = 0.92
        sub = sub_boom("thunder_3180", 0.0, 2.6, 0.75, 0.42, 0.7, 2.1, lp=120.0)
        snarls = [("snarl_attack", 0.05, 1.25), ("snarl_attack", 1.55, 2.95), ("snarls3", 2.85, 5.0)]
    else:
        creature = beastify(src("troll_bellows", 1.58, 3.78), 0.9, oct_db=-9.0, drive=2.2, seed=32)
        creature = lurch(creature, hseed("roar", i, "lurch"), n=2, depth=(0.3, 0.42), dur=(0.12, 0.2), span=(0.2, 0.75))
        growl = beastify(src("dl_growl4", 0.12, 2.0), 0.88, oct_db=None, ring=(29.0, 0.25), drive=1.8)
        scream = norm(src("horror_scream", 1.6, 3.9))
        scream = varispeed(scream, 0.8)
        scream = filt(ringmod(scream, 47.0, 0.4), HP(180, 2), LP(7000, 2), BELL(1500, 2.0, 1.0))
        scream = drunk(scream, 0.04, 2.5, hseed("roar", i, "scream"))
        scream = apply_env(scream, env(len(scream), [(0, 0.0), (0.12, 0.6), (0.5, 1.0), (2.0, 0.9), (2.6, 0.0)]))
        attack = filt(norm(src("rd_roar04", 0.0, 0.5)), HP(90, 2))
        put(out, creature, at, 0.0, align=onset(creature))
        put(out, growl, at + float(rng.uniform(0.0, 0.06)), -4.0, align=onset(growl))
        put(out, scream, at + float(rng.uniform(0.05, 0.14)), -6.0)
        put(out, attack, at, -6.0, align=onset(attack))
        body_rate = 0.88
        sub = sub_boom("expl_1023", 0.0, 2.0, 0.7, 0.38, 0.8, 2.1, lp=120.0)
        snarls = [("ghoul", 0.05, 2.05), ("ghoul", 5.35, 7.6), ("snarl_attack", 3.35, 4.2)]
    bits = snarl_bits(snarls, 2, hseed("roar", i, "bits"))
    scatter(out, bits, hseed("roar", i, "scatter"), at + 0.25, at + 1.75, 4, gain_db=(-6.0, 0.0),
            rate=(0.9, 1.05), rev=0.3)
    body = filt(norm(src("deep_roar", 0.08, 2.9)), LP(650, 2), HP(30, 2))
    body = varispeed(body, body_rate)
    body = apply_env(body, env(len(body), [(0, 1.0), (1.2, 0.8), (2.3, 0.0), (9, 0.0)]))
    put(out, body, at, -6.0, align=onset(body))
    sub = apply_env(sub, env(len(sub), [(0, 0.0), (0.25, 0.7), (0.9, 1.0), (1.8, 0.5), (2.1, 0.0)]))
    put(out, sub, at, -8.0)
    out = saturate(norm(out), 1.5, 0.04)
    ir = make_ir(2.3, seed=33 + i, hf=0.35, lf=1.3, predelay=0.025)
    y = reverb(out, ir, wet_db=-9.0)
    if i == 1:
        y = stammer(y[:ns(2.4)], hseed("roar", i, "stammer"), at=0.02, length=(0.085, 0.12), reps=(2, 2),
                    gap=(0.035, 0.075), level_db=(-3.0, 0.0))
        y = chop(y, hseed("roar", i, "gargle"), 1.55, 1.98, on=(0.035, 0.09), off=(0.015, 0.045), depth=0.85)
    else:
        y = shuffle(y, hseed("roar", i, "jumpcut"), 0.78, 1.42, chunk=(0.07, 0.16), reach=2, rev=0.35, drop=0.2)
        y = chop(y, hseed("roar", i, "gargle"), 1.72, 2.12, on=(0.04, 0.1), off=(0.015, 0.045), depth=0.8)
    y = y[:ns(T)]
    return apply_env(y, env(len(y), [(0, 1), (1.95, 1), (T, 0)]))


def build_hurt(i):
    """Short pained growls: a pained grunt or snarl over a monster groan, both on a slowing tape so the
    pitch sinks as he flinches, an octave-down growl, a tight room. Haphazard: one hiccups ('uh-UHH'), one's
    throat lurches and stutters on the way out, one's layers land sloppily and it stammers. No partial may
    ring: hurt_1's top layer is a gritty demon grunt (the clean creature cry it used before whistled), its
    groan body is detone()d and its ring modulation wanders instead of buzzing at one rate; hurt_3's body is
    detone()d and it lets go sooner, so it can't end on a lone hum."""
    T = 0.62
    out = np.zeros(ns(T + 1.0))
    at = 0.012
    rng = np.random.default_rng(hseed("hurt", i))
    if i == 1:
        body = beastify(src("zombie4", 0.0, 0.52), (0.92, 0.7), oct_db=-8.0, ring=None, seed=41)
        body = wander_ring(detone(body, 10.0), 33.0, 0.25, 6.0, hseed("hurt", 1, "ring"), 0.22)
        cry = beastify(src("dl_grunt3", 0.10, 0.45), (0.8, 0.62), oct_db=None, drive=1.6, seed=42)
        put(out, lev(body, -16.0), at, align=onset(body))
        put(out, lev(cry, -19.0), at + float(rng.uniform(0.0, 0.02)), align=onset(cry))
    elif i == 2:
        body = beastify(src("rd_troll03"), (0.88, 0.68), oct_db=-9.0, ring=(41.0, 0.25), seed=43)
        body = lurch(body, hseed("hurt", i, "lurch"), n=1, depth=(0.25, 0.35), dur=(0.06, 0.09), span=(0.25, 0.45))
        snarl = beastify(src("dl_snarl3"), (0.95, 0.76), oct_db=None, drive=1.6, seed=44)
        put(out, lev(body, -16.0), at, align=onset(body))
        put(out, lev(snarl, -20.0), at + float(rng.uniform(0.01, 0.04)), align=onset(snarl))
    else:
        body = beastify(src("rd_grunt07", 0.0, 0.56), (0.94, 0.74), oct_db=-10.0, ring=(27.0, 0.3), seed=45)
        body = detone(body, 12.0)                          # its fry tail held a near-pure ~150 Hz line
        grunt = beastify(src("dl_grunt1", 0.1, 0.72), (0.97, 0.8), oct_db=None, drive=1.6, seed=46)
        yelp = beastify(src("rd_hurt04"), (0.8, 0.62), oct_db=None, drive=1.6, seed=47)
        put(out, lev(body, -16.0), at, align=onset(body))
        put(out, lev(grunt, -19.0), at + float(rng.uniform(0.0, 0.035)), align=onset(grunt))
        put(out, lev(yelp, -21.0), at + float(rng.uniform(0.03, 0.08)), align=onset(yelp))
    ir = make_ir(0.55, seed=48 + i, hf=0.45, lf=1.0, predelay=0.008, er=1.0)
    y = reverb(out, ir, wet_db=-13.0)[:ns(T)]
    if i == 1:                                             # lets go sooner, so its hit stands out of its
        shape = [(0, 1), (0.14, 1.0), (0.28, 0.4), (0.45, 0.12), (T, 0)]   # tail like the other two's
    elif i == 3:                                           # lets go sooner too: no hum left at the end
        shape = [(0, 1), (0.14, 1.0), (0.36, 0.7), (0.46, 0.22), (T, 0)]
    else:
        shape = [(0, 1), (0.14, 1.0), (0.38, 0.75), (T, 0)]
    y = apply_env(y, env(len(y), shape))
    if i == 1:                                             # 'uh- UGH': the first catch comes out on its own
        y = stammer(y, hseed("hurt", i, "hiccup"), at=0.012, length=(0.045, 0.06), reps=(1, 1),
                    gap=(0.04, 0.065), level_db=(-2.0, 0.0))[:ns(0.68)]
        y = apply_env(y, env(len(y), [(0, 1), (0.6, 1), (0.68, 0)]))
    elif i == 2:                                           # 'UGH-gh-gh': the end stutters, with gaps
        y = stutter(y, hseed("hurt", i, "stutter"), n=1, slice_s=(0.05, 0.065), reps=(3, 3), span=(0.24, 0.29),
                    rev=0.0, step_db=-2.0, spacing=(1.4, 1.8))
    else:                                                  # stammers on the way out
        y = chop(y, hseed("hurt", i, "chop"), 0.22, 0.5, on=(0.04, 0.08), off=(0.012, 0.03), depth=0.85)
    return y


def build_death():
    """Long anguished howl that comes apart: a human death cry slowed further as it goes (the voice
    sinks) and buckling at random, a low undead howl on a drunk tape, demon grit, a ghost wail, a
    vocal-fry death rattle, choking grunts flung in at random, whispers leaving him backwards. After the
    reverb, the middle jump-cuts itself to pieces and the end sputters out in random dropouts."""
    T = 3.4
    out = np.zeros(ns(T + 3.0))
    main = norm(src("bad_death3", 0.93, 3.32))
    main = varispeed_curve(main, lambda t: 0.78 - 0.21 * np.clip(t / 3.3, 0, 1) ** 1.4, 4.5)
    main = lurch(main, hseed("death", "lurch"), n=3, depth=(0.25, 0.4), dur=(0.1, 0.22), span=(0.15, 0.7))
    main = filt(main, HP(70, 2), BELL(500, 3.0, 1.2), BELL(1500, -2.0, 1.0), BELL(3000, 2.0, 0.8))
    main = detone(main, 16.0)                              # a sustained cry: no partial may ring pure
    oc = filt(saturate(norm(octave_down(main, seed=51)), 2.4, 0.1), LP(1400, 2), HP(40, 2))
    howl = norm(src("undead1", 0.05, 2.22))
    howl = drunk(varispeed(howl, 0.6), 0.03, 2.0, hseed("death", "howl"))
    howl = saturate(norm(filt(ringmod(howl, 31.0, 0.28), HP(70, 2), BELL(600, 3.0, 1.0))), 1.6, 0.05)
    grit = beastify(src("dl_dies", 1.72, 2.62), 0.85, oct_db=None, drive=1.8)
    ghost = filt(varispeed(norm(src("ogre_ghost", 0.05, 2.9)), 0.66), LP(2600, 2), HP(150, 2))
    rattle = filt(varispeed(norm(src("evil_take3", 3.3, 5.6)), 0.85), HP(90, 4), LP(5000, 2))
    body = filt(norm(src("deep_roar", 0.08, 1.9)), LP(500, 2), HP(30, 2))
    body = apply_env(body, env(len(body), [(0, 1.0), (0.8, 0.7), (1.6, 0.0), (9, 0.0)]))
    at = onset(main)
    put(out, saturate(norm(main), 1.6, 0.05), 0.02, 0.0, align=at)
    put(out, oc, 0.02, -10.0, align=at)
    put(out, howl, 0.25, -5.0)
    put(out, grit, 0.02, -7.0, align=onset(grit))
    put(out, apply_env(ghost, env(len(ghost), [(0, 0), (0.4, 1), (2.0, 0.7), (2.5, 0), (9, 0)])), 0.9, -13.0)
    put(out, apply_env(rattle, env(len(rattle), [(0, 0), (0.3, 1), (1.5, 0.8), (1.9, 0), (9, 0)])), 1.45, -9.0)
    put(out, body, 0.02, -9.0, align=onset(body))
    chokes = [lev(varispeed(fades(filt(norm(src("dl_dies", a, b)), HP(90, 2)), 0.004, 0.04), 0.85), -17.0)
              for a, b in ((0.05, 0.48), (0.85, 1.45), (0.12, 0.3), (0.95, 1.2))]
    scatter(out, chokes, hseed("death", "chokes"), 1.0, 2.7, 3, gain_db=(-5.0, 0.0), rate=(0.8, 1.0), rev=0.15)
    wh = [lev(fades(filt(norm(src("chant8", a, a + 0.4)), HP(250, 2), LP(8000, 2)), 0.01, 0.06)[::-1], -22.0)
          for a in (0.2, 1.0, 1.9, 2.8)]
    scatter(out, wh, hseed("death", "whispers"), 1.8, 3.0, 4, gain_db=(-4.0, 0.0), rate=(0.65, 0.85), rev=0.0)
    out = apply_env(out, env(len(out), [(0, 1.0), (1.5, 1.0), (2.6, 0.5), (3.2, 0.12), (T + 3, 0.0)]))
    ir = make_ir(2.6, seed=52, hf=0.33, lf=1.3, predelay=0.03)
    y = reverb(out, ir, wet_db=-8.0)[:ns(T)]
    y = shuffle(y, hseed("death", "jumpcut"), 1.35, 2.3, chunk=(0.07, 0.2), reach=2, rev=0.35, drop=0.15)
    y = chop(y, hseed("death", "sputter"), 2.3, 3.3, on=(0.05, 0.16), off=(0.02, 0.07), depth=0.9)
    return apply_env(y, env(len(y), [(0, 1), (T - 0.5, 1), (T, 0)]))


def build_ambient(i):
    """Eldritch idle sounds, quiet and haphazard: whispers that cut in and out, a hitching breath,
    a throat hum that wanders and catches, a chant that skips."""
    rng = np.random.default_rng(hseed("ambient", i))
    if i == 1:                                            # backwards-and-forwards whispering
        T = 3.3
        w = filt(norm(src("chant8", 0.15, 2.45)), HP(180, 2), LP(9000, 2), BELL(3500, 3.0, 1.5))
        w = expand(norm(w), -34.0, 2.0)
        low = pitch_shift(w, 0.84, grain=0.06, overlap=6, jitter=0.2, seed=61)
        low = drunk(low, 0.045, 1.6, hseed("ambient", 1, "tape"))
        scat = granulate(w, 2.9, grain=(0.04, 0.17), density=15.0, reverse=0.55, stutter=0.4, rate=(0.68, 1.12), seed=62)
        back = filt(expand(norm(src("chant7", 0.4, 2.3)), -34.0, 2.0)[::-1], HP(200, 2), LP(7000, 2))
        out = np.zeros(ns(T + 3.0))
        put(out, norm(low), 0.05)
        put(out, norm(scat), float(rng.uniform(0.2, 0.5)), -6.0)
        put(out, norm(back), float(rng.uniform(0.8, 1.3)), -9.0)
        out = ringmod(out, 23.0, 0.2)
        out = shuffle(out, hseed("ambient", 1, "jumble"), 0.4, 2.8, chunk=(0.08, 0.25), reach=3, rev=0.4,
                      drop=0.25, drop_db=-40.0)          # jumbled, stop-start whispering; the room fills the gaps
        ir, wet = make_ir(2.4, seed=63, hf=0.35, lf=1.2, predelay=0.04), -5.0
        post = lambda y: y
    elif i == 2:                                          # a hitching breath in, a growling breath out, a chuff
        T = 3.4
        b = filt(varispeed(norm(src("ghostbreath", 0.1, 3.35)), 0.85), HP(90, 2), LP(3500, 2), BELL(700, 3.0, 1.5))
        inh = apply_env(b[:ns(1.5)], env(ns(1.5), [(0, 0.0), (0.9, 1.0), (1.3, 0.8), (1.5, 0.0)]))
        exh = b[ns(1.6):ns(3.4)]
        exh = apply_env(exh, env(len(exh), [(0, 0.0), (0.15, 1.0), (1.0, 0.6), (1.8, 0.0)]))
        rasp = filt(varispeed(norm(src("ghostmonster", 3.27, 5.67)), 0.9), HP(80, 2), LP(3000, 2))
        rasp = apply_env(rasp, env(len(rasp), [(0, 0.0), (1.0, 1.0), (1.4, 0.0), (99, 0.0)]))
        growl = filt(varispeed(norm(src("ghostmonster", 6.12, 8.51)), 0.8), HP(60, 2), LP(4000, 2))
        growl = apply_env(growl, env(len(growl), [(0, 0.0), (0.2, 1.0), (1.4, 0.6), (1.85, 0.0), (99, 0.0)]))
        growl = lurch(growl, hseed("ambient", 2, "lurch"), n=2, depth=(0.15, 0.3), dur=(0.1, 0.2), span=(0.2, 0.7))
        g = ringmod(norm(octave_down(growl, seed=64)), 25.0, 0.4)
        g = filt(saturate(g, 2.5, 0.1), LP(900, 2), HP(40, 2))
        te = float(rng.uniform(1.4, 1.72))                # the breath out comes when it likes
        out = np.zeros(ns(T + 3.0))
        put(out, lev(inh, -18.0), 0.05)
        put(out, lev(rasp, -25.0), 0.05 + float(rng.uniform(0.0, 0.25)))
        put(out, lev(exh, -19.0), te + 0.05)
        put(out, lev(growl, -20.0), te)
        put(out, lev(g, -27.0), te)
        ch = [lev(fades(filt(expand(norm(src("evil_take5", a, b)), -26.0, 2.5), HP(70, 2), LP(3000, 2)), 0.005, 0.06), -24.0)
              for a, b in ((0.48, 0.72), (1.30, 1.55), (2.25, 2.60), (3.47, 3.72), (3.90, 4.15))]
        scatter(out, ch, hseed("ambient", 2, "chuff"), 0.9, 3.0, 2, gain_db=(-4.0, 0.0), rate=(0.85, 1.0), rev=0.0)
        ir, wet = make_ir(1.6, seed=65, hf=0.4, lf=1.1, predelay=0.02), -9.0
        post = lambda y: chop(y, hseed("ambient", 2, "hitch"), 0.3, min(1.25, te - 0.05), on=(0.1, 0.25),
                              off=(0.04, 0.08), depth=0.85)              # the breath in hitches
    elif i == 3:                                          # throat hum: a droning demon voice that wanders and sinks,
        T = 3.5                                           # a troll's throat grumble (no clean hummed line: a hum
        wf = wander_fn(T + 0.2, 1.1, hseed("ambient", 3, "tape"))  # is too close to a pure tone)
        d = varispeed_curve(norm(src("demon_call", 2.0, 6.4)),
                            lambda t: 0.9 * (1.0 + 0.025 * wf(t)) * (1.0 - 0.05 * np.clip(t / T, 0, 1)), T + 0.1)
        d = filt(d, HP(50, 2), LP(5000, 2), BELL(950, 2.0, 0.8), BELL(2400, 4.0, 0.7))
        d = detone(d, 12.0)                                # its ~300 Hz overtone rings almost pure: tame it
        ff = wander_fn(T + 0.5, 0.6, hseed("ambient", 3, "throat"))

        def throat(t, f):                                  # a formant that wanders like throat singing, on the
            fc = 1100.0 * 2.0 ** (0.35 * ff(np.maximum(t, 0.0)))[:, None]   # drone itself (moderate, so no
            return 1.0 + 1.0 * band(f[None, :], fc, 0.5, floor_db=-80.0)  # single overtone turns into a whistle)
        d = stft_apply(d, throat)
        gr = filt(varispeed(norm(src("troll_idle", 0.95, 3.6)), 0.8), HP(45, 2), LP(4000, 2))
        oc = filt(saturate(norm(octave_down(d, seed=66)), 2.2, 0.1), LP(900, 2), HP(35, 2))
        oc = ringmod(oc, 21.0, 0.3)
        br = filt(varispeed(norm(src("ghostbreath", 0.4, 3.35)), 0.7), HP(120, 2), LP(2500, 2))
        out = np.zeros(ns(T + 3.0))
        put(out, lev(apply_env(d, env(len(d), [(0, 0), (0.35, 1), (3.0, 1), (3.45, 0), (99, 0)])), -19.0), 0.0)
        put(out, lev(apply_env(gr, env(len(gr), [(0, 0), (0.1, 1), (3.0, 1), (3.3, 0), (99, 0)])), -20.0),
            float(rng.uniform(0.0, 0.3)))
        put(out, lev(oc, -25.0), 0.0)
        put(out, lev(br, -28.0), 0.0)
        ir, wet = make_ir(2.2, seed=67, hf=0.35, lf=1.2, predelay=0.035), -9.0
        post = lambda y: chop(y, hseed("ambient", 3, "catch"), 0.5, 3.1, on=(0.25, 0.7), off=(0.05, 0.13),
                              depth=0.8)                                 # the throat catches
    else:                                                 # a chanted fragment that skips, a second voice answering
        T = 3.7                                           # off-key, over a backwards whisper
        c = filt(varispeed(norm(src("chant1", 0.95, 3.5)), 0.74), HP(60, 2), BELL(500, 2.0, 1.0), BELL(2500, 2.0, 1.0))
        c = saturate(norm(detone(c, 15.0)), 1.6, 0.05)    # sung: keep any one partial from ringing pure
        c2 = filt(varispeed(norm(src("chant5", 0.0, 1.6)), 0.62), HP(70, 2), BELL(700, 2.0, 1.0))
        c2 = ringmod(saturate(norm(detone(c2, 15.0)), 1.8, 0.05), 36.0, 0.3)
        oc = filt(saturate(norm(octave_down(c, seed=68)), 2.2, 0.1), LP(1200, 2), HP(35, 2))
        oc = ringmod(oc, 36.0, 0.35)
        wh = filt(norm(src("dl_nothing", 0.25, 2.45))[::-1], HP(250, 2), LP(8000, 2))
        wh = granulate(wh, 2.4, grain=(0.05, 0.17), density=11.0, reverse=0.35, stutter=0.4, rate=(0.8, 1.05), seed=69)
        out = np.zeros(ns(T + 3.0))
        put(out, lev(c, -18.0), 0.05)
        put(out, lev(c2, -25.0), float(rng.uniform(0.35, 0.95)))
        put(out, lev(oc, -25.0), 0.05)
        put(out, lev(wh, -26.0), float(rng.uniform(0.4, 0.9)))
        ir, wet = make_ir(2.6, seed=70, hf=0.35, lf=1.25, predelay=0.05), -8.0
        post = lambda y: shuffle(y, hseed("ambient", 4, "skip"), 0.5, 2.9, chunk=(0.1, 0.28), reach=2,
                                 rev=0.3, drop=0.3, drop_db=-12.0)       # the chant skips and falters
    y = reverb(out, ir, wet_db=wet)[:ns(T)]
    y = post(y)
    y = compress(y, -22.0, 2.0, win=0.05)
    return apply_env(y, env(len(y), [(0, 0.0), (0.06, 1.0), (T - 0.6, 1.0), (T, 0.0)]))


# ======================================================================================
# the disco laser: built only from real recordings (no synth, no sweeps, no tones)
# ======================================================================================
def build_laser_charge():
    """1.0 s charge-up, denser and louder to the end, where the fire takes over:
    - a vinyl rewind under a jerky hand: the record (a maniacal laugh + its crackle) spun backwards ever
      faster, the speed wobbling, the platter caught twice for a split second,
    - a reversed thunder crack: the roll swells into the crack's attack right at the end,
    - storm wind on a speeding tape, opening up, plus a reversed rope whoosh,
    - sparkler sizzle and fire crackle sped up, plus random crackle bursts that come thicker and louder,
    - a reversed explosion body sucking the low end in."""
    T = 1.0
    n = ns(T)
    t = np.arange(n) / SR
    u = t / T
    out = np.zeros(n + ns(2.0))
    rng = np.random.default_rng(hseed("charge"))

    jerk = np.maximum(0.2, 1.0 + 0.3 * np.clip(wander(n, 14.0, rng), -2.0, 2.0))
    catch = np.ones(n)
    for c in sorted(rng.uniform(0.18, 0.72, 2)):
        d = float(rng.uniform(0.025, 0.05))
        m = (t >= c) & (t <= c + d)
        catch[m] = 1.0 - 0.92 * np.sin(np.pi * (t[m] - c) / d) ** 2
    vel = -(0.35 + 2.6 * u ** 1.6) * jerk * catch          # reads the laugh from 2.45 s back to ~1.1 s
    laugh = filt(norm(src("laugh", 0.0, 3.0)), HP(140, 2), LP(9000, 2))
    crackle = filt(norm(src("vinyl_2547", 1.0, 4.2)), HP(250, 2))
    disc = norm(laugh) + 0.35 * norm(crackle[:len(laugh)])
    rw = filt(record(disc, vel, 2.45), HP(120, 2), BELL(2500, 2.0, 1.0))
    put(out, lev(rw, -17.0), 0.0)

    th = src("close_thunder", 1.40, 3.30)                  # the close crack + 1.7 s of its roll, backwards
    con = onset(th, -6.0)
    rv = filt(th, HP(45, 2))[::-1]
    put(out, lev(rv, -20.0), T - 0.014, align=len(rv) - 1 - con)    # lands just before the 10 ms fade

    w = varispeed_curve(src("wind_1450", 41.0, 44.0), lambda tt: 0.5 + 1.2 * np.clip(tt / T, 0, 1) ** 1.4, T + 0.05)
    w = stft_apply(w, lambda tt, ff: lp_res(ff[None, :], (450.0 * 18.0 ** np.clip(tt / T, 0, 1))[:, None], 2.0),
                   n=1024, hop=256)
    w = w[:n] * undb(-22.0 * (1.0 - u[:len(w)]) ** 1.5)
    put(out, lev(w, -24.0), 0.0)
    wh = src("whoosh_1796")
    pk = env_peak_index(wh, 0.01)
    whr = filt(wh, HP(90, 2))[::-1]
    put(out, lev(whr, -24.0), T - 0.02, align=len(whr) - 1 - pk)

    sp = varispeed_curve(src("sparkler", 3.0, 5.5), lambda tt: 0.7 + 0.9 * np.clip(tt / T, 0, 1), T + 0.05)
    sp = filt(sp, HP(700, 2))[:n]
    sp = sp * undb(-20.0 * (1.0 - u[:len(sp)]) ** 1.3)
    put(out, lev(tame(sp, 9.0), -25.0), 0.0)
    fc = varispeed_curve(src("fire_0987", 10.0, 13.0), lambda tt: 1.0 + 1.6 * np.clip(tt / T, 0, 1) ** 1.2, T + 0.05)
    fc = filt(fc, HP(500, 2))[:n]
    fc = fc * undb(-18.0 * (1.0 - u[:len(fc)]) ** 1.2)
    put(out, lev(tame(fc, 9.0), -26.0), 0.0)
    crk = (frags("sparkler", 3.0, 12.0, 6, (0.03, 0.09), hseed("charge", "sp"), hp=800.0, db=-20.0)
           + frags("fire_0987", 10.0, 30.0, 6, (0.03, 0.09), hseed("charge", "fire"), hp=500.0, db=-20.0)
           + frags("vinyl_2547", 1.0, 8.0, 4, (0.04, 0.1), hseed("charge", "vinyl"), hp=300.0, db=-20.0))
    bursts(out, crk, hseed("charge", "bursts"), 0.05, T - 0.012, 5.0, 70.0, -12.0, 0.0, rate=(1.0, 1.9),
           rev=0.3, curve=1.6)

    bm = src("chunky", 0.0, 2.4)                          # low end sucked in: an explosion body backwards
    bon = onset(bm, -10.0)
    bmr = filt(bm, LP(380, 2), HP(30, 2))[::-1]
    put(out, lev(bmr, -21.0), T - 0.004, align=len(bmr) - 1 - bon)

    y = out[:n] * undb(-12.0 * (1.0 - u) ** 2.0)          # overall: quiet start, full at the end
    y = saturate(norm(y), 1.6, 0.03)
    ir = make_ir(0.7, seed=76, hf=0.5, lf=1.0, predelay=0.01)
    y = reverb(y, ir, wet_db=-15.0)[:n]
    return y


def env4_max(x, a, b):
    """Highest 4 ms RMS of x between samples a and b (how loud a moment feels as a hit)."""
    w = ns(0.004)
    seg = x[max(0, a - w):b + w]
    if len(seg) < w:
        return 1e-12
    return float(np.sqrt(np.convolve(seg ** 2, np.ones(w) / w, mode="valid")).max())


def braked_stroke(key, a, b, r0, r1, pad=0.25):
    """A record stroke under a braking hand: the platter plays [a, b) of `key` while its speed falls linearly
    from r0 to r1, so whatever is on the record sweeps down in pitch (and, velocity-sensitive, gets quieter)."""
    x = norm(src(key, max(0.0, a - pad), b + pad))
    n = ns((b - a) / (0.5 * (r0 + r1)))
    vel = r0 + (r1 - r0) * np.arange(n) / max(1, n - 1)
    return record(x, vel, a - max(0.0, a - pad), ref=max(r0, 1.0))


LASER_ZIPS = [("scratch_2861", 0.70, 0.86, 1.5, 0.55), ("scratch_2859", 0.235, 0.39, 2.4, 0.8), ("rage", 1.85, 2.0, 2.4, 0.8)]
LASER_WOOMS = [(1.45, 0.55, 0.10), (1.4, 0.52, 0.11), (1.5, 0.56, 0.09)]   # rumble_expl tape: start, end speed, glide


def build_laser_fire(i):
    """The disco-laser shot: a scratch zips down, a hard crack lands, a sub boom sinks under it. Organic,
    punchy and short, with no oscillators:
    - the zip: a record under a braking hand (speed 1.5-2.4 down to 0.55-0.8 over 0.09-0.16 s), so the
      scratched sample strokes down in pitch by 9-10 semitones: variant 1 and 2 scratch real DJ strokes
      (variant 2's hand catches twice first, 'z-ZIP'), variant 3 scratches a monster's scream on a crackling
      record. It runs 11-15 dB under the hit, dry (a reverb tail would smear the stroke), and leaves an 8 ms
      gap, so the crack lands as one hard edge;
    - the crack: a staff blow on a stage floor, a far-off blast and the first crack of a very close
      thunderclap, peaks stacked, only their attacks (nothing of the recordings before the bang), the top
      2 ms densified a little. Crest stays at 12 dB or more in the first 30 ms;
    - behind the crack, from 20 ms on, the body: a rumbling explosion on a braking tape (a sub 'woom' sinking
      from about 90 to 45 Hz), a short blast body, the thunder's crackle, sizzle and fire bursts (all under
      8.5 kHz), a whoosh flying off and 1-2 smaller forked cracks. The body is levelled as loud as it can be
      while the crack stays the loudest moment, and it dies fast (-10 dB by ~0.15 s, -20 dB by ~0.35 s);
    - then a quiet thunder roll and a few bits of debris.
    No saturation or limiting of the whole mix: the family's limiter only shaves the crack's top."""
    v = i - 1
    T = (1.0, 1.12, 0.9)[v]
    n = ns(T + 2.0)
    rng = np.random.default_rng(hseed("laser", i))
    dry, edge, body = np.zeros(n), np.zeros(n), np.zeros(n)

    zkey, za, zb, r0, r1 = LASER_ZIPS[v]
    z = filt(braked_stroke(zkey, za, zb, r0, r1), HP(450, 2), BELL(1600, 6.0, 1.5), LP(9000, 4))
    if zkey == "rage":                                     # a scratched scream: put it on a crackling record
        vc = filt(norm(src("vinyl_2547", 1.6, 1.65 + len(z) / SR)), HP(400, 2), LP(8000, 2))
        z = norm(z) + 0.25 * norm(vc[:len(z)])
    zd = len(z) / SR
    z = fades(apply_env(z, env(len(z), [(0, float(undb(-6.0))), (zd, 1.0)])), 0.003, 0.006)   # pushes into it
    z0 = 0.004
    if v == 1:                                             # the hand catches twice before the full zip
        put(dry, lev(fades(z[:ns(0.045)], 0.003, 0.012), -26.0), z0)
        z0 += float(rng.uniform(0.055, 0.075))
    put(dry, lev(z, -24.0), z0)
    hit = z0 + zd + 0.008                                  # an 8 ms gap, then the crack
    pk = hit + 0.003

    def attack(key, a, b, rate, hp, db):
        x = filt(varispeed(norm(src(key, a, b)), rate), HP(hp, 2))
        xi = int(np.argmax(np.abs(x)))
        x = apply_env(x, env(len(x), [(0, 0.0), (max(0.0, xi / SR - 0.0035), 0.0), (max(0.0, xi / SR - 0.0025), 1.0),
                                      (xi / SR + 0.002, 1.0), (xi / SR + 0.007, 0.3), (xi / SR + 0.025, 0.06),
                                      (xi / SR + 0.085, 0.0), (99, 0.0)]))
        put(edge, x * float(undb(db)) / max(1e-9, float(np.max(np.abs(x)))), pk, align=xi)
    attack("theater_blows", (4.70, 5.85, 4.70)[v], (5.50, 6.65, 5.50)[v], (0.82, 0.78, 0.86)[v], 60.0, -1.0)
    attack("expl_1023", 0.0, 1.0, (0.85, 0.8, 0.9)[v], 80.0, -4.0)
    attack("close_thunder", 1.9, 2.2, (1.0, 0.92, 1.08)[v], 200.0, -3.0)
    top = float(np.max(np.abs(edge)))                      # densify only the crack's top 2 ms
    m = np.zeros(n)
    m[ns(pk) - ns(0.001):ns(pk) + ns(0.001)] = 1.0
    w = np.hanning(ns(0.0015) + 2)[1:-1]
    m = np.convolve(m, w / w.sum(), mode="same")
    edge = edge * (1.0 - m) + saturate(edge / top, 1.6) * top * m

    b0 = pk + 0.02                                         # the body comes in behind the crack
    s0, s1, gl = LASER_WOOMS[v]
    sb = sub_boom("rumble_expl", 0.0, 0.89, s0, s1, gl, 0.8, lp=160.0, drive=1.6, decay=0.1)
    so = onset(sb, -10.0)
    sb = apply_env(sb, env(len(sb), [(0, 0.0), (so / SR, 0.0), (so / SR + 0.02, 1.0), (99, 1.0)]))
    put(body, lev(sb, -16.0), b0, align=so)
    pb = filt(varispeed(norm(src("expl_1808")), (0.7, 0.62, 0.78)[v]), HP(150, 2), LP(6000, 2))
    po = onset(pb, -10.0) / SR
    pb = apply_env(pb, env(len(pb), [(0, 0.0), (po, 0.0), (po + 0.02, 1.0), (po + 0.05, 1.0), (po + 0.14, 0.25),
                                     (po + 0.3, 0.0), (99, 0.0)]))
    put(body, lev(pb, -11.0), b0, align=ns(po))
    cr = filt(mono(src("close_thunder", 2.0, 3.4, stereo=True)), HP(300, 2), LP(8500, 4))
    cr = apply_env(cr, env(len(cr), [(0, 0.0), (0.02, 1.0), (0.06, 0.5), (0.2, 0.12), (0.45, 0.0), (99, 0.0)]))
    put(body, lev(cr, -14.0), b0)
    crk = (frags("sparkler", 6.0, 20.0, 6, (0.02, 0.06), hseed("laser", i, "sp"), hp=900.0, db=-20.0)
           + frags("fire_0987", 14.0, 34.0, 6, (0.02, 0.07), hseed("laser", i, "fire"), hp=600.0, db=-20.0))
    cb = np.zeros(n)
    bursts(cb, crk, hseed("laser", i, "bursts"), b0 + 0.005, hit + 0.2, 70.0, 6.0, -6.0, -20.0,
           rate=(0.9, 1.6), rev=0.3, curve=0.6)
    body += filt(cb, LP(8500, 4))
    crk_src = mono(src("close_thunder", 1.9, 2.2, stereo=True))
    cs = int(np.argmax(np.abs(crk_src)))
    fork = fades(filt(crk_src[max(0, cs - ns(0.002)):cs + ns(0.05)], HP(300, 2)), 0.001, 0.03)
    tt = hit
    for k in range((1, 2, 1)[v]):                          # the bolt forks: smaller cracks at uneven gaps
        tt += float(rng.uniform(0.07, 0.15))
        piece = varispeed(fork, float(rng.uniform(0.85, 1.25)))
        put(body, lev(piece, -24.0 - 3.0 * k), tt, align=int(np.argmax(np.abs(piece))))
    wa, wb = [(0.05, 0.45), (1.05, 1.45), (2.15, 2.55)][v]
    sw = filt(varispeed(norm(src("whoosh_0572", wa, wb)), (1.35, 1.25, 1.45)[v]), HP(300, 2), LP(8500, 4))
    sw = apply_env(sw, env(len(sw), [(0, 1.0), (0.12, 0.6), (0.3, 0.0), (99, 0.0)]))
    wo = onset(sw, -20.0)
    sw = apply_env(sw, env(len(sw), [(0, 0.0), (wo / SR, 0.0), (wo / SR + 0.02, 1.0), (99, 1.0)]))
    put(body, lev(sw, -24.0), b0 - 0.006, align=wo)        # it flies off the hit
    tl = filt(norm(src("thunder_3115", 1.0, 3.2)), LP(900, 2), HP(200, 4))
    tl = apply_env(tl, env(len(tl), [(0, 0.0), (0.03, 1.0), (0.15, 0.4), (0.4, 0.12), (max(0.5, T - hit), 0.0), (99, 0.0)]))
    put(body, lev(saturate(norm(tl), 1.4), -30.0), b0 + 0.01)
    deb = frags("rock_breaking", 0.5, 8.0, 4, (0.06, 0.14), hseed("laser", i, "debris"), hp=300.0, db=-28.0)
    scatter(body, deb, hseed("laser", i, "debris_t"), hit + 0.25, T - 0.15, 3, gain_db=(-12.0, -8.0),
            rate=(0.9, 1.2), rev=0.2)

    # the body as loud as it can be while the crack stays the loudest moment (0.5 dB under it, 4 ms RMS);
    # variants 1 and 2 also keep the body's own sample peaks 2 dB under the crack's, so the crack sets the
    # level the family is mastered to
    k = ns(pk)
    body = body * float(undb(-0.5)) * env4_max(edge, k - ns(0.004), k + ns(0.006)) / max(
        env4_max(body + edge, k + ns(0.012), k + ns(0.3)), 1e-12)
    if v < 2:
        body = limit(body, dbfs(np.max(tp_env(edge))) - 2.0, 0.003, true_peak=True)
    ir = make_ir(0.8, seed=95 + i, hf=0.5, lf=0.45, predelay=0.012)    # lows die fast: the woom stays clear
    y = reverb(edge + body, ir, wet_db=-16.0)[:ns(T)]
    y = y + reverb(dry, make_ir(0.25, seed=195 + i, hf=0.5, lf=0.8, predelay=0.004), wet_db=-22.0)[:ns(T)]
    return apply_env(y, env(len(y), [(0, 1), (T - 0.22, 1), (T, 0)]))


# ======================================================================================
# DJ booth
# ======================================================================================
def build_needle():
    """Record scratch + needle drag stinger, played by an unsteady hand:
    - a real DJ back-stroke (the record is yanked) and a short return stroke, sloppily timed,
    - the record itself (crackle and a whispered chant) zipping under a jittery hand curve,
    - the stylus skidding across the grooves (a real needle-bump burst on a slowing platter),
    - a needle-drop thump, then the needle skips: one groove loops a few times, unevenly,
    - and the crackle settles."""
    T = 0.62
    n = ns(T)
    out = np.zeros(n + ns(1.0))
    rng = np.random.default_rng(hseed("needle"))

    s1 = fades(norm(src("scratch_2858", 0.055, 0.300)), 0.003, 0.02)
    s2 = fades(norm(src("scratch_2858", 0.300, 0.405)), 0.004, 0.02)
    put(out, lev(s1, -14.0), 0.0)
    put(out, lev(s2, -18.0), 0.235 + float(rng.uniform(-0.02, 0.02)))

    disc = norm(filt(src("vinyl_2547", 1.0, 4.0), HP(150, 2))) * 0.6 + norm(filt(src("chant8", 0.15, 3.15), HP(200, 2)))
    tt = np.arange(n) / SR
    vel = np.interp(tt, [0.0, 0.012, 0.08, 0.22, 0.28, 0.36, T], [1.0, 0.2, -3.2, 0.0, 2.2, 0.0, 0.0])
    vel = vel + 0.6 * np.clip(wander(n, 30.0, rng), -2.0, 2.0) * (np.abs(vel) > 0.05)
    k = np.hanning(ns(0.012))
    vel = np.convolve(vel, k / k.sum(), mode="same")
    sc = filt(record(disc, vel, 1.5), HP(250, 2), BELL(3000, 3.0, 1.0))
    put(out, lev(sc, -21.0), 0.0)

    dr = varispeed_curve(src("vinyl_0588", 5.50, 6.05), lambda t: 0.95 - 0.45 * np.clip(t / 0.4, 0, 1), 0.45)
    dr = apply_env(filt(dr, HP(70, 2)), env(len(dr), [(0, 1.0), (0.3, 0.7), (0.45, 0.0), (9, 0.0)]))
    put(out, lev(dr, -21.0), 0.25)
    th = filt(norm(src("vinyl_2547", 0.36, 0.66)), HP(35, 2))
    put(out, lev(th, -19.0), 0.255, align=onset(th))

    g = ns(float(rng.uniform(0.045, 0.065)))               # the needle skips: one groove, looping unevenly
    p0 = ns(float(rng.uniform(1.6, 2.6)))
    loop = filt(fades(disc[p0:p0 + g], 0.003, 0.006), HP(200, 2))
    t = 0.385 + float(rng.uniform(0.0, 0.02))
    for j in range(3):
        put(out, lev(loop, -22.0 - 2.5 * j), t)
        t += g / SR * float(rng.uniform(1.05, 1.45))

    tail = filt(norm(src("vinyl_0588", 2.0, 2.7)), HP(300, 2))
    tail = apply_env(tail, env(len(tail), [(0, 0), (0.05, 1), (0.25, 0.6), (0.4, 0.0), (9, 0)]))
    put(out, lev(tame(tail, 9.0), -27.0), 0.3)

    out = saturate(norm(out), 1.3)
    ir = make_ir(0.4, seed=212, hf=0.5, lf=1.0, predelay=0.005, er=1.0)
    y = reverb(out, ir, wet_db=-16.0)[:n]
    return apply_env(y, env(n, [(0, 1), (T - 0.12, 1), (T, 0)]))


def build_slam():
    """Two fists slam the heavy wooden booth, never quite together (a random flam):
    - a staff blow on a stage floor, slowed for mass,
    - a heavy door slam's body and a fist hit,
    - a sub thump from the door's own low resonance on a slowing tape,
    then the gear on top rattles (real loose-metal rattles, sped up small), something tips over and
    clatters a moment later, and it all settles."""
    T = 0.62
    out = np.zeros(ns(T + 1.0))
    rng = np.random.default_rng(220)

    def hit(key, a, b, rate, hp=40.0, body=None):
        x = norm(src(key, a, b))
        x = varispeed(x, rate) if abs(rate - 1.0) > 1e-6 else x
        x = filt(x, HP(hp, 2))
        if body:                                           # damp long door bodies: a slam, not a boom
            o = onset(x) / SR
            x = apply_env(x, env(len(x), [(0, 1.0), (o + 0.03, 1.0), (o + body, 0.0), (99, 0.0)]))
        return x

    A = 0.008
    B = A + float(rng.uniform(0.02, 0.045))
    for t, blow, door, fist, g in ((A, ("theater_blows", 4.70, 5.50, 0.78), ("door_slam4", 0.05, 0.92, 0.85),
                                    ("hit10", 0.0, 0.8, 0.9), 0.0),
                                   (B, ("theater_blows", 5.85, 6.65, 0.74), ("door_slam2", 0.0, 0.85, 0.9),
                                    ("door_close04", 0.0, 0.6, 0.85), -1.5)):
        x = hit(*blow)
        put(out, lev(x, -15.0 + g), t, align=onset(x))
        x = hit(*door, body=0.22)
        put(out, lev(x, -18.0 + g), t + float(rng.uniform(0.0, 0.006)), align=onset(x))
        x = hit(*fist, hp=60.0, body=0.2)
        put(out, lev(x, -18.0 + g), t, align=onset(x))
    flesh = hit("punch5", 0.0, 0.6, 1.0, 150.0)
    put(out, lev(flesh, -24.0), A, align=onset(flesh))
    sub = sub_boom("door_slam2", 0.0, 0.85, 0.7, 0.5, 0.15, 0.5, lp=130.0, decay=0.1)
    put(out, lev(sub, -18.0), A, align=onset(sub, -10.0))

    rat = [("dull_metal", 0.04, 0.22, 1.25, 0.05, -22.0), ("dull_metal", 0.24, 0.42, 1.35, 0.14, -25.0),
           ("metal_slam", 0.12, 0.42, 1.6, 0.09, -29.0), ("dull_metal", 0.52, 0.66, 1.5, 0.25, -28.0)]
    for key, a, b, r, t, g in rat:
        x = filt(varispeed(norm(src(key, a, b)), r * float(rng.uniform(0.92, 1.08))), HP(600, 2))
        put(out, lev(fades(x, 0.002, 0.02), g), t + float(rng.uniform(-0.015, 0.03)))
    bits = [("k_plate_heavy", 0.0, 0.06), ("dull_metal", 0.05, 0.11), ("dull_metal", 0.30, 0.36)]
    t = 0.06 + float(rng.uniform(0.0, 0.02))
    for k in range(12):
        key, a, b = bits[int(rng.integers(0, len(bits)))]
        g = varispeed(norm(src(key, a, b)), float(rng.uniform(1.5, 2.6)))
        g = filt(g * np.exp(-np.arange(len(g)) / ns(rng.uniform(0.008, 0.028))), HP(900, 2))
        put(out, lev(g, -27.0 - 1.1 * k + float(rng.uniform(-3.0, 2.5))), t, align=onset(g))
        t += float(rng.exponential(0.035)) + 0.008
        if t > T - 0.12:
            break
    tip = filt(varispeed(norm(src("metal_slam", 0.0, 0.45)), float(rng.uniform(1.3, 1.7))), HP(400, 2))
    put(out, lev(fades(tip, 0.002, 0.05), -24.0), float(rng.uniform(0.3, 0.42)), align=onset(tip))
    out = saturate(norm(out), 1.2)
    ir = make_ir(0.45, seed=221, hf=0.5, lf=1.0, predelay=0.006, er=1.0)
    y = reverb(out, ir, wet_db=-12.0)[:ns(T)]
    return apply_env(y, env(len(y), [(0, 1), (T - 0.14, 1), (T, 0)]))


# ======================================================================================
# the orbital strike soundtrack (stereo, exactly 15 s, synced to shaders/post/joash_strike)
# ======================================================================================
STRIKE_T = 15.0
STRIKE_MASTER = (-1.8, 1.2, 0.006)                         # limiter ceiling, peak normalisation, look-ahead (s)
TEAR = (0.72, 2.16)
CRACK_AT = 2.16
PLANET_WHOOSH = [2.72 + 0.384 * k for k in range(8)]
PLANET_HIT = [3.264 + 0.384 * k for k in range(8)]
SUN_FALL = (6.4, 8.0)
BLAST_AT = 8.0


def weight_db(x):
    """How heavy an impact feels: 0.6 x its punch (loudest 10 ms RMS in the first 80 ms) + 0.4 x its body
    (RMS of the first 400 ms), in dB. Used to level the planet impacts into a rising ramp."""
    p = x ** 2 if x.ndim == 1 else np.mean(x ** 2, axis=1)
    w = ns(0.01)
    r = np.convolve(p[:ns(0.09)], np.ones(w) / w, mode="valid")
    return 0.6 * 10.0 * math.log10(float(np.max(r)) + 1e-12) + 0.4 * 10.0 * math.log10(float(np.mean(p[:ns(0.4)])) + 1e-12)


def build_strike():
    """Synced to the shader (design/JEHOASH_V2.md section 7); the sync points are exact, the debris
    around them is haphazard (fixed seeds)."""
    n = ns(STRIKE_T)
    out = np.zeros((n, 2))
    rng = np.random.default_rng(300)
    hz = np.random.default_rng(hseed("strike", "haphazard"))

    # --- 0-2 s: storm swell + distant thunder; the storm keeps blowing under the planets ---------
    wind = filt(src("wind_1450", 30.0, 38.2, stereo=True), HP(90, 2), LP(2200, 2))
    wind = apply_env(norm(wind), env(ns(8.2), [(0, 0), (1.7, 1.0), (2.5, 0.85), (6.2, 0.6), (7.7, 0.3), (8.0, 0.0), (8.2, 0)]))
    put(out, wind, 0.0, -16.0)
    far = filt(norm(src("thunder_2718", 0.25, 6.5)), LP(420, 2), HP(30, 2))
    far = apply_env(far, env(len(far), [(0, 0), (0.15, 1), (4.0, 0.6), (6.2, 0)]))
    put(out, widen(far, 0.6, seed=301), 0.12, -12.0, align=onset(far))
    roll = filt(norm(src("thunder_3114", 1.0, 6.0)), LP(300, 2), HP(30, 2))
    roll = apply_env(roll, env(len(roll), [(0, 0), (0.8, 1), (3.5, 0.7), (5.0, 0)]))
    put(out, widen(roll, 0.7, seed=302), 0.9, -17.0)
    for k, (key, a) in enumerate((("thunder_3115", 1.2), ("thunder_2718", 6.5), ("thunder_3114", 2.4))):
        r = filt(norm(src(key, a, a + 2.6)), LP(380, 2), HP(28, 2))   # more far-off grumbles, anywhere
        r = apply_env(r, env(len(r), [(0, 0.0), (0.12, 1.0), (1.0, 0.45), (2.6, 0.0)]))
        put(out, widen(r, 0.6, seed=310 + k), float(hz.uniform(0.2, 1.7)), float(hz.uniform(-20.0, -15.0)),
            pan=float(hz.uniform(-0.6, 0.6)))

    # --- 0.72-2.16 s: the sky tears, ending in a crack -------------------------------------------
    # the sky's fabric rips: real DJ scratch strokes slowed way down, each faster and closer than the
    # last (their pitch arcs become groaning rips), wandering left and right, torn up by random dropouts
    starts = [0.72, 1.22, 1.58, 1.84, 2.00]                # tape speeds 0.42, 0.48, 0.74, 0.83, 0.96
    strokes = [("scratch_2858", 0.055, 0.300), ("scratch_2861", 0.075, 0.285), ("scratch_2870", 0.08, 0.33),
               ("scratch_2861", 0.50, 0.70), ("scratch_2858", 0.55, 0.78)]
    tb = np.zeros((n, 2))
    for k, (key, a, b) in enumerate(strokes):
        end = (starts[k + 1] if k + 1 < len(starts) else CRACK_AT) + 0.08
        s = varispeed(norm(src(key, a, b)), (b - a) / (end - starts[k]))
        s = fades(filt(s, HP(70, 2), LP(6500, 2)), 0.03, 0.04)
        put(tb, s, starts[k], -9.0 + 1.6 * k, pan=-0.45 if k % 2 == 0 else 0.45)
    out += chop(tb, hseed("strike", "tear"), 0.95, 2.1, on=(0.05, 0.16), off=(0.012, 0.04), depth=0.85)
    # electric sizzle swelling up (sparkler on a speeding tape)
    tl = TEAR[1] - TEAR[0]
    sp = varispeed_curve(src("sparkler", 6.0, 8.5), lambda t: 0.6 + 0.8 * np.clip(t / tl, 0, 1), tl + 0.05)
    sp = filt(sp, HP(600, 2))
    sp = apply_env(sp, env(len(sp), [(0, 0.0), (0.4, 0.12), (1.2, 0.6), (tl, 1.0), (tl + 0.05, 0.0)]))
    put(out, widen(norm(sp), 0.8, seed=306), TEAR[0], -14.0)
    # a gust on a speeding tape, opening up
    gust = varispeed_curve(src("wind_1450", 50.0, 53.0), lambda t: 0.7 + 0.6 * np.clip(t / tl, 0, 1), tl + 0.05)
    gust = stft_apply(gust, lambda t_, f_: lp_res(f_[None, :], (400.0 * 12.0 ** np.clip(t_ / tl, 0, 1))[:, None], 2.0),
                      n=1024, hop=256)
    gust = apply_env(gust, env(len(gust), [(0, 0.0), (0.5, 0.3), (tl, 1.0), (tl + 0.05, 0.0)]))
    put(out, widen(norm(gust), 0.7, seed=307), TEAR[0], -12.0)
    # random crackle bursts tearing at the seams, thicker toward the crack
    tf = (frags("sparkler", 6.0, 20.0, 8, (0.02, 0.07), hseed("strike", "tear_sp"), hp=800.0, db=-20.0)
          + frags("fire_0987", 12.0, 30.0, 8, (0.02, 0.08), hseed("strike", "tear_fire"), hp=500.0, db=-20.0))
    bursts(out, tf, hseed("strike", "tear_bursts"), 0.85, CRACK_AT - 0.01, 4.0, 45.0, -12.0, -2.0,
           rate=(0.8, 1.5), pan=(-0.85, 0.85), curve=1.4)
    # a thunder roll backwards, swelling into the crack
    rt = src("thunder_2718", 0.4, 3.2)
    ron = onset(rt, -8.0)
    rtr = filt(rt, LP(1600, 2), HP(35, 2))[::-1]
    put(out, widen(norm(rtr), 0.6, seed=308), CRACK_AT - 0.01, -9.0, align=len(rtr) - 1 - ron)
    # the crack: InspectorJ's very close strike (its loud, bright second crack) and a boom
    crack = norm(src("close_thunder", 1.40, 6.4, stereo=True))
    crack_on = ns(1.40) + onset(src("close_thunder", 1.40, 1.75), -6.0)
    crack = filt(crack, HP(40, 2))
    crack = apply_env(crack, env(len(crack), [(0, 0.0), (crack_on / SR - 1.40 - 0.02, 0.0), (crack_on / SR - 1.40, 1.0),
                                              (3.0, 0.5), (5.0, 0.0)]))
    put(out, crack, CRACK_AT, -4.5, align=crack_on - ns(1.40))
    boom = filt(norm(src("thunder_3180", 0.0, 5.0)), LP(500, 2), HP(28, 2))
    boom = apply_env(boom, env(len(boom), [(0, 1), (2.5, 0.6), (5.0, 0)]))
    put(out, widen(boom, 0.6, seed=305), CRACK_AT, -8.0, align=onset(boom))

    # --- planets: whoosh from 2.72 + 0.384k, impact at 3.264 + 0.384k, heavier and lower each time --
    hits = [("expl_1023", 0.0, 2.2), ("expl_1808", 0.0, 0.33), ("explode", 0.0, 1.72), ("cannon_hit", 0.0, 2.2),
            ("single_multi", 5.68, 7.9), ("ship_destroyed", 2.95, 5.2), ("chunky", 0.0, 2.2), ("rumble_expl", 0.0, 0.89)]
    whooshes = ["whoosh_1796", "whoosh_1801", "whoosh_1800", "whoosh_1795"]
    sec = [("cannon_hit", 0.0, 0.9), ("ship_destroyed", 3.0, 4.2), ("rock_breaking", 0.3, 1.4),
           ("expl_1807", 0.0, 0.21), ("single_multi", 6.0, 7.0), ("explode", 0.0, 0.9)]
    pf = frags("fire_0987", 30.0, 37.0, 8, (0.03, 0.09), hseed("strike", "planet_fire"), hp=400.0, db=-20.0)
    impacts = []                                           # levelled against the blast further down
    for k in range(8):
        side = -1.0 if k % 2 == 0 else 1.0
        heavy = k / 7.0
        tw, th = PLANET_WHOOSH[k], PLANET_HIT[k]
        wl = th - tw
        wn = ns(wl)
        w = mono(src(whooshes[k % 4], stereo=True))
        w = w[:env_peak_index(w) + ns(0.02)]
        w = varispeed(norm(w), len(w) / wn)                # rising half, stretched to end on the hit
        w = filt(w, HP(70, 2), LP(5000 - 2500 * heavy, 2))
        roar = varispeed_curve(src("tgv_0120", 3.0 + 0.7 * k, 5.0 + 0.7 * k),
                               lambda t, h=heavy: (0.9 - 0.3 * h) + 0.5 * np.clip(t / wl, 0, 1), wl + 0.02)
        roar = stft_apply(roar, lambda t_, f_, h=heavy: lp_res(f_[None, :], ((500.0 - 200.0 * h) * 8.0 ** np.clip(t_ / wl, 0, 1))[:, None], 2.0),
                          n=1024, hop=256)
        roar = roar[:wn] * undb(-20.0 * (1.0 - np.arange(min(wn, len(roar))) / wn) ** 1.3)
        whoosh = np.zeros(wn)
        put(whoosh, norm(w), 0.0)
        put(whoosh, norm(roar), 0.0, -4.0)
        whoosh = fades(whoosh, 0.02, 0.004)
        put(out, pan_curve(whoosh, np.linspace(-0.45 * side, 0.3 * side, wn)), tw, -12.0 + 4.0 * heavy)
        # impact: a real explosion, slowed further each time, + a sub thump (a blast body on a slowing
        # tape, lower each time) + falling stones for the big ones; haphazard: chunks of the planet keep
        # going off just after the hit and burning bits crackle. The whole impact is rendered on its own
        # and levelled to a loudness ramp, so every planet lands heavier than the one before.
        imp = np.zeros((ns(2.2), 2))
        key, a, b = hits[k]
        x = src(key, a, b, stereo=True)
        x = varispeed(norm(x), 1.0 - 0.36 * heavy)
        on = onset(x, -14.0)
        ma = main_attack(x)
        if ma - on > ns(0.02):                             # the -14 dB crossing is only a precursor (planet 6's
            x = fades(x[ma:], 0.0015, 0.0)                 # slice opens on a crackle, the bang comes 0.18 s
            on = 0                                         # later): line up the real bang, drop what's before it
        x = apply_env(x, env(len(x), [(0, 1), (on / SR + 0.25, 1.0), (on / SR + 0.9 + 0.5 * heavy, 0.0), (99, 0)]))
        put(imp, x, 0.0, 0.0, pan=0.3 * side, align=on)
        thump = sub_boom("expl_1808", 0.0, 0.33, 0.85 - 0.3 * heavy, 0.5 - 0.22 * heavy, 0.2,
                         0.6 + 0.6 * heavy, lp=150.0 - 40.0 * heavy, decay=0.2 + 0.25 * heavy)
        put(imp, thump, 0.0, -2.0, pan=0.15 * side, align=onset(thump, -10.0))
        if k >= 3:
            st = [1.52, 2.92, 4.12, 5.32, 6.52]
            d = filt(norm(src("stones_1022", st[k - 3], st[k - 3] + 0.9)), HP(150, 2))
            put(imp, d, 0.04, -9.0, pan=0.5 * side, align=onset(d))
        for j in range(1 + k // 3):
            key, a, b = sec[int(hz.integers(0, len(sec)))]
            x2 = varispeed(norm(src(key, a, b, stereo=True)), float(hz.uniform(0.8, 1.05)) - 0.25 * heavy)
            o2 = onset(x2, -14.0)
            x2 = apply_env(x2, env(len(x2), [(0, 1.0), (o2 / SR + 0.12, 1.0), (o2 / SR + 0.45 + 0.3 * heavy, 0.0), (99, 0.0)]))
            put(imp, x2, float(hz.uniform(0.06, 0.24)), -11.0 + float(hz.uniform(-2.0, 1.5)),
                pan=side * float(hz.uniform(0.15, 0.75)), align=o2)
        bursts(imp, pf, hseed("strike", "planet_crackle", k), 0.02, 0.3, 40.0, 6.0, -6.0, -16.0,
               rate=(0.8, 1.3), pan=(-0.7, 0.7))
        impacts.append((th, imp))

    # --- 6.4-8.0 s: the sun falls: a rising roar (a train's roar and a monster's on speeding tapes)
    # and a rising sub (a deep wind rumble and a rocket engine on speeding tapes), crackling as it comes
    s0, s1 = SUN_FALL
    sl = s1 - s0
    m = ns(sl)
    st = np.arange(m) / SR
    tg = varispeed_curve(src("tgv_0120", 2.0, 7.0), lambda t: 0.55 + 0.75 * np.clip(t / sl, 0, 1) ** 1.6, sl + 0.05)
    tg = stft_apply(tg, lambda t_, f_: lp_res(f_[None, :], (350.0 * 16.0 ** np.clip(t_ / sl, 0, 1))[:, None], 2.0))
    mr = varispeed_curve(src("deep_roar", 0.3, 5.5), lambda t: 0.55 + 0.5 * np.clip(t / sl, 0, 1) ** 1.3, sl + 0.05)
    mr = filt(mr, HP(60, 2), LP(3000, 2))
    sub = varispeed_curve(src("deep_rumble", 10.0, 16.0), lambda t: 0.5 + 1.1 * np.clip(t / sl, 0, 1) ** 1.5, sl + 0.05)
    eng = varispeed_curve(src("rocket_engine", 0.5, 5.5), lambda t: 0.45 + 0.8 * np.clip(t / sl, 0, 1) ** 1.5, sl + 0.05)
    sub = filt(norm(sub) + 0.6 * norm(eng), LP(260, 2), HP(25, 2))
    cres = undb(-18.0 * (1.0 - st / sl) ** 1.4)
    sun = np.zeros(m)
    put(sun, norm(tg[:m]), 0.0)
    put(sun, norm(mr[:m]), 0.0, -6.0)
    sun *= cres
    put(out, widen(sun, 0.7, seed=320), s0, -3.0)
    sub = saturate(norm(sub[:m]) * undb(-18.0 * (1.0 - st[:len(sub[:m])] / sl) ** 1.6), 1.8)
    put(out, sub, s0, -5.0)
    sf = frags("fire_0987", 2.0, 36.0, 8, (0.03, 0.1), hseed("strike", "sun_fire"), hp=300.0, db=-20.0)
    bursts(out, sf, hseed("strike", "sun_crackle"), s0 + 0.1, s1 - 0.03, 5.0, 50.0, -16.0, -4.0,
           rate=(0.7, 1.2), pan=(-0.8, 0.8), curve=1.5)
    suck = norm(src("chunky", 0.08, 2.6, stereo=True))[::-1]   # reversed blast body sucks into 8.0 s
    suck = apply_env(suck, env(len(suck), [(0, 0), (1.2, 0.3), (len(suck) / SR, 1.0)]))
    put(out, suck, BLAST_AT - len(suck) / SR - 0.015, -9.0)
    out[ns(BLAST_AT - 0.03):ns(BLAST_AT)] *= np.linspace(1.0, 0.25, ns(0.03))[:, None]  # breath before the blast

    # --- 8.0 s: the explosion (biggest, wide) ----------------------------------------------------
    blast = np.zeros((ns(7.0), 2))
    layers = [("chunky", 0.0, 2.6, 0.82, 0.000, -2.0, -0.45), ("explode", 0.0, 1.72, 0.76, 0.004, -3.0, 0.45),
              ("cannon_hit", 0.0, 4.9, 0.7, 0.010, -6.0, 0.0), ("ship_destroyed", 2.95, 9.0, 0.85, 0.02, -8.0, -0.25),
              ("single_multi", 5.68, 12.0, 0.8, 0.0, -6.0, 0.25), ("rock_breaking", 0.0, 6.0, 0.9, 0.06, -10.0, 0.0)]
    for key, a, b, r, dt, g, pan in layers:
        x = varispeed(norm(src(key, a, b, stereo=True)), r)
        put(blast, x, dt, g, pan=pan, align=onset(x, -14.0))
    hj = varispeed(norm(src("hjm_boom")), 0.9)
    put(blast, widen(filt(hj, HP(28, 2)), 0.5, seed=330), 0.0, -7.0, align=onset(hj))
    sb = sub_boom("expl_1808", 0.0, 0.33, 0.6, 0.3, 0.8, 2.6, lp=110.0, decay=0.9)
    put(blast, sb, 0.0, -2.0, align=onset(sb, -10.0))
    sb2 = sub_boom("hjm_boom", 0.0, 4.5, 0.7, 0.42, 1.2, 4.0, lp=120.0, decay=1.4)
    put(blast, sb2, 0.0, -5.0)
    blast = reverb(blast, make_ir(3.4, seed=331, hf=0.35, lf=1.3, predelay=0.02, stereo=True), wet_db=-10.0)
    put(out, blast, BLAST_AT)
    # every planet lands heavier than the last: weight = punch (loudest 10 ms) and body (first 400 ms),
    # ramped from 12 dB under the blast (planet 1) to 6 dB under it (planet 8). Just before each one the air
    # is sucked out: the storm, the whooshes and the previous planet's debris duck 7 dB for the last ~50 ms
    # (sidechain-style, he's a DJ), so every impact lands on a hole and hits hard. What the ear gets is the
    # weight in the finished mix (the whoosh, the duck, the last planet's debris and the master limiter all
    # count), so the ramp is then evened out there: planets 2-7 are nudged until the weight measured on the
    # mono mix, mastered the way main() masters the strike, rises in equal steps from planet 1's to planet
    # 8's (at least 0.45 dB a step).
    ref = weight_db(blast)
    duck = float(undb(-7.0))
    bed = out

    def land(offsets):
        y = bed
        for k, (th, imp) in enumerate(impacts):
            y = apply_env(y, env(n, [(0, 1.0), (th - 0.075, 1.0), (th - 0.025, duck), (th + 0.005, duck),
                                     (th + 0.065, 1.0), (STRIKE_T, 1.0)]))
            put(y, imp, th, ref - 12.0 + 6.0 * k / 7.0 - weight_db(imp) + offsets[k])
        return y

    offsets = np.zeros(len(impacts))
    for _ in range(10):
        out = land(offsets)
        mm = mono(limit(norm(clean(out, hp=22.0), STRIKE_MASTER[1]), STRIKE_MASTER[0], STRIKE_MASTER[2], true_peak=True))
        w = np.array([weight_db(mm[ns(th):]) for th, _ in impacts])
        rise = max(float(w[-1] - w[0]), 0.45 * (len(w) - 1))
        err = w[0] + rise * np.arange(len(w)) / (len(w) - 1) - w
        err[0] = 0.0
        if rise <= w[-1] - w[0]:
            err[-1] = 0.0
        if np.max(np.abs(err)) < 0.08:
            break
        offsets += err
    print("STRIKE_PLANET_WEIGHTS_IN_MIX", json.dumps([round(float(x), 2) for x in w]),
          "offsets_db", json.dumps([round(float(x), 2) for x in offsets]))
    # haphazard: the blast sets off a ragged chain of smaller ones all around (a Valencia mascleta's
    # firecracker finale, slowed into deep bangs) and three later secondary booms, farther off
    ms = norm(src("masclet", 236.0, 246.0, stereo=True))
    for k in range(16):
        L = float(hz.uniform(0.22, 0.6))
        a = float(hz.uniform(0.0, 10.0 - L))
        piece = varispeed(ms[ns(a):ns(a + L)], float(hz.uniform(0.48, 0.72)))
        piece = fades(filt(piece, HP(35, 2)), 0.004, 0.08)
        t = BLAST_AT + 0.08 + 2.4 * float(hz.uniform(0.0, 1.0)) ** 1.7
        put(out, piece, t, -13.0 - 3.0 * (t - BLAST_AT) + float(hz.uniform(-3.0, 2.0)), pan=float(hz.uniform(-0.85, 0.85)))
    for (lo, hi), (key, a, b), g in (((8.45, 9.0), ("cannon_hit", 0.0, 3.0), -9.0),
                                     ((9.3, 10.3), ("explode", 0.0, 1.72), -13.0),
                                     ((10.6, 11.7), ("single_multi", 6.4, 8.6), -17.0)):
        x = varispeed(norm(src(key, a, b, stereo=True)), float(hz.uniform(0.55, 0.7)))
        x = filt(x, LP(2200, 2), HP(30, 2))
        put(out, x, float(hz.uniform(lo, hi)), g, pan=float(hz.uniform(-0.7, 0.7)), align=onset(x, -14.0))

    # --- 8.0-11.0 s: shockwave sweep (pressure front rolling across, pitch falling, gusting) -------
    swl = 3.0
    m = ns(swl)
    wnd = mono(src("wind_1450", 60.0, 60.0 + swl, stereo=True))
    shk = np.zeros(m)
    put(shk, mono(src("shake", 0.0, 2.5, stereo=True)), 0.0)
    trn = varispeed_curve(src("tgv_0120", 4.0, 9.0), lambda t: 1.3 - 0.6 * np.clip(t / swl, 0, 1), swl + 0.02)
    sw = norm(wnd[:m]) + 0.7 * norm(shk) + 0.6 * np.pad(norm(trn), (0, max(0, m - len(trn))))[:m]
    sw = stft_apply(sw, lambda t_, f_: band(f_[None, :], (3500.0 * (160.0 / 3500.0) ** np.clip(t_ / swl, 0, 1) ** 0.7)[:, None], 1.6))
    sw = norm(sw) * env(m, [(0, 0), (0.35, 1.0), (1.2, 0.6), (swl, 0.0)])
    sw = flutter(sw, hseed("strike", "gusts"), 0.4, 7.0)
    put(out, pan_curve(sw, np.linspace(-0.8, 0.8, m)), BLAST_AT, -6.0)

    # --- 8.2-14 s: embers and debris crackle -------------------------------------------------------
    el = 6.3
    fire = np.stack([src("fire_0987", 10.0, 10.0 + el), src("fire_0987", 21.0, 21.0 + el)], axis=1)
    fire = filt(fire, HP(300, 2), LP(9000, 2))
    fire = apply_env(norm(fire), env(ns(el), [(0, 0), (0.6, 1.0), (2.3, 0.85), (5.8, 0.0), (el, 0.0)]))
    put(out, fire, 8.2, -13.0)
    pops = filt(src("fireplace_2857", 5.0, 5.0 + el, stereo=True), HP(800, 2))
    pops = apply_env(norm(pops), env(ns(el), [(0, 0), (0.8, 1.0), (3.0, 0.6), (5.6, 0.0), (el, 0.0)]))
    put(out, pops, 8.2, -18.0)
    rubble = filt(norm(src("cave_in", 0.25, 4.1)), LP(5000, 2), HP(80, 2))
    rubble = apply_env(rubble, env(len(rubble), [(0, 0), (0.3, 1.0), (2.5, 0.5), (3.85, 0.0)]))
    put(out, widen(rubble, 0.8, seed=350), 8.25, -14.0)
    starts = [1.52, 2.92, 4.12, 5.32, 6.52, 7.72, 9.12, 10.42]
    when = [8.45, 8.95, 9.6, 10.3, 11.1, 11.9, 12.75, 13.45]
    for k in range(8):
        d = filt(norm(src("stones_1022", starts[k], starts[k] + 0.9)), HP(120, 2))
        put(out, d, when[k] + float(hz.uniform(-0.2, 0.2)), -11.0 - 1.6 * k, pan=float(rng.uniform(-0.6, 0.6)), align=onset(d))
    pp = frags("fireplace_2857", 5.0, 40.0, 14, (0.015, 0.05), hseed("strike", "ember_pops"), hp=1200.0, db=-20.0)
    bursts(out, pp, hseed("strike", "embers"), 8.6, 14.3, 16.0, 2.5, -8.0, -20.0, rate=(0.8, 1.3),
           pan=(-0.9, 0.9), curve=0.8)
    rumble = filt(norm(src("thunder_3114", 4.0, 11.0)), LP(260, 2), HP(28, 2))
    rumble = apply_env(rumble, env(len(rumble), [(0, 0), (0.3, 1.0), (3.0, 0.6), (6.4, 0.0), (7.0, 0.0)]))
    put(out, widen(rumble, 0.7, seed=360), 8.1, -10.0)

    # --- fade out by 15 s ------------------------------------------------------------------------
    return apply_env(out, env(n, [(0, 1.0), (12.6, 1.0), (14.85, 0.0), (STRIKE_T, 0.0)]))


# ======================================================================================
# build
# ======================================================================================
FAMILY = {  # family: (loudness target, sample-peak ceiling dBFS, limiter budget dB[, measure])
    # measure: "lufs" (integrated, the default) or "punch" (mean of integrated and loudest-200 ms loudness,
    # for short hits). The laser's limiter only shaves the top 1.2 dB of its crack, so the crack keeps a crest
    # of 12 dB or more in its first 30 ms instead of being squashed into the body (that costs loudness: the
    # laser family is mastered quieter than the others in LUFS, but it hits harder).
    "voice": (-13.0, -1.5, 4.0), "roar": (-12.0, -1.5, 4.0), "hurt": (-12.6, -1.5, 4.0, "punch"),
    "death": (-14.0, -1.5, 4.0), "ambient": (-20.0, -6.0, 1.5), "charge": (-14.0, -1.5, 3.0),
    "laser": (-12.5, -1.5, 1.2, "punch"), "needle": (-14.0, -1.5, 3.0), "slam": (-12.0, -1.5, 3.0),
}
MEASURES = {"lufs": lufs, "punch": punch_loudness}


def family_measure(family):
    spec = FAMILY.get(family, ())
    return MEASURES[spec[3]] if len(spec) > 3 else lufs


OUTPUTS = [  # name, file, builder, family
    ("welcome_voice", "joash/welcome_voice", build_welcome_voice, "voice"),
    ("roar_1", "joash/roar_1", lambda: build_roar(1), "roar"),
    ("roar_2", "joash/roar_2", lambda: build_roar(2), "roar"),
    ("hurt_1", "joash/hurt_1", lambda: build_hurt(1), "hurt"),
    ("hurt_2", "joash/hurt_2", lambda: build_hurt(2), "hurt"),
    ("hurt_3", "joash/hurt_3", lambda: build_hurt(3), "hurt"),
    ("death", "joash/death", build_death, "death"),
    ("ambient_1", "joash/ambient_1", lambda: build_ambient(1), "ambient"),
    ("ambient_2", "joash/ambient_2", lambda: build_ambient(2), "ambient"),
    ("ambient_3", "joash/ambient_3", lambda: build_ambient(3), "ambient"),
    ("ambient_4", "joash/ambient_4", lambda: build_ambient(4), "ambient"),
    ("laser_charge", "joash/laser_charge", build_laser_charge, "charge"),
    ("laser_fire_1", "joash/laser_fire_1", lambda: build_laser_fire(1), "laser"),
    ("laser_fire_2", "joash/laser_fire_2", lambda: build_laser_fire(2), "laser"),
    ("laser_fire_3", "joash/laser_fire_3", lambda: build_laser_fire(3), "laser"),
    ("needle", "dj_booth/needle", build_needle, "needle"),
    ("slam", "dj_booth/slam", build_slam, "slam"),
    ("strike", "joash/strike", build_strike, "strike"),
]


def _sha(path):
    with open(path, "rb") as fh:
        return hashlib.sha256(fh.read()).hexdigest()


def credits_block():
    """Credits for every non-Kenney source used, grouped by pack, with the outputs that use it."""
    packs = {}
    special = []
    for key, outs in sorted(USED.items()):
        s = SOURCES[key]
        if "kenney" in s["author"].lower():
            continue
        if s.get("credit_line"):
            special.append(s["credit_line"])
            continue
        p = packs.setdefault((s["pack"], s["author"], s["license"]), {"items": [], "outs": set(), "urls": []})
        p["items"].append(s.get("item") or os.path.basename(s["path"]))
        p["outs"] |= {o for o in outs if o}
        url = s.get("pack_url") or s["url"]
        if url not in p["urls"]:
            p["urls"].append(url)
    lines = ["# >>> build_sounds_v2.py (generated - edit the manifest, not this block)",
             "Jehoash v2 sounds (sounds/joash: ambient_1-4, hurt_1-3, death, welcome_voice, roar_1-2, laser_charge,",
             "laser_fire_1-3, strike; sounds/dj_booth: needle, slam) are mixed by tools/audio/build_sounds_v2.py",
             "from these recordings (processing only - no synthesized tones):"]
    for (pack, author, lic), p in sorted(packs.items(), key=lambda kv: kv[0][0].lower()):
        lines.append(f'  "{pack}" by {author}, {lic} - {" , ".join(p["urls"])}')
        lines.append(f"      used: {', '.join(sorted(set(p['items'])))}")
        lines.append(f"      in: {', '.join(sorted(p['outs']))}")
    if any("kenney" in SOURCES[k]["author"].lower() for k in USED):
        lines.append("  Kenney Impact Sounds (CC0, credited above) - small gear-rattle bits in dj_booth/slam.")
    lines += special
    lines.append("CC0 1.0 = https://creativecommons.org/publicdomain/zero/1.0/ ; CC-BY 3.0 = https://creativecommons.org/licenses/by/3.0/ ;"
                 " CC-BY 4.0 = https://creativecommons.org/licenses/by/4.0/")
    lines.append("# <<< build_sounds_v2.py")
    return "\n".join(lines) + "\n"


def update_credits(path):
    with open(path, encoding="utf-8") as fh:
        text = fh.read()
    block = credits_block()
    a, b = text.find("# >>> build_sounds_v2.py"), text.find("# <<< build_sounds_v2.py")
    if a >= 0 and b > a:
        e = text.find("\n", b)
        text = text[:a] + block + (text[e + 1:] if e >= 0 else "")
    else:
        text = text.rstrip("\n") + "\n\n" + block
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("CREDITS updated", path)


def main():
    kept_before = {k: _sha(os.path.join(SOUNDS_DIR, k)) for k in KEPT if os.path.isfile(os.path.join(SOUNDS_DIR, k))}
    want = [o for o in OUTPUTS if not ONLY or o[0] in ONLY]
    families = {o[3] for o in want}
    raw = {}
    for name, rel, builder, family in OUTPUTS:                # build every member of each wanted family
        if family in families:
            CURRENT[0] = rel
            raw[name] = clean(builder(), hp=22.0 if family == "strike" else 25.0)
    targets = {}
    for family in sorted(families - {"strike"}):
        target, ceiling, budget = FAMILY[family][:3]
        measure = family_measure(family)
        members = [o[0] for o in OUTPUTS if o[3] == family]
        at_ceiling = [measure(raw[m]) + ceiling - dbfs(np.max(tp_env(raw[m]))) for m in members]  # if only TP-normalised
        targets[family] = min(target, min(at_ceiling) + budget)
        print("FAMILY", family, "target", round(targets[family], 2), "measure", measure.__name__, "members", members,
              "loudness_at_ceiling", [round(v, 2) for v in at_ceiling])
    welcome_target = None
    for name, rel, builder, family in want:
        if family == "strike":
            y, d, info = master_write(rel, raw[name], family, ceiling=STRIKE_MASTER[0], peak_db=STRIKE_MASTER[1],
                                      look=STRIKE_MASTER[2], bitrate=192000, note="stereo")
            if len(y) != ns(STRIKE_T) or len(d) != ns(STRIKE_T) or d.shape[1] != 2:
                raise SystemExit("strike is not exactly 15.0 s stereo")
        else:
            y, d, info = master_write(rel, raw[name], family, lufs_target=targets[family], ceiling=FAMILY[family][1],
                                      measure=family_measure(family))
            info["family_target_lufs"] = round(targets[family], 2)
            if family_measure(family) is not lufs:
                info["family_measure"] = "punch: mean of integrated and loudest-200 ms loudness"
            if name == "welcome_voice":
                welcome_target = lufs(y)
        REPORT.append(info)
        print("WROTE", json.dumps(info))
    if welcome_target is not None and MANIFEST.get("welcome_ref_out"):
        CURRENT[0] = None
        ref = clean(norm(src("porkrind")))
        ref = ref * float(undb(min(welcome_target - lufs(ref), -1.5 - dbfs(np.max(tp_env(ref))))))
        ref_path = MANIFEST["welcome_ref_out"]
        rel = os.path.splitext(os.path.basename(ref_path))[0]
        _y, _d, info = master_write(rel, ref, "reference", ceiling=-1.5, peak_db=dbfs(peak(ref)),
                                    note="unwired A/B reference: the clean original, levelled",
                                    out_dir=os.path.dirname(ref_path))
        info["file"] = ref_path
        REPORT.append(info)
        print("WROTE", json.dumps(info))
    if IN_REPO and not ONLY:
        for rel in OBSOLETE:
            p = os.path.join(SOUNDS_DIR, rel)
            if os.path.isfile(p):
                os.remove(p)
                print("DELETED", rel)
        update_credits(os.path.join(SOUNDS_DIR, "credits.txt"))
    kept_after = {k: _sha(os.path.join(SOUNDS_DIR, k)) for k in kept_before}
    if kept_after != kept_before:
        raise SystemExit("a kept sound file changed!")
    print("KEPT_UNCHANGED", len(kept_before))
    print("USED_SOURCES", json.dumps({k: sorted(o for o in v if o) for k, v in sorted(USED.items())}))
    if OPTS.get("report"):
        with open(OPTS["report"], "w", encoding="utf-8") as fh:
            json.dump({"outputs": REPORT, "family_targets": targets}, fh, indent=1)
    print("REPORT_JSON", json.dumps(REPORT))


if __name__ == "__main__":
    main()
