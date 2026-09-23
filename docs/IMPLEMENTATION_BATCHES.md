# Research Plugin - Implementation Batches

Roadmap from batch 2 onward. Batch 1 (setup) is complete.

**Redesign (batches 14-18):** Locked specification in [`RESEARCH_REDESIGN.md`](RESEARCH_REDESIGN.md). Includes `gui.yml`, planner removal, progress/wave fixes, aspect item lists, and output-centric `inputs/` + `outputs/`.

## Batch 1 - Setup (complete)

**Deliverables:**

- Maven project `research/` with pom, deploy to `TFMC/Research/`
- Minimal `Research.java` (folders + default config copy)
- `ResourceBootstrap` helper
- Default yaml tree in `src/main/resources/`
- `docs/SYSTEM.md`, `docs/IMPLEMENTATION_BATCHES.md`

**Staff can edit after:** yaml examples in resources (copied to server on first run).

**Test checklist:**

- [ ] `mvn package` succeeds
- [ ] JAR appears in `TFMC/Research/research-0.1.0.jar`
- [ ] Plugin loads on server with setup-only log message
- [ ] `plugins/Research/` contains all default folders and yaml files

---

## Batch 2 - Config loaders and reload (complete)

**Deliverables:**

- `LoaderInterface` loaders: `AspectLoader`, `ExperimentItemLoader`, `ProjectLoader`, `PlannerLoader`, `ConfigLoader`, `MessagesLoader`
- `Cache`, `Messages`, `ItemRef`, `YamlFolder`
- Folder iteration for `aspects/`, `experiment-items/`, `projects/`, `planner/` (VehicleFramework pattern)
- Validation on load (unknown aspect refs, bad item refs, duplicate ids)
- `/research reload` command with `research.admin` permission
- `Research.reloadAll()` orchestrating all loaders
- `docs/ARCHITECTURE.md`

**Staff can edit after:** all yaml; reload without restart.

**Test checklist:**

- [ ] `mvn package` succeeds (`research-0.2.0.jar`)
- [ ] `/research reload` reports success on valid yaml
- [ ] Invalid yaml logs clear errors (e.g. project references missing aspect)
- [ ] Duplicate project id across two files fails validation
- [ ] `ItemRef` matches vanilla/MMOItems/ItemsAdder stacks in a dev test

---

## Batch 3 - Mental Points and player persistence (complete)

**Deliverables:**

- `ResearchPlayer` model + `PlayerSaveData` Gson DTO
- `PlayerStore` - `data/players/<uuid>.json`
- `PlayerManager` - join/quit, online regen task, offline accrual on join
- `MmoAttributes` - configured `mmocore_id` reads from MMOCore
- Attribute-scaled regen from `attributes.regen_bonuses` in `config.yml`

**Staff can edit after:** `mental_points` and `attributes.regen_bonuses` in `config.yml`.

**Test checklist:**

- [ ] `mvn package` -> `research-0.3.0.jar`
- [ ] MP regens while online at configured rate
- [ ] Offline player gains MP on join when `offline_regen: true`
- [ ] Higher wisdom/intelligence increases regen (MMOCore stat edits)
- [ ] Player data persists across relog
- [ ] `/research reload` restarts regen task without wiping loaded MP

---

## Batch 4 - Station shell and project lifecycle (complete)

**Deliverables:**

- Lectern interaction via `ResearchManager` (`Cache.stationBlock`)
- One active project per lectern (owner stored on the station)
- Random project start stub from `ProjectLoader` pool
- Scrap with confirmation GUI
- `StationStore` persistence in `data/stations/`
- Placeholder chest GUI via `InventoryManager`

**Staff can edit after:** `station` section in `config.yml`, `projects/` definitions.

**Test checklist:**

- [ ] Right-click lectern opens GUI with mystery paper + visible aspects
- [ ] Second player cannot use occupied station
- [ ] Scrap clears project and frees station
- [ ] Station state survives restart
- [ ] Player with active project at lectern A can start a new project at lectern B

---

## Batch 5 - Experiment logic (complete)

**Deliverables:**

- Experiment item slot in placeholder GUI (slot 22)
- Primary/secondary aspect preview (slots 12, 14)
- Confirm experiment: spend MP (`experiment_cost`), item not consumed
- Per-aspect experiment points on `ActiveProject`
- Tested experiment items tracked and shown gray in preview

**Staff can edit after:** `experiment-items/`, `mental_points.experiment_cost`.

**Test checklist:**

- [ ] Dynamite (if configured) adds destruction + fire test points
- [ ] MP deducted on confirm; item stays in inventory
- [ ] Unknown item rejected with message
- [ ] Same item cannot be re-tested (or shows as already tested)
- [ ] `aspect_points` and `tested_items` persist across restart

---

## Batch 6 - Confirm, reject, and completion (complete)

**Deliverables:**

- Confirm/reject thresholds from `config.yml` + discovery attribute modifiers (`DiscoveryScaling`)
- Aspect state: UNKNOWN, TESTING, CONFIRMED, REJECTED
- Completion when all non-hidden required aspects reach CONFIRMED with enough points
- UI updates: gray rejected, green confirmed with points progress

**Staff can edit after:** `experiment.base_confirm_tests`, `experiment.base_reject_tests`, discovery attribute scaling.

**Test checklist:**

- [ ] Wrong aspect eventually rejects after enough tests
- [ ] Correct aspect confirms and tracks point progress toward `required_points`
- [ ] Project completes when recipe satisfied
- [ ] High intelligence confirms/rejects faster
- [ ] State, test counts, and `completed` persist across restart

---

## Batch 7 - Reveal system (complete)

**Deliverables:**

- Product reveal when `base_after_confirmed_aspects` threshold met (discovery attribute bonus)
- Hidden aspect slots appear per `reveal_when_confirmed_count` (discovery attribute bonus)
- Mystery paper -> product display item swap in GUI
- Messages from `messages.yml` (`product.revealed`)

**Staff can edit after:** `product_reveal`, per-aspect `hidden` and `reveal_when_confirmed_count` in projects.

**Test checklist:**

- [ ] Product name/item revealed at correct progress point
- [ ] Hidden arcanum slot appears only after enough confirms
- [ ] High intelligence reveals earlier than low intelligence
- [ ] `product_revealed` survives restart; hidden visibility recomputed on load

---

## Batch 8 - Research Planner (complete)

**Deliverables:**

- Pre-start planner GUI from `planner/categories.yml`
- MP cost for preference vs free random
- Bias odds from `base_bias_percent` to `max_bias_percent` scaled by discovery attribute
- Category -> aspect grouping in UI

**Staff can edit after:** `planner/categories.yml`, `preference_cost`, bias percents.

**Test checklist:**

- [ ] `enabled: false` -> lectern starts random project immediately
- [ ] `enabled: true` -> planner GUI opens; free start picks unbiased project
- [ ] Paid aspect/category deducts MP and biases selection
- [ ] High intelligence yields higher bias % than low intelligence

---

## Batch 9 - Full grid station UI (9x6) (superseded by batch 11)

**Historical note:** Batch 9 shipped a ring-grid layout with path lines from center to confirmed aspects. Batch 11 replaced this with the vertical column layout; `GridPaths` and `gui.path_lines` were removed.

**Note:** Bukkit chest inventories max out at 54 slots (9x6), not 63. The design uses columns 0-1 for controls and columns 2-8 as a 7-wide aspect grid with center paper at slot 23.

**Deliverables:**

- 54-slot layout: 2 control columns + 7x6 aspect/path grid
- Central mystery/product paper at slot 23
- Aspect nodes on a ring around the center (`GridLayout.ASPECT_RING_SLOTS`)
- Path lines (ItemsAdder line textures) from center to confirmed aspects via `gui.path_lines`
- GUI item refs from `config.yml` `gui` section

**Staff can edit after:** aspect `display` items, `grid_color`, `gui` button and path line refs.

**Test checklist:**

- [x] Grid shows control column + 7-wide area + center paper
- [x] Paths appear when aspects confirm (grid_color maps to path line item)
- [x] Rejected aspects gray out on grid ring
- [x] Experiment / confirm / scrap clicks use new slots (0, 1, 45)

---

## Batch 10 - Results, hooks, migration (complete)

**Deliverables:**

- Weighted result selection from project `results` (`ResultPicker`)
- Item grant via `ItemRef` (`ResultGrantor`); inventory full blocks completion (superseded by Batch 13 world spawn)
- `ResearchCompleteEvent` Codex unlock stub + console log
- `external_modifiers` hook (`ExternalModifiers`, empty default)
- Project `enabled` flag for production pool control
- [`MIGRATION_FROM_ADVANCEDRESEARCH.md`](MIGRATION_FROM_ADVANCEDRESEARCH.md)

**Staff can edit after:** project `results`, `external_modifiers`, project `enabled`.

**Test checklist:**

- [x] Completion grants weighted result item
- [x] Inventory full shows message, project stays active until space available
- [x] `ResearchCompleteEvent` fires; Codex stub logged

---

## Batch 11 - Vertical station GUI

### Batch 11a - Layout foundation (complete)

**Deliverables:**

- Vertical 54-slot layout in `GridLayout` (scrap 0, MP 2, product 10, aspects col 3, experiment 28, confirm 36, previews 37-38)
- `InventoryManager.populateMain()` uses new slots; aspects still in yaml order (shuffle in 11b)
- Confirm wave radiates from product slot 10; skips aspect column
- `gui.locked_aspect` + `aspect.locked_name` / `aspect.locked_lore` prepared for 11c

**Test checklist:**

- [ ] Fixed slots match design on open
- [ ] Scrap, experiment, confirm, previews work
- [ ] Gray filler on unused slots
- [ ] `/research reload` safe with GUI open

### Batch 11b - Shuffle + persistence (complete)

**Deliverables:**

- `aspectSlotOrder` on `ActiveProject`, persisted in station JSON as `aspect_slot_order`
- `AspectSlotOrder` utility: shuffle on start, reconcile on load for legacy saves
- `InventoryManager` renders aspects at shuffled column positions; hidden slots show lock icon until reveal (11c)
- Projects with more than 6 aspects fail validation at load

**Test checklist:**

- [ ] Two lecterns same project can show different aspect row order
- [ ] Order survives server restart
- [ ] Legacy station files without `aspect_slot_order` get shuffled order on load

### Batch 11c - Locked hidden slots (complete)

**Deliverables:**

- `buildLockedAspectItem()` using `gui.locked_aspect` and `aspect.locked_name` / `aspect.locked_lore`
- Hidden aspect slots show lock until `RevealHelper` visibility threshold; then real aspect icon at same shuffled row
- Wave animation skips lock items via aspect column skip + `ItemRef.matches` guard

**Test checklist:**

- [ ] Example codex: arcanum shows lock until 2 aspects confirmed
- [ ] Visible aspects show real icons at shuffled positions from project start
- [ ] Lock slots not overwritten by filler or wave pulse

### Batch 11d - Docs polish (complete)

**Deliverables:**

- [`SYSTEM.md`](SYSTEM.md): vertical slot map, shuffle, locks; removed path lines and stale batch references
- [`ARCHITECTURE.md`](ARCHITECTURE.md): removed `GridPaths`; added `AspectSlotOrder`, batch 11 status, `aspect_slot_order` persistence
- [`IMPLEMENTATION_BATCHES.md`](IMPLEMENTATION_BATCHES.md): batch 9 superseded note, dependency graph, handoff section
- [`MIGRATION_FROM_ADVANCEDRESEARCH.md`](MIGRATION_FROM_ADVANCEDRESEARCH.md): vertical GUI description

**Test checklist:**

- [x] No stale `GridPaths`, `path_lines`, `ASPECT_RING`, or `7x7` references in staff-facing docs
- [x] Slot numbers in SYSTEM.md match `GridLayout` constants

---

## Batch 12 - Aspect row progress UI

### Batch 12a - Aspect rows + progress panes (complete)

**Deliverables:**

- Always 6 aspect rows (lock col 3 + 5 progress panes cols 4-8 per row)
- Random decoy rows for projects with fewer than 6 aspects
- `AspectProgressLayout` pane threshold math
- `AspectSlotOrder.buildRowAssignments` / 6-entry `aspect_slot_order` with empty decoy slots
- Yellow `???` progress panes driven by aspect points (`gui.aspect_progress_unknown`)
- Wave/filler skip all aspect row slots

**Test checklist:**

- [ ] 4-aspect project: 6 lock rows; 2 decoy rows never show yellow progress
- [ ] Experiment points fill correct row's yellow panes at threshold steps
- [ ] 3-needed aspect: only 3 active panes (thresholds 1/2/3)
- [ ] Legacy `aspect_slot_order` migrates to 6-row format on load
- [ ] Confirm wave does not overwrite locks or progress panes

### Batch 12b - Discovery + confirm gate (complete)

**Deliverables:**

- `aspect_discovery` / `aspect_confirm` config with intelligence scaling (`DiscoveryScaling`)
- Identity reveal: aspect display item + orange panes with shared name/lore
- Points-% confirm gate; removed `base_confirm_tests` / `base_reject_tests`
- Off-recipe experiment aspects ignored (no points, no reject)
- Removed `hidden` / `reveal_when_confirmed_count` from project yaml and `ProjectDef`
- Version **0.12.0**

**Test checklist:**

- [ ] 10-required aspect: yellow until reveal %; orange row at threshold; confirm at confirm %
- [ ] High intelligence reveals/confirms earlier than low intelligence
- [ ] Off-recipe aspect from experiment: no row change
- [ ] `required_points: 0` discovers/confirms on first point
- [ ] Product reveal and full completion still work

### Batch 12c - Reveal tuning + wave colors (complete)

**Deliverables:**

- Pane-based identity reveal and confirm thresholds (aligned with progress panes)
- Rotating `gui.wave_pulse_colors` on each experiment confirm
- `required_points` must be > 0; metal removed from example codex
- Version **0.12.1**

---

## Batch 13 - Result templates + world spawn (complete)

**Deliverables:**

- `templates/` folder with `TemplateLoader` and `ResultTemplateDef`
- Project results accept `t.template_id`; nested templates with cycle detection at load
- `ResultRef` + `ResultPicker.pickResolved` resolves templates to concrete item refs
- `ResultSpawnEffects` pops result above lectern (kick velocity, crit trail, burst particles)
- `station.result_spawn` config; removed inventory grant / inventory-full completion gate
- Example `templates/runestones.yml` + `projects/example_staff_runestone.yml`
- Version **0.13.0**

**Staff can edit after:** template `outputs`, project `t.*` results, `station.result_spawn` tuning.

**Test checklist:**

- [ ] `/research reload` loads templates before projects
- [ ] Cycle in nested `t.*` logs severe and skips bad entry
- [ ] `example_staff_runestone` completes with random rune from `t.runestones`
- [ ] `ResearchCompleteEvent` carries resolved `m.*` ref
- [ ] Result entity spawns above lectern with crit trail; player picks up from ground
- [ ] `example_decarian_codex` also world-spawns result

---

## Batch 14 - `gui.yml` + remove Research Planner (complete)

**Spec:** [`RESEARCH_REDESIGN.md`](RESEARCH_REDESIGN.md) sections `gui.yml`, Research Planner removed.

**Deliverables:**

- New `gui.yml` with `items.*` refs and `colors.*` hex tokens (default copied on first run)
- `GuiLoader` + `GuiCache`; remove `gui` section from `config.yml`
- Update `validateGuiItemRefs()` to read from `gui.yml`
- **Delete Research Planner:** remove `planner/` resources, `PlannerLoader`, `PlannerGuiState`, `PlannerDef`, planner GUI/handlers
- Lectern start opens main GUI directly (no planner)
- Remove planner keys from `messages.yml`
- Version **0.14.0**

**Staff can edit after:** `gui.yml` icons and colour tokens.

**Test checklist:**

- [x] `/research reload` loads `gui.yml`; missing file logs severe
- [x] Invalid item ref in `gui.yml` logs on reload
- [x] Right-click lectern with valid start item opens main research GUI (never planner)
- [x] No `planner/` folder created on fresh install
- [x] Existing in-progress stations still load (still on old project model until batch 18)

---

## Batch 15 - Progress UI: 5 panes, point cap, pulse wave (complete)

**Spec:** [`RESEARCH_REDESIGN.md`](RESEARCH_REDESIGN.md) Progress panes, Confirm wave, Point cap.

**Deliverables:**

- `AspectProgressLayout`: always 5 active panes when `required_points > 0`
- Retune default `aspect_discovery` / `aspect_confirm` in `config.yml` if low point totals reveal too early (document chosen defaults in SYSTEM.md)
- Cap `addAspectPoints` at `required_points`; no 4/3 display
- Per-aspect `pulse_color` on aspects (replace unused `grid_color` or alias it); fallback `gui.yml` `items.wave_pulse_default`
- Pulse colour selection: primary aspect if it gained recipe points, else secondary, else default
- Wave animation: trailing ring reverts to gray filler (pulse, not sticky); remove `wave_pulse_colors` roster
- Version **0.15.0**

**Staff can edit after:** `pulse_color` per aspect, `wave_pulse_default`, discovery thresholds.

**Test checklist:**

- [x] Aspect with `required_points: 3` fills all 5 panes at 3/3
- [x] Points stop at required max; further confirms do not increase stored points
- [x] Confirm dynamite (destruction primary): wave uses red (or configured `pulse_color`)
- [x] Off-recipe confirm: wave uses default colour; slots revert to gray behind pulse front
- [x] Reveal/confirm thresholds still feel correct on 3-, 6-, and 10-point aspects

---

## Batch 16 - GUI hex styling (complete)

**Spec:** [`RESEARCH_REDESIGN.md`](RESEARCH_REDESIGN.md) GUI hex styling.

**Deliverables:**

- `GuiText` helper applies `StringFormatter.formatHex` using `gui.yml` `colors.*`
- `InventoryManager` (and scrap confirm sub-GUI) use palette for display names/lore
- Aspect row labels, MP item, confirm button, progress `???` text, wave pulse marker, filler panes
- **Chat messages unchanged** (`messages.yml` still uses § codes only)
- Version **0.16.0**

**Test checklist:**

- [x] GUI labels show hex colours in client; chat confirm lines unchanged
- [x] Missing colour token falls back safely (white `#ffffff`)
- [x] No em dash (U+2014) in new player-facing GUI strings

---

## Batch 17 - Aspect-centric experiment items (complete)

**Spec:** [`RESEARCH_REDESIGN.md`](RESEARCH_REDESIGN.md) Aspect catalog.

**Deliverables:**

- `AspectDef` + `AspectLoader`: `sounds`, `primary_items`, `secondary_items`
- `AspectItemRegistry` reverse index; duplicate same-role registration logs conflict, last wins
- `ExperimentItemLoader` removed; `findByItemStack` via `AspectItemRegistry`
- `config.yml` `experiment.primary_points` / `experiment.secondary_points`
- `experiment-items/` removed; example content merged into `aspects/elements.yml`
- Preview, confirm sounds, and point application use registry + global defaults
- Version **0.17.0**

**Staff can edit after:** aspect item lists and sounds only (no per-item yaml files).

**Deploy:** delete server `plugins/Research/experiment-items/` after updating jar.

**Test checklist:**

- [x] Items in `primary_items` grant primary aspect + default points on confirm
- [x] Same item can be secondary on another aspect
- [x] Duplicate primary on two aspects: reload logs conflict; last aspect wins
- [x] Unknown item in experiment slot still blocked with existing message
- [x] `/research reload` rebuilds index without restart

---

## Batch 18 - Output-centric research model (complete)

**Spec:** [`RESEARCH_REDESIGN.md`](RESEARCH_REDESIGN.md) Inputs, Outputs, persistence, Common vs rare.

**Deliverables:**

- `OutputLoader` + `OutputDef`; `InputLoader` + `InputDef`
- `OutputPicker.roll` + `ResultResolver.resolve` at station start; no player bias
- `ActiveProject` stores `inputId`, `resolvedOutputId`, `resolvedResultRef`
- `StationSaveData` / Gson persistence for new fields; no legacy migration
- Runtime reads aspects, displays, completion from rolled output
- Completion spawns pre-resolved result; `ResultPicker` removed
- `projects/` removed; `inputs/` + `outputs/` examples (codex, staff runestone, rare fragment)
- `InputMatcher` replaces `ProjectPicker`; `ResearchCompleteEvent` adds `inputId` / `outputId`
- Version **1.0.0**

**Staff action on deploy:** Delete server folders `projects/`, `experiment-items/`, `planner/`; clear stale station JSON; author `inputs/` and `outputs/`.

**Test checklist:**

- [x] Named paper input with one output: result known from start; aspects match output yaml
- [x] Rare input with multiple outputs: output rolled at start, persisted across relog/reload
- [x] Mid-research `/research reload` does not re-roll output
- [x] Template result (`t.*`) resolved at start; completion spawns same item
- [x] Product reveal uses rolled output's `product_display` / thresholds
- [x] `ResearchCompleteEvent` fires with resolved item ref
- [x] Invalid output id in input yaml fails reload

---

## Dependency graph

```text
Batch 2 -> Batch 3 -> Batch 4 -> Batch 5 -> Batch 6
Batch 6 -> Batch 7 -> Batch 10 (results)
Batch 7 -> Batch 9 (ring grid) -> Batch 11 (vertical GUI) -> Batch 12 (aspect rows, discovery)
Batch 13 (templates + world spawn)
Batch 14 (gui.yml, planner removal) -> Batch 15 (progress + wave) -> Batch 16 (hex GUI)
Batch 15 -> Batch 17 (aspect item lists; pulse_color already on aspects from 15)
Batch 17 -> Batch 18 (outputs/inputs; requires aspect index from 17)
```

**Note:** Batch 8 (Research Planner) is **superseded and removed** in batch 14.

## Handoff to content staff

After **batch 18**, staff author `aspects/`, `inputs/`, `outputs/`, and `templates/` only. See [`RESEARCH_REDESIGN.md`](RESEARCH_REDESIGN.md) and [`SYSTEM.md`](SYSTEM.md).
