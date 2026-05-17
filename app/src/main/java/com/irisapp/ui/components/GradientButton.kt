package com.irisapp.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irisapp.ui.theme.CyanAccent
import com.irisapp.ui.theme.ElectricCyan
import com.irisapp.ui.theme.GlassBorder
import com.irisapp.ui.theme.LiquidViolet
import com.irisapp.ui.theme.SpaceGrotesk
import com.irisapp.ui.theme.TextPrimary

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
    icon: @Composable (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(100.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1.0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "press_scale"
    )

    val t = rememberInfiniteTransition(label = "btn_border")
    val borderAlpha by t.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            tween(2400), RepeatMode.Reverse
        ),
        label = "border_alpha"
    )
    // Slow hue shift: morphs the 3 accent colors into each other
    val colorShift by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(3600), RepeatMode.Reverse
        ),
        label = "color_shift"
    )

    Box(
        modifier = (if (fillWidth) modifier.fillMaxWidth() else modifier)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = if (enabled) 1.0f else 0.35f
            }
            .clip(shape)
            .drawBehind {
                val cornerRad = size.minDimension / 2f
                if (enabled) {
                    val c1 = lerp(ElectricCyan,       LiquidViolet,       colorShift)
                    val c2 = lerp(LiquidViolet,       Color(0xFFFF006E),  colorShift)
                    val c3 = lerp(Color(0xFFFF006E),  ElectricCyan,       colorShift)
                    drawRoundRect(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                c1.copy(alpha = borderAlpha),
                                c2.copy(alpha = borderAlpha * 0.80f),
                                c3.copy(alpha = borderAlpha * 0.65f),
                                c1.copy(alpha = borderAlpha)
                            )
                        ),
                        cornerRadius = CornerRadius(cornerRad),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                } else {
                    drawRoundRect(
                        color = GlassBorder,
                        cornerRadius = CornerRadius(cornerRad),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
            .background(Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = if (fillWidth) 28.dp else 16.dp, vertical = if (fillWidth) 15.dp else 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                letterSpacing = 0.8.sp,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun GradientOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (RowScope.() -> Unit)? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}
