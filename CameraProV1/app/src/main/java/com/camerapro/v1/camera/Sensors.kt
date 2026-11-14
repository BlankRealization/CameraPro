package com.camerapro.v1.camera

import android.content.Context

/** The phone's motion sensors in one place, so the activity can start and stop them together. */
class Sensors(context: Context) {
    val level = LevelSensor(context)
    val motion = MotionSensor(context)

    fun start() {
        level.start()
        motion.start()
    }

    fun stop() {
        level.stop()
        motion.stop()
    }
}
