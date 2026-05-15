package com.irisapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.irisapp.ui.theme.CyanAccent
import com.irisapp.ui.theme.ElectricCyan
import com.irisapp.ui.theme.GreenSuccess
import com.irisapp.ui.theme.LiquidViolet

enum class BlobState { IDLE, LISTENING, PROCESSING, DONE }

@Composable
fun BlobPersona(
    blobState: BlobState,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "blob_ambient")

    // ── Continuous ambient animations ──────────────────────────────────────
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (blobState) {
                    BlobState.PROCESSING -> 1200
                    BlobState.LISTENING  -> 1600
                    else                 -> 2800
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe_scale"
    )

    val floatOffsetY by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_y"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Ripple ring 1 — primary wave
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_scale"
    )
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_alpha"
    )

    // Ripple ring 2 — staggered offset for layered wave feel
    val ripple2Scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2_scale"
    )
    val ripple2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2_alpha"
    )

    // Spinning ring for PROCESSING
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    val glowColor by animateColorAsState(
        targetValue = when (blobState) {
            BlobState.IDLE       -> ElectricCyan
            BlobState.LISTENING  -> CyanAccent
            BlobState.PROCESSING -> LiquidViolet
            BlobState.DONE       -> GreenSuccess
        },
        animationSpec = tween(600),
        label = "glow_color"
    )

    val targetGlowRadius by animateFloatAsState(
        targetValue = when (blobState) {
            BlobState.PROCESSING -> 0.80f
            BlobState.LISTENING  -> 0.72f
            else                 -> 0.60f
        },
        animationSpec = tween(600),
        label = "glow_radius"
    )

    // ── State-change impact pulse (fires on every BlobState change) ────────
    val impactAnim = remember { Animatable(1f) }
    LaunchedEffect(blobState) {
        impactAnim.snapTo(1f)
        impactAnim.animateTo(1.16f, tween(90, easing = FastOutSlowInEasing))
        impactAnim.animateTo(0.93f, tween(130, easing = FastOutSlowInEasing))
        impactAnim.animateTo(1.0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }

    // ── DONE success burst (one-shot expanding ring) ───────────────────────
    val doneRingAnim = remember { Animatable(0f) }
    LaunchedEffect(blobState) {
        if (blobState == BlobState.DONE) {
            doneRingAnim.snapTo(1f)
            doneRingAnim.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
        } else {
            doneRingAnim.snapTo(0f)
        }
    }

    val context = LocalContext.current
    val resId = context.resources.getIdentifier("iris_character", "drawable", context.packageName)

    val combinedScale = breatheScale * impactAnim.value

    Box(
        modifier = modifier.size(size * 1.8f),
        contentAlignment = Alignment.Center
    ) {
        // ── Ambient halo glow ─────────────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val glowR = size.toPx() * targetGlowRadius * breatheScale

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = glowAlpha * 0.50f),
                        LiquidViolet.copy(alpha = glowAlpha * 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = glowR
                ),
                radius = glowR,
                center = Offset(cx, cy)
            )
        }

        // ── Ripple rings (LISTENING / PROCESSING) ────────────────────────────
        if (blobState == BlobState.LISTENING || blobState == BlobState.PROCESSING) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                val baseR = size.toPx() * 0.42f
                drawCircle(
                    color = glowColor.copy(alpha = rippleAlpha),
                    radius = baseR * rippleScale,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = glowColor.copy(alpha = ripple2Alpha * 0.65f),
                    radius = baseR * ripple2Scale,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        // ── Spinning ring (PROCESSING) ────────────────────────────────────────
        if (blobState == BlobState.PROCESSING) {
            Canvas(modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = ringRotation }
            ) {
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                val ringR = size.toPx() * 0.50f
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            LiquidViolet.copy(alpha = 0.8f),
                            ElectricCyan.copy(alpha = 0.4f),
                            Color.Transparent,
                            LiquidViolet.copy(alpha = 0.8f)
                        ),
                        center = Offset(cx, cy)
                    ),
                    radius = ringR,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }

        // ── DONE success burst (one-shot expanding rings) ─────────────────────
        if (doneRingAnim.value > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                val progress = 1f - doneRingAnim.value  // 0 → 1 as burst fades
                val outerR = size.toPx() * (0.5f + 1.2f * progress)
                val innerR = size.toPx() * (0.5f + 0.75f * progress)
                drawCircle(
                    color = GreenSuccess.copy(alpha = doneRingAnim.value * 0.70f),
                    radius = outerR,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = GreenSuccess.copy(alpha = doneRingAnim.value * 0.40f),
                    radius = innerR,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        // ── Character image ───────────────────────────────────────────────────
        if (resId != 0) {
            Image(
                painter = painterResource(resId),
                contentDescription = "Iris AI persona",
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = combinedScale
                        scaleY = combinedScale
                        translationY = floatOffsetY * density
                    },
                contentScale = ContentScale.Fit
            )
        } else {
            FallbackBlobCanvas(
                glowColor = glowColor,
                breatheScale = combinedScale,
                floatOffsetY = floatOffsetY,
                size = size
            )
        }
    }
}

@Composable
private fun FallbackBlobCanvas(
    glowColor: Color,
    breatheScale: Float,
    floatOffsetY: Float,
    size: Dp
) {
    Canvas(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = breatheScale
                scaleY = breatheScale
                translationY = floatOffsetY * density
            }
    ) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val rx = this.size.width * 0.38f
        val ry = this.size.height * 0.44f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, cy - ry)
            cubicTo(cx + rx * 0.55f, cy - ry, cx + rx, cy - ry * 0.55f, cx + rx, cy)
            cubicTo(cx + rx, cy + ry * 0.55f, cx + rx * 0.55f, cy + ry, cx, cy + ry)
            cubicTo(cx - rx * 0.55f, cy + ry, cx - rx, cy + ry * 0.55f, cx - rx, cy)
            cubicTo(cx - rx, cy - ry * 0.55f, cx - rx * 0.55f, cy - ry, cx, cy - ry)
            close()
        }
        drawPath(path, Brush.radialGradient(
            colors = listOf(Color(0xFF4400CC), Color(0xFF110040)),
            center = Offset(cx, cy), radius = ry * 1.1f
        ))
        drawPath(path, Brush.radialGradient(
            colors = listOf(glowColor.copy(0.55f), Color.Transparent),
            center = Offset(cx - rx * 0.15f, cy - ry * 0.2f), radius = ry * 0.85f
        ))
        val specR = rx * 0.28f
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(0.55f), Color.Transparent),
                center = Offset(cx - rx * 0.22f, cy - ry * 0.46f), radius = specR
            ),
            radius = specR, center = Offset(cx - rx * 0.22f, cy - ry * 0.46f)
        )
    }
}
