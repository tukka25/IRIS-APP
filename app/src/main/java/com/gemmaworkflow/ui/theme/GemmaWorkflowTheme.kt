package com.gemmaworkflow.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Apple HIG color palette ───────────────────────────────────────────────────

val AppleBlue    = Color(0xFF007AFF)
val AppleGreen   = Color(0xFF34C759)
val AppleRed     = Color(0xFFFF3B30)
val AppleOrange  = Color(0xFFFF9500)

val SystemBackground          = Color(0xFFF2F2F7)
val SecondarySystemBackground = Color(0xFFE5E5EA)
val PrimaryLabel              = Color(0xFF000000)
val SecondaryLabel            = Color(0xFF636366)
val TertiaryLabel             = Color(0xFFAEAEB2)
val Separator                 = Color(0xFFC6C6C8)

// ── Color scheme ──────────────────────────────────────────────────────────────

private val AppColors = lightColorScheme(
    primary              = AppleBlue,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFE5F1FF),
    onPrimaryContainer   = AppleBlue,
    secondary            = AppleGreen,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFD4F5DF),
    onSecondaryContainer = Color(0xFF1A6B35),
    background           = SystemBackground,
    onBackground         = PrimaryLabel,
    surface              = Color.White,
    onSurface            = PrimaryLabel,
    surfaceVariant       = SecondarySystemBackground,
    onSurfaceVariant     = SecondaryLabel,
    outline              = Separator,
    outlineVariant       = Color(0xFFE0E0E0),
    error                = AppleRed,
    onError              = Color.White,
    errorContainer       = Color(0xFFFFECEA),
    onErrorContainer     = AppleRed,
    tertiary             = AppleOrange,
    onTertiary           = Color.White,
)

// ── Typography (iOS scale) ────────────────────────────────────────────────────

private val AppTypography = Typography(
    displayLarge  = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium= TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge    = TextStyle(fontSize = 20.sp, fontWeight = FontWeight(600)),
    titleMedium   = TextStyle(fontSize = 17.sp, fontWeight = FontWeight(600)),
    titleSmall    = TextStyle(fontSize = 15.sp, fontWeight = FontWeight(600)),
    bodyLarge     = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal),
    bodyMedium    = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodySmall     = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    labelMedium   = TextStyle(fontSize = 12.sp, fontWeight = FontWeight(600), letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal),
)

// ── Shapes ────────────────────────────────────────────────────────────────────

private val AppShapes = Shapes(
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

// ── Theme ─────────────────────────────────────────────────────────────────────

@Composable
fun GemmaWorkflowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography  = AppTypography,
        shapes      = AppShapes,
        content     = content
    )
}
