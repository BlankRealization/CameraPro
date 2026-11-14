package com.camerapro.v1.pose

import android.content.Context
import android.util.Log
import org.json.JSONObject

/** A point in "head units": head height = 1, origin = the figure's head center, x to the viewer's right, y down. */
data class Pt(val x: Float, val y: Float)

/** One stick figure: joint name to position (head, neck, l/r Shoulder, Elbow, Wrist, Hip, Knee, Ankle). */
typealias Figure = Map<String, Pt>

enum class Framing { FULL, HALF, CLOSE }

/** A target pose from the bundled library. [scenes] holds SceneType names this pose suits. */
data class Pose(
    val id: String,
    val name: String,
    /** One short sentence telling the person what to do. */
    val cue: String,
    val people: Int,
    val framing: Framing,
    /** "rear", "selfie" or "both": which camera mode the pose was designed for. */
    val camera: String,
    val scenes: Set<String>,
    val moods: List<String>,
    /** 0 to 3: how popular the pose is right now. */
    val trend: Int,
    val figures: List<Figure>,
)

/** Loads the pose library bundled in the app (assets/poses/poses.json). Works offline; nothing is downloaded. */
object PoseLibrary {

    private const val TAG = "PoseLibrary"
    private const val FILE = "poses/poses.json"

    fun load(context: Context): List<Pose> {
        return try {
            val text = context.assets.open(FILE).bufferedReader().use { it.readText() }
            parse(text)
        } catch (e: Exception) {
            Log.e(TAG, "Could not load the pose library", e)
            emptyList()
        }
    }

    private fun parse(text: String): List<Pose> {
        val poses = ArrayList<Pose>()
        val array = JSONObject(text).getJSONArray("poses")
        for (i in 0 until array.length()) {
            try {
                poses.add(parsePose(array.getJSONObject(i)))
            } catch (e: Exception) {
                // One broken entry should not hide the rest of the library.
                Log.w(TAG, "Skipping pose #$i", e)
            }
        }
        return poses
    }

    private fun parsePose(o: JSONObject): Pose {
        val figures = ArrayList<Figure>()
        val figureArray = o.getJSONArray("figures")
        for (i in 0 until figureArray.length()) {
            val joints = figureArray.getJSONObject(i)
            val figure = HashMap<String, Pt>()
            for (name in joints.keys()) {
                val xy = joints.getJSONArray(name)
                figure[name] = Pt(xy.getDouble(0).toFloat(), xy.getDouble(1).toFloat())
            }
            figures.add(figure)
        }
        val scenes = HashSet<String>()
        val sceneArray = o.getJSONArray("scenes")
        for (i in 0 until sceneArray.length()) scenes.add(sceneArray.getString(i))
        val moods = ArrayList<String>()
        val moodArray = o.getJSONArray("moods")
        for (i in 0 until moodArray.length()) moods.add(moodArray.getString(i))

        return Pose(
            id = o.getString("id"),
            name = o.getString("name"),
            cue = o.getString("cue"),
            people = o.getInt("people"),
            framing = when (o.getString("framing")) {
                "close" -> Framing.CLOSE
                "half" -> Framing.HALF
                else -> Framing.FULL
            },
            camera = o.getString("camera"),
            scenes = scenes,
            moods = moods,
            trend = o.getInt("trend"),
            figures = figures,
        )
    }
}
