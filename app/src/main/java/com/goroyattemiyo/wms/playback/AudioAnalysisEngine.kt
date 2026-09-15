package com.goroyattemiyo.wms.playback

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure, allocation-bounded FFT analysis. This class is owned by the analysis worker. */
internal class AudioAnalysisEngine(
    val windowSize: Int = DEFAULT_WINDOW_SIZE,
    val binCount: Int = DEFAULT_BIN_COUNT,
) {
    init {
        require(windowSize > 1 && windowSize and (windowSize - 1) == 0) {
            "FFT window size must be a power of two"
        }
        require(binCount > 0)
    }

    private val hann = FloatArray(windowSize) { index ->
        (0.5 - 0.5 * cos(2.0 * PI * index / (windowSize - 1))).toFloat()
    }
    private val hannSum = hann.sum().coerceAtLeast(1f)
    private val real = FloatArray(windowSize)
    private val imaginary = FloatArray(windowSize)
    private val magnitudes = FloatArray(windowSize / 2 + 1)
    private val previousBins = FloatArray(binCount)
    private val rawBins = FloatArray(binCount)
    private val smoothedBins = FloatArray(binCount)
    private var hasPreviousSpectrum = false
    private var smoothedRms = 0f
    private var smoothedPeak = 0f
    private var smoothedBass = 0f
    private var smoothedLowMid = 0f
    private var smoothedMid = 0f
    private var smoothedHigh = 0f
    private var previousRawPeak = 0f

    fun reset() {
        previousBins.fill(0f)
        smoothedBins.fill(0f)
        hasPreviousSpectrum = false
        smoothedRms = 0f
        smoothedPeak = 0f
        smoothedBass = 0f
        smoothedLowMid = 0f
        smoothedMid = 0f
        smoothedHigh = 0f
        previousRawPeak = 0f
    }

    fun analyze(
        left: FloatArray,
        right: FloatArray?,
        sampleRateHz: Int,
        timestampNanos: Long = System.nanoTime(),
    ): AudioAnalysisFrame {
        require(sampleRateHz > 0)
        val inputCount = min(windowSize, left.size)
        val inputOffset = (left.size - inputCount).coerceAtLeast(0)
        val padding = windowSize - inputCount
        var sumSquares = 0.0
        var rawPeak = 0f

        repeat(windowSize) { index ->
            val sourceIndex = index - padding
            val mono = if (sourceIndex >= 0) {
                val leftValue = left[inputOffset + sourceIndex].coerceIn(-1f, 1f)
                val rightValue = right?.getOrNull(inputOffset + sourceIndex)?.coerceIn(-1f, 1f)
                if (rightValue == null) leftValue else (leftValue + rightValue) * 0.5f
            } else {
                0f
            }
            sumSquares += mono * mono
            rawPeak = max(rawPeak, kotlin.math.abs(mono))
            real[index] = mono * hann[index]
            imaginary[index] = 0f
        }

        Radix2Fft.transform(real, imaginary)
        repeat(magnitudes.size) { index ->
            magnitudes[index] = (2f * hypot(real[index], imaginary[index]) / hannSum)
                .coerceIn(0f, 1f)
        }
        magnitudes[0] = 0f

        logarithmicBins(sampleRateHz, rawBins)
        var positiveDelta = 0f
        var maxDelta = 0f
        repeat(binCount) { index ->
            val delta = (rawBins[index] - previousBins[index]).coerceAtLeast(0f)
            positiveDelta += delta
            maxDelta = max(maxDelta, delta)
            smoothedBins[index] = attackRelease(smoothedBins[index], rawBins[index])
            previousBins[index] = rawBins[index]
        }

        val rawRms = sqrt(sumSquares / windowSize).toFloat().coerceIn(0f, 1f)
        smoothedRms = attackRelease(smoothedRms, rawRms)
        smoothedPeak = attackRelease(smoothedPeak, rawPeak)
        smoothedBass = attackRelease(smoothedBass, bandEnergy(sampleRateHz, 20f, 160f))
        smoothedLowMid = attackRelease(smoothedLowMid, bandEnergy(sampleRateHz, 160f, 500f))
        smoothedMid = attackRelease(smoothedMid, bandEnergy(sampleRateHz, 500f, 2_000f))
        smoothedHigh = attackRelease(
            smoothedHigh,
            bandEnergy(sampleRateHz, 2_000f, min(20_000f, sampleRateHz * 0.5f)),
        )

        val spectralFlux = positiveDelta / binCount
        val onsetStrength = if (hasPreviousSpectrum) {
            (maxDelta * 0.72f + spectralFlux * 5f + (rawPeak - previousRawPeak).coerceAtLeast(0f) * 0.25f)
                .coerceIn(0f, 1f)
        } else {
            0f
        }
        hasPreviousSpectrum = true
        previousRawPeak = rawPeak

        val animationSeconds = timestampNanos / 1_000_000_000.0
        val leftWaveform = downsample(left, WAVEFORM_POINTS)
        return AudioAnalysisFrame(
            rms = smoothedRms,
            peak = smoothedPeak,
            normalizedLevel = (smoothedRms * 2.2f).coerceIn(0f, 1f),
            waveform = leftWaveform,
            leftWaveform = leftWaveform,
            rightWaveform = right?.let { downsample(it, WAVEFORM_POINTS) } ?: FloatArray(0),
            fftBins = smoothedBins.copyOf(),
            bass = (smoothedBass * BAND_GAIN).coerceIn(0f, 1f),
            lowMid = (smoothedLowMid * BAND_GAIN).coerceIn(0f, 1f),
            mid = (smoothedMid * BAND_GAIN).coerceIn(0f, 1f),
            high = (smoothedHigh * BAND_GAIN).coerceIn(0f, 1f),
            spectralCentroid = spectralCentroid(sampleRateHz),
            spectralFlux = spectralFlux.coerceIn(0f, 1f),
            onsetStrength = onsetStrength,
            phase = (animationSeconds % 1.0).toFloat(),
            animationTimeSeconds = animationSeconds.toFloat(),
            sampleRateHz = sampleRateHz,
            channelCount = if (right == null) 1 else 2,
        )
    }

    private fun logarithmicBins(sampleRateHz: Int, output: FloatArray) {
        val nyquist = sampleRateHz * 0.5f
        val maxFrequency = min(20_000f, nyquist)
        val minFrequency = min(MIN_FREQUENCY_HZ, maxFrequency * 0.5f)
        val logRange = ln(maxFrequency / minFrequency)
        repeat(binCount) { index ->
            val lower = minFrequency * kotlin.math.exp(logRange * index / binCount)
            val upper = minFrequency * kotlin.math.exp(logRange * (index + 1) / binCount)
            val start = ceil(lower * windowSize / sampleRateHz).toInt().coerceIn(1, magnitudes.lastIndex)
            val endExclusive = ceil(upper * windowSize / sampleRateHz).toInt()
                .coerceIn(start + 1, magnitudes.size)
            var energy = 0.0
            for (fftIndex in start until endExclusive) {
                val magnitude = magnitudes[fftIndex]
                energy += magnitude * magnitude
            }
            output[index] = sqrt(energy).toFloat().coerceIn(0f, 1f)
        }
    }

    private fun bandEnergy(sampleRateHz: Int, lowHz: Float, highHz: Float): Float {
        if (highHz <= lowHz) return 0f
        val start = ceil(lowHz * windowSize / sampleRateHz).toInt().coerceIn(1, magnitudes.lastIndex)
        val endExclusive = ceil(highHz * windowSize / sampleRateHz).toInt()
            .coerceIn(start + 1, magnitudes.size)
        var energy = 0.0
        for (index in start until endExclusive) {
            val magnitude = magnitudes[index]
            energy += magnitude * magnitude
        }
        return sqrt(energy).toFloat().coerceIn(0f, 1f)
    }

    private fun spectralCentroid(sampleRateHz: Int): Float {
        var weighted = 0.0
        var energy = 0.0
        for (index in 1 until magnitudes.size) {
            val power = magnitudes[index] * magnitudes[index]
            val frequency = index.toDouble() * sampleRateHz / windowSize
            weighted += frequency * power
            energy += power
        }
        return if (energy <= SILENCE_EPSILON) 0f else (weighted / energy).toFloat()
    }

    private fun downsample(samples: FloatArray, points: Int): FloatArray {
        if (samples.isEmpty()) return FloatArray(0)
        return FloatArray(points) { point ->
            val start = point * samples.size / points
            val end = ((point + 1) * samples.size / points).coerceAtLeast(start + 1).coerceAtMost(samples.size)
            var sum = 0f
            for (index in start until end) sum += samples[index]
            (sum / (end - start)).coerceIn(-1f, 1f)
        }
    }

    private fun attackRelease(previous: Float, target: Float): Float {
        val coefficient = if (target > previous) ATTACK else RELEASE
        return previous + (target - previous) * coefficient
    }

    companion object {
        const val DEFAULT_WINDOW_SIZE = 2_048
        const val DEFAULT_BIN_COUNT = 48
        const val WAVEFORM_POINTS = 128
        private const val MIN_FREQUENCY_HZ = 20f
        private const val ATTACK = 0.72f
        private const val RELEASE = 0.16f
        private const val BAND_GAIN = 1.45f
        private const val SILENCE_EPSILON = 1e-12
    }
}

/** In-place O(N log N) radix-2 Cooley-Tukey FFT. */
internal object Radix2Fft {
    fun transform(real: FloatArray, imaginary: FloatArray) {
        require(real.size == imaginary.size)
        val size = real.size
        require(size > 1 && size and (size - 1) == 0)

        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val realValue = real[i]
                real[i] = real[j]
                real[j] = realValue
                val imaginaryValue = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = imaginaryValue
            }
        }

        var length = 2
        while (length <= size) {
            val angle = -2.0 * PI / length
            val stepReal = cos(angle).toFloat()
            val stepImaginary = sin(angle).toFloat()
            var start = 0
            while (start < size) {
                var twiddleReal = 1f
                var twiddleImaginary = 0f
                for (offset in 0 until length / 2) {
                    val even = start + offset
                    val odd = even + length / 2
                    val oddReal = real[odd] * twiddleReal - imaginary[odd] * twiddleImaginary
                    val oddImaginary = real[odd] * twiddleImaginary + imaginary[odd] * twiddleReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextReal = twiddleReal * stepReal - twiddleImaginary * stepImaginary
                    twiddleImaginary = twiddleReal * stepImaginary + twiddleImaginary * stepReal
                    twiddleReal = nextReal
                }
                start += length
            }
            length = length shl 1
        }
    }
}
