package io.github.kvmy666.duostatusbar.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The app's visual vocabulary: obsidian, red wine, one accent.
 *
 * Dark is the product surface, not a mode. The values here are the only source for the shell and the
 * preview; the theme maps them onto Material so stock controls inherit the same palette.
 */
internal object DuoColors {
    val Background = Color(0xFF0A0A0A)
    val BackgroundDeep = Color(0xFF070607)
    val Surface = Color(0xFF121012)
    val SurfaceRaised = Color(0xFF171316)

    val Wine = Color(0xFF7B1E3A)
    val WineDeep = Color(0xFF4C0F27)
    val WineBright = Color(0xFFC95478)
    val WineMist = Color(0xFFE3A6B8)

    val NavAccent = Color(0xFF3C88F8)

    val TextPrimary = Color(0xFFF4EFF0)
    val TextSecondary = Color(0xFFB9B0B3)
    val TextTertiary = Color(0xFF8E8588)

    val Outline = Color(0xFF2A2628)
    val OutlineStrong = Color(0xFF5C5457)
    val Hairline = Color(0x14FFFFFF)

    val Success = Color(0xFF9AD1A8)
    val Warning = Color(0xFFE3A6B8)
    val Danger = Color(0xFFF08A8A)
}

/** 4 dp rhythm, used everywhere. No component invents its own spacing. */
internal object DuoSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val section = 32.dp
    val hero = 40.dp
}

/** One radius hierarchy: control, field, button, surface, pill. */
internal object DuoRadius {
    val badge = 8.dp
    val field = 12.dp
    val control = 14.dp
    val button = 16.dp
    val surface = 24.dp
    val sheet = 32.dp
    val pill = 999.dp
}

internal val DuoTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp
    )
)
