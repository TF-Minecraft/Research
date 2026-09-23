# Research Plugin - System Overview

> **Version 1.0.0 (batch 18):** Research uses `inputs/` + `outputs/` instead of `projects/`. Output and result are rolled at station start and persisted in station JSON. See [`RESEARCH_REDESIGN.md`](RESEARCH_REDESIGN.md).

This document explains the Thaumcraft-style research system for staff who author config and for developers implementing later batches.

## What players do

Research is a **discovery minigame**, not a spreadsheet grind.

1. **Start a project** at a research station (default: lectern). **Hold** that project's **start_item** research paper (`m.research.r_*` MMOItems) in your hand and right-click the lectern. The paper in your hand is consumed (not items elsewhere in your inventory).
2. **Click an item** in your inventory to place it in the experiment slot (one per click). **Click the experiment slot** to take it back out.
3. **Confirm the experiment** (costs Mental Points). The item's **primary aspect** grants `experiment.primary_points` (default 2) and **secondary aspect** grants `experiment.secondary_points` (default 1) toward testing those aspects on the project.
4. After enough experiments on an aspect, it is either **confirmed** (you need X points in it to complete the recipe) or **rejected** (gray out - do not use it anymore).
5. Over time the **product reveals** and aspect rows **unlock identity** (tested-unknown icon, then aspect display + orange progress panes).
6. When all required aspects are confirmed with enough points, the **pre-resolved result** pops above the lectern. The result was chosen at station start (output roll + template resolve). Pick it up from the world. Codex plugins listen to `ResearchCompleteEvent`.

High **Intelligence** (configurable MMOCore attribute) lowers aspect identity reveal and confirm thresholds, speeds product reveal. **Wisdom** and **Intelligence** can increase Focus regen (configured in TFMCCore `focus.yml`). Mental points are **character-keyed** on TFMCCore, shared with Magic meditation.

## Comparison with AdvancedResearch

| Area | AdvancedResearch (old) | Research (new) |
|------|------------------------|----------------|
| Core action | Click abstract research notes | Insert real items as experiments |
| Product | Known from the start | Hidden until mid-research |
| Required elements | Names visible immediately | Hidden until discovered |
| Item cost | Starter item consumed; notes use MP only | Per-project `start_item` paper on start; experiment items returned after confirm |
| Feedback | Random +/- deltas on known bars | Confirm / reject / gray aspects |
| UI | 54-slot vanilla chest GUI | Vertical 54-slot station GUI (shuffled aspect column, lock placeholders) |
| Intelligence | Better note RNG | Earlier reveals and confirm/reject |

Both plugins should **not** share the same lectern stations until migration is planned.

## Config folder layout

All paths are under `plugins/Research/` on the server.

| Path | Purpose |
|------|---------|
| `config.yml` | MP economy, attribute IDs, experiment thresholds, global experiment points, station block |
| `messages.yml` | Player-facing chat strings |
| `aspects/` | Aspect catalog (display, sounds, `primary_items`, `secondary_items`) |
| `inputs/` | Start items mapped to weighted output id pools |
| `outputs/` | Rolled recipes: displays, aspects, single `result` |
| `templates/` | Weighted result template pools (`t.*` refs in output `result.template`) |
| `planner/` | Research Planner (**removed in batch 14**) |
| `gui.yml` | GUI item refs and hex colour tokens (**added in batch 14**) |
| `data/players/` | Runtime player JSON (batch 3) |
| `data/stations/` | Runtime active station state (batch 4) |

Load order: `config.yml`, `messages.yml`, `gui.yml`, `aspects/`, `templates/`, `outputs/`, `inputs/`. Aspect and loader yaml files iterate in **alphabetical filename order**. Duplicate start items across inputs fail reload. Duplicate aspect item refs in the same role log a conflict and **last loaded wins**. Use `/research reload` to apply yaml changes without a restart.

## Item reference format

Never hardcode item ids in Java. Use these strings in yaml:

| Prefix | Example | Meaning |
|--------|---------|---------|
| `m.TYPE.ID` | `m.MATERIAL.DYNAMITE` | MMOItems type + item id |
| `ia.namespace:id` | `ia.tfmc:arcane_crystal` | ItemsAdder namespaced id |
| `vanilla.MATERIAL` | `vanilla.IRON_INGOT` | Vanilla material |
| `t.ID` | `t.runestones` | Result template id in `templates/` |

Result templates can nest (`t.child` inside a template). Cycles are detected at load; invalid entries are skipped with a severe log.

## Authoring workflow for staff

### 1. Define aspects (`aspects/`)

Each aspect needs a unique id key used everywhere else:

```yaml
destruction:
  name: "§cDestruction"
  lore:
    - "§7The force of ruin."
  display:
    item: ia.mcicons:icon_cancel
  grid_color: red
  pulse_color: vanilla.RED_STAINED_GLASS_PANE
  sounds:
    input: entity.item.pickup
    confirm: entity.generic.explode
    volume: 0.6
    pitch: 1.2
  primary_items:
    - m.materials.dynamite
    - v.GUNPOWDER
  secondary_items:
    - v.TNT
```

You can split aspects across multiple files (e.g. `elements.yml`, `arcane.yml`). Aspect yaml files load in alphabetical filename order.

Global point defaults live in `config.yml`:

```yaml
experiment:
  primary_points: 2
  secondary_points: 1
```

**Item lookup:** scan all aspects. A ref in `primary_items` grants primary aspect + `primary_points`. A ref in `secondary_items` grants secondary aspect + `secondary_points`. One item may be primary on one aspect and secondary on another. Confirm and input sounds use the **primary** aspect's `sounds` block; if the item has no primary registration, use the secondary aspect's sounds.

**Deploy note:** delete the legacy `experiment-items/` folder on the server after updating to batch 17+; item lists in `aspects/` are authoritative.

### 2. Define inputs (`inputs/`)

Maps a **start item** to weighted **output ids**:

```yaml
decarian_codex_paper:
  enabled: true
  start_item:
    item: m.research.r_
    amount: 1
  outputs:
    - id: decarian_codex
      weight: 1.0
```

Each start item ref must be unique across all inputs. Output is rolled once when the player starts research.

### 3. Define outputs (`outputs/`)

Recipe, GUI displays, and result. **No `start_item` here.**

```yaml
decarian_codex:
  enabled: true
  mystery_display:
    item: m.research.r_
  product_display:
    item: m.research.c_arcane_crystal
  product_reveal:
    base_after_confirmed_aspects: 2
  result:
    item: m.research.c_arcane_crystal
  aspects:
    destruction:
      required_points: 6
    arcanum:
      required_points: 4
```

- `result.item` - concrete ref spawned on completion.
- `result.template` - `t.*` id; resolved **at station start** to a concrete ref and stored on the station.

`required_points` must be **greater than 0** for every aspect in an output.

**Deploy note:** delete legacy `projects/` and `experiment-items/` folders on the server when upgrading to 1.0.0. Clear stale `data/stations/*.json` (old `project_id` format is not migrated).

### 4. Result templates (`templates/`)

Reusable weighted pools referenced as `t.template_id` in output `result.template`:

```yaml
runestones:
  outputs:
    - item: m.seithr_runes.rune_of_ice_shard
      weight: 1.0
    - item: m.oseni_runes.rune_of_fire_breath
      weight: 1.0
```

Output example:

```yaml
result:
  template: t.runestones
```

`ResearchCompleteEvent` carries `inputId`, `outputId`, and the resolved concrete item ref (e.g. `m.*`), not `t.runestones`.

### 5. Tune economy (`config.yml`)

- `mental_points` - cap, hourly regen, offline regen, experiment cost.
- `attributes.discovery.mmocore_id` - which MMOCore stat lowers reveal/confirm thresholds (default `intelligence`).
- `aspect_discovery` - `base_reveal_percent`, `reveal_percent_reduction_per_point`, `min_reveal_percent`.
- `aspect_confirm` - `base_confirm_percent`, `confirm_percent_reduction_per_point`, `min_confirm_percent`.
- `attributes.regen_bonuses` - list of stats that add MP per hour.
- `station.result_spawn` - kick velocity, crit trail duration, burst particles when the result item pops above the lectern.

Attribute ids are **not** hardcoded in plugin code.

## Aspect state machine (runtime)

Each aspect on an active project moves through:

```text
UNKNOWN -> TESTING (accumulating experiment points) -> CONFIRMED or REJECTED
```

- **CONFIRMED**: aspect is part of the recipe; confirmed when points reach `aspect_confirm` percent of `required_points` (intelligence lowers threshold).
- **REJECTED**: legacy state only; off-recipe experiment aspects are ignored (no points, no UI).

Confirm thresholds use `aspect_confirm` in `config.yml`, not experiment test counts. Off-recipe aspects from experiment items do nothing.

## Station GUI

The research station uses a **vertical 54-slot** (9x6) chest inventory. Fixed control slots plus a shuffled aspect column; `grid_filler` on unused slots.

### Slot map

| Slot | Role |
|------|------|
| 0 | Scrap |
| 2 | Mental Points display |
| 10 | Mystery / product paper |
| 3, 12, 21, 30, 39, 48 | Aspect lock column (always 6 rows) |
| 4-8, 13-17, ... (cols 4-8 per row) | Progress panes (5 per aspect row) |
| 28 | Experiment item |
| 36 | Confirm experiment |
| 37, 38 | Primary / secondary aspect preview |

### Aspect rows

- **Always 6 rows:** Projects with fewer than 6 aspects use random **decoy rows** (black glass divider + empty progress panes).
- **Shuffle:** Real aspect ids shuffled into random rows at project start (`aspect_slot_order`, 6 entries, empty = decoy).
- **Progress panes (batch 15):** Always **5 active panes** per real aspect row. Pane k fills when points reach `ceil(k * required / 5)`. At full `required_points`, all 5 panes are lit (e.g. 3/3 fills 5 panes). Points are capped at `required_points` (no overstack).
- **Divider (col 3):** `aspect_row_divider` (default black stained glass, blank name, no hover tooltip) for decoy rows and real aspects with **0 points**.
- **Tested-unknown (col 3):** When points > 0 but identity is not revealed, `aspect_tested_unknown` icon. Label from `messages.yml` `aspect.tested_unknown_name`. Yellow progress panes fill as points accumulate (`aspect.progress_unknown_name`).
- **Identity reveal:** When **filled progress panes** reach `aspect_discovery` percent of 5 panes (default 0.8; intelligence lowers threshold), col 3 becomes the aspect display item and filled panes become orange glass with shared name/lore.
- **Confirm:** When filled panes reach `aspect_confirm` percent of 5 panes (default 0.8), aspect state becomes CONFIRMED. Completion still requires full `required_points` on every recipe aspect.
- **Off-recipe aspects:** Experiment items that grant aspects not in the rolled output yaml add no points and show no UI feedback (wave and sound still play). Example: iron ingot (metal) on decarian codex.

### Confirm wave

After confirming an experiment, a brief **pulse** radiates outward from the product slot (10). Colour comes from the **primary aspect's** `pulse_color` in `aspects/` if that aspect gained recipe points; else the secondary aspect's colour; else `gui.yml` `items.wave_pulse_default`. Each ring reverts to gray filler behind the wave front (not sticky). Only empty filler slots are touched, not content slots.

Aspect `pulse_color` is an item ref (e.g. `vanilla.RED_STAINED_GLASS_PANE`). If omitted, `grid_color` maps to a default pane colour.

### GUI item refs (`gui.yml` `items` section)

- `confirm_button`, `cancel_button`, `filler` (legacy fallback), `grid_filler` (background padding), `aspect_progress_inactive` (cols 4-8 empty panes), `mental_points_icon`, `locked_aspect`, `aspect_row_divider`, `aspect_tested_unknown`, `aspect_progress_unknown`, `aspect_progress_revealed`, `wave_pulse_default`
- Padding items (`aspect_row_divider`, `aspect_progress_inactive`, `grid_filler`) use a blank display name and hide tooltips so they do not pop a name on hover.
- `labels.inventory_title` / `labels.scrap_confirm_title` plus `colors.inventory_title` for the chest title (applied on each open, so `/research reload` is picked up next time the GUI opens).
- Hex colour tokens under `colors`; applied to GUI item names/lore via `GuiText` + TLibs `StringFormatter.formatHex`.
- GUI labels that come from `messages.yml` (`aspect.progress_unknown_name`, `aspect.tested_unknown_name`, lore lines via `GuiText.guiMessage`) are formatted the same way. Chat (`Messages.get` / `Messages.format`) also runs `formatHex` when the string contains `#`.

## Research Planner (batch 8) - removed in batch 14

The Research Planner allowed players to spend MP to bias project selection. It is **removed** in batch 14: output is rolled at research start with no player bias. See [`RESEARCH_REDESIGN.md`](RESEARCH_REDESIGN.md).

~~Before starting a project, players may:~~

~~- Pay Mental Points to bias toward an aspect or planner category (higher chance that aspect appears in the project).~~
~~- Skip preference for free.~~

~~Odds scale from `base_bias_percent` to `max_bias_percent` based on discovery attribute. Config lives in `planner/categories.yml`.~~

## Post-implementation notes

- **Researcher profession** perks - configure via `external_modifiers` in `config.yml` when profession rework is ready.
- **AdvancedResearch migration** - see [`MIGRATION_FROM_ADVANCEDRESEARCH.md`](MIGRATION_FROM_ADVANCEDRESEARCH.md); do not run both plugins on the same stations.

## Example playthrough (Decarian Codex)

Balanced for **6 unique experiment items** and default `aspect_confirm.base_confirm_percent: 0.8`.

1. Player opens station - six lock rows; decoy rows never gain progress.
2. Confirm **dynamite**, **gunpowder**, and **TNT**. Yellow panes fill on unknown rows; at 60% of active panes filled, rows reveal aspect name + orange panes.
3. Destruction and fire **confirm** when enough panes fill; product reveals after 2 confirmed aspects.
4. Confirm **arcane crystal**, **enchanted book**, and **lapis** for the revealed arcanum row until 4 points and confirm.
5. Pre-resolved result item `m.research.c_arcane_crystal` pops above the lectern (chosen at station start).

## Reload

`/research reload` (permission `research.admin`) reloads all yaml and restarts the MP regen task. Safe to run with a station GUI open.
