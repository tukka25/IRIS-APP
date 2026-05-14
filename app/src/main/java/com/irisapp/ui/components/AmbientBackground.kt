package com.irisapp.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure-black background with 3 large ambient light orbs that orbit slowly,
 * creating an aurora / volumetric-light effect.
 */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val t = rememberInfiniteTransition(label = "ambient_bg")

    // Full 0→2π orbit angles at different speeds → circular orbital paths
    val a1 by t.animateFloat(
        0f, (2f * PI).toFloat(),
        infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "a1"
    )
    val a2 by t.animateFloat(
        0f, (2f * PI).toFloat(),
        infiniteRepeatable(tween(7200, easing = LinearEasing), RepeatMode.Restart),
        label = "a2"
    )
    val a3 by t.animateFloat(
        0f, (2f * PI).toFloat(),
        infiniteRepeatable(tween(9800, easing = LinearEasing), RepeatMode.Restart),
        label = "a3"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // ── Cyan orb — orbits upper-left quadrant ────────────────────────
            val cyanX = w * 0.22f + cos(a1) * w * 0.22f
            val cyanY = h * 0.08f + sin(a1) * h * 0.14f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00F2FE).copy(alpha = 0.28f),
                        Color(0xFF5EF2FF).copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(cyanX, cyanY),
                    radius = w * 0.70f
                ),
                radius = w * 0.70f,
                center = Offset(cyanX, cyanY)
            )

            // ── Violet orb — orbits upper-right, counter-phase ───────────────
            val violetX = w * 0.78f + cos(a2 + PI.toFloat()) * w * 0.18f
            val violetY = h * 0.10f + sin(a2 + PI.toFloat()) * h * 0.12f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFB200FF).copy(alpha = 0.22f),
                        Color(0xFF6F00FF).copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(violetX, violetY),
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = Offset(violetX, violetY)
            )

            // ── Magenta/pink accent — drifts across mid-screen ───────────────
            val magX = w * 0.50f + cos(a3 + 1.0f) * w * 0.28f
            val magY = h * 0.30f + sin(a3 * 0.6f) * h * 0.12f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF006E).copy(alpha = 0.10f),
                        Color(0xFFB200FF).copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(magX, magY),
                    radius = w * 0.55f
                ),
                radius = w * 0.55f,
                center = Offset(magX, magY)
            )
        }

        content()
    }
}
