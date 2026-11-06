# Intermediate Zoom Out Review — NGE Terminal Interface v4 (Stage 8, Mode: REVIEW)

**Target:** `docs/designs/nge-terminal-interface-v3.md` (312 lines, semantic boundary line 237)  
**Mode:** REVIEW (intermediate zoom out; focus on additions/mitigations since Stage 5)  
**Standard:** `resources/document_standards/zoom-out-standard.md`  
**Prior reviews inputs:** Stage 5 zoom_out_report (1 Blocker / 4 Major / 6 Nits), comprehension_report (F1–F13), critic_design (14), critic_structural (6), critic_style (18)  
**Date:** 2026-11-06  
**Verdict:** NEEDS SIMPLIFICATION — 1 Blocker (framing contested), 5 Majors, 6 Nits

> **Lens note.** This is an *intermediate* zoom out, not a re-audit. It scores one question: did the v4 revision since Stage 5 (v1, 245 lines) add complexity faster than Stage 5 mitigations, or resolve it? Net answer: **complexity grew faster than mitigation.** Every Stage 5 finding persists; the revision added a new day/night time system on top of the unresolved palette-matrix problem, grew the mockup-architecture tree from ~6 to ~30 lines, and let mockup-only features (simulated-clock drift, trapezoid MAGI, 116px odometer) enter the spec without any traced app requirement. The only genuine simplifications since Stage 5 are Q1 font-licensing closure and a more precise Non-Goals permission line — and the latter still under-scopes the real integration surface.

---

## Delta Since Stage 5 (what the revision actually changed)

| Change | Effect on zoom-out score |
|---|---|
| 6 → 14 reference images, fonts settled (OFL) | Neutral — strengthens context sufficiency, adds no runtime complexity. |
| Day/night moved from headlight-only to **time-based AUTO/MANUAL + headlight "override"** (lines 15, 31, 82–86, 179) | **Negative.** The single largest complexity addition since Stage 5. Replaces a working 2-state theme path with three competing sources of truth (see Finding 4). |
| New mockup elements: wandering kanji band, metadata side-channels, A.T. Field honeycomb (~240 hexes), 116px odometer + 24-seg ladder, 104px knob, 13-tile MORE sheet, trapezoid MAGI | **Negative (mockup-side scope).** None of these beyond the MORE sheet is traced to an app requirement; layout deltas are weeds in the architecture section (see Finding 6). The 13 MORE tiles, however, are **verified reuse** — `layout.json` `meta.more` already lists 13 tiles (Radio, USB Music, Video, Dashcam, Sound EQ, AUX, Files, Car, CAN, Wheel keys, Android, Screen off, Home setup). |
| Mockup Architecture section expanded 6 → 30 lines (lines 137–170), including DOM nesting, pixel budgets, `S` state shorthand, "simulated clock for drift" | **Negative.** Altitude dropped further, not less. Mockup internals now read as architecture. |
| Non-Goals: "No new permissions **beyond GPS access**" (line 37) | Small positive in wording, no substance — still contradicts the 3-integration surface (critic F-01). |
| Touch-target rule ≥44px added (line 232) | Partial mitigation, contradicted by stale 40px line (see Nit 2). |
| CUJ-6 Day/Night Toggle added | Neutral-to-negative: documents the new complexity badge rather than justifying it. |

---

## Findings

### 1. [Blocker] Altitude Drop / Platform Mismatch — SVG/CSS architecture still describes the mockup, not the app
**Affected scope:** Lines 28, 47, 102–129, 152–188, 230; §SVG Hex Grid (182–188) and applyPalette() pseudocode  
**Simplicity Rationale:** Unchanged verbatim from Stage 5 Finding 1, and now confirmed Blocker-grade by two further independent reviews (critic_design F-02; structural hierarchy passes but the technology choice is still unnamed). The design claims "Use SVG for hexagonal button grids to reduce rendering overhead" (line 28) while Q5 admits the app "uses baked images + Canvas" (line 48). There is zero SVG/VectorDrawable code in `launcher/app/src/main/` today, and `HomeView.kt` draws exclusively via `Canvas` calls (`onDraw` → `drawTab`, `drawPlate`, `drawDisplay`, `drawScale`, `drawPads`, `led`) — the native path is Canvas path drawing of hexagon shapes, which the design never states. **Fix:** Scope all SVG/CSS language to the mockup; state one native rendering path. The altitude drop is not cosmetic: palette-transition cost (tint vs re-bake) determines whether the 22MB / 175ms / 3.3MB budgets hold at all, and cannot be reasoned about from CSS-variable pseudocode.

### 2. [Major] YAGNI — Accelerometer early-transition detection remains speculative, with the concession buried in its own Alternative
**Affected scope:** Lines 46 (Q3), 97, 109, 214–218, 231, 305  
**Simplicity Rationale:** Identical to Stage 5 Finding 2, still unresolved after a full revision cycle. Alternative C's own text admits GPS "is reliable" at ~1Hz (line 217); the accelerometer exists for a ~1-second lag nobody has demonstrated as a defect on-device, with no threshold, axis, or fusion rule, and no `SensorManager` usage exists anywhere in the codebase. The "GPS unavailable" fallback (line 231) already covers the outage case, making the supplement redundant even in its strongest form. This is now the *second* consecutive review cycle carrying the same speculative feature — the definition of YAGNI inertia. **Fix:** GPS-only for v1; gate accelerometer work on a measured transition-lag threshold on the actual headunit.

### 3. [Major] Premature Complexity — Palette state matrix is now larger than the 6 states Stage 5 flagged
**Affected scope:** Lines 15, 31, 56, 86, 179, 233; Hardware table; `Car.kt` lines 31, 86–90, 120–122, 496  
**Simplicity Rationale:** Stage 5 Finding 3 flagged 3 zones × 2 day/night modes = 6 states. The revision made it worse, not better: day/night now resolves through *three* authorities — `mcu_light_state` polling in `Car.kt` (live, drives `applyPanel()`), the new time-based clock, and a MANUAL user override — with no precedence rule stated, plus a new `dayNightMode` pref that duplicates the existing `theme` pref (DEFAULTS `"theme":"auto"`, `Car.kt` line 496). The codebase already hosts a clean 3-theme variable system (`layout.json` `vars`: light/dark/night; `Ui.kt` `setTheme()`/`themeVar()`): adding speed zones as additional theme-var sets inside that system is the natural reuse host. Instead the design proposes a parallel state machine with a 6+-state matrix, no cost model for tint-vs-rebake, and fresh terminology ("MAGI stack", "telemetry rail") that matches no existing layer. **Fix:** Derive the zone × light matrix inside `Ui.kt` theme vars; define a single `resolveTheme()` (MANUAL > time-based, drop the headlight third source — its only stated justification is that the sensor "may not be connected," which argues for deletion, not retention); reuse the existing `theme` pref key.

### 4. [Major] Architectural Dead-End / New Since Stage 5 — Day/night time system is mockup-validated but not app-designed (three sources of truth, mockup-only drift state)
**Affected scope:** Lines 15, 26, 31, 82–86, 97–98, 140, 166, 179, 259, 264, 284  
**Simplicity Rationale:** The revision's headline addition is the least designed part of the document (structural critic confirms: the whole day/night feature has no Risks & Mitigations entry; critic F-09's precedence table is still absent). Concretely: (a) no precedence among `mcu_light_state`, clock, MANUAL; (b) unclear whether the existing `applyPanel()` polling is removed (breaking the current night trigger) or runs as a parallel path; (c) the mockup's "simulated-clock drift override" (line 166) has no app analogue — there is no design for what the device does when its own clock is wrong, which is the entire premise of "drift-proof" testing; (d) `dayNightMode` pref duplicates `theme`. The design brief confirms headless time-based logic exists *in the mockup simulator only*. This is a speculative feature implemented in the reference artifact and back-ported into the spec as if decided. **Fix:** One decision function, one pref, threat-model the device clock (or drop "drift-proof" language and accept time-based behavior between 17:00/05:00 as-is).

### 5. [Major] YAGNI / Scope Creep — Mockup-first expansion: 9 competing simulator files retained, mockup-only features entering the spec
**Affected scope:** Lines 15, 48, 135, 164–170, 292–296; `mockup/nge-terminal*.html` × 9  
**Simplicity Rationale:** Stage 5 Finding 4 is now quantitatively worse. The repo carries 9 full simulator implementations (v3 baseline, deepseek/glm/crouton × v3/v4). The design names `nge-terminal-deepseek-v4.html` (84KB) "canonical" yet retains all 8 others "for comparison" with no reader or pending decision identified — every design change must now be eyeballed across 9 files. Worse, the hierarchy has inverted: Q5 declares "the mockup is design spec; app uses baked images + Canvas" (line 48), so features are *born in the simulator and promoted into the spec* — simulated-clock drift override, accent picker (5 colors), 116px odometer + 24-seg ladder, trapezoid MAGI — with no traced app requirement or acceptance test separating "mockup toy" from "product surface." (Credit where due: the 5-color accent picker does map to existing reuse — `layout.json` settings meta has `accent` with exactly 5 options, so that one mockup feature is genuinely grounded. It is the only new mockup feature with such a mapping.) **Fix:** Archive 8 variants; make the spec drive the mockup, not the reverse; each promoted feature needs a one-line app-requirement trace.

### 6. [Major] Altitude Drop (Weeds) — Mockup architecture tree grew to 30 lines of layout internals
**Affected scope:** Lines 137–170 (Mockup Architecture), 190–196 (MAGI clip-path), 100–129 (Speed Tracking / Palette System pseudocode)  
**Simplicity Rationale:** Stage 5 Nit 6 complained about ~15 lines of weeds. The revision *tripled* them: 32px status rail, 42px banner, 338px main, 152px telemetry rail, 18px kanji band, 638px stage, 216px MAGI stack, clip-path polygon values, `S (…, …) + prefs` shorthand with "…" — none of this belongs in a design document, and the growing mass actively buries the missing app-level decisions (native hex path, palette cost model). A naive reader cannot tell which of these numbers constrain the app and which describe only the HTML file. Compress to three sentences: one canonical mockup file, palette state machine with 3 zones, apply function updates all surfaces; pixel detail stays in the mockup. The `S …` ellipsis and `R-13` dangling reference (line 162) are comprehension gaps (comprehension F4/F5) that a 30-line tree makes harder, not easier.

---

### Nits

**Nit 1 — Unresolved reconciliation regressions.**
The SKU of "30 lines of weeds" should not have produced a *second* set of numbers for the same component. MAGI trapezoids are "196x44" (lines 15, 146) and "120×50px" (lines 73, 192); speed readout is "116px odometer" (lines 15, 151) and "32px, prominent" (line 56). Critic F-03's Blocker on dimensional contradictions stands. A design that introduces new measurements faster than it retires old ones fails the "one concept, one term, one unit" test and cannot be built from.

**Nit 2 — Touch-target inconsistency sentence-by-sentence.**
66×76 (lines 15, 154) vs 40px diameter (line 188) vs ≥44px (line 232). The ≥44px rule was added as a mitigation since Stage 5 but the stale 40px line was not removed, so the revision now has *three* numbers where Stage 5 had two.

**Nit 3 — Future Improvements list remains unfiltered speculation.**
Lines 303–309, identical to Stage 5 Nit 8: speed logging, custom palettes, GPS navigation, speed-based audio muting — the latter two are safety-relevant on a driving headunit and must never ship as unexamined "future" items. Delete or gate behind a separate safety review.

**Nit 4 — Alternative C self-contradiction, now with Alternative D.**
"Reason for Rejection: Not rejected — primary approach" (line 218) persists from Stage 5 Nit 9. The new Alternative D (lines 220–224) then presents headlight-based day/night as rejected on preference while line 179 keeps headlight as an "override" — the document both rejects and retains the same path. Pick one state per alternative and word it once.

**Nit 5 — Unsubstantiated SVG performance claim, still present.**
"SVG hex grid is lighter than Canvas for static elements" (line 230) — no baseline, no measurement, and per Finding 1 it describes the wrong platform anyway. The testable budget exists in the Verification Plan (line 281); the comparative claim adds noise.

**Nit 6 — GPS runtime-permission / denial gap, confirmed by code.**
`ACCESS_FINE_LOCATION` is a dangerous permission requiring a runtime dialog; the manifest today has no location entry (`android.permission.SYSTEM_ALERT_WINDOW`, `READ_LOGS` only), and the plan lists only the manifest edit (lines 271–274) with no runtime request, denial UX, or disabled-GPS behavior. One sentence fixes it: who requests (first-run in `HomeActivity`), and the denied state is the normal palette (not latched stale), with TTL reverting a stale fix to `normal`. Carried since Stage 5 Nit 11 and critic F-05/F-10; three review cycles with the same one-sentence gap.

---

## Reuse Verification (code search, this stage)

| Proposed element | Existing codebase | Verdict |
|---|---|---|
| Theme state (`light`/`dark`/`night`), `Car.themeName()`, `Ui.setTheme()`/`themeVar()` | `Car.kt` lines 31, 86–90, 120–122; `Ui.kt`; `layout.json` `vars` (3 themes) | **Partial reuse opportunity, extended.** Confirmed this stage: live 3-theme var system is the natural host for palette zones. Design still proposes a parallel matrix without mapping to it. |
| Speed thresholds in SharedPreferences | `Car.kt` `prefs`, `DEFAULTS`, `SheetView.kt` generic pref editor | **Reuse OK.** Existing infra suffices; no new prefs infra proposed. Same verdict as Stage 5. |
| `mcu_light_state` / `mcu_backcar_state` polling | `Car.kt` `sys(K_LIGHT)`, `sys(K_BACKCAR)`, `applyPanel()` | **Must respect.** Day/night must integrate with, not duplicate, the existing `applyPanel()` trigger (see Finding 4). |
| GPS `LocationManager` / `SensorManager` | **Zero matches** in `launcher/app/src/main/` | **New code, no wheel reinvented.** Confirmed: no existing location/sensor infrastructure to reuse. |
| Hex/SVG/MAGI rendering | `HomeView.kt`, `DeckView.kt`, `KnobView.kt` (Canvas, baked images); **zero SVG/VectorDrawable** in app or res | **No reusable component.** Canvas hexagon path drawing is the viable native path; not specified (see Finding 1). |
| 13-tile MORE sheet | `layout.json` `meta.more` — **exactly 13 tiles**, names enumerated | **Reuse verified.** 13-tile claim is grounded in existing data; the sheet needs no new content work. |
| Accent picker (5 colors) | `layout.json` `meta.settings` entry `accent` with exactly 5 options; `Car.kt` DEFAULTS `accent` | **Reuse verified.** One mockup feature with a genuine existing mapping — noted as the sim's best-grounded promotion. |
| Mockup variants (9 HTML files) | `mockup/nge-terminal*.html` × 9, sizes 35–100KB | **Maintenance burden.** Unchanged since Stage 5 (see Finding 5). |

## Root Problem Focus

The root-problem choice is still sound: the user wants an NGE terminal aesthetic plus speed awareness, and all upstream workflows are retained (Non-Goals unchanged in spirit). No symptom-patching; the failure mode is the reverse — **speculative elaboration around a sound core.** The v4 revision's new features (time-based day/night, drift simulation, odometer ladder, MAGI trapezoids) were validated against the mockup simulator before being designed for the app, which is how a 245-line Stage-5 draft grew into a 312-line Stage-8 spec while its Blocker stayed exactly where it was.

## Verdict Rationale

**1 Blocker** (platform mismatch: SVG/CSS describes the mockup; native hex-rendering path still undefined — now corroborated by 3 reviewers). **5 Majors** (accelerometer speculation persists; palette matrix expanded from 6 to 3-source-of-truth states; day/night time system introduced before designed; 9-variant mockup bloat with inverted spec/mockup hierarchy; 30-line weeds tree). **6 Nits** (dimensional regressions added — MAGI and speed readout now dual-numbered; touch-target triple number; speculative Future Improvements; Alt-C/D self-contradictions; SVG performance claim; GPS runtime-permission gap). **Severity counts: Blocker 1, Major 5, Nit 6, Total 12.**

The mitigation direction required is the opposite of the revision direction: shrink the matrix to the existing `Ui.kt` theme-var host, delete one of the three theme sources, GPS-only v1, one canonical mockup, and demote pixel layout back into the mockup where it belongs. With those four moves the document is implementable; without them the next revision will add a fifth layer on the same open Blocker.
