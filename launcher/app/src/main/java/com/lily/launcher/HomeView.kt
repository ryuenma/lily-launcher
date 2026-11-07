package com.lily.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import java.util.Calendar
import java.util.Locale

/**
 * The home screen, drawn entirely with Canvas paths in the canonical NGE terminal layout:
 * status rail → banner → main (telemetry · kanji band · stage · MAGI) → ticker → deck
 * (hex keypad · knob · speed plate). Nothing comes from a baked image: every shape is a [Path]
 * and every colour is read from [Ui.zoneColor], so a speed-zone change is a paint swap plus one
 * invalidate.
 */
@SuppressLint("ViewConstructor")
class HomeView(ctx: Context, private val ui: Ui, private val car: Car,
               private val onMore: () -> Unit) : View(ctx) {

    private val type = NgeType(ui)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val path = Path()
    private val path2 = Path()
    private val honey = Path()
    private val tmp = RectF()

    /** Resolved accent for latched keys, the knob, the progress fill and the MAGI nodes. */
    var acc = 0; private set
    private var night = false

    init {
        isClickable = true
        buildHoneycomb()
    }

    // ═════════════ palette (zone + day/night) ═════════════

    /**
     * Day/night modulates brightness in the normal zone only; caution/emergency speed palettes win
     * (design §8.3 precedence), which is what [Ui.zoneColor] already encodes for text and accent.
     */
    private fun dim(c: Int) = if (night && car.speedZone == SpeedZone.NORMAL) mix(c, Color.BLACK, .78f) else c

    private fun bg() = dim(ui.zoneColor("bg"))
    private fun ink() = dim(ui.zoneColor("text"))
    private fun rule() = withAlpha(ink(), .28f)
    private fun zoneAccent() = ui.zoneColor("accent")
    private fun onFill() = if (car.speedZone == SpeedZone.EMERGENCY) ui.zoneColor("ok") else Color.BLACK

    /**
     * The accent pref is a user choice, so it can override the palette — but only in the normal
     * zone and only when it differs from the stock value; otherwise [Ui.zoneColor] rules.
     */
    private fun accentColor(): Int {
        val fallback = zoneAccent()
        if (car.speedZone != SpeedZone.NORMAL) return fallback
        val pref = runCatching { parseCss(car.str("accent")) }.getOrDefault(fallback)
        return dim(if (pref == parseCss(STOCK_ACCENT)) fallback else pref)
    }

    fun restyle() {
        night = car.night
        acc = accentColor()
    }

    // ═════════════ drawing ═════════════

    override fun onDraw(c: Canvas) {
        fill.style = Paint.Style.FILL
        fill.color = bg()
        c.drawRect(0f, 0f, Nge.W, Nge.H, fill)
        drawHoneycomb(c)
        drawStatusRail(c)
        drawBanner(c)
        drawTelemetry(c)
        drawJpBand(c)
        drawStage(c)
        drawMagi(c)
        drawTicker(c)
        drawDeck(c)
        drawWarningOverlay(c)
    }

    // ── fullscreen warning overlay (pulse, no layout shift) ──
    private fun drawWarningOverlay(c: Canvas) {
        val zone = car.speedZone
        if (zone == SpeedZone.NORMAL) return
        val color = if (zone == SpeedZone.EMERGENCY) ui.zoneColor("alert") else ui.zoneColor("caution")
        val pulse = (Math.sin(SystemClock.uptimeMillis() / 300.0) * 0.3 + 0.7).toFloat()
        fill.style = Paint.Style.FILL
        fill.color = color
        fill.alpha = (pulse * 80).toInt()
        c.drawRect(0f, 0f, Nge.W.toFloat(), Nge.H.toFloat(), fill)
        fill.alpha = 255
        postInvalidateDelayed(50)
    }

    /** A.T. Field: one cached path holding every hexagon, drawn as a single stroke pass. */
    private fun buildHoneycomb() {
        val hh = 14f
        val hw = hh * .866f
        val dx = hw * 2f
        val dy = hh * 1.5f
        var row = 0
        var y = -dy
        while (y < Nge.H + dy) {
            val off = if (row % 2 == 0) 0f else dx / 2f
            var x = off - dx
            while (x < Nge.W + dx) {
                Nge.hexPath(path2, x, y, hw, hh)
                honey.addPath(path2)
                x += dx
            }
            y += dy
            row++
        }
    }

    private fun drawHoneycomb(c: Canvas) {
        line.strokeWidth = .5f
        line.color = withAlpha(ui.zoneColor("text"), if (night) .035f else .075f)
        c.drawPath(honey, line)
    }

    // ── status rail: system state · clock · LAMP · day/night chip ──
    private fun drawStatusRail(c: Canvas) {
        val r = Nge.rail
        fill.color = Color.BLACK
        c.drawRect(r, fill)
        line.color = rule(); line.strokeWidth = 1f
        c.drawLine(0f, r.bottom - .5f, Nge.W, r.bottom - .5f, line)

        // heartbeat dot: 1.26 s on, .54 s dim, stepped like the mockup's blink
        val lit = (SystemClock.uptimeMillis() % 1800L) < 1260L
        fill.color = if (lit) ink() else withAlpha(ink(), .15f)
        c.drawRect(12f, 13f, 18f, 19f, fill)

        val p = type.paint(9f, track = 2f)
        c.text(p, "SYSTEM ONLINE", 24f, 20f, ink())
        val jp = type.paint(9f, track = 1f)
        c.text(jp, "稼働中", 24f + p.measureText("SYSTEM ONLINE") + 8f, 20f, withAlpha(ink(), .5f))

        // speed-zone chip, so the current palette is named in the rail as well as in the seals
        val chipText = type.paint(8f, track = 1.2f, align = Paint.Align.CENTER)
        val zone = car.speedZone
        val zoneLabel = "ZONE · " + when (zone) {
            SpeedZone.CAUTION -> "CAUT"
            SpeedZone.EMERGENCY -> "EMER"
            else -> "NORM"
        }
        chip(c, 166f, 7f, 86f, 18f, zoneLabel, chipText,
            if (zone == SpeedZone.NORMAL) withAlpha(ink(), .3f) else zoneAccent(),
            if (zone == SpeedZone.NORMAL) Color.TRANSPARENT else zoneAccent(),
            if (zone == SpeedZone.NORMAL) withAlpha(ink(), .65f) else Color.BLACK)

        val mid = type.paint(9f, track = 2.5f, align = Paint.Align.CENTER)
        c.text(mid, "第01種監視体制 // MAGI LINK ESTABLISHED", Nge.W / 2f, 20f, withAlpha(ink(), .55f))

        // right: clock · LAMP chip · AUTO/DAY chip
        val themeW = 78f
        val themeL = 1012f - themeW
        val themeLabel = (if (car.dayNightMode == "auto") "AUTO" else "MAN") + " " + if (night) "NIGHT" else "DAY"
        chip(c, themeL, 7f, themeW, 18f, themeLabel, chipText,
            withAlpha(ink(), .6f), if (night) ink() else Color.TRANSPARENT, if (night) Color.BLACK else ink())

        val lampOn = car.night
        val lampW = 80f
        val lampL = themeL - 10f - lampW
        chip(c, lampL, 7f, lampW, 18f, if (lampOn) "LAMP · ON" else "LAMP · OFF", chipText,
            if (lampOn) ui.zoneColor("caution") else withAlpha(ink(), .35f),
            if (lampOn) ui.zoneColor("caution") else Color.TRANSPARENT,
            if (lampOn) Color.BLACK else withAlpha(ink(), .65f))

        val now = Calendar.getInstance()
        val clock = type.paint(14f, mono = true, track = 1f, align = Paint.Align.RIGHT)
        c.text(clock, String.format(Locale.US, "%02d:%02d:%02d",
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND)),
            lampL - 10f, 21f, ink())
        val tz = type.paint(8f, track = 1f, align = Paint.Align.RIGHT)
        c.text(tz, "LOC", lampL - 10f - clock.measureText("00:00:00") - 4f, 20f, withAlpha(ink(), .45f))
    }

    private fun chip(c: Canvas, x: Float, top: Float, w: Float, h: Float, label: String, p: Paint,
                     border: Int, bgColor: Int, fg: Int) {
        if (bgColor != Color.TRANSPARENT) {
            fill.color = bgColor
            c.drawRect(x, top, x + w, top + h, fill)
        } else {
            line.color = border; line.strokeWidth = 1f
            c.drawRect(x + .5f, top + .5f, x + w - .5f, top + h - .5f, line)
        }
        c.text(p, label, x + w / 2f, top + h / 2f + 3.2f, fg)
    }

    // ── banner: hazard bar · JP/EN header · octagonal zone seal ──
    private fun drawBanner(c: Canvas) {
        val b = Nge.banner
        fill.color = zoneAccent()
        c.drawRect(b, fill)
        fill.color = Color.BLACK
        c.drawRect(b.left, b.bottom - 2f, b.right, b.bottom, fill)

        tmp.set(b.left + 12f, b.centerY() - 7f, b.left + 64f, b.centerY() + 7f)
        bars(c, tmp, onFill(), 3f, 7f)

        val jp = type.paint(13f, track = 3f)
        c.text(jp, zoneJp(), b.left + 76f, b.centerY() + 5f, onFill())

        val title = type.paint(15f, track = 5f, align = Paint.Align.CENTER)
        c.text(title, zoneTitle(), Nge.W / 2f, b.centerY() + 6f, onFill())

        drawSeal(c, 912f, b.centerY(), 15f, zoneJp(), zoneEn(), onFill(), 10f, 5f)

        val rev = type.paint(7.5f, mono = true, track = .8f, align = Paint.Align.RIGHT)
        c.text(rev, "REV 4.0", 1012f, b.top + 17f, withAlpha(onFill(), .75f))
        c.text(rev, "7IN · 1024×600", 1012f, b.top + 30f, withAlpha(onFill(), .75f))
    }

    /** Vertical hazard bars (the banner's `.bar` in the mockup). */
    private fun bars(c: Canvas, r: RectF, color: Int, w: Float, period: Float) {
        fill.color = color
        var x = r.left
        while (x < r.right) {
            c.drawRect(x, r.top, (x + w).coerceAtMost(r.right), r.bottom, fill)
            x += period
        }
    }

    // ── telemetry rail: the metadata side-channels ──
    private fun drawTelemetry(c: Canvas) {
        val t = Nge.telemetry
        val l = t.left + 10f
        val r = t.right - 10f

        val hd = type.paint(8f, track = 2.4f)
        c.text(hd, "TELEMETRY", l, t.top + 20f, withAlpha(ink(), .55f))
        val hj = type.paint(7f, track = 1f, align = Paint.Align.RIGHT)
        c.text(hj, "記録", r, t.top + 20f, withAlpha(ink(), .9f))
        line.color = rule(); line.strokeWidth = 1f
        c.drawLine(l, t.top + 26f, r, t.top + 26f, line)

        val kp = type.paint(7f, track = 1.8f)
        val vp = type.paint(11f, mono = true, track = .4f)
        var y = t.top + 32f
        for ((k, value) in telemetryRows()) {
            fill.color = withAlpha(ink(), .45f)
            c.drawRect(l, y + 4f, l + 2f, y + 28f, fill)
            c.text(kp, k, l + 6f, y + 11f, withAlpha(ink(), .45f))
            val flash = k == "VOLUME" && SystemClock.uptimeMillis() < volFlashUntil
            c.text(vp, vp.fit(value, r - l - 10f), l + 6f, y + 27f, if (flash) acc else ink())
            y += 34f
        }

        val bottom = t.bottom - 46f
        val heights = intArrayOf(20, 34, 52, 40, 68, 82, 64, 92, 76, 100, 70, 88)
        val bw = 4f
        val gap = (r - l - 12 * bw) / 11f
        for (i in 0 until 12) {
            val x = l + i * (bw + gap)
            val h = 32f * heights[i] / 100f
            fill.color = if (i % 4 == 3) withAlpha(ink(), .25f) else withAlpha(ink(), .8f)
            c.drawRect(x, bottom - h, x + bw, bottom, fill)
        }
        val sn = type.paint(7f, track = 1.6f)
        c.text(sn, "SIGNAL / 24 CH", l, bottom + 14f, withAlpha(ink(), .4f))
    }

    private fun telemetryRows(): List<Pair<String, String>> = listOf(
        "SPEED" to String.format(Locale.US, "%03d KM/H", car.speedKmh),
        "VOLUME" to "${car.vol} / ${car.maxVol}",
        "SOURCE" to car.source.name,
        "THEME" to (if (car.dayNightMode == "auto") "AUTO" else "MAN") + "·" + if (night) "NIGHT" else "DAY",
        "UPLINK" to (car.btName?.let { it.take(9).uppercase(Locale.US) } ?: "—") + " / " + linkName(),
        "CODE" to "LILY-01",
    )

    private fun linkName(): String = when {
        car.aa == Aa.CONNECTED -> "AA"
        car.source == Src.RADIO -> "FM"
        car.source == Src.USB -> "USB"
        car.btName != null -> "BT"
        else -> "—"
    }

    // ── vertical kanji band ──
    private fun drawJpBand(c: Canvas) {
        val b = Nge.jpBand
        fill.color = Color.BLACK
        c.drawRect(b, fill)
        line.color = rule(); line.strokeWidth = 1f
        c.drawLine(b.right - .5f, b.top, b.right - .5f, b.bottom, line)

        val chars = "監視記録 第零一号".replace(" ", "")
        val p = type.paint(9f, track = 0f, align = Paint.Align.CENTER)
        val pitch = 26f
        val start = b.centerY() - (chars.length - 1) * pitch / 2f
        chars.forEachIndexed { i, ch ->
            c.text(p, ch.toString(), b.centerX(), start + i * pitch, withAlpha(ui.zoneColor("alert"), .85f))
        }
    }

    // ── stage: source, track, progress, data cells ──
    private fun drawStage(c: Canvas) {
        val s = Nge.stage
        val l = s.left + 14f
        val r = s.right - 14f
        val v = view3()

        val hb = s.top + 25f
        val lab = type.paint(9f, track = 2.6f)
        val label = stageLabel(v)
        c.text(lab, label, l, hb, ink())

        val cp = type.paint(7.5f, mono = true, track = 1.4f, align = Paint.Align.CENTER)
        val c2 = stageChip2(v)
        val c1 = stageChip(v)
        val w2 = cp.measureText(c2) + 12f
        val x2 = r - w2
        val w1 = cp.measureText(c1) + 12f
        val x1 = x2 - 6f - w1
        val chipBorder = withAlpha(ink(), .35f)
        chip(c, x1, s.top + 12f, w1, 16f, c1, cp, chipBorder, Color.TRANSPARENT, withAlpha(ink(), .9f))
        chip(c, x2, s.top + 12f, w2, 16f, c2, cp, chipBorder, Color.TRANSPARENT, withAlpha(ink(), .9f))
        line.color = rule(); line.strokeWidth = 1f
        c.drawLine(l + lab.measureText(label) + 8f, hb - 4f, x1 - 8f, hb - 4f, line)

        val bodyTop = s.top + 38f
        val info = info(v)
        drawAlbum(c, RectF(l, bodyTop + 36f, l + 118f, bodyTop + 164f), info.glyph, info.sub)

        val aa = car.aa == Aa.CONNECTED
        val trackX = l + 132f
        val trackW = (r - (if (aa) 204f else 0f)) - trackX
        drawTrack(c, info, trackX, bodyTop, trackW)
        if (aa) drawAaMap(c, RectF(r - 190f, bodyTop + 36f, r, bodyTop + 164f))

        drawProgress(c, v, l, r, s.top + 246f)
        drawCells(c, v, l, r, s.top + 284f)
    }

    private class Info(val kicker: String, val title: String, val artist: String, val album: String,
                       val glyph: String, val sub: String, val big: Boolean = false, val active: Boolean = false)

    enum class View3 { TAPE, RADIO, USB, EMPTY }

    fun view3(): View3 = when {
        car.source == Src.RADIO -> View3.RADIO
        car.source == Src.USB -> View3.USB
        car.btName == null && !car.hasTrack -> View3.EMPTY
        else -> View3.TAPE
    }

    private fun stageLabel(v: View3) = when (v) {
        View3.RADIO -> "NOW PLAYING // RADIO"
        View3.USB -> "NOW PLAYING // USB"
        View3.EMPTY -> "DISPLAY // STANDBY"
        View3.TAPE -> "NOW PLAYING // TAPE"
    }

    private fun stageChip(v: View3) = when (v) {
        View3.RADIO -> "SRC ${car.band}"
        View3.USB -> "SRC USB"
        View3.EMPTY -> "SRC —"
        View3.TAPE -> "SRC BT"
    }

    private fun stageChip2(v: View3) = when (v) {
        View3.RADIO -> "RDS ON"
        View3.USB -> "MP3 320"
        View3.EMPTY -> "NO LINK"
        View3.TAPE -> "AAC 320"
    }

    private fun info(v: View3): Info = when (v) {
        View3.RADIO -> Info("${car.band} BAND · ${if (car.muted) "MUTED" else "STEREO"}",
            car.freq.ifEmpty { "--.-" }, car.station.ifEmpty { "${car.band} RADIO" },
            "RDS · ${if (car.muted) "MUTED" else "NOW ON AIR"}", "波",
            "${car.band} · ${car.freq}", big = true)
        View3.USB -> Info("USB STORAGE · TRACK", car.usbTitle.ifEmpty { "NO MEDIA" },
            car.usbArtist.ifEmpty { "USB MUSIC" }, "MP3 · USB 01", "記", "USB · 01", active = car.usbPlaying)
        View3.EMPTY -> Info("NO ACTIVE SOURCE", "NO SIGNAL", "信号待ち",
            "CONNECT A PHONE OR START RADIO", "無", "NO SIG")
        View3.TAPE -> Info("${car.btName?.uppercase(Locale.US) ?: "BLUETOOTH"} · A2DP",
            car.title.ifEmpty { "BLUETOOTH AUDIO" }, car.artist.ifEmpty { "—" },
            "AAC 320 · 44.1 KHZ", "音", "BT · A2DP", active = car.playing)
    }

    private fun drawAlbum(c: Canvas, r: RectF, glyph: String, sub: String) {
        Nge.hexPath(path, r.centerX(), r.centerY(), 53f, 63f)
        line.strokeWidth = 1f
        line.color = withAlpha(ink(), .35f)
        c.drawPath(path, line)
        Nge.hexPath(path, r.centerX(), r.centerY(), 46f, 55f)
        line.strokeWidth = 1.5f
        line.color = ink()
        c.drawPath(path, line)
        c.drawLine(r.centerX(), r.centerY() - 55f, r.centerX(), r.centerY() - 40f, line)
        c.drawLine(r.centerX(), r.centerY() + 40f, r.centerX(), r.centerY() + 55f, line)
        val g = type.paint(42f, align = Paint.Align.CENTER)
        c.text(g, glyph, r.centerX(), r.centerY() + 2f, ink())
        val sb = type.paint(7f, mono = true, track = 1f, align = Paint.Align.CENTER)
        c.text(sb, sub, r.centerX(), r.centerY() + 24f, withAlpha(ink(), .6f))
    }

    private fun drawTrack(c: Canvas, i: Info, x: Float, top: Float, w: Float) {
        var y = top + 44f
        val k = type.paint(8f, track = 2.4f)
        c.text(k, k.fit(i.kicker, w), x, y, withAlpha(ink(), .5f))
        y += 34f
        val big = type.paint(if (i.big) 56f else 30f, mono = i.big, track = if (i.big) 0f else .5f)
        c.text(big, big.fit(i.title, w), x, y, ink())
        y += if (i.big) 26f else 24f
        val a = type.paint(15f, track = 1f)
        c.text(a, a.fit(i.artist, w), x, y, withAlpha(ink(), .82f))
        y += 18f
        val al = type.paint(8f, track = 2f)
        c.text(al, al.fit(i.album, w), x, y, withAlpha(ink(), .4f))
        drawSpectrum(c, x, y + 14f, i.active, w)
    }

    private fun drawSpectrum(c: Canvas, x: Float, top: Float, active: Boolean, w: Float) {
        val hs = intArrayOf(40, 72, 55, 90, 64, 100, 48, 82, 58, 94, 68, 44, 86, 60)
        val bw = 4f
        val gap = 3f
        val count = hs.size.coerceAtMost(((w + gap) / (bw + gap)).toInt())
        for (i in 0 until count) {
            val s = if (active) hs[i] / 100f else hs[i] / 100f * .14f
            val bh = 28f * s
            fill.color = if (active) withAlpha(ink(), .9f) else withAlpha(ink(), .35f)
            c.drawRect(x + i * (bw + gap), top + 28f - bh, x + i * (bw + gap) + bw, top + 28f, fill)
        }
        line.color = rule(); line.strokeWidth = 1f
        c.drawLine(x, top + 31f, x + count * (bw + gap) - gap, top + 31f, line)
    }

    private fun drawAaMap(c: Canvas, r: RectF) {
        fill.color = Color.BLACK
        c.drawRect(r, fill)
        line.color = withAlpha(ink(), .18f); line.strokeWidth = 1f
        for (i in 1..3) {
            val y = r.top + r.height() * i / 4f
            c.drawLine(r.left, y, r.right, y, line)
        }
        for (i in 1..4) {
            val x = r.left + r.width() * i / 5f
            c.drawLine(x, r.top, x, r.bottom, line)
        }
        line.color = acc; line.strokeWidth = 4f
        path.reset()
        path.moveTo(r.left + 18f, r.bottom - 16f)
        path.lineTo(r.left + 18f, r.top + 58f)
        path.lineTo(r.left + 76f, r.top + 58f)
        path.lineTo(r.left + 76f, r.top + 32f)
        path.lineTo(r.left + 140f, r.top + 32f)
        path.lineTo(r.left + 140f, r.top + 18f)
        c.drawPath(path, line)
        fill.color = ui.zoneColor("ok")
        c.drawCircle(r.left + 18f, r.bottom - 16f, 5f, fill)
        line.color = ui.zoneColor("ok"); line.strokeWidth = 2f
        c.drawCircle(r.left + 140f, r.top + 18f, 5f, line)
        val t = type.paint(7f, mono = true, track = .4f)
        c.text(t, "3.2 KM / 00:07", r.left + 8f, r.bottom - 6f, withAlpha(ui.zoneColor("ok"), .7f))
        line.color = rule(); line.strokeWidth = 1f
        c.drawRect(r, line)
    }

    private class Prog(val label: String, val pct: Float, val cur: String, val len: String)

    private fun fmt(sec: Int) = String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)

    private fun progress(v: View3): Prog = when {
        v == View3.RADIO -> Prog("SIGNAL", if (car.muted) 0f else .62f, if (car.muted) "MUTED" else "62%", "")
        (v == View3.TAPE || v == View3.USB) && car.posKnown && car.total > 0 ->
            Prog("TIME", (car.counter.toFloat() / car.total).coerceIn(0f, 1f), fmt(car.counter), fmt(car.total))
        else -> Prog("TIME", 0f, "--:--", "--:--")
    }

    private fun drawProgress(c: Canvas, v: View3, l: Float, r: Float, top: Float) {
        val p = progress(v)
        val pl = type.paint(7.5f, track = 2f)
        c.text(pl, p.label, l, top + 10f, withAlpha(ink(), .5f))
        val m = type.paint(9f, mono = true, track = .5f)
        c.text(m, p.cur, l + pl.measureText(p.label) + 8f, top + 10f, ink())
        val right = type.paint(9f, mono = true, track = .5f, align = Paint.Align.RIGHT)
        c.text(right, p.len, r, top + 10f, withAlpha(ink(), .45f))

        val by = top + 18f
        fill.color = Color.BLACK
        c.drawRect(l, by, r, by + 6f, fill)
        if (p.pct > 0f) {
            fill.color = acc
            c.drawRect(l + 1f, by + 1f, l + 1f + (r - l - 2f) * p.pct, by + 5f, fill)
        }
        line.color = withAlpha(Color.BLACK, .55f); line.strokeWidth = 1f
        var x = l + 20f
        while (x < r) {
            c.drawLine(x, by + 1f, x, by + 5f, line)
            x += 20f
        }
        line.color = withAlpha(ink(), .35f); line.strokeWidth = 1f
        c.drawRect(l, by, r, by + 6f, line)
    }

    private fun cells(v: View3): List<Pair<String, String>> = when (v) {
        View3.RADIO -> listOf("BAND" to car.band,
            "MODE" to if (car.muted) "MUTED" else "STEREO",
            "RDS" to if (car.station.isEmpty()) "WAIT" else "ACTIVE",
            "PRESET" to "P03")
        View3.USB -> listOf("MEDIA" to "USB 01", "FORMAT" to "MP3", "TRACKS" to "128", "STATE" to stateWord())
        View3.TAPE -> listOf("CODEC" to "AAC", "RATE" to "320K", "CHANNEL" to "2.0", "STATE" to stateWord())
        View3.EMPTY -> listOf("CODEC" to "—", "RATE" to "—", "CHANNEL" to "—", "STATE" to "IDLE")
    }

    private fun stateWord() = when {
        car.source == Src.RADIO -> if (car.muted) "MUTED" else "ON AIR"
        car.playing -> "PLAYING"
        car.source == Src.BT && car.btName == null -> "IDLE"
        else -> "PAUSED"
    }

    private fun drawCells(c: Canvas, v: View3, l: Float, r: Float, top: Float) {
        val rows = cells(v)
        val gap = 5f
        val cw = (r - l - gap * 3f) / 4f
        val kp = type.paint(6.5f, track = 1.6f)
        val vp = type.paint(10f, mono = true, track = .4f)
        rows.forEachIndexed { i, (k, value) ->
            val x = l + i * (cw + gap)
            line.color = rule(); line.strokeWidth = 1f
            c.drawRect(x, top, x + cw, top + 40f, line)
            c.text(kp, k, x + 6f, top + 13f, withAlpha(ink(), .45f))
            c.text(vp, vp.fit(value, cw - 12f), x + 6f, top + 31f, ink())
        }
    }

    // ── MAGI: three 196×44 trapezoids (Melchior=BT · Balthasar=RADIO · Casper=USB) ──
    private fun drawMagi(c: Canvas) {
        val m = Nge.magi
        val l = m.left + 10f
        val r = m.right - 10f
        val hd = type.paint(8f, track = 2.2f)
        c.text(hd, "MAGI SYSTEM", l, m.top + 20f, withAlpha(ink(), .55f))
        val hj = type.paint(7f, track = 1f, align = Paint.Align.RIGHT)
        c.text(hj, "三賢者", r, m.top + 20f, withAlpha(ink(), .9f))
        line.color = rule(); line.strokeWidth = 1f
        c.drawLine(l, m.top + 26f, r, m.top + 26f, line)

        val names = arrayOf("MELCHIOR", "BALTHASAR", "CASPER")
        val captions = arrayOf("メルキオール / 通信", "バルタザール / 音声", "カスパー / 車両")
        val nm = type.paint(11f, track = 1.8f)
        val st = type.paint(9.5f, mono = true, track = .6f, align = Paint.Align.RIGHT)
        val jp = type.paint(7f, track = 1.2f)
        for (i in 0 until 3) {
            val tr = Nge.magiTrap(i)
            val active = when (i) {
                0 -> car.source == Src.BT && car.btName != null
                1 -> car.source == Src.RADIO
                else -> car.source == Src.USB
            }
            val status = when (i) {
                0 -> if (car.btName != null) "LINK" else "NO LINK"
                1 -> if (active) "TUNER" else "STANDBY"
                else -> if (active) "USB" else "STANDBY"
            }
            val inverted = i == 1
            Nge.trapezoidPath(path, tr, tr.width() * .09f, inverted)
            fill.color = if (active) acc else withAlpha(ink(), .35f)
            c.drawPath(path, fill)
            tmp.set(tr); tmp.inset(2f, 2f)
            Nge.trapezoidPath(path2, tmp, tmp.width() * .09f, inverted)
            fill.color = if (active) acc else Color.BLACK
            c.drawPath(path2, fill)

            val fg = if (active) Color.BLACK else ink()
            val inset = if (inverted) 26f else 20f
            c.text(nm, names[i], tr.left + inset, tr.centerY() + 4f, fg)
            c.text(st, status, tr.right - inset, tr.centerY() + 4f, if (active) fg else withAlpha(ink(), .8f))
            c.text(jp, captions[i], tr.left + 4f, tr.bottom + 11f, withAlpha(ink(), .45f))
        }

        val rows = listOf(
            "LAYER 3 · CONNECTION" to linkName(),
            "LAYER 2 · DATA LINK" to when {
                car.source == Src.RADIO -> "FM"
                car.source == Src.USB -> "USB"
                car.btName != null -> "A2DP"
                else -> "IDLE"
            },
            "LAYER 1 · PHYSICAL" to "12V",
            "SPEED ZONE" to car.speedZone.name,
        )
        val lk = type.paint(7.5f, track = 1.4f)
        val lv = type.paint(8f, mono = true, track = .4f, align = Paint.Align.RIGHT)
        var y = m.bottom - 10f - (rows.size - 1) * 18f
        for ((k, value) in rows) {
            c.text(lk, k, l, y, withAlpha(ink(), .75f))
            c.text(lv, value, r, y, ink())
            line.color = withAlpha(ink(), .18f); line.strokeWidth = 1f
            c.drawLine(l, y + 4f, r, y + 4f, line)
            y += 18f
        }
    }

    // ── ticker base (DeckView paints the scrolling text when a zone is active) ──
    private fun drawTicker(c: Canvas) {
        if (car.speedZone == SpeedZone.NORMAL) {
            line.color = rule(); line.strokeWidth = 1f
            c.drawLine(0f, Nge.ticker.top, Nge.W, Nge.ticker.top, line)
        }
    }

    // ── deck: hex keypad + speed plate (the knob is its own view) ──
    private fun drawDeck(c: Canvas) {
        line.color = rule(); line.strokeWidth = 1f
        c.drawLine(0f, Nge.deck.top, Nge.W, Nge.deck.top, line)
        drawKeypad(c)
        drawSpeedPlate(c)
    }

    private enum class T { PREV, PLAY, NEXT, BT, RADIO, USB, PHONE, SWITCH, AUTO, MORE, LCD }

    private val pads = listOf(T.PREV, T.PLAY, T.NEXT, T.BT, T.RADIO, T.USB, T.PHONE, T.SWITCH, T.AUTO, T.MORE)

    private fun drawKeypad(c: Canvas) {
        val labels = arrayOf("PREV", "PLAY", "NEXT", "BT", "RADIO", "USB", "PHONE", "SWITCH", "AUTO", "MORE")
        val jp = arrayOf("前曲", "再生", "次曲", "無線", "受信", "記憶", "通話", "切替", "自動", "拡張")
        val lab = type.paint(8.5f, track = 1.2f, align = Paint.Align.CENTER)
        val jpP = type.paint(6.4f, track = .6f, align = Paint.Align.CENTER)
        val okCol = ui.zoneColor("ok")

        pads.forEachIndexed { i, t ->
            val (cx, cyBase) = Nge.hexCenters[i]
            val down = pressed == t
            val y = if (down) cyBase + 2f else cyBase
            val latched = when (t) {
                T.BT -> car.source == Src.BT
                T.RADIO -> car.source == Src.RADIO
                T.USB -> car.source == Src.USB
                else -> false
            }
            val lit = t == T.PLAY && car.playing

            Nge.hexPath(path, cx, y, Nge.HEX_W / 2f, Nge.HEX_H / 2f)
            fill.color = when {
                latched -> acc
                lit -> okCol
                down -> withAlpha(ink(), .12f)
                else -> Color.BLACK
            }
            c.drawPath(path, fill)
            line.strokeWidth = if (latched || lit) 1.5f else 1.2f
            line.color = when {
                latched -> acc
                lit -> okCol
                else -> withAlpha(ink(), .45f)
            }
            c.drawPath(path, line)

            val fg = if (latched || lit) Color.BLACK else ink()
            drawIcon(c, t, cx, y - 10f, fg, lit)
            c.text(lab, if (t == T.PLAY && car.playing) "PAUSE" else labels[i], cx, y + 14f, fg)
            c.text(jpP, jp[i], cx, y + 23f, if (latched || lit) withAlpha(Color.BLACK, .6f) else withAlpha(ink(), .5f))
        }
    }

    private fun drawIcon(c: Canvas, t: T, cx: Float, cy: Float, col: Int, lit: Boolean) {
        line.strokeWidth = 1.6f
        line.color = col
        fill.color = col
        when (t) {
            T.PREV, T.NEXT -> {
                val d = if (t == T.PREV) 1f else -1f
                path.reset()
                path.moveTo(cx + 2f * d, cy - 7f); path.lineTo(cx - 5f * d, cy); path.lineTo(cx + 2f * d, cy + 7f); path.close()
                path2.reset()
                path2.moveTo(cx + 9f * d, cy - 7f); path2.lineTo(cx + 2f * d, cy); path2.lineTo(cx + 9f * d, cy + 7f); path2.close()
                c.drawPath(path, fill)
                c.drawPath(path2, fill)
            }
            T.PLAY -> if (lit) {
                c.drawRect(cx - 6f, cy - 8f, cx - 2f, cy + 8f, fill)
                c.drawRect(cx + 2f, cy - 8f, cx + 6f, cy + 8f, fill)
            } else {
                path.reset()
                path.moveTo(cx - 5f, cy - 8f); path.lineTo(cx + 8f, cy); path.lineTo(cx - 5f, cy + 8f); path.close()
                c.drawPath(path, fill)
            }
            T.BT -> {
                path.reset()
                path.moveTo(cx - 4f, cy - 7f); path.lineTo(cx + 4f, cy); path.lineTo(cx - 4f, cy + 7f)
                path.moveTo(cx - 4f, cy - 7f); path.lineTo(cx + 2f, cy - 3f)
                path.moveTo(cx - 4f, cy + 7f); path.lineTo(cx + 2f, cy + 3f)
                path.moveTo(cx - 8f, cy); path.lineTo(cx + 4f, cy)
                c.drawPath(path, line)
            }
            T.RADIO -> {
                tmp.set(cx - 9f, cy - 6f, cx + 9f, cy + 12f)
                c.drawArc(tmp, 200f, 140f, false, line)
                tmp.set(cx - 4f, cy - 1f, cx + 4f, cy + 7f)
                c.drawArc(tmp, 200f, 140f, false, line)
                c.drawCircle(cx, cy + 5f, 1.6f, fill)
            }
            T.USB -> {
                path.reset()
                path.moveTo(cx, cy + 8f); path.lineTo(cx, cy - 8f)
                path.moveTo(cx, cy - 8f); path.lineTo(cx - 3f, cy - 4f)
                path.moveTo(cx, cy - 8f); path.lineTo(cx + 3f, cy - 4f)
                path.moveTo(cx, cy - 1f); path.lineTo(cx - 4f, cy - 4f)
                path.moveTo(cx, cy - 1f); path.lineTo(cx + 4f, cy - 4f)
                c.drawPath(path, line)
                c.drawCircle(cx, cy + 8f, 2f, fill)
            }
            T.PHONE -> {
                path.reset()
                path.moveTo(cx - 6f, cy - 6f)
                path.lineTo(cx - 3f, cy - 8f)
                path.lineTo(cx, cy - 3f)
                path.lineTo(cx - 2f, cy - 1f)
                path.quadTo(cx + 1f, cy + 3f, cx + 4f, cy + 5f)
                path.lineTo(cx + 6f, cy + 3f)
                path.lineTo(cx + 9f, cy + 6f)
                path.lineTo(cx + 7f, cy + 8f)
                path.quadTo(cx - 6f, cy + 8f, cx - 6f, cy - 6f)
                path.close()
                c.drawPath(path, line)
            }
            T.SWITCH -> {
                path.reset()
                path.moveTo(cx - 8f, cy - 4f); path.lineTo(cx + 7f, cy - 4f); path.lineTo(cx + 4f, cy - 7f)
                path.moveTo(cx + 8f, cy + 4f); path.lineTo(cx - 7f, cy + 4f); path.lineTo(cx - 4f, cy + 7f)
                c.drawPath(path, line)
            }
            T.AUTO -> {
                val p = type.paint(13f, align = Paint.Align.CENTER)
                c.text(p, "AA", cx, cy + 5f, col)
            }
            T.MORE -> {
                c.drawCircle(cx - 6f, cy, 2f, fill)
                c.drawCircle(cx, cy, 2f, fill)
                c.drawCircle(cx + 6f, cy, 2f, fill)
            }
            T.LCD -> Unit
        }
    }

    // ── speed plate: 116px odometer · octagonal zone seal · 24-segment ladder ──
    private fun drawSpeedPlate(c: Canvas) {
        val r = Nge.speed
        fill.color = Color.BLACK
        c.drawRect(r, fill)
        line.color = rule(); line.strokeWidth = 1f
        c.drawRect(r, line)

        val hd = type.paint(7.5f, track = 2.2f)
        c.text(hd, "GROUND SPEED", r.left + 10f, r.top + 16f, withAlpha(ink(), .55f))
        val hj = type.paint(7f, track = 1f)
        c.text(hj, "対地速度", r.left + 10f + hd.measureText("GROUND SPEED") + 8f, r.top + 16f, withAlpha(ink(), .85f))
        val fx = type.paint(7f, mono = true, track = .8f, align = Paint.Align.RIGHT)
        c.text(fx, "GPS · 3D FIX", r.right - 10f, r.top + 16f, withAlpha(ink(), .8f))

        val num = String.format(Locale.US, "%03d", car.speedKmh.coerceIn(0, 999))
        val np = type.paint(116f, mono = true, track = -4f)
        c.text(np, num, r.left + 12f, 548f, ink())
        val numW = np.measureText(num)

        val ub = type.paint(15f, track = 1.6f)
        c.text(ub, "km/h", r.left + 12f + numW + 8f, 520f, ink())
        val ul = type.paint(7f, mono = true, track = 1f)
        c.text(ul, "LIM " + String.format(Locale.US, "%03d", car.cautionThreshold()),
            r.left + 12f + numW + 8f, 534f, withAlpha(ink(), .45f))

        drawSeal(c, 948f, 520f, 50f, zoneJp(), zoneEn(), ink(), 26f, 6.5f)
        drawLadder(c, r.left + 12f, 552f, 880f - (r.left + 12f))
    }

    private fun drawLadder(c: Canvas, l: Float, top: Float, w: Float) {
        val segs = 24
        val gap = 2f
        val sw = (w - gap * (segs - 1)) / segs
        val lit = Math.round(car.speedKmh / 160f * segs)
        val hot = car.speedZone != SpeedZone.NORMAL
        for (i in 0 until segs) {
            val x = l + i * (sw + gap)
            fill.color = when {
                i < lit && hot -> ui.zoneColor("alert")
                i < lit -> ink()
                else -> withAlpha(ink(), .10f)
            }
            c.drawRect(x, top, x + sw, top + 11f, fill)
            line.color = withAlpha(ink(), .30f); line.strokeWidth = 1f
            c.drawLine(x, top, x + sw, top, line)
        }

        val marks = listOf(0 to "000",
            car.cautionThreshold() to String.format(Locale.US, "%03d", car.cautionThreshold()),
            car.emergencyThreshold() to String.format(Locale.US, "%03d", car.emergencyThreshold()),
            160 to "160")
        marks.forEachIndexed { i, m ->
            val x = l + (w * (m.first / 160f)).coerceIn(0f, w)
            val align = when (i) {
                0 -> Paint.Align.LEFT
                marks.lastIndex -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            val p = type.paint(7f, mono = true, track = .4f, align = align)
            c.text(p, m.second, x, top + 26f, withAlpha(ink(), .6f))
            fill.color = withAlpha(ink(), .8f)
            c.drawRect((x - .5f).coerceIn(l, l + w), top - 6f, (x + .5f).coerceIn(l, l + w), top - 1f, fill)
        }
    }

    private fun drawSeal(c: Canvas, cx: Float, cy: Float, radius: Float, jp: String, en: String,
                         color: Int, jpSize: Float, enSize: Float) {
        Nge.octagonPath(path, cx, cy, radius)
        line.strokeWidth = if (radius > 20f) 2f else 1.5f
        line.color = color
        c.drawPath(path, line)
        Nge.octagonPath(path2, cx, cy, radius * .86f)
        line.strokeWidth = .8f
        line.color = withAlpha(color, .5f)
        c.drawPath(path2, line)

        val j = type.paint(jpSize, align = Paint.Align.CENTER)
        c.text(j, jp, cx, cy + jpSize * (if (radius > 20f) .3f else .34f), color)
        val e = type.paint(enSize, track = .4f, align = Paint.Align.CENTER)
        c.text(e, en, cx, cy + radius * .62f, withAlpha(color, .8f))
    }

    // ── zone copy ──
    private fun zoneJp() = when (car.speedZone) {
        SpeedZone.CAUTION -> "注意"
        SpeedZone.EMERGENCY -> "危険"
        else -> "通常"
    }

    private fun zoneEn() = when (car.speedZone) {
        SpeedZone.CAUTION -> "CAUTION"
        SpeedZone.EMERGENCY -> "EMERGENCY"
        else -> "NORMAL"
    }

    private fun zoneTitle() = when (car.speedZone) {
        SpeedZone.CAUTION -> "CAUTION // SPEED THRESHOLD EXCEEDED"
        SpeedZone.EMERGENCY -> "EMERGENCY // 第一種戦闘配置"
        else -> "DIRECT LINK CONNECTION : LILY"
    }

    // ═════════════ touch ═════════════

    private var pressed: T? = null

    // volume readout flash (the knob itself is KnobView)
    private var volFlashUntil = 0L
    fun onVolume() {
        volFlashUntil = SystemClock.uptimeMillis() + 1500
        postInvalidateDelayed(1550)
        invalidate()
    }

    // one-second tick between real position reports (never runs on an invented clock)
    private val ticker = object : Runnable {
        override fun run() {
            if (car.playing && car.source == Src.BT && car.posKnown &&
                (car.total == 0 || car.counter < car.total)) {
                car.counter++
                invalidate()
            }
            postDelayed(this, 1000)
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); postDelayed(ticker, 1000) }
    override fun onDetachedFromWindow() { removeCallbacks(ticker); super.onDetachedFromWindow() }

    private fun hit(x: Float, y: Float): T? {
        for (i in pads.indices) if (Nge.inHex(i, x, y)) return pads[i]
        if (Nge.magiTrap(0).contains(x, y)) return T.BT
        if (Nge.magiTrap(1).contains(x, y)) return T.RADIO
        if (Nge.magiTrap(2).contains(x, y)) return T.USB
        if (Nge.stage.contains(x, y)) return T.LCD
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = hit(e.x, e.y) ?: return false
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val t = pressed
                pressed = null
                if (t != null && hit(e.x, e.y) == t) act(t)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressed = null; invalidate() }
        }
        return true
    }

    private fun act(t: T) = when (t) {
        T.PHONE, T.SWITCH -> car.launch(Car.PHONE_UI)
        T.AUTO -> car.launch(Car.ZLINK_UI)
        T.PREV -> car.transport('b')
        T.PLAY -> car.transport('p')
        T.NEXT -> car.transport('n')
        T.BT -> car.selectSource(Src.BT)          // switch + play, stay on Home
        T.RADIO -> car.selectSource(Src.RADIO)
        T.USB -> car.selectSource(Src.USB)
        T.MORE -> onMore()
        T.LCD -> car.launch(when (car.source) {
            Src.RADIO -> Car.RADIO_UI
            Src.USB -> Car.USB_MUSIC_UI
            Src.BT -> Car.BT_MUSIC_UI
        })
    }

    companion object { private const val STOCK_ACCENT = "#FF5A1F" }
}
