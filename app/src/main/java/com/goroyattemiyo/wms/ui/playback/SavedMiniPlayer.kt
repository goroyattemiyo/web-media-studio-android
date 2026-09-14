package com.goroyattemiyo.wms.ui.playback

import android.content.ComponentName
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.goroyattemiyo.wms.R
import com.goroyattemiyo.wms.library.MediaEntity
import com.goroyattemiyo.wms.playback.PlaybackService
import com.goroyattemiyo.wms.playback.toPlaybackMediaItem
import com.goroyattemiyo.wms.ui.components.formatPlaybackTime
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun SavedMiniPlayer(
    controller: MediaController?,
    media: MediaEntity,
    playbackRequest: PlaybackRequest?,
    onPositionChanged: (Long) -> Unit,
    onMediaTransition: (String) -> Unit,
    onOpenNowPlaying: () -> Unit,
    expanded: Boolean,
) {
    val fileAvailable = remember(media.localPath) {
        File(media.localPath).let { it.exists() && it.length() > 0L }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    var lastPersistedPositionMs by remember(media.id) { mutableLongStateOf(media.lastPositionMs) }

    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }

            override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                item?.mediaId?.takeIf(String::isNotBlank)?.let(onMediaTransition)
            }
        }
        controller.addListener(listener)
        isPlaying = controller.isPlaying
        playbackState = controller.playbackState
        controller.currentMediaItem?.mediaId?.takeIf(String::isNotBlank)?.let(onMediaTransition)
        onDispose {
            controller.removeListener(listener)
        }
    }

    LaunchedEffect(controller) {
        while (true) {
            val knownDuration = controller?.duration ?: C.TIME_UNSET
            durationMs = if (knownDuration == C.TIME_UNSET || knownDuration < 0L) {
                0L
            } else {
                knownDuration
            }
            if (!isSeeking) {
                positionMs = (controller?.currentPosition ?: 0L)
                    .coerceIn(0L, durationMs.coerceAtLeast(0L))
            }
            if (controller?.isPlaying == true && kotlin.math.abs(positionMs - lastPersistedPositionMs) >= 5_000L) {
                lastPersistedPositionMs = positionMs
                onPositionChanged(positionMs)
            }
            delay(if (controller?.isPlaying == true) 250L else 750L)
        }
    }

    val displayedPositionMs = if (isSeeking && durationMs > 0L) {
        (seekFraction * durationMs).toLong()
    } else {
        positionMs
    }
    val displayedFraction = when {
        durationMs <= 0L -> 0f
        isSeeking -> seekFraction
        else -> (positionMs.toDouble() / durationMs.toDouble()).toFloat().coerceIn(0f, 1f)
    }
    val statusText = when {
        !fileAvailable -> "Local · ファイルを開けません"
        playbackState == Player.STATE_BUFFERING -> "Local · 読み込み中"
        playbackState == Player.STATE_READY && isPlaying -> "Local · 再生中"
        playbackState == Player.STATE_READY -> "Local · 一時停止"
        playbackState == Player.STATE_ENDED -> "Local · 再生完了"
        else -> "Local · 準備中"
    }

    LaunchedEffect(controller, playbackRequest) {
        if (controller != null && playbackRequest != null) {
            val playableQueue = playbackRequest.queue.filter { item ->
                File(item.localPath).let { it.exists() && it.length() > 0L }
            }
            val startIndex = playableQueue.indexOfFirst { it.id == playbackRequest.mediaId }
            if (startIndex >= 0) {
                val requestedMedia = playableQueue[startIndex]
                controller.setMediaItems(
                    playableQueue.map(MediaEntity::toPlaybackMediaItem),
                    startIndex,
                    requestedMedia.lastPositionMs.coerceAtLeast(0L),
                )
                controller.prepare()
                controller.play()
            }
        }
    }

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(Color(0x6657D8FF), Color(0x221D64FF), Color.Transparent),
                            ),
                            shape = RoundedCornerShape(18.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.wms_emblem),
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = media.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onOpenNowPlaying) { Text("Now Playing") }
                }
                if (!expanded) {
                    TextButton(
                        onClick = {
                            if (controller?.isPlaying == true) controller.pause() else controller?.play()
                        },
                        enabled = controller != null && fileAvailable,
                    ) { Text(if (isPlaying) "停止" else "再生") }
                    TextButton(
                        onClick = { controller?.seekToNextMediaItem() },
                        enabled = controller?.hasNextMediaItem() == true,
                    ) { Text("次") }
                }
            }

            if (expanded) {
                Slider(
                    value = displayedFraction,
                    onValueChange = {
                        isSeeking = true
                        seekFraction = it
                    },
                    onValueChangeFinished = {
                        val targetMs = (seekFraction * durationMs).toLong().coerceIn(0L, durationMs)
                        controller?.seekTo(targetMs)
                        positionMs = targetMs
                        lastPersistedPositionMs = targetMs
                        onPositionChanged(targetMs)
                        isSeeking = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = controller != null && fileAvailable && durationMs > 0L,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatPlaybackTime(displayedPositionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatPlaybackTime(durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val targetMs = ((controller?.currentPosition ?: 0L) - 10_000L).coerceAtLeast(0L)
                            controller?.seekTo(targetMs)
                            positionMs = targetMs
                            lastPersistedPositionMs = targetMs
                            onPositionChanged(targetMs)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = controller != null && fileAvailable && durationMs > 0L,
                    ) {
                        Text("−10秒")
                    }
                    Button(
                        onClick = {
                            if (controller?.isPlaying == true) {
                                controller.pause()
                                lastPersistedPositionMs = controller.currentPosition
                                onPositionChanged(controller.currentPosition)
                            } else {
                                if (controller?.playbackState == Player.STATE_ENDED) controller.seekTo(0L)
                                controller?.play()
                            }
                        },
                        modifier = Modifier.weight(1.4f),
                        enabled = controller != null && fileAvailable,
                    ) {
                        Text(if (isPlaying) "一時停止" else "再生")
                    }
                    OutlinedButton(
                        onClick = {
                            val targetMs = ((controller?.currentPosition ?: 0L) + 10_000L).coerceAtMost(durationMs)
                            controller?.seekTo(targetMs)
                            positionMs = targetMs
                            lastPersistedPositionMs = targetMs
                            onPositionChanged(targetMs)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = controller != null && fileAvailable && durationMs > 0L,
                    ) {
                        Text("+10秒")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { controller?.seekToPreviousMediaItem() },
                        modifier = Modifier.weight(1f),
                        enabled = controller?.hasPreviousMediaItem() == true,
                    ) {
                        Text("前へ")
                    }
                    OutlinedButton(
                        onClick = { controller?.seekToNextMediaItem() },
                        modifier = Modifier.weight(1f),
                        enabled = controller?.hasNextMediaItem() == true,
                    ) {
                        Text("次へ")
                    }
                }
            }
        }
    }
}

@Composable
fun rememberPlaybackController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    return controller
}
