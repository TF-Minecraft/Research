# Research Plugin - Architecture

Compact layout aligned with Gathering, Dowsing, and AdvancedCrafting patterns. Gameplay logic lives in managers, not micro-services.

> **Redesign (batches 14-18):** See [`RESEARCH_REDESIGN.md`](RESEARCH_REDESIGN.md). Planner removed; `gui.yml` added; `outputs/` + `inputs/` replace `projects/`; aspect item lists replace `experiment-items/`.

## Package map

```text
net.tfminecraft.research/
  Research.java           # enable, loadConfigs, reloadAll, folders
  Cache.java              # scalars from config.yml
  Messages.java           # player strings from messages.yml
  ResourceBootstrap.java  # first-run resource copy

  loader/                 # LoaderInterface + static registries
  registry/               # AspectItemRegistry (batch 17+)
  model/                  # yaml definition objects
  manager/                # PlayerManager (Focus facade); ResearchManager, InventoryManager
  database/               # station persistence (batch 4+)
  command/                # CommandManager
  util/                   # ItemRef, YamlFolder, GridLayout, AspectSlotOrder, OutputPicker, ResultResolver, InputMatcher, ResultSpawnEffects, ExternalModifiers
  event/                  # ResearchCompleteEvent (Codex hook)
```

## Data flow

1. **Config load** (`Research.loadConfigs`) runs loaders in order:
   - `ConfigLoader` -> `Cache`
   - `MessagesLoader` -> `Messages`
   - `GuiLoader` -> GUI item refs + colour tokens (batch 14+)
   - `AspectLoader` -> aspect registry (+ item index batch 17+)
   - `TemplateLoader` -> result template registry (validates nested `t.*` refs)
   - `OutputLoader` -> output registry (batch 18+; replaces `ProjectLoader`)
   - `InputLoader` -> input registry (batch 18+)

   **Removed batch 14:** `PlannerLoader`  
   **Removed batch 17:** `ExperimentItemLoader` (replaced by `AspectItemRegistry`)  
   **Removed batch 18:** `ProjectLoader`, `ProjectPicker`, `ResultPicker`

2. **Runtime**:
   - `PlayerManager` - reads/spends character Focus via TFMCCore (no local MP store)
   - `MmoAttributes` - read configured MMOCore stat ids from `Cache`
   - `ResearchManager` - lectern, experiments, confirm/reject, reveal, complete (batch 4+)
   - `InventoryManager` - GUI render only (batch 4+)
   - `StationStore` - per-station JSON in `data/stations/` (owner, rolled output, aspect state, tested items)

## Registries vs Cache

| Source | Storage | Access |
|--------|---------|--------|
| `config.yml` scalars | `Cache` static fields | `Cache.mentalPointsMax`, etc. |
| `gui.yml` | `GuiLoader` / `GuiCache` | GUI item refs, hex colour tokens |
| `aspects/`, `outputs/`, `inputs/` | Loader static maps | `AspectLoader.getById(id)` |
| Player-facing text | `Messages` | `Messages.get("reload.success")` |

## Item references

Yaml: `vanilla.MATERIAL`, `m.TYPE.ID`, `ia.namespace:id`

[`ItemRef`](src/main/java/net/tfminecraft/research/util/ItemRef.java) normalizes `vanilla.` to TLibs `v.` for builds. Matching uses Material, MMOItems NBT, or ItemsAdder `CustomStack`.

## YAML folders

Loaded via [`YamlFolder.listYamlFiles`](src/main/java/net/tfminecraft/research/util/YamlFolder.java) - same pattern as VehicleFramework `vehicles/` and AdvancedCrafting `recipes/`.

## What not to add

- Separate service class per verb (`ExperimentService`, etc.)
- One Java file per listener event
- Hardcoded attribute names or item ids (use `Cache` and yaml)
- Player-biased output selection (planner removed)

## Batch status

- Batch 1: scaffold
- Batch 2: loaders + `/research reload`
- Batch 3: Mental Points + player persistence (`PlayerManager`, `PlayerStore`, `MmoAttributes`)
- Batch 4: Station shell + placeholder GUI (`ResearchManager`, `InventoryManager`, `StationStore`)
- Batch 5: Experiment logic (item slot, MP spend, aspect point tracking)
- Batch 6: Confirm/reject thresholds, aspect states, completion detection
- Batch 7: Product and hidden aspect reveals (`RevealHelper`, `DiscoveryScaling`)
- Batch 8: Research Planner (**removed batch 14**)
- Batch 9: Ring-grid station GUI (superseded by batch 11 vertical layout; path lines removed)
- Batch 10: Results, Codex event stub, migration docs (`ResearchCompleteEvent`)
- Batch 12: Aspect row progress UI with discovery reveal and points-% confirm gate
- Batch 13: Result templates (`t.*`), world result spawn (`ResultSpawnEffects`, `TemplateLoader`)
- **Batch 14-18:** Redesign - see [`IMPLEMENTATION_BATCHES.md`](IMPLEMENTATION_BATCHES.md) and [`RESEARCH_REDESIGN.md`](RESEARCH_REDESIGN.md)
