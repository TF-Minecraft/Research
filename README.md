# Research

> Run experiments at research stations to uncover hidden recipes and discoveries for TF-Minecraft characters.

Research turns lecterns into research stations. A player starts a project with a
configured research item, then tests real items as experiments to work out which hidden
aspects the project needs. Each experiment costs mental points, and a finished
project produces its result item at the station.

## Features

- **Configurable projects** — each configured start item rolls one of its weighted
  outputs, and the output's result is fixed when the project begins.
- **Experiment-driven discovery** — items carry primary and secondary aspects that
  add points to the project, while unneeded aspects are eventually rejected.
- **Hidden recipes** — required aspects sit among decoy rows in a shuffled station
  menu, and both their identity and the product are revealed as research progresses.
- **Character focus** — experiments spend RPCharacters focus points, and a configurable
  MMOCore attribute lowers the reveal and confirmation thresholds.
- **Weighted result pools** — outputs grant a fixed item or roll from nested,
  weighted result templates using TLibs item paths.
- **Plugin integration** — completed projects fire a `ResearchCompleteEvent` so
  other plugins can react to discoveries.
- **Persistent stations** — ownership, aspect progress, tested items, and the rolled
  result survive server restarts.

Originally created by [Drefvelin](https://github.com/Drefvelin).

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/Research/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Focus dependency

This version requires RPCharacters 2.1.0 or later for character focus; Research no
longer depends on TFMCCore. Deploy the matching RPCharacters, TFMCCore ownership
handoff build (if installed), and Magic/Research consumers together, then restart.
An older installed TFMCCore makes RPCharacters stand down focus to avoid duplicate
writers, so experiments cannot spend focus until the plugin set is compatible.
Existing Research player files remain available to RPCharacters for legacy focus
imports; this change does not delete or rewrite them.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and pre-existing
material retain their own licenses.
