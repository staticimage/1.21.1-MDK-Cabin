"""Package a completed native player-robe capture (Pillow and ffmpeg/imageio-ffmpeg).

Run after: gradlew -I tools/visual_smoke/smoke.gradle -ProbeReview runClient
"""
from pathlib import Path
import csv
import html
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/robe-review'
STILLS = {
    'front': 'Front: classic on the left, slim on the right',
    'back': 'Back and cape print',
    'side': 'Side and sleeve fit',
    'walk': 'Walking',
    'crouch': 'Crouching',
    'jump': 'Jumping',
    'bow': 'Using a bow',
    'swim': 'Swimming pose',
    'ride': 'Riding',
    'armor-and-head-turn': 'Helmet, leggings, boots and head turn',
    'first-person': 'First-person sleeve',
    'first-person-item': 'First person with a held item',
    'inventory': 'Survival inventory preview',
    'unequipped': 'Local player unequipped',
    're-equipped': 'Local player re-equipped',
}


def main():
    runs = sorted((run for run in (OUT / 'captures').iterdir() if (run / 'frames.csv').is_file()),
                  key=lambda run: (run / 'frames.csv').stat().st_mtime)
    if not runs:
        raise SystemExit('No completed robe capture. Run the robe preview first.')
    run = runs[-1]
    with (run / 'frames.csv').open(newline='') as stream:
        rows = list(csv.DictReader(stream))
    for row in rows:
        path = run / 'screenshots' / row['file']
        if not path.is_file() or path.stat().st_size < 8:
            raise SystemExit(f'Capture has not finished writing: {path}')
    frames = [row for row in rows if row['file'].startswith('motion-')]
    if len(frames) < 60:
        raise SystemExit('Capture is missing motion frames.')
    ffmpeg = shutil.which('ffmpeg')
    if not ffmpeg:
        import imageio_ffmpeg
        ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    for speed, name in [(1, 'motion'), (4, 'motion-slow')]:
        target = OUT / f'{name}.mp4'
        if target.exists() and target.stat().st_mtime >= (run / 'frames.csv').stat().st_mtime:
            continue
        concat = OUT / f'{name}.ffconcat'
        lines = ['ffconcat version 1.0']
        for i, row in enumerate(frames):
            duration = float(frames[i + 1]['seconds']) - float(row['seconds']) if i + 1 < len(frames) else 1 / 60
            path = (run / 'screenshots' / row['file']).resolve().as_posix().replace("'", "'\\''")
            lines += [f"file '{path}'", f'duration {max(1 / 240, duration) * speed:.6f}']
        lines += [f"file '{path}'"]
        concat.write_text('\n'.join(lines), encoding='utf-8')
        subprocess.run([ffmpeg, '-y', '-loglevel', 'error', '-safe', '0', '-f', 'concat', '-i', str(concat),
                        '-vf', 'fps=60', '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '19',
                        '-movflags', '+faststart', str(target)], check=True)
    (OUT / 'stills').mkdir(exist_ok=True)
    cards = []
    for name, label in STILLS.items():
        source = run / 'screenshots' / f'{name}.png'
        if not source.is_file():
            raise SystemExit(f'Missing required still: {source}')
        shutil.copyfile(source, OUT / 'stills' / source.name)
        cards.append(f'<figure><a href="stills/{name}.png"><img loading="lazy" src="stills/{name}.png" '
                     f'alt="{html.escape(label)}"></a><figcaption>{html.escape(label)}</figcaption></figure>')
    doc = '''<!doctype html><html lang="en"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Jehoash's wearable robe</title>
<style>body{font:16px system-ui;background:#18181b;color:#eee;max-width:1100px;margin:32px auto;padding:0 16px}
a{color:#c6ddff}video{width:100%}.stills{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:20px}
figure{margin:0}img{width:100%}figcaption{margin:6px 0 16px}</style>
<h1>Jehoash's wearable robe</h1>
<p>Existing BRG fabric and cover prints, an open-faced hood, fitted classic and slim sleeves,
and simulated skirt, cape and shoulder fabric. Classic is on the left; slim is on the right.</p>
<h2>Native in-game motion</h2><p>Walk, crouch, jump, bow, swimming pose and riding, in that order.</p>
<video controls loop preload="metadata" poster="stills/front.png" src="motion.mp4"></video>
<p><a href="motion-slow.mp4">Quarter-speed video</a> · <a href="motion.mp4">Normal-speed video</a></p>
<h2>Equipment and pose views</h2><div class="stills">'''
    doc += ''.join(cards) + '</div><p>Capture: ' + html.escape(run.name) + '</p></html>'
    (OUT / 'index.html').write_text(doc, encoding='utf-8')
    print(f'Review: {OUT / "index.html"} | {len(frames)} motion frames, {len(cards)} stills')


if __name__ == '__main__':
    main()
