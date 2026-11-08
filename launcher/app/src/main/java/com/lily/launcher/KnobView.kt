package com.lily.launcher

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The 104px volume knob: lavender body ring, tick ring, accent arc and notch, value in the well.
 * Drag up/down to step (5 px per step, like the mockup), scroll wheel and arrow / volume keys also
 * step it, and a tap opens the EQ.
 */
@SuppressLint("ViewConstructor")
class KnobView(ctx: Context, private val ui: Ui, private val car: Car,
               private val acc: () -> Int, private val onStep: () -> Unit) : View(ctx) {

    /** Screen-space area this view covers: the dial plus its VOLUME / value labels. */
    val area: RectF = RectF(Nge.knobArea)

    private val type = NgeType(ui)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val tmp = RectF()
    private val ease = PathInterpolator(.2f, .8f, .2f, 1f)
    private val dial = Nge.knob
    private val unit = dial.width() / 100f          // the mockup draws the dial in a 100×100 box

    /** Volume the knob shows: the finger's while dragging, then held until the stereo reports it. */
    private var shown = car.vol
    private var holdUntil = 0L
    private var angle = angleFor(car.vol)
    private var anim: ValueAnimator? = null
    private fun angleFor(v: Int) = -135f + v.toFloat() / car.maxVol * 270f

    init { isFocusable = true; isClickable = true }

    /** Stereo volume changed (wheel, rotary knob, or our own steps arriving). */
    fun onVolume() {
        if (dragging || SystemClock.uptimeMillis() < holdUntil && car.vol != shown) return
        shown = car.vol
        anim?.cancel()
        anim = ValueAnimator.ofFloat(angle, angleFor(shown)).apply {
            duration = 150
            interpolator = ease
            addUpdateListener { angle = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        c.translate(-left.toFloat(), -top.toFloat())          // draw in screen coordinates
        val cx = dial.centerX()
        val cy = dial.centerY()
        val ink = ui.zoneColor("text")
        val a = acc()
        val frac = (shown.toFloat() / car.maxVol).coerceIn(0f, 1f)
        val lit = (frac * 20).roundToInt()

        // tick ring: 21 ticks over the 270° travel, lit up to the current volume
        for (i in 0..20) {
            val ang = Math.toRadians(-135.0 + i * 13.5)
            line.strokeWidth = 1f * unit
            line.color = if (i <= lit) a else withAlpha(ink, .25f)
            val r1 = 33f * unit
            val r2 = 36f * unit
            c.drawLine(cx + (sin(ang) * r1).toFloat(), cy - (cos(ang) * r1).toFloat(),
                cx + (sin(ang) * r2).toFloat(), cy - (cos(ang) * r2).toFloat(), line)
        }

        // lavender body ring
        line.color = ink
        line.strokeWidth = 7f * unit
        c.drawCircle(cx, cy, 45f * unit, line)

        // accent arc: 270° from the lower left, filled to the volume fraction
        tmp.set(cx - 38f * unit, cy - 38f * unit, cx + 38f * unit, cy + 38f * unit)
        line.color = a
        line.strokeWidth = 3f * unit
        c.drawArc(tmp, 135f, 270f * frac, false, line)

        // well + notch
        fill.color = Color.BLACK
        c.drawCircle(cx, cy, 31f * unit, fill)
        line.color = ink
        line.strokeWidth = 1.5f * unit
        c.drawCircle(cx, cy, 31f * unit, line)
        c.save()
        c.rotate(angle, cx, cy)
        line.color = a
        line.strokeWidth = 3f * unit
        c.drawLine(cx, cy - 29f * unit, cx, cy - 20f * unit, line)
        c.restore()

        // value in the well, then the labels under the dial
        val vp = type.paint(26f, mono = true, align = Paint.Align.CENTER)
        c.text(vp, "$shown", cx, cy + 9f, ink)
        val lab = type.paint(8f, track = 2.4f, align = Paint.Align.CENTER)
        c.text(lab, "VOLUME", cx, cy + 52f * unit + 16f, withAlpha(ink, .5f))
        val out = type.paint(11f, mono = true, track = .6f, align = Paint.Align.CENTER)
        c.text(out, "$shown / ${car.maxVol}", cx, cy + 52f * unit + 30f, ink)
    }

    // ── touch: drag up/down one step per 5 px (like the mockup), tap opens EQ ──
    private var pressed = false
    private var dragging = false
    private var downY = 0f
    private var startVol = 0

    private fun inWell(x: Float, y: Float) =
        kotlin.math.hypot(x - dial.centerX(), y - dial.centerY()) <= 52f * unit

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x + left
        val y = e.y + top
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!inWell(x, y)) return false
                pressed = true
                dragging = false
                downY = y
                startVol = shown
                anim?.cancel()
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = downY - y
                if (!dragging && abs(dy) > 4f) dragging = true
                if (dragging) {
                    val target = (startVol + (dy / 5f).roundToInt()).coerceIn(0, car.maxVol)
                    while (shown != target) {                     // one wheel-key step per notch
                        val up = target > shown
                        car.volumeStep(up)
                        shown += if (up) 1 else -1
                        onStep()
                    }
                    angle = angleFor(shown)                       // follow the finger, no easing lag
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging && inWell(x, y)) car.launch(Car.EQ_UI)
                pressed = false
                dragging = false
                holdUntil = SystemClock.uptimeMillis() + 1500 // keep our value while the MCU catches up
                postDelayed({ if (!dragging) onVolume() }, 1600)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressed = false; dragging = false; invalidate() }
        }
        return true
    }

    /** Mouse wheel / trackball over the dial. */
    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_SCROLL) return super.onGenericMotionEvent(e)
        if (!inWell(e.x + left, e.y + top)) return super.onGenericMotionEvent(e)
        step(e.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0f)
        return true
    }

    /** Arrow keys and the head unit's own volume keys. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_VOLUME_UP -> { step(true); true }
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_VOLUME_DOWN -> { step(false); true }
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> { car.launch(Car.EQ_UI); true }
        else -> super.onKeyDown(keyCode, event)
    }

    private fun step(up: Boolean) {
        if (up && shown >= car.maxVol || !up && shown <= 0) return
        anim?.cancel()
        car.volumeStep(up)
        shown += if (up) 1 else -1
        angle = angleFor(shown)
        onStep()
        holdUntil = SystemClock.uptimeMillis() + 1500
        invalidate()
    }
}
