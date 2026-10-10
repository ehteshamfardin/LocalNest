package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MacDarkAccent,
    onPrimary = Color.Black,
    primaryContainer = MacDarkAccentMuted,
    onPrimaryContainer = MacDarkAccent,
    secondary = Color(0xFF58A6FF),
    onSecondary = Color.Black,
    background = MacDarkBg,
    onBackground = MacDarkTextPrimary,
    surface = MacDarkSurface,
    onSurface = MacDarkTextPrimary,
    surfaceVariant = MacDarkSurfaceVariant,
    onSurfaceVariant = MacDarkTextSecondary,
    outline = MacDarkBorder,
    error = StatusRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = MacLightAccent,
    onPrimary = Color.White,
    primaryContainer = MacLightAccentMuted,
    onPrimaryContainer = MacLightAccent,
    secondary = Color(0xFF0969DA),
    onSecondary = Color.White,
    background = MacLightBg,
    onBackground = MacLightTextPrimary,
    surface = MacLightSurface,
    onSurface = MacLightTextPrimary,
    surfaceVariant = MacLightSurfaceVariant,
    onSurfaceVariant = MacLightTextSecondary,
    outline = MacLightBorder,
    error = StatusRed,
    onError = Color.White
)

@Composable
fun LocalShareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
