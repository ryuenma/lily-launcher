# NGE Terminal Interface for Lily Launcher (v4 — DeepSeek design target, 7" Touch Optimized)

*   **Author:** ryuenma
*   **Status:** Draft — v4 design target: DeepSeek v4 simulator (`mockup/nge-terminal-deepseek-v4.html`). All findings from the EGM consolidated review (2026-11-06) have been incorporated per owner decisions.
*   **Date:** 2026-11-06
*   **Canonical mockup:** `mockup/nge-terminal-deepseek-v4.html` (84 KB, single file). GLM/Crouton v4 variants and all v3 variants are archived to `mockup/archive/`.

---

## 1. Terms (glossary)

*   **A.T. Field** — decorative honeycomb hex-grid background pattern (~240 hexes (SVG pattern in mockup)) in the mockup; a visual reference only, not a functional component. In the native app the honeycomb is drawn on Canvas.
*   **MAGI** — three trapezoid indicators representing the audio sources: **Melchior = BT**, **Balthasar = RADIO**, **Casper = USB** (left-to-right layout order).
*   **LAMP** — headlights status indicator in the status rail (ON/OFF), sourced from `mcu_light_state` if available, else reflects time-based night mode.
*   **S** — mockup-only central state object (speed, source, track, night, dayNightMode, usb, bt, reverse, call, …). In the app these live in `Car.kt` fields + `HomeActivity` state, not a single object.
*   **Baked images** — static raster assets (app icons, album art, vendor bitmaps) that are NOT recolored; they keep original colors in all zones.
*   **Canvas shapes** — Canvas-drawn elements (background, honeycomb, MAGI trapezoids, speed ladder, hazard stripes, knob, text) whose color is driven by the zone enum (native equivalent of CSS variables). Zone change = swap Paint colors + invalidate; no tinting or re-baking.
*   **mcu_backcar_state / mcu_light_state** — MCU telemetry fields for reverse-gear and headlight status, polled via the existing `sys()` helper in `Car.kt`. Headlights are conditional ("if available, else time-based only").
*   **NwdAudioService** — vendor audio service constraint; no audio/sound additions permitted (Non-Goals).
*   **Drift** — mockup-only concept: the simulator tests that day/night switches at 17:00/05:00 even if the real clock differs; the app uses the system clock directly.
*   **R-13** — antislop rule: zero glow (no text-shadow/box-shadow for glow; flat solid fill only).
*   **Metadata side-channels** — status-rail readouts (codes, layers, protocols) shown alongside the main display; content defined in the canonical mockup.
*   **MORE sheet (15 tiles)** — Radio, USB Music, Video, Dashcam, Sound EQ, AUX, Files, Car, CAN, Wheel keys, Android, Screen off, Home setup.
*   **Accent picker (5 colors)** — selectable accent color applied to latched/active elements; values defined in the canonical mockup.

---

## 2. Overview

This design adapts the Lily launcher's UI from its Teenage Engineering EP-series aesthetic to a Neon Genesis Evangelion (NGE) terminal interface design language, with a dynamic color palette that shifts based on vehicle speed. All upstream features and workflows are retained; only the visual layer and layout are reworked.

The design is driven by 14 NGE terminal interface reference images (6 original + 8 additional), analyzed via opencode-cli-free (glm-5.3-flash-free). Key findings: NGE uses **zero glow** (flat solid fill), **Mincho serif** (Noto Serif JP) for Japanese kanji warnings, **pointy-top hexagons** as the primary geometric element, and **bilingual JP/EN** text throughout.

**v4 update (design target = DeepSeek v4 simulator):** 7" 2DIN headunit (1024×600, 18mm thumb). Touch targets enlarged (hexes 66×76, knob 104, sheet buttons ≥44, speed readout 116px). Day/night mode is time-based (17:00 night, 05:00 morning), toggleable (AUTO/MANUAL) with clock-drift override. Warning animations overlay on top of existing UI (no layout shift). Speed indicator enlarged to 116px odometer with 24-seg ladder. Volume knob enlarged to 104px with lavender ring. MAGI system uses 3 trapezoid indicators (196×44). 15-tile MORE sheet (13 + Magisk + Restart). More animations (pulse, ticker marquee, marching stripes, spectrum). Canonical mockup: `mockup/nge-terminal-deepseek-v4.html`.

**Platform note (B-01):** SVG/CSS applies to the mockup only. The native app renders hexagons via Canvas path drawing with Paint colors driven by the zone enum (see §8.5). The "SVG lighter than Canvas" claim is mockup-scoped and is not carried into the app.

---

## 3. The Problem

The current Lily launcher uses a Teenage Engineering EP-series aesthetic (silver body, black display band, square pads, orange knob). While functional, it lacks the machine-like presence of a terminal interface. The user wants a UI that feels like a NERV command center — where information density, color-coded alert states, and hexagonal geometry communicate system status at a glance. Palette transition completes within one render frame; no layout shift occurs during zone changes.

Additionally, the launcher has no awareness of vehicle speed. The color palette is static regardless of driving conditions. The user wants the palette to shift based on speed: calm greens below 60 km/h, caution yellows between 60–100 km/h, and emergency reds above 100 km/h.

---

## 4. Goals

*   Adapt the launcher's visual layer to NGE terminal interface design language.
*   Implement dynamic color palette based on vehicle speed (GPS-sourced).
*   Retain all upstream features and workflows (BT/RADIO/USB source switching, volume, Android Auto, reverse camera, calls, settings).
*   Use Canvas path drawing for hexagonal button grids in the native app (SVG is mockup-only).
*   Make speed thresholds configurable by the user.
*   Optimize for 7" 2DIN touch screen (1024×600, 18mm thumb targets (≈113px at 160dpi)).
*   Time-based day/night mode (17:00 night, 05:00 morning), toggleable.
*   Maintain performance targets (responsive; Home return target < 200ms; RAM budget 22MB — runtime resource usage is the priority, not APK size).

---

## 5. Non-Goals

*   No changes to AIDL services, broadcast receivers, or vendor integrations **except** the new sensor integrations required by this design: GPS (`LocationManager`/`GPS_PROVIDER`), accelerometer (`SensorManager`/`TYPE_ACCELEROMETER` or `TYPE_LINEAR_ACCELERATION`), and MCU telemetry (`mcu_light_state`, `mcu_backcar_state`) which are read-only polls via the existing `sys()` helper.
*   No new permissions beyond GPS access (`ACCESS_FINE_LOCATION`).
*   No audio/sound additions (NwdAudioService constraint).
*   No internet permission.
*   No changes to the install/rollback scripts.
*   Scalability and APK size are not design concerns (private-use app, not a live service).

---

## 6. Open Questions

*   **Q1**: Font licensing — Noto Serif JP (OFL) + Oswald (OFL) + JetBrains Mono (OFL) — all OFL-licensed, free to use. Resolved.
*   **Q2**: Speed thresholds (60/100 km/h) configurable in settings — confirmed by user.
*   **Q3**: Accelerometer for early transition detection — implemented in v1 as an optional supplement (GPS remains authoritative).
*   **Q4**: Hex grid via Canvas path drawing in the app (SVG in mockup) — confirmed.
*   **Q5**: Mockup fidelity — mockup is design spec; app uses Canvas drawing. Acceptance: side-by-side diff at 1024×600 for the 9 CUJs.
*   **Q6**: Day/night mode toggleable (AUTO/MANUAL), time-based (17:00 night, 05:00 morning), not headlight-based.

---

## 7. Critical User Journeys (CUJs)

### CUJ 1: Normal Driving (< 60 km/h)

*   **Given**: Vehicle speed below 60 km/h, GPS fix available.
*   **When**: User presses Home or launcher is active.
*   **Then**: UI renders in normal palette on black background. Honeycomb hex grid background (A.T. Field, ~240 hexes (SVG pattern in mockup)). No hazard stripes. JP kanji labels (見現時刻表示, 危険). Speed readout (116px, prominent). All source keys (BT/RADIO/USB) functional. Header: "DIRECT LINK CONNECTION : LILY". Day/night based on time (17:00–05:00 = night).

### CUJ 2: Highway Driving (60–100 km/h)

*   **Given**: Vehicle speed between 60–100 km/h.
*   **When**: Speed crosses the caution threshold (with hysteresis ±5 km/h and ≥1 s dwell).
*   **Then**: Palette shifts to yellow/black. Yellow hazard stripes (top/bottom overlay, no layout shift). Octagonal CAUTION seal. Header changes to "CAUTION : SPEED LIMIT". Ticker panel shows repeating CAUTION text. Warning overlay appears on top of existing UI (pulse animation). All functionality retained.

### CUJ 3: High-Speed Driving (> 100 km/h)

*   **Given**: Vehicle speed above 100 km/h.
*   **When**: Speed crosses the emergency threshold (with hysteresis ±5 km/h and ≥1 s dwell).
*   **Then**: Palette shifts to black/red. Red hazard stripes (red/black). Octagonal EMERGENCY seal. Header changes to "第一種戦闘配置 : LILY". Ticker panel shows repeating EMERGENCY text. Warning overlay (pulse animation). Hex grid cells turn red. All functionality retained.

### CUJ 4: Source Switching

*   **Given**: Launcher is active, any speed zone.
*   **When**: User presses BT/RADIO/USB hex key.
*   **Then**: Source switches without opening an app. MAGI node diagram (3 trapezoid indicators, 196×44, Melchior=BT / Balthasar=RADIO / Casper=USB) updates (active node fills). Display area updates with source-specific content. Palette remains speed-based.

### CUJ 5: Reverse Camera Overlay

*   **Given**: Vehicle in reverse gear (`mcu_backcar_state`).
*   **When**: Reverse gear engaged.
*   **Then**: Full-screen overlay with "REVERSE" banner and hazard stripes. Camera feed appears on top. Reverse only occurs in the normal speed zone; the reverse monitor takes the full screen (no palette/zone interaction, no fancy animations).

### CUJ 6: Day/Night Toggle

*   **Given**: Launcher is active.
*   **When**: User toggles day/night mode (AUTO/MANUAL).
*   **Then**: In AUTO mode, theme follows time (17:00–05:00 = night, 05:00–17:00 = day). In MANUAL mode, user can force day or night. Toggle persists across sessions. Day/night modulates only the normal zone (speed zone wins in caution/emergency).

### CUJ 7: GPS Denied / Unavailable

*   **Given**: GPS permission denied or GPS disabled at system level.
*   **When**: HomeActivity launches.
*   **Then**: Permission rationale dialog shown at first launch; if denied → speed stays 0 / normal zone, no nag loop. If GPS disabled at system level → one-time notice + normal zone. No crash.

### CUJ 8: Stale Fix Timeout

*   **Given**: No GPS fix for 3 seconds (e.g., tunnel).
*   **When**: Staleness TTL expires.
*   **Then**: Speed reverts to normal zone (clears any latched emergency). No permanent emergency lock.

### CUJ 9: Combined Override Precedence

*   **Given**: Reverse engaged at night above 100 km/h (theoretical; reverse is impossible at high speed in practice).
*   **Then**: Reverse takes full screen (no palette interaction); speed zone is normal during reverse. Precedence: reverse > day/night > speed-zone palette (but reverse only occurs in normal zone).

---

## 8. Technical Plan (Architecture)

### 8.1 Hardware

| Component | Hardware | Android API | Purpose |
|---|---|---|---|
| GPS | GlobalSat (glgps) | LocationManager/GPS_PROVIDER | Vehicle speed (m/s → km/h) |
| Accelerometer | mc32x0 G-sensor (TWI2) | SensorManager/TYPE_ACCELEROMETER or TYPE_LINEAR_ACCELERATION | Early transition detection (optional supplement) |
| Headlights | MCU (mcu_light_state) | Settings.System (if available, else time-based only) | Night mode (supplementary) |

### 8.2 Speed Tracking

```
HomeActivity.onResume() → requestLocationUpdates(GPS_PROVIDER, 500ms, 0m, listener)
  → onLocationChanged()
  → if accuracy > 20m or speed < 0: ignore fix
  → Location.getSpeed() (m/s) × 3.6 = km/h
  → updateSpeedZone(km/h)   // hysteresis ±5 km/h, dwell ≥1 s
  → applyPalette(zone)
HomeActivity.onPause() → removeUpdates(listener)
Staleness: no fix for 3 s → revert to normal zone
```

Accelerometer supplement: `TYPE_LINEAR_ACCELERATION` if available (fallback `TYPE_ACCELEROMETER` with high-pass filter); magnitude > 2.5 m/s² sustained ≥500 ms → hint "transition likely" (pre-warm, skip debounce). GPS is always authoritative; accelerometer never overrides the zone. If sensor absent → GPS-only, no crash.

### 8.3 Palette System

```
updateSpeedZone():
  if speed >= emergencyThreshold (with hysteresis): zone = 'emergency'
  elif speed >= cautionThreshold (with hysteresis): zone = 'caution'
  else: zone = 'normal'

applyPalette(zone):
  swap Paint colors for all Canvas-drawn elements (background, honeycomb, MAGI, speed ladder, hazard stripes, knob, text)
  toggle hazard stripes overlay (no layout shift)
  update header text (JP kanji)
  update ticker panel (show/hide, color)
  update node diagram fills (MAGI)
  update hex button colors
  update octagonal seal
  update JP kanji labels
  update warning overlay (show/hide with pulse animation)
  invalidate Canvas
```

Thresholds stored in `Car.prefs` (existing `prefs`/`DEFAULTS`/`SheetView` pattern), configurable via settings. Defaults: caution 60 km/h, emergency 100 km/h. Hysteresis ±5 km/h, dwell ≥1 s. Boundary: caution if speed >= cautionThreshold; emergency if speed >= emergencyThreshold.

**Precedence (B-05, B-15):** reverse > day/night > speed-zone palette. Reverse only occurs in the normal zone (full-screen overlay, no palette interaction). In caution/emergency zones, speed palette wins; day/night modulates only in the normal zone (brightness).

**Cost model (B-14):** paint-swap + invalidate; negligible CPU. No baked variants, no tinting (B-12). 22MB RAM budget acceptable.

**Palette + animation approach (B-05):** D + 4 — hybrid palette (Canvas paint-swap + ColorFilter where a raster must adapt) + full animation set (overlay march/pulse/ticker + MAGI fills + speed-ladder segments + knob rotation + spectrum/EQ bars + blinking seals). Highest fidelity to the mockup; performance risk to the 200ms Home-return target must be profiled on device.

### 8.4 Day/Night

Time-based: night 17:00, morning 05:00. AUTO mode follows the system clock. MANUAL mode forces day or night. Headlights (`mcu_light_state`) are supplementary: if available, headlight-on can force night as an override; if unavailable, time-based only. Single `resolveTheme()` function: MANUAL > headlight-override > time-based > auto. Reuse existing `theme` pref key (no new `dayNightMode` key).

### 8.5 Native Rendering (Canvas)

*   Hexagons: Canvas `drawPath` with pointy-top polygon; Paint color from zone enum.
*   MAGI trapezoids: Canvas `drawPath` with clip; active = filled with accent, inactive = outline.
*   Hazard stripes: overlay drawn on top of existing UI (no layout shift).
*   Octagonal seals: Canvas path or clip.
*   Honeycomb background: Canvas pattern (~240 hexes (SVG pattern in mockup)).
*   Knob: Canvas arc + ticks; rotation driven by volume.
*   Fonts: Noto Serif JP (Mincho) + Oswald (condensed) + JetBrains Mono, loaded as Android resources (subsetted for size, but runtime resource usage is the priority).

### 8.6 Mockup Architecture (HTML/CSS/JS)

Canonical mockup: `mockup/nge-terminal-deepseek-v4.html` (84 KB, single file). Pixel layout, DOM nesting, and pseudocode live in the mockup; the design doc describes behavior and contracts only. See the mockup for: 32px status rail, 42px banner, 338px main (152px telemetry rail · 18px kanji band · 638px stage · 216px MAGI stack), 18px ticker, 170px deck (11-hex honeycomb, 104px knob, 480px speed plate), 116px speed readout, 24-seg ladder, 15-tile MORE sheet (13 + Magisk + Restart), 5-color accent picker, simulated clock for drift testing.

### 8.6.1 7" layout revision (implementation, supersedes §8.6 metrics)

The mockup's CSS pixel sizes were authored for a 1:1 desktop render. On the actual 7" 1024×600 panel (≈170 dpi, 1 px = 1 dp), 9 px labels and 67×78 hexes read as ~10 mm touch targets and borderline-unreadable metadata. The native layout therefore keeps the four bands and the NGE grammar but reallocates the 600 px:

| Band | v4 mockup | 7" app | Notes |
|---|---|---|---|
| Status rail | 32 | **40** | 11 px labels, 17 px clock, 24 px chips |
| Banner | 42 | **54** | 19 px title, 16 px kanji, 18 px seal |
| Main | 338 | **300** | 168 telemetry · 24 kanji · 616 stage · 216 MAGI |
| Ticker | 18 | **22** | 11 px marquee |
| Deck | 170 | **184** | 80×92 hexes · 112 knob · 398×168 speed plate |

* **Keypad:** 11 hexes, 80×92 at 69 px pitch (was 67×78 at 58.25) — +19 % linear per pad.
* **Stage:** 128×152 album hex, 42 px track title (52 px radio), 20 px artist, 12 px progress bar, 4×48 px data cells; the Android Auto map is replaced by an `AA LINK` chip so the track block gets the full 436 px width.
* **Speed plate:** 124 px odometer, 96 px seal, 24-segment ladder with 13 px segments.
* **One visualiser only:** the telemetry `.signals` strip is removed. The stage spectrum (36 bars, CSS `@keyframes spec` equivalents) is the single animation loop and is drawn by `SpectrumView` at 20 fps in its own 436×38 layer, so the loop does not repaint the whole Canvas.
* Geometry lives in `Nge.kt`; the mockup remains the visual reference for colour and shape, not for pixel sizes.


*   Fonts: Noto Serif JP (Mincho) + Oswald (condensed) + JetBrains Mono.
*   State: `S` (speed, source, track, night, dayNightMode, usb, bt, reverse, call, …) + prefs.
*   Palette: `applyPalette()` based on speed zone.
*   Time-based day/night: 17:00 night, 05:00 morning, toggleable.
*   Render: display content, node diagram, hex buttons, metadata, MAGI, speed plate, knob.
*   Simulator: speed slider 0–160, source toggles, threshold inputs, accent picker, day/night toggle, headlights, BT device, BT track, USB phone, reverse/call events, volume.

### 8.7 App Architecture (Kotlin)

The existing app uses View + Canvas with baked images. Changes needed:

1. Add GPS speed tracking (`LocationManager`/`GPS_PROVIDER`) per §8.2.
2. Add speed zone state to the app's state machine (`Car.kt`: speed field, speed zone field, threshold prefs; `Car` is a plain prefs-backed class, not a data class).
3. Modify rendering to use speed-based palette (Canvas paint swap per §8.3 and §8.5).
4. Add speed threshold settings to settings (via `SheetView.kt`, not a separate `SettingsSheet`).
5. Add time-based day/night mode (replace headlight-based with time-based, keep headlight as override per §8.4).
6. Update the mockup reference (canonical: `mockup/nge-terminal-deepseek-v4.html`).

### 8.8 SVG Hex Grid (mockup only)

Hexagonal buttons rendered as SVG `<polygon>` elements with text labels in the HTML mockup. SVG is used instead of Canvas in the mockup to reduce rendering overhead and allow CSS styling. Each hex has: fill (none outline or filled active/latched), stroke (current color, changes with palette), text (button label in Oswald). Size: 66×76 for comfortable touch (18mm thumb). **Not applicable to the native app** — see §8.5.

### 8.9 MAGI Node Diagram

Three trapezoid indicators (196×44) arranged vertically (y=139/199/259), representing BT/RADIO/USB source keys. Each trapezoid: `clip-path: polygon(9% 0%, 91% 0%, 100% 100%, 0% 100%)`. Active state: filled with accent color, black text. Inactive state: outline only, current color text. Label below: Melchior/Balthasar/Casper (JP Mincho). Mapping: Melchior=BT, Balthasar=RADIO, Casper=USB.

---

## 9. Alternatives Considered

### Alternative A: Keep TE Aesthetic, Add Speed Palette Only

*   **Pros**: Minimal visual change, lower risk.
*   **Cons**: Doesn't address the user's desire for a terminal interface.
*   **Reason for Rejection**: User explicitly wants NGE terminal interface.

### Alternative B: Full Canvas Rewrite with Dynamic Palette

*   **Pros**: Complete control over rendering.
*   **Cons**: Higher development effort, risks breaking performance targets.
*   **Reason for Rejection**: Canvas path drawing + paint swap is sufficient; no full rewrite needed.

### Alternative C: Use GPS Only (No Accelerometer)

*   **Accepted — primary approach.** GPS-only is the baseline and is fully sufficient. Accelerometer is an optional supplement for early transition hinting, not a requirement. GPS speed is reliable; adding accelerometer requires proof of transition lag. Both GPS and accelerometer are implemented in v1 (accelerometer as supplement per §8.2).

### Alternative D: Headlight-Based Day/Night (Original)

*   **Pros**: Uses existing `mcu_light_state`.
*   **Cons**: Headlight sensor may not be connected; user wants time-based.
*   **Reason for Rejection**: User requested time-based (17:00 night, 05:00 morning) with manual toggle. Headlights retained as conditional override.

### Alternative E: SVG Hex Grid in Native App

*   **Rejected.** SVG is a web/mockup construct; the native app uses Canvas path drawing. SVG would require WebView or a third-party library, adding overhead. Canvas path drawing achieves the same visual result with native performance.

**Comparison (Canvas vs SVG for native app):**

| Dimension | Canvas path drawing | SVG (via WebView/lib) |
|---|---|---|
| Performance risk | Low (native, single surface) | Medium (WebView overhead, extra layer) |
| Effort | Medium (path math, Paint swap) | Low (reuse mockup SVG) |
| APK/RAM impact | Minimal | +WebView or lib (~MBs) |
| Reversibility | High | Medium (lib dependency) |
| Fidelity to mockup | High (same shapes/colors) | Exact (same SVG) |

Measurable differentiator: Canvas keeps Home return < 200ms and RAM < 22MB without an additional rendering layer.

---

## 10. Non-Functional Requirements

*   **Performance**: Responsive; Home return < 200ms (measured via systrace/profiler). RAM < 22MB. Runtime resource usage is the priority (not APK size). Canvas paint-swap + invalidate is negligible CPU; D+4 animation set must be profiled on device.
*   **Resilience**: GPS unavailable (tunnel) → revert to normal zone after 3 s staleness TTL. Accelerometer absent → GPS-only, no crash. GPS permission denied → speed 0 / normal zone, no nag loop.
*   **Accessibility**: Hex buttons keyboard-navigable (Tab/Enter). Focus indicators required. Touch targets ≥ 44px hit-area (WCAG 2.5.5); 18mm visual ≈ 113px at 160 dpi. Authoritative touch target: 66×76 (per v4 mockup).
*   **Theme consistency**: Speed-based palette and time-based day/night work together per the precedence table (§8.3). Day/night modulates only the normal zone.

---

## 11. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| GPS fix lag causes UI strobe at thresholds | Medium | Medium | Hysteresis ±5 km/h + ≥1 s dwell per zone transition (B-08) |
| Accelerometer false positives trigger early transitions | Medium | Low | Accelerometer only hints; GPS is authoritative; disable sensor if noisy |
| Canvas rendering exceeds 200ms Home return | Low | High | Paint-swap + invalidate (no re-bake); profile on device; side-by-side diff vs mockup |
| Location permission denied permanently | Medium | Low | Speed 0 / normal zone; no nag loop; one-time notice |
| GPS disabled at system level | Medium | Low | One-time notice + normal zone |
| Tunnel staleness latches emergency | Low | Medium | 3 s staleness TTL reverts to normal |
| MCU telemetry unavailable (headlights/reverse) | Medium | Low | Conditional: if available use it; else time-based/reverse-disabled |
| Font loading impact on startup | Low | Low | Subset fonts; measure startup time |
| Mockup↔app fidelity drift | Medium | Medium | Side-by-side diff at 1024×600 for the 9 CUJs (M-06) |

**Security & Privacy:** Location data is held in memory only; never logged to disk (private-use app, no internet permission). No location telemetry leaves the device.

**Failure modes:** (1) GPS fix accuracy > 20m → ignore fix, keep last good speed. (2) GPS speed < 0 → ignore. (3) No fix for 3 s → normal zone. (4) Sensor absent → GPS-only, no crash. (5) Permission denied → speed 0 / normal zone. (6) Reverse engaged → full-screen overlay, normal zone only.

---

## 12. Detailed Implementation Plan

### Phase 1 — GPS + State (depends on: none)

1.  `launcher/app/src/main/AndroidManifest.xml` (modify): add `ACCESS_FINE_LOCATION` permission.
2.  `launcher/app/src/main/.../HomeActivity.kt` (modify): add `LocationManager`/`GPS_PROVIDER` registration in `onResume`, `removeUpdates` in `onPause`; runtime permission request with rationale dialog; speed conversion (m/s × 3.6); accuracy filter (> 20m ignore, speed < 0 ignore); staleness TTL (3 s → normal zone); `updateSpeedZone()` with hysteresis (±5 km/h, ≥1 s dwell); `applyPalette()` (Canvas paint swap + invalidate).
3.  `launcher/app/src/main/.../Car.kt` (modify): add `speed` field, `speedZone` field, threshold prefs (caution/emergency) in existing `prefs`/`DEFAULTS`; reuse `theme` pref key for day/night (no new `dayNightMode` key). `Car` is a plain prefs-backed class, not a data class.

### Phase 2 — Palette + Day/Night (depends on: Phase 1)

4.  `launcher/app/src/main/.../HomeActivity.kt` (modify): `resolveTheme()` (MANUAL > headlight-override > time-based > auto); time-based switching (17:00 night, 05:00 morning); headlight override if `mcu_light_state` available.
5.  Canvas rendering (modify): paint-swap palette application; hazard stripes overlay (no layout shift); warning overlay (pulse); ticker marquee; MAGI trapezoid fills; speed ladder segments; knob rotation; octagonal seals; spectrum/EQ bars; blinking seals.

### Phase 3 — Settings + Manifest (depends on: Phase 1)

6.  `launcher/app/src/main/.../SheetView.kt` (modify): add speed threshold configuration (caution km/h, emergency km/h) using existing `SheetView`/`Car.prefs` pattern; day/night mode toggle (AUTO/MANUAL).
7.  `launcher/app/src/main/res/...` (modify): add GPS permission rationale string, speed-related string resources (threshold labels, zone names).

### Phase 4 — Build & Test (depends on: Phases 1–3)

8.  Build: `./gradlew assembleRelease` must succeed.
9.  Device test: install on headunit; verify all states render (side-by-side diff vs canonical mockup at 1024×600 for CUJ 1–9).
10. Performance: verify Home return < 200ms, RAM < 22MB (measured via profiler).
11. Touch targets: verify all interactive elements ≥ 44px hit-area (authoritative: 66×76).
12. Day/night: verify time-based switching at 17:00 and 05:00.
13. Thresholds: verify hysteresis ±5 km/h + ≥1 s dwell (no strobe).
14. Antislop: re-run `antislop` skill on the diff.
15. Mockup action: archive non-canonical variants to `mockup/archive/`; canonical mockup is `mockup/nge-terminal-deepseek-v4.html`.

### Target Files Summary

| # | File | Type | Action |
|---|---|---|---|
| 1 | `mockup/nge-terminal-deepseek-v4.html` | HTML/CSS/JS mockup | Canonical reference (already exists) |
| 2 | `docs/designs/nge-terminal-interface-v3.md` | Design specification | Modify (this revision) |
| 3 | `launcher/app/src/main/AndroidManifest.xml` | Android manifest | Modify (add ACCESS_FINE_LOCATION) |
| 4 | `launcher/app/src/main/.../HomeActivity.kt` | Kotlin Activity | Modify (GPS speed tracking, zone state, palette, day/night) |
| 5 | `launcher/app/src/main/.../Car.kt` | Kotlin class (prefs-backed) | Modify (speed, speedZone, thresholds, theme reuse) |
| 6 | `launcher/app/src/main/.../SheetView.kt` | Kotlin UI component | Modify (threshold config, day/night toggle) |
| 7 | `launcher/app/src/main/res/...` | Android resources | Modify (permission rationale, speed strings) |

---

## 13. Verification Plan

*   **Build**: `./gradlew assembleRelease` must succeed.
*   **Device test**: install on headunit; verify all states render (side-by-side diff vs canonical mockup at 1024×600 for CUJ 1–9).
*   **Speed simulation**: use mockup simulator to verify palette transitions (design intent only; native verification requires on-device measurement).
*   **Performance**: verify RAM < 22MB, Home return < 200ms (measured via systrace/profiler).
*   **Accessibility**: Tab navigation through hex grid, focus indicators visible.
*   **Touch targets**: verify all interactive elements ≥ 44px hit-area (authoritative 66×76).
*   **Day/night**: verify time-based switching at 17:00 and 05:00.
*   **Thresholds**: verify hysteresis ±5 km/h + ≥1 s dwell (no strobe).
*   **Antislop**: re-run `antislop` skill on the diff.
*   **GPS**: verify permission flow, denied-state (normal zone), staleness TTL (3 s → normal), accuracy filter (> 20m ignore).

---

## 14. Additional Resources

*   Design target mockup (v4): `mockup/nge-terminal-deepseek-v4.html` (canonical reference for implementation).
*   Mockup (v3 baseline, archived): `mockup/archive/nge-terminal.html`.
*   DeepSeek mockup (v3, archived): `mockup/archive/nge-terminal-deepseek.html`.
*   GLM mockup (v3, archived): `mockup/archive/nge-terminal-glm.html`.
*   v4 variants (archived): `mockup/archive/nge-terminal-glm-v4.html`, `mockup/archive/nge-terminal-crouton-v4.html`.
*   Design brief: `docs/design-brief-nge-terminal.md`.
*   Integration notes: `docs/INTEGRATION.md`.
*   Test plan: `docs/TESTING.md`.
*   EGM consolidated review: `docs/designs/egm-review/consolidated_review_report.md`.
*   NGE reference: `terminal-interface-design` skill (14 images analyzed).
*   Antislop audit: PASS (no findings).

---

## 15. Future Improvements

*All items below require a separate review/safety assessment before inclusion; none are in scope for this design.*

*   **Accelerometer integration**: early palette transitions (implemented in v1 as supplement per §8.2).
*   **Speed-based audio**: adjust volume or mute at high speeds (safety feature — requires safety review).
*   **GPS navigation integration**: show next turn or distance to destination (requires safety review).
*   **Customizable palette**: allow user to define color schemes per speed zone (requires UX review).
*   **Speed logging**: log speed data for trip analysis (requires privacy review; location never logged by default).

---

*Revised with EGM consolidated review decisions (2026-11-06).*
