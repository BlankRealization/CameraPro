package com.camerapro.v1.pose

import com.camerapro.v1.analysis.SceneType

/**
 * Ranks the bundled poses for what the camera sees right now. Everything is a simple score, so it
 * runs instantly and offline. It looks at how many people are in frame, selfie or rear camera, how
 * big the face is (which says how much of the body will be in the photo), and the scene.
 */
object PoseMatcher {

    /**
     * @param faceCount how many faces are in the frame
     * @param largestFaceArea area of the biggest face as a share of the frame (0 to 1), or null if no face
     * @return all poses, best first. Poses with the same score keep a fixed order.
     */
    fun rank(
        poses: List<Pose>,
        scene: SceneType,
        selfie: Boolean,
        faceCount: Int,
        largestFaceArea: Float?,
    ): List<Pose> {
        val wantedFraming = largestFaceArea?.let {
            when {
                it >= CLOSE_FACE_AREA -> Framing.CLOSE
                it >= HALF_FACE_AREA -> Framing.HALF
                else -> Framing.FULL
            }
        }
        return poses
            .map { it to score(it, scene, selfie, faceCount, wantedFraming) }
            .sortedWith(compareByDescending<Pair<Pose, Int>> { it.second }.thenBy { it.first.id })
            .map { it.first }
    }

    private fun score(pose: Pose, scene: SceneType, selfie: Boolean, faceCount: Int, wanted: Framing?): Int {
        var score = pose.trend * 3

        // How many people
        score += when {
            faceCount >= 2 -> if (pose.people >= 2) 30 else -20
            else -> if (pose.people == 1) 30 else -30
        }

        // Selfie or rear camera
        score += if (selfie) {
            if (pose.camera == "selfie") 25 else if (pose.camera == "both") 5 else -5
        } else {
            if (pose.camera == "selfie") -10 else 0
        }

        // Face size says how much of the body is in the shot
        if (wanted != null) {
            score += when {
                pose.framing == wanted -> 20
                kotlin.math.abs(pose.framing.ordinal - wanted.ordinal) == 1 -> 8
                else -> 0
            }
        }

        if (scene.name in pose.scenes) score += 15
        return score
    }

    /**
     * Calculates a 0..100 alignment match score between detected ML Kit body landmarks and target Pose.
     */
    fun calculatePoseMatchScore(
        pose: Pose,
        landmarks: List<com.camerapro.v1.analysis.DetectedBodyLandmark>
    ): Int? {
        if (landmarks.size < 6) return null
        val figure = pose.figures.firstOrNull() ?: return null

        val landmarkMap = landmarks.associateBy { it.type }
        val lShoulder = landmarkMap[11]
        val rShoulder = landmarkMap[12]
        if (lShoulder == null || rShoulder == null) return null

        var matchSum = 0f
        var count = 0

        fun checkJoint(landmarkType: Int, targetName: String) {
            val lm = landmarkMap[landmarkType] ?: return
            val targetPt = figure[targetName] ?: return
            val dx = lm.x - (0.5f + targetPt.x * 0.15f)
            val dy = lm.y - (0.3f + targetPt.y * 0.15f)
            val dist = kotlin.math.hypot(dx, dy)
            val jointScore = (1f - kotlin.math.min(dist / 0.35f, 1f)) * 100f
            matchSum += jointScore
            count++
        }

        checkJoint(13, "lElbow")
        checkJoint(14, "rElbow")
        checkJoint(15, "lWrist")
        checkJoint(16, "rWrist")
        checkJoint(25, "lKnee")
        checkJoint(26, "rKnee")

        return if (count > 0) (matchSum / count).toInt().coerceIn(0, 100) else null
    }

    private const val CLOSE_FACE_AREA = 0.10f
    private const val HALF_FACE_AREA = 0.03f
}
