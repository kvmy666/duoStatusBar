package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.RendererType
import app.rive.runtime.kotlin.core.ViewModelInstance
import io.github.kvmy666.duostatusbar.L
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

    override val ui: View get() = this

    /** True once the drawing is live; the host uses this to decide whether it may hide stock icons. */
    override val isReady: Boolean get() = viewModelInstance != null

    override fun start(): Boolean {
        if (started) return isReady
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

            val machine = view.stateMachines.firstOrNull()
            viewModelInstance = machine?.viewModelInstance
            if (viewModelInstance == null) {
                L.w("no view model instance bound - binds will not run")
                return false
            }
            L.i("Duo view ready (machines=${view.stateMachines.size}, inputs=${machine?.inputNames})")
            true
        } catch (t: Throwable) {
            L.e("start failed: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /** Pushes a full state snapshot into the drawing. Never throws. */
    override fun render(v: DuoVisual) {
        val vm = viewModelInstance ?: return
        val failures = try {
            DuoBinder.apply(vm, v)
        } catch (t: Throwable) {
            L.e("render failed: ${t.javaClass.simpleName}: ${t.message}")
            return
        }
        if (failures > 0) {
            L.w("$failures of ${DuoBinder.PROPERTY_COUNT} properties did not bind")
        }
    }

    /** Re-fires the 500 ms reveal (screen on, unlock, first attach). Never throws. */
    override fun reveal() {
        val vm = viewModelInstance ?: return
        try {
            // The state machine fires on the false -> true edge, so the request is cleared after it runs.
            DuoBinder.requestReveal(vm, true)
            postDelayed({ DuoBinder.requestReveal(vm, false) }, DuoBinder.REVEAL_MS + 60L)
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
        try {
            DuoRiveView::class.java.classLoader
                ?.getResourceAsStream(RAW_ENTRY)
                ?.use { return it.readBytes() }
        } catch (t: Throwable) {
            L.w("classloader route failed: ${t.message}")
        }
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
    }
}
