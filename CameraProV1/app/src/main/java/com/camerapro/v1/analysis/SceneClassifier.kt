package com.camerapro.v1.analysis

/**
 * Turns ML Kit image labels (like "Sky", "Mountain", "Pizza") plus detected faces into one of the
 * scene types the camera logic understands. Rule-based on purpose: easy to read and tune on a real phone.
 */
object SceneClassifier {

    private val portraitWords = setOf(
        "person", "face", "selfie", "smile", "hair", "hairstyle", "skin", "beard", "forehead",
        "chin", "cheek", "eyebrow", "eyelash", "lip", "nose", "head", "neck", "eye", "jaw", "facial",
    )

    private val landscapeWords = setOf(
        "sky", "cloud", "mountain", "hill", "beach", "sea", "ocean", "lake", "river", "water",
        "sunset", "sunrise", "horizon", "field", "grassland", "meadow", "forest", "valley", "coast",
        "shore", "desert", "snow", "cliff", "waterfall", "nature", "wilderness", "landscape",
        "tree", "plant", "grass", "flower", "plateau", "dune", "lagoon",
    )

    private val foodWords = setOf(
        "food", "dish", "cuisine", "meal", "dessert", "fruit", "vegetable", "drink", "coffee",
        "cake", "pizza", "bread", "breakfast", "lunch", "dinner", "snack", "tableware", "plate",
        "baking", "ingredient", "produce", "recipe", "cup", "tea", "bowl",
    )

    private val architectureWords = setOf(
        "building", "architecture", "skyscraper", "tower", "bridge", "house", "facade", "castle",
        "temple", "church", "mosque", "monument", "city", "landmark", "palace", "street", "urban",
        "roof", "window", "arch", "column", "skyline", "cathedral", "fort", "gate",
    )

    private const val MIN_SCENE_SCORE = 0.45f
    private const val MIN_PORTRAIT_LABEL_SCORE = 0.7f
    private const val MIN_FACE_AREA = 0.01f // a face covering at least 1% of the frame

    fun classify(labels: List<Pair<String, Float>>, subjects: List<Subject>): SceneType {
        val largestFace = subjects
            .filter { it.kind == SubjectKind.FACE }
            .maxOfOrNull { it.rect.area } ?: 0f
        if (largestFace >= MIN_FACE_AREA) return SceneType.PORTRAIT

        if (score(labels, portraitWords) >= MIN_PORTRAIT_LABEL_SCORE) return SceneType.PORTRAIT

        val candidates = listOf(
            SceneType.LANDSCAPE to score(labels, landscapeWords),
            SceneType.FOOD to score(labels, foodWords),
            SceneType.ARCHITECTURE to score(labels, architectureWords),
        )
        val best = candidates.maxByOrNull { it.second }
        return if (best != null && best.second >= MIN_SCENE_SCORE) best.first else SceneType.GENERAL
    }

    /** Sum of the confidences of every label that matches one of the words. */
    private fun score(labels: List<Pair<String, Float>>, words: Set<String>): Float {
        var total = 0f
        for ((text, confidence) in labels) {
            if (matches(text, words)) total += confidence
        }
        return total
    }

    // Whole-word match, so "street" does not count as "tree".
    private fun matches(label: String, words: Set<String>): Boolean {
        val lower = label.lowercase()
        if (lower in words) return true
        return lower.split(' ', '-', '_', '/').any { it in words }
    }
}
