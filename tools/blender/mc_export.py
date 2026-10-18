"""Export a Cabin Mod rig from Blender to Minecraft (vanilla HierarchicalModel + baked animations).

Run inside Blender, e.g. through tools/blender/bl.py:
    python tools/blender/bl.py tools/blender/mc_export.py --set mob=joash --set cls=Jehoash \
        --set repo=C:/Users/calca/Desktop/cabinmod/1.21.1-MDK-Cabin

See design/BLENDER_CONVENTIONS.md for the rig rules. Writes:
    src/main/java/net/austin/cabinmod/entity/client/generated/<Cls>ModelBase.java
    src/main/resources/assets/cabinmod/animations/<mob>.json
    src/main/resources/assets/cabinmod/textures/entity/<mob>/<mob>_uv_template.png
    src/main/resources/assets/cabinmod/textures/entity/<mob>/<mob>_uv_legend.txt
"""
import colorsys
import json
import math
import os
import struct
import zlib

import bpy
from mathutils import Matrix, Vector

ARGS = globals().get("ARGS", {})
MOB = ARGS["mob"]
CLS = ARGS["cls"]
REPO = ARGS["repo"]
ROT_TOL = float(ARGS.get("rot_tol", 0.05))      # degrees
POS_TOL = float(ARGS.get("pos_tol", 0.01))      # pixels
SCALE_TOL = float(ARGS.get("scale_tol", 0.001))

# Blender (x right, y back, z up; mob faces -Y) -> Minecraft model space (y down; mob faces -Z).
C3 = Matrix(((1, 0, 0), (0, 0, -1), (0, 1, 0)))
C3T = C3.transposed()
warnings = []


def to_mc(v):
    return Vector((v.x, -v.z, v.y))


def part_name(bone_name):
    return bone_name[2:] if bone_name.startswith("b_") else bone_name


def is_exported(bone_name):
    return bone_name.startswith("b_") or bone_name.startswith("loc_")


def java_ident(name):
    parts = [p for p in name.split("_") if p]
    ident = parts[0] + "".join(p[:1].upper() + p[1:] for p in parts[1:])
    return ident + "Part" if ident in ("root", "default", "new", "class", "final") else ident


def jf(x):
    s = f"{x:.4f}".rstrip("0").rstrip(".")
    if s in ("-0", ""):
        s = "0"
    return s + "F"


# ---------------------------------------------------------------- rig discovery
rig = bpy.data.objects.get(f"rig_{MOB}")
if rig is None or rig.type != "ARMATURE":
    raise RuntimeError(f"armature rig_{MOB} not found")

arm = rig.data
bones = [b for b in arm.bones if is_exported(b.name)]
if not any(b.name == "b_root" for b in bones):
    raise RuntimeError("rig needs a b_root bone at the feet")


def exported_parent(bone):
    p = bone.parent
    while p is not None and not is_exported(p.name):
        p = p.parent
    return p


children = {b.name: [] for b in bones}
roots = []
for b in bones:
    p = exported_parent(b)
    (children[p.name] if p else roots).append(b)


def rest_offset(bone):
    p = exported_parent(bone)
    return bone.head_local - (p.head_local if p else Vector())


# ---------------------------------------------------------------- cubes (rest pose)
saved_pose_position = arm.pose_position
arm.pose_position = "REST"
bpy.context.view_layer.update()
rig_inv = rig.matrix_world.inverted()

cubes = {b.name: [] for b in bones}
for obj in bpy.data.objects:
    if obj.type != "MESH" or not obj.name.startswith("c_") or obj.parent != rig:
        continue
    if obj.parent_type != "BONE" or obj.parent_bone not in cubes:
        warnings.append(f"{obj.name}: not bone-parented to an exported bone, skipped")
        continue
    if obj.vertex_groups or any(m.type == "ARMATURE" for m in obj.modifiers):
        warnings.append(f"{obj.name}: has vertex groups / armature modifier (rigid only!)")
    bone = arm.bones[obj.parent_bone]
    m = rig_inv @ obj.matrix_world
    pts = [to_mc(m @ v.co - bone.head_local) for v in obj.data.vertices]
    lo = Vector((min(p[i] for p in pts) for i in range(3)))
    hi = Vector((max(p[i] for p in pts) for i in range(3)))
    corner_ok = all(all(abs(p[i] - lo[i]) < 1e-3 or abs(p[i] - hi[i]) < 1e-3 for i in range(3)) for p in pts)
    if len(pts) != 8 or not corner_ok:
        warnings.append(f"{obj.name}: not an axis-aligned box at rest, exported as its bounding box")
    size = hi - lo
    cubes[bone.name].append({
        "name": obj.name,
        "min": lo, "size": size,
        "inflate": float(obj.get("inflate", 0.0)),
        "mirror": bool(obj.get("mirror", False)),
        "uv": list(obj["uv"]) if "uv" in obj else None,
    })

arm.pose_position = saved_pose_position
bpy.context.view_layer.update()

# ---------------------------------------------------------------- box-UV packing
all_cubes = [c for lst in cubes.values() for c in lst]
for c in all_cubes:
    w, h, d = (math.ceil(c["size"][i] - 1e-4) for i in range(3))
    c["fp"] = (2 * (d + w), d + h)
    c["whd"] = (w, h, d)


def try_pack(size):
    grid = [[False] * size for _ in range(size)]

    def free(x, y, fw, fh):
        if x + fw > size or y + fh > size:
            return False
        return all(not grid[yy][xx] for yy in range(y, y + fh) for xx in range(x, x + fw))

    def mark(x, y, fw, fh):
        for yy in range(y, y + fh):
            for xx in range(x, x + fw):
                grid[yy][xx] = True

    placed = {}
    for c in [c for c in all_cubes if c["uv"]]:
        u, v = int(c["uv"][0]), int(c["uv"][1])
        fw, fh = c["fp"]
        if u + fw > size or v + fh > size:
            return None
        mark(u, v, fw, fh)
        placed[c["name"]] = (u, v)
    for c in sorted((c for c in all_cubes if not c["uv"]), key=lambda c: (-c["fp"][1], -c["fp"][0])):
        fw, fh = c["fp"]
        spot = next(((x, y) for y in range(size) for x in range(size) if free(x, y, fw, fh)), None)
        if spot is None:
            return None
        mark(*spot, fw, fh)
        placed[c["name"]] = spot
    return placed


for atlas in (64, 128, 256, 512):
    uv_map = try_pack(atlas)
    if uv_map is not None:
        break
else:
    raise RuntimeError("cubes do not fit in a 512x512 atlas")
for c in all_cubes:
    c["uv_xy"] = uv_map[c["name"]]

# ---------------------------------------------------------------- model java
lines = []


def emit_part(bone, parent_var, depth):
    var = f"p_{java_ident(part_name(bone.name))}"
    off = to_mc(rest_offset(bone))
    if exported_parent(bone) is None:
        off = off + Vector((0, 24, 0))
    builder = "CubeListBuilder.create()"
    for c in cubes[bone.name]:
        u, v = c["uv_xy"]
        lo, sz = c["min"], c["size"]
        builder += f"\n                .texOffs({u}, {v})"
        if c["mirror"]:
            builder += ".mirror()"
        deform = f"new CubeDeformation({jf(c['inflate'])})" if c["inflate"] else "CubeDeformation.NONE"
        builder += (f".addBox({jf(lo.x)}, {jf(lo.y)}, {jf(lo.z)}, "
                    f"{jf(sz.x)}, {jf(sz.y)}, {jf(sz.z)}, {deform})")
        if c["mirror"]:
            builder += ".mirror(false)"
    lines.append(f"        PartDefinition {var} = {parent_var}.addOrReplaceChild(\"{part_name(bone.name)}\", {builder},")
    lines.append(f"                PartPose.offset({jf(off.x)}, {jf(off.y)}, {jf(off.z)}));")
    for ch in children[bone.name]:
        emit_part(ch, var, depth + 1)


for r in roots:
    emit_part(r, "mesh", 0)

fields, lookups = [], []


def emit_lookup(bone, parent_expr):
    name = part_name(bone.name)
    ident = java_ident(name)
    fields.append(f"    public final ModelPart {ident};")
    lookups.append(f"        this.{ident} = {parent_expr}.getChild(\"{name}\");")
    for ch in children[bone.name]:
        emit_lookup(ch, f"this.{ident}")


for r in roots:
    emit_lookup(r, "root")

java = f"""// GENERATED by tools/blender/mc_export.py from rig_{MOB} - do not edit; re-export from Blender.
package net.austin.cabinmod.entity.client.generated;

import net.austin.cabinmod.CabinMod;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public abstract class {CLS}ModelBase<T extends Entity> extends HierarchicalModel<T> {{
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CabinMod.MOD_ID, "{MOB}"), "main");
    public static final int TEXTURE_SIZE = {atlas};

    private final ModelPart meshRoot;
{chr(10).join(fields)}

    protected {CLS}ModelBase(ModelPart root) {{
        this.meshRoot = root;
{chr(10).join(lookups)}
    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition mesh = meshDefinition.getRoot();
{chr(10).join(lines)}
        return LayerDefinition.create(meshDefinition, {atlas}, {atlas});
    }}

    @Override
    public ModelPart root() {{
        return meshRoot;
    }}
}}
"""

java_dir = os.path.join(REPO, "src/main/java/net/austin/cabinmod/entity/client/generated")
os.makedirs(java_dir, exist_ok=True)
with open(os.path.join(java_dir, f"{CLS}ModelBase.java"), "w", encoding="utf-8", newline="\n") as f:
    f.write(java)

# ---------------------------------------------------------------- animations (baked)
scene = bpy.context.scene
fps = scene.render.fps / scene.render.fps_base
if abs(fps - 24) > 1e-6:
    warnings.append(f"scene fps is {fps}, conventions say 24 (times are still correct)")


def reduce_keys(samples, tol):
    """Drop samples that linear interpolation between kept neighbours reproduces within tol."""
    if len(samples) <= 2:
        return samples
    kept = [samples[0]]
    i = 0
    while i < len(samples) - 1:
        j = i + 1
        while j + 1 < len(samples):
            t0, a = samples[i][0], samples[i][1:]
            t1, b = samples[j + 1][0], samples[j + 1][1:]
            ok = True
            for k in range(i + 1, j + 1):
                tk, vk = samples[k][0], samples[k][1:]
                f = (tk - t0) / (t1 - t0)
                if any(abs(a[n] + (b[n] - a[n]) * f - vk[n]) > tol for n in range(3)):
                    ok = False
                    break
            if not ok:
                break
            j += 1
        kept.append(samples[j])
        i = j
    return kept


ad = rig.animation_data or rig.animation_data_create()
saved_action, saved_frame = ad.action, scene.frame_current
clips = {}
for action in sorted((a for a in bpy.data.actions if a.name.startswith(MOB + "_")), key=lambda a: a.name):
    ad.action = action
    start, end = (int(round(x)) for x in action.frame_range)
    tracks = {b.name: {"rot": [], "pos": [], "scale": []} for b in bones}
    prev_euler = {}
    for frame in range(start, end + 1):
        scene.frame_set(frame)
        t = (frame - start) / fps
        world = {}
        for b in bones:
            pb = rig.pose.bones[b.name]
            world[b.name] = (pb.matrix @ b.matrix_local.inverted()) @ Matrix.Translation(b.head_local)
        for b in bones:
            p = exported_parent(b)
            local = (world[p.name].inverted() if p else Matrix()) @ world[b.name]
            loc, quat, scl = local.decompose()
            dpos = loc - rest_offset(b)
            rot_mc = C3 @ quat.to_matrix() @ C3T
            e = rot_mc.to_euler("XYZ", prev_euler[b.name]) if b.name in prev_euler else rot_mc.to_euler("XYZ")
            prev_euler[b.name] = e
            tr = tracks[b.name]
            tr["rot"].append([t] + [math.degrees(a) for a in e])
            tr["pos"].append([t, dpos.x, dpos.z, dpos.y])          # posVec(x, up, z) convention
            tr["scale"].append([t, scl.x, scl.z, scl.y])
    bone_out = {}
    for b in bones:
        out = {}
        for chan, tol, ident in (("rot", ROT_TOL, 0.0), ("pos", POS_TOL, 0.0), ("scale", SCALE_TOL, 1.0)):
            s = tracks[b.name][chan]
            if all(abs(v - ident) <= tol for row in s for v in row[1:]):
                continue
            out[chan] = [[round(x, 4) + 0.0 for x in row] for row in reduce_keys(s, tol)]
        if out:
            bone_out[part_name(b.name)] = out
    events = []
    for m in sorted(action.pose_markers, key=lambda m: m.frame):
        if start <= m.frame <= end:
            kind, _, arg = m.name.partition(":")
            events.append({"t": round((m.frame - start) / fps, 4), "type": kind, "arg": arg})
    clips[action.name[len(MOB) + 1:]] = {
        "length": round((end - start) / fps, 4),
        "loop": action.name.endswith("_loop"),
        "events": events,
        "bones": bone_out,
    }
ad.action = saved_action
scene.frame_set(saved_frame)

res_dir = os.path.join(REPO, "src/main/resources/assets/cabinmod")
os.makedirs(os.path.join(res_dir, "animations"), exist_ok=True)
with open(os.path.join(res_dir, "animations", f"{MOB}.json"), "w", encoding="utf-8", newline="\n") as f:
    json.dump({"mob": MOB, "format": 1, "clips": clips}, f, separators=(",", ":"))

# ---------------------------------------------------------------- UV template png
px = bytearray(atlas * atlas * 4)


def fill(x, y, w, h, rgb):
    for yy in range(y, min(y + h, atlas)):
        for xx in range(x, min(x + w, atlas)):
            edge = xx in (x, x + w - 1) or yy in (y, y + h - 1)
            k = 0.6 if edge else 1.0
            i = (yy * atlas + xx) * 4
            px[i:i + 4] = bytes((int(rgb[0] * k), int(rgb[1] * k), int(rgb[2] * k), 255))


legend = [f"{MOB} UV legend - atlas {atlas}x{atlas}. Rects are x,y,w,h. 'right'/'left' are the mob's own sides.", ""]
FACES = (("top", 1.0), ("bottom", 0.55), ("right", 0.8), ("front", 0.95), ("left", 0.75), ("back", 0.65))
for n, c in enumerate(sorted(all_cubes, key=lambda c: c["name"])):
    u, v = c["uv_xy"]
    w, h, d = c["whd"]
    rects = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }
    hue = (n * 0.61803398875) % 1.0
    legend.append(f"{c['name']}  (offset {u},{v}  size {w}x{h}x{d})")
    for face, shade in FACES:
        r, g, b = colorsys.hsv_to_rgb(hue, 0.65, shade)
        x, y, fw, fh = rects[face]
        if fw > 0 and fh > 0:
            fill(x, y, fw, fh, (r * 255, g * 255, b * 255))
        legend.append(f"    {face:<6} {x},{y},{fw},{fh}")


def png_bytes(w, h, rgba):
    raw = b"".join(b"\0" + bytes(rgba[y * w * 4:(y + 1) * w * 4]) for y in range(h))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


tex_dir = os.path.join(res_dir, "textures/entity", MOB)
os.makedirs(tex_dir, exist_ok=True)
with open(os.path.join(tex_dir, f"{MOB}_uv_template.png"), "wb") as f:
    f.write(png_bytes(atlas, atlas, px))
with open(os.path.join(tex_dir, f"{MOB}_uv_legend.txt"), "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(legend) + "\n")

result = {
    "atlas": atlas,
    "parts": len(bones),
    "cubes": len(all_cubes),
    "clips": {k: {"length": v["length"], "loop": v["loop"], "animated_parts": len(v["bones"]),
                  "keys": sum(len(ch) for bo in v["bones"].values() for ch in bo.values()),
                  "events": len(v["events"])} for k, v in clips.items()},
    "warnings": warnings,
}
