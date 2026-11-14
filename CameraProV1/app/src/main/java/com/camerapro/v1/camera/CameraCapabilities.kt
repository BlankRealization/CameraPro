package com.camerapro.v1.camera

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import android.util.Range
import kotlin.math.roundToInt

/**
 * What the phone's main rear camera lets an app control. Phase 3 (scene-aware settings)
 * uses this to clamp every request to what the hardware supports.
 */
data class CameraCapabilities(
    val cameraId: String,
    val backCameraCount: Int,
    val hardwareLevel: String,
    val manualSensor: Boolean,
    val manualPostProcessing: Boolean,
    val rawCapture: Boolean,
    val logicalMultiCamera: Boolean,
    val physicalCameraIds: List<String>,
    val isoRange: Range<Int>?,
    val exposureTimeRangeNs: Range<Long>?,
    val minFocusDistanceDiopters: Float,
    val aeCompensationRange: Range<Int>?,
    val aeCompensationStep: Float,
    val zoomRatioRange: Range<Float>?,
    val maxDigitalZoom: Float,
) {
    fun summaryLines(): List<String> = listOf(
        "Camera id: $cameraId (rear cameras found: $backCameraCount)",
        "Hardware level: $hardwareLevel",
        "Manual sensor control (ISO, shutter): ${yesNo(manualSensor)}",
        "Manual white balance / post-processing: ${yesNo(manualPostProcessing)}",
        "RAW capture: ${yesNo(rawCapture)}",
        "Multi-lens (logical) camera: ${yesNo(logicalMultiCamera)}" +
            if (physicalCameraIds.isNotEmpty()) " (lenses: ${physicalCameraIds.joinToString()})" else "",
        "ISO range: " + (isoRange?.let { "${it.lower} to ${it.upper}" } ?: "not reported"),
        "Shutter range: " + (exposureTimeRangeNs?.let {
            "${formatShutter(it.lower)} to ${formatShutter(it.upper)}"
        } ?: "not reported"),
        "Closest focus: " + if (minFocusDistanceDiopters > 0f) {
            "about ${(100f / minFocusDistanceDiopters).roundToInt()} cm"
        } else {
            "fixed focus"
        },
        "Exposure compensation: " + (aeCompensationRange?.let {
            "${it.lower} to ${it.upper} steps of ${"%.2f".format(aeCompensationStep)} EV"
        } ?: "not reported"),
        "Zoom ratio: " + (zoomRatioRange?.let { "${it.lower}x to ${it.upper}x" }
            ?: "max digital zoom ${"%.1f".format(maxDigitalZoom)}x"),
    )

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"

    private fun formatShutter(ns: Long): String =
        if (ns >= 1_000_000_000L) {
            "%.1f s".format(ns / 1_000_000_000.0)
        } else {
            "1/${(1_000_000_000.0 / ns).roundToInt()} s"
        }
}

object CameraCapabilityChecker {
    private const val TAG = "CameraCapability"

    /** Reads the main rear camera's characteristics. Returns null if no rear camera is readable. */
    fun check(context: Context): CameraCapabilities? {
        return try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val backIds = manager.cameraIdList.filter { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            val mainId = backIds.firstOrNull() ?: return null
            val chars = manager.getCameraCharacteristics(mainId)

            val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.toList() ?: emptyList()

            val zoomRange: Range<Float>? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                } else {
                    null
                }

            val result = CameraCapabilities(
                cameraId = mainId,
                backCameraCount = backIds.size,
                hardwareLevel = hardwareLevelName(
                    chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                ),
                manualSensor = caps.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
                ),
                manualPostProcessing = caps.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING
                ),
                rawCapture = caps.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
                ),
                logicalMultiCamera = caps.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
                ),
                physicalCameraIds = chars.physicalCameraIds.toList(),
                isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE),
                exposureTimeRangeNs = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE),
                minFocusDistanceDiopters =
                    chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f,
                aeCompensationRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE),
                aeCompensationStep =
                    chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0f,
                zoomRatioRange = zoomRange,
                maxDigitalZoom =
                    chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f,
            )

            // Copy these lines from Logcat (filter "CameraCapability") when reporting device issues.
            result.summaryLines().forEach { Log.i(TAG, it) }
            result
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Could not read camera characteristics", e)
            null
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unexpected error reading camera characteristics", e)
            null
        }
    }

    private fun hardwareLevelName(level: Int?): String = when (level) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "unknown"
    }
}
