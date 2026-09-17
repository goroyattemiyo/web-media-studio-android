package com.goroyattemiyo.wms.ui.startup

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tanh

object StartupSoundPlayer {
    private const val TAG = "WMS-StartupSound"
    private const val SAMPLE_RATE = 48_000
    private const val DURATION_SECONDS = 2.1
    private const val MAX_WAIT_MS = 3_200L

    fun play(context: Context) {
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(AudioManager::class.java)
        if (audioManager == null) {
            Log.w(TAG, "AudioManager unavailable")
            return
        }

        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        Log.i(TAG, "request: mediaVolume=$volume/$maxVolume ringerMode=${audioManager.ringerMode}")
        if (volume <= 0) {
            Log.i(TAG, "skip: media volume is zero")
            return
        }

        Thread(
            {
                runCatching { playStreaming(audioManager) }
                    .onFailure { error -> Log.e(TAG, "startup sound failed", error) }
            },
            "wms-startup-sound",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun playStreaming(audioManager: AudioManager) {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change -> Log.d(TAG, "focus=$change") }
            .build()
        val focusResult = audioManager.requestAudioFocus(focusRequest)
        Log.i(TAG, "audioFocus=$focusResult")

        val channelMask = AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, channelMask, encoding)
        require(minBuffer > 0) { "invalid AudioTrack buffer size: $minBuffer" }
        val bufferBytes = max(minBuffer * 2, 16_384)
        val pcm = renderPcm()
        val totalFrames = pcm.size / 2

        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()

        try {
            Log.i(TAG, "trackState=${track.state} bufferBytes=$bufferBytes session=${track.audioSessionId}")
            require(track.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack not initialized" }

            track.setVolume(0.86f)
            track.play()
            Log.i(TAG, "play called: playState=${track.playState}")

            var offset = 0
            val chunkShorts = max(2_048, bufferBytes / Short.SIZE_BYTES / 2)
            while (offset < pcm.size) {
                val count = minOf(chunkShorts, pcm.size - offset)
                val written = track.write(pcm, offset, count, AudioTrack.WRITE_BLOCKING)
                if (written < 0) error("AudioTrack.write failed: $written")
                offset += written
            }
            Log.i(TAG, "pcm queued: shorts=$offset frames=$totalFrames")

            val deadline = System.currentTimeMillis() + MAX_WAIT_MS
            while (track.playbackHeadPosition < totalFrames - 128 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20L)
            }
            Log.i(
                TAG,
                "finished: playState=${track.playState} head=${track.playbackHeadPosition}/$totalFrames",
            )
        } finally {
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            track.release()
            runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
        }
    }

    private fun renderPcm(): ShortArray {
        val frames = (SAMPLE_RATE * DURATION_SECONDS).toInt()
        val output = ShortArray(frames * 2)
        val twoPi = 2.0 * PI

        repeat(frames) { frame ->
            val t = frame.toDouble() / SAMPLE_RATE.toDouble()
            val master = smoothAttack(t, 0.015) * smoothRelease(t, DURATION_SECONDS, 0.30)

            val bassEnvelope = smoothAttack(t, 0.018) * exp(-t * 1.30)
            val bass = (
                sin(twoPi * 58.0 * t) +
                    0.42 * sin(twoPi * 116.0 * t + 0.16)
                ) * bassEnvelope

            val bloomT = (t - 0.20).coerceAtLeast(0.0)
            val bloomEnvelope = if (t < 0.20) 0.0 else smoothAttack(bloomT, 0.16) * exp(-bloomT * 0.52)
            val leftBloom = (
                0.72 * sin(twoPi * 220.0 * t) +
                    0.48 * sin(twoPi * 277.18 * t + 0.22) +
                    0.31 * sin(twoPi * 329.63 * t + 0.44)
                ) * bloomEnvelope
            val rightBloom = (
                0.72 * sin(twoPi * 220.0 * t + 0.10) +
                    0.48 * sin(twoPi * 277.18 * t + 0.46) +
                    0.31 * sin(twoPi * 329.63 * t + 0.70)
                ) * bloomEnvelope

            val shineT = (t - 0.82).coerceAtLeast(0.0)
            val shineEnvelope = if (t < 0.82) 0.0 else smoothAttack(shineT, 0.025) * exp(-shineT * 2.05)
            val sweep = twoPi * (820.0 * shineT + 210.0 * shineT * shineT)
            val leftShine = (sin(sweep) + 0.33 * sin(twoPi * 1_340.0 * shineT + 0.2)) * shineEnvelope
            val rightShine = (sin(sweep + 0.28) + 0.33 * sin(twoPi * 1_340.0 * shineT + 0.58)) * shineEnvelope

            val left = master * (0.76 * bass + 0.45 * leftBloom + 0.24 * leftShine)
            val right = master * (0.76 * bass + 0.45 * rightBloom + 0.24 * rightShine)
            output[frame * 2] = toPcm16(left)
            output[frame * 2 + 1] = toPcm16(right)
        }
        return output
    }

    private fun smoothAttack(t: Double, duration: Double): Double =
        (t / duration).coerceIn(0.0, 1.0).let { it * it * (3.0 - 2.0 * it) }

    private fun smoothRelease(t: Double, end: Double, duration: Double): Double =
        ((end - t) / duration).coerceIn(0.0, 1.0).let { it * it * (3.0 - 2.0 * it) }

    private fun toPcm16(value: Double): Short =
        (tanh(value * 1.35) * Short.MAX_VALUE * 0.92)
            .toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
}
