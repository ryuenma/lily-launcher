package com.lily.launcher

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject

/**
 * Everything baked from the mockup by tools/bake.mjs: where each image goes, element rects,
 * and the text styles Chrome measured. Nothing here is hand-tuned; re-bake to change the look.
 */
class Ui(private val ctx: Context, initialTheme: String) {
    private val json = JSONObject(ctx.assets.open("ui/layout.json").bufferedReader().use { it.readText() })
    private val rects = json.getJSONObject("rect")
    val meta: JSONObject = json.getJSONObject("meta")
    /**
     * The three faces the mockup's CSS names, at the weights it asks for.
     *
     * Oswald and Noto Serif JP both ship as *variable* fonts, and their default instances are 400 and
     * **ExtraLight 200** — so `Typeface.createFromAsset` silently paints every kanji hairline and every
     * Oswald caption at regular, whatever the CSS says. Each face below is pinned to an explicit `wght`
     * axis instance; the Latin face carries Noto Serif JP as its fallback so a kanji inside an Oswald
     * run (`.banner .title{font-family:var(--font-en),var(--font-jp)}`) stays Mincho instead of dropping
     * to the system sans. Mono is a static Bold file — JetBrains Mono has no wght axis here.
     */
    val mono: Typeface = Typeface.createFromAsset(ctx.assets, "fonts/JetBrainsMono-Bold.ttf")
    // NERV UI fonts (nerv-ui reference)
    val notoSerifDisplay: Typeface = Typeface.createFromAsset(ctx.assets, "fonts/NotoSerifDisplay.ttf")
    val shipporiMinchoB1: Typeface = Typeface.createFromAsset(ctx.assets, "fonts/ShipporiMinchoB1.ttf")
    val sairaExtraCondensed: Typeface = Typeface.createFromAsset(ctx.assets, "fonts/SairaExtraCondensed.ttf")

    private val latinCache = HashMap<Int, Typeface>()
    private val jpCache = HashMap<Int, Typeface>()
    private val titleCache = HashMap<Int, Typeface>()
    private val warnCache = HashMap<Int, Typeface>()

    /** Oswald at [weight] (200–700), with Shippori Mincho B1 behind it for glyphs Oswald lacks. */
    fun latin(weight: Int): Typeface = latinCache.getOrPut(weight) {
        val w = weight.coerceIn(200, 700)
        runCatching {
            val oswaldFont = Font.Builder(ctx.assets, "fonts/Oswald.ttf").setFontVariationSettings("'wght' $w").build()
            val notoFont = Font.Builder(ctx.assets, "fonts/ShipporiMinchoB1.ttf").build()
            Typeface.CustomFallbackBuilder(FontFamily.Builder(oswaldFont).build())
                .addCustomFallback(FontFamily.Builder(notoFont).build())
                .build()
        }.getOrElse { e ->
            Log.w(Car.TAG, "latin@$w: falling back to the default instance", e)
            runCatching { Typeface.createFromAsset(ctx.assets, "fonts/Oswald.ttf") }
                .getOrDefault(Typeface.SANS_SERIF)
        }
    }

    /** Shippori Mincho B1 (NERV institutional JP) at [weight]. */
    fun jp(weight: Int): Typeface = jpCache.getOrPut(weight) {
        runCatching { Typeface.createFromAsset(ctx.assets, "fonts/ShipporiMinchoB1.ttf") }
            .getOrDefault(Typeface.SERIF)
    }

    /** Noto Serif Display (NERV title) at [weight] (700–900), variable font. */
    fun title(weight: Int): Typeface = titleCache.getOrPut(weight) {
        runCatching {
            val w = weight.coerceIn(700, 900)
            val font = Font.Builder(ctx.assets, "fonts/NotoSerifDisplay.ttf").setFontVariationSettings("'wght' $w").build()
            Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build()).build()
        }.getOrDefault(Typeface.SERIF)
    }

    /** Saira Extra Condensed (warning stamps) at [weight]. */
    fun warn(weight: Int): Typeface = warnCache.getOrPut(weight) {
        runCatching { Typeface.createFromAsset(ctx.assets, "fonts/SairaExtraCondensed-Bold.ttf") }
            .getOrDefault(Typeface.SANS_SERIF)
    }

    var theme = initialTheme; private set
    private var pending: String? = null
    private var first = true
    private val main = Handler(Looper.getMainLooper())
    private val rectCache = HashMap<String, RectF>()
    private val specs = HashMap<String, TextSpec>()

    /**
     * light | dark | night. The first call applies at once; later ones (headlights on/off) decode the new set
     * off the UI thread so Home never freezes, then swap, drop the old set and call [ready] on the main thread.
     */
    fun setTheme(t: String, ready: () -> Unit) {
        if (first) { first = false; theme = t; return }
        if (t == theme) { pending = null; return }
        if (t == pending) return
        pending = t
        main.post {
            if (pending != t) return@post
            pending = null; theme = t
            specs.clear()
            ready()
        }
    }

    fun rect(name: String): RectF = rectCache.getOrPut(name) { rects.getJSONObject(name).toRect() }
    fun metaRect(name: String): RectF = rectCache.getOrPut("meta/$name") { meta.getJSONObject(name).toRect() }

    /** [scale] < 1 is a smaller copy of the measured style (long song titles shrink to fit). */
    fun text(key: String, scale: Float = 1f): TextSpec = specs.getOrPut(if (scale == 1f) key else "$key@$scale") {
        TextSpec(json.getJSONObject("text").getJSONObject(theme).getJSONObject(key), this, scale)
    }

    fun themeVar(name: String): Int = parseCss(json.getJSONObject("vars").getJSONObject(theme).getString(name))

    /** Zone-aware color accessor for Canvas rendering (NGE palette). */
    fun zoneColor(name: String): Int {
        val base = when (name) {
            "bg" -> parseCss("#0a0a12")
            "text" -> if (speedZone == SpeedZone.NORMAL) parseCss("#4de89a") else parseCss("#b9afcb")
            "text2" -> parseCss("#888888")
            "accent" -> if (speedZone == SpeedZone.NORMAL) parseCss("#4de89a") else parseCss("#ff4a1c")
            "alert" -> parseCss("#e31c23")
            "caution" -> parseCss("#f5b400")
            "ok" -> parseCss("#4de89a")
            else -> parseCss("#b9afcb")
        }
        return when (speedZone) {
            SpeedZone.CAUTION -> when (name) {
                "text" -> parseCss("#f5b400")
                "accent" -> parseCss("#f5b400")
                else -> base
            }
            SpeedZone.EMERGENCY -> when (name) {
                "text" -> parseCss("#e31c23")
                "accent" -> parseCss("#e31c23")
                else -> base
            }
            else -> base
        }
    }

    var speedZone: SpeedZone = SpeedZone.NORMAL

    companion object {
        private const val OSWALD = "Oswald.ttf"
        private const val NOTO = "NotoSerifJP.ttf"
        fun isEmulator(): Boolean {
            val hw = android.os.Build.HARDWARE.lowercase()
            return hw.contains("ranchu") || hw.contains("goldfish") || hw.contains("qemu")
        }
    }
}

fun JSONObject.f(k: String) = getDouble(k).toFloat()
fun JSONObject.toRect() = RectF(f("x"), f("y"), f("x") + f("w"), f("y") + f("h"))

/** One text style measured in Chrome (size, letter-spacing, colour, shadows, real baseline). */
class TextSpec(j: JSONObject, ui: Ui, scale: Float = 1f) {
    val x = j.f("x"); val y = j.f("y"); val h = j.f("h") * scale
    val color = parseCss(j.getString("color"))
    val bg = parseCss(j.getString("bg"))
    val padL = j.f("padL")
    private val upper = j.getBoolean("upper")
    val lineHeight: Float = if (j.isNull("lineHeight")) h else j.f("lineHeight") * scale
    /** Offset from the element top to the first line's baseline (measured in Chrome). */
    val firstBaseline: Float = (j.f("baseline") - y) * scale
    private val shadows = parseShadows(j.getString("shadow"))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = if (j.getBoolean("mono")) ui.mono else ui.latin(400)
        textSize = j.f("size") * scale
        letterSpacing = j.f("ls")
        color = this@TextSpec.color
    }
    private val shadowPaint = Paint(paint)

    fun str(s: String) = if (upper) s.uppercase() else s
    fun width(s: String) = paint.measureText(str(s))

    /** Draws [s] with its CSS text-shadows, baseline at [base]. */
    fun draw(c: Canvas, s: String, x: Float, base: Float, color: Int = this.color) {
        val t = str(s)
        for (sh in shadows.asReversed()) {        // CSS paints the first shadow on top
            shadowPaint.set(paint)
            shadowPaint.color = sh.color
            shadowPaint.maskFilter = sh.filter
            c.drawText(t, x + sh.dx, base + sh.dy, shadowPaint)
        }
        paint.color = color
        c.drawText(t, x, base, paint)
    }

    /** Fits [s] into [maxW] with a trailing ellipsis, like CSS text-overflow. */
    fun ellipsize(s: String, maxW: Float): String {
        val t = str(s)
        if (paint.measureText(t) <= maxW) return t
        var n = paint.breakText(t, true, maxW - paint.measureText("…"), null)
        while (n > 0 && t[n - 1] == ' ') n--
        return t.substring(0, n) + "…"
    }

    /** Word-wraps into at most [maxLines] lines, ellipsizing the last. */
    fun wrap(s: String, maxW: Float, maxLines: Int): List<String> {
        val words = str(s).split(' ').filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        var line = ""
        var i = 0
        while (i < words.size) {
            val next = if (line.isEmpty()) words[i] else "$line ${words[i]}"
            if (paint.measureText(next) <= maxW || line.isEmpty()) { line = next; i++ } else {
                out += line; line = ""
                if (out.size == maxLines - 1) { line = words.drop(i).joinToString(" "); i = words.size }
            }
        }
        if (line.isNotEmpty()) out += line
        if (out.size > maxLines) out.subList(maxLines, out.size).clear()
        if (out.isNotEmpty()) out[out.size - 1] = ellipsize(out.last(), maxW)
        return out
    }

    private class Shadow(val color: Int, val dx: Float, val dy: Float, blur: Float) {
        val filter = if (blur > 0) BlurMaskFilter(blur / 2, BlurMaskFilter.Blur.NORMAL) else null   // made once, not per draw
    }

    private fun parseShadows(s: String): List<Shadow> {
        if (s == "none") return emptyList()
        return SHADOW.findAll(s).map { m ->
            Shadow(parseCss(m.groupValues[1]), m.groupValues[2].toFloat(), m.groupValues[3].toFloat(), m.groupValues[4].toFloat())
        }.toList()
    }

    companion object {
        private val SHADOW = Regex("""((?:rgba?|color)\([^)]*\)|#[0-9a-fA-F]+)\s+(-?[\d.]+)px\s+(-?[\d.]+)px\s+(-?[\d.]+)px""")
    }
}

private val NUM = Regex("""-?[\d.]+""")
private val cssCache = HashMap<String, Int>()   // main thread only

/** CSS colour → ARGB: #rgb, #rrggbb, rgb(), rgba(), color(srgb r g b / a). */
fun parseCss(s: String): Int = cssCache.getOrPut(s) {
    val t = s.trim()
    if (t.startsWith("#")) {
        val h = if (t.length == 4) t.drop(1).map { "$it$it" }.joinToString("") else t.drop(1)
        return@getOrPut Color.parseColor("#$h")
    }
    val nums = NUM.findAll(t.substringAfter('(')).map { it.value.toFloat() }.toList()
    val a = if (nums.size > 3) nums[3] else 1f
    if (t.startsWith("color(")) Color.argb((a * 255).toInt(), (nums[0] * 255).toInt(), (nums[1] * 255).toInt(), (nums[2] * 255).toInt())
    else Color.argb((a * 255 + .5f).toInt(), nums[0].toInt(), nums[1].toInt(), nums[2].toInt())
}

/** CSS color-mix(in srgb, c p%, other). */
fun mix(c: Int, other: Int, p: Float): Int = Color.argb(
    (Color.alpha(c) * p + Color.alpha(other) * (1 - p)).toInt(),
    (Color.red(c) * p + Color.red(other) * (1 - p)).toInt(),
    (Color.green(c) * p + Color.green(other) * (1 - p)).toInt(),
    (Color.blue(c) * p + Color.blue(other) * (1 - p)).toInt(),
)

fun withAlpha(c: Int, a: Float) = Color.argb((a * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))
