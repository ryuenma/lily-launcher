# EGM Stage 7 — Design Critic Review
**Target:** `lily-launcher/docs/designs/nge-terminal-interface-v3.md` (FeatureRange lines 1–236)
**Standard:** `egm/resources/document_standards/feature-spec-standard.md`
**Mode:** REVIEW
**Date:** 2026-11-06

## Verdict
The specification is a usable visual/UI proposal but **fails the standard's Risks & Limits gate** and is not yet implementable as written: it contains internal dimensional contradictions, an undefined Android rendering path for the SVG hex grid, and no GPS lifecycle / security / resource-arithmetic. It passes on Problem, Goals/Non-Goals, Proposed Architecture (high level), and Alternatives on paper, but the Risks & Mitigations requirement is satisfied only by 3 bullet points under "Non-Functional Requirements" (§228–234).

## Standard Compliance Matrix
| Requirement | Status | Evidence |
|---|---|---|
| Problem Statement | ✅ | §17–21 (aesthetic + speed awareness) |
| Goals & Non-Goals | ✅ with gaps | §23–40; note Non-Goals contradict Hardware table (see F-01) |
| Proposed Architecture | ⚠️ Partial | §90–197; Android path undefined (F-02) |
| Alternatives Considered | ⚠️ Thin | §200–225; 3 of 4 rejected on preference alone, no cost/perf data (F-03) |
| Risks & Mitigations | ❌ Missing | §228–234 is not a risks section; no security, scalability, failure-mode table (F-04) |

## Findings

### F-01 — Blocker — Non-Goals contradict Architecture
- **Scope:** Lines 36 ("No changes to AIDL services, broadcast receivers, or vendor integrations") vs Lines 96–98 (GPS `LocationManager`, accelerometer `SensorManager`, MCU `mcu_light_state` via `Settings.System`).
- **Finding:** Adding `LocationManager`/`SensorManager` listeners plus a runtime GPS permission is a new integration surface and requires lifecycle/permission handling. The doc simultaneously claims "no changes to … integrations" and proposes two new sensor integrations with no manifest, permission, or lifecycle spec.
- **Recommendation:** Rewrite Non-Goals to explicitly include the two new sensor integrations, and add a permission/lifecycle subsection (see F-05).

### F-02 — Blocker — Android rendering path for SVG hex grid is undefined
- **Scope:** Lines 28, 174–188.
- **Finding:** The doc states the app "uses View + Canvas with baked images" (L174) yet mandates SVG `<polygon>` hexagons (L184) "to reduce rendering overhead" (L28, L230). SVG is a web/mockup construct; no Android equivalent is specified (custom `View`, `VectorDrawable`/SVG-android, `WebView`, or Compose). The claim "SVG lighter than Canvas for static elements" is unbacked — no benchmark, no choice, no hybrid rule for when Canvas is used (tape deck, L212). This is the central implementation fork and it is asserted, not designed.
- **Recommendation:** Choose the Android hex rendering technology, state the WebView/custom-View trade-offs, and provide a budget/benchmark or drop the performance claim to a hypothesis with a test plan.

### F-03 — Blocker — Internal dimensional contradictions across spec
- **Scope:** Speed readout: L15 "116px odometer" / L151 "116px" vs L56 "32px, prominent". MAGI trapezoids: L15/L146 "196x44" vs L73/L192 "120×50px". Hex buttons: L15/L154 "66x76" vs L188 "40px diameter".
- **Finding:** A competent implementer cannot build from contradictory dimensions and the contradictions touch core touch-target claims. The 40px hex (L188) also violates the doc's own ≥44px WCAG target (L232) and the 18mm thumb goal (L30).
- **Recommendation:** Single canonical size table (mockup vs app), resolve all three conflicts, and reconcile against the 1024×600 / 18mm constraint.

### F-04 — Blocker — No Risks & Mitigations section (standard failure)
- **Scope:** Entire range; closest content is §228–234.
- **Finding:** Standard requires explicit failure modes, security implications, scalability limits, and mitigations. None exist as a section. Performance budget (22MB RAM, 175ms, 3.3MB APK) is stated (L32) but never validated against font payload (Noto Serif JP), palette-variant baked images (L177), or SVG density (~240 background hexes, L56/153). "Maintain … 3.3MB APK" alongside 3 OFL font families is an unproven assertion.
- **Recommendation:** Add `Risks & Mitigations` with a table: risk / likelihood / impact / mitigation / owner-test. Include APK size accounting (fonts subsetted), RAM during 240-hex + pulse animations, and a measurement method for the 175ms Home-return target.

### F-05 — Major — GPS subsystem underspecified (lifecycle, battery, permissions)
- **Scope:** Lines 96, 102–109, 131, 231.
- **Finding:** No `requestLocationUpdates` parameters (minTime/minDistance), no `LocationManager` availability check, no Android 6+ runtime permission flow, no behavior when GPS is disabled/denied, no battery/CPU cost estimate. "Fallback to last known speed or 0" (L231) is ambiguous — a stale >100 km/h value in a tunnel would latch the EMERGENCY palette indefinitely; no TTL is defined.
- **Recommendation:** Specify permission rationale, registration/teardown on pause/resume, update interval, a staleness timeout (e.g., revert to `normal` after N seconds without fix), and battery impact note.

### F-06 — Major — Threshold design has no hysteresis or debounce
- **Scope:** Lines 61, 67 ("instantly shifts" on crossing), 114–117.
- **Finding:** Palette/hehaze flips immediately at 60/100 km/h thresholds with 1 Hz GPS noise. No hysteresis band, no debounce window, no accelerometer fusion rule. Boundary oscillation will strobe the UI while driving — a safety and usability defect for a driving UI.
- **Recommendation:** Add hysteresis (e.g., ±5 km/h) and a dwell time (e.g., ≥1 s) per zone transition; define the accelerometer + GPS fusion rule or remove the accelerometer claim.

### F-07 — Major — Accelerometer role is vague and internally inconsistent
- **Scope:** Lines 46 (Q3 "recommended as supplement"), 109 ("detect acceleration > threshold"), 218 ("primary approach … accelerometer is supplement — not rejected").
- **Finding:** "Acceleration > threshold" without axis, magnitude, window, or calibration against vehicle idle vibration is not a design. Alt-C's rejection rationale is self-contradictory ("Not rejected — primary approach"), so the GPS-vs-accelerometer decision is not actually made.
- **Recommendation:** Define the detection signal or downgrade accelerometer to a future non-goal. Rewrite Alt-C as a real decision with rejection rationale.

### F-08 — Major — Alternatives rejected on preference, not evidence
- **Scope:** Lines 200–225 vs Standard "Alternatives Rule".
- **Finding:** Standard requires rejection reasons including complexity/cost/performance/scalability. All four alternatives cite user preference ("user wants NGE", "user requested time-based") with at most one-line cost notes. No quantitative comparison (effort, APK/RAM impact, risk) is given for the Canvas-rewrite vs SVG decision — the most consequential architectural fork.
- **Recommendation:** Add a comparison table (effort, performance risk, APK/RAM impact, reversibility) and cite one measurable differentiator per rejected option.

### F-09 — Major — State-precedence conflicts unresolved
- **Scope:** Lines 80, 86, 233.
- **Finding:** Three overrides compete without a priority model: reverse camera "overridden to emergency (red) regardless of speed" (L80), day/night "overrides color when in normal speed zone" (L233), and the speed-zone palette. RF: what wins when in reverse at night above 100 km/h? The modal priority (reverse > guard > day/night > speed) is implied but never stated.
- **Recommendation:** Add a precedence table with one winner per (speed zone × reverse × day/night) cell.

### F-10 — Major — Security / privacy never addressed
- **Scope:** Entire range (absence).
- **Finding:** Continuous GPS location is sensitive personal data. No statement on permission rationale, data retention/logging (location must not be logged to disk), or Android Auto / call-data flows interacting with the new display. "No internet permission" (L39) does not mitigate local location-data handling.
- **Recommendation:** One short Security & Privacy subsection: what location data is collected, where it lives, retention = in-memory only, no logging.

### F-11 — Major — CUJ coverage gaps (negative paths)
- **Scope:** Lines 50–87.
- **Finding:** Six happy-path CUJs only. Missing: GPS denied/unavailable, null/0 fix, cold start before first fix, exact-threshold equality (60.0 / 100.0), reverse-while-emergency, night+emergency combination. For a vehicle UI, negative paths are the ones that matter.
- **Recommendation:** Add CUJ-7..9 for GPS-denied, stale-fix timeout, and combined-override precedence.

### F-12 — Nit — CUJ-1 speed readout and palette wording inconsistencies
- **Scope:** Line 56 ("green/yellow/blue palette", "32px"), Line 69 ("Hex grid cells turn red").
- **Finding:** "green/yellow/blue" does not match the 3-zone scheme elsewhere (normal/caution/emergency); hex-cell recolor is not in the `applyPalette()` subroutine list (L119–129).
- **Recommendation:** Align CUJ-1 palette naming with the zone state machine; add hex-cell recolor to `applyPalette()` or remove from CUJ-3.

### F-13 — Nit — Headlight fallback claim is unbacked
- **Scope:** Line 98 ("Headlights | MCU (mcu_light_state) | Settings.System") and §200 Alt-D.
- **Finding:** No mechanism is described for surfacing MCU state through `Settings.System`. Alt-D correctly notes the sensor "may not be connected" but the Hardware table still lists it as a component with no fallback wiring.
- **Recommendation:** Mark headlights as "if available, else time-based only" in the Hardware table, or drop the row.

### F-14 — Nit — Mockup↔app fidelity is declared, not verified
- **Scope:** Lines 48 (Q5), 135, 174–180.
- **Finding:** "Mockup is design spec; app uses baked images + Canvas" (L48) admits a fidelity gap with no verification method (screenshot diff, pixel tolerance, acceptance checklist).
- **Recommendation:** Define one acceptance method (e.g., side-by-side diff at 1024×600 for the 6 CUJs) or restate mockup as non-normative reference.

## Severity Count
- **Blocker:** 4 (F-01, F-02, F-03, F-04)
- **Major:** 7 (F-05, F-06, F-07, F-08, F-09, F-10, F-11)
- **Nit:** 3 (F-12, F-13, F-14)
- **Total:** 14

## Bottom Line
Do not proceed to implementation until F-01–F-04 are resolved: the Non-Goals contradict the sensor design, the core Android SVG technology is unnamed, three touch-critical dimensions contradict themselves, and the mandatory Risks & Mitigations section is absent. With those fixed, the remaining Majors are well-scoped and addressable in one revision pass.
