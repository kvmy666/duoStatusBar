package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Red Wine, complete and in both modes (FR-11).
 *
 * The values mirror `res/values/colors.xml` one-for-one: the XML side styles the Activity and any
 * platform-drawn surface, the Compose side styles everything inside it. Keeping them equal is the point —
 * a theme that exists twice and disagrees is how an app starts looking broken in one mode only.
 */
private val RedWineLight = lightColorScheme(
    primary = Color(0xFF7B1E3A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9DF),
    onPrimaryContainer = Color(0xFF3F001C),
    secondary = Color(0xFF74565C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9DF),
    onSecondaryContainer = Color(0xFF2B151A),
    tertiary = Color(0xFF7C5635),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF2D1600),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF22191B),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF22191B),
    surfaceVariant = Color(0xFFF3DDE0),
    onSurfaceVariant = Color(0xFF524345),
    outline = Color(0xFF857376),
    outlineVariant = Color(0xFFD7C1C4)
)

private val RedWineDark = darkColorScheme(
    primary = Color(0xFFFFB1C1),
    onPrimary = Color(0xFF4C0F27),
    primaryContainer = Color(0xFF641E37),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = Color(0xFFE3BDC3),
    onSecondary = Color(0xFF42292F),
    secondaryContainer = Color(0xFF5A4046),
    onSecondaryContainer = Color(0xFFFFD9DF),
    tertiary = Color(0xFFEFBD91),
    onTertiary = Color(0xFF47290B),
    tertiaryContainer = Color(0xFF613C1C),
    onTertiaryContainer = Color(0xFFFFDCC2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191113),
    onBackground = Color(0xFFF0DEDF),
    surface = Color(0xFF191113),
    onSurface = Color(0xFFF0DEDF),
    surfaceVariant = Color(0xFF524345),
    onSurfaceVariant = Color(0xFFD7C1C4),
    outline = Color(0xFFA08C8F),
    outlineVariant = Color(0xFF524345)
)

@Composable
fun DuoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) RedWineDark else RedWineLight,
        content = content
    )
}
