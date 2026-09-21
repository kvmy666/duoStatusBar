package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.github.kvmy666.duostatusbar.hook.integration.AutoExpand
import io.github.kvmy666.duostatusbar.hook.rom.RomDetection
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Puts the Duo element into the status bar and takes the stock icons out of it.
 *
 * Both targets were measured on the device in Phase 0 (`docs/devicereport-oos16.md`), not guessed:
 *
 *     EndSideContentLayout id=status_bar_end_side_content  329x90 @859,38
 *       LinearLayout id=system_icons                       329x61 @859,52  <- we insert here
 *         StatusIconContainer id=statusIcons               246x61          <- stock icons
 *         StatBatteryMeterView id=battery                   83x61 @1105,52  <- battery slot
 *
 * FR-08 wants the stock icons *really* gone, so every child of `id=system_icons` except our own view
 * is set to GONE **and** given zero width — not covered by an overlay. The views stay in the tree
 * (this ROM still references them), they simply draw nothing and occupy nothing.
 *
 * Ids are resolved against SystemUI's own resources at runtime, so a ROM that spells them differently
 * still works as long as the AOSP names are present; `system_icons` is tried first, then the
 * OxygenOS spellings.
 */
internal class DuoIconHost(private val context: Context) {

    private var host: LinearLayout? = null
    private var element: DuoElement? = null
    private val guard = DuoGuard(context)
    private val rom = RomDetection.forThisRom(
        Build.MANUFACTURER.orEmpty(),
        Build.BRAND.orEmpty(),
        Build.PRODUCT.orEmpty(),
        Build.DISPLAY.orEmpty()
    )
    private val romLogged = AtomicBoolean(false)
    private val logged = AtomicBoolean(false)
    private val gateLogged = AtomicBoolean(false)
    private val factsLogged = AtomicBoolean(false)
    private var settings = ModuleSettings.DEFAULT
    private val hiddenOriginals = ArrayList<HiddenState>()

    val duo: DuoElement? get() = element

    /** FR-16: whether the percentage should be drawn — asked by the state monitor on every render. */
    val showPercent: Boolean get() = settings.showPercent

    /**
     * Re-reads the user's settings and applies what can change while running (size, offset).
     * Called on attach and whenever the app says something changed, so no restart is needed.
     */
    fun refreshSettings(): ModuleSettings {
        val fresh = DuoSettingsClient.read(context)
        val changed = fresh != settings
        settings = fresh
        if (changed) {
            Log.i(
                TAG,
                "settings rev ${fresh.revision}: size ${fresh.sizePercent}%, offset ${fresh.offsetX}dp, " +
                    "percent=${fresh.showPercent}, rive=${fresh.useRive}"
            )
            applyLayout()
        }
        return fresh
    }

    /** Size and offset come from the settings, so reshaping the element needs no re-injection (FR-03/17). */
    private fun applyLayout() {
        val target = host ?: return
        val view = element?.ui ?: return
        try {
            view.layoutParams = LinearLayout.LayoutParams(slotWidthPx(target), ViewGroup.LayoutParams.MATCH_PARENT)
            view.translationX = settings.offsetX * context.resources.displayMetrics.density
            view.requestLayout()
            installGestures(view)
        } catch (t: Throwable) {
            Log.w(TAG, "applyLayout: ${t.message}")
        }
    }

    /**
     * FR-05/18: gestures are opt-in per action, and the hand-off is to Auto Expand — this module never
     * implements the actions itself, so the two modules cannot disagree about what a tap means.
     *
     * With the default settings (`no_action` everywhere) no listener is installed and the view is not
     * clickable, so touches fall straight through to whoever handled them before: the status bar's own
     * gestures, including Auto Expand's zones.
     */
    private fun installGestures(view: View) {
        val tap = settings.tapAction
        val doubleTap = settings.doubleTapAction
        val longPress = settings.longPressAction
        if (tap == AutoExpand.NO_ACTION && doubleTap == AutoExpand.NO_ACTION && longPress == AutoExpand.NO_ACTION) {
            view.setOnTouchListener(null)
            view.isClickable = false
            return
        }
        val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean = AutoExpand.request(context, tap)
            override fun onDoubleTap(e: MotionEvent): Boolean = AutoExpand.request(context, doubleTap)
            override fun onLongPress(e: MotionEvent) {
                AutoExpand.request(context, longPress)
            }
        })
        view.isClickable = true
        view.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
        Log.i(TAG, "gestures on: tap=$tap doubleTap=$doubleTap longPress=$longPress (handled by Auto Expand)")
    }

    /**
     * Finds `system_icons`, injects the Duo element, hides what it replaces. True on success.
     *
     * How far this goes is decided by the guard, not by the caller: stage 0 does nothing at all, stage 1
     * draws with plain Canvas (no native code in the path), stage 2 uses Rive when the runtime and the
     * window both allow it and falls back to Canvas when they do not. The stock icons are hidden only for
     * an element that reports itself ready, so a failure leaves the stock status bar untouched rather
     * than empty (FR-21).
     */
    fun attach(statusBarRoot: View): Boolean {
        if (element != null) return true
        return try {
            val stage = guard.stage()
            if (stage == DuoGuard.OFF) {
                if (gateLogged.compareAndSet(false, true)) {
                    Log.i(TAG, "gated off - enable with: ${guard.enableHint}")
                }
                return false
            }
            val target = findStatusIconsHost(statusBarRoot)
            if (target == null) {
                Log.w(TAG, "system_icons not found - status bar left untouched")
                return false
            }
            val candidate = createElement(statusBarRoot, stage)
            if (factsLogged.compareAndSet(false, true)) {
                DuoSbFacts.report(context, statusBarRoot, target, slotWidthPx(target))
            }
            if (!candidate.isReady) {
                Log.w(TAG, "element not ready - status bar left untouched")
                candidate.teardown()
                return false
            }
            refreshSettings()
            val width = slotWidthPx(target)
            candidate.ui.layoutParams = LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT)
            target.addView(candidate.ui)
            host = target
            element = candidate
            applyLayout()
            hideEverythingExcept(target, candidate.ui)
            candidate.reveal()
            forgetAttemptsAfterSurvival(candidate)
            Log.i(TAG, "Duo injected into ${target.javaClass.simpleName} (${width}px wide, ${settings.sizePercent}%)")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "attach failed: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    private fun createElement(root: View, stage: Int): DuoElement {
        if (stage >= DuoGuard.RIVE) {
            riveElement(root)?.let { return it }
        }
        val canvas = DuoCanvasView(context)
        canvas.start()
        Log.i(TAG, "element: Canvas (stage $stage)")
        return canvas
    }

    /**
     * Rive, with everything that can fail checked *before* the native call.
     *
     * `RiveAnimationView` draws into a Surface taken from a TextureView, so it needs a hardware
     * accelerated window — without one there is no surface, and no reason to try. The attempt is recorded
     * *before* the view is constructed, because a native fault kills the process before anything can be
     * caught: that record is what stops a crash loop from repeating itself on the next boot.
     */
    private fun riveElement(root: View): DuoElement? {
        if (!root.isHardwareAccelerated) {
            Log.w(TAG, "status bar window is not hardware accelerated - Rive needs a Surface, using Canvas")
            return null
        }
        if (!guard.riveAllowed()) return null
        guard.noteRiveAttempt()
        val rive = DuoRiveView(context)
        if (!rive.start()) {
            // A clean failure, not a death: hand the attempt back so the breaker only counts crashes.
            guard.clearRiveAttempts()
            rive.teardown()
            return null
        }
        return rive
    }

    /** Once the drawing has survived a few seconds, clear the counter: the breaker tracks deaths only. */
    private fun forgetAttemptsAfterSurvival(element: DuoElement) {
        if (element !is DuoRiveView) return
        try {
            element.ui.postDelayed({ guard.clearRiveAttempts() }, SURVIVAL_MS)
        } catch (_: Throwable) {
        }
    }

    /**
     * Re-applies the hiding pass: the ROM re-shows its icon views whenever the icon set changes, so
     * this runs again on layout changes rather than only once.
     */
    fun reapplyHiding() {
        val target = host ?: return
        val keep = element?.ui ?: return
        try {
            hideEverythingExcept(target, keep)
        } catch (t: Throwable) {
            Log.w(TAG, "reapplyHiding: ${t.message}")
        }
    }

    /** Removes the element and puts the stock icons back exactly as they were. */
    fun teardown() {
        try {
            element?.let { host?.removeView(it.ui) }
            element?.teardown()
        } catch (_: Throwable) {
        }
        element = null
        restoreStockViews()
        host = null
    }

    // ----------------------------------------------------------------------------- internals

    /**
     * Hides every stock view in the strip **and remembers exactly what it looked like**, so switching the
     * element off can put them back without a restart. Hiding is not destruction (FR-08): the views stay in
     * the tree, they simply draw nothing and occupy nothing.
     */
    private fun hideEverythingExcept(container: ViewGroup, keep: View) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child === keep) continue
            hideRemoving(child)
        }
        if (logged.compareAndSet(false, true)) {
            Log.i(TAG, "stock status-bar views removed (GONE + 0x0), not overlaid - FR-08")
        }
    }

    private fun hideDescendants(group: ViewGroup) {
        for (i in 0 until group.childCount) {
            hideRemoving(group.getChildAt(i))
        }
    }

    private fun hideRemoving(view: View) {
        rememberOriginal(view)
        view.visibility = View.GONE
        view.layoutParams?.let { lp ->
            lp.width = 0
            lp.height = 0
            view.layoutParams = lp
        }
        if (view is ViewGroup) hideDescendants(view)
    }

    private fun rememberOriginal(view: View) {
        if (hiddenOriginals.any { it.view === view }) return
        val lp = view.layoutParams
        hiddenOriginals.add(HiddenState(view, view.visibility, lp?.width ?: 0, lp?.height ?: 0))
    }

    /** Puts every hidden view back as it was. Used when the element is switched off or torn down. */
    fun restoreStockViews() {
        for (state in hiddenOriginals) {
            try {
                state.view.visibility = state.visibility
                state.view.layoutParams?.let { lp ->
                    lp.width = state.width
                    lp.height = state.height
                    state.view.layoutParams = lp
                }
            } catch (t: Throwable) {
                Log.w(TAG, "restore: ${t.message}")
            }
        }
        hiddenOriginals.clear()
    }

    private data class HiddenState(val view: View, val visibility: Int, val width: Int, val height: Int)

    /** The Duo element takes the battery slot's column, scaled by the size setting (FR-03). */
    private fun slotWidthPx(container: ViewGroup): Int {
        val base = measuredSlotWidth(container)
        return (base * settings.sizePercent / 100).coerceAtLeast(1)
    }

    /** The battery slot's column — 83 px measured on the target device — or the best available estimate. */
    private fun measuredSlotWidth(container: ViewGroup): Int {
        val measured = try {
            val id = context.resources.getIdentifier(rom.batteryId, "id", rom.systemUiPackage)
            if (id != 0) container.findViewById<View>(id)?.width ?: 0 else 0
        } catch (_: Throwable) {
            0
        }
        if (measured > 0) return measured
        if (container.height > 0) return container.height // square, like the reference element
        return (27.4f * context.resources.displayMetrics.density).toInt() // 83 px at density 3.025
    }

    private fun findStatusIconsHost(root: View): LinearLayout? {
        if (romLogged.compareAndSet(false, true)) {
            Log.i(TAG, "ROM adapter: ${rom.id} (${rom.label}) - ${rom.notes}")
        }
        for (name in rom.containerIds) {
            val id = context.resources.getIdentifier(name, "id", rom.systemUiPackage)
            if (id == 0) continue
            val found = root.findViewById<View>(id)
            Log.d(TAG, "container $name -> ${found?.javaClass?.simpleName ?: "null"}")
            (found as? LinearLayout)?.let { return it }
        }
        Log.w(TAG, "no container id resolved (tried ${rom.containerIds}) - status bar left untouched")
        return null
    }

    private companion object {
        const val TAG = "DuoSB"
        const val SURVIVAL_MS = 4_000L
    }
}
