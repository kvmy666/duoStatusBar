package io.github.kvmy666.duostatusbar

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.ViewModelInstance
import io.github.kvmy666.duostatusbar.hook.DuoBinder
import io.github.kvmy666.duostatusbar.hook.DuoMapping
import io.github.kvmy666.duostatusbar.hook.DuoVisual
import io.github.kvmy666.duostatusbar.ui.demoPhase
import kotlinx.coroutines.delay

/**
 * The real `duo.riv` with live controls — same asset, same mapping and same binding code as the status
 * bar, so what is judged here is what ships (Phase 2 controls, and the tuning surface for Phase 4).
 *
 * The point is the two things a still image cannot show: the reveal and the airplane morph.
 * Every control writes state; state goes through [DuoMapping] exactly as SystemUI does it; [DuoBinder]
 * pushes it at the drawing. A wiring mistake therefore shows up here, on a screen, instead of in the
 * status bar.
 *
 * The `Canvas` renderer matches the SystemUI path on purpose: a preview that flatters the asset with a
 * different backend would tune the wrong thing.
 */
@Composable
fun DuoPreview(
    modifier: Modifier = Modifier,
    pixelSize: Int = 200,
    loop: Boolean = false,
    revealMs: Int = 1000
) {
    var level by remember { mutableFloatStateOf(78f) }
    var charging by remember { mutableStateOf(false) }
    var saver by remember { mutableStateOf(false) }
    var airplane by remember { mutableStateOf(false) }
    var dnd by remember { mutableStateOf(false) }
    var wifi by remember { mutableFloatStateOf(3f) }
    var cell by remember { mutableFloatStateOf(4f) }
    /** FR-06: with Wi-Fi off the middle slot shows the cellular generation instead of the glyph. */
    var wifiOn by remember { mutableStateOf(true) }
    var generation by remember { mutableIntStateOf(0) }
    var revealTick by remember { mutableIntStateOf(0) }
    val instance = remember { mutableStateOf<ViewModelInstance?>(null) }

    // The one picture, from the same mapping the status bar uses.
    val visual = DuoMapping.visual(
        level = level.toInt(),
        charging = charging,
        saver = saver,
        showPercent = true,
        wifiLevel = wifi.toInt(),
        cellLevel = cell.toInt(),
        airplane = airplane,
        dnd = dnd,
        wifiOn = wifiOn,
        networkText = GENERATIONS[generation]
    )

    // The state machine fires on the false -> true edge, so the request is cleared afterwards.
    LaunchedEffect(revealTick) {
        val vm = instance.value ?: return@LaunchedEffect
        try {
            DuoBinder.requestReveal(vm, revealMs)
            delay(revealMs + 60L)
        } finally {
            DuoBinder.requestReveal(vm, 0)
        }
    }

    // FR-09: an infinite looping demonstration of what the reveal looks like, on demand.
    LaunchedEffect(loop, revealTick) {
        if (!loop) return@LaunchedEffect
        while (true) {
            val vm = instance.value ?: break
            try {
                DuoBinder.requestReveal(vm, revealMs)
                delay(revealMs + 60L)
            } finally {
                DuoBinder.requestReveal(vm, 0)
            }
            delay(LOOP_GAP_MS)
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pixelSize.dp)
                .background(Color(0xFF101014)),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier
                    .height(pixelSize.dp)
                    .padding(8.dp),
                factory = { context -> createRiveView(context, instance) },
                update = { pushVisual(instance.value, visual) }
            )
        }

        LabelledSlider("Battery ${level.toInt()}%", level, 0f..100f, steps = 0) { level = it }
        Toggle("Charging (bolt, green)", charging) { charging = it }
        Toggle("Battery saver (yellow)", saver) { saver = it }
        Toggle("Airplane mode (morph)", airplane) { airplane = it }
        Toggle("Do Not Disturb (moon)", dnd) { dnd = it }
        LabelledSlider("Wi-Fi ${wifi.toInt()} of 3", wifi, 0f..3f, steps = 2) { wifi = it }
        LabelledSlider("Cellular ${cell.toInt()} of 4", cell, 0f..4f, steps = 3) { cell = it }
        Toggle("Wi-Fi off (cellular label in the slot)", wifiOn) { wifiOn = it }
        LabelledSlider(
            "Generation ${GENERATIONS[generation]}",
            generation.toFloat(), 0f..3f, steps = 2
        ) { generation = it.toInt() }
        Button(onClick = { revealTick++ }) { Text("Replay reveal ($revealMs ms)") }
        Text(
            text = "tint #${"%08X".format(visual.tint)} · ${DuoBinder.PROPERTY_COUNT} properties bound per snapshot",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * FR-09: a small, looping Rive demonstration of one animation setting.
 *
 * It shows the *real* element, so the difference a switch makes is the real motion rather than a picture
 * of it: [visualAt] returns the snapshot for the current phase and the state machine animates between
 * them, which is what makes the arrival, the departure and the charging journey visible here.
 */
@Composable
fun DuoRivePreview(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    periodMs: Long = RIVE_DEMO_PERIOD_MS,
    fireReveal: Boolean = false,
    scaleFrom: Float = 1f,
    /** For the position setting: how far the element slides, in dp, at the on end. */
    slideDp: Float = 0f,
    visualAt: (Float) -> DuoVisual
) {
    val instance = remember { mutableStateOf<ViewModelInstance?>(null) }
    val lastVisual = remember { mutableStateOf<DuoVisual?>(null) }
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(periodMs) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            phase = demoPhase((((now - start) / 1_000_000L) % periodMs).toFloat() / periodMs)
        }
    }
    // The arrival is a trigger, not a state, so it is fired once per loop rather than driven by phase.
    LaunchedEffect(fireReveal, periodMs) {
        if (!fireReveal) return@LaunchedEffect
        while (true) {
            delay(periodMs)
            val vm = instance.value ?: continue
            try {
                DuoBinder.requestReveal(vm, 1000)
                delay(160)
                DuoBinder.requestReveal(vm, 0)
            } catch (_: Throwable) {
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF101014)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> createRiveView(ctx, instance) },
            update = { view ->
                val visual = visualAt(phase)
                // Only push when the snapshot actually changes, and only once the view model has bound:
                // the phase moves every frame, but the state machine needs one write per state.
                val vm = instance.value
                if (vm != null && visual != lastVisual.value) {
                    pushVisual(vm, visual)
                    lastVisual.value = visual
                }
                val scale = scaleFrom + (1f - scaleFrom) * phase
                view.scaleX = scale
                view.scaleY = scale
                view.translationX = slideDp * phase * view.resources.displayMetrics.density
            }
        )
    }
}

/**
 * The real Rive element, held still on one [visual]. Used where a demo must not cycle - the position
 * editor, whose translation is the user's drag rather than a looping phase.
 */
@Composable
fun DuoRiveStill(
    visual: DuoVisual,
    modifier: Modifier = Modifier,
    translationXDp: Float = 0f
) {
    val instance = remember { mutableStateOf<ViewModelInstance?>(null) }
    val lastVisual = remember { mutableStateOf<DuoVisual?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { ctx -> createRiveView(ctx, instance) },
        update = { view ->
            val vm = instance.value
            if (vm != null && visual != lastVisual.value) {
                pushVisual(vm, visual)
                lastVisual.value = visual
            }
            view.translationX = translationXDp * view.resources.displayMetrics.density
        }
    )
}

/** Builds the Rive view with the same renderer and layout the status bar uses. */
private fun createRiveView(
    context: Context,
    instance: MutableState<ViewModelInstance?>
): RiveAnimationView {
    val view = try {
        RiveInit.ensure(context)
        val builder = RiveAnimationView.Builder(context)
            .setRendererType(RendererType.Canvas)
            .setResource(R.raw.duo)
            .setArtboardName(ARTBOARD)
            .setStateMachineName(STATE_MACHINE)
            .setFit(Fit.CONTAIN)
            .setAutoplay(true)
            .setAutoBind(true)
        RiveAnimationView(builder)
    } catch (t: Throwable) {
        // An empty preview box beats a crash in the app that exists to diagnose the module.
        L.e("Rive preview setup failed: ${t.javaClass.simpleName}: ${t.message}")
        return RiveAnimationView(context)
    }
    watchForViewModelInstance(view, onFound = { instance.value = it })
    return view
}

private fun pushVisual(vm: ViewModelInstance?, visual: DuoVisual) {
    if (vm == null) return
    try {
        val failures = DuoBinder.apply(vm, visual)
        if (failures > 0) {
            L.w("preview: $failures of ${DuoBinder.PROPERTY_COUNT} properties did not bind")
        }
    } catch (t: Throwable) {
        L.w("preview push: ${t.javaClass.simpleName}: ${t.message}")
    }
}

/** The state machine appears some time after the bytes are handed over: watch for it, don't assume. */
private fun watchForViewModelInstance(
    view: RiveAnimationView,
    onFound: (ViewModelInstance) -> Unit,
    attempt: Int = 0
) {
    view.postDelayed({
        try {
            val machine = view.stateMachines.firstOrNull()
            val found = machine?.viewModelInstance
            if (found != null) {
                onFound(found)
                L.i("preview ready (machines=${view.stateMachines.size}, inputs=${machine?.inputNames})")
            } else if (attempt < MAX_POLLS) {
                watchForViewModelInstance(view, onFound, attempt + 1)
            } else {
                L.w("preview: no view model instance after $MAX_POLLS polls")
            }
        } catch (t: Throwable) {
            L.w("preview poll: ${t.javaClass.simpleName}: ${t.message}")
        }
    }, if (attempt == 0) 60L else POLL_MS)
}

private const val TAG = "DuoSB"
private const val ARTBOARD = "Duo"
private const val STATE_MACHINE = "Duo"
private const val POLL_MS = 100L
private const val MAX_POLLS = 25

/** FR-09: the pause between loops of the reveal in the preview, so the bounce stays readable. */
private const val LOOP_GAP_MS = 700L

/** FR-09: one full off -> on -> off cycle for the Rive row demos. */
private const val RIVE_DEMO_PERIOD_MS = 2800L

/** FR-06: the labels the middle slot can show when Wi-Fi is off, best first. */
private val GENERATIONS = listOf("5G", "4G", "3G", "2G")

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

