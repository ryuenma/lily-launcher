# Drift report — mechanical pass

_Produced by `egm_drift.py`. Mechanical checks only: stale paths and symbols that do not exist. Semantic drift (missing features, incomplete models, logic shortcuts, architectural divergence) needs the Goldfish review — see the runbook._

## docs/designs/nge-terminal-interface-v3.md

- `Car.kt` is named in the doc but only exists as `launcher/app/src/main/java/com/lily/launcher/Car.kt` — the doc's path is stale
- `SheetView.kt` is named in the doc but only exists as `launcher/app/src/main/java/com/lily/launcher/SheetView.kt` — the doc's path is stale
- `launcher/app/src/main/.../Car.kt` is named in the doc but only exists as `launcher/app/src/main/java/com/lily/launcher/Car.kt` — the doc's path is stale
- `launcher/app/src/main/.../HomeActivity.kt` is named in the doc but only exists as `launcher/app/src/main/java/com/lily/launcher/HomeActivity.kt` — the doc's path is stale
- `launcher/app/src/main/.../SheetView.kt` is named in the doc but only exists as `launcher/app/src/main/java/com/lily/launcher/SheetView.kt` — the doc's path is stale
- `Car.prefs` is named in the doc but appears nowhere in the source
- `SettingsSheet` is named in the doc but appears nowhere in the source
<!-- checked: 10 path(s), 15 symbol(s) -->
