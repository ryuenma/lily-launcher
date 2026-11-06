# EGM Stage 7 — Style Critic (Mode: REVIEW)

**Target:** `docs/designs/nge-terminal-interface-v3.md`
**FeatureRange:** lines 1–236
**Style Standard:** none supplied — audited against persona vectors: Hyperbole & Marketing Fluff, Jargon & Buzzwords, Filler Words & Wordiness, Subjective Quality Judgments, Synonym Redundancy.
**Reviewer stance:** naive fresh reader; dry, direct, factual engineering prose expected.

**Overall verdict:** Not style-clean. 1 Blocker, 8 Major, 9 Nit. The document functions as a design description but repeatedly leans on subjective branding, unmeasured performance claims, and recycled phrasing that obscures testable requirements. The Blocker is a terminology contradiction on touch-target size that will produce inconsistent implementations even though it is a style issue. The Majors are dominated by subjective quality judgments and unprovable marketing language in the Problem and Alternatives sections.

---

## Findings

### Finding 1 — Blocker

**Affected Scope:** Goals (L30), Mockup Architecture (L154), SVG Hex Grid (L188), Non-Functional Requirements (L232)

**Style Rationale:** Inconsistent terminology for the same concept — "18mm thumb targets" (L30), "66x76 touch targets" (L154), "40px diameter" (L188), "Touch targets ≥ 44px (WCAG 2.5.5)" (L232). A reader cannot reconcile whether 40px, 44px, 66×76, or 18mm is normative. This is not a value dispute; it is synonym/redundancy failure where four different phrasings for "touch target size" are used without a single canonical measurement. Style rule violated: one concept, one term, one unit. Blocks implementation because an implementer must guess which number to satisfy.

### Finding 2 — Major

**Affected Scope:** The Problem (L19)

**Style Rationale:** Subjective Quality Judgment cluster: "lacks the urgent, authoritative, machine-like presence," "feels like a NERV command center," "communicate system status at a glance," "While functional." None of these is measurable or verifiable. "Urgent" and "authoritative" are evaluative adjectives attached to a UI. "At a glance" is an unmeasured time-perception claim. "Feels like" makes the requirement depend on reader taste. Persona vector requires claims to be factual, measurable, verifiable. This paragraph reads as design intent but functions as marketing framing.

### Finding 3 — Major

**Affected Scope:** Overview (L15), CUJ 2 (L62), CUJ 3 (L68)

**Style Rationale:** Hyperbole & Marketing Fluff: "Palette instantly shifts" (L62, L68). "Instantly" is an unprovable universal timing claim with no frame budget or latency number. "Zero glow" (L13) is stated as a universal without scope. "More animations (pulse, ticker marquee, marching stripes, spectrum)" (L15) pairs a vague superlative "More" with an unrelated list. These phrases add signal noise; a precise document would state "within one frame" or omit timing.

### Finding 4 — Major

**Affected Scope:** The Problem (L21)

**Style Rationale:** Subjective descriptor + anthropomorphic filler: "no awareness of vehicle speed," "calm greens below 60 km/h," "caution yellows," "emergency reds." "Awareness" attributes cognition to software without definition. "Calm" is an evaluative color judgment that cannot be tested. "Caution" and "emergency" are functional zone names reused as color names, creating synonym redundancy with the later "normal/caution/emergency" zone identifiers (L115–117). Prefer "palette for zone NORMAL (green #...)". Additionally, "Additionally," (L21) is a filler connector that adds no information.

### Finding 5 — Major

**Affected Scope:** Technical Plan — SVG Hex Grid (L184), Non-Functional Requirements (L230)

**Style Rationale:** Unmeasurable comparative performance claims: "SVG is used instead of Canvas to reduce rendering overhead," "SVG hex grid is lighter than Canvas for static elements." No baseline, no measurement, no device condition. "Overhead" and "lighter" are subjective comparatives that cannot be verified from the document. Under persona vectors, performance claims must carry metrics (RAM, ms, frames) or be removed. These sentences justify an architectural choice on unprovable grounds.

### Finding 6 — Major

**Affected Scope:** Alternatives Considered — A (L204–206), B (L210–212), D (L222–224)

**Style Rationale:** Subjective Quality Judgments without quantification: "Minimal visual change, lower risk" (Alt A), "Higher development effort, risks breaking performance targets" (Alt B), "Headlight sensor may not be connected" (Alt D), "SVG + CSS is sufficient" (Alt B), "may not be" / "risks" / "sufficient" / "Minimal" / "Higher" are all unmeasured evaluative terms. Effort and risk are stated as adjectives rather than estimates, board constraints, or failure modes. "User explicitly wants NGE" (L206) and "User requested time-based" (L224) appeal to preference rather than requirement; the document never anchors these to a requirement ID.

### Finding 7 — Major

**Affected Scope:** Open Questions (L44–48)

**Style Rationale:** Meta-commentary filler + subjective recommendation: "all OFL-licensed, free to use. Resolved." "free to use" is vague promotional phrasing for a license fact (OFL already states the permission). "confirmed by user" (Q2, Q4) and "recommended as supplement" (Q3) record process history, not requirements. "Resolved." and "confirmed by user" add noise without adding a testable criterion. "Mockup fidelity — mockup is design spec" (Q5) uses "design spec" synonym for mockup without defining the relationship.

### Finding 8 — Major

**Affected Scope:** Cross-scope synonym redundancy — layout-shift phrasing

**Location:** L15 "(no layout shift)", L62 "on top of existing UI", L121 "(overlay, not layout-shifting)", L156 "no layout shift"

**Style Rationale:** Same concept restated four times with variant phrasing: "overlay," "no layout shift," "not layout-shifting," "on top of existing UI." This is textbook Synonym Redundancy. The reader must infer all four mean "hazard/warning elements are absolutely positioned and do not reflow the layout." A single defined term (e.g., "non-reflowing overlay") used once would suffice.

### Finding 9 — Major

**Affected Scope:** Alternatives Considered — A, B, D (L204–224)

**Style Rationale:** Adjacent synonym redundancy: Alternative A lists Cons "Doesn't address the user's desire for a terminal interface" and immediately follows with Reason for Rejection "User explicitly wants NGE terminal interface." These two adjacent lines justify rejection with the same proposition restated. Across the full scope, "The user wants" appears 6+ times (L19, L21, L44–48, L205–206, L223–224) with variants "wants / explicitly wants / requested / confirmed / recommended / desire." One requirement statement with an ID would replace all of them.

### Finding 10 — Nit

**Affected Scope:** Overview (L13, L15), Mockup Architecture (L135)

**Style Rationale:** Marketing-flavored adjectives: "Key findings:" (L13), "Canonical mockup" (L15, L135). "Canonical" is an evaluative branding term meaning "official" without an authority or version-control rule defining it. "Key" is filler weighting. The factual content underneath is specific (file name, size, date); the adjectives add no information.

### Finding 11 — Nit

**Affected Scope:** CUJ 1 (L56), CUJ 2 (L62), CUJ 3 (L68)

**Style Rationale:** Wordiness and imprecision: CUJ 1 Then is an ~80-word mega-sentence stacking palette, background, hazard stripes, labels, readout, keys, header, and day/night into one clause chain. "Prominent" (speed readout) and "~240 hexes" are unmeasurable ("prominent" by what metric; "~" by what tolerance). CUJ 2 and CUJ 3 repeat the boilerplate "All functionality retained." verbatim — filler that should be stated once globally, not per journey.

### Finding 12 — Nit

**Affected Scope:** Jargon & Buzzwords — L48, L140, L160, L164, L168, L174, L212

**Style Rationale:** Undefined technical buzzwords obscuring meaning: "baked images" (L48, L174), "drift-proof" (L140), "simulated-clock drift override" (L15), "Metadata side-channels (codes, layers, protocols)" (L160), "State: S (..., ...) + prefs" (L164), "accent picker" / "USB phone" (L168), "tape deck" (L212). Each is a compressed or metaphorical label without definition. "Side-channels" in particular borrows a security term for a benign metadata display. "Baked" and "tape deck" require a glossary; without one, a fresh reader cannot determine scope or implementation meaning.

### Finding 13 — Nit

**Affected Scope:** Speed Tracking (L109), Non-Functional Requirements (L231)

**Style Rationale:** Unquantified "early": "early palette transition," "early transition detection," "Accelerometer supplement for early transitions." "Early" is a vague temporal buzzword with no threshold, delta, or GPS-fix-rate comparison. Complementary filler: "supplement" is used as a noun/verb interchangeably (L46, L97, L109, L218) without a defined heading or criterion for what qualifies as a supplement versus a primary path.

### Finding 14 — Nit

**Affected Scope:** SVG Hex Grid (L188), Non-Functional Requirements (L233)

**Style Rationale:** Subjective/filler evaluative terms: "for comfortable touch" (L188), "must work together" (L233). "Comfortable" is unmeasurable without a biomechanical or target-size criterion (and conflicts with Finding 1's size inconsistency). "Must work together" states a wish without a combination rule; the following sentence "Day/night overrides color when in normal speed zone" is imprecise about which properties are overridden (background? accent? all variables?).

### Finding 15 — Nit

**Affected Scope:** Mockup Architecture (L162)

**Style Rationale:** Dangling reference: "No glow (flat fill only; R-13)." The token "R-13" is opaque without an anchor, table of requirements, or footnote within lines 1–236. Style rule: every identifier must be resolvable on first read by a naive team member. "(flat fill only; R-13)" pairs a precise rendering rule with an unresolvable label.

### Finding 16 — Nit

**Affected Scope:** Mockup Architecture State/Simulator (L164–169)

**Style Rationale:** Imprecise ellipsis and compressed labels: "S (speed, source, track, night, dayNightMode, usb, bt, reverse, call, ...) + prefs" (L164) uses "..." instead of a closed list or a reference to a state table, making the state schema unverifiable. "USB phone" (L169) is ambiguous shorthand (USB audio? USB host status? phone via USB?). "Spectrum" (L15) and "marching stripes" are animation names without duration or loop definition in the audited range.

### Finding 17 — Nit

**Affected Scope:** Alternatives Considered — C (L218)

**Style Rationale:** Contradictory filler under a defined heading: Alternative C's Reason for Rejection reads "Not rejected — primary approach. Accelerometer is supplement." The document labels the section "Alternatives Considered," implying each entry is considered and rejected, then immediately states "Not rejected." The self-contradiction is stylistic noise; either reclassify as "Accepted / Primary" or remove the entry from the alternatives list.

### Finding 18 — Nit

**Affected Scope:** Overview (L13), Technical Plan (L174)

**Style Rationale:** Process-history filler and vague imperative: "analyzed via opencode-cli-free (glm-5.3-flash-free)" (L13) records the tool used for image analysis, which is provenance noise for a design document (unless the review process requires it). "Changes needed:" (L174) is a vague English imperative substituting for a typed requirements list (add/modify/remove with IDs). "Update the mockup to reflect final design" (item 6) is circular — no detailable outcome.

---

## Summary counts

- **Blocker:** 1
- **Major:** 8 (Findings 2–9)
- **Nit:** 9 (Findings 10–18)
- **Total:** 18

## Cross-cutting style notes

1. **Voice and mood:** The document alternates between precise telegraphic architecture lists (L138–169) and narrative marketing prose (L19, L21, L204–224). The narrative passages are where every Major subjective judgment occurs.
2. **Requirement language:** No requirement IDs, no RFC 2119 keywords (MUST/SHOULD/MAY), no acceptance thresholds in the Problem, Goals, or Alternatives prose. Performance numbers appear only in Goals and Non-Functional Requirements (L32, L230) and are not echoed where behavioral claims are made.
3. **Redundancy pattern:** The canopy redundancies — "no layout shift" (×4), "user wants/requested" (×6), "all functionality retained" (×2), "canonical mockup retained for comparison" (L135) — suggest copy-paste from a template that was not deduplicated into a single source of truth.
