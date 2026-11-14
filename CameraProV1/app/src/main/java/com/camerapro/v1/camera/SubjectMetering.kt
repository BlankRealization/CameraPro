package com.camerapro.v1.camera

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import com.camerapro.v1.analysis.Subject
import com.camerapro.v1.analysis.SubjectKind
import kotlin.math.hypot

/**
 * Points the phone's own focus, exposure and white balance at the subject that matters:
 *  - a subject the user locked by tapping it gets focus + exposure + white balance, refreshed as it moves;
 *  - with Auto on, a face gets exposure + white balance (focus stays continuous, so a face that
 *    moves toward or away from you stays sharp);
 *  - a tap on empty space is a plain tap-to-focus that the face logic leaves alone for a few seconds.
 * Call everything on the main thread.
 */
class SubjectMetering {

    private var metered = false
    private var meteredLocked = false
    private var lastX = 0f
    private var lastY = 0f
    private var lastMs = 0L
    private var holdOffUntilMs = 0L

    fun update(
        controller: LifecycleCameraController,
        view: PreviewView?,
        subject: Subject?,
        locked: Boolean,
        autoFace: Boolean,
        nowMs: Long,
    ) {
        // The user just tapped somewhere on purpose: do not override that with face metering.
        if (!locked && nowMs < holdOffUntilMs) return

        val wanted = subject != null && (locked || (autoFace && subject.kind == SubjectKind.FACE))
        if (!wanted || subject == null || view == null) {
            if (metered) {
                cancel(controller)
                metered = false
            }
            return
        }

        val cx = subject.rect.centerX
        val cy = subject.rect.centerY
        val moved = hypot(cx - lastX, cy - lastY) > MOVE_THRESHOLD
        val sinceMs = nowMs - lastMs
        val modeChanged = locked != meteredLocked

        // Only re-meter when the subject has actually moved, so a still subject is not re-focused over and over.
        if (!metered || modeChanged || (moved && sinceMs >= MIN_GAP_MS)) {
            meter(controller, view, cx, cy, locked, nowMs)
        }
    }

    /** Forget the current metering, for example after switching cameras. */
    fun reset() {
        metered = false
        holdOffUntilMs = 0L
    }

    /** Plain tap-to-focus at a spot of the preview (x and y between 0 and 1). */
    fun tapFocus(controller: LifecycleCameraController, view: PreviewView?, nx: Float, ny: Float) {
        if (view == null || view.width == 0 || view.height == 0) return
        try {
            val point = view.meteringPointFactory.createPoint(nx * view.width, ny * view.height)
            val action = FocusMeteringAction.Builder(point, ALL_FLAGS).build()
            controller.cameraControl?.startFocusAndMetering(action)
            metered = false
            holdOffUntilMs = SystemClock.elapsedRealtime() + TAP_HOLD_MS
        } catch (e: RuntimeException) {
            Log.w(TAG, "Tap to focus failed", e)
        }
    }

    private fun meter(
        controller: LifecycleCameraController,
        view: PreviewView,
        cx: Float,
        cy: Float,
        locked: Boolean,
        nowMs: Long,
    ) {
        if (view.width == 0 || view.height == 0) return
        try {
            val point = view.meteringPointFactory.createPoint(cx * view.width, cy * view.height)
            val flags = if (locked) ALL_FLAGS else FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
            val action = FocusMeteringAction.Builder(point, flags).disableAutoCancel().build()
            if (controller.cameraInfo?.isFocusMeteringSupported(action) == false) return
            controller.cameraControl?.startFocusAndMetering(action)
            metered = true
            meteredLocked = locked
            lastX = cx
            lastY = cy
            lastMs = nowMs
        } catch (e: RuntimeException) {
            Log.w(TAG, "Metering failed", e)
        }
    }

    private fun cancel(controller: LifecycleCameraController) {
        try {
            controller.cameraControl?.cancelFocusAndMetering()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not cancel metering", e)
        }
    }

    private companion object {
        const val TAG = "SubjectMetering"
        const val ALL_FLAGS =
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
        const val MOVE_THRESHOLD = 0.10f // re-meter after the subject moves 10% of the frame
        const val MIN_GAP_MS = 1000L
        const val TAP_HOLD_MS = 5000L
    }
}
