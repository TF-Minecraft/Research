# Research

> Run experiments at research stations to uncover hidden recipes and discoveries for TF-Minecraft characters.

Research turns lecterns into research stations. A player starts a project with a
configured research item, then tests real items as experiments to work out which hidden
aspects the project needs. Each experiment spends character focus, and a finished
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
- **Ordinary lectern use** — lecterns without an active project still accept and
  open written books and books and quills.

Originally created by [Drefvelin](https://github.com/Drefvelin).

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/Research/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Tests and coverage

Run `mvn clean verify` with Java 21 after preparing the pinned private and shared
dependencies using the [build dependency guide](https://github.com/TF-Minecraft/Docs/blob/main/PIPELINES.md#build-dependencies).
JUnit 5, Mockito, and MockBukkit cover project progress, menus, item handling,
persistence, configuration, and lifecycle behaviour. Surefire test results are
in `target/surefire-reports/`. The build enforces **100% executable-line coverage** across
all production Java classes, with no coverage exclusions. JaCoCo's HTML and XML
reports are written to `target/site/jacoco/` and uploaded by the Build workflow.
Branch and instruction coverage are reported separately; the enforced threshold
is line coverage. Build and maintenance scripts are outside this runtime-code
metric; live Focus, item-plugin, and MMOCore integrations need server testing.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and pre-existing
material retain their own licenses.
