# Lily Launcher NGE UI — Final Implementation Verification

*   **Date:** 2026-11-07
*   **Repository:** `C:/Users/ryuen/Documents/Projects/Headunit/lily-launcher`
*   **Commit verified:** `448761a` (`feat: add Magisk + Restart tiles to More sheet; debug speed slider (floating)`), branch `main`
*   **Scope:** Canvas NGE reskin in `HomeView`, `DeckView`, `KnobView`, `SheetView`, `Stage`, `Nge`; the 15-tile More sheet; the debug speed control; prev/next icon geometry; normal-zone palette; baked-image removal; design-doc consistency; build.
*   **Method:** static read of the Kotlin sources and design docs, `git` history/status, a debug + release Gradle build, and live QC on `emulator-5554` at 1024×600 (app data cleared, location permission granted). No source files were modified. This report is the only file added.
*   **Working tree before verification:** clean (no tracked changes).

---

## Verdict at a glance

| # | Check | Result | Summary |
|---|---|---|---|
| 1 | More sheet has 15 tiles (13 original + Magisk + Restart) with correct icons and labels | **PARTIAL** | 15/15 tiles exist, with the intended labels and glyphs. The third row overflows the sheet and the 600px screen: Japanese captions are clipped and the panel border/footer are obscured. |
| 2 | Debug speed slider (floating button) present | **PASS** | An 80×80 `SPD` floating button opens a `Debug speed` dialog with a 0–160 `SeekBar`; moving it to 160 produced the emergency palette. It overlaps the speed-zone seal and is not gated to debug builds. |
| 3 | Prev/next icons correctly oriented | **PASS** | Static geometry and the live screenshot both show PREV pointing left and NEXT pointing right. |
| 4 | Normal zone uses green `#4de89a` for text/accent | **PASS** | `Ui.zoneColor("text")` and `Ui.zoneColor("accent")` both return `#4de89a` in `SpeedZone.NORMAL`; the default live render is green. A user-selected non-stock accent overrides the accent in the normal zone by design. |
| 5 | All Canvas drawing uses `zoneColor()` (no hardcoded TE colors) | **PASS with exceptions** | Every chromatic UI colour in the five Canvas views is derived from `zoneColor()` (often via `withAlpha()`/`mix()`). The only non-`zoneColor` chromatic literals are the accent-picker swatch values and the `STOCK_ACCENT` comparison sentinel; no TE orange/lavender is painted as home chrome. |
| 6 | No baked `ui.image()` calls in HomeView/DeckView/KnobView/SheetView/Stage | **PASS** | Zero `ui.image` / `image(` call sites in all five files. `Ui.image()` and `Ui.preloadHome()` still exist as dead code in `Ui.kt`, but nothing calls them. |
| 7 | Design doc consistent with implementation | **FAIL** | The design doc still specifies a **13-tile** More sheet and does not mention Magisk, Restart, or the debug speed control. It also retains several pre-existing geometry/key claims that contradict `Nge.kt` and `Car.kt`. |
| 8 | Build passes | **PASS** | `:app:assembleDebug` and `:app:assembleRelease` both succeeded (exit 0). Only two deprecation warnings. |

**Overall:** the implementation implements the requested interaction and palette changes. Two user-visible defects remain: the More sheet's third row is vertically clipped, and the Magisk tile is a no-op because its launch component is package-only. The design document is stale relative to the implementation and must be updated before the design can be called consistent.

---

## 1. More sheet — 15 tiles

### What is implemented

`SheetView.kt:42-47` loads the 13 baked entries from `layout.json` `meta.more` and appends two runtime entries:

```kotlin
base.add(org.json.JSONObject().apply { put("name", "Magisk"); put("component", "com.topjohnwu.magisk"); put("special", null) })
base.add(org.json.JSONObject().apply { put("name", "Restart"); put("component", null); put("special", "restart") })
```

The grid is laid out 5 columns × 3 rows (`SheetView.kt:155-167`), and the header derives its count from the list (`"${more.size} MODULES · SELECT ONE"`), so it reads **15 MODULES** at runtime.

Live screenshot (1024×600, emulator-5554) confirms all 15 tiles, in order:

| # | Label | Icon | Japanese caption in source |
|---|---|---|---|
| 1 | RADIO | signal arcs + dial | 受信機 |
| 2 | USB MUSIC | USB stick | 記憶媒体 |
| 3 | VIDEO | frame + play | 映像 |
| 4 | DASHCAM | camera body + lens | 記録映像 |
| 5 | SOUND EQ | three faders | 音響 |
| 6 | AUX | jack rings | 外部入力 |
| 7 | FILES | folder | 書庫 |
| 8 | CAR | car body + wheels | 車両 |
| 9 | CAN | box + bus stubs | 車載網 |
| 10 | WHEEL KEYS | steering wheel | 操舵 |
| 11 | ANDROID | Android head | 接続 |
| 12 | SCREEN OFF | power symbol | 消灯 |
| 13 | HOME SETUP | house | 画面設定 |
| 14 | MAGISK | shield + “M” | 管理 |
| 15 | RESTART | circular arrow | 再起動 |

The two new glyphs are defined at `SheetView.kt:295-311`. The screenshot shows the shield-with-M in slot 14 and the circular-arrow in slot 15, matching the labels.

### Defect: the third row does not fit

Geometry in `SheetView.kt`:

*   `moreSheet = RectF(62f, 92f, 962f, 544f)` — sheet height **452px**.
*   Grid origin `y0 = r.top + 46f + 34f` = **172px**.
*   Tile height `th = 150f`, row gap `gy = 6f`.
*   Row tops: **172 / 328 / 484**; row bottoms: **322 / 478 / 634**.

The third row therefore extends to **y=634**, which is:

*   **34px past the 600px screen** (`Nge.H = 600f`), and
*   **90px past the sheet panel bottom** (544px).

Consequences visible in the live screenshot:

*   The Japanese sublabels on row 3 baseline at `y + 126` = **610px**, fully off-screen; they are not visible.
*   The English labels baseline at `y + 112` = **596px**, only 4px above the screen edge.
*   The panel's bottom border at 544px passes through the middle of the row-3 hexes and is hidden behind them.
*   The footer rule (512px) and footer text (532px) are covered by the row-3 tiles except at the left margin.

The hexagons themselves (centered at y=535, height 102) end at y=586 and remain visible; the issue is the tile cell/JP captions/panel containment, not the hex outlines. There is no scrolling in `SheetView`, so this is a real clipping defect, not an off-screen scroll position.

**Verdict:** count, order, labels, and icons pass; layout containment fails.

### Defect: Magisk tile does not launch anything

`SheetView.kt:44` sets the component to the bare package name `com.topjohnwu.magisk`. `SheetView.kt:471-473` passes that string to `Car.launch()`, and `Car.kt:467-471` does:

```kotlin
fun launch(component: String) {
    val cn = ComponentName.unflattenFromString(component) ?: return
    ...
}
```

`ComponentName.unflattenFromString()` requires a `package/class` pair (or `package/class#flatten`); a package-only string has no `/` and returns `null`. The tile is therefore a **silent no-op**, not an app launch. The same tile would also do nothing if Magisk is not installed. This is independent of the icon/label check but should be fixed before calling the sheet complete.

`Restart` calls `Runtime.getRuntime().exec("reboot")` (`SheetView.kt:470`). The app is not a system/root app, so this is device-dependent: on a normal Android build it will fail with a permission error, which is caught and only logged. The tile is visually correct, but the action has no user feedback and is not verified on hardware.

---

## 2. Debug speed slider (floating button)

`HomeActivity.kt:104-110` adds a floating `Button` labelled `SPD` to the root `FrameLayout`, 80×80, gravity bottom-end, 10px margins. `HomeActivity.kt:205-227` implements `showSpeedDialog()`:

*   title `Debug speed`;
*   a label showing the current speed;
*   an `android.widget.SeekBar` with `max = 160`, `progress = car.speedKmh`;
*   `onProgressChanged` calls `car.setSpeed(p)` and `stage.render()`;
*   a `DONE` button.

Live QC: tapping the floating `SPD` button opened the dialog; dragging the slider to the right end showed **160 km/h** and the UI switched to `ZONE · EMER`, with the red emergency palette, hazard stripes, and emergency ticker. This confirms the control is wired through the real `Car.setSpeed()` path, not a mocked display.

Caveats:

*   The button is an unstyled gray Android `Button`; the screenshot shows it overlapping the lower-right of the normal-zone octagonal seal (`drawSpeedPlate` draws the seal centered at 948,520 with radius 50; the button occupies x 934–1014, y 510–590).
*   The control is in the release source path and is labelled “remove later” in the comment, not guarded by `BuildConfig.DEBUG`. It will ship unless removed or gated.

**Verdict:** present and functional; placement/release hygiene issues noted.

---

## 3. Prev/next icon orientation

`HomeView.kt:667-675`:

```kotlin
T.PREV, T.NEXT -> {
    val d = if (t == T.PREV) 1f else -1f
    path.moveTo(cx + 2f * d, cy - 7f); path.lineTo(cx - 5f * d, cy); path.lineTo(cx + 2f * d, cy + 7f)
    path2.moveTo(cx + 9f * d, cy - 7f); path2.lineTo(cx + 2f * d, cy); path2.lineTo(cx + 9f * d, cy + 7f)
    ...
}
```

For PREV (`d = 1`), both triangles have their tips at smaller x (`cx-5`, `cx+2` with bases at `cx+2`, `cx+9`), i.e. both point **left**. For NEXT (`d = -1`), the same vertices mirror to the right. The live screenshot confirms the double-chevron PREV points left and NEXT points right. The actions are also correct: `T.PREV -> car.transport('b')`, `T.NEXT -> car.transport('n')` (`HomeView.kt:900-902`).

**Verdict:** PASS.

---

## 4. Normal-zone green palette

`Ui.kt:130-154` is the zone-aware accessor:

```kotlin
"text"   -> if (speedZone == SpeedZone.NORMAL) parseCss("#4de89a") else parseCss("#b9afcb")
"accent" -> if (speedZone == SpeedZone.NORMAL) parseCss("#4de89a") else parseCss("#ff4a1c")
```

Caution overrides both to `#f5b400`; emergency to `#e31c23`. `Stage.render()` copies `car.speedZone` into `ui.speedZone` before invalidating (`Stage.kt:38`), so the accessor reads the same zone the views render.

Live QC with cleared app data (default accent): the normal home screen is green-on-near-black: banner, labels, hex outlines, active BT key, knob arc, ladder, and speed digits are all `#4de89a`-family green. No amber/red/blue chrome appears in the normal zone.

Caveats:

*   `HomeView.accentColor()` (`HomeView.kt:65-70`) uses the user `accent` preference in the normal zone when it is not the stock `#FF5A1F` value. The default pref is `#FF5A1F` (`Car.kt:543`), so the default is the `#4de89a` palette. Selecting e.g. blue in the accent picker intentionally changes the normal-zone accent to blue; text remains green. This is a design feature, not a zoneColor defect.
*   The accent picker maps the stock `#FF5A1F` to `#FF4A1C` for display (`SheetView.kt:412`), while `HomeView` treats `#FF5A1F` as “use palette green.” The picker therefore highlights the orange swatch by default even though the rendered default accent is green; tapping that swatch changes the normal accent from green to `#FF4A1C`. This is a minor state/presentation inconsistency.
*   At night, `HomeView` dims the normal palette (`dim()` mixes toward black); the sheet and knob do not apply the same dimming. This is outside the green-token check but worth noting for visual consistency.

**Verdict:** PASS for the requested `#4de89a` text/accent rule.

---

## 5. Canvas colour sourcing (`zoneColor()` vs hardcoded TE colours)

Audited the five Canvas views for every `Color.*` / hex literal and every `draw*` call:

| File | `zoneColor` uses | Chromatic literals found | Assessment |
|---|---|---|---|
| `HomeView.kt` | 13 | `#FF5A1F` (`STOCK_ACCENT`, comparison only) | All painted chrome uses `bg()`, `ink()`, `zoneAccent()`, `onFill()`, `acc`, or `withAlpha()`/`mix()` of those. `Color.BLACK`/`Color.TRANSPARENT` are neutral. |
| `DeckView.kt` | 3 | none | Uses `zoneColor("accent")`, `zoneColor("alert")`, `zoneColor("ok")`, `Color.BLACK`. |
| `KnobView.kt` | 1 | none | Uses `zoneColor("text")` and the `acc()` callback (resolved accent); black well. |
| `SheetView.kt` | 4 | `#FF4A1C`, `#258AC3`, `#2FB35A`, `#F5B400`, `#B9AFCB`, `#FF5A1F` | Sheet chrome uses `ink()`/`bg()`/`accent()`/`onFill()`; chromatic literals are the five accent-picker swatches plus the stock-accent display mapping. |
| `Stage.kt` | 1 | none | Only copies `car.speedZone` into `ui.speedZone`; no drawing. |

The only drawn colours not obtained directly from `zoneColor()` are:

1. the five accent-picker swatches in `SheetView.accentRow()` (`SheetView.kt:406-424`), which must display the real selectable colours; and
2. `STOCK_ACCENT = "#FF5A1F"` in `HomeView.kt:914`, used only as a sentinel to decide whether the palette fallback applies (it is never assigned to a Paint).

No TE colours (`#FF5A1F`, `#b9afcb`, `#0a0a12`, `#888`, `#555`) are painted as Home/Deck/Knob/Sheet chrome. `Ui.zoneColor()` itself defines the palette literals, which is the intended single source of truth.

One nuance: `Ui.zoneColor("text2")` is a fixed `#888888` in every zone, and unknown names fall back to `#b9afcb`. Neither is currently used by the five views (`text2` has no call site), but the fallback is a latent TE-lavender path if a future caller passes an unknown name.

**Verdict:** PASS, with the accent-picker/sentinel exceptions documented above.

---

## 6. Baked-image removal

A repository-wide grep for `ui.image` / `image(` in `com/lily/launcher` returned **zero call sites**. Per-file counts:

| File | `ui.image` | `image(` | `c.draw*` calls |
|---|---:|---:|---:|
| `HomeView.kt` | 0 | 0 | 65 |
| `DeckView.kt` | 0 | 0 | 3 |
| `KnobView.kt` | 0 | 0 | 6 |
| `SheetView.kt` | 0 | 0 | 70 |
| `Stage.kt` | 0 | 0 | 0 |

`Stage` composes the Canvas views and never touches `Ui.image`. `App.kt` no longer calls `preloadHome()` (commit `ad7fbf6` removed it). `Ui.kt` still contains the general-purpose `image()` loader (`Ui.kt:100-107`), `preloadHome()` (`Ui.kt:79-84`), and the PNG decode path; they are dead for the current view layer. If the goal is to delete baked-image machinery entirely, those methods and the `layout.json` `img` block can be removed in a separate cleanup, but they are not called by the five views.

**Verdict:** PASS for the stated check.

---

## 7. Design-doc consistency

Primary design document: `docs/designs/nge-terminal-interface-v3.md` (titled “v4 — DeepSeek design target”). It was last changed in `95fb9ce`, before the Canvas reskin and before the 15-tile/debug-control commits, and it has not been updated for this implementation.

### 7a. Direct contradictions with the implementation

| Doc claim | Doc line(s) | Implementation | Result |
|---|---|---|---|
| “MORE sheet (13 tiles)” / “13-tile MORE sheet” | 23, 34, 210 | `SheetView.kt:44-45` appends Magisk + Restart; header renders 15 | **FAIL** |
| Magisk and Restart are not described anywhere | — (0 occurrences) | Present as tiles 14/15 | **FAIL** |
| “11-hex honeycomb” in the deck | 210 | `Nge.hexCenters` is 6 + 4 = **10**; `pads` has 10 entries | **FAIL** |
| “452px speed plate” | 210 | `Nge.speed = RectF(534f, …, 1014f, …)` = **480px** | **FAIL** |
| “~240 hexes” A.T. Field | 12, 89, 204 | `HomeView.buildHoneycomb()` tiles **1,380** hexes (31 rows, 45/44 per row) | **FAIL** (pre-existing) |
| “arranged horizontally” for MAGI | 236 | `Nge.magiTrap(i)` stacks vertically (`top + 30 + i*58`) | **FAIL** (pre-existing) |
| Trapezoid clip `polygon(15% 0, 85% 0, 100% 100%, 0 100%)` | 236 | `Nge.trapezoidPath` insets by `tr.width() * .09f` (9%) | **FAIL** (pre-existing) |
| “Reuse existing `theme` pref key (no new `dayNightMode` key)” | 196, 317 | `Car.kt:48-51,133` stores and reads a separate `dayNightMode` pref | **FAIL** |
| “18mm visual ≈ 86px at 160 dpi” | 286 | 18mm × 160dpi = **113.4px**; 86px is 13.65mm | **FAIL** (pre-existing arithmetic) |
| “6 CUJs” vs CUJ 1–9 | 78, 303 vs §7 | Nine CUJs are defined | **FAIL** (doc-internal) |
| RAM 22MB vs RAM < 25MB | 57/190 vs 284/333/359 | No implementation measurement in this pass | **FAIL** (doc-internal, unverified) |

### 7b. Claims that do match the implementation

| Doc claim | Implementation evidence | Result |
|---|---|---|
| Bands 32 rail / 42 banner / 338 main / 18 ticker / 170 deck | `Nge.kt:18-28` | PASS |
| Columns 152 telemetry / 18 kanji / 638 stage / 216 MAGI | `Nge.kt:31-34` | PASS |
| 104px knob | `Nge.kt:39` (`406..510`) | PASS |
| 116px speed readout, 24-segment ladder | `HomeView.kt:758, 773` | PASS |
| 3 MAGI trapezoids, 196×44, 10px inset | `Nge.kt:67-70` | PASS (orientation wording fails) |
| 5-colour accent picker | `SheetView.kt:511` | PASS (values, not default behaviour) |
| Normal palette “calm greens” / canonical mockup `#4de89a` | `Ui.kt:133,135` | PASS at intent level; doc has no normative hex table and the background is `#0a0a12`, not the mockup’s `#0b0b13`/`#0e0e17` |
| Canvas hexes / trapezoids / seals / knob / honeycomb | `Nge.kt`, `HomeView.kt`, `KnobView.kt` | PASS |
| Debug speed slider is a mockup simulator feature | `docs/designs/nge-terminal-interface-v3.md:217` | The **mockup** slider exists; the **app** debug `SPD` control is undocumented and is not the same feature |

### 7c. Other stale statements

*   `SheetView.kt:17` still says “The More grid (13 tiles)”; `SheetView.kt:148` says “MORE: 13 hex tiles.” Both are stale after the 15-tile change.
*   `docs/designs/nge-terminal-interface-v3.md:221` says “The existing app uses View + Canvas with baked images.” The view layer no longer draws baked images; only `Ui.kt` retains dead image-loading code.
*   The doc’s CUJ copy is not the implementation copy: e.g. CUJ 2 says header “CAUTION : SPEED LIMIT,” while `HomeView.zoneTitle()` renders “CAUTION // SPEED THRESHOLD EXCEEDED”; CUJ 3 says “第一種戦闘配置 : LILY,” while the implementation renders “EMERGENCY // 第一種戦闘配置.” The doc is describing intent, but it is not a textual match.

**Verdict:** FAIL. At minimum the doc needs a 15-tile More-sheet update, a Magisk/Restart entry, and the debug-control decision; the older geometry/key contradictions should be corrected in the same revision.

---

## 8. Build

Command run from `launcher/` through the Gradle compact wrapper:

```text
gradlew.bat :app:assembleDebug :app:assembleRelease --console=plain --no-scan
```

Result:

*   **exit status 0**
*   `:app:assembleDebug` and `:app:assembleRelease` both completed; no failed tasks.
*   Debug APK: `launcher/app/build/outputs/apk/debug/app-debug.apk`, 5,708,008 bytes, SHA-256 `669e7269d1f9d2706cd3ffb1940a215fbf8957dbad290d04032721029a36aad3`.
*   Release APK: `launcher/app/build/outputs/apk/release/app-release.apk`, 2,865,639 bytes, SHA-256 `35052e2af2fd70fd70be272cf06763a8eeb59120d7cb3ffa162f6dd977001414`.
*   Warnings only:
    *   `Car.kt:225` — `getParcelableExtra(String)` deprecated.
    *   `HomeActivity.kt:57` — overrides a deprecated member without `@Deprecated`.

The release build is minified/shrunk (`isMinifyEnabled = true`, `isShrinkResources = true`) and debug-signed per `app/build.gradle.kts`; it builds successfully. No runtime test of the release APK was performed in this pass (the debug APK was installed for UI QC).

**Verdict:** PASS.

---

## Additional findings

1. **More-sheet overflow (High).** Row 3 ends 34px below the screen and 90px below the panel; Japanese captions are unreadable. Fix by reducing tile height/gap, expanding the sheet, or using 3 rows × 5 columns with a shorter tile (e.g. 120px) and recalculating `y0`/footer.
2. **Magisk tile is a no-op (High).** `com.topjohnwu.magisk` has no `/class`; `Car.launch()` returns before starting anything. Use `PackageManager.getLaunchIntentForPackage()` or a valid `package/activity` component.
3. **Restart is privilege-dependent (Medium).** `Runtime.exec("reboot")` from a non-system app will normally fail; the exception is logged but the user gets no feedback. Verify on the target head unit, or use a supported reboot API/root path.
4. **Debug control ships in release (Medium).** The `SPD` button and dialog are unconditional and overlap the normal-zone seal. Remove them or gate them behind `BuildConfig.DEBUG`.
5. **Accent-picker default mismatch (Low).** The default pref `#FF5A1F` renders as green but the picker displays `#FF4A1C` as selected; tapping it changes the normal accent to orange-red.
6. **Stale comments (Low).** `SheetView.kt:17,148` still claim 13 tiles.
7. **Night dimming is not applied to `SheetView`/`KnobView` (Low).** `HomeView` dims normal-zone colours at night; the other views do not, so the sheet/knob stay at full brightness.

## Recommended follow-up (not applied)

*   Fix the More grid vertical layout and re-run the 1024×600 emulator check for all 15 tiles.
*   Fix the Magisk launch path; verify the Restart action on the actual head unit.
*   Update `docs/designs/nge-terminal-interface-v3.md`: 15 tiles, Magisk/Restart, debug-control decision, 10 keypad hexes, 480px speed plate, `dayNightMode` key, MAGI orientation/inset, 1,380-hex honeycomb, 113px/18mm, and one RAM figure.
*   Decide whether the debug `SPD` control should be removed before release or retained behind a debug flag.

## Evidence index

*   Static: `launcher/app/src/main/java/com/lily/launcher/{HomeView,DeckView,KnobView,SheetView,Stage,Nge,Ui,Car,HomeActivity,App}.kt`; `launcher/app/src/main/assets/ui/layout.json`; `docs/designs/nge-terminal-interface-v3.md`.
*   Git: `448761a` HEAD; `8bbd45f` (prev/next swap + green palette); `ad7fbf6` (Canvas reskin); working tree clean before report creation.
*   Live: emulator `emulator-5554`, 1024×600, debug APK installed; `pm clear` + location grant; screenshots captured at 1024×600 for normal home, More sheet, debug dialog, and emergency state. Screenshots are temporary QC artifacts and were not committed.
*   Build: Gradle wrapper output above; APK hashes listed in §8.
