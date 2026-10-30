"""Export Jehoash and his DJ booth from design/blender/joash_v2.blend in one headless run.

    "E:/Blender Foundation/blender/blender.exe" --background --factory-startup \
        --python tools/blender/export_all.py -- [--blend design/blender/joash_v2.blend] [--fresh auto|1|0] [--report r.json]

Runs tools/blender/mc_export.py once per rig (exec with an ARGS dict, exactly like bl.py does in a live Blender):
    rig_joash    -> mob joash,    class Jehoash
    rig_dj_booth -> mob dj_booth, class DjBooth
Each run writes entity/client/generated/<Cls>ModelBase.java, assets/cabinmod/animations/<mob>.json and
textures/entity/<mob>/<mob>_uv_template.png / _uv_legend.txt / _uv_lock.json.

--fresh (default auto): "1" ignores the UV lock and repacks the atlas (the rig is still being built). auto = "1"
only while every <mob>*.png in the texture folder is an untouched generated placeholder (tEXt cabinmod-placeholder=1
and cabinmod-placeholder-idat = sha1 of its pixel data); as soon as one is painted or
edited it switches to "0", so the lock keeps that paint where it is.

Afterwards the written clip files are checked against the clip table of design/JEHOASH_V2.md (section 5):
every clip present, length and marker times within half a frame, looping flag from the _loop suffix.
The .blend is not saved (the exporter only adds cube "uv" properties; the lock json is the record).
"""
import hashlib
import json
import os
import re
import struct
import sys

import bpy

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_DEFAULT = os.path.abspath(os.path.join(HERE, "..", "..")).replace("\\", "/")
TARGETS = (("joash", "Jehoash", "rig_joash"), ("dj_booth", "DjBooth", "rig_dj_booth"))
TAG, TAG_IDAT = "cabinmod-placeholder", "cabinmod-placeholder-idat"


def parse_args():
    import argparse
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=REPO_DEFAULT)
    ap.add_argument("--blend", default="")
    ap.add_argument("--contract", default="")
    ap.add_argument("--fresh", default="auto", choices=("auto", "1", "0"))
    ap.add_argument("--report", default="")
    a = ap.parse_args(argv)
    a.repo = os.path.abspath(a.repo).replace("\\", "/")
    a.blend = a.blend or os.path.join(a.repo, "design", "blender", "joash_v2.blend")
    a.contract = a.contract or os.path.join(a.repo, "design", "JEHOASH_V2.md")
    return a


def is_placeholder(path):
    """A placeholder is tagged and still has its original pixel-data hash."""
    try:
        data = open(path, "rb").read()
    except OSError:
        return False
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return False
    text, idat, pos = {}, hashlib.sha1(), 8
    while pos + 8 <= len(data):
        n, tag = struct.unpack(">I4s", data[pos:pos + 8])
        payload = data[pos + 8:pos + 8 + n]
        pos += 12 + n
        if tag == b"tEXt" and b"\0" in payload:
            k, v = payload.split(b"\0", 1)
            text[k.decode("latin-1")] = v.decode("latin-1")
        elif tag == b"IDAT":
            idat.update(payload)
        elif tag == b"IEND":
            break
    return text.get(TAG) == "1" and text.get(TAG_IDAT) == idat.hexdigest()


def painted_textures(repo, mob):
    """PNGs of this mob that a person painted or edited. The uv template is the exporter's own."""
    d = os.path.join(repo, "src/main/resources/assets/cabinmod/textures/entity", mob)
    if not os.path.isdir(d):
        return []
    return sorted(f for f in os.listdir(d) if f.endswith(".png") and not f.endswith("_uv_template.png")
                  and not is_placeholder(os.path.join(d, f)))


# ---------------------------------------------------------------- contract section 5
def parse_clip_table(path, fps):
    """{(mob, clip): {"length": s, "loop": bool, "markers": [(name, s)]}} from the section-5 clip table."""
    want = {}
    if not os.path.exists(path):
        return want
    text = open(path, encoding="utf-8").read()
    m = re.search(r"^## 5\..*?$(.*?)^##? ", text, re.S | re.M)
    if not m:
        return want
    for line in m.group(1).splitlines():
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 3 or not cells[0] or cells[0] in ("clip",) or set(cells[0]) <= set("-"):
            continue
        name, length, markers = cells[0], cells[1], cells[2]
        mob = "joash"
        if name.startswith("booth:"):
            mob, name = "dj_booth", name.split(":", 1)[1].strip()
        if not re.fullmatch(r"[a-z_]+", name):
            continue
        part = length.split("=")[-1].strip()
        if part.endswith(" f"):
            secs = float(part[:-2]) / fps
        else:
            secs = float(re.findall(r"[\d.]+", part)[0])
        marks = []
        if markers not in ("—", "-", ""):
            for item in markers.split(","):
                nm, t = item.strip().rsplit(" ", 1)
                marks.append((nm, float(t)))
        want[(mob, name)] = {"length": secs, "loop": name.endswith("_loop"), "markers": marks}
    return want


def check_clips(repo, want, fps):
    tol = 0.5 / fps + 1e-3          # everything sits on the nearest frame
    problems, table = [], {}
    for mob, _, _ in TARGETS:
        p = os.path.join(repo, "src/main/resources/assets/cabinmod/animations", f"{mob}.json")
        clips = json.load(open(p, encoding="utf-8"))["clips"] if os.path.exists(p) else {}
        for name, c in sorted(clips.items()):
            table[f"{mob}:{name}"] = {"length": c["length"], "frames": round(c["length"] * fps), "loop": c["loop"],
                                      "markers": [f"{e['type']}{':' + e['arg'] if e['arg'] else ''} {e['t']}"
                                                  for e in c["events"]],
                                      "animated_parts": len(c["bones"])}
            if (mob, name) not in want:
                problems.append(f"{mob}:{name} is exported but not in the contract table")
        for (wm, wn), w in want.items():
            if wm != mob:
                continue
            c = clips.get(wn)
            if c is None:
                problems.append(f"{mob}:{wn} missing from {mob}.json")
                continue
            if abs(c["length"] - w["length"]) > tol:
                problems.append(f"{mob}:{wn} length {c['length']} s, contract {w['length']} s")
            if c["loop"] != w["loop"]:
                problems.append(f"{mob}:{wn} loop={c['loop']}, contract loop={w['loop']}")
            got = [((e["type"] + ":" + e["arg"]) if e["arg"] else e["type"], e["t"]) for e in c["events"]]
            if [n for n, _ in got] != [n for n, _ in w["markers"]]:
                problems.append(f"{mob}:{wn} markers {got}, contract {w['markers']}")
            else:
                for (n, t), (_, wt) in zip(got, w["markers"]):
                    if abs(t - wt) > tol:
                        problems.append(f"{mob}:{wn} marker {n} at {t} s, contract {wt} s")
    return table, problems


# ---------------------------------------------------------------- run
def main():
    a = parse_args()
    exporter = os.path.join(HERE, "mc_export.py")
    src = open(exporter, encoding="utf-8").read()
    code = compile(src, exporter, "exec")
    bpy.ops.wm.open_mainfile(filepath=os.path.abspath(a.blend))
    scene = bpy.context.scene
    fps = scene.render.fps / scene.render.fps_base

    summary = {"blend": a.blend.replace("\\", "/"), "fps": fps, "mobs": {}, "warnings": []}
    for mob, cls, rig_name in TARGETS:
        painted = painted_textures(a.repo, mob)
        fresh = a.fresh
        if fresh == "auto":
            fresh = "0" if painted else "1"
        if fresh == "1" and painted:
            summary["warnings"].append(f"{mob}: --fresh 1 with user-painted textures {painted}: the atlas is repacked "
                                       f"and that paint will no longer line up")
        g = {"ARGS": {"mob": mob, "cls": cls, "repo": a.repo, "fresh": fresh}, "__name__": "mc_export"}
        exec(code, g)
        res = g["result"]
        rig = bpy.data.objects[rig_name]
        cubes = sorted(g["all_cubes"], key=lambda c: c["name"])
        res["fresh"] = fresh
        res["painted_textures"] = painted
        res["parts_by_kind"] = {k: sum(1 for b in rig.data.bones if b.name.startswith(k))
                                for k in ("b_", "loc_", "phys_")}
        res["cube_list"] = {c["name"]: {"uv": list(c["uv_xy"]), "size": [round(v, 4) for v in c["size"]]}
                            for c in cubes}
        res["fractional_cubes"] = sorted(c["name"] for c in cubes
                                         if any(abs(v - round(v)) > 1e-4 for v in c["size"]))
        summary["mobs"][mob] = res
        summary["warnings"] += [f"{mob}: {w}" for w in res["warnings"]]
        print(f"EXPORTED {mob}: {res['parts']} parts, {res['cubes']} cubes, atlas {res['atlas']}, "
              f"{len(res['clips'])} clips, {len(res['warnings'])} warnings", flush=True)

    want = parse_clip_table(a.contract, fps)
    if not want:
        summary["warnings"].append(f"could not read the clip table (section 5) of {a.contract}")
    table, problems = check_clips(a.repo, want, fps)
    summary["clips"] = table
    summary["contract_problems"] = problems
    summary["warnings"] += problems
    if a.report:
        with open(a.report, "w", encoding="utf-8", newline="\n") as f:
            json.dump(summary, f, indent=1)
    short = {k: v for k, v in summary.items() if k != "mobs"}
    short["mobs"] = {m: {k: v for k, v in r.items() if k != "cube_list"} for m, r in summary["mobs"].items()}
    print("EXPORT_ALL " + json.dumps(short), flush=True)
    print("EXPORT_ALL " + ("OK, no warnings" if not summary["warnings"] else
                           f"{len(summary['warnings'])} WARNINGS:\n  " + "\n  ".join(summary["warnings"])), flush=True)


main()
