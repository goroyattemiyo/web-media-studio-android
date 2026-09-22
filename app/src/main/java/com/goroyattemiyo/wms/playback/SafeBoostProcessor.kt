package com.goroyattemiyo.wms.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Optional WMS PCM gain followed by a linked sample-peak limiter. Neither true-peak nor
 * downstream codec, Android volume, amplifier or speaker clipping protection is guaranteed.
 * No per-frame allocations or timeline changes; the unity path is byte-for-byte unchanged.
 * Android AudioEffect EQ must remain OFF while boost is active.
 */
@UnstableApi
internal class SafeBoostProcessor : BaseAudioProcessor() {
    @Volatile var pcmSupported: Boolean = false
        private set
    @Volatile var limitedFrames: Long = 0
        private set
    @Volatile var measuredBoostDb: Float = Float.NaN
        private set
    @Volatile var onPcmSupportChanged: ((Boolean) -> Unit)? = null
    @Volatile private var targetGain = 1f
    @Volatile private var forceUnity = false
    private var currentGain = 1f
    private var limiterReduction = 1f
    private var sampleRate = 48000
    private var measurementFrames = 0
    private var inputEnergy = 0.0
    private var outputEnergy = 0.0

    fun setBoostGain(gain: Float) {
        val accepted = gain.coerceIn(1f, 2f)
        if (targetGain != accepted) {
            targetGain = accepted
            resetMeasurement()
        }
        if (accepted <= 1f) forceUnity = true
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val accepted = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        pcmSupported = accepted
        onPcmSupportChanged?.invoke(accepted)
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(1)
        return if (accepted) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        currentGain = 1f
        limiterReduction = 1f
        resetMeasurement()
    }

    override fun onReset() {
        pcmSupported = false
        currentGain = 1f
        limiterReduction = 1f
        targetGain = 1f
        forceUnity = false
        resetMeasurement()
    }

    private fun resetMeasurement() {
        measurementFrames = 0
        inputEnergy = 0.0
        outputEnergy = 0.0
        measuredBoostDb = Float.NaN
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val output = replaceOutputBuffer(remaining).order(ByteOrder.nativeOrder())
        inputBuffer.order(ByteOrder.nativeOrder())
        if (forceUnity) {
            currentGain = 1f
            limiterReduction = 1f
            forceUnity = false
        }
        if (targetGain <= 1f && currentGain <= 1f && limiterReduction >= 1f) {
            measuredBoostDb = Float.NaN
            output.put(inputBuffer)
            output.flip()
            return
        }
        val floatPcm = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (floatPcm) 4 else 2
        val channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        val frameBytes = bytesPerSample * channelCount
        val gainStep = 1f / (sampleRate * 0.015f).coerceAtLeast(1f)
        val releaseStep = 1f / (sampleRate * 0.080f).coerceAtLeast(1f)
        while (inputBuffer.remaining() >= frameBytes) {
            val frameStart = inputBuffer.position()
            var peak = 0f
            for (channel in 0 until channelCount) {
                val offset = frameStart + channel * bytesPerSample
                val value = if (floatPcm) inputBuffer.getFloat(offset) else inputBuffer.getShort(offset) / 32768f
                peak = maxOf(peak, if (value.isFinite()) abs(value) else 1f)
            }
            currentGain += (targetGain - currentGain) * gainStep
            val requested = if (peak > 0f) min(1f, OUTPUT_CEILING / (peak * currentGain)) else 1f
            limiterReduction = if (requested < limiterReduction) requested else
                (limiterReduction + (requested - limiterReduction) * releaseStep).coerceAtMost(1f)
            if (limiterReduction < 0.999f) limitedFrames++
            val appliedGain = currentGain * limiterReduction
            for (channel in 0 until channelCount) {
                val offset = frameStart + channel * bytesPerSample
                val input = if (floatPcm) inputBuffer.getFloat(offset) else inputBuffer.getShort(offset) / 32768f
                val finite = if (input.isFinite()) input else 0f
                val value = (finite * appliedGain).coerceIn(-OUTPUT_CEILING, OUTPUT_CEILING)
                if (floatPcm) output.putFloat(value) else
                    output.putShort((value * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
                inputEnergy += finite.toDouble() * finite.toDouble()
                outputEnergy += value.toDouble() * value.toDouble()
            }
            inputBuffer.position(frameStart + frameBytes)
            measurementFrames++
            if (measurementFrames >= (sampleRate / 4).coerceAtLeast(1)) {
                val count = measurementFrames * channelCount
                measuredBoostDb = if (inputEnergy > count * 1e-8 && outputEnergy > 0.0) {
                    (10.0 * log10(outputEnergy / inputEnergy)).toFloat()
                } else Float.NaN
                measurementFrames = 0
                inputEnergy = 0.0
                outputEnergy = 0.0
            }
        }
        if (inputBuffer.hasRemaining()) output.put(inputBuffer)
        output.flip()
    }

    private companion object {
        const val OUTPUT_CEILING = 0.8912509f // -1 dBFS sample-peak, not true-peak protection.
    }
}
