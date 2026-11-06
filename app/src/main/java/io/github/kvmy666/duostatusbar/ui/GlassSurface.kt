package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens

/**
 * Apple's published Liquid Glass values, cross-checked against the iOS 26 design kit transcriptions
 * (light angle -45°, material opacity 60%, frost 14, depth 16, layered shadow 0/8/28 at 14%) and the
 * Kyant Backdrop examples (vibrancy = 1.5 saturation, lens 16/32 dp).
 *
 * The blur and lens values are dp converted with `toPx()` at the draw site: passing raw `16f` was the
 * earlier mistake that made the glass look almost flat.
 */
internal object GlassTokens {
    const val SATURATION = 1.5f
    const val BRIGHTNESS = 0.04f
    const val CONTRAST = 1.0f

    val NavigationBlur = 12.dp
    val CardBlur = 24.dp
    val ControlBlur = 12.dp
    val ThinBlur = 8.dp

    val NavigationLensHeight = 24.dp
    val NavigationLensAmount = 24.dp
    val ControlLensHeight = 8.dp
    val ControlLensAmount = 12.dp

    val CardShape = RoundedCornerShape(DuoRadius.surface)
    val CardTint = Color.White.copy(alpha = 0.07f)
    val ControlTint = Color.White.copy(alpha = 0.09f)
    val BorderHighlight = Color.White.copy(alpha = 0.20f)

    // ColorDuo 1.1.7 liquid navigation: #121212/#FAFAFA at 0.40, a 16% liquid selection surface
    // and a white rim instead of a border. The 18dp shadow and the wine gradient are gone.
    val NavigationTintDark = Color(0xFF121212).copy(alpha = 0.40f)
    val NavigationTintLight = Color(0xFFFAFAFA).copy(alpha = 0.40f)
    const val NavigationSelectionAlpha = 0.16f
    const val NavigationReflectionAlpha = 0.16f
    val NavigationRimWidth = 0.75.dp
    val NavigationRimBlur = 0.4.dp
    const val NavigationRimAlphaDark = 0.78f
    const val NavigationRimAlphaLight = 0.96f
}

/** The measurement is a dark-first product surface, so the tint follows the active Material scheme. */
@Composable
internal fun glassNavigationDark(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
internal fun glassNavigationTint(): Color =
    if (glassNavigationDark()) GlassTokens.NavigationTintDark else GlassTokens.NavigationTintLight

/**
 * The live lens is a runtime shader. On the API 36 emulator's software GPU it caused an ANR at
 * launch (263 ms frames), so the emulator gets a cheaper preview: no chromatic lens and half blur.
 * Phones keep the full Apple values from [GlassTokens]. This is a quality switch, not a fake result.
 */
internal object GlassQuality {
    val isEmulator: Boolean = run {
        val fp = android.os.Build.FINGERPRINT
        val hw = android.os.Build.HARDWARE
        fp.startsWith("generic") || fp.contains("emulator") ||
            hw.contains("ranchu") || hw.contains("goldfish")
    }
    val lensEnabled: Boolean = !isEmulator
    /** One live surface is affordable; the whole settings stack is not. */
    val liveSurfaces: Boolean = !isEmulator
    /** The emulator's software GPU cannot afford one live backdrop per switch/slider. */
    val liveControls: Boolean = !isEmulator
    val blurScale: Float = if (isEmulator) 0.5f else 1f
}

internal val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * App-only liquid-glass scaffold.
 *
 * Two recordings, on purpose:
 *  - [wallpaper] is the static gradient only. Controls and cards inside the page sample it, so glass
 *    never samples the layer that contains itself (the RC-017 recursive-capture crash).
 *  - [page] is the page content. The floating bar samples wallpaper + page through a combined
 *    backdrop, so the scrolling text is refracted by the bar without being captured twice.
 *
 * Nothing here is used by the SystemUI module; the module keeps its own Canvas/Rive path.
 */
@Composable
fun GlassBackdrop(
    floatingBar: @Composable (Backdrop) -> Unit = {},
    scrollEdge: @Composable (Backdrop) -> Unit = {},
    content: @Composable (Backdrop) -> Unit
) {
    val wallpaper = rememberLayerBackdrop()
    val page = rememberLayerBackdrop()
    val barBackdrop = rememberCombinedBackdrop(wallpaper, page)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(wallpaper)
        ) {
            Wallpaper()
        }
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(page)
        ) {
            CompositionLocalProvider(LocalGlassBackdrop provides wallpaper) {
                content(wallpaper)
            }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides wallpaper) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                scrollEdge(barBackdrop)
                floatingBar(barBackdrop)
            }
        }
    }
}

@Composable
private fun Wallpaper() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(DuoColors.BackgroundDeep)
        // Two wine blooms are the only light in the scene: they give the liquid glass an edge to
        // refract without turning the background into a decorative gradient.
        drawCircle(
            color = DuoColors.Wine.copy(alpha = 0.10f),
            radius = size.minDimension * 0.58f,
            center = Offset(size.width * 0.92f, size.height * 0.04f)
        )
        drawCircle(
            color = DuoColors.Wine.copy(alpha = 0.08f),
            radius = size.minDimension * 0.55f,
            center = Offset(size.width * 0.08f, size.height * 0.94f)
        )
    }
}

@Composable
fun GlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = GlassTokens.CardShape,
    liquid: Boolean = false,
    pressed: Boolean = false,
    blurRadius: androidx.compose.ui.unit.Dp = if (liquid) GlassTokens.NavigationBlur else GlassTokens.CardBlur,
    tint: Color = if (liquid) glassNavigationTint() else GlassTokens.CardTint,
    borderBrush: Brush? = null,
    showBorder: Boolean = true,
    highlight: (() -> Highlight)? = { Highlight.Default },
    shadow: (() -> Shadow)? = { Shadow.Default },
    content: @Composable () -> Unit
) {
    // ColorDuo's liquid navigation has no shadow; the rim is the only edge. Cards keep the iOS layer.
    val depth = if (liquid) {
        modifier
    } else {
        modifier.shadow(
            elevation = 8.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.14f),
            spotColor = Color.Black.copy(alpha = 0.14f)
        )
    }
    val surface = if (GlassQuality.liveSurfaces) {
        depth.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                // Backdrop applies effects in the documented order: colour filter -> blur -> lens.
                colorControls(
                    brightness = GlassTokens.BRIGHTNESS,
                    contrast = GlassTokens.CONTRAST,
                    saturation = GlassTokens.SATURATION
                )
                blur((blurRadius * GlassQuality.blurScale).toPx())
                if (liquid && GlassQuality.lensEnabled) lens(
                    refractionHeight = GlassTokens.NavigationLensHeight.toPx(),
                    refractionAmount = GlassTokens.NavigationLensAmount.toPx(),
                    depthEffect = true,
                    chromaticAberration = true
                )
            },
            highlight = highlight,
            shadow = shadow,
            // The layerBlock keeps a press-scale from scaling the sampled backdrop itself.
            layerBlock = {
                val scale = if (pressed) 1.012f else 1f
                scaleX = scale
                scaleY = scale
            },
            onDrawSurface = { drawRect(tint) }
        )
    } else {
        // Static preview material: same tint, no runtime shader. The API 36 emulator cannot
        // sustain the live path (200 ms p95), but a phone gets GlassQuality.liveSurfaces=true.
        depth.background(
            Brush.verticalGradient(
                listOf(tint.copy(alpha = tint.alpha + 0.06f), tint.copy(alpha = tint.alpha * 0.55f))
            ),
            shape
        )
    }
    val edged = if (showBorder) {
        surface.border(
            width = 1.dp,
            brush = borderBrush ?: Brush.linearGradient(
                listOf(
                    GlassTokens.BorderHighlight,
                    Color.White.copy(alpha = 0.04f),
                    DuoColors.Wine.copy(alpha = 0.24f)
                )
            ),
            shape = shape
        )
    } else {
        surface
    }
    Box(modifier = edged) {
        content()
    }
}

/**
 * Settings sections use a translucent frosted card, not a live backdrop.
 *
 * The API 36 emulator showed why: ten simultaneously-scrolling `LayerBackdrop` cards plus the scroll
 * edge and the navigation material caused an ANR. Apple puts the liquid-glass layer on navigation and
 * controls, not on every content card; the controls inside these cards are still real glass.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassTokens.CardShape,
    colors: CardColors = CardDefaults.cardColors(),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.border(1.dp, DuoColors.Hairline, shape),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = colors.containerColor.copy(alpha = 0.94f),
            contentColor = colors.contentColor
        )
    ) {
        Column { content() }
    }
}

/**
 * The scroll edge material from iOS 26: the content continues under the top and bottom edges, but a
 * progressively masked blur keeps the bars readable. The mask is a `DstIn` gradient inside an offscreen
 * layer, so the blur fades out instead of ending on a hard rectangle.
 */
@Composable
fun ScrollEdgeGlass(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    topHeight: androidx.compose.ui.unit.Dp = 72.dp,
    bottomHeight: androidx.compose.ui.unit.Dp = 92.dp
) {
    Box(modifier.fillMaxSize()) {
        ScrollEdge(backdrop, topHeight, top = true, modifier = Modifier.align(Alignment.TopCenter))
        ScrollEdge(backdrop, bottomHeight, top = false, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ScrollEdge(
    backdrop: Backdrop,
    height: androidx.compose.ui.unit.Dp,
    top: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        if (!GlassQuality.liveSurfaces) {
            // Static scroll shade on the emulator: the fade is preserved, the full-width blur is not.
            val shade = listOf(
                DuoColors.BackgroundDeep.copy(alpha = 0.48f),
                DuoColors.BackgroundDeep.copy(alpha = 0.16f),
                Color.Transparent
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(if (top) shade else shade.reversed()))
            )
            return@Box
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = {
                        colorControls(
                            brightness = GlassTokens.BRIGHTNESS,
                            contrast = GlassTokens.CONTRAST,
                            saturation = GlassTokens.SATURATION
                        )
                        blur((GlassTokens.CardBlur * GlassQuality.blurScale).toPx())
                    }
                )
        )
        Canvas(Modifier.fillMaxSize()) {
            val fade = listOf(
                Color.Black,
                Color.Black.copy(alpha = 0.72f),
                Color.Transparent
            )
            drawRect(
                brush = Brush.verticalGradient(if (top) fade else fade.reversed()),
                blendMode = BlendMode.DstIn
            )
        }
    }
}
