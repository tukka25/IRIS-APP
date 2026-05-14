package com.irisapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Lumen Dark Palette ─────────────────────────────────────────────────────────

// Backgrounds
val BackgroundDark    = Color(0xFF06080d)
val SurfaceDark       = Color(0xFF0a1424)
val SurfaceVariantDark= Color(0xFF100b22)

// Accents
val CyanAccent        = Color(0xFF5EF2FF)
val VioletAccent      = Color(0xFFB57BFF)

// ── Extended Palette (Redesign 2026) ──────────────────────────────────────────
val ElectricCyan  = Color(0xFF00F2FE)
val DeepPurple    = Color(0xFF6F00FF)
val LiquidViolet  = Color(0xFFB200FF)
val ObsidianDark  = Color(0xFF050509)
val CyanVioletStart   = Color(0xFF5EF2FF)
val CyanVioletEnd     = Color(0xFFB57BFF)

// Semantic
val GreenSuccess      = Color(0xFF7CF0A8)
val AmberWarning      = Color(0xFFFFC15E)

// Text
val TextPrimary       = Color(0xFFffffff)
val TextSecondary     = Color(0xFF999999)
val TextTertiary      = Color(0xFF666666)

// Borders / glass
val GlassBorder       = Color(0x1A8A9ADC)   // rgba(140,170,220,0.08) — matches HTML mockup
val GlassSurface      = Color(0x0DFFFFFF)   // rgba(255,255,255,0.03)

// Surface elevation tints
val SurfaceTintCyan   = Color(0x1A5EF2FF)   // ~10% cyan overlay
val SurfaceTintViolet  = Color(0x1AB57BFF)   // ~10% violet overlay

// ── Gradient brushes ───────────────────────────────────────────────────────────

/** Cyan → Violet horizontal gradient, matching the hex-edge gradient in the mockup. */
val CyanVioletGradient = Brush.horizontalGradient(
    listOf(CyanVioletStart, CyanVioletEnd)
)

/** Cyan → Violet diagonal gradient, for fills and buttons. */
val CyanVioletGradientDiagonal = Brush.linearGradient(
    listOf(CyanVioletStart, CyanVioletEnd)
)

/** Radial glow centered on cyan, for background halos. */
val CyanGlowRadial = Brush.radialGradient(
    colors = listOf(
        Color(0xFF9CFBFF).copy(alpha = 0.9f),
        Color(0xFF5EF2FF).copy(alpha = 0.4f),
        Color(0xFFB57BFF).copy(alpha = 0f)
    ),
    radius = 0.5f
)

// ── Color scheme ────────────────────────────────────────────────────────────────

private val DarkColors = darkColorScheme(
    primary              = CyanAccent,
    onPrimary            = Color(0xFF000000),
    primaryContainer     = SurfaceVariantDark,
    onPrimaryContainer   = CyanAccent,
    secondary            = VioletAccent,
    onSecondary          = Color(0xFF000000),
    secondaryContainer   = SurfaceVariantDark,
    onSecondaryContainer = VioletAccent,
    tertiary             = GreenSuccess,
    onTertiary           = Color(0xFF000000),
    background           = BackgroundDark,
    onBackground         = TextPrimary,
    surface              = SurfaceDark,
    onSurface            = TextPrimary,
    surfaceVariant       = SurfaceVariantDark,
    onSurfaceVariant     = TextSecondary,
    outline              = GlassBorder,
    outlineVariant       = Color(0x0DFFFFFF),
    error                = Color(0xFFFF6B6B),
    onError              = Color.White,
    errorContainer       = Color(0xFF2a1215),
    onErrorContainer     = Color(0xFFFF8A80),
)

// ── Typography (iOS scale, white on dark) ─────────────────────────────────────

private val DarkTypography = Typography(
    displayLarge  = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold,   color = TextPrimary),
    headlineMedium= TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold,   color = TextPrimary),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold,   color = TextPrimary),
    titleLarge    = TextStyle(fontSize = 20.sp, fontWeight = FontWeight(600),   color = TextPrimary),
    titleMedium   = TextStyle(fontSize = 17.sp, fontWeight = FontWeight(600),   color = TextPrimary),
    titleSmall    = TextStyle(fontSize = 15.sp, fontWeight = FontWeight(600),   color = TextPrimary),
    bodyLarge     = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, color = TextPrimary),
    bodyMedium    = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = TextPrimary),
    bodySmall     = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = TextSecondary),
    labelMedium   = TextStyle(fontSize = 12.sp, fontWeight = FontWeight(600),   color = TextTertiary, letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = TextTertiary),
)

// ── Shapes (Apple HIG radii — preserved from original) ─────────────────────────

private val AppShapes = Shapes(
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

// ── Theme ─────────────────────────────────────────────────────────────────────

@Composable
fun IrisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography  = DarkTypography,
        shapes      = AppShapes,
        content     = content
    )
}