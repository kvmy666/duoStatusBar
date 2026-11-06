package io.github.kvmy666.duostatusbar

import io.github.kvmy666.duostatusbar.R

import android.content.Context
import android.os.Handler
import android.os.Looper
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
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.ViewModelInstance
import io.github.kvmy666.duostatusbar.hook.DuoBinder
import io.github.kvmy666.duostatusbar.hook.DuoBinding
import io.github.kvmy666.duostatusbar.hook.DuoMapping
import io.github.kvmy666.duostatusbar.hook.DuoVisual
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.ui.GlassSlider
import io.github.kvmy666.duostatusbar.ui.GlassSwitch
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
    revealMs: Int = 1000,
     /** The drawing to show: [R.raw.duo] for 2 SIM, [R.raw.duo_old] for Classic. */
    rawRes: Int = R.raw.duo,
    /** The middle-slot setting to render; `percent` enables the invert-center layout. Home owns it. */
    middleSlot: String = DuoPrefs.MIDDLE_SLOT_AUTO,
    /** Called when the user toggles Invert center; Home persists via middle_slot. */
    onInvertChange: (Boolean) -> Unit = {},
    /** Clock text shown when middleSlot=clock; empty uses the current hour. */
    clockText: String = ""
) {
    var level by remember { mutableFloatStateOf(78f) }
    var charging by remember { mutableStateOf(false) }
    var saver by remember { mutableStateOf(false) }
    var airplane by remember { mutableStateOf(false) }
    var dnd by remember { mutableStateOf(false) }
    /** When on, Do Not Disturb turns the signal dots into moons instead of taking the middle. */
    var dndDots by remember { mutableStateOf(false) }
    var wifi by rememberSaveable { mutableFloatStateOf(3f) }
    var cell by rememberSaveable { mutableFloatStateOf(4f) }
    /** FR-06: with Wi-Fi off the middle slot shows the cellular generation instead of the glyph. */
    var wifiOn by remember { mutableStateOf(true) }
    var wifiDots by remember { mutableStateOf(false) }
    var generation by rememberSaveable { mutableIntStateOf(0) }
    var revealTick by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val minuteBucket = (System.currentTimeMillis() / 60_000L).toInt()
    val resolvedClock = remember(clockText, middleSlot, minuteBucket) {
        if (clockText.isNotEmpty()) clockText
        else if (middleSlot != DuoPrefs.MIDDLE_SLOT_CLOCK) ""
        else {
            val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
            java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date())
        }
    }
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
        showDnd = !dndDots,
        dndDots = dndDots,
        networkText = GENERATIONS[generation],
        wifiDots = wifiDots,
        middleSlot = middleSlot,
        clockText = resolvedClock
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pixelSize.dp)
                .background(Color(0xFF101014)),
            contentAlignment = Alignment.Center
        ) {
            // Two persistent panes, but the inactive one is created lazily on the first design switch:
            // loading both Rive assets on first Home composition is wasteful. The active pane is always
            // present; a pane that has been created stays composed (hidden) so switching back is instant.
            var loadedDesigns by remember { mutableStateOf(setOf(rawRes)) }
            LaunchedEffect(rawRes) { loadedDesigns = loadedDesigns + rawRes }
            if (R.raw.duo_old in loadedDesigns) {
                PreviewPane(
                    rawRes = R.raw.duo_old,
                    active = rawRes == R.raw.duo_old,
                    visual = visual,
                    modifier = Modifier.fillMaxSize(),
                    pixelSize = pixelSize,
                    revealTick = revealTick,
                    loop = loop,
                    revealMs = revealMs
                )
            }
            if (R.raw.duo in loadedDesigns) {
                PreviewPane(
                    rawRes = R.raw.duo,
                    active = rawRes == R.raw.duo,
                    visual = visual,
                    modifier = Modifier.fillMaxSize(),
                    pixelSize = pixelSize,
                    revealTick = revealTick,
                    loop = loop,
                    revealMs = revealMs
                )
            }
        }

        LabelledSlider(stringResource(R.string.preview_battery, level.toInt()), level, 0f..100f, steps = 0) { level = it }
        Toggle(stringResource(R.string.preview_charging), charging) { charging = it }
        Toggle(stringResource(R.string.preview_saver), saver) { saver = it }
        Toggle(stringResource(R.string.home_live_sim_invert), middleSlot == DuoPrefs.MIDDLE_SLOT_PERCENT) { onInvertChange(it) }
        Toggle(stringResource(R.string.preview_airplane), airplane) { airplane = it }
        Toggle(stringResource(R.string.preview_dnd), dnd) { dnd = it }
        Toggle(stringResource(R.string.preview_dnd_dots), dndDots) { dndDots = it }
        LabelledSlider(stringResource(R.string.preview_wifi, wifi.toInt()), wifi, 0f..3f, steps = 2) { wifi = it }
        LabelledSlider(stringResource(R.string.preview_cell, cell.toInt()), cell, 0f..4f, steps = 3) { cell = it }
        Toggle(stringResource(R.string.preview_wifi_off), !wifiOn) { wifiOn = !it }
        Toggle(stringResource(R.string.preview_wifi_dots), wifiDots) { wifiDots = it }
        LabelledSlider(
            stringResource(R.string.preview_network, GENERATIONS[generation]),
            generation.toFloat(), 0f..3f, steps = 2
        ) { generation = it.toInt() }
        Button(onClick = { revealTick++ }) { Text(stringResource(R.string.preview_replay)) }

    }
}

@Composable
private fun PreviewPane(
    rawRes: Int,
    active: Boolean,
    visual: DuoVisual,
    modifier: Modifier = Modifier,
    pixelSize: Int = 200,
    revealTick: Int = 0,
    loop: Boolean = false,
    revealMs: Int = 1000
) {
    val context = LocalContext.current
    val riveOk = remember { RiveInit.ensure(context) }

    if (!riveOk) {
        Box(modifier = modifier.background(Color(0xFF202024)))
        return
    }

    val instance = remember { mutableStateOf<DuoBinding?>(null) }
    val lastVisual = remember { mutableStateOf<DuoVisual?>(null) }
    val rive = remember { mutableStateOf<RiveAnimationView?>(null) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val pollHandle = remember { mutableStateOf<Runnable?>(null) }
    val idleStop = remember {
        Runnable {
            try {
                val renderer = rive.value?.artboardRenderer
                if (renderer?.isPlaying == true) {
                    renderer.stop()
                    L.d("preview renderer paused (idle)")
                }
            } catch (t: Throwable) {
                L.w("preview idle stop: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            handler.removeCallbacks(idleStop)
            pollHandle.value?.let { cb -> rive.value?.removeCallbacks(cb) }
            try {
                rive.value?.artboardRenderer?.stop()
            } catch (t: Throwable) {
                L.w("preview renderer stop: ${t.javaClass.simpleName}: ${t.message}")
            }
            rive.value = null
        }
    }

    // Wake the renderer when this pane becomes active; stop it when hidden.
    LaunchedEffect(active) {
        if (active) {
            lastVisual.value = null
            rive.value?.let { wakeStillRenderer(it, handler, idleStop) }
        } else {
            handler.removeCallbacks(idleStop)
            try {
                rive.value?.artboardRenderer?.stop()
            } catch (t: Throwable) {
                L.w("preview renderer stop: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    // Reveal on request / on becoming active.
    LaunchedEffect(active, revealTick) {
        if (!active) return@LaunchedEffect
        val binding = instance.value ?: return@LaunchedEffect
        try {
            binding.requestReveal(revealMs)
            delay(revealMs + 60L)
        } finally {
            binding.requestReveal(0)
        }
    }

    // Looping demonstration, only while active.
    LaunchedEffect(active, loop, revealTick) {
        if (!active || !loop) return@LaunchedEffect
        while (true) {
            val binding = instance.value ?: break
            try {
                binding.requestReveal(revealMs)
                delay(revealMs + 60L)
            } finally {
                binding.requestReveal(0)
            }
            delay(LOOP_GAP_MS)
        }
    }

    Box(modifier = modifier.alpha(if (active) 1f else 0f)) {
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            factory = { ctx ->
                createRiveView(ctx, instance, rawRes, pollHandle).also { view ->
                    rive.value = view
                    if (!active) view.post {
                        try { view.artboardRenderer?.stop() } catch (t: Throwable) {
                            L.w("preview renderer stop: ${t.javaClass.simpleName}: ${t.message}")
                        }
                    }
                }
            },
            update = { view ->
                if (!active) {
                    try {
                        view.artboardRenderer?.stop()
                    } catch (t: Throwable) {
                        L.w("preview renderer stop: ${t.javaClass.simpleName}: ${t.message}")
                    }
                    return@AndroidView
                }
                val binding = instance.value
                if (binding != null && visual != lastVisual.value) {
                    pushVisual(binding, visual)
                    lastVisual.value = visual
                    wakeStillRenderer(view, handler, idleStop)
                }
            }
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
    /** The drawing to show: [R.raw.duo] for 2 SIM, [R.raw.duo_old] for Classic. */
    rawRes: Int = R.raw.duo,
    /** A null background lets the real ring sit directly on a designed surface. */
    background: Color? = Color(0xFF101014),
    visualAt: (Float) -> DuoVisual
) {
    val instance = remember { mutableStateOf<DuoBinding?>(null) }
    val lastVisual = remember { mutableStateOf<DuoVisual?>(null) }
    val rive = remember { mutableStateOf<RiveAnimationView?>(null) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var phase by remember { mutableFloatStateOf(0f) }
    val resumed = rememberResumed()
    val idleStop = remember {
        Runnable {
            try {
                val renderer = rive.value?.artboardRenderer
                if (renderer?.isPlaying == true) {
                    renderer.stop()
                    L.d("preview renderer paused (idle)")
                }
            } catch (t: Throwable) {
                L.w("preview idle stop: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            handler.removeCallbacks(idleStop)
            try {
                rive.value?.artboardRenderer?.stop()
            } catch (t: Throwable) {
                L.w("preview renderer stop: ${t.javaClass.simpleName}: ${t.message}")
            }
            rive.value = null
        }
    }

    LaunchedEffect(periodMs, resumed) {
        if (!resumed) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            phase = demoPhase((((now - start) / 1_000_000L) % periodMs).toFloat() / periodMs)
        }
    }
    // The arrival is a trigger, not a state, so it is fired once per loop rather than driven by phase.
    LaunchedEffect(fireReveal, periodMs, resumed) {
        if (!fireReveal || !resumed) return@LaunchedEffect
        while (true) {
            delay(periodMs)
            val binding = instance.value ?: continue
            try {
                binding.requestReveal(1000)
                delay(160)
                binding.requestReveal(0)
            } catch (_: Throwable) {
            }
        }
    }

    val surface = if (background != null) {
        modifier.size(size).clip(CircleShape).background(background)
    } else {
        modifier.size(size)
    }
    Box(
        modifier = surface,
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> createRiveView(ctx, instance, rawRes).also { rive.value = it } },
            update = { view ->
                val visual = visualAt(phase)
                // Only push when the snapshot actually changes, and only once the view model has bound:
                // the phase moves every frame, but the state machine needs one write per state.
                val binding = instance.value
                if (binding != null && visual != lastVisual.value) {
                    pushVisual(binding, visual)
                    lastVisual.value = visual
                    wakeStillRenderer(view, handler, idleStop)
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
    translationXDp: Float = 0f,
    /** The drawing this still shows: [R.raw.duo] for the dual-SIM design, [R.raw.duo_old] for Classic. */
    rawRes: Int = R.raw.duo,
    /** When > 0, schedule a hard renderer stop this long after the visual becomes an event (event previews). */
    hardStopAfterMs: Long = 0L
) {
    // A different drawing needs a different Rive view: the builder reads the resource once, so the
    // old view cannot be repointed. Keying on the resource recreates the view (and its binding) only
    // when the user actually switches design.
    key(rawRes) {
        val instance = remember { mutableStateOf<DuoBinding?>(null) }
        val lastVisual = remember { mutableStateOf<DuoVisual?>(null) }
        val lastTranslation = remember { mutableStateOf(Float.NaN) }
        val rive = remember { mutableStateOf<RiveAnimationView?>(null) }
        val handler = remember { Handler(Looper.getMainLooper()) }
        val idleLogged = remember { booleanArrayOf(false) }

        // The state machine's idle animations loop forever, so without this the still would advance and
        // draw at frame rate for as long as the screen is open — the same drain the status-bar view
        // pauses for in DuoRiveView. Every real change re-arms the pause; a still that settles costs
        // nothing until the next change.
        val idleStop = remember {
            Runnable {
                try {
                    val renderer = rive.value?.artboardRenderer
                    if (renderer?.isPlaying == true) {
                        renderer.stop()
                        if (!idleLogged[0]) {
                            idleLogged[0] = true
                            L.d("preview renderer paused (idle)")
                        }
                    }
                } catch (t: Throwable) {
                    L.w("preview idle stop: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
        }
        val hardStop = remember {
            Runnable {
                try {
                    rive.value?.artboardRenderer?.stop()
                } catch (t: Throwable) {
                    L.w("preview hard stop: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                handler.removeCallbacks(idleStop)
                handler.removeCallbacks(hardStop)
                try {
                    rive.value?.artboardRenderer?.stop()
                } catch (t: Throwable) {
                    L.w("preview renderer stop: ${t.javaClass.simpleName}: ${t.message}")
                }
                rive.value = null
            }
        }

        // The caller supplies the bounded size (the Home/Customization previews pass an explicit
        // square). Clipping keeps the Fit.CONTAIN drawing inside that slot on every device density
        // without changing the update path, so a live settings change still pushes immediately.
        AndroidView(
            modifier = modifier.clipToBounds(),
            factory = { ctx -> createRiveView(ctx, instance, rawRes).also { rive.value = it } },
            update = { view ->
                val binding = instance.value
                val wasEventVisible = lastVisual.value?.eventVisible == true
                var changed = false
                if (binding != null && visual != lastVisual.value) {
                    pushVisual(binding, visual)
                    lastVisual.value = visual
                    changed = true
                }
                if (translationXDp != lastTranslation.value) {
                    lastTranslation.value = translationXDp
                    view.translationX = translationXDp * view.resources.displayMetrics.density
                    changed = true
                }
                // Never arm the stop before the first snapshot is drawn: the file's own first frame is
                // not the caller's state, and the binding may not have landed yet.
                if (changed && lastVisual.value != null) {
                    when {
                        // Event start: draw it and arm a bounded stop so the preview cannot loop forever.
                        visual.eventVisible -> {
                            wakeStillRenderer(view, handler, idleStop)
                            if (hardStopAfterMs > 0L) {
                                handler.removeCallbacks(hardStop)
                                handler.postDelayed(hardStop, hardStopAfterMs)
                            }
                        }
                        // Event end: do not wake the renderer again (that would cancel the idle stop).
                        wasEventVisible -> Unit
                        // Ordinary still change: show it, then pause when it settles.
                        else -> wakeStillRenderer(view, handler, idleStop)
                    }
                }
            }
        )
    }
}

/**
 * Starts the still's renderer and re-arms its idle pause. `start()` is idempotent, so the burst of
 * changes during a drag costs nothing extra and the pause lands after the last one.
 */
private fun wakeStillRenderer(
    view: RiveAnimationView,
    handler: Handler,
    idleStop: Runnable
) {
    handler.removeCallbacks(idleStop)
    try {
        view.artboardRenderer?.start()
    } catch (t: Throwable) {
        L.w("preview renderer start: ${t.javaClass.simpleName}: ${t.message}")
    }
    handler.postDelayed(idleStop, STILL_IDLE_MS)
}

/** Builds the Rive view with the same renderer and layout the status bar uses. */
private fun createRiveView(
    context: Context,
    instance: MutableState<DuoBinding?>,
    rawRes: Int = R.raw.duo,
    pollHandle: MutableState<Runnable?>? = null
): RiveAnimationView {
    if (!RiveInit.ensure(context)) {
        L.e("Rive preview setup failed: RiveInit.ensure returned false")
        return RiveAnimationView(context)
    }
    val view = try {
        val builder = RiveAnimationView.Builder(context)
            .setRendererType(RendererType.Canvas)
            .setResource(rawRes)
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
    watchForViewModelInstance(view, onFound = { vm -> instance.value = DuoBinder.bind(vm, view.file?.lock) }, pollHandle = pollHandle)
    return view
}

private fun pushVisual(binding: DuoBinding?, visual: DuoVisual) {
    if (binding == null) return
    try {
        val failures = binding.apply(visual)
        if (failures > 0) {
            L.w("preview: $failures of ${DuoBinder.PROPERTY_COUNT} properties did not bind")
        }
    } catch (t: Throwable) {
        L.w("preview push: ${t.javaClass.simpleName}: ${t.message}")
    }
}

/**
 * True while the host screen is resumed. A preview that is kept composed by a scrolling page must
 * not keep a frame callback or a Rive renderer running after the app goes to the background.
 */
@Composable
internal fun rememberResumed(): Boolean {
    var resumed by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { resumed = false }
    return resumed
}

/** The state machine appears some time after the bytes are handed over: watch for it, don't assume. */
private fun watchForViewModelInstance(
    view: RiveAnimationView,
    onFound: (ViewModelInstance) -> Unit,
    pollHandle: MutableState<Runnable?>? = null
) {
    val runnable = object : Runnable {
        private var attempt = 0
        override fun run() {
            try {
                val machine = view.stateMachines.firstOrNull()
                val found = machine?.viewModelInstance
                if (found != null) {
                    onFound(found)
                    L.i("preview ready (machines=${view.stateMachines.size}, inputs=${machine?.inputNames})")
                } else if (attempt < MAX_POLLS) {
                    attempt++
                    view.postDelayed(this, POLL_MS)
                } else {
                    L.w("preview: no view model instance after $MAX_POLLS polls")
                }
            } catch (t: Throwable) {
                L.w("preview poll: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }
    pollHandle?.value = runnable
    view.postDelayed(runnable, 60L)
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

/** Quiet time after the last still-preview change before its renderer is paused. */
private const val STILL_IDLE_MS = 2_000L

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
        GlassSlider(value = value, onValueChange = onChange, range = range, steps = steps)
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
        GlassSwitch(checked = checked, onCheckedChange = onChange)
    }
}

