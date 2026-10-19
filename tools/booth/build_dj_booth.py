"""Generate the DJ booth's block models (two halves) and placeholder textures.

    python tools/booth/build_dj_booth.py <repo_dir>

Models are authored facing north (front panel at z=0, the crowd side); Jehoash stands behind (+z).
MAIN half: deck 1 + left of mixer. SIDE half (placed clockwise of MAIN): right of mixer, deck 2, boom mic.
The platters are not in the models - DjBoothRenderer draws them spinning.
"""
import json
import os
import random
import sys

from PIL import Image, ImageDraw

REPO = sys.argv[1]
RES = os.path.join(REPO, "src/main/resources/assets/cabinmod")
TEX = os.path.join(RES, "textures/block/dj_booth")
os.makedirs(TEX, exist_ok=True)
rnd = random.Random(4)
S = 32                                     # 2 px per model unit

PINK, CYAN, LIME = (255, 63, 210), (60, 235, 255), (140, 255, 60)


def save(name, img):
    img.save(os.path.join(TEX, name + ".png"))


def noise_fill(img, base, amt):
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            n = rnd.randint(-amt, amt)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in base) + (255,)


# panel: black, neon strips, little equalizer bars
img = Image.new("RGBA", (S, S)); noise_fill(img, (16, 12, 22), 6); d = ImageDraw.Draw(img)
d.rectangle((0, 3, S - 1, 4), fill=PINK); d.rectangle((0, S - 5, S - 1, S - 4), fill=CYAN)
for i, x in enumerate(range(3, S - 3, 3)):
    h = rnd.randint(3, 14)
    d.rectangle((x, S - 8 - h, x + 1, S - 8), fill=[PINK, CYAN, LIME][i % 3])
save("panel", img)

img = Image.new("RGBA", (S, S)); noise_fill(img, (28, 22, 36), 8); save("body", img)
img = Image.new("RGBA", (S, S)); noise_fill(img, (40, 36, 46), 5)
ImageDraw.Draw(img).rectangle((0, 0, S - 1, S - 1), outline=(70, 66, 80, 255)); save("top", img)
img = Image.new("RGBA", (S, S)); noise_fill(img, (150, 150, 160), 18); save("metal", img)
img = Image.new("RGBA", (S, S)); noise_fill(img, (52, 50, 58), 6)            # deck plinth top
ImageDraw.Draw(img).ellipse((S - 7, S - 7, S - 3, S - 3), fill=(220, 40, 40, 255)); save("deck", img)

img = Image.new("RGBA", (S, S)); noise_fill(img, (30, 30, 34), 5); d = ImageDraw.Draw(img)   # mixer
for row in range(3):
    for col in range(4):
        x, y = 4 + col * 7, 3 + row * 6
        d.ellipse((x, y, x + 3, y + 3), fill=(200, 200, 210, 255))
for col in range(4):
    x = 5 + col * 7
    d.rectangle((x + 1, 21, x + 1, 30), fill=(10, 10, 10, 255))
    fy = rnd.randint(22, 27); d.rectangle((x, fy, x + 2, fy + 1), fill=[PINK, CYAN, LIME, PINK][col])
save("mixer", img)

img = Image.new("RGBA", (S, S)); noise_fill(img, (20, 20, 24), 4)
ImageDraw.Draw(img).rectangle((0, 0, S - 1, 6), fill=(60, 60, 66, 255)); save("mic", img)

# record: grooves, pink label, transparent outside the disc (drawn by the renderer)
R = 128
img = Image.new("RGBA", (R, R), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
d.ellipse((0, 0, R - 1, R - 1), fill=(14, 14, 16, 255))
for r in range(R // 2 - 3, R // 5, -3):
    g = 28 + (r % 9) * 2
    d.ellipse((R / 2 - r, R / 2 - r, R / 2 + r, R / 2 + r), outline=(g, g, g + 4, 255))
d.ellipse((R / 2 - 20, R / 2 - 20, R / 2 + 20, R / 2 + 20), fill=PINK + (255,))
d.rectangle((R / 2 - 14, R / 2 - 3, R / 2 + 14, R / 2 + 3), fill=(255, 255, 255, 255))   # a stripe so spinning reads
d.ellipse((R / 2 - 2, R / 2 - 2, R / 2 + 2, R / 2 + 2), fill=(10, 10, 10, 255))
img.save(os.path.join(TEX, "platter.png"))


def faces(tex, up=None, north=None):
    f = {k: {"texture": "#" + tex} for k in ("north", "south", "east", "west", "up", "down")}
    if up: f["up"] = {"texture": "#" + up}
    if north: f["north"] = {"texture": "#" + north}
    return f


def el(frm, to, tex, up=None, north=None, rot=None):
    e = {"from": frm, "to": to, "faces": faces(tex, up, north)}
    if rot: e["rotation"] = rot
    return e


TEXTURES = {k: f"cabinmod:block/dj_booth/{k}" for k in ("panel", "body", "top", "metal", "deck", "mixer", "mic")}
TEXTURES["particle"] = "cabinmod:block/dj_booth/body"
BASE = [
    el([0, 0, 0], [16, 13, 2], "body", north="panel"),
    el([0, 0, 2], [16, 13, 16], "body"),
    el([0, 13, 0], [16, 14, 16], "body", up="top"),
]


def tonearm(base_x):
    return [
        el([base_x, 14, 11.5], [base_x + 1.5, 16, 13], "metal"),
        el([base_x - 5.5, 15.6, 12], [base_x + 0.5, 16.1, 12.6], "metal",
           rot={"origin": [base_x + 0.75, 15.85, 12.3], "axis": "y", "angle": -22.5}),
    ]


main = BASE + [
    el([1, 14, 3], [13, 15.5, 14], "metal", up="deck"),                       # deck 1, platter centre (7, 8.5)
    *tonearm(11.2),
    el([13.5, 14, 4], [16, 15.6, 13], "metal", up="mixer"),                   # mixer, left part
]
side = BASE + [
    el([0, 14, 4], [4.5, 15.6, 13], "metal", up="mixer"),                     # mixer, right part
    el([5, 14, 3], [15, 15.5, 14], "metal", up="deck"),                       # deck 2, platter centre (10, 8.5)
    *tonearm(13.2),
    # boom-stand mic: base and pole at the front-right, boom swinging back over the decks to where
    # Jehoash stands, mic hanging off the end
    el([12.5, 14, 0.5], [15.5, 14.6, 3.5], "mic"),
    el([13.6, 14.6, 1.6], [14.4, 30.5, 2.4], "metal"),
    el([-3, 30.1, 1.6], [14.4, 30.9, 2.4], "metal",
       rot={"origin": [14, 30.5, 2], "axis": "y", "angle": 45}),
    el([1, 27.4, 13], [3, 30.6, 15], "mic"),
]

models = os.path.join(RES, "models/block")
for name, elements in (("dj_booth_main", main), ("dj_booth_side", side)):
    with open(os.path.join(models, name + ".json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"parent": "block/block", "ambientocclusion": False, "textures": TEXTURES, "elements": elements}, f, indent=1)

variants = {}
for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
    for part in ("main", "side"):
        v = {"model": f"cabinmod:block/dj_booth_{part}"}
        if y:
            v["y"] = y
        variants[f"facing={facing},part={part}"] = v
with open(os.path.join(RES, "blockstates/dj_booth.json"), "w", encoding="utf-8", newline="\n") as f:
    json.dump({"variants": variants}, f, indent=2)

with open(os.path.join(RES, "models/item/dj_booth.json"), "w", encoding="utf-8", newline="\n") as f:
    json.dump({"parent": "cabinmod:block/dj_booth_main", "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.55, 0.55, 0.55]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}}}, f, indent=2)
print("booth models + textures written")
