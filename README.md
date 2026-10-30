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
5. Victory restores the room, stage truss and decorative lights. Jehoash always drops the key and has a **0.5% (1 in 200)** chance to drop his wearable robe when defeated by a player. Right-click the surviving booth to collect its one shared record. The dagger is boss-only and is not a reward.

The booth stays locked against extraction, mining and explosions until victory. Both platter visuals represent one record reward. An empty booth accepts any of the six CabinMod records for playback.

Jehoash's Robe (`cabinmod:jehoash_robe`) is an epic cosmetic item worn in the **chest slot**. Right-click or shift-click it into the equipment slot. It has no armor stats or durability and does not stack. The loot table makes one fixed robe roll per real boss defeat; Looting does not increase it, and illusions never drop it. Dropped robes use normal Minecraft item pickup and ground physics.

The wearable robe uses the existing BRG cloth atlas with a player-sized, open-faced hood, separately fitted classic/slim sleeves and first-person sleeve rendering. Its upper body follows the animated player torso; the skirt, cape, shoulder drape and sleeves use cloth physics with animated body/leg, block and mount collision. Cloth resets on teleport, dimension change and re-equipping. Vanilla equipment saves and networking handle persistence and visibility to other players.

## Multiplayer

The server owns support health, phase changes, collapse timing, teleport and rewards. All players see the same encounter; there is no forced camera.

Group size is sampled from living survival/adventure players inside the arena on the first support hit. Each support has `60 ? (1 + 0.4 ? (players ? 1))` health; the boss has `250 ? (1 + 0.6 ? (players ? 1))` health. Joining later does not heal or rescale the encounter. Support hit cooldowns are per attacker, so simultaneous hits from teammates count. The first support hit starts laser warnings immediately, with fixtures staggered by 14 ticks. Lights distribute warnings across eligible players, including players beside the support line, hold one target during each warning, then lock their aim for six ticks before firing. Creative players can see the attacks but retain normal damage immunity. The on-foot boss periodically changes focus between players. The key and booth record are shared encounter rewards.

Support health, party scaling and collapse progress survive save/reload. Each client's record playback starts when it enters hearing range; animations follow that client's actual audio clock. Audio is not globally seek-synchronized between clients.

The selected record loops continuously through the entire fight. First-hit activation, the falling beam, teleport, and on-foot combat keep the same client sound instance and playback clock. The detached booth shares the boss UUID as its audio key, so handing it over does not restart or double the song. Its combat lock prevents extraction only. Playback stays full-volume across the arena, fades outside 32 blocks and ends at 128 blocks; the player's Records volume setting still applies. After victory it continues until someone takes the record. See the `AUDIO HANDOFF` comments in `DiscoMusic.java` and the ownership comments in `DjBoothBlockEntity.java`.



## Where to modify things

The active editable GIMP sources are [Jehoash's wing outfit](design/textures/jehoash_phase_two.xcf), [his supplied face](design/textures/jehoash_face.xcf), [the BRG cloak](design/textures/jehoash_brg_cloak.xcf), [the collage booth](design/textures/dj_booth_direct.xcf), and [its inventory icon](design/textures/dj_booth_item.xcf). Export each to its corresponding PNG under `src/main/resources/assets/cabinmod/textures/`; Minecraft reads PNGs, not XCFs. The original body atlas, older bloodied cloth and rounded underlayer assets are retained as inactive design sources.
The body and booth atlases are 1024x1024 PNGs mapped to a 128x128 UV space: multiply UV coordinates by eight when painting. The body atlas is opaque; the separate simulated hood and veil cover the face until garment release at collapse tick 88. `JehoashFaceLayer` selects `[158,80]..[302,242]` from the unchanged 366x378 photograph.
The BRG cloak uses a 2048x2048 atlas with seven garment UV islands. Full, outward-facing covers appear on the front robe and back cape; matching botanical fabric covers the hood, veil, mantle, sleeves, sides and hems. [UV rectangles](design/textures/jehoash_brg_cloak_uv.json) match `ClothSheet`. [build_jehoash_brg_cloak.py](tools/textures/build_jehoash_brg_cloak.py) rebuilds the PNG and layered OpenRaster source using the original supplied cover plus the [botanical artwork](design/textures/jehoash_brg_botanicals.png). Open `build/jehoash_brg_cloak.ora` in GIMP and save to the design XCF after regenerating. The booth cabinet UVs remain in `dj_booth_uv_legend.txt` beside its PNG.
Phase two uses a fuller blocky torso and limbs with articulated elbows and knees. Its ivory/lavender feather artwork comes from [Screenshot 2025-04-28 191821.png](design/references/cult-moodboard/Screenshot%202025-04-28%20191821.png), independent of the cloak's BRG design. The [painted source panels](design/textures/jehoash_phase_two_wing_panels.png) contain the complete body, fabric and leather artwork; [the prompt record](design/textures/jehoash_phase_two_art.json) documents generation. [build_jehoash_phase_two.py](tools/textures/build_jehoash_phase_two.py) fits those painted surfaces into native cube UVs and writes the [shared geometry/UV manifest](design/textures/jehoash_phase_two_model.json), generated Java geometry and `build/jehoash_phase_two.ora`. Save that ORA to the design XCF after regenerating. This workflow packs painted artwork; it does not synthesize stitched rectangles or stamp small ornaments.
These native `JehoashModel` parts share root scaling with the face, hands, dagger and disco ball during teleporting. The body becomes visible beneath the cloth at tick 82; shoulders remain covered until sleeve release at tick 88. The throw continues through combat entry at tick 110 and blends into normal motion by tick 128. Detached fabric keeps its momentum, runs at fixed 120 Hz with interpolated rendering, and fades over the final eight ticks of its 38-tick lifetime; attached fabric tuning is unchanged.
The [Blender scene](design/blender/joash_v2.blend) includes a `MC_joash_runtime_reference` collection showing the garment UVs and exact cuboid body geometry from that manifest. Run [sync_jehoash_reference.py](tools/blender/sync_jehoash_reference.py) after rebuilding the body or cloak UVs. [render_jehoash_review.py](tools/blender/render_jehoash_review.py) renders front/back/side/three-quarter views without saving preview changes. Runtime animation and fabric behavior are implemented in Java.
Run `./gradlew -I tools/visual_smoke/smoke.gradle -PraveRevealReview runClient` with Java 21 for reveal, body-transform checks, a complete teleport through its actual blink marker, walking, both attacks and the full death clip. Add `-PrevealFps=30` or `-PrevealFps=120`; use `-PrevealSide=side`, `-PrevealSide=back` or `-PrevealSide=three-quarter` for other angles. Each run writes a separate folder under `build/rave-preview/brg-review`, then exits. [pack_jehoash_review.py](tools/visual_smoke/pack_jehoash_review.py) creates a local review page with normal and quarter-speed videos, selecting only captures of the current body texture. The harness is excluded from normal builds.
Run `./gradlew -I tools/visual_smoke/smoke.gradle -ProbeReview runClient` for the wearable robe. This checks native classic/slim meshes at 30/60/120 FPS and captures both player renderers, movement poses, first-person sleeves, inventory rendering and server-synchronized removal/re-equipping. Add `-ProbeFps=30` or `-ProbeFps=120` for a different capture rate. Run [pack_robe_review.py](tools/visual_smoke/pack_robe_review.py) afterward to create `build/robe-review/index.html` with stills and normal/quarter-speed motion. [render_jehoash_robe_icon.py](tools/blender/render_jehoash_robe_icon.py) renders the 128x128 inventory icon in Blender from the exported native garment in `build/robe-review/geometry`, using the same BRG atlas. For IDE debugger launches, `-PraveVisualSmoke` exposes the optional harness sources; ordinary builds exclude them.
The booth cabinet has separate front, right, left, and back collage layers. Hardware remains on the base layer; the item icon has its own source and should be refreshed when the booth design changes.
The mirror ball and dagger use separate procedural geometry/materials. Blocky gloves use the painted body atlas and share its wrist bones.

The light show runs at the client's rendered frame rate, with 48-sided feathered cones and clipped floor pools.
`RaveLighting` controls cone width (`SPOT_RADIUS`), transparency, panel borders, rainbow-wave speed and music-driven brightness.
Exposed dance-floor panels animate locally; corruption hides them and victory restores them. Silent arenas retain smooth color waves.
This is a visual glow effect, not a change to Minecraft's block-light engine, and requires no shader pack.

The following files contain explanatory comments and tuning notes in the code itself:

| File | What it controls |
| --- | --- |
| [RaveBlock.java](src/main/java/net/austin/cabinmod/block/custom/RaveBlock.java) | Detailed wall/floor guide: saved states, JSON model selection, animated texture strip, growth, restoration, lighting and collision. Start here to reverse-engineer the walls. |
| [RaveArena.java](src/main/java/net/austin/cabinmod/worldgen/RaveArena.java) | Arena dimensions, stage/truss positions, support creation, flesh wave, reversible access-field blocks and stage restoration. |
| [JehoashEntity.java](src/main/java/net/austin/cabinmod/entity/custom/JehoashEntity.java) | Server phase machine, first-hit trigger, party scaling, knockback, field cycle, cutscene events, boss AI and rewards. `SUPPORT_HITS_BEFORE_EJECT` and `FIELD_*` tune stage access. |
| [ArenaLightEntity.java](src/main/java/net/austin/cabinmod/entity/custom/ArenaLightEntity.java) | Warning/lock/fire timing, damage, multiplayer targeting, creative testing and wall collision. |
| [RaveLighting.java](src/main/java/net/austin/cabinmod/client/RaveLighting.java) | Continuous colored cones, soft projected floor pools and music-reactive dance-floor panels. |
| [TrussSupportEntity.java](src/main/java/net/austin/cabinmod/entity/custom/TrussSupportEntity.java) | Hittable column wrappers and per-attacker cooldowns. |
| [StageBarrierBlock.java](src/main/java/net/austin/cabinmod/block/custom/StageBarrierBlock.java) | Physical stage field: blocks players/projectiles, lets fixture laser raycasts through. |
| [StageCollapse.java](src/main/java/net/austin/cabinmod/entity/StageCollapse.java) | Shared dodge, impact and end cues; beam fall/roll/retreat curves. |
| [JehoashModel.java](src/main/java/net/austin/cabinmod/entity/client/JehoashModel.java) | Mesh/bones, two-link arm IK, conductor/collapse poses, garment throw and on-foot follow-through. |
| [ClothSheet.java](src/main/java/net/austin/cabinmod/entity/client/physics/ClothSheet.java) | Connected robe, cape, mantle, sleeves and hood grids, fabric constraints, collision, ragged hems and full release. |
| [DjPerformance.java](src/main/java/net/austin/cabinmod/entity/client/DjPerformance.java) | Per-song hand, platter and crossfader choreography. |
| [RaveScreenRenderer.java](src/main/java/net/austin/cabinmod/entity/client/RaveScreenRenderer.java) | Static original images, proportions and panel layout. No animation. |

The field follows a 200-tick cycle: warns/ejects at tick 100, becomes solid at 112, and opens at 172. Repeated support hits can also request a 60-tick closure after a 12-tick clearance delay. Fields disappear for the collapse and are cleaned up on victory. None of this takes over the camera.

## Burning porta-potty

```mcfunction
/give @s cabinmod:burning_portapotty
```

Also available in the CabinMod creative tab. The decorative prop occupies two blocks and has an open door, visible toilet bowl, animated fire, smoke and embers. It has no interaction or spreading-fire behavior yet.

## Transient Diviner: cosmic ascent and starfall

Opening the diviner starts a client cinematic. The camera rises to a top-down view and climbs faster and faster until the world gives way to space, while the space ambience (choir pad, sub drone, distant chimes) fades in. The Blender planet then turns below, and the view tilts up into the stars, where constellations draw themselves in. The menu then forms as a refracting glass panel, with the diviner's own Cabin-in-Agartha GUI art hanging behind it like a divine apparition. It has torn glowing edges, a halo, god rays and a sigil ring, and the inventory's slot art shows through the glass. Taking a result never puts it on the cursor: it streaks out of the panel into the sky. When you close the menu, the camera follows the result down as a shooting star through the atmosphere onto the diviner. On the way down, a summoning circle charges up on the ground and lightning arcs from the comet. The impact plays black-and-white, then red-and-gold ink frames, then a flash, lens cracks with god rays, a pillar of light, shockwaves and after-shocks, and the item is left on top of the block. Nearby players feel the shake. Other players nearby see the comet, the crater and the shockwave in the world. Esc during the return skips the cinematic. Breaking the block while a star is up drops the item.

**In the heavens.** The Cabin talks with you from a glass box beside the panel. He greets you, then offers topics: what he can make (every diviner recipe, with item icons), what you could make from what you carry, who he is, where you are, and how stars fall. He also reacts to what you lay in the diviner. Each word he speaks comes with a low hum, and the Agartha image brightens while he talks. The panel answers with chimes when you lay in or take out an item and as each star of the progress constellation lights, and a brighter choir layer swells in while you work.

**Star colours.** Every star burns in the colours of what went in and what came out. A recipe can name them (`"star": {"core": "#RRGGBB", "trail": "#RRGGBB"}` in `data/cabinmod/recipe/*.json`). Otherwise [StarColors.java](src/main/java/net/austin/cabinmod/client/diviner/StarColors.java) takes them from the items' textures. The colours reach the comet, trail, summoning circle, crater, shockwave, particles, the space comet and the launch beam.

**Multiplayer.** Each player's launches are their own stars (`CraftingBlockEntity.Star`): they fall when that player leaves the heavens, not when someone else closes the menu, and each lands on its own. While someone is up there, everyone around sees them float in a trance with their arms raised (a client mixin, `client/mixin/HumanoidModelMixin`) and a beam climbing from the diviner. High above it the sky opens onto deep space, with the Cabin in Agartha in it, faint and far away ([HeavensFx.java](src/main/java/net/austin/cabinmod/client/diviner/HeavensFx.java), `diviner_rift.fsh`). A launched result shoots up out of the diviner for everyone, and nearby players faintly hear the Cabin's hums from the diviner (`network/ModPayloads`).

**Camera feel.** Over each phase's exact path the camera rides a spring (it lags and catches up), banks into turns, sways like a hand-held camera and shakes: buffeting that grows with speed and re-entry heat, and jolts at the liftoff, punching out of the atmosphere, the comet passing, the cut back to the world and the final drop. On the way up the climb stalls against the air, then bursts through with a FOV punch. The falling star swoops in on a curved, corkscrewing path (`DivinerFx.pathOffset`) and tumbles, and the camera hands off from space to the world with the star held in the same place on screen.

The diviner's own block model and textures are unchanged.

| File | What it controls |
| --- | --- |
| [DivinerCinematic.java](src/main/java/net/austin/cabinmod/client/diviner/DivinerCinematic.java) | Timeline (intro 110 ticks, return = `CraftingBlockEntity.RETURN_TICKS`, impact 40), camera path, FOV, HUD hiding and post-chain uniforms. |
| [SpaceScene.java](src/main/java/net/austin/cabinmod/client/diviner/SpaceScene.java) | Draws the planet, clouds, atmosphere and comet into the post chain's `planet` target. `SITE_U/V` picks where the diviner sits on the map. |
| [DivinerFx.java](src/main/java/net/austin/cabinmod/client/diviner/DivinerFx.java) | In-world comet path (`cometDistance`), trail, crater, dome, ground cracks and its own flipbook particles (no vanilla particles). |
| [CraftingBlockEntity.java](src/main/java/net/austin/cabinmod/block/entity/CraftingBlockEntity.java) / [StarboundResultSlot.java](src/main/java/net/austin/cabinmod/screen/custom/StarboundResultSlot.java) | Server side: pending stars, landing, drops. |
| `shaders/post/diviner_cosmos.json`, `shaders/program/diviner_*.fsh` | Stars, Milky Way, constellations, haze, warp, comet glow, re-entry plasma, bloom, impact frames. |
| `shaders/core/diviner_*.fsh` | Planet, clouds, atmosphere scattering, comet, crater, trail, ground decal, sprites, glass panel. |

The planet, clouds, atmosphere shell, comet, crater crown and shards live in [diviner_space.blend](design/blender/diviner_space.blend). The planet look comes from the `planet_bake_*` materials. [build_diviner_space.py](tools/blender/build_diviner_space.py) rebuilds that scene, bakes `textures/diviner/planet_*.png`, and exports `meshes/*.cmesh` through [mesh_export.py](tools/blender/mesh_export.py), a triangle-mesh exporter separate from the cube-only `mc_export.py`. [render_vfx_sheets.py](tools/blender/render_vfx_sheets.py) renders the Eevee flipbooks in `textures/vfx/diviner`, and [build_diviner_sounds.py](tools/audio/build_diviner_sounds.py) synthesizes `sounds/diviner/*.ogg` with numpy, scipy and soundfile. It builds a formant choir, Shepard risers, bells, a sub boom, glass shatter and convolution reverb. The cinematic sounds are stereo and the in-world sounds are mono. The apparition samples `textures/gui/crafting_block/crafting_block_gui.png` directly (see `ART_RECT` in `DivinerCinematic`), so it updates whenever you repaint that art. The two Blender scripts run headless:

```powershell
& "E:\Blender Foundation\Blender 5.1\blender.exe" --background --factory-startup --python tools/blender/build_diviner_space.py -- $PWD
& "E:\Blender Foundation\Blender 5.1\blender.exe" --background --factory-startup --python tools/blender/render_vfx_sheets.py -- $PWD
python tools/audio/build_diviner_sounds.py   # keeps the existing space_loop.ogg unless --all
```

`./gradlew.bat -I tools/visual_smoke/diviner_smoke.gradle runClient --no-configuration-cache` captures the whole sequence to `build/rave-preview/screenshots/diviner-*.png` and then exits. Add `-PdivinerNight` to capture it at midnight.

`bash tools/visual_smoke/diviner_mp.sh` runs a live two-player session on this machine: an offline dedicated server (port 25599) and two clients, Alice and Bob. Alice goes up alone while Bob watches her trance, the beam, the heavens opening and her star falling. Then both go up together, both launch, and Bob leaves first. The server logs every star and viewer and prints a PASS/FAIL verdict (only Bob's star may fall, Alice's waits for her). Each client saves screenshots to `build/mp/<name>/screenshots`.

## Build and verification

```powershell
./gradlew.bat build
./gradlew.bat runGameTestServer
python tools/validate_rave_assets.py
```

Nineteen GameTests and asset validation cover record locking/extraction/save/reload, booth hand-over, guaranteed key plus rare robe rewards, arena restoration, shard collision/homing/cleanup, harmless idle lights versus damaging attacks, two-block prop placement/removal, stand-origin flesh spread, six choreography loops, and the complete staged encounter with **two simulated server players**. The robe tests cover the exact drop-chance boundary, player-kill requirement, Looting independence, illusion exclusion, pickup, right-click equipment swaps, shift-clicking and saved equipment. Multiplayer checks include group scaling, simultaneous hits, distributed warning targets, disabled right-click activation, first-hit activation, both supports required, reload during both phases, last-second teleport, delayed flesh, shared record and restored truss. The access test checks creative/support-line targeting, actual firing, third-hit ejection, temporary and periodic fields, laser passage, player collision and cleanup. The decorative-light test checks joining-client metadata, reload continuity, sub-tick aim motion and the absence of animation-generated data updates. Nine diviner tests check that every way of taking a result launches it, that the output refuses items, that the result lands on top of the block exactly `RETURN_TICKS` after the menu closes, that an abandoned star still falls, and that breaking the block drops a falling result. With two players, they also check that each player's launches are separate stars, that one player leaving doesn't drop the other's star, and that stars land one by one. These are automated server simulations, not a separate live multi-client play session.

`runData` regenerates loot tables. `tools/audio/analyze_records.py` measures existing audio (requires numpy, scipy, soundfile). `tools/visual_smoke/pack_flesh_animation.py` repacks the authored flesh frames (Pillow).

The optional visible preview harness is compiled only with:

```powershell
./gradlew.bat -I tools/visual_smoke/smoke.gradle runClient --no-configuration-cache
```

It requires a disposable `build/rave-preview/saves/RavePreview/level.dat`, replaces the area around `(0,80,0)`, scripts the encounter, captures frames, resets it, and leaves Minecraft open. It is excluded from the release JAR. Screenshots are in `build/rave-preview/screenshots`; the latest set starts with `review-`.

Add `-PraveLightReview` to that command for a shorter lighting-only check. It captures Fancy/Fabulous rendering and a wall-occlusion case, checks frame-rate aim and pause/resume, compares frame times with the new effects disabled/enabled, then exits. Its `lighting-*.png` captures and log measurements use the same disposable preview directory.
