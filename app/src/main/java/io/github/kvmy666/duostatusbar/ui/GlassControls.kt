package io.github.kvmy666.duostatusbar.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import kotlin.math.roundToInt

/**
 * The settings switch, drawn as a small liquid-glass capsule. It uses the same Apple values as the
 * navigation material, at a thinner scale: colour filter -> blur -> lens, with a readable tint.
 *
 * A Material switch remains the fallback when no wallpaper backdrop is available (previews/tests).
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null) {
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        return
    }
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.62f,
            stiffness = 420f
        ),
        label = "glass-switch"
    )
    val density = LocalDensity.current
    val trackWidth = 52.dp
    val trackHeight = 32.dp
    val knob = 24.dp
    val inset = 4.dp
    val travel = with(density) { (trackWidth - knob - inset * 2).toPx() }
    Box(
        Modifier
            .size(trackWidth, trackHeight)
            .alpha(if (enabled) 1f else 0.42f)
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled
            ) { onCheckedChange(!checked) }
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            }
    ) {
        // Track: a glass capsule with the selected state carried by a wine tint, not an opaque fill.
        // On the emulator the live blur is replaced by a static frosted gradient (same palette), so
        // the software GPU does not pay for one backdrop per switch.
        val wine = DuoColors.Wine
        val offTrack = DuoColors.SurfaceRaised
        val track = if (GlassQuality.liveControls) {
            Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        colorControls(
                            brightness = if (checked) GlassTokens.BRIGHTNESS else -0.03f,
                            contrast = GlassTokens.CONTRAST,
                            // An off switch must not inherit the wine bloom behind it.
                            saturation = if (checked) GlassTokens.SATURATION else 0f
                        )
                        blur((GlassTokens.ControlBlur * GlassQuality.blurScale).toPx())
                        if (GlassQuality.lensEnabled) lens(
                            refractionHeight = 6.dp.toPx(),
                            refractionAmount = 9.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = checked
                        )
                    },
                    onDrawSurface = {
                        drawRect(
                            if (checked) {
                                wine.copy(alpha = 0.72f)
                            } else {
                                offTrack.copy(alpha = 0.90f)
                            }
                        )
                    }
                )
        } else {
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (checked) wine.copy(alpha = 0.86f) else offTrack,
                            if (checked) DuoColors.WineDeep.copy(alpha = 0.92f) else DuoColors.Background
                        )
                    ),
                    CircleShape
                )
        }
        Box(
            track.border(
                1.dp,
                if (checked) DuoColors.WineMist.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.14f),
                CircleShape
            )
        )
        // Knob: a bright glass bead. It is not drawn from the track backdrop (that would sample the
        // switch's own layer); a high-opacity tint reads the same at this size and avoids recursion.
        Box(
            Modifier
                .offset {
                    IntOffset(
                        with(density) { inset.toPx() }.roundToInt() +
                            (travel * progress).roundToInt(),
                        with(density) { inset.toPx() }.roundToInt()
                    )
                }
                .size(knob)
                .shadow(3.dp, CircleShape, clip = false)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.98f), Color(0xFFDDE7FF))
                    ),
                    CircleShape
                )
                .border(0.5.dp, Color.White.copy(alpha = 0.9f), CircleShape)
        )
    }
}

/**
 * The settings slider, with a glass track and a glass thumb. The thumb samples the wallpaper, not the
 * track, so it cannot recurse; the filled part is a wine gradient clipped to the capsule.
 */
@Composable
fun GlassSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null) {
        // Fallback limitation: the Material Slider keeps its own whole-track drag. Replacing it
        // with the thumb-only hit target would duplicate the whole glass slider; left as-is only
        // in the no-backdrop (preview/test) path, which is not the shipped UI.
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            enabled = enabled
        )
        return
    }
    val density = LocalDensity.current
    val thumbSize = 28.dp
    val thumbPx = with(density) { thumbSize.toPx() }
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)

    fun valueAt(x: Float, width: Int): Float {
        val travel = (width - thumbPx).coerceAtLeast(1f)
        val raw = (x / travel).coerceIn(0f, 1f)
        val snapped = if (steps > 0) {
            val divisions = steps + 1
            (raw * divisions).roundToInt().toFloat() / divisions
        } else {
            raw
        }
        return range.start + snapped * span
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, range, steps)
                if (enabled) {
                    setProgress { target ->
                        onValueChange(target.coerceIn(range.start, range.endInclusive))
                        true
                    }
                }
            }
    ) {
        val trackWidth = with(density) { maxWidth.toPx() }
        var dragging by remember { mutableStateOf(false) }
        var dragFraction by remember { mutableFloatStateOf(fraction) }
        LaunchedEffect(value) { if (!dragging) dragFraction = ((value - range.start) / span).coerceIn(0f, 1f) }

        fun snapFraction(raw: Float): Float {
            val coerced = raw.coerceIn(0f, 1f)
            return if (steps > 0) {
                val divisions = steps + 1
                (coerced * divisions).roundToInt().toFloat() / divisions
            } else coerced
        }

        val displayFraction = if (dragging) snapFraction(dragFraction) else fraction
        val filledWidth = (trackWidth - thumbPx) * displayFraction
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .align(Alignment.CenterStart)
        ) {
            if (GlassQuality.liveControls) {
                GlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    blurRadius = GlassTokens.ControlBlur,
                    tint = Color.White.copy(alpha = 0.10f)
                ) {}
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF273044).copy(alpha = 0.78f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                )
            }
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF7B1E3A), Color(0xFFC26A8C))
                        )
                    )
            )
        }
        val thumb = Modifier
            .offset { IntOffset(filledWidth.roundToInt(), 0) }
            .size(thumbSize)
            .align(Alignment.CenterStart)
            .shadow(5.dp, CircleShape, clip = false)
        val thumbSurface = if (GlassQuality.liveControls) {
            thumb.drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    colorControls(
                        brightness = 0.08f,
                        contrast = 1.08f,
                        saturation = 1.2f
                    )
                    blur((GlassTokens.ThinBlur * GlassQuality.blurScale).toPx())
                    if (GlassQuality.lensEnabled) lens(
                        refractionHeight = 5.dp.toPx(),
                        refractionAmount = 7.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = true
                    )
                },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = 0.82f))
                }
            )
        } else {
            thumb.background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0xFFD6E0F5))),
                CircleShape
            )
        }
        Box(thumbSurface.border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape))

        // Transparent full-size hit target for tap/drag gestures.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(enabled, range, steps, thumbPx, trackWidth) {
                    if (!enabled) return@pointerInput
                    coroutineScope {
                        launch {
                            detectTapGestures { offset ->
                                val newValue = sliderValueAt(offset.x, trackWidth.roundToInt(), thumbPx, range.start, range.endInclusive, steps)
                                dragFraction = ((newValue - range.start) / span).coerceIn(0f, 1f)
                                onValueChange(newValue)
                            }
                        }
                        launch {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    dragging = true
                                    val travel = (trackWidth - thumbPx).coerceAtLeast(1f)
                                    dragFraction = (offset.x / travel).coerceIn(0f, 1f)
                                    val snapped = snapFraction(dragFraction)
                                    onValueChange(range.start + snapped * span)
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val travel = (trackWidth - thumbPx).coerceAtLeast(1f)
                                    dragFraction = (dragFraction + dragAmount / travel).coerceIn(0f, 1f)
                                    val snapped = snapFraction(dragFraction)
                                    onValueChange(range.start + snapped * span)
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false }
                            )
                        }
                    }
                }
        )
    }
}

/** Pure mapping from a pointer x (in the slider's outer box) to a slider value. */
internal fun sliderValueAt(
    xPx: Float,
    widthPx: Int,
    thumbPx: Float,
    rangeStart: Float,
    rangeEnd: Float,
    steps: Int
): Float {
    val span = (rangeEnd - rangeStart).takeIf { it > 0f } ?: 1f
    val travel = (widthPx - thumbPx).coerceAtLeast(1f)
    val raw = (xPx / travel).coerceIn(0f, 1f)
    val snapped = if (steps > 0) {
        val divisions = steps + 1
        (raw * divisions).roundToInt().toFloat() / divisions
    } else {
        raw
    }
    return rangeStart + snapped * span
}
