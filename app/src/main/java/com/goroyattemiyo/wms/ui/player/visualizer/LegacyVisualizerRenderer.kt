package com.goroyattemiyo.wms.ui.player.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.goroyattemiyo.wms.playback.AudioAnalysisFrame
import com.goroyattemiyo.wms.playback.LightweightVisualizerRenderer
import com.goroyattemiyo.wms.playback.VisualizerMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun LegacyVisualizerRenderer(
    mode: VisualizerMode,
    frame: AudioAnalysisFrame,
    phase: Float,
    primary: Color,
    secondary: Color,
    modifier: Modifier,
) {
    val renderState = LightweightVisualizerRenderer.render(mode, frame.copy(phase = phase))
    Canvas(modifier) {
        when (mode) {
            VisualizerMode.MINIMAL -> drawLine(
                primary.copy(alpha = 0.7f),
                Offset(size.width * 0.2f, center.y),
                Offset(size.width * 0.8f, center.y),
                2f,
            )
            VisualizerMode.RAINBOW_RING -> repeat(9) { index ->
                val radius = size.minDimension * (0.08f + index * 0.055f + renderState.emphasis * 0.02f)
                drawCircle(
                    Color.hsv((index * 32f + phase * 100f) % 360f, 0.7f, 1f).copy(alpha = 0.7f),
                    radius,
                    style = Stroke((10 - index).toFloat()),
                )
            }
            VisualizerMode.SPECTRUM_CITY -> renderState.values.forEachIndexed { index, value ->
                val width = size.width / (renderState.values.size * 1.25f)
                val left = index * size.width / renderState.values.size
                val height = value.coerceAtLeast(0.08f) * size.height * 0.68f
                drawRect(
                    color = primary.copy(alpha = 0.35f + value * 0.65f),
                    topLeft = Offset(left, size.height - height),
                    size = Size(width, height),
                )
                drawLine(secondary, Offset(left, size.height - height), Offset(left + width, size.height - height), 2f)
            }
            VisualizerMode.KALEIDO -> {
                val path = Path()
                var x = 0.1f
                var y = 0.1f
                repeat(180) { index ->
                    val nextX = sin(1.7f * y + renderState.emphasis * 4f) * 0.45f + 0.5f
                    val nextY = sin(2.3f * x + renderState.values.getOrElse(index % renderState.values.size) { 0.2f } * 5f) * 0.45f + 0.5f
                    if (index == 0) path.moveTo(x * size.width, y * size.height) else path.lineTo(x * size.width, y * size.height)
                    x = nextX
                    y = nextY
                }
                drawPath(path, secondary.copy(alpha = 0.8f), style = Stroke(2f))
            }
            VisualizerMode.EMBLEM -> {
                drawCircle(primary.copy(alpha = 0.5f), size.minDimension * (0.25f + renderState.emphasis * 0.12f), style = Stroke(4f))
                drawCircle(secondary.copy(alpha = 0.35f), size.minDimension * (0.34f + renderState.emphasis * 0.08f), style = Stroke(1f))
            }
            else -> {
                val radius = size.minDimension * 0.27f
                renderState.values.forEachIndexed { index, value ->
                    val angle = index * 2f * PI.toFloat() / renderState.values.size - PI.toFloat() / 2f
                    val start = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
                    val end = Offset(center.x + cos(angle) * (radius + value * size.minDimension * 0.25f), center.y + sin(angle) * (radius + value * size.minDimension * 0.25f))
                    drawLine(primary, start, end, 4f)
                }
            }
        }
    }
}
