package com.camerapro.v1.camera

import com.camerapro.v1.analysis.NormRect
import com.camerapro.v1.analysis.SubjectKind
import kotlin.math.abs
import kotlin.math.sqrt

/** Works out a zoom ratio that makes the subject a comfortable size without cutting it off. */
object ZoomPlanner {

    private const val FACE_TARGET_AREA = 0.06f
    private const val OBJECT_TARGET_AREA = 0.20f
    private const val EDGE_MARGIN = 0.02f
    private const val MAX_CHANGE = 3f
    private const val MIN_CHANGE = 0.5f
    private const val MIN_WORTH_CHANGING = 0.10f

    /** Returns the zoom ratio to move to, or null if the current zoom is already fine. */
    fun targetRatio(
        current: Float,
        minRatio: Float,
        maxRatio: Float,
        rect: NormRect,
        kind: SubjectKind,
    ): Float? {
        if (rect.area <= 0f) return null
        val targetArea = if (kind == SubjectKind.FACE) FACE_TARGET_AREA else OBJECT_TARGET_AREA

        // The box area grows with the square of the zoom ratio.
        var change = sqrt(targetArea / rect.area).coerceIn(MIN_CHANGE, MAX_CHANGE)

        // Zooming scales positions around the center of the frame; never push an edge outside.
        for (edge in floatArrayOf(rect.left, rect.right, rect.top, rect.bottom)) {
            val d = abs(edge - 0.5f)
            if (d > 0.001f) change = minOf(change, (0.5f - EDGE_MARGIN) / d)
        }

        val target = (current * change).coerceIn(minRatio, maxRatio)
        return if (abs(target / current - 1f) >= MIN_WORTH_CHANGING) target else null
    }
}
