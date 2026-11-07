# UI Design Verification — Lily Launcher NGE Reskin

*   **Date:** 2026-11-07
*   **Scope:** colour/typography tokens of the NGE reskin, baked layout tokens, bake-source consistency, design-doc internal consistency, 13 MORE tiles + 5 accents.
*   **Method:** static read of the four artifacts + headless Chromium measurement of the two mockups at 1024×600 (`getComputedStyle` / `getBoundingClientRect`). No files were modified.
*   **Files inspected:**
    *   `mockup/nge-terminal-deepseek-v4.html` (canonical mockup per design doc)
    *   `mockup/index.html` (bake source, per commit history)
    *   `launcher/app/src/main/assets/ui/layout.json`
    *   `docs/designs/nge-terminal-interface-v3.md`
    *   supporting: `launcher/app/src/main/java/com/lily/launcher/{Ui,HomeView,KnobView,Car}.kt`

---

## Verdict at a glance

| # | Check | Result |
|---|---|---|
| 1 | Extract CSS vars + fonts from canonical mockup | Done — see §1 |
| 2 | Extract baked `vars` (light/dark/night) | Done — see §2 |
| 3 | Baked colours vs stated NGE palette (`#0a0a12` / `#b9afcb` / `#ff4a1c` / `#888`) | **PARTIAL FAIL** — bg/text/text2 match for light+dark; **accent does not** (`#ff4a1c` appears nowhere in `layout.json`; default accent is `#FF5A1F`) |
| 3b | Baked colours vs the design doc's *declared canonical mockup* | **FAIL** — the canonical mockup is a different palette (green `#4de89a` ink on `#0b0b13`, accent `#258ac3`) and does not even define `--body`/`--ink2`/`--ink3` |
| 4 | `mockup/index.html` theme blocks vs `layout.json` | **PASS** — 12/12 theme values identical; accents array 5/5 identical |
| 5 | Design doc internal consistency | **FAIL** — 13 defects, incl. 2 numeric contradictions and 1 provenance gap (§5) |
| 6 | 13 MORE tiles + 5 accents vs design doc | **Tiles PASS** (13/13, same names, same order). **Accents PARTIAL** — count 5 ✓, values 3/5 ✓ vs mockup, 5/5 vs `index.html` |

**Headline:** the baked theme tokens are a faithful copy of `mockup/index.html`, and they match the NGE palette quoted in the task for light/dark. They are **not** derived from `mockup/nge-terminal-deepseek-v4.html`, which the design doc names as the canonical mockup and which uses a completely different (green-phosphor MAGI) palette. The bake and the design doc are describing two different designs.

---

## 1. Canonical mockup CSS variables

`mockup/nge-terminal-deepseek-v4.html`, `.screen` base block (lines 48–54):

| Variable | Value | Line |
|---|---|---|
| `--bg` | `#0b0b13` | 48 |
| `--panel` | `#050509` | 48 |
| `--ink` | `#4de89a` | 49 |
| `--ink-2` | `#f5b400` | 49 |
| `--ink-dim` | `#2c7252` | 49 |
| `--alert` | `#ff4a1c` | 50 |
| `--alert-2` | `#e31c23` | 50 |
| `--rule` | `rgba(77,232,154,.28)` | 51 |
| `--on-fill` | `#000` | 52 |
| `--accent` | `#258ac3` | 53 |
| `--button` | `#b9afcb` | 54 |

Zone / theme overrides:

| State | Overrides | Line |
|---|---|---|
| `[data-zone="caution"]` | `--ink:#f5b400 --ink-2:#ff6a00 --ink-dim:#7a5b09 --alert:#f5b400 --alert-2:#ff6a00 --rule:rgba(245,180,0,.30) --bg:#0b0a07` | 58–63 |
| `[data-zone="emergency"]` | `--ink:#ff4a1c --ink-2:#e31c23 --ink-dim:#6d1c0d --alert:#e31c23 --alert-2:#ff4a1c --rule:rgba(255,74,28,.34) --on-fill:#4de89a --bg:#000000` | 64–70 |
| `[data-theme="day"][normal|caution]` | `--bg:#0e0e17` | 71 |
| `[data-theme="night"]` | `--bg:#030307` | 72 |
| `[data-theme="night"][emergency]` | `--bg:#000` | 73 |

The element ships with `data-theme="day" data-zone="normal"` (line 567), so the **computed** boot background is `#0e0e17`, never `#0b0b13`.

Fonts (`:root`, lines 12–14), confirmed computed:

| Token | Stack |
|---|---|
| `--font-jp` | `"Noto Serif JP","Hiragino Mincho ProN","Yu Mincho",serif` |
| `--font-en` | `"Oswald","Rajdhani","Eurostile","Helvetica Neue",sans-serif` (computed resolves to Oswald first) |
| `--font-mono` | `"JetBrains Mono","Consolas",monospace` |

**Naming note:** this mockup has **no** `--text`, `--text2` or `--bg`-family token set of the shape used by the baked JSON. Its text tokens are `--ink` / `--ink-2` / `--ink-dim`. Any mapping from `layout.json`'s `--ink/--ink2/--ink3/--body` to this file is therefore invented, not inherited.

Accent picker in this mockup (5 swatches, rendered order): `#ff4a1c`, `#258ac3`, `#2fb35a`, `#f5b400`, `#b9afcb`; runtime default `S.accent = "#258ac3"` (line 1041).

---

## 2. Baked `vars` (`launcher/app/src/main/assets/ui/layout.json`)

| Token | light | dark | night |
|---|---|---|---|
| `--ink` | `#b9afcb` | `#b9afcb` | `#888` |
| `--ink2` | `#888` | `#888` | `#666` |
| `--ink3` | `#555` | `#555` | `#444` |
| `--body` | `#0a0a12` | `#0a0a12` | `#050508` |

`light` and `dark` are byte-identical; `night` is the only distinct token set.

Other baked blocks: `size = [1024, 600]`; `accents` = 5 entries; `meta.more` = 13 entries; `img` 114 / `rect` 66 / `text` 3.

Consumption (Kotlin): `Ui.kt:127 themeVar(name)` reads `vars[theme][name]`; only two tokens are actually read — `--ink` (`KnobView.kt:62`) and `--ink3` (`HomeView.kt:180`). **`--ink2` and `--body` are baked but never consumed**; the background is carried by raster `bg@light/dark/night.png`.

---

## 3. Baked colours vs mockup palette

### 3a. Against the NGE palette quoted in the verification brief

| Token | Target | Baked light/dark | Baked night | Result |
|---|---|---|---|---|
| bg | `#0a0a12` | `#0a0a12` | `#050508` | match (light/dark) |
| text | `#b9afcb` | `#b9afcb` | `#888` | match (light/dark) |
| text2 | `#888` | `#888` | `#666` | match (light/dark) |
| accent | `#ff4a1c` | — (no accent token in `vars`) | — | **fail** |

`#ff4a1c` occurs **0 times** in `layout.json` (case-insensitive search). The accent is delivered by `accents[]` + `meta.settings[accent].options`, whose first/default value is `#FF5A1F` (also `Car.kt:543 DEFAULT "accent" to "#FF5A1F"`). `#E8332A` is the only red in the picker, not `#ff4a1c`.

### 3b. Against the design doc's canonical mockup (`nge-terminal-deepseek-v4.html`)

| Token | Canonical mockup | `index.html` | `layout.json` | Verdict |
|---|---|---|---|---|
| bg | `#0b0b13` base / `#0e0e17` day / `#030307` night | `#0a0a12`, night `#050508` | `#0a0a12`, night `#050508` | baked ≠ mockup |
| text | `#4de89a` (green) | `#b9afcb` | `#b9afcb` | baked ≠ mockup |
| text2 | `--ink-dim #2c7252` (no `--ink2`) | `#888` | `#888` | baked ≠ mockup |
| accent | `#258ac3` | `#FF5A1F` (`--pacc #ff4a1c` page chrome) | `#FF5A1F` | baked ≠ mockup |

Three independent divergences (bg, ink, accent), plus an incompatible token schema (4 tokens vs `--bg/--ink/--ink-2/--ink-dim/--alert/--alert-2/--rule/--on-fill/--accent/--button`). The reskin is not a bake of the doc's canonical mockup.

Partial bridge: the app's speed-zone stripe colours are taken from the canonical mockup — `HomeView.kt:88-89` uses `#f5b400` (caution `--alert`) and `#e31c23` (emergency `--alert-2`). So the implementation mixes the mockup's zone alerts with `index.html`'s theme tokens.

---

## 4. Bake source (`mockup/index.html`) vs baked JSON

Theme blocks, verified in-browser by toggling `data-theme` / `data-night`:

| Token | light | dark | dark + `data-night="1"` |
|---|---|---|---|
| `--body` | `#0a0a12` | `#0a0a12` | `#050508` |
| `--ink` | `#b9afcb` | `#b9afcb` | `#888` |
| `--ink2` | `#888` | `#888` | `#666` |
| `--ink3` | `#555` | `#555` | `#444` |

**PASS — 12/12 values identical to `layout.json`.** Accents (`index.html:2465`):

```
['TE orange','#FF5A1F','Org'],['Blue','#258AC3','Blu'],['Green','#2FB35A','Grn'],['Yellow','#F5B400','Yel'],['Red','#E8332A','Red']
```

**PASS — 5/5 identical to `layout.json` `accents[]`** (same names, hexes, shorts, same order).

Issues inside the bake source itself:

*   `light` and `dark` are identical, so "day" mode is a dark theme. The comment on the `light` block still reads *"Day: bright polished aluminium, near-white with pure-black ink so it stays crisp in direct sun"* while its values are near-black `#0a0a12` + lavender `#b9afcb` — a stale comment contradicting its own declarations.
*   Two accents coexist: page chrome `--pacc #ff4a1c` (line 16) and device `--accent #FF5A1F` (line 278). The bake took the device one.

---

## 5. Design-doc internal consistency (`docs/designs/nge-terminal-interface-v3.md`)

### 5a. Dimensions that verify against the canonical mockup (measured)

| Claim | Doc line | Measured | Result |
|---|---|---|---|
| Canvas 1024×600 | 34 | 1024×600 | pass |
| status rail 32px | 210 | 32 | pass |
| banner 42px | 210 | 42 | pass |
| main 338px | 210 | 338 | pass |
| telemetry rail 152px | 210 | 152 | pass |
| kanji band 18px | 210 | 18 | pass |
| stage 638px | 210 | 638 | pass |
| MAGI column 216px | 210 | 216 | pass |
| ticker 18px | 210 | 18 | pass |
| deck 170px | 210 | 170 | pass |
| MAGI trapezoid 196×44 | 34, 236 | 196×44 | pass |
| speed readout 116px | 34, 210 | 116px | pass |
| speed ladder 24 segments | 34, 210 | 24 | pass |
| hex keypad 11 hexes | 210 | 11 | pass |
| hex 66×76 | 34, 232 | polygon 65.8×76 (67.3×77.7 bbox w/ 1.5px stroke) | pass |
| knob 104px | 34 | 104 | pass |
| knob "lavender ring" | 34 | stroke `#b9afcb` | pass |
| MORE sheet 13 tiles | 34, 210 | 13 | pass |
| accent picker 5 colours | 34, 210 | 5 swatches | pass |
| rows sum: 32+42+338+18+170 | — | = 600 | pass |
| columns sum: 152+18+638+216 | — | = 1024 | pass |

### 5b. Defects

| ID | Defect | Evidence | Severity |
|---|---|---|---|
| D1 | **RAM budget contradiction: 22 MB vs 25 MB.** Lines 57 ("RAM budget 22MB"), 190 ("22MB RAM budget acceptable") and 278 ("RAM < 22MB") vs lines 284 ("RAM < 25MB"), 333 ("RAM < 25MB") and 359 ("RAM < 25MB"). | grep of doc | **Medium** |
| D2 | **"452px speed plate" is wrong.** Line 210. Measured `.speed` = **480px** (`1024 − 20 deck padding − 20 gaps − 372 keypad − 132 knob-wrap`). Off by 28px. | Chromium `getBoundingClientRect` | **Medium** |
| D3 | **Doc and bake name different canonical designs.** Doc line 6/210/344 declares `mockup/nge-terminal-deepseek-v4.html` canonical; the baked tokens come from `mockup/index.html`. The doc never mentions `index.html` anywhere (0 hits). | grep + §3 above | **High** |
| D4 | **"~240 hexes" is unsupported.** Lines 12, 89, 204. The mockup draws the A.T. Field as one SVG `<pattern id="hny">` (24.25×42 tile) filling a 1024×600 rect — an unbounded tiling, no discrete ~240-hex set. Approx. drawn cells ≈ 1,200. | mockup 569–580 | Low |
| D5 | **MAGI orientation is contradicted.** Line 13 "(left-to-right layout order)" and line 236 "arranged horizontally". Measured: the three trapezoids stack **vertically** at x=852, y=139/199/259. | `getBoundingClientRect` | Low |
| D6 | **Trapezoid clip-path values differ.** Line 236 `polygon(15% 0%, 85% 0%, 100% 100%, 0% 100%)`; mockup line 247 `polygon(9% 0,91% 0,100% 100%,0 100%)`. | static read | Low |
| D7 | **CUJ count contradiction.** Lines 78 and 303 say "the 6 CUJs"; the doc defines CUJ 1–9 (§7, lines 83–136) and lines 332/357 say "CUJ 1–9". | grep | Medium |
| D8 | **"18mm visual ≈ 86px at 160 dpi" is arithmetically wrong.** Line 286. 18 mm = 0.709 in → **113.4 px** at 160 dpi (120.2 px at the panel's real 169.5 dpi for 7"/1024×600). 86 px is 13.65 mm. Conversely 66×76 px = 10.5×12.1 mm at 160 dpi, so "66×76 … (18mm thumb)" (line 232) is also inconsistent. | arithmetic | Medium |
| D9 | **Version naming mismatch.** Line 1 titles the doc "v4" while the file is `nge-terminal-interface-v3.md` and §7/§12 still call it v3. | Line 1 | Low |
| D10 | **MCU read path inconsistency.** Line 18 says `mcu_light_state` is "polled via the existing `sys()` helper"; line 148 lists its Android API as `Settings.System`. | Lines 18 vs 148 | Low |
| D11 | **Self-contradicting alternative.** Line 256 labels Alternative C "Use GPS Only (No Accelerometer)" as "Accepted — primary approach" and in the same bullet states "Both GPS and accelerometer are implemented in v1". | Line 256 | Low |
| D12 | **Doc contains no colour values at all** (0 matches for `#RRGGBB`). It defers accent values to "the canonical mockup" (line 24), but the bake used `index.html`'s values — so the doc cannot arbitrate the palette. | grep | Medium |
| D13 | **Day/night is nominal.** `light` and `dark` token sets are identical in `index.html` and `layout.json`, yet CUJ 6 / §8.4 require a day/night modulation of the normal zone. Any difference must come from `bg@*.png`, not from tokens. | §2, §4 | Medium |

**Dangling references:** none. All 9 referenced paths resolve — `docs/design-brief-nge-terminal.md`, `docs/INTEGRATION.md`, `docs/TESTING.md`, `docs/designs/egm-review/consolidated_review_report.md`, `mockup/archive/{nge-terminal,nge-terminal-deepseek,nge-terminal-glm,nge-terminal-glm-v4,nge-terminal-crouton-v4}.html`. Mockup size claim "84 KB" is accurate (86,013 B = 84.0 KiB).

---

## 6. 13 MORE tiles and 5 accents vs design doc

### Tiles — PASS (13/13, exact names, exact order)

Doc §1 (line 23) vs `layout.json meta.more[]`:

| # | Doc | `layout.json` | Component |
|---|---|---|---|
| 1 | Radio | Radio | `com.nwd.radio/.home_horizontalActivity` |
| 2 | USB Music | USB Music | `com.nwd.android.music.ui/.home_horizontalActivity` |
| 3 | Video | Video | `com.nwd.android.video.ui/.home_horizontalActivity` |
| 4 | Dashcam | Dashcam | `com.nwd.carkithd2cam/.home_horizontalActivity` |
| 5 | Sound EQ | Sound EQ | `com.nwd.audioset/.home_horizontalActivity` |
| 6 | AUX | AUX | `com.nwd.auxin/.home_horizontalActivity` |
| 7 | Files | Files | `com.nwd.filemanager/.FileManager` |
| 8 | Car | Car | `com.android.car.setting/.home_horizontalActivity` |
| 9 | CAN | CAN | `com.nwd.can.setting/.ui.MainActivity` |
| 10 | Wheel keys | Wheel keys | `com.android.launcher/com.nwd.wheel.learning.MainActivity` |
| 11 | Android | Android | `com.android.settings/.Settings` |
| 12 | Screen off | Screen off | special `screenoff` |
| 13 | Home setup | Home setup | special `settings` |

The mockup's own `TILES` array (line 1667–1681) carries the same 13 entries in the same order (uppercase labels). PASS on all three artifacts.

### Accents — count PASS, values FAIL vs the doc's canonical mockup

| # | `layout.json` | Canonical mockup | `index.html` ACCENTS |
|---|---|---|---|
| 1 | `#FF5A1F` "TE orange" | `#ff4a1c` | `#FF5A1F` |
| 2 | `#258AC3` Blue | `#258ac3` | `#258AC3` |
| 3 | `#2FB35A` Green | `#2fb35a` | `#2FB35A` |
| 4 | `#F5B400` Yellow | `#f5b400` | `#F5B400` |
| 5 | `#E8332A` Red | `#b9afcb` | `#E8332A` |

3/5 shared with the canonical mockup, 2/5 divergent (`#FF5A1F`↔`#ff4a1c`, `#E8332A`↔`#b9afcb`); 5/5 identical to `index.html`. Doc §1 line 24 ("values defined in the canonical mockup") is therefore not satisfied by the bake. `meta.settings[accent].options` duplicates the same 5 hexes, so the picker and the list are internally consistent — just not consistent with the doc's stated source.

---

## 7. Findings summary

**Must fix before the reskin can be called verified**

1. Decide which mockup is canonical. Either
   (a) promote `mockup/index.html` to canonical in the design doc (it is the actual bake source and the actual NGE palette), or
   (b) re-bake from `nge-terminal-deepseek-v4.html` (green `#4de89a` ink, `#0b0b13`/`#0e0e17`/`#030307` bg, `#258ac3` default accent).
   Today the doc, the bake and the app's zone stripes each follow a different source.
2. Add `#ff4a1c` to the accent set (or restate the accent target as `#FF5A1F`). The stated NGE accent exists nowhere in the baked tokens.
3. Resolve 22 MB vs 25 MB RAM budget (D1) — pick one number and apply to lines 57, 190, 278, 284, 333, 359.
4. Correct `452px` → `480px` speed plate (D2), and `6 CUJs` → `9 CUJs` (D7).

**Should fix**

5. `18mm ≈ 86px at 160 dpi` → `≈113px at 160 dpi` (≈120px on the 7" panel); reconcile `66×76` against the 18 mm claim (D8).
6. MAGI orientation wording: vertical stack, top-to-bottom (D5); clip-path `9%/91%` (D6).
7. `~240 hexes` → describe the SVG pattern, or drop the count (D4).
8. Give the doc a normative colour table with hex values (D12); drop the unused `--ink2`/`--body` tokens or start consuming them (`--body` is dead; background comes from `bg@*.png`).
9. Make `light` differ from `dark`, or relabel the theme pair (D13); fix the stale "near-white" comment in `index.html`.

**Nice to have**

10. Rename `nge-terminal-interface-v3.md` → `…-v4.md` or revert the "v4" title (D9).
11. Fix `Settings.System` vs `sys()` for MCU telemetry (D10); fix Alternative C's status label (D11).

---

## Appendix — raw evidence

*   Chromium measurements (canonical mockup, 1024×600):
    `rail 1024×32 · banner 1024×42 · main 1024×338 · telemetry 152 · jp-band 18 · stage 638 · magi-col 216 · ticker 1024×18 · deck 1024×170 · keypad 372×150 · knob-wrap 132×130 · speed 480×152 · trap 196×44 · zone-seal 100×100 · hex 67.3×77.7 (11) · segs 24 · magi 3 (196×54 each incl. label) · tiles 13 · swatches [#ff4a1c,#258ac3,#2fb35a,#f5b400,#b9afcb] · .spd-num 116px`
*   Computed `.screen` tokens at boot (`data-theme="day"`, `data-zone="normal"`):
    `--bg #0e0e17 · --ink #4de89a · --accent #258ac3 · --button #b9afcb · --panel #050509 · --alert #ff4a1c · --rule rgba(77,232,154,.28) · --on-fill #000`
*   `index.html` computed theme tokens: light = dark = `#0a0a12 / #b9afcb / #888 / #555`; `dark+data-night=1` = `#050508 / #888 / #666 / #444` — identical to `layout.json`.
*   Arithmetic: `hypot(1024,600) = 1186.8 px`; `1186.8/7" = 169.5 dpi`; `18 mm × 160 dpi = 113.4 px`; `86 px = 13.65 mm`; `66×76 px = 10.5×12.1 mm @160 dpi`; deck `1024−20−20−372−132 = 480`.
*   Git: `a8bb5f8` (re-bake from NGE-themed mockup) precedes `bcce6c7` (v4 NGE mockups / design target); `layout.json` last changed in `a8bb5f8`, so the baked tokens predate and do not derive from `nge-terminal-deepseek-v4.html`.
