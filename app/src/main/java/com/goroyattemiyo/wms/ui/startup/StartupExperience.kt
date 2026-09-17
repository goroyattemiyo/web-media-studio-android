package com.goroyattemiyo.wms.ui.startup

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.goroyattemiyo.wms.R
import java.util.concurrent.atomic.AtomicBoolean

object StartupExperienceSession {
    private val consumed = AtomicBoolean(false)

    fun consumeColdStart(): Boolean = consumed.compareAndSet(false, true)
}

object StartupExperiencePreferences {
    private const val PREFS_NAME = "wms_startup_experience"
    private const val KEY_ANIMATION = "startup_animation_enabled"

    fun animationEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ANIMATION, true)

    fun setAnimationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ANIMATION, enabled)
            .apply()
    }

    // Kept temporarily for binary/source compatibility with the current MainActivity.
    // Startup audio is intentionally disabled and no longer exposed in the UI.
    fun soundEnabled(context: Context): Boolean = false

    fun setSoundEnabled(context: Context, enabled: Boolean) = Unit
}

object StartupSonicLogo {
    fun playIfAllowed(context: Context) = Unit
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun StartupExperienceScreen(
    statusText: String,
    modifier: Modifier = Modifier,
) {
    val entrance = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 520,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0C1426),
                        Color(0xFF060911),
                        Color(0xFF03050A),
                    ),
                    radius = 920f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.wms_emblem),
            contentDescription = "WMS",
            modifier = Modifier
                .size(184.dp)
                .graphicsLayer {
                    val progress = entrance.value
                    alpha = progress
                    val scale = 0.80f + (0.20f * progress)
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}
