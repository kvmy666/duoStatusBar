package io.github.kvmy666.duostatusbar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Obsidian and red wine.
 *
 * The app is dark by design: the wallpaper is part of the liquid-glass material, and a light surface
 * would change what the glass refracts. The light scheme stays complete so a caller can still opt in,
 * but it follows the same wine brand rather than a second palette.
 */
private val ObsidianWine = darkColorScheme(
    primary = DuoColors.Wine,
    onPrimary = Color.White,
    primaryContainer = DuoColors.WineDeep,
    onPrimaryContainer = DuoColors.WineMist,
    secondary = DuoColors.WineMist,
    onSecondary = Color(0xFF2A0D18),
    secondaryContainer = Color(0xFF2A1219),
    onSecondaryContainer = DuoColors.WineMist,
    tertiary = DuoColors.TextSecondary,
    onTertiary = DuoColors.Background,
    tertiaryContainer = DuoColors.SurfaceRaised,
    onTertiaryContainer = DuoColors.TextPrimary,
    error = DuoColors.Danger,
    onError = Color(0xFF2A0A0A),
    errorContainer = Color(0xFF3A1214),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DuoColors.Background,
    onBackground = DuoColors.TextPrimary,
    surface = DuoColors.Background,
    onSurface = DuoColors.TextPrimary,
    surfaceVariant = DuoColors.Surface,
    onSurfaceVariant = DuoColors.TextSecondary,
    outline = DuoColors.OutlineStrong,
    outlineVariant = DuoColors.Outline,
    scrim = Color.Black
)

private val PorcelainWine = lightColorScheme(
    primary = DuoColors.Wine,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6DDE5),
    onPrimaryContainer = DuoColors.WineDeep,
    secondary = Color(0xFF7A4A5A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2DEE4),
    onSecondaryContainer = Color(0xFF2B151A),
    tertiary = Color(0xFF6B5A5E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEFE4E6),
    onTertiaryContainer = Color(0xFF22191B),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFAF7F7),
    onBackground = Color(0xFF1C1819),
    surface = Color(0xFFFAF7F7),
    onSurface = Color(0xFF1C1819),
    surfaceVariant = Color(0xFFF0E7E9),
    onSurfaceVariant = Color(0xFF4E4446),
    outline = Color(0xFF807376),
    outlineVariant = Color(0xFFD3C5C8)
)

@Composable
fun DuoTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) ObsidianWine else PorcelainWine,
        typography = DuoTypography,
        content = content
    )
}
