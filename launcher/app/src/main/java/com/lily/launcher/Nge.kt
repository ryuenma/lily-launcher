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

    /** Deck row: 372 keypad · 132 knob · speed plate to the right edge. */
    val keypad = RectF(10f, deck.top + 8f, 382f, deck.bottom - 12f)
    val knobArea = RectF(392f, deck.top + 14f, 524f, deck.bottom - 8f)
    val knob = RectF(406f, deck.top + 22f, 510f, deck.top + 126f)
    val speed = RectF(534f, deck.top + 8f, 1014f, deck.bottom - 10f)

    // ── hex pads: 66×76 pointy-top, 57px pitch, two interlocking rows (6 + 4) ──
    const val HEX_W = 66f
    const val HEX_H = 76f
    const val HEX_PITCH = 57f

    val hexCenters: List<Pair<Float, Float>> = ArrayList<Pair<Float, Float>>(10).apply {
        val cx0 = keypad.centerX()
        val cy0 = keypad.centerY() - HEX_PITCH / 2f
        for (i in 0 until 6) add(cx0 + (i - 2.5f) * HEX_PITCH to cy0)
        for (i in 0 until 4) add(cx0 + (i - 1.5f) * HEX_PITCH to cy0 + HEX_PITCH)
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

/** Cached NGE text styles: (size, face, tracking in px, alignment) → one Paint. Main thread only. */
class NgeType(private val ui: Ui) {
    private val cache = HashMap<String, Paint>()

    fun paint(size: Float, mono: Boolean = false, jp: Boolean = false, track: Float = 0f,
              align: Paint.Align = Paint.Align.LEFT): Paint =
        cache.getOrPut("$size|$mono|$jp|$track|${align.name}") {
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                typeface = when {
                    mono -> ui.mono
                    jp -> ui.notoSerifJp
                    else -> ui.heros
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
