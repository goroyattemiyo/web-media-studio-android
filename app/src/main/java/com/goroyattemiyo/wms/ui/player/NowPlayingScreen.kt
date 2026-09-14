package com.goroyattemiyo.wms.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.goroyattemiyo.wms.R
import com.goroyattemiyo.wms.playback.AudioAnalysisFrame
import com.goroyattemiyo.wms.playback.LightweightVisualizerRenderer
import com.goroyattemiyo.wms.playback.PcmAudioAnalysisBus
import com.goroyattemiyo.wms.playback.VisualizerMode

@Composable
fun NowPlayingScreen(
    title: String,
    onBack: () -> Unit,
    visualizerMode: VisualizerMode,
    reducedMotion: Boolean,
    onVisualizerSelected: (String) -> Unit,
    controller: MediaController?,
    isVideo: Boolean,
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
    val visualPrimary = MaterialTheme.colorScheme.primary
    val visualSecondary = MaterialTheme.colorScheme.secondary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("戻る") }
        }
        Text("Now Playing", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .size(240.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xAA57D8FF), Color(0x553B55FF), Color.Transparent),
                    ),
                    RoundedCornerShape(48.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isVideo && controller != null) {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            useController = false
                            player = controller
                        }
                    },
                    update = { it.player = controller },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(36.dp)),
                )
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = visualPrimary.copy(alpha = renderState.emphasis),
                        radius = size.minDimension * (0.32f + renderState.emphasis * 0.08f),
                        style = Stroke(width = 5.dp.toPx()),
                    )
                    val step = size.width / (renderState.values.size + 1)
                    renderState.values.forEachIndexed { index, value ->
                        val x = step * (index + 1)
                        val halfHeight = size.height * value * 0.22f
                        drawLine(
                            color = visualSecondary.copy(alpha = 0.35f + value * 0.6f),
                            start = androidx.compose.ui.geometry.Offset(x, center.y - halfHeight),
                            end = androidx.compose.ui.geometry.Offset(x, center.y + halfHeight),
                            strokeWidth = 4.dp.toPx(),
                        )
                    }
                }
                Image(
                    painter = painterResource(R.drawable.wms_emblem),
                    contentDescription = "WMS",
                    modifier = Modifier.size(150.dp),
                )
            }
        }
        Text(
            title.ifBlank { "WMS Local Player" },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "端末内メディア · MediaSession",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(if (isVideo) "Video · MP4" else "Visualizer: ${visualizerMode.id}", fontWeight = FontWeight.Bold)
        if (reducedMotion) {
            Text(
                "モーション軽減中 · minimal を使用",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!isVideo) Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VisualizerMode.entries.forEach { mode ->
                OutlinedButton(
                    onClick = { onVisualizerSelected(mode.id) },
                    enabled = !reducedMotion,
                ) {
                    Text(if (mode == visualizerMode) "✓ ${mode.id}" else mode.id)
                }
            }
        }
    }
}
