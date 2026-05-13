package com.gemmaworkflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemmaworkflow.ui.theme.CyanAccent
import com.gemmaworkflow.ui.theme.GlassBorder
import com.gemmaworkflow.ui.theme.SurfaceDark

/**
 * Glow color sets for each scene type.
 * These match the Lumen HTML mockup's color language.
 */
object SceneGlowColors {
    val Morning = Color(0xFFFFC15E)  // warm amber
    val Work    = CyanAccent         // cyan
    val Dinner  = Color(0xFFFF6BD6) // violet-pink
    val Sleep   = Color(0xFFB57BFF) // deep violet
    val Default = CyanAccent
}

/**
 * A compact horizontal chip representing a workflow/routine scene.
 * Used in the scene strip at the top of the Workflows tab.
 *
 * @param name        Label shown below the icon
 * @param deviceCount Number of actions/devices in this scene
 * @param glowColor   Color for the chip's left accent bar and icon tint
 * @param onClick    Called when the chip is tapped
 */
@Composable
fun SceneChip(
    name: String,
    deviceCount: Int,
    glowColor: Color = SceneGlowColors.Default,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.12f),
                        glowColor.copy(alpha = 0.04f)
                    )
                )
            )
            .background(SurfaceDark.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Glow dot indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(glowColor)
            )

            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight(600),
                        fontSize = 13.sp,
                        color = Color.White
                    )
                )
                Text(
                    text = "$deviceCount actions",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        color = glowColor.copy(alpha = 0.8f)
                    )
                )
            }
        }
    }
}

/**
 * A scrollable row of scene chips — the strip at the top of the Workflows tab.
 */
@Composable
fun SceneChipStrip(
    scenes: List<SceneChipData>,
    onChipClick: (SceneChipData) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        scenes.forEach { scene ->
            SceneChip(
                name = scene.name,
                deviceCount = scene.actionCount,
                glowColor = scene.glowColor,
                onClick = { onChipClick(scene) }
            )
        }
    }
}

/** Data class for a single scene chip entry. */
data class SceneChipData(
    val id: String,
    val name: String,
    val actionCount: Int,
    val glowColor: Color = SceneGlowColors.Default
)