package com.goroyattemiyo.wms.ui.startup

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

object StartupSoundPlayer {
    private const val TAG = "WMS-StartupSound"
    private const val SAMPLE_RATE = 48_000
    private const val DURATION_MS = 760L
    private const val TOTAL_FRAMES = (SAMPLE_RATE * DURATION_MS / 1_000L).toInt()
    private const val ATTACK_FRAMES = (SAMPLE_RATE * 0.030).toInt()
    private const val RELEASE_START_FRAME = (SAMPLE_RATE * 0.430).toInt()
    private const val TABLE_SIZE = 2_048

    private val playedThisProcess = AtomicBoolean(false)

    private val sineTable = FloatArray(TABLE_SIZE) { index ->
        sin(2.0 * PI * index.toDouble() / TABLE_SIZE.toDouble()).toFloat()
    }

    /**
     * Plays once per app process. The sound is intentionally just one short,
     * low WMS startup hum: no rhythm, bloom, chime, or ending hit.
     */
    fun play(context: Context) {
        if (!playedThisProcess.compareAndSet(false, true)) {
            Log.d(TAG, "skip: already played in this process")
            return
        }

        val requestedAt = SystemClock.elapsedRealtime()
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(AudioManager::class.java)
        if (audioManager == null) {
            Log.w(TAG, "AudioManager unavailable")
            return
        }

        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        Log.i(TAG, "request: mediaVolume=$volume/$maxVolume")
        if (volume <= 0) {
            Log.i(TAG, "skip: media volume is zero")
            return
        }

        Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                runCatching { playHum(audioManager, requestedAt) }
                    .onFailure { error -> Log.e(TAG, "startup hum failed", error) }
            },
            "wms-startup-hum",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun playHum(audioManager: AudioManager, requestedAt: Long) {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
        val focusResult = audioManager.requestAudioFocus(focusRequest)

        val pcm = renderHum()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
            .build()

        try {
            require(track.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack not initialized" }
            val written = track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            require(written == pcm.size) { "AudioTrack short write: $written/${pcm.size}" }

            track.setVolume(0.70f)
            track.play()
            Log.i(
                TAG,
                "hum started: focus=$focusResult at=${SystemClock.elapsedRealtime() - requestedAt}ms",
            )
            Thread.sleep(DURATION_MS + 80L)
            Log.i(TAG, "hum finished: head=${track.playbackHeadPosition}/$TOTAL_FRAMES")
        } finally {
            runCatching { track.stop() }
            track.release()
            runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
        }
    }

    private fun renderHum(): ShortArray {
        val output = ShortArray(TOTAL_FRAMES * 2)
        var phase = 0f
        var harmonicPhase = 0f
        val fundamentalStep = (72.0 * TABLE_SIZE / SAMPLE_RATE.toDouble()).toFloat()
        val harmonicStep = fundamentalStep * 2f

        var out = 0
        repeat(TOTAL_FRAMES) { frame ->
            val envelope = when {
                frame < ATTACK_FRAMES -> smooth(frame.toFloat() / ATTACK_FRAMES.toFloat())
                frame >= RELEASE_START_FRAME -> {
                    val progress = (frame - RELEASE_START_FRAME).toFloat() /
                        (TOTAL_FRAMES - RELEASE_START_FRAME).toFloat()
                    1f - smooth(progress.coerceIn(0f, 1f))
                }
                else -> 1f
            }

            val fundamental = sineTable[phase.toInt().coerceIn(0, TABLE_SIZE - 1)]
            val harmonic = sineTable[harmonicPhase.toInt().coerceIn(0, TABLE_SIZE - 1)]
            val sample = ((fundamental * 0.88f) + (harmonic * 0.12f)) * envelope * 0.72f
            val pcm = (sample * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()

            output[out++] = pcm
            output[out++] = pcm

            phase += fundamentalStep
            if (phase >= TABLE_SIZE) phase -= TABLE_SIZE
            harmonicPhase += harmonicStep
            if (harmonicPhase >= TABLE_SIZE) harmonicPhase -= TABLE_SIZE
        }
        return output
    }

    private fun smooth(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}
