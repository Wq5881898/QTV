package com.qtv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = QtvBlue,
    onPrimary = QtvText,
    primaryContainer = QtvBlueDim,
    onPrimaryContainer = QtvText,
    secondary = QtvGreen,
    onSecondary = QtvBackground,
    secondaryContainer = QtvSurfaceAlt,
    onSecondaryContainer = QtvText,
    background = QtvBackground,
    onBackground = QtvText,
    surface = QtvSurface,
    onSurface = QtvText,
    surfaceVariant = QtvSurfaceAlt,
    onSurfaceVariant = QtvTextMuted,
    surfaceContainer = QtvSurface,
    surfaceContainerLow = QtvSurfaceAlt,
    surfaceBright = QtvSurfaceFocus,
    outline = QtvOutline,
)

@Composable
fun QTVTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
