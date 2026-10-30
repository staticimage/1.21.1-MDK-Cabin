# CabinMod: multistage cult rave

Minecraft **1.21.1**, NeoForge **21.1.164**, Java **21**. Original ZIPs are untouched. This project includes the original mod content plus the revised Jehoash encounter.

## Play

Replace the previous CabinMod JAR in your instance's `mods` folder. Keep only one CabinMod JAR installed.

Generate a **new** arena in a clear, loaded 25 x 9 x 25 area above its floor:

```mcfunction
/cabinmod rave_arena ~ ~ ~
```

The coordinates are the center at standing height. The entrance faces south. Existing arenas are not rebuilt automatically. Natural dungeon placement is not enabled in this iteration.

1. Jehoash DJs on a two-block podium beneath a boxed metal truss. Six moving-head lights sweep harmlessly across the dance floor. The two orange support collars can already be attacked. Right-clicking the boss does not activate anything.
2. The **first damaging hit on either support** activates phase one. The boss bar says **supports**. Jehoash stays at the booth, directs the fixtures, and is protected from damage. Attack both supports with melee weapons or player-owned projectiles while dodging the warned laser shots. Three accepted support hits by a player on the stage launch that player toward the dance floor. A temporary field closes after a short clearance delay; it also closes periodically, preventing players from camping the supports.
3. Breaking both supports begins the shared collapse sequence: torn connections, buckling columns, swaying beam, sparking/stuttering fixtures, Jehoash looking up and recoiling, a last-second teleport, impact debris and a shockwave, then his crouched landing and dagger reveal. The sequence lasts about 5.5 seconds. **Your camera and movement remain under your control.**
4. **Only when the beam lands**, animated flesh begins spreading out from beneath his stand. Combat resumes on foot with the cult dagger, tracking glass shards from the spherical disco ball, teleports, and illusions. The fallen lights are destroyed with the rig.
5. Victory restores the room, stage truss and decorative lights. Jehoash drops the key only. Right-click the surviving booth to collect its one shared record. The dagger is boss-only and is not a reward.

The booth stays locked against extraction, mining and explosions until victory. Both platter visuals represent one record reward. An empty booth accepts any of the six CabinMod records for playback.

## Multiplayer

The server owns support health, phase changes, collapse timing, teleport and rewards. All players see the same encounter; there is no forced camera.

Group size is sampled from living survival/adventure players inside the arena on the first support hit. Each support has `60 ? (1 + 0.4 ? (players ? 1))` health; the boss has `250 ? (1 + 0.6 ? (players ? 1))` health. Joining later does not heal or rescale the encounter. Support hit cooldowns are per attacker, so simultaneous hits from teammates count. The first support hit starts laser warnings immediately, with fixtures staggered by 14 ticks. Lights distribute warnings across eligible players, including players beside the support line, hold one target during each warning, then lock their aim for six ticks before firing. Creative players can see the attacks but retain normal damage immunity. The on-foot boss periodically changes focus between players. The key and booth record are shared encounter rewards.

Support health, party scaling and collapse progress survive save/reload. Each client's record playback starts when it enters hearing range; animations follow that client's actual audio clock. Audio is not globally seek-synchronized between clients.

The selected record loops continuously through the entire fight. First-hit activation, the falling beam, teleport, and on-foot combat keep the same client sound instance and playback clock. The detached booth shares the boss UUID as its audio key, so handing it over does not restart or double the song. Its combat lock prevents extraction only. Playback stays full-volume across the arena, fades outside 32 blocks and ends at 128 blocks; the player's Records volume setting still applies. After victory it continues until someone takes the record. See the `AUDIO HANDOFF` comments in `DiscoMusic.java` and the ownership comments in `DjBoothBlockEntity.java`.

## Direct images and animation

The robe and booth now use the supplied images **directly**, packed into the existing UV layouts. The screen displays these two original files side by side, unchanged:

- `Screenshot 2025-04-28 191821.png` (wing image)
- `Screenshot 2025-04-28 180651.png` (angel photo)

The screen is completely static: no fake equalizer, pulsing tint, glitch, crossfade, or animation clock. The copies in the mod are byte-for-byte identical to the supplied files; asset validation checks their hashes. The rejected generated robe/booth/screen artwork is archived in `design/rejected-art/` and is not used by the game.

Run `python tools/textures/pack_direct_cult_art.py` to rebuild the robe and booth atlases. Its named `ROBE_PATCHES` list maps each exact source image to a model UV rectangle, and its docstring explains how to change that mapping. It preserves the original face and hood opening. Supplied originals are in `design/references/cult-moodboard/`.

Active image files:

- `src/main/resources/assets/cabinmod/textures/entity/joash/joash_direct.png`
- `src/main/resources/assets/cabinmod/textures/entity/dj_booth/dj_booth_direct.png`
- `src/main/resources/assets/cabinmod/textures/entity/rave/supplied_wings.png`
- `src/main/resources/assets/cabinmod/textures/entity/rave/supplied_angel.png`

Elbows, wrists and knees articulate beneath the robe; hands remain simple mittens. Sleeves, hood, cape and strands use simulated cloth inertia. Six measured song beat maps drive the DJ's 16-beat hand/turntable choreography. The spherical mirror ball fires tracking glass shards; the separately shaded dagger remains boss-only. Walls and floors use the existing 16-frame animated flesh material.

## Where to modify things

The following files contain explanatory comments and tuning notes in the code itself:

| File | What it controls |
| --- | --- |
| [RaveBlock.java](src/main/java/net/austin/cabinmod/block/custom/RaveBlock.java) | Detailed wall/floor guide: saved states, JSON model selection, animated texture strip, growth, restoration, lighting and collision. Start here to reverse-engineer the walls. |
| [RaveArena.java](src/main/java/net/austin/cabinmod/worldgen/RaveArena.java) | Arena dimensions, stage/truss positions, support creation, flesh wave, reversible access-field blocks and stage restoration. |
| [JehoashEntity.java](src/main/java/net/austin/cabinmod/entity/custom/JehoashEntity.java) | Server phase machine, first-hit trigger, party scaling, knockback, field cycle, cutscene events, boss AI and rewards. `SUPPORT_HITS_BEFORE_EJECT` and `FIELD_*` tune stage access. |
| [ArenaLightEntity.java](src/main/java/net/austin/cabinmod/entity/custom/ArenaLightEntity.java) | Warning/lock/fire timing, damage, multiplayer targeting, creative testing and wall collision. |
| [TrussSupportEntity.java](src/main/java/net/austin/cabinmod/entity/custom/TrussSupportEntity.java) | Hittable column wrappers and per-attacker cooldowns. |
| [StageBarrierBlock.java](src/main/java/net/austin/cabinmod/block/custom/StageBarrierBlock.java) | Physical stage field: blocks players/projectiles, lets fixture laser raycasts through. |
| [StageCollapse.java](src/main/java/net/austin/cabinmod/entity/StageCollapse.java) | Shared dodge, impact and end cues; beam fall/roll/retreat curves. |
| [JehoashModel.java](src/main/java/net/austin/cabinmod/entity/client/JehoashModel.java) | Mesh/bones, two-link arm IK, conductor/collapse poses and cloth attachment. |
| [DjPerformance.java](src/main/java/net/austin/cabinmod/entity/client/DjPerformance.java) | Per-song hand, platter and crossfader choreography. |
| [RaveScreenRenderer.java](src/main/java/net/austin/cabinmod/entity/client/RaveScreenRenderer.java) | Static original images, proportions and panel layout. No animation. |
| [pack_direct_cult_art.py](tools/textures/pack_direct_cult_art.py) | Direct image-to-UV mapping for robe and booth. |

The field follows a 200-tick cycle: warns/ejects at tick 100, becomes solid at 112, and opens at 172. Repeated support hits can also request a 60-tick closure after a 12-tick clearance delay. Fields disappear for the collapse and are cleaned up on victory. None of this takes over the camera.

## Burning porta-potty

```mcfunction
/give @s cabinmod:burning_portapotty
```

Also available in the CabinMod creative tab. The decorative prop occupies two blocks and has an open door, visible toilet bowl, animated fire, smoke and embers. It has no interaction or spreading-fire behavior yet.

## Build and verification

```powershell
./gradlew.bat build
./gradlew.bat runGameTestServer
python tools/validate_rave_assets.py
```

Ten GameTests and asset validation cover record locking/extraction/save/reload, booth hand-over, key-only reward contracts, arena restoration, shard collision/homing/cleanup, harmless idle lights versus damaging attacks, two-block prop placement/removal, stand-origin flesh spread, six choreography loops, and the complete staged encounter with **two simulated server players**. Multiplayer checks include group scaling, simultaneous hits, distributed warning targets, disabled right-click activation, first-hit activation, both supports required, reload during both phases, last-second teleport, delayed flesh, shared record and restored truss. The additional access test checks creative/support-line targeting, actual firing, third-hit ejection, temporary and periodic fields, laser passage, player collision and cleanup. These are automated server simulations, not a separate live multi-client play session.

`runData` regenerates loot tables. `tools/audio/analyze_records.py` measures existing audio (requires numpy, scipy, soundfile). `tools/visual_smoke/pack_flesh_animation.py` repacks the authored flesh frames (Pillow).

The optional visible preview harness is compiled only with:

```powershell
./gradlew.bat -I tools/visual_smoke/smoke.gradle runClient --no-configuration-cache
```

It requires a disposable `build/rave-preview/saves/RavePreview/level.dat`, replaces the area around `(0,80,0)`, scripts the encounter, captures frames, resets it, and leaves Minecraft open. It is excluded from the release JAR. Screenshots are in `build/rave-preview/screenshots`; the latest set starts with `review-`.
