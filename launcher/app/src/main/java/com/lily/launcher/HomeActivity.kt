package com.lily.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Default HOME. Draws the mockup at 1:1 on the 1024×600 panel (scaled if the window differs),
 * fullscreen with our own tabs; swiping down from the top edge brings the Android bar back.
 *
 * GPS vehicle speed (LocationManager/GPS_PROVIDER) drives the speed-zone palette.
 * Thresholds + hysteresis live in Car; this activity only feeds Car.setSpeed().
 * Thresholds are editable via a long-press dialog (Phase 3, programmatic, no bake).
 */
class HomeActivity : Activity() {
    private lateinit var car: Car
    private lateinit var stage: Stage

    private val clock = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = stage.home.invalidate()
    }
    private val refresh: () -> Unit = { stage.render() }

    // ── GPS speed ──
    private var locationManager: LocationManager? = null
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            val acc = loc.accuracy
            val spd = loc.speed
            if (acc > 20f || spd < 0f) return
            val kmh = (spd * 3.6).toInt()
            car.setSpeed(kmh)
            lastFixMs = System.currentTimeMillis()
            scheduleStaleCheck()
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) { car.setSpeed(0) }
    }
    private var lastFixMs = 0L
    private val staleHandler = Handler(Looper.getMainLooper())
    private val staleRunnable = object : Runnable {
        override fun run() {
            if (lastFixMs > 0 && System.currentTimeMillis() - lastFixMs > 3000L) {
                car.setSpeed(0)
            }
            staleHandler.postDelayed(this, 1000L)
        }
    }
    private fun scheduleStaleCheck() {
        staleHandler.removeCallbacks(staleRunnable)
        staleHandler.postDelayed(staleRunnable, 1000L)
    }

    // ── Accelerometer supplement ──
    private var sensorManager: SensorManager? = null
    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val m = kotlin.math.sqrt((e.values[0]*e.values[0] + e.values[1]*e.values[1] + e.values[2]*e.values[2]).toDouble())
            if (m > 2.5) { /* hint only; GPS authoritative */ }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        car = (application as App).car
        val ui = (application as App).ui.get()
        stage = Stage(this, ui, car)
        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF000000.toInt())
        root.addView(stage, FrameLayout.LayoutParams(Stage.W, Stage.H))
        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val s = minOf((r - l) / Stage.W.toFloat(), (b - t) / Stage.H.toFloat())
            stage.pivotX = 0f; stage.pivotY = 0f; stage.scaleX = s; stage.scaleY = s
            stage.translationX = ((r - l) - Stage.W * s) / 2; stage.translationY = ((b - t) - Stage.H * s) / 2
        }
        // long-press anywhere → threshold settings dialog
        root.setOnLongClickListener { showThresholdDialog(); true }
        setContentView(root)
        car.onVolume = { stage.home.onVolume(); stage.knob.onVolume() }
        root.post { ui.preloadSheets() }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (!hasLocationPermission()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
        }
    }

    override fun onResume() {
        super.onResume()
        immersive()
        car.listeners += refresh
        registerReceiver(clock, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        stage.render()
        startLocationUpdates()
        startAccel()
        scheduleStaleCheck()
    }

    override fun onPause() {
        car.listeners -= refresh
        unregisterReceiver(clock)
        stopLocationUpdates()
        stopAccel()
        staleHandler.removeCallbacks(staleRunnable)
        stage.deck.playing = false
        super.onPause()
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        try {
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, locationListener, mainLooper)
        } catch (e: Exception) {
            car.setSpeed(0)
        }
    }

    private fun stopLocationUpdates() {
        try { locationManager?.removeUpdates(locationListener) } catch (e: Exception) {}
    }

    private fun startAccel() {
        val sm = sensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor != null) {
            sm.registerListener(accelListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun stopAccel() {
        try { sensorManager?.unregisterListener(accelListener) } catch (e: Exception) {}
    }

    private fun showThresholdDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 20) }
        val cautionLabel = TextView(this).apply { text = "Caution threshold (km/h)" }
        val cautionInput = EditText(this).apply { setText(car.cautionThreshold().toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val emergencyLabel = TextView(this).apply { text = "Emergency threshold (km/h)" }
        val emergencyInput = EditText(this).apply { setText(car.emergencyThreshold().toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val dayNightLabel = TextView(this).apply { text = "Day/night mode" }
        val dayNightRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (mode in listOf("auto", "manual_day", "manual_night")) {
            val btn = android.widget.Button(this).apply {
                text = when (mode) { "auto" -> "AUTO"; "manual_day" -> "DAY"; else -> "NIGHT" }
                setOnClickListener {
                    car.setDayNightMode(mode)
                    stage.render()
                }
            }
            dayNightRow.addView(btn)
        }
        layout.addView(cautionLabel); layout.addView(cautionInput)
        layout.addView(emergencyLabel); layout.addView(emergencyInput)
        layout.addView(dayNightLabel); layout.addView(dayNightRow)
        AlertDialog.Builder(this)
            .setTitle("Speed thresholds")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val c = cautionInput.text.toString().toIntOrNull() ?: car.cautionThreshold()
                val e = emergencyInput.text.toString().toIntOrNull() ?: car.emergencyThreshold()
                car.prefs.edit().putInt("cautionKmh", c).putInt("emergencyKmh", e).apply()
                car.setSpeed(car.speedKmh) // re-evaluate zone
                stage.render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Intent.ACTION_MAIN == intent.action) stage.sheet.close()
    }

    override fun onBackPressed() { stage.sheet.back() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }
}
