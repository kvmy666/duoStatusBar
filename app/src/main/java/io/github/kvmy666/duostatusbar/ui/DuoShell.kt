package io.github.kvmy666.duostatusbar.ui

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.settings.DuoActions
import io.github.kvmy666.duostatusbar.settings.DuoOrientation
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.DuoSettings
import io.github.kvmy666.duostatusbar.settings.SettingsBridge
import io.github.kvmy666.duostatusbar.settings.StockIconHider
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DuoShell() {
    var page by rememberSaveable { mutableStateOf(DuoPage.HOME) }
    var navInteracting by remember { mutableStateOf(false) }
    GlassBackdrop(
        scrollEdge = { backdrop -> ScrollEdgeGlass(backdrop) },
        floatingBar = { backdrop ->
            LensNavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    // 80dp outer bounds for the 64dp pill: the held selector grows to 73dp and
                    // needs room above and below the pill so an ancestor clip cannot cut it.
                    .padding(horizontal = DuoSpace.xxl, vertical = 7.dp)
                    .height(80.dp)
                    .scale(if (navInteracting) 1.015f else 1f),
                pageBackdrop = backdrop,
                pressed = navInteracting,
                selected = page,
                onSelect = { page = it },
                onInteracting = { navInteracting = it }
            )
        }
    ) { backdrop ->
        Box(Modifier.fillMaxSize()) {
            when (page) {
                DuoPage.HOME -> DuoHomeScreen()
                DuoPage.CUSTOMIZATION -> CustomizationPage()
                DuoPage.REPORT -> DuoReportScreen(Modifier.fillMaxSize())
                DuoPage.SETTINGS -> Box(Modifier.fillMaxSize()) {
                    DuoSettingsScreen(Modifier.fillMaxSize(), showCustomization = false)
                    IconButton(
                        onClick = { page = DuoPage.HOME },
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(DuoSpace.md)
                            .align(Alignment.TopStart)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                            tint = DuoColors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}

internal enum class DuoPage { HOME, CUSTOMIZATION, REPORT, SETTINGS }

private data class NavItem(val page: DuoPage, val label: Int)

@Composable
private fun LensNavigationBar(
    modifier: Modifier = Modifier,
    pageBackdrop: Backdrop,
    pressed: Boolean,
    selected: DuoPage,
    onSelect: (DuoPage) -> Unit,
    onInteracting: (Boolean) -> Unit
) {
    val items = listOf(
        NavItem(DuoPage.HOME, R.string.nav_home),
        NavItem(DuoPage.CUSTOMIZATION, R.string.nav_engine),
        NavItem(DuoPage.REPORT, R.string.nav_report),
        NavItem(DuoPage.SETTINGS, R.string.nav_settings)
    )
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val tabsBackdrop = rememberLayerBackdrop()
    val rowBackdrop = rememberLayerBackdrop()
    val selectorBackdrop = rememberCombinedBackdrop(pageBackdrop, tabsBackdrop)
    val selectorMovingBackdrop = rememberCombinedBackdrop(selectorBackdrop, rowBackdrop)
    val pillHeight = 64.dp
    BoxWithConstraints(modifier) {
        val itemWidth: Dp = maxWidth / items.size.toFloat()
        val itemWidthPx = with(density) { itemWidth.toPx() }
        // Four tabs on a 1080-wide screen leave ~86dp per slot, so the old fixed selector would
        // overrun its neighbour. Cap it by the slot; wide screens keep the original 71/94dp look.
        val restWidth = minOf(71.dp, itemWidth * 0.86f)
        val restHeight = 57.dp
        val heldWidth = minOf(94.dp, itemWidth * 0.98f)
        val heldHeight = 73.dp
        val restWidthPx = with(density) { restWidth.toPx() }
        val restHeightPx = with(density) { restHeight.toPx() }
        val heldWidthPx = with(density) { heldWidth.toPx() }
        val heldHeightPx = with(density) { heldHeight.toPx() }
        val maxStretchPx = with(density) { 26.dp.toPx() }
        val maxSquashPx = with(density) { 4.dp.toPx() }
        val index = items.indexOfFirst { it.page == selected }.coerceAtLeast(0)
        val lastIndex = items.lastIndex
        fun stationX(tab: Int): Float = itemWidthPx * (tab + 0.5f)
        val centerX = remember { Animatable(stationX(index)) }
        var dragging by remember { mutableStateOf(false) }
        var dragDirection by remember { mutableStateOf(0f) }
        // Key on itemWidthPx so rotation/resize recompute the derived state instead of capturing a stale width.
        val hoverIdx by remember(itemWidthPx) { derivedStateOf { if (dragging) hoverIndex(centerX.value, itemWidthPx, items.size) else -1 } }
        val pillHeightPx = with(density) { pillHeight.toPx() }
        val navTint = glassNavigationTint()
        val navDark = glassNavigationDark()
        val rimColor = Color.White.copy(
            alpha = if (glassNavigationDark()) {
                GlassTokens.NavigationRimAlphaDark
            } else {
                GlassTokens.NavigationRimAlphaLight
            }
        )
        // ColorDuo's press progress: spring(0.7, 500). It drives the 16% specular reflection.
        val press by animateFloatAsState(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
            label = "nav press"
        )

        val moving = dragging || press > 0.01f

        LaunchedEffect(selected, itemWidthPx) {
            if (!dragging) {
                centerX.animateTo(
                    targetValue = stationX(index),
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f)
                )
            }
        }

        val selWidthDp by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (dragging) heldWidth else restWidth,
            // ColorDuo stretch spring: spring(0.8, 350).
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 350f),
            label = "sel width"
        )
        val selHeightDp by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (dragging) heldHeight else restHeight,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 350f),
            label = "sel height"
        )
        val selectorWidthPx = with(density) { selWidthDp.toPx() }
        val selectorHeightPx = with(density) { selHeightDp.toPx() }
        val selectorX = centerX.value - selectorWidthPx / 2f
        val verticalOffset = (pillHeightPx - selectorHeightPx) / 2f
        val selectorShape = RoundedCornerShape(50)

        Box(
            Modifier
                .fillMaxWidth()
                .height(pillHeight)
                .align(Alignment.Center)
                .pointerInput(itemWidthPx, items.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            dragging = true
                            dragDirection = 0f
                            onInteracting(true)
                            scope.launch { centerX.snapTo(offset.x) }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount.x != 0f) dragDirection = if (dragAmount.x > 0f) 1f else -1f
                            scope.launch {
                                centerX.snapTo(
                                    (centerX.value + dragAmount.x)
                                        .coerceIn(restWidthPx / 2f, itemWidthPx * items.size - restWidthPx / 2f)
                                )
                            }
                        },
                        onDragEnd = {
                            dragging = false
                            dragDirection = 0f
                            onInteracting(false)
                            val target = hoverIndex(centerX.value, itemWidthPx, items.size)
                            onSelect(items[target].page)
                            scope.launch {
                                centerX.animateTo(
                                    stationX(target),
                                    spring(dampingRatio = 0.78f, stiffness = 420f)
                                )
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            dragDirection = 0f
                            onInteracting(false)
                            scope.launch {
                                centerX.animateTo(
                                    stationX(index),
                                    spring(dampingRatio = 0.78f, stiffness = 420f)
                                )
                            }
                        }
                    )
                }
        ) {
            // 1. Pill material, recorded on its own. tabsBackdrop now records only the pill material;
            // the selector backdrop = page + pill. Icons/text are drawn above the selector and are
            // never sampled/blurred.
            Box(Modifier.fillMaxSize().layerBackdrop(tabsBackdrop)) {
                GlassSurface(
                    backdrop = pageBackdrop,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            // Manual rim is the FALLBACK only. On the live path the Backdrop highlight
                            // (passed to GlassSurface below) is the single rim owner.
                            if (!GlassQuality.liveSurfaces) {
                                val strokeWidth = GlassTokens.NavigationRimWidth.toPx()
                                val blur = GlassTokens.NavigationRimBlur.toPx()
                                val half = strokeWidth / 2f
                                val rimSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                                val radius = CornerRadius(size.height / 2f)
                                drawRoundRect(
                                    color = rimColor.copy(alpha = rimColor.alpha * 0.45f),
                                    topLeft = Offset(half, half),
                                    size = rimSize,
                                    cornerRadius = radius,
                                    style = Stroke(width = strokeWidth + blur)
                                )
                                drawRoundRect(
                                    color = rimColor,
                                    topLeft = Offset(half, half),
                                    size = rimSize,
                                    cornerRadius = radius,
                                    style = Stroke(width = strokeWidth)
                                )
                            }
                        },
                    shape = RoundedCornerShape(50),
                    liquid = true,
                    pressed = pressed,
                    tint = navTint,
                    showBorder = false,
                    // Pill no longer owns the live rim; the moving selector does. Keep fallback manual rim
                    // only when live surfaces are unavailable (see drawWithContent above).
                    highlight = null,
                    shadow = null
                ) {}
                // Radial press glow: white 0.16 * press, radius 1.5 * selector height, 8dp inset.
                // Centred on the moving selector (documented choice: selector centre, not active tab).
                // Press-only; no idle draw.
                // Interpretation of the 8dp inset (B2 fix): the glow layer is inset 8dp on every side of
                // the pill, so drawBehind runs in the inset rect (size = pill - 16dp). The centre is
                // remapped from pill space into that inset space: (centerX - 8dp, insetHeight/2). The
                // radius stays 1.5 * selectorHeight (selector height is the content height; the inset only
                // constrains the drawable region, it does not rescale the selector). The layer is clipped
                // to the pill shape so nothing paints outside the pill.
                if (press > 0f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(50))
                            .drawBehind {
                                val insetPx = 8.dp.toPx()
                                val center = Offset(centerX.value - insetPx, size.height / 2f)
                                val radius = selectorHeightPx * 1.5f
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = GlassTokens.NavigationReflectionAlpha * press),
                                            Color.Transparent
                                        ),
                                        center = center,
                                        radius = radius
                                    ),
                                    radius = radius,
                                    center = center
                                )
                            }
                    )
                }
                // tabsBackdrop now records only the pill material; icons/text are drawn above the
                // selector and are never sampled/blurred.
            }

            // 2. Liquid-glass selector: backdrop lens over page + pill. It sits above the pill but
            // behind the tabs (icons/text), so labels stay crisp. No blur at idle. Size animated via
            // layout, never scale.
            Box(
                Modifier
                    .zIndex(if (moving) 1f else 0f)
                    .offset { IntOffset(selectorX.roundToInt(), verticalOffset.roundToInt()) }
                    .width(selWidthDp)
                    .height(selHeightDp)
                    .shadow(elevation = 8.dp, shape = selectorShape, clip = false)
                    .drawBackdrop(
                        backdrop = if (moving) selectorMovingBackdrop else selectorBackdrop,
                        shape = { selectorShape },
                        effects = {
                            if (moving) {
                                // Transparent selector: no vibrancy, no blur; lens refracts icons/text.
                                blur(0.dp.toPx())
                                lens(
                                    refractionHeight = GlassTokens.NavigationLensHeight.toPx(),
                                    refractionAmount = GlassTokens.NavigationLensAmount.toPx(),
                                    depthEffect = true,
                                    chromaticAberration = true
                                )
                            }
                        },
                        highlight = {
                            Highlight(
                                width = GlassTokens.NavigationRimWidth,
                                blurRadius = GlassTokens.NavigationRimBlur,
                                alpha = (if (navDark) GlassTokens.NavigationRimAlphaDark else GlassTokens.NavigationRimAlphaLight) *
                                    if (moving) 1f else 0.45f,
                                style = HighlightStyle.Default(0.5f, 45f, 1f)
                            )
                        },
                        shadow = null,
                        onDrawSurface = {
                            // Frosted surface tint, always drawn.
                            val frostedAlpha = if (navDark) {
                                0.12f * (1f - 0.6f * press)
                            } else {
                                0.06f * (1f - 0.6f * press)
                            }
                            val frostedColor = if (navDark) {
                                Color.White.copy(alpha = frostedAlpha)
                            } else {
                                Color.Black.copy(alpha = frostedAlpha)
                            }
                            drawRect(frostedColor)
                            if (moving) {
                                drawRect(navTint.copy(alpha = 0.04f))
                            }
                            // Subtle inner shadow: dark stroke inset ~1.5dp, alpha ~0.18.
                            val innerInset = 1.5.dp.toPx()
                            val innerStroke = GlassTokens.NavigationRimWidth.toPx()
                            val innerSize = Size(size.width - innerInset * 2f, size.height - innerInset * 2f)
                            val innerRadius = CornerRadius((size.height - innerInset * 2f) / 2f)
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.18f),
                                topLeft = Offset(innerInset, innerInset),
                                size = innerSize,
                                cornerRadius = innerRadius,
                                style = Stroke(width = innerStroke)
                            )
                            // Keep the existing soft rim.
                            val strokeWidth = GlassTokens.NavigationRimWidth.toPx()
                            val half = strokeWidth / 2f
                            val rimSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                            val radius = CornerRadius(size.height / 2f)
                            drawRoundRect(
                                color = rimColor.copy(alpha = rimColor.alpha * 0.45f),
                                topLeft = Offset(half, half),
                                size = rimSize,
                                cornerRadius = radius,
                                style = Stroke(width = strokeWidth)
                            )
                        }
                    )
            )
            // Tab icons and labels drawn above the selector so they stay crisp; selector is below.
            Row(Modifier.zIndex(if (moving) 0f else 1f).fillMaxSize().selectableGroup().layerBackdrop(rowBackdrop)) {
                items.forEachIndexed { itemIndex, item ->
                    val active = item.page == selected
                    val hovered = hoverIdx == itemIndex
                    val tabTint by animateColorAsState(
                        targetValue = navTabColor(
                            hovered,
                            active,
                            moving,
                            Color.White
                        ),
                        label = "nav tab tint"
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = active,
                                onClick = { onSelect(item.page) },
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            )
                            .padding(horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(NavIconSource.iconFor(item.page)),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(tabTint),
                            modifier = Modifier.size(if (active) 24.dp else 17.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(item.label),
                            color = tabTint,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomizationPage() {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(DuoPrefs.read(context)) }
    var fallback by remember { mutableStateOf(DuoPrefs.fallback(context)) }
    val autoExpand = remember { DuoActions.isAutoExpandInstalled(context) }
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) {
        settings = DuoPrefs.read(context)
        fallback = DuoPrefs.fallback(context)
    }

    fun persist(new: DuoSettings) {
        val wasEnabled = settings.enabled
        DuoPrefs.write(context, new)
        context.sendBroadcast(Intent(DuoPrefs.ACTION_SETTINGS_CHANGED))
        SettingsBridge.push(context)
        settings = new
        val other = if (DuoOrientation.of(context) == DuoOrientation.LANDSCAPE) {
            DuoOrientation.PORTRAIT
        } else {
            DuoOrientation.LANDSCAPE
        }
        if (wasEnabled && !new.enabled && DuoPrefs.hideStockIcons(context) &&
            !DuoPrefs.read(context, other).enabled
        ) {
            StockIconHider.apply(context, false) { }
        }
    }

    val search = SearchGate { true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scroll)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.customization_title),
            style = MaterialTheme.typography.headlineLarge,
            color = DuoColors.TextPrimary
        )

         // Engine selector card.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(DuoRadius.surface),
            colors = CardDefaults.cardColors(containerColor = DuoColors.Surface.copy(alpha = 0.94f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.engine_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = stringResource(
                        R.string.engine_current,
                        when (settings.engineMode) {
                            DuoPrefs.ENGINE_RIVE -> stringResource(R.string.engine_rive)
                            DuoPrefs.ENGINE_CANVAS -> stringResource(R.string.engine_canvas)
                            else -> stringResource(R.string.engine_auto)
                        }
                    )
                )
                Text(text = stringResource(R.string.engine_mode_detail), style = MaterialTheme.typography.bodySmall)
                OptionPicker(
                    label = stringResource(R.string.engine_mode),
                    options = listOf(
                        DuoPrefs.ENGINE_AUTO to stringResource(R.string.engine_auto),
                        DuoPrefs.ENGINE_RIVE to stringResource(R.string.engine_rive),
                        DuoPrefs.ENGINE_CANVAS to stringResource(R.string.engine_canvas)
                    ),
                    selectedKey = settings.engineMode,
                    enabled = true
                ) { key ->
                    persist(settings.copy(engineMode = key, useRive = key != DuoPrefs.ENGINE_CANVAS))
                }
                Text(text = stringResource(R.string.engine_auto_note), style = MaterialTheme.typography.bodySmall)
                if (fallback.isNotEmpty()) {
                    Text(text = stringResource(R.string.engine_fallback, fallback))
                }
            }
        }

        // Seven sections.
        CustomizeSection(settings = settings, onUpdate = { persist(it) }, matches = search, onRestart = { restartSystemUi(context) })
        AnimationsSection(settings = settings, onUpdate = { persist(it) }, matches = search)
        AppearanceSection(settings = settings, onUpdate = { persist(it) }, matches = search)
        SurfacesSection(settings = settings, onUpdate = { persist(it) }, matches = search)
        IconsSection(settings = settings, onUpdate = { persist(it) }, matches = search)
        ActionsSection(settings = settings, onUpdate = { persist(it) }, matches = search, autoExpand = autoExpand)
        LiveEventsSection(matches = search, settings = settings, onUpdate = { persist(it) })
        DownloadSection(settings = settings, onUpdate = { persist(it) }, matches = search)
    }
}
