package com.irisapp.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.irisapp.ui.theme.CyanAccent
import com.irisapp.ui.theme.CyanVioletGradient
import com.irisapp.ui.theme.ElectricCyan
import com.irisapp.ui.theme.GlassSurface
import com.irisapp.ui.theme.LiquidViolet
import com.irisapp.ui.theme.TextPrimary
import com.irisapp.ui.theme.TextSecondary

private enum class ConsoleState { IDLE, TYPING, GENERATING }

@Composable
fun LivingInputConsole(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isGenerating: Boolean = false,
    onMicClick: () -> Unit = {},
    placeholder: String = "What should IrisApp do?"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val consoleState = when {
        isGenerating       -> ConsoleState.GENERATING
        value.isNotEmpty() -> ConsoleState.TYPING
        else               -> ConsoleState.IDLE
    }

    val infiniteTransition = rememberInfiniteTransition(label = "console_ring")

    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )
    val idleGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_glow"
    )

    val targetBorderAlpha = when (consoleState) {
        ConsoleState.IDLE       -> if (isFocused) 0.55f else idleGlowAlpha
        ConsoleState.TYPING     -> 0.65f
        ConsoleState.GENERATING -> 0.90f
    }
    val borderGlowAlpha by animateFloatAsState(
        targetValue = targetBorderAlpha,
        animationSpec = tween(400),
        label = "border_glow"
    )

    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassSurface)
            .drawBehind {
                val strokeWidth = 2.dp.toPx()
                val cornerRad = 28.dp.toPx()
                if (consoleState == ConsoleState.GENERATING) {
                    rotate(ringRotation) {
                        drawRoundRect(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    ElectricCyan.copy(alpha = borderGlowAlpha),
                                    LiquidViolet.copy(alpha = borderGlowAlpha),
                                    ElectricCyan.copy(alpha = borderGlowAlpha)
                                )
                            ),
                            cornerRadius = CornerRadius(cornerRad),
                            style = Stroke(width = strokeWidth)
                        )
                    }
                } else {
                    drawRoundRect(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                ElectricCyan.copy(alpha = borderGlowAlpha),
                                LiquidViolet.copy(alpha = borderGlowAlpha * 0.7f),
                                ElectricCyan.copy(alpha = borderGlowAlpha)
                            )
                        ),
                        cornerRadius = CornerRadius(cornerRad),
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                cursorBrush = SolidColor(CyanAccent),
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextSecondary.copy(alpha = 0.6f)
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Box(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(ElectricCyan, LiquidViolet)))
                    .clickable(onClick = onMicClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Voice input",
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
