package com.lily.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import kotlin.math.sin

/**
 * The single live visualiser: the NGE spectrum band under the track block.
 *
 * The v4 mockup had two: a static 12-bar `.signals` strip in the telemetry column and the
 * `.spectrum` under the track (CSS `@keyframes spec`, bars alternating 640/780/900/1100 ms). The
 * signals strip carried no data and the spectrum was frozen in the app, so this view keeps the
 * spectrum and animates it — one loop, one place to look.
 *
 * It is its own view for a reason: the loop runs at 20 fps, and repainting a 436×38 rect is cheap
 * where repainting [HomeView] (honeycomb + every band) twenty times a second is not.
 */
@SuppressLint("ViewConstructor")
class SpectrumView(ctx: Context, private val ui: Ui, private val car: Car) : View(ctx) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    // Base heights straight from the mockup's spectrumHTML(): [40, 72, 55, 90, …]
    private val base = floatArrayOf(
        40f, 72f, 55f, 90f, 64f, 100f, 48f, 82f, 58f, 94f, 68f, 44f, 86f, 60f,
        52f, 96f, 62f, 78f, 46f, 88f, 70f, 100f, 54f, 84f, 66f, 92f, 50f, 76f,
        60f, 98f, 44f, 80f, 56f, 90f, 64f, 46f
    )
    // Per-bar phase and speed, so the band breathes like the CSS animation instead of pumping in unison.
    private val phase = FloatArray(base.size) { (it * 2.399f) % 6.2832f }
    private val speed = FloatArray(base.size) { 5.7f + (it % 4) * 1.5f }   // rad/s ≈ 640–1100 ms periods

    init { setWillNotDraw(false) }

    private fun active(): Boolean = when {
        car.source == Src.RADIO -> !car.muted
        car.source == Src.USB -> car.usbPlaying
        else -> car.playing
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val ink = if (car.night && car.speedZone == SpeedZone.NORMAL)
            mix(ui.zoneColor("text"), Color.BLACK, .78f) else ui.zoneColor("text")
        val live = active()
        val t = SystemClock.uptimeMillis() / 1000f

        val gap = 5f
        val barW = 6f
        val count = ((w + gap) / (barW + gap)).toInt().coerceAtMost(base.size)
        val baseline = h - 1f
        val full = h - 6f

        for (i in 0 until count) {
            val wave = sin(t * speed[i] + phase[i]) * .5f + .5f       // 0..1
            val f = if (live) base[i] / 100f * (.18f + .82f * wave) else base[i] / 100f * .12f
            val bh = full * f
            fill.color = if (live) withAlpha(ink, .55f + .35f * f) else withAlpha(ink, .30f)
            val x = i * (barW + gap)
            c.drawRect(x, baseline - bh, x + barW, baseline, fill)
        }

        line.color = withAlpha(ink, .28f)
        line.strokeWidth = 1f
        c.drawLine(0f, baseline, w, baseline, line)

        if (live) postInvalidateDelayed(FRAME_MS)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        invalidate()
    }

    companion object { private const val FRAME_MS = 50L }
}
