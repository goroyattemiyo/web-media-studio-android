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
            VisualizerMode.MINIMAL -> {
                val path = Path()
                val amplitude = size.height * (0.018f + frame.normalizedLevel * 0.12f)
                val wavelength = size.width * (0.42f - frame.high * 0.12f)
                repeat(96) { index ->
                    val progress = index / 95f
                    val x = size.width * progress
                    val y = center.y + sin(progress * size.width / wavelength * 2f * PI.toFloat() + phase * 2f * PI.toFloat()) * amplitude
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, primary.copy(alpha = 0.42f + frame.normalizedLevel * 0.5f), style = Stroke(1.2f + frame.normalizedLevel * 2.6f))
            }
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
                val idle = 0.055f + 0.018f * sin(phase * 2f * PI.toFloat() + index * 0.7f)
                val height = (idle + value * 0.76f) * size.height
                drawRect(
                    color = primary.copy(alpha = 0.38f + value * 0.62f),
                    topLeft = Offset(left, size.height - height),
                    size = Size(width, height),
                )
                drawLine(secondary.copy(alpha = 0.65f + value * 0.35f), Offset(left, size.height - height), Offset(left + width, size.height - height), 2f + value * 2f)
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
                val pulse = frame.onsetStrength
                val baseRadius = size.minDimension * (0.20f + frame.bass * 0.19f + 0.015f * sin(phase * 2f * PI.toFloat()))
                drawCircle(primary.copy(alpha = 0.28f + frame.high * 0.55f), baseRadius + size.minDimension * 0.05f, style = Stroke(8f + frame.high * 13f))
                val ring = Path()
                repeat(65) { index ->
                    val angle = index * 2f * PI.toFloat() / 64f
                    val deformation = sin(angle * 5f + phase * 2f * PI.toFloat()) * frame.mid * size.minDimension * 0.06f
                    val point = Offset(center.x + cos(angle) * (baseRadius + deformation), center.y + sin(angle) * (baseRadius + deformation))
                    if (index == 0) ring.moveTo(point.x, point.y) else ring.lineTo(point.x, point.y)
                }
                drawPath(ring, secondary.copy(alpha = 0.62f + frame.mid * 0.35f), style = Stroke(2f + frame.high * 3f))
                if (pulse > 0.02f) {
                    drawCircle(secondary.copy(alpha = pulse * 0.72f), baseRadius + size.minDimension * pulse * 0.32f, style = Stroke(2f + pulse * 6f))
                }
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
