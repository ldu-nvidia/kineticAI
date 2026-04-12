package com.mycarv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SkyBlue,
    onPrimary = DarkBase,
    primaryContainer = DeepBlue,
    onPrimaryContainer = IceBlue,
    secondary = AccentOrange,
    onSecondary = DarkBase,
    background = DarkBase,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondary,
    error = AccentRed,
    onError = SnowWhite,
)

@Composable
fun MyCarvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = MyCarvTypography,
        content = content,
    )
}
