package com.goroyattemiyo.wms.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.goroyattemiyo.wms.library.MediaEntity
import com.goroyattemiyo.wms.playback.AudioAnalysisFrame
import com.goroyattemiyo.wms.playback.LightweightVisualizerRenderer
import com.goroyattemiyo.wms.playback.PcmAudioAnalysisBus
import com.goroyattemiyo.wms.playback.VisualizerMode
import com.goroyattemiyo.wms.ui.media.MediaArtwork
import com.goroyattemiyo.wms.ui.media.toMediaPresentation

@Composable
fun MediaVisualSurface(
    media: MediaEntity,
    controller: MediaController?,
    visualizerMode: VisualizerMode,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val presentation = media.toMediaPresentation()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (presentation.isVideo && controller != null) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        player = controller
                    }
                },
                update = { it.player = controller },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            if (visualizerMode == VisualizerMode.EMBLEM) {
                MediaArtwork(
                    media = presentation,
                    contentDescription = presentation.title,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)))
            }
            AudioVisualizer(
                visualizerMode = visualizerMode,
                reducedMotion = reducedMotion,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun AudioVisualizer(
    visualizerMode: VisualizerMode,
    reducedMotion: Boolean,
    modifier: Modifier,
) {
    val analysisFrame by PcmAudioAnalysisBus.frames.collectAsStateWithLifecycle(
        initialValue = AudioAnalysisFrame(),
    )
    val phase = if (reducedMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "wms-visualizer")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing)),
            label = "visualizer-phase",
        )
        animatedPhase
    }
    val renderState = LightweightVisualizerRenderer.render(
        visualizerMode,
        analysisFrame.copy(phase = phase),
    )
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Canvas(modifier) {
        val values = renderState.values
        when (visualizerMode) {
            VisualizerMode.OSCILLOSCOPE, VisualizerMode.WAVE -> {
                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = size.width * index / values.lastIndex.coerceAtLeast(1)
                    val y = center.y + (value - 0.5f) * size.height * 0.72f
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, primary.copy(alpha = 0.9f), style = Stroke(4.dp.toPx()))
            }
            VisualizerMode.SPECTRUM_CITY, VisualizerMode.BARS -> values.forEachIndexed { index, value ->
                val width = size.width / (values.size * 1.45f)
                val left = index * size.width / values.size + width * 0.22f
                val height = (value.coerceAtLeast(0.08f) * size.height * 0.84f)
                drawRect(secondary.copy(alpha = 0.5f + value * 0.5f), androidx.compose.ui.geometry.Offset(left, size.height - height), androidx.compose.ui.geometry.Size(width, height))
            }
            VisualizerMode.RAINBOW_RING, VisualizerMode.KALEIDO -> {
                val radius = size.minDimension * 0.27f
                values.forEachIndexed { index, value ->
                    val angle = index * 360f / values.size - 90f
                    val radians = Math.toRadians(angle.toDouble())
                    val start = androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(radians).toFloat() * radius, center.y + kotlin.math.sin(radians).toFloat() * radius)
                    val end = androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(radians).toFloat() * (radius + value * size.minDimension * 0.25f), center.y + kotlin.math.sin(radians).toFloat() * (radius + value * size.minDimension * 0.25f))
                    drawLine(Color.hsv((index * 360f / values.size + renderState.emphasis * 90f) % 360f, 0.72f, 1f), start, end, 5.dp.toPx())
                }
            }
            else -> {
                drawCircle(primary.copy(alpha = renderState.emphasis), size.minDimension * (0.22f + renderState.emphasis * 0.12f), style = Stroke(5.dp.toPx()))
                values.forEachIndexed { index, value ->
                    val x = size.width * (index + 1) / (values.size + 1)
                    drawLine(secondary.copy(alpha = 0.45f + value * 0.55f), androidx.compose.ui.geometry.Offset(x, center.y - value * size.height * 0.25f), androidx.compose.ui.geometry.Offset(x, center.y + value * size.height * 0.25f), 4.dp.toPx())
                }
            }
        }
    }
}
