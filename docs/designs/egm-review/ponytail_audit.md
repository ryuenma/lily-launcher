# Ponytail audit — Lily Launcher

**Repo:** `C:/Users/ryuen/Documents/Projects/Headunit/lily-launcher`
**Commit audited:** `32ceea9` (working tree clean)
**Date:** 2026-11-07
**Scope:** whole repo. Code focus `launcher/app/src/main/java/` (11 files, 3,320 lines, incl. `com.acloud.newinterface.MusicBean`) + design docs + build/install config. Report only — no files were modified.

**Load assumed:** one unit, one user, 1024×600, 4-core / 1–2 GB, 2 Hz GPS, no concurrent writers. Anything judged "at scale" is judged against a *decade-old Android 10 head unit*, which is a harsher target than a server.

**Method:** read every Kotlin file end to end, the manifest, both Gradle files, `bake.mjs`, both shell scripts, and the design docs; grepped every public `Ui`/view API for callers; inspected the built APKs for asset parity. No build, no device run, no test run (no tests exist).

---

## Scorecard

| Rank | Count | Meaning here |
|---|---:|---|
| Blocker | 3 | Broken artifact or unverified NFR on the shipping path |
| Fix | 15 | Wrong result / wasted work a user or the next dev will hit |
| Watch | 11 | Real but bounded; revisit when the area is touched |

The two structural facts that colour everything below:

1. **The view layer is 100 % hand-drawn Canvas.** `Ui.image()`, `preloadHome()`, `preloadSheets()`, `setTheme()`, `rect()`, `metaRect()`, `text()`, `themeVar()`, `TextSpec` and `Placed` have **zero callers** (verified by grep). The 288 baked PNGs (3.1 MB), `layout.json` (29 KB) and the 300-line `bake.mjs` feed nothing that is drawn — yet `Ui`'s constructor still parses `layout.json` and `SheetView` still reads `meta.more` out of it.
2. **Nothing is tested.** No `src/test`, no `src/androidTest`, no test dependency. CI runs `assembleRelease` only.

---

## Blockers

### P-01 — The last release build predates the font reskin, and the font fallback isn't actually a fallback

`app/build/outputs/apk/release/app-release.apk` was built 14:27. `NotoSerifJP.ttf` and `Oswald.ttf` were committed at 17:39 and 18:14. `unzip -l` on that release APK: `assets/fonts/` contains only `JetBrainsMono-Bold.ttf` and `texgyreheros-bold.otf` — **neither new font is in it**. `install.sh` builds and installs exactly that variant.

That artifact cannot start:

```kotlin
// Ui.kt:45-55
fun latin(weight: Int): Typeface = latinCache.getOrPut(weight) {
    runCatching { Typeface.CustomFallbackBuilder(family(OSWALD, w)) … }
        .getOrElse { e -> Log.w(…); Typeface.createFromAsset(ctx.assets, "fonts/$OSWALD") }
}
```

`Font.Builder(assets, "fonts/Oswald.ttf")` throws on a missing asset; `runCatching` catches it; the `getOrElse` branch then calls `createFromAsset` on the *same missing asset*, outside the guard. That `RuntimeException` propagates through `NgeType.paint` into `HomeView.onDraw` → crash on the first frame of the default Home. `Ui.jp()` has the identical shape.

Fix: rebuild release and verify on the unit (the only post-reskin artifact is the debug APK, 14.4 MB); make both fallbacks `createFromAsset` guarded, and fall back to `Typeface.DEFAULT` if the asset is absent, so a bad asset degrades the type instead of killing Home.

### P-02 — A debug control that fakes vehicle speed ships in the release UI

`HomeActivity.kt:104-110` adds an 80×80 `Button("SPD")` at bottom-right, opening a 0–160 SeekBar (`:205-227`) wired to `car.setSpeed(p)`. It is commented *"remove later"* and is present unconditionally — no `BuildConfig.DEBUG` guard. It sits over the speed plate (x 934–1014, y 510–590), i.e. on top of the 24-segment ladder, and it can put the UI into CAUTION/EMERGENCY with the car stationary (or show 0 at 120 km/h). On a device whose whole purpose is a speed-zone warning, that is a shipping defect, not a dev nicety.

Fix: delete both the button and `showSpeedDialog()`, or gate on `applicationInfo.flags and FLAG_DEBUGGABLE`.

### P-03 — The RAM/APK budget is now unmeasured and plausibly blown by the font

README:35 states **22 MB RAM, 3.3 MB release APK**. Both predate the reskin. `NotoSerifJP.ttf` is **12.9 MB** (8.1 MB gzipped), `Oswald.ttf` 168 KB.

`Ui.latin(w)` builds a `FontFamily` from Noto at weight `w` **and** `Ui.jp(w)` builds another; the caches are separate (`latinCache` keyed by weight, `jpCache` by JP weight). HomeView asks for latin 400/600/700/900 and jp 400/600/700/900 → up to 8 distinct `Font` objects over the same 12.9 MB file, and the two caches never share one. TTF is not in `noCompress`, so each `Font.Builder(assets, path)` decompresses the asset into a heap buffer (uncompressed assets are mmapped; compressed ones are copied). That is tens of MB of native/heap for one face. A rebuilt release APK is ≈ 8.1 + 3.1 + 0.4 ≈ **11–12 MB**, ~3.5× the documented number.

Fix (measure first): `adb shell dumpsys meminfo com.lily.launcher` after a theme switch and after opening the More sheet. Then either add `"ttf"` to `androidResources.noCompress` (mmap, costs ~5 MB APK) and share a single Noto `FontFamily` per weight between `latin()` and `jp()`, or subset the font to the ~120 glyphs actually drawn. Update README:35 with the new measurement either way.

---

## Fixes

### F-01 — `setSpeed()` redraws the whole home on every GPS fix, even when nothing changed

```kotlin
// Car.kt:82-83
if (speedZone != prev) changed()
changed()
```

The first line is dead (the second always runs). The unconditional one means every 2 Hz fix, and the 1 Hz stale-timer `setSpeed(0)`, posts a full `Stage.render()` → `HomeView.onDraw` (1,380 hexes + ~40 text runs + `Calendar.getInstance()` + ~10 list/array allocations) into a hardware layer that must be re-uploaded at 1024×600. While parked with GPS live this is a 1–2 Hz full-screen redraw of a static screen.

Fix: `if (speedKmh != kmh || speedZone != prev) changed()`; delete the dead `if`.

### F-02 — USB never gets a time readout; the `View3.USB` progress branch is unreachable

`HomeView.kt:472` shows elapsed/total for `TAPE || USB` gated on `car.posKnown`, but `posKnown` is only ever set on the BT paths (`Car.kt:214`, `:313`). `MUSIC_META` resets `counter`/`total` (`:226`) and never sets `posKnown`. So the USB view is permanently `--:--`, and a branch written for it is dead code.

Fix: either set `posKnown` from the USB player's state/position broadcast if one exists, or drop `View3.USB` from that branch and stop pretending.

### F-03 — The two threshold editors disagree, and neither enforces caution < emergency

- `SheetView.stepRow` (`:398-406`) steps by 5, clamps 20–200.
- `HomeActivity.showThresholdDialog` (`:194-199`) accepts any `toIntOrNull()` — 0, negatives, 9999 — and writes both keys directly.
- `Car.setSpeed` (`:75-81`) tests `>= e` first, so **emergency ≤ caution silently makes CAUTION unreachable**; the ladder (`HomeView:828-843`) then draws `LIMIT` labels out of order.

Same setting, three rules. Fix: one `Car.setThresholds(caution, emergency)` that clamps (`20..200`, `emergency >= caution + 10`) and is the only writer; point both UIs at it.

### F-04 — `Nge.inHex()` accepts taps up to 2× outside the hexagon in the shoulder band

```kotlin
// Nge.kt:68-69
val shoulder = HEX_H / 4f
return dy <= shoulder || dx <= hw * (hh - dy) / shoulder
```

`hexPath` puts the vertical sides at `±hh/2`, so the slanted shoulder spans `hh/2..hh`, not `hh/4..hh`. At `dy = 0.75·hh` the test allows `dx = hw` (33.6 px) where the drawn polygon is 16.8 px wide. Taps in that band register on a hex the user is not touching; with reverse-order hit-testing (`HomeView:911`) the *later* pad wins, so the action can be the neighbour's.

Fix: `shoulder = hh / 2f`.

### F-05 — One day/night rule, implemented twice, and the one the docs describe never runs on the unit

`Car.resolveNight()` (`:54-64`) is the clock rule (17:00/05:00). `readSettings()` (`:164-168`) re-implements the same `when` inline and **only calls `resolveNight()` when `mcu_light_state` is absent**. On the K2501 the key exists, so headlights always win and the clock is dead code in practice. That contradicts:

- design v3 §6 Q6 / CUJ 6 ("time-based … not headlight-based") and §8.4 ("Precedence: MANUAL > headlight-override > time-based");
- the Setup footer the app itself draws: `"THEME FOLLOWS CLOCK · NOT HEADLIGHT STATE"` (`SheetView:331`);
- README:25 ("themes that follow the headlights").

Three documents, two behaviours, one duplicated rule. Also `start()` (`:133`) reads `prefs.getString("dayNightMode", …)` **without** the `try/catch` that `str()`/`flag()` exist to provide — a wrongly-typed pref from an older build crashes `Application.onCreate`, i.e. a launcher crash loop.

Fix: single `resolveNight()` used by `readSettings()`, decide once whether headlights override or not and make the doc + footer say the same thing, and route every pref read through `str()`/`flag()`.

### F-06 — The track-length reader respawns a failing `logcat` every 5 s, forever

`Car.kt:324-346`: `while (true) { logcat …; waitFor(); sleep(5000) }` on a daemon thread. Exit status is never checked. With `READ_LOGS` not granted (fresh install, `pm grant` refused, user revoked), `logcat` exits immediately with an error; the loop restarts it every 5 s for the life of the process, logging a warning each time. That is a permanent process/battery tax and log spam on a unit where the log buffer is small.

Fix: count consecutive failures, stop after 3, and only retry on `ACTION_BOOT_COMPLETED`/app restart; check `waitFor() != 0`.

### F-07 — The Setup accent swatch shows a colour the UI is not using

`SheetView.accentRow` (`:414`) maps the stock default `#FF5A1F` → `#FF4A1C` and marks that swatch selected. `HomeView.accentColor()` (`:67-72`) treats `#FF5A1F` as "stock → use the palette", which in NORMAL is **green `#4de89a`**. So out of the box the settings sheet says "orange" while every latched key is green. After the user taps the orange swatch, the pref becomes `#FF4A1C`, the sheet still says orange, and now the UI really is orange — the swatch was lying only until it was pressed.

Fix: one function `activeAccent()` used by both; either drop the `#FF5A1F`→`#FF4A1C` alias or make the sheet render "palette" as its own unselected state.

### F-08 — The baked-asset pipeline is dead weight the app still depends on at startup

`Ui` is constructed on a background thread (`App.kt:15`) with `ctx.assets.open("ui/layout.json")`, `json.getJSONObject("img")`, `getJSONObject("rect")` — all synchronous, all unused. `SheetView.more` (`:43-48`) reads `ui.meta.getJSONArray("more")` for the 13 tile labels/components — the only live use of the file. Meanwhile `bake.mjs` bakes `mockup/index.html` (lavender `--ink:#b9afcb`), while the canonical mockup is `nge-terminal-deepseek-v4.html` (green `#4de89a`) and the app is hand-drawn from the latter. Re-baking therefore produces 288 images and a `layout.json` in the *wrong palette*, matching neither the design doc nor the app.

Fix: move the 13 More entries into Kotlin (`SheetView` already appends Magisk/Restart the same way), then delete `layout.json`, the 288 PNGs, `Ui`'s decode/cache API (`image`, `load`, `decode`, `decodeAll`, `preloadHome`, `preloadSheets`, `setTheme`, `placed`, `cache`, `files`, `TextSpec`, `Placed`, `rect`, `metaRect`, `text`, `themeVar` — ~140 lines), and either delete `bake.mjs` or repoint it at the canonical mockup. That removes 3.1 MB of APK, one startup parse, and an entire tool that currently can only make things worse.

### F-09 — `ui.get()` on the main thread, no timeout, no catch

`HomeActivity.onCreate` (`:92`) blocks the main thread on a `FutureTask` whose callable parses JSON and opens font assets. Any throw there becomes `ExecutionException` from `get()` → crash on Home start, with a stack that points at the Activity, not the cause.

Fix: `runCatching { ui.get(2, TimeUnit.SECONDS) }` with a visible fallback (or construct `Ui` inline now that it no longer needs to preload images — see F-08).

### F-10 — `SheetView.close()` clears `state` before animating, so the fade-out never renders

`close()` (`:82-87`) sets `kind = null` first; `onDraw` (`:98`) starts with `val k = kind ?: return`. The 200 ms alpha/translate animation therefore runs on a view that draws nothing: the sheet disappears instantly and the animation is invisible. Same for `close(false)` (fine) and for the scrim.

Fix: keep `kind` until `withEndAction`, and return early only when `visibility == GONE`.

### F-11 — The long-press threshold dialog is unreachable over most of the screen, and duplicates Setup

`root.setOnLongClickListener { showThresholdDialog() }` (`HomeActivity:103`) only fires where `HomeView.onTouchEvent` returns `false`, which is only when `hit()` returns null (`:923`). Any long-press on the keypad, the stage (`T.LCD` covers 638×338 px) or a MAGI trapezoid is swallowed. So "long-press anywhere" works on the telemetry rail, the speed plate and the banner — not where a thumb naturally rests.

Fix: handle long-press inside `HomeView` (track `ACTION_DOWN` time, fire at 500 ms if the pointer has not moved), or drop the dialog and keep Setup as the single editor (see F-03).

### F-12 — Per-frame work in the two animated views

- `DeckView.box()` reads `car.cautionThreshold()`/`emergencyThreshold()` per frame (`:130-131`, `:200-202`) — 2 `SharedPreferences` + `runCatching` per frame ≈ 60/s at 30 fps, for values that cannot change inside a zone.
- `DeckView.onDraw` runs `String.format` ×3 + `type.paint(...)` lookups whose cache key is a **new string per call** (`Nge.kt:132`) — ~4 allocations per frame.
- `HomeView.onDraw` allocates `intArrayOf`, `listOf`, `List<Pair>` and formatted strings on every redraw (`:253`, `:266-273`, `:421`, `:503-511`), i.e. 2 Hz while driving.

Fix: cache the two thresholds when a zone is entered (and invalidate on pref change); hoist the stripe `Path`/paints into fields; replace `NgeType.paint`'s string key with a small data key or pre-resolved fields.

### F-13 — Binder calls on the main thread in touch paths

`Car.setDayNightMode` (`:49-53`) calls `readSettings()` synchronously → 6 `Settings.System.getInt` binder reads inside a click handler; `SheetView` then calls `render()`. `applyPanel()` is correctly posted to `io`, but `readSettings` itself is not. Same pattern at `start()` (`:135`) on the main thread during `Application.onCreate`.

Fix: make `setDayNightMode` write the pref + optimistically set `night` + `changed()`, and let the existing content observer confirm; or post `readSettings` to `io` and hop back.

### F-14 — Documentation that no longer matches the code (details in the next section)

The design doc's own verification list is the acceptance criteria for this app and half of it is unimplemented or contradicted. Fix the doc or the code — not both silently.

### F-15 — No tests, and the cheap ones are the valuable ones

Nothing covers the parts that decide what the driver sees: `setFreq` unit parsing (`Car:241-256`), the hysteresis state machine (`:70-84`), `Nge.inHex`, `parseCss`/`mix`, `MusicBean.name` extension stripping. All are pure and take no Android types; they belong in `src/test` with JUnit, ~80 lines total. Today a regression in the 10 kHz vs 100 kHz branch or in `shoulder` ships silently (as F-04 did). Add `testImplementation("junit:junit:4.13.2")` and one CI step (`./gradlew testReleaseUnitTest`).

---

## Watch

| ID | Finding | Why it matters later |
|---|---|---|
| W-01 | `Car.receiver` is registered with permission `null` (`:142`) and accepts every vendor action string, including `ACTION_KEY_VALUE` (play/next/prev/launcher) and `ACTION_BT_CONNECTION_CHANGE` with an arbitrary device name. | Any app on the unit can spoof track titles, flip `inCall`, or pull Home forward. Fine on a single-user rooted unit; it is the first thing to tighten if the app is ever shared. |
| W-02 | `getprop` uses `Class.forName("android.os.SystemProperties")` behind `runCatching` (`:384-385`). | If the hidden-API list changes, `getprop` returns `""` forever and Android Auto auto-open silently dies with no log line beyond the catch. Log once at failure. |
| W-03 | `transport('p')` flips `btPlaying` optimistically before the binder call (`:515`), and `selectSource(USB)` schedules a play 300 ms later (`:490`). | With a dead binder or a fast source switch, the UI shows a play state the stereo never entered, and the delayed play fires after the user left USB. |
| W-04 | The whole stage rect is a hit target that opens the stock app (`HomeView:915`, `:948-952`). | Undocumented in the design doc; it also means a mis-aimed tap on the album glyph leaves Home. |
| W-05 | `enforceStart` guards the radio with `mcuSource == SOURCE_RADIO && lastAndroid != Src.BT` (`:423`). | Only protects FM when the user's last Android source was **USB**; with the default `lastAndroid = BT` the ignition-on path switches FM → BT. That is the documented behaviour ("play when the phone connects"), but the guard's comment ("don't hijack the radio you chose") describes a different rule. Decide which one it is. |
| W-06 | `DeckView` re-arms `postInvalidateDelayed(33)` from inside `onDraw` and never cancels it. | Bounded while the view is invisible (no draws), but the pending callback outlives `onPause` and the view object. Cancel in `onDetachedFromWindow`. |
| W-07 | Three independent timers run for the process lifetime: 2 s `getprop` poll (`:366-381`), 1 Hz `HomeView` ticker (`:895-906`), 500 ms GPS. | No throttling when the panel is off or Home is not visible. Acceptable today; it is the first thing a "why is the battery flat" report will find. |
| W-08 | The rail presents invented telemetry as measured: `GPS · 3D FIX` with no fix, `AAC 320`, `TRACKS 128`, `62%` signal, `12V`, `PRESET P03`, `3.2 KM / 00:07` (`HomeView:461`, `:508-511`, `:793`). | Consistent with the terminal aesthetic, but README's "every number in it is measured" refers to the trailer and the app reads as live data. Either label them decorative or wire them. |
| W-09 | `TextSpec.ellipsize` calls `breakText(t, true, maxW - dots, …)` with a potentially negative width (`Ui:254`); `parseCss` truncates alpha with `toInt()`; `withAlpha` truncates. | All in the unused `TextSpec` path today; if F-08 is only half-done and a view revives it, `breakText` with a negative budget returns 0 and the string becomes `"…"`. |
| W-10 | `eq/apply.sh` rewrites `DspData.xml` and `chmod 551`s its directory (`chmod 440` the file, `chown 10051`). | Any other writer to that prefs dir now fails silently; undoing needs root. Documented in README, but it is a data-loss trap for the next person who edits DSP settings. |
| W-11 | `lint { abortOnError = false; checkReleaseBuilds = false }` and CI only builds. | The build is the only gate, so unused imports, API-level mistakes and the dead code in F-08 can accumulate unrung. |

---

## Design docs vs code

| Doc claim | Reality |
|---|---|
| v3 §1 (:23) "MORE sheet (**15 tiles**)" then lists 13 names | 15 exist (13 baked + Magisk + Restart), but the enumeration is still the old 13. The EGM review flagged this exact pattern; it survived the revision. |
| v3 §7 CUJ 2/3 (:94, :100), §8.2 (:157), §8.3 (:186), §11 (:295), §13 (:336, :363): "hysteresis ±5 km/h **and ≥1 s dwell**" | Hysteresis exists (`Car:74-81`). **No dwell timer anywhere** — a single spurious fix can flash CAUTION for 500 ms. Half of B-08 remains open. |
| v3 §7 CUJ 5 (:113): full-screen `REVERSE` banner + hazard stripes | `car.reverse` is only used to defer Android Auto (`Car:173`, `:390`). No reverse overlay exists — the stock camera app covers the panel. |
| v3 §8.4 (:196) and §12 (:317): "Reuse existing `theme` pref key (**no new `dayNightMode` key**)" | `dayNightMode` exists and is the only thing the mode switch writes (`Car:48-53`). `theme` now means something else. |
| v3 §10 (:286): hex buttons "keyboard-navigable (Tab/Enter), focus indicators required" | `HomeView` has no `onKeyDown`, no focus states, no `isFocusable`. Only the knob handles keys, and it never receives focus (`KnobView:178-183`). |
| v3 §7 CUJ 7 (:125), §12 (:316, :327): permission rationale dialog, one-time GPS-disabled notice, string resources | `requestPermissions` with no rationale and no `onRequestPermissionsResult` (`HomeActivity:116-118`). No notice, no strings — every label is hardcoded Kotlin. |
| v3 §8.6 (:210) / §14 (:371): canonical mockup `nge-terminal-deepseek-v4.html` | `bake.mjs:13` bakes `mockup/index.html` (different palette, 101 KB, older layout). Neither matches the hand-drawn app. |
| v3 §2 (:34) "15-tile MORE sheet" vs `SheetView` KDoc (:18) "The More grid (13 tiles)" | Two counts in the same feature, one of them in the class that draws it. |
| `design-brief-nge-terminal.md` §7 (:243-246): SVG hexes, CSS glow, Rajdhani/Noto Sans CJK, accelerometer fallback | Every one of these was rejected by v4 (Canvas, zero glow R-13, Oswald/Noto Serif JP, GPS-only). The brief also promises reverse and incoming-call overlays (§5.2, :173-174) that do not exist. It ends with leaked shell lines `EOF` / `echo "written"` (:276-277). It is a pre-decision artefact still cited as a resource by v3 §14. |
| README:35 "22 MB … 3.3 MB release APK" | See P-03: font alone is 12.9 MB, and no measurement exists post-reskin. |
| README:25 themes "follow the headlights" vs Setup footer "THEME FOLLOWS CLOCK · NOT HEADLIGHT STATE" vs v3 §6 Q6 "not headlight-based" | Code: headlights win whenever `mcu_light_state` exists. Three statements, one behaviour. |
| v3 §1 (:12) A.T. Field "~240 hexes" | `HomeView.buildHoneycomb()` generates **1,380** hexes into one path, stroked every redraw. Either the doc or the density is wrong; the perf budget in §10 assumes the doc's number. |
| `docs/designs/nge-terminal-interface.md` (261 lines, "Draft", same date, no version marker) | Duplicate of v3's opening sections with the pre-v4 numbers (6 CUJs, old palette wording). Superseded, not marked as such, still in `docs/designs/`. |

Test plan (`docs/TESTING.md`) is 15 manual checkboxes and says so honestly ("There are no automated tests"). Two of its items are already false: "Headlights switch to the night theme" (they do, and they override the clock the Setup sheet claims to follow) and "Dragging the knob changes the volume one step per notch" (it sends one key broadcast **per 5 px of finger travel**, up to ~40 ordered broadcasts in a single `ACTION_MOVE` — `KnobView:141-151`).

---

## Lean / cuts

| Cut | Size | Note |
|---|---|---|
| Baked-asset path in `Ui` (F-08) | ~140 lines + 288 PNGs (3.1 MB) + `layout.json` | Zero callers. Keep only `mono`/`latin`/`jp`/`zoneColor`. |
| `bake.mjs` | 300 lines | Targets the wrong mockup; output unread. |
| `TextSpec` class | 77 lines (`Ui:216-292`) | Only instantiated by `Ui.text()`, which has no callers. |
| `HomeActivity` accelerometer block | 22 lines (`:76-84`, `:157-168`) | `if (m > 2.5) { /* hint only */ }` — registers a sensor at `SENSOR_DELAY_NORMAL` and does nothing with the data. Either implement the ≥500 ms hint the doc describes or delete the registration. |
| Debug speed button + dialog | 25 lines | P-02. |
| Duplicate day/night rule | ~12 lines | F-05. |
| Unused imports | 3 | `HomeActivity:22 ViewGroup`; `HomeView:7 LinearGradient`, `:11 Shader`. |
| Duplicate threshold editor | ~34 lines | F-11/F-03: pick Setup **or** the long-press dialog. |
| `docs/designs/nge-terminal-interface.md` | 261 lines | Superseded by v3; delete or mark Archived. Stale docs are read as truth. |

Dependencies: none were added, and the app still has zero libraries (`android.useAndroidX=false`, no `dependencies` block). That is the repo's best structural decision — keep it that way.

---

## What I did not read / did not do

- **Did not build or run.** No device was attached (`adb devices` empty) and the task forbids writes. All load and memory claims are static analysis plus APK inspection; P-03 needs a `dumpsys meminfo` before it is a number rather than a mechanism.
- **Did not read** `docs/architecture/lily-launcher.html` (859 KB single-file diagram), `mockup/index.html` in full (101 KB; only selectors, palette vars and the clock were grepped), `mockup/nge-terminal-deepseek-v4.html` beyond its palette, `mockup/archive/*` (9 files), the nine individual EGM stage reports (only `consolidated_review_report.md`), `eq/*.xml` values, or the PNG/JPG assets.
- **Did not evaluate** vendor-side behaviour: whether `pm grant READ_LOGS` succeeds on the unit, whether `persist.nwd.launcher.default` is still the selection mechanism, or whether the AIDL transaction numbers still match firmware. Those are `docs/INTEGRATION.md` claims taken as given.
- **Did not review** `Ui.decodeAll`'s `parallelStream()` concurrency in depth: it is only reachable from the dead preload path, and `cache` is a plain `HashMap` written from a worker thread in `preloadHome` — if F-08 is answered by *reviving* preload instead of deleting it, that race (and `preloadHome` vs `preloadSheets` both touching `cache`) has to be fixed first.

---

## Suggested order

1. P-01 rebuild + verify, and guard the font fallback (one commit).
2. P-02 delete the debug speed control.
3. F-01 `changed()` guard — biggest CPU win for two lines.
4. F-05 day/night: one rule, one pref, docs aligned (also settles the README:25 vs Setup-footer contradiction).
5. F-03 thresholds: one validated writer.
6. F-06 logcat loop, F-04 `inHex`, F-02 USB.
7. F-08 cut the dead pipeline (then F-09 disappears with it).
8. F-15 tests for the pure functions; add `./gradlew test` to CI.
9. Remaining fixes, then the doc rewrite against what now exists.
