package com.camerapro.v1.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.camerapro.v1.analysis.FrameAnalyzer
import com.camerapro.v1.analysis.SceneType
import com.camerapro.v1.analysis.SubjectKind
import com.camerapro.v1.analysis.Subject
import com.camerapro.v1.camera.CameraCapabilityChecker
import com.camerapro.v1.camera.ExposureAutopilot
import com.camerapro.v1.camera.Sensors
import com.camerapro.v1.camera.SubjectMetering
import com.camerapro.v1.camera.ZoomPlanner
import com.camerapro.v1.composition.CompositionRule
import com.camerapro.v1.composition.Guidance
import com.camerapro.v1.composition.GuidanceTracker
import com.camerapro.v1.composition.RuleChoice
import com.camerapro.v1.composition.ZoomHint
import com.camerapro.v1.pose.Pose
import com.camerapro.v1.pose.PoseLibrary
import com.camerapro.v1.pose.PoseMatcher
import com.camerapro.v1.pose.drawPose
import com.camerapro.v1.quality.QualityMonitor
import com.camerapro.v1.quality.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

private const val TAG = "CameraScreen"
private val LevelGreen = Color(0xFF4CAF50)
private val AmberYellow = Color(0xFFFFC107)
private val PoseColor = Color(0xFF4DD0E1).copy(alpha = 0.9f)

/** The shutter waits (briefly) while the phone turns faster than this, in radians per second. */
private const val STEADY_LIMIT = 0.35f
private const val MAX_STEADY_WAIT_MS = 600

/** Room kept for the controls: beside the preview in landscape, below it in portrait. */
private val SIDE_PANEL_MIN_WIDTH = 220.dp
private val BOTTOM_BAR_MIN_HEIGHT = 190.dp

enum class AspectRatioChoice(val label: String, val ratio: Float) {
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_1_1("1:1", 1f),
    RATIO_4_5("4:5", 4f / 5f),
    RATIO_9_16("9:16", 9f / 16f);

    fun next(): AspectRatioChoice = entries[(ordinal + 1) % entries.size]
}

/** Holds the preview view so camera code can turn a spot on screen into a metering point. */
private class PreviewRef {
    var view: PreviewView? = null
}

/** Entry point: asks for the camera permission, then shows the camera. */
@Composable
fun CameraApp(sensors: Sensors) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // Re-check when the user comes back from the system settings screen.
    val hostActivity = remember(context) { context.findComponentActivity() }
    DisposableEffect(hostActivity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        hostActivity.lifecycle.addObserver(observer)
        onDispose { hostActivity.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        CameraScreen(sensors)
    } else {
        PermissionScreen(
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
                context.startActivity(intent)
            },
        )
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Camera Pro V1 needs the camera to show a live preview and take photos.",
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequest, modifier = Modifier.padding(top = 24.dp)) {
            Text("Allow camera")
        }
        TextButton(onClick = onOpenSettings) {
            Text("Open app settings")
        }
    }
}

@Composable
private fun CameraScreen(sensors: Sensors) {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val scope = rememberCoroutineScope()
    val hostView = LocalView.current

    val frameAnalyzer = remember { FrameAnalyzer() }
    val cameraController = remember(context, frameAnalyzer) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            setImageAnalysisAnalyzer(frameAnalyzer.executor, frameAnalyzer)
            // Taps are handled below: tap a subject to lock it, or tap empty space to focus there.
            isTapToFocusEnabled = false
        }
    }
    DisposableEffect(cameraController) {
        cameraController.bindToLifecycle(activity)
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            frameAnalyzer.close()
        }
    }

    val capabilities = remember(context) { CameraCapabilityChecker.check(context) }

    val roll by sensors.level.rollDegrees.collectAsState()
    val analysis by frameAnalyzer.results.collectAsState()

    val tracker = remember { GuidanceTracker() }
    val qualityMonitor = remember { QualityMonitor() }
    val autopilot = remember { ExposureAutopilot() }
    val metering = remember { SubjectMetering() }
    val previewRef = remember { PreviewRef() }

    var guidance by remember { mutableStateOf(Guidance.EMPTY) }
    var warnings by remember { mutableStateOf<List<Warning>>(emptyList()) }
    var evText by remember { mutableStateOf<String?>(null) }

    var showGrid by remember { mutableStateOf(true) }
    var showGuide by remember { mutableStateOf(true) }
    var autoOn by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    var ruleChoice by remember { mutableStateOf(RuleChoice.AUTO) }
    var gridRule by remember { mutableStateOf(CompositionRule.THIRDS) }
    var frontCamera by remember { mutableStateOf(false) }

    var flashModeState by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var isTorchOn by remember { mutableStateOf(false) }
    var aspectRatioChoice by remember { mutableStateOf(AspectRatioChoice.RATIO_4_3) }
    var timerChoiceSeconds by remember { mutableIntStateOf(0) }
    var timerCountdown by remember { mutableIntStateOf(0) }
    var isCountingDown by remember { mutableStateOf(false) }
    var isVideoMode by remember { mutableStateOf(false) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<androidx.camera.video.Recording?>(null) }
    var showHistogram by remember { mutableStateOf(false) }
    var showManualEvSlider by remember { mutableStateOf(false) }
    var manualEvIndex by remember { mutableIntStateOf(0) }
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var showExifDialog by remember { mutableStateOf(false) }

    var poses by remember { mutableStateOf<List<Pose>>(emptyList()) }
    var poseOn by remember { mutableStateOf(false) }
    var currentPose by remember { mutableStateOf<Pose?>(null) }
    LaunchedEffect(Unit) {
        poses = withContext(Dispatchers.IO) { PoseLibrary.load(context) }
    }

    // The screen rotation. Changing it does not recreate the activity, so listen for it directly.
    var displayRotation by remember { mutableIntStateOf(hostView.display?.rotation ?: Surface.ROTATION_0) }
    DisposableEffect(context, hostView) {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                displayRotation = hostView.display?.rotation ?: Surface.ROTATION_0
            }
        }
        displayManager.registerDisplayListener(listener, null)
        displayRotation = hostView.display?.rotation ?: displayRotation
        onDispose { displayManager.unregisterDisplayListener(listener) }
    }

    // Tells the level sensor and the frame analyzer how the screen and camera are oriented right now.
    val applyOrientation: () -> Unit = {
        sensors.level.displayRotation = displayRotation
        frameAnalyzer.mirrorX = frontCamera
        val degrees = try {
            cameraController.cameraInfo?.getSensorRotationDegrees(displayRotation)
        } catch (e: RuntimeException) {
            null
        }
        frameAnalyzer.displayRotationDegrees = degrees ?: -1
    }
    LaunchedEffect(displayRotation, frontCamera) { applyOrientation() }

    LaunchedEffect(statusText) {
        if (statusText != null) {
            delay(1500)
            statusText = null
        }
    }

    // Runs for every analyzed frame (about 10 times a second) and whenever Auto or the rule is changed.
    LaunchedEffect(analysis, autoOn, ruleChoice) {
        applyOrientation()
        val now = SystemClock.elapsedRealtime()
        val g = tracker.update(
            analysis = analysis,
            rollDegrees = sensors.level.rollDegrees.value,
            nowMs = now,
            angularSpeed = sensors.motion.angularSpeed.value,
            ruleChoice = ruleChoice,
        )
        guidance = g
        if (ruleChoice == RuleChoice.AUTO) {
            g.rule?.let { gridRule = it }
        } else {
            gridRule = when (ruleChoice) {
                RuleChoice.GOLDEN -> CompositionRule.GOLDEN
                RuleChoice.CENTER -> CompositionRule.CENTER
                else -> CompositionRule.THIRDS
            }
        }
        if (tracker.consumeLostLock()) statusText = "Lost the locked subject"

        warnings = qualityMonitor.update(analysis, g.subject?.rect, sensors.motion.angularSpeed.value)

        metering.update(cameraController, previewRef.view, g.subject, g.locked, autoOn, now)
        if (autoOn) {
            autopilot.update(cameraController, analysis, g.subject?.rect, g.subject?.kind, now)
        }
        evText = autopilot.currentEv(cameraController)?.let { "EV ${"%+.1f".format(Locale.US, it)}" }
    }

    // Turning Auto off puts exposure compensation back to neutral.
    LaunchedEffect(autoOn) {
        if (!autoOn) autopilot.reset(cameraController)
    }

    val handleTap = { nx: Float, ny: Float ->
        if (!showGuide) {
            metering.tapFocus(cameraController, previewRef.view, nx, ny)
        } else {
            val hit = analysis?.subjects.orEmpty()
                .filter { nx >= it.rect.left && nx <= it.rect.right && ny >= it.rect.top && ny <= it.rect.bottom }
                .minByOrNull { it.rect.area }
            val now = SystemClock.elapsedRealtime()
            if (hit != null && tracker.isLockedTo(hit)) {
                tracker.unlock()
                statusText = "Subject released"
            } else if (hit != null) {
                tracker.lock(hit, now)
                statusText = "Subject locked"
            } else {
                // Tap on empty space: let go of any lock and focus right there.
                tracker.unlock()
                metering.tapFocus(cameraController, previewRef.view, nx, ny)
            }
        }
        Unit
    }
    val currentTap = rememberUpdatedState(handleTap)

    val cycleRule: () -> Unit = {
        ruleChoice = ruleChoice.next()
        statusText = "Rule: ${ruleChoice.label}"
    }

    val flipCamera: () -> Unit = {
        val wantFront = !frontCamera
        val selector = if (wantFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            if (cameraController.hasCamera(selector)) {
                cameraController.cameraSelector = selector
                frontCamera = wantFront
                // Everything learned about the old camera is wrong for the new one.
                tracker.reset()
                metering.reset()
                autopilot.reset(cameraController)
                frameAnalyzer.mirrorX = wantFront
                frameAnalyzer.clearResults()
                guidance = Guidance.EMPTY
                warnings = emptyList()
                statusText = if (wantFront) "Selfie camera" else "Rear camera"
            } else {
                statusText = if (wantFront) "No front camera found" else "No rear camera found"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not switch camera", e)
            statusText = "Could not switch camera"
        }
    }

    // Moves to the next (step = 1) or previous (step = -1) pose. The list is ranked for what the camera
    // sees at this moment, but the pose on screen only changes when the user asks, so it never jumps by itself.
    val stepPose: (Int) -> Unit = { step ->
        if (poses.isEmpty()) {
            statusText = "Pose library not found"
        } else {
            val faces = analysis?.subjects.orEmpty().filter { it.kind == SubjectKind.FACE }
            val ranked = PoseMatcher.rank(
                poses = poses,
                scene = analysis?.scene ?: SceneType.GENERAL,
                selfie = frontCamera,
                faceCount = faces.size,
                largestFaceArea = faces.maxOfOrNull { it.rect.area },
            )
            val index = ranked.indexOfFirst { it.id == currentPose?.id }
            val next = if (index < 0) 0 else Math.floorMod(index + step, ranked.size)
            currentPose = ranked[next]
            poseOn = true
        }
    }
    val togglePose: () -> Unit = {
        if (poseOn) {
            poseOn = false
        } else if (currentPose != null) {
            poseOn = true
        } else {
            stepPose(1)
        }
    }

    // Faces the pose outline is placed on: the smoothed main face for one person, the two biggest for two.
    val rawFaces = analysis?.subjects.orEmpty()
        .filter { it.kind == SubjectKind.FACE }
        .sortedByDescending { it.rect.area }
        .take(2)
        .map { it.rect }
    val smoothFace = guidance.subject?.takeIf { it.kind == SubjectKind.FACE }?.rect
    val poseFaces = if ((currentPose?.people ?: 1) >= 2) rawFaces else listOfNotNull(smoothFace ?: rawFaces.firstOrNull())

    val cycleFlash: () -> Unit = {
        when {
            !isTorchOn && flashModeState == ImageCapture.FLASH_MODE_OFF -> {
                flashModeState = ImageCapture.FLASH_MODE_AUTO
                cameraController.imageCaptureFlashMode = ImageCapture.FLASH_MODE_AUTO
                cameraController.enableTorch(false)
                isTorchOn = false
                statusText = "Flash: Auto"
            }
            !isTorchOn && flashModeState == ImageCapture.FLASH_MODE_AUTO -> {
                flashModeState = ImageCapture.FLASH_MODE_ON
                cameraController.imageCaptureFlashMode = ImageCapture.FLASH_MODE_ON
                cameraController.enableTorch(false)
                isTorchOn = false
                statusText = "Flash: On"
            }
            !isTorchOn && flashModeState == ImageCapture.FLASH_MODE_ON -> {
                flashModeState = ImageCapture.FLASH_MODE_OFF
                cameraController.imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
                cameraController.enableTorch(true)
                isTorchOn = true
                statusText = "Flash: Torch"
            }
            else -> {
                flashModeState = ImageCapture.FLASH_MODE_OFF
                cameraController.imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
                cameraController.enableTorch(false)
                isTorchOn = false
                statusText = "Flash: Off"
            }
        }
    }

    val cycleTimer: () -> Unit = {
        timerChoiceSeconds = when (timerChoiceSeconds) {
            0 -> 3
            3 -> 5
            5 -> 10
            else -> 0
        }
        statusText = if (timerChoiceSeconds > 0) "Timer: ${timerChoiceSeconds}s" else "Timer: Off"
    }

    val cycleAspectRatio: () -> Unit = {
        aspectRatioChoice = aspectRatioChoice.next()
        statusText = "Ratio: ${aspectRatioChoice.label}"
    }

    val toggleVideoMode: () -> Unit = {
        val newMode = !isVideoMode
        if (isRecordingVideo) {
            activeRecording?.stop()
            activeRecording = null
            isRecordingVideo = false
        }
        isVideoMode = newMode
        com.camerapro.v1.camera.VideoHelper.enableVideoMode(cameraController, newMode)
        statusText = if (newMode) "Video Mode" else "Photo Mode"
    }

    val onShutter: () -> Unit = {
        if (isVideoMode) {
            if (isRecordingVideo) {
                activeRecording?.stop()
                activeRecording = null
                isRecordingVideo = false
                statusText = "Video recording stopped"
            } else {
                scope.launch {
                    if (timerChoiceSeconds > 0) {
                        isCountingDown = true
                        val toneG = try { android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80) } catch (e: Exception) { null }
                        for (i in timerChoiceSeconds downTo 1) {
                            timerCountdown = i
                            toneG?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
                            delay(1000)
                        }
                        isCountingDown = false
                        toneG?.release()
                    }
                    isRecordingVideo = true
                    statusText = "Recording video..."
                    val rec = com.camerapro.v1.camera.VideoHelper.startVideoRecording(
                        context = context,
                        controller = cameraController,
                        onSaved = { uri ->
                            lastCapturedUri = uri
                            isRecordingVideo = false
                            statusText = "Video saved"
                        },
                        onError = { err ->
                            isRecordingVideo = false
                            statusText = "Video error: $err"
                        }
                    )
                    activeRecording = rec
                }
            }
        } else {
            scope.launch {
                if (timerChoiceSeconds > 0) {
                    isCountingDown = true
                    val toneG = try { android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80) } catch (e: Exception) { null }
                    for (i in timerChoiceSeconds downTo 1) {
                        timerCountdown = i
                        toneG?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
                        delay(1000)
                    }
                    isCountingDown = false
                    toneG?.release()
                }
                if (sensors.motion.isAvailable && sensors.motion.angularSpeed.value > STEADY_LIMIT) {
                    statusText = "Waiting for steady..."
                    var waited = 0
                    while (waited < MAX_STEADY_WAIT_MS && sensors.motion.angularSpeed.value > STEADY_LIMIT) {
                        delay(50)
                        waited += 50
                    }
                }
                takePhoto(context, cameraController) { saved, uri ->
                    if (saved && uri != null) {
                        lastCapturedUri = uri
                    }
                    statusText = if (saved) "Photo saved" else "Could not save photo"
                }
            }
        }
    }

    // One layout tree for both orientations: the preview stays in the same place in the tree, so
    // rotating the phone only changes sizes and where the buttons sit, and never rebuilds the camera view.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding(),
    ) {
        val landscape = maxWidth > maxHeight
        val topBarHeight = 48.dp

        val previewWidth: Dp
        val previewHeight: Dp
        if (landscape) {
            previewWidth = minOf(maxHeight * 4f / 3f, maxWidth - SIDE_PANEL_MIN_WIDTH).coerceAtLeast(0.dp)
            previewHeight = previewWidth * 3f / 4f
        } else {
            previewHeight = minOf(maxWidth * 4f / 3f, maxHeight - topBarHeight - BOTTOM_BAR_MIN_HEIGHT).coerceAtLeast(0.dp)
            previewWidth = previewHeight * 3f / 4f
        }

        val previewPlacement = if (landscape) {
            Modifier.align(Alignment.CenterStart)
        } else {
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = topBarHeight)
        }

        // Preview with overlays on top
        Box(modifier = previewPlacement.size(previewWidth, previewHeight)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        controller = cameraController
                        previewRef.view = this

                        val view = this
                        val detector = GestureDetector(
                            ctx,
                            object : GestureDetector.SimpleOnGestureListener() {
                                override fun onSingleTapUp(e: MotionEvent): Boolean {
                                    if (view.width > 0 && view.height > 0) {
                                        currentTap.value(e.x / view.width, e.y / view.height)
                                    }
                                    // Not consumed, so pinch-to-zoom keeps working.
                                    return false
                                }
                            },
                        )
                        // Without this, the second of two quick taps is treated as a double-tap and ignored.
                        detector.setOnDoubleTapListener(null)
                        setOnTouchListener { _, event ->
                            detector.onTouchEvent(event)
                            false
                        }
                    }
                },
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                if (showGrid) {
                    val gridColor = Color.White.copy(alpha = 0.35f)
                    val stroke = 1.dp.toPx()
                    for (fraction in gridRule.gridLines) {
                        val x = size.width * fraction
                        val y = size.height * fraction
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), stroke)
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), stroke)
                    }
                }

                val pose = currentPose
                if (poseOn && pose != null) {
                    drawPose(pose, poseFaces, PoseColor)
                }

                if (showGuide) {
                    drawGuidance(guidance)
                }

                // Level line: fixed reference ticks plus a line that stays parallel to the horizon.
                val half = size.minDimension * 0.14f
                val gap = size.minDimension * 0.04f
                val isLevel = abs(roll) <= 1f
                val lineColor = if (isLevel) LevelGreen else Color.White.copy(alpha = 0.9f)
                val refColor = Color.White.copy(alpha = 0.6f)
                val strokeWidth = 3.dp.toPx()

                drawLine(
                    refColor, Offset(cx - half - gap - 18.dp.toPx(), cy), Offset(cx - half - gap, cy),
                    strokeWidth, StrokeCap.Round
                )
                drawLine(
                    refColor, Offset(cx + half + gap, cy), Offset(cx + half + gap + 18.dp.toPx(), cy),
                    strokeWidth, StrokeCap.Round
                )

                if (sensors.level.isAvailable) {
                    rotate(degrees = -roll, pivot = Offset(cx, cy)) {
                        drawLine(
                            lineColor, Offset(cx - half, cy), Offset(cx + half, cy),
                            strokeWidth, StrokeCap.Round
                        )
                    }
                }
            }

            if (showGuide) {
                // Scene readout (top left): scene type, rule, strongest label, time per frame, exposure
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Scene: ${analysis?.scene?.label ?: "..."}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val ruleLabel = if (ruleChoice == RuleChoice.AUTO) {
                        "${gridRule.label} · auto"
                    } else {
                        gridRule.label
                    }
                    Text(
                        text = "Rule: $ruleLabel",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                    )
                    val debugLine = listOfNotNull(
                        analysis?.topLabel,
                        analysis?.let { "${it.analysisMs} ms" },
                    ).joinToString(" · ").ifEmpty { "analyzing..." }
                    Text(
                        text = debugLine,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                    )
                    evText?.let { ev ->
                        Text(
                            text = if (autoOn) "$ev · auto" else ev,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                        )
                    }
                }

                // Composition score (top right)
                val scoreColor = when {
                    guidance.score >= 75 -> LevelGreen
                    guidance.score >= 50 -> AmberYellow
                    else -> Color.White
                }
                Text(
                    text = "Score ${guidance.score}",
                    color = scoreColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )

                // Warnings, lock state and advice (bottom center)
                val zoomTappable = guidance.subject != null &&
                    guidance.zoomHint != ZoomHint.NONE &&
                    !guidance.needsStraightening
                val adviceText = if (zoomTappable) "${guidance.message} · tap to zoom" else guidance.message
                val zoomAction: () -> Unit = {
                    val target = guidance.subject
                    statusText = if (target != null) zoomToFit(cameraController, target) else null
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // QR Code floating chip
                    val firstBarcode = analysis?.barcodes?.firstOrNull()
                    if (firstBarcode != null) {
                        InfoChip(
                            text = "QR: ${firstBarcode.displayValue}",
                            color = Color.Cyan,
                            onClick = {
                                val targetUrl = firstBarcode.url ?: if (firstBarcode.displayValue.startsWith("http")) firstBarcode.displayValue else null
                                if (targetUrl != null) {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                                    } catch (e: Exception) {
                                        statusText = "Cannot open link"
                                    }
                                }
                            }
                        )
                    }

                    // In landscape the preview is short, so show fewer chips.
                    warnings.take(if (landscape) 1 else 2).forEach { warning ->
                        InfoChip(text = warning.text, color = AmberYellow)
                    }
                    if (guidance.locked) {
                        InfoChip(
                            text = "Subject locked · tap to release",
                            onClick = { tracker.unlock() },
                        )
                    }
                    InfoChip(
                        text = adviceText,
                        color = if (guidance.isAligned) LevelGreen else Color.White,
                        onClick = if (zoomTappable) zoomAction else null,
                    )
                }
            }

            // Selfie ring light frame overlay
            if (frontCamera && (flashModeState == ImageCapture.FLASH_MODE_ON || isTorchOn)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(16.dp, Color(0xFFFFF8E7).copy(alpha = 0.85f))
                )
            }

            // Interactive Live Pose Match Score Badge
            val livePoseScore = remember(currentPose, analysis?.poseLandmarks) {
                currentPose?.let { p ->
                    analysis?.poseLandmarks?.let { lm ->
                        PoseMatcher.calculatePoseMatchScore(p, lm)
                    }
                }
            }
            if (poseOn && currentPose != null && livePoseScore != null) {
                val badgeColor = if (livePoseScore >= 80) LevelGreen else AmberYellow
                Text(
                    text = "Pose Match: $livePoseScore%",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .background(badgeColor, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            // Countdown timer overlay
            if (isCountingDown) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$timerCountdown",
                        color = Color.White,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Live Luma Histogram Overlay
            if (showHistogram) {
                HistogramView(
                    histogram = analysis?.histogram,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 45.dp, end = 10.dp)
                )
            }

            val landscapePose = currentPose
            if (landscape && poseOn && landscapePose != null) {
                PoseCard(
                    pose = landscapePose,
                    onPrevious = { stepPose(-1) },
                    onNext = { stepPose(1) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .widthIn(max = 280.dp),
                )
            }

            statusText?.let { message ->
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (landscape && poseOn && currentPose != null) 96.dp else 56.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        if (landscape) {
            // Side panel: six compact round buttons in a fixed 2 x 3 grid, shutter beside them.
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(maxWidth - previewWidth)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundButton("Grid", size = 44.dp, dimmed = !showGrid) { showGrid = !showGrid }
                        RoundButton("Guide", size = 44.dp, dimmed = !showGuide) { showGuide = !showGuide }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundButton("Auto", size = 44.dp, dimmed = !autoOn) { autoOn = !autoOn }
                        RoundButton("Rule\n${ruleChoice.label}", size = 44.dp, onClick = cycleRule)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundButton("Pose", size = 44.dp, dimmed = !poseOn, onClick = togglePose)
                        RoundButton(if (frontCamera) "Rear\ncam" else "Selfie\ncam", size = 44.dp, onClick = flipCamera)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundButton("Info", size = 44.dp) { showInfo = true }
                    }
                }
                ShutterButton(onClick = onShutter)
            }
        } else {
            // Top bar
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topBarHeight)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopButton(if (showGrid) "Grid" else "Grid off") { showGrid = !showGrid }
                TopButton(if (showGuide) "Guide" else "Guide off") { showGuide = !showGuide }
                TopButton(
                    when {
                        isTorchOn -> "Torch"
                        flashModeState == ImageCapture.FLASH_MODE_AUTO -> "Flash: A"
                        flashModeState == ImageCapture.FLASH_MODE_ON -> "Flash: On"
                        else -> "Flash: Off"
                    },
                    cycleFlash
                )
                TopButton(if (timerChoiceSeconds > 0) "${timerChoiceSeconds}s" else "Timer", cycleTimer)
                TopButton(aspectRatioChoice.label, cycleAspectRatio)
                TopButton(if (showHistogram) "Histo" else "Histo off") { showHistogram = !showHistogram }
                TopButton("Info") { showInfo = true }
            }

            // Bottom area: pose card (when on), then rule, pose, shutter and camera flip
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(maxHeight - topBarHeight - previewHeight),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val portraitPose = currentPose
                if (poseOn && portraitPose != null) {
                    PoseCard(
                        pose = portraitPose,
                        onPrevious = { stepPose(-1) },
                        onNext = { stepPose(1) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Gallery Thumbnail Button
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray)
                            .border(1.5.dp, Color.White, CircleShape)
                            .clickable {
                                if (lastCapturedUri != null) showExifDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (lastCapturedUri != null) {
                            Text("EXIF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Gal", color = Color.Gray, fontSize = 11.sp)
                        }
                    }

                    RoundButton(text = "Rule\n${ruleChoice.label}", onClick = cycleRule)
                    RoundButton(text = "Pose", dimmed = !poseOn, onClick = togglePose)

                    // Shutter button (turns Red when recording video or in video mode)
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .border(4.dp, if (isVideoMode) Color.Red else Color.White, CircleShape)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(if (isRecordingVideo) Color.Red else (if (isVideoMode) Color.Red.copy(alpha = 0.85f) else Color.White))
                            .clickable(onClick = onShutter),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRecordingVideo) {
                            Box(modifier = Modifier.size(20.dp).background(Color.White, RoundedCornerShape(4.dp)))
                        }
                    }

                    RoundButton(text = if (isVideoMode) "VIDEO" else "PHOTO", onClick = toggleVideoMode)
                    RoundButton(text = if (frontCamera) "Rear\ncam" else "Selfie\ncam", onClick = flipCamera)
                }
            }
        }
    }

    if (showExifDialog && lastCapturedUri != null) {
        ExifGalleryDialog(
            photoUri = lastCapturedUri!!,
            onDismiss = { showExifDialog = false }
        )
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("Close") }
            },
            title = { Text("Camera info") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val lines = capabilities?.summaryLines()
                        ?: listOf("Could not read the rear camera's capabilities.")
                    if (frontCamera) {
                        Text(
                            text = "These numbers are for the rear camera. The selfie camera is not listed yet.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    lines.forEach { line ->
                        Text(text = line, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
            },
        )
    }
}

/** A text button for the control bars. */
@Composable
private fun TopButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.heightIn(min = 36.dp),
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, maxLines = 1)
    }
}

/** A round outlined button with a short label. [dimmed] greys it out to show an "off" toggle. */
@Composable
private fun RoundButton(
    text: String,
    size: Dp = 60.dp,
    dimmed: Boolean = false,
    onClick: () -> Unit,
) {
    val alpha = if (dimmed) 0.45f else 1f
    Box(
        modifier = Modifier
            .size(size)
            .border(1.5f.dp, Color.White.copy(alpha = 0.6f * alpha), CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = alpha),
            fontSize = if (size < 56.dp) 10.sp else 11.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
    )
}

/** Shows the pose name and what to do, with buttons for the previous and next pose. */
@Composable
private fun PoseCard(pose: Pose, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopButton("<", onPrevious)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = pose.name,
                color = PoseColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = pose.cue,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
        TopButton(">", onNext)
    }
}

/** A small rounded label over the preview. Pass [onClick] to make it tappable. */
@Composable
private fun InfoChip(
    text: String,
    color: Color = Color.White,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    var chipModifier: Modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), shape)
    if (onClick != null) {
        chipModifier = chipModifier.clip(shape).clickable(onClick = onClick)
    }
    Text(
        text = text,
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = chipModifier.padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** Zooms so the subject becomes a comfortable size. Returns a message if nothing could be done. */
private fun zoomToFit(controller: LifecycleCameraController, subject: Subject): String? {
    val zoom = controller.zoomState.value ?: return "Camera is not ready yet"
    val target = ZoomPlanner.targetRatio(
        current = zoom.zoomRatio,
        minRatio = zoom.minZoomRatio,
        maxRatio = zoom.maxZoomRatio,
        rect = subject.rect,
        kind = subject.kind,
    ) ?: return "Zoom is already about right. Move instead"
    return try {
        controller.setZoomRatio(target)
        null
    } catch (e: RuntimeException) {
        Log.w(TAG, "Could not set zoom", e)
        "Could not change the zoom"
    }
}

/** Draws the subject box, the dashed "put it here" box, and a line from one to the other. */
private fun DrawScope.drawGuidance(guidance: Guidance) {
    val w = size.width
    val h = size.height
    val accent = if (guidance.isAligned) LevelGreen else AmberYellow
    val subject = guidance.subject
    val ghost = guidance.ghost

    if (subject != null) {
        val r = subject.rect
        val boxColor = if (guidance.locked) AmberYellow else Color.White.copy(alpha = 0.75f)
        val boxWidth = if (guidance.locked) 3.dp.toPx() else 1.5f.dp.toPx()
        drawRect(
            color = boxColor,
            topLeft = Offset(r.left * w, r.top * h),
            size = Size(r.width * w, r.height * h),
            style = Stroke(width = boxWidth),
        )
    }

    if (ghost != null) {
        drawRect(
            color = accent,
            topLeft = Offset(ghost.left * w, ghost.top * h),
            size = Size(ghost.width * w, ghost.height * h),
            style = Stroke(
                width = 2.5f.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)),
            ),
        )
    }

    if (subject != null && ghost != null) {
        drawGuideLine(
            from = Offset(subject.rect.centerX * w, subject.rect.centerY * h),
            to = Offset(ghost.centerX * w, ghost.centerY * h),
            color = accent,
        )
    }
}

/**
 * A line from the subject's center to the center of the target box, ending in an arrowhead.
 * Hidden once the two are close, which is when the framing is already right.
 */
private fun DrawScope.drawGuideLine(from: Offset, to: Offset, color: Color) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = hypot(dx, dy)
    if (length < 28.dp.toPx()) return

    val ux = dx / length
    val uy = dy / length
    val head = 16.dp.toPx()
    val baseX = to.x - ux * head
    val baseY = to.y - uy * head
    val wing = head * 0.55f

    drawLine(
        color = color,
        start = from,
        end = Offset(baseX, baseY),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawCircle(color = color, radius = 5.dp.toPx(), center = from)

    val arrowHead = Path().apply {
        moveTo(to.x, to.y)
        lineTo(baseX - uy * wing, baseY + ux * wing)
        lineTo(baseX + uy * wing, baseY - ux * wing)
        close()
    }
    drawPath(arrowHead, color)
}

private fun takePhoto(
    context: Context,
    cameraController: LifecycleCameraController,
    onResult: (Boolean, Uri?) -> Unit,
) {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(java.util.Date())
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "CameraPro_$timestamp")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        // Scoped storage (Android 10+): saving to Pictures needs no storage permission.
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CameraProV1")
    }
    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values,
    ).build()

    try {
        cameraController.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val uri = outputFileResults.savedUri
                    Log.i(TAG, "Photo saved: $uri")
                    onResult(true, uri)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exception)
                    onResult(false, null)
                }
            },
        )
    } catch (e: IllegalStateException) {
        // Thrown if the shutter is tapped before the camera has finished starting.
        Log.w(TAG, "Camera not ready yet", e)
        onResult(false, null)
    }
}

private fun Context.findComponentActivity(): ComponentActivity {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    error("CameraScreen must be shown inside a ComponentActivity")
}
