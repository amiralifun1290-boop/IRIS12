package com.iris.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = IrisTeal,
    secondary = IrisTealDark,
    background = Black,
    surface = SurfaceDark,
    onPrimary = Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun IrisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
