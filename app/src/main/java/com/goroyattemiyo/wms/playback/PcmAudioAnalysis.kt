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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
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

/**
 * Audio-thread work is limited to decoding PCM into fixed primitive rings. FFT, smoothing,
 * waveform reduction, and frame allocation run on one bounded analysis worker.
 */
@UnstableApi
internal class PcmAnalysisBufferSink(
    private val analyzer: AudioAnalysisEngine = AudioAnalysisEngine(),
) : TeeAudioProcessor.AudioBufferSink, AutoCloseable {
    private val leftRing = FloatArray(analyzer.windowSize)
    private val rightRing = FloatArray(analyzer.windowSize)
    private val analysisLeft = FloatArray(analyzer.windowSize)
    private val analysisRight = FloatArray(analyzer.windowSize)
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "wms-audio-analysis").apply { isDaemon = true }
    }
    private val analysisPending = AtomicBoolean(false)

    @Volatile private var sampleRateHz = 0
    @Volatile private var channelCount = 0
    @Volatile private var encoding = C.ENCODING_INVALID
    @Volatile private var writeIndex = 0
    @Volatile private var availableSamples = 0
    @Volatile private var generation = 0L
    @Volatile private var writeSequence = 0L
    private var lastScheduledNanos = 0L
    @Volatile private var closed = false

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        if (closed) return
        writeSequence++
        try {
            this.sampleRateHz = sampleRateHz
            this.channelCount = channelCount
            this.encoding = encoding
            writeIndex = 0
            availableSamples = 0
            generation++
            leftRing.fill(0f)
            rightRing.fill(0f)
        } finally {
            writeSequence++
        }
        worker.execute { analyzer.reset() }
        PcmAudioAnalysisBus.clear()
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (closed) return
        val now = System.nanoTime()
        writeSequence++
        try {
            copyPcmIntoRing(buffer)
        } finally {
            writeSequence++
        }
        if (now - lastScheduledNanos < ANALYSIS_INTERVAL_NANOS) return
        if (!analysisPending.compareAndSet(false, true)) return
        lastScheduledNanos = now
        val requestedGeneration = generation
        worker.execute {
            try {
                val frame = analyzeStableSnapshot(requestedGeneration) ?: return@execute
                if (!closed && generation == requestedGeneration) PcmAudioAnalysisBus.publish(frame)
            } finally {
                analysisPending.set(false)
            }
        }
    }

    private fun copyPcmIntoRing(source: ByteBuffer) {
        if (channelCount !in 1..2) return
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> Short.SIZE_BYTES
            C.ENCODING_PCM_FLOAT -> Float.SIZE_BYTES
            else -> return
        }
        source.order(ByteOrder.LITTLE_ENDIAN)
        val frameBytes = bytesPerSample * channelCount
        var offset = source.position()
        while (offset + frameBytes <= source.limit()) {
            val left = readSample(source, offset)
            offset += bytesPerSample
            val right = if (channelCount == 2) {
                readSample(source, offset).also { offset += bytesPerSample }
            } else {
                left
            }
            leftRing[writeIndex] = left
            rightRing[writeIndex] = right
            writeIndex = (writeIndex + 1) % leftRing.size
            availableSamples = (availableSamples + 1).coerceAtMost(leftRing.size)
        }
    }

    private fun readSample(input: ByteBuffer, offset: Int): Float = when (encoding) {
        C.ENCODING_PCM_16BIT -> (input.getShort(offset) / 32768f).coerceIn(-1f, 1f)
        C.ENCODING_PCM_FLOAT -> input.getFloat(offset).coerceIn(-1f, 1f)
        else -> 0f
    }

    /** Seqlock-style snapshot: analysis drops a contested frame instead of blocking audio. */
    private fun analyzeStableSnapshot(requestedGeneration: Long): AudioAnalysisFrame? {
        val sequenceBefore = writeSequence
        if (sequenceBefore and 1L != 0L || requestedGeneration != generation) return null
        val capturedSampleRate = sampleRateHz
        val capturedChannels = channelCount
        val capturedAvailable = availableSamples
        val capturedWriteIndex = writeIndex
        if (capturedAvailable == 0 || capturedSampleRate <= 0) return null
        analysisLeft.fill(0f)
        analysisRight.fill(0f)
        val padding = analyzer.windowSize - capturedAvailable
        val oldestIndex = (capturedWriteIndex - capturedAvailable + analyzer.windowSize) % analyzer.windowSize
        repeat(capturedAvailable) { offset ->
            val ringIndex = (oldestIndex + offset) % analyzer.windowSize
            analysisLeft[padding + offset] = leftRing[ringIndex]
            analysisRight[padding + offset] = rightRing[ringIndex]
        }
        if (writeSequence != sequenceBefore || generation != requestedGeneration) return null
        return analyzer.analyze(
            left = analysisLeft,
            right = analysisRight.takeIf { capturedChannels == 2 },
            sampleRateHz = capturedSampleRate,
        )
    }

    override fun close() {
        closed = true
        worker.shutdownNow()
        PcmAudioAnalysisBus.clear()
    }

    private companion object {
        const val ANALYSIS_INTERVAL_NANOS = 40_000_000L // 25 Hz maximum publication rate.
    }
}

@UnstableApi
internal class AnalysisRenderersFactory(context: Context) : DefaultRenderersFactory(context), AutoCloseable {
    private val analysisSink = PcmAnalysisBufferSink()

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
        .setAudioProcessors(arrayOf<AudioProcessor>(TeeAudioProcessor(analysisSink)))
        .build()

    override fun close() = analysisSink.close()
}
