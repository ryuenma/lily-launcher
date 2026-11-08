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
 *
 * **7" legibility pass.** Type sizes, paddings and hit targets are sized for a 7" 1024×600 panel
 * at arm's length, not for a desktop browser at 100 % zoom: the rail carries 11 px labels, the
 * banner a 19 px title, the stage a 42 px track title over a 128×152 album hex, and the deck 80×92
 * keypad hexes around a 124 px odometer. The telemetry signal bars are gone — [SpectrumView] owns
 * the single animated visualiser under the track block.
 */
@SuppressLint("ViewConstructor")
class HomeView(ctx: Context, private val ui: Ui, private val car: Car,
               private val onMore: () -> Unit, private val onSetup: () -> Unit) : View(ctx) {

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
    }

    /** A.T. Field: one cached path holding every hexagon, drawn as a single stroke pass. */
    private fun buildHoneycomb() {
        val hh = 20f
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
        line.strokeWidth = .6f
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
        c.drawRect(14f, 16f, 22f, 24f, fill)

        val p = type.paint(11f, track = 2f, weight = 600)
        c.text(p, "SYSTEM ONLINE", 32f, 25f, ink())
        val jp = type.paint(11f, jp = true, track = 1f, weight = 600)
        c.text(jp, "稼働中", 32f + p.measureText("SYSTEM ONLINE") + 10f, 25f, withAlpha(ink(), .5f))

        // speed-zone chip, so the current palette is named in the rail as well as in the seals
        val chipText = type.paint(10f, track = 1.6f, align = Paint.Align.CENTER, weight = 600)
        val zone = car.speedZone
        val zoneLabel = "ZONE · " + when (zone) {
            SpeedZone.CAUTION -> "CAUT"
            SpeedZone.EMERGENCY -> "EMER"
            else -> "NORM"
        }
        chip(c, 170f, 8f, 110f, 24f, zoneLabel, chipText,
            if (zone == SpeedZone.NORMAL) withAlpha(ink(), .3f) else zoneAccent(),
            if (zone == SpeedZone.NORMAL) Color.TRANSPARENT else zoneAccent(),
            if (zone == SpeedZone.NORMAL) withAlpha(ink(), .65f) else Color.BLACK)

        val mid = type.paint(11f, jp = true, track = 2.5f, align = Paint.Align.CENTER, weight = 700)
        c.text(mid, "第01種監視体制 // " + if (car.btName != null) "MAGI LINK" else "MAGI OFFLINE", Nge.W / 2f, 26f, withAlpha(ink(), .55f))

        // right: clock · LAMP chip · AUTO/DAY chip
        val themeW = 104f
        val themeL = 1012f - themeW
        val themeLabel = (if (car.dayNightMode == "auto") "AUTO" else "MAN") + " " + if (night) "NIGHT" else "DAY"
        chip(c, themeL, 8f, themeW, 24f, themeLabel, chipText,
            withAlpha(ink(), .6f), if (night) ink() else Color.TRANSPARENT, if (night) Color.BLACK else ink())

        val lampOn = car.night
        val lampW = 96f
        val lampL = themeL - 10f - lampW
        chip(c, lampL, 8f, lampW, 24f, if (lampOn) "LAMP · ON" else "LAMP · OFF", chipText,
            if (lampOn) ui.zoneColor("caution") else withAlpha(ink(), .35f),
            if (lampOn) ui.zoneColor("caution") else Color.TRANSPARENT,
            if (lampOn) Color.BLACK else withAlpha(ink(), .65f))

        val now = Calendar.getInstance()
        val clock = type.paint(17f, mono = true, track = 1f, align = Paint.Align.RIGHT)
        c.text(clock, String.format(Locale.US, "%02d:%02d:%02d",
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND)),
            lampL - 12f, 26f, ink())
        val tz = type.paint(9f, mono = true, track = 1f, align = Paint.Align.RIGHT)
        c.text(tz, "LOC", lampL - 12f - clock.measureText("00:00:00") - 6f, 25f, withAlpha(ink(), .45f))
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
        c.text(p, label, x + w / 2f, top + h / 2f + 3.6f, fg)
    }

    // ── banner: hazard bar · JP/EN header · octagonal zone seal ──
    private fun drawBanner(c: Canvas) {
        val b = Nge.banner
        fill.color = zoneAccent()
        c.drawRect(b, fill)
        fill.color = Color.BLACK
        c.drawRect(b.left, b.bottom - 2f, b.right, b.bottom, fill)

        tmp.set(b.left + 16f, b.centerY() - 9f, b.left + 88f, b.centerY() + 9f)
        bars(c, tmp, onFill(), 4f, 9f)

        val jp = type.paint(16f, jp = true, track = 3f, weight = 700)
        c.text(jp, zoneJp(), b.left + 104f, b.centerY() + 6f, onFill())

        val title = type.paint(19f, title = true, track = if (car.speedZone == SpeedZone.EMERGENCY) 7f else 5f,
            align = Paint.Align.CENTER, weight = 700)
        c.text(title, zoneTitle(), Nge.W / 2f, b.centerY() + 7f, onFill())

        drawSeal(c, 866f, b.centerY(), 18f, zoneJp(), zoneEn(), onFill(), 12f, 7f, jpTrack = 2f)

        val rev = type.paint(9f, mono = true, track = 1f, align = Paint.Align.RIGHT)
        c.text(rev, "REV 4.0", 980f, b.top + 22f, withAlpha(onFill(), .75f))
        c.text(rev, "7IN · 1024×600", 980f, b.top + 40f, withAlpha(onFill(), .75f))
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

    // ── telemetry rail: the metadata side-channels (no visualiser — the stage owns that) ──
    private fun drawTelemetry(c: Canvas) {
        val t = Nge.telemetry
        val l = t.left + 12f
        val r = t.right - 12f

        val hd = type.paint(10f, track = 2.4f, weight = 600)
        c.text(hd, "TELEMETRY", l, t.top + 26f, withAlpha(ink(), .55f))
        val hj = type.paint(9f, jp = true, track = 1f, align = Paint.Align.RIGHT)
        c.text(hj, "記録", r, t.top + 26f, withAlpha(ink(), .9f))
        line.color = rule(); line.strokeWidth = 1f
        c.drawLine(l, t.top + 32f, r, t.top + 32f, line)

        val kp = type.paint(9.5f, track = 1.8f)
        val vp = type.paint(14f, mono = true, track = .2f)
        var y = t.top + 38f
        for ((k, value) in telemetryRows()) {
            fill.color = withAlpha(ink(), .45f)
            c.drawRect(l, y + 5f, l + 3f, y + 32f, fill)
            c.text(kp, k, l + 10f, y + 14f, withAlpha(ink(), .45f))
            val flash = k == "VOLUME" && SystemClock.uptimeMillis() < volFlashUntil
            c.text(vp, vp.fit(value, r - l - 14f), l + 10f, y + 32f, if (flash) acc else ink())
            y += 38f
        }

        // footer: the link state, now that the signal bars are gone
        val bottom = t.bottom - 22f
        line.color = rule(); line.strokeWidth = 1f
        c.drawLine(l, bottom - 12f, r, bottom - 12f, line)
        val fp = type.paint(10f, mono = true, track = 1.2f)
        c.text(fp, "BUS 12V · " + linkName() + " · OK", l, bottom, withAlpha(ink(), .55f))
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
        val p = type.paint(11f, jp = true, track = 0f, align = Paint.Align.CENTER, weight = 700)
        val pitch = 31f
        val start = b.centerY() - (chars.length - 1) * pitch / 2f
        chars.forEachIndexed { i, ch ->
            c.text(p, ch.toString(), b.centerX(), start + i * pitch, withAlpha(ui.zoneColor("alert"), .85f))
        }
    }

    // ── stage: source, track, progress, data cells ──
    private fun drawStage(c: Canvas) {
        val s = Nge.stage
        val l = s.left + 16f
        val r = s.right - 16f
        val v = view3()

        val hb = s.top + 27f
        val lab = type.paint(11f, track = 2.6f, weight = 600)
        val label = stageLabel(v)
        c.text(lab, label, l, hb, ink())

        val cp = type.paint(10f, mono = true, track = 1.4f, align = Paint.Align.CENTER)
        val c2 = stageChip2(v)
        val c1 = stageChip(v)
        val w2 = cp.measureText(c2) + 20f
        val x2 = r - w2
        val w1 = cp.measureText(c1) + 20f
        val x1 = x2 - 8f - w1
        val chipBorder = withAlpha(ink(), .35f)
        chip(c, x1, s.top + 12f, w1, 24f, c1, cp, chipBorder, Color.TRANSPARENT, withAlpha(ink(), .9f))
        chip(c, x2, s.top + 12f, w2, 24f, c2, cp, chipBorder, Color.TRANSPARENT, withAlpha(ink(), .9f))
        line.color = rule(); line.strokeWidth = 1f
        c.drawLine(l + lab.measureText(label) + 10f, hb - 5f, x1 - 10f, hb - 5f, line)

        val bodyTop = s.top + 40f
        val info = info(v)
        drawAlbum(c, RectF(l, bodyTop, l + 128f, bodyTop + 152f), info.glyph, info.sub)

        val trackX = l + 148f
        drawTrack(c, info, trackX, bodyTop, r - trackX)

        drawProgress(c, v, l, r, s.top + 214f)
        drawCells(c, v, l, r, s.top + 244f)
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

    private fun stageChip2(v: View3) = when {
        car.aa == Aa.CONNECTED && v == View3.TAPE -> "AA LINK"
        v == View3.RADIO -> "RDS ON"
        v == View3.USB -> "MP3 320"
        v == View3.EMPTY -> "NO LINK"
        else -> "AAC 320"
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
        Nge.hexPath(path, r.centerX(), r.centerY(), 64f, 76f)
        line.strokeWidth = 1.2f
        line.color = withAlpha(ink(), .35f)
        c.drawPath(path, line)
        Nge.hexPath(path, r.centerX(), r.centerY(), 55f, 65f)
        line.strokeWidth = 1.8f
        line.color = ink()
        c.drawPath(path, line)
        c.drawLine(r.centerX(), r.centerY() - 65f, r.centerX(), r.centerY() - 48f, line)
        c.drawLine(r.centerX(), r.centerY() + 48f, r.centerX(), r.centerY() + 65f, line)
        val g = type.paint(54f, jp = true, align = Paint.Align.CENTER, weight = 900)
        c.text(g, glyph, r.centerX(), r.centerY() + 4f, ink())
        val sb = type.paint(10f, mono = true, track = 1f, align = Paint.Align.CENTER)
        c.text(sb, sub, r.centerX(), r.centerY() + 34f, withAlpha(ink(), .6f))
    }

    private fun drawTrack(c: Canvas, i: Info, x: Float, top: Float, w: Float) {
        val k = type.paint(10.5f, track = 2.4f)
        c.text(k, k.fit(i.kicker, w), x, top + 16f, withAlpha(ink(), .5f))

        val tp = type.paint(if (i.big) 52f else 42f, mono = i.big, track = if (i.big) 0f else .5f,
            weight = if (i.big) 700 else 600)
        c.text(tp, tp.fit(i.title, w), x, top + if (i.big) 68f else 62f, ink())

        val a = type.paint(20f, jp = true, track = 1f, weight = 700)
        c.text(a, a.fit(i.artist, w), x, top + if (i.big) 98f else 92f, withAlpha(ink(), .82f))

        val al = type.paint(10f, track = 2f)
        c.text(al, al.fit(i.album, w), x, top + if (i.big) 114f else 112f, withAlpha(ink(), .4f))
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
        val pl = type.paint(10f, track = 2f)
        c.text(pl, p.label, l, top + 10f, withAlpha(ink(), .5f))
        val m = type.paint(13f, mono = true, track = .5f)
        c.text(m, p.cur, l + pl.measureText(p.label) + 10f, top + 10f, ink())
        val right = type.paint(13f, mono = true, track = .5f, align = Paint.Align.RIGHT)
        c.text(right, p.len, r, top + 10f, withAlpha(ink(), .45f))

        val by = top + 18f
        fill.color = Color.BLACK
        c.drawRect(l, by, r, by + 12f, fill)
        if (p.pct > 0f) {
            fill.color = acc
            c.drawRect(l + 2f, by + 2f, l + 2f + (r - l - 4f) * p.pct, by + 10f, fill)
        }
        line.color = withAlpha(Color.BLACK, .55f); line.strokeWidth = 1.5f
        var x = l + 24f
        while (x < r) {
            c.drawLine(x, by + 2f, x, by + 10f, line)
            x += 24f
        }
        line.color = withAlpha(ink(), .35f); line.strokeWidth = 1f
        c.drawRect(l, by, r, by + 12f, line)
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
        val gap = 8f
        val cw = (r - l - gap * 3f) / 4f
        val kp = type.paint(9f, track = 1.6f)
        val vp = type.paint(14f, mono = true, track = .2f)
        rows.forEachIndexed { i, (k, value) ->
            val x = l + i * (cw + gap)
            line.color = rule(); line.strokeWidth = 1f
            c.drawRect(x, top, x + cw, top + 48f, line)
            c.text(kp, k, x + 10f, top + 18f, withAlpha(ink(), .45f))
            c.text(vp, vp.fit(value, cw - 20f), x + 10f, top + 40f, ink())
        }
    }

    // ── MAGI: three 196×52 trapezoids (Melchior=BT · Balthasar=RADIO · Casper=USB) ──
    private fun drawMagi(c: Canvas) {
        val m = Nge.magi
        val l = m.left + 10f
        val r = m.right - 10f
        val hd = type.paint(10f, track = 2.2f, weight = 600)
        c.text(hd, "MAGI SYSTEM", l, m.top + 26f, withAlpha(ink(), .55f))
        val hj = type.paint(9f, jp = true, track = 1f, align = Paint.Align.RIGHT)
        c.text(hj, "三賢者", r, m.top + 26f, withAlpha(ink(), .9f))
        line.color = rule(); line.strokeWidth = 1f
        c.drawLine(l, m.top + 32f, r, m.top + 32f, line)

        val names = arrayOf("MELCHIOR", "BALTHASAR", "CASPER")
        val captions = arrayOf("メルキオール / 通信", "バルタザール / 音声", "カスパー / 車両")
        val nm = type.paint(13f, track = 1.6f, weight = 600)
        val st = type.paint(11f, mono = true, track = .6f, align = Paint.Align.RIGHT)
        val jp = type.paint(9f, jp = true, track = 1.2f)
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
            val inset = if (inverted) 30f else 22f
            c.text(nm, names[i], tr.left + inset, tr.centerY() + 5f, fg)
            c.text(st, status, tr.right - inset, tr.centerY() + 5f, if (active) fg else withAlpha(ink(), .8f))
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
        val lk = type.paint(9f, track = 1.4f)
        val lv = type.paint(10f, mono = true, track = .4f, align = Paint.Align.RIGHT)
        var y = m.bottom - 12f - (rows.size - 1) * 18f
        for ((k, value) in rows) {
            c.text(lk, k, l, y, withAlpha(ink(), .75f))
            c.text(lv, value, r, y, ink())
            line.color = withAlpha(ink(), .18f); line.strokeWidth = 1f
            c.drawLine(l, y + 5f, r, y + 5f, line)
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

    private enum class T { PREV, PLAY, NEXT, BT, RADIO, USB, PHONE, SWITCH, AUTO, SETUP, MORE, LCD }

    private val pads = listOf(T.PREV, T.PLAY, T.NEXT, T.BT, T.RADIO, T.USB,
                              T.PHONE, T.SWITCH, T.AUTO, T.SETUP, T.MORE)

    /**
     * Positions come from [Nge.hexCenters] (six over five, half-pitch stagger). Labels sit at
     * +14/+27 from the centre, the icon at -15, so the 80×92 pads breathe the same way the
     * mockup's 67×78 ones did.
     */
    private fun drawKeypad(c: Canvas) {
        val labels = arrayOf("PREV", "PLAY", "NEXT", "BT", "RADIO", "USB",
                             "PHONE", "SWITCH", "AUTO", "SETUP", "MORE")
        val jp = arrayOf("前曲", "再生", "次曲", "無線", "受信", "記憶",
                         "通話", "切替", "自動", "設定", "拡張")
        val lab = type.paint(10f, track = 1.3f, align = Paint.Align.CENTER, weight = 600)
        val jpP = type.paint(8f, jp = true, track = .7f, align = Paint.Align.CENTER, weight = 700)
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
            line.strokeWidth = 2f
            line.color = when {
                latched -> acc
                lit -> okCol
                else -> withAlpha(ink(), .45f)
            }
            c.drawPath(path, line)

            val fg = if (latched || lit) Color.BLACK else ink()
            drawIcon(c, t, cx, y - 15f, fg, lit)
            c.text(lab, if (t == T.PLAY && car.playing) "PAUSE" else labels[i], cx, y + 14f, fg)
            c.text(jpP, jp[i], cx, y + 27f, if (latched || lit) withAlpha(Color.BLACK, .6f) else withAlpha(ink(), .5f))
        }
    }

    /**
     * Icons are the mockup's `#keypad .hex .ico` path data verbatim, drawn in viewBox units on a
     * canvas scaled by [Nge.ICON_SCALE]: the numbers below read straight across from the HTML, and the
     * CTM scales the 1.5 px strokes and the 13 px `AA` glyph with the pad. Stroke caps are square per
     * `.hex .ico path{stroke-linecap:square}`; solid shapes keep the fill paint.
     */
    private fun drawIcon(c: Canvas, t: T, cx: Float, cy: Float, col: Int, lit: Boolean) {
        val s = Nge.ICON_SCALE
        c.save()
        c.translate(cx, cy)
        c.scale(s, s)
        line.strokeWidth = 1.5f
        line.strokeCap = Paint.Cap.SQUARE
        line.color = col
        fill.color = col
        when (t) {
            T.PREV -> {
                path.reset()
                path.moveTo(-2f, -7f); path.lineTo(-10f, 0f); path.lineTo(-2f, 7f); path.close()
                path.moveTo(8f, -7f); path.lineTo(0f, 0f); path.lineTo(8f, 7f); path.close()
                c.drawPath(path, fill)
            }
            T.PLAY -> if (lit) {                                    // .playing → .pause-ico
                c.drawRect(-6f, -8f, -2f, 8f, fill)
                c.drawRect(2f, -8f, 6f, 8f, fill)
            } else {
                path.reset()
                path.moveTo(-5f, -8f); path.lineTo(9f, 0f); path.lineTo(-5f, 8f); path.close()
                c.drawPath(path, fill)
            }
            T.NEXT -> {
                path.reset()
                path.moveTo(-9f, -7f); path.lineTo(-1f, 0f); path.lineTo(-9f, 7f); path.close()
                path.moveTo(1f, -7f); path.lineTo(9f, 0f); path.lineTo(1f, 7f); path.close()
                c.drawPath(path, fill)
            }
            T.BT -> {
                path.reset()
                path.moveTo(-4f, -7f); path.lineTo(4f, 0f); path.lineTo(-4f, 7f)
                path.moveTo(-4f, -7f); path.lineTo(2f, -3f)
                path.moveTo(-4f, 7f); path.lineTo(2f, 3f)
                path.moveTo(-8f, 0f); path.lineTo(4f, 0f)
                c.drawPath(path, line)
            }
            T.RADIO -> {
                // A11,11 (−8,4)→(8,4) and A6,6 (−4,0)→(4,0): centre below the chord, 223.3°/93.3° and 228.2°/83.6°
                tmp.set(-11f, .55f, 11f, 22.55f)
                c.drawArc(tmp, 223.34f, 93.32f, false, line)
                tmp.set(-6f, -1.53f, 6f, 10.47f)
                c.drawArc(tmp, 228.19f, 83.62f, false, line)
                c.drawCircle(0f, 5f, 1.6f, fill)
            }
            T.USB -> {
                path.reset()
                path.moveTo(0f, 8f); path.lineTo(0f, -8f)
                path.moveTo(0f, -8f); path.lineTo(-3f, -4f)
                path.moveTo(0f, -8f); path.lineTo(3f, -4f)
                path.moveTo(0f, -1f); path.lineTo(-4f, -4f)
                path.moveTo(0f, -1f); path.lineTo(4f, -4f)
                c.drawPath(path, line)
                c.drawCircle(0f, 8f, 2f, fill)
            }
            T.PHONE -> {
                path.reset()
                path.moveTo(-6f, -6f); path.lineTo(-3f, -8f); path.lineTo(0f, -3f); path.lineTo(-2f, -1f)
                tmp.set(-3.135f, -16.865f, 18.865f, 5.135f)          // A11,11 → centre (7.865,−5.865)
                path.arcTo(tmp, 153.75f, -37.5f, false)              // … 37.5° counter-clockwise to (3,4)
                path.lineTo(5f, 2f); path.lineTo(9f, 5f); path.lineTo(7f, 8f)
                path.cubicTo(1f, 9f, -7f, 1f, -6f, -6f)              // C1,9 −7,1 −6,−6
                path.close()
                c.drawPath(path, line)
            }
            T.SWITCH -> {
                path.reset()
                path.moveTo(-8f, -4f); path.lineTo(7f, -4f); path.lineTo(4f, -7f)
                path.moveTo(8f, 4f); path.lineTo(-7f, 4f); path.lineTo(-4f, 7f)
                c.drawPath(path, line)
            }
            T.AUTO -> {
                val p = type.paint(13f, align = Paint.Align.CENTER, weight = 700)
                c.text(p, "AA", 0f, 5f, col)
            }
            T.SETUP -> {
                path.reset()
                path.moveTo(-8f, -5f); path.lineTo(-2f, -5f)
                path.moveTo(2f, -5f); path.lineTo(8f, -5f)
                path.moveTo(-8f, 1f); path.lineTo(2f, 1f)
                path.moveTo(5f, 1f); path.lineTo(8f, 1f)
                path.moveTo(-8f, 6f); path.lineTo(-3f, 6f)
                path.moveTo(0f, 6f); path.lineTo(8f, 6f)
                c.drawPath(path, line)
                c.drawCircle(0f, -5f, 1.8f, fill)
                c.drawCircle(3.5f, 1f, 1.8f, fill)
                c.drawCircle(-1.5f, 6f, 1.8f, fill)
            }
            T.MORE -> {
                c.drawCircle(-6f, 0f, 2f, fill)
                c.drawCircle(0f, 0f, 2f, fill)
                c.drawCircle(6f, 0f, 2f, fill)
            }
            T.LCD -> Unit
        }
        line.strokeCap = Paint.Cap.BUTT
        c.restore()
    }

    // ── speed plate: 124 px odometer · octagonal zone seal · 24-segment ladder ──
    private fun drawSpeedPlate(c: Canvas) {
        val r = Nge.speed
        fill.color = Color.BLACK
        c.drawRect(r, fill)
        line.color = rule(); line.strokeWidth = 1f
        c.drawRect(r, line)

        val hd = type.paint(10f, track = 2.2f, weight = 600)
        c.text(hd, "GROUND SPEED", r.left + 12f, r.top + 22f, withAlpha(ink(), .55f))
        val hj = type.paint(9f, jp = true, track = 1f)
        c.text(hj, "対地速度", r.left + 12f + hd.measureText("GROUND SPEED") + 10f, r.top + 22f, withAlpha(ink(), .85f))
        val fx = type.paint(9f, mono = true, track = .8f, align = Paint.Align.RIGHT)
        c.text(fx, "GPS · 3D FIX", r.right - 130f, r.top + 22f, withAlpha(ink(), .8f))

        val num = String.format(Locale.US, "%03d", car.speedKmh.coerceIn(0, 999))
        val np = type.paint(124f, mono = true, track = -4f)
        c.text(np, num, r.left + 12f, r.top + 118f, ink())
        val numW = np.measureText(num)

        val ub = type.paint(20f, track = 1.6f, weight = 600)
        c.text(ub, "KM/H", r.left + 12f + numW + 12f, r.top + 78f, ink())
        val ul = type.paint(10f, mono = true, track = 1f)
        c.text(ul, "LIM " + String.format(Locale.US, "%03d", car.cautionThreshold()),
            r.left + 12f + numW + 12f, r.top + 100f, withAlpha(ink(), .45f))

        drawSeal(c, r.right - 64f, r.top + 62f, 44f, zoneJp(), zoneEn(), ink(), 24f, 8f, jpTrack = 1f)

        drawLadder(c, r.left + 12f, r.top + 136f, r.width() - 24f)
    }

    private fun drawLadder(c: Canvas, l: Float, top: Float, w: Float) {
        val segs = 24
        val gap = 3f
        val sw = (w - gap * (segs - 1)) / segs
        val lit = Math.round(car.speedKmh / 160f * segs)
        val hot = car.speedZone != SpeedZone.NORMAL
        for (i in 0 until segs) {
            val x = l + i * (sw + gap)
            fill.color = when {
                i < lit && hot -> ui.zoneColor("alert")
                i < lit -> ink()
                else -> withAlpha(ink(), .16f)
            }
            c.drawRect(x, top, x + sw, top + 14f, fill)
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
            val p = type.paint(9f, mono = true, track = .4f, align = align)
            c.text(p, m.second, x, top + 28f, withAlpha(ink(), .6f))
            fill.color = withAlpha(ink(), .8f)
            c.drawRect((x - .5f).coerceIn(l, l + w), top - 6f, (x + .5f).coerceIn(l, l + w), top - 1f, fill)
        }
    }

    private fun drawSeal(c: Canvas, cx: Float, cy: Float, radius: Float, jp: String, en: String,
                         color: Int, jpSize: Float, enSize: Float, jpTrack: Float = 0f) {
        Nge.octagonPath(path, cx, cy, radius)
        line.strokeWidth = if (radius > 26f) 2f else 1.5f
        line.color = color
        c.drawPath(path, line)
        Nge.octagonPath(path2, cx, cy, radius * .86f)
        line.strokeWidth = .8f
        line.color = withAlpha(color, .5f)
        c.drawPath(path2, line)

        val j = type.paint(jpSize, jp = true, track = jpTrack, align = Paint.Align.CENTER, weight = 900)
        c.text(j, jp, cx, cy + jpSize * (if (radius > 26f) .3f else .34f), color)
        val e = type.paint(enSize, track = 1.4f, align = Paint.Align.CENTER, weight = 600)
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
        // Hexes overlap ~11px at the row seams and the later pad paints on top (as later SVG siblings do),
        // so walk the list backwards to hit the pad that is actually visible there.
        for (i in pads.indices.reversed()) if (Nge.inHex(i, x, y)) return pads[i]
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
        T.SETUP -> onSetup()
        T.LCD -> car.launch(when (car.source) {
            Src.RADIO -> Car.RADIO_UI
            Src.USB -> Car.USB_MUSIC_UI
            Src.BT -> Car.BT_MUSIC_UI
        })
    }

    companion object { private const val STOCK_ACCENT = "#FF5A1F" }
}
