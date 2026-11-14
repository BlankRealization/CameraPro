package com.camerapro.v1.analysis

enum class SceneType(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
    FOOD("Food"),
    ARCHITECTURE("Architecture"),
    GENERAL("General"),
}

/** A rectangle in preview space, every value between 0 and 1 (0,0 = top left of the preview). */
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = width * height
}

enum class SubjectKind { FACE, OBJECT }

/** [trackingId] stays the same for the same face or object from frame to frame (null if unknown). */
data class Subject(val rect: NormRect, val kind: SubjectKind, val trackingId: Int? = null)

data class DetectedBodyLandmark(
    val type: Int,
    val x: Float,
    val y: Float,
    val likelihood: Float
)

data class BarcodeResult(
    val displayValue: String,
    val url: String? = null
)

/** Everything the analyzer learned from one camera frame. */
data class FrameAnalysis(
    val subjects: List<Subject>,
    val scene: SceneType,
    /** Strongest scene label with its confidence, for the debug readout, e.g. "Sky 0.91". */
    val topLabel: String?,
    /** How long this frame took to analyze, in milliseconds (Gate A target: under 300). */
    val analysisMs: Long,
    /** Coarse brightness map of the frame, used for low-light, backlight and exposure checks. */
    val luma: LumaGrid? = null,
    /** 256-bin luma histogram normalized for visual graph rendering. */
    val histogram: IntArray? = null,
    /** Live body landmarks detected by ML Kit Pose Detection. */
    val poseLandmarks: List<DetectedBodyLandmark> = emptyList(),
    /** Scanned QR codes or barcodes found in frame. */
    val barcodes: List<BarcodeResult> = emptyList(),
)

/**
 * A coarse brightness map of the frame (0 = black, 255 = white), upright, stored row by row
 * from the top left. Cheap to build, and enough to judge lighting.
 */
class LumaGrid(val cols: Int, val rows: Int, private val values: IntArray) {

    fun mean(): Float {
        if (values.isEmpty()) return 0f
        var sum = 0L
        for (value in values) sum += value
        return sum.toFloat() / values.size
    }

    /** Average brightness of the cells whose centers are inside [rect], or null if there are none. */
    fun meanInside(rect: NormRect): Float? = meanWhere { u, v -> contains(rect, u, v) }

    /** Average brightness of everything outside [rect], or null if [rect] covers the whole frame. */
    fun meanOutside(rect: NormRect): Float? = meanWhere { u, v -> !contains(rect, u, v) }

    /** Share of cells (0 to 1) that are brighter than [level]. */
    fun fractionAbove(level: Int): Float {
        if (values.isEmpty()) return 0f
        var count = 0
        for (value in values) if (value > level) count++
        return count.toFloat() / values.size
    }

    /** Computes a 256-bin luma histogram from the grid cells. */
    fun computeHistogram(): IntArray {
        val bins = IntArray(256)
        for (v in values) {
            val idx = v.coerceIn(0, 255)
            bins[idx]++
        }
        return bins
    }

    private fun contains(rect: NormRect, u: Float, v: Float): Boolean =
        u >= rect.left && u <= rect.right && v >= rect.top && v <= rect.bottom

    private inline fun meanWhere(include: (Float, Float) -> Boolean): Float? {
        var sum = 0L
        var count = 0
        for (row in 0 until rows) {
            val v = (row + 0.5f) / rows
            for (col in 0 until cols) {
                val u = (col + 0.5f) / cols
                if (include(u, v)) {
                    sum += values[row * cols + col]
                    count++
                }
            }
        }
        return if (count > 0) sum.toFloat() / count else null
    }
}
