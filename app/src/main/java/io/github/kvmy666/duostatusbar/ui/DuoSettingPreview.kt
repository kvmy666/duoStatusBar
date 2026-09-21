package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.kvmy666.duostatusbar.hook.DuoCanvasView
import io.github.kvmy666.duostatusbar.hook.DuoElement
import io.github.kvmy666.duostatusbar.hook.DuoVisual

/**
 * FR-09: a small, endlessly looping demonstration of what one setting does.
 *
 * It morphs between two snapshots - the setting's off state and its on state - and loops forever, so
 * the row explains itself instead of asking the user to imagine the difference. The demo is built from
 * the same [io.github.kvmy666.duostatusbar.hook.DuoMapping] the status bar uses and drawn by the same
 * `DuoCanvasView`, so it cannot show something the module does not do.
 *
 * The Canvas element is deliberate rather than the Rive one: several of these run at once, and each
 * Rive view is a native instance. The Canvas element is pure Android drawing, and it covers the ring
 * behaviour that these demos are about.
 */
@Composable
fun DuoSettingPreview(
    off: DuoVisual,
    on: DuoVisual,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    periodMs: Long = DEMO_PERIOD_MS,
    /** For the size setting: the element's scale at the off end, eased to 1 at the on end. */
    scaleFrom: Float = 1f,
    /** For the position setting: how far the element slides, in dp, at the on end. */
    slideDp: Float = 0f
) {
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(periodMs) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val elapsedMs = (now - start) / 1_000_000L
            phase = demoPhase((elapsedMs % periodMs).toFloat() / periodMs)
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(DEMO_BACKGROUND),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> DuoCanvasView(context).also { it.start() } },
            update = { view ->
                (view as? DuoElement)?.render(off.lerp(on, phase))
                val scale = scaleFrom + (1f - scaleFrom) * phase
                view.scaleX = scale
                view.scaleY = scale
                view.translationX = slideDp * phase * view.resources.displayMetrics.density
            }
        )
    }
}

/**
 * Where the demo is in its off -> on -> off cycle, given the fraction [t] through one period.
 *
 * A plain triangle wave would spend half the loop mid-morph, which reads as a blur rather than as two
 * states. So each end is held still for a moment and the move between them is eased.
 */
internal fun demoPhase(t: Float): Float = when {
    t < HOLD -> 0f
    t < HOLD + MOVE -> smooth((t - HOLD) / MOVE)
    t < HOLD + MOVE + HOLD -> 1f
    t < HOLD + MOVE + HOLD + MOVE -> 1f - smooth((t - HOLD - MOVE - HOLD) / MOVE)
    else -> 0f
}

private fun smooth(x: Float): Float = x * x * (3f - 2f * x)

/** Fraction of the loop each end is held, and spent moving between them. */
private const val HOLD = 0.16f
private const val MOVE = 0.30f

/** Long enough to read, short enough that several rows are not all in step. */
private const val DEMO_PERIOD_MS = 2400L

/**
 * The chip the demo is drawn on. The element is white, so it needs a dark backing to be visible on a
 * light card — without this the row demos were effectively invisible in light mode, which is exactly
 * what "the GIFs aren't showing what the toggle does" looked like.
 */
private val DEMO_BACKGROUND = Color(0xFF101014)
