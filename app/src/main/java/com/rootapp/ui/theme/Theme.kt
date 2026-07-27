package com.rootapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The runtime palette that every screen reads. Driven by [TimeOfDay] + minimalist flag. */
data class RootPalette(
    val bg1: Color,
    val bg2: Color,
    val surface: Color,
    val onSurface: Color,
    val dim: Color,
    val accent: Color,
    val accentSoft: Color,
    val orb: Color,
    val orbGlow: Color,
    val starsAlpha: Float,
    val isMoon: Boolean,
    val dark: Boolean,
)

val LocalRootPalette = staticCompositionLocalOf { paletteFor(TimeOfDay.NIGHT) }

private val AmberFixed = Color(0xFFC79A54) // premium accent, constant across themes

fun paletteFor(t: TimeOfDay): RootPalette = when (t) {
    TimeOfDay.MIDNIGHT -> RootPalette(
        bg1 = Color(0xFF04060B), bg2 = Color(0xFF0A0F18),
        surface = Color(0xFF10151F), onSurface = Color(0xFFE7EAF1), dim = Color(0xFF79808E),
        accent = Color(0xFF4E74B0), accentSoft = Color(0x294E74B0),
        orb = Color(0xFF8FA8C9), orbGlow = Color(0xFF2A4A7A),
        starsAlpha = 1f, isMoon = true, dark = true,
    )
    TimeOfDay.NIGHT -> RootPalette(
        bg1 = Color(0xFF0A1E3C), bg2 = Color(0xFF123056),
        surface = Color(0xFF152A4B), onSurface = Color(0xFFEAF1FB), dim = Color(0xFF93A6C6),
        accent = Color(0xFF5FA0E0), accentSoft = Color(0x2E5FA0E0),
        orb = Color(0xFFDCE9FA), orbGlow = Color(0xFF3F74C4),
        starsAlpha = 0.7f, isMoon = true, dark = true,
    )
    TimeOfDay.DUSK -> RootPalette(
        bg1 = Color(0xFF3A335C), bg2 = Color(0xFFC9764A),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF231B2E), dim = Color(0xFF6E6478),
        accent = Color(0xFFB5622F), accentSoft = Color(0x1FB5622F),
        orb = Color(0xFFFFB877), orbGlow = Color(0xFFE88A3C),
        starsAlpha = 0.15f, isMoon = false, dark = false,
    )
    TimeOfDay.DAWN -> RootPalette(
        bg1 = Color(0xFFAEBFDE), bg2 = Color(0xFFF3DED2),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF22283A), dim = Color(0xFF69708A),
        accent = Color(0xFF5A6FA8), accentSoft = Color(0x1F5A6FA8),
        orb = Color(0xFFFFE3B0), orbGlow = Color(0xFFF2C98A),
        starsAlpha = 0.1f, isMoon = false, dark = false,
    )
    TimeOfDay.DAY -> RootPalette(
        bg1 = Color(0xFFBFDBF2), bg2 = Color(0xFFF5FAFE),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF12202E), dim = Color(0xFF5A6675),
        accent = Color(0xFF2E77C2), accentSoft = Color(0x1A2E77C2),
        orb = Color(0xFFFFD86B), orbGlow = Color(0xFFFFCF4D),
        starsAlpha = 0f, isMoon = false, dark = false,
    )
}

/** Pure black & white override (docs/DECISIONS.md D12). Keeps isMoon so the orb still phases. */
fun minimalistPalette(t: TimeOfDay): RootPalette = RootPalette(
    bg1 = Color(0xFFF5F5F5), bg2 = Color(0xFFFDFDFD),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0E0E0E), dim = Color(0xFF7C7C7C),
    accent = Color(0xFF111111), accentSoft = Color(0xFFECECEC),
    orb = Color(0xFFCFCFCF), orbGlow = Color(0xFFD8D8D8),
    starsAlpha = 0f, isMoon = Sky.isMoon(t), dark = false,
)

/** Constant premium accent, exposed for callers that need it. */
val PremiumAccent: Color = AmberFixed

@Composable
fun RootTheme(
    timeOfDay: TimeOfDay,
    minimalist: Boolean,
    content: @Composable () -> Unit,
) {
    val palette = if (minimalist) minimalistPalette(timeOfDay) else paletteFor(timeOfDay)
    val onAccent = if (palette.dark) Color(0xFF06101F) else Color.White
    val base = if (palette.dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = palette.accent,
        onPrimary = onAccent,
        primaryContainer = palette.accentSoft,
        onPrimaryContainer = palette.onSurface,
        secondaryContainer = palette.accentSoft,
        onSecondaryContainer = palette.accent,
        background = palette.bg2,
        onBackground = palette.onSurface,
        surface = palette.surface,
        onSurface = palette.onSurface,
        surfaceVariant = palette.surface,
        onSurfaceVariant = palette.dim,
        outline = palette.dim,
        outlineVariant = palette.dim,
    )
    CompositionLocalProvider(LocalRootPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
