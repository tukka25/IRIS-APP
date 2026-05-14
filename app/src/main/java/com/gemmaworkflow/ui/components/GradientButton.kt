package com.gemmaworkflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gemmaworkflow.ui.theme.CyanAccent
import com.gemmaworkflow.ui.theme.CyanVioletGradient

// CyanVioletGradient defined in GemmaWorkflowTheme.kt
// Disabled state uses a separate dimmed brush (no alpha on Brush possible)
private val DisabledCyanVioletGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF2A7A80),
        Color(0xFF5A3B8A)
    )
)

@Composable
private fun gradientBrushFor(enabled: Boolean): Brush =
    if (enabled) CyanVioletGradient else DisabledCyanVioletGradient

/**
 * Primary action button filled with a cyan→violet gradient,
 * matching the accent style in the Lumen HTML mockup.
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.Black,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Color.Black.copy(alpha = 0.4f)
        ),
        contentPadding = PaddingValues(0.dp)  // custom padding via inner box
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(
                    brush = gradientBrushFor(enabled)
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/**
 * Outlined variant that keeps the gradient on the border/stroke only.
 */
@Composable
fun GradientOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (RowScope.() -> Unit)? = null
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = CyanAccent
        )
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}