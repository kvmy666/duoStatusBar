package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Direction
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.ViewModelInstance
import app.rive.runtime.kotlin.renderers.Renderer
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.RiveInit
import io.github.kvmy666.duostatusbar.settings.DuoPrefs

/** The raw drawing for [designStyle]; an unknown value keeps the classic drawing. Pure and testable. */
internal fun rawResourceFor(designStyle: String?): Int =
    if (DuoPrefs.normalizeDesignStyle(designStyle) == DuoPrefs.DESIGN_OLD) R.raw.duo_old else R.raw.duo

/**
 * The Duo element (the real one), drawn by Rive inside the SystemUI process.
 *
 * `RiveAnimationView` is embedded in a `FrameLayout` so the status-bar side owns the bounds while Rive
 * owns the drawing. The builder is used instead of `setRiveBytes` for one non-negotiable reason: the
 * **renderer type must be explicit**. `RiveAnimationView` is a `TextureView`-based surface, and leaving
 * the type unset makes the runtime read `Rive.defaultRendererType` — the static that `Rive.init` fails
 * to assign inside SystemUI, and the null that segfaulted the first Phase 3 attempt (see [RiveInit]).
 * Passing `RendererType.Canvas` means the type cannot be null whatever that static holds.
 *
 * `Fit.CONTAIN` keeps the element square whatever shape the ROM's slot is: the measured slot is 83x61 px,
 * so the ring scales to the 61 px height and centres, instead of being stretched into an oval.
 *
 * Every step is guarded: if anything here fails the stock status bar stays exactly as it was (FR-21).
 */
internal class DuoRiveView(
    context: Context,
    part: DuoPart = DuoPart.ALL,
    designStyle: String = DuoPrefs.DESIGN_OLD
) : FrameLayout(context), DuoElement {

    internal val designStyle: String = DuoPrefs.normalizeDesignStyle(designStyle)

    private var rive: RiveAnimationView? = null
    private var viewModelInstance: ViewModelInstance? = null

    /**
     * The view model paired with Rive's file lock, so every write serializes with the renderer's advance
     * (see [DuoBinder] for the `ConcurrentModificationException` this prevents).
     */
    private var binding: DuoBinding? = null
    private var started = false
    private var polls = 0
    private var pendingVisual: DuoVisual? = null
    private var assetLogged = false

    /** The newest snapshot. The fill animation draws intermediates of this, not a second source. */
    private var targetVisual: DuoVisual? = null

    /** Grows a gray signal dot into the white Do Not Disturb crescent. See [DotMoonMotion]. */
    private val motion = DotMoonMotion()
    private var ticking = false
    private val tick = Runnable {
        ticking = false
        val target = binding ?: return@Runnable
        pump(target)
    }

    /** The last snapshot asked for, so an identical one is skipped. Fill frames are not stored here. */
    private var lastVisual: DuoVisual? = null
    private var lastPart: DuoPart = part

    /**
     * Which group this view draws. Changing it replays the last snapshot, so turning the split
     * layout on hides the inner icons in the ring's view without waiting for the next status tick.
     */
    override var part: DuoPart = part
        set(value) {
            if (field == value) return
            field = value
            val current = lastVisual ?: return
            lastVisual = null
            render(current)
        }
    private val readyActions = ArrayList<() -> Unit>()
    private val failedActions = ArrayList<() -> Unit>()

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Whether the renderer should be advancing at all. False while the element is off screen, so the
     * looping state machine cannot keep drawing into a hidden view.
     */
    private var renderActive = true

    /**
     * Stops the renderer once a burst of changes has settled.
     *
     * The state machine's idle animations loop forever, so the renderer never goes idle on its own and
     * advances/draws at frame rate for the life of the SystemUI process — the reported 130 mAh drain
     * (vs the ~15 mAh baseline) that had nothing to do with the screen being on. Every snapshot and
     * reveal restarts it; a short quiet period after the last one stops it again, so animation still
     * plays but a static element costs nothing.
     */
    private val idleStop = Runnable {
        try {
            val renderer = rive?.artboardRenderer
            if (renderer?.isPlaying == true) {
                renderer.stop()
                L.i("Rive renderer paused (idle)")
            }
        } catch (t: Throwable) {
            L.w("renderer idle stop: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    override val ui: View get() = this

    override val rendererName: String get() = "Rive"

    /** True once the drawing is live; the host uses this to decide whether it may hide stock icons. */
    override val isReady: Boolean get() = viewModelInstance != null

    /**
     * Builds the view and returns true when the *runtime* is usable. It deliberately does **not** require
     * the view model instance yet: Rive binds the state machine's instance only after the view has been
     * attached to a window, and at this point the element has not been added to the status bar. Reading it
     * here is what made the module fall back to Canvas on every attempt - the instance was always null,
     * not because the asset was wrong. [onReady] reports the real, later readiness.
     */
    override fun start(): Boolean {
        if (started) return true
        started = true
        return try {
            // False means the runtime is not usable here; the host falls back to the no-native view.
            if (!RiveInit.ensure(context)) {
                L.e("Rive runtime unavailable - not creating a Rive view")
                return false
            }
            val bytes = loadRiveBytes() ?: return false

            val builder = RiveAnimationView.Builder(context)
                .setRendererType(RendererType.Canvas)
                .setResource(bytes)
                .setArtboardName(ARTBOARD)
                .setStateMachineName(STATE_MACHINE)
                .setFit(Fit.CONTAIN)
                .setAlignment(Alignment.CENTER)
                .setAutoplay(true)
                .setAutoBind(true)
                .setShouldLoadCDNAssets(false)
                // Touch feedback is not needed from Rive: any gesture the user asks for is handled one level
                // up and handed to Auto Expand. Letting Rive consume touches would swallow it instead.
                .setTouchPassThrough(true)
            val view = GuardedRiveView(builder)
            ensureInjectedViewId(view, this)
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            rive = view

            pollForInstance()
            true
        } catch (t: Throwable) {
            L.e("start failed: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    override fun onReady(action: () -> Unit) {
        if (isReady) action() else readyActions.add(action)
    }

    override fun onFailed(action: () -> Unit) {
        failedActions.add(action)
    }

    /**
     * The state machine appears some time after the view is attached to a window, so it is polled rather
     * than assumed. On success every waiter runs once; on exhaustion the host is told, so it can fall back
     * to Canvas instead of leaving an empty slot.
     */
    private fun pollForInstance() {
        postDelayed({
            // teardown() clears both fields; a disposed view must not keep polling (or bind late).
            if (!started || rive == null) return@postDelayed
            if (viewModelInstance != null) return@postDelayed
            try {
                val machine = rive?.stateMachines?.firstOrNull()
                val found = machine?.viewModelInstance
                if (found != null) {
                    viewModelInstance = found
                    binding = DuoBinder.bind(found, rive?.file?.lock)
                    // The state machine must actually be running: a data bind only applies while one is,
                    // and the render loop only runs while the renderer is playing. With `autoplay` alone
                    // the machine sat idle (`playingStateMachines=0`), so live changes were written but
                    // never drawn - the element only ever showed its bind-time frame. Start it explicitly.
                    rive?.play(STATE_MACHINE, Loop.LOOP, Direction.AUTO, true, true)
                    renderActive = true
                    scheduleIdleStop()
                    L.i("Duo view ready (machines=${rive?.stateMachines?.size}, " +
                            "playing=${rive?.playingStateMachines?.size}, inputs=${machine.inputNames})")
                    pendingVisual?.let { render(it) }
                    pendingVisual = null
                    readyActions.toList().forEach { it() }
                    readyActions.clear()
                    failedActions.clear()
                } else if (++polls < MAX_POLLS) {
                    pollForInstance()
                } else {
                    L.w("no view model instance after $MAX_POLLS polls - Rive element did not bind")
                    failedActions.toList().forEach { it() }
                    readyActions.clear()
                    failedActions.clear()
                }
            } catch (t: Throwable) {
                L.w("instance poll: ${t.javaClass.simpleName}: ${t.message}")
                if (++polls < MAX_POLLS) pollForInstance() else failedActions.toList().forEach { it() }
            }
        }, POLL_MS)
    }

    /** Pushes a full state snapshot into the drawing. Never throws. */
    override fun render(v: DuoVisual) {
        // Before the instance binds there is nothing to write to. Remember the newest state and replay
        // it once the machine is live. That replay must not count as already drawn: the file's own first
        // frame is a full ring reading "100", and marking this snapshot done would leave that frame up
        // until the next status change. A new icon-cluster view hits this every time split mode turns on.
        val target = binding ?: run { pendingVisual = v; return }
        // An identical snapshot changes nothing on screen, and each real write also re-wakes the Rive
        // renderer (`artboardRenderer.start()` below). Skipping repeats is what keeps the element from
        // re-rendering on every status-bar layout pass, which was a steady battery drain.
        if (v == lastVisual && part == lastPart) return
        lastVisual = v
        lastPart = part
        targetVisual = v
        pump(target)
    }

    /**
     * Writes the current fill frame. While a gray dot is still becoming a moon, arms the next frame
     * so the renderer stays awake until the fill finishes — an idle pause mid-way would freeze it.
     */
    private fun pump(target: DuoBinding) {
        val next = targetVisual ?: return
        val shown = part.apply(motion.push(next, SystemClock.uptimeMillis()))
        val failures = try {
            target.apply(shown)
        } catch (t: Throwable) {
            L.e("render failed: ${t.javaClass.simpleName}: ${t.message}")
            return
        }
        if (failures > 0) {
            L.w("$failures of ${DuoBinder.PROPERTY_COUNT} properties did not bind")
        }
        // The writes land in the view model, but the drawing only follows if the renderer is running -
        // and the renderer's loop stops once the state machine settles into a hold state (measured:
        // `isPlaying=false` after the first frame, so every later change was written and never drawn).
        // `start()` is the renderer's own "run the loop" call and is idempotent (`if (isPlaying) return`),
        // so asking for it on every snapshot is cheap and keeps the element live (FR-16/FR-06). It is
        // then paused again after a quiet period, so a looping idle animation cannot drain the battery.
        if (renderActive) {
            startRenderer()
            scheduleIdleStop()
            if (motion.running && !ticking) {
                ticking = true
                handler.postDelayed(tick, FRAME_MS)
            }
        }
    }

    /** Starts or stops the renderer; see the field and [idleStop]. */
    override fun setRenderActive(active: Boolean) {
        renderActive = active
        if (active) {
            startRenderer()
            val target = binding
            if (target != null && motion.running) pump(target) else scheduleIdleStop()
        } else {
            handler.removeCallbacks(idleStop)
            handler.removeCallbacks(tick)
            ticking = false
            try {
                rive?.artboardRenderer?.stop()
            } catch (t: Throwable) {
                L.w("renderer stop: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    private fun startRenderer() {
        try {
            rive?.artboardRenderer?.start()
        } catch (t: Throwable) {
            L.w("renderer start: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** (Re)arms the idle pause. No-op while the element is off screen. */
    private fun scheduleIdleStop() {
        handler.removeCallbacks(idleStop)
        if (!renderActive) return
        handler.postDelayed(idleStop, IDLE_MS)
    }

    /** Re-fires the reveal (screen on, unlock, first attach). Never throws. */
    override fun reveal(ms: Int) {
        val target = binding ?: run {
            L.w("reveal skipped - no view model instance yet")
            return
        }
        L.i("reveal fired (${ms}ms)")
        try {
            // One number is both the trigger and the duration: the machine fires on a non-zero value, so
            // it has to be cleared or it re-fires the moment the arrival ends. Clearing early is what
            // makes it fire once - the machine has already latched the state by then.
            target.requestReveal(ms)
            postDelayed({ target.requestReveal(0) }, DuoBinder.REVEAL_CLEAR_MS)
            if (renderActive) {
                startRenderer()
                scheduleIdleStop()
            }
        } catch (t: Throwable) {
            L.e("reveal failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    override fun teardown() {
        try {
            handler.removeCallbacks(idleStop)
            handler.removeCallbacks(tick)
            rive?.stop()
            removeAllViews()
        } catch (_: Throwable) {
        }
        rive = null
        viewModelInstance = null
        binding = null
        pendingVisual = null
        targetVisual = null
        ticking = false
        lastVisual = null
        lastPart = DuoPart.ALL
        readyActions.clear()
        failedActions.clear()
        renderActive = false
        started = false
    }

    // ------------------------------------------------------------------------------------ binding
    // Property names and writes live in DuoBinder, shared with the in-app preview so the two surfaces
    // cannot disagree about what they are showing.

    // ---------------------------------------------------------------------- asset loading

    /**
     * Reads the selected drawing from the module APK through the module's own resource table.
     *
     * R8/resource shrinking renames `res/raw/duo.riv` to a short name (`res/zy.riv` on 1.0.1), so
     * reading by path silently fails and the element quietly falls back to Canvas. `openRawResource`
     * resolves the id, not the name, so the rename cannot break it.
     */
    private fun loadRiveBytes(): ByteArray? {
        val resource = rawResourceFor(designStyle)
        if (!assetLogged) {
            assetLogged = true
            L.i("Rive asset: $designStyle (res 0x${resource.toString(16)})")
        }
        return try {
            val moduleContext = context.createPackageContext(MODULE_PACKAGE, 0)
            moduleContext.resources.openRawResource(resource).use { it.readBytes() }
        } catch (t: Throwable) {
            L.e("Rive asset $designStyle unreadable: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    companion object {
        private const val TAG = "DuoSB"
        private const val MODULE_PACKAGE = "io.github.kvmy666.duostatusbar"
        private const val ARTBOARD = "Duo"
        private const val STATE_MACHINE = "Duo"

        // The instance binds a few frames after the view is attached; 25 x 100 ms mirrors the app preview,
        // which is where this timing was first measured.
        private const val POLL_MS = 100L
        private const val MAX_POLLS = 25

        /** Idle time after the last change before the renderer is paused (see [idleStop]). */
        private const val IDLE_MS = 3_000L

        /** How often a dot-to-moon fill writes the next frame. */
        private const val FRAME_MS = 16L
    }
}


/**
 * A [RiveAnimationView] that waits for Rive's canvas worker before the Surface is released.
 *
 * rive-android 10.2.0 releases the Java [android.view.Surface] on the UI thread as soon as the
 * texture is lost or resized (`onSurfaceTextureDestroyed`, and `onSurfaceTextureSizeChanged`,
 * which calls `onSurfaceTextureAvailable`). The draw runs on a shared worker thread and calls
 * `Surface.lockCanvas`. If that call is still in flight, the Surface throws
 * `IllegalStateException: Surface has already been released`, and the native code does not clear
 * the exception, so ART aborts SystemUI.
 *
 * [Renderer.stop] only queues the halt. Deleting a throwaway Canvas renderer waits on that same
 * worker until every job queued ahead of it — including an in-flight frame — has finished. The
 * real renderer stays alive; [isAttached][Renderer.isAttached] is cleared so a status tick cannot
 * start another frame until the next surface is installed.
 */
private class GuardedRiveView(builder: RiveAnimationView.Builder) : RiveAnimationView(builder) {

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        awaitCanvasWorker()
        super.onSurfaceTextureAvailable(surface, width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        awaitCanvasWorker()
        return super.onSurfaceTextureDestroyed(surface)
    }

    private fun awaitCanvasWorker() {
        val current = renderer ?: return
        if (!current.hasCppObject) return
        if (!current.isAttached && !current.isPlaying) return
        current.isAttached = false
        try {
            current.stop()
        } catch (t: Throwable) {
            L.w("rive stop before surface release: ${t.javaClass.simpleName}: ${t.message}")
        }
        val gate = CanvasWorkerGate()
        try {
            gate.make()
            gate.delete()
            L.i("Rive worker drained before surface release")
        } catch (t: Throwable) {
            L.w("rive worker drain: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}

/**
 * Exists only so its [delete][Renderer.delete] can wait on the shared Canvas worker.
 *
 * Every Canvas [Renderer] in this process draws on one worker. [Renderer.delete] posts a job and
 * blocks until that job, and everything queued before it, has finished. This renderer is never
 * given a surface and never touches the file controller, so deleting it does not dispose the
 * animation that is on screen.
 */
private class CanvasWorkerGate : Renderer(RendererType.Canvas, false) {
    override fun draw() {}
    override fun advance(elapsed: Float) {}
}

