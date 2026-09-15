package com.goroyattemiyo.wms.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.goroyattemiyo.wms.library.MediaEntity
import com.goroyattemiyo.wms.playback.AudioAnalysisFrame
import com.goroyattemiyo.wms.playback.PcmAudioAnalysisBus
import com.goroyattemiyo.wms.playback.VisualizerMode
import com.goroyattemiyo.wms.playback.VisualizerRendererType
import com.goroyattemiyo.wms.ui.media.MediaArtwork
import com.goroyattemiyo.wms.ui.media.toMediaPresentation
import com.goroyattemiyo.wms.ui.player.visualizer.VisualizerSurface
import kotlinx.coroutines.delay

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
    val phaseDriven = visualizerMode.rendererType in PHASE_DRIVEN_RENDERERS
    val phase = rememberCappedPhase(enabled = !reducedMotion && phaseDriven)
    VisualizerSurface(
        mode = if (reducedMotion) VisualizerMode.MINIMAL else visualizerMode,
        frame = analysisFrame.copy(phase = phase),
        phase = phase,
        primary = MaterialTheme.colorScheme.primary,
        secondary = MaterialTheme.colorScheme.secondary,
        modifier = modifier,
    )
}

@Composable
private fun rememberCappedPhase(enabled: Boolean): Float {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            phase = 0f
            return@LaunchedEffect
        }
        val startNanos = System.nanoTime()
        while (true) {
            phase = ((System.nanoTime() - startNanos) % PHASE_DURATION_NANOS).toFloat() /
                PHASE_DURATION_NANOS.toFloat()
            delay(RENDER_INTERVAL_MS)
        }
    }
    return phase
}

private val PHASE_DRIVEN_RENDERERS = setOf(
    VisualizerRendererType.LIQUID_METABALLS,
    VisualizerRendererType.VORONOI_SHARDS,
    VisualizerRendererType.HYPER_TUNNEL,
    VisualizerRendererType.SPECTRUM_CITY,
    VisualizerRendererType.EMBLEM_REACTOR,
    VisualizerRendererType.MINIMAL,
    VisualizerRendererType.STRANGE_ATTRACTOR,
)
private const val PHASE_DURATION_NANOS = 12_000_000_000L
private const val RENDER_INTERVAL_MS = 33L
