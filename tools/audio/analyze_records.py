"""Measure each bundled record's beat grid and energy envelope for the DJ animation.
Run: python tools/audio/analyze_records.py (numpy, scipy, soundfile).
The audio is never modified. The output is loaded by RecordDance on the client.
"""
import json
from pathlib import Path
import numpy as np
import soundfile as sf
from scipy.signal import resample_poly, correlate, find_peaks

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/cabinmod/animations/record_dances.json'
profiles = {}
for name in ('mo2', 'brg', 'hyg', 'bla', 'dna', 'sos'):
    audio, rate = sf.read(ROOT / f'src/main/resources/assets/cabinmod/sounds/{name}_disc.ogg', always_2d=True)
    duration = len(audio) / rate
    mono = resample_poly(audio.mean(axis=1), 11025, rate)
    hop, window = 256, 1024
    frames = np.lib.stride_tricks.sliding_window_view(mono, window)[::hop]
    spectrum = np.log1p(np.abs(np.fft.rfft(frames * np.hanning(window), axis=1)))
    flux = np.maximum(np.diff(spectrum, axis=0), 0).sum(axis=1)
    fps = 11025 / hop
    centered = flux - flux.mean()
    ac = correlate(centered, centered, mode='full', method='fft')[len(flux)-1:]
    lo, hi = int(60*fps/170), int(60*fps/75)
    lag = lo + np.argmax(ac[lo:hi])
    a,b,c = ac[lag-1:lag+2]
    period = lag + .5*(a-c)/(a-2*b+c)
    bpm = 60*fps/period
    offsets = np.arange(0, period, .25)
    offset = offsets[np.argmax([flux[np.arange(o,len(flux),period).astype(int)].sum() for o in offsets])]
    # Snap each predicted beat to a nearby onset. This follows tempo drift instead of assuming one fixed BPM.
    beats = []
    for predicted in np.arange(offset, len(flux), period):
        left, right = max(0,int(predicted-period*.2)), min(len(flux),int(predicted+period*.2)+1)
        beat = (left + np.argmax(flux[left:right]) + 1) / fps
        if not beats or beat > beats[-1] + .18:
            beats.append(round(beat,4))
    rms = np.sqrt(np.mean(frames**2, axis=1))
    energy = np.interp(np.arange(0,duration,.1), np.arange(len(rms))/fps, rms)
    energy = np.clip(energy / max(np.percentile(energy,90),1e-6),0,1)
    profiles[name+'_disc'] = dict(bpm=round(bpm,3), seconds=round(duration,4), beats=beats,
                                  energy=[round(float(x),3) for x in energy])
    print(name, 'BPM', round(bpm,2), 'seconds', round(duration,2), 'beats',len(beats))
OUT.write_text(json.dumps(profiles,separators=(',',':'))+'\n')
