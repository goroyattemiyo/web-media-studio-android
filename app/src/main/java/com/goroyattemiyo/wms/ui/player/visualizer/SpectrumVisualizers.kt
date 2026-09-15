package com.goroyattemiyo.wms.ui.player.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.goroyattemiyo.wms.playback.AudioAnalysisFrame

@Composable
internal fun SpectrogramWaterfallRenderer(
    frame: AudioAnalysisFrame,
    primary: Color,
    secondary: Color,
    modifier: Modifier,
) {
    val history = rememberSpectrumHistory(frame, SPECTROGRAM_ROWS)
    Canvas(modifier) {
        if (history.isEmpty()) return@Canvas
        val rowHeight = size.height / SPECTROGRAM_ROWS
        history.forEachIndexed { historyIndex, bins ->
            val displayRow = SPECTROGRAM_ROWS - history.size + historyIndex
            val age = (historyIndex + 1f) / history.size
            bins.forEachIndexed { binIndex, magnitude ->
                val x = binIndex * size.width / bins.size
                val width = size.width / bins.size + 1f
                val shaped = magnitude.coerceIn(0f, 1f).let { it * it.coerceAtLeast(0.18f) }
                val heat = lerpColor(Color(0xFF07101A), secondary, shaped)
                val color = lerpColor(heat, primary, (shaped * 1.25f).coerceIn(0f, 1f))
                drawRect(
                    color = color.copy(alpha = 0.35f + age * 0.65f),
                    topLeft = Offset(x, displayRow * rowHeight),
                    size = Size(width, rowHeight + 1f),
                )
            }
        }
    }
}

@Composable
internal fun WireframeTerrainRenderer(
    frame: AudioAnalysisFrame,
    primary: Color,
    secondary: Color,
    modifier: Modifier,
) {
    val history = rememberSpectrumHistory(frame, TERRAIN_ROWS)
    Canvas(modifier) {
        if (history.isEmpty()) return@Canvas
        val visible = history.takeLast(TERRAIN_ROWS)
        visible.forEachIndexed { index, bins ->
            val depth = (index + 1f) / visible.size
            val perspective = 0.32f + depth * 0.68f
            val horizontalInset = size.width * (1f - perspective) * 0.5f
            val baseY = size.height * (0.14f + depth * 0.8f)
            val heightScale = size.height * (0.035f + depth * 0.20f)
            val path = Path()
            bins.forEachIndexed { binIndex, magnitude ->
                val x = horizontalInset + binIndex.toFloat() / bins.lastIndex.coerceAtLeast(1) * size.width * perspective
                val shaped = magnitude.coerceIn(0f, 1f).let { it * it.coerceAtLeast(0.25f) }
                val y = baseY - shaped * heightScale
                if (binIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lerpColor(secondary, primary, depth).copy(alpha = 0.18f + depth * 0.78f),
                style = Stroke(width = 0.8f + depth * 2.2f),
            )
        }

        repeat(9) { column ->
            val nearX = column * size.width / 8f
            val farX = size.width * 0.34f + column * size.width * 0.32f / 8f
            drawLine(
                color = secondary.copy(alpha = 0.18f),
                start = Offset(farX, size.height * 0.14f),
                end = Offset(nearX, size.height * 0.94f),
                strokeWidth = 1f,
            )
        }
    }
}

@Composable
private fun rememberSpectrumHistory(
    frame: AudioAnalysisFrame,
    capacity: Int,
): List<FloatArray> {
    val history = remember(capacity) { mutableStateListOf<FloatArray>() }
    LaunchedEffect(frame.fftBins) {
        if (frame.fftBins.isNotEmpty()) {
            history.add(frame.fftBins)
            while (history.size > capacity) history.removeAt(0)
        }
    }
    return history
}

private const val SPECTROGRAM_ROWS = 64
private const val TERRAIN_ROWS = 36
