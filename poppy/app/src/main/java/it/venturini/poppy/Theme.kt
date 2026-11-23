package it.venturini.poppy

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

class Pal(
    val bg: Color, val card: Color, val ink: Color, val muted: Color, val line: Color,
    val sx: Color, val sxSoft: Color, val dx: Color, val dxSoft: Color,
    val bot: Color, val botSoft: Color, val pump: Color, val pumpSoft: Color,
    val nap: Color, val napSoft: Color
)

val LightPal = Pal(
    bg = Color(0xFFFBF6F1), card = Color(0xFFFFFFFF), ink = Color(0xFF2B2633), muted = Color(0xFF8D8597), line = Color(0xFFEFE7DF),
    sx = Color(0xFFEC7F74), sxSoft = Color(0xFFFDE6E2), dx = Color(0xFF5F97D6), dxSoft = Color(0xFFE1ECFA),
    bot = Color(0xFFE9A23B), botSoft = Color(0xFFFCEFD8), pump = Color(0xFF9A7FDC), pumpSoft = Color(0xFFEEE7FB),
    nap = Color(0xFF3FA69A), napSoft = Color(0xFFDCF2EE)
)

val DarkPal = Pal(
    bg = Color(0xFF17151C), card = Color(0xFF221F29), ink = Color(0xFFF2EEF6), muted = Color(0xFF9C95A8), line = Color(0xFF312C39),
    sx = Color(0xFFEC7F74), sxSoft = Color(0xFF40292A), dx = Color(0xFF5F97D6), dxSoft = Color(0xFF212F41),
    bot = Color(0xFFE9A23B), botSoft = Color(0xFF3B301C), pump = Color(0xFF9A7FDC), pumpSoft = Color(0xFF2E2742),
    nap = Color(0xFF3FA69A), napSoft = Color(0xFF1D3532)
)

val LocalPal = staticCompositionLocalOf { LightPal }

val pal: Pal
    @Composable get() = LocalPal.current

private fun scheme(p: Pal, dark: Boolean): androidx.compose.material3.ColorScheme {
    return if (dark) darkColorScheme(
        primary = p.ink, onPrimary = p.bg, primaryContainer = p.line, onPrimaryContainer = p.ink,
        secondary = p.bot, secondaryContainer = p.line, onSecondaryContainer = p.ink,
        background = p.bg, onBackground = p.ink, surface = p.bg, onSurface = p.ink, onSurfaceVariant = p.muted,
        surfaceVariant = p.line, outline = p.line, outlineVariant = p.line,
        surfaceContainerLowest = p.bg, surfaceContainerLow = p.card, surfaceContainer = p.card,
        surfaceContainerHigh = p.card, surfaceContainerHighest = p.card
    ) else lightColorScheme(
        primary = p.ink, onPrimary = p.bg, primaryContainer = p.line, onPrimaryContainer = p.ink,
        secondary = p.bot, secondaryContainer = p.line, onSecondaryContainer = p.ink,
        background = p.bg, onBackground = p.ink, surface = p.bg, onSurface = p.ink, onSurfaceVariant = p.muted,
        surfaceVariant = p.line, outline = p.line, outlineVariant = p.line,
        surfaceContainerLowest = p.bg, surfaceContainerLow = p.card, surfaceContainer = p.card,
        surfaceContainerHigh = p.card, surfaceContainerHighest = p.card
    )
}

@Composable
fun PoppyTheme(mode: Int = 0, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val p = if (dark) DarkPal else LightPal
    CompositionLocalProvider(LocalPal provides p) {
        MaterialTheme(colorScheme = scheme(p, dark), content = content)
    }
}
