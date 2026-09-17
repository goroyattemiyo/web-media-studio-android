package com.goroyattemiyo.wms.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
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
}
