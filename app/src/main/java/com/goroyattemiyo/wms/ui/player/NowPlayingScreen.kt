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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.goroyattemiyo.wms.library.MediaEntity
import com.goroyattemiyo.wms.playback.AbLoopState
import com.goroyattemiyo.wms.playback.AbLoopStateBus
import com.goroyattemiyo.wms.playback.PlaybackCommand
import com.goroyattemiyo.wms.playback.PlaybackSpeed
import com.goroyattemiyo.wms.playback.RepeatOption
import com.goroyattemiyo.wms.playback.toggledShuffleEnabled
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
    var currentMediaId by remember(controller) {
        mutableStateOf(controller?.currentMediaItem?.mediaId?.takeIf(String::isNotBlank))
    }
    val activeMedia = queue.firstOrNull { it.id == currentMediaId } ?: media

    var playing by remember { mutableStateOf(false) }
    var position by remember(activeMedia?.id) { mutableLongStateOf(activeMedia?.lastPositionMs ?: 0L) }
    var duration by remember(activeMedia?.id) { mutableLongStateOf(activeMedia?.durationMs ?: 0L) }
    var seeking by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    var previous by remember { mutableStateOf(false) }
    var next by remember { mutableStateOf(false) }
    var visualizerMenuOpen by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var repeatMode by remember { mutableStateOf(RepeatOption.OFF) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    val abLoop by AbLoopStateBus.state.collectAsStateWithLifecycle()

    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                currentMediaId = item?.mediaId?.takeIf(String::isNotBlank)
                previous = controller.hasPreviousMediaItem()
                next = controller.hasNextMediaItem()
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                currentMediaId = controller.currentMediaItem?.mediaId?.takeIf(String::isNotBlank)
                previous = controller.hasPreviousMediaItem()
                next = controller.hasNextMediaItem()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                speed = playbackParameters.speed
            }

            override fun onRepeatModeChanged(mode: Int) {
                repeatMode = RepeatOption.fromMedia3(mode)
            }

            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                shuffleEnabled = enabled
            }
        }
        controller.addListener(listener)
        currentMediaId = controller.currentMediaItem?.mediaId?.takeIf(String::isNotBlank)
        playing = controller.isPlaying
        previous = controller.hasPreviousMediaItem()
        next = controller.hasNextMediaItem()
        speed = controller.playbackParameters.speed
        repeatMode = RepeatOption.fromMedia3(controller.repeatMode)
        shuffleEnabled = controller.shuffleModeEnabled
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(controller, activeMedia?.id) {
        while (true) {
            val known = controller?.duration ?: C.TIME_UNSET
            duration = if (known == C.TIME_UNSET || known < 0L) activeMedia?.durationMs ?: 0L else known
            if (!seeking) {
                position = (controller?.currentPosition ?: 0L)
                    .coerceIn(0L, duration.coerceAtLeast(0L))
            }
            delay(if (controller?.isPlaying == true) 250L else 750L)
        }
    }

    val shownPosition = if (seeking) (fraction * duration).toLong() else position
    val shownFraction = if (seeking) {
        fraction
    } else if (duration > 0L) {
        (position.toDouble() / duration).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val queueIndex = queue.indexOfFirst { it.id == activeMedia?.id }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹") }
            Text(
                "Now Playing",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (activeMedia?.mediaType != "VIDEO") Box {
                TextButton(onClick = { visualizerMenuOpen = true }, enabled = !reducedMotion) {
                    Text("表示")
                }
                DropdownMenu(
                    expanded = visualizerMenuOpen,
                    onDismissRequest = { visualizerMenuOpen = false },
                ) {
                    VisualizerMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (mode == visualizerMode) "✓ ${visualizerLabel(mode)}"
                                    else visualizerLabel(mode),
                                )
                            },
                            onClick = {
                                onVisualizerSelected(mode.id)
                                visualizerMenuOpen = false
                            },
                        )
                    }
                }
            }
        }

        if (activeMedia != null) {
            MediaVisualSurface(
                activeMedia,
                controller,
                visualizerMode,
                reducedMotion,
                Modifier
                    .fillMaxWidth()
                    .height(if (activeMedia.mediaType == "VIDEO") 245.dp else 280.dp)
                    .clip(RoundedCornerShape(28.dp)),
            )
        }

        Text(
            activeMedia?.title ?: "メディアを選択してください",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            activeMedia?.author?.takeIf(String::isNotBlank) ?: activeMedia?.provider.orEmpty(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = shownFraction,
            onValueChange = {
                seeking = true
                fraction = it
            },
            onValueChangeFinished = {
                controller?.seekTo((fraction * duration).toLong())
                seeking = false
            },
            enabled = controller != null && duration > 0L,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatPlaybackTime(shownPosition))
            Text(formatPlaybackTime(duration))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { controller?.seekToPreviousMediaItem() },
                enabled = previous,
            ) { Text("⏮") }
            TextButton(
                onClick = {
                    controller?.seekTo(((controller.currentPosition) - 10_000L).coerceAtLeast(0L))
                },
                enabled = controller != null,
            ) { Text("↶10") }
            Button(
                onClick = {
                    if (controller?.isPlaying == true) {
                        controller.pause()
                    } else {
                        if (controller?.playbackState == Player.STATE_ENDED) controller.seekTo(0L)
                        controller?.play()
                    }
                },
                enabled = controller != null,
                shape = CircleShape,
            ) {
                Text(if (playing) "❚❚" else "▶", style = MaterialTheme.typography.headlineSmall)
            }
            TextButton(
                onClick = {
                    controller?.seekTo(((controller.currentPosition) + 10_000L).coerceAtMost(duration))
                },
                enabled = controller != null && duration > 0L,
            ) { Text("10↷") }
            TextButton(
                onClick = { controller?.seekToNextMediaItem() },
                enabled = next,
            ) { Text("⏭") }
        }

        TextButton(onClick = { optionsOpen = !optionsOpen }) {
            Text(if (optionsOpen) "再生オプションを閉じる" else "再生オプション")
        }
        if (optionsOpen) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("再生オプション", fontWeight = FontWeight.Bold)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        TextButton(onClick = { speedMenuOpen = true }) {
                            Text("速度 ${PlaybackSpeed.label(speed)}")
                        }
                        DropdownMenu(
                            expanded = speedMenuOpen,
                            onDismissRequest = { speedMenuOpen = false },
                        ) {
                            PlaybackSpeed.allowed.forEach { value ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (PlaybackSpeed.normalize(speed) == value) {
                                                "✓ ${PlaybackSpeed.label(value)}"
                                            } else {
                                                PlaybackSpeed.label(value)
                                            },
                                        )
                                    },
                                    onClick = {
                                        controller?.playbackParameters = PlaybackParameters(value)
                                        speedMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { controller?.repeatMode = nextRepeatMode(repeatMode).media3Mode },
                    ) { Text(repeatMode.label) }
                    TextButton(
                        onClick = { controller?.shuffleModeEnabled = toggledShuffleEnabled(shuffleEnabled) },
                    ) {
                        Text(if (shuffleEnabled) "シャッフル ON" else "シャッフル OFF")
                    }
                }
                Text("A-B ループ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            controller?.sendCustomCommand(
                                SessionCommand(PlaybackCommand.SET_AB_A, android.os.Bundle.EMPTY),
                                android.os.Bundle.EMPTY,
                            )
                        },
                        enabled = controller != null,
                    ) { Text("Aを設定") }
                    TextButton(
                        onClick = {
                            controller?.sendCustomCommand(
                                SessionCommand(PlaybackCommand.SET_AB_B, android.os.Bundle.EMPTY),
                                android.os.Bundle.EMPTY,
                            )
                        },
                        enabled = controller != null && abLoop.pointAMs != null,
                    ) { Text("Bを設定") }
                    TextButton(
                        onClick = {
                            controller?.sendCustomCommand(
                                SessionCommand(PlaybackCommand.TOGGLE_AB, android.os.Bundle.EMPTY),
                                android.os.Bundle.EMPTY,
                            )
                        },
                        enabled = abLoop.isValid,
                    ) { Text(if (abLoop.enabled) "ループ ON" else "ループ開始") }
                    TextButton(
                        onClick = {
                            controller?.sendCustomCommand(
                                SessionCommand(PlaybackCommand.CLEAR_AB, android.os.Bundle.EMPTY),
                                android.os.Bundle.EMPTY,
                            )
                        },
                        enabled = abLoop.pointAMs != null,
                    ) { Text("クリア") }
                }
                Text(
                    abLoopLabel(abLoop),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (queueIndex >= 0 && queue.size > 1) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("次に再生", fontWeight = FontWeight.Bold)
                queue.drop(queueIndex + 1).take(2).forEach {
                    Text(
                        it.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun nextRepeatMode(current: RepeatOption): RepeatOption = when (current) {
    RepeatOption.OFF -> RepeatOption.ALL
    RepeatOption.ALL -> RepeatOption.ONE
    RepeatOption.ONE -> RepeatOption.OFF
}

private fun abLoopLabel(state: AbLoopState): String = when {
    state.enabled -> "A-B ループ中: ${formatPlaybackTime(state.pointAMs!!)} – ${formatPlaybackTime(state.pointBMs!!)}"
    state.isValid -> "範囲: ${formatPlaybackTime(state.pointAMs!!)} – ${formatPlaybackTime(state.pointBMs!!)}"
    state.pointAMs != null -> "Aを設定済み。BはAより後の位置で設定してください。"
    else -> "A地点とB地点を設定すると、この区間を繰り返せます。"
}

private fun visualizerLabel(mode: VisualizerMode): String = mode.displayName
