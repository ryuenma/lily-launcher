package com.lily.launcher

import android.app.Activity
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
import android.widget.FrameLayout

/**
 * Default HOME. Draws the mockup at 1:1 on the 1024×600 panel (scaled if the window differs),
 * fullscreen with our own tabs; swiping down from the top edge brings the Android bar back.
 *
 * GPS vehicle speed (LocationManager/GPS_PROVIDER) drives the speed-zone palette.
 * Thresholds + hysteresis live in Car; this activity only feeds Car.setSpeed().
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
            if (acc > 20f || spd < 0f) return   // ignore low-quality / invalid fixes
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
                car.setSpeed(0)   // tunnel / no fix → normal zone
            }
            staleHandler.postDelayed(this, 1000L)
        }
    }
    private fun scheduleStaleCheck() {
        staleHandler.removeCallbacks(staleRunnable)
        staleHandler.postDelayed(staleRunnable, 1000L)
    }

    // ── Accelerometer supplement (early transition hint) ──
    private var sensorManager: SensorManager? = null
    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            // magnitude of linear acceleration; hint only, never overrides GPS zone
            val m = kotlin.math.sqrt((e.values[0]*e.values[0] + e.values[1]*e.values[1] + e.values[2]*e.values[2]).toDouble())
            if (m > 2.5) {
                // pre-warm: no zone change; GPS remains authoritative
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        car = (application as App).car
        val ui = (application as App).ui.get()           // usually ready: loaded while Car and the activity started
        stage = Stage(this, ui, car)
        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF000000.toInt())
        root.addView(stage, FrameLayout.LayoutParams(Stage.W, Stage.H))
        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->      // fit other panel sizes, keep aspect
            val s = minOf((r - l) / Stage.W.toFloat(), (b - t) / Stage.H.toFloat())
            stage.pivotX = 0f; stage.pivotY = 0f; stage.scaleX = s; stage.scaleY = s
            stage.translationX = ((r - l) - Stage.W * s) / 2; stage.translationY = ((b - t) - Stage.H * s) / 2
        }
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
        stage.deck.playing = false           // stop animating while Home is covered
        super.onPause()
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        try {
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, locationListener, mainLooper)
        } catch (e: Exception) {
            // GPS unavailable → normal zone
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

    /** HOME pressed while already home: close any sheet (like the stock launcher). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Intent.ACTION_MAIN == intent.action) stage.sheet.close()
    }

    override fun onBackPressed() { stage.sheet.back() }   // never leave Home with Back

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
