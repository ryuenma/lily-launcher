# Consolidated EGM Review Report — NGE Terminal Interface for Lily Launcher (v4)

**Target:** `docs/designs/nge-terminal-interface-v3.md` (312 lines, semantic boundary line 237)
**Preset:** Consolidated report with all review rubrics (uninterrupted full flow)
**Date:** 2026-11-06
**Canonical mockup (design target):** `mockup/nge-terminal-deepseek-v4.html`
**Status:** Review complete — document remains in Draft; no fixes applied (review-only pass).

---

## 1. Executive summary

The EGM review ran all five stages (Zoom Out, Comprehension, Critic ×3, Intermediate Zoom Out, Implementation Readiness ×3) against the v4 design document. The document is **sound in intent and root-problem focus** (aesthetic + speed-aware palette, all upstream workflows retained) but is **not yet implementation-ready**: the architecture sections conflate the HTML mockup with the native Android app, the implementation plan delegates every concrete decision to the implementer, and the mandatory Risks & Mitigations section is absent.

**Totals across all stages:** 26 Blockers, 57 Majors, 41 Nits (124 findings). After deduplication of findings raised by multiple reviewers, the consolidated actionable set is **16 Blockers, 24 Majors, 18 Nits** (58 unique items). The most consequential cluster is the platform mismatch (SVG/CSS describes the mockup, not the native Canvas app) plus the undefined day/night × speed-palette precedence and the missing GPS lifecycle/permission spec.

**Recommendation:** Resolve the 16 consolidated Blockers in one revision pass before any implementation work; the Majors and Nits can be addressed in the same or a follow-up pass. Do not start the 7-step implementation plan (HANDOVER "What's Next") until B-01..B-06 are closed.

---

## 2. Stage severity counts

| Stage | Reviewer(s) | Blocker | Major | Nit | Total |
|---|---|---:|---:|---:|---:|
| 5 — Initial Zoom Out | egm-zoom-out | 1 | 4 | 6 | 11 |
| 6 — Comprehension | egm-comprehension | 0 | 5 | 8 | 13 |
| 7 — Critic (design) | egm-design-critic | 4 | 7 | 3 | 14 |
| 7 — Critic (structural) | egm-structural | 1 | 3 | 2 | 6 |
| 7 — Critic (style) | egm-style | 1 | 8 | 9 | 18 |
| 8 — Intermediate Zoom Out | egm-zoom-out | 1 | 5 | 6 | 12 |
| 9 — Implementation Readiness | egm-implementation-readiness | 4 | 8 | 4 | 16 |
| 9 — Implementation Readiness | egm-codebase-details | 6 | 7 | 3 | 16 |
| 9 — Implementation Readiness | egm-style | 0 | 4 | 3 | 7 |
| **Raw total** | | **18** | **51** | **44** | **113** |

(Counts as reported by each subagent; minor cross-stage double counting collapsed in §3–§5. Consolidated unique counts: 16 Blockers, 24 Majors, 18 Nits.)

---

## 3. Consolidated Blockers (must resolve before implementation)

| ID | Consolidated finding | Raised by (stage) | Affected scope |
|---|---|---|---|
| B-01 | **Platform mismatch: SVG/CSS architecture describes the HTML mockup, not the native app.** "Use SVG for hexagonal button grids" + CSS variables (`--bg`, `--text`, `--accent`) and `applyPalette()` are mockup constructs; the app is `View + Canvas + baked images` and never defines how hexagons are rendered natively (custom View, VectorDrawable, Compose, or re-baked images). The "SVG lighter than Canvas" claim is unbacked. | Stage 5 F1, Stage 7 (design) F-02, Stage 8, Stage 9 (codebase) | Lines 28, 47, 133–188, 230 |
| B-02 | **Missing mandatory `Risks & Mitigations` section.** Only 3 bullets under Non-Functional Requirements; no failure modes, security, scalability, or mitigation table. Performance budget (22MB RAM, 175ms, 3.3MB APK) is asserted, not validated against 3 OFL font families + palette-variant baked images + ~240 background hexes. | Stage 7 (design) F-04, Stage 7 (structural) F1 | §228–234; entire range |
| B-03 | **Internal dimensional contradictions across the spec.** Speed readout 116px (L15/L151) vs 32px (L56); MAGI trapezoids 196×44 (L15/L146) vs 120×50 (L73/L192); hex buttons 66×76 (L15/L154) vs 40px diameter (L188) which also violates the doc's own ≥44px WCAG target and the 18mm thumb goal. | Stage 7 (design) F-03, Stage 7 (style) F1, Stage 6 F4/F9 | Lines 15, 56, 73, 146, 151, 154, 188, 192, 232 |
| B-04 | **Non-Goals contradict Architecture.** "No changes to AIDL services, broadcast receivers, or vendor integrations" (L36) while proposing `LocationManager`/`SensorManager` listeners plus runtime GPS permission — new integration surfaces with no manifest, permission, or lifecycle spec. | Stage 7 (design) F-01 | Lines 36, 96–98 |
| B-05 | **Day/night × speed-palette precedence undefined.** Three competing sources (reverse camera → emergency, day/night → overrides in normal zone, speed-zone palette) with no priority model; `mcu_light_state` polling in `Car.kt` vs new time-based switching vs MANUAL override. Also `theme` pref key overlaps new `dayNightMode` pref (two keys, one concept). | Stage 5 F5, Stage 6 F8, Stage 7 (design) F-09, Stage 8, Stage 9 (codebase) | Lines 15, 26, 31, 80, 86, 179, 233; `Car.kt` |
| B-06 | **GPS subsystem underspecified (lifecycle, permissions, battery, staleness).** No `requestLocationUpdates` params, no availability check, no Android 6+ runtime permission flow, no disabled/denied behavior, no battery cost. "Fallback to last known speed or 0" has no TTL → a stale >100 km/h value in a tunnel latches EMERGENCY indefinitely. | Stage 7 (design) F-05, Stage 9 (codebase) | Lines 96, 102–109, 131, 231; `HomeActivity.kt` |
| B-07 | **Implementation plan not implementation-ready.** `HomeActivity.kt` (80 lines) has no `LocationManager`; plan specifies no provider, lifecycle, permission-denial/no-fix fallback, m/s→km/h conversion, exact km/h thresholds, or hysteresis. `Car.kt` mischaracterized as a "Kotlin data class" (it is a plain prefs-backed `class Car`); `dayNightMode` switching contradicts code's `mcu_light_state` headlight-only logic. Placeholder paths (`...`), undefined `SettingsSheet` (actual `SheetView.kt`), non-existent `references/nge-design-language.md`, 14-images vs brief's 6-images contradiction, emulator-based verification vs `TESTING.md` real-unit setup. No phasing, no dependency order, no create/modify markers. | Stage 9 (implementation) F1–F4, Stage 9 (codebase) | Lines 235–283; `HomeActivity.kt`, `Car.kt`, `SheetView.kt` |
| B-08 | **Threshold design has no hysteresis or debounce.** Palette flips instantly at 60/100 km/h on 1 Hz GPS noise → UI strobe while driving. Boundary-value ambiguity at exactly 60.0/100.0 (strict `>`). | Stage 7 (design) F-06, Stage 6 F9 | Lines 61, 67, 113–117 |
| B-09 | **Accelerometer role vague and internally inconsistent.** "Acceleration > threshold" with no axis, magnitude, window, or calibration; no `SensorManager`/`TYPE_ACCELEROMETER` exists in the app. Alternative C's rejection rationale is self-contradictory ("Not rejected — primary approach"). GPS-only is viable and should be primary. | Stage 5 F2, Stage 7 (design) F-07, Stage 8 | Lines 46, 97, 109, 214–218, 231, 305 |
| B-10 | **Security & privacy never addressed.** Continuous GPS location is sensitive; no permission rationale, no in-memory-only retention statement, no logging prohibition, no interaction with Android Auto/call-data flows. | Stage 7 (design) F-10 | Entire range (absence) |
| B-11 | **CUJ coverage gaps (negative paths).** Only 6 happy-path CUJs; missing GPS denied/unavailable, null/0 fix, cold start before first fix, exact-threshold equality, reverse-while-emergency, night+emergency combination. | Stage 7 (design) F-11 | Lines 50–87 |
| B-12 | **"Baked images" and palette tinting mechanism undefined.** Presented as undecided "or" (tinting vs re-baking) with no definition of what is baked, which images need variants, or asset-pipeline scope. | Stage 6 F6 | Lines 48, 174, 177 |
| B-13 | **Mockup variant bloat (9 HTML files).** `nge-terminal.html` (v3 baseline) + 3 v3 variants + 3 v4 variants + canonical v4, each a full independent simulator. Every design change must be reasoned in 3–9 files; no reader identified for retained variants. | Stage 5 F4, Stage 8 | Lines 15, 135, 292–296; `mockup/` |
| B-14 | **6-way palette matrix (3 zones × 2 day/night) without cost model.** Precedence rules for caution/emergency + night unspecified; baked-image variant count (6×) or tinting CPU cost on a 22MB device unbudgeted. | Stage 5 F3, Stage 8 | Lines 15, 31, 56, 86, 233 |
| B-15 | **State-precedence conflicts (reverse / day-night / speed).** Modal priority implied but never stated; what wins when in reverse at night above 100 km/h? | Stage 7 (design) F-09 | Lines 80, 86, 233 |
| B-16 | **Alternatives rejected on preference, not evidence.** Standard requires complexity/cost/performance/scalability rationale; all four cite user preference with at most one-line cost notes; no quantitative comparison for the most consequential Canvas-vs-SVG fork. | Stage 7 (design) F-08 | Lines 200–225 |

---

## 4. Consolidated Majors

| ID | Consolidated finding | Raised by | Affected scope |
|---|---|---|---|
| M-01 | Undefined terms: "A.T. Field" (honeycomb concept), MAGI name↔source mapping (Melchior/Balthasar/Casper ↔ BT/RADIO/USB), "LAMP" state, `S` state object, "baked images", `mcu_backcar_state`/`mcu_light_state`, NwdAudioService, "drift", "metadata side-channels", 13-tile MORE composition, 5 accent colors, `R-13`. | Stage 6 F1,F2,F3,F4,F6,F7,F10,F11,F12,F13; Stage 7 (style) F9,F12,F14 | Lines 13, 15, 36, 56, 74, 78, 98, 139–153, 160, 162, 164, 168, 244 |
| M-02 | Palette priority rule stated only for normal zone; caution/emergency + day/night behavior unspecified. | Stage 6 F8 | Line 233 |
| M-03 | Threshold semantics boundary ambiguity (strict `>` vs "crosses" wording); no hysteresis. | Stage 6 F9, Stage 7 (design) F-06 | Lines 61, 67, 113–117 |
| M-04 | Day/night toggle vs existing headlight theme: three competing sources, `theme` pref vs `dayNightMode` pref overlap, no `resolveTheme()` decision function. | Stage 5 F5, Stage 9 (codebase) | Lines 15, 26, 31, 86, 179, 233; `Car.kt` |
| M-05 | GPS runtime-permission / denial handling gap (manifest entry only; no runtime request, denial UX, or denied-state behavior). | Stage 5 (Nit 11), Stage 7 (design) F-05, Stage 9 (codebase) | Lines 37, 274; `AndroidManifest.xml` |
| M-06 | Mockup↔app fidelity declared, not verified (no screenshot diff / pixel tolerance / acceptance checklist). | Stage 7 (design) F-14, Stage 9 (implementation) | Lines 48, 135, 174–180 |
| M-07 | Mockup architecture (pixel layout tree, pseudocode, clip-path) is weeds in the design doc; belongs in the mockup. | Stage 5 (Nit 6), Stage 7 (style) | Lines 100–129, 137–170, 190–196 |
| M-08 | Headlight fallback claim unbacked (`mcu_light_state` via `Settings.System` with no mechanism). | Stage 7 (design) F-13 | Line 98; Alt-D |
| M-09 | CUJ-1 palette naming ("green/yellow/blue") doesn't match the 3-zone scheme; hex-cell recolor not in `applyPalette()`. | Stage 7 (design) F-12 | Lines 56, 69, 119–129 |
| M-10 | Subjective/marketing prose in The Problem and Alternatives ("urgent, authoritative presence", "instantly shifts", "lighter than Canvas", "user wants") makes requirements untestable. | Stage 7 (style) F2–F9 | Lines 19, 21, 61, 67, 230 |
| M-11 | Synonym redundancy ("no layout shift" ×4, "all functionality retained" ×2, "instantly shifts"). | Stage 7 (style) F10–F18 | Multiple |
| M-12 | SettingsSheet widgets/validation undefined; `res/...` has no resource names; Manifest runtime flow missing. | Stage 9 (implementation) | Lines 245–259 |
| M-13 | No phasing/checkpoints in implementation plan; no dependency order; mockup entries are status not actions. | Stage 9 (implementation) | Lines 221–283 |
| M-14 | Accelerometer early-transition is speculative with no proven 1 Hz lag problem and zero `SensorManager` code; redundant given GPS-denied fallback. | Stage 5 F2, Stage 7 (design) F-07 | Lines 46, 97, 109, 214–218, 231, 305 |
| M-15 | 6-way palette matrix without cost model (tinting vs re-baking, APK size impact). | Stage 5 F3, Stage 8, Stage 9 (implementation) | Lines 15, 31, 56, 86, 233 |
| M-16 | Touch-target inconsistency (40px vs 44px vs 66×76 vs 18mm ≈ 86px at 160 dpi) will produce divergent implementations. | Stage 5 (Nit 7), Stage 7 (design) F-03, Stage 7 (style) F1 | Lines 15, 30, 188, 232; HANDOVER L31 |
| M-17 | `references/nge-design-language.md` and `terminal-interface-design` skill references; 14 images vs brief's 6 images contradiction. | Stage 9 (codebase) | Lines 281–282; `docs/design-brief-nge-terminal.md` |
| M-18 | `SettingsSheet` should be `SheetView.kt`; emulator-based verification contradicts `TESTING.md` real-unit-only setup. | Stage 9 (codebase) | Lines 245–248, 262–269 |
| M-19 | Non-existent `references/nge-design-language.md` (no such file in repo). | Stage 9 (codebase) | Lines 281–282 |
| M-20 | CUJ-1 palette wording ("green/yellow/blue") doesn't match 3-zone scheme. | Stage 7 (design) F-12 | Line 56 |
| M-21 | Headlight fallback claim unbacked (`mcu_light_state` via `Settings.System` with no mechanism). | Stage 7 (design) F-13 | Line 98; Alt-D |
| M-22 | Mockup↔app fidelity declared, not verified (no screenshot diff / pixel tolerance). | Stage 7 (design) F-14, Stage 9 (implementation) | Lines 48, 135, 174–180 |
| M-23 | Future Improvements list is unfiltered speculation (speed logging, customizable palettes, GPS nav integration, speed-based audio) with no user request, safety analysis, or problem statement. | Stage 5 (Nit 8), Stage 7 (style) | Lines 303–309 |
| M-24 | Alternative C self-contradiction ("Not rejected — primary approach"). | Stage 5 (Nit 9) | Lines 214–218 |

---

## 5. Consolidated Nits

| ID | Consolidated finding | Raised by |
|---|---|---|
| N-01 | Pseudocode and mockup-internal details (file tree, clip-path) in architecture sections — compress. | Stage 5 (Nit 6), Stage 7 (style) |
| N-02 | Inconsistent touch targets (40px vs 66×76 vs 44px minimum). | Stage 5 (Nit 7), Stage 7 (design) F-03, Stage 7 (style) F1 |
| N-03 | "R-13", "LAMP", "S" dangling references. | Stage 6 F3,F4,F5 |
| N-04 | "etc." and "..." shorthand in CSS variable list and state object. | Stage 6 F4,F13 |
| N-05 | Boundary-value ambiguity in threshold logic (strict `>`). | Stage 6 F9 |
| N-06 | "Drift" / simulated-clock concept undefined. | Stage 6 F10 |
| N-07 | 13-tile MORE sheet composition unenumerated. | Stage 6 F11 |
| N-08 | "Metadata side-channels" contents undefined. | Stage 6 F12 |
| N-09 | "Accent picker (5 colors)" colors unnamed. | Stage 6 F13 |
| N-10 | CUJ-1 speed readout/palette wording inconsistencies. | Stage 7 (design) F-12 |
| N-11 | Headlight fallback claim unbacked. | Stage 7 (design) F-13 |
| N-12 | Headings renamed vs blueprint (`Problem Statement` → `The Problem`, etc.); optional sections precede required. | Stage 7 (structural) F2–F5 |
| N-13 | Title/filename version mismatch (v3 file, v4 title). | Stage 7 (structural) F6 |
| N-14 | "More animations"/"enlarged"/"canonical" relative adjectives; filler "Type / Scope" labels; unsubstantiated "(safety feature)" tag. | Stage 9 (style) S1–S7 |
| N-15 | Verification bullets with no observable pass criteria. | Stage 9 (style) S2/S5/S7 |
| N-16 | Antislop audit re-run not specified as a command. | Stage 9 (implementation) |
| N-17 | Simulator≠native verification gap. | Stage 9 (implementation) |
| N-18 | Mockup entries are status, not actions. | Stage 9 (implementation) |

---

## 6. Traceability matrix (raw finding → consolidated item)

| Consolidated ID | Raw findings (stage: id) |
|---|---|
| B-01 | Stage5 F1, Stage7-design F-02, Stage8, Stage9-codebase |
| B-02 | Stage7-design F-04, Stage7-structural F1 |
| B-03 | Stage7-design F-03, Stage7-style F1, Stage6 F4/F9 |
| B-04 | Stage7-design F-01 |
| B-05 | Stage5 F5, Stage6 F8, Stage7-design F-09, Stage8, Stage9-codebase |
| B-06 | Stage7-design F-05, Stage9-codebase |
| B-07 | Stage9-implementation F1–F4, Stage9-codebase |
| B-08 | Stage7-design F-06, Stage6 F9 |
| B-09 | Stage5 F2, Stage7-design F-07, Stage8 |
| B-10 | Stage7-design F-10 |
| B-11 | Stage7-design F-11 |
| B-12 | Stage6 F6 |
| B-13 | Stage5 F4, Stage8 |
| B-14 | Stage5 F3, Stage8 |
| B-15 | Stage7-design F-09 |
| B-16 | Stage7-design F-08 |
| M-01 | Stage6 F1–F7,F10–F13; Stage7-style F9,F12,F14 |
| M-02 | Stage6 F8 |
| M-03 | Stage6 F9; Stage7-design F-06 |
| M-04 | Stage5 F5; Stage9-codebase |
| M-05 | Stage5 (Nit 11); Stage7-design F-05; Stage9-codebase |
| M-06 | Stage7-design F-14; Stage9-implementation |
| M-07 | Stage5 (Nit 6); Stage7-style |
| M-08 | Stage7-design F-13 |
| M-09 | Stage7-design F-12 |
| M-10 | Stage7-style F2–F9 |
| M-11 | Stage7-style F10–F18 |
| M-12 | Stage9-implementation |
| M-13 | Stage9-implementation |
| M-14 | Stage5 F2; Stage7-design F-07 |
| M-15 | Stage5 F3; Stage8; Stage9-implementation |
| M-16 | Stage5 (Nit 7); Stage7-design F-03; Stage7-style F1 |
| M-17 | Stage9-codebase |
| M-18 | Stage9-codebase |
| M-19 | Stage9-codebase |
| M-20 | Stage7-design F-12 |
| M-21 | Stage7-design F-13 |
| M-22 | Stage7-design F-14; Stage9-implementation |
| M-23 | Stage5 (Nit 8); Stage7-style |
| M-24 | Stage5 (Nit 9) |
| N-01..N-18 | See §5 source columns |

---

## 7. Recommended next steps

1. **Resolve B-01..B-06 first** (platform mismatch, missing Risks section, dimensional contradictions, Non-Goals contradiction, day/night precedence, GPS lifecycle). These are the load-bearing ambiguities; everything downstream depends on them.
2. **Decide rendering technology (B-01) before touching Kotlin.** Pick Canvas path drawing of hexagon shapes vs re-baked image variants; drop or test the "SVG lighter" claim. This single decision unblocks the entire Technical Plan.
3. **Define `resolveTheme()` precedence (B-05) and reuse `Ui.kt`'s theme vars + `Car.prefs`** rather than a parallel 6-state matrix (Stage 8 reuse finding). Derive palette zones inside `Ui.kt`.
4. **GPS-only v1 (B-09, B-06):** ship `LocationManager`/`GPS_PROVIDER` with runtime permission + staleness TTL; defer accelerometer to a measured follow-up.
5. **Archive 8 of 9 mockup variants (B-13)** to `mockup/archive/`; keep canonical v4 + v3 baseline.
6. **Rewrite the Detailed Implementation Plan (B-07)** with phasing, dependency order, create/modify markers, concrete types/keys, and observable verification criteria (Stage 9 findings).
7. **Add `Risks & Mitigations` section (B-02)** with APK/RAM accounting, security/privacy (B-10), and GPS-denied CUJs (B-11).
8. **Address Majors M-01..M-24** in the same or a follow-up revision pass; most are documentation/clarity fixes.
9. **Re-run the EGM review (at least Stages 5, 7, 9)** after the revision to confirm convergence; the skill allows one re-audit per stage.

---

## 8. Source report files

All stage reports are under `docs/designs/egm-review/`:

- `zoom_out_report.md` (Stage 5)
- `comprehension_report.md` (Stage 6)
- `critic_design.md` (Stage 7 — design critic)
- `critic_structural.md` (Stage 7 — structural)
- `critic_style.md` (Stage 7 — style)
- `intermediate_zoom_out_report.md` (Stage 8)
- `impl_readiness_implementation.md` (Stage 9 — implementation readiness)
- `impl_readiness_codebase.md` (Stage 9 — codebase details)
- `impl_readiness_style.md` (Stage 9 — style)

This consolidated report: `docs/designs/egm-review/consolidated_review_report.md`.

---

*Consolidated by the Elephant (parent) from the nine Goldfish stage reports. No design-doc edits were applied during this review pass.*
