# Re-baked NGE assets vs original: PNG-level diff and the GrallocUploadTh crash

**Scope:** `lily-launcher/launcher/app/src/main/assets/ui/` (288 PNGs + `layout.json`)
**Compared:** `d36ac94` (initial commit — the asset set the app was known-good with) vs `HEAD` = `161c6d3` (latest re-bake, Chrome), plus the assets actually packaged in `launcher/app/build/outputs/apk/debug/app-debug.apk`.
**Environment:** no `adb`/emulator SDK present on this machine, so nothing was run on a device. Every claim below is measured from the files on disk and the code in the repo. Where a conclusion needs a tombstone, it is marked as hypothesis.

---

## 1. Verdict

**No asset differs in any PNG metadata field.** Not one of the 288 files changed colour type, bit depth, interlace method, colour profile, gamma, compression method, filter method, chunk layout, or validity. The re-bake changed **pixel values only**.

So the `SIGSEGV` in `GrallocUploadTh` cannot be a PNG-decoding or PNG-format problem. `GrallocUploadTh` is HWUI's `HardwareBitmapUploader` thread, reached only when a bitmap is created as `Bitmap.Config.HARDWARE` — and the size/format it is asked to allocate is a function of `width × height × pixel format`, all of which are byte-for-byte identical between the two asset sets.

The asset set is therefore a **correlate, not a cause**. The crash surface is in the app's decode configuration (`Ui.kt`), which this report identifies and for which it proposes a fix.

---

## 2. What was measured

Method: parse every PNG chunk directly from `git show <rev>:<path>` (no working-tree copy), verify CRCs, inflate every `IDAT` stream, check the decompressed length against `(stride+1) × height`, histogram the per-scanline filter bytes, and decode every file with libpng via Pillow.

| Property | Original (`d36ac94`) | Re-baked (`HEAD`) | Differ? |
|---|---|---|---|
| Files | 288 | 288 | no |
| Byte-identical files | — | — | **0 / 288** |
| Total bytes | 2,853,898 | 2,429,634 | yes (−14.9 %) |
| Colour type | 279 × RGBA (6), 9 × RGB (2) | 279 × RGBA (6), 9 × RGB (2) | no |
| Bit depth | 8 for all | 8 for all | no |
| Interlace | 0 (none) for all | 0 (none) for all | no |
| IHDR compression method | 0 for all | 0 for all | no |
| IHDR filter method | 0 for all | 0 for all | no |
| Ancillary chunks (iCCP, sRGB, gAMA, cHRM, tRNS, PLTE, sBIT, eXIf, pHYs, acTL/fcTL) | **none** | **none** | no |
| Chunk sequence | `IHDR, IDAT…, IEND` | `IHDR, IDAT…, IEND` | no |
| IDAT chunking | 4096-byte chunks, trailing 6-byte zlib flush | same | no |
| zlib header | `78 9C` (deflate, 32 K window, default level) | `78 9C` | no |
| CRC validity | all valid | all valid | no |
| Trailing bytes after IEND | 0 | 0 | no |
| Inflated size == `(stride+1)×h` | 288/288 | 288/288 | no |
| Scanline filter bytes used | 1–4 (Sub/Up/Average/Paeth) | 1–4 | same alphabet |
| Decodes cleanly (libpng/Pillow) | 288/288 | 288/288 | no |
| Dimensions per slot vs `layout.json` (`w×scale`) | — | 0 mismatches | no |
| Alpha class (`has-alpha` / `opaque`) | 279 / 9 | 279 / 9 | no transitions |
| Fully transparent or single-colour tiles | 0 | 0 | no |
| Alpha coverage shift > 15 points | — | 0 files | no |

Sample chunk dumps (representative; all files follow the same pattern):

```
OLD bg@dark.png    309867B 1024x600 bd=8 ct=2 il=0  IHDR, IDAT(4096)×75, IDAT(1692), IDAT(6), IEND
NEW bg@dark.png    119219B 1024x600 bd=8 ct=2 il=0  IHDR, IDAT(4096)×29, IDAT(12),   IDAT(6), IEND

OLD knob_rot.png    70574B 412x412 bd=8 ct=6 il=0  IHDR, IDAT(4096)×17, IDAT(663),  IDAT(6), IEND
NEW knob_rot.png    71631B 412x412 bd=8 ct=6 il=0  IHDR, IDAT(4096)×17, IDAT(1720), IDAT(6), IEND
```

The only difference is the compressed payload length. `layout.json` is also structurally unchanged: `img` 114 entries, `rect` 66 entries, `meta` 13 entries, no added/removed keys, no NaN/Inf, no non-positive `w`/`h`; the single `rect` delta is `plateDiv.x` 240.984375 → 240.09375 (a text-measurement rounding).

### 2.1 The specific PNG landmines that were ruled out

Each of these is a real cause of Android/hardware-bitmap trouble, and each was checked and is absent from **both** revisions:

- **16-bit depth** (decodes to `RGBA_F16` → `R16G16B16A16_FLOAT` AHardwareBuffer, which many gralloc/GPU stacks reject or mishandle): absent, all 8-bit.
- **Indexed colour + `tRNS`** (palette decode path, 1-byte-per-pixel expansion): absent, no `PLTE`/`tRNS` anywhere.
- **Grey+alpha (colour type 4)**: absent.
- **Adam7 interlace**: absent.
- **Embedded ICC (`iCCP`)** or wide-gamut `sRGB`/`cHRM`/`gAMA`: absent. Since there is no colour profile, `BitmapFactory` cannot promote the image to an F16 hardware buffer for gamut preservation.
- **Apple `CgBI`, APNG (`acTL`/`fcTL`), `eXIf`**: absent from every asset.
- **Corrupt/truncated `IDAT`, bad CRC, trailing garbage**: none; 288/288 inflate to exactly the expected raster size.
- **Dimensions or `layout.json` scale changed**: none.

For contrast, the *non-asset* PNGs in this repo do carry chunks — the `docs/` screenshots have `sRGB`+`sBIT`, the launcher icons have `eXIf`+`sRGB` — which shows the chunk scanner is not blind. The asset pipeline simply emits bare `IHDR/IDAT/IEND`, identically in both revisions.

---

## 3. What actually differs

1. **Pixel values only** — a colour reskin (flat NGE fill replacing the metallic gradient; `bg@dark` drops 309 KB → 119 KB for that reason). Nothing that a decoder branches on.
2. **Per-file sizes move in both directions**, from −190 KB (`bg@dark`, the flattened gradient) through small jitter (`led_g_on` +8 B, `pad_play_play_up_FF5A1F@light` −69 B, `deck` +108 B) up to +38 KB (`more_bg@light`). Same dimensions, same format.
3. **Duplicate-group churn**: 50 groups of byte-identical images in the original set, 53 in the re-baked set; the additions are pairs that legitimately share content (`mi_4_up@dark` = `mi_4_down@dark`, `opt_3_*` across themes). No blank or all-transparent asset was produced.
4. **The APK that was actually built/installed is not HEAD.** `app-debug.apk` (built 22:27, assets copied at 22:27) predates the HEAD asset bake (22:39). 14 of its 288 assets differ from HEAD, and **5 of those match no commit at all**:

   ```
   differs from HEAD (14): deck.png, led_g_off.png, led_k_on.png, mi_12_up@night.png,
     mi_7_up@dark.png, opt_3_1_up@light.png, opt_3_2_up@dark.png, pad_next_next_up@night.png,
     pad_play_pause_down_2FB35A@night.png, pad_play_pause_down_F5B400@light.png,
     pad_play_pause_up_258AC3@light.png, pad_play_play_up_2FB35A@light.png,
     pad_prev_prev_down@dark.png, pad_prev_prev_down@light.png

   no matching commit (5): opt_3_1_up@light.png, pad_next_next_up@night.png,
     pad_play_pause_up_258AC3@light.png, pad_play_play_up_2FB35A@light.png,
     pad_prev_prev_down@light.png
   ```

   Those 14 were also checked: valid 8-bit RGBA, non-interlaced, correct CRCs, no ancillary chunks. So even the uncommitted bake is format-clean — but it means a crash reproduced from that APK is **not** reproducible from `git checkout HEAD`, which matters for bisection.

---

## 4. Why the crash is in the code path, not the files

`GrallocUploadTh` is the name Android's HWUI gives the `HardwareBitmapUploader` worker (`frameworks/base/libs/hwui/HardwareBitmapUploader.cpp`, started as `"GrallocUploadThread"`; Linux truncates thread names to 15 characters, which is why tombstones show `GrallocUploadTh`). It exists for exactly one operation: taking a `Bitmap.Config.HARDWARE` bitmap, allocating an `AHardwareBuffer` for it, binding that buffer as an `EGLImage`, and copying the decoded pixels into the texture.

Two properties of that path matter here:

- **The allocation is content-independent.** `AHardwareBuffer_allocate` is driven by `width`, `height` and the buffer format derived from `SkColorType` (`RGBA_8888` for the 279 RGBA tiles, `RGBX_8888` for the 9 RGB backgrounds). All three are unchanged across the re-bake, so the allocator sees an identical workload either way.
- **The upload is a pure memcpy of decoded pixels.** There is no branch on pixel values, no lookup table, no colour-managed conversion (there is no colour profile to convert from). A reskin cannot change which native calls run.

The one place where pixels *could* have mattered — an image becoming fully opaque and therefore decoding to `RGBX` instead of `RGBA` — was measured: the opacity class is identical for all 288 files.

What *is* fragile in this app is the way that path is entered, in `Ui.kt`:

```kotlin
private fun decodeAll(want: (String, String) -> Boolean): Map<String, Bitmap> {
    ...
    .parallelStream().forEach { n -> decode(n)?.let { out[n] = it } }   // N threads
}

private fun decode(f: String): Bitmap? {
    ...
    ctx.assets.open("ui/$f.png").use { BitmapFactory.decodeStream(it, null, OPTS) }
}

companion object {
    private val OPTS = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = Bitmap.Config.HARDWARE     // ← enters GrallocUploadTh
    }
}
```

Three concrete defects, in order of relevance to a native crash:

1. **`inPreferredConfig = HARDWARE` on an emulator.** Every one of the ~94 preloaded bitmaps (~14 MB of RGBA8) is created through `HardwareBitmapUploader`, i.e. through the thread that is crashing. This path is optional — it is a first-frame latency optimisation documented in the file's own comment ("nothing is uploaded on the first frame"). On a real unit with a vendor GPU it works (the README's 22 MB / 175 ms numbers were measured there); on the emulator's goldfish/SwiftShader/ANGLE gralloc it is the least-tested path in the stack. A `SIGSEGV` there is a native fault and **cannot be caught in Kotlin**, so a `try/catch` fallback is not a mitigation — the only fix is not to enter it.
2. **One shared, mutable `BitmapFactory.Options` across all decode threads.** `BitmapFactory` writes `outWidth`, `outHeight`, `outMimeType`, `outConfig`, `outColorSpace` into the `Options` object it is given. `decodeAll` runs `parallelStream()` while `preloadSheets()` runs on a second thread and `load()` can decode on the UI thread during `onDraw` — all sharing the same static `OPTS`. This is an unsynchronised data race on a JNI-written object.
3. **Hardware decoding starts in `Application.onCreate()`** on a bare `ui-load` thread (`App.kt`), before any `View`/window/RenderThread exists, and `Stage.init` additionally pins `HomeView` and `KnobView` to `LAYER_TYPE_HARDWARE`. So process start does: spawn a thread → allocate ~94 hardware buffers and upload them → then build a hardware-accelerated view hierarchy. Any failure in the first step surfaces as a native crash before the first frame, which matches "crashes on launch with these assets".

None of those three can be triggered *by* PNG metadata; all three can be triggered *by timing and build-to-build churn* — which is exactly what a re-bake produces (different file sizes → different decode durations → different thread interleaving → the latent fault appears or hides). That is the most economical explanation of "original works, re-baked crashes" given identical headers: a latent native fault in the hardware-bitmap upload path whose exposure moved with the re-bake's timing, not an image that is malformed.

**Hypothesis to confirm with a tombstone (not verified here):** the faulting frame will be inside `HardwareBitmapUploader::GLUploader::onUploadHardwareBitmap` → `eglCreateImageKHR` / `glEGLImageTargetTexture2DOES`, with `EGL_BAD_ALLOC` or a null `EGLImage` in the log immediately before the signal. If instead the tombstone shows `SkPngCodec` frames, this report's conclusion is wrong and the asset bytes should be re-examined — but the measurements above say they will not.

---

## 5. Proposed fix

### 5.1 Immediate (removes the crash surface)

Decode to `ARGB_8888` instead of `HARDWARE` on the emulator, keeping `HARDWARE` for the real head unit. At ~14 MB of decoded pixels the memory cost is irrelevant, and HWUI's normal texture cache handles the upload on `RenderThread` — the path every Android app exercises.

```kotlin
// Ui.kt
private fun decode(f: String): Bitmap? {
    if ("$f.png" !in files) return null
    // Per-decode options: BitmapFactory writes out* fields into this object.
    val o = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = PREFERRED
    }
    return try {
        ctx.assets.open("ui/$f.png").use { BitmapFactory.decodeStream(it, null, o) }
    } catch (e: IOException) { null } catch (e: OutOfMemoryError) { null }
}

companion object {
    // Hardware bitmaps are uploaded through HWUI's GrallocUploadThread; the emulator's
    // gralloc is not reliable there. Vendor units are.
    private val PREFERRED: Bitmap.Config =
        if (Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") ||
            Build.FINGERPRINT.startsWith("generic"))
            Bitmap.Config.ARGB_8888
        else
            Bitmap.Config.HARDWARE
}
```

Then delete the static `OPTS`. If you want to keep a single fast path, at least make it `ThreadLocal<BitmapFactory.Options>`.

### 5.2 If `HARDWARE` must stay on the emulator

Bound the concurrency rather than decoding ~94 buffers across all cores at once (e.g. `Executors.newFixedThreadPool(2)` instead of `parallelStream()`), and defer `preloadHome` until after the first frame so the RenderThread and its EGL context already exist. This does not fix a driver bug, it only stops hitting it during startup.

### 5.3 Bake-side hardening (defence in depth)

The current pipeline already emits the ideal form (`8-bit RGBA/RGB`, non-interlaced, no ancillary chunks, `78 9C`, 4096-byte IDATs). Add a post-bake normalisation step so a future Chrome/Edge version cannot silently change that:

```
# after node tools/bake.mjs
pngcrush -q -rem alla -rem text -l 9 <file> <file>.tmp && mv <file>.tmp <file>
# or: convert <file> -depth 8 -strip <file>
```

and assert the invariant in CI: every `assets/ui/*.png` must have `bit depth 8`, `colour type ∈ {2,6}`, `interlace 0`, and no chunks outside `{IHDR, IDAT, IEND}`. That is a 20-line Python check and it turns "the bake changed the assets' encoding" from a mystery into a build failure.

### 5.4 Also fix, independent of the crash

- Rebuild and re-install from a clean `HEAD` bake before drawing any conclusion: the APK on disk contains 14 assets that are not in any commit (§3.4).
- `decodeAll` swallows every `Throwable` into a `Log.w`, so a systematically failing decode looks like "the image just isn't there". Log the file name.

---

## 6. How to confirm in 10 minutes on the emulator

```bash
adb logcat -c
adb install -r launcher/app/build/outputs/apk/debug/app-debug.apk
adb logcat -b crash -v threadtime        # expect: Fatal signal 11 ... in tid NNN (GrallocUploadTh)
adb shell ls -l /data/tombstones
adb pull /data/tombstones/tombstone_00
adb shell getprop ro.hardware.egl        # swiftshader / angle confirms the software GL stack
```

Then bisect the asset set, not the metadata — swap in the good set, confirm clean, and replace half the files at a time; if the crash follows no particular file (i.e. it is flaky), that is positive evidence for the timing/race explanation in §4.

Quick sanity check that the two sets really are format-identical, runnable from the repo root:

```bash
git show d36ac94:launcher/app/src/main/assets/ui/bg@dark.png | python3 -c \
  "import sys,struct;d=sys.stdin.buffer.read();print(struct.unpack('>IIBBBBB',d[16:29]))"
git show HEAD:launcher/app/src/main/assets/ui/bg@dark.png   | python3 -c \
  "import sys,struct;d=sys.stdin.buffer.read();print(struct.unpack('>IIBBBBB',d[16:29]))"
# both print (1024, 600, 8, 2, 0, 0, 0)
```

---

## 7. Limitations

- No device, emulator, `adb` or Android SDK is installed here, so the `SIGSEGV` was not reproduced and no tombstone was read. The conclusion about *what cannot be the cause* rests on complete measurement of all 288 files in three copies (original commit, HEAD, packaged APK) and is solid. The conclusion about *what is the cause* is a ranked hypothesis, stated as such.
- The task premise ("same dimensions, format, size") is confirmed for dimensions and format; **size is not the same** — the re-baked set is 424 KB smaller overall, with per-file deltas from −190,648 B (`bg@dark.png`, 309,867 → 119,219, the flattened gradient) to +38,097 B (`more_bg@light.png`, 80,597 → 118,694). That size delta is the most likely reason the crash's timing changed.
