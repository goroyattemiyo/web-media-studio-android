package com.goroyattemiyo.wms.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.goroyattemiyo.wms.MainActivity
import com.goroyattemiyo.wms.WmsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.google.common.util.concurrent.Futures
import org.json.JSONArray

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: ExoPlayer
    private lateinit var soundEngine: SoundEngine
    private var analysisCloser: AutoCloseable? = null
    private var librarySession: MediaLibrarySession? = null
    private var abLoopState = AbLoopState()
        set(value) {
            field = value
            AbLoopStateBus.publish(value)
        }

    private val preferences by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }
    private val applicationRepository by lazy { (application as WmsApplication).mediaRepository }
    private val mediaDao by lazy { (application as WmsApplication).database.mediaDao() }

    private val persistPosition = object : Runnable {
        override fun run() {
            persistPlaybackState()
            handler.postDelayed(this, POSITION_PERSIST_INTERVAL_MS)
        }
    }

    private val soundStatsTicker = object : Runnable {
        override fun run() {
            if (::soundEngine.isInitialized) soundEngine.reportLimiterActivity()
            handler.postDelayed(this, 1_000L)
        }
    }

    private val abLoopTicker = object : Runnable {
        override fun run() {
            if (abLoopState.shouldLoopAt(player.currentPosition)) {
                player.seekTo(abLoopState.pointAMs!!)
            }
            if (abLoopState.enabled) handler.postDelayed(this, AB_LOOP_INTERVAL_MS)
        }
    }

    private fun clearAbLoop() {
        abLoopState = abLoopState.clear()
        handler.removeCallbacks(abLoopTicker)
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // A-B points belong to the previous media item, never to the next track.
            clearAbLoop()
            persistPlaybackState()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason != Player.DISCONTINUITY_REASON_SEEK) return
            val nextState = abLoopState.afterManualSeek(newPosition.positionMs)
            if (nextState != abLoopState) clearAbLoop()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) PcmAudioAnalysisBus.clear()
            persistPlaybackState()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            persistPlaybackState()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            persistPlayerOptions()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            persistPlayerOptions()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            persistPlayerOptions()
        }

        override fun onVolumeChanged(volume: Float) {
            if (::soundEngine.isInitialized) soundEngine.onPlayerVolumeChanged(volume)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (::soundEngine.isInitialized) soundEngine.onAudioSessionIdChanged(audioSessionId)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AbLoopStateBus.publish(abLoopState)
        val renderersFactory = SoundRenderersFactory(this)
        analysisCloser = renderersFactory
        player = ExoPlayer.Builder(this, renderersFactory).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
            addListener(playerListener)
        }
        soundEngine = SoundEngine(this, player, renderersFactory.boostProcessor, handler, preferences)

        restorePlayerOptions()
        restorePlaybackState()
        soundEngine.onAudioSessionIdChanged(player.audioSessionId)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        librarySession = MediaLibrarySession.Builder(
            this,
            player,
            object : MediaLibrarySession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val builder = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        .buildUpon()
                        .add(SessionCommand(PlaybackCommand.SET_AB_A, Bundle.EMPTY))
                        .add(SessionCommand(PlaybackCommand.SET_AB_B, Bundle.EMPTY))
                        .add(SessionCommand(PlaybackCommand.TOGGLE_AB, Bundle.EMPTY))
                        .add(SessionCommand(PlaybackCommand.CLEAR_AB, Bundle.EMPTY))
                    SoundCommand.actions.forEach { builder.add(SessionCommand(it, Bundle.EMPTY)) }
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(builder.build())
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ) = Futures.immediateFuture(
                    SessionResult(
                        when (customCommand.customAction) {
                            PlaybackCommand.SET_AB_A -> {
                                clearAbLoop()
                                abLoopState = abLoopState.setA(player.currentPosition)
                                SessionResult.RESULT_SUCCESS
                            }
                            PlaybackCommand.SET_AB_B -> {
                                abLoopState = abLoopState.setB(player.currentPosition)
                                SessionResult.RESULT_SUCCESS
                            }
                            PlaybackCommand.TOGGLE_AB -> {
                                abLoopState = abLoopState.toggle()
                                handler.removeCallbacks(abLoopTicker)
                                if (abLoopState.enabled) handler.post(abLoopTicker)
                                SessionResult.RESULT_SUCCESS
                            }
                            PlaybackCommand.CLEAR_AB -> { clearAbLoop(); SessionResult.RESULT_SUCCESS }
                            in SoundCommand.actions -> soundEngine.execute(customCommand.customAction, args)
                            else -> SessionResult.RESULT_ERROR_BAD_VALUE
                        },
                    ),
                )
            },
        ).setSessionActivity(sessionActivity)
            .build()

        handler.postDelayed(persistPosition, POSITION_PERSIST_INTERVAL_MS)
        handler.postDelayed(soundStatsTicker, 1_000L)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        librarySession

    override fun onDestroy() {
        handler.removeCallbacks(persistPosition)
        handler.removeCallbacks(soundStatsTicker)
        clearAbLoop()
        persistPlaybackState()
        librarySession?.release()
        librarySession = null
        player.removeListener(playerListener)
        if (::soundEngine.isInitialized) soundEngine.close()
        player.release()
        analysisCloser?.close()
        analysisCloser = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun restorePlaybackState() {
        val ids = decodeQueueIds(preferences.getString(KEY_QUEUE_IDS, null))
        if (ids.isEmpty()) return

        val items = runBlocking(Dispatchers.IO) {
            ids.mapNotNull { id -> mediaDao.getById(id)?.toPlaybackMediaItem() }
        }
        if (items.isEmpty()) return

        val requestedIndex = preferences.getInt(KEY_QUEUE_INDEX, 0)
        val startIndex = requestedIndex.coerceIn(0, items.lastIndex)
        val startPositionMs = preferences.getLong(KEY_POSITION_MS, 0L).coerceAtLeast(0L)
        player.setMediaItems(items, startIndex, startPositionMs)
        player.prepare()
        player.playWhenReady = false
    }

    private fun restorePlayerOptions() {
        player.playbackParameters = PlaybackParameters(PlaybackSpeed.normalize(preferences.getFloat(KEY_SPEED, 1f)))
        player.repeatMode = RepeatOption.fromMedia3(preferences.getInt(KEY_REPEAT_MODE, Player.REPEAT_MODE_OFF)).media3Mode
        player.shuffleModeEnabled = preferences.getBoolean(KEY_SHUFFLE, false)
    }

    private fun persistPlayerOptions() {
        if (!::player.isInitialized) return
        preferences.edit()
            .putFloat(KEY_SPEED, PlaybackSpeed.normalize(player.playbackParameters.speed))
            .putInt(KEY_REPEAT_MODE, RepeatOption.fromMedia3(player.repeatMode).media3Mode)
            .putBoolean(KEY_SHUFFLE, player.shuffleModeEnabled)
            .apply()
    }

    private fun persistPlaybackState() {
        if (!::player.isInitialized || player.mediaItemCount == 0) return

        val ids = buildList {
            repeat(player.mediaItemCount) { index ->
                player.getMediaItemAt(index).mediaId.takeIf(String::isNotBlank)?.let(::add)
            }
        }
        val mediaId = player.currentMediaItem?.mediaId
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        preferences.edit()
            .putString(KEY_QUEUE_IDS, JSONArray(ids).toString())
            .putInt(KEY_QUEUE_INDEX, player.currentMediaItemIndex.coerceAtLeast(0))
            .putLong(KEY_POSITION_MS, positionMs)
            .apply()

        if (!mediaId.isNullOrBlank()) {
            serviceScope.launch {
                applicationRepository.updateLastPosition(mediaId, positionMs)
            }
        }
    }

    private fun decodeQueueIds(value: String?): List<String> = runCatching {
        val array = JSONArray(value ?: return emptyList())
        buildList {
            repeat(array.length()) { index ->
                array.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val PREFS_NAME = "wms_playback"
        const val KEY_QUEUE_IDS = "queue_ids"
        const val KEY_QUEUE_INDEX = "queue_index"
        const val KEY_POSITION_MS = "position_ms"
        const val KEY_SPEED = "speed"
        const val KEY_REPEAT_MODE = "repeat_mode"
        const val KEY_SHUFFLE = "shuffle"
        const val POSITION_PERSIST_INTERVAL_MS = 5_000L
        const val AB_LOOP_INTERVAL_MS = 200L
    }
}
