# Design Brief: NGE Terminal Interface for Lily Launcher

## 1. Vision

Adapt the Lily launcher's UI to a **Neon Genesis Evangelion (NGE) terminal interface** design language, with a **dynamic color palette** that shifts based on vehicle speed. All upstream features and workflows are retained — only the visual layer and layout are reworked.

---

## 2. Reference Analysis

The design is driven by NGE terminal interface reference images, analyzed via opencode. Key findings:

- **Zero glow** (flat solid fill only, R-13) — no text-shadow/box-shadow for glow
- **Mincho serif** (Shippori Mincho B1) for Japanese kanji warnings
- **Condensed grotesque** (Oswald) for Latin caps labels
- **Saira Extra Condensed** for warning stamps/emergency banners
- **JetBrains Mono** for numerals and data readouts
- **Pointy-top hexagons** as the primary geometric element
- **Bilingual JP/EN** text throughout
- **Honeycomb hex grid** background (A.T. Field, ~1,380 hexes)

Additional design reference: [NERV UI](https://github.com/TheGreatGildo/nerv-ui) — NERV Operations Console design system (MIT). [idrive7-EVAHUD](https://github.com/PenguinDukeYDAuto/idrive7-EVAHUD) — EVA HUD for BMW iDrive 7 (MIT).

---

## 3. Dynamic Color Palette (Speed-Based)

### 3.1 Speed Thresholds

| Speed | Palette | Decor | Mood |
|-------|---------|-------|------|
| **< 60 km/h** | Green (#4DE89A) on black | Idle/normal, honeycomb bg, no hazard decor | Calm, informational |
| **60–100 km/h** | Yellow (#F5B400) on black | Caution hazard stripes, warning seals, ticker | Alert, attentive |
| **> 100 km/h** | Red (#E31C23) on black | Emergency hazard stripes, inverted fills, alarm banners, pulse overlay | Critical, urgent |

### 3.2 Color Mapping by Speed

**Below 60 km/h (Normal):**
- Background: `#0A0A12` (near-black, purple-tinted)
- Primary text: Phosphor green `#4DE89A`
- Secondary text: `#B9AFCB` (lavender-gray)
- Accent: `#FF4A1C` (orange-red)
- Decor: Honeycomb hex grid, standard MAGI diagram, no hazard stripes

**60–100 km/h (Caution):**
- Background: `#0A0A12`
- Primary text: Yellow/amber `#F5B400`
- Secondary text: Orange `#FF6A00`
- Decor: Diagonal hazard stripes (yellow/black), warning octagon seals, ticker panel
- Header: `CAUTION` banner with bar ornaments
- Fullscreen pulsing overlay (yellow, 40% alpha)

**Above 100 km/h (Emergency):**
- Background: `#000000` (pure black)
- Primary text: Deep red `#E31C23`
- Secondary text: Red `#FF4A1C`
- Decor: Full hazard stripes (red/black), `EMERGENCY` banners, inverted fills (red fill + green text), hex tessellation with red cells
- Header: `EMERGENCY` banner, repeating text-as-texture
- Fullscreen pulsing overlay (red, 62% alpha) with center modal box

### 3.3 Transition Behavior

- Palette shifts are **instant** (no smooth transitions) — NGE aesthetic favors state changes
- Hysteresis: ±5 km/h + ≥1 s dwell per zone transition (prevents strobe on GPS noise)
- Boundary: caution if speed >= cautionThreshold; emergency if speed >= emergencyThreshold
- When crossing a threshold, the entire UI re-renders with the new palette

---

## 4. Hardware Capabilities

### 4.1 GPS — PRESENT

- **Hardware:** GlobalSat GPS module (glgps/glgps_31, auto-started gps-daemon)
- **Android API:** `LocationManager` with `GPS_PROVIDER` → `Location.getSpeed()` returns m/s
- **Conversion:** m/s × 3.6 = km/h
- **Update frequency:** 500ms, 0m distance filter
- **Staleness TTL:** 3s (reverts to normal zone if no fix)
- **Accuracy filter:** > 20m ignore, speed < 0 ignore

### 4.2 Accelerometer — PRESENT

- **Sensor:** mc32x0 G-sensor (3-axis accelerometer), TWI2, address 0x4c
- **Android API:** `SensorManager` with `TYPE_ACCELEROMETER` or `TYPE_LINEAR_ACCELERATION`
- **Use:** Early transition detection (optional supplement, GPS remains authoritative)
- **Trigger:** magnitude > 2.5 m/s² sustained ≥500ms → hint "transition likely"

### 4.3 Headlights — SUPPLEMENTARY

- **MCU:** `mcu_light_state` (polled via existing `sys()` helper in Car.kt)
- **Use:** Night mode override (if available, else time-based only)

### 4.4 Recommendation

**Use GPS for speed.** `Location.getSpeed()` is the most reliable source. Accelerometer supplements for early transition detection. Headlights are supplementary for night mode.

---

## 5. Layout Mapping: Existing Features → NGE Terminal

### 5.1 Screen Structure (1024×600 landscape)

```
┌─────────────────────────────────────────────────────────────────┐
│  STATUS RAIL (40px): clock · speed zone · LAMP · AUTO/DAY      │
│  BANNER (54px): JP/EN header · octagonal zone seal · REV        │
├────────┬────────────────────────────────────────┬───────────────┤
│TELEMETRY│           STAGE (616px)              │  MAGI (216px) │
│ (168px)│  ┌─────────────────────────────────┐ │ MELCHIOR (BT) │
│        │  │  Album hex · Title · Artist     │ │ BALTHASAR(RADIO)│
│ SPEED  │  │  Spectrum · AA panel · Progress │ │ CASPER (USB)  │
│ VOLUME │  │  Data cells (SPEED/ZONE/VOL/TEMP)│ │ LAYER 3/2/1   │
│ SOURCE │  └─────────────────────────────────┘ │               │
│ THEME  │  Vertical kanji band (24px)          │               │
│ UPLINK │                                      │               │
│ CODE   │                                      │               │
├────────┴────────────────────────────────────────┴───────────────┤
│  TICKER (22px): scrolling CAUTION/EMERGENCY text (zone only)  │
├─────────────────────────────────────────────────────────────────┤
│  DECK (184px): Hex keypad (11 hexes, 6+5 staggered) · Knob · Speed plate │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 Element Mapping

| Existing Element | NGE Terminal Equivalent | Notes |
|-----------------|------------------------|-------|
| Clock plate (h:mm AM/PM) | Top-left metadata block, odometer-style | `CODE: HH:MM` format |
| Device name tab (orange) | Header banner node label | `BALTHASAR·2` style |
| TAPE display (title/artist) | Central display area, boxed | Red/green text on black |
| RADIO display (freq/station) | Central display area, boxed | Same layout, different content |
| USB display (title) | Central display area, boxed | Same layout |
| EMPTY display | Central display: `NO SIGNAL` in large caps | With `WAITING` micro-text |
| Pads (PREV/PLAY/NEXT) | Hexagonal buttons in grid | Red/green fill states |
| Source keys (BT/RADIO/USB) | Triangular node diagram nodes | Latched = filled, unlatched = outline |
| PHONE key | Node in diagram | Orange when BT connected |
| SWITCH key | Node in diagram | |
| AUTO key | Node in diagram | |
| MORE key | Node in diagram | Opens More sheet |
| Volume knob | Bottom-center, with `VOL: XX` readout | Odometer-style numerals |
| MORE sheet | Overlay: hex grid of 15 tiles | Octagonal/hexagonal buttons |
| Settings sheet | Overlay: key-value pairs in red frames | `SETTING: VALUE` format |
| Night mode | Palette shift (already handled by speed system) | Headlights still toggle theme |

### 5.3 Hex Grid Button Layout

11 hexagonal buttons arranged in a staggered 6+5 grid (pointy-top). Each hex can be:
- **Filled** (red) = active/latched
- **Outline** (red on black) = inactive
- **Green fill** = decision node (BT connected, etc.)
- **Inverted** (red fill + green text) = critical state

### 5.4 Metadata Side-Channels

Left column (red, small, tight leading):
```
CODE: 258
FILE: MAGI_SYS
EXT: 4096
MODE: AUTO
PRIORITY: AAA
SPEED: 087 km/h
```

Right column (red, small):
```
LAYER 3: CONNECTION
LAYER 2: DATA LINK
LAYER 1: PHYSICAL
SOURCE: BLUETOOTH
VOL: 18/40
```

---

## 6. Implementation Plan

### Phase 1: Backend (Kotlin) ✅ DONE
- GPS speed tracking (`LocationManager`/`GPS_PROVIDER`)
- Speed zone state machine in `Car.kt` (SpeedZone enum, hysteresis ±5 km/h)
- Palette application rendering (Canvas paint swap)
- Settings sheet speed thresholds
- AndroidManifest `ACCESS_FINE_LOCATION`
- Day/night time-based switching (17:00 night, 05:00 morning)

### Phase 2: UI Reskin (Canvas) ✅ DONE
- Replace baked images with Canvas-drawn NGE elements
- Honeycomb background (A.T. Field, ~1,380 hexes)
- Status rail, banner, telemetry rail, stage, MAGI trapezoids
- Hex keypad (11 hexes, 6+5 staggered, SVG-scaled)
- Speed plate (124px odometer, 24-seg ladder, octagonal seal)
- Volume knob (104px, lavender ring, ticks, accent arc)
- Zone overlays (hazard stripes, ticker, fullscreen pulse)
- More sheet (15 tiles, 5×3 grid)
- Settings sheet (day/night, AA, play-on-connect, speed source, thresholds, accent)

### Phase 3: Build & Test ✅ DONE
- Build APK, install on emulator
- Verify all states render correctly
- Test speed-based palette (via debug slider)
- Build release APK

### Phase 4: Commit ✅ DONE
- Commit code changes
- Update docs with new design brief

---

## 7. Key Design Decisions

1. **Speed source:** GPS `Location.getSpeed()` (m/s → km/h). Accelerometer as supplement for early transitions.
2. **Palette transition:** Instant (no animation). NGE aesthetic favors state changes over smooth transitions.
3. **Speed display:** Odometer-style numerals, always visible (top-right, like `TRANSFER: 93.0%`).
4. **Hex grid:** Canvas path drawing for hexagonal buttons (SVG in mockup only).
5. **Font:** Oswald (Latin, condensed), Shippori Mincho B1 (Japanese, Mincho), Saira Extra Condensed (warning stamps), JetBrains Mono (numerics).
6. **Glow:** Zero glow (flat solid fill only, R-13).
7. **Rendering:** Canvas path drawing (no baked images, no SVG in app).
8. **Day/night:** Time-based (17:00 night, 05:00 morning), AUTO/MANUAL toggle. Headlight override if available.
9. **Speed thresholds:** Configurable (default 60/100 km/h). Hysteresis ±5 km/h, dwell ≥1s.

---

## 8. What Stays the Same (Upstream Retention)

- All broadcast receivers and AIDL integrations (unchanged)
- All source switching logic (BT/RADIO/USB)
- Volume control (knob drag + steering wheel keys)
- Night mode (headlights → theme, now also influenced by speed)
- Android Auto auto-open (with reverse/call guards)
- Reverse camera overlay (handled by Android system)
- Incoming call overlay (handled by Android system)
- More sheet (15 tiles)
- Settings sheet (resumeOnStart, autoAA, theme, accent, speed thresholds)
- Play-on-connect behavior
- All AIDL services (BTFeature, RadioFeature, etc.)
- No sounds (NwdAudioService constraint)
- No internet permission
- Memory/performance targets (22MB RAM, 175ms Home return)

---

## 9. Open Questions

1. **Font licensing:** Oswald, Shippori Mincho B1, Saira Extra Condensed, JetBrains Mono (all OFL). Acceptable? — Resolved.
2. **Speed threshold tuning:** 60 km/h and 100 km/h are initial guesses. Configurable in settings? — Resolved (configurable).
3. **Accelerometer usage:** Use it for early transition detection (pre-GPS speed change) or skip it? — Resolved (optional supplement).
4. **Mockup fidelity:** How closely should the mockup match the final app? — Resolved (Canvas drawing, mockup is design spec).
5. **Hex grid feasibility:** Can the existing View+Canvas approach render hexagonal buttons efficiently? — Resolved (Canvas path drawing works).

---

*Updated 2026-11-08 with final implementation state.*