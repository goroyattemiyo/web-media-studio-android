package com.goroyattemiyo.wms.ui.startup

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goroyattemiyo.wms.R
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
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

        Thread(
            {
                runCatching { playPcm(audioManager) }
            },
            "wms-startup-sonic-logo",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun playPcm(audioManager: AudioManager) {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
        val focusGranted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        val pcm = renderPcm()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
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
            track.setVolume(0.72f)
            track.play()
            Thread.sleep(DURATION_MS + 140L)
        } finally {
            runCatching { track.stop() }
            track.release()
            if (focusGranted) {
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
        }
    }

    private fun renderPcm(): ShortArray {
        val frames = (SAMPLE_RATE * DURATION_SECONDS).toInt()
        val output = ShortArray(frames * 2)
        val twoPi = 2.0 * PI

        repeat(frames) { frame ->
            val t = frame.toDouble() / SAMPLE_RATE.toDouble()
            val masterFade = smoothAttack(t, 0.018) * smoothRelease(t, DURATION_SECONDS, 0.36)

            val lowEnvelope = smoothAttack(t, 0.025) * exp(-t * 1.18)
            val low = (
                sin(twoPi * 52.0 * t) +
                    0.46 * sin(twoPi * 104.0 * t + 0.18)
                ) * lowEnvelope

            val bloomTime = (t - 0.24).coerceAtLeast(0.0)
            val bloomEnvelope = if (t < 0.24) 0.0 else smoothAttack(bloomTime, 0.22) * exp(-bloomTime * 0.40)
            val leftBloom = (
                0.74 * sin(twoPi * 196.0 * t) +
                    0.52 * sin(twoPi * 246.94 * t + 0.20) +
                    0.34 * sin(twoPi * 293.66 * t + 0.46)
                ) * bloomEnvelope
            val rightBloom = (
                0.74 * sin(twoPi * 196.0 * t + 0.10) +
                    0.52 * sin(twoPi * 246.94 * t + 0.38) +
                    0.34 * sin(twoPi * 293.66 * t + 0.66)
                ) * bloomEnvelope

            val shimmerTime = (t - 1.02).coerceAtLeast(0.0)
            val shimmerEnvelope = if (t < 1.02) 0.0 else smoothAttack(shimmerTime, 0.035) * exp(-shimmerTime * 1.82)
            val shimmerPhase = twoPi * (760.0 * shimmerTime + 185.0 * shimmerTime * shimmerTime)
            val leftShimmer = (
                sin(shimmerPhase) + 0.42 * sin(twoPi * 1_180.0 * shimmerTime + 0.28)
                ) * shimmerEnvelope
            val rightShimmer = (
                sin(shimmerPhase + 0.24) + 0.42 * sin(twoPi * 1_180.0 * shimmerTime + 0.64)
                ) * shimmerEnvelope

            val left = masterFade * (0.72 * low + 0.42 * leftBloom + 0.24 * leftShimmer)
            val right = masterFade * (0.72 * low + 0.42 * rightBloom + 0.24 * rightShimmer)

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
        (tanh(value * 1.30) * Short.MAX_VALUE * 0.88)
            .toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
}

@Composable
fun StartupExperienceScreen(
    statusText: String,
    modifier: Modifier = Modifier,
) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = tween(720, easing = FastOutSlowInEasing),
        )
    }

    val transition = rememberInfiniteTransition(label = "wms-startup")
    val breathe by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.018f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logo-breathe",
    )
    val orbitRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbit-rotation",
    )
    val counterRotation by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "counter-rotation",
    )
    val glowPulse by transition.animateFloat(
        initialValue = 0.56f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow-pulse",
    )
    val loadingAlpha by transition.animateFloat(
        initialValue = 0.50f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(680, easing = FastOutSlowInEasing),
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
        Canvas(modifier = Modifier.size(330.dp)) {
            val center = this.center
            val baseRadius = size.minDimension * 0.31f

            drawCircle(
                color = cyan.copy(alpha = 0.07f + 0.06f * glowPulse),
                radius = baseRadius * 1.27f,
            )
            drawCircle(
                color = purple.copy(alpha = 0.08f + 0.05f * glowPulse),
                radius = baseRadius * 1.07f,
            )

            rotate(orbitRotation, pivot = center) {
                drawArc(
                    color = cyan.copy(alpha = 0.90f),
                    startAngle = -16f,
                    sweepAngle = 82f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                    size = Size(baseRadius * 2f, baseRadius * 2f),
                    style = Stroke(width = 5.5f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = purple.copy(alpha = 0.72f),
                    startAngle = 154f,
                    sweepAngle = 54f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                    size = Size(baseRadius * 2f, baseRadius * 2f),
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round),
                )
                repeat(4) { index ->
                    val angle = Math.toRadians((index * 90.0) + 24.0)
                    val particle = Offset(
                        x = center.x + cos(angle).toFloat() * baseRadius,
                        y = center.y + sin(angle).toFloat() * baseRadius,
                    )
                    drawCircle(
                        color = if (index % 2 == 0) cyan else blue,
                        radius = if (index == 0) 8.5f else 5.5f,
                        center = particle,
                        alpha = 0.65f + 0.30f * glowPulse,
                    )
                }
            }

            val outerRadius = baseRadius * 1.27f
            rotate(counterRotation, pivot = center) {
                drawArc(
                    color = blue.copy(alpha = 0.46f),
                    startAngle = 20f,
                    sweepAngle = 128f,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = Size(outerRadius * 2f, outerRadius * 2f),
                    style = Stroke(width = 2.6f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = purple.copy(alpha = 0.38f),
                    startAngle = 210f,
                    sweepAngle = 86f,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = Size(outerRadius * 2f, outerRadius * 2f),
                    style = Stroke(width = 2.2f, cap = StrokeCap.Round),
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
                        val entry = entrance.value
                        alpha = entry
                        scaleX = (0.62f + 0.38f * entry) * breathe
                        scaleY = (0.62f + 0.38f * entry) * breathe
                        rotationZ = -12f * (1f - entry)
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
