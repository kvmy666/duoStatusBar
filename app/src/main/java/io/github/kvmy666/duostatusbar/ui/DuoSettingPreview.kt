package io.github.kvmy666.duostatusbar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kvmy666.duostatusbar.DuoRivePreview
import io.github.kvmy666.duostatusbar.DuoRiveStill
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.rememberResumed
import io.github.kvmy666.duostatusbar.hook.DuoMapping
import io.github.kvmy666.duostatusbar.hook.DuoPart
import io.github.kvmy666.duostatusbar.hook.DuoVisual
import io.github.kvmy666.duostatusbar.hook.events.LiveEventType
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import kotlinx.coroutines.delay

/**
 * FR-09: a small, endlessly looping demonstration of what one setting does.
 *
 * It morphs between two snapshots - the setting's off state and its on state - and loops forever, so
 * the row explains itself instead of asking the user to imagine the difference. The demo is built from
 * the same [io.github.kvmy666.duostatusbar.hook.DuoMapping] the status bar uses and drawn by the same
 * **Rive** scene the module ships, so it cannot show something the module does not do. (It used to draw
 * with the Canvas fallback, which is why the previews looked different from the real status bar.)
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
    slideDp: Float = 0f,
    /** The drawing to show: [R.raw.duo] for 2 SIM, [R.raw.duo_old] for Classic. */
    rawRes: Int = R.raw.duo
) {
    DuoRivePreview(
        modifier = modifier,
        size = size,
        periodMs = periodMs,
        scaleFrom = scaleFrom,
        slideDp = slideDp,
        rawRes = rawRes
    ) { phase -> off.lerp(on, phase) }
}

/**
 * CUS-008: play one live-event animation in the real Duo Rive scene.
 *
 * The chosen event is held idle, then becomes visible for the event's lifetime; the visible edge is
 * what starts the Rive Event layer. The same scene and state machine as the status bar render it, so a
 * selected prototype icon cannot animate differently here than on the bar.
 */
@Composable
fun DuoEventSimulationPreview(
    eventType: Int,
    playKey: Int,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    rawRes: Int = R.raw.duo,
    split: Boolean = false
) {
    var visible by remember(eventType, playKey) { mutableStateOf(false) }
    LaunchedEffect(eventType, playKey) {
        if (playKey == 0) return@LaunchedEffect
        visible = false
        delay(180)
        visible = true
        val eventDurationMs = when (eventType) {
            LiveEventType.FLASHLIGHT.riveId -> 1_600L
            LiveEventType.SCREENSHOT.riveId -> 1_050L
            else -> 1_450L
        }
        delay(eventDurationMs)
        visible = false
    }
    val visual = DuoMapping.visual(
        level = 40, charging = false, saver = false, showPercent = true,
        wifiLevel = 3, cellLevel = 4, airplane = false,
        eventType = eventType, eventVisible = visible
    )
    // Split mode shows only the ring part; the event preview reuses the same still, so the part is
    // applied to the visual rather than introducing a second view.
    val shown = if (split) DuoPart.RING.apply(visual) else visual
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Color(0xFF101014)),
        contentAlignment = Alignment.Center
    ) {
        DuoRiveStill(
            visual = shown,
            modifier = Modifier.fillMaxSize(),
            rawRes = rawRes,
            hardStopAfterMs = when (eventType) {
                LiveEventType.FLASHLIGHT.riveId -> 1_800L
                LiveEventType.SCREENSHOT.riveId -> 1_250L
                else -> 1_650L
            }
        )
    }
}

/**
 * A compact, looping live-event preview for a settings row.
 *
 * [eventType] is a [DuoPrefs.EVENT_*] bit, translated here into the Rive event id the scene expects.
 * The preview replays the real event animation every [EVENT_PREVIEW_PERIOD_MS] while [enabled] is true;
 * the delay-based loop cancels immediately when the master switch is off, so a disabled row neither
 * animates nor keeps a coroutine alive. It fits the [SettingSwitch] preview slot at the default size.
 */
@Composable
fun DuoEventRowPreview(
    eventType: Int,
    enabled: Boolean,
    rawRes: Int = R.raw.duo,
    split: Boolean = false,
    size: Dp = 56.dp
) {
    var playKey by remember(eventType) { mutableIntStateOf(0) }
    val resumed = rememberResumed()
    LaunchedEffect(eventType, enabled, resumed) {
        if (!enabled || !resumed) {
            playKey = 0
            return@LaunchedEffect
        }
        while (true) {
            // Play at once, then leave the event enough idle time before the next repeat.
            playKey++
            delay(EVENT_PREVIEW_PERIOD_MS)
        }
    }
    DuoEventSimulationPreview(
        eventType = eventRiveId(eventType),
        playKey = if (enabled) playKey else 0,
        size = size,
        rawRes = rawRes,
        split = split
    )
}

/** Maps a toggle bit to its [LiveEventType] Rive id; a non-bit value is treated as a Rive id already. */
private fun eventRiveId(eventBit: Int): Int = when (eventBit) {
    DuoPrefs.EVENT_ALARM -> LiveEventType.ALARM.riveId
    DuoPrefs.EVENT_FLASHLIGHT -> LiveEventType.FLASHLIGHT.riveId
    DuoPrefs.EVENT_SCREENSHOT -> LiveEventType.SCREENSHOT.riveId
    DuoPrefs.EVENT_MIC -> LiveEventType.MIC.riveId
    DuoPrefs.EVENT_LOCATION -> LiveEventType.LOCATION.riveId
    DuoPrefs.EVENT_VOLUME -> LiveEventType.VOLUME.riveId
    DuoPrefs.EVENT_UNLOCK -> LiveEventType.UNLOCK.riveId
    DuoPrefs.EVENT_CALL -> LiveEventType.CALL.riveId
    else -> eventBit
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

/** One live-event replay every 3.2 s; the event itself lasts at most 1.6 s. */
private const val EVENT_PREVIEW_PERIOD_MS = 3_200L
