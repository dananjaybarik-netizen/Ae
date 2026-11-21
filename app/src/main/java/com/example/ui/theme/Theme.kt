package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val AiStudioColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = StudioViolet,
    onSecondary = TextPrimary,
    secondaryContainer = StudioVioletContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = KeyframeAmber,
    onTertiary = ObsidianVoid,
    tertiaryContainer = KeyframeAmberContainer,
    onTertiaryContainer = KeyframeAmber,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHighest,
    surfaceContainerHighest = ColorTokens.SurfaceHighest,
    outline = StudioBorder,
    outlineVariant = ColorTokens.BorderSubtle,
    error = CrimsonAlert,
    onError = TextPrimary,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonAlert
)

private object ColorTokens {
    val SurfaceHighest = androidx.compose.ui.graphics.Color(0xFF283044)
    val BorderSubtle = androidx.compose.ui.graphics.Color(0xFF1D2332)
}

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun AiStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AiStudioColorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content
    )
}
