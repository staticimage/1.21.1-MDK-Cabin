"""Preview renders for design/blender/joash_v2.blend (Workbench): contact sheets per clip + MP4 with audio.

Run headless only:
    "E:/Blender Foundation/blender/blender.exe" --background --factory-startup --python tools/blender/render_previews.py -- \
        --blend design/blender/joash_v2.blend --out <dir> [--clips jam_loop,interrupted] [--frames 12] \
        [--video jam_loop] [--loops 2] [--no-sheets] [--reel "jam_loop@560-640,interrupted,walk_loop*2,..."]

Contact sheets: <out>/<clip>.png - evenly spaced frames from a 3/4-front camera (plus a side view for
jam_loop / interrupted / walk_loop), each tile stamped with frame / beat (jam: clip beat + contract beat, the clip is
rotated by the action's 'jam_phase_beats') or seconds + markers. Every pose-marker frame is shown, together with the
anticipation pose 4 frames before it. Additive clips (hurt) are shown on top of idle_loop.
Video: <out>/<clip>.mp4 - 640x400 Workbench frames, H.264 + AAC with the scene's phonk from frame 0
(--loops N repeats a looping clip so the seam plays against the continuing music).
Reel: <out>/<reel-name>.mp4 - several clips back to back; jam_loop segments carry the phonk at their beat
position, other clips get the mod's sounds at their pose markers (v2 file names first, current stand-ins
as fallback, missing files skipped).
"""
import json
import math
import os
import shutil
import subprocess
import sys

import bpy
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
FFMPEG_DEFAULT = "C:/Users/calca/Desktop/Video Downloader/_internal/bin/ffmpeg.exe"
CLIPS = ["jam_loop", "interrupted", "idle_loop", "walk_loop", "attack_melee", "attack_ranged", "teleport",
         "summon_illusions", "hurt", "death"]
SIDE_VIEW = {"jam_loop", "interrupted", "walk_loop"}
WITH_BOOTH = {"jam_loop", "interrupted"}
ANTICIPATION = 4                 # contact sheets also show the pose this many frames before every pose marker


def parse_args():
    import argparse
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ap = argparse.ArgumentParser()
    ap.add_argument("--blend", default=os.path.join(REPO, "design", "blender", "joash_v2.blend"))
    ap.add_argument("--out", required=True)
    ap.add_argument("--clips", default=",".join(CLIPS))
    ap.add_argument("--frames", type=int, default=12)
    ap.add_argument("--tile", default="480x300")
    ap.add_argument("--no-sheets", action="store_true")
    ap.add_argument("--video", default="", help="comma list of clips to render as MP4")
    ap.add_argument("--video-res", default="640x400")
    ap.add_argument("--loops", type=int, default=1, help="repeat a looping clip N times in the video")
    ap.add_argument("--music", default="", help="audio for the video (default: the scene's sound strip)")
    ap.add_argument("--audio-events", default="", help="json list of [seconds, path, gain] mixed into the video audio")
    ap.add_argument("--reel", default="", help="clips back to back in one MP4, e.g. 'jam_loop@560-640,interrupted,walk_loop*2'")
    ap.add_argument("--reel-name", default="review_reel")
    ap.add_argument("--ffmpeg", default=FFMPEG_DEFAULT)
    ap.add_argument("--keep-frames", action="store_true")
    return ap.parse_args(argv)


ARGS = parse_args()
bpy.ops.wm.open_mainfile(filepath=os.path.abspath(ARGS.blend))
scene = bpy.context.scene
FPS = scene.render.fps / scene.render.fps_base
RIG_J = bpy.data.objects["rig_joash"]
RIG_B = bpy.data.objects["rig_dj_booth"]
COLL_B = bpy.data.collections["MC_dj_booth"]
CAM = bpy.data.objects["cam_preview"]
os.makedirs(ARGS.out, exist_ok=True)

# ---------------------------------------------------------------- render look
scene.render.engine = "BLENDER_WORKBENCH"
sh = scene.display.shading
sh.light = "STUDIO"
sh.color_type = "MATERIAL"
sh.show_cavity = True
sh.cavity_type = "BOTH"
sh.show_object_outline = True
sh.show_shadows = True
sh.shadow_intensity = 0.35
scene.display.render_aa = "8"
scene.render.film_transparent = False
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGB"
scene.render.use_stamp = True
for attr in ("use_stamp_date", "use_stamp_time", "use_stamp_render_time", "use_stamp_frame", "use_stamp_frame_range",
             "use_stamp_memory", "use_stamp_hostname", "use_stamp_camera", "use_stamp_lens", "use_stamp_scene",
             "use_stamp_marker", "use_stamp_filename", "use_stamp_sequencer_strip", "use_stamp_labels"):
    if hasattr(scene.render, attr):
        setattr(scene.render, attr, False)
scene.render.use_stamp_note = True
scene.render.stamp_font_size = 15
scene.render.stamp_foreground = (1, 1, 1, 1)
scene.render.stamp_background = (0, 0, 0, 0.55)
for ob in bpy.data.objects:
    if ob.type == "ARMATURE":
        ob.hide_render = True

SECTIONS = [(0, "ride"), (8, "scratch"), (16, "hands up"), (20, "phones+knob"), (24, "build"), (28, "drop")]
JAM_ACT = bpy.data.actions.get("joash_jam_loop")
JAM_PHASE = int(JAM_ACT.get("jam_phase_beats", 0)) if JAM_ACT else 0   # clip beat = contract beat + phase


def label(clip, f, L):
    if clip == "jam_loop":
        b = f / 20.0
        cb = (b - JAM_PHASE) % 32                      # contract beat (section 5 numbering)
        sec = [n for b0, n in SECTIONS if cb >= b0][-1]
        return f"jam_loop  f{f:03d}  beat {b:5.2f} (contract b{cb:5.2f})  {sec}"
    act = bpy.data.actions.get("joash_" + clip)
    near = [m.name for m in act.pose_markers if abs(m.frame - f) <= 1] if act else []
    near += [f"before {m.name}" for m in act.pose_markers if 3 <= m.frame - f <= 5] if act and not near else []
    add = "  (additive, shown over idle)" if act and act.get("additive") else ""
    return f"{clip}  f{f:03d}  {f / FPS:4.2f}s" + (f"  [{', '.join(near)}]" if near else "") + add


def clear_nla():
    ad = RIG_J.animation_data
    for t in list(ad.nla_tracks):
        ad.nla_tracks.remove(t)
    ad.action_blend_type = "REPLACE"


def set_clip(clip):
    """Assign the clip; an additive clip (custom property 'additive', e.g. hurt) is shown the way the game plays it:
    on top of idle_loop (temporary NLA track, Combine) - the saved .blend is never touched."""
    act = bpy.data.actions["joash_" + clip]
    clear_nla()
    ad = RIG_J.animation_data
    if act.get("additive"):
        ad.action = None
        tr = ad.nla_tracks.new()
        tr.strips.new("idle_base", 0, bpy.data.actions["joash_idle_loop"])
        ad.action = act
        ad.action_blend_type = "COMBINE"
    else:
        ad.action = act
    bact = bpy.data.actions.get("dj_booth_" + clip) or bpy.data.actions.get("dj_booth_jam_loop")
    RIG_B.animation_data.action = bact
    COLL_B.hide_render = clip not in WITH_BOOTH
    start, end = (int(round(x)) for x in act.frame_range)
    return start, end, act


def look(pos, tgt, lens):
    CAM.location = pos
    CAM.rotation_euler = (Vector(tgt) - Vector(pos)).to_track_quat("-Z", "Y").to_euler()
    CAM.data.lens = lens


def view(clip, kind):
    # lens/distance chosen so the vertical field covers the full 58 px figure + raised arms + orb
    if kind == "side":
        if clip == "walk_loop":
            return (118, -2, 36), (0, -2, 31), 33
        return (132, -7, 40), (0, -7, 35), 44
    if clip in WITH_BOOTH:
        return (58, -104, 72), (1, -8, 38), 41
    if clip == "death":
        return (52, -96, 58), (0, -5, 25), 33
    if clip == "attack_ranged":
        return (50, -96, 70), (-2, -5, 35), 32
    return (50, -94, 66), (0, -2, 31), 32


def render_to(path, res, note):
    scene.render.resolution_x, scene.render.resolution_y = res
    scene.render.resolution_percentage = 100
    scene.render.stamp_note_text = note
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


def load_px(path):
    import numpy as np
    img = bpy.data.images.load(path, check_existing=False)
    w, h = img.size
    a = np.empty(w * h * 4, dtype=np.float32)
    img.pixels.foreach_get(a)
    bpy.data.images.remove(img)
    return a.reshape(h, w, 4)


def save_px(arr, path):
    import numpy as np
    h, w = arr.shape[:2]
    img = bpy.data.images.new("sheet", w, h, alpha=False)
    img.pixels.foreach_set(np.ascontiguousarray(arr, dtype=np.float32).ravel())
    img.filepath_raw = path
    img.file_format = "PNG"
    img.save()
    bpy.data.images.remove(img)


def frames_for(clip, start, end, act, n):
    L = end - start
    if clip == "jam_loop":
        return list(range(0, L, 40))                          # every 2 beats (16 tiles)
    if act.use_cyclic:
        fr = [start + int(round(i * L / n)) for i in range(n)]
    else:
        fr = [start + int(round(i * L / (n - 1))) for i in range(n)]
        must = []                    # every event frame exactly, plus the anticipation pose 4 frames before it
        for m in sorted(act.pose_markers, key=lambda m: m.frame):
            must.append(m.frame)
            if m.frame - ANTICIPATION >= start:
                must.append(m.frame - ANTICIPATION)
        fr = [x for x in fr if all(abs(x - y) > 2 for y in must)] + must
    return sorted(set(fr))


def contact_sheet(clip):
    import numpy as np
    start, end, act = set_clip(clip)
    tw, th = (int(v) for v in ARGS.tile.split("x"))
    kinds = ["front"] + (["side"] if clip in SIDE_VIEW else [])
    frames = frames_for(clip, start, end, act, ARGS.frames)
    tmp = os.path.join(ARGS.out, "_tiles")
    os.makedirs(tmp, exist_ok=True)
    tiles = []
    for f in frames:
        scene.frame_set(f)
        col = []
        for k in kinds:
            pos, tgt, lens = view(clip, k)
            look(pos, tgt, lens)
            p = os.path.join(tmp, f"{clip}_{k}_{f:04d}.png")
            render_to(p, (tw, th), label(clip, f, end - start) + ("" if k == "front" else "  (side)"))
            col.append(load_px(p))
        tiles.append(np.concatenate(col[::-1], axis=0))       # image rows run bottom-up: front on top
    cols = 4
    rows = math.ceil(len(tiles) / cols)
    ch, cw = tiles[0].shape[:2]
    pad = 4
    sheet = np.zeros((rows * (ch + pad) + pad, cols * (cw + pad) + pad, 4), dtype=np.float32)
    sheet[..., 3] = 1.0
    sheet[..., :3] = 0.02
    for i, t in enumerate(tiles):
        r, c = divmod(i, cols)
        y0 = sheet.shape[0] - pad - (r + 1) * (ch + pad) + pad
        x0 = pad + c * (cw + pad)
        sheet[y0:y0 + ch, x0:x0 + cw] = t
    out = os.path.join(ARGS.out, f"{clip}.png")
    save_px(sheet, out)
    shutil.rmtree(tmp, ignore_errors=True)
    return out, frames


def scene_music():
    if ARGS.music:
        return ARGS.music
    se = scene.sequence_editor
    if se:
        for st in se.sequences_all:
            if st.type == "SOUND":
                return bpy.path.abspath(st.sound.filepath)
    return ""


def video(clip):
    start, end, act = set_clip(clip)
    w, h = (int(v) for v in ARGS.video_res.split("x"))
    d = os.path.join(ARGS.out, f"_frames_{clip}")
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d)
    loop = act.use_cyclic
    last = end - 1 if loop else end
    pos, tgt, lens = view(clip, "front")
    look(pos, tgt, lens)
    for i, f in enumerate(range(start, last + 1)):
        scene.frame_set(f)
        render_to(os.path.join(d, f"{i:04d}.png"), (w, h), label(clip, f, end - start))
    n = last - start + 1
    loops = ARGS.loops if loop else 1
    dur = n * loops / FPS
    out = os.path.join(ARGS.out, f"{clip}.mp4")
    music = scene_music()
    cmd = [ARGS.ffmpeg, "-y", "-loglevel", "error"]
    if loops > 1:
        cmd += ["-stream_loop", str(loops - 1)]
    cmd += ["-framerate", str(int(FPS)), "-i", os.path.join(d, "%04d.png")]
    filters, amaps = [], []
    if music and os.path.exists(music):
        cmd += ["-i", music]
        amaps.append("[1:a]")
    events = json.loads(ARGS.audio_events) if ARGS.audio_events else []
    for k, (t, path, gain) in enumerate(events):
        cmd += ["-i", path]
        idx = len(amaps) + 1
        filters.append(f"[{idx}:a]adelay={int(t * 1000)}|{int(t * 1000)},volume={gain}[e{k}]")
        amaps.append(f"[e{k}]")
    if amaps:
        if len(amaps) > 1:
            filters.append("".join(amaps) + f"amix=inputs={len(amaps)}:normalize=0[aout]")
            cmd += ["-filter_complex", ";".join(filters), "-map", "0:v", "-map", "[aout]"]
        else:
            cmd += ["-map", "0:v", "-map", "1:a"]
        cmd += ["-c:a", "aac", "-b:a", "160k"]
    cmd += ["-c:v", "libx264", "-preset", "medium", "-crf", "19", "-pix_fmt", "yuv420p", "-t", f"{dur:.4f}",
            "-movflags", "+faststart", out]
    subprocess.run(cmd, check=True)
    if not ARGS.keep_frames:
        shutil.rmtree(d, ignore_errors=True)
    return out


SOUNDS = os.path.join(REPO, "src", "main", "resources", "assets", "cabinmod", "sounds")
# clip -> [(marker name or seconds, candidate sound files (v2 name first, current stand-in after), gain)]
SFX = {
    "interrupted": [(0.0, ["dj_booth/needle.ogg", "dj_booth/scratch_1.ogg"], 0.9),
                    ("voice", ["joash/welcome_voice.ogg", "joash/welcome.ogg"], 1.0),
                    ("slam", ["dj_booth/slam.ogg"], 1.0),
                    ("slam", ["cabin_block_hit.ogg"], 0.9),          # the cabin scream, as in game
                    ("roar", ["joash/roar_1.ogg"], 1.0),
                    ("move:blink", ["joash/teleport_1.ogg"], 0.8)],
    "idle_loop": [(0.0, ["joash/ambient_1.ogg"], 0.6)],
    "attack_ranged": [(0.0, ["joash/laser_charge.ogg"], 0.8), ("fire", ["joash/laser_fire_1.ogg", "joash/laser_1.ogg"], 0.9)],
    "teleport": [("move:blink", ["joash/teleport_2.ogg"], 0.9)],
    "summon_illusions": [("summon", ["joash/illusions_1.ogg"], 0.9)],
    "hurt": [(0.0, ["joash/hurt_1.ogg"], 0.9)],
    "death": [(0.0, ["joash/death.ogg"], 1.0)],
}


def reel(spec, name):
    """One MP4 of several clips back to back: 'jam_loop@560-640,interrupted,walk_loop*2,...'.
    jam_loop segments play the phonk at their beat-clock position (fading 0.3 s into the next clip);
    other clips get the SFX above at their markers (missing files are skipped)."""
    w, h = (int(v) for v in ARGS.video_res.split("x"))
    d = os.path.join(ARGS.out, f"_frames_{name}")
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d)
    idx, t0, audio = 0, 0.0, []           # audio: (path, delay_s, gain, trim_start, trim_end, fade_out)
    used = []
    for item in [x for x in spec.split(",") if x]:
        reps = 1
        if "*" in item:
            item, r = item.split("*")
            reps = int(r)
        rng = None
        if "@" in item:
            item, rr = item.split("@")
            rng = tuple(int(v) for v in rr.split("-"))
        start, end, act = set_clip(item)
        loop = act.use_cyclic
        a, b = rng if rng else (start, end if not loop else end - 1)
        if rng and loop:
            b -= 1
        pos, tgt, lens = view(item, "front")
        look(pos, tgt, lens)
        for _ in range(reps):
            n0 = idx
            for f in range(a, b + 1):
                scene.frame_set(f)
                render_to(os.path.join(d, f"{idx:04d}.png"), (w, h), label(item, f, end - start))
                idx += 1
            dur = (idx - n0) / FPS
            if item == "jam_loop":
                music = scene_music()
                if music:
                    audio.append((music, t0, 1.0, a / FPS, a / FPS + dur + 0.3, 0.3))
            for when, cands, gain in SFX.get(item, []):
                path = next((os.path.join(SOUNDS, c) for c in cands if os.path.exists(os.path.join(SOUNDS, c))), None)
                if path is None:
                    continue
                if isinstance(when, str):
                    m = next((m for m in act.pose_markers if m.name == when), None)
                    if m is None:
                        continue
                    when = m.frame / FPS
                if a / FPS <= when <= b / FPS + 1e-6:
                    audio.append((path, t0 + when - a / FPS, gain, None, None, None))
                    used.append((item, os.path.relpath(path, SOUNDS), round(t0 + when - a / FPS, 2)))
            t0 += dur
    out = os.path.join(ARGS.out, f"{name}.mp4")
    cmd = [ARGS.ffmpeg, "-y", "-loglevel", "error", "-framerate", str(int(FPS)), "-i", os.path.join(d, "%04d.png")]
    filters, maps = [], []
    for k, (path, delay, gain, ts, te, fade) in enumerate(audio):
        cmd += ["-i", path]
        ms = int(round(delay * 1000))
        chain = f"[{k + 1}:a]"
        if ts is not None:
            chain += f"atrim=start={ts:.4f}:end={te:.4f},asetpts=PTS-STARTPTS,afade=t=out:st={te - ts - fade:.4f}:d={fade:.4f},"
        chain += f"adelay={ms}|{ms},volume={gain}[a{k}]"
        filters.append(chain)
        maps.append(f"[a{k}]")
    if maps:
        filters.append("".join(maps) + f"amix=inputs={len(maps)}:normalize=0[aout]")
        cmd += ["-filter_complex", ";".join(filters), "-map", "0:v", "-map", "[aout]", "-c:a", "aac", "-b:a", "160k"]
    cmd += ["-c:v", "libx264", "-preset", "medium", "-crf", "19", "-pix_fmt", "yuv420p", "-t", f"{t0:.4f}",
            "-movflags", "+faststart", out]
    subprocess.run(cmd, check=True)
    if not ARGS.keep_frames:
        shutil.rmtree(d, ignore_errors=True)
    print("REEL SFX", used, flush=True)
    return out


results = {"sheets": {}, "videos": []}
clips = [c for c in ARGS.clips.split(",") if c]
if not ARGS.no_sheets:
    for clip in clips:
        if bpy.data.actions.get("joash_" + clip):
            path, frames = contact_sheet(clip)
            results["sheets"][clip] = {"image": path, "frames": frames}
            print("SHEET", clip, path, flush=True)
for clip in [c for c in ARGS.video.split(",") if c]:
    results["videos"].append(video(clip))
    print("VIDEO", results["videos"][-1], flush=True)
if ARGS.reel:
    results["videos"].append(reel(ARGS.reel, ARGS.reel_name))
    print("VIDEO", results["videos"][-1], flush=True)
clear_nla()
RIG_J.animation_data.action = bpy.data.actions["joash_jam_loop"]
RIG_B.animation_data.action = bpy.data.actions["dj_booth_jam_loop"]
with open(os.path.join(ARGS.out, "previews.json"), "w", encoding="utf-8") as fh:
    json.dump(results, fh, indent=1)
print("PREVIEWS", json.dumps(results))
