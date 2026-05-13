package com.gemmaworkflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gemmaworkflow.ui.theme.GlassBorder
import com.gemmaworkflow.ui.theme.GlassSurface
import com.gemmaworkflow.ui.theme.SurfaceDark

/**
 * A dark glassmorphic card matching the Lumen HTML mockup:
 * - Near-transparent dark surface with subtle white tint
 * - 0.5px border in rgba(140,170,220,0.08)
 * - Rounded corners (16dp)
 *
 * Use for workflow cards, device panels, and any content containers
 * that sit on the dark background.
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    borderWidth: Dp = 0.5.dp,
    cornerRadius: Dp = 16.dp,
    backgroundColor: Color = GlassSurface,
    glowColor: Color? = null,  // optional cyan/violet tint for active state
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(borderWidth, GlassBorder, shape)
    ) {
        content()
    }
}

/**
 * Convenience Surface-based glass card for use inside Material 3 layouts.
 * Applies the same visual treatment but preserves Material surface semantics.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = GlassSurface,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .border(
                    width = 0.5.dp,
                    color = GlassBorder,
                    shape = RoundedCornerShape(cornerRadius)
                )
                .padding(1.dp)  // small inner padding so content doesn't touch border
        ) {
            content()
        }
    }
}