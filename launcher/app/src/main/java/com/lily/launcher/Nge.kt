package com.lily.launcher

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Geometry and shared drawing helpers for the NGE terminal — the one source of truth for the
 * 1024×600 panel. Band heights are taken straight from the canonical mockup
 * (32 rail · 42 banner · 338 main · 18 ticker · 170 deck = 600), so HomeView, DeckView, KnobView,
 * SheetView and Stage all agree on where things sit without reading layout.json.
 */
object Nge {
    const val W = 1024f
    const val H = 600f

    const val RAIL_H = 32f
    const val BANNER_H = 42f
    const val MAIN_H = 338f
    const val TICKER_H = 18f
    const val DECK_H = 170f

    val rail = RectF(0f, 0f, W, RAIL_H)
    val banner = RectF(0f, RAIL_H, W, RAIL_H + BANNER_H)
    val main = RectF(0f, banner.bottom, W, banner.bottom + MAIN_H)
    val ticker = RectF(0f, main.bottom, W, main.bottom + TICKER_H)
    val deck = RectF(0f, ticker.bottom, W, H)

    /** 152px telemetry rail · 18px kanji band · 638px stage · 216px MAGI stack. */
    val telemetry = RectF(0f, main.top, 152f, main.bottom)
    val jpBand = RectF(152f, main.top, 170f, main.bottom)
    val stage = RectF(170f, main.top, 808f, main.bottom)
    val magi = RectF(808f, main.top, W, main.bottom)

    /** Deck row: 372×150 keypad · 132 knob · speed plate to the right edge (`.deck` padding 8px 10px). */
    val keypad = RectF(10f, deck.top + 10.5f, 382f, deck.top + 160.5f)
    val knobArea = RectF(392f, deck.top + 14f, 524f, deck.bottom - 8f)
    val knob = RectF(406f, deck.top + 22f, 510f, deck.top + 126f)
    val speed = RectF(534f, deck.top + 8f, 1014f, deck.bottom - 10f)

    // ── hex pads: the mockup's `#keypad` svg, viewBox 0 0 364 138 in a 372×150 CSS box ──
    // The element keeps its aspect ratio (SVG `preserveAspectRatio` defaults to xMidYMid meet), so the
    // viewBox is scaled uniformly by KEY_SCALE and letterboxed 4.48px top and bottom. Every number in
    // hexCenters is the mockup's own coordinate × KEY_SCALE, so the two layouts cannot drift apart.
    // Row 1 is six hexes at y=40, row 2 is five at y=97 — staggered by half a pitch (57 vs 28.5).
    const val KEY_SCALE = 372f / 364f                                // 1.02198
    private const val KEY_VB_TOP = (150f - 138f * KEY_SCALE) / 2f    // 4.4835 letterbox
    const val HEX_W = 65.82f * KEY_SCALE                             // polygon 2 × 32.91 → 67.27
    const val HEX_H = 76f * KEY_SCALE                                // polygon 2 × 38 → 77.67
    const val HEX_PITCH = 57f * KEY_SCALE                            // 58.25

    val hexCenters: List<Pair<Float, Float>> = ArrayList<Pair<Float, Float>>(11).apply {
        val ox = keypad.left
        val oy = keypad.top + KEY_VB_TOP
        for (i in 0 until 6) add((ox + (41f + i * 57f) * KEY_SCALE) to (oy + 40f * KEY_SCALE))
        for (i in 0 until 5) add((ox + (69f + i * 57f) * KEY_SCALE) to (oy + 97f * KEY_SCALE))
    }

    /** Point-in-hexagon test (bounding boxes overlap at the interlocking edges). */
    fun inHex(i: Int, x: Float, y: Float): Boolean {
        val (cx, cy) = hexCenters[i]
        val dx = kotlin.math.abs(x - cx)
        val dy = kotlin.math.abs(y - cy)
        val hw = HEX_W / 2f
        val hh = HEX_H / 2f
        if (dx > hw || dy > hh) return false
        val shoulder = HEX_H / 4f
        return dy <= shoulder || dx <= hw * (hh - dy) / shoulder
    }

    /** MAGI source trapezoids: 196×44 each, 10px inset inside the 216px column. */
    fun magiTrap(i: Int): RectF {
        val top = magi.top + 30f + i * 58f
        return RectF(magi.left + 10f, top, magi.right - 10f, top + 44f)
    }

    // ── shapes ──
    fun hexPath(p: Path, cx: Float, cy: Float, hw: Float, hh: Float): Path {
        p.reset()
        p.moveTo(cx, cy - hh)
        p.lineTo(cx + hw, cy - hh / 2f)
        p.lineTo(cx + hw, cy + hh / 2f)
        p.lineTo(cx, cy + hh)
        p.lineTo(cx - hw, cy + hh / 2f)
        p.lineTo(cx - hw, cy - hh / 2f)
        p.close()
        return p
    }

    /** MAGI trapezoid: narrow edge up, or inverted (narrow edge down) for the middle node. */
    fun trapezoidPath(p: Path, r: RectF, inset: Float, inverted: Boolean): Path {
        p.reset()
        val topIn = if (inverted) 0f else inset
        val botIn = if (inverted) inset else 0f
        p.moveTo(r.left + topIn, r.top)
        p.lineTo(r.right - topIn, r.top)
        p.lineTo(r.right - botIn, r.bottom)
        p.lineTo(r.left + botIn, r.bottom)
        p.close()
        return p
    }

    fun octagonPath(p: Path, cx: Float, cy: Float, radius: Float, cut: Float = .42f): Path {
        val a = radius * cut
        p.reset()
        p.moveTo(cx - a, cy - radius)
        p.lineTo(cx + a, cy - radius)
        p.lineTo(cx + radius, cy - a)
        p.lineTo(cx + radius, cy + a)
        p.lineTo(cx + a, cy + radius)
        p.lineTo(cx - a, cy + radius)
        p.lineTo(cx - radius, cy + a)
        p.lineTo(cx - radius, cy - a)
        p.close()
        return p
    }
}

/**
 * Cached NGE text styles: (size, face, weight, tracking px, alignment) → one Paint. Main thread only.
 *
 * [weight] is the CSS `font-weight` of the element the text belongs to, applied as a real variable-font
 * instance: Oswald is 200–700, Noto Serif JP 200–900. Default 400 is the CSS default — anything the
 * mockup bolds (600/700/900) must say so here, or it renders Regular.
 */
class NgeType(private val ui: Ui) {
    private val cache = HashMap<String, Paint>()

    fun paint(size: Float, mono: Boolean = false, jp: Boolean = false, track: Float = 0f,
              align: Paint.Align = Paint.Align.LEFT, weight: Int = 400): Paint =
        cache.getOrPut("$size|$mono|$jp|$track|${align.name}|$weight") {
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                typeface = when {
                    mono -> ui.mono          // JetBrains Mono Bold — numerals, readouts, chips
                    jp -> ui.jp(weight)      // Noto Serif JP (Mincho) — kanji runs
                    else -> ui.latin(weight) // Oswald, Noto Serif JP behind it for stray kanji
                }
                textSize = size
                if (size > 0f) letterSpacing = track / size
                textAlign = align
            }
        }
}

/** Draws [s] with [p], colour set in place (the paint is cached per style, not per colour). */
fun Canvas.text(p: Paint, s: String, x: Float, baseline: Float, color: Int) {
    p.color = color
    drawText(s, x, baseline, p)
}

/** Fits [s] into [maxW] with a trailing ellipsis. */
fun Paint.fit(s: String, maxW: Float): String {
    if (measureText(s) <= maxW) return s
    val dots = measureText("…")
    var n = breakText(s, true, (maxW - dots).coerceAtLeast(0f), null)
    while (n > 0 && s[n - 1] == ' ') n--
    return s.substring(0, n) + "…"
}
