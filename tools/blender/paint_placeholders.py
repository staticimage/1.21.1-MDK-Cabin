"""Placeholder textures for Jehoash (joash) and his DJ booth, plus the booth's item icon.

    python tools/blender/paint_placeholders.py [--repo <repo>] [--scale 4] [--only joash,dj_booth]
                                              [--no-icon] [--blender <blender.exe>] [--preview <dir>]

Run after tools/blender/export_all.py. Per mob it reads
    textures/entity/<mob>/<mob>_uv_legend.txt          cube names, UV offsets, face rects
    entity/client/generated/<Cls>ModelBase.java        the exact box sizes + rest positions the game builds
and paints, at --scale x the atlas size (model UVs are normalised, so the game samples the bigger image as is):
    textures/entity/<mob>/<mob>.png             base colours (cutout: alpha 0 = hole, e.g. the hood's open front)
    textures/entity/<mob>/<mob>_emissive.png    full-bright glow, drawn with RenderType.eyes (additive):
                                                black/transparent = no glow
Then (unless --no-icon) it renders the booth in its rest pose, 3/4 view, transparent background, with headless
Blender (tools/blender/render_booth_icon.py) and writes textures/item/dj_booth.png (128 x 128).
--preview <dir> also renders both mobs with their new textures (front/back 3/4) into <dir> for a look.

Ownership: every PNG written here carries the tEXt chunks cabinmod-placeholder=1 and cabinmod-placeholder-idat=
<sha1 of its pixel data chunks>. A PNG is only ever replaced when both are present and the hash still matches;
anything else (no tag = painted by a person; tag but different pixels = edited in a program that kept the tag)
is reported and left alone. Delete a file to get a fresh placeholder.

Face rects: Minecraft lays a box's faces out from its float size (a 0.7 px finger has 0.7 px wide UV faces), while
the legend/template rects are rounded up to whole pixels. The faces are painted at the exact float rects so each
face samples its own paint in game.
"""
import argparse
import hashlib
import io
import json
import math
import os
import re
import struct
import subprocess
import tempfile
import zlib

import numpy as np
from PIL import Image, PngImagePlugin

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_DEFAULT = os.path.abspath(os.path.join(HERE, "..", ".."))
BLENDER_DEFAULT = "E:/Blender Foundation/blender/blender.exe"
RES = "src/main/resources/assets/cabinmod"
GEN = "src/main/java/net/austin/cabinmod/entity/client/generated"
MOBS = {"joash": "Jehoash", "dj_booth": "DjBooth"}
TAG, TAG_IDAT = "cabinmod-placeholder", "cabinmod-placeholder-idat"
FACE_SRC = RES + "/textures/entity/villager/profession/cabinger.png"   # the SCARY GUY
FACE_CROP = (80, 80, 160, 180)
ICON_SIZE = 128

# palette (0..255)
PURPLE = (30, 16, 48)
BLACK = (14, 12, 18)
PINK = (255, 63, 210)
CYAN = (60, 235, 255)
LIME = (140, 255, 60)
SKIN = (176, 168, 160)
YELLOW = (255, 224, 46)
WHITE = (236, 236, 242)
DEEP = (50, 22, 86)              # cape
ROBE_PURPLE = (46, 21, 74)
SLEEVE = (27, 18, 41)
CLOTH_BLACK = (17, 15, 22)
NAIL = (30, 12, 40)
GREY = (44, 44, 51)
METAL = (146, 149, 160)
SILVER = (188, 190, 201)
VINYL = (13, 13, 16)
SIDES = ("front", "left", "back", "right")
NORMAL = {"front": (0, -1, 0), "back": (0, 1, 0), "left": (1, 0, 0), "right": (-1, 0, 0),
          "top": (0, 0, 1), "bottom": (0, 0, -1)}


# ============================================================================ PNG ownership tags
def _chunks(data):
    pos = 8
    while pos + 8 <= len(data):
        n, tag = struct.unpack(">I4s", data[pos:pos + 8])
        yield tag, data[pos + 8:pos + 8 + n]
        pos += 12 + n
        if tag == b"IEND":
            break


def _chunk(tag, payload):
    return struct.pack(">I", len(payload)) + tag + payload + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)


def png_owner(path):
    """'missing' | 'placeholder' (ours, pixels untouched) | 'user' (anything else: never overwrite)."""
    if not os.path.exists(path):
        return "missing"
    try:
        data = open(path, "rb").read()
        if data[:8] != b"\x89PNG\r\n\x1a\n":
            return "user"
        text, idat = {}, hashlib.sha1()
        for tag, payload in _chunks(data):
            if tag == b"tEXt" and b"\0" in payload:
                k, v = payload.split(b"\0", 1)
                text[k.decode("latin-1")] = v.decode("latin-1")
            elif tag == b"IDAT":
                idat.update(payload)
        ok = text.get(TAG) == "1" and text.get(TAG_IDAT) == idat.hexdigest()
        return "placeholder" if ok else "user"
    except OSError:
        return "user"


def tagged_png(img):
    """PNG bytes of a PIL image with tEXt cabinmod-placeholder=1 (via PIL) + the pixel-data hash (before IEND)."""
    info = PngImagePlugin.PngInfo()
    info.add_text(TAG, "1")
    buf = io.BytesIO()
    img.save(buf, "PNG", pnginfo=info, optimize=True)
    data = buf.getvalue()
    idat = hashlib.sha1(b"".join(p for t, p in _chunks(data) if t == b"IDAT")).hexdigest()
    out = [data[:8]]
    for t, p in _chunks(data):
        if t == b"IEND":
            out.append(_chunk(b"tEXt", TAG_IDAT.encode("latin-1") + b"\0" + idat.encode("latin-1")))
        out.append(_chunk(t, p))
    return b"".join(out)


def write_placeholder(path, img, log):
    state = png_owner(path)
    rel = os.path.relpath(path, REPO).replace("\\", "/")
    if state == "user":
        log["skipped_user_art"].append(rel)
        print(f"  KEEP {rel}: painted or edited by hand (no untouched placeholder tag) - not overwritten", flush=True)
        return False
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(tagged_png(img))
    log["written"].append(rel)
    print(f"  wrote {rel} {img.size[0]}x{img.size[1]}", flush=True)
    return True


# ============================================================================ model data
def read_legend(path):
    atlas, cubes, cur = None, {}, None
    for line in open(path, encoding="utf-8"):
        m = re.search(r"atlas (\d+)x(\d+)", line)
        if atlas is None and m:
            atlas = int(m.group(1))
            continue
        m = re.match(r"(c_\w+)\s+\(offset (\d+),(\d+)\s+size (\d+)x(\d+)x(\d+)\)", line)
        if m:
            cur = m.group(1)
            cubes[cur] = {"uv": (int(m.group(2)), int(m.group(3))),
                          "isize": tuple(int(m.group(i)) for i in (4, 5, 6)), "faces": {}}
            continue
        m = re.match(r"\s+(top|bottom|right|front|left|back)\s+(\d+),(\d+),(\d+),(\d+)", line)
        if m and cur:
            cubes[cur]["faces"][m.group(1)] = tuple(int(m.group(i)) for i in range(2, 6))
    if atlas is None:
        raise RuntimeError(f"{path}: no atlas size")
    return atlas, cubes


PART_RE = re.compile(r'PartDefinition\s+(\w+)\s*=\s*(\w+)\.addOrReplaceChild\("([^"]+)",\s*CubeListBuilder\.create\(\)'
                     r'(.*?),\s*PartPose\.offset\(\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([-\d.]+)F\s*\)\);', re.S)
BOX_RE = re.compile(r'\.texOffs\((\d+),\s*(\d+)\)(\.mirror\(\))?\.addBox\(\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([-\d.]+)F,'
                    r'\s*([-\d.]+)F,\s*([-\d.]+)F,\s*([-\d.]+)F,\s*(?:CubeDeformation\.NONE|new CubeDeformation\(([^)]*)\))')


def read_java(path):
    """(u, v) -> box: part name, absolute rest origin (Minecraft model space), float dims (these lay out the UVs),
    grow (CubeDeformation: moves the geometry only), mirror."""
    text = open(path, encoding="utf-8").read()
    pos, boxes = {"mesh": np.zeros(3)}, {}
    for m in PART_RE.finditer(text):
        var, parent, name, body = m.group(1), m.group(2), m.group(3), m.group(4)
        pos[var] = pos[parent] + np.array([float(m.group(i)) for i in (5, 6, 7)])
        for b in BOX_RE.finditer(body):
            grow = [float(x.strip().rstrip("Ff")) for x in b.group(10).split(",")] if b.group(10) else [0.0]
            boxes[(int(b.group(1)), int(b.group(2)))] = {
                "part": name, "origin": pos[var] + np.array([float(b.group(i)) for i in (4, 5, 6)]),
                "dims": np.array([float(b.group(i)) for i in (7, 8, 9)]), "mirror": bool(b.group(3)),
                "grow": np.array(grow * 3 if len(grow) == 1 else grow)}
    return boxes


def blender_box(box):
    """Rest bounds in Blender/contract coordinates: +X = mob's left, -Y = front, Z = px above the feet."""
    g = box["grow"]
    o, d = box["origin"] - g, box["dims"] + 2 * g
    return (np.array([o[0], o[2], 24.0 - (o[1] + d[1])]), np.array([o[0] + d[0], o[2] + d[2], 24.0 - o[1]]))


def exact_rects(uv, dims):
    """Minecraft's box-UV face rects (x, y, w, h in atlas px) from the float box size."""
    u, v = uv
    w, h, d = (float(x) for x in dims)
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def face_xyz(face, lo, hi, s, t):
    """Texture coords (s left->right, t top->bottom, 0..1) of a face -> rest position (X, Y, Z) and the face's size
    (horizontal, vertical) in px. Matches ModelPart.Cube: every face reads unmirrored when seen from outside."""
    X0, Y0, Z0 = lo
    X1, Y1, Z1 = hi
    one = np.ones_like(s)
    if face == "front":
        return (X0 + s * (X1 - X0), Y0 * one, Z1 - t * (Z1 - Z0)), (X1 - X0, Z1 - Z0)
    if face == "back":
        return (X1 - s * (X1 - X0), Y1 * one, Z1 - t * (Z1 - Z0)), (X1 - X0, Z1 - Z0)
    if face == "right":
        return (X0 * one, Y1 - s * (Y1 - Y0), Z1 - t * (Z1 - Z0)), (Y1 - Y0, Z1 - Z0)
    if face == "left":
        return (X1 * one, Y0 + s * (Y1 - Y0), Z1 - t * (Z1 - Z0)), (Y1 - Y0, Z1 - Z0)
    if face == "top":
        return (X0 + s * (X1 - X0), Y1 - t * (Y1 - Y0), Z1 * one), (X1 - X0, Y1 - Y0)
    return (X0 + s * (X1 - X0), Y1 - t * (Y1 - Y0), Z0 * one), (X1 - X0, Y1 - Y0)       # bottom


# ============================================================================ painting
class Face:
    """One face of one box: texel arrays (rgb 0..255, alpha, emit) + where each texel sits on the rest model."""

    def __init__(self, cube, face, lo, hi, s, t, scale, ctx):
        self.cube, self.face, self.lo, self.hi, self.S, self.ctx = cube, face, lo, hi, scale, ctx
        (self.X, self.Y, self.Z), (self.fw, self.fh) = face_xyz(face, lo, hi, s, t)
        self.a, self.b = s * self.fw, t * self.fh          # face-local px: a left->right, b top->bottom
        self.n = NORMAL[face]
        self.rgb = np.zeros(s.shape + (3,))
        self.alpha = np.full(s.shape, 255.0)
        self.emit = np.zeros(s.shape + (3,))
        self.rng = np.random.default_rng(zlib.crc32(f"{cube}/{face}".encode()))

    @property
    def side(self):
        return self.face in SIDES

    def fill(self, col, mask=None, k=1.0):
        c = np.asarray(col, float) * k
        if mask is None:
            self.rgb[...] = c
        else:
            self.rgb[mask] = c

    def blend(self, col, w, mask=None):
        """rgb = rgb*(1-w) + col*w (w scalar or per texel)."""
        w = np.broadcast_to(np.asarray(w, float), self.a.shape)
        if mask is not None:
            w = np.where(mask, w, 0.0)
        self.rgb = self.rgb * (1 - w[..., None]) + np.asarray(col, float) * w[..., None]

    def glow(self, col, mask=None, k=1.0):
        c = np.asarray(col, float) * np.broadcast_to(np.asarray(k, float), self.a.shape)[..., None]
        if mask is not None:
            c = np.where(mask[..., None], c, 0.0)
        self.emit = np.maximum(self.emit, c)

    def mul(self, k):
        self.rgb *= np.broadcast_to(np.asarray(k, float), self.a.shape)[..., None]

    def noise(self, sigma=0.035, block=0.03, mask=None):
        n = 1 + sigma * self.rng.standard_normal(self.a.shape)
        if block:
            tab = self.rng.standard_normal((int(self.fh) + 2, int(self.fw) + 2))
            n += block * tab[np.clip(self.b.astype(int), 0, tab.shape[0] - 1),
                             np.clip(self.a.astype(int), 0, tab.shape[1] - 1)]
        if mask is not None:
            n = np.where(mask, n, 1.0)
        self.mul(n)

    def hole(self, mask):
        self.alpha[mask] = 0
        self.rgb[mask] = 0
        self.emit[mask] = 0

    def edge_dist(self):
        return np.minimum(np.minimum(self.a, self.fw - self.a), np.minimum(self.b, self.fh - self.b))


def mix(c1, c2, w):
    return tuple(a * (1 - w) + b * w for a, b in zip(c1, c2))


def perimeter(f):
    """Distance around a box's four sides (continuous, increasing left->right on every side face)."""
    X0, Y0, _ = f.lo
    X1, Y1, _ = f.hi
    W, D = X1 - X0, Y1 - Y0
    return {"front": f.X - X0, "left": W + (f.Y - Y0), "back": W + D + (X1 - f.X),
            "right": 2 * W + D + (Y1 - f.Y)}[f.face], 2 * (W + D)


def side_of(name):
    m = re.search(r"_(l|r)(?:_\d+)?$", name)
    return m.group(1) if m else ""


# ---------------------------------------------------------------------------- Jehoash
def m_skin(f, k=1.0):
    f.fill(SKIN, k=k)
    f.noise(0.03, 0.025)


def m_head(f):
    m_skin(f)
    if f.face == "front":                                    # the SCARY GUY, stretched over the whole face
        ny, nx = f.a.shape
        img = f.ctx["face_src"].resize((nx, ny), Image.LANCZOS)
        arr = np.asarray(img, float)
        w = arr[..., 3:4] / 255.0
        f.rgb = f.rgb * (1 - w) + arr[..., :3] * w
    elif f.side:
        f.mul(1 - 0.12 * (f.b / f.fh))                       # a little darker toward the jaw


def m_hood(f):
    f.fill(PURPLE)
    if f.face == "back" or f.face in ("left", "right"):
        f.mul(1 + 0.09 * np.sin(f.a * 2.4 + 0.7 * np.sin(f.b * 0.9)))     # soft folds
    f.mul(1 - 0.18 * np.clip((f.Z - f.lo[2]) < 1.2, 0, 1))                # shadowed lower edge
    f.noise(0.04, 0.035)
    if f.face == "front":                                    # open front: a 1 px neon rim around the face
        e = f.edge_dist()
        f.hole(e >= 1.0)
        f.fill(PINK, (e >= 0.25) & (e < 0.75))
        f.fill(mix(PINK, PURPLE, 0.35), (e >= 0.75) & (e < 1.0))
        f.glow(PINK, (e >= 0.25) & (e < 0.75))
        f.glow(PINK, (e >= 0.75) & (e < 1.0), 0.5)
        f.glow(PINK, e < 0.25, 0.18)
    else:                                                    # the neon piping wraps onto the sides
        pipe = (f.Y - f.lo[1]) < 0.25
        f.fill(PINK, pipe)
        f.glow(PINK, pipe, 0.8)


def m_hp_band(f):
    f.fill((22, 20, 28))
    if f.face == "top":
        f.blend((70, 68, 82), 0.6, np.abs(f.Y) < 0.22)
    f.noise(0.03, 0.02)


def m_hp_cup(f):
    f.fill((20, 18, 26))
    f.noise(0.03, 0.02)
    outer = "left" if side_of(f.cube) == "l" else "right"
    if f.face == outer:
        ca, cb = f.fw / 2, f.fh / 2
        r = np.hypot((f.a - ca) / 1.3, (f.b - cb) / 1.75)
        f.fill((36, 34, 46), r < 0.66)
        f.fill((50, 48, 62), (r < 0.66) & ((np.floor(f.a * 4) + np.floor(f.b * 4)) % 2 == 0))
        ring = (r >= 0.66) & (r < 1.0)
        f.fill(CYAN, ring)
        f.glow(CYAN, ring)
        f.glow(CYAN, (r >= 0.5) & (r < 0.66), 0.22)
    elif f.face in ("front", "back", "top", "bottom"):      # thin LED edge on the outer rim
        rim = np.abs(f.X) > np.abs(f.hi[0] if outer == "left" else f.lo[0]) - 0.25
        f.fill(CYAN, rim, 0.75)
        f.glow(CYAN, rim, 0.5)


def m_neck(f):
    m_skin(f, 0.86)
    if f.side:
        f.mul(1 - 0.22 * np.clip(1 - f.b / 1.2, 0, 1))     # under the chin


def m_top(f):
    """Black raver top: helical diagonal pink/cyan stripes, acid smiley on the chest."""
    f.fill(BLACK)
    f.noise(0.05, 0.04)
    if f.side:
        p, per = perimeter(f)
        period = per / max(1, round(per / 4.2))
        q = ((p - f.Z) / period) % 1.0
        pink, cyan = q < 0.17, (q >= 0.5) & (q < 0.67)
        f.fill(mix(PINK, BLACK, 0.12), pink)
        f.fill(mix(CYAN, BLACK, 0.12), cyan)
        f.glow(PINK, pink, 0.42)
        f.glow(CYAN, cyan, 0.42)
    if f.face in ("top", "bottom"):
        f.mul(0.85)
    if f.cube == "c_joash_chest" and f.face == "front":
        smiley(f, f.fw / 2, f.fh * 0.42, 2.55)


def smiley(f, ca, cb, R):
    dx, dy = f.a - ca, f.b - cb
    r = np.hypot(dx, dy)
    disc = r < R
    rim = disc & (r >= R - 0.28)
    eyes = (((dx - 0.36 * R) / (0.12 * R)) ** 2 + ((dy + 0.22 * R) / (0.24 * R)) ** 2 < 1) | \
           (((dx + 0.36 * R) / (0.12 * R)) ** 2 + ((dy + 0.22 * R) / (0.24 * R)) ** 2 < 1)
    r2 = np.hypot(dx, dy + 0.08 * R)
    mouth = (r2 >= 0.52 * R) & (r2 < 0.67 * R) & (dy > 0.12 * R)
    face = disc & ~rim & ~eyes & ~mouth
    f.fill(YELLOW, face)
    f.fill((24, 18, 10), disc & (rim | eyes | mouth))
    f.glow(YELLOW, face, 0.32)


def m_pelvis(f):
    f.fill(CLOTH_BLACK)
    f.noise(0.05, 0.04)
    if f.side:
        belt = (f.Z >= 25.95) & (f.Z < 27.25)
        f.fill((30, 27, 36), belt)
        neon = (f.Z >= 26.35) & (f.Z < 26.85)
        f.fill(LIME, neon)
        f.glow(LIME, neon)
        if f.face == "front":
            buckle = belt & (np.abs(f.X) < 0.9)
            frame = buckle & ((np.abs(f.X) >= 0.6) | (f.Z < 26.2) | (f.Z >= 27.0))
            f.fill((44, 40, 52), buckle)
            f.fill(PINK, frame)
            f.glow(PINK, frame)


def m_sleeve(f):
    f.fill(SLEEVE)
    if f.side:
        f.mul(1 + 0.07 * np.sin(f.Z * 1.7 + np.sin(f.a * 1.3)))        # creases
    f.noise(0.045, 0.035)


def m_forearm(f):
    m_sleeve(f)
    if f.side:
        lime = (f.Z >= 20.9) & (f.Z < 21.6)
        pink = (f.Z >= 22.0) & (f.Z < 22.7)
        f.fill(LIME, lime)
        f.fill(PINK, pink)
        f.glow(LIME, lime)
        f.glow(PINK, pink)
        f.mul(np.where(f.Z < 20.85, 0.8, 1.0))                         # cuff shadow under the bracelets


def m_hand(f):
    m_skin(f)


def m_fingertip(f):
    """Distal finger/thumb segment: dark nail on the back of the hand side (+Y at rest) and over the tip."""
    m_skin(f)
    if f.face == "back":
        f.fill(NAIL, f.b > f.fh * 0.5)
        f.fill((70, 40, 84), (f.b > f.fh * 0.5) & (f.b < f.fh * 0.5 + 0.2))   # cuticle highlight
    elif f.face == "bottom":
        f.fill(NAIL, f.Y > (f.lo[1] + f.hi[1]) / 2 - 0.1)
    elif f.face in ("left", "right"):
        f.fill(NAIL, (f.b > f.fh * 0.55) & (f.Y > f.hi[1] - 0.2))


def m_leg(f):
    f.fill(CLOTH_BLACK)
    f.noise(0.05, 0.035)
    outer = "left" if side_of(f.cube) == "l" else "right"
    if f.face == outer:
        c = f.fw / 2
        st = ((f.a >= c - 0.625) & (f.a < c - 0.125)) | ((f.a >= c + 0.125) & (f.a < c + 0.625))
        f.fill(PINK, st)
        f.glow(PINK, st, 0.6)
    if f.side and "thigh" in f.cube:
        f.mul(1 - 0.25 * np.clip((f.Z - 23.0) / 1.5, 0, 1))         # under the pelvis


def m_boot(f):
    f.fill((22, 20, 28))
    f.noise(0.04, 0.03)
    if f.side:
        sole = f.Z < 0.75
        f.fill(WHITE, sole)
        f.fill((150, 150, 160), sole & (f.Z < 0.18))
        f.fill(PINK, (f.Z >= 0.75) & (f.Z < 0.95))
        if f.face == "front":
            f.blend((60, 58, 70), 0.5, (f.Z >= 0.95) & (f.Z < 1.6))     # toe cap
    elif f.face == "bottom":
        f.fill((214, 214, 222))
        f.fill((150, 150, 160), (np.floor((f.Y - f.lo[1]) / 0.75) % 2) == 0)
    elif f.face == "top":
        cx = (f.lo[0] + f.hi[0]) / 2
        front = f.Y < f.lo[1] + 3.6
        zig = np.abs(np.abs(f.X - cx) - 0.55 * (1 - ((f.Y - f.lo[1]) / 0.8) % 1.0)) < 0.18
        laces = front & (f.Y > f.lo[1] + 0.8) & zig & (np.abs(f.X - cx) < 0.8)
        f.fill(PINK, laces)


def m_cape(f):
    seg = int(f.cube.rsplit("_", 1)[1])
    inner_x = f.lo[0] if f.lo[0] > 0 else f.hi[0]            # the edge at the centre of his back
    outer_face, lining_face = "back", "front"
    if f.face == outer_face:
        f.fill(DEEP)
        f.mul(1 + 0.11 * np.sin((np.abs(f.X) - 0.25) * 2.9))           # vertical folds
    elif f.face == lining_face:
        f.fill((28, 12, 46))
    else:
        f.fill(mix(DEEP, BLACK, 0.3))
    f.noise(0.04, 0.03)
    seam = np.abs(f.X - inner_x) < 0.4
    if f.face in (outer_face, "top", "bottom"):
        f.fill(CYAN, seam, 0.9)
        f.glow(CYAN, seam, 0.7)
    if f.face in ("left", "right") and abs(f.X.flat[0] - inner_x) < 1e-3:
        f.fill(CYAN, None, 0.9)
        f.glow(CYAN, None, 0.7)
    if seg == 6:                                             # glowing pink hem
        z0 = f.lo[2]
        hem = (f.Z < z0 + 0.9) if f.face != "bottom" else np.ones_like(f.a, bool)
        aura = (f.Z >= z0 + 0.9) & (f.Z < z0 + 1.7) & (f.face != "bottom")
        f.blend(PINK, 0.35 * (1 - (f.Z - z0 - 0.9) / 0.8), aura)
        f.glow(PINK, aura, 0.3 * (1 - (f.Z - z0 - 0.9) / 0.8))
        f.fill(PINK, hem)
        f.glow(PINK, hem)


def m_robe(f):
    sid, seg = f.cube.split("_")[-2], int(f.cube.split("_")[-1])
    parity, (cx, cy) = f.ctx["robe"][sid]
    base = ROBE_PURPLE if parity == 0 else (15, 13, 19)
    neon = CYAN if parity == 0 else PINK
    rl = math.hypot(cx, cy)
    dot = (f.n[0] * cx + f.n[1] * cy) / rl if rl else 0.0
    f.fill(base)
    f.noise(0.045, 0.035)
    if f.side:
        if dot > 0.3:                                        # outside: neon edges
            edge = (f.a < 0.35) | (f.a > f.fw - 0.35)
            f.fill(neon, edge)
            f.glow(neon, edge, 0.55)
        elif dot < -0.3:                                     # lining
            f.mul(0.6)
        else:                                                # thin side = neon piping
            f.fill(mix(neon, base, 0.15))
            f.glow(neon, None, 0.5)
    else:
        f.mul(0.7)
    if seg == 4:                                             # faintly glowing tips
        z0 = f.lo[2]
        k = np.clip(1 - (f.Z - z0) / 1.6, 0, 1) if f.face != "bottom" else np.ones_like(f.a)
        f.blend(neon, 0.65 * k)
        f.glow(neon, k > 0, 0.35 * k)


def m_mirror(f, tile=1.0, glints=0.16):
    ta = max(1, round(f.fw / tile))
    tb = max(1, round(f.fh / tile))
    sa, sb = f.fw / ta, f.fh / tb
    ia, ib = np.clip((f.a / sa).astype(int), 0, ta - 1), np.clip((f.b / sb).astype(int), 0, tb - 1)
    lum = f.rng.uniform(0.5, 1.0, (tb, ta))
    tint = f.rng.uniform(-1, 1, (tb, ta))
    L = lum[ib, ia] * (0.82 + 0.18 * (1 - (f.a + f.b) / (f.fw + f.fh)))
    f.rgb[...] = np.stack([205 * L + 12 * tint[ib, ia], 210 * L, 230 * L - 8 * tint[ib, ia]], -1)
    pick = f.rng.uniform(0, 1, (tb, ta))
    cols = np.array([PINK, CYAN, LIME, WHITE, YELLOW], float)
    which = f.rng.integers(0, len(cols), (tb, ta))
    g = (pick < glints)[ib, ia]
    c = cols[which[ib, ia]]
    f.rgb = np.where(g[..., None], c, f.rgb)
    f.emit = np.where(g[..., None], c * 0.9, f.emit)
    fa, fb = (f.a / sa) % 1.0, (f.b / sb) % 1.0
    grout = (fa * sa < 0.22) | (fb * sb < 0.22)
    if ta == 1 and tb == 1:
        grout = f.edge_dist() < 0.2
    f.fill((40, 40, 52), grout)
    f.emit[grout] = 0
    sparkle = (pick > 0.93)[ib, ia] & ~grout & ((np.abs(fa - 0.5) * sa < 0.13) | (np.abs(fb - 0.5) * sb < 0.13))
    f.fill(WHITE, sparkle)
    f.glow(WHITE, sparkle)


def m_orb(f):
    m_mirror(f, 1.0, 0.16)


def m_shard(f):
    m_mirror(f, f.fw, 0.5)


# ---------------------------------------------------------------------------- DJ booth
def m_cabinet(f):
    f.fill((15, 14, 19))
    f.noise(0.04, 0.025)
    X, Z = f.X, f.Z
    if f.face == "front":
        seams = (np.abs(np.abs(X) - 8.0) < 0.13)
        f.fill((40, 40, 48), seams)
        center = np.abs(X) < 7.75
        # equalizer: 12 bars of LED segments in the centre panel
        k = np.floor((X + 7.375) / 1.25)
        inbar = center & (k >= 0) & (k < 12) & (((X + 7.375) % 1.25) < 1.0) & (Z >= 4.5)
        heights = np.array([15.5, 17.0, 14.0, 12.5, 15.0, 11.0, 9.5, 12.0, 8.0, 10.0, 6.5, 5.0])
        hk = heights[np.clip(k, 0, 11).astype(int)]
        lit = inbar & (Z < 4.5 + hk) & (((Z - 4.5) % 0.75) < 0.5)
        level = (Z - 4.5) / 17.0
        col = np.where((level < 0.45)[..., None], np.array(LIME, float),
                       np.where((level < 0.72)[..., None], np.array(YELLOW, float), np.array(PINK, float)))
        f.rgb = np.where(lit[..., None], col, f.rgb)
        f.emit = np.where(lit[..., None], col, f.emit)
        off = inbar & ~lit & (Z < 22.0) & (((Z - 4.5) % 0.75) < 0.5)
        f.fill((30, 30, 38), off)
        peak = inbar & (Z >= 4.5 + hk + 0.75) & (Z < 4.5 + hk + 1.25)
        f.fill(WHITE, peak)
        f.glow(WHITE, peak, 0.8)
        # side panels: neon chevrons pointing at the equalizer
        side = np.abs(X) > 8.25
        u = np.abs(X) - 8.25
        diag = (u + (Z - 3.5) * 0.9) % 5.0
        stripe_p = side & (diag < 0.6) & (Z > 3.2) & (Z < 21.8)
        stripe_c = side & (diag >= 2.5) & (diag < 3.1) & (Z > 3.2) & (Z < 21.8)
        f.fill(PINK, stripe_p)
        f.fill(CYAN, stripe_c)
        f.glow(PINK, stripe_p, 0.85)
        f.glow(CYAN, stripe_c, 0.85)
    if f.side:
        top = (Z >= 23.2) & (Z < 23.8)
        bot = (Z >= 1.2) & (Z < 1.7)
        f.fill(PINK, top)
        f.fill(CYAN, bot)
        f.glow(PINK, top)
        f.glow(CYAN, bot)
        if f.face in ("left", "right"):
            v = (f.Y - f.lo[1] >= 0.7) & (f.Y - f.lo[1] < 1.2) & (Z > 1.7) & (Z < 23.2)
            f.fill(CYAN, v)
            f.glow(CYAN, v, 0.85)
        if f.face == "back":                                 # the DJ's side: panels, a few status LEDs
            f.fill((32, 32, 40), (np.abs(np.abs(X) - 8.0) < 0.13) | (np.abs(Z - 12.5) < 0.1))
            leds = (np.abs(Z - 21.5) < 0.2) & (np.abs(((X + 23) % 3.0) - 1.5) < 0.2) & (np.abs(X) < 20)
            f.fill(LIME, leds)
            f.glow(LIME, leds)
            f.blend((15, 14, 19), 1.0, top | bot)
            f.emit[top | bot] = 0
    if f.face == "bottom":
        f.mul(0.6)


def m_booth_top(f):
    f.fill(GREY)
    f.noise(0.05, 0.03)
    if f.face == "top":
        rim = f.edge_dist() < 0.5
        f.mul(np.where(rim, 1.25, 1.0))
    elif f.side:
        f.mul(np.where(f.b < 0.25, 1.35, 0.85))


def m_deck(f):
    f.fill(METAL)
    if f.face == "top":
        f.mul(1 + 0.06 * np.sin(f.Y * 9.0 + f.rng.standard_normal(f.a.shape) * 0.6))  # brushed
        cx = (f.lo[0] + f.hi[0]) / 2
        cy = f.lo[1] + 6.0
        r = np.hypot(f.X - cx, f.Y - cy)
        f.mul(np.where((r > 5.6) & (r < 6.2), 0.55, 1.0))                      # platter well
        outer = cx + (6.4 if cx > 0 else -6.4)
        slot = (np.abs(f.X - outer) < 0.18) & (f.Y > f.lo[1] + 1.2) & (f.Y < f.lo[1] + 7.0)
        f.fill((20, 20, 26), slot)
        knob = (np.abs(f.X - outer) < 0.45) & (np.abs(f.Y - (f.lo[1] + 4.0)) < 0.3)
        f.fill((230, 232, 240), knob)
        bx = cx - (5.9 if cx > 0 else -5.9)
        button = (np.abs(f.X - bx) < 0.7) & (np.abs(f.Y - (f.lo[1] + 1.3)) < 0.7)
        f.fill((40, 40, 48), button)
        led = (np.abs(f.X - bx) < 0.25) & (np.abs(f.Y - (f.lo[1] + 1.3)) < 0.25)
        f.fill(PINK, led)
        f.glow(PINK, led)
    else:
        f.fill(mix(METAL, BLACK, 0.45))
        if f.face == "front":
            led = (np.abs(f.Z - 27.0) < 0.22) & (np.abs(((f.X % 2.0) - 1.0)) < 0.22) & \
                  (np.abs(f.X - (f.lo[0] + f.hi[0]) / 2) < 4.5)
            f.fill(CYAN, led)
            f.glow(CYAN, led)
    f.noise(0.03, 0.02)


def m_vinyl(f):
    f.fill(VINYL)
    cx, cy = (f.lo[0] + f.hi[0]) / 2, (f.lo[1] + f.hi[1]) / 2
    if f.face == "top":
        r = np.hypot(f.X - cx, f.Y - cy)
        th = np.arctan2(f.Y - cy, f.X - cx)
        groove = ((r * 2.0) % 1.0 < 0.45) & (r > 1.7) & (r < 5.4)
        gaps = (np.abs(r - 2.9) < 0.12) | (np.abs(r - 4.1) < 0.12)
        sheen = 1 + 1.6 * np.clip(np.cos(2 * (th - 0.8)), 0, 1) ** 6
        f.fill((34, 34, 42), groove)
        f.mul(np.where(groove, sheen, 1.0))
        f.fill(VINYL, gaps)
        f.fill((24, 24, 30), r >= 5.4)
    else:
        f.fill((28, 28, 34))
        f.mul(np.where(f.b < 0.2, 1.3, 1.0))
    f.noise(0.03, 0.0)


def m_label(f):
    f.fill(PINK)
    if f.face == "top":
        cx, cy = (f.lo[0] + f.hi[0]) / 2, (f.lo[1] + f.hi[1]) / 2
        r = np.hypot(f.X - cx, f.Y - cy)
        f.fill((150, 30, 120), (r > 0.62) & (r < 0.78))
        f.fill(WHITE, (np.abs(f.Y - cy - 0.42) < 0.08) & (np.abs(f.X - cx) < 0.45))
        f.fill((210, 212, 222), r < 0.22)
    else:
        f.mul(0.75)
    f.noise(0.02, 0.0)


def m_silver(f):
    f.fill(SILVER)
    f.mul({"top": 1.08, "bottom": 0.6}.get(f.face, 0.9))
    f.noise(0.03, 0.02)


def m_tonearm_base(f):
    f.fill((30, 30, 38))
    if f.face == "top":
        r = np.hypot(f.X - (f.lo[0] + f.hi[0]) / 2, f.Y - (f.lo[1] + f.hi[1]) / 2)
        f.fill(SILVER, r < 0.85)
        f.fill((90, 92, 104), r < 0.35)
    elif f.side:
        f.fill(SILVER, f.b < 0.3)
    f.noise(0.03, 0.02)


def m_headshell(f):
    m_silver(f)
    if f.face == "bottom":
        f.fill((22, 22, 28))
    elif f.side:
        f.fill((22, 22, 28), f.b > f.fh * 0.55)


def m_mixer(f):
    f.fill((25, 25, 31))
    f.noise(0.04, 0.02)
    X, Y, Z = f.X, f.Y, f.Z
    if f.face == "top":
        for x in (-2.5, 2.5):                                # channel fader slots
            f.fill((52, 52, 62), (np.abs(X - x) < 0.32) & (Y > -0.6) & (Y < 4.8))
            f.fill((4, 4, 6), (np.abs(X - x) < 0.13) & (Y > -0.4) & (Y < 4.6))
        f.fill((52, 52, 62), (np.abs(Y + 3.0) < 0.32) & (np.abs(X - 0.7) < 2.6))   # crossfader slot
        f.fill((4, 4, 6), (np.abs(Y + 3.0) < 0.13) & (np.abs(X - 0.7) < 2.4))
        for x in (-3.5, -1.2, 1.2, 3.5):                     # knob collars + tick marks
            r = np.hypot(X - x, Y - 5.5)
            f.fill((70, 70, 84), (r > 0.62) & (r < 0.8))
        for x in (-0.65, 0.65):                              # level meters
            k = np.floor((4.4 - Y) / 0.55)
            seg = (np.abs(X - x) < 0.2) & (k >= 0) & (k < 9) & (((4.4 - Y) % 0.55) < 0.38)
            col = np.where((k < 5)[..., None], np.array(LIME, float),
                           np.where((k < 7)[..., None], np.array(YELLOW, float), np.array(PINK, float)))
            lit = seg & (k < (7 if x < 0 else 6))
            f.rgb = np.where(lit[..., None], col, f.rgb)
            f.emit = np.where(lit[..., None], col, f.emit)
            f.fill((34, 34, 42), seg & ~lit)
        rim = f.edge_dist() < 0.3
        f.mul(np.where(rim, 1.3, 1.0))
    elif f.side:
        line = (Z >= 28.45) & (Z < 28.75)
        c = PINK if f.face in ("front", "back") else CYAN
        f.fill(c, line)
        f.glow(c, line, 0.9)
        if f.face == "front":
            dots = (np.abs(Z - 27.4) < 0.2) & (np.abs(((X + 6) % 1.5) - 0.75) < 0.2)
            f.fill(CYAN, dots)
            f.glow(CYAN, dots)


CAPS = {"c_booth_fader_1": CYAN, "c_booth_fader_2": PINK, "c_booth_xfader": LIME,
        "c_booth_knob_1": PINK, "c_booth_knob_2": CYAN, "c_booth_knob_3": LIME, "c_booth_knob_4": YELLOW}


def m_cap(f):
    col = CAPS[f.cube]
    f.fill(col, None, 0.9 if f.face == "top" else 0.62)
    if f.face == "top":
        cx, cy = (f.lo[0] + f.hi[0]) / 2, (f.lo[1] + f.hi[1]) / 2
        if "knob" in f.cube:
            mark = (np.abs(f.X - cx) < 0.13) & (f.Y < cy) & (f.Y > cy - 0.45)
        else:
            mark = np.abs(f.Y - cy) < 0.13
        f.fill(WHITE, mark)
    f.noise(0.02, 0.0)


def m_laptop_base(f):
    f.fill(SILVER)
    if f.face == "top":
        keys = (f.X > -3.6) & (f.X < 3.6) & (f.Y > 4.9) & (f.Y < 7.7)
        grid = ((f.X + 3.6) % 0.6 < 0.45) & ((f.Y - 4.9) % 0.56 < 0.42)
        f.fill((58, 58, 68), keys & grid)
        f.fill((150, 152, 162), keys & ~grid)
    else:
        f.mul(0.82)
    f.noise(0.025, 0.015)


def m_laptop_screen(f):
    f.fill(SILVER)
    f.noise(0.025, 0.015)
    if f.face == "front":                                    # the lid, facing the crowd: glowing logo
        r = np.hypot(f.a - f.fw / 2, f.b - f.fh * 0.48)
        logo = (r > 0.55) & (r < 0.95)
        f.fill(PINK, logo)
        f.glow(PINK, logo, 0.8)
    elif f.face == "back":                                   # the display, facing the DJ
        e = f.edge_dist()
        f.fill((12, 12, 16), e < 0.38)
        scr = e >= 0.38
        f.fill((9, 13, 38), scr)
        f.glow((14, 22, 60), scr)
        w = np.zeros_like(f.a)
        for k, (amp, ph) in enumerate(((0.55, 0.3), (0.3, 1.7), (0.22, 4.1))):
            w += amp * np.abs(np.sin(f.a * (2.1 + 1.7 * k) + ph))
        for row, col in ((1.75, CYAN), (3.85, PINK)):
            band = scr & (np.abs(f.b - row) < 0.15 + 0.6 * w)
            f.fill(col, band)
            f.glow(col, band)
        head = scr & (np.abs(f.a - f.fw * 0.36) < 0.1)
        f.fill(WHITE, head)
        f.glow(WHITE, head)
        grid = scr & (np.abs(f.b - 2.8) < 0.06)
        f.fill((40, 50, 100), grid)
        f.glow((30, 40, 90), grid)
    else:
        f.mul(0.85)


def m_black_metal(f):
    f.fill((18, 18, 23))
    if f.side:
        f.mul(np.where(f.a < 0.25, 1.9, 1.0))
    f.noise(0.03, 0.02)


def m_mic(f):
    f.fill((18, 18, 23))
    grille = (f.Z < 46.4) if f.face != "bottom" else np.ones_like(f.a, bool)
    f.fill((156, 158, 170), grille)
    dots = grille & ((np.floor(f.a * 4) + np.floor(f.b * 4)) % 2 == 0)
    f.fill((70, 72, 84), dots)
    ring = (f.Z >= 46.4) & (f.Z < 46.65) & (f.face != "bottom")
    f.fill(PINK, ring)
    f.glow(PINK, ring, 0.8)
    f.noise(0.03, 0.0)


def m_unknown(f):
    f.fill((90, 90, 100))
    f.noise(0.05, 0.0)


MATERIALS = [
    (r"c_joash_head$", m_head), (r"c_joash_hood$", m_hood), (r"c_joash_hp_band$", m_hp_band),
    (r"c_joash_hp_cup_[lr]$", m_hp_cup), (r"c_joash_neck$", m_neck), (r"c_joash_(chest|belly)$", m_top),
    (r"c_joash_pelvis$", m_pelvis), (r"c_joash_(shoulder|upperarm)_[lr]$", m_sleeve),
    (r"c_joash_forearm_[lr]$", m_forearm), (r"c_joash_(palm_[lr]|finger_\w+_[lr]_1|thumb_[lr]_1)$", m_hand),
    (r"c_joash_(finger_\w+_[lr]_2|thumb_[lr]_2)$", m_fingertip), (r"c_joash_(thigh|shin)_[lr]$", m_leg),
    (r"c_joash_foot_[lr]$", m_boot), (r"c_joash_cape_[lr]_\d+$", m_cape), (r"c_joash_robe_\w+_\d+$", m_robe),
    (r"c_joash_orb$", m_orb), (r"c_joash_orb_sat_\d+$", m_shard),
    (r"c_booth_cabinet$", m_cabinet), (r"c_booth_top$", m_booth_top), (r"c_booth_deck_[lr]$", m_deck),
    (r"c_booth_platter_[lr]_\d+$", m_vinyl), (r"c_booth_label_[lr]$", m_label),
    (r"c_booth_tonearm_base_[lr]$", m_tonearm_base), (r"c_booth_tonearm_[lr]$", m_silver),
    (r"c_booth_headshell_[lr]$", m_headshell), (r"c_booth_mixer$", m_mixer),
    (r"c_booth_(fader_\d|xfader|knob_\d)$", m_cap), (r"c_booth_laptop_base$", m_laptop_base),
    (r"c_booth_laptop_screen$", m_laptop_screen), (r"c_booth_mic_(pole|boom)$", m_black_metal),
    (r"c_booth_mic$", m_mic),
]


def material(name):
    for pat, fn in MATERIALS:
        if re.match(pat, name):
            return fn
    return None


# ============================================================================ per mob
def load_model(repo, mob):
    cls = MOBS[mob]
    tex_dir = os.path.join(repo, RES, "textures/entity", mob)
    atlas, legend = read_legend(os.path.join(tex_dir, f"{mob}_uv_legend.txt"))
    boxes = read_java(os.path.join(repo, GEN, f"{cls}ModelBase.java"))
    cubes, problems = {}, []
    for name, L in sorted(legend.items()):
        box = boxes.get(L["uv"])
        if box is None:
            problems.append(f"{name}: no box with texOffs{L['uv']} in {cls}ModelBase.java (re-export?)")
            continue
        if tuple(math.ceil(x - 1e-4) for x in box["dims"]) != L["isize"]:
            problems.append(f"{name}: legend size {L['isize']} != java box {tuple(box['dims'])} (stale legend?)")
        if box["mirror"]:
            problems.append(f"{name}: mirrored UVs are not supported by the painter (painted unmirrored)")
        lo, hi = blender_box(box)
        cubes[name] = {"uv": L["uv"], "dims": box["dims"], "lo": lo, "hi": hi, "part": box["part"],
                       "rects": exact_rects(L["uv"], box["dims"])}
    return atlas, cubes, problems


def robe_table(cubes):
    """strand id -> (parity, centre (x, y)): strands alternate purple/black going round the waist."""
    cen = {}
    for name, c in cubes.items():
        m = re.match(r"c_joash_robe_(\w+)_1$", name)
        if m:
            cen[m.group(1)] = ((c["lo"][0] + c["hi"][0]) / 2, (c["lo"][1] + c["hi"][1]) / 2)
    order = sorted(cen, key=lambda k: math.atan2(cen[k][1], cen[k][0]))
    return {k: (i % 2, cen[k]) for i, k in enumerate(order)}


def paint(repo, mob, scale, log):
    atlas, cubes, problems = load_model(repo, mob)
    log["warnings"] += [f"{mob}: {p}" for p in problems]
    ctx = {"robe": robe_table(cubes)}
    if mob == "joash":
        ctx["face_src"] = Image.open(os.path.join(repo, FACE_SRC)).convert("RGBA").crop(FACE_CROP)
    size = atlas * scale
    base = np.zeros((size, size, 4))
    glow = np.zeros((size, size, 3))
    painted = 0
    for name, c in sorted(cubes.items()):
        fn = material(name)
        if fn is None:
            log["warnings"].append(f"{mob}: {name} has no placeholder material (painted grey)")
            fn = m_unknown
        for face, (x0, y0, w, h) in c["rects"].items():
            if w <= 0 or h <= 0:
                continue
            px0, px1 = int(round(x0 * scale)), int(round((x0 + w) * scale))
            py0, py1 = int(round(y0 * scale)), int(round((y0 + h) * scale))
            if px1 <= px0 or py1 <= py0:
                continue
            s = np.clip(((np.arange(px0, px1) + 0.5) / scale - x0) / w, 0, 1)
            t = np.clip(((np.arange(py0, py1) + 0.5) / scale - y0) / h, 0, 1)
            sg, tg = np.meshgrid(s, t)
            f = Face(name, face, c["lo"], c["hi"], sg, tg, scale, ctx)
            fn(f)
            # the glow is ADDED on top of the lit base in game: tone the base down under strong glow so neon
            # keeps its hue in daylight instead of washing out to pastel
            f.mul(1 - 0.3 * np.clip(f.emit.max(axis=-1) / 255.0 * 1.25, 0, 1))
            base[py0:py1, px0:px1, :3] = np.clip(f.rgb, 0, 255)
            base[py0:py1, px0:px1, 3] = f.alpha
            glow[py0:py1, px0:px1] = np.clip(f.emit, 0, 255) * (f.alpha[..., None] > 0)
            painted += 1
    base = np.round(base).astype(np.uint8)
    glow = np.round(glow).astype(np.uint8)
    lit = glow.max(axis=2) > 0
    emissive = np.zeros((size, size, 4), np.uint8)
    emissive[..., :3] = glow
    emissive[..., 3] = np.where(lit, 255, 0)
    tex_dir = os.path.join(repo, RES, "textures/entity", mob)
    write_placeholder(os.path.join(tex_dir, f"{mob}.png"), Image.fromarray(base, "RGBA"), log)
    write_placeholder(os.path.join(tex_dir, f"{mob}_emissive.png"), Image.fromarray(emissive, "RGBA"), log)
    log["mobs"][mob] = {"atlas": atlas, "texture": size, "cubes": len(cubes), "faces": painted,
                        "glowing_texels": int(lit.sum())}
    return atlas, cubes


# ============================================================================ Blender renders (icon, previews)
def model_json(repo, mob, atlas, cubes, path):
    tex_dir = os.path.join(repo, RES, "textures/entity", mob)
    data = {"mob": mob, "atlas": atlas,
            "texture": os.path.join(tex_dir, f"{mob}.png").replace("\\", "/"),
            "emissive": os.path.join(tex_dir, f"{mob}_emissive.png").replace("\\", "/"),
            "boxes": [{"name": n, "lo": [float(x) for x in c["lo"]], "hi": [float(x) for x in c["hi"]],
                       "rects": {k: [float(x) for x in r] for k, r in c["rects"].items()}}
                      for n, c in sorted(cubes.items())]}
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh)


def blender_render(blender, model, out, size, view, light=1.0):
    script = os.path.join(HERE, "render_booth_icon.py")
    cmd = [blender, "--background", "--factory-startup", "--python", script, "--",
           "--model", model, "--out", out, "--size", str(size), "--view", view, "--light", str(light)]
    res = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if res.returncode != 0 or not os.path.exists(out):
        raise RuntimeError(f"blender render failed ({res.returncode}):\n{res.stdout[-3000:]}\n{res.stderr[-3000:]}")


def render_icon(repo, blender, atlas, cubes, tmp, log):
    model = os.path.join(tmp, "dj_booth_model.json")
    model_json(repo, "dj_booth", atlas, cubes, model)
    raw = os.path.join(tmp, "dj_booth_icon_raw.png")
    blender_render(blender, model, raw, ICON_SIZE * 4, "icon")
    img = Image.open(raw).convert("RGBA").resize((ICON_SIZE, ICON_SIZE), Image.LANCZOS)
    arr = np.asarray(img).copy()
    # the booth is a BlockItem of a solid block: drawn with the cutout sheet, so give it a clean binary silhouette
    solid = arr[..., 3] >= 128
    arr[..., 3] = np.where(solid, 255, 0)
    arr[~solid] = 0
    write_placeholder(os.path.join(repo, RES, "textures/item/dj_booth.png"), Image.fromarray(arr, "RGBA"), log)


def render_previews(repo, blender, models, out_dir, tmp):
    os.makedirs(out_dir, exist_ok=True)
    for mob, (atlas, cubes) in models.items():
        model = os.path.join(tmp, f"{mob}_model.json")
        model_json(repo, mob, atlas, cubes, model)
        for view, light, tag in (("front34", 1.0, ""), ("back34", 1.0, ""), ("front34", 0.12, "_night")):
            out = os.path.join(out_dir, f"{mob}_{view}{tag}.png")
            blender_render(blender, model, out, 900, view, light)
            print(f"  preview {out}", flush=True)


# ============================================================================ main
def paint_effect_icon(repo, log):
    """Life of the Party (the curse his roar gives) HUD/inventory icon: an 18 x 18 pixel-art disco ball."""
    img = np.zeros((18, 18, 4), np.uint8)
    img[0:3, 9] = (40, 30, 60, 255)                                           # the string it hangs from
    glints = {(5, 10): (255, 60, 200), (11, 7): (60, 230, 255), (13, 12): (255, 60, 200), (8, 14): (60, 230, 255)}
    for y in range(18):
        for x in range(18):
            d = math.hypot(x + 0.5 - 9, y + 0.5 - 10)
            if d > 7.2:
                continue
            if d > 6.2:
                img[y, x] = (40, 30, 60, 255)                                 # outline
                continue
            light = 1.0 - 0.06 * max(0.0, (x - 6) + (y - 7))                  # lit from the top left
            base = (205, 205, 222) if (x // 2 + y // 2) % 2 else (130, 128, 156)
            img[y, x, :3] = [int(c * max(0.45, light)) for c in base]
            img[y, x, 3] = 255
            if (x, y) in glints:
                img[y, x, :3] = glints[(x, y)]
    for x, y in ((6, 6), (7, 6), (6, 7)):
        img[y, x] = (255, 255, 255, 255)                                      # specular
    write_placeholder(os.path.join(repo, RES, "textures/mob_effect/life_of_the_party.png"), Image.fromarray(img, "RGBA"), log)


def main():
    global REPO
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--repo", default=REPO_DEFAULT)
    ap.add_argument("--scale", type=int, default=4)
    ap.add_argument("--only", default="joash,dj_booth")
    ap.add_argument("--no-icon", action="store_true")
    ap.add_argument("--blender", default=BLENDER_DEFAULT)
    ap.add_argument("--preview", default="", help="also render textured previews of both mobs into this folder")
    ap.add_argument("--tmp", default="", help="work folder for the Blender renders (default: a temp dir)")
    a = ap.parse_args()
    REPO = os.path.abspath(a.repo)
    log = {"written": [], "skipped_user_art": [], "warnings": [], "mobs": {}}
    models = {}
    for mob in [m for m in a.only.split(",") if m]:
        if mob not in MOBS:
            raise SystemExit(f"unknown mob {mob}; known: {', '.join(MOBS)}")
        print(f"{mob}:", flush=True)
        models[mob] = paint(REPO, mob, a.scale, log)
    if not a.no_icon and "joash" in models:
        print("effect icon:", flush=True)
        paint_effect_icon(REPO, log)
    if (not a.no_icon and "dj_booth" in models) or a.preview:
        tmp_ctx = tempfile.TemporaryDirectory() if not a.tmp else None
        tmp = a.tmp or tmp_ctx.name
        os.makedirs(tmp, exist_ok=True)
        try:
            if not a.no_icon and "dj_booth" in models:
                print("item icon:", flush=True)
                render_icon(REPO, a.blender, *models["dj_booth"], tmp, log)
            if a.preview:
                print("previews:", flush=True)
                render_previews(REPO, a.blender, models, a.preview, tmp)
        finally:
            if tmp_ctx:
                tmp_ctx.cleanup()
    print("PAINT " + json.dumps(log), flush=True)


REPO = REPO_DEFAULT
if __name__ == "__main__":
    main()
