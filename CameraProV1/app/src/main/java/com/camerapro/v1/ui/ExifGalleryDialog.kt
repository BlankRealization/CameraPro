package com.camerapro.v1.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.InputStream

data class ExifDetails(
    val aperture: String,
    val shutterSpeed: String,
    val iso: String,
    val resolution: String,
    val date: String,
    val cameraModel: String
)

private fun readExif(context: Context, uri: Uri): ExifDetails {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        if (inputStream != null) {
            val exif = ExifInterface(inputStream)
            val aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER) ?: "N/A"
            val shutter = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME) ?: "N/A"
            val iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS) ?: "N/A"
            val w = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH) ?: "?"
            val h = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH) ?: "?"
            val date = exif.getAttribute(ExifInterface.TAG_DATETIME) ?: "Recent"
            val model = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "Camera Pro"
            inputStream.close()

            ExifDetails(
                aperture = if (aperture != "N/A") "f/$aperture" else "f/1.8",
                shutterSpeed = if (shutter != "N/A") "${shutter}s" else "Auto",
                iso = if (iso != "N/A") "ISO $iso" else "ISO Auto",
                resolution = "${w}x${h}",
                date = date,
                cameraModel = model
            )
        } else {
            ExifDetails("f/1.8", "Auto", "ISO Auto", "12 MP", "Just now", "Camera Pro")
        }
    } catch (e: Exception) {
        ExifDetails("f/1.8", "Auto", "ISO Auto", "12 MP", "Just now", "Camera Pro")
    }
}

@Composable
fun ExifGalleryDialog(
    photoUri: Uri,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val exif = remember(photoUri) { readExif(context, photoUri) }

    val bitmap = remember(photoUri) {
        try {
            context.contentResolver.openInputStream(photoUri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.Cyan)
            }
        },
        title = {
            Text("Captured Photo Details", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Photo Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        ExifRow("Camera", exif.cameraModel)
                        ExifRow("Aperture", exif.aperture)
                        ExifRow("Shutter Speed", exif.shutterSpeed)
                        ExifRow("ISO", exif.iso)
                        ExifRow("Resolution", exif.resolution)
                        ExifRow("Date / Time", exif.date)
                    }
                }
            }
        },
        containerColor = Color(0xFF181818)
    )
}

@Composable
private fun ExifRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
