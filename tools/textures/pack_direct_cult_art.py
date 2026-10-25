"""Pack the USER'S ORIGINAL PHOTOS into Minecraft UVs. No generated artwork is used.

Run: python tools/textures/pack_direct_cult_art.py  (requires Pillow)
All rectangle coordinates below are in the model's 128x128 UV coordinate system;
SCALE only changes output resolution, not the location of the print on the model.
The source photo is fitted intact (no cropping/painting) onto a dark rectangular
patch, then clipped to the original atlas alpha so the hood's face opening survives.
To change a garment print: edit source name / rectangle in ROBE_PATCHES, then rerun.
The head/mask UV area is deliberately left as the original placeholder texture.
To change which face of a cuboid gets a print, inspect JehoashModel.createBodyLayer:
for texOffs(u,v), cube width W / height H / depth D, the front rectangle starts at
(u+D,v+D) with size W x H; the back starts at (u+2*D+W,v+D).

The two screen PNGs are byte-for-byte copies, NOT repainted images. Their renderer
is RaveScreenRenderer and contains no time, beat, pulse, equalizer or glitch code.
"""
from pathlib import Path
from PIL import Image, ImageOps
import shutil

ROOT = Path(__file__).resolve().parents[2]
REF = ROOT / 'design/references/cult-moodboard'
TEX = ROOT / 'src/main/resources/assets/cabinmod/textures/entity'
SCALE = 8
WINGS = 'Screenshot 2025-04-28 191821.png'
ANGEL = 'Screenshot 2025-04-28 180651.png'
# label, exact supplied file, (UV x, UV y, UV width, UV height)
ROBE_PATCHES = [
    ('chest photo', ANGEL, (94, 6, 10, 18)),
    ('back feathers', WINGS, (110, 6, 10, 18)),
    ('robe front photo', ANGEL, (56, 8, 12, 18)),
    ('robe back feathers', WINGS, (76, 8, 12, 18)),
    ('right sleeve', WINGS, (0, 0, 12, 28)),
    ('left sleeve', WINGS, (12, 0, 12, 28)),
    ('hood fabric', WINGS, (0, 29, 40, 22)),
    ('cape and trailing cloth', WINGS, (0, 56, 60, 8)),
]

def fit_patch(atlas, source, rect, preserve_alpha=True):
    x,y,w,h = [round(v*SCALE) for v in rect]
    original_alpha = atlas.getchannel('A').crop((x,y,x+w,y+h))
    # Plain backing is a garment material, not an alteration of the photograph.
    patch = Image.new('RGBA', (w,h), (20,20,24,255))
    photo = Image.open(REF/source).convert('RGBA')
    photo.thumbnail((w,h), Image.Resampling.LANCZOS)
    patch.alpha_composite(photo, ((w-photo.width)//2,(h-photo.height)//2))
    if preserve_alpha: patch.putalpha(original_alpha)
    atlas.paste(patch,(x,y))

robe = Image.open(TEX/'joash/joash_placeholder.png').convert('RGBA').resize((128*SCALE,128*SCALE),Image.Resampling.NEAREST)
for label,source,rect in ROBE_PATCHES: fit_patch(robe,source,rect)
robe.save(TEX/'joash/joash_direct.png')
booth = Image.open(TEX/'dj_booth/dj_booth.png').convert('RGBA').resize((128*SCALE,128*SCALE),Image.Resampling.NEAREST)
# The cabinet front occupies UV [14,14] through [60,39]. Two original pictures
# fill this panel; hardware, decks, knobs and all actual record items stay intact.
fit_patch(booth,WINGS,(14,14,23,25),False)
fit_patch(booth,ANGEL,(37,14,23,25),False)
booth.save(TEX/'dj_booth/dj_booth_direct.png')
for source,name in [(WINGS,'supplied_wings.png'),(ANGEL,'supplied_angel.png')]: shutil.copy2(REF/source,TEX/'rave'/name)
print('Packed original photos into robe/booth UVs; copied both static screen images unchanged.')
