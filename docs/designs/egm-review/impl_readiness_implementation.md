# EGM Stage 9 — Implementation Readiness Review
**Target:** `docs/designs/nge-terminal-interface-v3.md` lines 237–312 (Detailed Implementation Plan + Verification Plan)  
**Standard:** `implementation-readiness-standard.md`  
**Mode:** REVIEW  
**Reviewer:** Implementation Readiness Goldfish (Pragmatic Tech Lead)  
**Date:** 2026-11-06

## Overall Verdict: FAIL — Not Implementation-Ready

A competent engineer unfamiliar with this codebase could not execute lines 237–312 and produce the correct changes on the first attempt. The section lists 7 files with one-line summary bullets, but specifies no precise logic changes, data structures, APIs, phases, or testable cases. The implementer must make virtually every design and architectural choice — violating the Zero-Decision Rule on every Kotlin file. The Verification Plan is a checklist of intentions, not a test plan, and its performance thresholds contradict the Non-Functional Requirements elsewhere in the doc.

**Severity counts:** 4 Blockers, 8 Majors, 4 Nits

## Standard Coverage

| Standard requirement | Status |
|---|---|
| Zero-Decision Rule | FAILED — implementer must decide architecture, APIs, error handling, and rendering strategy |
| Phasing & Checkpoints | FAILED — no phases defined; single undifferentiated file list |
| Target Scope | PARTIAL — 7 items named, but 4 use `...` paths and no explicit create/modify/delete inventory; missing layout/theme/tint files |
| Actionable Steps | FAILED — all "Proposed Changes" are one-line summaries with no signatures, types, logic, or ordering |
| Test Plan | FAILED — 8 generic bullets, no test cases, no edge cases, no test file locations, thresholds contradict NFR |

---

## Findings

### Blocker 1 — No phasing or checkpoints; work is not partitioned
- **Severity:** Blocker
- **Affected Scope:** Lines 237–286 (entire Detailed Implementation Plan); no phase structure anywhere
- **Finding & Guidance:** The Standard requires logical, incremental phases where each phase leaves the codebase stable, buildable, and testable. Lines 239–275 present one flat list of 7 files with no order, dependencies, or checkpoints. There is no way to land, build, or verify a subset (e.g., Manifest + Car.kt before HomeActivity.kt). To pass, the plan must define ordered phases — e.g., Phase 1: Manifest + permissions + pref scaffolding; Phase 2: Car.kt data model + migration; Phase 3: HomeActivity.kt speed zone state machine + palette application; Phase 4: SettingsSheet UI + wiring; Phase 5: Verification — each with build/test exit criteria.

### Blocker 2 — HomeActivity.kt action is unactionable (GPS, state machine, palette, day/night)
- **Severity:** Blocker
- **Affected Scope:** Lines 251–254 — `launcher/app/src/main/.../HomeActivity.kt` (Modify)
- **Finding & Guidance:** "Add GPS speed tracking (LocationManager), speed zone state machine, palette application, time-based day/night mode" requires the implementer to design everything: registration lifecycle (`onResume`/`onPause`, `requestLocationUpdates` params — provider, minTime, minDistance), permission flow (`requestPermissions`, `PERMISSION_GRANTED` handling, denial fallback, rationale), stale-fix handling (GPS older than N seconds, `getSpeed()` accuracy/`hasSpeed()`), zone state representation (enum name, threshold comparison — `>` vs `>=` at exactly 60/100 km/h), hysteresis/smoothing (raw vs smoothed speed, accelerometer early-transition per lines 109/231 if in scope), palette application mechanism for a View+Canvas app with baked images (tint? re-bake? theme overlay? — see Blocker 3), day/night application (theme change vs view tint, interaction with headlight `mcu_light_state` per lines 98/179), and thread model (LocationManager callbacks). Zero-Decision Rule violated. The plan must specify exact method names/signatures, lifecycle hooks, permission request sequence, zone type, and palette entry points in the existing Activity hierarchy.

### Blocker 3 — Rendering approach explicitly left to implementer (tint vs re-bake)
- **Severity:** Blocker
- **Affected Scope:** Lines 172–178 (App Architecture) as unresolved input to line 254 (`of baked images, or re-bake with palette variants`)
- **Finding & Guidance:** The plan's own words — "color tinting of baked images, or re-bake with palette variants" — delegate a core architectural decision to the implementer. Both options imply different file sets (tint: `ColorFilter`/layer-list XML/drawable modifiers per view; re-bake: new asset variants per zone × day/night, build-time pipeline, APK size impact vs 3.3 MB goal on line 32) and different performance characteristics (22 MB RAM / 175 ms Home goal). Combined with the mockup's CSS-variable palette (`applyPalette()`, lines 119–129) which has no native Android equivalent, the implementer must invent the translation. The plan must predetermine: tint vs re-bake, the exact Views/drawables tinted per zone, the color values per zone × day/night (the mockup's 5-color accent picker, line 168, is not usable as-is), and the mapping of each CSS variable to Android resources.

### Blocker 4 — Test Plan lists intentions, not test cases; thresholds contradict NFR
- **Severity:** Blocker
- **Affected Scope:** Lines 276–285 (Verification Plan), vs lines 32/230 (22 MB / 175 ms) and 231–232 (GPS-tunnel fallback, ≥44 px targets)
- **Finding & Guidance:** Verification says "Emulator test: verify all states render," "Speed simulation: use mockup simulator," "Performance: RAM < 25 MB, Home < 200 ms" — none is a test case. No Given/When/Then, no pass/fail assertions, no test file locations (`src/test`, `androidTest`), no unit vs instrumented vs manual designation, no edge cases (GPS denied/unavailable, stale fix, speed exactly 60/100, caution ≥ emergency thresholds, 17:00/05:00 boundaries, AUTO vs MANUAL persistence, reverse-camera priority over speed palette, headlight-vs-time conflict). The stated targets (25 MB / 200 ms) contradict the Non-Functional Requirements (22 MB / 175 ms) — the implementer must choose which bar to hit. The plan must enumerate specific tests with inputs, expected outputs, and the conflicting threshold resolved (one canonical set of numbers).

### Major 1 — Car.kt has no types, names, defaults, or persistence keys
- **Severity:** Major
- **Affected Scope:** Lines 256–259 — `launcher/app/src/main/.../Car.kt` (Modify)
- **Finding & Guidance:** "Add speed field, speed zone field, speed threshold preferences, dayNightMode preference" gives no field names/usability (e.g., `speedKmh: Float = 0f`, `zone: SpeedZone = NORMAL`, `cautionThresholdKmh: Int = 60`, `emergencyThresholdKmh: Int = 100`, `dayNightMode: DayNightMode = AUTO`), no SharedPreferences file/key names, no file format, no migration strategy for existing installs, no validation constraints (minimum/maximum thresholds, caution < emergency invariant), and no default values aligned to lines 21/45 (60/100 km/h) and line 165 (17:00/05:00). The implementer must design the persisted schema. Specify every field, type, default, pref key, and migration step.

### Major 2 — File paths are non-explicit (`...`); fresh engineer cannot locate files
- **Severity:** Major
- **Affected Scope:** Lines 251, 256, 261, 266 (`launcher/app/src/main/.../`)
- **Finding & Guidance:** The Standard's Target Scope requires an explicit list of files to create, modify, or delete. Four of seven entries use `...` placeholders, and `SettingsSheet` lacks a filename/extension (`.kt`? layout resource?), and `res/...` is a directory, not files. A fresh AI session cannot resolve these without searching the codebase. List every concrete path (e.g., `launcher/app/src/main/java/.../HomeActivity.kt`, `res/values/strings.xml`, `res/values/themes.xml`, `res/layout/settings_sheet.xml` if applicable), plus explicit create vs modify vs delete per file.

### Major 3 — SettingsSheet widget, validation, and persistence wiring undefined
- **Severity:** Major
- **Affected Scope:** Lines 261–264 — `SettingsSheet` (Modify)
- **Finding & Guidance:** "Add speed threshold configuration (caution km/h, emergency km/h), day/night mode toggle" leaves widget choice (EditText/SeekBar/Spinner), input range and validation (e.g., both 0–160 km/h per mockup slider, line 168; caution < emergency, rejection vs clamping), save semantics (immediate vs Apply/Cancel), layout file changes required for new rows/controls, and the binding mechanism from UI controls to `Car.kt` preferences unspecified. Specify widget types, layout XML touched, min/max values, validation rule outcomes, and the exact preference keys written on save.

### Major 4 — `res/...` entry has no resource names, files, or values
- **Severity:** Major
- **Affected Scope:** Lines 266–269 — `launcher/app/src/main/res/...` (Modify)
- **Finding & Guidance:** "Add GPS permission, speed-related string resources, time-based theme strings" names no files, no resource keys, no values. The implementer must decide which `values/*.xml` files to edit, what string keys (`speed_caution_label`? etc.), what theme attributes change per zone × day/night, and whether new color/dimen resources are created (and their exact hex/dp values — the mockup palette cannot be pasted into Android XML). Enumerate each resource file, key, and value, or explicitly point to the mockup's canonical values table.

### Major 5 — Manifest change is incomplete for location runtime behavior
- **Severity:** Major
- **Affected Scope:** Lines 271–274 — `AndroidManifest.xml` (Modify)
- **Finding & Guidance:** "Add `ACCESS_FINE_LOCATION` permission" is the most specific step in the plan, but still leaves decisions: FINE vs COARSE (speed via `getSpeed()` requires FINE — state this dependency), no `maxSdkVersion`, no `required` attribute handling, no runtime request flow (which Activity requests, when, and what happens on denial — HomeActivity plan is silent), and no interaction with the "no new permissions beyond GPS access" constraint (line 37) if rationale UI is needed. Specify the exact `<uses-permission>` line, the requesting component, and the denial path (fallback to last known speed / 0 per line 231 vs crash).

### Major 6 — Accelerometer scope ambiguous (in plan or in Future Improvements?)
- **Severity:** Major
- **Affected Scope:** Lines 97/109/231 (Accelerometer supplement, early transition) vs line 305 (Future Improvements: "Accelerometer integration") vs no accelerometer entry in lines 239–275
- **Finding & Guidance:** The Technical Plan and Resilience NFR describe accelerometer-based early transition detection as part of current behavior, but the Detailed Plan has no accelerometer file entry (no SensorManager code, no threshold value, no sensor availability fallback), and Future Improvements lists it as not-yet-done. An implementer cannot tell whether to implement it now or defer. Remove it from the current scope or add a file entry with the exact threshold, sensor type (`TYPE_ACCELEROMETER`), sampling rate, and fallback when `mc32x0` is absent.

### Major 7 — Hex grid, palette, and animation rendering have no native file mapping
- **Severity:** Major
- **Affected Scope:** Lines 182–197 (SVG hex grid, MAGI diagram) vs lines 239–275 (no layout/drawable/View entries)
- **Finding & Guidance:** The plan describes SVG `<polygon>` hex buttons and `clip-path` MAGI trapezoids — web techniques — while the app is View+Canvas with baked images (line 174). No Detailed Plan entry explains whether hexes are custom Views, ImageViews with baked PNGs, or SVG via ImageView, which layout/dimens resources hold the 66×76 hex / 104 knob / 116 odometer targets (lines 15/151), or how ticker marquee, hazard stripes (overlay, no layout shift), warning pulse, and spectrum animations are implemented natively (AnimationDrawable? PropertyAnimator? custom Canvas?). The mockup's CSS animation names (pulse, ticker marquee, marching stripes, spectrum, line 15) need a native mapping table per element. Add per-file entries for every layout, drawable, dimens, and custom View touched, or state explicitly that mockup animations are out of scope for the native app.

### Major 8 — Mockup and design-brief entries state status, not actions
- **Severity:** Major
- **Affected Scope:** Lines 241–249 — `mockup/nge-terminal-deepseek-v4.html`, `docs/design-brief-nge-terminal.md`
- **Finding & Guidance:** Entries read as status reports ("Committed as untracked (promote when approved)," "Committed") rather than implementation steps. There is no diff spec: what lines/sections to add or change, what approval criterion promotes the mockup, or what to do if the mockup changes after Kotlin implementation begins (single source of truth?). If these are already-done prerequisites, move them out of the Target Scope; if they are work items, specify the concrete changes and the promotion gate.

### Nit 1 — Antislop verification has no command, owner, or pass criterion
- **Severity:** Nit
- **Affected Scope:** Line 285 ("Re-run antislop audit after implementation")
- **Finding & Guidance:** No skill/script name, no invocation, no threshold (compare "PASS (no findings)" at line 301), and no statement of whether a failure blocks the release. One line naming the exact audit command and pass bar would make this actionable.

### Nit 2 — Simulator-based speed verification not connected to native verification
- **Severity:** Nit
- **Affected Scope:** Line 280 ("Use mockup simulator to verify palette transitions")
- **Finding & Guidance:** The mockup simulator (speed slider 0–160, line 168) verifies the HTML mockup, not the Kotlin app. State whether native verification replays simulator scenarios (recorded input sequences), uses a fake LocationManager, or requires a physical drive test, and which scenario list (normal/caution/emergency + day/night interaction) constitutes coverage.

### Nit 3 — No create-vs-modify-vs-delete markers or file inventory reconciliation
- **Severity:** Nit
- **Affected Scope:** Lines 239–275
- **Finding & Guidance:** Only entries 2 is marked "(Created)"; others say "(Modify)" but provide no Created/Modified/Deleted inventory. Per the Standard's Target Scope, mark every file explicitly, including new resources/assets (if re-bake is chosen) and any deletions (dead TE-era assets/layouts).

### Nit 4 — No ordering/dependency statements between files
- **Severity:** Nit
- **Affected Scope:** Lines 239–275
- **Finding & Guidance:** Even without formal phases (see Blocker 1), the plan should state the build order (Manifest → Car.kt + prefs migration → HomeActivity.kt → SettingsSheet → resources) so a fresh engineer does not wire UI before the data model exists. One dependency sentence per file suffices.

---

## What Good Looks Like (minimum to pass re-review)

1. Ordered phases with stable buildable exit criteria per phase (Blocker 1).
2. For each Kotlin file: exact class/member names, types, defaults, lifecycle hooks, permission sequence, and error/fallback paths (Blockers 2–3, Majors 1/3/5).
3. Render decision predetermined: tint vs re-bake, per-view mechanism, color values per zone × day/night mapped to Android resources (Blocker 3, Major 7).
4. Resolved verification targets (one canonical RAM/Home pair) and test cases with inputs, assertions, locations, and edge-case coverage (Blocker 4).
5. Explicit file inventory with full paths and create/modify/delete markers, including every layout/drawable/dimen/custom View (Major 2, Nit 3).
6. Accelerometer either in-scope with threshold/sampling spec or explicitly deferred (Major 6).

*This review does not modify the design doc.*
