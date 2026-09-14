package com.goroyattemiyo.wms.ui.player

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.goroyattemiyo.wms.R
import com.goroyattemiyo.wms.library.MediaEntity
import com.goroyattemiyo.wms.playback.VisualizerMode

@Composable
fun NowPlayingScreen(
    media: MediaEntity?,
    onBack: () -> Unit,
    visualizerMode: VisualizerMode,
    reducedMotion: Boolean,
    onVisualizerSelected: (String) -> Unit,
    controller: MediaController?,
) {
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
            if (media != null) {
                MediaVisualSurface(
                    media = media,
                    controller = controller,
                    visualizerMode = visualizerMode,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(36.dp)),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.wms_emblem),
                    contentDescription = "WMS",
                    modifier = Modifier.size(150.dp),
                )
            }
        }
        Text(
            media?.title?.ifBlank { "WMS Local Player" } ?: "WMS Local Player",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "端末内メディア · MediaSession",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            media?.author?.takeIf(String::isNotBlank) ?: media?.provider ?: "Local",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (media?.mediaType == "VIDEO") "Video · MP4" else "Visualizer: ${visualizerMode.id}",
            fontWeight = FontWeight.Bold,
        )
        if (reducedMotion) {
            Text(
                "モーション軽減中 · minimal を使用",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (media?.mediaType != "VIDEO") Row(
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
