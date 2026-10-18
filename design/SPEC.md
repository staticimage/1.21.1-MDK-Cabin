# Lockdown Expansion — Design Spec

Fill in every `____`. Anything marked *(suggestion)* is just an idea — replace or delete it.
Nothing gets coded until its row is filled in and confirmed.

Registry ids must be lowercase `a-z0-9_`. Display names can use `§` color codes like the
existing lang file (`§b` aqua, `§d` pink, `§c` red, `§o` italic).

Existing crafting stations: vanilla crafting table, furnace/smoker/blast furnace, smithing table,
**Transient Diviner** (`crafting_block`, 1 input → 1 output recipe type).

---

## 1. Jehoash the Mystic / Haruspex (hostile, mid-tier)

| Field | Value |
|---|---|
| Registry id | ____ *(suggestion: `jehoash`)* |
| Display name | ____ |
| Size (blocks, w × h) | ____ |
| HP / damage / speed | ____ |
| Where it spawns (biomes, light, time) | ____ |
| How rare | ____ |
| Abilities (no summons) | ____ *(suggestions: omen debuff "reading" the player · short blink/teleport · ranged hex)* |
| Sounds (vanilla stand-ins ok) | ____ |

**Silhouette / look** — describe or sketch (robe? hood? masks? extra limbs? floating bits?):
____

**Animations wanted** (idle / walk / attack / ritual / hurt / death / other):
____

### Jehoash's weapon (separate item, swappable later)
| Field | Value |
|---|---|
| Registry id | ____ |
| Display name / tooltip | ____ |
| Obtainable by player? (drop / recipe / no) | ____ |
| Stats if usable | ____ |

### Jehoash drops
| # | Item (existing or new) | Count | Chance | Looting bonus? |
|---|---|---|---|---|
| 1 | ____ | ____ | ____ | ____ |
| 2 | ____ | ____ | ____ | ____ |
| 3 | ____ | ____ | ____ | ____ |

---

## 2. Cabin, Warden of Lockdown (boss)

| Field | Value |
|---|---|
| Registry id | ____ |
| Display name (also shown on boss bar) | ____ |
| Size (blocks) | ____ |
| HP / armor / damage | ____ |
| How it's summoned or found | ____ |
| Phases (how many, HP thresholds) | ____ |
| Attacks per phase | ____ |
| Lockdown mechanic (what "lockdown" does in the fight) | ____ |
| Music / sounds | ____ |

**Silhouette / look**:
____

**Animations wanted**:
____

### Boss drops
| # | Item | Count | Chance |
|---|---|---|---|
| 1 | ____ | ____ | ____ |
| 2 | ____ | ____ | ____ |
| 3 | ____ | ____ | ____ |

---

## 3. New items

Copy the block per item.

### Item: ____
| Field | Value |
|---|---|
| Registry id | ____ |
| Display name | ____ |
| Tooltip | ____ |
| Type (material / tool / weapon / armor / food / summon / key / other) | ____ |
| Material tier / stats | ____ |
| How obtained (drop / loot chest / recipe) | ____ |
| Recipe (ingredients + shape) | ____ |
| Station | ____ |
| Creative tab (CABIN ITEMS / CABIN BLOCKS) | ____ |

---

## 4. New blocks

### Block: ____
| Field | Value |
|---|---|
| Registry id | ____ |
| Display name | ____ |
| Variants (stairs / slab / wall / fence / door / trapdoor / none) | ____ |
| Hardness / tool needed | ____ |
| Drops (itself / other / silk touch rule) | ____ |
| Special behaviour | ____ |
| Recipe + station | ____ |

---

## 5. New material / ore (optional)

| Field | Value |
|---|---|
| Ore registry id + name | ____ |
| Stone / deepslate variants | ____ |
| Height range + vein size + veins per chunk | ____ |
| Drops (raw item) | ____ |
| Smelts into | ____ |
| Tool/armor tier built from it | ____ |

---

## 6. Lockdown structure (first of several)

| Field | Value |
|---|---|
| Registry id + name | ____ |
| Biomes | ____ |
| Surface / underground / both | ____ |
| Rough size | ____ |
| Rooms / wings | ____ |
| Who spawns inside | ____ |
| Loot chests (which rooms, what loot) | ____ |
| How rare (spacing in chunks) | ____ |

**Theme notes ("Cabin is the warden of lockdown"):**
____

---

## 7. Fixes waiting on a name
| Thing | Current | New |
|---|---|---|
| `cabin_trapdoor` display name | `§ccabinmod.cabin_trapdoor` (placeholder) | ____ |
