package com.goroyattemiyo.wms.playback

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor

/** FFT sees the original decoded signal; gain/limiter is last in the app PCM chain. */
@UnstableApi
internal class SoundRenderersFactory(context: Context) : DefaultRenderersFactory(context), AutoCloseable {
    private val analysisSink = PcmAnalysisBufferSink()
    val boostProcessor = SafeBoostProcessor()

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
        .setAudioProcessors(arrayOf<AudioProcessor>(TeeAudioProcessor(analysisSink), boostProcessor))
        .build()

    override fun close() {
        boostProcessor.setBoostGain(1f)
        analysisSink.close()
    }
}
