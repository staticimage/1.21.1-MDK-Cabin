"""Build the Jehoash v2 rig, the DJ booth rig and every clip from an empty scene (deterministic).

Contract: design/JEHOASH_V2.md sections 3-5 (+ design/BLENDER_CONVENTIONS.md). Run headless only:

    "E:/Blender Foundation/blender/blender.exe" --background --factory-startup \
        --python tools/blender/build_joash_rig.py -- [--out design/blender/joash_v2.blend] [--report r.json]

What it builds
  rig_joash (origin) + rig_dj_booth (object at (0,-16,0)); bones/cubes exactly as the contract tables,
  bone-parented axis-aligned c_ boxes with flat preview materials; IK (ik_hand/ik_pole/ik_foot/ik_knee),
  hands/feet Copy Rotation from their ik_ target; one Action per clip (joash_<clip>, dj_booth_<clip>),
  pose markers, the phonk as a sequencer sound strip at frame 0, a floor and cam_preview.
  Cloth (phys_*) only gets gentle kinematic draping (gravity hang + leg/torso/floor collision) - the
  game simulates those chains.

Conventions used for keys (all semantic angles are about the ARMATURE axes, in the bone's rest frame):
  pitch = about +X (positive leans/nods forward, swings a hanging limb BACK), yaw = about +Z (positive
  turns to his left = +X), roll = about +Y (positive tilts the top to his left). Every bone is
  axis-aligned at rest, so each semantic axis is exactly one local Euler channel (signed).

Clearance / anatomy passes (rig-side, the contract geometry is unchanged):
  - every clip keys b_orb_pivot with ORB_OUT (x -2.5): the ball floats at x -12.5 instead of the contract
    rest x -10, so its spinning corners clear the right headphone cup (contract change proposed in the notes);
  - the mirror shards orbit in rings whose planes stand beside the head (normals near +-X), never through it;
  - orb_avoid_pass: the ball (and its shards) eases out of the way of the arms/head where they would meet;
  - resolve_hands: every IK hand key is re-solved against the analytic elbow and clamped to anatomical wrist
    ranges (flexion/extension, radial/ulnar deviation, pronation) - contact points stay where they are keyed;
  - the booth gets clearance in the clips that play behind it (lean beside the boom mic, tonearms parked
    off the platters in the jam, robe strands kept out of the cabinet);
  - drape_pass: robe/cape segments are pushed out of leg/arm capsules (sampled along and across each segment);
    shins lying on the floor (kneeling) are draped over, not entered.
Every pass evaluates its clip from a clean rest pose (assign() resets the pose when the action changes), so the
passes see exactly what the saved clip plays.
"""
import json
import math
import os
import random
import re
import sys

import bpy
import numpy as np
from mathutils import Matrix, Quaternion, Vector

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
FPS = 38                      # 20 frames per beat at 114 BPM
FPB = 20


def F(b):
    v = b * FPB
    return int(v) if float(v).is_integer() else v


def SEC(s):
    return int(round(s * FPS))


# =====================================================================================  args
def parse_args():
    import argparse
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(REPO, "design", "blender", "joash_v2.blend"))
    ap.add_argument("--music", default=os.path.join(REPO, "src", "main", "resources", "assets", "cabinmod",
                                                     "sounds", "music", "disco.ogg"))
    ap.add_argument("--contract", default=os.path.join(REPO, "design", "JEHOASH_V2.md"))
    ap.add_argument("--report", default="")
    ap.add_argument("--no-save", action="store_true")
    ap.add_argument("--no-validate", action="store_true")
    ap.add_argument("--clips", default="", help="comma list (debug: build only these joash clips)")
    return ap.parse_args(argv)


ARGS = parse_args()

# =====================================================================================  contract data
def mir_name(n):
    return re.sub(r"_l(?=_|$)", "_r", n)


def mir_v(v):
    return (-v[0], v[1], v[2])


def mir_rng(r):
    return (-r[1], -r[0])


# ---- rig_joash bones: (name, head, tail, parent)
J_CORE = [
    ("b_root", (0, 0, 0), (0, 0, 6), None),
    ("b_hips", (0, 0, 26), (0, 0, 28), "b_root"),
    ("b_spine", (0, 0, 28), (0, 0, 35), "b_hips"),
    ("b_chest", (0, 0, 35), (0, 0, 44), "b_spine"),
    ("b_neck", (0, 0, 44), (0, 0, 47.5), "b_chest"),
    ("b_head", (0, 0, 47.5), (0, 0, 56), "b_neck"),
]
J_LEFT = [
    ("b_leg_upper_l", (3, 0, 24), (3, 0, 12.5), "b_hips"),
    ("b_leg_lower_l", (3, 0, 12.5), (3, -0.5, 2), "b_leg_upper_l"),
    ("b_foot_l", (3, -0.5, 2), (3, -4, 2), "b_leg_lower_l"),
    ("b_shoulder_l", (2, 0, 42.5), (6.5, 0, 42.5), "b_chest"),
    ("b_arm_upper_l", (6.5, 0, 42.5), (6.5, 0, 30.5), "b_shoulder_l"),
    ("b_arm_lower_l", (6.5, 0, 30.5), (6.5, 0, 20.5), "b_arm_upper_l"),
    ("b_hand_l", (6.5, 0, 20.5), (6.5, 0, 17.5), "b_arm_lower_l"),
    ("b_finger_index_l_1", (7.35, 0, 17.5), (7.35, 0, 15.8), "b_hand_l"),
    ("b_finger_index_l_2", (7.35, 0, 15.8), (7.35, 0, 14.5), "b_finger_index_l_1"),
    ("b_finger_middle_l_1", (6.5, 0, 17.5), (6.5, 0, 15.6), "b_hand_l"),
    ("b_finger_middle_l_2", (6.5, 0, 15.6), (6.5, 0, 14.2), "b_finger_middle_l_1"),
    ("b_finger_ring_l_1", (5.65, 0, 17.5), (5.65, 0, 15.9), "b_hand_l"),
    ("b_finger_ring_l_2", (5.65, 0, 15.9), (5.65, 0, 14.8), "b_finger_ring_l_1"),
    # thumb_1 tail kept vertical (bones must stay axis-aligned); thumb_2 head is the contract pivot
    ("b_thumb_l_1", (6.0, -0.65, 19.0), (6.0, -0.65, 17.6), "b_hand_l"),
    ("b_thumb_l_2", (6.0, -1.0, 17.6), (6.0, -1.0, 16.6), "b_thumb_l_1"),
]
J_MISC = [
    ("b_orb_pivot", (-10, 2, 52), (-10, 2, 56), "b_root"),
    ("b_orb", (-10, 2, 52), (-10, 2, 55), "b_orb_pivot"),
    ("b_orb_sat_1", (-3.5, 2, 52), (-2, 2, 52), "b_orb_pivot"),
    ("b_orb_sat_2", (-10, 2, 58.5), (-10, 2, 60), "b_orb_pivot"),
    ("b_orb_sat_3", (-16.5, 2, 52), (-18, 2, 52), "b_orb_pivot"),
    ("loc_orb", (-10, 2, 52), (-10, -1, 52), "b_orb"),
    ("loc_mouth", (0, -4.2, 50), (0, -5.2, 50), "b_head"),
    ("loc_hand_r_item", (-6.5, 0, 18), (-6.5, 0, 16), "b_hand_r"),
]
J_IK = [
    ("ik_hand_l", (6.5, 0, 20.5), (6.5, 0, 17.5), "b_root"),
    ("ik_hand_r", (-6.5, 0, 20.5), (-6.5, 0, 17.5), "b_root"),
    ("ik_pole_l", (6.5, 12, 30.5), (6.5, 12, 28.5), "b_chest"),
    ("ik_pole_r", (-6.5, 12, 30.5), (-6.5, 12, 28.5), "b_chest"),
    ("ik_foot_l", (3, -0.5, 2), (3, -4, 2), "b_root"),
    ("ik_foot_r", (-3, -0.5, 2), (-3, -4, 2), "b_root"),
    ("ik_knee_l", (3, -30, 12.5), (3, -30, 10.5), "b_root"),
    ("ik_knee_r", (-3, -30, 12.5), (-3, -30, 10.5), "b_root"),
]

# ---- cloth chains
CAPE = []        # (chain, [bone names])
J_CLOTH = []
for side, sn in ((1, "l"), (-1, "r")):
    names = []
    for i in range(6):
        z = 43.5 - 5 * i
        n = f"phys_cape_{sn}_{i + 1}"
        J_CLOTH.append((n, (side * 2.5, 3.4, z), (side * 2.5, 3.4, z - 5), "b_chest" if i == 0 else names[-1]))
        names.append(n)
    CAPE.append((f"cape_{sn}", names))
ROBE_STRANDS = (  # id, x, y, size_x, size_y
    [(f"f{i + 1}", x, -3.0, 3.0, 0.8) for i, x in enumerate((-3.2, 0.0, 3.2))]
    + [(f"b{i + 1}", x, 3.0, 3.0, 0.8) for i, x in enumerate((-3.2, 0.0, 3.2))]
    + [(f"l{i + 1}", 5.0, y, 0.8, 2.4) for i, y in enumerate((-2.0, 0.0, 2.0))]
    + [(f"r{i + 1}", -5.0, y, 0.8, 2.4) for i, y in enumerate((-2.0, 0.0, 2.0))]
    + [("c1", 4.6, -2.8, 1.2, 1.2), ("c2", -4.6, -2.8, 1.2, 1.2), ("c3", 4.6, 2.8, 1.2, 1.2), ("c4", -4.6, 2.8, 1.2, 1.2)]
)
ROBE = []
for sid, x, y, sx, sy in ROBE_STRANDS:
    names = []
    for i in range(4):
        z = 24.5 - 5.5 * i
        n = f"phys_robe_{sid}_{i + 1}"
        J_CLOTH.append((n, (x, y, z), (x, y, z - 5.5), "b_hips" if i == 0 else names[-1]))
        names.append(n)
    ROBE.append((f"robe_{sid}", names))

J_BONES = (J_CORE + J_LEFT + [(mir_name(n), mir_v(h), mir_v(t), mir_name(p)) for n, h, t, p in J_LEFT]
           + J_MISC + J_CLOTH + J_IK)

# ---- rig_joash cubes: (name, bone, xr, yr, zr, material)
J_CUBES_L = [
    ("c_joash_thigh_l", "b_leg_upper_l", (1.5, 4.5), (-1.5, 1.5), (12.5, 24.5), "black"),
    ("c_joash_shin_l", "b_leg_lower_l", (1.75, 4.25), (-1.25, 1.25), (2, 12.5), "black"),
    ("c_joash_foot_l", "b_foot_l", (1.5, 4.5), (-4, 1.5), (0, 2), "black"),
    ("c_joash_hp_cup_l", "b_head", (5, 6.5), (-1.75, 1.75), (50, 54.5), "pink"),
    ("c_joash_shoulder_l", "b_shoulder_l", (4.5, 8), (-2, 2), (41.5, 44.5), "robe"),
    ("c_joash_upperarm_l", "b_arm_upper_l", (5.25, 7.75), (-1.25, 1.25), (30.5, 42.5), "robe"),
    ("c_joash_forearm_l", "b_arm_lower_l", (5.4, 7.6), (-1.1, 1.1), (20.5, 30.5), "robe"),
    ("c_joash_palm_l", "b_hand_l", (5.25, 7.75), (-0.65, 0.65), (17.5, 20.5), "skin"),
    ("c_joash_finger_index_l_1", "b_finger_index_l_1", (7.0, 7.7), (-0.35, 0.35), (15.8, 17.5), "skin"),
    ("c_joash_finger_index_l_2", "b_finger_index_l_2", (7.0, 7.7), (-0.35, 0.35), (14.5, 15.8), "skin"),
    ("c_joash_finger_middle_l_1", "b_finger_middle_l_1", (6.15, 6.85), (-0.35, 0.35), (15.6, 17.5), "skin"),
    ("c_joash_finger_middle_l_2", "b_finger_middle_l_2", (6.15, 6.85), (-0.35, 0.35), (14.2, 15.6), "skin"),
    ("c_joash_finger_ring_l_1", "b_finger_ring_l_1", (5.3, 6.0), (-0.35, 0.35), (15.9, 17.5), "skin"),
    ("c_joash_finger_ring_l_2", "b_finger_ring_l_2", (5.3, 6.0), (-0.35, 0.35), (14.8, 15.9), "skin"),
    ("c_joash_thumb_l_1", "b_thumb_l_1", (5.65, 6.35), (-1.35, -0.65), (17.6, 19.0), "skin"),
    ("c_joash_thumb_l_2", "b_thumb_l_2", (5.65, 6.35), (-1.35, -0.65), (16.6, 17.6), "skin"),
]
J_CUBES = [
    ("c_joash_pelvis", "b_hips", (-4.5, 4.5), (-2.5, 2.5), (23, 28), "robe"),
    ("c_joash_belly", "b_spine", (-4, 4), (-2.5, 2.5), (28, 35), "robe"),
    ("c_joash_chest", "b_chest", (-5.5, 5.5), (-3, 3), (35, 44), "robe"),
    ("c_joash_neck", "b_neck", (-1.5, 1.5), (-1.5, 1.5), (44, 47.5), "skin"),
    ("c_joash_head", "b_head", (-4, 4), (-4, 4), (47.5, 55.5), "skin"),
    ("c_joash_hood", "b_head", (-5, 5), (-5, 5), (46.5, 57.5), "robe"),
    ("c_joash_hp_band", "b_head", (-5.5, 5.5), (-0.75, 0.75), (57.5, 58.5), "black"),
] + J_CUBES_L + [(mir_name(n), mir_name(b), mir_rng(x), y, z, m) for n, b, x, y, z, m in J_CUBES_L] + [
    ("c_joash_orb", "b_orb", (-13, -7), (-1, 5), (49, 55), "mirror"),
    ("c_joash_orb_sat_1", "b_orb_sat_1", (-4.1, -2.9), (1.4, 2.6), (51.4, 52.6), "mirror"),
    ("c_joash_orb_sat_2", "b_orb_sat_2", (-10.6, -9.4), (1.4, 2.6), (57.9, 59.1), "mirror"),
    ("c_joash_orb_sat_3", "b_orb_sat_3", (-17.1, -15.9), (1.4, 2.6), (51.4, 52.6), "mirror"),
]
for chain, names in CAPE:
    side = 1 if chain.endswith("_l") else -1
    for i, n in enumerate(names):
        z = 43.5 - 5 * i
        xr = (0.25, 4.75) if side > 0 else (-4.75, -0.25)
        J_CUBES.append((n.replace("phys_", "c_joash_"), n, xr, (3.1, 3.7), (z - 5, z), "black"))
for (sid, x, y, sx, sy), (chain, names) in zip(ROBE_STRANDS, ROBE):
    for i, n in enumerate(names):
        z = 24.5 - 5.5 * i
        J_CUBES.append((n.replace("phys_", "c_joash_"), n, (x - sx / 2, x + sx / 2), (y - sy / 2, y + sy / 2),
                        (z - 5.5, z), "robe"))
OPEN_FRONT = {"c_joash_hood"}      # contract: hood has an open front -> preview mesh drops the -Y face

# ---- rig_dj_booth (booth space; object sits at (0,-16,0))
B_LEFT = [
    ("b_deck_l", (14, 1.5, 26), (14, 1.5, 28), "b_cabinet"),
    ("b_platter_l", (14, 1.5, 28), (14, 1.5, 29.5), "b_deck_l"),
    ("b_tonearm_l_base", (19.5, 6, 28), (19.5, 6, 30), "b_deck_l"),
    ("b_tonearm_l", (19.5, 6, 30), (15, 6, 30), "b_tonearm_l_base"),
]
B_BONES = [
    ("b_root", (0, 0, 0), (0, 0, 6), None),
    ("b_cabinet", (0, 0, 0), (0, 0, 12), "b_root"),
] + B_LEFT + [(mir_name(n), mir_v(h), mir_v(t), mir_name(p)) for n, h, t, p in B_LEFT] + [
    ("b_mixer", (0, 1, 26), (0, 1, 29), "b_cabinet"),
    ("b_fader_1", (-2.5, 2, 29), (-2.5, 2, 30.3), "b_mixer"),
    ("b_fader_2", (2.5, 2, 29), (2.5, 2, 30.3), "b_mixer"),
    ("b_xfader", (0, -3, 29), (0, -3, 30.2), "b_mixer"),
] + [(f"b_knob_{i + 1}", (x, 5.5, 29), (x, 5.5, 30.2), "b_mixer") for i, x in enumerate((-3.5, -1.2, 1.2, 3.5))] + [
    ("b_laptop", (0, 7, 29.5), (0, 7, 35), "b_cabinet"),
    ("b_mic_stand", (-21.5, 6.5, 26), (-21.5, 6.5, 48), "b_cabinet"),
    ("b_mic_boom", (-21.5, 6.5, 48), (-4, 6.5, 48), "b_mic_stand"),
    ("b_mic", (-4, 6.5, 48), (-4, 6.5, 44.5), "b_mic_boom"),
]
B_CUBES_L = [
    ("c_booth_deck_l", "b_deck_l", (6.5, 21.5), (-4.5, 7.5), (26, 28), "metal"),
    ("c_booth_platter_l_1", "b_platter_l", (8.5, 19.5), (-1, 4), (28, 28.8), "vinyl"),
    ("c_booth_platter_l_2", "b_platter_l", (9.5, 18.5), (-3, 6), (28, 28.8), "vinyl"),
    ("c_booth_platter_l_3", "b_platter_l", (11.5, 16.5), (-4, 7), (28, 28.8), "vinyl"),
    ("c_booth_label_l", "b_platter_l", (13, 15), (0.5, 2.5), (28.8, 29), "pink"),
    ("c_booth_tonearm_base_l", "b_tonearm_l_base", (18.5, 20.5), (5, 7), (28, 30.5), "metal"),
    ("c_booth_tonearm_l", "b_tonearm_l", (15, 19.5), (5.6, 6.4), (29.8, 30.4), "metal"),
    ("c_booth_headshell_l", "b_tonearm_l", (14.2, 15.4), (5.2, 6.8), (29.6, 30.4), "metal"),
]
B_CUBES = [
    ("c_booth_cabinet", "b_cabinet", (-23, 23), (-6, 8), (0, 25), "black"),
    ("c_booth_top", "b_cabinet", (-23.5, 23.5), (-6.5, 8.5), (25, 26), "metal"),
] + B_CUBES_L + [(mir_name(n), mir_name(b), mir_rng(x), y, z, m) for n, b, x, y, z, m in B_CUBES_L] + [
    ("c_booth_mixer", "b_mixer", (-6, 6), (-5, 7), (26, 29), "black"),
    ("c_booth_fader_1", "b_fader_1", (-3.1, -1.9), (1.4, 2.6), (29, 30.3), "cyan"),
    ("c_booth_fader_2", "b_fader_2", (1.9, 3.1), (1.4, 2.6), (29, 30.3), "cyan"),
    ("c_booth_xfader", "b_xfader", (-0.7, 0.7), (-3.6, -2.4), (29, 30.2), "pink"),
] + [(f"c_booth_knob_{i + 1}", f"b_knob_{i + 1}", (x - 0.5, x + 0.5), (5, 6), (29, 30.2), "cyan")
     for i, x in enumerate((-3.5, -1.2, 1.2, 3.5))] + [
    ("c_booth_laptop_base", "b_laptop", (-4, 4), (4.5, 8.5), (29, 29.6), "metal"),
    ("c_booth_laptop_screen", "b_laptop", (-4, 4), (8.2, 8.7), (29.6, 35.2), "cyan"),
    ("c_booth_mic_pole", "b_mic_stand", (-22, -21), (6, 7), (26, 48), "metal"),
    ("c_booth_mic_boom", "b_mic_boom", (-21.5, -4), (6.1, 6.9), (47.6, 48.4), "metal"),
    ("c_booth_mic", "b_mic", (-5, -3), (5.5, 7.5), (44.5, 48), "black"),
]

BOOTH_OFFSET = Vector((0, -16, 0))
# hand targets in Jehoash space (section 4)
T_PLATTER_L = Vector((14, -14.5, 28.8))
T_PLATTER_R = Vector((-14, -14.5, 28.8))
T_FADER_1 = Vector((-2.5, -14, 30.3))
T_FADER_2 = Vector((2.5, -14, 30.3))
T_XFADER = Vector((0, -19, 30.2))
T_KNOB_3 = Vector((1.2, -10.5, 30.2))
T_CUP_L = Vector((7.2, 0, 52.3))
T_MIC = Vector((-4, -9.5, 46))        # listed in the contract; no clip in section 5 grabs the mic
T_EDGE_L = Vector((10, -21.5, 26))
T_EDGE_R = Vector((-10, -21.5, 26))
# rig-side clearance offsets (see the module docstring)
ORB_OUT = Vector((-2.5, 0.0, 0.0))     # added to every b_orb_pivot location key (raw=True keys skip it)
TONEARM_PARK = 115.0                   # deg the tonearms swing out to the booth corners (off the records, clear of his
                                       # forearms and elbows) while he DJs
BOOTH_BACK_Y = -7.5                    # Jehoash-space y of the booth's back face (top slab); cloth stays behind it
# The phonk's drops land on track beats 32 / 192 (and its breakdowns on 80 / 160), i.e. on loop beat 0 of the
# contract's beat clock (jam_loop time = (beats mod 32) x 60/114 s). The routine is choreographed in contract
# beats (section 5: b0 ride ... b28 drop) and then every jam key (Jehoash + booth) is rotated by this many beats,
# so the b24-27 build plays over the risers and the b28 fling hits the drop. Set to 0 if DiscoMusic.jamMillis
# gets the -4 beat phase instead.
JAM_PHASE_BEATS = 4

MATERIALS = {   # category -> (rgb, metallic, roughness)
    "skin": ((0.80, 0.69, 0.60), 0.0, 0.6),
    "robe": ((0.33, 0.11, 0.47), 0.0, 0.7),
    "black": ((0.045, 0.045, 0.055), 0.0, 0.5),
    "pink": ((1.0, 0.12, 0.62), 0.0, 0.4),
    "cyan": ((0.08, 0.88, 0.95), 0.0, 0.4),
    "metal": ((0.56, 0.58, 0.63), 0.8, 0.35),
    "vinyl": ((0.025, 0.025, 0.03), 0.0, 0.25),
    "mirror": ((0.82, 0.86, 0.95), 0.25, 0.15),
    "floor": ((0.16, 0.15, 0.18), 0.0, 0.9),
}

# =====================================================================================  scene + rigs
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.fps = FPS
scene.render.fps_base = 1.0
scene.unit_settings.system = "NONE"
scene.frame_start = 0
scene.frame_end = F(32) - 1


def new_coll(name):
    c = bpy.data.collections.new(name)
    scene.collection.children.link(c)
    return c


COLL_J = new_coll("MC_joash")
COLL_B = new_coll("MC_dj_booth")
COLL_P = new_coll("preview")

MATS = {}
for key, (rgb, met, rough) in MATERIALS.items():
    m = bpy.data.materials.new(f"mat_{key}")
    m.diffuse_color = (*rgb, 1.0)
    m.metallic = met
    m.roughness = rough
    MATS[key] = m


def make_rig(name, coll, location, bones):
    arm = bpy.data.armatures.new(name)
    rig = bpy.data.objects.new(name, arm)
    coll.objects.link(rig)
    rig.location = location
    rig.show_in_front = True
    arm.display_type = "OCTAHEDRAL"
    bpy.context.view_layer.update()
    for o in bpy.context.view_layer.objects:
        o.select_set(False)
    bpy.context.view_layer.objects.active = rig
    rig.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    ebs = {}
    for n, h, t, p in bones:
        eb = arm.edit_bones.new(n)
        eb.head = h
        eb.tail = t
        eb.roll = 0.0
        eb.use_deform = not n.startswith(("ik_", "ctl_"))
        ebs[n] = eb
    for n, h, t, p in bones:
        if p:
            eb = ebs[n]
            eb.parent = ebs[p]
            eb.use_connect = (Vector(h) - ebs[p].tail).length < 1e-6
    bpy.ops.object.mode_set(mode="OBJECT")
    rig.select_set(False)
    return rig


RIG_J = make_rig("rig_joash", COLL_J, (0, 0, 0), J_BONES)
RIG_B = make_rig("rig_dj_booth", COLL_B, tuple(BOOTH_OFFSET), B_BONES)


def bone_groups(rig, groups):
    arm = rig.data
    for gname, pred in groups:
        bc = arm.collections.new(gname)
        for b in arm.bones:
            if pred(b.name):
                bc.assign(b)


bone_groups(RIG_J, [("body", lambda n: n.startswith("b_") and "finger" not in n and "thumb" not in n),
                    ("fingers", lambda n: "finger" in n or "thumb" in n),
                    ("cloth", lambda n: n.startswith("phys_")),
                    ("locators", lambda n: n.startswith("loc_")),
                    ("ik", lambda n: n.startswith(("ik_", "ctl_")))])
bone_groups(RIG_B, [("booth", lambda n: True)])


# ---- axis maps + rotation modes
def axis_map(bone):
    """World axis w -> (local axis index, sign). Exact for axis-aligned bones; the shins (contract foot
    head at y=-0.5) tilt 2.7 deg in YZ, where X stays exact and Y/Z are a close approximation."""
    M = bone.matrix_local.to_3x3()
    out = []
    for w in range(3):
        row = [M[w][j] for j in range(3)]
        j = max(range(3), key=lambda k: abs(row[k]))
        if abs(row[j]) < 0.99:
            raise RuntimeError(f"{bone.name} is not (nearly) axis-aligned at rest")
        out.append((j, 1.0 if row[j] > 0 else -1.0))
    return out


AMAP = {}
for rig in (RIG_J, RIG_B):
    for b in rig.data.bones:
        AMAP[(rig.name, b.name)] = axis_map(b)


def world_order(rig, n):
    if rig is RIG_B:
        return (0, 1, 2)
    if n in ("b_root", "b_hips", "b_spine", "b_chest", "b_neck", "b_head"):
        return (1, 0, 2)          # roll, pitch, yaw (yaw outermost)
    if "finger" in n or "thumb" in n:
        return (0, 1, 2)          # curl, spread, twist
    if n.startswith(("b_orb", "loc_")):
        return (2, 0, 1)          # spin first, then tilt
    return (2, 1, 0)              # limbs: twist, abduct, flex (flex outermost)


for rig in (RIG_J, RIG_B):
    for pb in rig.pose.bones:
        if pb.name.startswith(("ik_", "phys_")):
            pb.rotation_mode = "QUATERNION"
        else:
            am = AMAP[(rig.name, pb.name)]
            pb.rotation_mode = "".join("XYZ"[am[w][0]] for w in world_order(rig, pb.name))

# ---- constraints
pbj = RIG_J.pose.bones
for sn in ("l", "r"):
    ik = pbj[f"b_arm_lower_{sn}"].constraints.new("IK")
    ik.name = "IK"
    ik.target, ik.subtarget = RIG_J, f"ik_hand_{sn}"
    ik.pole_target, ik.pole_subtarget = RIG_J, f"ik_pole_{sn}"
    ik.chain_count = 2
    ik.use_stretch = False
    cr = pbj[f"b_hand_{sn}"].constraints.new("COPY_ROTATION")
    cr.name = "CopyRot"
    cr.target, cr.subtarget = RIG_J, f"ik_hand_{sn}"
    cr.target_space = cr.owner_space = "WORLD"
    ik = pbj[f"b_leg_lower_{sn}"].constraints.new("IK")
    ik.name = "IK"
    ik.target, ik.subtarget = RIG_J, f"ik_foot_{sn}"
    ik.pole_target, ik.pole_subtarget = RIG_J, f"ik_knee_{sn}"
    ik.chain_count = 2
    ik.use_stretch = False
    cr = pbj[f"b_foot_{sn}"].constraints.new("COPY_ROTATION")
    cr.name = "CopyRot"
    cr.target, cr.subtarget = RIG_J, f"ik_foot_{sn}"
    cr.target_space = cr.owner_space = "WORLD"
RIG_J.pose.ik_solver = "LEGACY"

# ---- cubes
def make_cube(rig, coll, name, bone, xr, yr, zr, mat):
    me = bpy.data.meshes.new(name)
    v = [(x, y, z) for x in (-0.5, 0.5) for y in (-0.5, 0.5) for z in (-0.5, 0.5)]
    faces = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    if name in OPEN_FRONT:
        faces = [f for f in faces if f != (0, 4, 5, 1)]       # -Y face
    me.from_pydata(v, [], faces)
    me.update()
    me.materials.append(MATS[mat])
    ob = bpy.data.objects.new(name, me)
    coll.objects.link(ob)
    ob.parent = rig
    ob.parent_type = "BONE"
    ob.parent_bone = bone
    bpy.context.view_layer.update()
    c = Vector(((xr[0] + xr[1]) / 2, (yr[0] + yr[1]) / 2, (zr[0] + zr[1]) / 2))
    s = (xr[1] - xr[0], yr[1] - yr[0], zr[1] - zr[0])
    ob.matrix_world = rig.matrix_world @ Matrix.Translation(c) @ Matrix.Diagonal((*s, 1.0))
    if name in OPEN_FRONT:
        ob["open_front"] = True
    return ob


RIG_J.data.pose_position = RIG_B.data.pose_position = "REST"
bpy.context.view_layer.update()
for spec in J_CUBES:
    make_cube(RIG_J, COLL_J, *spec)
for spec in B_CUBES:
    make_cube(RIG_B, COLL_B, *spec)
RIG_J.data.pose_position = RIG_B.data.pose_position = "POSE"
bpy.context.view_layer.update()

# ---- pole-angle calibration (empirical: axis-aligned straight chains -> one of 0/90/180/-90)
def chain_bend(rig, upper, lower, pole):
    pb = rig.pose.bones
    a, e, t = pb[upper].head, pb[lower].head, pb[lower].tail
    ax = (t - a).normalized()
    d = e - a
    d -= ax * d.dot(ax)
    p = pb[pole].head - a
    p -= ax * p.dot(ax)
    if d.length < 1e-6 or p.length < 1e-6:
        return 0.0, 0.0
    return d.length, d.normalized().dot(p.normalized())


LEG_PREBEND = 6.0       # deg of knee flex seeding the IK (rest knee is 0.26 px hyperextended)


def calibrate():
    out = {}
    tests = {   # (lower bone, upper, target, pole, target offset (world), prebend bone)
        "arm_l": ("b_arm_lower_l", "b_arm_upper_l", "ik_hand_l", "ik_pole_l", Vector((1, -9, 4)), "b_arm_lower_l"),
        "arm_r": ("b_arm_lower_r", "b_arm_upper_r", "ik_hand_r", "ik_pole_r", Vector((-1, -9, 4)), "b_arm_lower_r"),
        "leg_l": ("b_leg_lower_l", "b_leg_upper_l", "ik_foot_l", "ik_knee_l", Vector((0, 0, 3)), "b_leg_lower_l"),
        "leg_r": ("b_leg_lower_r", "b_leg_upper_r", "ik_foot_r", "ik_knee_r", Vector((0, 0, 3)), "b_leg_lower_r"),
    }
    for key, (lo, up, tg, pole, off, pre) in tests.items():
        am = AMAP[(RIG_J.name, tg)]
        pbt = pbj[tg]
        loc = [0.0, 0.0, 0.0]
        for w in range(3):
            j, sg = am[w]
            loc[j] = sg * off[w]
        pbt.location = loc
        pp = pbj[pre]
        prebend_euler(pp, 2.0 if "arm" in key else -LEG_PREBEND)
        best = None
        for ang in (0, 90, 180, -90):
            pbj[lo].constraints["IK"].pole_angle = math.radians(ang)
            bpy.context.view_layer.update()
            mag, dot = chain_bend(RIG_J, up, lo, pole)
            # twist: the upper bone's local Z must keep pointing back (+Y) like its rest/FK frame,
            # otherwise painted fronts end up on the back of the limb
            zax = pbj[up].matrix.to_3x3() @ Vector((0, 0, 1))
            score = dot + (0.5 if zax.y > 0 else -0.5)
            if best is None or score > best[3]:
                best = (ang, round(dot, 4), round(zax.y, 4), score)
        pbj[lo].constraints["IK"].pole_angle = math.radians(best[0])
        out[key] = best
        pbt.location = (0, 0, 0)
        pp.rotation_euler = (0, 0, 0)
    bpy.context.view_layer.update()
    return out


def prebend_euler(pb, flex_deg):
    """Small FK flex so the straight contract chain gives the IK solver a bend to start from.
    Arms: forward flex (pitch -); legs: knee flex (shin swings back, pitch +)."""
    am = AMAP[(RIG_J.name, pb.name)]
    j, sg = am[0]
    e = [0.0, 0.0, 0.0]
    e[j] = sg * math.radians(-flex_deg)
    pb.rotation_euler = e


POLE_CAL = calibrate()

# =====================================================================================  key DSL
AX = {"x": 0, "y": 1, "z": 2}


def Rx(d):
    return Matrix.Rotation(math.radians(d), 3, "X")


def Ry(d):
    return Matrix.Rotation(math.radians(d), 3, "Y")


def Rz(d):
    return Matrix.Rotation(math.radians(d), 3, "Z")


IP = {  # interpolation codes
    "A": ("BEZIER", "AUTO_CLAMPED", None),
    "V": ("BEZIER", "VECTOR", None),
    "L": ("LINEAR", None, None),
    "C": ("CONSTANT", None, None),
    "S": ("QUART", None, "EASE_OUT"),        # snappy: fast start, settles
    "X": ("EXPO", None, "EASE_OUT"),
    "SI": ("QUART", None, "EASE_IN"),
    "QI": ("QUAD", None, "EASE_IN"),         # falling
    "QO": ("QUAD", None, "EASE_OUT"),
    "E": ("SINE", None, "EASE_IN_OUT"),
    "B": ("BACK", None, "EASE_OUT"),         # overshoot
}


class Clip:
    def __init__(self, rig, name, length, loop=False, markers=()):
        self.rig = rig
        self.name = name
        self.L = int(length)
        self.loop = loop
        self.markers = list(markers)
        self.keys = {}          # (path, idx) -> {frame: (value, ip)}
        self.qkeys = {}         # bone -> {frame: (Quaternion, ip)}
        self.offset = set()     # channels that accumulate (spins): no wrap, cycles REPEAT_OFFSET
        self.checks = []        # contact checks for validation
        self.hreq = {}          # (side, frame) -> hand key request, re-solved by resolve_hands()
        self.anat_log = []
        self.act = bpy.data.actions.new(name)
        self.act.use_fake_user = True
        self.ik_arms = True

    # -- raw
    def _f(self, f):
        f = float(f)
        if self.loop:
            f = f % self.L
            if abs(f - self.L) < 1e-6:
                f = 0.0
        return round(f, 4)

    def _put(self, path, idx, f, v, ip, wrap=True):
        ff = self._f(f) if wrap else round(float(f), 4)
        self.keys.setdefault((path, idx), {})[ff] = (float(v), ip)

    def _am(self, b, w):
        return AMAP[(self.rig.name, b)][w]

    # -- semantic channels
    def rot(self, b, axis, f, deg, ip="A"):
        j, sg = self._am(b, AX[axis])
        self._put(f'pose.bones["{b}"].rotation_euler', j, f, sg * math.radians(deg), ip)

    def spin(self, b, axis, f, deg, ip="L"):       # accumulating rotation (not wrapped)
        j, sg = self._am(b, AX[axis])
        key = (f'pose.bones["{b}"].rotation_euler', j)
        self.offset.add(key)
        self._put(key[0], j, f, sg * math.radians(deg), ip, wrap=False)

    def loc(self, b, axis, f, v, ip="A", raw=False):
        if b == "b_orb_pivot" and self.rig is RIG_J and not raw:
            v = v + ORB_OUT[AX[axis]]          # the ball floats 2.5 px further out than the contract rest
        j, sg = self._am(b, AX[axis])
        self._put(f'pose.bones["{b}"].location', j, f, sg * v, ip)

    def loc3(self, b, f, v, ip="A"):
        for i, a in enumerate("xyz"):
            self.loc(b, a, f, v[i], ip)

    def scl(self, b, axis, f, v, ip="A"):
        j, _ = self._am(b, AX[axis])
        self._put(f'pose.bones["{b}"].scale', j, f, v, ip)

    def quat(self, b, f, R, ip="A"):
        bone = self.rig.data.bones[b]
        qr = bone.matrix_local.to_quaternion()
        qw = R.to_quaternion() if not isinstance(R, Quaternion) else R
        q = qr.inverted() @ qw @ qr
        self.qkeys.setdefault(b, {})[self._f(f)] = (q, ip)

    def quat_local(self, b, f, q, ip="A"):
        self.qkeys.setdefault(b, {})[self._f(f)] = (q.copy(), ip)

    def target(self, b, f, pos, R, ip="A"):
        rest = self.rig.data.bones[b].head_local
        self.loc3(b, f, Vector(pos) - rest, ip)
        self.quat(b, f, R, ip)

    def infl(self, b, con, f, v, ip="A"):
        self._put(f'pose.bones["{b}"].constraints["{con}"].influence', 0, f, v, ip)

    def has(self, path, idx=0):
        return (path, idx) in self.keys

    def remove_range(self, path_prefix, f0, f1):
        """Drop keys of channels starting with path_prefix inside [f0, f1] (for later passes)."""
        for (path, idx), d in self.keys.items():
            if path.startswith(path_prefix):
                for k in [k for k in d if f0 - 1e-6 <= k <= f1 + 1e-6]:
                    del d[k]
        m = re.match(r'pose\.bones\["ik_hand_([lr])"\]', path_prefix)
        if m:
            s = 1 if m.group(1) == "l" else -1
            for key in [k for k in self.hreq if k[0] == s and f0 - 1e-6 <= k[1] <= f1 + 1e-6]:
                del self.hreq[key]

    def remove_qrange(self, b, f0, f1):
        d = self.qkeys.get(b, {})
        for k in [k for k in d if f0 - 1e-6 <= k <= f1 + 1e-6]:
            del d[k]

    # -- build F-curves
    def finalize(self):
        act = self.act
        for fc in list(act.fcurves):
            act.fcurves.remove(fc)
        chans = dict(self.keys)
        for b, d in self.qkeys.items():
            frames = sorted(d)
            prev = None
            fixed = {}
            for f in frames:
                q, ip = d[f]
                if prev is not None and prev.dot(q) < 0:
                    q = -q
                fixed[f] = (q, ip)
                prev = q
            for i in range(4):
                chans[(f'pose.bones["{b}"].rotation_quaternion', i)] = {f: (fixed[f][0][i], fixed[f][1]) for f in frames}
        for (path, idx), d in sorted(chans.items()):
            frames = sorted(d)
            if not frames:
                continue
            vals = [d[f] for f in frames]
            if self.loop and (path, idx) not in self.offset:
                frames = frames + [frames[0] + self.L]
                vals = vals + [vals[0]]
            grp = path.split('"')[1] if '"' in path else "misc"
            fc = act.fcurves.new(path, index=idx, action_group=grp)
            fc.keyframe_points.add(len(frames))
            co = []
            for f, (v, _) in zip(frames, vals):
                co += [f, v]
            fc.keyframe_points.foreach_set("co", co)
            for kp, (v, ip) in zip(fc.keyframe_points, vals):
                interp, handle, easing = IP[ip]
                kp.interpolation = interp
                if handle:
                    kp.handle_left_type = kp.handle_right_type = handle
                if easing:
                    kp.easing = easing
            if self.loop:
                m = fc.modifiers.new("CYCLES")
                if (path, idx) in self.offset:
                    m.mode_before = m.mode_after = "REPEAT_OFFSET"
            fc.update()
        act.use_frame_range = True
        act.frame_start = 0
        act.frame_end = self.L
        act.use_cyclic = self.loop
        for m in list(act.pose_markers):
            act.pose_markers.remove(m)
        for name, f in self.markers:
            mk = act.pose_markers.new(name)
            mk.frame = int(f)

    def check(self, f, side, grip, target, label, contract=None, surface=None):
        """Contact check for validation. surface: the hand rests on a surface at that height - then the error is
        the palm contact's horizontal distance from the target, or how far the hand's lowest point is below the
        surface / more than 0.45 px above it."""
        self.checks.append(dict(frame=int(round(f)), side=side, grip=grip, target=tuple(target), label=label,
                                contract=tuple(contract) if contract is not None else None, surface=surface))

    def rotate(self, df):
        """Shift every key of a looping clip by df frames (wrapped at L). Accumulating spin channels are left
        alone: callers only rotate by whole spin periods, where they are unchanged."""
        assert self.loop
        L = self.L

        def sh(f):
            v = round((f + df) % L, 4)
            return 0.0 if abs(v - L) < 1e-6 else v

        for key, d in list(self.keys.items()):
            if key not in self.offset:
                self.keys[key] = {sh(f): v for f, v in d.items()}
        for b, d in list(self.qkeys.items()):
            self.qkeys[b] = {sh(f): v for f, v in d.items()}
        self.hreq = {(s, sh(f)): v for (s, f), v in self.hreq.items()}
        for ch in self.checks:
            ch["frame"] = int(round(sh(ch["frame"])))

    def raw_value(self, path, idx, f):
        """Evaluated F-curve value (needs finalize())."""
        fc = self.act.fcurves.find(path, index=idx)
        return fc.evaluate(f) if fc else None


def assign(rig, act):
    """Assign an action for evaluation. Switching actions first resets the pose to rest: channels the new action does
    not key would otherwise keep the previous action's values (the clean-up passes would then work on a pose that
    fill_missing() later changes)."""
    ad = rig.animation_data or rig.animation_data_create()
    if ad.action is not act:
        for pb in rig.pose.bones:
            pb.location = (0.0, 0.0, 0.0)
            pb.rotation_quaternion = (1.0, 0.0, 0.0, 0.0)
            pb.rotation_euler = (0.0, 0.0, 0.0)
            pb.scale = (1.0, 1.0, 1.0)
    ad.action = act


def eval_frame(f):
    fi = int(math.floor(f + 1e-6))
    sub = float(f) - fi
    if sub > 1e-4:
        scene.frame_set(fi, subframe=sub)
    else:
        scene.frame_set(fi)


# =====================================================================================  hand library
def mv(v, s):
    return Vector((s * v[0], v[1], v[2]))


def orient(fdir, phint):
    """Hand rotation (armature axes): rest -Z (wrist->knuckles) -> fdir, rest -Y (palm normal) -> phint."""
    f = Vector(fdir).normalized()
    p = Vector(phint)
    p = (p - f * p.dot(f)).normalized()
    ry, rz = -p, -f
    rx = ry.cross(rz)
    return Matrix((rx, ry, rz)).transposed()




BONE_HEAD = {n: Vector(h) for n, h, t, p in J_BONES}
BONE_TAIL = {n: Vector(t) for n, h, t, p in J_BONES}
CUBE_BY_BONE = {}
for n, b, xr, yr, zr, m in J_CUBES:
    CUBE_BY_BONE.setdefault(b, []).append((xr, yr, zr))


def sname(s):
    return "l" if s > 0 else "r"


def wrist0(s):
    return Vector((6.5 * s, 0, 20.5))


# finger poses (canonical): finger -> (curl1, curl2, spread toward index side); thumb -> (curl1, curl2, swing)
FP = {
    "flat": dict(index=(7, 5, 10), middle=(6, 5, 0), ring=(7, 6, -10), thumb=(4, 4, 16)),
    "ride": dict(index=(-16, 6, 12), middle=(-16, 6, 0), ring=(-14, 8, -12), thumb=(-4, 4, 18)),
    "relax": dict(index=(16, 22, 4), middle=(19, 25, 0), ring=(23, 28, -4), thumb=(10, 10, 10)),
    "scratch": dict(index=(-4, 14, 6), middle=(-2, 16, 0), ring=(2, 18, -6), thumb=(6, 8, 16)),
    "pinch": dict(index=(50, 45, -6), middle=(72, 88, 0), ring=(82, 92, 4), thumb=(0, 0, 0)),   # thumb solved below
    "fist": dict(index=(96, 102, 0), middle=(98, 104, 0), ring=(100, 106, 0), thumb=(44, 30, 34)),
    "point": dict(index=(-2, 0, 4), middle=(96, 102, 0), ring=(100, 106, 0), thumb=(42, 34, 30)),
    "claw": dict(index=(40, 62, 14), middle=(42, 66, 0), ring=(46, 70, -14), thumb=(24, 36, 22)),
    "splay": dict(index=(-14, -8, 26), middle=(-12, -6, 0), ring=(-14, -8, -26), thumb=(-6, -4, 44)),
    "grip": dict(index=(80, 64, 0), middle=(82, 66, 0), ring=(84, 68, 0), thumb=(30, 22, 26)),
    "press": dict(index=(12, 10, 6), middle=(12, 10, 0), ring=(14, 12, -6), thumb=(8, 8, 18)),
    "cup": dict(index=(30, 30, 4), middle=(32, 32, 0), ring=(34, 34, -4), thumb=(20, 18, 24)),
    "phone": dict(index=(14, 10, 6), middle=(14, 10, 0), ring=(16, 12, -6), thumb=(-18, -8, 28)),   # palm on a cup, thumb off it
}


def finger_bones(s):
    n = sname(s)
    return {"index": (f"b_finger_index_{n}_1", f"b_finger_index_{n}_2"),
            "middle": (f"b_finger_middle_{n}_1", f"b_finger_middle_{n}_2"),
            "ring": (f"b_finger_ring_{n}_1", f"b_finger_ring_{n}_2"),
            "thumb": (f"b_thumb_{n}_1", f"b_thumb_{n}_2")}


def seg_rots(s, finger, v):
    c1, c2, sp = v
    q1 = Ry(-s * sp) @ Rx(-c1)        # curl (about X) first, then spread (about Y)
    q2 = Rx(-c2)
    return q1, q2


def hand_fk(s, pose):
    """Finger segment transforms in hand-rest space: bone -> (head, Rchain)."""
    out = {}
    for finger, (b1, b2) in finger_bones(s).items():
        q1, q2 = seg_rots(s, finger, pose[finger])
        h1, h2 = BONE_HEAD[b1], BONE_HEAD[b2]
        h2p = h1 + q1 @ (h2 - h1)
        out[b1] = (h1, q1)
        out[b2] = (h2p, q1 @ q2)
    return out


def tip(s, pose, finger):
    fk = hand_fk(s, pose)
    b2 = finger_bones(s)[finger][1]
    h, R = fk[b2]
    return h + R @ (BONE_TAIL[b2] - BONE_HEAD[b2])


PINCH_GAP = 1.45      # index tip <-> thumb tip (bone tails): a 1.2 px cap fits between the 0.7 px fingers


def solve_pinch_thumb(s, pose):
    target = tip(s, pose, "index")
    best = None
    for c1 in range(-30, 71, 2):
        for sw in range(-10, 71, 2):
            p = dict(pose)
            p["thumb"] = (c1, c1 * 0.7, sw)
            t = tip(s, p, "thumb")
            d = (t - target).length
            if best is None or abs(d - PINCH_GAP) < best[0]:
                best = (abs(d - PINCH_GAP), (c1, round(c1 * 0.7, 2), sw))
    return best[1]


FP["pinch"]["thumb"] = solve_pinch_thumb(1, FP["pinch"])




def contact_local(s, grip, pose=None):
    """Grip contact point relative to the wrist, in hand-rest axes."""
    if grip == "palm":
        return Vector((0, -0.65, -1.5))
    if grip == "palmc":
        return Vector((0, 0, -1.5))
    if grip == "palmk":
        return Vector((0, -0.65, -2.6))
    if grip == "pinch":
        p = FP["pinch"]
        return (tip(s, p, "index") + tip(s, p, "thumb")) / 2 - wrist0(s)
    raise KeyError(grip)


def hand_corners(s, pose):
    """All hand cube corners (palm + fingers) in hand-rest space, relative to the wrist."""
    W0 = wrist0(s)
    pts = []
    n = sname(s)
    for xr, yr, zr in CUBE_BY_BONE[f"b_hand_{n}"]:
        pts += [Vector((x, y, z)) - W0 for x in xr for y in yr for z in zr]
    fk = hand_fk(s, pose)
    for b, (h, R) in fk.items():
        for xr, yr, zr in CUBE_BY_BONE[b]:
            for x in xr:
                for y in yr:
                    for z in zr:
                        pts.append(h + R @ (Vector((x, y, z)) - BONE_HEAD[b]) - W0)
    return pts


def extreme_local(s, pose, R, direction):
    d = Vector(direction).normalized()
    return max(hand_corners(s, pose), key=lambda p: (R @ p).dot(d))


def key_fingers(c, s, f, pose, ip="A", extra=None):
    """Key one finger pose; extra = {finger: (dc1, dc2, dsp)} added on top."""
    if isinstance(pose, str):
        pose = FP[pose]
    for finger, (b1, b2) in finger_bones(s).items():
        c1, c2, sp = pose[finger]
        if extra and finger in extra:
            e = extra[finger]
            c1, c2, sp = c1 + e[0], c2 + e[1], sp + e[2]
        c.rot(b1, "x", f, -c1, ip)
        c.rot(b1, "y", f, -s * sp, ip)
        c.rot(b2, "x", f, -c2, ip)


def blend_pose(a, b, t):
    a, b = (FP[a] if isinstance(a, str) else a), (FP[b] if isinstance(b, str) else b)
    return {k: tuple(a[k][i] * (1 - t) + b[k][i] * t for i in range(3)) for k in a}


RIDE_FDIR = (-0.25, -0.93, -0.30)


def ride_rot(s):
    """Hand on a platter: fingers forward-in and slightly down, heel of the hand lifted."""
    return orient(mv(RIDE_FDIR, s), mv((0.0, -0.3, -1.0), s))


def wrist_for(s, contact, R, grip, pose=None):
    if grip == "wrist":
        return Vector(contact)
    if grip == "extreme":
        loc = extreme_local(s, pose, R, Vector((0, 0, -1)))
        return Vector(contact) - R @ loc
    return Vector(contact) - R @ contact_local(s, grip, pose)


def surface_lift(s, W, R, pose, surface):
    """Raise the wrist so no corner of the palm/fingers (finger pose `pose`) dips below z = surface + 0.05."""
    if surface is None or pose is None:
        return W
    p = FP[pose] if isinstance(pose, str) else pose
    mz = W.z + min((R @ q).z for q in hand_corners(s, p))
    if mz < surface + 0.05:
        W = W + Vector((0, 0, surface + 0.05 - mz))
    return W


def key_hand(c, s, f, contact, R=None, grip="palm", ip="A", pose=None, anat=None, lim=None, surface=None):
    """Place ik_hand so the grip's contact point lands on `contact` with hand orientation R.
    The key is also recorded as a request: resolve_hands() re-solves it against the analytic elbow, either
    clamping R into the anatomical wrist range or (anat=(flex, dev, pron)) building it from the forearm.
    surface: z of the surface under the hand; the whole hand (finger pose `pose`) is kept on top of it."""
    if R is None:
        R = orient((0, 0, -1), (0, -1, 0))       # placeholder until resolve_hands()
    W = surface_lift(s, wrist_for(s, contact, R, grip, pose), R, pose, surface)
    c.target(f"ik_hand_{sname(s)}", f, W, R, ip)
    c.hreq[(s, c._f(f))] = dict(contact=Vector(contact), R=R.copy(), grip=grip, ip=ip, pose=pose, anat=anat, lim=lim,
                                surface=surface)
    return W


def key_pole(c, s, f, p, ip="A"):
    c.loc3(f"ik_pole_{sname(s)}", f, mv(p, s) - BONE_HEAD[f"ik_pole_{sname(s)}"], ip)


def key_foot(c, s, f, pos, R=None, ip="A"):
    c.target(f"ik_foot_{sname(s)}", f, pos, R if R is not None else Matrix.Identity(3), ip)


def ik_on(c, f=0, arms=1.0, legs=1.0):
    for sn in ("l", "r"):
        c.infl(f"b_arm_lower_{sn}", "IK", f, arms, "C")
        c.infl(f"b_hand_{sn}", "CopyRot", f, arms, "C")
        c.infl(f"b_leg_lower_{sn}", "IK", f, legs, "C")
        c.infl(f"b_foot_{sn}", "CopyRot", f, legs, "C")
        if arms > 0.5:
            c.rot(f"b_arm_lower_{sn}", "x", f, -2.0, "C")     # IK pre-bend seed (straight contract chain)
        if legs > 0.5:
            c.rot(f"b_leg_lower_{sn}", "x", f, LEG_PREBEND, "C")


# FK arm helper: flex (forward +), abd (outward +), twist (outward +), elbow flex (+)
def key_arm_fk(c, s, f, flex=0, abd=0, twist=0, elbow=0, wflex=0, wdev=0, wtwist=0, ip="A"):
    n = sname(s)
    c.rot(f"b_arm_upper_{n}", "x", f, -flex, ip)
    c.rot(f"b_arm_upper_{n}", "y", f, -s * abd, ip)
    c.rot(f"b_arm_upper_{n}", "z", f, s * twist, ip)
    c.rot(f"b_arm_lower_{n}", "x", f, -elbow, ip)
    c.rot(f"b_hand_{n}", "x", f, -wflex, ip)
    c.rot(f"b_hand_{n}", "y", f, -s * wdev, ip)
    c.rot(f"b_hand_{n}", "z", f, s * wtwist, ip)


# =====================================================================================  anatomy: wrists
# Wrist angles relative to the forearm (hand rest: fingers -Z, palm normal -Y, radial = index side = +X on the left):
#   flex  + toward the palm, - extension;  dev  + radial (index side), - ulnar;
#   pron  palm normal vs the elbow's anterior side: 0 = palm faces the upper arm (full supination, = the rest
#         pose's palm-forward), 90 = neutral (palm medial), 180 = full pronation.
ARM_L1, ARM_L2 = 12.0, 10.0
WRIST_LIM = dict(flex=(-60.0, 70.0), dev=(-30.0, 18.0), pron=(-5.0, 170.0))


def arm_solve(S, W, P):
    """Analytic 2-bone arm IK: elbow position for shoulder S, wrist target W, pole P (Blender's IK bends the
    chain toward the pole; the pole angle is calibrated for exactly that)."""
    u = W - S
    d = max(1e-3, min(u.length, ARM_L1 + ARM_L2 - 1e-3))
    u.normalize()
    a = (ARM_L1 ** 2 - ARM_L2 ** 2 + d * d) / (2 * d)
    h = math.sqrt(max(0.0, ARM_L1 ** 2 - a * a))
    v = P - S
    v -= u * v.dot(u)
    if v.length < 1e-6:
        v = Vector((0, 1, 0)) - u * u.y
    v.normalize()
    return S + u * a + v * h


def forearm_frame(s, S, E, W, P):
    f = (W - E).normalized()
    ant = S - E
    ant -= f * ant.dot(f)
    alt = E - P                        # nearly straight arm: anterior = away from the pole side
    alt -= f * alt.dot(f)
    if alt.length > 1e-6:
        ant = ant + alt.normalized() * 0.05
    ant.normalize()
    med = f.cross(ant) * s
    return f, ant, med


def anat_build(s, fr, flex, dev, pron):
    f, ant, med = fr
    ps, a, b = (math.radians(x) for x in (pron, flex, dev))
    p0 = ant * math.cos(ps) + med * math.sin(ps)
    d1 = f * math.cos(a) + p0 * math.sin(a)
    n = p0 * math.cos(a) - f * math.sin(a)
    r1 = n.cross(d1) * s
    d = d1 * math.cos(b) + r1 * math.sin(b)
    return orient(d, n)


def anat_decompose(s, fr, R):
    f, ant, med = fr
    d = R @ Vector((0, 0, -1))
    n = R @ Vector((0, -1, 0))
    sa = max(-1.0, min(1.0, -n.dot(f)))
    a = math.asin(sa)
    p0 = n + f * sa
    if p0.length < 1e-6:
        p0 = ant.copy()
    p0.normalize()
    ps = math.degrees(math.atan2(p0.dot(med), p0.dot(ant)))
    if ps < -90:
        ps += 360
    d1 = f * math.cos(a) + p0 * math.sin(a)
    r1 = n.cross(d1) * s
    b = math.degrees(math.atan2(d.dot(r1), d.dot(d1)))
    return math.degrees(a), b, ps


def clamp(v, lo_hi):
    return max(lo_hi[0], min(lo_hi[1], v))


def resolve_hands(c, iters=5):
    """Re-key every recorded IK hand request: elbow from the evaluated shoulder + pole (analytic IK), hand
    orientation clamped into WRIST_LIM (or built from anat=(flex, dev, pron)), wrist re-placed so the grip's
    contact point stays on its target."""
    if not c.hreq or c.rig is not RIG_J:
        return
    c.finalize()
    assign(RIG_J, c.act)
    pb = RIG_J.pose.bones
    log = []
    for (s, f) in sorted(c.hreq, key=lambda k: (k[1], k[0])):
        rq = c.hreq[(s, f)]
        eval_frame(f)
        n = sname(s)
        S = pb[f"b_arm_upper_{n}"].head.copy()
        P = pb[f"ik_pole_{n}"].head.copy()
        lim = rq["lim"] or WRIST_LIM
        R = rq["R"]
        W = wrist_for(s, rq["contact"], R, rq["grip"], rq["pose"])
        before = after = None
        for _ in range(iters):
            E = arm_solve(S, W, P)
            fr = forearm_frame(s, S, E, W, P)
            if rq["anat"] is not None:
                R2 = anat_build(s, fr, *rq["anat"])
                before = after = tuple(rq["anat"])
            else:
                fl, dv, pr = anat_decompose(s, fr, R)
                fl2, dv2, pr2 = clamp(fl, lim["flex"]), clamp(dv, lim["dev"]), clamp(pr, lim["pron"])
                before, after = (fl, dv, pr), (fl2, dv2, pr2)
                R2 = R if (abs(fl2 - fl) + abs(dv2 - dv) + abs(pr2 - pr)) < 1e-6 else anat_build(s, fr, fl2, dv2, pr2)
            W = surface_lift(s, wrist_for(s, rq["contact"], R2, rq["grip"], rq["pose"]), R2, rq["pose"], rq.get("surface"))
        c.target(f"ik_hand_{n}", f, W, R2, rq["ip"])
        moved = math.degrees(R.to_quaternion().rotation_difference(R2.to_quaternion()).angle)
        log.append(dict(frame=f, side=n, req=tuple(round(x, 1) for x in before), used=tuple(round(x, 1) for x in after),
                        clamp_deg=round(moved if moved <= 180 else 360 - moved, 1), reach=round((W - S).length, 2)))
    c.anat_log = log
    c.finalize()


def wrist_fix_pass(c, tol=3.0, iters=3):
    """Between hand keys the interpolated IK hand rotation can leave the anatomical wrist range for a frame or
    two (fast moves between very different grips). Key the clamped orientation on exactly those frames (the
    wrist stays where it is) and repeat until none is left."""
    if c.rig is not RIG_J:
        return 0
    total = 0
    frames = list(range(0, c.L)) if c.loop else list(range(0, c.L + 1))
    for it in range(iters):
        c.finalize()
        assign(RIG_J, c.act)
        pb = RIG_J.pose.bones
        fixes = []
        for f in frames:
            eval_frame(f)
            for s in (1, -1):
                n = sname(s)
                if pb[f"b_arm_lower_{n}"].constraints["IK"].influence < 0.5:
                    continue
                S, E, W = pb[f"b_arm_upper_{n}"].head.copy(), pb[f"b_arm_lower_{n}"].head.copy(), pb[f"b_hand_{n}"].head.copy()
                P = pb[f"ik_pole_{n}"].head.copy()
                fr = forearm_frame(s, S, E, W, P)
                R = pb[f"b_hand_{n}"].matrix.to_3x3().normalized() @ RIG_J.data.bones[f"b_hand_{n}"].matrix_local.to_3x3().transposed()
                fl, dv, pr = anat_decompose(s, fr, R)
                cl = (clamp(fl, WRIST_LIM["flex"]), clamp(dv, WRIST_LIM["dev"]), clamp(pr, WRIST_LIM["pron"]))
                if abs(cl[0] - fl) + abs(cl[1] - dv) + abs(cl[2] - pr) > tol:
                    Rt = pb[f"ik_hand_{n}"].matrix.to_3x3().normalized() @ RIG_J.data.bones[f"ik_hand_{n}"].matrix_local.to_3x3().transposed()
                    Tw = pb[f"ik_hand_{n}"].head.copy()
                    fixes.append((f, s, Tw, anat_build(s, fr, *cl)))
        if not fixes:
            break
        for f, s, Tw, R2 in fixes:
            c.target(f"ik_hand_{sname(s)}", f, Tw, R2, "A")
        total += len(fixes)
    c.finalize()
    return total


# =====================================================================================  orb helpers
SAT_REST = {1: Vector((6.5, 0, 0)), 2: Vector((0, 0, 6.5)), 3: Vector((-6.5, 0, 0))}   # contract rest offsets


def _ring(normal):
    """Orbit plane basis (e1 = 'up' in the ring, e2) for a ring with the given normal."""
    n = Vector(normal).normalized()
    e1 = Vector((0, 0, 1)) - n * n.z
    e1.normalize()
    return e1, n.cross(e1)


# Rings stand beside the head: normals within ~25 deg of +-X, so a shard never swings more than ~0.43 r toward
# the hood (the contract rest of sat_1 at x -3.5 is inside the head; every clip keys the shards onto these rings).
SAT_PLANES = {
    1: _ring(Rz(26) @ Vector((1, 0, 0))),
    2: _ring(Ry(-24) @ Vector((1, 0, 0))),
    3: _ring(Rz(-18) @ Ry(16) @ Vector((-1, 0, 0))),
}
SAT_R = 7.0          # orbit radius: shard centre 7 px from the ball centre (ball corner 5.2 + shard corner 1.04 + gap)


def _opt_sat_phases():
    """Phases/directions so the three shards never meet where their rings cross (they share one angle clock in
    every clip): maximise the smallest pairwise angle over a full turn (deterministic grid search)."""
    E = {k: (np.array(SAT_PLANES[k][0]), np.array(SAT_PLANES[k][1])) for k in (1, 2, 3)}
    t = np.linspace(0, 2 * np.pi, 240, endpoint=False)
    ph = np.radians(np.arange(0, 360, 6))
    best = None
    for d2 in (1, -1):
        for d3 in (1, -1):
            a1 = t[None, None, :]
            P1 = E[1][0][None, None, None, :] * np.cos(a1)[..., None] + E[1][1][None, None, None, :] * np.sin(a1)[..., None]
            a2 = ph[:, None, None] + d2 * t[None, None, :]
            P2 = E[2][0] * np.cos(a2)[..., None] + E[2][1] * np.sin(a2)[..., None]          # (n2,1,T,3)
            a3 = ph[None, :, None] + d3 * t[None, None, :]
            P3 = E[3][0] * np.cos(a3)[..., None] + E[3][1] * np.sin(a3)[..., None]          # (1,n3,T,3)
            d12 = np.linalg.norm(P1 - P2, axis=-1).min(axis=-1)                              # (n2,1)
            d13 = np.linalg.norm(P1 - P3, axis=-1).min(axis=-1)                              # (1,n3)
            d23 = np.linalg.norm(P2 - P3, axis=-1).min(axis=-1)                              # (n2,n3)
            score = np.minimum(np.minimum(d12, d13), d23)
            i, j = np.unravel_index(int(np.argmax(score)), score.shape)
            if best is None or score[i, j] > best[0] + 1e-9:
                best = (float(score[i, j]), d2, d3, float(ph[i]), float(ph[j]))
    return best


_SATB = _opt_sat_phases()
SAT_PHASE = {1: 0.0, 2: _SATB[3], 3: _SATB[4]}
SAT_DIR = {1: 1, 2: _SATB[1], 3: _SATB[2]}
SAT_MIN_CHORD = _SATB[0]         # closest approach of two shards, in orbit radii


def sat_offset(k, angle, radius):
    e1, e2 = SAT_PLANES[k]
    return (e1 * math.cos(angle) + e2 * math.sin(angle)) * radius


def key_sats(c, frames, angle_fn, radius_fn, ip="A", world_fn=None):
    """Key satellite locations (relative to their rest offsets from the pivot) at the given frames."""
    for f in frames:
        for k in (1, 2, 3):
            if world_fn is not None:
                off = world_fn(k, f)
            else:
                off = sat_offset(k, SAT_PHASE[k] + SAT_DIR[k] * angle_fn(f), radius_fn(k, f))
            c.loc3(f"b_orb_sat_{k}", f, off - SAT_REST[k], ip)


# =====================================================================================  clip: idle_loop
IDLE_L = SEC(3.0)                # 114


def idle_values(t):
    """Idle channel values at frame t (cyclic 114)."""
    ph = 2 * math.pi * t / 57.0          # 1.5 s bob
    br = 2 * math.pi * t / 114.0         # 3 s breath / head roll
    return dict(
        hips_z=-1.0 + 0.8 * math.cos(ph), hips_x=0.25 * math.sin(br), hips_yaw=1.5 * math.sin(br),
        spine_p=6 + 0.8 * math.cos(ph + 0.6), chest_p=5 - 2.0 * math.cos(br), chest_roll=0.0,
        sh_z=0.35 + 0.35 * math.cos(br), neck_p=-2.0, head_p=-7 + 1.5 * math.cos(ph + 1.0),
        head_roll=8 * math.sin(br), head_yaw=0.0,
    )


def idle_pose(c, f, t=0.0, ip="A"):
    """Key the full idle base pose (as at idle frame t) at frame f of another clip."""
    v = idle_values(t)
    c.loc("b_hips", "z", f, v["hips_z"], ip)
    c.loc("b_hips", "x", f, v["hips_x"], ip)
    c.loc("b_hips", "y", f, 0.0, ip)
    c.rot("b_hips", "z", f, v["hips_yaw"], ip)
    c.rot("b_hips", "x", f, 0.0, ip)
    c.rot("b_hips", "y", f, 0.0, ip)
    c.rot("b_spine", "x", f, v["spine_p"], ip)
    c.rot("b_spine", "z", f, 0.0, ip)
    c.rot("b_spine", "y", f, 0.0, ip)
    c.rot("b_chest", "x", f, v["chest_p"], ip)
    c.rot("b_chest", "z", f, 0.0, ip)
    c.rot("b_chest", "y", f, v["chest_roll"], ip)
    c.rot("b_neck", "x", f, v["neck_p"], ip)
    c.rot("b_neck", "z", f, 0.0, ip)
    c.rot("b_neck", "y", f, 0.0, ip)
    c.rot("b_head", "x", f, v["head_p"], ip)
    c.rot("b_head", "y", f, v["head_roll"], ip)
    c.rot("b_head", "z", f, v["head_yaw"], ip)
    for s in (1, -1):
        n = sname(s)
        c.loc(f"b_shoulder_{n}", "z", f, v["sh_z"], ip)
        c.rot(f"b_shoulder_{n}", "y", f, 0.0, ip)
        key_foot(c, s, f, IDLE_FOOT[s], IDLE_FOOT_R[s], ip)


IDLE_FOOT = {1: Vector((4.0, -0.9, 2.0)), -1: Vector((-4.0, -0.9, 2.0))}
IDLE_FOOT_R = {1: Rz(-8), -1: Rz(8)}           # toes slightly out
IDLE_ARM = dict(flex=14, abd=9, twist=12, elbow=38, wflex=12, wdev=-4)


def idle_arms(c, f, t=0.0, ip="A"):
    for s in (1, -1):
        ph = 2 * math.pi * (t - 4) / 57.0 + (0 if s > 0 else 0.5)
        key_arm_fk(c, s, f, flex=IDLE_ARM["flex"] + 2.5 * math.cos(ph), abd=IDLE_ARM["abd"],
                   twist=IDLE_ARM["twist"], elbow=IDLE_ARM["elbow"] + 3 * math.cos(ph + 0.5),
                   wflex=IDLE_ARM["wflex"] + 3 * math.cos(ph + 1.0), wdev=IDLE_ARM["wdev"], ip=ip)


def idle_fingers(c, f, t=0.0, ip="A"):
    for s in (1, -1):
        base = FP["claw"]
        ex = {}
        for i, fg in enumerate(("index", "middle", "ring", "thumb")):
            ph = 2 * math.pi * t / 57.0 - i * 0.7 + (0 if s > 0 else math.pi)
            a = 10 * math.sin(ph)
            ex[fg] = (a, a * 1.2, 0)
        key_fingers(c, s, f, base, ip, extra=ex)


def orb_idle(c, frames, period=57.0, bob=1.2, f0=0.0):
    for f in frames:
        t = f - f0
        c.loc("b_orb_pivot", "z", f, bob * math.sin(2 * math.pi * t / 57.0 + 0.8))
        c.loc("b_orb_pivot", "x", f, 0.6 * math.sin(2 * math.pi * t / 114.0))
        c.loc("b_orb_pivot", "y", f, 0.0)
    key_sats(c, frames, lambda f: 2 * math.pi * (f - f0) / period, lambda k, f: SAT_R + 0.3 * math.sin(2 * math.pi * (f - f0) / 57.0))


def build_idle():
    c = Clip(RIG_J, "joash_idle_loop", IDLE_L, loop=True)
    c.ik_arms = False
    ik_on(c, 0, arms=0.0, legs=1.0)
    L = IDLE_L
    # hips bob (lowest at 28.5/85.5), sway, yaw: keys at quarter periods
    for f in [i * 14.25 for i in range(8)]:
        v = idle_values(f)
        c.loc("b_hips", "z", f, v["hips_z"])
    for f in [i * 28.5 for i in range(4)]:
        v = idle_values(f)
        c.loc("b_hips", "x", f, v["hips_x"])
        c.rot("b_hips", "z", f, v["hips_yaw"])
        c.rot("b_chest", "x", f, v["chest_p"])
        c.loc("b_shoulder_l", "z", f, v["sh_z"])
        c.loc("b_shoulder_r", "z", f, v["sh_z"])
    for f in [i * 14.25 for i in range(8)]:
        v = idle_values(f)
        c.rot("b_spine", "x", f, v["spine_p"])
        c.rot("b_head", "x", f, v["head_p"])
    c.loc("b_hips", "y", 0, 0.0)
    for b in ("b_hips", "b_spine", "b_chest", "b_neck"):
        for a in ("x", "y", "z"):
            if not c.has(f'pose.bones["{b}"].rotation_euler', AMAP[(RIG_J.name, b)][AX[a]][0]):
                c.rot(b, a, 0, 0.0)
    c.rot("b_neck", "x", 0, -2.0)
    # head roll: slow +-8 with eldritch 2-frame 12 deg snaps at 1.1 s and 2.4 s
    for f in [i * 14.25 for i in range(8)]:
        if not (36 < f < 50) and not (86 < f < 100):
            c.rot("b_head", "y", f, idle_values(f)["head_roll"])
    t1, t2 = SEC(1.1), SEC(2.4)          # 42, 91
    c.rot("b_head", "y", t1 - 2, idle_values(t1 - 2)["head_roll"])
    c.rot("b_head", "y", t1, idle_values(t1)["head_roll"] + 12, "C")
    c.rot("b_head", "y", t1 + 2, idle_values(t1 + 2)["head_roll"] + 9, "A")
    c.rot("b_head", "y", t1 + 7, idle_values(t1 + 7)["head_roll"])
    c.rot("b_head", "z", 0, 0.0)
    c.rot("b_head", "z", t2 - 2, 0.0)
    c.rot("b_head", "z", t2, -12, "C")
    c.rot("b_head", "z", t2 + 2, -9, "A")
    c.rot("b_head", "z", t2 + 8, 0.0)
    c.rot("b_head", "y", t2 - 2, idle_values(t2 - 2)["head_roll"])
    c.rot("b_head", "y", t2 + 6, idle_values(t2 + 6)["head_roll"])
    for s in (1, -1):
        n = sname(s)
        c.rot(f"b_shoulder_{n}", "y", 0, 0.0)
        key_foot(c, s, 0, IDLE_FOOT[s], IDLE_FOOT_R[s])
    for f in [i * 14.25 for i in range(8)]:
        idle_arms(c, f, f)
    for f in [i * 9.5 for i in range(12)]:
        idle_fingers(c, f, f)
    orb_idle(c, list(range(0, L, 2)))
    c.rot("b_orb", "x", 0, 15.0)
    c.spin("b_orb", "z", 0, 0.0)
    c.spin("b_orb", "z", L, -360.0)
    for k in (1, 2, 3):
        c.spin(f"b_orb_sat_{k}", "z", 0, 0.0)
        c.spin(f"b_orb_sat_{k}", "z", L, 360.0 * (2 if k != 2 else -2))
        c.rot(f"b_orb_sat_{k}", "x", 0, 35.0 * k)
    return c


# =====================================================================================  clip: jam_loop (+ booth)
JAM_L = F(32)          # 640


def sect(b):
    if b < 8:
        return "ride"
    if b < 16:
        return "scratch"
    if b < 20:
        return "up"
    if b < 24:
        return "phones"
    if b < 28:
        return "build"
    return "drop"


# shared timeline functions (Jehoash hands <-> booth parts stay in sync)
def scratch1_s(k):            # b8..15.5 baby scratch on 8ths: k = 0..15
    return -1 if k % 2 == 0 else 1


def fader2_y(b):              # jam-space y of fader_2 cap
    if b <= 0:
        return -14.0
    if b < 7.5:
        steps = math.floor(b)
        frac = min(1.0, (b - steps) / 0.5)
        frac = 0.5 - 0.5 * math.cos(math.pi * frac)
        return -14.0 + 2.0 * (steps + frac) / 7.5
    if b < 30.75:
        return -12.0
    if b < 31.25:
        u = (b - 30.75) / 0.5
        return -12.0 - 2.0 * (0.5 - 0.5 * math.cos(math.pi * u))
    return -14.0


def knob3_angle(b):         # booth knob_3 (deg, clockwise = negative about +Z)
    if b < 20:
        return 0.0
    if b < 24:
        step = math.floor(b - 20)
        u = min(1.0, (b - 20 - step) / 0.5)
        return -22.5 * (step + 0.5 - 0.5 * math.cos(math.pi * u))
    if b < 29.75:
        return -90.0
    if b < 30.25:
        u = (b - 29.75) / 0.5
        return -90.0 + 90.0 * (0.5 - 0.5 * math.cos(math.pi * u))
    return 0.0


CUP_OUT = 0.85                  # palm centre this far outside the contract cup point (the thumb base sits under the palm)
PINCH_Z = {"fader": 30.35, "xfader": 30.25, "knob": 30.3}   # pinch the caps near their tops (knob tops stand 0.6 px
                                                             # above the laptop base that covers them - contract geometry)
KNOB_R = orient((0.46, -0.08, -0.88), (-0.82, 0.05, -0.57))   # right hand down onto knob_3 from his right side
KNOB_POLE = (16, -14, 38)      # elbow out to his right and forward: the forearm passes in front of the screen


XF_PATTERNS = [[0, 1, 0, 1, 0, 1, 1, 0], [1, 0, 1, 1, 0, 1, 0, 0], [0, 1, 1, 0, 1, 0, 1, 1], [1, 1, 0, 1, 0, 0, 1, 0]]
JAM_RNG_SEED = 114032          # the "haphazard" groove is seeded: every build is identical


def jam_groove():
    """Per-beat variation of the groove (the routine stays locked to the beat; depth, timing of the off-beat
    rebounds, sway, rolls and hand amplitudes wander like a human DJ's). Bar downbeats (b % 4 == 0) hit harder."""
    rng = random.Random(JAM_RNG_SEED)
    g = []
    for b in range(32):
        s = sect(b)
        down = b % 4 == 0
        nod0 = {"ride": 14.0, "scratch": 14.0, "up": 12.0, "phones": 8.0, "build": 8.0, "drop": 0.0}[s]
        g.append(dict(
            down=down,
            dip=min(2.9, (2.0 if s == "build" else 1.2) * rng.uniform(0.7, 1.3) * (1.3 if down else 1.0)),
            nod=nod0 * rng.uniform(0.74, 1.22) * (1.18 if down else 1.0),
            up=rng.uniform(0.7, 1.35),                         # off-beat head lift factor
            jit=rng.choice((-2, -1, -1, 0, 1, 1, 2)),           # off-beat key jitter (frames)
            jit2=rng.choice((-1, 0, 0, 1)),
            sway=0.8 * rng.uniform(0.65, 1.35),
            roll=rng.uniform(3.0, 7.0),
            pulse=rng.uniform(2.0, 4.6),
            lift=0.5 * rng.uniform(0.6, 1.5),
            bob=rng.uniform(0.7, 1.3),
            amp=rng.uniform(0.72, 1.3),                         # hand amplitude (nudge / scratch)
            amp2=rng.uniform(0.72, 1.3),
            glance=rng.choice((0.0, 0.0, 0.0, 4.0, -3.0)),      # occasional extra look aside
        ))
    xf = [rng.randrange(len(XF_PATTERNS)) for _ in range(4)]
    return g, xf


JAM_G, JAM_XF = jam_groove()


def xfader_x(k):            # k = 16th index from b8; the transformer pattern changes every 2 beats
    return 1.5 * XF_PATTERNS[JAM_XF[(k // 8) % 4]][k % 8]


def build_jam():
    J = Clip(RIG_J, "joash_jam_loop", JAM_L, loop=True)
    B = Clip(RIG_B, "dj_booth_jam_loop", JAM_L, loop=True)
    ik_on(J, 0, arms=1.0, legs=1.0)
    G = JAM_G
    HB = -0.5          # hips base (soft knees)

    # ---------------- groove: hips (dip on the beat, rebound by the off-beat; downbeats deeper)
    for b in range(32):
        gb = G[b]
        if b == 28:
            J.loc("b_hips", "z", F(27.75), HB - 2.8)       # anticipation crouch
            J.loc("b_hips", "z", F(28), HB + 0.3)          # fling extension
            J.loc("b_hips", "z", F(28.5), HB - 0.6)
            continue
        if b == 27:
            J.loc("b_hips", "z", F(27), HB - gb["dip"], "V")
            J.loc("b_hips", "z", F(27.5), HB - 0.4)
            continue
        J.loc("b_hips", "z", F(b), HB - gb["dip"], "V")
        J.loc("b_hips", "z", F(b + 0.5) + gb["jit"], HB + 0.15 * (gb["up"] - 1))

    def build_bias(b):          # lean to his left in the build so the head clears the boom mic
        return 1.0 if 24 <= b < 28 else 0.0

    def mixer_bias(b):          # turned a little to his left while the right hand works the mixer (b8-15)
        return 1.0 if 8 <= b < 16 else 0.0

    for b in range(32):
        sg = 1 if b % 2 == 0 else -1
        gb = G[b]
        J.loc("b_hips", "x", F(b), gb["sway"] * sg + 0.6 * build_bias(b) + 0.5 * mixer_bias(b))
        J.rot("b_hips", "y", F(b), -2.0 * sg * gb["amp2"])
        J.rot("b_hips", "z", F(b + 0.5) + gb["jit2"], 4.0 * sg * gb["amp"] + 3.0 * mixer_bias(b))
    for b, y in ((0, 2.6), (23.5, 2.6), (24, 3.4), (27.75, 3.4), (28.1, 2.6)):
        J.loc("b_hips", "y", F(b), y)        # stand back from the boom mic; hips go back as he leans in (build)
    J.rot("b_hips", "x", 0, 0.0)
    # spine counter-sway (lags 2 frames) and chest yaw/roll (lags 1-3 frames)
    for b in range(32):
        sg = 1 if b % 2 == 0 else -1
        gb = G[b]
        J.rot("b_spine", "y", F(b) + 2, 2.6 * sg * gb["amp2"] + 4.0 * build_bias(b))
        J.rot("b_spine", "z", F(b + 0.5) + 2 + gb["jit2"], -2.5 * sg + 2.0 * mixer_bias(b))
        J.rot("b_chest", "y", F(b) + 3, -0.8 * sg + 5.0 * build_bias(b))
        J.rot("b_chest", "z", F(b + 0.5) + 3, -1.5 * sg * gb["amp"] + 4.0 * mixer_bias(b))
    J.rot("b_spine", "x", 0, 1.5)

    def lean(b):
        if 24 <= b < 27.75:
            return 20.0
        if 23.5 <= b < 24:
            return 12 + 8 * (b - 23.5) / 0.5
        return 12.0

    for b in range(32):
        gb = G[b]
        if b == 28:
            continue
        if b == 27:
            J.rot("b_chest", "x", F(27) + 1, lean(27) + gb["pulse"], "V")
            J.rot("b_chest", "x", F(27.5) + 1, lean(27.5))
            J.rot("b_chest", "x", F(27.75), 22.0)
            continue
        if b == 29:
            J.rot("b_chest", "x", F(29) + 1, 6.0)
            J.rot("b_chest", "x", F(29.5) + 1, 10.0)
            continue
        J.rot("b_chest", "x", F(b) + 1, lean(b) + gb["pulse"] + (1.5 if gb["down"] else 0.0), "V")
        J.rot("b_chest", "x", F(b + 0.5) + 1 + gb["jit"], lean(b + 0.5))
    J.rot("b_chest", "x", F(28) + 1, -7.0)
    J.rot("b_chest", "x", F(28.3), -9.0)
    J.rot("b_chest", "x", F(28.6), -5.0)

    # ---------------- head + neck: nods (head on the beat, neck lags 2 frames), roll, looks, fills
    def bias(b):
        return {"ride": 0, "scratch": 2, "up": -8, "phones": 0, "build": 2, "drop": 0}[sect(b)]

    head_p = {}

    def hp(f, v, ip):
        head_p[f] = (v, ip)

    for b in range(32):
        s = sect(b)
        gb = G[b]
        if s == "build":
            for q in (0, 0.5):
                hp(F(b + q), bias(b) + gb["nod"] * (1.0 if q == 0 else 0.8), "V")
                hp(F(b + q + 0.25), bias(b) - 3 * gb["up"], "A")
        elif s == "drop" or b == 19:
            pass
        elif b == 7:                     # fill: double-time nod into the scratch section
            hp(F(7), bias(7) + gb["nod"], "V")
            hp(F(7.25), bias(7) - 2.0, "A")
            hp(F(7.5), bias(7) + gb["nod"] * 0.8, "V")
            hp(F(7.75), bias(7) - 3.0, "A")
        elif b == 15:                    # fill: skipped nod, head shake (yaw below) before the hands go up
            hp(F(15), bias(15) + 4.0, "A")
            hp(F(15.5), bias(15) - 2.0, "A")
        elif b == 23:                    # fill: no nod - listening hard into the headphone
            hp(F(23), bias(23) + 3.0, "A")
            hp(F(23.5), bias(23) + 1.0, "A")
        else:
            amp = gb["nod"]
            hp(F(b), bias(b) + amp, "V")
            hp(F(b + 0.5) + gb["jit"], bias(b) - 4 * amp / 14 * gb["up"], "A")
    hp(F(18.8), 2.0, "A")
    hp(F(19), -19.0, "A")
    hp(F(19.3), -17.0, "A")
    hp(F(19.65), -10.0, "A")
    hp(F(27.75), 8.0, "A")
    hp(F(28), -18.0, "A")
    hp(F(28.3), -20.0, "A")
    hp(F(28.65), -13.0, "A")
    hp(F(29), -6.0, "A")
    hp(F(29.5), -1.0, "A")
    hp(F(30), bias(0) + 14, "V")
    hp(F(30.5), bias(0) - 4, "A")
    hp(F(31), bias(0) + 14, "V")
    hp(F(31.5), bias(0) - 4, "A")
    for f, (v, ip) in head_p.items():
        J.rot("b_head", "x", f, v, ip)
        J.rot("b_neck", "x", f + 2, 0.25 * v, ip)
    # roll: +-3..7 over 2 beats, 12 deg into the headphone (b20-23.5)
    for b in range(32):
        sg = 1 if b % 2 == 0 else -1
        gb = G[b]
        if 20 <= b < 24:
            J.rot("b_head", "y", F(b + 0.5), 11.0 + 2.0 * sg)
        elif b == 19:
            J.rot("b_head", "y", F(19.5), 2.0)
        elif b in (28, 29):
            J.rot("b_head", "y", F(b + 0.5), 1.0 * sg)
        elif 24 <= b < 28:
            J.rot("b_head", "y", F(b + 0.5), 3.0 + gb["roll"] * 0.7 * sg)
        else:
            J.rot("b_head", "y", F(b + 0.5) + gb["jit2"], gb["roll"] * sg)
    J.rot("b_head", "y", F(19.9), 6.0)
    J.rot("b_neck", "y", 0, 0.0)
    look = [(0, 6), (7.5, 6), (8, 10), (15.5, 10), (16, -2), (19.5, 0), (20, -7), (23.5, -7), (24, 0),
            (27.75, 0), (29.5, 2), (30, 6)]
    for b, v in look:
        J.rot("b_head", "z", F(b), 0.65 * v)
        J.rot("b_neck", "z", F(b) + 2, 0.35 * v)
    for b in (3, 5, 10, 13, 26):                   # glances aside on a few off-beats (seeded)
        gl = G[b]["glance"]
        if gl:
            base = 0.65 * (6 if b < 8 else 10 if b < 16 else 0)
            J.rot("b_head", "z", F(b + 0.5), base + gl)
            J.rot("b_head", "z", F(b + 1) - 2, base)
    for f, v in ((F(15) + 2, 6.5 + 7), (F(15.25), 6.5 - 8), (F(15.5) - 2, 6.5 + 6), (F(15.75) - 2, 6.5 - 3)):
        J.rot("b_head", "z", f, v, "S")            # fill: head shake at b15

    # ---------------- shoulders (lift on off-beats, more when the arm is raised; downbeats drop)
    def sh_extra(s, b):
        if s > 0:
            return {"up": 0.4, "phones": -0.9, "drop": 0.3}.get(sect(b), 0.0) if not (b >= 29.75) else 0.0
        if sect(b) == "up" and b < 19.5:
            return 0.6
        return 0.3 if 28 <= b < 29.5 else 0.0

    for s in (1, -1):
        n = sname(s)
        for b in range(32):
            gb = G[b]
            J.loc(f"b_shoulder_{n}", "z", F(b), sh_extra(s, b) - (0.3 if gb["down"] else 0.0), "V")
            J.loc(f"b_shoulder_{n}", "z", F(b + 0.5) + gb["jit"], gb["lift"] + sh_extra(s, b + 0.5))
        elev = [(0, 0), (15.6, 0), (16, 0 if s > 0 else 10), (19.4, 0 if s > 0 else 10), (20, 2 if s > 0 else 0),
                (23.5, 2 if s > 0 else 0), (24, 0), (27.75, 0), (28, 7), (28.6, 6), (29.5, 2), (30, 0)]
        for b, e in elev:
            J.rot(f"b_shoulder_{n}", "y", F(b), -s * e)
        # right shoulder reaches forward while the hand works the mixer / crossfader
        prot = [(0, 8), (7.6, 8), (8, 12), (15.5, 12), (15.8, 0), (19.5, 0), (19.9, 6), (23.75, 6), (24, 0),
                (29.6, 0), (29.8, 6), (30.5, 8)] if s < 0 else [(0, 0), (15.6, 0), (16, 9), (19.4, 9), (19.8, 0)]
        for b, e in prot:
            J.rot(f"b_shoulder_{n}", "z", F(b), -s * e)
        key_foot(J, s, 0, Vector((3.0 * s, -0.5, 2.0)))

    # ---------------- orb: bob (peak on the beat), drift, spin once per 4 beats, satellites orbit
    for b in range(32):
        gb = G[b]
        J.loc("b_orb_pivot", "z", F(b), 1.0 * gb["bob"])
        J.loc("b_orb_pivot", "z", F(b + 0.5), -1.0 * gb["bob"])
    for b in range(0, 32, 2):
        J.loc("b_orb_pivot", "x", F(b), 1.2 * math.sin(2 * math.pi * b / 16))
        J.loc("b_orb_pivot", "y", F(b), 0.8 * math.sin(2 * math.pi * b / 8 + 1.0))
    J.rot("b_orb", "x", 0, 15.0)
    J.spin("b_orb", "z", 0, 0.0)
    J.spin("b_orb", "z", JAM_L, -8 * 360.0)
    key_sats(J, list(range(0, JAM_L, 2)), lambda f: 2 * math.pi * f / F(4),
             lambda k, f: SAT_R + 0.5 * math.cos(2 * math.pi * f / FPB))
    for k in (1, 2, 3):
        J.spin(f"b_orb_sat_{k}", "z", 0, 0.0)
        J.spin(f"b_orb_sat_{k}", "z", JAM_L, 360.0 * (16 if k != 2 else -16))
        J.rot(f"b_orb_sat_{k}", "x", 0, 35.0 * k)

    # ---------------- hands
    L_RIDE = ride_rot(1)
    R_RIDE = ride_rot(-1)
    PIN_R = orient((0.30, -0.42, -0.86), (0.25, 0.75, -0.35))  # right pinch from above (mixer)
    PLAT = T_PLATTER_L.z + 0.2                                   # record label top (hands rest on top of it)

    def pinch_rot(yaw=0.0):
        return Rz(yaw) @ PIN_R

    # LEFT
    s = 1
    for b in [x * 0.5 for x in range(0, 16)]:                 # b0..7.5 ride, nudges on the beat
        on = (b % 1) == 0
        a = G[int(b)]["amp"]
        dx = 0.8 * a if on else -0.8 * a
        yaw = 3 * a if on else -3 * a
        key_hand(J, s, F(b), T_PLATTER_L + Vector((dx, 0, 0)), Rz(yaw) @ L_RIDE, "palmk", "V" if on else "A",
                 pose="ride", surface=PLAT)
    J.check(0, s, "palmk", T_PLATTER_L + Vector((0.8 * G[0]["amp"], 0, 0)), "jam b0 left hand resting on platter L", T_PLATTER_L,
            surface=T_PLATTER_L.z)
    for k in range(16):                                        # b8..15.5 baby scratch on 8ths
        b = 8 + 0.5 * k
        sg = scratch1_s(k)
        a = G[int(b)]["amp"] if k % 2 == 0 else G[int(b)]["amp2"]
        key_hand(J, s, F(b), T_PLATTER_L + Vector((0, 2.2 * sg * a, 0)), Rz(20 * sg * a) @ L_RIDE, "palmk", "S",
                 pose="scratch", surface=PLAT)
        B.rot("b_platter_l", "z", F(b), 40 * sg * a, "S")
    J.check(F(8), s, "palmk", T_PLATTER_L + Vector((0, 2.2 * scratch1_s(0) * G[8]["amp"], 0)), "jam b8 left scratch start",
            T_PLATTER_L, surface=T_PLATTER_L.z)
    B.rot("b_platter_l", "z", F(7.5), 0.0)
    B.rot("b_platter_l", "z", F(15.75), 0.0)
    # lift off -> point at the crowd (b16-19)
    key_hand(J, s, F(15.75), T_PLATTER_L + Vector((-1.5, -1.0, 5.0)), Rz(10) @ orient((-0.2, -0.9, 0.35), (0, 0, -1)), "palmk")
    PT = orient((0.30, -0.94, 0.16), (-0.1, 0, -1))
    for b, w, ip in [(16, (12.5, -18.5, 46.0), "A"), (16.15, (13.0, -19.3, 46.6), "A"), (16.5, (12.6, -18.0, 46.0), "A"),
                     (17, (11.0, -19.0, 47.0), "A"), (17.5, (11.2, -18.0, 46.6), "A"), (18, (13.6, -18.6, 45.6), "A"),
                     (18.5, (13.2, -18.0, 46.0), "A"), (19, (13.0, -16.0, 50.5), "A"), (19.4, (12.6, -14.0, 51.5), "A")]:
        key_hand(J, s, F(b), Vector(w), PT, "wrist", ip)
    # headphones b20-23.5 (re-keyed against the evaluated head in jam_phones_pass)
    PRESS = orient((0, 0.25, 0.97), (-1, 0, 0))
    for b in (20, 21, 22, 23, 23.5):
        key_hand(J, s, F(b), T_CUP_L + Vector((CUP_OUT, 0, 0)), PRESS, "palmc")
    # down to the platter for the build (peel off the cup outward first)
    key_hand(J, s, F(23.62), Vector((14.5, -3.0, 47.0)), orient((0.2, -0.3, 0.93), (-0.9, -0.2, 0.1)), "wrist")
    key_hand(J, s, F(23.78), T_PLATTER_L + Vector((-2.0, 3.0, 7.0)), Rz(5) @ orient((-0.3, -0.85, -0.4), (0, 0.2, -1)), "palmk")
    for k in range(8):                                         # b24..27.5 left on 16ths 1+3
        b = 24 + 0.5 * k
        sg = 1 if k % 2 == 0 else -1
        a = G[int(b)]["amp"]
        key_hand(J, s, F(b), T_PLATTER_L + Vector((0, 1.6 * sg * a, 0)), Rz(14 * sg * a) @ L_RIDE, "palmk", "S",
                 pose="scratch", surface=PLAT)
        B.rot("b_platter_l", "z", F(b), 30 * sg * a, "S")
    J.check(F(24), s, "palmk", T_PLATTER_L + Vector((0, 1.6 * G[24]["amp"], 0)), "jam b24 left on platter L", T_PLATTER_L,
            surface=T_PLATTER_L.z)
    B.rot("b_platter_l", "z", F(27.75), 0.0)
    B.rot("b_platter_l", "z", F(32), 0.0)
    # drop: anticipation, V fling, overshoot, float down, land at b30 (ride)
    key_hand(J, s, F(27.75), T_PLATTER_L + Vector((-2.5, 3.0, 1.2)), Rz(-6) @ L_RIDE, "palmk",
             pose=blend_pose("scratch", "fist", 0.6), surface=PLAT)
    V_L = orient((0.55, -0.25, 0.80), (0.3, -0.9, -0.1))
    key_hand(J, s, F(28), Vector((17.5, -4.5, 58.5)), V_L, "wrist")
    key_hand(J, s, F(28.15), Vector((18.5, -5.0, 59.6)), V_L, "wrist")
    key_hand(J, s, F(28.5), Vector((17.8, -4.5, 58.6)), V_L, "wrist")
    key_hand(J, s, F(29), Vector((17.5, -8.5, 52.0)), Rz(-10) @ orient((0.45, -0.6, 0.55), (0.3, -0.5, -0.8)), "wrist")
    key_hand(J, s, F(29.5), Vector((15.5, -11.5, 42.0)), Rz(-6) @ orient((0.1, -0.95, 0.1), (0, 0.1, -1)), "wrist")
    key_hand(J, s, F(29.8), T_PLATTER_L + Vector((0.4, 0.2, 1.4)), Rz(2) @ L_RIDE, "palmk", pose="ride", surface=PLAT)
    for b in (30, 30.5, 31, 31.5):
        on = (b % 1) == 0
        a = G[int(b)]["amp"]
        key_hand(J, s, F(b), T_PLATTER_L + Vector((0.8 * a if on else -0.8 * a, 0, 0)), Rz(3 * a if on else -3 * a) @ L_RIDE,
                 "palmk", "V" if on else "A", pose="ride", surface=PLAT)
    J.check(F(30), s, "palmk", T_PLATTER_L + Vector((0.8 * G[30]["amp"], 0, 0)), "jam b30 left back on platter L", T_PLATTER_L,
            surface=T_PLATTER_L.z)
    # left fingers
    key_fingers(J, s, 0, "ride")
    for b in (2, 4, 6):
        key_fingers(J, s, F(b), "ride", extra={"index": (6, 3, 0), "middle": (3, 2, 0)})
    key_fingers(J, s, F(7.6), "ride")
    key_fingers(J, s, F(8), "scratch")
    key_fingers(J, s, F(15.5), "scratch")
    key_fingers(J, s, F(15.75), "relax")
    key_fingers(J, s, F(16), "point")
    key_fingers(J, s, F(19.4), "point")
    key_fingers(J, s, F(19.8), "relax")
    key_fingers(J, s, F(20), "phone")
    key_fingers(J, s, F(23.5), "phone")
    key_fingers(J, s, F(23.8), "relax")
    key_fingers(J, s, F(24), "scratch")
    key_fingers(J, s, F(27.5), "scratch")
    key_fingers(J, s, F(27.75), blend_pose("scratch", "fist", 0.6))
    key_fingers(J, s, F(28), "splay", "B")
    key_fingers(J, s, F(28.5), "splay")
    for i, b in enumerate((28.75, 29.0, 29.25, 29.5)):     # rippling fingers
        ex = {fg: (18 * math.sin(i * 1.6 - j * 0.9), 22 * math.sin(i * 1.6 - j * 0.9 - 0.5), 0)
              for j, fg in enumerate(("index", "middle", "ring", "thumb"))}
        key_fingers(J, s, F(b), "relax", extra=ex)
    key_fingers(J, s, F(29.8), "ride")
    # left pole
    for b, p in [(0, (16, 9, 31)), (7.6, (16, 9, 31)), (8, (16, 8, 36)), (15.5, (16, 8, 36)), (16, (19, 2, 40)), (19.4, (19, 2, 42)), (20, (17, -3, 44)),
                 (23.5, (17, -3, 44)), (23.85, (16, 8, 33)), (27.75, (16, 9, 31)), (28, (19, 8, 40)), (29.2, (18, 8, 38)),
                 (29.8, (16, 9, 31))]:
        key_pole(J, s, F(b), p)

    # RIGHT
    s = -1
    for b in [x * 0.5 for x in range(0, 16)]:                # b0..7.5 pinch fader_2, push -14 -> -12
        y = fader2_y(b)
        key_hand(J, s, F(b), Vector((T_FADER_2.x, y, PINCH_Z["fader"])), pinch_rot(0), "pinch", "E")
        B.loc("b_fader_2", "y", F(b), y + 14.0, "E")
    J.check(0, s, "pinch", Vector((T_FADER_2.x, -14.0, PINCH_Z["fader"])), "jam b0 right pinch fader_2", T_FADER_2)
    B.loc("b_fader_2", "y", F(30.75), 2.0, "E")
    B.loc("b_fader_2", "y", F(31.25), 0.0, "E")
    XF_R = Rz(-10) @ orient((0.25, -0.62, -0.74), (0.2, 0.7, -0.6))
    key_hand(J, s, F(7.78), Vector((1.0, -16.0, 33.5)), XF_R, "pinch")
    for k in range(30):                                        # b8..15.25 transformer cuts on 16ths
        b = 8 + 0.25 * k
        x = xfader_x(k)
        key_hand(J, s, F(b), Vector((x, T_XFADER.y, PINCH_Z["xfader"])), XF_R, "pinch", "S")
        B.loc("b_xfader", "x", F(b), x, "S")
        tap = 14 if x > 0 else 0
        key_fingers(J, s, F(b), "pinch", "S", extra={"index": (tap, tap * 0.6, 0)})
    J.check(F(8), s, "pinch", Vector((xfader_x(0), T_XFADER.y, PINCH_Z["xfader"])), "jam b8 right pinch xfader", T_XFADER)
    B.loc("b_xfader", "x", F(7.75), 0.0)
    B.loc("b_xfader", "x", F(15.6), 0.0)
    # fist pumps 16/17/18 (+held 19); the hand comes up BEHIND the boom mic
    FIST = orient((0.05, -0.2, 0.98), (0.95, -0.2, 0))
    key_hand(J, s, F(15.55), Vector((-3.0, -14.0, 35.5)), pinch_rot(-10), "wrist")
    key_hand(J, s, F(15.68), Vector((-7.0, -7.0, 42.0)), Rx(-30) @ FIST, "wrist")
    key_hand(J, s, F(15.8), Vector((-9.0, -4.5, 52.0)), FIST, "wrist")
    for b, z, ip in [(16, 61.5, "V"), (16.5, 54.0, "A"), (17, 61.5, "V"), (17.5, 54.0, "A"), (18, 61.5, "V"),
                     (18.5, 55.0, "A"), (19, 62.5, "A"), (19.3, 62.0, "A")]:
        key_hand(J, s, F(b), Vector((-9.0, -3.5, z)), FIST, "wrist", ip)
    # down to the knob from his right, the forearm passing in front of the laptop screen
    key_hand(J, s, F(19.5), Vector((-12.0, -3.0, 50.0)), FIST, "wrist")
    key_hand(J, s, F(19.68), Vector((-12.5, -7.0, 41.0)), Rz(15) @ KNOB_R, "wrist")
    key_hand(J, s, F(19.84), Vector((-6.5, -11.5, 34.0)), Rz(8) @ KNOB_R, "wrist")
    for b in [20 + 0.5 * i for i in range(8)]:
        a = knob3_angle(b)
        key_hand(J, s, F(b), Vector((T_KNOB_3.x, T_KNOB_3.y, PINCH_Z["knob"])), Rz(0.35 * a) @ KNOB_R, "pinch", "E")
        B.rot("b_knob_3", "z", F(b), a, "E")
    J.check(F(20), s, "pinch", Vector((T_KNOB_3.x, T_KNOB_3.y, PINCH_Z["knob"])), "jam b20 right pinch knob_3", T_KNOB_3)
    B.rot("b_knob_3", "z", F(24), -90.0)
    B.rot("b_knob_3", "z", F(29.75), -90.0, "E")
    B.rot("b_knob_3", "z", F(30.25), 0.0)
    # platter R scratches on 16ths 2+4 (b24.25..27.75)
    key_hand(J, s, F(23.9), T_PLATTER_R + Vector((2.0, 2.0, 5.0)), Rz(-5) @ R_RIDE, "palmk")
    for k in range(8):
        b = 24.25 + 0.5 * k
        sg = 1 if k % 2 == 0 else -1
        a = G[int(b)]["amp2"]
        key_hand(J, s, F(b), T_PLATTER_R + Vector((0, 1.6 * sg * a, 0)), Rz(-14 * sg * a) @ R_RIDE, "palmk", "S",
                 pose="scratch", surface=PLAT)
        B.rot("b_platter_r", "z", F(b), -30 * sg * a, "S")
    key_hand(J, s, F(24.05), T_PLATTER_R + Vector((0, -1.6, 0)), Rz(14) @ R_RIDE, "palmk", pose="scratch", surface=PLAT)
    J.check(F(24.25), s, "palmk", T_PLATTER_R + Vector((0, 1.6 * G[24]["amp2"], 0)), "jam b24 right on platter R", T_PLATTER_R,
            surface=T_PLATTER_R.z)
    B.rot("b_platter_r", "z", 0, 0.0)
    B.rot("b_platter_r", "z", F(24), 0.0)
    B.rot("b_platter_r", "z", F(27.95), 0.0)
    key_hand(J, s, F(27.85), T_PLATTER_R + Vector((2.5, 3.0, 1.2)), Rz(6) @ R_RIDE, "palmk",
             pose=blend_pose("scratch", "fist", 0.6), surface=PLAT)
    V_R = orient((-0.55, -0.25, 0.80), (-0.3, -0.9, -0.1))
    key_hand(J, s, F(28), Vector((-18.0, -6.0, 57.0)), V_R, "wrist")
    key_hand(J, s, F(28.15), Vector((-18.6, -6.4, 57.8)), V_R, "wrist")
    key_hand(J, s, F(28.5), Vector((-18.2, -6.0, 57.1)), V_R, "wrist")
    # float down BEHIND the boom mic (boom bar at y~-9.5, z~48 on his right), then to the knob from the side
    FLOAT_R = orient((-0.3, 0.15, 0.94), (-0.25, -0.95, 0.1))       # fingers trailing up as the arm floats down
    key_hand(J, s, F(29), Vector((-14.5, -3.0, 52.5)), FLOAT_R, "wrist")
    key_hand(J, s, F(29.2), Vector((-12.5, -3.6, 48.0)), Rx(-25) @ FLOAT_R, "wrist")
    key_hand(J, s, F(29.4), Vector((-10.5, -6.5, 42.0)), Rz(15) @ KNOB_R, "wrist")
    key_hand(J, s, F(29.6), Vector((-6.5, -11.5, 34.0)), Rz(8) @ KNOB_R, "wrist")
    # knob reset (pinch at 90, twist back), then fader_2 back to -14 (b0 pose)
    for b in (29.75, 30.0, 30.25):
        key_hand(J, s, F(b), Vector((T_KNOB_3.x, T_KNOB_3.y, PINCH_Z["knob"])), Rz(0.35 * knob3_angle(b)) @ KNOB_R, "pinch", "E")
    key_hand(J, s, F(30.5), Vector((2.0, -12.5, 33.0)), pinch_rot(5), "pinch")
    for b in (30.75, 31.0, 31.25, 31.5):
        key_hand(J, s, F(b), Vector((T_FADER_2.x, fader2_y(b), PINCH_Z["fader"])), pinch_rot(0), "pinch", "E")
    # right fingers
    key_fingers(J, s, 0, "pinch")
    key_fingers(J, s, F(7.5), "pinch")
    key_fingers(J, s, F(7.78), blend_pose("pinch", "relax", 0.5))
    key_fingers(J, s, F(15.4), "pinch")
    key_fingers(J, s, F(15.6), blend_pose("pinch", "fist", 0.5))
    key_fingers(J, s, F(15.8), "fist")
    key_fingers(J, s, F(19.3), "fist")
    key_fingers(J, s, F(19.6), blend_pose("fist", "pinch", 0.5))
    key_fingers(J, s, F(20), "pinch")
    key_fingers(J, s, F(23.75), "pinch")
    key_fingers(J, s, F(23.9), "relax")
    key_fingers(J, s, F(24.25), "scratch")
    key_fingers(J, s, F(27.75), "scratch")
    key_fingers(J, s, F(27.88), blend_pose("scratch", "fist", 0.6))
    key_fingers(J, s, F(28), "splay", "B")
    key_fingers(J, s, F(28.5), "splay")
    for i, b in enumerate((28.75, 29.0, 29.25)):
        ex = {fg: (18 * math.sin(i * 1.6 - j * 0.9 + 1.2), 22 * math.sin(i * 1.6 - j * 0.9 + 0.7), 0)
              for j, fg in enumerate(("index", "middle", "ring", "thumb"))}
        key_fingers(J, s, F(b), "relax", extra=ex)
    key_fingers(J, s, F(29.6), "pinch")
    key_fingers(J, s, F(30.3), "pinch")
    key_fingers(J, s, F(30.5), blend_pose("pinch", "relax", 0.4))
    key_fingers(J, s, F(30.75), "pinch")
    # right pole: high elbow over the laptop screen on the mixer, out to his right for the knob
    for b, p in [(0, (19, 3, 41)), (7.6, (19, 3, 41)), (8, (18, 2, 42)), (15.5, (18, 2, 42)), (15.8, (18, 0, 46)),
                 (19.4, (18, 0, 46)), (19.65, (20, -2, 38)), (19.85, KNOB_POLE), (23.75, KNOB_POLE), (24, (16, 9, 31)),
                 (27.9, (16, 9, 31)), (28, (19, 8, 40)), (29.3, (18, 4, 40)), (29.6, KNOB_POLE), (30.3, KNOB_POLE),
                 (30.6, (19, 3, 41))]:
        key_pole(J, s, F(b), p)

    # ---------------- booth: tonearms parked back off the records (his forearms pass right over them)
    for sn, sg in (("l", 1), ("r", -1)):
        B.rot(f"b_tonearm_{sn}", "z", 0, sg * TONEARM_PARK)
    B.loc("b_fader_1", "y", 0, 0.0)
    J.markers = []
    return J, B


def jam_phones_pass(J):
    """Re-key the left hand on the headphone cup against the evaluated head (b20-23.5)."""
    assign(RIG_J, J.act)
    head_rest = RIG_J.data.bones["b_head"].matrix_local
    PRESS = orient((0, 0.25, 0.97), (-1, 0, 0))
    J.remove_range('pose.bones["ik_hand_l"].location', F(20) - 0.5, F(23.5) + 0.5)
    J.remove_qrange("ik_hand_l", F(20) - 0.5, F(23.5) + 0.5)
    for f in range(F(20), F(23.5) + 1, 2):
        eval_frame(f)
        M = RIG_J.pose.bones["b_head"].matrix @ head_rest.inverted()
        contact = M @ (T_CUP_L + Vector((CUP_OUT, 0, 0)))
        R = M.to_3x3().normalized() @ PRESS
        key_hand(J, 1, f, contact, R, "palmc")
        if f == F(20):
            J.check(f, 1, "palmc", contact, "jam b20 left palm on headphone cup (head-attached)", M @ T_CUP_L)


HEAD_SET = ("c_joash_hood", "c_joash_head", "c_joash_hp_band", "c_joash_hp_cup_l", "c_joash_hp_cup_r")
MIC_SET = ("c_booth_mic", "c_booth_mic_boom", "c_booth_mic_pole")


def jam_clear_pass(J, B):
    """The boom mic hangs 3.5 px in front of the right half of his face: wherever a nod would bring the hood
    within 0.45 px of it, the hips (whole body) ease back along +Y; the IK hands stay on the decks."""
    J.clear_req = clear_pass(J, HEAD_SET, MIC_SET, "b_hips", "y", +1, 0.45, list(range(0, J.L)), smooth=10, step=2,
                             booth_act=B.act)
    hot = [f for f, v in J.clear_req.items() if v > 0]
    print(f"JAM CLEAR: {len(hot)} frames need the hips back, max {max(J.clear_req.values()):.1f} px", flush=True)


# =====================================================================================  clip: interrupted (+ booth)
INT_L = SEC(4.6)        # 175
M_VOICE, M_SLAM, M_BOOTH, M_ROAR, M_BLINK = SEC(1.2), SEC(2.5), SEC(2.65), SEC(2.9), SEC(4.3)
BOOTH_INT_L = SEC(2.65)


EDGE_Y = BOOTH_OFFSET.y - 6.5          # Jehoash-space y of the booth top slab's front face (-22.5)
EDGE_TOP = 26.0                         # top of the slab
EDGE_OVER = 0.5                         # knuckle line this far in front of the edge: the fingers hook down its face
FP["hook"] = dict(index=(34, 6, 4), middle=(35, 6, 0), ring=(36, 7, -4), thumb=(-6, -4, 40))
FP["talon"] = dict(index=(28, 72, 16), middle=(30, 76, 0), ring=(32, 80, -16), thumb=(18, 40, 30))   # spread, tips hooked


def edge_wrist(s, contact, R, pose):
    """Wrist for a palm-down grip over the slab's front edge: the palm's front edge overhangs the edge by 0.45 px
    (the hooked fingers go down in front of it) and nothing of the hand that is above the slab dips into it.
    contact = (palm centre x, edge y, slab top z)."""
    p = FP[pose] if isinstance(pose, str) else pose
    n = sname(s)
    palm = [R @ (Vector((x, y, z)) - wrist0(s)) for xr, yr, zr in CUBE_BY_BONE[f"b_hand_{n}"]
            for x in xr for y in yr for z in zr]
    fk = hand_fk(s, p)
    thumb = []
    for b in (f"b_thumb_{n}_1", f"b_thumb_{n}_2"):
        h, Rb = fk[b]
        for xr, yr, zr in CUBE_BY_BONE[b]:
            thumb += [R @ (h + Rb @ (Vector((x, y, z)) - BONE_HEAD[b]) - wrist0(s)) for x in xr for y in yr for z in zr]
    K = R @ (BONE_HEAD[f"b_finger_middle_{n}_1"] - wrist0(s))       # knuckle line (finger roots)
    W = Vector((contact.x - sum(q.x for q in palm) / len(palm), contact.y - EDGE_OVER - K.y, 0.0))
    over = [q for q in palm + thumb if W.y + q.y > contact.y + 0.05]
    W.z = contact.z + 0.05 - min(q.z for q in (over or palm + thumb))   # the heel of the hand rests on the slab
    return W


_wrist_for_base = wrist_for


def wrist_for(s, contact, R, grip, pose=None):         # noqa: F811  (adds the edge grip)
    if grip == "edge":
        return edge_wrist(s, Vector(contact), R, pose)
    return _wrist_for_base(s, contact, R, grip, pose)


def int_glare_rot(s, yaw=0.0):
    """Claw held up in front of the chest: palm down, fingers reaching forward-down at the player."""
    return Rz(yaw) @ orient((s * 0.15, -0.75, -0.65), (0, 0, -1))


def build_interrupted(jam, idle_ik):
    J = Clip(RIG_J, "joash_interrupted", INT_L, markers=[("voice", M_VOICE), ("slam", M_SLAM), ("booth", M_BOOTH),
                                                          ("roar", M_ROAR), ("move:blink", M_BLINK)])
    B = Clip(RIG_B, "dj_booth_interrupted", BOOTH_INT_L)
    ik_on(J, 0, arms=1.0, legs=1.0)
    rnd = random.Random(4171)
    HB = -0.5
    L_RIDE = ride_rot(1)
    PIN_R = orient((0.30, -0.42, -0.86), (0.25, 0.75, -0.35))
    GRIP = orient((0, -0.64, -0.77), (0, 0.77, -0.64))       # wrist cocked up, heel on the slab, fingers hooked over the edge

    # ---- body rows: frame -> channel values (None = not keyed). Channels: hips z/y/x, spine p, chest p/yaw/roll,
    #      neck p, head p/roll/yaw (pitch + = nod forward; roll + = top to his left; yaw + = turn to his left)
    j, sg = AMAP[(RIG_J.name, "b_hips")][AX["y"]]
    HY0 = sg * jam.raw_value('pose.bones["b_hips"].location', j, 0)    # jam b0 hips setback (mic clearance)
    jz, sgz = AMAP[(RIG_J.name, "b_hips")][AX["z"]]
    HZ0 = sgz * jam.raw_value('pose.bones["b_hips"].location', jz, 0)   # jam b0 knee dip
    def jam_rot(b, ax):
        jj, sgn = AMAP[(RIG_J.name, b)][AX[ax]]
        return math.degrees(sgn * jam.raw_value(f'pose.bones["{b}"].rotation_euler', jj, 0))

    CP0, CY0, CR0 = jam_rot("b_chest", "x"), jam_rot("b_chest", "z"), jam_rot("b_chest", "y")   # jam b0 chest
    rows = [
        # freeze (f0 = the jam pose, copied below): startled straightening, then frozen at f11 ...
        (4, dict(hz=HZ0 + 0.3, hy=HY0, hx=0.8, sp=1.5, cp=CP0, cy=CY0, nk=4.0)),
        (11, dict(hz=HZ0 + 0.4, hy=HY0, hx=0.7, sp=1.0, cp=CP0, cy=CY0, nk=3.5, hp=10.0, hr=1.0, hyw=3.0)),
        # ... body frozen while only the head ratchets round to the crowd (snap, hold, snap, hold, snap, stare)
        (36, dict(hz=HZ0 + 0.4, hy=HY0, hx=0.6, sp=1.0, cp=CP0, cy=CY0, cr=CR0, nk=3.5)),
        (39, dict(hz=HB + 0.2, hy=2.2, hx=0.6, sp=-2.0, cp=4.0, cy=0, cr=0, nk=0.0)),            # straightens
        (42, dict(hz=HB - 0.6, hy=1.3, hx=0.9, sp=-4.0, cp=-1.0, cy=0, cr=0, nk=-2.0, hp=-12.0, hr=16.0, hyw=-4.0)),  # pull back
        # VOICE: lunges over the booth, grips the front edge, head jutting forward (chest stays above the laptop screen)
        (M_VOICE, dict(hz=HB - 1.0, hy=1.7, hx=1.6, sp=20.0, cp=23.0, cy=4, cr=4.0, nk=-12.0, hp=-16.0, hr=7.0, hyw=0.0)),
        (48, dict(nk=-5.0, hp=-21.0, hr=9.0)),                                                   # syllable 1: head thrust
        (53, dict(nk=-11.0, hp=-16.0, hr=6.0)),
        (61, dict(hz=HB - 1.1, hy=1.6, sp=20.5, cp=23.5, nk=-4.5, hp=-22.0, hr=4.0, hyw=-3.0)),     # syllable 2
        (66, dict(nk=-11.0, hp=-16.5, hr=5.0, hyw=-1.0)),
        (80, dict(hz=HB - 1.2, hy=1.5, hx=1.5, sp=22.0, cp=25.0, cy=4, cr=3.5, nk=-11.5, hp=-17.5, hr=6.0, hyw=1.0)),  # creeping in
        # rears up: both fists come up together and hang high over his head (2.4 s), body arched back, face up
        (84, dict(hz=HB - 0.9, hy=1.4, hx=1.4, sp=10.0, cp=12.0, cy=0, cr=1.0, nk=-6.0, hp=-12.0, hr=3.0, hyw=0.0)),
        (89, dict(hz=HB + 1.0, hy=1.9, hx=1.8, sp=-6.0, cp=-15.0, cy=0, cr=2.0, nk=-5.0, hp=-8.0, hr=2.0, hyw=0.0)),
        (92, dict(hz=HB + 1.2, hy=2.1, hx=2.0, sp=-7.0, cp=-17.0, cy=0, cr=2.5, nk=-6.0, hp=-10.0, hr=2.0, hyw=0.0)),
        # the drop: two frames, the torso whips forward behind the fists
        (93, dict(hz=HB + 0.8, hy=2.2, hx=2.2, sp=-3.0, cp=-9.0, cy=0, cr=3.0, nk=-3.0, hp=-4.0, hr=3.0, hyw=1.0)),
        (94, dict(hz=HB - 0.3, hy=2.4, hx=2.4, sp=2.0, cp=3.0, cy=-1, cr=4.0, nk=2.0, hp=6.0, hr=4.0, hyw=2.0)),
        # SLAM (2.5 s): the fists hit the records with the arms nearly straight, shoulders shrug, head snaps down,
        # knees give ...
        (M_SLAM, dict(hz=HB - 1.8, hy=2.6, hx=2.6, sp=6.0, cp=14.0, cy=-2, cr=4.5, nk=6.0, hp=17.0, hr=5.0, hyw=3.0)),
        # ... and the body keeps sinking over the planted fists (knee dip, chest lurch, head buried) ...
        (97, dict(hz=HB - 3.6, hy=2.8, hx=2.8, sp=11.0, cp=22.0, cy=-2, cr=5.5, nk=6.0, hp=12.0, hr=7.0, hyw=3.0)),
        # ... then the head comes up into a snarl at the player
        (99, dict(hz=HB - 3.3, hy=2.8, hx=2.7, sp=12.0, cp=25.0, cy=-1, cr=5.0, nk=2.0, hp=-2.0, hr=6.0, hyw=1.0)),
        (101, dict(hz=HB - 2.6, hy=2.6, hx=2.4, sp=10.0, cp=20.0, cy=0, cr=4.0, nk=-2.0, hp=-8.0, hr=4.0, hyw=0.0)),
        # rears back (inhale), then the ROAR thrown forward at the player, trembling
        (106, dict(hz=HB + 0.4, hy=0.8, hx=0.3, sp=-6.0, cp=-14.0, cy=0, cr=0.0, nk=-8.0, hp=-18.0, hr=0.0, hyw=0.0)),
        (M_ROAR, dict(hz=HB + 0.7, hy=0.6, hx=0.2, sp=-5.0, cp=-12.0, cy=0, cr=0.0, nk=0.0, hp=-4.0, hr=0.0, hyw=0.0)),
        (133, dict(hz=HB + 0.3, hy=0.6, hx=0.3, sp=-3.0, cp=-8.0, cy=0, cr=0.0, nk=1.0, hp=-2.0, hr=0.0, hyw=0.0)),
        # hunched glare: crouched, back rounded, hips back and to his left (clear of the boom mic), the neck thrust
        # forward and down with the face tipped up at the player from under the hood; heavy breathing, a slow roll
        # drift and one 3-frame snap
        (138, dict(hz=-1.3, hy=2.2, hx=1.6, sp=8.0, cp=13.0, cy=2, cr=2.0, nk=6.0, hp=-8.0, hr=-3.0, hyw=0.0)),
        (142, dict(hz=-2.8, hy=3.4, hx=2.6, sp=13.0, cp=21.0, cy=3, cr=3.0, nk=18.0, hp=-30.0, hr=-5.0, hyw=0.0)),
        (147, dict(cp=22.5, hr=-3.0, hyw=1.5)),
        (150, dict(cp=21.0, hr=-1.0, hyw=2.0)),
        (153, dict(cp=21.5, hr=8.0, hyw=-3.0)),                                                  # 3-frame snap
        (158, dict(cp=23.0, hr=6.0, hyw=-2.0)),
        (M_BLINK, dict(hz=-2.8, hy=3.4, hx=2.6, sp=13.0, cp=21.5, cy=3, cr=3.0, nk=18.5, hp=-30.0, hr=7.0, hyw=-2.0)),
    ]
    chan = {"hz": ("loc", "b_hips", "z"), "hy": ("loc", "b_hips", "y"), "hx": ("loc", "b_hips", "x"),
            "sp": ("rot", "b_spine", "x"), "cp": ("rot", "b_chest", "x"), "cy": ("rot", "b_chest", "z"),
            "cr": ("rot", "b_chest", "y"), "nk": ("rot", "b_neck", "x"), "hp": ("rot", "b_head", "x"),
            "hr": ("rot", "b_head", "y"), "hyw": ("rot", "b_head", "z")}
    IPS = {M_SLAM: "QO"}                     # decelerate through the follow-through (the drop is keyed every frame)
    for f, vals in rows:
        for k, v in vals.items():
            kind, b, ax = chan[k]
            ip = "S" if f in (48, 61, 153) else IPS.get(f, "A")
            (J.loc if kind == "loc" else J.rot)(b, ax, f, v, ip)
    # the head ratchet (snaps 'S', holds flat) f11-f39 and a locked stare with one micro twitch
    for f, p, r, yw, ip in ((14, 2.5, 6.0, 3.0, "S"), (21, 2.5, 6.0, 3.0, "A"), (23, -6.0, 12.0, 2.0, "S"),
                            (28, -6.0, 12.0, 2.0, "A"), (31, -16.0, 22.0, -7.0, "S"), (33, -15.0, 19.0, -5.5, "A"),
                            (36, -15.0, 19.0, -5.5, "A"), (37, -15.5, 21.5, -6.0, "C"), (38, -15.0, 19.0, -5.5, "A")):
        J.rot("b_head", "x", f, p, ip)
        J.rot("b_head", "y", f, r, ip)
        J.rot("b_head", "z", f, yw, ip)
    J.rot("b_neck", "y", 0, 0.0)
    J.rot("b_neck", "z", 0, 1.4)
    J.rot("b_neck", "z", 30, 0.0)
    J.rot("b_hips", "y", 0, -2.0)
    J.rot("b_hips", "y", 20, 0.0)
    J.rot("b_hips", "z", 0, 0.0)
    J.rot("b_hips", "x", 0, 0.0)
    J.rot("b_spine", "y", 0, 2.6)
    J.rot("b_spine", "y", 20, 0.0)
    J.rot("b_spine", "z", 0, 0.0)
    # freeze shudder (needle scratch): 1-frame alternating jolts decaying over 0.5 s, so they survive the game's
    # 0.25 s cross-fade from the frozen jam pose
    for f in range(1, 19):
        a = (1 if f % 2 else -1) * 2.4 * (1 - f / 20.0)
        J.rot("b_chest", "y", f, CR0 + a * 0.5, "L")
        J.rot("b_head", "z", f, 3.0 + a * 1.4, "L")
    J.rot("b_chest", "y", 19, CR0)
    # WELCOME hold: faint tremble while he creeps forward (chest roll, head yaw), every 3 frames
    for i, f in enumerate(range(68, 80, 3)):
        sg = 1 if i % 2 == 0 else -1
        J.rot("b_chest", "y", f, 3.5 + 0.7 * sg, "A")
    # roar tremble: decaying alternation every 2 frames f111-132 (chest roll, head yaw/pitch, shoulders)
    for i, f in enumerate(range(111, 133, 2)):
        amp = 1.0 - 0.6 * i / 10.0
        sg = 1 if i % 2 == 0 else -1
        J.rot("b_chest", "y", f, 1.5 * sg * amp, "L")
        J.rot("b_head", "z", f, 2.0 * sg * amp, "L")
        J.rot("b_head", "x", f, -4.0 + 2.0 * -sg * amp, "L")
    idle_pose(J, INT_L, 0.0)
    # shoulders: z lift, elevation (y), protraction (z)
    for s in (1, -1):
        n = sname(s)
        for f, z, e, pr in ((11, 0.2, 0, 0), (36, 0.4, 2, 0), (42, 0.8, 4, 0), (M_VOICE, 1.0, 8 if s > 0 else 4, 6),
                            (48, 1.4, 10 if s > 0 else 6, 7), (53, 1.0, 8 if s > 0 else 4, 6), (61, 1.4, 10 if s > 0 else 6, 7),
                            (66, 1.0, 8 if s > 0 else 4, 6), (84, 1.0, 6, 4), (87, 1.4, 14, 0), (89, 1.7, 20, 0),
                            (92, 1.9, 22, -2), (94, 1.0, 8, 4), (M_SLAM, 1.4, 10, 10), (97, 0.4, 2, 10), (99, 0.2, 0, 8),
                            (103, 0.0, 0, 4), (M_ROAR, 1.0, 10, 0), (133, 0.8, 8, 0), (138, 0.8, 6, 6), (142, 0.6, 18, 8),
                            (147, 0.9, 19, 8), (152, 0.6, 17, 8), (157, 0.9, 19, 9), (M_BLINK, 0.7, 18, 8),
                            (INT_L, idle_values(0)["sh_z"], 0, 0)):
            J.loc(f"b_shoulder_{n}", "z", f, z)
            J.rot(f"b_shoulder_{n}", "y", f, -s * e)
            J.rot(f"b_shoulder_{n}", "z", f, -s * pr)
    # feet: DJ stance -> widen into the combat stance during the roar
    for s in (1, -1):
        DJ = Vector((3.0 * s, -0.5, 2.0))
        BACK = Vector((3.6 * s, 1.6, 2.0))           # a short step back while rearing up: room for the knee dip
        f0, f1 = (82, 87) if s > 0 else (84, 89)
        key_foot(J, s, 0, DJ)
        key_foot(J, s, f0, DJ)
        key_foot(J, s, (f0 + f1) / 2, (DJ + BACK) / 2 + Vector((0, 0, 1.6)), Rx(-6))
        key_foot(J, s, f1, BACK)
        key_foot(J, s, 103 if s > 0 else 106, BACK)
        key_foot(J, s, 107 if s > 0 else 110, (BACK + IDLE_FOOT[s]) / 2 + Vector((0, 0, 1.5)), Rz(-4 * s))
        key_foot(J, s, 111 if s > 0 else 114, IDLE_FOOT[s], IDLE_FOOT_R[s])
        key_foot(J, s, INT_L, IDLE_FOOT[s], IDLE_FOOT_R[s])

    # ---- hands
    PLAT = T_PLATTER_L.z + 0.2
    a0 = JAM_G[0]["amp"]
    key_hand(J, 1, 0, T_PLATTER_L + Vector((0.8 * a0, 0, 0)), Rz(3 * a0) @ L_RIDE, "palmk", pose="ride", surface=PLAT)
    key_hand(J, -1, 0, Vector((T_FADER_2.x, -14.0, PINCH_Z["fader"])), PIN_R, "pinch")
    # needle drag: the left hand yanks the record back
    key_hand(J, 1, 3, T_PLATTER_L + Vector((0.4, 2.4, 0)), Rz(-18) @ L_RIDE, "palmk", "S", pose="ride", surface=PLAT)
    key_hand(J, 1, 6, T_PLATTER_L + Vector((0.4, 0.8, 0)), Rz(-6) @ L_RIDE, "palmk", "S", pose="ride", surface=PLAT)
    key_hand(J, 1, 8, T_PLATTER_L + Vector((0.4, 1.6, 0)), Rz(-12) @ L_RIDE, "palmk", pose="ride", surface=PLAT)
    key_hand(J, 1, 11, T_PLATTER_L + Vector((0.4, 1.4, 0)), Rz(-10) @ L_RIDE, "palmk", pose="ride", surface=PLAT)
    key_hand(J, -1, 11, Vector((T_FADER_2.x, -14.0, PINCH_Z["fader"])), PIN_R, "pinch")
    # hands lift off slowly and curl into claws while the head turns
    key_hand(J, 1, 16, T_PLATTER_L + Vector((0.0, 1.8, 1.6)), Rz(-6) @ L_RIDE, "palmk")
    key_hand(J, -1, 16, Vector((T_FADER_2.x, -13.6, PINCH_Z["fader"] + 1.4)), Rz(-6) @ PIN_R, "pinch")
    key_hand(J, 1, 26, Vector((12.5, -9.5, 34.0)), orient((-0.3, -0.7, -0.6), (0, 0.6, -0.8)), "wrist")
    key_hand(J, -1, 28, Vector((-8.5, -9.0, 35.0)), orient((0.3, -0.7, -0.6), (0, 0.6, -0.8)), "wrist")
    key_hand(J, 1, 38, Vector((11.0, -6.5, 36.5)), orient((-0.2, -0.85, -0.45), (0.1, 0.5, -0.9)), "wrist")
    key_hand(J, -1, 38, Vector((-11.0, -6.5, 36.5)), orient((0.2, -0.85, -0.45), (-0.1, 0.5, -0.9)), "wrist")
    # grab the booth front edge at the voice (palm down on the slab, fingers hooked over the edge)
    for s, T in ((1, T_EDGE_L), (-1, T_EDGE_R)):
        edge = Vector((T.x, EDGE_Y, EDGE_TOP + 0.3))
        key_hand(J, s, 43, edge + Vector((0, 4.0, 4.5)), Rx(-20) @ GRIP, "edge", pose="splay")
        for f in (M_VOICE, 53, 66, 81):
            key_hand(J, s, f, edge, GRIP, "edge", pose="hook")
        key_hand(J, s, 83, edge + Vector((0, -0.5, 2.2)), Rx(15) @ GRIP, "edge", pose="hook")    # unhook: off the face first
        for f in (48, 61):
            key_hand(J, s, f, edge, GRIP, "edge", pose="hook")
        J.check(M_VOICE, s, "palm", T + Vector((0, -0.6, 1.45)), "interrupted voice: hand over the booth front edge (palm centre)", T)
    # rear up: the fists clench at the chest and rise together BEHIND the boom bar to hang high over his head (2.4 s);
    # the SLAM (2.5 s) is a two-frame arc forward over the head and down, the arms nearly straight when the hammer
    # fists hit the records (the right forearm crosses the boom bar's plane between f94 and f95)
    FST = {s: orient((s * 0.1, -0.15, 1.0), (-s * 0.9, -0.3, 0)) for s in (1, -1)}             # knuckles up, fists facing
    SWING = {s: orient((s * 0.1, -0.85, 0.5), (-s * 0.9, 0.1, 0.4)) for s in (1, -1)}           # fists leading forward
    SLAM = {s: orient((s * 0.05, -0.4, -0.92), (-s * 0.95, 0.15, -0.1)) for s in (1, -1)}       # hammer fists, pinky side down
    for s in (1, -1):                                    # (x relative to his hips, which drift 1.4 -> 2.6 px left)
        key_hand(J, s, 85, Vector((1.4 + s * 10.0, -8.0, 37.5)), Rx(-40) @ FST[s], "wrist")
        key_hand(J, s, 87, Vector((1.6 + s * 8.5, -2.5, 51.0)), FST[s], "wrist")
        key_hand(J, s, 89, Vector((1.8 + s * 6.0, 1.0, 62.6)), FST[s], "wrist")
        key_hand(J, s, 92, Vector((2.0 + s * 5.6, 2.2, 63.2)), FST[s], "wrist")
        key_hand(J, s, 93, Vector((2.2 + s * 6.5, -2.8, 61.5)), Rx(-25) @ FST[s], "wrist")
        key_hand(J, s, 94, Vector((2.4 + s * 10.0, -12.0, 53.0)), SWING[s], "wrist")      # beside the head, not in front
        tgt = Vector((11.0 if s > 0 else -8.5, -18.0, T_PLATTER_L.z))
        key_hand(J, s, M_SLAM, tgt - Vector((0, 0, 0.45)), SLAM[s], "extreme", "A", pose=FP["fist"])
        for f in (97, 100):
            key_hand(J, s, f, tgt, SLAM[s], "extreme", "A", pose=FP["fist"])
        key_hand(J, s, 103, tgt + Vector((0, 1.5, 4.0)), SLAM[s], "extreme", "A", pose=FP["fist"])
        J.check(M_SLAM, s, "fist", tgt - Vector((0, 0, 0.45)), "interrupted slam: fist bottom on the record (booth sinks 0.45)", tgt)
    # rear back (inhale), arms wide and up, fingers splayed - ROAR - trembling
    WIDE = {s: orient((s * 0.6, -0.3, 0.75), (s * 0.2, -0.95, -0.2)) for s in (1, -1)}
    for s in (1, -1):
        key_hand(J, s, 106, Vector((s * 16.0, -8.0, 50.0)), WIDE[s], "wrist")
        key_hand(J, s, M_ROAR, Vector((s * 19.0, -4.5, 56.5)), WIDE[s], "wrist", "S")
        for i, f in enumerate(range(112, 133, 4)):
            amp = 1.0 - 0.6 * i / 5.0
            sg = 1 if i % 2 == 0 else -1
            key_hand(J, s, f, Vector((s * (19.3 + 0.6 * sg * amp), -4.0, 56.8 - 0.25 * i + 0.6 * sg * amp)), WIDE[s], "wrist")
        key_hand(J, s, 134, Vector((s * 18.0, -5.0, 54.5)), WIDE[s], "wrist")
    # hunched glare: both claws held out over the booth toward the player at chest height, palms forward, talons
    # curled at him and flexing one by one; elbows down and out at 45 deg (never flared at shoulder height)
    for s in (1, -1):
        W, R, P = idle_ik[s]
        CF = orient((s * 0.2, -0.75, 0.62), (0, -0.62, -0.75))
        gx = 2.6 + (11.5 if s > 0 else -9.5)             # x relative to his hips (2.6 px left, clear of the boom mic)
        if s < 0:                                        # down from the roar behind the boom bar's plane
            key_hand(J, s, 135, Vector((-18.5, -2.5, 51.5)), WIDE[s], "wrist")
        key_hand(J, s, 137, Vector((15.5, -7.0, 46.0)) if s > 0 else Vector((-14.5, -2.5, 44.0)),
                 Rx(35) @ int_glare_rot(s, 12 * s), "wrist")
        key_hand(J, s, 140, Vector((gx + s * 1.0, -15.0, 37.0)), Rx(-30) @ CF, "wrist")
        key_hand(J, s, 142, Vector((gx, -21.0, 39.5)), CF, "wrist", "S")
        key_hand(J, s, 148, Vector((gx - s * 0.3, -21.6, 38.9)), Rz(-5 * s) @ CF, "wrist")
        key_hand(J, s, 155, Vector((gx + s * 0.2, -20.8, 39.8)), Rz(4 * s) @ CF, "wrist")
        key_hand(J, s, M_BLINK, Vector((gx, -21.4, 39.2)), CF, "wrist")
        key_hand(J, s, INT_L, W, R, "wrist")
        key_pole(J, s, INT_L, P)
    # fingers
    for s in (1, -1):
        key_fingers(J, s, 0, "ride" if s > 0 else "pinch")
        key_fingers(J, s, 11, "ride" if s > 0 else "pinch")
        key_fingers(J, s, 16, blend_pose("ride" if s > 0 else "pinch", "relax", 0.5))
        key_fingers(J, s, 26 if s > 0 else 28, blend_pose("relax", "claw", 0.45))
        key_fingers(J, s, 36, "claw")
        key_fingers(J, s, 43, "splay")
        key_fingers(J, s, M_VOICE, "hook", "S")
        key_fingers(J, s, 48, "hook", "S", extra={"index": (2, -3, 0), "middle": (2, -3, 0), "ring": (2, -3, 0)})
        key_fingers(J, s, 53, "hook")
        key_fingers(J, s, 61, "hook", "S", extra={"index": (2, -3, 0), "middle": (2, -3, 0), "ring": (2, -3, 0)})
        key_fingers(J, s, 66, "hook")
        key_fingers(J, s, 81, "hook")
        key_fingers(J, s, 85, blend_pose("hook", "fist", 0.5))
        key_fingers(J, s, 88, "fist")
        key_fingers(J, s, 101, "fist")
        key_fingers(J, s, 106, blend_pose("fist", "splay", 0.6))
        key_fingers(J, s, M_ROAR, "splay", "B")
        for i, f in enumerate(range(112, 133, 2)):             # tremble
            amp = 8.0 * (1.0 - 0.6 * i / 10.0)
            sg = 1 if i % 2 == 0 else -1
            key_fingers(J, s, f, "splay", "L", extra={fg: (sg * amp * (1 + 0.15 * j), -sg * amp * 0.7, 0)
                                                      for j, fg in enumerate(("index", "middle", "ring", "thumb"))})
        key_fingers(J, s, 134, "splay")
        key_fingers(J, s, 140, "talon")
        # twitching talons: one finger at a time, irregular gaps (2/5/3/7...), snappy
        f, gaps = 143, (2, 5, 3, 7, 2, 4)
        k = 0
        while f < M_BLINK - 1:
            fg = rnd.choice(("index", "middle", "ring", "thumb"))
            amt = rnd.choice((-18, -14, 14, 20))
            key_fingers(J, s, f, "talon", "S" if k % 2 == 0 else "C", extra={fg: (amt, amt * 1.2, 0)})
            key_fingers(J, s, f + 1, "talon", "A")
            f += gaps[k % len(gaps)] + (1 if s < 0 else 0)
            k += 1
        key_fingers(J, s, M_BLINK, "talon")
        idle_fingers_single(J, s, INT_L)
    # poles
    for s in (1, -1):
        for f, p in [(0, (16, 9, 31) if s > 0 else (19, 3, 41)), (11, (16, 9, 31) if s > 0 else (19, 3, 41)),
                     (16, (16, 9, 32) if s > 0 else (19, 3, 42)), (30, (14, 8, 30)), (38, (14, 8, 28)), (42, (17, 6, 50)),
                     (M_VOICE, (16, 6, 56)), (81, (16, 6, 56)), (85, (14, 12, 28)), (87, (16, 2, 40)), (89, (13, -8, 58)),
                     (92, (13, -8, 58)), (94, (16, 4, 52)), (M_SLAM, (10, 14, 47)), (101, (11, 14, 44)), (M_ROAR, (20, 6, 40)),
                     (134, (20, 6, 40)), (139, (16, 6, 28)), (142, (16, 4, 22)), (M_BLINK, (16, 4, 22))]:
            key_pole(J, s, f, p)

    # ---- booth: needle drag 0-0.3 s, grab jolt, slam shake at 2.5 s (it knocks the parked tonearms back onto
    #      the records - the block shows them at rest), rest at 2.65 s
    for f, a in ((0, 0.0), (3, -48.0), (6, -16.0), (8, -34.0), (11, -28.0), (30, 0.0)):
        B.rot("b_platter_l", "z", f, a, "S" if f in (3, 6) else "A")
    B.rot("b_platter_l", "z", BOOTH_INT_L, 0.0)
    for f, a in ((0, 0.0), (3, 6.0), (6, -2.0), (9, 3.0), (12, 0.0)):
        B.rot("b_platter_r", "z", f, a)
    for bn, sg in (("b_tonearm_l", 1), ("b_tonearm_r", -1)):
        P0 = sg * TONEARM_PARK
        for f, a, ip in ((0, P0, "A"), (2, P0 + sg * 7.0, "A"), (5, P0 - sg * 3.0, "A"), (9, P0 + sg * 1.0, "A"), (13, P0, "A"),
                         (M_SLAM, P0, "QI"), (M_SLAM + 2, sg * 30.0, "A"), (M_SLAM + 4, -sg * 8.0, "A"), (BOOTH_INT_L, 0.0, "A")):
            B.rot(bn, "z", f, a, ip)
    # (the grab at the voice pulls the front edge up toward him; the slam drives it down)
    for f, z, rx in ((0, 0, 0), (M_VOICE - 1, 0, 0), (M_VOICE + 1, -0.15, -0.6), (M_VOICE + 4, 0.05, 0.25), (M_VOICE + 8, 0, 0),
                     (M_SLAM - 1, 0, 0), (M_SLAM, -0.45, 1.6), (M_SLAM + 2, 0.12, -1.1), (M_SLAM + 4, -0.08, 0.4), (BOOTH_INT_L, 0, 0)):
        B.loc("b_cabinet", "z", f, z, "QO" if f == M_SLAM else "A")
        B.rot("b_cabinet", "x", f, rx)
    for bn in ("b_deck_l", "b_deck_r"):                  # the decks take the blow (fists stay on them)
        for f, z in ((0, 0), (M_SLAM, 0), (M_SLAM + 1, -0.35), (M_SLAM + 3, 0.05), (BOOTH_INT_L, 0)):
            B.loc(bn, "z", f, z)
    for f, z in ((0, 0), (M_SLAM, 0), (M_SLAM + 2, 1.0), (M_SLAM + 4, -0.1), (BOOTH_INT_L, 0)):
        B.loc("b_mixer", "z", f, z)
    for bn, sg in (("b_tonearm_l", 1), ("b_tonearm_r", -1)):
        for f, a in ((M_SLAM, 0), (M_SLAM + 2, 14), (M_SLAM + 4, -5), (BOOTH_INT_L, 0)):
            B.rot(bn, "x", f, sg * a)
    for f, z, rx in ((0, 0, 0), (M_SLAM, 0, 0), (M_SLAM + 2, 1.6, 6), (M_SLAM + 4, 0.15, -2), (BOOTH_INT_L, 0, 0)):
        B.loc("b_laptop", "z", f, z)                     # the laptop jumps and tips toward the crowd
        B.rot("b_laptop", "x", f, rx)
    for f, a in ((0, 0), (M_SLAM, 0), (M_SLAM + 1, 7.0), (M_SLAM + 3, -4.5), (M_SLAM + 5, 2.0), (BOOTH_INT_L, 0)):
        B.rot("b_mic_stand", "y", f, a)
    for i, bn in enumerate(("b_fader_1", "b_fader_2", "b_xfader", "b_knob_1", "b_knob_2", "b_knob_3", "b_knob_4")):
        for f, z in ((0, 0), (M_SLAM, 0), (M_SLAM + 2, 0.8 + 0.15 * (i % 3)), (M_SLAM + 4, -0.05), (BOOTH_INT_L, 0)):
            B.loc(bn, "z", f, z)
    # ---- orb
    jam_orb0 = Vector((0.0, 0.8 * math.sin(1.0), 1.0 * JAM_G[0]["bob"]))
    ORB = [(0, jam_orb0), (3, jam_orb0 + Vector((0.6, 0.3, -0.8))), (6, jam_orb0 + Vector((-0.3, 0.0, 0.4))),
           (11, jam_orb0 + Vector((0.0, 0.0, -0.4))), (38, Vector((1.0, 0.0, 1.5))), (M_VOICE, Vector((3.0, -3.0, -1.0))),
           (84, Vector((3.5, -3.5, -1.4))), (89, Vector((-4.0, 4.0, 2.0))), (93, Vector((-4.0, 4.0, 2.4))),
           (M_SLAM, Vector((-2.0, 2.4, -2.2))), (98, Vector((-2.0, 2.0, 1.2))),
           (M_ROAR, Vector((0.0, 1.0, 4.5))), (130, Vector((0.0, 0.5, 3.0))),
           (M_BLINK, Vector((0.3, 0.0, 1.0))), (INT_L, Vector((0.0, 0.0, ORB_Z0)))]
    for f, o in ORB:
        J.loc3("b_orb_pivot", f, o, "S" if f in (3, M_SLAM) else "A")
    acc, angs = 0.0, {}
    for f in range(0, INT_L + 1):
        angs[f] = acc
        burst = 4.0 * math.exp(-((f - M_ROAR) / 14.0) ** 2)
        acc += 2 * math.pi / 80.0 * (1.0 + burst - 0.6 * math.exp(-((f - 6) / 6.0) ** 2))
    sc_a = round(angs[INT_L] / (2 * math.pi)) * 2 * math.pi / angs[INT_L]

    def irad(k, f):
        r = SAT_R + 0.5 * max(0.0, 1 - f / 20.0)
        return r + 4.0 * math.exp(-((f - M_ROAR - 3) / 9.0) ** 2)

    key_sats(J, sorted(set(range(0, INT_L + 1, 2)) | {INT_L}), lambda f: angs[int(f)] * sc_a, irad)
    J.rot("b_orb", "x", 0, 15.0)
    J.spin("b_orb", "z", 0, 0.0)
    J.spin("b_orb", "z", INT_L, -720.0)
    for k in (1, 2, 3):
        J.spin(f"b_orb_sat_{k}", "z", 0, 0.0)
        J.spin(f"b_orb_sat_{k}", "z", INT_L, 1440.0 * (1 if k != 2 else -1))
        J.rot(f"b_orb_sat_{k}", "x", 0, 35.0 * k)
    copy_frame(jam, 0, J, 0)
    J.finalize()
    B.finalize()
    # safety nets while the booth stands in front of him: the chest stays off the laptop screen during the lean,
    # the hood off the boom mic during the glare (hips ease back where needed)
    J.clear_req = clear_pass(J, ("c_joash_chest", "c_joash_belly", "c_joash_neck", "c_joash_shoulder_l", "c_joash_shoulder_r",
                                 "c_joash_hood", "c_joash_head"),
                             ("c_booth_laptop_screen", "c_booth_laptop_base"), "b_hips", "y", +1, 0.35, list(range(0, 106)),
                             smooth=6, step=1, smax=3.5, booth_act=B.act)
    J.clear_req2 = clear_pass(J, HEAD_SET, MIC_SET, "b_hips", "x", +1, 0.4, list(range(0, M_BLINK + 1)), smooth=6, step=1,
                              smax=4.0, booth_act=B.act)
    hot1 = sorted(f for f, v in J.clear_req.items() if v > 0)
    hot2 = sorted(f for f, v in J.clear_req2.items() if v > 0)
    print("INT CLEAR: chest/laptop %.1f px back (frames %s), head/mic %.1f px left (frames %s)"
          % (max(J.clear_req.values()), hot1, max(J.clear_req2.values()), hot2), flush=True)
    return J, B


def copy_frame(src, f_src, dst, f_dst, skip=("phys_",)):
    """Key dst at f_dst with src's evaluated channel values at f_src (same rig, raw local values)."""
    quats = {}
    for fc in src.act.fcurves:
        path, idx = fc.data_path, fc.array_index
        bone = path.split('"')[1] if '"' in path else ""
        if bone.startswith(skip):
            continue
        v = fc.evaluate(f_src)
        if path.endswith("rotation_quaternion"):
            quats.setdefault(bone, [1.0, 0.0, 0.0, 0.0])[idx] = v
        elif (path, idx) in src.offset:
            continue
        else:
            dst._put(path, idx, f_dst, v, "A")
    for bone, q in quats.items():
        dst.quat_local(bone, f_dst, Quaternion(q).normalized())


def idle_fingers_single(c, s, f):
    base = FP["claw"]
    ex = {}
    for i, fg in enumerate(("index", "middle", "ring", "thumb")):
        ph = -i * 0.7 + (0 if s > 0 else math.pi)
        a = 10 * math.sin(ph)
        ex[fg] = (a, a * 1.2, 0)
    key_fingers(c, s, f, base, "A", extra=ex)


# =====================================================================================  combat clips
def fk_start(c, f=0, t=0.0):
    """Idle base pose incl. FK arms and fingers (for FK clips)."""
    idle_pose(c, f, t)
    idle_arms(c, f, t)
    idle_fingers(c, f, t)


ORB_Z0 = 1.2 * math.sin(0.8)          # idle frame-0 pivot height offset


def orb_combat(c, L):
    """Idle orb motion time-warped to whole cycles, so the clip starts and ends exactly on idle frame 0.
    Short clips still get one full cycle (never a frozen orbit)."""
    k1 = max(1, round(L / 57.0))       # bob + orbit cycles
    k2 = max(1, round(L / 114.0))      # sway + spin cycles
    frames = sorted(set(range(0, L + 1, 2)) | {L})
    for f in frames:
        t1 = 57.0 * k1 * f / L
        t2 = 114.0 * k2 * f / L
        c.loc("b_orb_pivot", "z", f, 1.2 * math.sin(2 * math.pi * t1 / 57.0 + 0.8))
        c.loc("b_orb_pivot", "x", f, 0.6 * math.sin(2 * math.pi * t2 / 114.0))
        c.loc("b_orb_pivot", "y", f, 0.0)
    key_sats(c, frames, lambda f: 2 * math.pi * k1 * f / L, lambda k, f: SAT_R + 0.3 * math.sin(2 * math.pi * k1 * f / L))
    orb_spin(c, L, -k2)


def orb_spin(c, L, turns=-1):
    """Whole turns only, so the end state equals the start state."""
    c.rot("b_orb", "x", 0, 15.0)
    c.spin("b_orb", "z", 0, 0.0)
    c.spin("b_orb", "z", L, 360.0 * turns)
    for k in (1, 2, 3):
        c.spin(f"b_orb_sat_{k}", "z", 0, 0.0)
        c.spin(f"b_orb_sat_{k}", "z", L, 720.0 * abs(turns) * (1 if k != 2 else -1))
        c.rot(f"b_orb_sat_{k}", "x", 0, 35.0 * k)


WALK_L = 46


def build_walk():
    """Long lanky strides: hips high enough that the trailing leg straightens (thighs -22..+33 deg, knees 7-15 deg
    at contact, ~45 at passing, 12-17 in mid-stance), lowest at contact, short swing lift, moderate toe-off roll."""
    c = Clip(RIG_J, "joash_walk_loop", WALK_L, loop=True)
    c.ik_arms = False
    ik_on(c, 0, arms=0.0, legs=1.0)
    L, H = WALK_L, WALK_L // 2
    HB = -0.6
    # hips: lowest at contact (0, 23), highest at passing
    for f, z in ((0, HB - 0.4), (5, HB - 0.2), (11.5, HB + 0.45), (H, HB - 0.4), (H + 5, HB - 0.2), (H + 11.5, HB + 0.45)):
        c.loc("b_hips", "z", f, z)
    for f, x in ((9, 0.9), (H + 9, -0.9)):
        c.loc("b_hips", "x", f, x)
    for f, yaw, roll in ((0, -7, 0), (11.5, 0, -2.5), (H, 7, 0), (H + 11.5, 0, 2.5)):
        c.rot("b_hips", "z", f, yaw)
        c.rot("b_hips", "y", f, roll)
    c.loc("b_hips", "y", 0, -1.0)                 # a little ahead of the feet: the trailing thigh goes behind the hip
    c.rot("b_hips", "x", 0, 3.0)
    for f, yaw in ((2, 3), (H + 2, -3)):
        c.rot("b_spine", "z", f, yaw)
    c.rot("b_spine", "x", 0, 4.0)
    c.rot("b_spine", "y", 0, 0.0)
    for f, yaw in ((3, 6), (H + 3, -6)):
        c.rot("b_chest", "z", f, yaw)
    for f, p in ((2, 6.5), (13, 5.0), (H + 2, 6.5), (H + 13, 5.0)):
        c.rot("b_chest", "x", f, p)
    for f, r in ((13, 1.5), (H + 13, -1.5)):
        c.rot("b_chest", "y", f, r)
    for f, p in ((3, -2.0), (14, -4.0), (H + 3, -2.0), (H + 14, -4.0)):
        c.rot("b_neck", "x", f, p)
        c.rot("b_head", "x", f, -6.0 + (1.5 if p == -2.0 else -1.0))
    for f, yaw in ((4, -2.5), (H + 4, 2.5)):
        c.rot("b_head", "z", f, yaw)
        c.rot("b_neck", "z", f, yaw * 0.5)
    for f, r in ((10, 2.0), (H + 10, -2.0)):
        c.rot("b_head", "y", f, r)
    c.rot("b_neck", "y", 0, 0.0)
    for s in (1, -1):
        n = sname(s)
        for f, z in ((0, 0.2), (12, 0.6), (H, 0.2), (H + 12, 0.6)):
            c.loc(f"b_shoulder_{n}", "z", f, z)
        c.rot(f"b_shoulder_{n}", "y", 0, 0.0)
    # feet: stance 26 f sliding back, swing 20 f with a low lift; right offset half a cycle
    STR = 9.5
    LIFT = 1.2
    ROLL = 16.0

    def foot_at(s, f):
        ph = (f + (0 if s > 0 else H)) % L
        x = 3.6 * s
        if ph <= 26:
            u = ph / 26.0
            y = -STR + 2 * STR * u
            if ph < 4:
                toe = -14 * (1 - ph / 4.0)         # heel strike, toe up
            elif ph > 18:
                toe = ROLL * ((ph - 18) / 8.0)     # heel rises before toe-off
            else:
                toe = 0.0
            return foot_pose(x, y, toe)
        u = (ph - 26) / 20.0
        y = STR - 2 * STR * (0.5 - 0.5 * math.cos(math.pi * u))
        lift = LIFT * math.sin(math.pi * min(1.0, u * 1.08))
        toe = ROLL * (1 - u) ** 2 - 14 * max(0.0, (u - 0.6) / 0.4)
        p, R = foot_pose(x, y, toe)
        return p + Vector((0, 0, lift)), R

    for s in (1, -1):
        for f in range(0, L, 2):
            p, R = foot_at(s, f)
            key_foot(c, s, f, p, R)
    # arms: counter swing +-20 with bent elbows (lag 3 frames)
    for s in (1, -1):
        for f, sw in ((3, 1), (H + 3, -1)):
            fwd = -sw * s          # left arm back when the left foot is forward (f0)
            key_arm_fk(c, s, f, flex=6 + 22 * fwd, abd=10, twist=10, elbow=24 + 22 * max(0, fwd), wflex=10 + 8 * fwd, wdev=-4)
    for s in (1, -1):
        for f in (0, 11, H, H + 11):
            ex = {fg: (6 * math.sin(f / 46 * 2 * math.pi + j), 6 * math.sin(f / 46 * 2 * math.pi + j + 0.5), 0)
                  for j, fg in enumerate(("index", "middle", "ring", "thumb"))}
            key_fingers(c, s, f, blend_pose("claw", "relax", 0.4), extra=ex)
    # orb trails along
    for f in range(0, L, 2):
        c.loc("b_orb_pivot", "z", f, 1.2 * math.sin(2 * math.pi * f / H + 0.6))
        c.loc("b_orb_pivot", "y", f, 1.5 + 0.4 * math.sin(2 * math.pi * f / L))
        c.loc("b_orb_pivot", "x", f, 0.3 * math.sin(2 * math.pi * f / L))
    key_sats(c, list(range(0, L, 2)), lambda f: 2 * math.pi * f / H, lambda k, f: SAT_R)
    c.rot("b_orb", "x", 0, 15.0)
    c.spin("b_orb", "z", 0, 0.0)
    c.spin("b_orb", "z", L, -360.0)
    for k in (1, 2, 3):
        c.spin(f"b_orb_sat_{k}", "z", 0, 0.0)
        c.spin(f"b_orb_sat_{k}", "z", L, 720.0 * (1 if k != 2 else -1))
        c.rot(f"b_orb_sat_{k}", "x", 0, 35.0 * k)
    return c


def foot_pose(x, y_ankle_flat, toe_down):
    """Ankle position + rotation for a foot pivoting about its toe (toe_down > 0) or heel (toe_down < 0)."""
    ank = Vector((x, y_ankle_flat, 2.0))
    if toe_down >= 0:
        piv = ank + Vector((0, -3.5, -2.0))
    else:
        piv = ank + Vector((0, 2.0, -2.0))
    R = Rx(toe_down)
    return piv + R @ (ank - piv), R


def build_melee():
    """Wind-up overhead and OUT (claw beside the head, cocked at the player, head counter-turned to keep the eyes on
    him), a lunging slash that is at full reach - claw forward at chest/head height - on the hit frame, then the
    down-and-across follow-through in front of the chest, recover."""
    L = SEC(1.0)
    c = Clip(RIG_J, "joash_attack_melee", L, markers=[("hit", SEC(0.5))])
    c.ik_arms = False
    ik_on(c, 0, arms=0.0, legs=1.0)
    fk_start(c, 0)
    fk_start(c, L)
    HIT = SEC(0.5)
    # torso: wind (twist right 25), lunge + slash (twist left 30), knees dip; head counter-rotates onto the target
    for f, hz, hy, hyaw, syaw, cyaw, sp, cp, hdp, hdy in (
            (6, -1.6, 0.6, -3, -3, -4, 5, 3, -6, 4),
            (13, -0.6, 1.4, -8, -8, -9, 3, -6, 2, 12),
            (15, -0.8, 1.2, -8, -8, -10, 3, -7, 2, 13),
            (17, -2.2, -3.0, 0, 2, 4, 7, 10, 3, -6),
            (HIT, -3.0, -6.0, 8, 10, 12, 10, 18, 2, -26),
            (21, -3.3, -6.3, 9, 11, 13, 11, 21, 3, -28),
            (24, -2.9, -5.0, 7, 9, 10, 10, 18, 2, -20),
            (28, -2.3, -2.8, 4, 5, 5, 8, 12, -2, -9),
            (31, -1.7, -0.8, 1, 1, 2, 7, 8, -5, -2)):
        c.loc("b_hips", "z", f, hz, "QO" if f == HIT else "A")
        c.loc("b_hips", "y", f, hy, "QO" if f == HIT else "A")
        c.rot("b_hips", "z", f, hyaw)
        c.rot("b_spine", "z", f, syaw)
        c.rot("b_chest", "z", f, cyaw)
        c.rot("b_spine", "x", f, sp)
        c.rot("b_chest", "x", f, cp)
        c.rot("b_head", "x", f, hdp)
        c.rot("b_head", "z", f, hdy)
    # feet: the left foot steps into the lunge and back
    for s in (1, -1):
        key_foot(c, s, 0, IDLE_FOOT[s], IDLE_FOOT_R[s])
    key_foot(c, 1, 12, IDLE_FOOT[1], IDLE_FOOT_R[1])
    key_foot(c, 1, 15, IDLE_FOOT[1] + Vector((0.2, -3.0, 1.8)), IDLE_FOOT_R[1] @ Rx(-8))
    key_foot(c, 1, 18, IDLE_FOOT[1] + Vector((0.4, -6.0, 0.0)), IDLE_FOOT_R[1], "QO")
    key_foot(c, 1, 25, IDLE_FOOT[1] + Vector((0.4, -6.0, 0.0)), IDLE_FOOT_R[1])
    key_foot(c, 1, 29, IDLE_FOOT[1] + Vector((0.2, -3.0, 1.6)), IDLE_FOOT_R[1] @ Rx(-6))
    key_foot(c, 1, 33, IDLE_FOOT[1], IDLE_FOOT_R[1])
    key_foot(c, -1, 33, IDLE_FOOT[-1], IDLE_FOOT_R[-1])
    # right arm: wind overhead and out -> slash -> full reach on HIT -> follow through across the front -> recover
    s = -1
    for f, fl, ab, tw, el, wf, ip in ((6, 40, 20, 0, 50, -5, "A"), (13, 140, 65, -40, 85, -25, "A"),
                                      (15, 146, 66, -42, 88, -28, "QI"), (17, 118, 32, -12, 42, -6, "A"),
                                      (HIT, 88, 6, 4, 10, 8, "A"), (21, 82, 4, 10, 14, 12, "A"),
                                      (24, 72, 3, 22, 26, 18, "A"), (28, 42, 6, 16, 32, 14, "A"),
                                      (31, 18, 8, 12, 38, 12, "A")):
        key_arm_fk(c, s, f, flex=fl, abd=ab, twist=tw, elbow=el, wflex=wf, wdev=-4, ip=ip)
    c.loc("b_shoulder_r", "z", 13, 1.0)
    c.rot("b_shoulder_r", "y", 13, -s * 14)
    c.rot("b_shoulder_r", "z", 13, 0.0)
    c.loc("b_shoulder_r", "z", HIT, 0.2)
    c.rot("b_shoulder_r", "y", HIT, 4.0)
    c.rot("b_shoulder_r", "z", HIT, -s * 10)          # reach: shoulder forward
    c.rot("b_shoulder_r", "z", 28, 0.0)
    # left arm: counterbalance down and out, swings back on the slash
    s = 1
    for f, fl, ab, el in ((13, 5, 35, 30), (HIT, -14, 30, 38), (25, 2, 20, 40)):
        key_arm_fk(c, s, f, flex=fl, abd=ab, twist=12, elbow=el, wflex=10, wdev=-4)
    for s in (1, -1):
        key_fingers(c, s, 13, "claw", extra={"index": (0, 0, 8), "ring": (0, 0, -8)} if s < 0 else None)
        key_fingers(c, s, 17 if s < 0 else 15, "splay" if s < 0 else "claw", "S" if s < 0 else "A")
        key_fingers(c, s, HIT, blend_pose("splay", "claw", 0.25) if s < 0 else "claw")
        key_fingers(c, s, 22, "claw")
        key_fingers(c, s, 27, "claw")
    orb_combat(c, L)
    return c


RANGED_ORB_WINDOW = (8, 52)       # frames where the ranged clip choreographs the orb itself (raw, exact fire point)


def build_ranged(idle_ik):
    """Conducting hands stay outside the hood's silhouette; the ball arcs over the head to the contract charge point
    (0,-8,60) and is exactly there on the fire frame (the beam is drawn from it); the hands frame it from the front,
    then punch forward and down along the beam line while the head comes down onto the target."""
    L = SEC(1.6)
    FIRE = SEC(1.0)
    c = Clip(RIG_J, "joash_attack_ranged", L, markers=[("fire", FIRE)])
    ik_on(c, 0, arms=1.0, legs=1.0)
    idle_pose(c, 0)
    idle_pose(c, L)
    idle_fingers(c, 0)
    idle_fingers(c, L)
    # orb path: pivot offset from the contract rest (-10,2,52). f0/L carry ORB_OUT (= idle); f8-52 are raw (exact)
    ORB = [(0, (0, 0, ORB_Z0), False), (8, (-1.5, -0.5, 3.5), True), (13, (-0.5, 0.5, 9.0), True),
           (19, (5.0, -3.0, 13.0), True), (26, (9.5, -9.0, 9.5), True), (30, (10, -10, 8), True),
           (35, (10, -9, 8.6), True), (FIRE, (10, -10, 8), True), (FIRE + 3, (10, -7.5, 9.5), True),
           (FIRE + 8, (5.0, -3.0, 13.0), True), (52, (-1.0, 0.0, 6.0), True), (L, (0, 0, ORB_Z0), False)]
    for f, o, raw in ORB:
        for i, a in enumerate("xyz"):
            c.loc("b_orb_pivot", a, f, o[i], "S" if f == FIRE else "A", raw=raw)

    def orb_world(f):
        pts = [(f0, Vector(o) + (Vector() if raw else ORB_OUT)) for f0, o, raw in ORB]
        for (f0, o0), (f1, o1) in zip(pts, pts[1:]):
            if f0 <= f <= f1:
                u = (f - f0) / (f1 - f0)
                u = 0.5 - 0.5 * math.cos(math.pi * u)
                return Vector((-10, 2, 52)) + o0.lerp(o1, u)
        return Vector((-10, 2, 52))

    # satellites spin up: integrate angular speed
    ang, angs = 0.0, {}
    for f in range(0, L + 1):
        w = 2 * math.pi / 57.0 * (1 + 5.0 * math.sin(math.pi * min(1.0, f / FIRE)) ** 2 if f <= FIRE else
                                  1 + 5.0 * max(0.0, 1 - (f - FIRE) / 18.0))
        angs[f] = ang
        ang += w
    total = angs[L]
    turns = round(total / (2 * math.pi))
    scale = turns * 2 * math.pi / total if total else 1

    def rad(k, f):
        if FIRE <= f <= FIRE + 6:
            return SAT_R + 1.5 * math.sin(math.pi * (f - FIRE) / 6.0)
        return SAT_R + 0.3 * math.sin(2 * math.pi * f / L)

    def smooth(f, a, b):
        u = min(1.0, max(0.0, (f - a) / float(b - a)))
        return u * u * (3 - 2 * u)

    def sat_off(k, f):
        """While the ball charges in front of his face the shards leave their side rings for a halo facing the
        player (XZ plane, 120 deg apart) - the side rings would sweep them back through his hood."""
        a = angs[int(f)] * scale
        r = rad(k, f)
        ring = sat_offset(k, SAT_PHASE[k] + SAT_DIR[k] * a, r)
        w = smooth(f, 18, 26) * (1.0 - smooth(f, FIRE + 6, FIRE + 13))
        if w <= 0:
            return ring
        ph = a + 2 * math.pi * (k - 1) / 3.0
        halo = Vector((math.cos(ph), -0.15, math.sin(ph))).normalized() * r
        v = ring.lerp(halo, w)
        v = v.normalized() * r if v.length > 1e-6 else halo
        return v + Vector((0, -3.5 * w, 0))

    key_sats(c, sorted(set(range(0, L + 1, 2)) | {L}), None, None, world_fn=sat_off)
    orb_spin(c, L, turns=-2.0)
    # body
    for f, hz, sp, cp, nk, hdp, hdr in ((14, -1.0, 3, -2, -4, -14, 4),
                                        (30, -0.6, 0, -9, -8, -22, -4),
                                        (36, -1.2, 1, -6, -6, -18, -2),
                                        (FIRE, -2.2, 5, 6, 1, 7, 0),
                                        (FIRE + 3, -1.6, 2, -2, 0, 2, 0),
                                        (50, -1.4, 5, 3, -2, -8, 2)):
        c.loc("b_hips", "z", f, hz)
        c.rot("b_spine", "x", f, sp)
        c.rot("b_chest", "x", f, cp)
        c.rot("b_neck", "x", f, nk)
        c.rot("b_head", "x", f, hdp)
        c.rot("b_head", "y", f, hdr)
    # hands conduct up to the orb (outside the hood), frame it from the front, punch along the beam on fire
    for s in (1, -1):
        W0, R0, P0 = idle_ik[s]
        key_hand(c, s, 0, W0, R0, "wrist")
        key_pole(c, s, 0, P0)
        up = orient((s * 0.2, -0.5, 0.85), (-s * 0.6, -0.3, -0.7))
        key_hand(c, s, 10, Vector((s * 11.5, -8.0, 38.0)), up, "wrist")
        for i, f in enumerate(range(14, 31, 4)):            # conducting circles, never in front of the hood
            a = i * 1.7 + (0 if s > 0 else math.pi)
            ow = orb_world(f)
            hx = s * max(11.5, abs(ow.x + s * 9.0))
            key_hand(c, s, f, Vector((hx + 1.4 * math.cos(a), min(ow.y - 3.0, -7.5), ow.z - 8.0 + 1.6 * math.sin(a))),
                     Rz(10 * math.sin(a)) @ up, "wrist")
        frame = orient((-s * 0.25, 0.3, 0.92), (-s * 0.45, 0.88, -0.1))     # palms toward the ball, fingers up
        for f, y in ((33, -15.0), (36, -14.6)):
            key_hand(c, s, f, Vector((s * 7.5, y, 56.5)), frame, "wrist")
        thrust = orient((s * 0.08, -0.85, -0.5), (0, -0.5, 0.86))           # palms out along the beam
        key_hand(c, s, FIRE, Vector((s * 7.0, -21.0, 47.0)), thrust, "wrist", "S")
        key_hand(c, s, FIRE + 3, Vector((s * 7.5, -20.0, 47.5)), thrust, "wrist")
        key_hand(c, s, 50, Vector((s * 9.0, -10.0, 40.0)), up, "wrist")
        key_hand(c, s, L, W0, R0, "wrist")
        key_pole(c, s, 10, (16, 6, 30))
        key_pole(c, s, 30, (18, 4, 40))
        key_pole(c, s, FIRE, (16, 4, 36))
        key_pole(c, s, L, P0)
        key_fingers(c, s, 10, "relax")
        for i, f in enumerate(range(14, 31, 4)):
            ex = {fg: (12 * math.sin(i * 1.3 - j * 0.8), 14 * math.sin(i * 1.3 - j * 0.8 - 0.4), 0)
                  for j, fg in enumerate(("index", "middle", "ring", "thumb"))}
            key_fingers(c, s, f, "relax", extra=ex)
        key_fingers(c, s, 34, "cup")
        key_fingers(c, s, FIRE, "splay", "B")
        key_fingers(c, s, FIRE + 6, "splay")
        key_fingers(c, s, 50, "relax")
    return c


def leg_fk3(c, s, f, hip_joint, ankle, foot_R, ip="A"):
    """Key FK thigh/shin/foot (raw local Euler) reproducing the IK solution exactly. Parent hips unrotated."""
    n = sname(s)
    names = (f"b_leg_upper_{n}", f"b_leg_lower_{n}", f"b_foot_{n}")
    bu, bl, bf = (RIG_J.data.bones[x] for x in names)
    L1 = (bu.tail_local - bu.head_local).length
    L2 = (bl.tail_local - bl.head_local).length
    H, A = Vector(hip_joint), Vector(ankle)
    u = A - H
    D = min(u.length, L1 + L2 - 1e-4)
    u.normalize()
    P = Vector((H.x, -30.0, 12.5))
    v = P - H
    v -= u * v.dot(u)
    v.normalize()
    ca = max(-1.0, min(1.0, (L1 * L1 + D * D - L2 * L2) / (2 * L1 * D)))
    K = H + (u * ca + v * math.sqrt(1 - ca * ca)) * L1
    X = v.cross(u).normalized()

    def frame(y):
        y = y.normalized()
        return Matrix((X, y, X.cross(y))).transposed()

    M1, M2 = frame(K - H), frame(A - K)
    ru, rl, rf = (b.matrix_local.to_3x3() for b in (bu, bl, bf))
    Mf = foot_R @ rf
    bases = (ru.transposed() @ M1, rl.transposed() @ ru @ M1.transposed() @ M2, rf.transposed() @ rl @ M2.transposed() @ Mf)
    for name, basis in zip(names, bases):
        e = basis.to_euler(RIG_J.pose.bones[name].rotation_mode)
        for i in range(3):
            c._put(f'pose.bones["{name}"].rotation_euler', i, f, e[i], ip)


def build_teleport():
    L = SEC(1.0)
    BL = SEC(0.5)
    c = Clip(RIG_J, "joash_teleport", L, markers=[("move:blink", BL)])
    c.ik_arms = False
    ik_on(c, 0, arms=0.0, legs=0.0)            # FK legs: IK does not survive the non-uniform root squash
    fk_start(c, 0)
    fk_start(c, L)
    # crouch + wrap (hips straight so the planar leg solve is exact)
    POSES = [  # f, hips z, spine, chest, neck, head
        (0, idle_values(0)["hips_z"], idle_values(0)["spine_p"], idle_values(0)["chest_p"], -2, idle_values(0)["head_p"]),
        (5, -0.5, 4, 2, -2, -10),
        (12, -5.5, 14, 26, 6, 12),
        (17, -7.0, 16, 32, 6, 15),
        (BL, -7.2, 16, 33, 6, 16),
        (23, -0.5, -4, -10, -6, -10),
        (28, -1.6, 7, 9, -1, -4),
        (32, -1.1, 6, 5, -2, -8),
        (L, idle_values(0)["hips_z"], idle_values(0)["spine_p"], idle_values(0)["chest_p"], -2, idle_values(0)["head_p"])]
    for f, hz, sp, cp, nk, hd in POSES:
        c.loc("b_hips", "z", f, hz)
        c.rot("b_hips", "z", f, 0.0)
        c.loc("b_hips", "x", f, 0.0)
        c.rot("b_spine", "x", f, sp)
        c.rot("b_chest", "x", f, cp)
        c.rot("b_neck", "x", f, nk)
        c.rot("b_head", "x", f, hd)
    c.finalize()
    jz, sgz = AMAP[(RIG_J.name, "b_hips")][2]
    for f in range(0, L + 1):           # FK legs solved on every frame: the feet stay planted between keys
        hz = sgz * c.raw_value('pose.bones["b_hips"].location', jz, f)
        for s in (1, -1):
            leg_fk3(c, s, f, Vector((3.0 * s, 0.0, 24.0 + hz)), IDLE_FOOT[s], IDLE_FOOT_R[s], "L")
    for f, r in ((0, 0.0), (BL, 0.0), (23, 12.0), (28, 4.0), (32, 0.0)):
        c.rot("b_head", "y", f, r)
    # root squash -> blink -> unfurl with overshoot
    for f, xy, z in ((0, 1, 1), (9, 1.0, 1.0), (13, 1.08, 0.78), (17, 1.22, 0.36), (BL, 1.3, 0.15),
                     (22, 0.84, 1.28), (26, 1.07, 0.9), (30, 0.97, 1.04), (34, 1.0, 1.0), (L, 1, 1)):
        c.scl("b_root", "x", f, xy)
        c.scl("b_root", "y", f, xy)
        c.scl("b_root", "z", f, z)
    # arms wrap the body staggered (left high, right low: the forearms lie one above the other, in front of the
    # chest), then unfurl into an asymmetric spread - left claw up, right claw at the player (right lags a frame)
    for f, l, r in ((7, (55, 8, -45, 90, 10), (22, 12, -30, 70, 8)),
                    (12, (80, 0, -75, 108, 14), (52, 2, -90, 102, 12)),
                    (BL, (86, 0, -80, 112, 16), (55, 0, -95, 105, 14))):
        key_arm_fk(c, 1, f, flex=l[0], abd=l[1], twist=l[2], elbow=l[3], wflex=l[4], wdev=0)
        key_arm_fk(c, -1, f, flex=r[0], abd=r[1], twist=r[2], elbow=r[3], wflex=r[4], wdev=0)
    key_arm_fk(c, 1, 23, flex=30, abd=110, twist=25, elbow=40, wflex=-20, wdev=0, ip="B")
    key_arm_fk(c, -1, 24, flex=65, abd=55, twist=15, elbow=55, wflex=-10, wdev=0, ip="B")
    key_arm_fk(c, 1, 28, flex=22, abd=34, twist=16, elbow=44, wflex=6, wdev=-4)
    key_arm_fk(c, -1, 29, flex=28, abd=22, twist=14, elbow=46, wflex=6, wdev=-4)
    for s in (1, -1):
        key_fingers(c, s, 12, "grip")
        key_fingers(c, s, BL, "fist")
        key_fingers(c, s, 23 if s > 0 else 24, "splay", "B")
        key_fingers(c, s, 28 if s > 0 else 29, "claw")
    orb_combat(c, L)
    return c


def build_summon(idle_ik):
    """Gather (hands drawn in to the chest, claws curled, hunched, glaring) -> 2-3 frame burst into arms up and out,
    elbows above the shoulders and nearly straight, palms up and out -> trembling hold -> release."""
    L = SEC(1.2)
    SU = SEC(0.6)
    c = Clip(RIG_J, "joash_summon_illusions", L, markers=[("summon", SU)])
    ik_on(c, 0, arms=1.0, legs=1.0)
    idle_pose(c, 0)
    idle_pose(c, L)
    idle_fingers(c, 0)
    idle_fingers(c, L)
    for f, hz, sp, cp, nk, hd, ip in ((8, -1.0, 4, 10, 4, 8, "A"), (17, -1.7, 6, 14, 5, 9, "A"),
                                      (19, -1.6, 5, 12, 4, 6, "SI"), (22, -0.4, -2, -10, -5, -14, "B"),
                                      (SU, -0.5, -2, -10, -5, -15, "A"), (32, -0.8, -1, -8, -4, -12, "A"),
                                      (40, -1.0, 4, 2, -3, -8, "A")):
        c.loc("b_hips", "z", f, hz, ip)
        c.rot("b_spine", "x", f, sp, ip)
        c.rot("b_chest", "x", f, cp, ip)
        c.rot("b_neck", "x", f, nk, ip)
        c.rot("b_head", "x", f, hd, ip)
    for s in (1, -1):
        W0, R0, P0 = idle_ik[s]
        key_hand(c, s, 0, W0, R0, "wrist")
        key_pole(c, s, 0, P0)
        IN = orient((-s * 0.55, -0.75, 0.3), (-s * 0.6, 0.2, -0.75))       # claws drawn in toward the chest/orb
        key_hand(c, s, 8, Vector((s * 8.5, -8.0, 34.0)), IN, "wrist")
        key_hand(c, s, 17, Vector((s * 6.5, -8.5, 35.5)), IN, "wrist")
        key_hand(c, s, 19, Vector((s * 7.0, -8.5, 36.5)), IN, "wrist", "SI")
        PU = orient((s * 0.75, -0.25, 0.6), (-s * 0.35, -0.2, 0.92))       # palms up and out, fingers out
        key_hand(c, s, 22, Vector((s * 17.6, -5.5, 59.0)), PU, "wrist", "B")
        for i, f in enumerate(range(SU, 33, 2)):
            sg = 1 if i % 2 == 0 else -1
            key_hand(c, s, f, Vector((s * (17.0 + 0.4 * sg), -5.5, 58.0 - 0.15 * i + 0.4 * sg)), Rx(-3 * sg) @ PU, "wrist", "L")
        key_hand(c, s, 38, Vector((s * 13.0, -7.0, 40.0)), R0, "wrist")
        key_hand(c, s, L, W0, R0, "wrist")
        key_pole(c, s, 8, (16, 8, 26))
        key_pole(c, s, 17, (16, 8, 26))
        key_pole(c, s, 22, (20, 4, 30))
        key_pole(c, s, 32, (20, 4, 30))
        key_pole(c, s, L, P0)
        key_fingers(c, s, 8, "claw")
        key_fingers(c, s, 17, "fist", extra={"index": (-20, -20, 0), "middle": (-20, -20, 0), "ring": (-20, -20, 0)})
        key_fingers(c, s, 19, "claw", "SI")
        key_fingers(c, s, 22, "splay", "B")
        for i, f in enumerate(range(SU + 1, 33, 2)):          # trembling
            amp = 9 * (1.0 - 0.5 * i / 5.0)
            sg = 1 if i % 2 == 0 else -1
            key_fingers(c, s, f, "splay", "L", extra={fg: (sg * amp * (1 + 0.2 * j), -sg * amp * 0.8, 0)
                                                      for j, fg in enumerate(("index", "middle", "ring", "thumb"))})
        key_fingers(c, s, 34, "splay")
        key_fingers(c, s, 41, "claw")
    for f, z in ((0, 0), (12, 2), (19, 3), (SU, 8.5), (32, 7.5), (L, 0)):
        c.loc("b_orb_pivot", "z", f, z + ORB_Z0)
    c.loc("b_orb_pivot", "x", 0, 0.0)
    c.loc("b_orb_pivot", "y", 0, 0.0)
    for f, sc in ((0, 1), (SU - 2, 1.0), (SU, 1.3), (SU + 5, 1.0), (L, 1)):
        for a in "xyz":
            c.scl("b_orb", a, f, sc)
    ang, angs = 0.0, {}
    for f in range(0, L + 1):
        angs[f] = ang
        ang += 2 * math.pi / 57.0 * (1 + 3.0 * math.sin(math.pi * f / L) ** 2)
    sc_a = round(angs[L] / (2 * math.pi)) * 2 * math.pi / angs[L]
    key_sats(c, sorted(set(range(0, L + 1, 2)) | {L}), lambda f: angs[int(f)] * sc_a,
             lambda k, f: SAT_R + 0.3 * math.sin(2 * math.pi * f / L) + (3.0 * math.sin(math.pi * (f - SU) / 12.0) if SU <= f <= SU + 12 else 0))
    orb_spin(c, L, turns=-2.0)
    return c


def build_hurt():
    """ADDITIVE: JehoashModel plays hurt at full weight ON TOP of idle/walk (vanilla animations add up), so this
    clip only holds the reaction offsets from the rest pose (zero at f0 and at the end): head snaps back and aside,
    chest recoils, shoulders flinch up, arms jerk out, fingers splay (against the idle claw curl), the ball jolts."""
    L = SEC(0.5)
    c = Clip(RIG_J, "joash_hurt", L)
    c.ik_arms = False
    ik_on(c, 0, arms=0.0, legs=0.0)            # pure FK deltas; no leg keys (the base clip's feet stay planted)
    zero = {"sp": 0, "cp": 0, "cr": 0, "nk": 0, "hp": 0, "hy": 0, "hr": 0}
    rows = [(0, zero, "A"), (2, dict(sp=-2, cp=-8, cr=2, nk=-5, hp=-12, hy=15, hr=6), "S"),
            (5, dict(sp=-2.5, cp=-9, cr=2.5, nk=-5, hp=-11, hy=13, hr=5), "A"),
            (10, dict(sp=-0.5, cp=-2, cr=0.5, nk=-1, hp=-3, hy=4, hr=1), "A"), (14, dict(sp=0, cp=0.5, cr=0, nk=0, hp=1, hy=-1, hr=0), "A"),
            (L, zero, "A")]
    ch = {"sp": ("b_spine", "x"), "cp": ("b_chest", "x"), "cr": ("b_chest", "y"), "nk": ("b_neck", "x"),
          "hp": ("b_head", "x"), "hy": ("b_head", "z"), "hr": ("b_head", "y")}
    for f, vals, ip in rows:
        for k, v in vals.items():
            b, ax = ch[k]
            c.rot(b, ax, f, v, ip)
    splay = {fg: tuple(0.9 * (FP["splay"][fg][i] - FP["claw"][fg][i]) for i in range(3)) for fg in FP["splay"]}
    zero_f = {fg: (0.0, 0.0, 0.0) for fg in FP["splay"]}
    for s in (1, -1):
        n = sname(s)
        for f, z, e, ip in ((0, 0.0, 0, "A"), (3, 0.25, 3, "S"), (9, 0.08, 1, "A"), (L, 0.0, 0, "A")):
            c.loc(f"b_shoulder_{n}", "z", f, z, ip)
            c.rot(f"b_shoulder_{n}", "y", f, -s * e, ip)
        for f, k, ip in ((0, 0.0, "A"), (3, 1.0, "S"), (8, 0.55, "A"), (13, 0.15, "A"), (L, 0.0, "A")):
            key_arm_fk(c, s, f, flex=-6 * k, abd=12 * k, twist=4 * k, elbow=10 * k, wflex=-14 * k, wdev=0, ip=ip)
        for f, k, ip in ((0, 0.0, "A"), (3, 1.0, "S"), (9, 0.6, "A"), (14, 0.15, "A"), (L, 0.0, "A")):
            key_fingers(c, s, f, zero_f, ip, extra={fg: tuple(k * v for v in splay[fg]) for fg in splay})
    for f, o in ((0, (0, 0, 0)), (3, (-1.4, 0.8, -0.6)), (9, (-0.5, 0.3, -0.2)), (L, (0, 0, 0))):
        for i, a in enumerate("xyz"):
            c.loc("b_orb_pivot", a, f, o[i], raw=True)
    return c


def build_death(idle_ik):
    L = SEC(4.0)
    c = Clip(RIG_J, "joash_death", L)
    ik_on(c, 0, arms=1.0, legs=1.0)
    idle_pose(c, 0)
    idle_fingers(c, 0)
    KH = 1.8          # the kneel sits 1.8 px higher than before: shins rest on the floor instead of sinking into it
    # body: recoil, stagger clutching head, drop to knees, arms to the sky (crescendo, face <= ~40 deg up),
    # hang, then dead-weight collapse forward with a rebound; the head lands last and lolls aside
    BODY = [  # f, hips x, y, z, hips pitch, spine, chest, chest yaw, neck, head p, head yaw, head roll, ip
        (4, 0.0, 1.6, -1.2, 0, -4, -10, 0, -5, -16, 6, 0, "A"),
        (12, 0.6, 1.8, -1.6, 0, 6, 10, 4, 6, 14, -6, 4, "A"),
        (24, -1.6, 2.8, -1.8, 2, 8, 14, -8, 8, 16, 14, -6, "A"),
        (34, 1.8, 3.4, -2.2, 2, 9, 12, 8, 6, 10, -12, 8, "A"),
        (46, -1.4, 3.6, -2.4, 2, 7, 15, -6, 8, 18, 10, -6, "A"),
        (56, 0.8, 3.4, -2.6, 2, 8, 13, 4, 6, 12, -6, 4, "A"),
        (61, 0.4, 3.2, -3.4, 4, 10, 18, 0, 6, 14, 0, 0, "A"),
        (72, 0.2, 2.4, -9.0 + KH * 0.5, 8, 14, 24, 0, 8, 18, 0, 0, "QI"),
        (80, 0.0, 1.4, -13.6 + KH, 4, 16, 30, 0, 8, 22, 0, 0, "A"),        # knees hit
        (84, 0.0, 1.4, -12.9 + KH, 2, 10, 18, 0, 4, 12, 0, 0, "A"),
        (91, 0.0, 1.4, -13.2 + KH, 0, 4, 6, 0, 0, 2, 0, 0, "A"),
        (105, 0.0, 1.4, -12.8 + KH, -3, -5, -12, 0, -6, -14, 0, 0, "A"),    # the sky opens: reach up, face ~40 up
        (117, 0.0, 1.4, -12.5 + KH, -4, -6, -15, 0, -6, -10, 0, 3, "A"),
        (127, 0.0, 1.4, -12.4 + KH, -4, -7, -18, 0, -5, -8, 0, 2, "A"),     # hang ...
        (129, 0.0, 1.4, -12.4 + KH, -4, -7, -18, 0, -5, -8, 0, 2, "QI"),    # ... then dead weight
        (139, 0.0, 1.0, -13.5 + KH, 10, 27, 50, 0, 2, 6, 0, -4, "A"),       # folds flat over his knees
        (142, 0.0, 1.0, -13.35 + KH, 9, 25, 46, 0, 2, 7, 3, -12, "A"),      # rebound
        (145, 0.0, 1.0, -13.5 + KH, 10, 26, 49, 0, 1, 6, 8, -22, "A"),
        (149, 0.0, 1.0, -13.5 + KH, 10, 27, 50, 0, 0, 5, 12, -30, "A"),     # head lands last, lolling aside
        (L, 0.0, 1.0, -13.5 + KH, 10, 27, 50, 0, 0, 5, 12, -30, "A")]
    for f, hx, hy, hz, hp, sp, cp, cy, nk, hdp, hdy, hdr, ip in BODY:
        c.loc("b_hips", "x", f, hx, ip)
        c.loc("b_hips", "y", f, hy, ip)
        c.loc("b_hips", "z", f, hz, ip)
        c.rot("b_hips", "x", f, hp, ip)
        c.rot("b_spine", "x", f, sp, ip)
        c.rot("b_chest", "x", f, cp, ip)
        c.rot("b_chest", "z", f, cy, ip)
        c.rot("b_neck", "x", f, nk, ip)
        c.rot("b_head", "x", f, hdp, ip)
        c.rot("b_head", "z", f, hdy, ip)
        c.rot("b_head", "y", f, hdr, ip)
    for i, f in enumerate(range(107, 128, 2)):            # growing tremble while he reaches
        amp = 1.0 + 2.0 * i / 10.0
        sg = 1 if i % 2 == 0 else -1
        c.rot("b_chest", "y", f, sg * amp, "L")
        c.rot("b_head", "z", f, -sg * amp * 0.8, "L")
    # feet: stagger steps back, then fold under for the kneel (feet tops on the floor)
    for s in (1, -1):
        p0 = IDLE_FOOT[s]
        key_foot(c, s, 0, p0, IDLE_FOOT_R[s])
        st = (22, 30) if s < 0 else (38, 46)
        key_foot(c, s, st[0], p0 + Vector((0, 0, 0)), IDLE_FOOT_R[s])
        key_foot(c, s, (st[0] + st[1]) / 2, p0 + Vector((0, 2.5, 2.2)), IDLE_FOOT_R[s] @ Rx(10))
        key_foot(c, s, st[1], p0 + Vector((0, 4.0, 0)), IDLE_FOOT_R[s])
        key_foot(c, s, 61, p0 + Vector((0, 4.0, 0)), IDLE_FOOT_R[s])
        key_foot(c, s, 72, Vector((s * 3.6, 6.0, 4.4 + KH * 0.5)), Rx(70))
        key_foot(c, s, 80, Vector((s * 3.4, 8.2, 1.0 + KH)), Rx(150))
        key_foot(c, s, L, Vector((s * 3.4, 8.2, 1.0 + KH)), Rx(150))
    # hands: fly to the head (death_clutch_pass re-keys them head-attached 14-61), down at the knees, up to the
    # sky (one higher than the other, climbing, trembling), then they fall loose to his sides, palms up
    for s in (1, -1):
        W0, R0, P0 = idle_ik[s]
        key_hand(c, s, 0, W0, R0, "wrist")
        key_pole(c, s, 0, P0)
        key_hand(c, s, 8, Vector((s * 11.5, -6.0, 44.0)), orient((s * -0.3, -0.2, 0.93), (-s, 0, 0)), "wrist")
        key_pole(c, s, 12, (17, -2, 40))
        key_pole(c, s, 61, (17, -2, 40))
        key_hand(c, s, 64, Vector((s * 13.0, -5.0, 40.0)), orient((s * 0.3, -0.4, -0.86), (-s * 0.5, 0.3, -0.6)), "wrist")
        key_hand(c, s, 68, Vector((s * 9.5, -6.0, 26.0)), orient((s * 0.1, -0.5, -0.86), (s * 0.3, 0.8, -0.5)), "wrist")
        key_hand(c, s, 80, Vector((s * 7.0, -8.0, 16.0 + KH)), orient((0, -0.6, -0.8), (0, 0.8, -0.6)), "wrist")
        key_pole(c, s, 72, (16, 8, 18))
        key_hand(c, s, 91, Vector((s * 7.5, -7.0, 19.0 + KH)), orient((0, -0.6, -0.8), (0, 0.8, -0.6)), "wrist")
        SKY = orient((s * 0.3, -0.25, 0.92), (0, -0.95, -0.25))
        hi = 1.5 if s > 0 else 0.0                       # his left hand reaches higher
        key_hand(c, s, 105, Vector((s * 10.0, -1.5, 46.0 + KH + hi)), SKY, "wrist")
        for i, f in enumerate(range(109, 128, 3)):
            sg = 1 if i % 2 == 0 else -1
            amp = 0.3 + 0.5 * i / 6.0
            key_hand(c, s, f, Vector((s * (10.4 + sg * amp * 0.6), -1.2, 45.5 + KH + hi * 0.6 + 0.3 * i + sg * amp)), SKY, "wrist", "L")
        key_hand(c, s, 129, Vector((s * 10.8, -1.0, 47.4 + KH + hi * 0.6)), SKY, "wrist", "QI")
        key_pole(c, s, 100, (18, 6, 26))
        key_pole(c, s, 129, (18, 6, 26))
        FALL = orient((s * 0.25, -0.45, -0.86), (s * 0.3, 0.85, -0.4))
        UP = orient((s * 0.35, -0.93, -0.05), (-s, 0.0, 0.5))                # limp at his side, resting on its edge
        rel = blend_pose("relax", "flat", 0.6)
        key_hand(c, s, 135, Vector((s * 13.5, -11.0, 22.0)), FALL, "wrist", "QI")
        key_hand(c, s, 138, Vector((s * 12.4, -12.6, 9.0)), Rx(-35) @ UP, "wrist", pose=rel)   # turning palm-up as it falls
        key_hand(c, s, 140, Vector((s * 11.2, -12.7, 1.6)), Rx(-12) @ UP, "wrist", pose=rel, surface=0.0)
        key_hand(c, s, 141, Vector((s * 10.8, -12.6, 0.0)), UP, "wrist", pose=rel, surface=0.0)
        key_hand(c, s, 144, Vector((s * 11.0, -12.9, 0.0)), Rz(-s * 6) @ UP, "wrist", pose=rel, surface=0.0)
        key_hand(c, s, L, Vector((s * 11.0, -12.9, 0.0)), Rz(-s * 6) @ UP, "wrist", pose=rel, surface=0.0)
        key_pole(c, s, 137, (18, 8, 20))
        key_pole(c, s, L, (18, 8, 20))
        key_fingers(c, s, 6, "splay")
        key_fingers(c, s, 14, "press")
        key_fingers(c, s, 61, "press")
        key_fingers(c, s, 70, "relax")
        key_fingers(c, s, 92, "relax")
        key_fingers(c, s, 102, "splay")
        for i, f in enumerate(range(104, 129, 2)):
            sg = 1 if i % 2 == 0 else -1
            amp = 4 + 6 * i / 12.0
            key_fingers(c, s, f, "splay", "L", extra={fg: (sg * amp, -sg * amp * 0.8, 0) for fg in ("index", "middle", "ring", "thumb")})
        key_fingers(c, s, 134, "relax")
        key_fingers(c, s, 143, rel)
        key_fingers(c, s, L, rel)
    # orb: hovers (wobbling), drops to the ground at 3.4 s and bounces; lands on a face; satellites scatter
    DROP = SEC(3.4)
    orb_frames = list(range(0, DROP + 1, 2))
    orb_idle(c, orb_frames)
    SKY_OFF = Vector((-3.5, 5.0, 1.5))                   # out of the right hand's way while he reaches for the sky

    def sky_w(f):
        u = min(1.0, max(0.0, (f - 96) / 8.0))
        return u * u * (3 - 2 * u)

    for f in orb_frames:
        if f >= 96:
            for i, a in enumerate("xyz"):
                base = (0.6 * math.sin(2 * math.pi * f / 114.0), 0.0, 1.2 * math.sin(2 * math.pi * f / 57.0 + 0.8))[i]
                c.loc("b_orb_pivot", a, f, base + SKY_OFF[i] * sky_w(f))
    spin_drop = -360.0 * DROP / IDLE_L
    spin_end = round((spin_drop - 140) / 90.0) * 90.0
    SETTLE = DROP + 18                                   # the tumble ends flat on a face
    c.spin("b_orb", "z", 0, 0.0)
    c.spin("b_orb", "z", DROP, spin_drop, "QI")
    c.spin("b_orb", "z", SETTLE, spin_end)
    c.spin("b_orb", "z", L, spin_end)
    c.rot("b_orb", "x", 0, 15.0)
    c.rot("b_orb", "x", DROP, 15.0, "QI")
    c.rot("b_orb", "x", SETTLE, 90.0)
    c.rot("b_orb", "x", L, 90.0)
    v0 = idle_values(0)
    piv0 = Vector((0.6 * math.sin(2 * math.pi * DROP / 114.0), 0, 1.2 * math.sin(2 * math.pi * DROP / 57.0 + 0.8))) + SKY_OFF
    ground = 3.0 * math.sqrt(3) + 0.05 - 52.0           # tumbling: keep every corner above the floor ...
    rest_g = 3.0 + 0.05 - 52.0                          # ... and settle on a face
    ORBP = [(DROP, piv0, "QI"), (DROP + 10, Vector((-9.0, 6.0, ground)), "QO"), (DROP + 13, Vector((-10.5, 4.5, ground + 2.4)), "QI"),
            (DROP + 16, Vector((-11.5, 3.0, ground)), "QO"), (SETTLE, Vector((-12.0, 2.0, rest_g + 0.6)), "QI"),
            (SETTLE + 2, Vector((-12.2, 1.6, rest_g)), "A"), (L, Vector((-12.2, 1.6, rest_g)), "A")]
    for f, o, ip in ORBP:
        c.loc3("b_orb_pivot", f, o, ip)
    c.finalize()
    piv_ch = [AMAP[(RIG_J.name, "b_orb_pivot")][w] for w in range(3)]
    sat_ground = {1: Vector((20.0, -4.0, 0.65)), 2: Vector((-9.0, 18.0, 0.65)), 3: Vector((-24.0, -10.0, 0.65))}
    rest_world = Vector((-10, 2, 52)) + ORB_OUT

    def pivot_world(f):
        """World position of the pivot from the clip's own (eased) F-curves, so the shards land where intended."""
        if f <= DROP:
            return rest_world + piv0
        v = Vector([sg * c.raw_value('pose.bones["b_orb_pivot"].location', j, f) for j, sg in piv_ch])
        return Vector((-10, 2, 52)) + v

    key_sats(c, list(range(0, DROP + 1, 2)), lambda f: 2 * math.pi * f / 57.0, lambda k, f: SAT_R)
    a_drop = 2 * math.pi * DROP / 57.0

    def scatter(k, f):
        u = min(1.0, (f - DROP) / 12.0)
        off0 = sat_offset(k, SAT_PHASE[k] + SAT_DIR[k] * a_drop, SAT_R)
        start = rest_world + piv0 + off0
        end = sat_ground[k]
        arc = Vector((0, 0, 9.0 * math.sin(math.pi * u) if u < 1 else 0))
        pop = off0.normalized() * 5.0 * math.sin(math.pi * min(1.0, u * 2.5)) if u < 0.4 else Vector()   # burst out first
        w = start.lerp(end, 1 - (1 - u) ** 2) + arc * (1 - u) + pop * (1 - u)
        off = w - pivot_world(f)
        if off.length < 7.4:                     # never inside the tumbling ball's reach (corner 5.2 + shard 1.04)
            off = off.normalized() * 7.4 if off.length > 1e-6 else Vector((0, 0, 7.4))
        return off

    key_sats(c, list(range(DROP + 1, L + 1)), None, None, world_fn=scatter)
    for k in (1, 2, 3):
        sg = 1 if k != 2 else -1
        s_drop = 720.0 * DROP / IDLE_L * sg
        s_end = round((s_drop + 400 * sg) / 90.0) * 90.0
        c.spin(f"b_orb_sat_{k}", "z", 0, 0.0)
        c.spin(f"b_orb_sat_{k}", "z", DROP, s_drop)
        c.spin(f"b_orb_sat_{k}", "z", DROP + 12, s_end, "QO")
        c.spin(f"b_orb_sat_{k}", "z", L, s_end)
        c.rot(f"b_orb_sat_{k}", "x", 0, 35.0 * k)
        c.rot(f"b_orb_sat_{k}", "x", DROP, 35.0 * k)
        c.rot(f"b_orb_sat_{k}", "x", DROP + 12, 90.0, "QO")
        c.rot(f"b_orb_sat_{k}", "x", L, 90.0)
    for s in (1, -1):
        n = sname(s)
        for f, z, e in ((0, v0["sh_z"], 0), (14, 0.8, 4), (61, 0.6, 3), (80, 0.2, 0), (105, 0.8, 12), (129, 0.8, 12),
                        (140, -0.4, -4), (L, -0.5, -4)):
            c.loc(f"b_shoulder_{n}", "z", f, z)
            c.rot(f"b_shoulder_{n}", "y", f, -s * e)
    return c


def death_clutch_pass(c):
    """Hands clutch the hood sides (head-attached) 14-61, palms outside the headphone cups (cup face at x +-6.5)."""
    assign(RIG_J, c.act)
    head_rest = RIG_J.data.bones["b_head"].matrix_local
    for s in (1, -1):
        b = f"ik_hand_{sname(s)}"
        c.remove_range(f'pose.bones["{b}"].location', 13.5, 61.5)
        c.remove_qrange(b, 13.5, 61.5)
    for f in range(14, 62, 2):
        eval_frame(f)
        M = RIG_J.pose.bones["b_head"].matrix @ head_rest.inverted()
        Mr = M.to_3x3().normalized()
        for s in (1, -1):
            contact = M @ Vector((s * 8.0, -0.8, 53.5))     # palm + thumb thickness off the cup
            R = Mr @ orient((s * -0.15, 0.35, 0.92), (-s, 0, 0))
            key_hand(c, s, f, contact, R, "palmc", pose="press")


# =====================================================================================  collision helpers (exact OBB)
def np_boxes(objs):
    """World centre C (n,3) and half edge vectors E (n,3 axes,3) of bone-parented unit cubes."""
    M = np.array([np.array(o.matrix_world) for o in objs], dtype=np.float64)
    C = M[:, :3, 3].copy()
    E = 0.5 * np.transpose(M[:, :3, :3], (0, 2, 1))
    return C, E


def _face_normals(E):
    n0 = np.cross(E[:, 1], E[:, 2])
    n1 = np.cross(E[:, 2], E[:, 0])
    n2 = np.cross(E[:, 0], E[:, 1])
    N = np.stack([n0, n1, n2], axis=1)
    N /= np.maximum(np.linalg.norm(N, axis=2, keepdims=True), 1e-12)
    return N


def sat_depth(CA, EA, CB, EB):
    """Row-aligned penetration depth of box A[k] vs box B[k] (separating-axis test over the 15 axes of two
    parallelepipeds). > 0 = overlap depth, < 0 = separated by at least that much."""
    if len(CA) == 0:
        return np.zeros(0)
    NA, NB = _face_normals(EA), _face_normals(EB)
    ax = [NA[:, a] for a in range(3)] + [NB[:, a] for a in range(3)]
    for a in range(3):
        for b in range(3):
            cr = np.cross(EA[:, a], EB[:, b])
            ln = np.linalg.norm(cr, axis=1, keepdims=True)
            ok = ln[:, 0] > 1e-6 * (np.linalg.norm(EA[:, a], axis=1) * np.linalg.norm(EB[:, b], axis=1) + 1e-12)
            ax.append(np.where(ok[:, None], cr / np.maximum(ln, 1e-12), NA[:, 0]))
    Lx = np.stack(ax, axis=1)
    d = CB - CA
    rA = np.abs(np.einsum("pkc,pac->pka", Lx, EA)).sum(axis=2)
    rB = np.abs(np.einsum("pkc,pac->pka", Lx, EB)).sum(axis=2)
    dist = np.abs(np.einsum("pkc,pc->pk", Lx, d))
    return (rA + rB - dist).min(axis=1)


def _windows(flags, L, loop, grow):
    """Index ranges (start, end inclusive, may run past L for loops) of True flags, dilated by `grow`."""
    n = len(flags)
    idx = [i for i in range(n) if flags[i]]
    if not idx:
        return []
    spans = []
    s0 = p = idx[0]
    for i in idx[1:]:
        if i - p > 2 * grow + 1:
            spans.append((s0, p))
            s0 = i
        p = i
    spans.append((s0, p))
    if loop and len(spans) > 1 and spans[0][0] + L - spans[-1][1] <= 2 * grow + 1:
        first = spans.pop(0)
        last = spans.pop()
        spans.append((last[0], first[1] + L))
    return spans


ORB_SET = ("c_joash_orb", "c_joash_orb_sat_1", "c_joash_orb_sat_2", "c_joash_orb_sat_3")
AVOID_SET = tuple(
    [f"c_joash_{p}_{s}" for s in ("l", "r") for p in ("shoulder", "upperarm", "forearm", "palm")]
    + [f"c_joash_finger_{fg}_{s}_{i}" for s in ("l", "r") for fg in ("index", "middle", "ring") for i in (1, 2)]
    + [f"c_joash_thumb_{s}_{i}" for s in ("l", "r") for i in (1, 2)]
    + ["c_joash_head", "c_joash_hood", "c_joash_hp_band", "c_joash_hp_cup_l", "c_joash_hp_cup_r", "c_joash_neck",
       "c_joash_chest"])
AVOID_DIRS = [(0, 0.78, 0.62), (0, 0.45, 0.89), (-0.55, 0.55, 0.62), (0, 1, 0), (0, 0, 1), (-0.8, 0.0, 0.6),
              (-0.7, 0.7, 0.0), (0.35, 0.6, 0.72), (0, -0.55, 0.83), (-0.5, 0.3, -0.81)]


def _pivot_world_add(c, g0, g1, offs):
    """Re-key b_orb_pivot location on every frame g0..g1 (may run past L for loops) as its current value plus the
    world offset offs[frame]; keys already inside the run are replaced."""
    path = 'pose.bones["b_orb_pivot"].location'
    L = c.L
    fs = []
    for g in range(g0, g1 + 1):
        f = g % L if c.loop else g
        if c.loop or 0 <= f <= L:
            fs.append(f)
    base = {f: [c.raw_value(path, j, f) for j in range(3)] for f in fs}
    span = g1 - g0
    for j in range(3):
        d = c.keys.get((path, j), {})
        for k in list(d):
            if c.loop:
                kk = (k - g0) % L
                inside = kk <= span + 0.5 or kk >= L - 0.5
            else:
                inside = g0 - 0.5 <= k <= g1 + 0.5
            if inside:
                del d[k]
    for f in fs:
        off = offs.get(f, Vector())
        for w in range(3):
            j, sg = c._am("b_orb_pivot", w)
            c._put(path, j, f, base[f][j] + sg * off[w], "A")


def orb_avoid_pass(c, margin=0.7, ease=6, exclude=(), iters=3, dirs=AVOID_DIRS, smax=14.0):
    """The disco ball and its shards ease out of the way of the arms, hands and head: on every frame where one of
    them comes within `margin` px of the body, b_orb_pivot gets an offset along the cheapest escape direction
    (searched per contact window), faded in/out over +-`ease` frames and keyed every frame."""
    if c.rig is not RIG_J:
        return []
    A = [bpy.data.objects[n] for n in ORB_SET]
    B = [bpy.data.objects[n] for n in AVOID_SET]
    I = np.repeat(np.arange(len(A)), len(B))
    Jx = np.tile(np.arange(len(B)), len(A))
    L = c.L
    frames = list(range(0, L)) if c.loop else list(range(0, L + 1))

    def excl(f):
        return any(a <= f <= b for a, b in exclude)

    log = []
    for it in range(iters):
        c.finalize()
        assign(RIG_J, c.act)
        data = {}
        for f in frames:
            eval_frame(f)
            CA, EA = np_boxes(A)
            CB, EB = np_boxes(B)
            data[f] = (CA[I], EA[I], CB[Jx], EB[Jx])

        def depth(f, off=None):
            CA, EA, CB, EB = data[f]
            return float(sat_depth(CA if off is None else CA + off, EA, CB, EB).max())

        flags = [(not excl(f)) and depth(f) + margin > 0 for f in frames]
        wins = _windows(flags, L, c.loop, ease)
        if not wins:
            break
        plan = {}
        for w0, w1 in wins:
            hit = [g for g in range(w0, w1 + 1) if flags[g % len(frames)]]
            best = None
            for k, dv in enumerate(dirs):
                dvec = np.array(dv, dtype=np.float64)
                dvec /= np.linalg.norm(dvec)
                sv = {}
                worst = 0.0
                for g in hit:
                    f = g % L if c.loop else g
                    s = 0.0
                    while s < smax and depth(f, dvec * s) + margin > 0:
                        s += 0.4
                    sv[g] = s
                    worst = max(worst, s)
                cost = worst * (1.0 + 0.05 * k)
                if best is None or cost < best[0]:
                    best = (cost, dvec, sv)
            _, dvec, sv = best
            for g in range(w0 - ease, w1 + ease + 1):
                if not c.loop and (g < 0 or g > L):
                    continue
                f = g % L if c.loop else g
                if excl(f):
                    continue
                amp = 0.0
                for h, s in sv.items():
                    u = min(1.0, abs(g - h) / float(ease + 1))
                    amp = max(amp, s * (0.5 + 0.5 * math.cos(math.pi * u)))
                if amp > 1e-3:
                    plan[f] = plan.get(f, Vector()) + Vector(dvec) * amp
            log.append(dict(it=it, window=(w0, w1), dir=tuple(round(float(x), 2) for x in dvec),
                            max_offset=round(max(sv.values()), 2)))
        if not plan:
            break
        # re-key per frame inside each contiguous run of planned frames (+1 frame either side for continuity)
        for r0, r1 in _windows([f in plan for f in frames], L, c.loop, 0):
            g0, g1 = r0 - 1, r1 + 1
            if not c.loop:
                g0, g1 = max(0, g0), min(L, g1)
            _pivot_world_add(c, g0, g1, {(g % L if c.loop else g): plan.get(g % L if c.loop else g, Vector())
                                         for g in range(g0, g1 + 1)})
    c.finalize()
    c.orb_log = log
    return log


def clear_pass(c, movers, obstacles, chan, axis, sign, margin, frames, smooth=10, step=2, smax=5.0, booth_act=None,
               pin_ends=False):
    """Smallest translation of the `movers` cubes along +-axis (world) that keeps them `margin` px clear of
    `obstacles`; added to the location `chan` bone (hips: the whole body moves, IK hands stay on their
    targets) as a smoothed envelope keyed every `step` frames. Returns the per-frame requirement."""
    A = [bpy.data.objects[n] for n in movers]
    B = [bpy.data.objects[n] for n in obstacles]
    I = np.repeat(np.arange(len(A)), len(B))
    Jx = np.tile(np.arange(len(B)), len(A))
    c.finalize()
    assign(RIG_J, c.act)
    if booth_act is not None:
        assign(RIG_B, booth_act)
    dv = np.zeros(3)
    dv[AX[axis]] = sign
    req = {}
    for f in frames:
        eval_frame(f)
        CA, EA = np_boxes(A)
        CB, EB = np_boxes(B)
        CA, EA, CB, EB = CA[I], EA[I], CB[Jx], EB[Jx]
        s = 0.0
        while s < smax and float(sat_depth(CA + dv * s, EA, CB, EB).max()) + margin > 0:
            s += 0.1
        req[f] = s
    hot = [f for f, s in req.items() if s > 0]
    if not hot:
        return req
    L = c.L
    path = f'pose.bones["{chan}"].location'
    j, sg = c._am(chan, AX[axis])
    lo, hi = min(hot) - smooth - step, max(hot) + smooth + step
    keyf = list(range(int(lo) - int(lo) % step, int(hi) + 1, step))
    base = {g: c.raw_value(path, j, g % L if c.loop else max(0, min(L, g))) for g in keyf}
    add = {}
    for g in keyf:
        amp = 0.0
        for h in hot:
            dd = abs(g - h)
            if c.loop:
                dd = min(dd, L - dd)
            u = min(1.0, dd / float(smooth + 1))
            amp = max(amp, req[h] * (0.5 + 0.5 * math.cos(math.pi * u)))
        if pin_ends and not c.loop:
            amp *= min(1.0, max(0.0, min(g, L - g)) / 4.0)
        add[g] = amp
    d = c.keys.get((path, j), {})
    for k in list(d):
        if c.loop:
            inside = any(abs(((k - g + L / 2) % L) - L / 2) < step / 2 + 1e-6 for g in keyf)
        else:
            inside = lo - 1e-6 <= k <= hi + 1e-6
        if inside:
            del d[k]
    for g in keyf:
        if not c.loop and (g < 0 or g > L):
            continue
        c._put(path, j, g, base[g] + sg * sign * add[g], "A")
    c.finalize()
    return req


def shoulder_clear_pass(c, margin=0.25):
    """The shoulder pads sit only 2 px under the hood's rim: wherever a hike, an elevation or a head tilt would put
    the hood onto a pad, that shoulder drops just enough (smoothed), so the hood never sinks into it."""
    if c.rig is not RIG_J:
        return
    frames = list(range(0, c.L)) if c.loop else list(range(0, c.L + 1))
    for sn in ("l", "r"):
        clear_pass(c, (f"c_joash_shoulder_{sn}",), HEAD_SET, f"b_shoulder_{sn}", "z", -1, margin, frames,
                   smooth=6, step=2, smax=3.0, pin_ends=True)


# =====================================================================================  cloth drape pass
def collect_pose(c, frames, bones):
    assign(RIG_J, c.act)
    out = {}
    for f in frames:
        eval_frame(f)
        out[f] = {b: RIG_J.pose.bones[b].matrix.copy() for b in bones}
    return out


def _chain_style(chain):
    """(preferred push direction, width axis, half width, half thickness) in rest armature axes."""
    if chain.startswith("cape"):
        return Vector((0, 1, 0)), Vector((1, 0, 0)), 2.25, 0.3
    sid = chain.split("_")[1]
    x, y = next((x, y) for s, x, y, sx, sy in ROBE_STRANDS if s == sid)
    k = sid[0]
    if k == "f":
        return Vector((0, -1, 0)), Vector((1, 0, 0)), 1.5, 0.4
    if k == "b":
        return Vector((0, 1, 0)), Vector((1, 0, 0)), 1.5, 0.4
    if k in "lr":
        return Vector((1 if k == "l" else -1, 0, 0)), Vector((0, 1, 0)), 1.2, 0.4
    return Vector((math.copysign(1, x), math.copysign(1, y), 0)).normalized(), Vector((1, 0, 0)), 0.6, 0.6


def drape_pass(c, step=2, booth_until=-1, margin=0.3):
    """Gentle kinematic cloth for the phys_* chains (the game simulates them; these keys are the pose they are
    pulled toward): each segment hangs toward gravity and trails its anchor; samples along every segment and
    across its width are pushed out of thigh/shin/foot capsules (radius 2.1/1.8/1.4 + half thickness + margin)
    along the strand's own side (front strands forward, side strands outward, cape up/back), out of the torso
    boxes, above the floor and (while the booth stands there, frames < booth_until) behind the booth's back face.
    Computed in b_root's rest space, so the teleport squash scales the drape with the body. Floor-lying segments
    keep their direction from key to key (no flips)."""
    L = c.L
    need = ["b_root", "b_hips", "b_spine", "b_chest", "b_leg_upper_l", "b_leg_lower_l", "b_leg_upper_r",
            "b_leg_lower_r", "b_foot_l", "b_foot_r", "b_arm_lower_l", "b_arm_lower_r", "b_hand_l", "b_hand_r"]
    P = collect_pose(c, list(range(0, L + 1)), need)
    bones = RIG_J.data.bones
    rest = {b.name: b.matrix_local.copy() for b in bones}
    for f, M in P.items():                         # unsquash: as if b_root were at rest
        U = rest["b_root"] @ M["b_root"].inverted()
        for b in need:
            M[b] = U @ M[b]
    DOWN = Vector((0, 0, -1))
    R_TH, R_SH, R_FT = 2.1, 1.8, 1.9

    def fr(f):
        return f % L if c.loop else max(0, min(L, f))

    def capsules(f):
        M = P[f]
        out = []
        for sn in ("l", "r"):
            x = 3.0 if sn == "l" else -3.0
            hip = M[f"b_leg_upper_{sn}"].translation.copy()
            knee = M[f"b_leg_lower_{sn}"].translation.copy()
            Mf = M[f"b_foot_{sn}"] @ rest[f"b_foot_{sn}"].inverted()
            ank = Mf @ Vector((x, -0.5, 2.0))
            out.append((hip, knee, R_TH, "thigh"))
            out.append((knee, ank, R_SH, "shin"))
            out.append((Mf @ Vector((x, 0.9, 1.0)), Mf @ Vector((x, -3.4, 1.0)), R_FT, "foot"))
            elb = M[f"b_arm_lower_{sn}"].translation.copy()
            wr = M[f"b_hand_{sn}"].translation.copy()
            Mh = M[f"b_hand_{sn}"] @ rest[f"b_hand_{sn}"].inverted()
            out.append((elb, wr, 1.5, "arm"))
            out.append((wr, Mh @ Vector((6.5 if sn == "l" else -6.5, 0.0, 15.5)), 1.4, "hand"))
        return out

    LOW_Z = 7.0                                    # a shin whose both ends are below this lies on the floor (kneeling)

    def ground_at(p, lows, half_t):
        """Height a strand point at p's x/y has to stay above to rest ON the floor-lying capsules (kneeling shins,
        feet), not inside them; -inf where none is under it."""
        g = -1e9
        for A, B, R, _k in lows:
            ab = Vector((B.x - A.x, B.y - A.y, 0.0))
            ap = Vector((p.x - A.x, p.y - A.y, 0.0))
            tt = max(0.0, min(1.0, ap.dot(ab) / max(1e-9, ab.length_squared)))
            q = A + (B - A) * tt
            h = Vector((p.x - q.x, p.y - q.y, 0.0)).length
            rr = R + half_t + margin
            if h < rr:
                g = max(g, q.z + math.sqrt(rr * rr - h * h))
        return g

    TORSO = [("b_hips", (-4.5, 4.5), (-2.5, 2.5), (23, 28)), ("b_spine", (-4, 4), (-2.5, 2.5), (28, 35)),
             ("b_chest", (-5.5, 5.5), (-3, 3), (35, 44))]

    def torso_push(p, f, is_cape, half):
        M = P[f]
        for bn, xr, yr, zr in TORSO:
            Mb = M[bn] @ rest[bn].inverted()
            lp = Mb.inverted() @ p
            mx = half + margin
            if xr[0] - mx < lp.x < xr[1] + mx and yr[0] - mx < lp.y < yr[1] + mx and zr[0] < lp.z < zr[1]:
                pens = [(lp.x - (xr[0] - mx), Vector((-1, 0, 0))), ((xr[1] + mx) - lp.x, Vector((1, 0, 0))),
                        (lp.y - (yr[0] - mx), Vector((0, -1, 0))), ((yr[1] + mx) - lp.y, Vector((0, 1, 0)))]
                if is_cape:
                    pens = [pens[3]]
                dd, nn = min(pens, key=lambda x: x[0])
                return (Mb.to_3x3() @ nn).normalized() * dd
        return None

    chains = [(ch, names) for ch, names in CAPE] + [(ch, names) for ch, names in ROBE]
    key_frames = list(range(0, L, step)) if c.loop else sorted(set(range(0, L + 1, step)) | {L})
    if c.loop:
        key_frames = key_frames            # loop frames wrap; the seam is exact because f and f+L share poses
    prev_flat = {}
    YB = BOOTH_BACK_Y
    for f in key_frames:
        caps = capsules(f)
        lows = [cp for cp in caps if cp[3] == "shin" and max(cp[0].z, cp[1].z) < LOW_Z]
        Mh = P[f]["b_hips"]
        Rh = Mh.to_3x3().normalized() @ rest["b_hips"].to_3x3().transposed()
        for chain, names in chains:
            is_cape = chain.startswith("cape")
            pref_r, wax_r, half_w, half_t = _chain_style(chain)
            parent = bones[names[0]].parent.name
            Mp = P[f][parent]
            Mp_lag = P[fr(f - 4)][parent]
            Rw_p = Mp.to_3x3().normalized()
            Rw_p_lag = Mp_lag.to_3x3().normalized()
            Ranch = Rw_p @ rest[parent].to_3x3().transposed()        # anchor rest->now rotation
            pref = Ranch @ pref_r if is_cape else Rh @ pref_r
            pref = Vector((pref.x, pref.y, 0))
            pref = pref.normalized() if pref.length > 1e-6 else Vector((0, 1, 0))
            p0 = Mp @ (rest[parent].inverted() @ bones[names[0]].head_local)
            p0_lag = Mp_lag @ (rest[parent].inverted() @ bones[names[0]].head_local)
            prev_rot = Rw_p
            prev_rest = rest[parent].to_3x3()
            prev_dir = None
            start = p0
            g = 0.5 if is_cape else 0.62
            for i, n in enumerate(names):
                Rr = rest[n].to_3x3()
                Rf = prev_rot @ prev_rest.transposed() @ Rr
                u = Rf @ Vector((0, 1, 0))
                if i == 0:
                    u_lag = (Rw_p_lag @ prev_rest.transposed() @ Rr) @ Vector((0, 1, 0))
                    trail = -0.2 * (p0 - p0_lag)
                    trail.z *= 0.4
                    if trail.length > 0.3:
                        trail = trail.normalized() * 0.3
                    d = (1 - g) * (u + 0.5 * (u_lag - u)) + g * DOWN + trail
                else:
                    d = 0.72 * prev_dir + 0.28 * DOWN
                d.normalize()
                seg = bones[n].length
                end = start + d * seg
                flo = half_t + (0.05 if is_cape else 0.6)
                pk = (chain, i)
                for _ in range(12):
                    dcur = (end - start).normalized()
                    Rcur = u.rotation_difference(dcur).to_matrix() @ Rf
                    wax = (Rcur @ Rr.transposed()) @ wax_r
                    push = Vector()
                    mid = (start + end) * 0.5
                    for A, B, R, kind in caps:
                        if kind == "shin" and max(A.z, B.z) < LOW_Z:   # kneeling shin: the strand drapes over it,
                            for t in (0.34, 0.67, 1.0):                  # lifted and carried to its own side
                                for wv in ((-half_w, 0.0, half_w) if half_w > 0.7 else (0.0,)):
                                    p = start + (end - start) * t + wax * wv
                                    gz = ground_at(p, ((A, B, R, kind),), half_t)
                                    if gz > p.z:
                                        need = gz - p.z
                                        cand = (Vector((0.0, 0.0, need)) + pref * (0.8 * need)) / t
                                        if cand.length > push.length:
                                            push = cand
                            continue
                        ab = B - A
                        tt = max(0.0, min(1.0, (mid - A).dot(ab) / max(1e-9, ab.length_squared)))
                        if (mid - (A + ab * tt)).length > R + half_t + margin + half_w + seg * 0.5 + 0.5:
                            continue
                        rr = R + half_t + margin
                        for t in (0.34, 0.67, 1.0):
                            for wv in ((-half_w, 0.0, half_w) if half_w > 0.7 else (0.0,)):
                                p = start + (end - start) * t + wax * wv
                                q = A + ab * max(0.0, min(1.0, (p - A).dot(ab) / max(1e-9, ab.length_squared)))
                                dv = p - q
                                dist = dv.length
                                if dist >= rr:
                                    continue
                                nrm = dv / dist if dist > 1e-6 else pref.copy()
                                npush = (nrm + 0.8 * pref)
                                if npush.length < 1e-6:
                                    npush = pref.copy()
                                npush.normalize()
                                cosa = max(0.35, npush.dot(nrm))
                                cand = npush * ((rr - dist) / cosa / t)
                                if cand.length > push.length:
                                    push = cand
                    for t in (0.5, 1.0):
                        p = start + (end - start) * t
                        tp = torso_push(p, f, is_cape, half_t)
                        if tp is not None and tp.length / t > push.length:
                            push = tp / t
                        if f < booth_until and not is_cape and p.z < 26.0 + half_t + margin:
                            yb = YB + half_t + margin
                            if p.y < yb and (yb - p.y) / t > push.length:
                                push = Vector((0, (yb - p.y) / t, 0))
                    flo_d = flo + abs(wax.z) * half_w
                    if end.z < flo_d:                  # the floor: swing the segment out along the ground
                        hd = Vector((dcur.x, dcur.y, 0))
                        if hd.length < 0.25:
                            hd = prev_flat.get(pk, pref).copy()
                        hd.normalize()
                        need = flo_d - end.z
                        fp = Vector((0, 0, need)) + hd * need * 1.2
                        if fp.length > push.length:
                            push = fp
                    if push.length < 1e-4:
                        break
                    end = end + push
                    d = (end - start).normalized()
                    end = start + d * seg
                d = (end - start).normalized()
                if start.z - flo < seg and end.z < flo - 0.05:
                    hz = max(0.0, start.z - flo)
                    h = math.sqrt(max(0.0, seg * seg - hz * hz))
                    hd = Vector((d.x, d.y, 0))
                    if hd.length < 0.3 and pk in prev_flat:
                        hd = prev_flat[pk].copy()
                    if hd.length < 1e-3:
                        hd = Vector((start.x - Mh.translation.x, start.y - Mh.translation.y, 0))
                    if hd.length < 1e-3:
                        hd = pref.copy()
                    hd.normalize()
                    if pk in prev_flat and hd.dot(prev_flat[pk]) < 0.0:
                        hd = (hd + 2.0 * prev_flat[pk]).normalized()
                    end = Vector((start.x, start.y, flo)) + hd * h
                    d = (end - start).normalized()
                for _ in range(3):                     # projected onto the floor: lift onto any shin/foot lying there
                    gz = ground_at(end, lows, half_t)
                    if gz <= end.z + 1e-4:
                        break
                    end = Vector((end.x, end.y, gz))
                    end = start + (end - start).normalized() * seg
                d = (end - start).normalized()
                if end.z < flo + 0.5 and Vector((d.x, d.y, 0)).length > 0.25:
                    prev_flat[pk] = Vector((d.x, d.y, 0)).normalized()
                elif end.z > flo + 2.0:
                    prev_flat.pop(pk, None)
                Qw = u.rotation_difference(d)
                Rw = Qw.to_matrix() @ Rf
                basis = (Rf.transposed() @ Rw).to_quaternion()
                c.quat_local(n, f, basis)
                prev_rot, prev_rest, prev_dir, start = Rw, Rr, d, end


# =====================================================================================  FK idle -> IK equivalents
def idle_ik_equivalent(idle):
    """Evaluate idle frame 0 (FK arms) and return per side: wrist, hand rotation, pole (chest-rest space)."""
    assign(RIG_J, idle.act)
    eval_frame(0)
    pb = RIG_J.pose.bones
    out = {}
    chest_M = pb["b_chest"].matrix @ RIG_J.data.bones["b_chest"].matrix_local.inverted()
    for s in (1, -1):
        n = sname(s)
        hand = pb[f"b_hand_{n}"]
        W = hand.head.copy()
        R = hand.matrix.to_3x3().normalized() @ RIG_J.data.bones[f"b_hand_{n}"].matrix_local.to_3x3().transposed()
        a, e, t = pb[f"b_arm_upper_{n}"].head, pb[f"b_arm_lower_{n}"].head, pb[f"b_arm_lower_{n}"].tail
        ax = (t - a).normalized()
        d = e - a
        d -= ax * d.dot(ax)
        pole_w = e + d.normalized() * 12.0
        pole_rs = chest_M.inverted() @ pole_w
        out[s] = (W, R, mv(pole_rs, s))          # stored canonical (key_pole mirrors again)
    return out


# =====================================================================================  leak-proofing
REST_VAL = {"location": 0.0, "rotation_euler": 0.0, "scale": 1.0}


def fill_missing(clips):
    """Every action keys every channel any action of the same rig uses, so switching actions never leaks."""
    by_rig = {}
    for c in clips:
        by_rig.setdefault(c.rig.name, []).append(c)
    for rname, cs in by_rig.items():
        union = set()
        qunion = set()
        for c in cs:
            union |= set(c.keys)
            qunion |= set(c.qkeys)
        for c in cs:
            for (path, idx) in union:
                if (path, idx) in c.keys and c.keys[(path, idx)]:
                    continue
                prop = path.rsplit(".", 1)[1]
                if prop == "influence":
                    v = 1.0
                elif (path, idx) in [(p, i) for cc in cs for (p, i) in cc.offset]:
                    v = 0.0
                else:
                    v = REST_VAL[prop]
                c.keys.setdefault((path, idx), {})[0.0] = (v, "C")
            for b in qunion:
                if not c.qkeys.get(b):
                    c.qkeys[b] = {0.0: (Quaternion(), "C")}


# =====================================================================================  build everything
def main():
    import time
    t_start = time.time()
    tlog = []

    def tick(label):
        tlog.append((label, round(time.time() - t_start, 1)))

    only = set(x for x in ARGS.clips.split(",") if x)
    clips = []
    idle = build_idle()
    idle.finalize()
    clips.append(idle)
    IDLE_IK = idle_ik_equivalent(idle)

    jam, booth_jam = build_jam()
    jam.finalize()
    jam_clear_pass(jam, booth_jam)          # hips back where a nod would bring the hood onto the boom mic
    shoulder_clear_pass(jam)
    jam_phones_pass(jam)                    # left palm follows the evaluated head on the headphone cup
    resolve_hands(jam)
    wrist_fix_pass(jam)
    clips += [jam, booth_jam]
    tick("jam")

    want = (lambda n: not only or n in only)
    if want("interrupted"):
        it, booth_it = build_interrupted(jam, IDLE_IK)   # starts from the (un-rotated) contract-b0 jam pose
        it.finalize()
        shoulder_clear_pass(it)
        resolve_hands(it)
        wrist_fix_pass(it)
        clips += [it, booth_it]
        tick("interrupted")
    if want("walk_loop"):
        clips.append(build_walk())
        shoulder_clear_pass(clips[-1])
    if want("attack_melee"):
        clips.append(build_melee())
        shoulder_clear_pass(clips[-1])
    if want("attack_ranged"):
        c = build_ranged(IDLE_IK)
        c.finalize()
        shoulder_clear_pass(c)
        resolve_hands(c)
        wrist_fix_pass(c)
        clips.append(c)
    if want("teleport"):
        clips.append(build_teleport())
        shoulder_clear_pass(clips[-1])
    if want("summon_illusions"):
        c = build_summon(IDLE_IK)
        c.finalize()
        shoulder_clear_pass(c)
        resolve_hands(c)
        wrist_fix_pass(c)
        clips.append(c)
    if want("hurt"):
        clips.append(build_hurt())
    if want("death"):
        d = build_death(IDLE_IK)
        d.finalize()
        death_clutch_pass(d)
        d.finalize()
        shoulder_clear_pass(d)
        resolve_hands(d)
        wrist_fix_pass(d)
        clips.append(d)
    tick("clips")
    for c in clips:
        c.finalize()
    # the ball and its shards dodge arms and head (choreographed orb paths excluded)
    ORB_EXCLUDE = {"joash_attack_ranged": [(RANGED_ORB_WINDOW[0], RANGED_ORB_WINDOW[1])],
                   "joash_death": [(SEC(3.4) - 2, SEC(4.0))]}
    for c in clips:
        if c.rig is RIG_J and c.name != "joash_hurt":
            orb_avoid_pass(c, exclude=ORB_EXCLUDE.get(c.name, ()))
    tick("orb_avoid")
    # cloth drape (needs the final body motion); hurt is an additive delta and leaves the cloth alone
    for c in clips:
        if c.rig is RIG_J and c.name != "joash_hurt":
            until = c.L + 1 if c.name == "joash_jam_loop" else (M_BLINK if c.name == "joash_interrupted" else -1)
            drape_pass(c, step=2 if c.loop else 1, booth_until=until)
    tick("drape")
    # beat-clock phase: the contract-b28 fling lands on the track's drops (see JAM_PHASE_BEATS)
    if JAM_PHASE_BEATS:
        jam.rotate(F(JAM_PHASE_BEATS))
        booth_jam.rotate(F(JAM_PHASE_BEATS))
    fill_missing(clips)
    for c in clips:
        c.finalize()
    jam.act["jam_phase_beats"] = JAM_PHASE_BEATS
    booth_jam.act["jam_phase_beats"] = JAM_PHASE_BEATS
    for c in clips:
        if c.name == "joash_hurt":
            c.act["additive"] = True           # played on top of idle/walk in game (JehoashModel adds it)

    # default state: jam loop on both rigs, music strip at frame 0
    assign(RIG_J, jam.act)
    assign(RIG_B, booth_jam.act)
    scene.frame_start, scene.frame_end = 0, JAM_L - 1
    finish_scene()
    scene.frame_set(0)
    tick("done")
    print(f"BUILD: {len(clips)} actions in {time.time() - t_start:.1f}s; pole calibration {POLE_CAL}; times {tlog}")
    return clips


def finish_scene():
    # floor + camera
    me = bpy.data.meshes.new("floor")
    me.from_pydata([(-120, -120, 0), (120, -120, 0), (120, 120, 0), (-120, 120, 0)], [], [(0, 1, 2, 3)])
    me.materials.append(MATS["floor"])
    fl = bpy.data.objects.new("floor", me)
    COLL_P.objects.link(fl)
    cam_d = bpy.data.cameras.new("cam_preview")
    cam_d.lens = 45
    cam_d.clip_start = 0.5
    cam_d.clip_end = 2000
    cam = bpy.data.objects.new("cam_preview", cam_d)
    COLL_P.objects.link(cam)
    pos, tgt = Vector((58, -100, 66)), Vector((0, -8, 34))
    cam.location = pos
    cam.rotation_euler = (tgt - pos).to_track_quat("-Z", "Y").to_euler()
    scene.camera = cam
    # sound
    if os.path.exists(ARGS.music):
        se = scene.sequence_editor_create()
        st = se.sequences.new_sound("disco", ARGS.music, 1, 0)
        st.sound.use_memory_cache = False
        scene.sync_mode = "AUDIO_SYNC"          # playback stays locked to the phonk
        scene.use_audio_scrub = True            # scrubbing the timeline plays the music too
    world = bpy.data.worlds.new("world")
    world.color = (0.05, 0.05, 0.07)
    scene.world = world
    scene.render.engine = "BLENDER_WORKBENCH"
    scene.display.shading.light = "STUDIO"
    scene.display.shading.color_type = "MATERIAL"
    scene.display.shading.show_cavity = True
    scene.render.resolution_x, scene.render.resolution_y = 640, 400


CLIPS = main()

# =====================================================================================  validation
EXPORTED = ("b_", "loc_", "phys_")
NUM = r"(-?[\d.]+)"


def parse_contract(path):
    """Bone heads/parents and named cube boxes from the contract tables (sections 3 and 4)."""
    out = {"joash": {"bones": {}, "cubes": {}}, "dj_booth": {"bones": {}, "cubes": {}}}
    if not os.path.exists(path):
        return out
    cur = None
    txt = open(path, encoding="utf-8").read().replace("−", "-")
    cube_re = re.compile(r"(c_[a-z0-9_]+) x\[" + NUM + "," + NUM + r"\] y\[" + NUM + "," + NUM + r"\] z\[" + NUM + "," + NUM + r"\]")
    for line in txt.splitlines():
        if line.startswith("## 3."):
            cur = "joash"
        elif line.startswith("## 4."):
            cur = "dj_booth"
        elif line.startswith("## "):
            cur = None
        if cur is None or not line.startswith("|"):
            continue
        cells = [x.strip() for x in line.strip().strip("|").split("|")]
        if len(cells) < 3:
            continue
        m = re.fullmatch(r"((?:b|loc)_[a-z0-9_]+)", cells[0])
        h = re.fullmatch(NUM + "," + NUM + "," + NUM, cells[1].replace(" ", ""))
        if m and h:
            parent = cells[2] if re.fullmatch(r"[a-z0-9_]+", cells[2]) else None
            out[cur]["bones"][m.group(1)] = (tuple(float(v) for v in h.groups()), parent)
        for cm in cube_re.finditer(line):
            out[cur]["cubes"][cm.group(1)] = tuple(float(v) for v in cm.groups()[1:])
    return out


def mirror_contract(d):
    for n, (h, p) in list(d["bones"].items()):
        if re.search(r"_l(?=_|$)", n):
            d["bones"].setdefault(mir_name(n), (mir_v(h), mir_name(p) if p else p))
    for n, (x0, x1, y0, y1, z0, z1) in list(d["cubes"].items()):
        if re.search(r"_l(?=_|$)", n):
            d["cubes"].setdefault(mir_name(n), (-x1, -x0, y0, y1, z0, z1))
    return d


def cube_bounds(rig, ob):
    m = rig.matrix_world.inverted() @ ob.matrix_world
    pts = [m @ v.co for v in ob.data.vertices]
    lo = [min(p[i] for p in pts) for i in range(3)]
    hi = [max(p[i] for p in pts) for i in range(3)]
    ok = len(pts) == 8 and all(all(abs(p[i] - lo[i]) < 1e-3 or abs(p[i] - hi[i]) < 1e-3 for i in range(3)) for p in pts)
    return lo, hi, ok


def hand_objects(s):
    n = sname(s)
    names = [f"c_joash_palm_{n}"] + [f"c_joash_finger_{fg}_{n}_{i}" for fg in ("index", "middle", "ring") for i in (1, 2)] \
        + [f"c_joash_thumb_{n}_{i}" for i in (1, 2)]
    return [bpy.data.objects[x] for x in names]


def eval_contact(chk):
    s = chk["side"]
    n = sname(s)
    pb = RIG_J.pose.bones
    grip = chk["grip"]
    if grip in ("palm", "palmc", "palmk"):
        Mr = RIG_J.data.bones[f"b_hand_{n}"].matrix_local
        return pb[f"b_hand_{n}"].matrix @ Mr.inverted() @ (wrist0(s) + contact_local(s, grip))
    if grip == "pinch":
        return (pb[f"b_finger_index_{n}_2"].tail + pb[f"b_thumb_{n}_2"].tail) / 2
    if grip == "fist":
        pts = [ob.matrix_world @ v.co for ob in hand_objects(s) for v in ob.data.vertices]
        return min(pts, key=lambda w: w.z)
    raise KeyError(grip)


def rot_deg(a, b):
    d = a.to_quaternion().rotation_difference(b.to_quaternion())
    return math.degrees(2 * math.acos(min(1.0, abs(d.w))))


def _is_cloth(n):
    return n.startswith(("c_joash_robe", "c_joash_cape"))


def _is_hand(n):
    return any(t in n for t in ("finger", "thumb", "palm"))


def _side(n):
    if n.endswith("_l") or "_l_" in n:
        return "l"
    if n.endswith("_r") or "_r_" in n:
        return "r"
    return ""


CONTROLS = ("c_booth_fader", "c_booth_xfader", "c_booth_knob", "c_booth_platter", "c_booth_label")


def pen_category(a, b):
    if (_is_hand(a) and b.startswith(CONTROLS)) or (_is_hand(b) and a.startswith(CONTROLS)):
        return "hand-control"
    if {a, b} & {"c_joash_chest"} and ({a, b} & {"c_joash_upperarm_l", "c_joash_upperarm_r"}):
        return "chest-upperarm"
    if a.startswith("c_booth_platter") and b.startswith("c_booth_tonearm_base") or \
            b.startswith("c_booth_platter") and a.startswith("c_booth_tonearm_base"):
        return "booth-contract-layout"
    def k(n):
        if _is_cloth(n):
            return "cloth"
        if "orb" in n:
            return "orb"
        if _is_hand(n):
            return "hand"
        if n.startswith("c_booth"):
            return "booth"
        return "body"
    return "-".join(sorted((k(a), k(b))))


# what counts as a problem (px of overlap beyond the rest overlap of that pair); a hand touching its booth target
# (platter, label, fader, crossfader, knob, deck, top) is allowed a little more
PEN_LIMIT = {"body-body": 0.3, "body-hand": 0.4, "hand-hand": 0.4, "body-orb": 0.2, "hand-orb": 0.2, "orb-orb": 0.2,
             "body-cloth": 0.5, "cloth-hand": 0.6, "cloth-orb": 0.6, "body-booth": 0.25, "booth-cloth": 0.5,
             "booth-hand": 0.6, "booth-booth": 0.3, "hand-control": 1.0, "chest-upperarm": 0.6,
             "booth-contract-layout": 2.0}
FLOOR_LIMIT = -0.15


def _pairs():
    JC = sorted([o for o in COLL_J.objects if o.type == "MESH"], key=lambda o: o.name)
    BC = sorted([o for o in COLL_B.objects if o.type == "MESH"], key=lambda o: o.name)
    jb, bb = RIG_J.data.bones, RIG_B.data.bones

    def parent(bones, b):
        x = bones.get(b)
        return x.parent.name if x and x.parent else None

    selfp = []
    for i, a in enumerate(JC):
        for b in JC[i + 1:]:
            na, nb = a.name, b.name
            if _is_cloth(na) and _is_cloth(nb):
                continue
            ba, bbn = a.parent_bone, b.parent_bone
            if ba == bbn or parent(jb, ba) == bbn or parent(jb, bbn) == ba:
                continue
            if _is_hand(na) and _is_hand(nb) and _side(na) == _side(nb):
                continue
            selfp.append((a, b))
    boothp = [(a, b) for a in JC for b in BC]
    bbp = []
    for i, a in enumerate(BC):
        for b in BC[i + 1:]:
            ba, bbn = a.parent_bone, b.parent_bone
            if ba == bbn or parent(bb, ba) == bbn or parent(bb, bbn) == ba:
                continue
            bbp.append((a, b))
    return JC, BC, selfp, boothp, bbp


def wrist_anat(s):
    """(flex, deviation, pronation, elbow bend) of the evaluated arm, same convention as resolve_hands."""
    n = sname(s)
    pb = RIG_J.pose.bones
    S, E, W = pb[f"b_arm_upper_{n}"].head, pb[f"b_arm_lower_{n}"].head, pb[f"b_hand_{n}"].head
    f = (W - E).normalized()
    ant = S - E
    ant = ant - f * ant.dot(f)
    if ant.length < 0.15 * (S - E).length:
        ua = pb[f"b_arm_upper_{n}"].matrix.to_3x3().normalized() @ (
            RIG_J.data.bones[f"b_arm_upper_{n}"].matrix_local.to_3x3().transposed() @ Vector((0, -1, 0)))
        ua = ua - f * ua.dot(f)
        ant = ua if ant.length < 1e-6 else ant.normalized() * 0.3 + ua.normalized()
    ant.normalize()
    med = f.cross(ant) * s
    R = pb[f"b_hand_{n}"].matrix.to_3x3().normalized() @ RIG_J.data.bones[f"b_hand_{n}"].matrix_local.to_3x3().transposed()
    fl, dv, pr = anat_decompose(s, (f, ant, med), R)
    return fl, dv, pr, 180 - math.degrees((S - E).angle(W - E))


class _Composite:
    """Evaluate an additive clip on top of a base clip (NLA, Combine), removed again afterwards."""

    def __init__(self, base_act, add_act):
        self.base, self.add = base_act, add_act

    def __enter__(self):
        ad = RIG_J.animation_data
        ad.action = None
        self.t1 = ad.nla_tracks.new()
        self.t1.strips.new("base", 0, self.base)
        self.t2 = ad.nla_tracks.new()
        st = self.t2.strips.new("add", 0, self.add)
        st.blend_type = "COMBINE"
        return self

    def __exit__(self, *exc):
        ad = RIG_J.animation_data
        ad.nla_tracks.remove(self.t2)
        ad.nla_tracks.remove(self.t1)


def validate(clips):
    rep = {"contract": {}, "cubes_rest_problems": [], "clips": {}, "pole_calibration": POLE_CAL,
           "pinch_thumb_solution": FP["pinch"]["thumb"], "shard_min_chord_radii": round(SAT_MIN_CHORD, 3),
           "jam_phase_beats": JAM_PHASE_BEATS, "pen_limits_px": PEN_LIMIT}
    con = parse_contract(ARGS.contract)
    for mob, rig in (("joash", RIG_J), ("dj_booth", RIG_B)):
        d = mirror_contract(con[mob])
        missing, wrong = [], []
        for n, (h, p) in d["bones"].items():
            b = rig.data.bones.get(n)
            if b is None:
                missing.append(n)
                continue
            if (b.head_local - Vector(h)).length > 1e-4:
                wrong.append(f"{n}: head {tuple(round(v, 3) for v in b.head_local)} != {h}")
            if p and (b.parent is None or b.parent.name != p):
                wrong.append(f"{n}: parent {b.parent.name if b.parent else None} != {p}")
        rig.data.pose_position = "REST"
        bpy.context.view_layer.update()
        for n, box in d["cubes"].items():
            ob = bpy.data.objects.get(n)
            if ob is None:
                missing.append(n)
                continue
            lo, hi, ok = cube_bounds(rig, ob)
            got = (lo[0], hi[0], lo[1], hi[1], lo[2], hi[2])
            if any(abs(a - b) > 1e-3 for a, b in zip(got, box)):
                wrong.append(f"{n}: box {tuple(round(v, 3) for v in got)} != {box}")
        rig.data.pose_position = "POSE"
        bpy.context.view_layer.update()
        rep["contract"][mob] = {"bones_checked": len(d["bones"]), "cubes_checked": len(d["cubes"]),
                                "missing": missing, "wrong": wrong}
    expect_j = ["b_orb_sat_1", "b_orb_sat_2", "b_orb_sat_3"] + [n for n, *_ in J_BONES]
    rep["contract"]["joash"]["all_bones_built"] = not [n for n in expect_j if n not in RIG_J.data.bones]
    expect_b = ["b_fader_1", "b_fader_2", "b_xfader", "b_knob_1", "b_knob_2", "b_knob_3", "b_knob_4", "b_deck_r",
                "b_platter_r", "b_tonearm_r_base", "b_tonearm_r"]
    rep["contract"]["dj_booth"]["structural_missing"] = [n for n in expect_b if n not in RIG_B.data.bones]
    rep["contract"]["joash"]["cube_objects"] = sum(1 for o in COLL_J.objects if o.type == "MESH")
    rep["contract"]["dj_booth"]["cube_objects"] = sum(1 for o in COLL_B.objects if o.type == "MESH")
    rep["nla_tracks"] = {r.name: len(r.animation_data.nla_tracks) if r.animation_data else 0 for r in (RIG_J, RIG_B)}
    # cubes: axis-aligned boxes at rest, bone-parented, no rotation
    for rig in (RIG_J, RIG_B):
        rig.data.pose_position = "REST"
    bpy.context.view_layer.update()
    for rig, coll in ((RIG_J, COLL_J), (RIG_B, COLL_B)):
        for ob in coll.objects:
            if ob.type != "MESH":
                continue
            lo, hi, ok = cube_bounds(rig, ob)
            m3 = (rig.matrix_world.inverted() @ ob.matrix_world).to_3x3()
            off = max(abs(m3[i][j]) for i in range(3) for j in range(3) if i != j)
            if not ok or off > 1e-5 or ob.parent_type != "BONE" or ob.parent is not rig or not ob.name.startswith("c_"):
                rep["cubes_rest_problems"].append(f"{ob.name}: aligned={ok} offdiag={off:.2e} parent={ob.parent_type}")
    # exact OBB pair sets + rest overlaps (some pairs touch by design at rest, e.g. chest/upper arm 0.25)
    JC, BC, selfp, boothp, bbp = _pairs()
    jidx = {o.name: i for i, o in enumerate(JC)}
    bidx = {o.name: i for i, o in enumerate(BC)}
    SI = np.array([jidx[a.name] for a, b in selfp])
    SJ = np.array([jidx[b.name] for a, b in selfp])
    BI = np.array([jidx[a.name] for a, b in boothp])
    BJ = np.array([bidx[b.name] for a, b in boothp])
    QI = np.array([bidx[a.name] for a, b in bbp])
    QJ = np.array([bidx[b.name] for a, b in bbp])
    CJ0, EJ0 = np_boxes(JC)
    CB0, EB0 = np_boxes(BC)
    rest_self = np.maximum(sat_depth(CJ0[SI], EJ0[SI], CJ0[SJ], EJ0[SJ]), 0.0)
    rest_bb = np.maximum(sat_depth(CB0[QI], EB0[QI], CB0[QJ], EB0[QJ]), 0.0)
    rep["booth_rest_overlaps"] = sorted([(round(float(d), 3), a.name, b.name) for (a, b), d in zip(bbp, rest_bb) if d > 0.05],
                                        reverse=True)
    for rig in (RIG_J, RIG_B):
        rig.data.pose_position = "POSE"
    bpy.context.view_layer.update()
    self_names = [(a.name, b.name) for a, b in selfp]
    booth_names = [(a.name, b.name) for a, b in boothp]
    bb_names = [(a.name, b.name) for a, b in bbp]

    booth_acts = {c.name: c for c in clips if c.rig is RIG_B}
    exported = [b.name for b in RIG_J.data.bones if b.name.startswith(EXPORTED)]
    bexported = [b.name for b in RIG_B.data.bones if b.name.startswith(EXPORTED)]
    acts = {c.name: c for c in clips}
    for c in clips:
        if c.rig is not RIG_J:
            continue
        clip = c.name[len("joash_"):]
        bc = booth_acts.get("dj_booth_" + clip)
        additive = clip == "hurt"
        comp = _Composite(acts["joash_idle_loop"].act, c.act) if additive else None
        if comp:
            comp.__enter__()
        else:
            assign(RIG_J, c.act)
        assign(RIG_B, bc.act if bc else booth_acts["dj_booth_jam_loop"].act)
        use_booth = clip in ("jam_loop", "interrupted")
        booth_end = M_BLINK if clip == "interrupted" else 10 ** 6
        reach, bends_bad, knee_bad = 0.0, [], []
        reach_at, reach_bad = None, 0
        first = last = bfirst = blast = None
        by_frame = {}
        for ch in c.checks:
            by_frame.setdefault(ch["frame"], []).append(ch)
        contacts = []
        hits = {}
        floor = {}
        wrist = {"l": [], "r": []}
        frames = range(0, c.L + 1)
        for f in frames:
            eval_frame(f)
            pb = RIG_J.pose.bones
            if f == 0:
                first = {n: pb[n].matrix.copy() for n in exported}
                bfirst = {n: RIG_B.pose.bones[n].matrix.copy() for n in bexported}
            if f == c.L:
                last = {n: pb[n].matrix.copy() for n in exported}
                blast = {n: RIG_B.pose.bones[n].matrix.copy() for n in bexported}
                if c.loop:
                    continue                      # frame L == frame 0 of the next cycle
            hips_fwd = pb["b_hips"].matrix.to_3x3().normalized() @ RIG_J.data.bones["b_hips"].matrix_local.to_3x3().transposed() @ Vector((0, -1, 0))
            for s in (1, -1):
                n = sname(s)
                if pb[f"b_arm_lower_{n}"].constraints["IK"].influence > 0.5:
                    err = (pb[f"b_arm_lower_{n}"].tail - pb[f"ik_hand_{n}"].head).length
                    if err > reach:
                        reach, reach_at = err, (f, n)
                    if err > 0.05:
                        reach_bad += 1
                for up, lo, kind in ((f"b_arm_upper_{n}", f"b_arm_lower_{n}", "elbow"), (f"b_leg_upper_{n}", f"b_leg_lower_{n}", "knee")):
                    a, e, t = pb[up].head, pb[lo].head, pb[lo].tail
                    ax = (t - a).normalized()
                    d = e - a
                    d -= ax * d.dot(ax)
                    if (kind == "elbow" and d.length > 0.6 and d.normalized().dot(Vector((s * 0.6, 0.8, 0))) < -0.25
                            and pb[f"b_arm_lower_{n}"].constraints["IK"].influence > 0.5):
                        bends_bad.append((f, n, tuple(round(v, 2) for v in d.normalized())))
                    if kind == "knee" and d.length > 0.4 and d.normalized().dot(hips_fwd) < 0.0:
                        knee_bad.append((f, n, round(d.length, 2)))
                if not additive:
                    wrist[n].append((f,) + tuple(round(v, 1) for v in wrist_anat(s)))
            for ch in by_frame.get(f, []):
                got = eval_contact(ch)
                tg = Vector(ch["target"])
                extra = {}
                if ch.get("surface") is not None:
                    low = min((ob.matrix_world @ v.co).z for ob in hand_objects(ch["side"]) for v in ob.data.vertices)
                    herr = (got.to_2d() - tg.to_2d()).length
                    verr = low - ch["surface"]
                    err = max(herr, max(0.0, -verr), max(0.0, verr - 0.45))
                    extra = dict(horizontal_err_px=round(herr, 3), lowest_point_above_surface_px=round(verr, 3))
                else:
                    err = (got - tg).length
                cerr = (got - Vector(ch["contract"])).length if ch["contract"] else None
                contacts.append(dict(label=ch["label"], frame=f, err_px=round(err, 3), ok=err <= 1.0,
                                     dist_to_contract_target_px=None if cerr is None else round(cerr, 3),
                                     got=tuple(round(v, 2) for v in got), **extra))
            CJ, EJ = np_boxes(JC)
            d = sat_depth(CJ[SI], EJ[SI], CJ[SJ], EJ[SJ]) - rest_self
            for k in np.nonzero(d > 0.12)[0]:
                key = self_names[k]
                h = hits.setdefault(key, [0.0, f, []])
                h[2].append(f)
                if d[k] > h[0]:
                    h[0], h[1] = float(d[k]), f
            if use_booth and f < booth_end:
                CB, EB = np_boxes(BC)
                d = sat_depth(CJ[BI], EJ[BI], CB[BJ], EB[BJ])
                for k in np.nonzero(d > 0.12)[0]:
                    key = booth_names[k]
                    h = hits.setdefault(key, [0.0, f, []])
                    h[2].append(f)
                    if d[k] > h[0]:
                        h[0], h[1] = float(d[k]), f
                d = sat_depth(CB[QI], EB[QI], CB[QJ], EB[QJ]) - rest_bb
                for k in np.nonzero(d > 0.12)[0]:
                    key = bb_names[k]
                    h = hits.setdefault(key, [0.0, f, []])
                    h[2].append(f)
                    if d[k] > h[0]:
                        h[0], h[1] = float(d[k]), f
            mz = CJ[:, 2] - np.abs(EJ[:, :, 2]).sum(axis=1)
            for i in np.nonzero(mz < FLOOR_LIMIT)[0]:
                h = floor.setdefault(JC[i].name, [0.0, f, 0])
                h[2] += 1
                if mz[i] < h[0]:
                    h[0], h[1] = float(mz[i]), f
        if comp:
            comp.__exit__()
        seam = None
        if c.loop:
            seam = {"max_pos_px": round(max((first[n].translation - last[n].translation).length for n in exported), 5),
                    "max_rot_deg": round(max(rot_deg(first[n], last[n]) for n in exported), 4)}
            if bc and bc.loop:
                seam["booth_max_pos_px"] = round(max((bfirst[n].translation - blast[n].translation).length for n in bexported), 5)
                seam["booth_max_rot_deg"] = round(max(rot_deg(bfirst[n], blast[n]) for n in bexported), 4)

        def ranges(fr):
            out, s0, p = [], fr[0], fr[0]
            for x in fr[1:]:
                if x != p + 1:
                    out.append((s0, p))
                    s0 = x
                p = x
            out.append((s0, p))
            return out[:8]

        pens = []
        for (a, b), (mx, at, fr) in hits.items():
            cat = pen_category(a, b)
            pens.append(dict(cat=cat, a=a, b=b, max=round(mx, 2), at=at, n=len(fr), ranges=ranges(fr),
                             over=mx > PEN_LIMIT.get(cat, 0.3)))
        pens.sort(key=lambda h: -h["max"])
        bycat = {}
        for h in pens:
            bycat.setdefault(h["cat"], h)
        wr = {}
        for n in ("l", "r"):
            rows = wrist[n]
            if not rows:
                continue
            bad = [r for r in rows if not (WRIST_LIM["flex"][0] - 5 <= r[1] <= WRIST_LIM["flex"][1] + 5
                                           and WRIST_LIM["dev"][0] - 5 <= r[2] <= WRIST_LIM["dev"][1] + 5
                                           and WRIST_LIM["pron"][0] - 5 <= r[3] <= WRIST_LIM["pron"][1] + 5)]
            wr[n] = dict(frames_out_of_range=len(bad),
                         flex=(min(r[1] for r in rows), max(r[1] for r in rows)),
                         dev=(min(r[2] for r in rows), max(r[2] for r in rows)),
                         pron=(min(r[3] for r in rows), max(r[3] for r in rows)),
                         elbow=(min(r[4] for r in rows), max(r[4] for r in rows)),
                         examples=[r for r in bad][:6], ranges=ranges([r[0] for r in bad]) if bad else [])
        rep["clips"][clip] = {
            "length_frames": c.L, "length_s": round(c.L / FPS, 4), "loop": c.loop, "additive": additive,
            "markers": [(m.name, m.frame, round(m.frame / FPS, 3)) for m in sorted(c.act.pose_markers, key=lambda m: m.frame)],
            "ik_max_wrist_error_px": round(reach, 4), "ik_worst_at": reach_at, "ik_frames_over_0.05px": reach_bad,
            "elbow_bad_frames": len(bends_bad), "elbow_bad_examples": bends_bad[:6],
            "knee_bad_frames": len(knee_bad), "knee_bad_examples": knee_bad[:6],
            "contacts": contacts, "loop_seam": seam,
            "penetration_over_limit": [h for h in pens if h["over"]],
            "penetration_worst_by_category": bycat,
            "penetration_pairs_total": len(pens),
            "floor": sorted([dict(cube=k, min=round(v[0], 2), at=v[1], n=v[2]) for k, v in floor.items()], key=lambda x: x["min"]),
            "wrist": wr,
            "orb_avoid": getattr(c, "orb_log", []),
        }
    # transitions: interrupted starts on the contract-b0 jam pose; combat clips start/end on idle f0
    def pose_at(act, f):
        assign(RIG_J, act)
        eval_frame(f)
        return {n: RIG_J.pose.bones[n].matrix.copy() for n in exported if not n.startswith("phys_")}

    def diff(a, b):
        worst = max(a, key=lambda n: (a[n].translation - b[n].translation).length + 0.05 * rot_deg(a[n], b[n]))
        return {"max_pos_px": round(max((a[n].translation - b[n].translation).length for n in a), 3),
                "max_rot_deg": round(max(rot_deg(a[n], b[n]) for n in a), 2), "worst_bone": worst}

    trans = {}
    if "joash_interrupted" in acts:
        trans[f"jam_f{F(JAM_PHASE_BEATS)} (contract b0) -> interrupted_f0"] = diff(
            pose_at(acts["joash_jam_loop"].act, F(JAM_PHASE_BEATS)), pose_at(acts["joash_interrupted"].act, 0))
        trans["interrupted_end -> idle_f0"] = diff(pose_at(acts["joash_interrupted"].act, acts["joash_interrupted"].L),
                                                   pose_at(acts["joash_idle_loop"].act, 0))
    idle0 = pose_at(acts["joash_idle_loop"].act, 0)
    for n in ("attack_melee", "attack_ranged", "teleport", "summon_illusions"):
        c = acts.get("joash_" + n)
        if c:
            trans[f"idle_f0 -> {n}_f0"] = diff(idle0, pose_at(c.act, 0))
            trans[f"{n}_end -> idle_f0"] = diff(pose_at(c.act, c.L), idle0)
    if "joash_hurt" in acts:
        assign(RIG_J, acts["joash_hurt"].act)
        out = {}
        for f in (0, acts["joash_hurt"].L):
            eval_frame(f)
            mloc = max(RIG_J.pose.bones[n].matrix_basis.translation.length for n in exported)
            mrot = 0.0
            for n in exported:
                ang = math.degrees(RIG_J.pose.bones[n].matrix_basis.to_quaternion().angle)
                mrot = max(mrot, min(ang, 360 - ang))
            out[f"f{f}"] = {"max_loc_px": round(mloc, 4), "max_rot_deg": round(mrot, 3)}
        trans["hurt (additive delta) at start/end"] = out
    if "joash_death" in acts:
        trans["idle_f0 -> death_f0"] = diff(idle0, pose_at(acts["joash_death"].act, 0))
    if "joash_walk_loop" in acts:
        trans["idle_f0 -> walk_f0 (informative)"] = diff(idle0, pose_at(acts["joash_walk_loop"].act, 0))
    rep["transitions"] = trans
    assign(RIG_J, bpy.data.actions["joash_jam_loop"])
    assign(RIG_B, bpy.data.actions["dj_booth_jam_loop"])
    eval_frame(0)
    return rep


REPORT = None
if not ARGS.no_validate:
    REPORT = validate(CLIPS)
    txt = json.dumps(REPORT, indent=1, default=str)
    if ARGS.report:
        os.makedirs(os.path.dirname(os.path.abspath(ARGS.report)), exist_ok=True)
        with open(ARGS.report, "w", encoding="utf-8") as fh:
            fh.write(txt)
    print("VALIDATION written" if ARGS.report else txt)

if not ARGS.no_save:
    os.makedirs(os.path.dirname(ARGS.out), exist_ok=True)
    for c in CLIPS:
        c.act.use_fake_user = True
    if scene.sequence_editor:
        for st in scene.sequence_editor.sequences_all:
            if st.type == "SOUND":
                try:
                    st.sound.filepath = bpy.path.relpath(bpy.path.abspath(st.sound.filepath), start=os.path.dirname(ARGS.out))
                except ValueError as e:      # different drive: keep absolute
                    print("sound path kept absolute:", e)
    bpy.ops.wm.save_as_mainfile(filepath=ARGS.out, compress=True)
    print("SAVED", ARGS.out)
