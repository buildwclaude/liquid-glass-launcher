package com.liquidglass.launcher

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlin.math.abs

class MainActivity : ComponentActivity(), SensorEventListener {

    // Device tilt (from the accelerometer) that glass surfaces react to.
    private val tilt = mutableStateOf(Tilt(0f, 0f))

    // Counts presses of the home gesture while already in the launcher,
    // so the drawer can snap shut.
    private val homePresses = mutableIntStateOf(0)

    private var sensorManager: SensorManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                LauncherRoot(
                    tilt = tilt.value,
                    homePresses = homePresses.intValue,
                    setWallpaperBlur = ::setWallpaperBlur,
                )
            }
        }
    }

    /**
     * Real wallpaper blur using the system's cross-window blur (Android 12+).
     * If the device has it switched off (e.g. battery saver), the call is
     * quietly ignored and the drawer's own dark veil keeps things readable.
     */
    private fun setWallpaperBlur(progress: Float) {
        try {
            window.setBackgroundBlurRadius((progress * 64f).toInt())
        } catch (_: Throwable) {
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The home gesture re-delivers our start intent; use it to reset the UI.
        homePresses.intValue++
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Convert gravity readings to a gentle -1..1 tilt, smoothed so the
        // highlights glide instead of jitter. Small changes are skipped to
        // avoid redrawing the screen for imperceptible movement.
        val x = (-event.values[0] / 9.81f).coerceIn(-1f, 1f)
        val y = (event.values[1] / 9.81f).coerceIn(-1f, 1f)
        val current = tilt.value
        val nx = current.x + 0.2f * (x - current.x)
        val ny = current.y + 0.2f * (y - current.y)
        if (abs(nx - current.x) > 0.01f || abs(ny - current.y) > 0.01f) {
            tilt.value = Tilt(nx, ny)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
