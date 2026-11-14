package com.camerapro.v1.pose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.camerapro.v1.analysis.NormRect

private val BONES = listOf(
    "neck" to "lShoulder", "neck" to "rShoulder",
    "lShoulder" to "lElbow", "lElbow" to "lWrist",
    "rShoulder" to "rElbow", "rElbow" to "rWrist",
    "lShoulder" to "lHip", "rShoulder" to "rHip", "lHip" to "rHip",
    "lHip" to "lKnee", "lKnee" to "lAnkle",
    "rHip" to "rKnee", "rKnee" to "rAnkle",
)

private val JOINT_DOTS = listOf("lElbow", "rElbow", "lWrist", "rWrist", "lKnee", "rKnee", "lAnkle", "rAnkle")

/** A face box is a bit smaller than the whole head, so the figure's head is drawn this much larger. */
private const val HEAD_FROM_FACE = 1.3f

/** Head height as a share of the preview height when no face is visible yet, by framing. */
private fun defaultHeadShare(framing: Framing) = when (framing) {
    Framing.FULL -> 0.11f
    Framing.HALF -> 0.20f
    Framing.CLOSE -> 0.30f
}

/** Where the head center sits (share of preview height) when no face is visible yet. */
private fun defaultHeadY(framing: Framing) = when (framing) {
    Framing.FULL -> 0.20f
    Framing.HALF -> 0.28f
    Framing.CLOSE -> 0.38f
}

/**
 * Draws the pose as a stick-figure outline, sized and placed from the faces in view.
 * One face: the first figure's head sits on it and the rest of the figure follows. Two people and two
 * faces: each figure's head sits on its own face (left face first). No face: a default spot,
 * so the person can step into the outline.
 */
fun DrawScope.drawPose(pose: Pose, faces: List<NormRect>, color: Color) {
    val w = size.width
    val h = size.height
    val sorted = faces.sortedBy { it.centerX }
    val base = sorted.firstOrNull()

    val baseUnit = base?.let { it.height * h * HEAD_FROM_FACE } ?: (defaultHeadShare(pose.framing) * h)
    val baseCenter = base?.let { Offset(it.centerX * w, it.centerY * h) }
        ?: Offset(w / 2f, defaultHeadY(pose.framing) * h)
    val firstHead = pose.figures.firstOrNull()?.get("head") ?: return

    val stroke = 3.dp.toPx()
    pose.figures.forEachIndexed { index, figure ->
        val own = if (pose.people >= 2 && sorted.size >= 2) sorted.getOrNull(index) else null
        val unit = own?.let { it.height * h * HEAD_FROM_FACE } ?: baseUnit
        val origin = own?.let { Offset(it.centerX * w, it.centerY * h) } ?: baseCenter
        val reference = if (own != null) (figure["head"] ?: firstHead) else firstHead

        fun at(name: String): Offset? {
            val joint = figure[name] ?: return null
            return Offset(origin.x + (joint.x - reference.x) * unit, origin.y + (joint.y - reference.y) * unit)
        }

        for ((from, to) in BONES) {
            val a = at(from) ?: continue
            val b = at(to) ?: continue
            drawLine(color, a, b, strokeWidth = stroke, cap = StrokeCap.Round)
        }

        val head = at("head")
        val neck = at("neck")
        if (head != null) {
            drawCircle(color, radius = unit * 0.5f, center = head, style = Stroke(width = stroke))
            if (neck != null) {
                drawLine(color, Offset(head.x, head.y + unit * 0.5f), neck, strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
        for (name in JOINT_DOTS) {
            at(name)?.let { drawCircle(color, radius = stroke * 0.9f, center = it) }
        }
    }
}
