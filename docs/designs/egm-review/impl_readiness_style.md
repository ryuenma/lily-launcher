# Implementation Readiness — Style Review (REVIEW Mode)

**Target:** `docs/designs/nge-terminal-interface-v3.md` lines 237–312 (Detailed Implementation Plan through end)
**Persona:** Style Goldfish (technical copy editor: clarity, precision, brevity, objective framing)
**Style Standard:** None supplied — audit against persona's default vectors only.
**Mode:** REVIEW

## Findings

### S1 — Vague count adjective without referent: "the 11 feedback points"
- **Severity:** Major
- **Affected Scope:** Line 244 ("incorporating the 11 feedback points: 7" 2DIN layout, ...")
- **Style Rationale:** "the 11 feedback points" assumes the reader knows which 11 points. It is a count without enumeration or cross-reference. Either list them, link to the source, or drop the numeral. A fresh reader cannot verify completeness against "11."

### S2 — Imprecise verification verbs (unmeasurable pass criteria)
- **Severity:** Major
- **Affected Scope:** Lines 278–285 ("Build must succeed", "verify all states render", "verify palette transitions", "Re-run antislop audit")
- **Style Rationale:** Most bullets use bare "verify"/"must succeed" with no observable pass condition. "All states render" does not say which states or what constitutes rendering. "Re-run antislop audit" omits the expected outcome (PASS? zero Majors?). Objective test criteria should name the expected result or threshold for each check.

### S3 — Filler meta-language in the plan header
- **Severity:** Nit
- **Affected Scope:** Lines 243, 249, 254, 259, 264, 269, 274 ("Type / Scope")
- **Style Rationale:** The label "Type / Scope" and its values ("HTML/CSS/JS mockup", "Design specification", "Kotlin Activity") are filler structure. The file path already implies the type; the scope adds nothing measurable. Delete the label and keep only the substantive "Proposed Changes."

### S4 — Value-laden marketing adjectives in the mockup summary
- **Severity:** Major
- **Affected Scope:** Line 244 ("more animations", "enlarged"); Line 292 ("canonical reference for implementation")
- **Style Rationale:** "More animations" is relative and uncounted; "enlarged" lacks a baseline (enlarged relative to v3 — say so). "Canonical reference" is a subjective endorsement, not a factual qualifier. Replace with measurable descriptors (e.g., "adds four animations: ...", "MAGI trapezoid 196×44 vs. 160×36 in v3") or remove the adjectives.

### S5 — Unmeasurable performance claims mixed with measurable ones
- **Severity:** Major
- **Affected Scope:** Line 281 ("RAM < 25MB, Home return < 200ms") vs. lines 279–280 ("all states render", "palette transitions")
- **Style Rationale:** Line 281 states concrete thresholds (good). Lines 279–280 state goals without conditions (poor). Mixing both in the same list weakens the credible ones. Apply the line-281 pattern (value + unit + method) to lines 279–280 or mark them as manual checks.

### S6 — Subjective safety tag without criterion
- **Severity:** Nit
- **Affected Scope:** Line 306 ("Adjust volume or mute at high speeds (safety feature)")
- **Style Rationale:** The parenthetical "(safety feature)" is an unsubstantiated value judgment. No safety criterion is cited (no regulation, no test threshold). Either cite the basis or delete the tag.

### S7 — Synonym redundancy: passive constructions vary without distinction
- **Severity:** Nit
- **Affected Scope:** Lines 278, 283, 284 ("must succeed" / "Verify" / "Verify")
- **Style Rationale:** Alternating "must succeed" and "Verify" for the same acceptance-test act is inconsistent voice. Standardize on one form — preferably "Verify: <observable condition>" — to signal that every bullet is a test, not an assertion.

## Summary

Range is mostly clean: specification prose is dense and factual, and line 281 already models good measurable style. The main defects are vague enumerations (S1, S4), verification bullets missing observable pass criteria (S2, S5, S7), and filler/meta labels (S3, S6). No hyperbole and no marketing fluff beyond the two adjectives in S4. Recommended fix order: S1 → S2 → S4 → S5 → S3 → S6 → S7.

**Counts:** Blockers: 0, Major: 4, Nit: 3
