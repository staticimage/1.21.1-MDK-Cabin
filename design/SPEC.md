# Lockdown Expansion — Design Spec

Fill in every `____`. Anything marked *(suggestion)* is just an idea — replace or delete it.
Nothing gets coded until its row is filled in and confirmed.

Registry ids must be lowercase `a-z0-9_`. Display names can use `§` color codes like the
existing lang file (`§b` aqua, `§d` pink, `§c` red, `§o` italic).

Existing crafting stations: vanilla crafting table, furnace/smoker/blast furnace, smithing table,
**Transient Diviner** (`crafting_block`, 1 input → 1 output recipe type).

---

anything in quotes should be read and translated to numerical values

## 1. Jehoash the Disco Destroyer (hostile, mid-tier boss)

| Field | Value |
|---|---|
| Registry id | joash |
| Display name | Jehoash the Disco Destroyer |
| Size (blocks, w × h) | 1.5 * 3.5 |
| HP / damage / speed | 250 / 50 / "as fast as a creeper running from a cat" |
| Where it spawns (biomes, light, time) | "he should spawn as a boss in his defined structure titled 'the disco'" |
| How rare | "as rare as a finding a jungle temple across the entire overworld (no biome restriction)" |
| Abilities (no summons) | "he should have a disco themed laser weapon that shoots at the player. when close he should have a melee attack as well. occasionally he should teleport around the boss arena and summon illusions of himself. he should have a debuff called 'life of the party' with a discoball icon that when active should immediately induce a jumpscare similar to the mining fatigue elder guardian effect, and the effect should give the player something similar to a super strong version of mining fatigue"|
| Sounds (vanilla stand-ins ok) | "put stand in sounds in for us to change later" |

**Silhouette / look** — describe or sketch (robe? hood? masks? extra limbs? floating bits?):
"he should look like a shrouded raver with a ranged laser weapon that looks like a disco ball and a melee weapon that will be textured later."

**Animations wanted** (idle / walk / attack / ritual / hurt / death / other):
idle / walk / attack melee / attack ranged / death / hurt / teleport / welcoming ("player enters the boss arena") / summon illusions

### Jehoash drops
| # | Item (existing or new) | Count | Chance | Looting bonus? |
|---|---|---|---|---|
| 1 | "every music disc by cabin mod should have a chance of dropping from the boss. only one music disc can drop at a time" | 1 | 10% | +10% |
| 2 | "new item - the wardens spare key" | 1 | 100% | 0% |


---

## 2. Cabin, Warden of Lockdown (boss)

| Field | Value |
|---|---|
| Registry id | cabin_boss |
| Display name (also shown on boss bar) | Cabin, BBC Warden |
| Size (blocks) | 2.5 * 5.5 |
| HP / armor / damage | 600 / "make him have moderately strong armor for the first phase, then remove the armor for the second phase" / 100 |
| How it's summoned or found | "cabin is found in his bbc lockdown and will be summoned after defeating the inmates and inputting the code to escape lockdown" |
| Phases (how many, HP thresholds) | 2, 75% |
| Attacks per phase | "2 unique attacks first phase, 4 more unique attacks in second phase" |
| Lockdown mechanic (what "lockdown" does in the fight) | "lockdown is the build up to the fight with cabin. this requires you to flick a lever that opens 4 cells that contain strong prisoners that will attempt to kill the player. each prisoner will drop a unique identifier that will be used to input a code also found in the structure. inputting this code will then start the boss fight." |
| Music / sounds | "put stand ins for us to change later" |

**Silhouette / look**:
"cabin should look like a hunched prison warden, similar to the mutant zombie from mutant monsters (https://www.curseforge.com/minecraft/mc-mods/mutant-monsters). he should be wielding the cabin mod item excaliber which should be properly scaled to match his size"

**Animations wanted**:
idle / walk / attack melee / attack 2 melee / attack melee phase 2 / attack 2 melee phase 2 / attack 3 melee phase 2 / attack 4 melee phase 2 / death / hurt / "next phase animation similar to something you would see in dark souls"

### Boss drops
| # | Item | Count | Chance |
|---|---|---|---|
| 1 | "a variant of excalibur that is inactive until processed by the transient diviner" | 1 | 70% |
| 2 | heart of the warden (already exists in the mod)  | 1 | 100% |

---

## 3. New items

Copy the block per item.

### Item: ____
| Field | Value |
|---|---|
| Registry id | dead_excalibur |
| Display name | Depleted Excalibur |
| Tooltip | A once powerful weapon, now reduced to a shell. |
| Type (material / tool / weapon / armor / food / summon / key / other) | weapon |
| Material tier / stats | "it should be wood tier but without any durability" |
| How obtained (drop / loot chest / recipe) | "dropped by cabin, bbc warden" |
| Recipe (ingredients + shape) | none |
| Station | none |
| Creative tab (CABIN ITEMS / CABIN BLOCKS) | CABIN ITEMS |

---


## 6. Lockdown structure (first of several)

| Field | Value |
|---|---|
| Registry id + name | bbc_lockdown |
| Biomes | "any in overworld" |
| Surface / underground / both | surface |
| Rough size | 50 * 50 |
| Rooms / wings | "the structure should be reminiscent of a prison and any rooms that would fit with that thematically should be added, however the center of the building should be a dedicated boss arena" |
| Who spawns inside | "prisoners and cabin, bbc warden" |
| Loot chests (which rooms, what loot) | "none for now" |
| How rare (spacing in chunks) | "1 every 500 chunks" |



## 7. Fixes waiting on a name
| Thing | Current | New |
|---|---|---|
| `cabin_trapdoor` display name | `§ccabinmod.cabin_trapdoor` (placeholder) | Jehoash Veil |

---

## Translated values (Claude) — check these

| Spec text | Value | Why |
|---|---|---|
| Jehoash hitbox `1.5 * 3.5` | `sized(1.5f, 3.5f)` | |
| Jehoash HP / damage | `MAX_HEALTH 250`, `ATTACK_DAMAGE 50` | taken literally (50 = 25 hearts, one-shots a player with no armor) |
| "as fast as a creeper running from a cat" | `MOVEMENT_SPEED 0.30` | creeper 0.25 × 1.2 cat-flee sprint |
| Disco: "as rare as a jungle temple, no biome restriction" | spacing 144, separation 48 (chunks) | temples are spacing 32 in jungles only (~5% of the world): 32/√0.05 ≈ 144 |
| Life of the Party | custom effect: −99% block break speed, −50% attack speed, 5 min, disco-ball icon (you texture), full-screen jumpscare overlay + `elder_guardian.curse` placeholder sound | modeled on the elder guardian's curse (5 min) |
| Jehoash disc drop | one roll from all 6 cabinmod discs, 10% base + 10% per Looting level | |
| Warden's Spare Key | 100% drop | |
| Cabin hitbox `2.5 * 5.5` | `sized(2.5f, 5.5f)` | |
| Cabin HP / damage | `MAX_HEALTH 600`, `ATTACK_DAMAGE 100` | taken literally (one-shots a player even in full netherite) |
| "moderately strong armor" phase 1 | `ARMOR 15`, `ARMOR_TOUGHNESS 4` → both 0 at phase 2 | between a full iron and a full diamond set |
| Phase 2 threshold 75% | at 450 HP | |
| "heart of the warden (already exists)" | `cabin_core` (Soul of the Warden) | the only existing warden heart/soul item |
| Depleted Excalibur | wood tier, unbreakable; Transient Diviner recipe `dead_excalibur` → `excalibur` | |
| bbc_lockdown "1 every 500 chunks" | spacing 22, separation 8 | one per ~500 chunks of *area* (√500 ≈ 22) |

---

## 8. The Disco (Jehoash's structure) — NEW, please fill
| Field | Value |
|---|---|
| Registry id + name | ____ *(suggestion: `the_disco`)* |
| Surface / underground | ____ |
| Rough size | ____ |
| Rooms (besides the boss arena) | ____ |
| Arena size | ____ |
| What triggers the "welcoming" (entering a marked area / a door / stepping on the dance floor) | ____ |

## 9. Prisoners (4 cells in bbc_lockdown) — NEW, please fill
| # | Registry id | Display name | HP / damage | Look / weapon | Unique identifier it drops (id + name) |
|---|---|---|---|---|---|
| 1 | ____ | ____ | ____ | ____ | ____ |
| 2 | ____ | ____ | ____ | ____ | ____ |
| 3 | ____ | ____ | ____ | ____ | ____ |
| 4 | ____ | ____ | ____ | ____ | ____ |

## 10. Lockdown code — NEW, please fill
| Field | Value |
|---|---|
| Where the code is shown in the structure (sign / book / painting / other) | ____ |
| Input block (registry id + name) | ____ *(e.g. a keypad)* |
| How input works (place the 4 identifiers in order / type digits / other) | ____ |
| Same code every time, or random per structure | ____ |

## 11. Cabin's attacks — name them (2 in phase 1, 4 in phase 2)
| Phase | Name | What it does |
|---|---|---|
| 1 | ____ | ____ |
| 1 | ____ | ____ |
| 2 | ____ | ____ |
| 2 | ____ | ____ |
| 2 | ____ | ____ |
| 2 | ____ | ____ |

## Decisions (2026-10-18)
- **Warden's Spare Key** opens the bbc_lockdown front gate. Progression: The Disco (Jehoash) → key → bbc_lockdown (prisoners → code → Cabin).
- **Damage scaled, not literal:** Jehoash `ATTACK_DAMAGE 12`, Cabin `ATTACK_DAMAGE 24` (supersedes 50 / 100 above).
- **bbc_lockdown rarity:** rarer than the Disco: spacing 180, separation 60 (supersedes 22 / 8 above).
- **Prisoners:** 2 rigs (brute + agile), 2 variants each (texture / weapon / stats differ).

## Decisions (2026-10-18, round 2)
- **The Disco:** underground rave — small surface entrance leading down to a hidden arena.
- **Arena:** 24×24 dance floor (Jehoash teleports within 11 blocks of its centre).
- **Welcoming trigger:** using a block in the arena — a DJ booth (`dj_booth`, display name TBD by user).
  Until then Jehoash is dormant and can't be hurt; using the booth plays `welcome`, shows the boss bar, starts the fight.
- **Illusions:** 3 decoys appear when he blinks; they copy him (walk, attack animations) but deal no damage,
  have 1 HP and pop when hit; gone after 15 s or at his next blink.
- **Life of the Party:** applied like the elder guardian's curse — to players near him while the fight is on,
  re-applied when it's missing or has under 1 minute left.
- **Laser:** aim locks at the start of the ranged clip (dodgeable), fires on the `fire` marker; 8 damage; rainbow beam.
