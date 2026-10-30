"""Package current Jehoash outfit captures into videos and a local review page.
Requires Pillow and ffmpeg on PATH (or the imageio-ffmpeg Python package).
Run after the front/side/back capture runs: python tools/visual_smoke/pack_jehoash_review.py
"""
from pathlib import Path
import csv
import shutil
import subprocess
import html
import hashlib

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/brg-review'
CAPTURES=ROOT/'build/rave-preview/brg-review'


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    ffmpeg=shutil.which('ffmpeg')
    if not ffmpeg:
        import imageio_ffmpeg
        ffmpeg=imageio_ffmpeg.get_ffmpeg_exe()
    videos=[]
    body_hash=hashlib.sha256((ROOT/'src/main/resources/assets/cabinmod/textures/entity/joash/jehoash_phase_two.png').read_bytes()).hexdigest()
    for label in ['front-60','three-quarter-60','side-60','back-30']:
        runs=sorted(run for run in CAPTURES.glob(label+'-*')
                    if (run/'frames.csv').exists() and (run/'body.sha256').exists()
                    and (run/'body.sha256').read_text().strip()==body_hash)
        runs=[run for run in runs if all((run/'screenshots'/row['file']).is_file()
                    and (run/'screenshots'/row['file']).stat().st_size>8
                    for row in csv.DictReader((run/'frames.csv').open()))]
        if not runs: continue
        run=runs[-1]
        if not (run/'frames.csv').exists(): continue
        rows=list(csv.DictReader((run/'frames.csv').open()))
        for sequence in ['reveal','teleport','walk','melee','ranged','death']:
            frames=[row for row in rows if row['file'].startswith(sequence+'-')]
            if not frames: continue
            for speed in [1,4]:
                target=OUT/f'{sequence}-{label}{"-slow" if speed==4 else ""}.mp4'
                if target.exists() and target.stat().st_size>0 and target.stat().st_mtime>=(run/'frames.csv').stat().st_mtime:
                    continue
                concat=OUT/f'{sequence}-{label}-{speed}x.ffconcat'
                lines=['ffconcat version 1.0']
                for i,row in enumerate(frames):
                    duration=float(frames[i+1]['seconds'])-float(row['seconds']) if i+1<len(frames) else 1/60
                    path=(run/'screenshots'/row['file']).resolve().as_posix()
                    lines += [f"file '{path}'",f'duration {max(1/240,duration)*speed:.6f}']
                lines += [f"file '{path}'"]
                concat.write_text('\n'.join(lines))
                subprocess.run([ffmpeg,'-y','-loglevel','error','-safe','0','-f','concat','-i',str(concat),
                                '-vf','fps=60','-c:v','libx264','-pix_fmt','yuv420p','-crf','19',
                                '-movflags','+faststart',str(target)],check=True)
            videos.append((label+' '+sequence,f'{sequence}-{label}'))
        still=run/'screenshots'/('cloak-'+label.split('-')[0]+'.png')
        if still.exists(): shutil.copyfile(still,OUT/('in-game-'+label+'.png'))
    images=[name for name in ['body-front','body-back','body-side','body-three-quarter','cloak-front','cloak-back'] if (OUT/(name+'.png')).exists()]
    doc='<!doctype html><html lang="en"><meta charset="utf-8"><title>Jehoash: painted wing body and BRG cloak</title>'
    doc+='<style>body{font:16px system-ui;background:#18181b;color:#eee;max-width:1100px;margin:32px auto;padding:0 15px}video{width:100%}a{color:#b7d2ff}section{margin-bottom:32px}.stills{display:flex;flex-wrap:wrap;gap:12px}.stills a{width:calc(33.333% - 12px)}.stills img{width:100%;object-fit:contain}</style>'
    doc+='<h1>Jehoash: painted wing body and BRG cloak</h1><p>Fuller blocky phase-two body, ivory and lavender wing artwork from Screenshot 2025-04-28 191821.png, and the existing BRG cloak.</p>'
    doc+='<h2>Body and cloak reference views</h2><div class="stills">'+''.join(f'<a href="{name}.png"><img src="{name}.png" alt="{name}"></a>' for name in images)+'</div>'
    doc+='<h2>In-game motion</h2><p>The teleport clips include the actual blink and the following illusion animation. Fixed camera angles and night vision make the outfit easier to inspect. Walking, melee, ranged and the full death clip follow. Only captures matching the current body texture are included.</p>'
    for label,file in videos:
        doc+=f'<section><h3>{html.escape(label)}</h3><video controls loop preload="none" src="{file}.mp4"></video><p><a href="{file}-slow.mp4">Quarter-speed playback</a></p></section>'
    (OUT/'index.html').write_text(doc+'</html>',encoding='utf-8')
    print('Review:',OUT/'index.html', '| current motion clips:',len(videos))


if __name__=='__main__':
    main()
