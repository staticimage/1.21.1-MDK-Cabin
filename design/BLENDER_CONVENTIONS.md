# Blender → Minecraft rig & animation conventions

Applies to every Cabin Mod mob (Jehoash, Cabin, prisoners). Blue Guy is untouched and does not use this.
Target: vanilla NeoForge `HierarchicalModel` + `AnimationDefinition` — no GeckoLib.

## The one hard rule
**Meshes are rigid boxes owned by exactly one bone.** No weight painting, no shape keys, no
modifiers that deform. Everything else in Blender is allowed, because the exporter bakes it.

## Scene
- Blender 4.2, a dedicated Cabin instance (never the STALKER session).
- One collection per mob: `MC_<mobid>` (e.g. `MC_joash`).
- Units: **1 Blender unit = 1 Minecraft pixel** (1/16 block). Scene unit scale is irrelevant; the
  exporter reads raw values.
- Axes: Blender −Y = mob forward, +Z = up. The exporter converts to Minecraft model space
  (+Y down, origin at the feet + 24 px).
- Frame rate **24 fps**. Exported keyframe times are `frame / 24` seconds.

## Bones
- One **Armature** per mob, `rig_<mobid>`, origin at the feet, inside the mob's collection.
  (An armature, not Empties: one Action then drives the whole body, and IK / constraints /
  action markers all work.)
- Each bone is named `b_<part>`; its **head is the pivot**, e.g. `b_head`, `b_arm_l`,
  `b_shroud_back_2`. Bone roll/direction does not matter — parts take the armature's axes at rest.
- The root is `b_root` at the feet. `_l` / `_r` = the mob's own left / right.
- Helper bones that should not become model parts (IK targets, poles, mechanism bones) are
  named `ik_*` / `ctl_*` and are skipped by the exporter (their effect is baked).
- Locators (bones with no cubes, exported as empty parts for code to use):
  - `loc_hand_r` — the held melee item is rendered here (swappable item, not modeled).
  - `loc_orb` — the floating disco ball (also a held item, drawn by a custom layer).
  - `loc_*` others for particles / laser origin as needed.

## Runtime cloth (physics chains and mesh references)
Jehoash's current garment uses connected surface grids in `entity/client/physics/ClothSheet.java`; its hood, veil, robe, cape, mantle and sleeves are simulated and rendered directly in Java. The `MC_joash_runtime_reference` collection in `design/blender/joash_v2.blend` shows their rest shape, along with the fuller native cuboid phase-two body. Garment UVs match `design/textures/jehoash_brg_cloak_uv.json`; body cubes and exact native face UVs come from `design/textures/jehoash_phase_two_model.json`, generated alongside the Java geometry by `tools/textures/build_jehoash_phase_two.py`. Its painted wing outfit uses Screenshot 2025-04-28 191821.png; the BRG artwork remains on the cloak. Run `tools/blender/sync_jehoash_reference.py` after rebuilding the body or cloak UVs. `ref_*` objects are inspection guides and are not exported.

The older `phys_*` chain convention below still applies to other legacy rigs:
Some cloth is simulated live in game instead of (or on top of) baked keys — robe strips, capes,
sleeves, cables. Name those bones `phys_<chain>_<n>`, e.g. `phys_cape_1`, `phys_cape_2`, `phys_cape_3`:
- each one parented to the previous; the first parented to the bone it hangs from (torso, arm…);
- the chain hangs **down** at rest, segment length = distance from one pivot to the next;
- one or more rigid cubes per segment, like any other bone.
In game `entity/client/physics/ClothChain` simulates each chain (Verlet: gravity, damping, wind from
the mob's movement, inertia from the animated anchor, body collision) and writes the result back as
rotations. Anything you key on `phys_` bones in Blender becomes the pose the cloth is pulled towards
(`stiffness`), so you can still direct big swishes by hand and let the physics add follow-through.
More segments = smoother cloth; 3–6 per strip is a good range. Bake previews in Blender with a
cloth/rigid-body sim if you like — in game the live sim takes over those bones.

## Cubes
- Box meshes named `c_<part>[_n]`, **bone-parented** (Parent Type: Bone) to exactly one bone —
  no Armature modifier, no vertex groups. At rest pose they must be **axis-aligned to the world
  axes** (pose bones to angle them, never rotate the cube in rest).
- Sizes in whole pixels; `inflate` stored as a custom property `inflate` (float, may be 0.25 / 0.5).
- UVs: box-UV, auto-packed by the exporter into a 64×64 or 128×128 atlas; custom property
  `uv` = `[u, v]` pins a cube's offset once you have painted it, so re-exports never move paint.
- Optional custom property `mirror` (bool) for mirrored UVs.

## Animations
- One **Action per clip**, named `<mobid>_<clip>[_loop]`, e.g. `joash_idle_loop`, `joash_attack_melee`.
  The `_loop` suffix sets `looping()` in game.
- Anything may drive the bones: keyframes, IK, constraints, drivers, rigid-body / spring chains.
  On export every bone is **sampled every frame** (24 fps) and written as LINEAR keyframes, so
  custom easing, overshoot and simulation are preserved exactly. Static channels are dropped.
- Allowed per bone: location, rotation, **scale** (squash & stretch; scale 0 = hidden part).
- Root motion does not move the hitbox: lunges/teleports are done in code, triggered by markers.

## Markers → gameplay events
**Action (pose) markers** — Action Editor → Marker menu with "Show Pose Markers" on, so they
travel with the clip — are exported as tick events:
- `hit` — melee damage frame
- `fire` — laser/projectile spawn frame
- `sfx:<sound_id>` — play a sound
- `fx:<particle>` — spawn particles at a locator (`fx:<particle>@loc_orb`)
- `move:<name>` — code-driven motion (lunge, blink)
- `end` — clip may be interrupted from here

## Layering in game
- Base locomotion (idle/walk) + additive loops (breathing, shroud sway) + one action clip on top.
- Code adds reactive motion after clips: head look-at, shroud trailing from velocity.
- Glow: a second `_emissive.png` texture drawn full-bright (eyes, runes, disco lights).

## UV lock (paint never moves)
Every export records each cube's texture patch in `textures/entity/<mobid>/<mobid>_uv_lock.json`
(commit it!) and in the cube's `uv` property. Rules the exporter enforces:
- Locked patches never move. New cubes go into free space only.
- Deleted / renamed cubes keep their space **reserved**. Release on purpose with
  `--set release=c_old_name,c_other`.
- A resized cube stays put if it still fits; if it no longer fits, **only that cube** moves and
  the export lists it under `MOVED_NEEDS_REPAINT`.
- The atlas never shrinks or rescales. If it must grow (64→128…), it is extended right/down and
  every painted `.png` in the folder is padded to match (original kept as `.png.bak<oldsize>`),
  so existing pixels keep their exact coordinates.
- Renaming a cube = a new cube (and a reservation for the old name). Don't rename after painting.

## Textures
Painted by the user. Paint `<mobid>.png` (and optional `<mobid>_emissive.png`) — never the template. The exporter writes `<mobid>_uv_template.png` (each cube a distinct hue,
each face a different shade: top lightest → bottom darkest) plus `<mobid>_uv_legend.txt`
(cube name → atlas rectangle per face) for painting over.

## Generated files (do not hand-edit)
Run: `python tools/blender/bl.py tools/blender/mc_export.py --set mob=<mobid> --set cls=<Class> --set repo=<repo path>`
- `entity/client/generated/<Class>ModelBase.java` — layer definition + a field per part. The
  hand-written `<Class>Model` extends it and does `setupAnim` (layering, look-at, reactive motion).
- `assets/cabinmod/animations/<mobid>.json` — baked clips + marker events. Data, not Java: dense
  baked keys would blow Java's 64 KB method limit. Loaded by `entity/anim/BakedClips` (common:
  lengths + events for server timing) and `entity/client/anim/BakedAnimations` (client:
  `AnimationDefinition`). Clip key = action name minus the `<mobid>_` prefix.
- `textures/entity/<mobid>/<mobid>_uv_template.png` + `_uv_legend.txt`
