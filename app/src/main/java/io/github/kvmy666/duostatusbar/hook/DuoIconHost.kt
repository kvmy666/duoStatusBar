package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
    private val logged = AtomicBoolean(false)
    private val gateLogged = AtomicBoolean(false)
    private val factsLogged = AtomicBoolean(false)

    val duo: DuoElement? get() = element

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
            val width = slotWidthPx(target)
            candidate.ui.layoutParams = LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT)
            target.addView(candidate.ui)
            host = target
            element = candidate
            hideEverythingExcept(target, candidate.ui)
            candidate.reveal()
            forgetAttemptsAfterSurvival(candidate)
            Log.i(TAG, "Duo injected into ${target.javaClass.simpleName} (${width}px wide)")
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

    fun teardown() {
        try {
            element?.let { host?.removeView(it.ui) }
            element?.teardown()
        } catch (_: Throwable) {
        }
        element = null
        host = null
    }

    // ----------------------------------------------------------------------------- internals

    private fun hideEverythingExcept(container: ViewGroup, keep: View) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child === keep) continue
            child.visibility = View.GONE
            child.layoutParams?.let { lp ->
                lp.width = 0
                lp.height = 0
                child.layoutParams = lp
            }
            if (child is ViewGroup) hideDescendants(child)
        }
        if (logged.compareAndSet(false, true)) {
            Log.i(TAG, "stock status-bar views removed (GONE + 0x0), not overlaid - FR-08")
        }
    }

    private fun hideDescendants(group: ViewGroup) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            child.visibility = View.GONE
            child.layoutParams?.let { lp ->
                lp.width = 0
                lp.height = 0
                child.layoutParams = lp
            }
            if (child is ViewGroup) hideDescendants(child)
        }
    }

    /** The Duo element takes the battery slot's column (83 px measured on the target device). */
    private fun slotWidthPx(container: ViewGroup): Int {
        val measured = try {
            val id = context.resources.getIdentifier("battery", "id", SYSTEMUI_PACKAGE)
            if (id != 0) container.findViewById<View>(id)?.width ?: 0 else 0
        } catch (_: Throwable) {
            0
        }
        if (measured > 0) return measured
        if (container.height > 0) return container.height // square, like the reference element
        return (27.4f * context.resources.displayMetrics.density).toInt() // 83 px at density 3.025
    }

    private fun findStatusIconsHost(root: View): LinearLayout? {
        for (name in CONTAINER_IDS) {
            val id = context.resources.getIdentifier(name, "id", SYSTEMUI_PACKAGE)
            if (id == 0) continue
            val found = root.findViewById<View>(id)
            Log.d(TAG, "container $name -> ${found?.javaClass?.simpleName ?: "null"}")
            (found as? LinearLayout)?.let { return it }
        }
        return null
    }

    private companion object {
        const val TAG = "DuoSB"
        const val SURVIVAL_MS = 4_000L
        const val SYSTEMUI_PACKAGE = "com.android.systemui"
        val CONTAINER_IDS = listOf("system_icons", "system_icons_container", "status_bar_end_side_content")
    }
}
