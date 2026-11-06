# Zoom Out Review — NGE Terminal Interface for Lily Launcher (v4)

**Target:** `docs/designs/nge-terminal-interface-v3.md` (312 lines, semantic boundary line 237)
**Mode:** REVIEW
**Standard:** `resources/document_standards/zoom-out-standard.md`
**Date:** 2026-11-06
**Verdict:** NEEDS SIMPLIFICATION — 1 Blocker, 4 Majors, 6 Nits

## Summary

The design correctly identifies the root problem (aesthetic + no speed awareness) and retains upstream features. The architecture, however, conflates the HTML mockup (SVG/CSS) with the native Android app (View + Canvas + baked images) — the app cannot use SVG `<polygon>` or CSS variables. Several speculative features (accelerometer early-transition, 6-way palette matrix, mockup variant retention) and one architectural gap (hex-grid rendering path in native code) need resolution before implementation. No new utilities are proposed that duplicate existing code; the existing theme/prefs/sheet infrastructure has partial reuse but is not explicitly mapped.

---

## Findings

### 1. [Blocker] Altitude Drop / Platform Mismatch — SVG/CSS architecture does not apply to the native app
**Affected scope:** Lines 28, 47, 152–188, 230; SVG Hex Grid section (lines 182–188)
**Simplicity Rationale:** The design states "Use SVG for hexagonal button grids to reduce rendering overhead" and specifies SVG `<polygon>` elements with `fill`/`stroke` and CSS variables (`--bg`, `--text`, `--accent`) in `applyPalette()`. The app is native Android (Kotlin, View + Canvas, baked PNG images per `launcher/app/src/main/assets/ui/layout.json` — 1024×600, pre-rendered slots). Native `View`/`Canvas` cannot consume SVG polygons or CSS variables. The SVG/CSS description is accurate for the HTML mockup (`mockup/nge-terminal-deepseek-v4.html`) but the design's "App Architecture (Kotlin)" section (lines 172–180) never resolves *how* hexagonal buttons are rendered in native code. Q5 (line 48: "app uses baked images + Canvas") contradicts the SVG claim. This is an altitude drop into weeds that describes the wrong platform and leaves the actual implementation path undefined. **Fix:** State explicitly that SVG/CSS applies to the mockup only; define the native rendering path (Canvas path drawing of hexagon shapes, or re-baked image variants per palette zone — lines 175–177 already hint at this). Do not claim SVG reduces rendering overhead in the app.

### 2. [Major] YAGNI / Speculative Feature — Accelerometer early-transition detection
**Affected scope:** Lines 46 (Q3), 97, 109, 214–218, 231, 305
**Simplicity Rationale:** GPS `Location.getSpeed()` at ~1 Hz is stated to be "reliable" (Alternative C, line 217: "GPS speed is reliable"). The accelerometer supplement exists to "detect acceleration > threshold for early palette transition before GPS fix updates." No evidence is presented that a ~1 s palette lag is a user-visible problem, nor any threshold value, sensor access path (mc32x0 on TWI2 is cited but no `SensorManager` usage exists in `launcher/app/src/main/java/com/lily/launcher/` — zero matches for `SensorManager`/`TYPE_ACCELEROMETER`), nor a fallback if the sensor is absent. GPS-denied fallback ("last known speed or 0", line 231) is already specified, making the accelerometer redundant. This is speculative: implement GPS-only first, measure actual transition lag on device, add accelerometer only if lag is proven unacceptable. Alternative C's own reasoning ("Not rejected — primary approach. Accelerometer is supplement.") confirms GPS-only is viable.

### 3. [Major] Premature Complexity — 6-way palette matrix (3 speed zones × 2 day/night modes)
**Affected scope:** Lines 15, 31, 56, 86, 233; Non-Functional Requirements (line 233)
**Simplicity Rationale:** Speed zones (normal/caution/emergency) and day/night (17:00/05:00 time-based) must "work together" (line 233: "Day/night overrides color when in normal speed zone"). This implies up to 6 distinct palette states. The design never defines the precedence rules for caution/emergency + night, nor how many baked image variants are required (lines 175–177: "color tinting of baked images, or re-bake with palette variants" — if re-baking, 6× the image assets; if tinting, the performance cost on a 22 MB device is unbudgeted). The existing codebase has a simple two-state theme (`Car.themeName()`: night → `"night"`, else light/dark from prefs; `Ui.kt` `setTheme()` swaps theme vars). Adding a parallel 6-state matrix is a significant expansion of the existing simple mechanism without a stated reason why 3 zones × 2 modes cannot be simplified (e.g., palette zones only, with day/night affecting brightness rather than full hue). **Fix:** Define explicit precedence (speed zone wins; day/night modulates only in normal zone — already stated) and state the rendering cost model: tinting vs re-baking, with APK size impact.

### 4. [Major] YAGNI / Combinatorial Bloat — Mockup variant retention (9 HTML files)
**Affected scope:** Lines 15, 135, 292–296; Additional Resources
**Simplicity Rationale:** The repo retains 9 mockup files: `nge-terminal.html` (v3 baseline), `nge-terminal-deepseek.html`, `nge-terminal-glm.html`, plus v3 variants (`-deepseek-v3`, `-glm-v3`, `-crouton-v3`) and v4 variants (`-deepseek-v4`, `-glm-v4`, `-crouton-v4`). The design names `nge-terminal-deepseek-v4.html` (84 KB) as canonical but retains "GLM and Crouton v4 variants … for comparison" and v3 files as baselines. Each variant is a full simulator with independent state/palette logic — every design change must now be reasoned about in 3–9 files. No reader is identified for the retained variants (no A/B decision is pending). **Fix:** Archive non-canonical variants to a `mockup/archive/` directory or delete; keep one canonical file + the v3 baseline.

### 5. [Major] Architectural Dead-End / Incomplete Spec — Day/night toggle vs existing headlight theme
**Affected scope:** Lines 15, 26, 31, 86, 179, 233; `Car.kt` lines 31, 86–88, 121–122, 496
**Simplicity Rationale:** The design replaces headlight-based night mode (`mcu_light_state` via `Settings.System`, already wired in `Car.kt`: `night = sys(K_LIGHT, 0) != 0`, `applyPanel()`) with time-based (17:00/05:00) AUTO/MANUAL, "keep headlight as override" (line 179). This creates three competing sources of truth for theme: (a) existing `mcu_light_state` polling in `Car.kt`, (b) new time-based clock, (c) MANUAL user override. The design does not state precedence among these, nor whether `mcu_light_state` polling is removed (breaking the existing `applyPanel()` trigger) or retained as a parallel path. The existing prefs key `theme` (DEFAULTS `"auto"`) overlaps the new `dayNightMode` pref — two keys for one concept. The mockup's "simulated clock for drift" (line 166) is mockup-only simulator state with no app analogue defined. **Fix:** Define one decision function (e.g., `resolveTheme()`: MANUAL > headlight-override > time-based > auto) and reuse the existing `theme` pref key rather than adding `dayNightMode`.

### 6. [Nit] Altitude Drop (Weeds) — Pseudocode and mockup-internal details in architecture sections
**Affected scope:** Lines 100–129 (Speed Tracking / Palette System pseudocode), 137–170 (Mockup Architecture tree), 190–196 (MAGI clip-path)
**Simplicity Rationale:** `updateSpeedZone()` / `applyPalette()` pseudocode enumerating 8 discrete DOM/CSS updates (`toggle hazard stripes`, `update node diagram fills`, etc.) is mockup-level implementation detail. The 30-line file-tree (lines 137–170) describes HTML element nesting (32px status rail, 42px banner, 338px main…) — layout measurements belong in the mockup, not the architecture. The MAGI `clip-path: polygon(15% 0%, 85% 0%, 100% 100%, 0% 100%)` is CSS for the HTML file only. These sections should compress to: "Palette state machine with 3 zones; apply function updates all surface elements" and leave pixel-level layout to the canonical mockup. No app-level interface is obscured — this is presentation hygiene, not correctness.

### 7. [Nit] Internal Inconsistency — Touch target sizes (40px vs 66×76 vs 44px minimum)
**Affected scope:** Line 15 (hexes 66×76), line 188 (40px diameter), line 232 (≥44px), HANDOVER line 31 (hex 40px+, knob 70px, readout 32px)
**Simplicity Rationale:** Three different numbers appear: v4 header claims hexes 66×76, SVG section claims "40px diameter for comfortable touch (18mm thumb)", NFR claims "≥44px (WCAG 2.5.5)". At 160 dpi, 18 mm ≈ 86 px — none of the stated sizes meet 18 mm. The 40 px figure is below the stated 44 px minimum and contradicts the 66×76 figure. The design should state one authoritative target (recommend 66×76 per v4 header, noting 18 mm visual ≈ 44 px hit-area minimum at 160 dpi) and remove the stale 40 px line.

### 8. [Nit] YAGNI — Future Improvements list is unfiltered speculation
**Affected scope:** Lines 303–309
**Simplicity Rationale:** Speed logging (trip analysis), customizable palettes, GPS navigation integration, and speed-based volume/mute are listed without any user request, safety analysis, or problem statement. Speed-based audio muting and navigation display on a headunit are safety-relevant (driver distraction) and should never be presented as unexamined "future improvements." Either remove the section or mark each item as requiring a separate safety review. No action required beyond removing speculative items from a design doc whose goals are explicitly aesthetic + speed palette.

### 9. [Nit] Incomplete Alternatives Considered — Alternative C self-contradiction
**Affected scope:** Lines 214–218
**Simplicity Rationale:** Alternative C ("Use GPS Only") is titled as an alternative, then "Reason for Rejection: Not rejected — primary approach." This is contradictory wording. It should be marked "Accepted — primary approach" so the audit trail is clean.

### 10. [Nit] Premature Optimization Claim — SVG "lighter than Canvas for static elements"
**Affected scope:** Line 230
**Simplicity Rationale:** The performance claim ("SVG hex grid is lighter than Canvas for static elements") is unsubstantiated and, per Finding 1, applies to the HTML mockup, not the native app. In the native app, palette transitions require re-rendering or re-baking Canvas-drawn elements; the cost model is different. Remove the comparative claim or replace with the testable target (RAM < 25 MB, Home return < 200 ms — already stated in Verification Plan line 281).

### 11. [Nit] Context Sufficiency — No runtime-permission / denial handling for GPS
**Affected scope:** Lines 37, 274; `AndroidManifest.xml`; `HANDOVER.md` line 71
**Simplicity Rationale:** `ACCESS_FINE_LOCATION` is a dangerous permission on Android 6.0+ (API 23+): manifest declaration alone is insufficient — a runtime grant dialog is required, and denial is the default. The design's Non-Goals state "No new permissions beyond GPS access" (line 37), but the implementation plan (lines 271–274) lists only the manifest entry with no runtime request, no denial UX, and no statement of what the launcher does if permission is denied (the tunnel fallback "last known speed or 0" would become the permanent state). This is a context gap, not a complexity issue: one sentence stating who requests permission (system-granted installer? runtime check in `HomeActivity` on first run?) and the denied-state behavior suffices.

---

## Reuse Verification (Code Search Results)

| Proposed element | Existing codebase | Verdict |
|---|---|---|
| Theme state (`light`/`dark`/`night`), `Car.themeName()`, `Ui.setTheme()` | `Car.kt` lines 31, 86–88, 121–122; `Ui.kt` `setTheme()`/`themeVar()` | **Partial reuse opportunity.** Existing two-state (night/light) theme system via `Ui.kt` theme vars + `layout.json` is the natural host for palette zones. Design proposes a parallel system without mapping to `Ui.kt`. |
| Speed thresholds in SharedPreferences | `Car.kt` `prefs`, `DEFAULTS`, `SheetView.kt` generic pref editor | **Reuse OK.** Existing `Car.prefs` + `SheetView` pattern already supports adding threshold keys without new infrastructure. Design does not propose new prefs infra. |
| `mcu_light_state` / `mcu_backcar_state` polling | `Car.kt` `sys(K_LIGHT)`, `sys(K_BACKCAR)`, `applyPanel()` | **Must respect.** Day/night change must integrate with, not duplicate, the existing `applyPanel()` trigger (see Finding 5). |
| GPS `LocationManager` / `SensorManager` | Zero matches in `launcher/app/src/main/java/` | **New code, no wheel reinvented.** No existing location/sensor infrastructure to reuse. |
| Hex/SVG/MAGI rendering | `HomeView.kt`, `DeckView.kt`, `KnobView.kt` (Canvas, baked images); zero SVG code in app | **No reusable component.** The app has no vector/shape rendering; this is genuinely new (see Finding 1 for the rendering-path gap). |
| `ACCESS_FINE_LOCATION` | No manifest entry, no runtime request code | **New.** Only noted in HANDOVER.md line 71 and the design doc. |
| Mockup variants (9 HTML files) | `mockup/nge-terminal*.html` × 9 | **Maintenance burden.** See Finding 4. |

## Root Problem Focus

The design correctly focuses on the stated root problem: the user wants an NGE terminal aesthetic and speed-aware palette, with all upstream workflows retained (Non-Goals confirm no AIDL/broadcast/vendor changes). No symptom-patching detected. The core risk is not problem selection but the altitude mismatch between the HTML mockup and the native app implementation.

## Verdict Rationale

**1 Blocker** (platform mismatch — SVG/CSS describes the mockup, not the app; native rendering path undefined). **4 Majors** (accelerometer speculation, 6-way palette matrix without cost model, mockup variant bloat, day/night three-source conflict). **6 Nits** (weeds/pseudocode, touch-target inconsistency, speculative Future Improvements, Alternative C wording, SVG performance claim, GPS runtime-permission gap). The design is sound in intent and root-problem focus; the architecture sections need simplification and platform correction before implementation.
