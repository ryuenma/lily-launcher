# Consolidated Audit Report — Lily Launcher NGE Reskin

**Date:** 2026-11-07
**Repo:** `C:/Users/ryuen/Documents/Projects/Headunit/lily-launcher`
**Audits:** Ponytail (full repo), EGM Drift (design doc), Antislop (UI quality)

---

## 1. Blockers (must fix before release)

| ID | Source | Finding | Affected file(s) |
|---|---|---|---|
| B-01 | Ponytail P-01 | Release APK built before font commits → missing NotoSerifJP.ttf/Oswald.ttf → `createFromAsset` returns null → crash in `onDraw` (`Ui.latin`/`jp` fallback calls `createFromAsset` outside `runCatching`) | `Ui.kt`, build artifact |
| B-02 | Ponytail P-02 | Debug "SPD" button + 0–160 SeekBar ships unconditionally (overlaps speed ladder) | `HomeActivity.kt` |
| B-03 | Ponytail P-03 | 22MB/3.3MB claims stale; 12.9MB font loaded as 8 separate `Font` objects (latin/jp caches never share) | `Ui.kt`, design doc |

## 2. Fixes (should fix)

| ID | Source | Finding | Affected file(s) |
|---|---|---|---|
| F-01 | Ponytail | `Car.setSpeed()` calls `changed()` unconditionally (dead `if` above) → full 1024×600 Canvas redraw at GPS rate even when speed unchanged | `Car.kt` |
| F-02 | Ponytail | `Nge.inHex` shoulder math uses `hh/4` instead of `hh/2` → taps up to 2× outside hexagon register | `Nge.kt` |
| F-03 | Ponytail | Day/night rule implemented twice; clock path dead when MCU exists (contradicts CUJ 6, Setup footer, README) | `Car.kt` |
| F-04 | Ponytail | Thresholds: two editors with different rules, no `caution < emergency` check → CAUTION can be unreachable | `SheetView.kt`, `HomeActivity.kt` |
| F-05 | Ponytail | `logcat` reader respawns every 5s forever if `READ_LOGS` denied | `Car.kt` |
| F-06 | Ponytail | USB progress branch unreachable (`posKnown` is BT-only) | `HomeView.kt` |
| F-07 | Ponytail | Accent swatch shows orange while UI is green (stock accent `#FF5A1F` vs zoneColor green) | `SheetView.kt` |
| F-08 | Ponytail | `SheetView.close()` clears state before animating → fade never draws | `SheetView.kt` |
| F-09 | Ponytail | Long-press settings unreachable over keypad/stage (touch handling) | `HomeView.kt`, `Stage.kt` |
| F-10 | Ponytail | Entire baked-asset pipeline is dead (`Ui.image`, `preload*`, `setTheme`, `rect`, `text`, `TextSpec`, 288 PNGs, `layout.json`, `bake.mjs` baking wrong mockup) but `Ui` constructor and `SheetView` More list still hard-depend on `layout.json` | `Ui.kt`, `SheetView.kt` |
| F-11 | Ponytail | Zero tests; pure functions (`setFreq`, hysteresis, `inHex`, `parseCss`) are free to cover | `launcher/` tests |
| F-12 | Ponytail | Design doc claims dwell ≥1s, reverse overlay, keyboard nav, permission rationale, "no new dayNightMode key" — none exist in code | `nge-terminal-interface-v3.md` |
| F-13 | Ponytail | "15 tiles" in doc but lists 13; design brief is pre-decision (SVG, glow, Rajdhani) with leaked lines | `nge-terminal-interface-v3.md`, `design-brief-nge-terminal.md` |
| F-14 | Ponytail | README says themes follow headlights; app's Setup footer says clock; code does headlights | `README.md`, `Car.kt` |
| F-15 | Ponytail | Honeycomb is 1,380 hexes, not doc's ~240 | `HomeView.kt`, design doc |
| F-16 | EGM Drift | `Car.prefs` referenced in doc but only exists as instance `car.prefs` (lowercase) | design doc |
| F-17 | EGM Drift | `SettingsSheet` referenced but actual component is `SheetView` | design doc |
| F-18 | EGM Drift | Stale paths: `Car.kt`, `SheetView.kt`, `HomeActivity.kt` (missing `launcher/app/src/main/java/com/lily/launcher/` prefix) | design doc |

## 3. Watch (low priority)

| ID | Source | Finding |
|---|---|---|
| W-01 | Ponytail | `HomeActivity.preloadSheets()` still decodes ~50 unused sheet PNGs (file kept as-is) |
| W-02 | Ponytail | 289 legacy TE PNGs + `layout.json` still packaged (unused, ~4.5MB) |
| W-03 | Ponytail | Settings right column has one empty row slot |
| W-04 | Ponytail | `docs/architecture/lily-launcher.html`, `mockup/index.html` not read (listed in report) |
| W-05 | Ponytail | 9 individual EGM stage reports not read |
| W-06 | Antislop | No glow used (R-13 compliant) — PASS |
| W-07 | Antislop | Touch targets ≥44px (66×76) — PASS |
| W-08 | Antislop | No em dashes in UI text — PASS |
| W-09 | Antislop | Keyboard navigation: hex buttons not keyboard-navigable (R-32) — FAIL |
| W-10 | Antislop | No empty/loading/error states for data-driven UI (R-27) — FAIL |
| W-11 | Antislop | No focus indicators on hex buttons (R-32) — FAIL |

## 4. Duplicates removed (consolidated)

- P-01 (release APK missing fonts) ↔ design doc's "3.3MB APK" claim (already in F-13).
- P-03 (22MB/3.3MB stale) ↔ B-02 from EGM review (already decided: runtime resource usage is priority).
- F-10 (dead baked pipeline) ↔ M-07 from EGM review (compress weeds / remove dead code).
- F-12 (doc claims non-existent features) ↔ B-04/B-11 from EGM review.
- F-13 (doc says 15 tiles, lists 13) ↔ M-01 from EGM review (already fixed in code, doc partially updated).
- F-14 (README vs code contradiction) ↔ M-04 from EGM review.
- F-15 (1,380 hexes vs ~240) ↔ M-07 from EGM review.

## 5. Recommended priority order

1. **B-01** (release APK crashes — missing fonts) — fix before any release.
2. **B-02** (debug button ships) — remove before release.
3. **F-01** (`setSpeed` unconditional `changed()`) — performance fix, easy.
4. **F-02** (`inHex` shoulder math) — correctness fix, easy.
5. **F-03** (day/night rule duplicated) — logic fix.
6. **F-04** (threshold validation) — add `caution < emergency` check.
7. **F-10** (remove dead baked pipeline) — lean cleanup, larger effort.
8. **F-11** (add tests for pure functions) — quality.
9. **W-09/W-10/W-11** (keyboard nav, UI states, focus indicators) — accessibility.

---

*Consolidated from: ponytail_audit.md, drift_report.md, antislop SKILL.md rules. No source files modified during this audit.*
