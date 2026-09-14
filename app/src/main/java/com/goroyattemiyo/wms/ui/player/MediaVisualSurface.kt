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
            MediaArtwork(
                media = presentation,
                contentDescription = presentation.title,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f)),
            )
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
        drawCircle(
            color = primary.copy(alpha = renderState.emphasis),
            radius = size.minDimension * (0.32f + renderState.emphasis * 0.08f),
            style = Stroke(width = 5.dp.toPx()),
        )
        val step = size.width / (renderState.values.size + 1)
        renderState.values.forEachIndexed { index, value ->
            val x = step * (index + 1)
            val halfHeight = size.height * value * 0.22f
            drawLine(
                color = secondary.copy(alpha = 0.45f + value * 0.55f),
                start = androidx.compose.ui.geometry.Offset(x, center.y - halfHeight),
                end = androidx.compose.ui.geometry.Offset(x, center.y + halfHeight),
                strokeWidth = 4.dp.toPx(),
            )
        }
    }
}
