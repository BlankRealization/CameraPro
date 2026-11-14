package com.camerapro.v1.composition

import com.camerapro.v1.analysis.FrameAnalysis
import com.camerapro.v1.analysis.NormRect
import com.camerapro.v1.analysis.SceneType
import com.camerapro.v1.analysis.Subject
import com.camerapro.v1.analysis.SubjectKind
import kotlin.math.max
import kotlin.math.min

/**
 * Keeps guidance steady from frame to frame: it follows one main subject, smooths its box so the
 * guide line does not jitter, keeps the same target point and rule until there is a clear reason to
 * change them, and forgets the subject only after it has been missing for a short while.
 * The user can lock onto a chosen subject; the lock follows it using ML Kit tracking ids, falling
 * back to box overlap, and releases itself if the subject is gone for a couple of seconds.
 * Call everything from one thread only (the UI thread).
 */
class GuidanceTracker {

    private var smoothed: Subject? = null
    private var lastSeenMs = 0L
    private var previousMoves: Set<Direction> = emptySet()

    private var locked = false
    private var lockedId: Int? = null
    private var lockedKind: SubjectKind? = null
    private var lostLock = false

    private val selector = TargetSelector()
    private var ghostSmoothed: NormRect? = null

    // Automatic rule choice only changes after the new rule has been wanted for a while.
    private var activeAutoRule: CompositionRule? = null
    private var pendingAutoRule: CompositionRule? = null
    private var pendingAutoCount = 0

    val isLocked: Boolean get() = locked

    fun isLockedTo(subject: Subject): Boolean =
        locked && lockedKind == subject.kind &&
            ((lockedId != null && lockedId == subject.trackingId) ||
                smoothed?.let { overlap(it.rect, subject.rect) > MIN_OVERLAP_TO_FOLLOW } == true)

    fun lock(subject: Subject, nowMs: Long) {
        locked = true
        lockedId = subject.trackingId
        lockedKind = subject.kind
        smoothed = subject
        lastSeenMs = nowMs
        lostLock = false
    }

    fun unlock() {
        locked = false
        lockedId = null
        lockedKind = null
    }

    /** Forget everything, for example when switching between the rear and front camera. */
    fun reset() {
        unlock()
        lostLock = false
        smoothed = null
        previousMoves = emptySet()
        selector.reset()
        ghostSmoothed = null
        activeAutoRule = null
        pendingAutoRule = null
        pendingAutoCount = 0
    }

    /** True once after a lock was dropped because the subject disappeared. */
    fun consumeLostLock(): Boolean {
        val value = lostLock
        lostLock = false
        return value
    }

    /**
     * @param angularSpeed how fast the phone is turning, in rad/s; while it is high the target point is not changed
     * @param ruleChoice the layout rule the user picked, or AUTO to choose by scene and subject
     */
    fun update(
        analysis: FrameAnalysis?,
        rollDegrees: Float,
        nowMs: Long,
        angularSpeed: Float = 0f,
        ruleChoice: RuleChoice = RuleChoice.AUTO,
    ): Guidance {
        val subjects = analysis?.subjects.orEmpty()
        val candidate = if (locked) findLocked(subjects) else pickPrimary(subjects)

        if (candidate != null) {
            val previous = smoothed
            smoothed = if (
                previous != null &&
                previous.kind == candidate.kind &&
                overlap(previous.rect, candidate.rect) > MIN_OVERLAP_TO_SMOOTH
            ) {
                candidate.copy(rect = blend(previous.rect, candidate.rect, SMOOTHING))
            } else {
                candidate
            }
            lastSeenMs = nowMs
        } else if (locked) {
            if (nowMs - lastSeenMs > LOCK_LOST_MS) {
                unlock()
                lostLock = true
                smoothed = null
            }
        } else if (nowMs - lastSeenMs > FORGET_AFTER_MS) {
            smoothed = null
        }

        val subject = smoothed
        var rule: CompositionRule? = null
        var target: TargetPoint? = null
        if (subject != null) {
            val chosenRule = resolveRule(ruleChoice, analysis?.scene ?: SceneType.GENERAL, subject)
            rule = chosenRule
            target = selector.select(
                candidates = CompositionEngine.candidates(chosenRule, subject.kind),
                cx = subject.rect.centerX,
                cy = subject.rect.centerY,
                nowMs = nowMs,
                phoneMoving = angularSpeed > MOVING_SPEED,
            )
        } else {
            selector.reset()
        }

        val raw = CompositionEngine.evaluate(subject, rollDegrees, previousMoves, target, rule)
        previousMoves = raw.moves
        return raw.copy(locked = locked, ghost = smoothGhost(raw.ghost))
    }

    /** The rule to use now. A manual choice applies at once; Auto only changes after about a second. */
    private fun resolveRule(choice: RuleChoice, scene: SceneType, subject: Subject): CompositionRule {
        if (choice != RuleChoice.AUTO) {
            return when (choice) {
                RuleChoice.THIRDS -> CompositionRule.THIRDS
                RuleChoice.GOLDEN -> CompositionRule.GOLDEN
                RuleChoice.CENTER -> CompositionRule.CENTER
                RuleChoice.GOLDEN_SPIRAL -> CompositionRule.GOLDEN_SPIRAL
                RuleChoice.DIAGONALS -> CompositionRule.DIAGONALS
                RuleChoice.DYNAMIC_SYMMETRY -> CompositionRule.DYNAMIC_SYMMETRY
                RuleChoice.AUTO -> CompositionRule.THIRDS
            }
        }

        val wanted = CompositionEngine.autoRule(scene, subject)
        val active = activeAutoRule
        if (active == null) {
            activeAutoRule = wanted
            return wanted
        }
        if (wanted == active) {
            pendingAutoRule = null
            pendingAutoCount = 0
            return active
        }
        if (wanted == pendingAutoRule) {
            pendingAutoCount++
        } else {
            pendingAutoRule = wanted
            pendingAutoCount = 1
        }
        if (pendingAutoCount >= RULE_SWITCH_FRAMES) {
            activeAutoRule = wanted
            pendingAutoRule = null
            pendingAutoCount = 0
            return wanted
        }
        return active
    }

    /** Glides the target box toward its new position instead of snapping. */
    private fun smoothGhost(raw: NormRect?): NormRect? {
        if (raw == null) {
            ghostSmoothed = null
            return null
        }
        val previous = ghostSmoothed
        val next = if (previous == null) raw else blend(previous, raw, GHOST_SMOOTHING)
        ghostSmoothed = next
        return next
    }

    /** The locked subject: same tracking id first, otherwise the box that overlaps it most. */
    private fun findLocked(subjects: List<Subject>): Subject? {
        val kind = lockedKind ?: return null
        val id = lockedId
        if (id != null) {
            subjects.firstOrNull { it.kind == kind && it.trackingId == id }?.let { return it }
        }
        val previous = smoothed ?: return null
        val best = subjects
            .filter { it.kind == kind }
            .maxByOrNull { overlap(previous.rect, it.rect) }
        if (best != null && overlap(previous.rect, best.rect) > MIN_OVERLAP_TO_FOLLOW) {
            lockedId = best.trackingId ?: lockedId
            return best
        }
        return null
    }

    /** Faces win (largest first). Otherwise the largest object that is not the whole frame. */
    private fun pickPrimary(subjects: List<Subject>): Subject? {
        val face = subjects
            .filter { it.kind == SubjectKind.FACE }
            .maxByOrNull { it.rect.area }
        if (face != null) return face
        return subjects
            .filter { it.kind == SubjectKind.OBJECT && it.rect.area < MAX_OBJECT_AREA }
            .maxByOrNull { it.rect.area }
    }

    /** Intersection over union of two boxes, 0 (no overlap) to 1 (identical). */
    private fun overlap(a: NormRect, b: NormRect): Float {
        val interWidth = min(a.right, b.right) - max(a.left, b.left)
        val interHeight = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (interWidth <= 0f || interHeight <= 0f) return 0f
        val inter = interWidth * interHeight
        val union = a.area + b.area - inter
        return if (union > 0f) inter / union else 0f
    }

    private fun blend(old: NormRect, new: NormRect, amount: Float): NormRect = NormRect(
        left = old.left + amount * (new.left - old.left),
        top = old.top + amount * (new.top - old.top),
        right = old.right + amount * (new.right - old.right),
        bottom = old.bottom + amount * (new.bottom - old.bottom),
    )

    private companion object {
        const val SMOOTHING = 0.4f
        const val GHOST_SMOOTHING = 0.35f
        const val MIN_OVERLAP_TO_SMOOTH = 0.15f
        const val MIN_OVERLAP_TO_FOLLOW = 0.2f
        const val FORGET_AFTER_MS = 800L
        const val LOCK_LOST_MS = 2500L
        const val MAX_OBJECT_AREA = 0.92f
        const val MOVING_SPEED = 0.5f // rad/s: panning, not just holding the phone
        const val RULE_SWITCH_FRAMES = 12 // about 1.2 s of analysis frames
    }
}
