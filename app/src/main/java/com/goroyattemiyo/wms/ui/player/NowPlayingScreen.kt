package com.goroyattemiyo.wms.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.goroyattemiyo.wms.library.MediaEntity
import com.goroyattemiyo.wms.playback.VisualizerMode
import com.goroyattemiyo.wms.ui.components.formatPlaybackTime
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(
    media: MediaEntity?,
    queue: List<MediaEntity>,
    onBack: () -> Unit,
    visualizerMode: VisualizerMode,
    reducedMotion: Boolean,
    onVisualizerSelected: (String) -> Unit,
    controller: MediaController?,
) {
    var playing by remember { mutableStateOf(false) }
    var position by remember(media?.id) { mutableLongStateOf(media?.lastPositionMs ?: 0L) }
    var duration by remember(media?.id) { mutableLongStateOf(media?.durationMs ?: 0L) }
    var seeking by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    var previous by remember { mutableStateOf(false) }
    var next by remember { mutableStateOf(false) }
    var visualizerMenuOpen by remember { mutableStateOf(false) }
    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                previous = controller.hasPreviousMediaItem(); next = controller.hasNextMediaItem()
            }
        }
        controller.addListener(listener)
        playing = controller.isPlaying; previous = controller.hasPreviousMediaItem(); next = controller.hasNextMediaItem()
        onDispose { controller.removeListener(listener) }
    }
    LaunchedEffect(controller, media?.id) {
        while (true) {
            val known = controller?.duration ?: C.TIME_UNSET
            duration = if (known == C.TIME_UNSET || known < 0L) media?.durationMs ?: 0L else known
            if (!seeking) position = (controller?.currentPosition ?: 0L).coerceIn(0L, duration.coerceAtLeast(0L))
            delay(if (controller?.isPlaying == true) 250L else 750L)
        }
    }
    val shownPosition = if (seeking) (fraction * duration).toLong() else position
    val shownFraction = if (seeking) fraction else if (duration > 0L) (position.toDouble() / duration).toFloat().coerceIn(0f, 1f) else 0f
    val queueIndex = queue.indexOfFirst { it.id == media?.id }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹") }
            Text("Now Playing", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (media?.mediaType != "VIDEO") Box {
                TextButton(onClick = { visualizerMenuOpen = true }, enabled = !reducedMotion) { Text("表示") }
                DropdownMenu(expanded = visualizerMenuOpen, onDismissRequest = { visualizerMenuOpen = false }) {
                    VisualizerMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(if (mode == visualizerMode) "✓ ${visualizerLabel(mode)}" else visualizerLabel(mode)) },
                            onClick = { onVisualizerSelected(mode.id); visualizerMenuOpen = false },
                        )
                    }
                }
            }
        }
        if (media != null) {
            MediaVisualSurface(
                media, controller, visualizerMode, reducedMotion,
                Modifier.fillMaxWidth().height(if (media.mediaType == "VIDEO") 245.dp else 280.dp).clip(RoundedCornerShape(28.dp)),
            )
        }
        Text(media?.title ?: "メディアを選択してください", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(media?.author?.takeIf(String::isNotBlank) ?: media?.provider.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = shownFraction, onValueChange = { seeking = true; fraction = it }, onValueChangeFinished = { controller?.seekTo((fraction * duration).toLong()); seeking = false }, enabled = controller != null && duration > 0L, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatPlaybackTime(shownPosition)); Text(formatPlaybackTime(duration)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { controller?.seekToPreviousMediaItem() }, enabled = previous) { Text("⏮") }
            Button(onClick = { if (controller?.isPlaying == true) controller.pause() else { if (controller?.playbackState == Player.STATE_ENDED) controller.seekTo(0L); controller?.play() } }, enabled = controller != null, shape = CircleShape) { Text(if (playing) "❚❚" else "▶", style = MaterialTheme.typography.headlineSmall) }
            TextButton(onClick = { controller?.seekToNextMediaItem() }, enabled = next) { Text("⏭") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { controller?.seekTo(((controller?.currentPosition ?: 0L) - 10_000L).coerceAtLeast(0L)) }, enabled = controller != null) { Text("↶ 10秒") }
            TextButton(onClick = { controller?.seekTo(((controller?.currentPosition ?: 0L) + 10_000L).coerceAtMost(duration)) }, enabled = controller != null && duration > 0L) { Text("10秒 ↷") }
        }
        if (queueIndex >= 0 && queue.size > 1) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("次に再生", fontWeight = FontWeight.Bold)
                queue.drop(queueIndex + 1).take(2).forEach { Text(it.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

private fun visualizerLabel(mode: VisualizerMode): String = when (mode.id) {
    "rainbow-ring" -> "レインボーリング"
    "oscilloscope" -> "オシロスコープ"
    "spectrum-city" -> "スペクトラム"
    "neon-tunnel" -> "ネオントンネル"
    "kaleido" -> "カレイド"
    "particles" -> "パーティクル"
    "minimal" -> "ミニマル"
    else -> mode.id.replaceFirstChar { it.uppercase() }
}
