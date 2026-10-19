"""Encode the Disco music for Minecraft and measure its tempo for the beat-synced idle animation.

    blender --background --factory-startup --python tools/audio/build_music.py -- <track.mp3|wav|ogg> <repo_dir>

Writes assets/cabinmod/sounds/music/disco.ogg (mono so it plays positionally from the booth, streamed)
and prints JSON with bpm, first-beat offset and length; the same goes to design/disco_music.json.
"""
import json
import os
import sys

import aud
import numpy as np

SRC, REPO = sys.argv[sys.argv.index("--") + 1:][:2]
RATE = 44100

snd = aud.Sound(SRC).resample(RATE, True)
stereo = snd.data().astype(np.float64)
mono = stereo.mean(axis=1)
length = len(mono) / RATE

# onset strength: half-wave rectified spectral flux on ~11.6 ms hops
hop, win = 512, 2048
frames = 1 + (len(mono) - win) // hop
window = np.hanning(win)
prev = None
flux = np.zeros(frames)
for i in range(frames):
    mag = np.abs(np.fft.rfft(mono[i * hop:i * hop + win] * window))
    mag = np.log1p(mag)
    if prev is not None:
        flux[i] = np.maximum(mag - prev, 0).sum()
    prev = mag
flux -= flux.mean()
fps = RATE / hop

# tempo: autocorrelation peak between 70 and 180 BPM, folded so phonk lands in its usual range
ac = np.correlate(flux, flux, mode="full")[frames - 1:]
lags = np.arange(len(ac))
bpm_of = lambda lag: 60 * fps / lag
lo, hi = int(60 * fps / 180), int(60 * fps / 70)
best = lo + int(np.argmax(ac[lo:hi]))
# refine with parabolic interpolation
a, b, c = ac[best - 1], ac[best], ac[best + 1]
best_f = best + 0.5 * (a - c) / (a - 2 * b + c)
bpm = bpm_of(best_f)

# beat phase: the offset whose beat grid collects the most onset energy
period = 60 / bpm * fps
scores = [flux[np.arange(o, frames, period).astype(int)].sum() for o in np.arange(0, period, 0.25)]
offset = float(np.arange(0, period, 0.25)[int(np.argmax(scores))] / fps)

out = os.path.join(REPO, "src/main/resources/assets/cabinmod/sounds/music/disco.ogg")
os.makedirs(os.path.dirname(out), exist_ok=True)
m = mono / max(np.max(np.abs(mono)), 1e-9) * 0.9
aud.Sound.buffer(m.astype(np.float32).reshape(-1, 1), RATE).write(
    out, RATE, aud.CHANNELS_MONO, aud.FORMAT_FLOAT32, aud.CONTAINER_OGG, aud.CODEC_VORBIS, 128000, 0)

info = {"source": os.path.basename(SRC), "bpm": round(bpm, 2), "first_beat_seconds": round(offset, 4),
        "length_seconds": round(length, 2), "beats": int((length - offset) * bpm / 60)}
with open(os.path.join(REPO, "design/disco_music.json"), "w", encoding="utf-8", newline="\n") as f:
    json.dump(info, f, indent=2)
print("MUSIC", json.dumps(info), "ogg bytes", os.path.getsize(out))
