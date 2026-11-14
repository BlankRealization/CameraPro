package com.camerapro.v1.camera

import android.util.Log
import androidx.camera.view.LifecycleCameraController
import com.camerapro.v1.analysis.FrameAnalysis
import com.camerapro.v1.analysis.NormRect
import com.camerapro.v1.analysis.SceneType
import com.camerapro.v1.analysis.SubjectKind
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Nudges the phone's exposure compensation so the subject (or the whole frame when there is no
 * subject) ends up at a sensible brightness for the scene. The phone's own auto exposure still
 * picks ISO and shutter; this only moves its target up or down, in small steps, at most every
 * 1.5 s, and stops inside a dead band so it never hunts back and forth.
 *
 * Call [update] on the main thread.
 */
class ExposureAutopilot {

    private var lastAdjustMs = 0L

    /** Current exposure bias in stops (EV), or null if the camera does not support it yet. */
    fun currentEv(controller: LifecycleCameraController): Float? {
        val state = controller.cameraInfo?.exposureState ?: return null
        if (!state.isExposureCompensationSupported) return null
        return state.exposureCompensationIndex * state.exposureCompensationStep.toFloat()
    }

    fun reset(controller: LifecycleCameraController) {
        val state = controller.cameraInfo?.exposureState ?: return
        if (state.isExposureCompensationSupported && state.exposureCompensationIndex != 0) {
            setIndex(controller, 0)
        }
    }

    fun update(
        controller: LifecycleCameraController,
        analysis: FrameAnalysis?,
        subject: NormRect?,
        subjectKind: SubjectKind?,
        nowMs: Long,
    ) {
        if (analysis == null) return
        val luma = analysis.luma ?: return
        val state = controller.cameraInfo?.exposureState ?: return
        if (!state.isExposureCompensationSupported) return
        if (nowMs - lastAdjustMs < MIN_INTERVAL_MS) return

        val stepEv = state.exposureCompensationStep.toFloat()
        if (stepEv <= 0f) return
        val range = state.exposureCompensationRange
        val minIndex = maxOf(range.lower, (MIN_EV / stepEv).roundToInt())
        val maxIndex = minOf(range.upper, (MAX_EV / stepEv).roundToInt())
        val index = state.exposureCompensationIndex

        val highlightShare = luma.fractionAbove(250)
        val direction: Int
        // With a subject, the subject's own brightness decides. A portrait against a bright sky
        // should keep the face well exposed and let the sky blow out, not darken the face.
        if (subject == null && highlightShare > BLOWN_SHARE) {
            direction = -1
        } else {
            val measured = (if (subject != null) luma.meanInside(subject) else null) ?: luma.mean()
            val target = targetBrightness(analysis.scene, subjectKind, subject != null)
            val error = target - measured
            if (abs(error) < DEAD_BAND) return
            direction = if (error > 0f) 1 else -1
            // Do not brighten a frame that already has a lot of near-white area.
            if (direction > 0 && subject == null && highlightShare > BRIGHTEN_LIMIT_SHARE) return
        }

        val stepIndexes = maxOf(1, (STEP_EV / stepEv).roundToInt())
        val newIndex = (index + direction * stepIndexes).coerceIn(minIndex, maxIndex)
        if (newIndex != index) {
            setIndex(controller, newIndex)
            lastAdjustMs = nowMs
        }
    }

    private fun setIndex(controller: LifecycleCameraController, index: Int) {
        try {
            controller.cameraControl?.setExposureCompensationIndex(index)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set exposure compensation", e)
        }
    }

    /** Target average brightness (0 to 255) for the area being measured. */
    private fun targetBrightness(scene: SceneType, kind: SubjectKind?, hasSubject: Boolean): Float =
        when {
            hasSubject && kind == SubjectKind.FACE -> 125f
            hasSubject -> 115f
            scene == SceneType.LANDSCAPE -> 105f
            scene == SceneType.FOOD -> 125f
            else -> 115f
        }

    private companion object {
        const val TAG = "ExposureAutopilot"
        const val MIN_INTERVAL_MS = 1500L // slow enough for the phone's own auto exposure to settle between steps
        const val DEAD_BAND = 22f
        const val STEP_EV = 0.33f
        const val MIN_EV = -2.0f
        const val MAX_EV = 1.5f
        const val BLOWN_SHARE = 0.12f
        const val BRIGHTEN_LIMIT_SHARE = 0.06f
    }
}
