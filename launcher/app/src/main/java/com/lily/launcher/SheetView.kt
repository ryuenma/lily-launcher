package com.lily.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.util.Log
import java.util.Locale

/**
 * The More grid (13 tiles) and the Home settings sheet, both drawn on Canvas — no baked sheet
 * images, no baked buttons. Hit targets are collected while drawing, so what you see is exactly
 * what you can press.
 */
@SuppressLint("ViewConstructor")
class SheetView(ctx: Context, private val ui: Ui, private val car: Car,
                private val onPrefs: () -> Unit) : View(ctx) {

    enum class Kind { MORE, SETTINGS }
    var kind: Kind? = null; private set

    private val type = NgeType(ui)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val path = Path()
    private val tmp = RectF()
    private val ease = PathInterpolator(.2f, .8f, .2f, 1f)

    private class Hot(val id: String, val r: RectF)
    private val hot = ArrayList<Hot>(24)
    private var pressed: String? = null

    private val more = run {
        val base = ui.meta.getJSONArray("more").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.toMutableList()
        base.add(org.json.JSONObject().apply { put("name", "Magisk"); put("component", "com.topjohnwu.magisk"); put("special", null) })
        base.add(org.json.JSONObject().apply { put("name", "Restart"); put("component", null); put("special", "restart") })
        base
    }

    private val moreSheet = RectF(62f, 92f, 962f, 544f)
    private val setSheet = RectF(132f, 88f, 892f, 512f)
    private val jpNames = arrayOf("受信機", "記憶媒体", "映像", "記録映像", "音響", "外部入力", "書庫",
        "車両", "車載網", "操舵", "接続", "消灯", "画面設定", "管理", "再起動")

    init {
        visibility = GONE
        isClickable = true
    }

    fun open(k: Kind) {
        val wasHidden = kind == null
        kind = k
        visibility = VISIBLE
        if (wasHidden) {
            alpha = 0f
            translationY = 24f
            animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(ease).start()
        }
        invalidate()
    }

    /** Back / Done: settings → more → closed, like the mockup. */
    fun back(): Boolean {
        when (kind) {
            Kind.SETTINGS -> open(Kind.MORE)
            Kind.MORE -> close()
            null -> return false
        }
        return true
    }

    fun close(animated: Boolean = true) {
        kind = null
        if (!animated) { visibility = GONE; return }
        animate().alpha(0f).translationY(24f).setDuration(200).setInterpolator(ease)
            .withEndAction { if (kind == null) visibility = GONE }.start()
    }

    // ═════════════ palette (the sheet follows the speed zone, like Home) ═════════════

    private fun ink() = ui.zoneColor("text")
    private fun bg() = ui.zoneColor("bg")
    private fun accent() = ui.zoneColor("accent")
    private fun onFill() = if (car.speedZone == SpeedZone.EMERGENCY) ui.zoneColor("ok") else Color.BLACK
    private fun rule() = withAlpha(ink(), .28f)

    override fun onDraw(c: Canvas) {
        val k = kind ?: return
        hot.clear()
        fill.color = Color.argb(199, 0, 0, 0)                 // .78 scrim
        c.drawRect(0f, 0f, Nge.W, Nge.H, fill)
        when (k) {
            Kind.MORE -> drawMore(c)
            Kind.SETTINGS -> drawSettings(c)
        }
    }

    // ── panels ──
    private fun panel(c: Canvas, r: RectF) {
        fill.color = Color.BLACK
        c.drawRect(r.left - 3f, r.top - 3f, r.right + 3f, r.bottom + 3f, fill)
        fill.color = bg()
        c.drawRect(r, fill)
        line.color = ink(); line.strokeWidth = 1f
        c.drawRect(r, line)
    }

    private fun header(c: Canvas, r: RectF, title: String, jp: String, meta: String) {
        tmp.set(r.left, r.top, r.right, r.top + 46f)
        fill.color = accent()
        c.drawRect(tmp, fill)
        fill.color = Color.BLACK
        c.drawRect(tmp.left, tmp.bottom - 2f, tmp.right, tmp.bottom, fill)

        val t = type.paint(15f, track = 5f, weight = 700)                    // .sheet-hd .t
        c.text(t, title, r.left + 14f, r.top + 29f, onFill())
        val j = type.paint(11f, jp = true, track = 3f, weight = 700)         // .sheet-hd .j
        c.text(j, jp, r.left + 14f + t.measureText(title) + 12f, r.top + 28f, withAlpha(onFill(), .85f))
        val m = type.paint(8f, mono = true, track = 1.4f, align = Paint.Align.RIGHT)
        c.text(m, meta, r.right - 14f - 52f, r.top + 28f, withAlpha(onFill(), .75f))

        val x = RectF(r.right - 14f - 44f, r.top + 1f, r.right - 14f, r.top + 45f)
        fill.color = Color.BLACK
        c.drawRect(x, fill)
        line.color = onFill(); line.strokeWidth = 1f
        c.drawRect(x, line)
        val cross = type.paint(16f, mono = true, align = Paint.Align.CENTER)
        c.text(cross, "X", x.centerX(), x.centerY() + 6f, ink())
        hot.add(Hot("close", RectF(x)))
    }

    private fun footer(c: Canvas, r: RectF, text: String) {
        line.color = rule(); line.strokeWidth = 1f
        c.drawLine(r.left + 14f, r.bottom - 32f, r.right - 14f, r.bottom - 32f, line)
        val p = type.paint(8.5f, mono = true, track = 1.2f)
        c.text(p, text, r.left + 14f, r.bottom - 12f, withAlpha(ink(), .7f))
    }

    // ── MORE: 13 hex tiles ──
    private fun drawMore(c: Canvas) {
        val r = moreSheet
        panel(c, r)
        header(c, r, "MORE", "拡張機能", "${more.size} MODULES · SELECT ONE")
        footer(c, r, pressed?.let { "SELECT // 選択" } ?: "READY // 待機")

        val tw = 106f
        val th = 100f
        val gx = 8f
        val gy = 4f
        val x0 = r.left + (r.width() - (5 * tw + 4 * gx)) / 2f
        val y0 = r.top + 46f + 24f
        more.forEachIndexed { i, m ->
            val col = i % 5
            val row = i / 5
            val x = x0 + col * (tw + gx)
            val y = y0 + row * (th + gy)
            tile(c, x, y, tw, th, i, m.optString("name").uppercase(Locale.US), jpNames.getOrElse(i) { "" })
        }
    }

    private fun tile(c: Canvas, x: Float, y: Float, w: Float, h: Float, i: Int, label: String, jp: String) {
        val cx = x + w / 2f
        val cy = y + 40f
        val down = pressed == "tile:$i"
        val shift = if (down) 2f else 0f

        Nge.hexPath(path, cx, cy + shift, 40f, 45f)
        fill.color = if (down) withAlpha(accent(), .22f) else Color.BLACK
        c.drawPath(path, fill)
        line.strokeWidth = if (down) 2f else 1.5f
        line.color = if (down) accent() else withAlpha(ink(), .45f)
        c.drawPath(path, line)

        val col = if (down) accent() else ink()
        tileIcon(c, i, cx, cy + shift, col)

        val lp = type.paint(8f, track = 1.2f, align = Paint.Align.CENTER, weight = 600)
        c.text(lp, label, cx, y + 92f + shift, col)
        val jpP = type.paint(7f, jp = true, track = 1f, align = Paint.Align.CENTER, weight = 700)
        c.text(jpP, jp, cx, y + 104f + shift, if (down) withAlpha(accent(), .8f) else withAlpha(ink(), .45f))

        hot.add(Hot("tile:$i", RectF(x, y, x + w, y + h)))
    }

    /** Tile glyphs in mockup order: radio, usb, video, dashcam, eq, aux, files, car, can, wheel, android, off, home. */
    private fun tileIcon(c: Canvas, i: Int, cx: Float, cy: Float, col: Int) {
        line.strokeWidth = 1.4f
        line.color = col
        fill.color = col
        when (i) {
            0 -> { // radio: waves + dial + body line
                tmp.set(cx - 18f, cy - 4f, cx + 18f, cy + 32f); c.drawArc(tmp, 200f, 140f, false, line)
                tmp.set(cx - 10f, cy + 4f, cx + 10f, cy + 24f); c.drawArc(tmp, 200f, 140f, false, line)
                c.drawCircle(cx, cy + 16f, 2.4f, fill)
                c.drawLine(cx - 20f, cy - 18f, cx + 20f, cy - 18f, line)
            }
            1 -> { // usb: stick + prongs
                c.drawRect(cx - 6f, cy - 8f, cx + 6f, cy + 22f, line)
                c.drawLine(cx, cy - 8f, cx, cy - 26f, line)
                c.drawLine(cx - 5f, cy - 20f, cx + 5f, cy - 20f, line)
                c.drawLine(cx - 4f, cy + 28f, cx - 4f, cy + 34f, line)
                c.drawLine(cx + 4f, cy + 28f, cx + 4f, cy + 34f, line)
            }
            2 -> { // video: frame + play
                c.drawRect(cx - 18f, cy - 13f, cx + 18f, cy + 23f, line)
                path.reset()
                path.moveTo(cx - 9f, cy - 5f); path.lineTo(cx + 6f, cy + 3f); path.lineTo(cx - 9f, cy + 11f); path.close()
                c.drawPath(path, fill)
            }
            3 -> { // dashcam: body + lens
                c.drawRect(cx - 20f, cy - 11f, cx + 12f, cy + 17f, line)
                path.reset()
                path.moveTo(cx + 12f, cy - 5f); path.lineTo(cx + 26f, cy - 11f)
                path.lineTo(cx + 26f, cy + 17f); path.lineTo(cx + 12f, cy + 11f); path.close()
                c.drawPath(path, line)
                c.drawCircle(cx - 4f, cy + 3f, 6f, line)
            }
            4 -> { // eq: three faders
                c.drawLine(cx - 16f, cy - 16f, cx - 16f, cy + 22f, line)
                c.drawLine(cx, cy - 28f, cx, cy + 22f, line)
                c.drawLine(cx + 16f, cy - 6f, cx + 16f, cy + 22f, line)
                c.drawRect(cx - 23f, cy - 4f, cx - 9f, cy, fill)
                c.drawRect(cx - 7f, cy + 6f, cx + 7f, cy + 10f, fill)
                c.drawRect(cx + 9f, cy - 16f, cx + 23f, cy - 12f, fill)
            }
            5 -> { // aux: jack rings
                c.drawCircle(cx, cy + 6f, 16f, line)
                c.drawCircle(cx, cy + 6f, 5f, line)
                c.drawLine(cx, cy - 10f, cx, cy - 24f, line)
            }
            6 -> { // files: folder
                path.reset()
                path.moveTo(cx - 22f, cy + 20f); path.lineTo(cx - 22f, cy - 16f)
                path.lineTo(cx - 8f, cy - 16f); path.lineTo(cx - 2f, cy - 8f)
                path.lineTo(cx + 22f, cy - 8f); path.lineTo(cx + 22f, cy + 20f); path.close()
                c.drawPath(path, line)
                c.drawLine(cx - 22f, cy + 2f, cx + 22f, cy + 2f, line)
            }
            7 -> { // car: body + wheels
                path.reset()
                path.moveTo(cx - 22f, cy + 14f); path.lineTo(cx - 22f, cy - 2f)
                path.lineTo(cx - 14f, cy - 16f); path.lineTo(cx + 14f, cy - 16f)
                path.lineTo(cx + 22f, cy - 2f); path.lineTo(cx + 22f, cy + 14f); path.close()
                c.drawPath(path, line)
                c.drawCircle(cx - 11f, cy + 18f, 5f, line)
                c.drawCircle(cx + 11f, cy + 18f, 5f, line)
                c.drawLine(cx - 12f, cy - 2f, cx + 12f, cy - 2f, line)
            }
            8 -> { // can: box + bus stubs
                c.drawRect(cx - 11f, cy - 11f, cx + 11f, cy + 17f, line)
                for (k in 0..2) {
                    val y = cy - 5f + k * 6f
                    c.drawLine(cx - 11f, y, cx - 22f, y, line)
                    c.drawLine(cx + 11f, y, cx + 22f, y, line)
                }
            }
            9 -> { // wheel: rim + hub + spokes
                c.drawCircle(cx, cy, 22f, line)
                c.drawCircle(cx, cy, 6f, line)
                c.drawLine(cx, cy - 22f, cx, cy - 6f, line)
                c.drawLine(cx - 19f, cy + 11f, cx - 5f, cy + 3f, line)
                c.drawLine(cx + 19f, cy + 11f, cx + 5f, cy + 3f, line)
            }
            10 -> { // android: dome + antennae + eyes
                tmp.set(cx - 20f, cy - 4f, cx + 20f, cy + 32f)
                c.drawArc(tmp, 180f, 180f, true, line)
                c.drawLine(cx - 20f, cy + 14f, cx + 20f, cy + 14f, line)
                c.drawLine(cx - 12f, cy - 4f, cx - 17f, cy - 13f, line)
                c.drawLine(cx + 12f, cy - 4f, cx + 17f, cy - 13f, line)
                c.drawCircle(cx - 7f, cy + 6f, 1.6f, fill)
                c.drawCircle(cx + 7f, cy + 6f, 1.6f, fill)
            }
            11 -> { // power
                c.drawLine(cx, cy - 22f, cx, cy - 4f, line)
                tmp.set(cx - 18f, cy - 16f, cx + 18f, cy + 20f)
                c.drawArc(tmp, 130f, 280f, false, line)
            }
            12 -> { // home setup
                path.reset()
                path.moveTo(cx, cy - 22f); path.lineTo(cx - 24f, cy - 2f)
                path.lineTo(cx - 24f, cy + 22f); path.lineTo(cx + 24f, cy + 22f)
                path.lineTo(cx + 24f, cy - 2f); path.close()
                c.drawPath(path, line)
                c.drawRect(cx - 6f, cy + 6f, cx + 6f, cy + 22f, line)
            }
            13 -> { // magisk: shield
                path.reset()
                path.moveTo(cx, cy - 24f); path.lineTo(cx - 18f, cy - 14f)
                path.lineTo(cx - 18f, cy + 8f); path.lineTo(cx, cy + 24f)
                path.lineTo(cx + 18f, cy + 8f); path.lineTo(cx + 18f, cy - 14f); path.close()
                c.drawPath(path, line)
                val mp = type.paint(12f, mono = true, align = Paint.Align.CENTER)
                c.text(mp, "M", cx, cy + 6f, ink())
            }
            14 -> { // restart: circular arrow
                tmp.set(cx - 20f, cy - 20f, cx + 20f, cy + 20f)
                c.drawArc(tmp, 45f, 270f, false, line)
                path.reset()
                path.moveTo(cx + 14f, cy - 14f); path.lineTo(cx + 20f, cy - 20f)
                path.lineTo(cx + 20f, cy - 8f); path.close()
                c.drawPath(path, fill)
            }
            else -> { // home
                path.reset()
                path.moveTo(cx, cy - 22f); path.lineTo(cx - 24f, cy - 2f)
                path.lineTo(cx - 24f, cy + 22f); path.lineTo(cx + 24f, cy + 22f)
                path.lineTo(cx + 24f, cy - 2f); path.close()
                c.drawPath(path, line)
                c.drawRect(cx - 6f, cy + 6f, cx + 6f, cy + 22f, line)
            }
        }
    }

    // ── SETTINGS ──
    private fun drawSettings(c: Canvas) {
        val r = setSheet
        panel(c, r)
        val mode = car.dayNightMode
        header(c, r, "SETUP", "設定",
            (if (mode == "auto") "AUTO" else "MANUAL") + " · " + if (car.night) "NIGHT" else "DAY")
        footer(c, r, "THEME FOLLOWS CLOCK · NOT HEADLIGHT STATE")

        val l = r.left + 18f
        val right = r.right - 18f
        val colW = (right - l - 26f) / 2f
        val x1 = l
        val x2 = l + colW + 26f
        var y = r.top + 60f

        // left column: day/night, Android Auto, play on connect, speed source
        segRow(c, x1, y, colW, "DAY / NIGHT", "時間連動 17:00 – 05:00", "mode",
            listOf("auto" to "AUTO", "manual_day" to "DAY", "manual_night" to "NIGHT"), mode)
        y += 70f
        segRow(c, x1, y, colW, "ANDROID AUTO", "接続時に自動起動", "aa",
            listOf("true" to "ON", "false" to "OFF"), car.flag("autoAA").toString())
        y += 70f
        segRow(c, x1, y, colW, "PLAY ON CONNECT", "再接続時に再生", "resume",
            listOf("true" to "ON", "false" to "OFF"), car.flag("resumeOnStart").toString())
        y += 70f
        segRow(c, x1, y, colW, "SPEED SOURCE", "速度取得", "gps",
            listOf("gps" to "GPS"), "gps")

        // right column: thresholds and accent
        y = r.top + 60f
        stepRow(c, x2, y, colW, "CAUTION", "注意 閾値 km/h", "caution", car.cautionThreshold())
        y += 70f
        stepRow(c, x2, y, colW, "EMERGENCY", "危険 閾値 km/h", "emergency", car.emergencyThreshold())
        y += 70f
        accentRow(c, x2, y, colW)
    }

    private fun rowLabel(c: Canvas, x: Float, y: Float, w: Float, key: String, sub: String, controlW: Float) {
        val kp = type.paint(9f, track = 1.8f)
        c.text(kp, kp.fit(key, w - controlW - 10f), x, y + 22f, ink())
        val sp = type.paint(7f, jp = true, track = 1f)                       // .srow .k small
        c.text(sp, sp.fit(sub, w - controlW - 10f), x, y + 38f, withAlpha(ink(), .5f))
        line.color = rule(); line.strokeWidth = 1f
        c.drawLine(x, y + 52f, x + w, y + 52f, line)
    }

    private fun segRow(c: Canvas, x: Float, y: Float, w: Float, key: String, sub: String, id: String,
                       options: List<Pair<String, String>>, active: String) {
        val bp = type.paint(9f, track = 1.4f, align = Paint.Align.CENTER, weight = 500)   // .seg button
        val bpOn = type.paint(9f, track = 1.4f, align = Paint.Align.CENTER, weight = 700) // .seg button.on
        val widths = options.map { bpOn.measureText(it.second) + 20f }
        val total = widths.sum() + 3f * (options.size - 1)
        val bx = x + w - total
        rowLabel(c, x, y, w, key, sub, total)
        var bx2 = bx
        options.forEachIndexed { i, (value, label) ->
            val bw = widths[i]
            val on = value == active
            val id2 = "seg:$id:$value"
            val down = pressed == id2
            tmp.set(bx2, y + 4f + if (down) 2f else 0f, bx2 + bw, y + 48f)
            fill.color = if (on) ink() else Color.BLACK
            c.drawRect(tmp, fill)
            if (!on) {
                line.color = withAlpha(ink(), .35f); line.strokeWidth = 1f
                c.drawRect(tmp, line)
            }
            c.text(if (on) bpOn else bp, label, tmp.centerX(), tmp.top + 28f, if (on) Color.BLACK else ink())
            hot.add(Hot(id2, RectF(tmp)))
            bx2 += bw + 3f
        }
    }

    private fun stepRow(c: Canvas, x: Float, y: Float, w: Float, key: String, sub: String, id: String, value: Int) {
        val total = 44f + 44f + 44f
        val bx = x + w - total
        rowLabel(c, x, y, w, key, sub, total)
        button(c, "step:$id:-1", bx, y + 4f, 44f, 44f, "−")
        val vp = type.paint(13f, mono = true, align = Paint.Align.CENTER)
        c.text(vp, String.format(Locale.US, "%03d", value), bx + 44f + 22f, y + 32f, ink())
        button(c, "step:$id:1", bx + 88f, y + 4f, 44f, 44f, "+")
    }

    private fun accentRow(c: Canvas, x: Float, y: Float, w: Float) {
        val sw = 44f
        val gap = 6f
        val total = 5 * sw + 4 * gap
        val bx = x + w - total
        rowLabel(c, x, y, w, "ACCENT", "アクセント", total)
        val current = car.str("accent").uppercase(Locale.US).let { if (it == "#FF5A1F") "#FF4A1C" else it }
        ACCENTS.forEachIndexed { i, hex ->
            val sx = bx + i * (sw + gap)
            val col = parseCss(hex)
            fill.color = col
            c.drawRect(sx, y + 4f, sx + sw, y + 48f, fill)
            val on = hex == current
            line.color = if (on) ink() else Color.BLACK
            line.strokeWidth = if (on) 2f else 1f
            c.drawRect(sx, y + 4f, sx + sw, y + 48f, line)
            hot.add(Hot("acc:$hex", RectF(sx, y + 4f, sx + sw, y + 48f)))
        }
    }

    private fun button(c: Canvas, id: String, x: Float, y: Float, w: Float, h: Float, label: String) {
        val down = pressed == id
        val top = y + if (down) 2f else 0f
        fill.color = Color.BLACK
        c.drawRect(x, top, x + w, top + h, fill)
        line.color = withAlpha(ink(), .45f); line.strokeWidth = 1f
        c.drawRect(x, top, x + w, top + h, line)
        val p = type.paint(15f, mono = true, align = Paint.Align.CENTER)
        c.text(p, label, x + w / 2f, top + h / 2f + 5f, ink())
        hot.add(Hot(id, RectF(x, top, x + w, top + h)))
    }

    // ═════════════ touch ═════════════

    private fun hit(x: Float, y: Float): String? {
        for (h in hot) if (h.r.contains(x, y)) return h.id
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (kind == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed = hit(e.x, e.y); invalidate() }
            MotionEvent.ACTION_UP -> {
                val id = pressed
                pressed = null
                if (id != null && hit(e.x, e.y) == id) act(id)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { pressed = null; invalidate() }
        }
        return true                        // the sheet swallows touches meant for Home underneath
    }

    private fun act(id: String) {
        when {
            id == "close" -> back()
            id.startsWith("tile:") -> {
                val i = id.removePrefix("tile:").toIntOrNull() ?: return
                val m = more.getOrNull(i) ?: return
                when (m.optString("special")) {
                    "settings" -> open(Kind.SETTINGS)
                    "screenoff" -> { close(false); car.launch(Car.SCREEN_OFF_UI) }
                    "restart" -> { close(false); try { Runtime.getRuntime().exec("reboot") } catch (e: Exception) { Log.w("LilySheet", "reboot", e) } }
                    else -> {
                        val component = m.optString("component")
                        if (component.isNotEmpty()) {
                            close(false)
                            if (component.contains("/")) car.launch(component)
                            else {
                                // bare package name (e.g. Magisk) — launch by package
                                try {
                                    val intent = context.packageManager.getLaunchIntentForPackage(component)
                                    if (intent != null) context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (e: Exception) { Log.w("LilySheet", "launch $component", e) }
                            }
                        }
                    }
                }
            }
            id.startsWith("seg:") -> {
                val parts = id.split(':')
                if (parts.size < 3) return
                when (parts[1]) {
                    "mode" -> car.setDayNightMode(parts[2])
                    "aa" -> car.prefs.edit().putBoolean("autoAA", parts[2] == "true").apply()
                    "resume" -> car.prefs.edit().putBoolean("resumeOnStart", parts[2] == "true").apply()
                    "gps" -> return                       // GPS is the only source (design §8.2)
                }
                onPrefs()
            }
            id.startsWith("step:") -> {
                val parts = id.split(':')
                if (parts.size < 3) return
                val delta = parts[2].toIntOrNull() ?: return
                val key = parts[1]
                val cur = when (key) {
                    "caution" -> car.cautionThreshold()
                    "emergency" -> car.emergencyThreshold()
                    else -> return
                }
                val next = (cur + delta * 5).coerceIn(20, 200)
                car.prefs.edit().putInt(if (key == "caution") "cautionKmh" else "emergencyKmh", next).apply()
                car.setSpeed(car.speedKmh)                // re-evaluate the zone with the new threshold
                onPrefs()
            }
            id.startsWith("acc:") -> {
                car.prefs.edit().putString("accent", id.removePrefix("acc:")).apply()
                onPrefs()
            }
        }
    }

    companion object {
        private val ACCENTS = arrayOf("#FF4A1C", "#258AC3", "#2FB35A", "#F5B400", "#B9AFCB")
    }
}
