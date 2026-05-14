package com.irisapp.ui.components

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
import com.irisapp.ui.theme.CyanAccent
import com.irisapp.ui.theme.GlassBorder
import com.irisapp.ui.theme.SurfaceDark

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
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        glowColor.copy(alpha = if (isActive) 0.22f else 0.12f),
                        glowColor.copy(alpha = if (isActive) 0.08f else 0.04f)
                    )
                )
            )
            .background(SurfaceDark.copy(alpha = if (isActive) 0.85f else 0.7f))
            .then(
                if (isActive) Modifier.background(
                    glowColor.copy(alpha = 0.08f),
                    shape
                ) else Modifier
            )
            .clip(shape)
            .then(
                if (isActive) Modifier.background(glowColor.copy(alpha = 0.25f), shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Glow dot — brighter when active
            Box(
                modifier = Modifier
                    .size(if (isActive) 10.dp else 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isActive) glowColor else glowColor.copy(alpha = 0.7f))
            )

            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight(if (isActive) 700 else 600),
                        fontSize = 13.sp,
                        color = if (isActive) Color.White else Color.White.copy(alpha = 0.9f)
                    )
                )
                Text(
                    text = "$deviceCount actions",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        color = glowColor.copy(alpha = if (isActive) 1f else 0.8f)
                    )
                )
            }
        }
    }
}

/**
 * A scrollable row of scene chips — the strip at the top of the Workflows tab.
 * @param selectedSceneId The ID of the currently selected scene (null = none selected)
 */
@Composable
fun SceneChipStrip(
    scenes: List<SceneChipData>,
    onChipClick: (SceneChipData) -> Unit,
    selectedSceneId: String? = null,
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
                onClick = { onChipClick(scene) },
                isActive = scene.id == selectedSceneId
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