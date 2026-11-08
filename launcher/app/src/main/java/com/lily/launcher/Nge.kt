package com.lily.launcher

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Geometry and shared drawing helpers for the NGE terminal — the one source of truth for the
 * 1024×600 panel.
 *
 * **7" revision.** The first pass traced the mockup's CSS pixel-for-pixel, which on a 7" 1024×600
 * panel reads as 9 px labels and 67×78 hexes — about 10 mm of glass per touch target. This pass
 * keeps the four bands and the NGE grammar (rail · banner · main · ticker · deck) but spends the
 * height on legibility instead of side-channels: rail and banner grow to fit 11–19 px text, the deck
 * grows to fit 80×92 hexes and a 124 px odometer, and the main area is rebalanced so the stage — the
 * part you actually read while driving — owns the room the telemetry signal bars used to waste.
 * Band budget: 40 + 54 + 300 + 22 + 184 = 600.
 */
object Nge {
    const val W = 1024f
    const val H = 600f

    const val RAIL_H = 40f
    const val BANNER_H = 54f
    const val TICKER_H = 22f
    const val DECK_H = 184f
    const val MAIN_H = H - RAIL_H - BANNER_H - TICKER_H - DECK_H   // 300

    val rail = RectF(0f, 0f, W, RAIL_H)
    val banner = RectF(0f, RAIL_H, W, RAIL_H + BANNER_H)
    val main = RectF(0f, banner.bottom, W, banner.bottom + MAIN_H)
    val ticker = RectF(0f, main.bottom, W, main.bottom + TICKER_H)
    val deck = RectF(0f, ticker.bottom, W, H)

    /** 168 telemetry rail · 24 kanji band · 616 stage · 216 MAGI stack. */
    val telemetry = RectF(0f, main.top, 168f, main.bottom)
    val jpBand = RectF(168f, main.top, 192f, main.bottom)
    val stage = RectF(192f, main.top, 808f, main.bottom)
    val magi = RectF(808f, main.top, W, main.bottom)

    /**
     * The one live visualiser: a full-width spectrum band under the track block. [SpectrumView]
     * paints it in its own pass, so the 20 fps loop repaints 436×38 px instead of the whole canvas.
     */
    val spectrum = RectF(stage.left + 164f, stage.top + 160f, stage.right - 16f, stage.top + 198f)

    /** Deck row: 468×164 keypad · 112 knob · 398 speed plate (`.deck` padding 10 px). */
    val keypad = RectF(10f, deck.top + 10f, 478f, deck.top + 174f)
    val knobArea = RectF(482f, deck.top + 8f, 610f, deck.bottom - 8f)
    val knob = RectF(490f, deck.top + 8f, 602f, deck.top + 120f)     // 112 px dial
    val speed = RectF(616f, deck.top + 8f, 1014f, deck.bottom - 8f)  // 398×168

    // ── hex pads: two staggered rows, six over five ──
    // Pointy-top hexes: width = √3 · hh, horizontal pitch = 1.5 · hh, row pitch = 1.5 · hh.
    // 80×92 is +19 % linear on the v4 mockup's 67×78 — the size a thumb on a 7" 2-DIN needs.
    const val HEX_W = 80f
    const val HEX_H = 92f
    const val HEX_PITCH = 69f
    /** Icons are drawn in mockup viewBox units; this is how far they grow with the pad. */
    const val ICON_SCALE = 1.18f

    val hexCenters: List<Pair<Float, Float>> = ArrayList<Pair<Float, Float>>(11).apply {
        val cx0 = keypad.centerX()
        for (i in 0 until 6) add((cx0 - 2.5f * HEX_PITCH + i * HEX_PITCH) to (keypad.top + 8f + HEX_H / 2f))
        for (i in 0 until 5) add((cx0 - 2f * HEX_PITCH + i * HEX_PITCH) to (keypad.top + 8f + HEX_H / 2f + HEX_PITCH))
    }

    /** Point-in-hexagon test (bounding boxes overlap at the interlocking edges). */
    fun inHex(i: Int, x: Float, y: Float): Boolean {
        val (cx, cy) = hexCenters[i]
        val dx = kotlin.math.abs(x - cx)
        val dy = kotlin.math.abs(y - cy)
        val hw = HEX_W / 2f
        val hh = HEX_H / 2f
        if (dx > hw || dy > hh) return false
        val shoulder = hh / 2f                     // the slant spans hh/2, not hh/4
        return dy <= shoulder || dx <= hw * (hh - dy) / shoulder
    }

    /** MAGI source trapezoids: 196×52 each, 64 px pitch inside the 216 px column. */
    fun magiTrap(i: Int): RectF {
        val top = magi.top + 36f + i * 64f
        return RectF(magi.left + 10f, top, magi.right - 10f, top + 52f)
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

    fun paint(size: Float, mono: Boolean = false, jp: Boolean = false, title: Boolean = false, warn: Boolean = false, track: Float = 0f,
              align: Paint.Align = Paint.Align.LEFT, weight: Int = 400): Paint =
        cache.getOrPut("$size|$mono|$jp|$title|$warn|$track|$weight|${align.name}") {
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                typeface = when {
                    mono -> ui.mono
                    jp -> ui.jp(weight)
                    title -> ui.title(weight)
                    warn -> ui.warn(weight)
                    else -> ui.latin(weight)
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
