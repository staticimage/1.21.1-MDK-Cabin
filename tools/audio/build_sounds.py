"""Build Cabin Mod's custom sounds from CC0 Kenney packs (+ a synthesized record scratch).

Run with Blender's Python (uses its audaspace `aud` module for Ogg Vorbis, no ffmpeg needed):
    blender --background --factory-startup --python tools/audio/build_sounds.py -- <kenney_dir> <repo_dir>
<kenney_dir> holds the extracted packs: kenney_sci-fi-sounds, kenney_impact-sounds,
kenney_digital-audio, kenney_rpg-audio (all CC0, https://kenney.nl). Positional sounds are mono.
"""
import glob
import os
import sys

import aud
import numpy as np

KENNEY, REPO = sys.argv[sys.argv.index("--") + 1:][:2]
OUT = os.path.join(REPO, "src/main/resources/assets/cabinmod/sounds")
RATE = 44100
rng = np.random.default_rng(5)


def load(name):
    hits = glob.glob(os.path.join(KENNEY, "**", name + ".ogg"), recursive=True)
    if not hits:
        raise SystemExit("missing " + name)
    s = aud.Sound(hits[0]).resample(RATE, True).rechannel(1)
    return s.data()[:, 0].astype(np.float64)


def varispeed(x, factor):
    """Tape-style speed change: factor < 1 lower and slower."""
    n = int(len(x) / factor)
    return np.interp(np.arange(n) * factor, np.arange(len(x)), x)


def mix(*layers):
    """layers: (signal, start_seconds, gain)"""
    end = max(int(start * RATE) + len(sig) for sig, start, _ in layers)
    out = np.zeros(end)
    for sig, start, gain in layers:
        i = int(start * RATE)
        out[i:i + len(sig)] += sig * gain
    return out


def finish(x, peak=0.9, fade_ms=8, drive=0.0):
    if drive:
        x = np.tanh(x * (1 + drive)) / np.tanh(1 + drive)
    f = int(RATE * fade_ms / 1000)
    x[-f:] *= np.linspace(1, 0, f)
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def write(rel, x):
    path = os.path.join(OUT, rel + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    snd = aud.Sound.buffer(x.astype(np.float32).reshape(-1, 1), RATE)
    snd.write(path, RATE, aud.CHANNELS_MONO, aud.FORMAT_FLOAT32, aud.CONTAINER_OGG, aud.CODEC_VORBIS, 96000, 0)
    print("wrote", rel, f"{len(x) / RATE:.2f}s")


def scratch(seconds=0.9):
    """Record scratch: a synth stab read back and forth by a 'hand' moving the platter."""
    t = np.arange(int(RATE * 0.6)) / RATE
    stab = sum(np.sin(2 * np.pi * f * t) for f in (110, 165, 220, 277)) * np.exp(-t * 3)
    stab += rng.normal(0, 0.05, len(t))                                   # vinyl hiss
    n = int(RATE * seconds)
    u = np.arange(n) / n
    # playhead: forward, back, forward-fast, back - the classic "wikka"
    pos = 0.25 * np.sin(2 * np.pi * 2 * u) + 0.15 * np.sin(2 * np.pi * 5 * u) + 0.3
    out = np.interp(pos * len(stab), np.arange(len(stab)), stab)
    speed = np.abs(np.gradient(pos))
    return out * np.clip(speed / speed.max() * 1.6, 0, 1)                 # stopped platter = silence


# --- Jehoash ---------------------------------------------------------------------------
for i in range(3):
    write(f"joash/laser_{i + 1}", finish(mix(
        (varispeed(load(f"laserLarge_00{i}"), 0.85), 0, 1.0),
        (load(f"phaserUp{i + 1}"), 0.02, 0.55))))
    write(f"joash/teleport_{i + 1}", finish(load(f"phaseJump{i + 1}")))
    write(f"joash/illusion_pop_{i + 1}", finish(mix(
        (varispeed(load(f"impactGlass_light_00{i}"), 1.1), 0, 1.0),
        (load(f"zap{1 + i % 2}"), 0, 0.3))))
    write(f"joash/hurt_{i + 1}", finish(mix(
        (load(f"impactPunch_medium_00{i}"), 0, 1.0),
        (varispeed(load("highDown"), 0.8 + 0.1 * i), 0.02, 0.35))))
    write(f"joash/ambient_{i + 1}", finish(varispeed(load(f"computerNoise_00{i}"), 0.7)[:int(RATE * 2.5)], peak=0.55, fade_ms=400))
for i in range(2):
    write(f"joash/illusions_{i + 1}", finish(mix(
        (load(f"forceField_00{i}"), 0, 1.0),
        (load("zapThreeToneUp"), 0.1, 0.5))))
write("joash/welcome", finish(mix(
    (load("lowFrequency_explosion_000"), 0, 1.0),
    (load("zapThreeToneUp"), 0.15, 0.8),
    (varispeed(load("powerUp1"), 0.8), 0.25, 0.5))))
write("joash/death", finish(mix(
    (load("lowFrequency_explosion_001"), 0, 1.0),
    (varispeed(load("lowDown"), 0.6), 0.1, 0.9),
    (load("explosionCrunch_002"), 0.35, 0.6)), drive=0.5))

# --- DJ booth ---------------------------------------------------------------------------
for i in range(2):
    write(f"dj_booth/scratch_{i + 1}", finish(scratch(0.8 + 0.2 * i)))

# --- Life of the Party jumpscare: loud, distorted, hits on frame one -------------------
laser = varispeed(load("laserLarge_004"), 0.5)
write("life_of_the_party_jumpscare", finish(mix(
    (load("impactBell_heavy_000"), 0, 1.0),
    (load("explosionCrunch_000"), 0, 0.9),
    (laser, 0, 0.7),
    (laser[::-1], 0.05, 0.4)), peak=0.97, drive=1.5))
