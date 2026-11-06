# Design Brief: NGE Terminal Interface for Lily Launcher

## 1. Vision

Adapt the Lily launcher's UI from its current Teenage Engineering EP-series aesthetic to a **Neon Genesis Evangelion (NGE) terminal interface** design language, with a **dynamic color palette** that shifts based on vehicle speed.

All upstream features and workflows are retained — only the visual layer and layout are reworked.

---

## 2. Reference Analysis (6 Images)

Six NGE terminal interface screenshots were analyzed via opencode (glm-5.3-flash-free). Key findings:

### 2.1 Color System

| Role | Color | Hex (approx) |
|------|-------|-------------|
| Screen background | Near-black, purple-tinted | `#0A0A12` |
| System/authority text | Hot orange-red | `#FF4A1C` / `#FF6A00` |
| Decision nodes / status | Phosphor green | `#4DE89A` / `#66FFB2` |
| Critical/veto state | Deep red fill | `#E31C23` |
| Chassis (device frame) | Dark indigo-violet | `#3A3448` |
| Physical buttons | Lavender-gray | `#B9AFCB` |
| Hazard/caution accent | Yellow/amber | `#F5B400` |
| Foreign data channel | Cyan (rare) | `#258AC3` |

**Color = state machine:** grey/teal = idle, amber = active, red = breach/alarm, green = decision nodes. Inverted fill (red fill + green text) = critical/veto state.

### 2.2 Typography

- **Primary:** Condensed grotesque sans-serif, all-caps (Eurostile / Handel Gothic / Microgramma-like)
- **Weight:** Bold-to-medium, tight tracking
- **CJK:** Gothic condensed for dense bureaucratic lists
- **Numerals:** Tabular/odometer monospace digits
- **Alarm voice:** Wide-tracked caps (`WARNING`, `EMERGENCY`)
- **Bilingual:** JP/EN text mixed; JP used for system labels, EN for data

### 2.3 Layout Patterns

1. **Hexagonal tessellation** — hexes as atomic data cells; repetition over hierarchy
2. **Boxed banner headers** — heavy red outlines with bar/rail ornaments on flanks
3. **Triangular node diagram** — three nodes (BALTHASAR/CASPER/MELCHIOR) converging on center
4. **Dense metadata side-channels** — codes, layers, protocols stacked vertically
5. **Hazard stripes** — diagonal red/black barber-pole for alarm states
6. **Octagonal/hexagonal warning seals** — rotated kanji stamps in corners
7. **Physical hardware buttons** — round lavender buttons at bottom with small arrow glyphs
8. **Inverted fill for critical state** — red fill + knocked-out green text

### 2.4 Glow & Texture

- Phosphor bloom on bright text (halo effect)
- Subtle scanlines
- Compression grain / vignette
- Screen-on-screen feel (not dead flat)
- Image 3 (poster style) is the exception: flat vector, no glow

---

## 3. Dynamic Color Palette (Speed-Based)

### 3.1 Speed Thresholds

| Speed | Palette | Decor | Mood |
|-------|---------|-------|------|
| **< 60 km/h** | Green + yellow + blue on black | Idle/normal, no hazard decor | Calm, informational |
| **60–100 km/h** | Yellow + black | Caution stripes, warning seals | Alert, attentive |
| **> 100 km/h** | Black + red | Emergency hazard stripes, inverted fills, alarm banners | Critical, urgent |

### 3.2 Color Mapping by Speed

**Below 60 km/h (Normal):**
- Background: `#0A0A12` (near-black, purple-tinted)
- Primary text: Phosphor green `#4DE89A`
- Secondary text: Amber `#F5B400`
- Accent: Cyan `#258AC3` (foreign data channel)
- Decor: None — clean hex grid, standard node diagram

**60–100 km/h (Caution):**
- Background: `#0A0A12` (darker, more contrast)
- Primary text: Yellow/amber `#F5B400`
- Secondary text: Orange `#FF6A00`
- Decor: Diagonal hazard stripes (yellow/black), warning octagon seals, `LEVEL↑` micro-tags
- Header: `CAUTION` banner with bar ornaments

**Above 100 km/h (Emergency):**
- Background: `#000000` (pure black)
- Primary text: Hot red `#FF4A1C`
- Secondary text: Deep red `#E31C23`
- Decor: Full hazard stripes (red/black), `EMERGENCY` banners, inverted fills (red fill + green text), hex tessellation with red cells
- Header: `EMERGENCY` banner, repeating text-as-texture

### 3.3 Transition Behavior

- Palette shifts should be **instant** (not animated) — the NGE aesthetic is about state changes, not smooth transitions
- When crossing a threshold, the entire UI re-renders with the new palette
- The speed value itself should be prominently displayed (odometer-style numerals)
- A "speed indicator" element should always be visible (top-right corner, like the `TRANSFER: 93.0%` readout)

---

## 4. Hardware Capabilities

### 4.1 GPS — PRESENT ✅

- **Hardware:** GlobalSat GPS module (`glgps` / `glgps_31` binaries)
- **Service:** `gps-daemon` / `gps-daemon31` (auto-started by `check_and_start_gps.sh`)
- **Hardware versions:** v2.0 (Broadcom), v3.0 (Broadcom)
- **App:** `GPS_Monitor_VP2.9.9` (system app)
- **Android API:** `LocationManager` with `GPS_PROVIDER` → `Location.getSpeed()` returns m/s
- **Conversion:** m/s × 3.6 = km/h
- **Update frequency:** ~1 Hz (standard GPS fix rate)

### 4.2 Accelerometer — PRESENT ✅

- **Sensor:** mc32x0 G-sensor (3-axis accelerometer)
- **Bus:** TWI2, address `0x4c`
- **Android API:** `SensorManager` with `TYPE_ACCELEROMETER`
- **Use case:** Could supplement GPS for speed estimation, but GPS is more reliable for vehicle speed. Accelerometer is better for detecting acceleration/deceleration events (could trigger palette shifts before GPS speed updates).

### 4.3 Light Sensor — NOT CONFIRMED

- `FactoryRuleConfig.xml` mentions "光感模块开关" (light sensor module switch), but no confirmed hardware
- The existing Lily code uses `mcu_light_state` (headlights) for night mode, which is more reliable than a light sensor

### 4.4 Recommendation

**Use GPS for speed.** `Location.getSpeed()` is the most reliable source. Fall back to accelerometer-based estimation only if GPS is unavailable (tunnel, etc.). The accelerometer can also detect sudden braking/acceleration for early palette transitions.

---

## 5. Layout Mapping: Existing Features → NGE Terminal

### 5.1 Screen Structure (1024×600 landscape)

```
┌─────────────────────────────────────────────────────────────────┐
│  HEADER BANNER: "DIRECT LINK CONNECTION : LILY"  (red, boxed)  │
│  ┌──────────┐  ┌──────────────────────────────┐  ┌──────────┐ │
│  │ METADATA │  │                              │  │ LAYER/   │ │
│  │ CODE:    │  │     MAIN DISPLAY AREA        │  │ STATUS   │ │
│  │ FILE:    │  │  (TAPE / RADIO / USB / AA)  │  │ Layer 3  │ │
│  │ EXT:     │  │                              │  │ Layer 2  │ │
│  │ PRIORITY │  │                              │  │ Layer 1  │ │
│  └──────────┘  └──────────────────────────────┘  └──────────┘ │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  TRIANGULAR NODE DIAGRAM (BT / RADIO / USB nodes)          │ │
│  └────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  BOTTOM: Volume knob + Physical buttons + Speed readout     │ │
│  └────────────────────────────────────────────────────────────┘ │
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
| Volume knob | Bottom-center, with `VOL: XX` readout | Odometer-style numerals |
| MORE sheet | Overlay: hex grid of 13 tiles | Octagonal/hexagonal buttons |
| Settings sheet | Overlay: key-value pairs in red frames | `SETTING: VALUE` format |
| Reverse camera | Full-screen overlay, `REVERSE` banner | Hazard stripes on edges |
| Incoming call | Full-screen overlay, `INCOMING CALL` banner | Hazard stripes on edges |
| Android Auto | Full-screen overlay, `ANDROID AUTO` banner | |
| Night mode | Palette shift (already handled by speed system) | Headlights still toggle theme |

### 5.3 Hex Grid Button Layout

The physical pads (PREV, PLAY, NEXT, BT, RADIO, USB, PHONE, SWITCH, AUTO, MORE) should be arranged in a hexagonal grid instead of a flat row. Each hex can be:
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

### Phase 1: Mockup Redesign (HTML/CSS)
- Create new mockup variant with NGE terminal styling
- Implement hex grid layout for buttons
- Implement triangular node diagram for source keys
- Implement dynamic palette system (speed-based)
- Add hazard stripes, octagonal seals, bar ornaments
- Keep simulator panel for testing all states

### Phase 2: Speed Integration (Kotlin)
- Add GPS speed tracking to the app
- `LocationManager` with `GPS_PROVIDER`
- Convert m/s → km/h
- Update palette based on speed thresholds
- Use accelerometer for early transition detection

### Phase 3: Build & Test
- Build APK, install on emulator
- Verify all states render correctly
- Test speed-based palette (simulate via mockup simulator)
- Build release APK

### Phase 4: Commit
- Commit mockup + code changes
- Update docs with new design brief

---

## 7. Key Design Decisions

1. **Speed source:** GPS `Location.getSpeed()` (m/s → km/h). Accelerometer as supplement for early transitions.
2. **Palette transition:** Instant (no animation). NGE aesthetic favors state changes over smooth transitions.
3. **Speed display:** Odometer-style numerals, always visible (top-right, like `TRANSFER: 93.0%`).
4. **Hex grid:** SVG or Canvas hexagons for buttons. CSS hexagons are possible but SVG gives more control.
5. **Font:** Need to bundle a condensed grotesque. Eurostile is commercial; alternatives: "Bank Gothic" (free variants), "Microgramma" (free variants), or use "Rajdhani" (Google Font, free, condensed, geometric).
6. **CJK:** Need a condensed gothic CJK font. "Noto Sans CJK JP" is free and has condensed variants.
7. **Glow:** CSS `text-shadow` for phosphor bloom. Keep subtle — the NGE glow is moderate, not neon-sign bright.

---

## 8. What Stays the Same (Upstream Retention)

- All broadcast receivers and AIDL integrations (unchanged)
- All source switching logic (BT/RADIO/USB)
- Volume control (knob drag + steering wheel keys)
- Night mode (headlights → theme, now also influenced by speed)
- Android Auto auto-open (with reverse/call guards)
- Reverse camera overlay
- Incoming call overlay
- More sheet (13 tiles)
- Settings sheet (resumeOnStart, autoAA, theme, accent)
- Play-on-connect behavior
- All AIDL services (BTFeature, RadioFeature, etc.)
- No sounds (NwdAudioService constraint)
- No internet permission
- Memory/performance targets (22MB RAM, 175ms Home return)

---

## 9. Open Questions

1. **Font licensing:** Rajdhani (Google Fonts, OFL) for Latin, Noto Sans CJK JP for CJK. Acceptable?
2. **Speed threshold tuning:** 60 km/h and 100 km/h are initial guesses. Should they be configurable in settings?
3. **Accelerometer usage:** Use it for early transition detection (pre-GPS speed change) or skip it?
4. **Mockup fidelity:** How closely should the mockup match the final app? The app uses baked images + Canvas, so the mockup is the design spec.
5. **Hex grid feasibility:** Can the existing View+Canvas approach render hexagonal buttons efficiently? Or should we use SVG?
EOF
echo "written"