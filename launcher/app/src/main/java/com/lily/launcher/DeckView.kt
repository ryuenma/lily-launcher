package com.lily.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import java.util.Locale

/**
 * The fullscreen caution / emergency layer, traced 1:1 from the canonical simulator
 * (`mockup/nge-terminal-deepseek-v4.html`, `.warn` z-index 80 + `.ticker` z-index 3). Paint order
 * follows the mockup's own stacking, which is what makes it read the same:
 *
 *  1. `.ticker` band, 18 px at y 412–430, with the 26 s marquee — it sits *under* the overlay;
 *  2. `.warn` backdrop: caution gradient (72 % → 18 % → 18 % → 72 % black) or flat 62 % black;
 *  3. `.warn-bars` 26 px strips pinned to the very top and bottom edges, opaque black carrying
 *     -45° alert stripes that march one 34 px period per 1.1 s;
 *  4. the centered `.warn-box`: alert fill, 3 px black frame, 2 px alert outline, 14 × 40 padding,
 *     pulsing 1 → .38 on the 1.1 s `steps(1,end)` cycle;
 *  5. `.warn-tag` 46 px above the bottom edge.
 *
 * Every value below is the simulator's, not an approximation: `--alert` #f5b400/#e31c23,
 * `--alert-2` #ff6a00/#ff4a1c, `--on-fill` #000/#4de89a. The view lives in [Stage] above
 * [HomeView] *and* [KnobView], so the dial stays under the overlay instead of punching through it.
 */
@SuppressLint("ViewConstructor")
class DeckView(ctx: Context, private val ui: Ui, private val car: Car) : View(ctx) {
    var playing = false
        set(v) { if (field != v) { field = v; invalidate() } }

    private val type = NgeType(ui)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val stripe = Path()
    private var backdropShader: Shader? = null

    override fun onDraw(c: Canvas) {
        val zone = car.speedZone
        if (zone == SpeedZone.NORMAL) return

        val now = SystemClock.uptimeMillis()
        val em = zone == SpeedZone.EMERGENCY
        val onFill = if (em) GREEN else Color.BLACK          // --on-fill

        ticker(c, now, em)
        backdrop(c, em)
        val march = (now % MARCH_MS) / MARCH_MS.toFloat() * STRIPE_PERIOD   // @keyframes march
        bars(c, 0f, BAR_H, alert(em), march)                                // .warn-bars.top
        bars(c, Nge.H - BAR_H, Nge.H, alert(em), march)                     // .warn-bars.bottom
        box(c, now, em, onFill)
        tag(c, em)

        postInvalidateDelayed(FRAME_MS)
    }

    // ── .ticker (z-index 3, so the overlay dims it) ────────────────────────────────────────────
    private fun ticker(c: Canvas, now: Long, em: Boolean) {
        val t = Nge.ticker
        fill.color = if (em) alert2(em) else ink(em)     // .emergency --alert-2 / .caution --ink
        c.drawRect(t, fill)
        fill.color = Color.BLACK                          // .ticker.on{border-top-color:#000}
        c.drawRect(t.left, t.top, t.right, t.top + 1f, fill)

        c.save()
        c.clipRect(t.left, t.top + 1f, t.right, t.bottom)
        val p = type.paint(9f, track = 2.4f)
        p.color = if (em) GREEN else Color.BLACK          // color: var(--on-fill) / #000
        val msg = tickerText(em)
        val span = p.measureText(msg) + 48f               // .ticker-track span{padding-right:48px}
        val fm = p.fontMetrics
        val base = (t.top + 1f + t.bottom) / 2f - (fm.ascent + fm.descent) / 2f
        // @keyframes marquee{0%:translateX(0) 100%:translateX(-25%)} over 26s = exactly one span
        var x = -((now % MARQUEE_MS) / MARQUEE_MS.toFloat() * span)
        while (x < Nge.W) {
            c.drawText(msg, x, base, p)
            x += span
        }
        c.restore()
    }

    // ── .warn backdrop ─────────────────────────────────────────────────────────────────────────
    private fun backdrop(c: Canvas, em: Boolean) {
        if (em) {
            fill.color = withAlpha(Color.BLACK, .62f)      // .warn.emergency{rgba(0,0,0,.62)}
        } else {
            fill.color = Color.BLACK
            fill.shader = backdropShader ?: LinearGradient(0f, 0f, 0f, Nge.H, intArrayOf(
                withAlpha(Color.BLACK, .72f), withAlpha(Color.BLACK, .18f),
                withAlpha(Color.BLACK, .18f), withAlpha(Color.BLACK, .72f)),
                floatArrayOf(0f, .26f, .74f, 1f), Shader.TileMode.CLAMP).also { backdropShader = it }
        }
        c.drawRect(0f, 0f, Nge.W, Nge.H, fill)
        fill.shader = null
    }

    /**
     * `.warn-bars`: 26 px tall, `repeating-linear-gradient(-45deg, var(--alert) 0 12px, #000 12px
     * 24px)` on a 34 px tile. 12 px measured along the 45° axis is 17 px measured across, so the
     * tile holds exactly one alert + one black band: opaque black with a 17 px alert stripe every
     * 34 px, sheared 26 px across the strip's height.
     */
    private fun bars(c: Canvas, top: Float, bottom: Float, color: Int, shift: Float) {
        fill.color = Color.BLACK
        c.drawRect(0f, top, Nge.W, bottom, fill)
        fill.color = color
        val h = bottom - top
        var x = -STRIPE_PERIOD + shift
        while (x < Nge.W + STRIPE_PERIOD) {
            stripe.reset()
            stripe.moveTo(x, top)
            stripe.lineTo(x + STRIPE_ALERT, top)
            stripe.lineTo(x + STRIPE_ALERT - h, bottom)
            stripe.lineTo(x - h, bottom)
            stripe.close()
            c.drawPath(stripe, fill)
            x += STRIPE_PERIOD
        }
    }

    // ── .warn-box ──────────────────────────────────────────────────────────────────────────────
    private fun box(c: Canvas, now: Long, em: Boolean, onFill: Int) {
        val jp = if (em) "危険" else "注意"
        val en = if (em) "EMERGENCY" else "CAUTION"
        val sub = "SPEED ${pad(car.speedKmh)} KM/H · LIMIT " +
            pad(if (em) car.emergencyThreshold() else car.cautionThreshold())

        val jpP = type.paint(60f, track = 10f, align = Paint.Align.CENTER)
        val enP = type.paint(22f, track = 9f, align = Paint.Align.CENTER)
        val subP = type.paint(10f, mono = true, track = 2f, align = Paint.Align.CENTER)

        // .warn-box is 14/40 padding + a 3px border around its widest line; the divs stack as
        // 60 + 2 + 22×1.2 + 6 + 10×1.2, so the border box is 140.4px tall whatever the speed.
        val contentW = maxOf(jpP.measureText(jp), enP.measureText(en), subP.measureText(sub))
        val contentH = JP_LH + EN_MT + EN_LH + SUB_MT + SUB_LH
        val boxW = contentW + 2f * PAD_H + 2f * BORDER
        val boxH = contentH + 2f * PAD_V + 2f * BORDER
        val boxL = (Nge.W - boxW) / 2f
        val boxT = (Nge.H - boxH) / 2f

        val op = if ((now % PULSE_MS) / PULSE_MS.toFloat() < .65f) 1f else .38f   // @keyframes pulse

        // outline: 2px solid var(--alert), painted outside the border edge
        line.color = withAlpha(alert(em), op); line.strokeWidth = OUTLINE
        c.drawRect(boxL - OUTLINE / 2f, boxT - OUTLINE / 2f,
            boxL + boxW + OUTLINE / 2f, boxT + boxH + OUTLINE / 2f, line)
        // border: 3px solid #000, inside the border edge
        line.color = withAlpha(Color.BLACK, op); line.strokeWidth = BORDER
        c.drawRect(boxL + BORDER / 2f, boxT + BORDER / 2f,
            boxL + boxW - BORDER / 2f, boxT + boxH - BORDER / 2f, line)
        // background: var(--alert), running under the border
        fill.color = withAlpha(alert(em), op)
        c.drawRect(boxL + BORDER, boxT + BORDER, boxL + boxW - BORDER, boxT + boxH - BORDER, fill)

        val text = withAlpha(onFill, op)
        val top = boxT + BORDER + PAD_V
        val jpTop = top
        val enTop = jpTop + JP_LH + EN_MT
        val subTop = enTop + EN_LH + SUB_MT
        c.text(jpP, jp, Nge.W / 2f, jpTop + lineBaseline(jpP, JP_LH), text)
        c.text(enP, en, Nge.W / 2f, enTop + lineBaseline(enP, EN_LH), text)
        // .warn-sub{opacity:.85}
        c.text(subP, sub, Nge.W / 2f, subTop + lineBaseline(subP, SUB_LH),
            withAlpha(onFill, .85f * op))
    }

    /**
     * CSS baseline inside a fixed line box: the glyph box (ascent+descent) is centred in the line,
     * so a font whose metrics exceed `line-height:1` (any CJK face) hangs below the line top. Using
     * `lineH - descent` instead floats the kanji ~16 px high against the mockup.
     */
    private fun lineBaseline(p: Paint, lineH: Float): Float {
        val fm = p.fontMetrics
        return (lineH - (fm.descent - fm.ascent)) / 2f - fm.ascent
    }

    // ── .warn-tag (bottom:46px) ────────────────────────────────────────────────────────────────
    private fun tag(c: Canvas, em: Boolean) {
        val p = type.paint(9f, mono = true, track = 2.4f, align = Paint.Align.CENTER)
        val s = if (em) "SPEED THRESHOLD VIOLATION // 速度超過" else "APPROACHING LIMIT // 速度注意"
        c.text(p, s, Nge.W / 2f, Nge.H - 46f - p.fontMetrics.descent,
            if (em) GREEN else ink(em))
    }

    // ── zone palette straight from .screen[data-zone] ──────────────────────────────────────────
    private fun alert(em: Boolean) = if (em) RED else AMBER        // --alert
    private fun alert2(em: Boolean) = if (em) ORANGE else AMBER_2  // --alert-2
    private fun ink(em: Boolean) = if (em) ORANGE else AMBER       // --ink

    private fun pad(v: Int) = String.format(Locale.US, "%03d", v)

    private fun tickerText(em: Boolean): String {
        val spd = pad(car.speedKmh)
        return if (em) {
            "非常事態 // EMERGENCY — SPEED $spd KM/H — LIMIT ${pad(car.emergencyThreshold())} — REDUCE SPEED ///   "
        } else {
            "注意 // CAUTION — SPEED $spd KM/H — LIMIT ${pad(car.cautionThreshold())} — MONITOR GROUND SPEED ///   "
        }
    }

    companion object {
        // @keyframes march / pulse / marquee timings, in ms
        private const val MARCH_MS = 1_100L
        private const val PULSE_MS = 1_100L
        private const val MARQUEE_MS = 26_000L
        private const val FRAME_MS = 33L

        // .warn-bars: 26px strip, 34px tile, 17px alert band
        private const val BAR_H = 26f
        private const val STRIPE_PERIOD = 34f
        private const val STRIPE_ALERT = 17f

        // .warn-box metrics
        private const val PAD_H = 40f
        private const val PAD_V = 14f
        private const val BORDER = 3f
        private const val OUTLINE = 2f
        private const val JP_LH = 60f       // .warn-jp font-size 60 / line-height 1
        private const val EN_MT = 2f        // .warn-en margin-top
        private const val EN_LH = 26.4f     // 22px × 1.2
        private const val SUB_MT = 6f       // .warn-sub margin-top
        private const val SUB_LH = 12f      // 10px × 1.2

        // --alert / --alert-2 / --ink / --on-fill per zone
        private val AMBER = 0xFFF5B400.toInt()
        private val AMBER_2 = 0xFFFF6A00.toInt()
        private val RED = 0xFFE31C23.toInt()
        private val ORANGE = 0xFFFF4A1C.toInt()
        private val GREEN = 0xFF4DE89A.toInt()
    }
}
