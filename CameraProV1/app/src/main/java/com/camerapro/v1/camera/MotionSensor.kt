package com.camerapro.v1.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Reports how fast the phone is rotating, in radians per second (about 0.05 to 0.2 for a steady
 * hand, above 0.6 when panning or shaking). Call [start] in onResume and [stop] in onPause.
 */
class MotionSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _angularSpeed = MutableStateFlow(0f)
    val angularSpeed: StateFlow<Float> = _angularSpeed.asStateFlow()

    val isAvailable: Boolean get() = gyroscope != null

    private var smoothed = 0f

    fun start() {
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        smoothed = 0f
        _angularSpeed.value = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val speed = sqrt(x * x + y * y + z * z)
        smoothed += ALPHA * (speed - smoothed)
        _angularSpeed.value = smoothed
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val ALPHA = 0.25f
    }
}
