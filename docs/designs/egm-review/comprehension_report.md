# Comprehension Test — NGE Terminal Interface v4 (REVIEW Mode)

**Target:** `docs/designs/nge-terminal-interface-v3.md` (312 lines)  
**FeatureRange:** Lines 1–236 (Overview through Non-Functional Requirements)  
**DetailedRange:** Lines 237–312 (Detailed Implementation Plan through end)  
**Context:** `docs/design-brief-nge-terminal.md`, `mockup/nge-terminal-deepseek-v4.html` (not inspected — path referenced only)  
**Persona:** Comprehension Goldfish (naive-reader audit: undefined terms, assumed knowledge, self-containment)

---

## Findings

### F1 — Undefined: `A.T. Field` (Honeycomb / Hex Grid Concept)

- **Severity:** Major
- **Affected Scope:** Lines 13, 56, 139–153, 162 (Overview, CUJ 1, Mockup Architecture)
- **Discrepancy Description & Rationale:** "A.T. Field" is introduced as the name for the honeycomb hex-grid background (~240 hexes) with no inline definition. A fresh reader does not know whether it is a decorative background pattern, a functional component, or a state variable. The term is NGE franchise lore (A.T. Field = Absolute Terror Field) and is never explained or linked. Because the document is self-contained elsewhere (e.g., it defines MAGI node labels and palette zones), the absence of a definition here is a comprehension gap. A naive reader cannot determine if "A.T. Field" maps to a CSS class, an SVG element, or a design reference only.

### F2 — Undefined: MAGI Node Label Mapping (Melchior/Balthasar/Casper ↔ BT/RADIO/USB)

- **Severity:** Major
- **Affected Scope:** Line 74 (CUJ 4), lines 146–147 (Mockup Architecture), lines 192–197 (MAGI Node Diagram)
- **Discrepancy Description & Rationale:** CUJ 4 states the MAGI node diagram (3 trapezoid indicators) "updates (active node fills)" when a BT/RADIO/USB key is pressed, implying a 1-to-1 mapping between source keys and the three MAGI labels. Line 146 names the three trapezoids "(Melchior/Balthasar/Casper)" and line 196 lists "Label below: Melchior/Balthasar/Casper (JP Mincho)" — but neither line 74 nor lines 190–197 explicitly states which MAGI name corresponds to which source (e.g., Melchior=BT, Casper=RADIO, Balthasar=USB). A fresh reader must infer the mapping (and the order) from layout position alone. The mapping is never spelled out in any section.

### F3 — Undefined: "LAMP" State (Status Rail)

- **Severity:** Nit
- **Affected Scope:** Line 140 (Mockup Architecture — status rail)
- **Discrepancy Description & Rationale:** The status rail is described as "(clock, speed zone, LAMP state, drift-proof day/night)". The term "LAMP" appears only here and is never defined in the document. A naive reader cannot tell whether LAMP refers to headlights, a system indicator, or a mockup variable name, or how its state is determined/reflected in the app.

### F4 — Undefined: `S` (Simulator State Object) and Prefs Scope

- **Severity:** Nit
- **Affected Scope:** Line 164 (Mockup Architecture — "State: S (speed, source, track, night, dayNightMode, usb, bt, reverse, call, ...) + prefs")
- **Discrepancy Description & Rationale:** "S" is used as shorthand for the mockup's central state object but is never introduced as a concept. The parenthetical list uses "..." which a naive reader cannot expand — the reader does not know the full set of fields held in S, whether "prefs" is a separate object or a subset, or where persisted preferences (thresholds, dayNightMode) live within this structure. The document elsewhere (lines 131, 259) mentions "SharedPreferences" for the app, but the relationship between mockup `S`/prefs and the app's `Car.kt` preference fields is not stated.

### F5 — Undefined: `R-13` (Zero-Glow Rule Reference)

- **Severity:** Nit
- **Affected Scope:** Line 162 (Mockup Architecture — "No glow (flat fill only; R-13)")
- **Discrepancy Description & Rationale:** "R-13" is cited as the authority for the zero-glow rule but is never defined, numbered elsewhere in the document, or linked to an external rules file. A fresh reader encounters a dangling cross-reference that resolves to nothing within the document. The Additional Resources section (lines 289–302) lists a `terminal-interface-design` skill and `references/nge-design-language.md` but does not mention any numbered rule set.

### F6 — Undefined: "Baked Images" and Palette Tinting Mechanism

- **Severity:** Major
- **Affected Scope:** Line 48 (Q5: "app uses baked images + Canvas"), line 174 (App Architecture), line 177 ("Modify rendering to use speed-based palette (color tinting of baked images, or re-bake with palette variants)")
- **Discrepancy Description & Rationale:** The document repeatedly references "baked images" as the app's existing rendering strategy but never defines what a baked image is (pre-rendered raster assets? Which elements are baked vs. drawn at runtime?), how it interacts with the dynamic palette (tinting vs. re-baking is presented as an undecided "or"), or which images need palette variants. A naive reader — especially a new implementer — cannot determine the scope of work from the "or" phrasing. This is both a vague explanation and an assumed-knowledge gap: the reader is expected to know the existing app's asset pipeline without any link or brief description.

### F7 — Assumed Knowledge: `mcu_backcar_state`, `mcu_light_state`, NwdAudioService

- **Severity:** Major
- **Affected Scope:** Line 36 (Non-Goals: "NwdAudioService constraint"), line 78 (CUJ 5: "mcu_backcar_state"), line 98 (Hardware table: "mcu_light_state")
- **Discrepancy Description & Rationale:** Three MCU/telemetry field names are used without any brief definition of what they represent (e.g., reverse-gear sensor signal, headlight status) or where they are sourced from (MCU broadcast? Settings.System?). The NwdAudioService is cited as a constraint on audio additions but is never identified (service class? vendor SDK? audio policy?). A fresh reader unfamiliar with the headunit's MCU integration layer cannot self-contain their understanding; the document assumes institutional knowledge of the existing codebase's MCU interface. (The design brief at lines 122–125 does explain that the existing code uses `mcu_light_state` for headlights and mentions reverse states, but the target document itself does not carry that explanation forward.)

### F8 — Vague: "Day/night overrides color when in normal speed zone" (Palette Priority Rules)

- **Severity:** Major
- **Affected Scope:** Line 233 (Non-Functional Requirements — "Theme consistency")
- **Discrepancy Description & Rationale:** The document states: "Both speed-based palette and time-based day/night must work together. Day/night overrides color when in normal speed zone." It does not specify the full priority rule: what happens when the speed zone is caution or emergency AND day/night is active? Does speed palette always win over day/night, or does day/night modify the speed palette (e.g., darker variants)? The phrase "overrides color" is also vague — which colors (background only, or full palette)? A naive reader cannot implement the theme interaction from this single sentence.

### F9 — Undefined: Threshold Semantics in `updateSpeedZone()` Pseudocode

- **Severity:** Nit
- **Affected Scope:** Lines 113–117 (Palette System pseudocode)
- **Discrepancy Description & Rationale:** The pseudocode reads `if speed > emergencyThreshold: zone = 'emergency'` / `elif speed > cautionThreshold: zone = 'caution'`. A fresh reader notices that at exactly 100 km/h (equal to `emergencyThreshold`), the zone is 'caution' (not 'emergency'), and at exactly 60 km/h the zone is 'normal' — but the CUJs (lines 60–68) use "crosses 60" / "crosses 100" language and "between 60-100" / "above 100" phrasing that conflicts with strict `>` semantics at boundary values. The document never clarifies boundary behavior (is 60.0 included in caution?). This is a minor but real ambiguity for an implementer translating the pseudocode to Kotlin.

### F10 — Assumed Knowledge: "Drift" / "Simulated-Clock Drift Override"

- **Severity:** Nit
- **Affected Scope:** Line 15 (v4 update), line 140 (status rail), line 166 ("simulated clock for drift")
- **Discrepancy Description & Rationale:** The terms "drift" and "drift-proof" appear three times with no definition. A naive reader does not know what is drifting (device clock? Android system time? GPS time?) or why a simulated clock is needed to prove drift-proofing. The concept is mockup-specific (the simulator tests that day/night switches at 17:00/05:00 even if the real clock differs) but is never explained.

### F11 — Undefined: 13-Tile MORE Sheet Composition

- **Severity:** Nit
- **Affected Scope:** Line 15 (v4 update: "13-tile MORE sheet"), line 244 (Detailed Implementation Plan)
- **Discrepancy Description & Rationale:** The document states that the MORE sheet has 13 tiles but never enumerates them or references where the tile list is defined. A fresh reader cannot confirm whether the 13 tiles match the existing app's MORE sheet contents or are a new set. (The design brief at line 259 says "More sheet (13 tiles)" as a retained upstream feature, but the target document does not carry forward the tile identities.)

### F12 — Vague: "Metadata Side-Channels" (Contents Undefined)

- **Severity:** Nit
- **Affected Scope:** Line 160 (Mockup Architecture — "Metadata side-channels (codes, layers, protocols)")
- **Discrepancy Description & Rationale:** The mockup tree lists "metadata side-channels" with only a parenthetical hint. The reader is not told what codes, layers, or protocols are displayed, how they differentiate between speed zones, or whether they are static or dynamic. The design brief (lines 188–205) provides example metadata content, but the target document does not inline or link to it. An implementer reading only the target document cannot reproduce the side-channel content.

### F13 — Undefined: "Accent Picker (5 Colors)" and Palette Variable Set

- **Severity:** Nit
- **Affected Scope:** Line 168 (Simulator: "accent picker (5 colors)"), line 120 ("set CSS variables (--bg, --text, --accent, etc.)")
- **Discrepancy Description & Rationale:** The simulator includes an "accent picker (5 colors)" but the five colors are never named in the document. Similarly, the CSS variable list uses "etc." leaving the full palette variable set undefined. A naive reader cannot determine which accent colors are available or what other CSS variables beyond `--bg`, `--text`, `--accent` participate in the palette system.

---

## Severity Summary

| Severity   | Count | Finding IDs |
|------------|-------|-------------|
| Blocker    | 0     | — |
| Major      | 5     | F1, F2, F6, F7, F8 |
| Nit        | 8     | F3, F4, F5, F9, F10, F11, F12, F13 |
| **Total**  | **13** | |

No Blockers were found. The five Major findings are: (1) the "A.T. Field" honeycomb concept is never defined, (2) the MAGI name-to-source mapping (Melchior/Balthasar/Casper ↔ BT/RADIO/USB) must be inferred from layout position, (3) "baked images" and palette tinting vs. re-baking is left as an undecided "or" without defining the existing asset pipeline, (4) MCU field names (`mcu_backcar_state`, `mcu_light_state`) and NwdAudioService are used without any definition for a reader unfamiliar with the headunit's MCU layer, and (5) the day/night vs. speed-palette priority rule is stated only for the normal zone, leaving the caution/emergency behavior unspecified. Eight Nits cover dangling references ("R-13", "LAMP", "S"), shorthand ("etc.", "..."), boundary-value ambiguity in threshold logic, mockup-specific concepts ("drift"), and unenumerated lists (13 MORE tiles, 5 accent colors, metadata side-channels).
