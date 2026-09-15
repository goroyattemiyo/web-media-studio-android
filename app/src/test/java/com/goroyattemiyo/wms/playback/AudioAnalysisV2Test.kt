package com.goroyattemiyo.wms.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAnalysisV2Test {
    @Test
    fun silenceProducesNearZeroAnalysis() {
        val frame = AudioAnalysisEngine().analyze(FloatArray(WINDOW_SIZE), null, 48_000)

        assertTrue(frame.rms < EPSILON)
        assertTrue(frame.peak < EPSILON)
        assertTrue(frame.fftBins.all { it < EPSILON })
        assertTrue(frame.bass < EPSILON)
        assertTrue(frame.lowMid < EPSILON)
        assertTrue(frame.mid < EPSILON)
        assertTrue(frame.high < EPSILON)
        assertTrue(frame.spectralCentroid < EPSILON)
    }

    @Test
    fun eightyHertzSineIsBassDominant() {
        val frame = analyzeTone(80f, 48_000)
        assertDominant(frame.bass, frame.lowMid, frame.mid, frame.high)
    }

    @Test
    fun threeHundredHertzSineIsLowMidDominant() {
        val frame = analyzeTone(300f, 48_000)
        assertDominant(frame.lowMid, frame.bass, frame.mid, frame.high)
    }

    @Test
    fun oneKilohertzSineIsMidDominant() {
        val frame = analyzeTone(1_000f, 48_000)
        assertDominant(frame.mid, frame.bass, frame.lowMid, frame.high)
    }

    @Test
    fun eightKilohertzSineIsHighDominant() {
        val frame = analyzeTone(8_000f, 48_000)
        assertDominant(frame.high, frame.bass, frame.lowMid, frame.mid)
    }

    @Test
    fun spectralCentroidOrdersLowBeforeHighFrequency() {
        val low = analyzeTone(80f, 48_000)
        val high = analyzeTone(8_000f, 48_000)
        assertTrue(low.spectralCentroid < high.spectralCentroid)
        assertTrue(low.spectralCentroid in 40f..180f)
        assertTrue(high.spectralCentroid in 7_500f..8_500f)
    }

    @Test
    fun steadyToneHasLowFluxAfterFirstTransition() {
        val engine = AudioAnalysisEngine()
        engine.analyze(FloatArray(WINDOW_SIZE), null, 48_000)
        val transient = engine.analyze(sine(1_000f, 48_000), null, 48_000)
        val steady = engine.analyze(sine(1_000f, 48_000), null, 48_000)

        assertTrue(transient.onsetStrength > steady.onsetStrength)
        assertTrue(steady.spectralFlux < 0.001f)
    }

    @Test
    fun silenceToToneRaisesOnsetStrength() {
        val engine = AudioAnalysisEngine()
        engine.analyze(FloatArray(WINDOW_SIZE), null, 48_000)
        val transient = engine.analyze(sine(300f, 48_000), null, 48_000)
        assertTrue(transient.onsetStrength > 0.1f)
    }

    @Test
    fun frequencyMappingWorksAtFortyFourAndFortyEightKilohertz() {
        listOf(44_100, 48_000).forEach { sampleRate ->
            val frame = analyzeTone(1_000f, sampleRate)
            assertDominant(frame.mid, frame.bass, frame.lowMid, frame.high)
            assertEquals(AudioAnalysisEngine.DEFAULT_BIN_COUNT, frame.fftBins.size)
            assertEquals(sampleRate, frame.sampleRateHz)
        }
    }

    @Test
    fun stereoWaveformsRemainSeparated() {
        val left = sine(440f, 48_000)
        val right = sine(880f, 48_000)
        val frame = AudioAnalysisEngine().analyze(left, right, 48_000)

        assertEquals(2, frame.channelCount)
        assertEquals(AudioAnalysisEngine.WAVEFORM_POINTS, frame.leftWaveform.size)
        assertEquals(AudioAnalysisEngine.WAVEFORM_POINTS, frame.rightWaveform.size)
        assertTrue(!frame.leftWaveform.contentEquals(frame.rightWaveform))
    }

    @UnstableApi
    @Test
    fun pcm16AndFloatSinksPublishStereoFrames() {
        listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT).forEach { encoding ->
            val sink = PcmAnalysisBufferSink()
            try {
                sink.flush(48_000, 2, encoding)
                sink.handleBuffer(stereoPcmBuffer(encoding))
                val deadline = System.nanoTime() + 2_000_000_000L
                while (PcmAudioAnalysisBus.frames.value.sampleRateHz != 48_000 && System.nanoTime() < deadline) {
                    Thread.yield()
                }
                val frame = PcmAudioAnalysisBus.frames.value
                assertEquals(48_000, frame.sampleRateHz)
                assertEquals(2, frame.channelCount)
                assertTrue(frame.rms > 0f)
                assertTrue(!frame.leftWaveform.contentEquals(frame.rightWaveform))
            } finally {
                sink.close()
            }
        }
    }

    private fun analyzeTone(frequency: Float, sampleRate: Int): AudioAnalysisFrame =
        AudioAnalysisEngine().analyze(sine(frequency, sampleRate), null, sampleRate)

    private fun sine(frequency: Float, sampleRate: Int, amplitude: Float = 0.8f): FloatArray =
        FloatArray(WINDOW_SIZE) { index ->
            amplitude * sin(2.0 * PI * frequency * index / sampleRate).toFloat()
        }

    private fun stereoPcmBuffer(encoding: Int): ByteBuffer {
        val bytesPerSample = if (encoding == C.ENCODING_PCM_16BIT) Short.SIZE_BYTES else Float.SIZE_BYTES
        val output = ByteBuffer.allocate(WINDOW_SIZE * 2 * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN)
        val left = sine(440f, 48_000)
        val right = sine(880f, 48_000)
        repeat(WINDOW_SIZE) { index ->
            if (encoding == C.ENCODING_PCM_16BIT) {
                output.putShort((left[index] * Short.MAX_VALUE).toInt().toShort())
                output.putShort((right[index] * Short.MAX_VALUE).toInt().toShort())
            } else {
                output.putFloat(left[index])
                output.putFloat(right[index])
            }
        }
        output.flip()
        return output
    }

    private fun assertDominant(expected: Float, vararg others: Float) {
        assertTrue("Expected $expected to dominate ${others.toList()}", others.all { expected > it * 1.5f })
    }

    private companion object {
        const val WINDOW_SIZE = AudioAnalysisEngine.DEFAULT_WINDOW_SIZE
        const val EPSILON = 0.0001f
    }
}
