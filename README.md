# CabinMod: multistage cult rave

Minecraft **1.21.1**, NeoForge **21.1.164**, Java **21**. Original ZIPs are untouched. This project includes the original mod content plus the revised Jehoash encounter.

## Play together from IntelliJ, on different networks

The **CabinMod - Play Together** launch downloads **e4mc 6.2.3 for NeoForge** and **DevLogin** automatically. You do not need Playit, a VPN app, port forwarding, or router changes. e4mc shares the host's integrated server over the internet after the host clicks **Open to LAN**. It uses external relay services, so an internet connection and an available e4mc service are required.

### One-time setup: both of you

1. Clone this repository, or pull its latest **main** branch. Both players should use the same commit (`git rev-parse --short HEAD` shows it). If testing an unpublished branch, both check out that same branch and commit instead.
2. Open the folder containing `build.gradle` in IntelliJ IDEA as a Gradle project. Use **JDK 21** for the project and Gradle JVM (Settings → Build, Execution, Deployment → Build Tools → Gradle). IntelliJ's **Download JDK** option can install it. No separate Minecraft/NeoForge installer or global Gradle installation is required for these dev runs.
3. Click **Reload All Gradle Projects** and let the first download/import finish. This can take a while: Minecraft assets, NeoForge, mappings, and the multiplayer tools are downloaded.
4. In the run dropdown, select **CabinMod - Play Together** and click **Debug** (bug icon) if you want to edit code while playing, or **Run** otherwise. If the generated run is not listed yet, use Gradle's `runClient` task. Windows terminal equivalent: `./gradlew.bat runClient`.
5. On the first launch, read the IntelliJ Run/Debug console. DevLogin asks you to visit **https://www.microsoft.com/link**, enter the displayed code, and sign in. Each person signs in to **their own Microsoft account that owns Minecraft Java Edition**. Finish the browser prompts; Minecraft then opens. Later launches normally reuse your local login.
6. Confirm that the **Mods** menu lists **Cabin Mod** and **e4mc**. The login should use your Minecraft profile rather than the offline `Dev` name. You do not need to install another CabinMod JAR into `run/mods`: this launch already loads your source code.

Account login is the one part the repository cannot do for you. DevLogin stores its login cache locally in your user home's `.devlogin` directory, outside the project. Do not share that cache. The checked-in configuration contains no passwords, account tokens, or fixed player names.

### You: host the world

1. Choose **Singleplayer → Create New World** (or open an existing test world). For boss testing, enable commands and start in Creative so you can build the arena.
2. Once in the world, press **Esc → Open to LAN**. Choose the joining players' game mode, then click **Start LAN World**. Despite the button name, e4mc allows your friend on another network to join.
3. Open chat with **T** and wait for e4mc's public address. Copy the **e4mc address**, not the ordinary numeric LAN port. Send the complete address to your friend. A new sharing session may produce a different address.
4. Keep Minecraft and the world open. Returning to the title screen or stopping the IntelliJ process ends the session. With friends connected, the Escape menu does not pause the shared world.

### Your friend: join the world

1. Launch **CabinMod - Play Together** from their clone and complete their own login.
2. Choose **Multiplayer → Direct Connection**.
3. Paste your complete e4mc address into **Server Address**, then click **Join Server**. They do not click Open to LAN; only the host does that.
4. Both of you now play in the host's world while each client runs its local CabinMod code. The world is saved on the host's computer.

Your friend can alternatively use a normal Minecraft launcher with NeoForge **21.1.164** and the matching CabinMod JAR from `build/libs`. They do not need e4mc just to join a relay address. Using the same repository commit on both computers is the simplest way to keep development builds matched.

### Test the boss together

With commands enabled, stand in a clear area and run:

```mcfunction
/cabinmod rave_arena ~ ~ ~
```

Both enter the arena before starting; group scaling is chosen on the first support hit. Give yourselves equipment, switch to Survival, then hit an **orange truss support collar**. The first hit activates the light attacks. Damage both supports, survive the falling beam sequence, and fight Jehoash on foot. The key and the record in the surviving booth are one shared set of rewards. For a non-combat prop preview, use `/give @s cabinmod:burning_portapotty`.

### Coding during the session

Existing method-body changes can often be applied with IntelliJ's **Compile and Reload Modified Files** while using Debug. Server logic runs in the host's process; client rendering runs separately on each player's computer. Reloading your own classes does not update your friend's process. New fields/methods, registration changes, and many other structural changes require a restart. For those, commit/push, have your friend pull the same revision, restart both clients, and share the new e4mc address. Stopping at a suspending breakpoint in the host can stall the server and time out your friend; use logging or non-suspending breakpoints for shared play.

For solo testing without an account prompt, choose **CabinMod - Offline Solo** (`./gradlew.bat runClientOffline`). Its separate world directory is `run/offline`; it does not load e4mc. The normal Play Together directory remains `run`. The `server` and `gameTestServer` tasks remain dedicated/local testing tools and do not automatically publish a server through e4mc.

### If joining fails

- **No login prompt / invalid session / offline Dev player:** use Play Together, complete Microsoft login, and restart that run if the session expired. Each player needs a different account.
- **e4mc missing from Mods:** pull the latest commit, reload Gradle, and launch Play Together rather than Offline Solo or an old manually created configuration.
- **No e4mc address or a relay error:** check the host's chat and `run/logs/latest.log`. Close/reopen the world and share it again. The relay service must be reachable; no repository setting can guarantee that a school/work network permits it.
- **Wrong address:** use the full e4mc address shown in the host's chat. `localhost`, a home `192.168.*` address, and the LAN port alone will not reach the host from another network.
- **Mod mismatch / missing registry entries:** compare commit IDs and NeoForge versions, remove old duplicate CabinMod JARs from `run/mods`, and restart both clients.
- **Windows Firewall prompt:** allow the Minecraft Java process to communicate on your current network if prompted. This is separate from router port forwarding.
- **Already connected as this player:** use separate Microsoft accounts, not two copies of the same account session.

Implementation/tuning: `build.gradle` gives Play Together an empty `multiplayer` source set that reuses the main compiled mod and adds e4mc to its runtime dependencies. It also enables ModDevGradle's `devLogin` flag. Neither tool is bundled into the published CabinMod JAR or added as a required dependency for ordinary players. Update the pinned `multiplayerDevRuntime` dependency there when intentionally changing e4mc versions.

References: [e4mc hosting instructions](https://e4mc.link/), [ModDevGradle account login](https://github.com/neoforged/ModDevGradle#using-authenticated-minecraft-accounts), [DevLogin first login](https://github.com/covers1624/DevLogin#usage-and-installation), [IntelliJ HotSwap limitations](https://www.jetbrains.com/help/idea/altering-the-program-s-execution-flow.html#reload_classes).

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
