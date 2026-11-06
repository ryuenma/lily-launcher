# EGM Stage 9 — Implementation Readiness — codebase_details Review

**Target doc:** `docs/designs/nge-terminal-interface-v3.md`
**Audited range:** DetailedRange lines 237–312 (Detailed Implementation Plan + Verification Plan + Additional Resources + Future Improvements)
**Mode:** REVIEW
**Date:** 2026-11-06
**Standard:** `implementation-readiness-standard.md` (zero-decision rule: a fresh implementer must execute on first attempt with no guessing)
**Reviewer persona:** codebase_details (Codebase Details Goldfish — pedantic Technical Quality Auditor)

---

## Findings

### 🔴 Blocker — `Car.kt` mischaracterized as "Kotlin data class" (lines 256–259)

The DetailedRange describes `launcher/app/src/main/.../Car.kt` as a "Kotlin data class". The actual `launcher/app/src/main/java/com/lily/launcher/Car.kt` is a plain `class Car(private val app: Context)` (532 lines) holding a `SharedPreferences` handle (`prefs = app.getSharedPreferences("lily", ...)`), a `DEFAULTS` map (`resumeOnStart`, `autoAA`, `theme`, `accent`, `lastAndroid`), handlers, binder helpers, and broadcast handling — no `data class`, no auto-generated `copy()`/`componentN()`. A fresh implementer instructed to add fields "to the data class" will search for a data class declaration that does not exist and must independently decide where to put the new state.

The proposed fields (`speed`, `speed zone`, `speed threshold preferences`, `dayNightMode preference`) do not exist in the code. There is no `speed`, no `SpeedZone`, and no `dayNightMode` anywhere under `launcher/app/src/main/`. The existing night signal is `night: Boolean` derived from `mcu_light_state` (headlights) in `readSettings()` — not a time-based (`17:00`/`05:00`) preference. The v3 Verification Plan (line 284) verifies "time-based switching at 17:00 and 05:00", a behavior no code path in `Car.kt` implements or hooks into.

**Fix required:** State the exact class (`class Car`), exact full path, and exact field names/types/defaults to add; reconcile `dayNightMode` against the existing `mcu_light_state`-driven `night` flag and the `themeName()` logic.

---

### 🔴 Blocker — `SettingsSheet` is a non-existent reference (lines 261–264)

The DetailedRange names `launcher/app/src/main/.../SettingsSheet` as a file to modify. No `SettingsSheet` file or class exists in the codebase. The actual settings UI lives in `launcher/app/src/main/java/com/lily/launcher/SheetView.kt` (128 lines, `SheetView` / `SheetView.Kind`). The name collision `CurrentSettingsSheet`-style speculation is not safe: an implementer told to "Modify SettingsSheet" will look for a file that does not exist and must guess that `SheetView.kt` is the target.

The proposed changes ("speed threshold configuration (caution km/h, emergency km/h), day/night mode toggle") name no preference keys, no `prefs.edit()` call sites, and no `DEFAULTS` entries. The existing settings (`resumeOnStart`, `autoAA`, `theme`, `accent`) are backed by `Car.DEFAULTS` and `Car.flag()`/`Car.str()`; the plan never says the new keys go there, so the implementer must invent the persistence mechanism — a zero-decision violation.

**Fix required:** Name `SheetView.kt`, the exact `Kind`/row to add, and the exact preference keys/defaults to register in `Car.DEFAULTS`.

---

### 🔴 Blocker — `HomeActivity.kt` GPS claim contradicts the existing code (lines 251–254)

The DetailedRange says "Add GPS speed tracking (LocationManager), speed zone state machine, palette application, time-based day/night mode" to `HomeActivity.kt`. The actual `HomeActivity.kt` is 80 lines, extends `Activity`, and contains **no** `LocationManager`, no `GPS_PROVIDER`, no `Location`, no speed state machine, and no palette application. `grep -rn "ACCESS_FINE|LocationManager|GPS"` across `launcher/app/src/main/` returns only a comment in `DeckView.kt` ("spool speeds from the mockup: 3.2 s and 2.1 s per turn, clockwise").

Worse, the plan never specifies:
- Which provider (`GPS_PROVIDER` vs `NETWORK_PROVIDER`), update interval/distance, or callback thread;
- The m/s → km/h conversion (the design brief §4.1 gives `×3.6`, but the DetailedRange never references it, so the implementer must go hunting — the standard forbids required lookups);
- The zone thresholds as hard values (the brief §9 lists "60 km/h and 100 km/h are initial guesses" as an **open question**; the DetailedRange treats them as settled while never printing the numbers);
- Permission flow: `ACCESS_FINE_LOCATION` is added to the manifest (line 274) but runtime permission request, denial handling, and "GPS unavailable / no fix" fallback are never specified.
- Lifecycle: where `requestLocationUpdates` is registered/unregistered (Home is a `singleTask` launcher activity; a leak or duplicate callback is the predictable outcome of leaving this to the implementer).

Every one of these is a design choice the standard requires to be pre-determined. This is the Zero-Decision Rule failing on the plan's central feature.

**Fix required:** Write out provider, interval, thread, conversion, exact thresholds in km/h with units, registration lifecycle (which `onCreate`/`onPause`/`onResume`/`onDestroy` methods), runtime-permission handling, and GPS-unavailable fallback — or explicitly defer and mark the plan as not ready.

---

### 🔴 Blocker — Target paths use `...` ellipses; no implementer-executable file list (lines 251–274)

Seven of seven "Target Files" entries use `launcher/app/src/main/...` or `launcher/app/src/main/res/...` ellipses, and `SettingsSheet` carries neither extension nor package. The Implementation Readiness Standard requires an explicit list of files to be created, modified, or deleted. The real paths are `launcher/app/src/main/java/com/lily/launcher/HomeActivity.kt`, `launcher/app/src/main/java/com/lily/launcher/Car.kt`, `launcher/app/src/main/java/com/lily/launcher/SheetView.kt`, `launcher/app/src/main/res/...`, `launcher/app/src/main/AndroidManifest.xml` — an implementer unfamiliar with the codebase cannot derive these from the plan without a search the standard forbids.

Per the reviewer instructions: because the plan lists files without marking each `Create`, `Modify`, or `Delete`, this is reported once here as a Major/Major-plus, not per-path. Items 2 ("Created") and 1 ("Design target, v4") carry loose labels ("Created", "(Design target, v4)", "Committed", "Committed as untracked (promote when approved)") that are not the required `Create`/`Modify`/`Delete` verbs, so every file is ambiguous about action.

---

### 🔴 Blocker — `references/nge-design-language.md` cited but does not exist (line 300)

The Additional Resources list cites `references/nge-design-language.md` as the "NGE design language" reference. No such file exists anywhere in the repo (`find . -iname "*nge-design*"` returns nothing; `docs/` contains `INTEGRATION.md`, `TESTING.md`, `design-brief-nge-terminal.md`, and no `references/` subdirectory). This is a textbook Non-Existent Reference on an item the doc states exists now (it is listed under resources, not under proposed files), and every later reader will inherit the false statement.

---

### 🔴 Blocker — "14 images" reference analysis contradicts the design brief's "6 images" (line 249)

The DetailedRange says `docs/design-brief-nge-terminal.md` contains "reference analysis (14 images)". The brief itself (§2) says "Six NGE terminal interface screenshots were analyzed via opencode (glm-5.3-flash-free)" and every section thereafter describes 6 images. The upstream `inge-terminal-interface-v3.md` line 299 repeats "terminal-interface-design skill (14 images analyzed)". Three sources, two numbers. A fresh implementer cannot know whether the brief is complete (6) or truncated (14), which means the design authority for the palette/layout is ambiguous at the plan's entry point.

---

### 🟡 Major — "Speed zone state machine" and "palette application" are named but never specified (line 254)

The plan names a state machine and a palette application step but defines no states, no transition table, no hysteresis, no debounce (GPS at ~1 Hz will flap across a boundary), and no target classes (does the palette live in `Car.kt`, a new `Palette.kt`, or inside `HomeActivity`?). The design brief §3.3 says palette shifts are "instant (not animated)" and the whole UI re-renders on threshold crossing, but the DetailedRange never cites that, so the implementer must independently decide transition timing, animation, and state ownership — zero-decision violations, and the exact kind of silent divergence the standard exists to prevent.

`HomeView.kt` (349 lines) and `Ui.kt` (237 lines) — where palette application would necessarily land — are not named in the Target Files list at all. **Undocumented gap:** the two classes most likely to change are missing from the scope.

---

### 🟡 Major — `dayNightMode preference` vs. existing `mcu_light_state` night logic (lines 259, 264, 284)

The plan adds a "dayNightMode preference" and a "day/night mode toggle", and the Verification Plan checks "time-based switching at 17:00 and 05:00". The code's night determination is `mcu_light_state` (headlights) → `night: Boolean` → `themeName()` → `applyPanel()` (LCD contrast 65 day / 50 factory night), with persistence via `theme: "auto" | "dark" | "light"`. There is no clock-based check (`Calendar`, `AlarmManager`, or otherwise) anywhere in `Car.kt` or `HomeActivity.kt`. The plan never explains how time-based mode interacts with headlight mode, what wins, or where the 17:00/05:00 values are stored (prefs keys, header constants, or hardcoded). Two competing sources of truth for "night" are introduced with no reconciliation — the implementer must design the arbitration themselves.

---

### 🟡 Major — `res/...` and `AndroidManifest.xml` changes are underspecified (lines 266–274)

Line 269 promises "GPS permission, speed-related string resources, time-based theme strings" under a `res/...` umbrella (no file names, no string keys, no values, no resource type). Line 274 adds `ACCESS_FINE_LOCATION` but never says which `uses-permission` line, whether `ACCESS_COARSE_LOCATION` is also needed (required on Android 10+ for `getSpeed()` with coarse grants), whether `uses-feature`/maxSdk is relevant, or how the runtime-request flow in (Blocker, HomeActivity) will consume it — a "Configure, not decide" case that leaves the manifest edit to guesswork.

---

### 🟡 Major — Mockup v4 (line 241–244) is both "canonical reference" and "Committed as untracked (promote when approved)"

The file `mockup/nge-terminal-deepseek-v4.html` exists (84.0 KB on disk alongside `nge-terminal-crouton-v4.html` and `nge-terminal-glm-v4.html`). The plan's own words — "Committed as untracked" — are self-contradictory: a path cannot be simultaneously committed and untracked. The implementer cannot tell whether v4 is frozen (canonical) or still open for promotion, and which of the three v4 variants (`deepseek`, `glm`, `crouton`) is authoritative is stated only in prose, not in a `Canonical: yes` marker. The 11 feedback points are summarized (7" 2DIN, 18mm targets, hexes 66x76, knob 104, speed readout 116, MAGI trapezoids 196x44, 13-tile MORE, overlay warning no-layout-shift, animations, no glow) but no pixel map, breakpoint table, or tolerance is given in the plan; the implementer must reverse-engineer the HTML, which the standard forbids.

---

### 🟡 Major — Verification Plan is a checklist without executable test cases (lines 276–285)

The Standard requires "specific test cases (happy path and edge cases)". The Verification Plan lists aspirations, not cases:

- "Emulator test: Install on emulator, verify all states render" — contradicts the repo's own `docs/TESTING.md` ("There are no automated tests: the interesting behaviour only exists on the real unit. After install.sh, tick these off... developed against one NWD K2501 running Android 10"). No emulator exists in the documented setup; the test cannot be executed as written.
- "Speed simulation: Use mockup simulator to verify palette transitions" — no simulator entry point, no scripted speed sequence, no expected palette output per step.
- "Performance: RAM < 25MB, Home return < 200ms" — no measurement method (which profiler, which ADB command, cold or warm start, which scenario). The design brief §8 cites 22 MB / 175 ms, so the plan's numbers already disagree with the brief.
- "Day/night: Verify time-based switching at 17:00 and 05:00" — no clock-mock or device setup described; the unit has no reliable wall-clock the plan acknowledges.
- "Touch targets ≥ 44px" and "Tab navigation through hex grid, focus indicators visible" — no element list, no traversal order, no pass/fail criterion per element.
- "Antislop: Re-run antislop audit" — the antislop skill is not named, no command given.

There are no edge cases at all: no GPS-denied, no GPS-no-fix, no threshold-flap, no headlight-vs-time conflict, no reverse-camera-during-emergency-palette case — despite `INTEGRATION.md` documenting that reverse camera, calls, and Android Auto take precedence over everything else.

---

### 🟡 Major — Performance, scope, and safety numbers are unstated assumptions (lines 281–283)

"RAM < 25MB, Home return < 200ms" appear with no baseline, no measurement harness, and no unit behavior described (AOT-compiled APK vs debug, which activity, is GPS sampling counted). The 18mm thumb target / 44px touch target conversion (dp vs px at the unit's density) is never stated; the plan assumes 1 px = 1 dp without saying so. On a 7" 2DIN unit where the design brief (§5.1) assumed 1024×600, the plan never reconciles the new resolution against the existing `HomeView`/`DeckView` bitmap pipeline (`DeckView.kt` bakes bitmaps; `KnobView.kt` is a gesture surface).

---

### 🟢 Nit — Build command `./gradlew assembleRelease` is asserted without establishing the wrapper exists

The plan's first verification line asserts gradlew works. A fresh implementer should be told where the wrapper lives (`launcher/`), which Gradle version, and whether the release signing is configured. As written it is the standard's "competent engineer can execute on first attempt" test in name only — minor, because the path likely exists, but the plan never says so.

---

### 🟢 Nit — "Antislop audit: PASS (no findings)" (line 301) is an unverifiable self-attestation

The Additional Resources claim an antislop audit passed with no findings, but cite no audit artifact, date, skill version, or command. A reader cannot distinguish "audited and clean" from "never audited". Nit-level: does not block implementation, but degrades trust in the resource list.

---

### 🟢 Nit — The plan cites `docs/design-brief-nge-terminal.md` as "Committed" (line 249) while leaving mockup v4 "untracked"

Line 249 asserts the brief is committed; a `git log --follow` check is not embedded in the plan, and the two files' version-control states differ (brief committed, v4 untracked per line 244's own contradictory wording). Version drift between design authority (brief) and canonical mockup (v4) is a plausible near-term divergence the plan creates but does not guard against. Git status was not verified in this review; flagged as unverified claim, not as confirmed falsehood.

---

## Cross-reference: plan claims vs. code reality (summary table)

| Plan claim (lines) | Code reality | Verdict |
|---|---|---|
| `HomeActivity.kt` — add LocationManager GPS + state machine + palette (251–254) | 80-line Activity, no LocationManager, no speed code | Contradicts the Code |
| `Car.kt` is a "Kotlin data class" (256–259) | Plain `class Car`, prefs-backed, no data-class semantics | Contradicts the Code |
| `SettingsSheet` to modify (261–264) | No such file/class; actual `SheetView.kt` | Non-Existent Reference |
| `dayNightMode preference` + 17:00/05:00 switching (259, 284) | No clock logic; night = `mcu_light_state` headlights | Contradicts the Code / Unstated Assumption |
| `res/...` strings + GPS permission (266–274) | Manifest has only `SYSTEM_ALERT_WINDOW`, `READ_LOGS` | Unstated detail (coarse perm, runtime flow) |
| `docs/design-brief-nge-terminal.md` — "14 images" (249) | Brief §2 says 6 images | Contradicts the Code (doc vs. doc) |
| `references/nge-design-language.md` (300) | File does not exist | Non-Existent Reference |
| Verification on emulator (279) | `TESTING.md`: real unit only, no emulator | Contradicts the Code |
| RAM < 25 MB / Home < 200 ms (281) | Brief §8 says 22 MB / 175 ms | Unstated assumption / drift |

---

## Overall Implementation Readiness verdict

**Not ready.** The DetailedRange names the right feature areas (GPS speed, night mode, manifest, verification) but fails the Implementation Readiness Standard's Zero-Decision Rule at the core feature: a fresh implementer touching `HomeActivity.kt`, `Car.kt`, and `SheetView.kt` would have to guess target paths (ellipses), class memberships (data class vs. plain class, `SettingsSheet` vs. `SheetView`), every GPS decision (provider, lifecycle, permission-denial and no-fix behavior, conversion, thresholds, hysteresis), how time-based night arbitrates against headlight night, and what "palette application" concretely edits (no classes named). Three Non-Existent/contradictory references (`SettingsSheet`, `references/nge-design-language.md`, 14-vs-6 image counts) mean the plan actively misleads its reader, and the verification plan describes tests that cannot be executed as written on the documented real-unit setup.

**Counts:** 🔴 Blockers 6 · 🟡 Majors 7 · 🟢 Nits 3.
