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
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val stripe = Path()

    private fun ink() = ui.zoneColor("text")

    override fun onDraw(c: Canvas) {
        val zone = car.speedZone
        if (zone == SpeedZone.NORMAL) return

        val now = SystemClock.uptimeMillis()
        val accent = ui.zoneColor("accent")
        val em = zone == SpeedZone.EMERGENCY
        val onFill = Color.BLACK
        val alertColor = if (em) ui.zoneColor("alert") else ui.zoneColor("caution")
        val jp = if (em) "危険" else "注意"
        val en = if (em) "EMERGENCY" else "CAUTION"
        val sub = "SPEED ${String.format(Locale.US, "%03d", car.speedKmh)} KM/H · LIMIT ${String.format(Locale.US, "%03d", if (em) car.emergencyThreshold() else car.cautionThreshold())}"
        val tag = "SPEED THRESHOLD EXCEEDED // 速度超過"

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
        val travel = (now / 48L % w.toLong()).toFloat()
        var x = Nge.ticker.left - travel
        while (x < Nge.W) {
            c.drawText(msg, x, Nge.ticker.centerY() + 3.5f, p)
            x += w
        }
        c.restore()

        // fullscreen dim layer
        if (em) {
            fill.color = withAlpha(Color.BLACK, .62f)
            c.drawRect(0f, 0f, Nge.W, Nge.H, fill)
        } else {
            fill.shader = LinearGradient(0f, 0f, 0f, Nge.H, intArrayOf(
                withAlpha(Color.BLACK, .72f), withAlpha(Color.BLACK, .18f),
                withAlpha(Color.BLACK, .18f), withAlpha(Color.BLACK, .72f)),
                floatArrayOf(0f, .26f, .74f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, Nge.W, Nge.H, fill)
            fill.shader = null
        }

        // center box: alert fill, 3px black border, 2px alert outline, pulse 1→.38 over 1.1s
        val op = if ((now % 1100L) / 1100f < .65f) 1f else .38f
        val jpP = type.paint(60f, track = 10f, align = Paint.Align.CENTER)
        val enP = type.paint(22f, track = 9f, align = Paint.Align.CENTER)
        val subP = type.paint(10f, mono = true, track = 2f, align = Paint.Align.CENTER)
        val contentW = maxOf(jpP.measureText(jp), enP.measureText(en), subP.measureText(sub))
        val contentH = 60f + 2f + 26f + 6f + 12f
        val boxW = contentW + 80f
        val boxH = contentH + 28f
        val boxL = (Nge.W - boxW) / 2f
        val boxT = (Nge.H - boxH) / 2f
        fill.color = alertColor
        fill.alpha = (op * 255f).toInt().coerceIn(0, 255)
        c.drawRect(boxL + 3f, boxT + 3f, boxL + boxW - 3f, boxT + boxH - 3f, fill)
        fill.alpha = 255
        val ol = line
        ol.color = withAlpha(alertColor, op); ol.strokeWidth = 2f
        c.drawRect(boxL - 1f, boxT - 1f, boxL + boxW + 1f, boxT + boxH + 1f, ol)
        ol.color = withAlpha(Color.BLACK, op); ol.strokeWidth = 3f
        c.drawRect(boxL + 1.5f, boxT + 1.5f, boxL + boxW - 1.5f, boxT + boxH - 1.5f, ol)
        val jf = jpP.fontMetrics; val ef = enP.fontMetrics; val sf = subP.fontMetrics
        var y = boxT + 14f
        c.text(jpP, jp, Nge.W / 2f, y + 60f - jf.descent, withAlpha(onFill, op))
        y += 62f
        c.text(enP, en, Nge.W / 2f, y + 26f - ef.descent, withAlpha(onFill, op))
        y += 32f
        c.text(subP, sub, Nge.W / 2f, y + 12f - sf.descent, withAlpha(onFill, .85f * op))

        // tag pinned 46px above bottom edge
        val tagP = type.paint(9f, mono = true, track = 2.4f, align = Paint.Align.CENTER)
        c.text(tagP, tag, Nge.W / 2f, Nge.H - 46f - tagP.fontMetrics.descent,
            if (em) ui.zoneColor("ok") else ink())

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
