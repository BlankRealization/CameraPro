package com.camerapro.v1.composition

import com.camerapro.v1.analysis.NormRect
import com.camerapro.v1.analysis.SceneType
import com.camerapro.v1.analysis.Subject
import com.camerapro.v1.analysis.SubjectKind
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** Which way the user should move the phone (pan = turn sideways, tilt = turn up or down). */
enum class Direction(val label: String) {
    LEFT("pan left"),
    RIGHT("pan right"),
    UP("tilt up"),
    DOWN("tilt down"),
}

enum class ZoomHint { NONE, CLOSER, BACK }

/** The layout rule the subject is placed by. [gridLines] are the guide lines, as fractions of the frame. */
enum class CompositionRule(val label: String, val gridLines: List<Float>) {
    THIRDS("Thirds", listOf(1f / 3f, 2f / 3f)),
    GOLDEN("Golden ratio", listOf(0.382f, 0.618f)),
    CENTER("Center", listOf(0.5f)),
    GOLDEN_SPIRAL("Golden Spiral", listOf(0.618f, 0.382f)),
    DIAGONALS("Diagonals", listOf(0.33f, 0.67f)),
    DYNAMIC_SYMMETRY("Dynamic Symmetry", listOf(0.25f, 0.75f));
}

/** What the user picked with the Rule button. AUTO chooses by scene and subject. */
enum class RuleChoice(val label: String) {
    AUTO("Auto"),
    THIRDS("Thirds"),
    GOLDEN("Golden"),
    CENTER("Center"),
    GOLDEN_SPIRAL("Spiral"),
    DIAGONALS("Diagonal"),
    DYNAMIC_SYMMETRY("Symmetry");

    fun next(): RuleChoice = entries[(ordinal + 1) % entries.size]
}

/** One place the subject could go. The [id] stays the same while the point itself is the same. */
data class TargetPoint(val id: String, val x: Float, val y: Float)

data class Guidance(
    /** Composition score from 0 to 100. */
    val score: Int,
    /** The subject the advice is about (smoothed), or null if nothing was found. */
    val subject: Subject?,
    /** Where that subject should sit in the frame (same size as the subject). */
    val ghost: NormRect?,
    val moves: Set<Direction>,
    val zoomHint: ZoomHint,
    val needsStraightening: Boolean,
    val message: String,
    /** True while the user has locked onto this subject by tapping it. */
    val locked: Boolean = false,
    /** The rule the target was chosen by, or null when there is no subject. */
    val rule: CompositionRule? = null,
) {
    val isAligned: Boolean
        get() = subject != null && moves.isEmpty() && zoomHint == ZoomHint.NONE

    companion object {
        val EMPTY = Guidance(
            score = 0,
            subject = null,
            ghost = null,
            moves = emptySet(),
            zoomHint = ZoomHint.NONE,
            needsStraightening = false,
            message = "Point at a subject",
        )
    }
}

/**
 * Rule-based composition scoring. The subject is placed by one rule (thirds, golden ratio or center);
 * the score rewards good placement, sensible size, clearance from the edges and a level horizon.
 * Which target point is used is decided by [TargetSelector], so it does not jump between corners.
 */
object CompositionEngine {

    private const val SHOW_THRESHOLD = 0.08f // start showing a move beyond 8% of the frame
    private const val HIDE_THRESHOLD = 0.05f // keep showing it until the error drops below 5%
    private const val TILT_LIMIT_DEGREES = 3f
    private const val FACE_Y = 0.38f // a face sits a little above the middle
    private const val FACE_CENTER_Y = 0.45f

    /**
     * The rule that suits this scene and subject, used while the Rule button is on Auto:
     * faces go on thirds, big or symmetrical subjects (buildings, close-up food) in the center,
     * landscapes and small lone subjects on the golden ratio, everything else on thirds.
     */
    fun autoRule(scene: SceneType, subject: Subject): CompositionRule {
        val area = subject.rect.area
        return when {
            subject.kind == SubjectKind.FACE -> CompositionRule.THIRDS
            area > 0.35f -> CompositionRule.CENTER
            scene == SceneType.ARCHITECTURE -> CompositionRule.CENTER
            scene == SceneType.FOOD -> if (area > 0.15f) CompositionRule.CENTER else CompositionRule.THIRDS
            scene == SceneType.LANDSCAPE -> CompositionRule.GOLDEN
            area < 0.04f -> CompositionRule.GOLDEN
            else -> CompositionRule.THIRDS
        }
    }

    /** The places the subject could go under this rule. */
    fun candidates(rule: CompositionRule, kind: SubjectKind): List<TargetPoint> {
        val isFace = kind == SubjectKind.FACE
        if (rule == CompositionRule.CENTER) {
            return listOf(TargetPoint("C", 0.5f, if (isFace) FACE_CENTER_Y else 0.5f))
        }
        if (rule == CompositionRule.GOLDEN_SPIRAL) {
            return listOf(TargetPoint("GS", 0.618f, if (isFace) FACE_Y else 0.382f))
        }
        val low = rule.gridLines[0]
        val high = rule.gridLines[1]
        if (isFace) {
            return listOf(
                TargetPoint("L", low, FACE_Y),
                TargetPoint("C", 0.5f, FACE_Y),
                TargetPoint("R", high, FACE_Y),
            )
        }
        return listOf(
            TargetPoint("TL", low, low),
            TargetPoint("TR", high, low),
            TargetPoint("BL", low, high),
            TargetPoint("BR", high, high),
        )
    }

    fun evaluate(
        subject: Subject?,
        rollDegrees: Float,
        previousMoves: Set<Direction>,
        target: TargetPoint?,
        rule: CompositionRule?,
    ): Guidance {
        val needsStraightening = abs(rollDegrees) > TILT_LIMIT_DEGREES
        val levelScore = 20f * (1f - min(abs(rollDegrees), 10f) / 10f)

        if (subject == null || target == null) {
            return Guidance(
                score = levelScore.roundToInt(),
                subject = null,
                ghost = null,
                moves = emptySet(),
                zoomHint = ZoomHint.NONE,
                needsStraightening = needsStraightening,
                message = if (needsStraightening) "Straighten the phone" else "Point at a subject",
            )
        }

        val rect = subject.rect
        val area = rect.area
        val isFace = subject.kind == SubjectKind.FACE

        val dx = target.x - rect.centerX // positive: subject should move right in the frame
        val dy = target.y - rect.centerY // positive: subject should move down in the frame

        // Moving the subject right in the frame means turning the phone left, and so on.
        val moves = mutableSetOf<Direction>()
        if (dx > threshold(Direction.LEFT, previousMoves)) moves.add(Direction.LEFT)
        if (dx < -threshold(Direction.RIGHT, previousMoves)) moves.add(Direction.RIGHT)
        if (dy > threshold(Direction.UP, previousMoves)) moves.add(Direction.UP)
        if (dy < -threshold(Direction.DOWN, previousMoves)) moves.add(Direction.DOWN)

        val tooSmall = area < (if (isFace) 0.012f else 0.04f)
        val tooBig = area > (if (isFace) 0.35f else 0.8f)
        val cutOff = rect.left < 0.01f || rect.top < 0.01f || rect.right > 0.99f || rect.bottom > 0.99f
        val zoomHint = when {
            tooSmall -> ZoomHint.CLOSER
            tooBig || cutOff -> ZoomHint.BACK
            else -> ZoomHint.NONE
        }

        val distance = hypot(dx, dy)
        val placementScore = 40f * (1f - min(distance / 0.35f, 1f))
        val sizeScore = if (zoomHint == ZoomHint.NONE) 25f else 10f
        val clearanceScore = if (cutOff) 5f else 15f
        val score = (placementScore + sizeScore + clearanceScore + levelScore)
            .roundToInt()
            .coerceIn(0, 100)

        val ghostLeft = (target.x - rect.width / 2f).coerceIn(0f, 1f - rect.width)
        val ghostTop = (target.y - rect.height / 2f).coerceIn(0f, 1f - rect.height)
        val ghost = NormRect(ghostLeft, ghostTop, ghostLeft + rect.width, ghostTop + rect.height)

        val message = when {
            needsStraightening -> "Straighten the phone"
            zoomHint == ZoomHint.CLOSER -> "Move closer or zoom in"
            zoomHint == ZoomHint.BACK -> "Step back or zoom out"
            moves.isNotEmpty() -> moves.joinToString(" and ") { it.label }
                .replaceFirstChar { it.uppercase() }
            else -> "Great framing, hold steady"
        }

        return Guidance(
            score = score,
            subject = subject,
            ghost = ghost,
            moves = moves,
            zoomHint = zoomHint,
            needsStraightening = needsStraightening,
            message = message,
            rule = rule,
        )
    }

    private fun threshold(direction: Direction, previousMoves: Set<Direction>): Float =
        if (direction in previousMoves) HIDE_THRESHOLD else SHOW_THRESHOLD
}
