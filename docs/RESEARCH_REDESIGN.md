# Research Plugin - Redesign Spec (locked)

This document locks design decisions for batches 14 onward. It supersedes conflicting sections in older docs (`projects/`, `experiment-items/`, Research Planner, input-centric flow, `config.yml` `gui` section).

**No legacy migration.** Staff delete old folders on the server and author fresh yaml under the new layout.

---

## Goals

1. Richer GUI presentation (SimpleFactions-style hex colours on GUI text only; chat messages unchanged).
2. Confirm wave as a **pulse** (colour expands, trailing slots revert to gray filler).
3. **100% progress always fills all 5 panes** per aspect row (even when `required_points` is 3).
4. **Aspect-centric experiment items** - each aspect owns sounds, `primary_items`, and `secondary_items`.
5. **Cap aspect points** at `required_points` (no 4/3 overstack).
6. **Output-centric research** - roll the result at **project start**, not at completion; aspects and GUI displays come from the rolled output.
7. **Remove Research Planner** - player choices must not bias the rolled output.
8. **`gui.yml`** - all GUI item refs and GUI hex colour tokens (layout stays fixed in code).

---

## Folder layout (target)

| Path | Purpose |
|------|---------|
| `config.yml` | MP economy, attributes, discovery thresholds, station block/effects, experiment point defaults |
| `gui.yml` | GUI item refs + hex colour tokens (no layout) |
| `messages.yml` | Player-facing chat strings (unchanged role) |
| `aspects/` | Aspect catalog: display, pulse colour, sounds, item lists |
| `inputs/` | Start items and weighted output pools |
| `outputs/` | Result definitions: displays, aspect recipe, final result |
| `templates/` | Weighted item pools (`t.*` refs) |
| `data/players/` | Player MP persistence |
| `data/stations/` | Active station state including rolled output |

**Removed (delete on server, remove from plugin):**

- `experiment-items/`
- `projects/`
- `planner/`

---

## Player flow (target)

1. Player holds a **start item** (research paper / fragment) and right-clicks the lectern.
2. Plugin matches `inputs/` by start item, **rolls one output** from that input's weighted pool, resolves template refs if needed, and **persists** `resolvedOutputId` + `resolvedResultRef` on the station.
3. Main GUI opens using **that output's** `mystery_display`, aspect recipe, and reveal rules.
4. Player experiments with **global** items (any item in aspect lists). Only aspects on the rolled output's recipe gain points.
5. Aspect identity reveal, confirm, and product reveal behave as today (pane-based thresholds).
6. On completion, spawn the **already-resolved** result above the lectern (no roll at finish).

There is **no** pre-start planner screen. Start goes directly to the main research GUI.

---

## Common vs rare research (staff model)

| Type | Input | Output roll | GUI visibility |
|------|-------|-------------|----------------|
| **Named / common** | Crafted paper tied to one item (e.g. Abyssalite ingot + parchment) | Single output, weight 1.0 | `mystery_display` may equal `product_display` and final result from the start |
| **Rare / mystery** | Lost Knowledge Fragment (generic paper) | Random from multi-entry pool at start | Mystery placeholder until product reveal threshold; then `product_display`; final item on complete |

The same three display stages apply to every output:

1. **Start** - `mystery_display`
2. **After product reveal threshold** - `product_display`
3. **Completion** - `result` (concrete `m.*` / `ia.*` / `vanilla.*`, or pre-resolved pick from `t.*` at start)

---

## `gui.yml`

All GUI **icons and pane materials** move out of `config.yml`. Layout (slot numbers) remains in `GridLayout` Java.

```yaml
# Item refs: m.TYPE.ID, ia.namespace:id, vanilla.MATERIAL
items:
  confirm_button: ia.mcicons:icon_confirm
  cancel_button: ia.mcicons:icon_cancel
  scrap_button: ia.mcicons:icon_cancel   # if distinct from cancel scrap flow
  filler: vanilla.GRAY_STAINED_GLASS_PANE
  mental_points_icon: vanilla.LIGHT
  locked_aspect: ia.mcicons:icon_lock
  aspect_progress_unknown: vanilla.YELLOW_STAINED_GLASS_PANE
  aspect_progress_revealed: vanilla.ORANGE_STAINED_GLASS_PANE
  wave_pulse_default: vanilla.LIGHT_BLUE_STAINED_GLASS_PANE

# Hex colours for GUI display names/lore only (TLibs StringFormatter.formatHex).
# Chat messages stay in messages.yml with standard § codes.
colors:
  label_muted: "#7a706a"
  label_body: "#d4c9ae"
  label_accent: "#b8ae61"
  aspect_confirmed: "#82d461"
  aspect_hidden: "#575150"
  progress_unknown: "#9c9775"
  mental_points: "#d1b43f"
  wave_pulse_name: "#5bc4d4"
```

Staff may retune icons and palette without touching `config.yml`. Java reads tokens from `GuiLoader` / `GuiCache` (name TBD in implementation).

---

## Aspect catalog (`aspects/`)

Each aspect id is a top-level key. **Experiment contribution is defined here**, not in a separate experiment-items folder.

```yaml
destruction:
  name: "§cDestruction"
  lore:
    - "§7The force of ruin and explosive change."
  display:
    item: ia.mcicons:icon_cancel
  pulse_color: vanilla.RED_STAINED_GLASS_PANE
  sounds:
    input: entity.generic.explode
    confirm: entity.generic.explode
    volume: 0.6
    pitch: 1.2
  primary_items:
    - m.MATERIAL.DYNAMITE
    - m.MATERIAL.GUNPOWDER
  secondary_items:
    - m.MATERIAL.TNT
```

### Item lookup rules

- Global defaults in `config.yml`:

```yaml
experiment:
  primary_points: 2
  secondary_points: 1
```

- For a placed item ref, scan all aspects:
  - Listed under `primary_items` of aspect A → primary aspect A, `primary_points`.
  - Listed under `secondary_items` of aspect B → secondary aspect B, `secondary_points`.
- One item may be primary for one aspect and secondary for another (normal case).
- **Confirm sound** and **input sound** use the item's **primary** aspect's `sounds` block. If the item has no primary registration (secondary-only), use the secondary aspect's sounds.

### Duplicate registration

If the same item ref appears in the **same role** (`primary_items` or `secondary_items`) on more than one aspect:

- Log `[Research] SEVERE` (or warning) naming the item, aspects, and which aspect won.
- **Last loaded wins** (file iteration order documented in SYSTEM.md).

Conflicts to detect at load:

- Same item in `primary_items` on two aspects.
- Same item in `secondary_items` on two aspects.
- Same item in both `primary_items` and `secondary_items` **on the same aspect** (log; prefer primary).

### Pulse colour on confirm wave

When an experiment is confirmed:

| Case | Wave pane colour |
|------|------------------|
| Primary aspect gained points on the rolled output recipe | That aspect's `pulse_color` |
| Only secondary hit the recipe | Secondary aspect's `pulse_color` |
| Neither hit the recipe | `gui.yml` `items.wave_pulse_default` |

Remove rotating `wave_pulse_colors` list from config.

### Point cap

When adding aspect points:

```text
newPoints = min(required_points, current + delta)
```

Do not exceed `required_points`. Optional: skip further point application when already at cap (silent; chat unchanged).

---

## Progress panes

- **Always 5 active panes** per real aspect row (`AspectProgressLayout.activePaneCount` returns 5 when `required_points > 0`).
- Pane k fills when `currentPoints >= ceil(k * required / 5)` for k = 1..5.
- Example: `required_points: 3` at 3/3 → all **5** panes lit.
- Retune `aspect_discovery` / `aspect_confirm` after this change (low `required_points` reaches reveal thresholds faster with 5 panes).

Decoy rows unchanged (6 rows, empty decoy slots).

---

## Confirm wave animation

After confirm (when project not complete):

1. Pulse colour from aspect rules above.
2. Rings expand outward from product slot (existing Manhattan ring logic).
3. **Trailing revert:** as ring N is painted, ring N-1 reverts to gray `filler` (true pulse, not sticky colour until full redraw).
4. Final ring revert + `populateMain()` refresh.

Wave still skips reserved slots (controls, aspect rows, experiment slot, etc.).

---

## Inputs (`inputs/`)

Maps **start items** to weighted **output** ids.

```yaml
lost_knowledge_fragment:
  enabled: true
  start_item:
    item: m.research.lost_fragment
    amount: 1
  outputs:
    - id: seithr_essence
      weight: 1.0
    - id: decarian_codex
      weight: 0.5

abyssalite_paper:
  enabled: true
  start_item:
    item: m.research.paper_abyssalite
    amount: 1
  outputs:
    - id: abyssalite_ingot
      weight: 1.0
```

- Match held item to `start_item` ref (same as today's project start match).
- If multiple inputs match the same start item, load validation fails (duplicate start item refs).
- Roll output once in `startResearch()`; store on `ActiveProject` / station JSON.

---

## Outputs (`outputs/`)

Defines recipe, GUI displays, and result. **No `start_item` here.**

```yaml
seithr_essence:
  enabled: true
  mystery_display:
    item: m.research.mystery_paper
  product_display:
    item: m.research.seithr_preview
  product_reveal:
    base_after_confirmed_aspects: 2
  result:
    item: m.alchemy.seithr_essence
  aspects:
    ice:
      required_points: 3
    food:
      required_points: 2
    magic:
      required_points: 5

abyssalite_ingot:
  enabled: true
  mystery_display:
    item: m.research.paper_abyssalite
  product_display:
    item: m.research.paper_abyssalite
  product_reveal:
    base_after_confirmed_aspects: 0
  result:
    item: m.materials.abyssalite_ingot
  aspects:
    metal:
      required_points: 4
    earth:
      required_points: 2
```

### Result field

- `result.item` - concrete ref.
- `result.template` - `t.*` id; **resolve at start** to concrete ref, store `resolvedResultRef` on station.
- Nested templates follow existing `TemplateLoader` rules.

### Completion

Spawn `resolvedResultRef` above lectern. Fire `ResearchCompleteEvent` with resolved ref. **No** `ResultPicker` roll at complete time.

---

## Persistence (`data/stations/`)

Extend station save data:

| Field | Purpose |
|-------|---------|
| `inputId` | Which input definition started the session |
| `resolvedOutputId` | Rolled output id |
| `resolvedResultRef` | Concrete item ref after template resolution |
| Existing fields | `aspect_slot_order`, aspect points, tested items, product revealed, etc. |

Mid-research reload must not re-roll output.

---

## Research Planner - removed

The Research Planner (batch 8) is **deleted**, not disabled:

- Remove `planner/` folder, `PlannerLoader`, `PlannerGuiState`, planner GUI in `InventoryManager`, planner branch in `ResearchManager`.
- Remove bias logic from pickers (`preferredAspectIds`, `effectiveBiasPercent` for project selection).
- Remove planner messages from `messages.yml` (or leave unused keys harmlessly until cleanup).
- Lectern start: match input → roll output → open main GUI directly.

**Rationale:** Research discovers what the paper is; players cannot bias the rolled output.

---

## GUI hex styling (SimpleFactions-style)

- Use `me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter.formatHex` for GUI item display names and lore built in `InventoryManager`.
- Apply `gui.yml` `colors.*` tokens to aspect row labels, MP display, confirm button lore, progress pane `???` text, etc.
- **Do not** change chat feedback strings in `messages.yml` for this pass.

Optional later: map aspect `pulse_color` to revealed pane material; v1 may keep `aspect_progress_revealed` from `gui.yml` for all aspects.

---

## Load order

```text
config.yml
messages.yml
gui.yml
aspects/          (build item->aspect index here)
templates/
outputs/          (validate aspect refs)
inputs/           (validate output ids exist)
```

`/research reload` reloads all of the above. Delete stale registries for removed folders.

---

## Validation checklist (load time)

- Every aspect ref in `outputs/*.yml` exists in `aspects/`.
- Every `inputs/*.yml` output id exists in `outputs/`.
- Every item ref in aspect lists passes `ItemRef` validation.
- Duplicate start items across inputs fail load.
- Duplicate same-role item registration logs + last wins (warn staff in reload summary).
- Template cycles unchanged from batch 13 rules.

---

## Related docs

- Implementation order: [`IMPLEMENTATION_BATCHES.md`](IMPLEMENTATION_BATCHES.md) batches 14-18
- Staff authoring (updated after implementation): [`SYSTEM.md`](SYSTEM.md)
- Code layout: [`ARCHITECTURE.md`](ARCHITECTURE.md)
