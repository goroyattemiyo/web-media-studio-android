package com.goroyattemiyo.wms.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object PcmAudioAnalysisBus : AudioAnalysisDataSource {
    private val mutableFrames = MutableStateFlow(AudioAnalysisFrame())
    override val frames = mutableFrames.asStateFlow()

    internal fun publish(frame: AudioAnalysisFrame) {
        mutableFrames.value = frame
    }

    internal fun clear() {
        mutableFrames.value = AudioAnalysisFrame()
    }
}

@UnstableApi
internal class PcmAnalysisBufferSink : TeeAudioProcessor.AudioBufferSink {
    private var encoding: Int = C.ENCODING_INVALID

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.encoding = encoding
        PcmAudioAnalysisBus.clear()
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val samples = when (encoding) {
            C.ENCODING_PCM_16BIT -> readPcm16(buffer)
            C.ENCODING_PCM_FLOAT -> readPcmFloat(buffer)
            else -> emptyList()
        }
        if (samples.isEmpty()) return

        val level = sqrt(samples.sumOf { (it * it).toDouble() } / samples.size)
            .toFloat()
            .coerceIn(0f, 1f)
        PcmAudioAnalysisBus.publish(
            AudioAnalysisFrame(
                normalizedLevel = (level * 2.2f).coerceIn(0f, 1f),
                waveform = downsample(samples, 32) { it * 0.5f + 0.5f },
                spectrum = downsample(samples, 20) { abs(it) },
            ),
        )
    }

    private fun readPcm16(source: ByteBuffer): List<Float> {
        val input = source.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        val count = (input.remaining() / Short.SIZE_BYTES).coerceAtMost(MAX_SAMPLES)
        return List(count) { input.short / Short.MAX_VALUE.toFloat() }
    }

    private fun readPcmFloat(source: ByteBuffer): List<Float> {
        val input = source.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        val count = (input.remaining() / Float.SIZE_BYTES).coerceAtMost(MAX_SAMPLES)
        return List(count) { input.float.coerceIn(-1f, 1f) }
    }

    private fun downsample(samples: List<Float>, buckets: Int, transform: (Float) -> Float): List<Float> =
        List(buckets) { bucket ->
            val start = bucket * samples.size / buckets
            val end = ((bucket + 1) * samples.size / buckets).coerceAtLeast(start + 1)
            samples.subList(start, end.coerceAtMost(samples.size))
                .map(transform)
                .average()
                .toFloat()
                .coerceIn(0f, 1f)
        }

    private companion object {
        const val MAX_SAMPLES = 4_096
    }
}

@UnstableApi
internal class AnalysisRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
        .setAudioProcessors(arrayOf<AudioProcessor>(TeeAudioProcessor(PcmAnalysisBufferSink())))
        .build()
}
