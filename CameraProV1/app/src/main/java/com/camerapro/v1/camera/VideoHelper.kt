package com.camerapro.v1.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import java.text.SimpleDateFormat
import java.util.Locale

object VideoHelper {

    private const val TAG = "VideoHelper"

    fun enableVideoMode(controller: LifecycleCameraController, enabled: Boolean) {
        if (enabled) {
            controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
        } else {
            controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }

    fun startVideoRecording(
        context: Context,
        controller: LifecycleCameraController,
        onSaved: (android.net.Uri) -> Unit,
        onError: (String) -> Unit
    ): Recording? {
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_$name")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraPro")
        }

        val options = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val audioConfig = if (hasAudioPermission) AudioConfig.create(true) else AudioConfig.AUDIO_DISABLED

        return try {
            val executor = ContextCompat.getMainExecutor(context)
            controller.startRecording(
                options,
                audioConfig,
                executor,
                Consumer<VideoRecordEvent> { event ->
                    when (event) {
                        is VideoRecordEvent.Finalize -> {
                            if (!event.hasError()) {
                                val uri = event.outputResults.outputUri
                                onSaved(uri)
                            } else {
                                onError(event.cause?.message ?: "Video recording failed with error code ${event.error}")
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            onError(e.message ?: "Failed to start recording")
            null
        }
    }
}
