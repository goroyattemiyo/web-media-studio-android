package com.goroyattemiyo.wms.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.goroyattemiyo.wms.MainActivity
import com.goroyattemiyo.wms.WmsApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

class PlaybackService : MediaLibraryService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: ExoPlayer
    private var librarySession: MediaLibrarySession? = null

    private val preferences by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }
    private val applicationRepository by lazy { (application as WmsApplication).mediaRepository }
    private val mediaDao by lazy { (application as WmsApplication).database.mediaDao() }

    private val persistPosition = object : Runnable {
        override fun run() {
            persistPlaybackState()
            handler.postDelayed(this, POSITION_PERSIST_INTERVAL_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            persistPlaybackState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            persistPlaybackState()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            persistPlaybackState()
        }
    }

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this, AnalysisRenderersFactory(this)).build().apply {
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

        restorePlaybackState()

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
            object : MediaLibrarySession.Callback {},
        ).setSessionActivity(sessionActivity)
            .build()

        handler.postDelayed(persistPosition, POSITION_PERSIST_INTERVAL_MS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        librarySession

    override fun onDestroy() {
        handler.removeCallbacks(persistPosition)
        persistPlaybackState()
        librarySession?.release()
        librarySession = null
        player.removeListener(playerListener)
        player.release()
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
        const val POSITION_PERSIST_INTERVAL_MS = 5_000L
    }
}
