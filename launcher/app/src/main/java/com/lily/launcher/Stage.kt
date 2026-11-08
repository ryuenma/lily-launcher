package com.lily.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.FrameLayout

/**
 * The 1024×600 home, composed in NGE z-order: [HomeView] paints the background, rail, banner,
 * main area and deck; [SpectrumView] runs the one animated visualiser under the track; [DeckView]
 * adds the animated ticker and hazard stripes; [KnobView] owns the 112 px dial; [SheetView] sits on
 * top for More / Setup. No baked layout rects are involved — every view reads its geometry from [Nge].
 */
@SuppressLint("ViewConstructor")
class Stage(ctx: Context, val ui: Ui, val car: Car) : FrameLayout(ctx) {
    val home = HomeView(ctx, ui, car, { sheet.open(SheetView.Kind.MORE) },
                                      { sheet.open(SheetView.Kind.SETTINGS) })
    val spectrum = SpectrumView(ctx, ui, car)
    val deck = DeckView(ctx, ui, car)
    val knob = KnobView(ctx, ui, car, { home.acc }) { home.onVolume() }
    val sheet = SheetView(ctx, ui, car) { render() }

    init {
        // Home and the knob change at most once a second; as GPU layers they composite a cached
        // texture instead of re-recording their display lists while the ticker animates on top.
        home.setLayerType(LAYER_TYPE_HARDWARE, null)
        knob.setLayerType(LAYER_TYPE_HARDWARE, null)

        addView(home, LayoutParams(W, H))
        val sp = Nge.spectrum
        addView(spectrum, LayoutParams(sp.width().toInt(), sp.height().toInt()).apply {
            leftMargin = sp.left.toInt()
            topMargin = sp.top.toInt()
        })
        val k = Nge.knobArea
        addView(knob, LayoutParams(k.width().toInt() + 1, k.height().toInt() + 1).apply {
            leftMargin = k.left.toInt()
            topMargin = k.top.toInt()
        })
        addView(deck, LayoutParams(W, H))
        addView(sheet, LayoutParams(W, H))
        render()
    }

    fun render() {
        ui.speedZone = car.speedZone      // Ui.zoneColor() reads this every draw
        home.restyle()
        deck.visibility = if (home.view3() == HomeView.View3.TAPE || car.speedZone != SpeedZone.NORMAL) View.VISIBLE else View.INVISIBLE
        deck.playing = car.playing
        home.invalidate()
        spectrum.invalidate()
        deck.invalidate()
        knob.invalidate()
        sheet.invalidate()
    }

    companion object {
        const val W = 1024
        const val H = 600
    }
}
