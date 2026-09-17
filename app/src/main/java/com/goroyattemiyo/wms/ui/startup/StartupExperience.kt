package com.goroyattemiyo.wms.ui.startup

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goroyattemiyo.wms.R
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

object StartupExperienceSession {
    private val consumed = AtomicBoolean(false)

    fun consumeColdStart(): Boolean = consumed.compareAndSet(false, true)
}

object StartupExperiencePreferences {
    private const val PREFS_NAME = "wms_startup_experience"
    private const val KEY_ANIMATION = "startup_animation_enabled"
    private const val KEY_SOUND = "startup_sound_enabled"

    fun animationEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ANIMATION, true)

    fun soundEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SOUND, true)

    fun setAnimationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ANIMATION, enabled)
            .apply()
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SOUND, enabled)
            .apply()
    }
}

object StartupSonicLogo {
    private const val SAMPLE_RATE = 48_000
    private const val DURATION_SECONDS = 2.4
    private const val DURATION_MS = 2_400L

    fun playIfAllowed(context: Context) {
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(AudioManager::class.java) ?: return
        if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0) return
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        if (audioManager.isMusicActive) return

        Thread(
            {
                runCatching { playPcm() }
            },
            "wms-startup-sonic-logo",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun playPcm() {
        val pcm = renderPcm()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
            .build()

        try {
            if (track.state != AudioTrack.STATE_INITIALIZED) return
            track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            track.setVolume(0.34f)
            track.play()
            Thread.sleep(DURATION_MS + 120L)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun renderPcm(): ShortArray {
        val frames = (SAMPLE_RATE * DURATION_SECONDS).toInt()
        val output = ShortArray(frames * 2)
        val twoPi = 2.0 * PI

        repeat(frames) { frame ->
            val t = frame.toDouble() / SAMPLE_RATE.toDouble()
            val masterFade = smoothAttack(t, 0.025) * smoothRelease(t, DURATION_SECONDS, 0.42)

            val lowEnvelope = smoothAttack(t, 0.035) * exp(-t * 1.45)
            val low = (
                sin(twoPi * 55.0 * t) +
                    0.34 * sin(twoPi * 110.0 * t + 0.2)
                ) * lowEnvelope

            val bloomTime = (t - 0.32).coerceAtLeast(0.0)
            val bloomEnvelope = if (t < 0.32) 0.0 else smoothAttack(bloomTime, 0.34) * exp(-bloomTime * 0.48)
            val leftBloom = (
                0.70 * sin(twoPi * 220.0 * t) +
                    0.44 * sin(twoPi * 277.18 * t + 0.24) +
                    0.30 * sin(twoPi * 329.63 * t + 0.48)
                ) * bloomEnvelope
            val rightBloom = (
                0.70 * sin(twoPi * 220.0 * t + 0.12) +
                    0.44 * sin(twoPi * 277.18 * t + 0.42) +
                    0.30 * sin(twoPi * 329.63 * t + 0.65)
                ) * bloomEnvelope

            val shimmerTime = (t - 1.18).coerceAtLeast(0.0)
            val shimmerEnvelope = if (t < 1.18) 0.0 else smoothAttack(shimmerTime, 0.045) * exp(-shimmerTime * 2.25)
            val shimmerPhase = twoPi * (680.0 * shimmerTime + 150.0 * shimmerTime * shimmerTime)
            val leftShimmer = (
                sin(shimmerPhase) + 0.38 * sin(twoPi * 1_020.0 * shimmerTime + 0.3)
                ) * shimmerEnvelope
            val rightShimmer = (
                sin(shimmerPhase + 0.26) + 0.38 * sin(twoPi * 1_020.0 * shimmerTime + 0.62)
                ) * shimmerEnvelope

            val left = masterFade * (0.62 * low + 0.34 * leftBloom + 0.18 * leftShimmer)
            val right = masterFade * (0.62 * low + 0.34 * rightBloom + 0.18 * rightShimmer)

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
        (tanh(value * 1.35) * Short.MAX_VALUE * 0.82)
            .toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
}

@Composable
fun StartupExperienceScreen(
    statusText: String,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "wms-startup")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(
            animation = tween(980, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logo-pulse",
    )
    val tilt by transition.animateFloat(
        initialValue = -2.3f,
        targetValue = 2.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logo-tilt",
    )
    val ringProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring-wave",
    )
    val loadingAlpha by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(720, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loading-alpha",
    )

    val cyan = Color(0xFF3AF2FF)
    val blue = Color(0xFF1D9CFF)
    val purple = Color(0xFF9B4FFF)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF101A31), Color(0xFF070A12), Color(0xFF03050A)),
                    radius = 980f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(300.dp)) {
            repeat(3) { index ->
                val local = (ringProgress + index * 0.31f) % 1f
                val radius = size.minDimension * (0.20f + local * 0.28f)
                val alpha = (1f - local) * (0.22f - index * 0.035f)
                drawCircle(
                    color = when (index) {
                        0 -> cyan
                        1 -> blue
                        else -> purple
                    }.copy(alpha = alpha.coerceAtLeast(0f)),
                    radius = radius,
                    style = Stroke(width = 2.2f + (1f - local) * 3.2f, cap = StrokeCap.Round),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.wms_emblem),
                contentDescription = "WMS",
                modifier = Modifier
                    .size(176.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                        rotationZ = tilt
                    },
            )
            Text(
                text = "WMS",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 7.sp,
                modifier = Modifier.padding(start = 7.dp),
            )
            Text(
                text = "NOW LOADING…",
                color = cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.alpha(loadingAlpha),
            )
            Text(
                text = statusText,
                color = Color(0xFF9AA9C3),
                fontSize = 11.sp,
                letterSpacing = 0.6.sp,
            )
        }
    }
}
