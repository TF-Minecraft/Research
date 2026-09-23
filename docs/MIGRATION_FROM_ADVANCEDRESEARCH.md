# Migration from AdvancedResearch

This document covers moving from **AdvancedResearch** (`me.Plugins.AdvancedResearch`) to **Research** (`net.tfminecraft.research`). Do not run both plugins on the same server lecterns at the same time.

## Summary

| AdvancedResearch | Research |
|------------------|----------|
| `Input` projects in single config | `inputs/*.yml` + `outputs/*.yml` |
| Element strings on station | Aspect ids from `aspects/` registry |
| `ResearchNote` slots | Experiment item + aspect point tracking |
| Result string on `RStation` | Output `result` + `t.*` templates resolved at station start |
| Station data in AdvancedResearch DB | `plugins/Research/data/stations/*.json` |

## Before cutover

1. **Disable AdvancedResearch** on the test server (remove from `plugins/` or disable in plugin manager).
2. **Install Research** `research-0.12.0.jar` (or current version).
3. **Author yaml** for aspects, inputs, outputs, and templates (see `docs/SYSTEM.md`).
4. Delete legacy folders on deploy: `projects/`, `experiment-items/`, `planner/`. Clear stale `data/stations/*.json` when upgrading to 1.0.0.
5. Set `enabled: false` on example content until production definitions are ready.

## Station state

AdvancedResearch stores per-station:

- Owner, location, current input project, element progress, completion flag, result string

Research stores per-station in `data/stations/<world>_<x>_<y>_<z>.json`:

- Owner uuid, `input_id`, `resolved_output_id`, `resolved_result_ref`, aspect states/points, tested items, product revealed, completed, `aspect_slot_order`

There is **no automatic import** from AdvancedResearch station files or legacy Research `project_id` saves. Players with active projects will need to restart on Research stations after cutover.

## Content mapping

### Inputs and outputs

- AdvancedResearch `inputs.<key>` maps to an `inputs/` entry (start item) plus one or more `outputs/` recipes.
- `neededElements` / element lists -> `aspects` with `required_points` on the rolled output.
- Hidden elements -> aspect identity reveal is runtime via `aspect_discovery`.
- Result list -> split: input `outputs` pool picks output id at start; chosen output's `result.item` or `result.template` is resolved at start.

### Experiment items

AdvancedResearch used note items tied to elements. Research uses aspect `primary_items` / `secondary_items` lists that grant points when confirmed in the experiment slot.

### GUI

Research uses a vertical 54-slot (9x6) station GUI: six aspect rows (lock + progress panes), shuffled assignments with decoy rows, intelligence-scaled identity reveal, and orange progress panes. Off-recipe experiment aspects are ignored.

## Codex integration

On project completion, Research:

1. Spawns the pre-resolved result item via `ItemRef`.
2. Fires `ResearchCompleteEvent` with `inputId`, `outputId`, and `resultItemRef`.
3. Logs a Codex stub line to console for integration testing.

Codex plugins should listen to `net.tfminecraft.research.event.ResearchCompleteEvent` and unlock entries from `resultItemRef`. Use `getOutputId()` (or deprecated `getProjectId()`) for output-based unlocks.

## Mental Points

AdvancedResearch did not use Mental Points. Research uses MP for experiments and regen from `config.yml`. Tune `mental_points` and MMOCore attribute scaling before go-live.

## Rollback

If you must roll back:

1. Stop server.
2. Remove Research jar.
3. Restore AdvancedResearch jar and its data folder.
4. Research `plugins/Research/data/` can remain; it is not read by AdvancedResearch.

## Production checklist

- [ ] All live inputs and outputs authored with valid refs
- [ ] Example content `enabled: false` if not used
- [ ] Aspect item lists match server item ids (ItemsAdder / MMOItems)
- [ ] `/research reload` passes with no severe errors
- [ ] One full playthrough: start -> experiment -> confirm -> complete -> item grant
- [ ] Codex listener (if any) receives `ResearchCompleteEvent`
