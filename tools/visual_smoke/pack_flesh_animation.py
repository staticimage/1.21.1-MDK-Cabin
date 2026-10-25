"""Pack the generated 4x4 animation sheet into Minecraft's vertical sprite strip.
Only crops/resizes/repackages the authored frames; no procedural repainting.
Run from the repository root: python tools/visual_smoke/pack_flesh_animation.py
"""
from pathlib import Path
import json
from PIL import Image

root = Path(__file__).resolve().parents[2]
sheet = Image.open(root / 'design/textures/living_flesh-animation-sheet.png').convert('RGB')
size = 64
strip = Image.new('RGB', (size, size * 16))
frames = []
for i in range(16):
    x, y = i % 4, i // 4
    tile = sheet.crop((round(x * sheet.width / 4), round(y * sheet.height / 4),
                       round((x + 1) * sheet.width / 4), round((y + 1) * sheet.height / 4)))
    tile = tile.resize((size, size), Image.Resampling.NEAREST)
    strip.paste(tile, (0, i * size))
    frames.append(tile.resize((256, 256), Image.Resampling.NEAREST))
target = root / 'src/main/resources/assets/cabinmod/textures/block/living_flesh.png'
strip.save(target)
Path(str(target) + '.mcmeta').write_text(json.dumps({'animation': {
    'frametime': 3, 'interpolate': True, 'width': size, 'height': size, 'frames': list(range(16))
}}, indent=2) + '\n')
frames[0].save(root / 'design/textures/living_flesh-preview.gif', save_all=True,
               append_images=frames[1:], duration=150, loop=0)
