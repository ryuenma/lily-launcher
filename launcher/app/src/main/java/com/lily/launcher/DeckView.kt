package com.lily.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.View
import java.util.Locale

/**
 * Live layer for the deck band: hazard stripes that march and the caution/emergency ticker that
 * scrolls over the deck row. It draws nothing in the normal zone, so the deck costs no frames
 * while the car is in the calm palette; when a zone is active it repaints only these two bands
 * (HomeView's deck, knob and speed plate stay cached underneath).
 */
@SuppressLint("ViewConstructor")
class DeckView(ctx: Context, private val ui: Ui, private val car: Car) : View(ctx) {
    var playing = false
        set(v) { if (field != v) { field = v; invalidate() } }

    private val type = NgeType(ui)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stripe = Path()

    override fun onDraw(c: Canvas) {
        val zone = car.speedZone
        if (zone == SpeedZone.NORMAL) return

        val now = SystemClock.uptimeMillis()
        val accent = ui.zoneColor("accent")
        val em = zone == SpeedZone.EMERGENCY
        val onFill = if (em) ui.zoneColor("ok") else Color.BLACK

        // marching hazard bands: above the ticker and along the bottom edge
        val shift = (now % 1100L) / 1100f * 24f
        hazard(c, Nge.ticker.top - 8f, Nge.ticker.top, accent, shift)
        hazard(c, Nge.H - 8f, Nge.H, accent, shift)

        // ticker band + marquee
        fill.color = if (em) ui.zoneColor("alert") else accent
        c.drawRect(Nge.ticker, fill)
        c.save()
        c.clipRect(Nge.ticker)
        val p = type.paint(9f, track = 2.4f)
        p.color = onFill
        val msg = tickerText(em)
        val w = p.measureText(msg) + 48f
        val travel = (now / 48L % w.toLong()).toFloat()       // ~48 px per second
        var x = Nge.ticker.left - travel
        while (x < Nge.W) {
            c.drawText(msg, x, Nge.ticker.centerY() + 3.5f, p)
            x += w
        }
        c.restore()
        postInvalidateDelayed(FRAME_MS)
    }

    /** Diagonal hazard stripes at -45°, marching one 24px period per 1.1 s (CSS `march`). */
    private fun hazard(c: Canvas, top: Float, bottom: Float, color: Int, shift: Float) {
        fill.color = color
        val h = bottom - top
        var x = -48f + shift
        while (x < Nge.W + 48f) {
            stripe.reset()
            stripe.moveTo(x, top)
            stripe.lineTo(x + 12f, top)
            stripe.lineTo(x + 12f - h, bottom)
            stripe.lineTo(x - h, bottom)
            stripe.close()
            c.drawPath(stripe, fill)
            x += 24f
        }
    }

    private fun tickerText(em: Boolean): String {
        val spd = String.format(Locale.US, "%03d", car.speedKmh)
        return if (em) {
            "非常事態 // EMERGENCY — SPEED $spd KM/H — LIMIT ${String.format(Locale.US, "%03d", car.emergencyThreshold())} — REDUCE SPEED ///   "
        } else {
            "注意 // CAUTION — SPEED $spd KM/H — LIMIT ${String.format(Locale.US, "%03d", car.cautionThreshold())} — MONITOR GROUND SPEED ///   "
        }
    }

    companion object { private const val FRAME_MS = 50L }
}
