package it.venturini.poppy

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5B5BD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4FF),
    onPrimaryContainer = Color(0xFF14144D),
    secondary = Color(0xFFE8694A),
    secondaryContainer = Color(0xFFFFE3DA),
    onSecondaryContainer = Color(0xFF4A1A0C),
    background = Color(0xFFF6F6FB),
    surface = Color(0xFFF6F6FB),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB9B9FF),
    onPrimary = Color(0xFF1B1B66),
    primaryContainer = Color(0xFF3F3FA8),
    onPrimaryContainer = Color(0xFFE4E4FF),
    secondary = Color(0xFFFFB5A0),
    secondaryContainer = Color(0xFF6B2F1D),
    onSecondaryContainer = Color(0xFFFFE3DA),
    background = Color(0xFF121218),
    surface = Color(0xFF121218),
    surfaceContainerLow = Color(0xFF1C1C26),
    surfaceContainer = Color(0xFF22222C),
    surfaceContainerHigh = Color(0xFF282833),
    surfaceContainerHighest = Color(0xFF2E2E3A)
)

@Composable
fun PoppyTheme(mode: Int = 0, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
