package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Direction
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.ViewModelInstance
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.RiveInit
import java.util.zip.ZipFile

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
internal class DuoRiveView(context: Context) : FrameLayout(context), DuoElement {

    private var rive: RiveAnimationView? = null
    private var viewModelInstance: ViewModelInstance? = null
    private var started = false
    private var polls = 0
    private var pendingVisual: DuoVisual? = null
    private val readyActions = ArrayList<() -> Unit>()
    private val failedActions = ArrayList<() -> Unit>()

    override val ui: View get() = this

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
            val view = RiveAnimationView(builder)
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
            if (viewModelInstance != null) return@postDelayed
            try {
                val machine = rive?.stateMachines?.firstOrNull()
                val found = machine?.viewModelInstance
                if (found != null) {
                    viewModelInstance = found
                    // The state machine must actually be running: a data bind only applies while one is,
                    // and the render loop only runs while the renderer is playing. With `autoplay` alone
                    // the machine sat idle (`playingStateMachines=0`), so live changes were written but
                    // never drawn - the element only ever showed its bind-time frame. Start it explicitly.
                    rive?.play(STATE_MACHINE, Loop.LOOP, Direction.AUTO, true, true)
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
        // Before the instance binds there is nothing to write to; remember the newest state and replay it
        // the moment the machine is live, so the element never shows a stale first frame.
        val vm = viewModelInstance ?: run { pendingVisual = v; return }
        val failures = try {
            DuoBinder.apply(vm, v)
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
        // so asking for it on every snapshot is cheap and keeps the element live (FR-16/FR-06).
        try {
            rive?.artboardRenderer?.start()
        } catch (t: Throwable) {
            L.w("renderer start: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Re-fires the reveal (screen on, unlock, first attach). Never throws. */
    override fun reveal(ms: Int) {
        val vm = viewModelInstance ?: run {
            L.w("reveal skipped - no view model instance yet")
            return
        }
        L.i("reveal fired (${ms}ms)")
        try {
            // One number is both the trigger and the duration: the machine fires on a non-zero value, so
            // it has to be cleared or it re-fires the moment the arrival ends. Clearing early is what
            // makes it fire once - the machine has already latched the state by then.
            DuoBinder.requestReveal(vm, ms)
            postDelayed({ DuoBinder.requestReveal(vm, 0) }, DuoBinder.REVEAL_CLEAR_MS)
        } catch (t: Throwable) {
            L.e("reveal failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    override fun teardown() {
        try {
            rive?.stop()
            removeAllViews()
        } catch (_: Throwable) {
        }
        rive = null
        viewModelInstance = null
        pendingVisual = null
        readyActions.clear()
        failedActions.clear()
        started = false
    }

    // ------------------------------------------------------------------------------------ binding
    // Property names and writes live in DuoBinder, shared with the in-app preview so the two surfaces
    // cannot disagree about what they are showing.

    // ---------------------------------------------------------------------- asset loading

    /**
     * Reads `res/raw/duo.riv` from the module APK. Two independent routes because the module is a
     * guest here: its own classloader first, then the APK path reported by PackageManager.
     */
    private fun loadRiveBytes(): ByteArray? {
        // Route 1, the only one that survives a release build: the module's own resource table.
        // R8/resource shrinking renames `res/raw/duo.riv` to a short name (`res/zy.riv` on 1.0.1),
        // so reading by path - the routes below - silently fails and the element quietly falls back
        // to Canvas. `openRawResource` resolves the id, not the name, so the rename cannot break it.
        try {
            val moduleContext = context.createPackageContext(MODULE_PACKAGE, 0)
            moduleContext.resources.openRawResource(R.raw.duo).use { return it.readBytes() }
        } catch (t: Throwable) {
            L.w("module resource route failed: ${t.javaClass.simpleName}: ${t.message}")
        }
        // Route 2: the module classloader (works in debug builds, where nothing is renamed).
        try {
            DuoRiveView::class.java.classLoader
                ?.getResourceAsStream(RAW_ENTRY)
                ?.use { return it.readBytes() }
        } catch (t: Throwable) {
            L.w("classloader route failed: ${t.message}")
        }
        // Route 3: the APK zip by path (unrenamed builds only).
        return try {
            val apk = context.packageManager.getApplicationInfo(MODULE_PACKAGE, 0).sourceDir
            ZipFile(apk).use { zip ->
                zip.getEntry(RAW_ENTRY)?.let { zip.getInputStream(it).readBytes() }
            }
        } catch (t: Throwable) {
            L.e("APK route failed: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    companion object {
        private const val TAG = "DuoSB"
        private const val MODULE_PACKAGE = "io.github.kvmy666.duostatusbar"
        private const val RAW_ENTRY = "res/raw/duo.riv"
        private const val ARTBOARD = "Duo"
        private const val STATE_MACHINE = "Duo"

        // The instance binds a few frames after the view is attached; 25 x 100 ms mirrors the app preview,
        // which is where this timing was first measured.
        private const val POLL_MS = 100L
        private const val MAX_POLLS = 25
    }
}
