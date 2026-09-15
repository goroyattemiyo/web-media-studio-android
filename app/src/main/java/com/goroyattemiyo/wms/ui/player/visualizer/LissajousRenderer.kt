package com.goroyattemiyo.wms.ui.player.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.goroyattemiyo.wms.playback.AudioAnalysisFrame

@Composable
internal fun PhosphorLissajousRenderer(
    frame: AudioAnalysisFrame,
    primary: Color,
    secondary: Color,
    modifier: Modifier,
) {
    val trails = remember { mutableStateListOf<LissajousTrace>() }
    LaunchedEffect(frame.leftWaveform, frame.rightWaveform) {
        if (frame.leftWaveform.isNotEmpty()) {
            trails.add(LissajousTrace(frame.leftWaveform, frame.rightWaveform))
            while (trails.size > TRAIL_COUNT) trails.removeAt(0)
        }
    }

    Canvas(modifier) {
        repeat(9) { index ->
            val x = index * size.width / 8f
            val y = index * size.height / 8f
            drawLine(primary.copy(alpha = 0.055f), Offset(x, 0f), Offset(x, size.height), 1f)
            drawLine(primary.copy(alpha = 0.055f), Offset(0f, y), Offset(size.width, y), 1f)
        }

        trails.forEachIndexed { trailIndex, trace ->
            val age = (trailIndex + 1f) / trails.size
            val path = Path()
            val left = trace.left
            val right = trace.right
            left.forEachIndexed { index, leftValue ->
                val yValue = if (right.isNotEmpty()) {
                    right.getOrElse(index * right.size / left.size.coerceAtLeast(1)) { 0f }
                } else {
                    left[(index + left.size / 4) % left.size]
                }
                val x = center.x + leftValue * size.width * 0.39f
                val y = center.y + yValue * size.height * 0.39f
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val traceColor = lerpColor(secondary, primary, age)
            drawPath(path, traceColor.copy(alpha = age * 0.12f), style = Stroke(10f + frame.high * 5f))
            drawPath(path, traceColor.copy(alpha = age * 0.78f), style = Stroke(1.2f + age * 1.8f))
        }
    }
}

private data class LissajousTrace(
    val left: FloatArray,
    val right: FloatArray,
)

private const val TRAIL_COUNT = 7
