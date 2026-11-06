# NGE Terminal Interface for Lily Launcher

*   **Author:** ryuenma
*   **Status:** Draft
*   **Date:** 2026-11-06

---

## Overview

This design adapts the Lily launcher's UI from its Teenage Engineering EP-series aesthetic to a Neon Genesis Evangelion (NGE) terminal interface design language, with a dynamic color palette that shifts based on vehicle speed. All upstream features and workflows are retained; only the visual layer and layout are reworked.

The design is driven by six NGE terminal interface reference images, analyzed for color system, typography, layout patterns, and alarm-state visual language. The resulting UI uses a hexagonal tessellation, boxed banner headers, triangular node diagrams, hazard stripes, and inverted-fill critical states — all mapped to the launcher's existing feature set.

## The Problem

The current Lily launcher uses a Teenage Engineering EP-series aesthetic (silver body, black display band, square pads, orange knob). While functional and well-crafted, it lacks the urgent, authoritative, machine-like presence that a terminal interface provides. The user wants a UI that feels like a NERV command center — where information density, color-coded alert states, and hexagonal geometry communicate system status at a glance.

Additionally, the launcher has no awareness of vehicle speed. The color palette is static regardless of driving conditions. The user wants the palette to shift based on speed: calm greens below 60 km/h, caution yellows between 60-100 km/h, and emergency reds above 100 km/h.

## Goals

*   Adapt the launcher's visual layer to NGE terminal interface design language
*   Implement dynamic color palette based on vehicle speed (GPS-sourced)
*   Retain all upstream features and workflows (BT/RADIO/USB source switching, volume, Android Auto, reverse camera, calls, settings)
*   Use SVG for hexagonal button grids to reduce rendering overhead
*   Make speed thresholds configurable by the user
*   Maintain performance targets (22MB RAM, 175ms Home return, 3.3MB APK)

## Non-Goals

*   No changes to AIDL services, broadcast receivers, or vendor integrations
*   No new permissions or system settings changes
*   No audio/sound additions (NwdAudioService constraint)
*   No internet permission
*   No changes to the install/rollback scripts

## Open Questions

*   **Q1**: Rajdhani (Google Fonts, OFL) for Latin + Noto Sans CJK JP for CJK — acceptable font licensing?
*   **Q2**: Speed thresholds (60/100 km/h) configurable in settings — confirmed by user
*   **Q3**: Accelerometer for early transition detection — use as supplement to GPS, or skip?
*   **Q4**: Hex grid via SVG — confirmed by user
*   **Q5**: How closely should mockup match final app? (App uses baked images + Canvas)

## Critical User Journeys (CUJs)

### CUJ 1: Normal Driving (< 60 km/h)

*   **Given**: Vehicle speed below 60 km/h, GPS fix available
*   **When**: User presses Home or launcher is active
*   **Then**: UI renders in green/yellow/blue palette on black background. Hex grid buttons in green outline. No hazard stripes. Speed readout shows current km/h. All source keys (BT/RADIO/USB) functional.

### CUJ 2: Highway Driving (60-100 km/h)

*   **Given**: Vehicle speed between 60-100 km/h
*   **When**: Speed crosses 60 km/h threshold
*   **Then**: Palette instantly shifts to yellow/black. Hazard stripes appear on top/bottom. Warning octagon seals render. Header changes to "CAUTION: SPEED LIMIT". All functionality retained.

### CUJ 3: High-Speed Driving (> 100 km/h)

*   **Given**: Vehicle speed above 100 km/h
*   **When**: Speed crosses 100 km/h threshold
*   **Then**: Palette instantly shifts to black/red. Full hazard stripes (red/black). Header changes to "EMERGENCY: SELF DESTRUCTION". Inverted fills on critical elements. Hex cells turn red. All functionality retained.

### CUJ 4: Source Switching

*   **Given**: Launcher is active, any speed zone
*   **When**: User presses BT/RADIO/USB hex key
*   **Then**: Source switches without opening an app. Node diagram updates (active node fills). Display area updates with source-specific content. Palette remains speed-based.

### CUJ 5: Reverse Camera Overlay

*   **Given**: Vehicle in reverse gear (mcu_backcar_state)
*   **When**: Reverse gear engaged
*   **Then**: Full-screen overlay with "REVERSE" banner and hazard stripes. Camera feed appears on top. Palette temporarily overridden to emergency (red) regardless of speed.

---

## Technical Plan (Architecture)

### Hardware

| Component | Hardware | Android API | Purpose |
|-----------|----------|-------------|---------|
| GPS | GlobalSat (glgps) | LocationManager/GPS_PROVIDER | Vehicle speed (m/s → km/h) |
| Accelerometer | mc32x0 G-sensor (TWI2) | SensorManager/TYPE_ACCELEROMETER | Early transition detection |
| Headlights | MCU (mcu_light_state) | Settings.System | Night mode toggle |

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
  toggle hazard stripes
  update header text
  update node diagram fills
  update hex button colors
```

Thresholds stored in SharedPreferences, configurable via Settings sheet.

### Mockup Architecture (HTML/CSS/JS)

```
mockup/nge-terminal.html
├── CSS: NGE terminal styling (hex grid, banners, metadata, glow)
├── State: S (speed, source, track, night, etc.) + prefs
├── Palette: applyPalette() based on speed zone
├── Render: display content, node diagram, hex buttons, metadata
└── Simulator: speed slider, source toggles, threshold inputs
```

### App Architecture (Kotlin)

The existing app uses View + Canvas with baked images. Changes needed:
1. Add GPS speed tracking (LocationManager)
2. Add speed zone state to the app's state machine
3. Modify rendering to use speed-based palette (color tinting of baked images, or re-bake with palette variants)
4. Add speed threshold settings to Settings sheet
5. Update the mockup to reflect final design

### SVG Hex Grid

Hexagonal buttons rendered as SVG `<polygon>` elements with text labels. SVG is used instead of Canvas to reduce rendering overhead and allow CSS styling. Each hex has:
- `fill`: none (outline) or filled (active/latched)
- `stroke`: current color (changes with palette)
- `text`: button label

---

## Alternatives Considered

### Alternative A: Keep TE Aesthetic, Add Speed Palette Only

*   **Pros**: Minimal visual change, lower risk
*   **Cons**: Doesn't address the user's desire for a terminal interface; the TE aesthetic doesn't support alarm-state visual language as naturally
*   **Reason for Rejection**: User explicitly wants NGE terminal interface

### Alternative B: Full Canvas Rewrite with Dynamic Palette

*   **Pros**: Complete control over rendering, can do glow effects in Canvas
*   **Cons**: Higher development effort, risks breaking existing performance targets
*   **Reason for Rejection**: SVG + CSS is sufficient for the hex grid; Canvas reserved for complex animations (tape deck)

### Alternative C: Use GPS Only (No Accelerometer)

*   **Pros**: Simpler implementation, GPS speed is reliable
*   **Cons**: No early transition detection; palette only updates at GPS fix rate (~1 Hz)
*   **Reason for Rejection**: Not rejected — this is the primary approach. Accelerometer is a supplement, not a replacement.

---

## Non-Functional Requirements

*   **Performance**: Maintain 22MB RAM, 175ms Home return. SVG hex grid is lighter than Canvas for static elements.
*   **Resilience**: GPS unavailable (tunnel) → fall back to last known speed or 0. Accelerometer supplement for early transitions.
*   **Accessibility**: Hex buttons must be keyboard-navigable (Tab/Enter). Focus indicators required.
*   **Theme consistency**: Both speed-based palette and night mode must work together. Night mode overrides color when headlights on and speed < 60 km/h.

---

## Detailed Implementation Plan

### Target Files to Create/Modify

#### 1. `mockup/nge-terminal.html` (Created)

*   **Type / Scope**: HTML/CSS/JS mockup
*   **Proposed Changes**: Complete NGE terminal interface mockup with dynamic palette, hex grid, node diagram, simulator panel. Already created and committed.

#### 2. `docs/design-brief-nge-terminal.md` (Created)

*   **Type / Scope**: Design specification
*   **Proposed Changes**: Full design brief with reference analysis, palette system, hardware capabilities, layout mapping. Already created and committed.

#### 3. `launcher/app/src/main/.../HomeActivity.kt` (Modify)

*   **Type / Scope**: Kotlin Activity
*   **Proposed Changes**: Add GPS speed tracking (LocationManager), speed zone state machine, palette application (color tinting of baked images)

#### 4. `launcher/app/src/main/.../Car.kt` (Modify)

*   **Type / Scope**: Kotlin data class
*   **Proposed Changes**: Add speed field, speed zone field, speed threshold preferences

#### 5. `launcher/app/src/main/.../SettingsSheet` (Modify)

*   **Type / Scope**: Kotlin UI component
*   **Proposed Changes**: Add speed threshold configuration (caution km/h, emergency km/h)

#### 6. `launcher/app/src/main/res/...` (Modify)

*   **Type / Scope**: Android resources
*   **Proposed Changes**: Add GPS permission, speed-related string resources

#### 7. `launcher/app/src/main/AndroidManifest.xml` (Modify)

*   **Type / Scope**: Android manifest
*   **Proposed Changes**: Add `ACCESS_FINE_LOCATION` permission

### Verification Plan

*   **Build**: `./gradlew assembleRelease` must succeed
*   **Emulator test**: Install on emulator, verify all states render
*   **Speed simulation**: Use mockup simulator to verify palette transitions
*   **Performance**: Verify RAM < 25MB, Home return < 200ms
*   **Accessibility**: Tab navigation through hex grid, focus indicators visible

---

## Additional Resources

*   Design brief: `docs/design-brief-nge-terminal.md`
*   Mockup: `mockup/nge-terminal.html`
*   Integration notes: `docs/INTEGRATION.md`
*   Test plan: `docs/TESTING.md`
*   NGE reference images: 6 screenshots (analyzed via opencode)

## Future Improvements

*   **Accelerometer integration**: Use mc32x0 for early palette transitions
*   **Speed-based audio**: Adjust volume or mute at high speeds (safety feature)
*   **GPS navigation integration**: Show next turn or distance to destination
*   **Customizable palette**: Allow user to define their own color schemes per speed zone
*   **Speed logging**: Log speed data for trip analysis

---

*Drafted with EGM*