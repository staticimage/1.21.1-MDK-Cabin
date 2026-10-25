"""Build Jehoash's illusion sounds from recorded CC0 sources: voices, glass, metal and air. No synth tones.

Run with Blender's Python (numpy + audaspace `aud` for decoding and Ogg Vorbis, no ffmpeg needed):
    blender --background --factory-startup --python tools/audio/build_illusion_sounds.py -- \
        <audio_src_dir> <kenney_dir> <repo_dir> [--manifest <manifest.json>] [--credits <out.txt>] [--out <dir>]

<audio_src_dir> holds the downloaded voice/impact sources (voices/..., impacts/bigsoundbank/...).
<kenney_dir> holds the extracted Kenney packs kenney_impact-sounds and kenney_rpg-audio (CC0, https://kenney.nl).
--manifest  approved-source list; the build stops if a non-Kenney source is missing from it or is not CC0.
--credits   writes a credits snippet (pack, author, license, url) for the sources that were used.
--out       writes to another folder instead of the repo (for previews).
--only a,b  builds only the named sounds; --verbose prints each layer's share of the mix;
--stems <dir> writes every layer as a WAV at its final gain (for tuning).
Only these files are written, into assets/cabinmod/sounds/joash/ (mono, 44.1 kHz, peak <= -1 dBFS measured
on the decoded Ogg, levelled with gated BS.1770 loudness like build_sounds_v2.py: illusions -15 LUFS,
pops -17.5 LUFS):
  illusions_1/2     Jehoash conjures three decoys. The game starts this sound as they spawn, so the lead-in
                    is short: a reversed slowed ghost exhale and a reversed whispered syllable (with reverse
                    reverb) swell for ~0.3 s and stop on the "appearance" moment. There a short choir moan
                    blooms and dies away: one real voice layered three times at chord intervals, each copy
                    entering a moment after the last, like the three decoys. With it: a shimmer of scattered
                    real glass/metal grains (the mirror), a soft rope whoosh, and the swell's syllable murmured
                    forwards (its mirror image). Variant 1 is darker (undead moan, diminished chord, glass,
                    ghost-moan bed). Variant 2 is higher and chanted (sung chant, close cluster, metal + coins).
  illusion_pop_1-3  A decoy is hit and bursts (the game plays these at pitch 1.2): layered real glass hits, a
                    cascade of tiny glass fragments and settling tinkles, a small sword-swish air puff, and a
                    ghostly gasp. The gasp is a reversed whisper plus breath that grows and is sucked away
                    (its pitch and brightness fall as it is pulled back, and it stops dead); its final snap is
                    held under the glass so the pop stays one burst. A short room tail follows.
Deterministic: every random choice uses a fixed seed, and the Ogg stream serial is pinned, so a rebuild
produces byte-identical files.
"""
import json
import os
import shutil
import sys
import tempfile
import zlib

import aud
import numpy as np

RATE = 44100
ARGS = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []


def _opt(name):
    if name in ARGS:
        i = ARGS.index(name)
        val = ARGS[i + 1]
        del ARGS[i:i + 2]
        return val
    return None


MANIFEST, CREDITS, OUT_DIR, ONLY = _opt("--manifest"), _opt("--credits"), _opt("--out"), _opt("--only")
STEMS = _opt("--stems")
VERBOSE = "--verbose" in ARGS
ARGS = [a for a in ARGS if a != "--verbose"]
if len(ARGS) < 3:
    raise SystemExit(__doc__)
SRC_DIR, KENNEY_DIR, REPO = ARGS[:3]
OUT = OUT_DIR or os.path.join(REPO, "src", "main", "resources", "assets", "cabinmod", "sounds", "joash")

# --- sources ------------------------------------------------------------------------------------------
PACKS = {  # id: (pack, author, license, url)
    "ghost_breath": ("Ghost breath", "qubodup", "CC0 1.0", "https://opengameart.org/content/ghost-breath"),
    "chanting": ("Creepy Incoherent Chanting", "OwlishMedia", "CC0 1.0",
                 "https://opengameart.org/content/creepy-incoherent-chanting"),
    "undead": ("Undead Moans", "AntumDeluge", "CC0 1.0", "https://opengameart.org/content/undead-moans"),
    "ghost_moans": ("Ghost Monster Voice Moaning & Growling", "qubodup", "CC0 1.0",
                    "https://opengameart.org/content/ghost-monster-voice-moaning-growling"),
    "bigsoundbank": ("BigSoundBank", "Joseph Sardin", "CC0 1.0", "https://bigsoundbank.com"),
    "kenney_impact": ("Impact Sounds", "Kenney (kenney.nl)", "CC0 1.0", "https://kenney.nl/assets/impact-sounds"),
    "kenney_rpg": ("RPG Audio", "Kenney (kenney.nl)", "CC0 1.0", "https://kenney.nl/assets/rpg-audio"),
}
CHANT = "voices/owlishmedia_creepy_chanting/x_Creepychanting/"
BSB = "impacts/bigsoundbank/"
KI = "kenney_impact-sounds/Audio/"
SOURCES = {  # key: (root "src"|"kenney", relative path, pack id, credit detail)
    "breath": ("src", "voices/qubodup_ghost_breath/ghostbreath.flac", "ghost_breath", "ghostbreath.flac"),
    "whisper_e08": ("src", CHANT + "ethereal_chant_Subclip 08_08.wav", "chanting", "ethereal_chant_Subclip 08_08.wav"),
    "whisper_c7": ("src", CHANT + "chant7.wav", "chanting", "chant7.wav"),
    "whisper_c8": ("src", CHANT + "chant8.wav", "chanting", "chant8.wav"),
    "whisper_e07": ("src", CHANT + "ethereal_chant_Subclip 07_07.wav", "chanting", "ethereal_chant_Subclip 07_07.wav"),
    "chant5": ("src", CHANT + "chant5.wav", "chanting", "chant5.wav"),
    "moan": ("src", "voices/antumdeluge_undead_moans/x_undead-moans/undead-1.ogg", "undead", "undead-1.ogg"),
    "ghostmoan": ("src", "voices/qubodup_ghost_moans/x_qubodup-GhostMoans/qubodup-GhostMoans/wav/"
                         "qubodup-GhostMoan01.wav", "ghost_moans", "qubodup-GhostMoan01.wav"),
    "whoosh_rope3": ("src", BSB + "1795_whoosh_3/1795_whoosh_3.wav", "bigsoundbank",
                     "#1795 Whoosh 3 (https://bigsoundbank.com/UPLOAD/bwf-en/1795.wav)"),
    "whoosh_rope4": ("src", BSB + "1796_whoosh_4/1796_whoosh_4.wav", "bigsoundbank",
                     "#1796 Whoosh 4 (https://bigsoundbank.com/UPLOAD/bwf-en/1796.wav)"),
    "swish": ("src", BSB + "0572_whoosh_1/0572_whoosh_1.wav", "bigsoundbank",
              "#0572 Whoosh 1 (https://bigsoundbank.com/UPLOAD/bwf-en/0572.wav)"),
    "coins": ("kenney", "kenney_rpg-audio/Audio/handleCoins.ogg", "kenney_rpg", "handleCoins.ogg"),
}
for _w in ("light", "medium", "heavy"):
    for _i in range(5):
        SOURCES[f"glass_{_w}{_i}"] = ("kenney", f"{KI}impactGlass_{_w}_00{_i}.ogg", "kenney_impact",
                                      f"impactGlass_{_w}_00{_i}.ogg")
for _i in range(5):
    SOURCES[f"metal_light{_i}"] = ("kenney", f"{KI}impactMetal_light_00{_i}.ogg", "kenney_impact",
                                   f"impactMetal_light_00{_i}.ogg")

USED = []
_cache = {}


def path_of(key):
    root, rel, _, _ = SOURCES[key]
    return os.path.join(SRC_DIR if root == "src" else KENNEY_DIR, *rel.split("/"))


def load(key, t0=0.0, t1=None):
    """Mono float64 at 44.1 kHz (channels averaged), sliced to [t0, t1) seconds."""
    if key not in _cache:
        data = aud.Sound(path_of(key)).resample(RATE, 3).data().astype(np.float64)
        x = data.mean(axis=1)
        _cache[key] = x - x.mean()
        USED.append(key)
    x = _cache[key]
    return x[int(round(t0 * RATE)):None if t1 is None else int(round(t1 * RATE))].copy()


def verify_sources():
    """Refuse to build from anything outside the approved CC0 list / the two Kenney CC0 packs."""
    approved = {}
    if MANIFEST:
        with open(MANIFEST, encoding="utf-8") as f:
            for e in json.load(f):
                if e.get("group") in ("voice", "impact"):
                    approved[os.path.normcase(os.path.normpath(e["path"]))] = e
    for key in USED:
        root, rel, _, _ = SOURCES[key]
        p = path_of(key)
        if root == "kenney":
            pack = rel.split("/")[0]
            if pack not in ("kenney_impact-sounds", "kenney_rpg-audio"):
                raise SystemExit("not an allowed Kenney pack: " + p)
            with open(os.path.join(KENNEY_DIR, pack, "License.txt"), encoding="utf-8", errors="replace") as f:
                if "Creative Commons Zero" not in f.read():
                    raise SystemExit("Kenney pack is not CC0: " + pack)
        elif MANIFEST:
            e = approved.get(os.path.normcase(os.path.normpath(p)))
            if e is None or "CC0" not in e.get("license", ""):
                raise SystemExit("source not in the approved CC0 list: " + p)


# --- DSP helpers (numpy + aud) -------------------------------------------------------------------------
def varispeed(x, factor):
    """Tape-style speed change through aud's band-limited resampler: factor < 1 is lower and slower."""
    if abs(factor - 1.0) < 1e-9:
        return x.copy()
    snd = aud.Sound.buffer(np.ascontiguousarray(x, dtype=np.float32).reshape(-1, 1), RATE * factor)
    return snd.resample(RATE, 3).data()[:, 0].astype(np.float64)


def glide(x, rate):
    """Variable-speed playback; rate[i] is the speed at output sample i. Read from a 4x oversampled copy."""
    up = varispeed(x, 0.25)
    pos = np.concatenate([[0.0], np.cumsum(rate[:-1])]) * 4.0
    pos = pos[pos <= len(up) - 1]
    return np.interp(pos, np.arange(len(up)), up)


def biquad(x, kind, f0, q=0.7071, gain_db=0.0):
    """RBJ-cookbook biquad run by aud's IIR filter. kind: lp hp bp peak hs ls."""
    w = 2 * np.pi * f0 / RATE
    c, s = np.cos(w), np.sin(w)
    al = s / (2 * q)
    A = 10 ** (gain_db / 40)
    sq = 2 * np.sqrt(A) * al
    if kind == "lp":
        b, a = [(1 - c) / 2, 1 - c, (1 - c) / 2], [1 + al, -2 * c, 1 - al]
    elif kind == "hp":
        b, a = [(1 + c) / 2, -(1 + c), (1 + c) / 2], [1 + al, -2 * c, 1 - al]
    elif kind == "bp":
        b, a = [al, 0.0, -al], [1 + al, -2 * c, 1 - al]
    elif kind == "peak":
        b, a = [1 + al * A, -2 * c, 1 - al * A], [1 + al / A, -2 * c, 1 - al / A]
    elif kind == "hs":
        b = [A * ((A + 1) + (A - 1) * c + sq), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - sq)]
        a = [(A + 1) - (A - 1) * c + sq, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - sq]
    elif kind == "ls":
        b = [A * ((A + 1) - (A - 1) * c + sq), 2 * A * ((A - 1) - (A + 1) * c), A * ((A + 1) - (A - 1) * c - sq)]
        a = [(A + 1) + (A - 1) * c + sq, -2 * ((A - 1) + (A + 1) * c), (A + 1) + (A - 1) * c - sq]
    else:
        raise ValueError(kind)
    b = [float(v / a[0]) for v in b]
    a = [1.0, float(a[1] / a[0]), float(a[2] / a[0])]
    snd = aud.Sound.buffer(np.ascontiguousarray(x, dtype=np.float32).reshape(-1, 1), RATE).filter(b, a)
    return snd.data()[:, 0].astype(np.float64)


def spectral(x, gain_fn, n=1024, hop=256):
    """Time-varying EQ by STFT overlap-add; gain_fn(t_seconds, freqs) -> gains."""
    win = np.hanning(n + 1)[:-1]
    xp = np.concatenate([np.zeros(n), x, np.zeros(2 * n)])
    out, wsum = np.zeros(len(xp)), np.zeros(len(xp))
    f = np.fft.rfftfreq(n, 1.0 / RATE)
    for s0 in range(0, len(xp) - n + 1, hop):
        spec = np.fft.rfft(xp[s0:s0 + n] * win) * gain_fn((s0 + n // 2 - n) / RATE, f)
        out[s0:s0 + n] += np.fft.irfft(spec, n) * win
        wsum[s0:s0 + n] += win ** 2
    return (out / np.maximum(wsum, 1e-6))[n:n + len(x)]


def fftconv(a, b):
    n = len(a) + len(b) - 1
    size = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(a, size) * np.fft.rfft(b, size), size)[:n]


def reverb(x, rt60, seed, predelay=0.012, hp=180.0, damp=5000.0):
    """Wet-only diffuse tail: x convolved with a seeded decaying-noise impulse response (darker as it decays)."""
    rng = np.random.default_rng(seed)
    n = int(RATE * rt60)
    t = np.arange(n) / RATE
    noise = rng.standard_normal(n)
    ir = 0.5 * noise * np.exp(-6.91 * t / (0.5 * rt60)) + biquad(noise, "lp", damp, 0.6) * np.exp(-6.91 * t / rt60)
    k = int(0.003 * RATE)
    ir[:k] *= np.linspace(0.0, 1.0, k)
    ir = np.concatenate([np.zeros(int(predelay * RATE)), ir])
    ir /= np.sqrt(np.sum(ir ** 2))
    return fftconv(biquad(x, "hp", hp), ir)


def peak_norm(x, peak=1.0):
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def db(v):
    return 10 ** (v / 20.0)


def fades(x, fin=0.004, fout=0.06):
    x = x.copy()
    a, b = int(fin * RATE), int(fout * RATE)
    if a:
        x[:a] *= np.sin(np.linspace(0, np.pi / 2, a)) ** 2
    if b:
        x[-b:] *= np.cos(np.linspace(0, np.pi / 2, b)) ** 2
    return x


def env_peak_time(x, win=0.02):
    w = int(win * RATE)
    e = np.convolve(x ** 2, np.ones(w) / w, mode="same")
    return int(np.argmax(e)) / RATE


class Mix:
    """Fixed-length canvas; remembers named layers so their share of the mix can be reported."""

    def __init__(self, seconds):
        self.x = np.zeros(int(round(seconds * RATE)))
        self.layers = []

    def add(self, name, sig, at, gain_db=0.0):
        i = int(round(at * RATE))
        j0 = max(0, -i)
        i = max(0, i)
        n = min(len(sig) - j0, len(self.x) - i)
        if n <= 0:
            return
        part = np.zeros(len(self.x))
        part[i:i + n] = sig[j0:j0 + n] * db(gain_db)
        self.x += part
        self.layers.append((name, part))


def grains(rng, keys, length, density, amp, pitch, glen, offset=(0.0, 0.03), hp=1200.0):
    """Scatter short grains cut from real glass/metal hits (inhomogeneous Poisson, by thinning).
    density(t) grains/s and amp(t) are callables; pitch and glen are (lo, hi) ranges."""
    srcs = [load(k) for k in keys]
    out = np.zeros(int(length * RATE) + RATE)
    dmax = max(density(t) for t in np.arange(0.0, length, 0.002))
    t = 0.0
    while True:
        t += rng.exponential(1.0 / dmax)
        if t >= length:
            break
        u, si = rng.uniform(), int(rng.integers(len(srcs)))
        p = float(np.exp(rng.uniform(np.log(pitch[0]), np.log(pitch[1]))))
        gl, o, g = rng.uniform(*glen), rng.uniform(*offset), rng.uniform(0.35, 1.0)
        if u > density(t) / dmax:
            continue
        a = int(o * RATE)
        seg = varispeed(srcs[si][a:a + int(gl * p * RATE) + 256], p)[:int(gl * RATE)]
        m = len(seg)
        tt = np.arange(m) / RATE
        e = np.minimum(1.0, tt / 0.0015) * np.exp(-tt / (gl / 3.5))
        e[-min(64, m):] *= np.linspace(1.0, 0.0, min(64, m))
        i = int(t * RATE)
        out[i:i + m] += seg * e * amp(t) * g
    return biquad(out[:int(length * RATE)], "hp", hp)


def kweight(x):
    return biquad(biquad(x, "hs", 1500.0, 0.7071, 4.0), "hp", 38.0, 0.5)


def _block_loudness(x, hop_s):
    """Loudness of each 400 ms K-weighted block (BS.1770), hop_s apart."""
    y = kweight(x)
    w, hop = int(0.4 * RATE), int(hop_s * RATE)
    if len(y) < w:
        y = np.pad(y, (0, w - len(y)))
    c = np.concatenate([[0.0], np.cumsum(y ** 2)])
    s0 = np.arange(0, len(y) - w + 1, hop)
    return (c[s0 + w] - c[s0]) / w


def loudness_m_max(x):
    """Max momentary loudness (400 ms K-weighted windows, 10 ms hop), approx. LUFS."""
    return float(-0.691 + 10 * np.log10(np.max(_block_loudness(x, 0.01)) + 1e-20))


def loudness(x):
    """Gated integrated loudness, BS.1770-style (400 ms blocks, 75 % overlap, -70 LUFS absolute and -10 LU
    relative gates); the same measure build_sounds_v2.py levels the other Jehoash sounds with."""
    p = _block_loudness(x, 0.1)
    lb = -0.691 + 10 * np.log10(p + 1e-20)
    p = p[lb > -70.0]
    if not len(p):
        return -99.0
    rel = -0.691 + 10 * np.log10(np.mean(p)) - 10.0
    p = p[-0.691 + 10 * np.log10(p) > rel]
    return float(-0.691 + 10 * np.log10(np.mean(p)))


def limit(x, ceiling, look=0.0015, release=0.08):
    """Look-ahead peak limiter: the gain is <= ceiling/|x| at every sample, ramps down over `look`."""
    la = max(1, int(look * RATE))
    need = np.minimum(1.0, ceiling / np.maximum(np.abs(x), 1e-12))
    h = np.lib.stride_tricks.sliding_window_view(np.concatenate([need, np.ones(la)]), la + 1).min(axis=1)[:len(x)]
    r = np.exp(-1.0 / (release * RATE))
    g = np.empty_like(h)
    cur = 1.0
    for i, v in enumerate(h):
        cur = v if v < cur else v + (cur - v) * r
        g[i] = cur
    g = np.convolve(np.concatenate([np.ones(la), g]), np.ones(la + 1) / (la + 1), mode="valid")[:len(x)]
    return np.clip(x * g, -ceiling, ceiling)


GAIN_REDUCTION = {}  # name: limiter gain reduction in dB (reported in the WROTE lines)


def finish(name, x, target_lufs, ceiling_db=-1.5, max_gr_db=3.0, fin=0.004, fout=0.06, release=0.08):
    """DC/rumble cut, fades, gain toward a gated loudness target (BS.1770) with at most max_gr_db of peak
    limiting, look-ahead limit at ceiling_db. Limiting lowers the loudness a little, so the gain is refined."""
    x = fades(biquad(x, "hp", 35.0), fin, fout)
    ceiling = db(ceiling_db)
    cap = ceiling / np.max(np.abs(x)) * db(max_gr_db)
    gain = min(db(target_lufs - loudness(x)), cap)
    for _ in range(6):
        y = fades(limit(x * gain, ceiling, release=release), fin, fout)
        err = target_lufs - loudness(y)
        nxt = min(gain * db(err), cap)
        if abs(err) < 0.05 or abs(nxt / gain - 1.0) < 1e-4:
            break
        gain = nxt
    GAIN_REDUCTION[name] = round(max(0.0, 20 * np.log10(gain * np.max(np.abs(x)) / ceiling)), 2)
    return y, gain


# --- Ogg writing (pinned stream serial = byte-identical rebuilds) ---------------------------------------
_CRC_TABLE = []


def _ogg_crc(data):
    if not _CRC_TABLE:
        for i in range(256):
            r = i << 24
            for _ in range(8):
                r = ((r << 1) ^ 0x04C11DB7) if r & 0x80000000 else (r << 1)
            _CRC_TABLE.append(r & 0xFFFFFFFF)
    crc = 0
    for byte in data:
        crc = ((crc << 8) & 0xFFFFFFFF) ^ _CRC_TABLE[((crc >> 24) ^ byte) & 0xFF]
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
    """Decoded samples, cut/padded to the exact length in the last Ogg granule position (what stb_vorbis
    plays); aud's decoder can return a few hundred samples more or less than that."""
    with open(path, "rb") as f:
        data = f.read()
    i = data.rfind(b"OggS")
    n = int.from_bytes(data[i + 6:i + 14], "little")
    y = aud.Sound(path).resample(RATE, 3).data()[:, 0].astype(np.float64)
    return np.pad(y, (0, max(0, n - len(y))))[:n]


def measure(y):
    tp = varispeed(y, 0.25)  # 4x oversampled for a true-peak estimate
    return {"duration_s": round(len(y) / RATE, 3),
            "samples": len(y),
            "peak_dbfs": round(20 * np.log10(np.max(np.abs(y))), 2),
            "true_peak_dbtp": round(20 * np.log10(np.max(np.abs(tp))), 2),
            "rms_dbfs": round(10 * np.log10(np.mean(y ** 2)), 2),
            "lufs": round(loudness(y), 2),
            "lufs_m_max": round(loudness_m_max(y), 2)}


def write_ogg(name, x, tmp):
    """Encode (Vorbis 128 kb/s), pin the serial, decode and re-check: the decoded peak must be <= -1 dBFS."""
    part = os.path.join(tmp, name + ".ogg")
    for _ in range(6):
        aud.Sound.buffer(np.ascontiguousarray(x, dtype=np.float32).reshape(-1, 1), RATE).write(
            part, RATE, aud.CHANNELS_MONO, aud.FORMAT_FLOAT32, aud.CONTAINER_OGG, aud.CODEC_VORBIS, 128000, 0)
        pin_ogg_serial(part, zlib.crc32(name.encode("ascii")) & 0x7FFFFFFF)
        y = decode(part)
        pk = 20 * np.log10(np.max(np.abs(y)))
        if pk <= -1.0:
            break
        x = x * db(-1.1 - pk)
    else:
        raise SystemExit(name + ": could not keep the decoded peak under -1 dBFS")
    os.makedirs(OUT, exist_ok=True)
    dst = os.path.join(OUT, name + ".ogg")
    shutil.copyfile(part, dst)
    return dst, y


def report_layers(name, mix, gain):
    """--verbose: each layer's share of the mix; --stems <dir>: each layer as a float WAV at its final gain."""
    total = np.mean((mix.x * gain) ** 2) + 1e-20
    for lname, part in mix.layers:
        e = np.mean((part * gain) ** 2)
        if e > 0 and VERBOSE:
            print(f"   {name:16s} {lname:14s} {10 * np.log10(e):7.1f} dBFS rms  ({100 * e / total:5.1f}% of mix energy)")
        if STEMS:
            os.makedirs(STEMS, exist_ok=True)
            aud.Sound.buffer(np.ascontiguousarray(part * gain, dtype=np.float32).reshape(-1, 1), RATE).write(
                os.path.join(STEMS, f"{name}__{lname.replace(' ', '_')}.wav"), RATE, aud.CHANNELS_MONO,
                aud.FORMAT_FLOAT32, aud.CONTAINER_WAV, aud.CODEC_PCM, 0, 0)


# Loudness targets, gated BS.1770 LUFS. build_sounds_v2.py levels the other Jehoash sounds to -12...-15 (laser
# charge -15, hurt -14); the conjuring sits with the charge, the pop a little under the hurts (it is a glass
# transient, so it is held back by the limiter cap rather than squashed to the target).
ILLUSIONS_LUFS = -15.0
POP_LUFS = -17.5
POP_MAX_GR_DB = 7.0


# --- illusions: Jehoash conjures three decoys --------------------------------------------------------------
ILLUSIONS = {
    # hit = the "appearance" moment where the swell ends and the choir, whoosh and shimmer start. The game
    # plays this sound as the decoys spawn (JehoashEntity.summonIllusions), so the swell is kept to ~0.3 s.
    # breath = (start s, end s) of the ghost breath's exhale decay, played reversed at breath_rate.
    # whisper = (key, start s, end s): one whispered syllable from its loudest point into its decay; reversed,
    # it grows into the hit and stops there. The murmur then plays the same syllable forwards (a mirror image).
    1: dict(seed=101, length=1.34, hit=0.34,
            breath=(2.85, 3.14), breath_rate=0.85,
            whisper=("whisper_e08", 1.645, 1.855), whisper_rate=0.92, whisper_swell=0.34,
            whoosh="whoosh_rope4",
            # undead moan at three speeds = a diminished chord (root, minor third, tritone); each copy enters
            # a moment after the last. (speed, entry delay s, gain dB, source start s)
            voice="moan", choir=[(0.707, 0.000, 0.0, 0.30), (0.841, 0.045, -1.5, 0.52), (1.000, 0.090, -3.0, 0.74)],
            fall_cents=-120.0, choir_tau=0.40, choir_rt60=1.2, shimmer_db=(-4.0, -8.0),
            murmur=("whisper_e08", 1.60, 2.70),
            glitter=["glass_light0", "glass_light1", "glass_light2", "glass_light3", "glass_light4",
                     "glass_medium0", "glass_medium1", "glass_medium2"],
            glitter_pitch=(1.3, 2.6), bed=True),
    2: dict(seed=102, length=1.20, hit=0.30,
            breath=(2.80, 3.15), breath_rate=1.15,
            whisper=("whisper_c8", 1.175, 1.385), whisper_rate=1.06, whisper_swell=0.30,
            whoosh="whoosh_rope3",
            # a sung chant at three speeds, each copy on a different syllable: root, fourth and tritone,
            # a close beating cluster pitched above variant 1. It dies away faster than variant 1's moan
            # (choir_tau) so one ~480 Hz vowel harmonic is not left humming on its own in the fade-out.
            voice="chant5", choir=[(1.000, 0.000, 0.0, 0.87), (1.335, 0.035, -2.0, 0.50), (1.414, 0.070, -3.0, 1.33)],
            fall_cents=-70.0, choir_tau=0.32, choir_rt60=1.0, shimmer_db=(-7.0, -10.0),
            murmur=("whisper_c8", 1.15, 2.20),
            glitter=["metal_light0", "metal_light1", "metal_light2", "metal_light3", "coins",
                     "glass_light1", "glass_light3"],
            glitter_pitch=(1.1, 2.0), bed=False),
}


def drift(rng, n, cents, step=0.07):
    """Slow random pitch wander (smoothed, seeded) in cents, n samples long."""
    k = int(n / (step * RATE)) + 3
    pts = rng.standard_normal(k) * cents
    c = np.interp(np.arange(n) / (step * RATE), np.arange(k), pts)
    w = int(step * RATE)
    return np.convolve(np.pad(c, (w, w), mode="edge"), np.ones(w) / w, mode="same")[w:w + n]


def illusions(v):
    P = ILLUSIONS[v]
    rng = np.random.default_rng(P["seed"])
    L, hit = P["length"], P["hit"]
    mix = Mix(L)

    # 1. reversed ghost breath: the slowed exhale's decay played backwards is a rising, inhaling swell that
    #    snaps off at the hit. It starts audibly (not from silence) so it lines up with the spawn.
    b0, b1 = P["breath"]
    sw = varispeed(load("breath", b0, b1), P["breath_rate"])[::-1]
    sw = biquad(biquad(sw, "hp", 200.0), "lp", 9000.0)
    u = np.linspace(0.0, 1.0, len(sw))
    sw = peak_norm(sw * (0.25 + 0.75 * u ** 1.5))
    sw[-int(0.012 * RATE):] *= np.linspace(1.0, 0.0, int(0.012 * RATE))
    mix.add("breath swell", sw, hit - len(sw) / RATE, -5.0)

    # 2. reversed whisper with reverse reverb: the reverb tail becomes a swell that leads into the whispered
    #    syllable, which plays backwards, grows (crescendo) and stops at its loudest point on the hit
    key, t0, t1 = P["whisper"]
    w = biquad(varispeed(load(key, t0, t1), P["whisper_rate"]), "hp", 300.0)
    w = fades(w, 0.004, 0.03)
    wet = reverb(w, 1.1, P["seed"] + 7)
    full = np.zeros(len(wet))
    full[:len(w)] += w
    full += 0.9 * wet
    rev = full[::-1][-int(P["whisper_swell"] * RATE):]
    u = np.linspace(0.0, 1.0, len(rev))
    rev = peak_norm(rev * u ** 1.5)
    rev[-int(0.015 * RATE):] *= np.linspace(1.0, 0.0, int(0.015 * RATE))
    mix.add("whisper swell", rev, hit + 0.01 - len(rev) / RATE, -2.0)

    # 3. soft airy whoosh as they appear: a rope whoosh plus two quieter speed-shifted copies fanning out
    wh = biquad(biquad(load(P["whoosh"]), "hp", 180.0), "lp", 7000.0)
    wh = peak_norm(fades(wh, 0.005, 0.05))
    pk = env_peak_time(wh)
    for i, (f, dt, g) in enumerate([(1.0, 0.0, -5.0), (1.18, 0.07, -11.0), (0.86, 0.13, -12.0)]):
        c = varispeed(wh, f)
        mix.add(f"whoosh {i + 1}", c, hit + 0.02 + dt - pk / f, g)

    # 4. choir: one real voice at three speeds, each copy wandering slightly in pitch and sinking at the end
    choir = np.zeros(len(mix.x))
    for f, dt, g, src in P["choir"]:
        need = int((L - hit - dt) * RATE)
        u = np.arange(need) / need
        cents = drift(rng, need, 9.0) + P["fall_cents"] * np.clip((u - 0.35) / 0.65, 0.0, 1.0) ** 2
        rate = f * 2.0 ** (cents / 1200.0)
        seg = glide(load(P["voice"], src, src + rate.sum() / RATE + 0.05), rate)[:need]
        t = np.arange(len(seg)) / RATE
        e = np.sin(np.minimum(1.0, t / 0.09) * np.pi / 2) ** 2 * np.exp(-t / P["choir_tau"])
        i = int((hit + dt) * RATE)
        choir[i:i + len(seg)] += peak_norm(seg) * e * db(g)
    choir = biquad(biquad(biquad(choir, "hp", 110.0), "peak", 380.0, 1.0, -3.0), "hs", 4500.0, 0.7071, 2.0)
    choir = peak_norm(choir)
    mix.add("choir", choir, 0.0, -1.0)
    mix.add("choir verb", reverb(choir, P["choir_rt60"], P["seed"] + 11), 0.0, -10.0)

    # 5. whispering under the choir, as if the copies murmur while they settle
    key, t0, t1 = P["murmur"]
    mu = biquad(biquad(load(key, t0, t1), "hp", 1000.0), "lp", 9000.0)
    t = np.arange(len(mu)) / RATE
    mu = peak_norm(mu * np.sin(np.minimum(1.0, t / 0.12) * np.pi / 2) ** 2 * np.exp(-t / 0.45))
    mix.add("murmur", mu, hit, -10.0)

    # 6. mirror shimmer: grains of real glass / metal hits, densest as the copies appear, in a long wash
    def density(t):
        if t < hit - 0.22:
            return 0.0
        if t < hit:
            return 45.0 * ((t - hit + 0.22) / 0.22) ** 1.5
        return 8.0 + 37.0 * np.exp(-(t - hit) / 0.30)

    def amp(t):
        return min(1.0, max(0.0, (t - hit + 0.22) / 0.22)) * (0.35 + 0.65 * np.exp(-max(0.0, t - hit) / 0.45))

    gl = grains(rng, P["glitter"], L, density, amp, P["glitter_pitch"], (0.03, 0.09))
    gl = peak_norm(gl)
    mix.add("shimmer", gl, 0.0, P["shimmer_db"][0])
    mix.add("shimmer wash", peak_norm(reverb(gl, 1.4, P["seed"] + 13, hp=900.0)), 0.0, P["shimmer_db"][1])

    # 7. variant 1 only: a slowed ghost moan underneath for weight
    if P["bed"]:
        bed = biquad(varispeed(load("ghostmoan", 0.4, 1.9), 0.9), "lp", 900.0)
        t = np.arange(len(bed)) / RATE
        bed = peak_norm(bed * np.sin(np.minimum(1.0, t / 0.15) * np.pi / 2) ** 2 * np.exp(-t / 0.5))
        mix.add("ghost bed", bed, hit - 0.04, -13.0)

    x, gain = finish(f"illusions_{v}", mix.x, target_lufs=ILLUSIONS_LUFS, fout=0.12)
    if VERBOSE or STEMS:
        report_layers(f"illusions_{v}", mix, gain)
    return x


# --- illusion_pop: a decoy is hit and bursts --------------------------------------------------------------
POPS = {  # the game plays these at pitch 1.2 (JehoashIllusionEntity.pop), i.e. ~17 % shorter and brighter
    1: dict(seed=201, length=0.70,
            glass=[("glass_heavy1", 1.00, 0.000, 0.0), ("glass_light2", 1.25, 0.003, -5.0)],
            debris=["glass_light0", "glass_light3", "glass_light4", "glass_medium1"],
            puff=(0.05, 0.30), gasp=("whisper_c8", 1.15, 1.47, 1.00), gasp_glide=(1.00, 0.80), gasp_db=-5.0,
            snap_db=2.0),
    2: dict(seed=202, length=0.78,
            # second hit: heavy0 is a dense crash whose only rings are faint ones above 7 kHz. The medium hits
            # ring for up to ~0.5 s on one pitch, which left a steady ~1.1 kHz "ting" in this pop.
            glass=[("glass_heavy4", 0.92, 0.000, 0.0), ("glass_heavy0", 1.10, 0.004, -6.0)],
            debris=["glass_light1", "glass_light2", "glass_medium0", "glass_medium3"],
            puff=(1.12, 1.37), gasp=("whisper_c7", 0.20, 0.52, 0.94), gasp_glide=(1.00, 0.76), gasp_db=-6.0,
            snap_db=0.0),
    3: dict(seed=203, length=0.62,
            glass=[("glass_light4", 0.95, 0.000, 0.0), ("glass_heavy1", 1.20, 0.002, -4.0),
                   ("glass_light1", 1.40, 0.006, -7.0)],
            debris=["glass_light0", "glass_light2", "glass_light3", "glass_medium2"],
            puff=(2.30, 2.55), gasp=("whisper_e07", 0.49, 0.78, 1.08), gasp_glide=(1.04, 0.84), gasp_db=-3.5,
            snap_db=0.0),
}


def pop(v):
    P = POPS[v]
    rng = np.random.default_rng(P["seed"])
    L = P["length"]
    mix = Mix(L)

    # 1. glass shatter: real glass hits layered at different speeds so their rings do not line up
    glass = np.zeros(len(mix.x))
    for key, f, at, g in P["glass"]:
        s = varispeed(load(key), f)
        t = np.arange(len(s)) / RATE
        s = peak_norm(fades(s * np.exp(-np.maximum(0.0, t - 0.03) / 0.10), 0.0, 0.02)) * db(g)
        i = int(at * RATE)
        n = min(len(s), len(glass) - i)
        glass[i:i + n] += s[:n]
    glass = biquad(glass, "hp", 150.0)
    # every Kenney glass hit is a ~1 ms spike over a quiet ring; shave the spike so the glass is at most
    # 8 dB above its loudest 20 ms (a sharp crack still), which keeps the three pops level-matched
    n20 = int(0.02 * RATE)
    rms20 = np.sqrt(np.max(np.convolve(glass ** 2, np.ones(n20) / n20, mode="same")))
    glass = peak_norm(limit(glass, rms20 * db(8.0), look=0.001, release=0.015))
    mix.add("glass", glass, 0.0, 0.0)

    # 2. debris: a cascade of tiny glass fragments right after the hit, then ringing tinkles as they settle
    burst = grains(rng, P["debris"], 0.26, lambda t: 0.0 if t < 0.006 else 300.0 * np.exp(-t / 0.07),
                   lambda t: np.exp(-t / 0.10), (1.6, 3.4), (0.012, 0.035), offset=(0.0, 0.015), hp=1800.0)
    tinkle = grains(rng, P["debris"], 0.45, lambda t: 0.0 if t < 0.04 else 26.0,
                    lambda t: np.exp(-t / 0.18), (1.4, 2.8), (0.03, 0.07), offset=(0.0, 0.02), hp=1500.0)
    debris = np.zeros(len(tinkle))
    debris[:len(burst)] += peak_norm(burst)
    debris += db(-4.0) * peak_norm(tinkle)
    debris = peak_norm(debris)
    mix.add("debris", debris, 0.0, -4.0)

    # 3. small air puff: the loudest 120 ms of one sword swish
    t0, t1 = P["puff"]
    sws = load("swish", t0, t1)
    pk = env_peak_time(sws, 0.01)
    puff = sws[max(0, int((pk - 0.012) * RATE)):int((pk + 0.11) * RATE)]
    t = np.arange(len(puff)) / RATE
    puff = biquad(biquad(puff, "hp", 400.0), "lp", 6000.0) * np.minimum(1.0, t / 0.002) * np.exp(-t / 0.04)
    mix.add("air puff", peak_norm(fades(puff, 0.0, 0.01)), 0.0, -8.0)

    # 4. ghostly gasp sucked away: a whispered syllable with a sharp onset (plus the ghost breath's decay)
    #    reversed, so it swells and snaps off; it slows and dulls at the end, as if pulled back into nothing
    key, a, b, speed = P["gasp"]
    g = peak_norm(fades(biquad(varispeed(load(key, a, b), speed), "hp", 280.0), 0.004, 0.02))
    br = peak_norm(biquad(varispeed(load("breath", 2.55, 3.40), 2.7), "hp", 300.0))
    nb = min(len(g), len(br))
    g[:nb] += db(-8.0) * br[:nb]
    g = g[::-1]
    r0, r1 = P["gasp_glide"]
    u = np.linspace(0.0, 1.0, int(len(g) / (r0 + 0.4 * (r1 - r0))))
    g = glide(g, r0 + (r1 - r0) * u ** 1.5)
    dur = len(g) / RATE
    g = spectral(g, lambda t, f: 1.0 / np.sqrt(
        1.0 + (f / (11000.0 * (3000.0 / 11000.0) ** np.clip((t / dur - 0.6) / 0.4, 0.0, 1.0))) ** 4))
    u = np.linspace(0.0, 1.0, len(g))
    g = g * u ** 2.0
    # a whisper that starts on a plosive ends, reversed, on a loud snap; hold that snap (20 ms RMS) to
    # snap_db over the gasp's level just before it, so the pop stays one burst, glass first, not two hits
    n20, n5, tail = int(0.02 * RATE), int(0.005 * RATE), int(0.06 * RATE)
    ref = np.sqrt(np.mean(g[-tail - int(0.08 * RATE):-tail] ** 2))
    e = np.sqrt(np.convolve(g ** 2, np.ones(n20) / n20, mode="same")) + 1e-12
    gain = np.ones(len(g))
    gain[-tail:] = np.minimum(1.0, ref * db(P["snap_db"]) / e[-tail:])
    gain = np.convolve(np.pad(gain, (n5, n5), mode="edge"), np.ones(n5) / n5, mode="same")[n5:-n5]
    g = peak_norm(g * gain)
    g[-int(0.012 * RATE):] *= np.linspace(1.0, 0.0, int(0.012 * RATE))
    mix.add("gasp", g, 0.012, P["gasp_db"])

    # 5. a short room around the burst
    room_in = glass.copy()
    room_in[:len(debris)] += db(-6.0) * debris[:len(room_in)]
    mix.add("room", peak_norm(reverb(room_in, 0.55, P["seed"] + 5, hp=300.0)), 0.0, -13.0)

    x, gain = finish(f"illusion_pop_{v}", mix.x, target_lufs=POP_LUFS, max_gr_db=POP_MAX_GR_DB, fout=0.06,
                     release=0.04)
    if VERBOSE or STEMS:
        report_layers(f"illusion_pop_{v}", mix, gain)
    return x


# --- main ---------------------------------------------------------------------------------------------------
def main():
    jobs = [(f"illusions_{v}", lambda v=v: illusions(v)) for v in ILLUSIONS]
    jobs += [(f"illusion_pop_{v}", lambda v=v: pop(v)) for v in POPS]
    if ONLY:
        jobs = [j for j in jobs if j[0] in ONLY.split(",")]
    tmp = tempfile.mkdtemp(prefix="illusion_sounds_")
    results = []
    try:
        built = [(name, fn()) for name, fn in jobs]
        verify_sources()
        for name, x in built:
            dst, y = write_ogg(name, x, tmp)
            m = measure(y)
            m["limiter_gr_db"] = GAIN_REDUCTION.get(name, 0.0)
            m["file"] = dst.replace("\\", "/")
            results.append(m)
            print("WROTE", json.dumps(m))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    if CREDITS:
        write_credits(CREDITS)
    return results


def write_credits(path):
    by_pack = {}
    for key in USED:
        _, _, pack, detail = SOURCES[key]
        by_pack.setdefault(pack, []).append(detail)
    lines = ["Jehoash illusion sounds (sounds/joash/illusions_1-2.ogg, sounds/joash/illusion_pop_1-3.ogg) are mixed",
             "from these recordings by tools/audio/build_illusion_sounds.py (no synthesized tones):"]
    for pack in PACKS:
        if pack in by_pack:
            title, author, lic, url = PACKS[pack]
            lines.append(f'  "{title}" by {author}, {lic} - {url}')
            lines.append("      used: " + ", ".join(sorted(set(by_pack[pack]))))
    lines.append("CC0 1.0 = public domain dedication, https://creativecommons.org/publicdomain/zero/1.0/")
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print("CREDITS", path)


main()
