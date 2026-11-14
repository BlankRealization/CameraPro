package com.camerapro.v1.quality

import com.camerapro.v1.analysis.FrameAnalysis
import com.camerapro.v1.analysis.NormRect

enum class Warning(val text: String) {
    HOLD_STEADY("Hold steady"),
    LOW_LIGHT("Low light: hold steady"),
    BACKLIT("Subject is backlit"),
    HIGHLIGHTS("Bright areas washing out"),
}

/**
 * Turns raw frame brightness and phone motion into a short list of warnings. A warning must be hit
 * several frames in a row to appear and is missed several frames in a row to disappear,
 * so the labels do not flicker. Call [update] from one thread only (the UI thread).
 */
class QualityMonitor {

    private val streaks = IntArray(Warning.entries.size)
    private val active = BooleanArray(Warning.entries.size)

    fun update(analysis: FrameAnalysis?, subject: NormRect?, angularSpeed: Float): List<Warning> {
        val luma = analysis?.luma
        val mean = luma?.mean()
        val dark = mean != null && mean < DARK_MEAN

        val inside = if (luma != null && subject != null) luma.meanInside(subject) else null
        val outside = if (luma != null && subject != null) luma.meanOutside(subject) else null

        val hits = BooleanArray(Warning.entries.size)
        hits[Warning.HOLD_STEADY.ordinal] = angularSpeed > (if (dark) SHAKE_DARK else SHAKE_NORMAL)
        hits[Warning.LOW_LIGHT.ordinal] = dark
        hits[Warning.BACKLIT.ordinal] =
            inside != null && outside != null &&
                outside - inside > BACKLIT_GAP && inside < BACKLIT_SUBJECT_MAX
        hits[Warning.HIGHLIGHTS.ordinal] = luma != null && luma.fractionAbove(250) > HIGHLIGHT_SHARE

        val result = ArrayList<Warning>()
        for (warning in Warning.entries) {
            val i = warning.ordinal
            streaks[i] = if (hits[i]) (streaks[i] + 1).coerceAtMost(MAX_STREAK) else (streaks[i] - 1).coerceAtLeast(0)
            if (!active[i] && streaks[i] >= ON_STREAK) active[i] = true
            if (active[i] && streaks[i] == 0) active[i] = false
            if (active[i]) result.add(warning)
        }
        // Low light already says "hold steady", so drop the duplicate.
        if (Warning.LOW_LIGHT in result) result.remove(Warning.HOLD_STEADY)
        return result
    }

    private companion object {
        const val ON_STREAK = 3
        const val MAX_STREAK = 6
        const val SHAKE_NORMAL = 0.6f // rad/s
        const val SHAKE_DARK = 0.25f // rad/s, stricter because the exposure is longer
        const val DARK_MEAN = 45f
        const val BACKLIT_GAP = 55f
        const val BACKLIT_SUBJECT_MAX = 100f
        const val HIGHLIGHT_SHARE = 0.12f
    }
}
