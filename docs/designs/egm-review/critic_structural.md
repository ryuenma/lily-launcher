# Structural Review — nge-terminal-interface-v3.md (FeatureRange: 1–236)

- **Mode:** REVIEW
- **Standard:** `feature-spec-standard.md`
- **Target:** `docs/designs/nge-terminal-interface-v3.md`, lines 1–236
- **Scope note:** Review ends at line 236 (`---` thematic break). All structural checks apply only to lines 1–236.

## 1. Structure Map (extracted heading hierarchy)

| Line | Heading | Level |
|------|---------|-------|
| 1 | NGE Terminal Interface for Lily Launcher (v4 — DeepSeek design target, 7" Touch Optimized) | H1 |
| 9 | Overview | H2 |
| 17 | The Problem | H2 |
| 23 | Goals | H2 |
| 34 | Non-Goals | H2 |
| 42 | Open Questions | H2 |
| 50 | Critical User Journeys (CUJs) | H2 |
| 52, 58, 64, 70, 76, 82 | CUJ 1–6 | H3 under CUJs |
| 90 | Technical Plan (Architecture) | H2 |
| 92, 100, 111, 133, 172, 182, 190 | Hardware, Speed Tracking, Palette System, Mockup Architecture, App Architecture, SVG Hex Grid, MAGI Node Diagram | H3 under Technical Plan |
| 200 | Alternatives Considered | H2 |
| 202, 208, 214, 220 | Alternative A–D | H3 under Alternatives Considered |
| 228 | Non-Functional Requirements | H2 |

## 2. Blueprint comparison

Required by the standard: **Problem Statement**, **Goals & Non-Goals**, **Proposed Architecture**, **Alternatives Considered**, **Risks & Mitigations**.
Present: Alternatives Considered exact match. All other required sections are missing, renamed, or split. See findings below.

## 3. Findings

### Finding 1 — Missing Risks & Mitigations section
- **Severity:** Blocker
- **Affected Scope:** Whole document; Lines 228–234 (`Non-Functional Requirements`) substitute in place
- **Discrepancy Description & Rationale:** The standard requires an explicit `Risks & Mitigations` section covering failure modes, security implications, scalability limits, and mitigations. No header by that name appears in lines 1–236. The only risk-adjacent content is the single Resilience bullet at line 231 (GPS fallback in tunnel). There is no placement for security implications, scalability limits, or complexity mitigations, so the Risks & Limits principle fails template compliance and is not verifiable by a fresh reader.

### Finding 2 — Problem Statement header misspelled / renamed
- **Severity:** Major
- **Affected Scope:** Line 17 — `## The Problem`
- **Discrepancy Description & Rationale:** The required header is `Problem Statement`. The document uses `The Problem`, which fails exact-name matching against the blueprint. Content is present (TE aesthetic deficit + static palette), but a strict structural validator or naive reader searching for the required header will report it missing.

### Finding 3 — Goals & Non-Goals split into two separate H2s
- **Severity:** Major
- **Affected Scope:** Lines 23 (`## Goals`), 34 (`## Non-Goals`), 42 (`## Open Questions` intervenes after)
- **Discrepancy Description & Rationale:** The standard specifies a single `Goals & Non-Goals` section. The document splits it into two adjacent H2s and then places `Open Questions` immediately after. Content coverage is adequate (L25–40), but the single-section symmetry required by the template is broken.

### Finding 4 — Proposed Architecture header renamed
- **Severity:** Major
- **Affected Scope:** Line 90 — `## Technical Plan (Architecture)`
- **Discrepancy Description & Rationale:** The required header is `Proposed Architecture`. `Technical Plan (Architecture)` does not match. Sub-content does cover component layout (H3 Hardware, Speed Tracking, Palette System) and data flow (L102–107), so this is a naming/template deviation rather than a content gap, but it fails strict blueprint compliance.

### Finding 5 — Optional/extra sections not in blueprint precede required sections
- **Severity:** Nit
- **Affected Scope:** Line 9 (`## Overview`), Line 42 (`## Open Questions`), Line 50 (`## Critical User Journeys (CUJs)`), Line 228 (`## Non-Functional Requirements`)
- **Discrepancy Description & Rationale:** None of these four H2s appear in the standard's required list. They do not break hierarchy, but a strict symmetry check flags them as non-canonical sections. `Overview` also appears before `Problem Statement`, inverting the blueprint's opening order.

### Finding 6 — Title / filename version mismatch
- **Severity:** Nit
- **Affected Scope:** Line 1 vs filename `nge-terminal-interface-v3.md`; Line 4 Status: `Draft — v4 design target`
- **Discrepancy Description & Rationale:** The file is named `v3` while the H1 title and metadata explicitly declare `v4`. A fresh reader cannot tell which version is canonical from the filename alone. No heading-hierarchy impact, but it breaks structural symmetry between filename and document header.

## 4. Formatting & hierarchy checks (no findings)

- **Heading hierarchy:** PASS. Hierarchy is strictly H1 → H2 → H3 with no level skips and no orphan H3s. Every H3 sits under an H2; every H2 sits under the single H1.
- **Lists:** PASS. All bullet lists use consistent `*   ` (asterisk + three spaces) markers.
- **Tables:** PASS. The Hardware table (L94–98) has matching column counts and valid separator row.
- **Code blocks:** PASS. All three fenced blocks (L102, L114, L137) are properly opened and closed. No language tags are used, which the standard does not mandate.
- **Links:** PASS (no links to validate). Cross-references use inline code (`` `mockup/...` ``); no broken Markdown links or malformed URLs in scope.

## 5. Severity counts

- **Blocker:** 1 (Finding 1)
- **Major:** 3 (Findings 2, 3, 4)
- **Nit:** 2 (Findings 5, 6)
- **Total:** 6 findings
