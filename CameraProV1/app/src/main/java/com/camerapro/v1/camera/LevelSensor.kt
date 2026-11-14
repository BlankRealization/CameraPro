package com.camerapro.v1.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Reports how far the phone is rotated clockwise around its screen axis, in degrees, relative to the
 * screen as it is currently shown (portrait or landscape, see [displayRotation]). 0 means the horizon is level.
 *
 * Call [start] in onResume and [stop] in onPause so the sensor is off in the background.
 */
class LevelSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _rollDegrees = MutableStateFlow(0f)
    val rollDegrees: StateFlow<Float> = _rollDegrees.asStateFlow()

    val isAvailable: Boolean get() = accelerometer != null

    /**
     * How the screen is turned (Surface.ROTATION_0, _90, _180 or _270). The accelerometer axes belong to
     * the phone's body, so they are remapped to the screen's axes before the angle is worked out.
     * Set by the UI whenever the screen rotates.
     */
    @Volatile
    var displayRotation: Int = Surface.ROTATION_0

    private var filteredX = 0f
    private var filteredY = 0f
    private var hasSample = false

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        hasSample = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val rawX = event.values[0]
        val rawY = event.values[1]

        // Express gravity in the screen's own axes (x to the right of the screen, y toward its top).
        val x: Float
        val y: Float
        when (displayRotation) {
            Surface.ROTATION_90 -> {
                x = -rawY
                y = rawX
            }
            Surface.ROTATION_180 -> {
                x = -rawX
                y = -rawY
            }
            Surface.ROTATION_270 -> {
                x = rawY
                y = -rawX
            }
            else -> {
                x = rawX
                y = rawY
            }
        }

        // Low-pass filter so the line does not jitter with hand shake.
        if (!hasSample) {
            filteredX = x
            filteredY = y
            hasSample = true
        } else {
            filteredX += ALPHA * (x - filteredX)
            filteredY += ALPHA * (y - filteredY)
        }

        // Phone lying flat: gravity is almost entirely on the z axis, so roll is meaningless.
        if (hypot(filteredX, filteredY) < FLAT_THRESHOLD) return

        // Upright phone reads (0, +g). Tilting it clockwise moves gravity's reading to -x.
        _rollDegrees.value = Math.toDegrees(atan2(-filteredX, filteredY).toDouble()).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val ALPHA = 0.15f
        const val FLAT_THRESHOLD = 3f // m/s^2
    }
}
