# Jehoash v2 — the DJ, the booth, the interruption (design contract)

Everything here is the contract between the Blender rig build, the audio build and the Java code.
Change a name or a number here first, then everywhere else.

## 1. The flow in game
1. **Dormant (DJing).** Jehoash stands behind his booth, which is *part of his model*. He is snapped to a
   block centre facing a cardinal direction F; the booth occupies the 3 blocks one block in front of him
   (centre block = his block + F). The phonk plays **from the booth** (large range). His jam animation and
   the booth's moving parts are locked to the beat (114 BPM). His robe strands and cape float and
   pulse to the beat (runtime cloth physics + beat-synced levitation). He can't be hurt.
2. **Interrupted.** A player **right-clicks** him or the booth (hits just bounce off while he plays —
   he's invulnerable and keeps DJing). Needle-scratch + the music
   tape-stops (pitch drags down and dies). He freezes, turns, leans over the booth and snarls
   **"WELCOME"** (processed Porkrind line), rears up, **slams** the booth — the booth splits off and becomes a
   real block exactly where it was — then **roars** (Life of the Party lands, boss bar, cloth flares), and
   blinks into the arena. Clip `interrupted`.
3. **Fight.** As before (melee, dodgeable laser — now with a real beam effect and new sounds — blink +
   decoys, curse), new rig and clips.
4. **Death.** Death clip; the orbital strike opens the sky, planets crash — now **with its own synced
   soundtrack**. The booth block stays; any player can right-click it to start/stop the phonk.

## 2. Units and axes
- Blender: 1 unit = 1 px = 1/16 block. Character faces **−Y**, Z up, feet at z=0, mob's **left = +X**.
- Minecraft model space (exporter converts): +X = mob's left, +Y down, −Z = forward, root at y=24.
- **Scene fps = 38** → exactly **20 frames per beat** at 114 BPM (beat = 0.52632 s). All clips use it.
- Bone prefixes (see BLENDER_CONVENTIONS.md): `b_` part, `loc_` locator part, `phys_<chain>_<n>` runtime
  cloth part (exported, simulated in game), `ik_`/`ctl_` helpers (not exported). Cubes `c_*`, rigid,
  bone-parented, axis-aligned at rest.

## 3. Jehoash rig — `rig_joash` (mob id `joash`, class `Jehoash`)
Height 58 px (~3.6 blocks; hitbox 3.5). Positions are bone heads (pivots); cubes are [min,max] per axis.
Mirror every `_l` to `_r` by negating x.

| bone | head (x,y,z) | parent | cubes |
|---|---|---|---|
| b_root | 0,0,0 | — | — |
| b_hips | 0,0,26 | b_root | c_joash_pelvis x[-4.5,4.5] y[-2.5,2.5] z[23,28] |
| b_leg_upper_l | 3,0,24 | b_hips | c_joash_thigh_l x[1.5,4.5] y[-1.5,1.5] z[12.5,24.5] |
| b_leg_lower_l | 3,0,12.5 | b_leg_upper_l | c_joash_shin_l x[1.75,4.25] y[-1.25,1.25] z[2,12.5] |
| b_foot_l | 3,-0.5,2 | b_leg_lower_l | c_joash_foot_l x[1.5,4.5] y[-4,1.5] z[0,2] |
| b_spine | 0,0,28 | b_hips | c_joash_belly x[-4,4] y[-2.5,2.5] z[28,35] |
| b_chest | 0,0,35 | b_spine | c_joash_chest x[-5.5,5.5] y[-3,3] z[35,44] |
| b_neck | 0,0,44 | b_chest | c_joash_neck x[-1.5,1.5] y[-1.5,1.5] z[44,47.5] |
| b_head | 0,0,47.5 | b_neck | c_joash_head x[-4,4] y[-4,4] z[47.5,55.5]; c_joash_hood x[-5,5] y[-5,5] z[46.5,57.5] (open front); c_joash_hp_band x[-5.5,5.5] y[-0.75,0.75] z[57.5,58.5]; c_joash_hp_cup_l x[5,6.5] y[-1.75,1.75] z[50,54.5]; c_joash_hp_cup_r mirrored |
| b_shoulder_l | 2,0,42.5 | b_chest | c_joash_shoulder_l x[4.5,8] y[-2,2] z[41.5,44.5] |
| b_arm_upper_l | 6.5,0,42.5 | b_shoulder_l | c_joash_upperarm_l x[5.25,7.75] y[-1.25,1.25] z[30.5,42.5] |
| b_arm_lower_l | 6.5,0,30.5 | b_arm_upper_l | c_joash_forearm_l x[5.4,7.6] y[-1.1,1.1] z[20.5,30.5] |
| b_hand_l | 6.5,0,20.5 | b_arm_lower_l | c_joash_palm_l x[5.25,7.75] y[-0.65,0.65] z[17.5,20.5] |
| b_finger_index_l_1 | 7.35,0,17.5 | b_hand_l | x[7.0,7.7] y[-0.35,0.35] z[15.8,17.5] |
| b_finger_index_l_2 | 7.35,0,15.8 | b_finger_index_l_1 | x[7.0,7.7] y[-0.35,0.35] z[14.5,15.8] |
| b_finger_middle_l_1 | 6.5,0,17.5 | b_hand_l | x[6.15,6.85] y[-0.35,0.35] z[15.6,17.5] |
| b_finger_middle_l_2 | 6.5,0,15.6 | b_finger_middle_l_1 | x[6.15,6.85] y[-0.35,0.35] z[14.2,15.6] |
| b_finger_ring_l_1 | 5.65,0,17.5 | b_hand_l | x[5.3,6.0] y[-0.35,0.35] z[15.9,17.5] |
| b_finger_ring_l_2 | 5.65,0,15.9 | b_finger_ring_l_1 | x[5.3,6.0] y[-0.35,0.35] z[14.8,15.9] |
| b_thumb_l_1 | 6.0,-0.65,19.0 | b_hand_l | x[5.65,6.35] y[-1.35,-0.65] z[17.6,19.0] |
| b_thumb_l_2 | 6.0,-1.0,17.6 | b_thumb_l_1 | x[5.65,6.35] y[-1.35,-0.65] z[16.6,17.6] |
| b_orb_pivot | -10,2,52 | b_root | — |
| b_orb | -10,2,52 | b_orb_pivot | c_joash_orb x[-13,-7] y[-1,5] z[49,55] |
| b_orb_sat_1/2/3 | (-3.5,2,52) / (-10,2,58.5) / (-16.5,2,52) | b_orb_pivot | 1.2³ cube centred on the head |
| loc_orb | -10,2,52 | b_orb | — (laser origin) |
| loc_mouth | 0,-4.2,50 | b_head | — |
| loc_hand_r_item | -6.5,0,18 | b_hand_r | — (held item, later) |

**Cloth chains** (all hang straight down at rest, uniform segment length, each segment parented to the previous):
- Cape: `phys_cape_l_1..6` first head (2.5,3.4,43.5), parent b_chest, 6 × 5 px; cubes x[0.25,4.75] y[3.1,3.7]; `phys_cape_r_*` mirrored.
- Robe (parent b_hips, first head z=24.5, 4 × 5.5 px, chain names `robe_<id>`):
  front `robe_f1..f3` at y=-3.0, x=-3.2/0/3.2, cubes 3 wide (x) × 0.8 (y);
  back `robe_b1..b3` at y=+3.0 same x; left `robe_l1..l3` at x=+5.0, y=-2/0/2, cubes 0.8 (x) × 2.4 (y);
  right `robe_r1..r3` at x=-5.0; corners `robe_c1..c4` at (±4.6, ±2.8), cubes 1.2 × 1.2.
  Bone names: `phys_robe_f1_1..4` etc.
- (No sleeve ribbons and no back tendrils — user decision. Eldritch extra = the 3 mirror shards
  `b_orb_sat_1..3` orbiting the disco ball.)

**IK helpers** (not exported): `ik_hand_l/r` (hand targets), `ik_pole_l/r` (elbow poles, behind/outside),
`ik_foot_l/r` + `ik_knee_l/r` (feet planted while hips dip). Bake everything; the game only sees results.

## 4. Booth rig — `rig_dj_booth` (mob id `dj_booth`, class `DjBooth`)
Origin = centre of the booth's CENTER block, bottom. Front (crowd) faces −Y like Jehoash. In the .blend the
booth object sits at (0,−16,0) relative to Jehoash (one block in front). Footprint 3 blocks wide.

| bone | head | parent | cubes |
|---|---|---|---|
| b_root | 0,0,0 | — | — |
| b_cabinet | 0,0,0 | b_root | c_booth_cabinet x[-23,23] y[-6,8] z[0,25]; c_booth_top x[-23.5,23.5] y[-6.5,8.5] z[25,26] |
| b_deck_l | 14,1.5,26 | b_cabinet | c_booth_deck_l x[6.5,21.5] y[-4.5,7.5] z[26,28] |
| b_platter_l | 14,1.5,28 | b_deck_l | 3 stacked plates (round-ish): x[8.5,19.5] y[-1,4]; x[9.5,18.5] y[-3,6]; x[11.5,16.5] y[-4,7]; all z[28,28.8]; label x[13,15] y[0.5,2.5] z[28.8,29] |
| b_tonearm_l_base | 19.5,6,28 | b_deck_l | x[18.5,20.5] y[5,7] z[28,30.5] |
| b_tonearm_l | 19.5,6,30 | b_tonearm_l_base | arm x[15,19.5] y[5.6,6.4] z[29.8,30.4]; headshell x[14.2,15.4] y[5.2,6.8] z[29.6,30.4] |
| (mirror the deck/platter/tonearm to `_r` at x=−14) | | | |
| b_mixer | 0,1,26 | b_cabinet | c_booth_mixer x[-6,6] y[-5,7] z[26,29] |
| b_fader_1 / b_fader_2 | (−2.5,2,29) / (2.5,2,29) | b_mixer | caps 1.2×1.2×1.3 centred, z[29,30.3] |
| b_xfader | 0,−3,29 | b_mixer | x[-0.7,0.7] y[-3.6,-2.4] z[29,30.2] |
| b_knob_1..4 | (−3.5/−1.2/1.2/3.5, 5.5, 29) | b_mixer | 1×1×1.2 centred, z[29,30.2] |
| b_laptop | 0,7,29.5 | b_cabinet | base x[-4,4] y[4.5,8.5] z[29,29.6]; screen x[-4,4] y[8.2,8.7] z[29.6,35.2] |
| b_mic_stand | −21.5,6.5,26 | b_cabinet | pole x[-22,-21] y[6,7] z[26,48] |
| b_mic_boom | −21.5,6.5,48 | b_mic_stand | x[-21.5,-4] y[6.1,6.9] z[47.6,48.4] |
| b_mic | −4,6.5,48 | b_mic_boom | x[-5,-3] y[5.5,7.5] z[44.5,48] |

Hand targets in Jehoash space (booth at y=−16): platter L (14,−14.5,28.8), platter R (−14,−14.5,28.8),
fader_1 (−2.5,−14,30.3), fader_2 (2.5,−14,30.3), xfader (0,−19,30.2), knob_3 (1.2,−10.5,30.2),
left headphone cup (7.2,0,52.3), mic (−4,−9.5,46), booth front edge for gripping (±10,−21.5,26).

## 5. Clips (fps 38, times in seconds; markers are action pose markers)
`_loop` suffix = looping. Booth clips live on `rig_dj_booth`.

| clip | length | markers | notes |
|---|---|---|---|
| jam_loop | 32 beats = 640 f = 16.842 | — | dormant DJ set, driven by the music's beat clock in game |
| interrupted | 4.6 | voice 1.2, slam 2.5, booth 2.65, roar 2.9, move:blink 4.3 | the upset activation |
| idle_loop | 3.0 | — | combat idle |
| walk_loop | 1.2 | — | |
| attack_melee | 1.0 | hit 0.5 | |
| attack_ranged | 1.6 | fire 1.0 | |
| teleport | 1.0 | move:blink 0.5 | |
| summon_illusions | 1.2 | summon 0.6 | |
| hurt | 0.5 | — | |
| death | 4.0 | — | |
| booth: jam_loop | 640 f | — | faders/xfader/knob/platter wiggles matching his hands (platter spin itself is in code) |
| booth: interrupted | 2.65 | — | scratch wiggle 0–0.3, slam shake at 2.5 |

### jam_loop choreography (b = beat 0..32; bar = 4 beats)
Global groove every beat: knees dip −1.2 px on the beat and rebound by the off-beat (IK feet planted);
hips sway ±0.8 x over 2 beats with ±4° yaw; chest leans 12° forward, +3° pulse on each beat; head nods
+14° down on the beat / −4° on the off-beat with the neck lagging 2 frames; head roll ±5° over 2 beats;
shoulders lift 0.5 on off-beats; orb turns once per 4 beats, bobs ±1 (peak on the beat), satellites orbit.
- **b0–7 ride:** left palm flat on platter L, fingers spread, nudging ±0.8 x each beat; right thumb+index
  pinch fader_2 and push it from y −14 to −12 over the 8 beats.
- **b8–15 scratch:** left hand baby-scratches on 8ths (target ±2.2 y, snappy easing, wrist ±20°; booth
  platter L wiggles ±40° with it); right hand on the crossfader doing transformer cuts on 16ths (x 0↔+1.5,
  index finger tapping).
- **b16–19 hands up:** right fist pumps on beats 16/17/18 (fingers fully curled), left arm points at the
  crowd (index straight, others curled); head thrown back on beat 19.
- **b20–23 headphones & knob:** left hand presses the left headphone cup, head tilts left 12°; right hand
  twists knob_3 0→90° over the 4 beats (booth knob_3 matches).
- **b24–27 build:** both hands on the platters, alternating 16th scratches, lean in further (+8° chest),
  deeper knee dips, double-time nods.
- **b28–31 drop:** beat 28 both arms fling up and out (V), fingers splayed, head back; beat 29 arms float
  down with rippling fingers; beats 30–31 hands return to the b0 pose so the loop is seamless.

### interrupted
0.0–0.3 freeze + shudder (needle scratch); 0.3–1.2 slow creepy head turn to the crowd, roll 18°, hands lift
off and curl into claws; 1.2 **voice**: leans over the booth gripping its front edge, head jutting forward;
2.4 rears up, fists high; 2.5 **slam** both fists onto the booth top; 2.65 **booth** (booth becomes a block);
2.7–3.6 rears back, arms wide and up, head back, fingers splayed — **roar** at 2.9; 3.6–4.3 hunched glare,
fingers twitching; 4.3 **move:blink** into the arena.

### combat clips
- idle_loop: menacing hover (hips bob ±0.8 over 1.5 s), knees soft, breathing chest, slow head roll ±8°
  with eldritch twitches (2-frame 12° snaps at 1.1 s and 2.4 s), finger curl wave, orb orbiting.
- walk_loop: long lanky strides (thighs ±28°, knees up to 45° on passing), counter arm swing ±20° with
  bent elbows, hunched 10°, hips bob lowest at contact.
- attack_melee: 0–0.4 right arm winds overhead (elbow 90°, claw), torso twists right 25°; 0.4–0.52 claw
  slash down-forward, torso twists left 30°, knees dip; hit 0.5; recover.
- attack_ranged: 0–1.0 both hands rise to the orb conducting, orb drifts to (0,−8,60) above-front, satellites
  spin up, head tilts up; fire 1.0 hands thrust forward, orb recoils; recover by 1.6.
- teleport: 0–0.45 crouch + arms wrap the body, b_root scale squashes (z→0.15, x/y→1.3 at 0.5);
  blink 0.5; 0.5–1.0 unfurl with overshoot.
- summon_illusions: arms spread palms up, head back, fingers trembling, orb rises; summon 0.6.
- hurt: head snaps back/aside 15°, chest recoils, fingers splay.
- death: recoil; 0.4–1.6 staggers back clutching his head; 1.6–2.4 drops to his knees; 2.4–3.4 arms rise
  to the sky, head back (the sky is opening); 3.4–4.0 slumps; orb drops to the ground, satellites scatter.

## 6. Runtime (Java) contract
- Generated: `entity/client/generated/JehoashModelBase.java`, `DjBoothModelBase.java`; clips in
  `assets/cabinmod/animations/joash.json`, `dj_booth.json`. Generated bases expose `parts()` (name → part)
  and `PARENT` (name → parent name) for cloth/FK.
- Textures: `textures/entity/joash/joash.png` + `joash_emissive.png`; `textures/entity/dj_booth/dj_booth.png`
  + `dj_booth_emissive.png`; booth item icon `textures/item/dj_booth.png`. Placeholders are generated (tagged)
  and never overwrite a user-painted file.
- Beat clock: `DiscoMusic` (BPM 114, first beat 0.0058 s). jam_loop time = (beats mod 32) × 60/114 s.
- Cloth: every `phys_*` chain (cape + robe strands) gets a ClothChain; beat-synced levitation lifts the
  robe strands and billows the cape while dormant, flares them on the roar, lets them fall on the needle
  scratch.
- Interaction: right-click (mobInteract) only. While dormant he is invulnerable and hits do nothing.

## 0. User decisions (2026-10-18)
Booth: 3 wide full rig (decks, mixer, laptop, boom mic). Routine: as planned, with headphones.
Eldritch extras: orbiting mirror shards only (no tendrils, no sleeve ribbons). Trigger: right-click only.

## 7. Sounds (all mono except joash_strike)
| event | files | content |
|---|---|---|
| joash_ambient | joash/ambient_1..4 | eldritch whispers / breaths / low hums, 2–4 s, quiet |
| joash_hurt | joash/hurt_1..3 | pained growls, 0.4–0.7 s |
| joash_death | joash/death | long anguished howl, 2.5–3.5 s |
| joash_welcome_voice | joash/welcome_voice | processed Porkrind "Welcome!" (demonic, pitched down, layered) |
| joash_roar | joash/roar_1..2 | rage roar, 1.8–2.5 s |
| joash_laser_charge | joash/laser_charge | 1.0 s rising synth charge (ends where the fire starts) |
| joash_laser | joash/laser_fire_1..3 | rave laser: detuned supersaw zap sweeping down + noise burst + sub "wub", 0.8–1.2 s |
| joash_teleport | joash/teleport_1..3 | (keep current files: Kenney Digital Audio phaseJump1–3, tools/audio/build_sounds.py) user likes these; the only synth/sci-fi sound kept |
| joash_illusions | joash/illusions_1..2 | rebuilt from CC0 recordings by tools/audio/build_illusion_sounds.py (no synth tones), 1.2–1.34 s: reversed ghost breath + whisper swell into a three-voice choir moan, real glass/metal "mirror" shimmer, soft rope whoosh |
| joash_illusion_pop | joash/illusion_pop_1..3 | same script, 0.62–0.78 s (played at pitch 1.2): real glass shatter + fragments, reversed ghost gasp sucked away, small air puff |
| joash_strike | joash/strike (stereo, 15.0 s) | death strike soundtrack synced to the shader (below) |
| dj_booth_needle | dj_booth/needle | needle scratch/drag stinger, ~0.6 s |
| dj_booth_slam | dj_booth/slam | heavy thud on the booth, ~0.6 s |
| dj_booth_use | dj_booth/scratch_1..2 | (keep) |
| disco_music | music/disco | (keep; range raised to 112 blocks) |

**Strike timeline** (shader IsBlockHit = 8 s): storm swell + distant thunder 0–2 s; sky tearing 0.72–2.16 s
ending in a crack; 8 planets — whoosh from 2.72+0.384k s, impact at **3.264+0.384k s** (k=0..7: 3.26 3.65
4.03 4.42 4.80 5.18 5.57 5.95), each heavier/lower than the last, alternating slight L/R; sun falls 6.4–8.0 s
(rising roar + rising sub); **explosion at 8.0 s** (huge, wide); shockwave sweep 8.0–11.0 s; embers/debris
crackle 8.2–14 s; fade by 15.0 s.
