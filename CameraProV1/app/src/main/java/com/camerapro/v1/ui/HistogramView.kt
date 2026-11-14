package com.camerapro.v1.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun HistogramView(
    histogram: IntArray?,
    modifier: Modifier = Modifier
) {
    if (histogram == null || histogram.size < 256) return

    val maxVal = histogram.maxOrNull()?.coerceAtLeast(1) ?: 1

    Box(
        modifier = modifier
            .width(130.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            val fillPath = Path()
            val strokePath = Path()

            fillPath.moveTo(0f, h)
            strokePath.moveTo(0f, h - (histogram[0].toFloat() / maxVal * h))

            val step = w / 255f

            for (i in 0 until 256) {
                val x = i * step
                val valY = h - (histogram[i].toFloat() / maxVal * h)
                fillPath.lineTo(x, valY)
                strokePath.lineTo(x, valY)
            }

            fillPath.lineTo(w, h)
            fillPath.close()

            drawPath(
                path = fillPath,
                color = Color.White.copy(alpha = 0.25f)
            )

            drawPath(
                path = strokePath,
                color = Color.Cyan.copy(alpha = 0.85f),
                style = Stroke(width = 1.5f)
            )
        }
    }
}
