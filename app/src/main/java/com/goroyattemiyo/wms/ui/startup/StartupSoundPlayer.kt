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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

object StartupSoundPlayer {
    private const val TAG = "WMS-StartupSound"
    private const val SAMPLE_RATE = 48_000
    private const val DURATION_SECONDS = 2.1
    private const val TOTAL_FRAMES = (SAMPLE_RATE * DURATION_SECONDS).toInt()
    private const val CHUNK_FRAMES = 960 // 20 ms
    private const val PREFILL_FRAMES = 1_920 // 40 ms before play()
    private const val MAX_WAIT_MS = 3_200L
    private const val TABLE_SIZE = 2_048

    private val sineTable = FloatArray(TABLE_SIZE) { index ->
        sin(2.0 * PI * index.toDouble() / TABLE_SIZE.toDouble()).toFloat()
    }

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
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
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
        val startedAt = SystemClock.elapsedRealtime()
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

        val synth = SonicSynth()
        val chunk = ShortArray(CHUNK_FRAMES * 2)

        try {
            Log.i(
                TAG,
                "trackState=${track.state} bufferBytes=$bufferBytes session=${track.audioSessionId} " +
                    "readyIn=${SystemClock.elapsedRealtime() - startedAt}ms",
            )
            require(track.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack not initialized" }
            track.setVolume(0.86f)

            // Generate only a tiny pre-roll, then start playback immediately.
            var frameOffset = 0
            while (frameOffset < PREFILL_FRAMES && frameOffset < TOTAL_FRAMES) {
                val frames = minOf(CHUNK_FRAMES, PREFILL_FRAMES - frameOffset, TOTAL_FRAMES - frameOffset)
                val shorts = synth.render(chunk, frames)
                writeFully(track, chunk, shorts)
                frameOffset += frames
            }
            Log.i(
                TAG,
                "prefill queued: frames=$frameOffset in=${SystemClock.elapsedRealtime() - startedAt}ms",
            )

            track.play()
            Log.i(
                TAG,
                "play called: playState=${track.playState} at=${SystemClock.elapsedRealtime() - startedAt}ms",
            )

            // Keep generating short chunks while AudioTrack is already playing.
            while (frameOffset < TOTAL_FRAMES) {
                val frames = minOf(CHUNK_FRAMES, TOTAL_FRAMES - frameOffset)
                val shorts = synth.render(chunk, frames)
                writeFully(track, chunk, shorts)
                frameOffset += frames
            }
            Log.i(TAG, "pcm queued: frames=$frameOffset")

            val deadline = System.currentTimeMillis() + MAX_WAIT_MS
            while (track.playbackHeadPosition < TOTAL_FRAMES - 128 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20L)
            }
            Log.i(
                TAG,
                "finished: playState=${track.playState} head=${track.playbackHeadPosition}/$TOTAL_FRAMES",
            )
        } finally {
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            track.release()
            runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
        }
    }

    private fun writeFully(track: AudioTrack, buffer: ShortArray, shorts: Int) {
        var offset = 0
        while (offset < shorts) {
            val written = track.write(buffer, offset, shorts - offset, AudioTrack.WRITE_BLOCKING)
            if (written < 0) error("AudioTrack.write failed: $written")
            offset += written
        }
    }

    /**
     * Lightweight real-time synth. A small sine lookup table replaces thousands of Math.sin/exp calls,
     * so the first audio chunk is ready during the splash instead of seconds later.
     */
    private class SonicSynth {
        private val bassA = Oscillator(58.0)
        private val bassB = Oscillator(116.0, 0.16)
        private val bloomL1 = Oscillator(220.0)
        private val bloomL2 = Oscillator(277.18, 0.22)
        private val bloomL3 = Oscillator(329.63, 0.44)
        private val bloomR1 = Oscillator(220.0, 0.10)
        private val bloomR2 = Oscillator(277.18, 0.46)
        private val bloomR3 = Oscillator(329.63, 0.70)
        private val shineL = Oscillator(920.0, 0.20)
        private val shineR = Oscillator(920.0, 0.58)
        private var frame = 0

        fun render(output: ShortArray, frames: Int): Int {
            var out = 0
            repeat(frames) {
                val t = frame.toDouble() / SAMPLE_RATE.toDouble()
                val master = smoothAttack(t, 0.015) * smoothRelease(t, DURATION_SECONDS, 0.28)

                val bassEnvelope = smoothAttack(t, 0.018) * squaredDecay(t / 1.25)
                val bass = (bassA.next() + 0.42 * bassB.next()) * bassEnvelope

                val bloomT = t - 0.18
                val bloomEnvelope = if (bloomT <= 0.0) {
                    0.0
                } else {
                    smoothAttack(bloomT, 0.16) * squaredDecay(bloomT / 1.72)
                }
                val leftBloom = (
                    0.72 * bloomL1.next() +
                        0.48 * bloomL2.next() +
                        0.31 * bloomL3.next()
                    ) * bloomEnvelope
                val rightBloom = (
                    0.72 * bloomR1.next() +
                        0.48 * bloomR2.next() +
                        0.31 * bloomR3.next()
                    ) * bloomEnvelope

                val shineT = t - 0.72
                val shineEnvelope = if (shineT <= 0.0) {
                    0.0
                } else {
                    smoothAttack(shineT, 0.025) * squaredDecay(shineT / 1.18)
                }
                val leftShine = shineL.next() * shineEnvelope
                val rightShine = shineR.next() * shineEnvelope

                val left = master * (0.76 * bass + 0.45 * leftBloom + 0.28 * leftShine)
                val right = master * (0.76 * bass + 0.45 * rightBloom + 0.28 * rightShine)
                output[out++] = toPcm16(left)
                output[out++] = toPcm16(right)
                frame++
            }
            return out
        }
    }

    private class Oscillator(frequency: Double, phaseRadians: Double = 0.0) {
        private var phase = ((phaseRadians / (2.0 * PI)) * TABLE_SIZE).toFloat().let { value ->
            var normalized = value % TABLE_SIZE
            if (normalized < 0f) normalized += TABLE_SIZE
            normalized
        }
        private val step = (frequency * TABLE_SIZE / SAMPLE_RATE.toDouble()).toFloat()

        fun next(): Double {
            val value = sineTable[phase.toInt().coerceIn(0, TABLE_SIZE - 1)].toDouble()
            phase += step
            if (phase >= TABLE_SIZE) phase -= TABLE_SIZE
            return value
        }
    }

    private fun smoothAttack(t: Double, duration: Double): Double =
        (t / duration).coerceIn(0.0, 1.0).let { it * it * (3.0 - 2.0 * it) }

    private fun smoothRelease(t: Double, end: Double, duration: Double): Double =
        ((end - t) / duration).coerceIn(0.0, 1.0).let { it * it * (3.0 - 2.0 * it) }

    private fun squaredDecay(progress: Double): Double {
        val remaining = (1.0 - progress).coerceIn(0.0, 1.0)
        return remaining * remaining
    }

    private fun toPcm16(value: Double): Short {
        val soft = value / (1.0 + abs(value))
        return (soft * Short.MAX_VALUE * 0.98)
            .toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }
}
