package com.camerapro.v1.composition

import kotlin.math.hypot

/**
 * Picks which target point the subject should be placed on, and then sticks with it.
 *
 * Choosing the nearest point every frame makes the target jump between corners whenever the subject
 * sits near the middle. Instead, the first choice is the nearest point, and it only changes when
 * another point is clearly closer (by [SWITCH_MARGIN]), the current one has been held for at least
 * [MIN_HOLD_MS], and the phone is not being moved quickly.
 * Call from one thread only (the UI thread).
 */
class TargetSelector {

    private var currentId: String? = null
    private var chosenAtMs = 0L

    fun reset() {
        currentId = null
    }

    /**
     * @param cx the subject's center x, as a fraction of the frame
     * @param cy the subject's center y
     * @param phoneMoving true while the phone is turning fast, which freezes the choice
     */
    fun select(
        candidates: List<TargetPoint>,
        cx: Float,
        cy: Float,
        nowMs: Long,
        phoneMoving: Boolean,
    ): TargetPoint? {
        val best = candidates.minByOrNull { hypot(it.x - cx, it.y - cy) } ?: return null
        val current = candidates.firstOrNull { it.id == currentId }

        if (current == null) {
            choose(best, nowMs)
            return best
        }
        if (best.id == current.id) return current

        val currentDistance = hypot(current.x - cx, current.y - cy)
        val bestDistance = hypot(best.x - cx, best.y - cy)
        val clearlyCloser = bestDistance + SWITCH_MARGIN < currentDistance
        val heldLongEnough = nowMs - chosenAtMs >= MIN_HOLD_MS

        if (clearlyCloser && heldLongEnough && !phoneMoving) {
            choose(best, nowMs)
            return best
        }
        return current
    }

    private fun choose(point: TargetPoint, nowMs: Long) {
        currentId = point.id
        chosenAtMs = nowMs
    }

    private companion object {
        const val SWITCH_MARGIN = 0.10f
        const val MIN_HOLD_MS = 1500L
    }
}
