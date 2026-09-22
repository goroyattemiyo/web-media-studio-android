package com.goroyattemiyo.wms.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class SafeBoostProcessorTest {
    @Suppress("DEPRECATION")
    @Test fun unityDoesNotChangePcmSamples() {
        val processor = SafeBoostProcessor()
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        val source = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        source.putShort(12000).putShort((-23000).toShort()).putShort(32767).putShort(0).flip()
        processor.queueInput(source)
        val output = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(12000, output.short.toInt())
        assertEquals(-23000, output.short.toInt())
        assertEquals(32767, output.short.toInt())
        assertEquals(0, output.short.toInt())
        assertTrue(processor.measuredBoostDb.isNaN())
    }

    @Suppress("DEPRECATION")
    @Test fun quietPcmIsReallyBoostedByThreeDecibels() {
        val processor = SafeBoostProcessor()
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.setBoostGain(10.0.pow(3.0 / 20.0).toFloat())
        val frames = 48_000
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        repeat(frames) { input.putShort(5000).putShort((-5000).toShort()) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        output.position((frames - 1) * 4)
        assertEquals(7063, output.short.toInt(), 4) // +3 dB after gain ramp.
        assertTrue("measured=${processor.measuredBoostDb}", processor.measuredBoostDb.isFinite())
        assertEquals(3f, processor.measuredBoostDb, 0.15f)
        assertEquals(0L, processor.limitedFrames)
    }

    @Suppress("DEPRECATION")
    @Test fun highLevelPcmIsLimitedBelowFullScale() {
        val processor = SafeBoostProcessor()
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.setBoostGain(2f)
        val frames = 2_048
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        repeat(frames) { input.putShort(30000).putShort((-30000).toShort()) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        var maximum = 0
        while (output.hasRemaining()) maximum = maxOf(maximum, abs(output.short.toInt()))
        assertTrue("peak=$maximum", maximum <= 29_206)
        assertTrue("expected limiter activity", processor.limitedFrames > 0)
    }

    @Suppress("DEPRECATION")
    @Test fun resetDisarmsGainUntilExplicitlyRearmed() {
        val processor = SafeBoostProcessor()
        val format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()
        processor.setBoostGain(2f)
        processor.reset()
        processor.configure(format)
        processor.flush()
        val unity = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        unity.putShort(3000).putShort(3000).flip()
        processor.queueInput(unity)
        assertEquals(3000, processor.output.order(ByteOrder.nativeOrder()).short.toInt())
        processor.setBoostGain(2f) // SoundEngine rearms on the next PCM support callback.
        val signal = ByteBuffer.allocateDirect(48_000 * 4).order(ByteOrder.nativeOrder())
        repeat(48_000) { signal.putShort(3000).putShort(3000) }
        signal.flip()
        processor.queueInput(signal)
        val out = processor.output.order(ByteOrder.nativeOrder())
        out.position((48_000 - 1) * 4)
        assertTrue(out.short.toInt() > 5000)
        assertEquals(6.02f, processor.measuredBoostDb, 0.2f)
    }
}
