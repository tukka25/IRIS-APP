package com.gemmaworkflow.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gemmaworkflow.ui.theme.CyanAccent
import com.gemmaworkflow.ui.theme.CyanVioletGradient
import com.gemmaworkflow.ui.theme.GreenSuccess
import com.gemmaworkflow.ui.theme.SurfaceDark
import com.gemmaworkflow.ui.theme.SurfaceVariantDark
import com.gemmaworkflow.ui.theme.VioletAccent

// ── Palette ───────────────────────────────────────────────────────────────────

private val HexFace      = Color(0xFF0a1424)
private val HexFaceInner = Color(0xFF100b22)
private val GlowCyan     = Color(0xFF9CFBFF)
private val GlowViolet   = Color(0xFFB57BFF)

// ── Hex path helpers ───────────────────────────────────────────────────────────

/** Returns the path for a flat-top hex with center (cx, cy) and radius r. */
private fun hexPath(cx: Float, cy: Float, r: Float): Path {
    val path = Path()
    val angle = Math.PI / 6  // 30°
    for (i in 0 until 6) {
        val a = angle + i * Math.PI / 3
        val x = cx + r * kotlin.math.cos(a).toFloat()
        val y = cy + r * kotlin.math.sin(a).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

// ── Breathing animation ───────────────────────────────────────────────────────

@Composable
fun HexHeroIcon(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hex_breath")
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f

        // ── Outer radial glow ──────────────────────────────────────────────
        val glowRadius = w * 0.32f * breathe
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    GlowCyan.copy(alpha = 0.35f),
                    CyanAccent.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = glowRadius
            ),
            radius = glowRadius,
            center = Offset(cx, cy)
        )

        // ── Outer dashed rotating ring ─────────────────────────────────────
        val outerR = w * 0.46f
        rotate(rotation, pivot = Offset(cx, cy)) {
            drawPath(
                path = hexPath(cx, cy, outerR),
                color = CyanAccent.copy(alpha = 0.3f),
                style = Stroke(
                    width = 1.5f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(8f, 12f), 0f
                    )
                )
            )
        }

        // ── Main hex body ───────────────────────────────────────────────────
        val hexR = w * 0.40f
        // Fill
        drawPath(
            path = hexPath(cx, cy, hexR),
            brush = Brush.linearGradient(
                colors = listOf(HexFace, HexFaceInner),
                start = Offset(cx - hexR, cy - hexR),
                end = Offset(cx + hexR, cy + hexR)
            )
        )
        // Stroke — cyan→violet gradient
        drawPath(
            path = hexPath(cx, cy, hexR),
            brush = CyanVioletGradient,
            style = Stroke(width = 2f)
        )

        // ── 3D isometric home icon (scaled to ~40% of hex radius) ─────────
        val homeScale = hexR * 0.38f
        drawIsoHome(cx, cy, homeScale, breathe)

        // ── Vertex jewels (6 corners with breathing pulse) ─────────────────
        for (i in 0 until 6) {
            val angle = Math.PI / 6 + i * Math.PI / 3
            val vx = cx + outerR * kotlin.math.cos(angle).toFloat()
            val vy = cy + outerR * kotlin.math.sin(angle).toFloat()
            val jewelR = 3.5f * breathe
            val jewelColor = when (i % 3) {
                0 -> CyanAccent
                1 -> Color(0xFF9EA9FF)  // mid-violet-blue
                else -> VioletAccent
            }
            drawCircle(
                color = jewelColor,
                radius = jewelR,
                center = Offset(vx, vy)
            )
        }
    }
}

// ── 3D isometric home ──────────────────────────────────────────────────────────

private fun DrawScope.drawIsoHome(cx: Float, cy: Float, scale: Float, breathe: Float) {
    val s = scale * breathe  // subtle pulse on the home too

    // Re-usable vertices
    val top      = Offset(cx,        cy - s * 0.72f)
    val bottom   = Offset(cx,        cy + s * 0.65f)
    val leftUp   = Offset(cx - s,    cy - s * 0.28f)
    val rightUp  = Offset(cx + s,    cy - s * 0.28f)
    val leftDown = Offset(cx - s,    cy + s * 0.42f)
    val rightDown= Offset(cx + s,    cy + s * 0.42f)
    val leftBot  = Offset(cx - s,    cy + s)
    val rightBot = Offset(cx + s,    cy + s)

    // ── Right face (violet) ─────────────────────────────────────────────
    val rightFace = Path().apply {
        moveTo(cx, cy - s * 0.42f)
        lineTo(rightUp.x, rightUp.y)
        lineTo(rightDown.x, rightDown.y)
        lineTo(cx, bottom.y)
        close()
    }
    drawPath(rightFace, Color(0xFF5d2da8))
    drawPath(
        rightFace,
        brush = Brush.linearGradient(
            colors = listOf(VioletAccent.copy(alpha = 0.6f), Color.Transparent),
            start = Offset(cx + s, cy - s * 0.28f),
            end = Offset(cx, cy + s)
        )
    )
    drawPath(
        rightFace,
        color = VioletAccent.copy(alpha = 0.5f),
        style = Stroke(width = 1.2f)
    )

    // ── Left face (dark cyan) ────────────────────────────────────────────
    val leftFace = Path().apply {
        moveTo(cx, cy - s * 0.42f)
        lineTo(leftUp.x, leftUp.y)
        lineTo(leftDown.x, leftDown.y)
        lineTo(cx, bottom.y)
        close()
    }
    drawPath(leftFace, Color(0xFF0e3f56))
    drawPath(
        leftFace,
        brush = Brush.linearGradient(
            colors = listOf(CyanAccent.copy(alpha = 0.5f), Color.Transparent),
            start = Offset(cx - s, cy - s * 0.28f),
            end = Offset(cx, cy + s)
        )
    )
    drawPath(
        leftFace,
        color = CyanAccent.copy(alpha = 0.5f),
        style = Stroke(width = 1.2f)
    )

    // ── Roof / top face ──────────────────────────────────────────────────
    val roof = Path().apply {
        moveTo(cx, cy - s * 0.72f)   // peak
        lineTo(rightUp.x, rightUp.y)  // right base
        lineTo(cx, cy - s * 0.28f)   // center dip
        lineTo(leftUp.x, leftUp.y)    // left base
        close()
    }
    drawPath(roof, Color(0xFF142640))
    drawPath(
        roof,
        brush = CyanVioletGradient,
        style = Stroke(width = 1.5f)
    )

    // ── Center seam (glow line) ──────────────────────────────────────────
    drawLine(
        brush = CyanVioletGradient,
        start = Offset(cx, cy - s * 0.72f),
        end = Offset(cx, cy + s),
        strokeWidth = 1.5f,
        cap = StrokeCap.Round
    )

    // ── Door / window ────────────────────────────────────────────────────
    val doorW = s * 0.22f
    val doorH = s * 0.32f
    val doorX = cx - doorW / 2f
    val doorY = cy + s * 0.18f
    drawRoundRect(
        color = CyanAccent.copy(alpha = 0.85f),
        topLeft = Offset(doorX, doorY),
        size = Size(doorW, doorH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(doorW * 0.3f)
    )
    drawRoundRect(
        color = GlowCyan.copy(alpha = 0.6f),
        topLeft = Offset(doorX, doorY),
        size = Size(doorW, doorH),
        style = Stroke(width = 0.8f)
    )

    // ── Face stripe accents ──────────────────────────────────────────────
    val stripeOffset = s * 0.22f
    // Left face vertical stripe
    drawLine(
        color = CyanAccent.copy(alpha = 0.4f),
        start = Offset(cx - stripeOffset, cy - s * 0.1f),
        end = Offset(cx - stripeOffset, cy + s * 0.5f),
        strokeWidth = 0.8f
    )
    // Right face vertical stripe
    drawLine(
        color = VioletAccent.copy(alpha = 0.4f),
        start = Offset(cx + stripeOffset, cy - s * 0.1f),
        end = Offset(cx + stripeOffset, cy + s * 0.5f),
        strokeWidth = 0.8f
    )

    // ── Floor shadow ellipse ─────────────────────────────────────────────
    drawOval(
        color = Color.Black.copy(alpha = 0.4f),
        topLeft = Offset(cx - s * 0.9f, cy + s * 0.82f),
        size = Size(s * 1.8f, s * 0.15f)
    )
}