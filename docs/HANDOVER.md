# Handover: Lily Launcher NGE Terminal Interface Project

## Session: 2026-11-06

### Project State

**Repo:** `C:/Users/ryuen/Documents/Projects/Headunit/lily-launcher`
**Branch:** `main`
**Latest commit:** `b0fd209` (feat: v3 NGE mockups by DeepSeek/GLM/Crouton subagents)
**Git status:** Clean (all changes committed)

### What Was Done

1. **Emulator setup** — `lily-1024x600` AVD booted on Android 10 (API 29), 1024×600 @ 160 dpi. Lily installed and running. Screenshots captured.
2. **Vendor APK extraction** — 6 key APKs pulled from `system.img` (ext2 via 7-Zip) to `system-extracted/system/app/`: BC03BTService, 8925_Carkit_RadioService, com.nwd.android.music.ui (MusicBean), com.nwd.bt.music, com.nwd.radio, zlink5.
3. **NGE terminal mockup** — Complete HTML mockup with dynamic speed-based palette, hexagonal buttons, MAGI node diagram, simulator panel. Fonts: Noto Serif JP (Mincho) + Oswald + JetBrains Mono. No glow (flat fill). Committed as `0eb957f`.
4. **Design brief** — `docs/design-brief-nge-terminal.md` with 14-image NGE analysis, color system, dynamic palette, hardware capabilities.
5. **EGM design doc** — `docs/designs/nge-terminal-interface-v3.md` with full architecture, CUJs, implementation plan.
6. **Antislop audit** — PASS (no findings). Glow reduced to 0 (R-13 compliant).
7. **Additional mockups** — DeepSeek v4.1 and GLM 5.3 variants created via opencode-cli-free (`98516f9`, `8a069ce`).

### Design Decisions (Locked)

1. **Speed source**: GPS `Location.getSpeed()` (m/s → km/h). Accelerometer (mc32x0) as supplement for early transitions.
2. **Speed thresholds**: Configurable (default 60 km/h caution, 100 km/h emergency).
3. **Day/night mode**: Time-based (17:00 night, 05:00 morning), toggleable (AUTO/MANUAL). Not headlight-based.
4. **Hex grid**: SVG (not Canvas) to reduce rendering overhead.
5. **Fonts**: Noto Serif JP (Mincho) + Oswald (condensed) + JetBrains Mono (all OFL-licensed).
6. **No glow**: Flat solid fill only (per NGE reference analysis).
7. **Warning animations**: Overlay on top of existing UI (not layout-shifting). Pulse animation.
8. **Touch targets**: Minimum 44px (WCAG 2.5.5). Hex buttons 40px+, knob 70px, speed readout 32px.

### Hardware Capabilities

- **GPS**: GlobalSat (glgps/glgps_31), auto-started gps-daemon. `Location.getSpeed()` available.
- **Accelerometer**: mc32x0 G-sensor on TWI2, address 0x4c. `SensorManager.TYPE_ACCELEROMETER`.
- **Headlights**: MCU (mcu_light_state) — supplementary, not primary for day/night.

### Delegation Study Findings

**Available methods for spawning subagents with specific models:**

1. **`advanced-delegation` plugin (T2)** — `model_map` backend
   - Config: `delegation.backend: model_map` + routes in `delegation.backends.model_map.options`
   - Usage: `delegate_task(tasks=[{"goal": "...", "option": "deepseek"}])`
   - **Requires session rebuild** via `/delegation model_map --now` in chat, then next message
   - Available models: `opencode-go/deepseek-v4.1-flash`, `opencode-go/glm-5.3-flash`, `opencode-go/crouton-alpha-free`

2. **`delegate_task` (built-in T1)** — inherits parent model
   - No model selection per task
   - All subagents use the parent's model

3. **`opencode-delegate` skill (T3)** — external CLI
   - Uses opencode CLI directly with free-model ladder
   - Full worktree isolation
   - Currently blocked by free usage limit for DeepSeek/GLM

4. **`hermes chat`** — spawn new sessions
   - `hermes chat -q "..."` creates independent sessions
   - Can use different models via `-m` flag
   - No tool-level model routing

**Key lesson:** The `model_map` backend works, but the `delegate_task` tool schema must be rebuilt in a new session. In an existing session, the tools aren't updated even after `/delegation --now`.

### What's Next (Implementation Phase)

1. **GPS speed tracking** — Add `LocationManager` with `GPS_PROVIDER` to HomeActivity.kt
2. **Speed zone state machine** — Add speed field + zone to Car.kt
3. **Palette application** — Modify rendering to use speed-based palette
4. **Settings sheet** — Add speed threshold configuration
5. **AndroidManifest** — Add `ACCESS_FINE_LOCATION` permission
6. **Time-based day/night** — Replace headlight-based with time-based (17:00 night, 05:00 morning)
7. **Build & test** — `./gradlew assembleRelease`, install on emulator, verify

### Files of Note

| File | Description |
|------|-------------|
| `mockup/nge-terminal.html` | Current NGE mockup v3 (7" touch, time-based day/night, overlay warnings) |
| `mockup/nge-terminal-deepseek.html` | DeepSeek v4.1 variant (99.5K) |
| `mockup/nge-terminal-glm.html` | GLM 5.3 variant (62.5K) |
| `mockup/nge-terminal-deepseek-v3.html` | DeepSeek v3 variant (48.9K) |
| `mockup/nge-terminal-glm-v3.html` | GLM v3 variant (51.4K) |
| `mockup/nge-terminal-crouton-v3.html` | Crouton v3 variant (40.6K) |
| `docs/design-brief-nge-terminal.md` | Full design brief (14 images, color system, hardware) |
| `docs/designs/nge-terminal-interface-v3.md` | EGM design document (architecture, CUJs, implementation plan) |
| `docs/INTEGRATION.md` | Vendor integration notes (AIDL, broadcasts, etc.) |
| `docs/TESTING.md` | Manual test plan for head unit |

### Config State

- `delegation.backend: model_map` (set)
- `delegation.backends.model_map.options`: deepseek, glm, crouton (configured)
- `delegation.backends.model_map.default: crouton`
- Advanced-delegation plugin: installed and enabled

### How to Start Next Session

1. Ensure `delegation.backend: model_map` is set (already done)
2. Run `/delegation model_map --now` in chat to rebuild tools with `option` parameter
3. Send a new message to trigger tool rebuild
4. Then use `delegate_task(tasks=[{"goal": "...", "option": "deepseek"}])` for model-specific delegation

### Cleanup Needed

- Remove temporary files: `deepseek-done.txt`, `deepseek-stderr.log`, `deepseek-stderr2.log`, `glm-done.txt`, `glm-stderr.log`, `TASK_BRIEF.md`, `TASK_BRIEF_V3.md`
- Consider removing `.bak` and `.prev` files from mockup directory
- Consider removing the 3 v3 mockup variants if not needed (they were all from beam-free)