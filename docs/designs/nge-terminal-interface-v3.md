# NGE Terminal Interface for Lily Launcher (v4 — DeepSeek design target, 7" Touch Optimized)

*   **Author:** ryuenma
*   **Status:** Draft — v4 design target: DeepSeek v4 simulator (`mockup/nge-terminal-deepseek-v4.html`)
*   **Date:** 2026-11-06

---

## Overview

This design adapts the Lily launcher's UI from its Teenage Engineering EP-series aesthetic to a Neon Genesis Evangelion (NGE) terminal interface design language, with a dynamic color palette that shifts based on vehicle speed. All upstream features and workflows are retained; only the visual layer and layout are reworked.

The design is driven by 14 NGE terminal interface reference images (6 original + 8 additional), analyzed via opencode-cli-free (glm-5.3-flash-free). Key findings: NGE uses **zero glow** (flat solid fill), **Mincho serif** (Noto Serif JP) for Japanese kanji warnings, **pointy-top hexagons** as the primary geometric element, and **bilingual JP/EN** text throughout.

**v4 update (design target = DeepSeek v4 simulator)**: 7" 2DIN headunit (1024x600, 18mm thumb). Touch targets enlarged (hexes 66x76, knob 104, sheet buttons >=44, speed readout 116px). Day/night mode is time-based (17:00 night, 05:00 morning), toggleable (AUTO/MANUAL) with simulated-clock drift override. Warning animations overlay on top of existing UI (no layout shift). Speed indicator enlarged to 116px odometer with 24-seg ladder. Volume knob enlarged to 104px with lavender ring. MAGI system uses 3 trapezoid indicators (196x44). 13-tile MORE sheet. More animations (pulse, ticker marquee, marching stripes, spectrum). Canonical mockup: `mockup/nge-terminal-deepseek-v4.html` (GLM and Crouton v4 variants retained for comparison).

## The Problem

The current Lily launcher uses a Teenage Engineering EP-series aesthetic (silver body, black display band, square pads, orange knob). While functional, it lacks the urgent, authoritative, machine-like presence of a terminal interface. The user wants a UI that feels like a NERV command center — where information density, color-coded alert states, and hexagonal geometry communicate system status at a glance.

Additionally, the launcher has no awareness of vehicle speed. The color palette is static regardless of driving conditions. The user wants the palette to shift based on speed: calm greens below 60 km/h, caution yellows between 60-100 km/h, and emergency reds above 100 km/h.

## Goals

*   Adapt the launcher's visual layer to NGE terminal interface design language
*   Implement dynamic color palette based on vehicle speed (GPS-sourced)
*   Retain all upstream features and workflows (BT/RADIO/USB source switching, volume, Android Auto, reverse camera, calls, settings)
*   Use SVG for hexagonal button grids to reduce rendering overhead
*   Make speed thresholds configurable by the user
*   Optimize for 7" 2DIN touch screen (1024×600, 18mm thumb targets)
*   Time-based day/night mode (17:00 night, 05:00 morning), toggleable
*   Maintain performance targets (22MB RAM, 175ms Home return, 3.3MB APK)

## Non-Goals

*   No changes to AIDL services, broadcast receivers, or vendor integrations
*   No new permissions beyond GPS access
*   No audio/sound additions (NwdAudioService constraint)
*   No internet permission
*   No changes to the install/rollback scripts

## Open Questions

*   **Q1**: Font licensing — Noto Serif JP (OFL) + Oswald (OFL) + JetBrains Mono (OFL) — all OFL-licensed, free to use. Resolved.
*   **Q2**: Speed thresholds (60/100 km/h) configurable in settings — confirmed by user
*   **Q3**: Accelerometer for early transition detection — recommended as supplement to GPS
*   **Q4**: Hex grid via SVG — confirmed by user
*   **Q5**: Mockup fidelity — mockup is design spec; app uses baked images + Canvas

## Critical User Journeys (CUJs)

### CUJ 1: Normal Driving (< 60 km/h)

*   **Given**: Vehicle speed below 60 km/h, GPS fix available
*   **When**: User presses Home or launcher is active
*   **Then**: UI renders in green/yellow/blue palette on black background. Honeycomb hex grid background (A.T. Field, ~240 hexes). No hazard stripes. JP kanji labels (見現時刻表示, 危険). Speed readout (32px, prominent). All source keys (BT/RADIO/USB) functional. Header: "DIRECT LINK CONNECTION : LILY". Day/night based on time (17:00-05:00 = night).

### CUJ 2: Highway Driving (60-100 km/h)

*   **Given**: Vehicle speed between 60-100 km/h
*   **When**: Speed crosses 60 km/h threshold
*   **Then**: Palette instantly shifts to yellow/black. Yellow hazard stripes (top/bottom overlay, not layout-shifting). Octagonal CAUTION seal. Header changes to "CAUTION : SPEED LIMIT". Ticker panel shows repeating CAUTION text. Warning overlay appears on top of existing UI (pulse animation). All functionality retained.

### CUJ 3: High-Speed Driving (> 100 km/h)

*   **Given**: Vehicle speed above 100 km/h
*   **When**: Speed crosses 100 km/h threshold
*   **Then**: Palette instantly shifts to black/red. Red hazard stripes (red/black). Octagonal EMERGENCY seal. Header changes to "第一種戦闘配置 : LILY". Ticker panel shows repeating EMERGENCY text. Warning overlay (pulse animation). Hex grid cells turn red. All functionality retained.

### CUJ 4: Source Switching

*   **Given**: Launcher is active, any speed zone
*   **When**: User presses BT/RADIO/USB hex key
*   **Then**: Source switches without opening an app. MAGI node diagram (3 trapezoid indicators, 120×50px) updates (active node fills). Display area updates with source-specific content. Palette remains speed-based.

### CUJ 5: Reverse Camera Overlay

*   **Given**: Vehicle in reverse gear (mcu_backcar_state)
*   **When**: Reverse gear engaged
*   **Then**: Full-screen overlay with "REVERSE" banner and hazard stripes. Camera feed appears on top. Palette temporarily overridden to emergency (red) regardless of speed.

### CUJ 6: Day/Night Toggle

*   **Given**: Launcher is active
*   **When**: User toggles day/night mode (AUTO/MANUAL)
*   **Then**: In AUTO mode, theme follows time (17:00-05:00 = night, 05:00-17:00 = day). In MANUAL mode, user can force day or night. Toggle persists across sessions.

---

## Technical Plan (Architecture)

### Hardware

| Component | Hardware | Android API | Purpose |
|-----------|----------|-------------|---------|
| GPS | GlobalSat (glgps) | LocationManager/GPS_PROVIDER | Vehicle speed (m/s → km/h) |
| Accelerometer | mc32x0 G-sensor (TWI2) | SensorManager/TYPE_ACCELEROMETER | Early transition detection |
| Headlights | MCU (mcu_light_state) | Settings.System | Night mode (supplementary) |

### Speed Tracking

```
LocationManager → GPS_PROVIDER → onLocationChanged()
  → Location.getSpeed() (m/s) × 3.6 = km/h
  → updateSpeedZone(km/h)
  → applyPalette(zone)
```

Accelerometer supplement: detect acceleration > threshold for early palette transition before GPS fix updates.

### Palette System

```
updateSpeedZone():
  if speed > emergencyThreshold: zone = 'emergency'
  elif speed > cautionThreshold: zone = 'caution'
  else: zone = 'normal'

applyPalette(zone):
  set CSS variables (--bg, --text, --accent, etc.)
  toggle hazard stripes (overlay, not layout-shifting)
  update header text (JP kanji)
  update ticker panel (show/hide, color)
  update node diagram fills
  update hex button colors
  update octagonal seal
  update JP kanji labels
  update warning overlay (show/hide with pulse animation)
```

Thresholds stored in SharedPreferences, configurable via Settings sheet.

### Mockup Architecture (HTML/CSS/JS) — v4 design target (DeepSeek)

Canonical mockup: `mockup/nge-terminal-deepseek-v4.html` (84 KB, single file). GLM and Crouton v4 variants retained for comparison only.

```
mockup/nge-terminal-deepseek-v4.html
├── Layout (1052x628 chassis, 1024x600 screen):
│   ├── 32px status rail (clock, speed zone, LAMP state, drift-proof day/night)
│   ├── 42px banner (header JP/EN, octagonal seal)
│   ├── 338px main:
│   │   ├── 152px telemetry rail (speed, volume, source, theme)
│   │   ├── 18px vertical kanji band
│   │   ├── 638px stage (display content, hazard overlay, MAGI)
│   │   └── 216px MAGI stack (3 trapezoids 196x44, Melchior/Balthasar/Casper)
│   ├── 18px ticker (marquee, emergency/caution only)
│   └── 170px deck:
│       ├── 11-hex honeycomb (A.T. Field, ~hex grid)
│       ├── 104px volume knob (lavender ring, ticks, accent arc)
│       └── 452px speed plate (116px odometer + 24-seg ladder + threshold markers)
├── CSS: NGE terminal styling
│   ├── Honeycomb background (SVG, A.T. Field, ~240 hexes)
│   ├── Pointy-top hexagon buttons (SVG, 10 buttons, 66x76 touch targets)
│   ├── MAGI node diagram (3 trapezoid indicators, 196x44)
│   ├── Hazard stripes (top/bottom overlay, red or yellow, no layout shift)
│   ├── Octagonal warning seals (clip-path, corner, pulse in emergency)
│   ├── Ticker panel (repeating text, emergency/caution only, marquee)
│   ├── Warning overlay (pulse animation, absolute, pointer-none)
│   ├── Metadata side-channels (codes, layers, protocols)
│   ├── JP kanji labels (見現時刻表示, 危険, 提訴決議, 非常事態)
│   └── No glow (flat fill only; R-13)
├── Fonts: Noto Serif JP (Mincho) + Oswald (condensed) + JetBrains Mono
├── State: S (speed, source, track, night, dayNightMode, usb, bt, reverse, call, ...) + prefs
├── Palette: applyPalette() based on speed zone (normal/caution/emergency)
├── Time-based day/night: 17:00 night, 05:00 morning, AUTO/MANUAL toggle, simulated clock for drift
├── Render: display content, node diagram, hex buttons, metadata, MAGI, speed plate, knob
└── Simulator: speed slider 0-160, source toggles, threshold inputs, accent picker (5 colors),
    day/night toggle, headlights, BT device, BT track, USB phone, reverse/call events, volume
```

### App Architecture (Kotlin)

The existing app uses View + Canvas with baked images. Changes needed:
1. Add GPS speed tracking (LocationManager)
2. Add speed zone state to the app's state machine
3. Modify rendering to use speed-based palette (color tinting of baked images, or re-bake with palette variants)
4. Add speed threshold settings to Settings sheet
5. Add time-based day/night mode (replace headlight-based with time-based, keep headlight as override)
6. Update the mockup to reflect final design

### SVG Hex Grid

Hexagonal buttons rendered as SVG `<polygon>` elements with text labels. SVG is used instead of Canvas to reduce rendering overhead and allow CSS styling. Each hex has:
- `fill`: none (outline) or filled (active/latched)
- `stroke`: current color (changes with palette)
- `text`: button label in condensed grotesque (Oswald)
- Size: 40px diameter for comfortable touch (18mm thumb)

### MAGI Node Diagram

Three trapezoid indicators (120×50px) arranged horizontally, representing BT/RADIO/USB source keys. Each trapezoid:
- `clip-path: polygon(15% 0%, 85% 0%, 100% 100%, 0% 100%)`
- Active state: filled with accent color, black text
- Inactive state: outline only, current color text
- Label below: Melchior/Balthasar/Casper (JP Mincho)

---

## Alternatives Considered

### Alternative A: Keep TE Aesthetic, Add Speed Palette Only

*   **Pros**: Minimal visual change, lower risk
*   **Cons**: Doesn't address the user's desire for a terminal interface
*   **Reason for Rejection**: User explicitly wants NGE terminal interface

### Alternative B: Full Canvas Rewrite with Dynamic Palette

*   **Pros**: Complete control over rendering
*   **Cons**: Higher development effort, risks breaking performance targets
*   **Reason for Rejection**: SVG + CSS is sufficient for hex grid; Canvas reserved for complex animations (tape deck)

### Alternative C: Use GPS Only (No Accelerometer)

*   **Pros**: Simpler implementation, GPS speed is reliable
*   **Cons**: No early transition detection; palette only updates at GPS fix rate (~1 Hz)
*   **Reason for Rejection**: Not rejected — primary approach. Accelerometer is supplement.

### Alternative D: Headlight-Based Day/Night (Original)

*   **Pros**: Uses existing mcu_light_state
*   **Cons**: Headlight sensor may not be connected; user wants time-based
*   **Reason for Rejection**: User requested time-based (17:00 night, 05:00 morning) with manual toggle

---

## Non-Functional Requirements

*   **Performance**: Maintain 22MB RAM, 175ms Home return. SVG hex grid is lighter than Canvas for static elements.
*   **Resilience**: GPS unavailable (tunnel) → fall back to last known speed or 0. Accelerometer supplement for early transitions.
*   **Accessibility**: Hex buttons must be keyboard-navigable (Tab/Enter). Focus indicators required. Touch targets ≥ 44px (WCAG 2.5.5).
*   **Theme consistency**: Both speed-based palette and time-based day/night must work together. Day/night overrides color when in normal speed zone.

---

## Detailed Implementation Plan

### Target Files to Create/Modify

#### 1. `mockup/nge-terminal-deepseek-v4.html` (Design target, v4)

*   **Type / Scope**: HTML/CSS/JS mockup (canonical reference for implementation)
*   **Proposed Changes**: NGE terminal interface mockup v4 (DeepSeek design target) incorporating the 11 feedback points: 7" 2DIN layout, 18mm thumb targets (hexes 66x76, knob 104, speed readout 116), time-based day/night with manual override, overlay warning (no layout shift), 13-tile MORE sheet, enlarged MAGI trapezoids (196x44), visible volume knob, more animations (pulse, ticker marquee, marching stripes, spectrum), no glow. Simulator panel covers all states. Committed as untracked (promote when approved).

#### 2. `docs/design-brief-nge-terminal.md` (Created)

*   **Type / Scope**: Design specification
*   **Proposed Changes**: Full design brief with reference analysis (14 images), palette system, hardware capabilities, layout mapping. Committed.

#### 3. `launcher/app/src/main/.../HomeActivity.kt` (Modify)

*   **Type / Scope**: Kotlin Activity
*   **Proposed Changes**: Add GPS speed tracking (LocationManager), speed zone state machine, palette application, time-based day/night mode

#### 4. `launcher/app/src/main/.../Car.kt` (Modify)

*   **Type / Scope**: Kotlin data class
*   **Proposed Changes**: Add speed field, speed zone field, speed threshold preferences, dayNightMode preference

#### 5. `launcher/app/src/main/.../SettingsSheet` (Modify)

*   **Type / Scope**: Kotlin UI component
*   **Proposed Changes**: Add speed threshold configuration (caution km/h, emergency km/h), day/night mode toggle

#### 6. `launcher/app/src/main/res/...` (Modify)

*   **Type / Scope**: Android resources
*   **Proposed Changes**: Add GPS permission, speed-related string resources, time-based theme strings

#### 7. `launcher/app/src/main/AndroidManifest.xml` (Modify)

*   **Type / Scope**: Android manifest
*   **Proposed Changes**: Add `ACCESS_FINE_LOCATION` permission

### Verification Plan

*   **Build**: `./gradlew assembleRelease` must succeed
*   **Emulator test**: Install on emulator, verify all states render
*   **Speed simulation**: Use mockup simulator to verify palette transitions
*   **Performance**: Verify RAM < 25MB, Home return < 200ms
*   **Accessibility**: Tab navigation through hex grid, focus indicators visible
*   **Touch targets**: Verify all interactive elements ≥ 44px
*   **Day/night**: Verify time-based switching at 17:00 and 05:00
*   **Antislop**: Re-run antislop audit after implementation

---

## Additional Resources

*   Design brief: `docs/design-brief-nge-terminal.md`
*   Design target mockup (v4): `mockup/nge-terminal-deepseek-v4.html` (canonical reference for implementation)
*   Mockup (v3 baseline): `mockup/nge-terminal.html`
*   DeepSeek mockup (v3): `mockup/nge-terminal-deepseek.html`
*   GLM mockup (v3): `mockup/nge-terminal-glm.html`
*   v4 variants (comparison): `mockup/nge-terminal-glm-v4.html`, `mockup/nge-terminal-crouton-v4.html`
*   Integration notes: `docs/INTEGRATION.md`
*   Test plan: `docs/TESTING.md`
*   NGE reference: `terminal-interface-design` skill (14 images analyzed)
*   NGE design language: `references/nge-design-language.md`
*   Antislop audit: PASS (no findings)

## Future Improvements

*   **Accelerometer integration**: Use mc32x0 for early palette transitions
*   **Speed-based audio**: Adjust volume or mute at high speeds (safety feature)
*   **GPS navigation integration**: Show next turn or distance to destination
*   **Customizable palette**: Allow user to define their own color schemes per speed zone
*   **Speed logging**: Log speed data for trip analysis

---

*Drafted with EGM*