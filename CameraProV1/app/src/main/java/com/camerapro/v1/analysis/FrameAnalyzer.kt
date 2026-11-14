package com.camerapro.v1.analysis

import android.graphics.Rect
import android.media.Image
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Runs on every camera analysis frame: finds faces and objects, and (about once a second)
 * labels the scene. Results are published to [results]. Everything runs on one background thread,
 * so the preview is never blocked.
 *
 * Coordinates: the analysis image and the preview share the same 4:3 shape (3:4 when the phone is
 * upright, 4:3 when it is sideways), so a box normalized against the upright analysis image lands on
 * the same spot of the preview. The image is turned by [displayRotationDegrees] and, for the front
 * camera, mirrored to match what the preview shows.
 */
class FrameAnalyzer : ImageAnalysis.Analyzer {

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    /** Pass this to the camera controller so analysis and result handling share one thread. */
    val executor: Executor = Executor { runnable ->
        try {
            worker.execute(runnable)
        } catch (e: RejectedExecutionException) {
            Log.d(TAG, "Analyzer is closed; dropping a task")
        }
    }

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .build()
    )

    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
    )

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(LABEL_CONFIDENCE)
            .build()
    )

    private val poseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    private val barcodeScanner = BarcodeScanning.getClient()

    private val _results = MutableStateFlow<FrameAnalysis?>(null)
    val results: StateFlow<FrameAnalysis?> = _results.asStateFlow()

    @Volatile
    private var closed = false

    /**
     * Degrees the camera image must be turned clockwise to look upright on the screen as it is
     * shown now (from CameraInfo.getSensorRotationDegrees with the display rotation). Set by the UI.
     * -1 means unknown: the rotation CameraX attaches to each frame is used instead.
     */
    @Volatile
    var displayRotationDegrees: Int = -1

    /** True for the front camera, whose preview is shown mirrored; boxes are flipped to match. */
    @Volatile
    var mirrorX: Boolean = false

    // The fields below are only touched from the worker thread.
    private var lastLabelStartMs = 0L
    private var lastLabels: List<Pair<String, Float>> = emptyList()
    private var stableScene = SceneType.GENERAL
    private var pendingScene = SceneType.GENERAL
    private var pendingCount = 0

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (closed || mediaImage == null) {
            imageProxy.close()
            return
        }

        try {
            startAnalysis(imageProxy, mediaImage)
        } catch (e: Exception) {
            // Never leave a frame open, or the camera stops delivering new ones.
            Log.e(TAG, "Could not start frame analysis", e)
            imageProxy.close()
        }
    }

    private fun startAnalysis(imageProxy: ImageProxy, mediaImage: Image) {
        val startMs = SystemClock.elapsedRealtime()
        val rotation = displayRotationDegrees.takeIf { it >= 0 } ?: imageProxy.imageInfo.rotationDegrees
        val mirror = mirrorX
        val sideways = rotation == 90 || rotation == 270
        val frameWidth = (if (sideways) imageProxy.height else imageProxy.width).toFloat()
        val frameHeight = (if (sideways) imageProxy.width else imageProxy.height).toFloat()

        val input = InputImage.fromMediaImage(mediaImage, rotation)
        val luma = sampleLuma(imageProxy, rotation, mirror)

        val faceTask = faceDetector.process(input)
        val objectTask = objectDetector.process(input)
        val poseTask = poseDetector.process(input)
        val barcodeTask = barcodeScanner.process(input)
        val labelDue = startMs - lastLabelStartMs >= LABEL_INTERVAL_MS
        val labelTask = if (labelDue) {
            lastLabelStartMs = startMs
            labeler.process(input)
        } else {
            null
        }

        val tasks: List<Task<*>> = listOfNotNull<Task<*>>(faceTask, objectTask, poseTask, barcodeTask, labelTask)

        Tasks.whenAllComplete(tasks).addOnCompleteListener(executor) {
            try {
                if (closed) return@addOnCompleteListener

                val subjects = ArrayList<Subject>()
                if (faceTask.isSuccessful) {
                    for (face in faceTask.result) {
                        toNormRect(face.boundingBox, frameWidth, frameHeight, mirror)?.let {
                            subjects.add(Subject(it, SubjectKind.FACE, face.trackingId))
                        }
                    }
                }
                if (objectTask.isSuccessful) {
                    for (detected in objectTask.result) {
                        toNormRect(detected.boundingBox, frameWidth, frameHeight, mirror)?.let {
                            subjects.add(Subject(it, SubjectKind.OBJECT, detected.trackingId))
                        }
                    }
                }

                val poseLandmarks = ArrayList<DetectedBodyLandmark>()
                if (poseTask.isSuccessful) {
                    for (lm in poseTask.result.allPoseLandmarks) {
                        val normX = if (mirror) 1f - (lm.position.x / frameWidth) else lm.position.x / frameWidth
                        val normY = lm.position.y / frameHeight
                        poseLandmarks.add(
                            DetectedBodyLandmark(
                                type = lm.landmarkType,
                                x = normX.coerceIn(0f, 1f),
                                y = normY.coerceIn(0f, 1f),
                                likelihood = lm.inFrameLikelihood
                            )
                        )
                    }
                }

                val barcodes = ArrayList<BarcodeResult>()
                if (barcodeTask.isSuccessful) {
                    for (bc in barcodeTask.result) {
                        val display = bc.displayValue ?: bc.rawValue ?: ""
                        val url = bc.url?.url
                        if (display.isNotEmpty()) {
                            barcodes.add(BarcodeResult(displayValue = display, url = url))
                        }
                    }
                }

                if (labelTask != null && labelTask.isSuccessful) {
                    lastLabels = labelTask.result.map { it.text to it.confidence }
                }

                val candidate = SceneClassifier.classify(lastLabels, subjects)
                val scene = stabilize(candidate)
                val top = lastLabels.maxByOrNull { it.second }
                val topText = top?.let { "${it.first} ${"%.2f".format(Locale.US, it.second)}" }

                _results.value = FrameAnalysis(
                    subjects = subjects,
                    scene = scene,
                    topLabel = topText,
                    analysisMs = SystemClock.elapsedRealtime() - startMs,
                    luma = luma,
                    histogram = luma?.computeHistogram(),
                    poseLandmarks = poseLandmarks,
                    barcodes = barcodes,
                )
            } finally {
                imageProxy.close()
            }
        }
    }

    /**
     * Samples the brightness (Y plane) on a coarse upright grid. The camera buffer is stored in
     * sensor orientation, so each upright position is mapped back to a sensor pixel by rotation.
     */
    private fun sampleLuma(imageProxy: ImageProxy, rotation: Int, mirror: Boolean): LumaGrid? {
        val plane = imageProxy.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = imageProxy.width
        val height = imageProxy.height
        val values = IntArray(LUMA_COLS * LUMA_ROWS)

        for (row in 0 until LUMA_ROWS) {
            val v = (row + 0.5f) / LUMA_ROWS
            for (col in 0 until LUMA_COLS) {
                val shownU = (col + 0.5f) / LUMA_COLS
                val u = if (mirror) 1f - shownU else shownU
                val (sx, sy) = when (rotation) {
                    90 -> v to (1f - u)
                    180 -> (1f - u) to (1f - v)
                    270 -> (1f - v) to u
                    else -> u to v
                }
                val x = (sx * width).toInt().coerceIn(0, width - 1)
                val y = (sy * height).toInt().coerceIn(0, height - 1)
                values[row * LUMA_COLS + col] = buffer.get(y * rowStride + x * pixelStride).toInt() and 0xFF
            }
        }
        return LumaGrid(LUMA_COLS, LUMA_ROWS, values)
    }

    /** Only switch scene after the new one has been seen twice in a row, to avoid flicker. */
    private fun stabilize(candidate: SceneType): SceneType {
        if (candidate == stableScene) {
            pendingCount = 0
            return stableScene
        }
        if (candidate == pendingScene) {
            pendingCount++
        } else {
            pendingScene = candidate
            pendingCount = 1
        }
        if (pendingCount >= SCENE_CONFIRM_FRAMES) {
            stableScene = candidate
            pendingCount = 0
        }
        return stableScene
    }

    private fun toNormRect(box: Rect, width: Float, height: Float, mirror: Boolean): NormRect? {
        val left = (box.left / width).coerceIn(0f, 1f)
        val top = (box.top / height).coerceIn(0f, 1f)
        val right = (box.right / width).coerceIn(0f, 1f)
        val bottom = (box.bottom / height).coerceIn(0f, 1f)
        return if (right - left > MIN_BOX_SIZE && bottom - top > MIN_BOX_SIZE) {
            if (mirror) NormRect(1f - right, top, 1f - left, bottom) else NormRect(left, top, right, bottom)
        } else {
            null
        }
    }

    /** Drops the last result, for example after switching cameras, so old boxes are not shown on the new view. */
    fun clearResults() {
        _results.value = null
    }

    fun close() {
        closed = true
        faceDetector.close()
        objectDetector.close()
        labeler.close()
        poseDetector.close()
        barcodeScanner.close()
        worker.shutdown()
    }

    private companion object {
        const val TAG = "FrameAnalyzer"
        const val LABEL_CONFIDENCE = 0.5f
        const val LABEL_INTERVAL_MS = 1000L
        const val SCENE_CONFIRM_FRAMES = 2
        const val MIN_BOX_SIZE = 0.01f
        const val LUMA_COLS = 24
        const val LUMA_ROWS = 32
    }
}
