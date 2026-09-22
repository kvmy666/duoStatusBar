package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.rom.RomDetection

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
    private var root: View? = null
    private var element: DuoElement? = null

    /**
     * FR-03b: the status bar is not one bar. Read out of the device's SystemUI
     * (`reverse/SystemUI-device.apk`), three of them carry their own icon strip:
     *
     *     status_bar          system_icons               the home screen / in-app bar  (attached first)
     *     keyguard_status_bar system_icons               the lock screen
     *     combined_qs_header  shade_header_system_icons  the pulled-down shade's header
     *
     * The latter two live in the `NotificationShade` window and draw their own stock icons over the
     * element, which is why the lock screen and the shade looked untouched. Each gets a slot: its own
     * element and its own hiding pass, on the same rule as the main bar - never hide a strip the
     * element is not drawing in.
     */
    /** One-shot log gates, so a repeated pass cannot spam the log (see [LogOnce]). */
    private val logOnce = LogOnce()

    private inner class ExtraBar(val name: String, val center: Boolean = true) {
        var container: ViewGroup? = null

        /** What the element's size is capped by, and what it is centred in when [center]. */
        var bar: View? = null
        var element: DuoElement? = null

        /**
         * The slot width captured *before* the stock icons were hidden. Re-measuring later would read
         * the hidden battery's 0-width and fall back to the strip height, which changes the element's
         * size mid-session and forces a live relayout (see [SlotGeometry]).
         */
        var basePx = 0
    }

    /**
     * FR-03b: called as each icon view is added to a status icon container (hooked at
     * `StatusIconContainer.addView`).
     *
     * Hiding on a layout pass is not enough on the shade header: the ROM repopulates `statusIcons`
     * *after* the pass, so the icons came straight back. This hides each one at the moment it arrives,
     * which is the only moment the ROM cannot undo.
     */
    fun onStatusIconAdded(view: View) {
        try {
            if (view === element?.ui || extras.any { it.element?.ui === view }) return
            var parent: View? = view.parent as? View
            while (parent != null) {
                if (parent === host || extras.any { it.container === parent }) {
                    val container = parent
                    logOnce.once("added:${view.javaClass.simpleName}") {
                        L.i("hiding a status icon as it arrives: ${view.javaClass.simpleName} in " +
                                "${container.javaClass.simpleName}")
                    }
                    hider.hide(view)
                    return
                }
                parent = parent.parent as? View
            }
            logOnce.once("unmanaged:${view.javaClass.simpleName}") {
                L.i("status icon arrived in an unmanaged container: ${view.javaClass.simpleName} " +
                        "parent=${(view.parent as? View)?.javaClass?.simpleName}")
            }
        } catch (t: Throwable) {
            L.w("icon added: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private val extras = ArrayList<ExtraBar>()
    private val guard = DuoGuard(context)
    private val rom = RomDetection.forThisRom(
        Build.MANUFACTURER.orEmpty(),
        Build.BRAND.orEmpty(),
        Build.PRODUCT.orEmpty(),
        Build.DISPLAY.orEmpty()
    )
    private var settings = ModuleSettings.DEFAULT

    /** Size and position maths; owns the size/slot base captured once at attach (FR-03/17). */
    private val geometry = SlotGeometry(context, rom)

    /** Hides and restores the stock views (FR-08/21); owns their remembered original state. */
    private val hider = StockIconHider()

    /** Restyles the status-bar clock and remembers its original so switching off restores it. */
    private val clock = ClockFontController(context, rom)

    /** The element's own tap gestures (FR-05/18), driven from the status-bar touch hook. */
    private val gestures = ElementGestures(context)

    val duo: DuoElement? get() = element

    /** Pushes a snapshot at every element that is drawing, in every bar. */
    fun render(v: DuoVisual) {
        for (target in listOfNotNull(element) + extras.mapNotNull { it.element }) {
            try {
                target.render(v)
            } catch (t: Throwable) {
                L.w("render: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /**
     * FR-03b: puts the element into one of the *other* status bars - the keyguard's, or the shade
     * header's. [stripId] is the id of that bar's icon strip, measured from the device's SystemUI:
     * `system_icons` for the keyguard bar, `shade_header_system_icons` for the shade header.
     *
     * Only ever runs after the main bar is live, on the same rule as the main bar: never hide a strip
     * the element is not drawing in. If anything fails, that bar keeps its stock icons.
     */
    fun attachExtra(name: String, root: View, stripId: String): Boolean {
        if (extras.any { it.name == name }) return true
        if (element == null) return false
        return try {
            val stage = guard.stage()
            if (stage == DuoGuard.OFF) return false
            val id = context.resources.getIdentifier(stripId, "id", rom.systemUiPackage)
            if (id == 0) {
                L.w("$name: no id for $stripId - leaving its stock icons")
                return false
            }
            val found = root.findViewById<View>(id)
            if (found == null) {
                logOnce.once("missing:$name") {
                    L.i("$name: $stripId not in this window yet (id=$id) - waiting for it to be inflated")
                }
                return false
            }
            val target = found as? ViewGroup ?: return false
            if (target === host) return true // same strip as the main bar: nothing extra to do
            // Centre on the *bar*, not the window it lives in: the shade window is the whole screen, so
            // centring on it put the element 1300 px down the lock screen (measured).
            val bar = findBar(target) ?: target
            attachInto(name, target, bar, center = true, elementRoot = root, stage = stage, logClass = bar.javaClass.simpleName)
        } catch (t: Throwable) {
            L.e("$name attach: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /**
     * FR-03b, the shade header. It is not in the shade window's tree at all - `ShadeHeaderController`
     * builds it - so it is handed over directly by the hook instead of being searched for.
     *
     * Its icon area is the `shade_header_system_icons` frame (measured from `combined_qs_header.xml`),
     * found by walking up from the `statusIcons` container the controller itself binds to.
     */
    fun attachShadeHeader(header: View): Boolean {
        if (extras.any { it.name == "shade header" }) return true
        val area = findShadeIconsArea(header)
        if (area == null) {
            logOnce.once("missing:shade header") {
                L.w("shade header: no icon area found in ${header.javaClass.simpleName}")
            }
            return false
        }
        // Centred on the header, not the icon area: the area is a 0x0 strip at the header's end
        // (measured - it is laid out later), and the header is what has a real height to cap against.
        return attachExtraView("shade header", area, header, center = false)
    }

    private fun findShadeIconsArea(header: View): ViewGroup? {
        val id = context.resources.getIdentifier("shade_header_system_icons", "id", rom.systemUiPackage)
        (if (id != 0) header.findViewById<View>(id) as? ViewGroup else null)?.let { return it }
        // Fall back to the parent of the icon container the controller binds to.
        val icons = context.resources.getIdentifier("statusIcons", "id", rom.systemUiPackage)
        val container = if (icons != 0) header.findViewById<View>(icons) else null
        return container?.parent as? ViewGroup
    }

    /** Attaches into an already-known container (the shade header's icon area). */
    private fun attachExtraView(
        name: String,
        target: ViewGroup,
        cap: View? = null,
        center: Boolean = true
    ): Boolean {
        if (extras.any { it.name == name }) return true
        if (element == null) return false
        return try {
            val stage = guard.stage()
            if (stage == DuoGuard.OFF) return false
            if (target === host) return true
            attachInto(name, target, cap, center, elementRoot = target, stage = stage, logClass = target.javaClass.simpleName)
        } catch (t: Throwable) {
            L.e("$name attach: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /**
     * The shared body of every extra-bar attach: inject the element, hide that bar's stock icons once it
     * is drawing, and drop it cleanly if it never binds.
     *
     * [elementRoot] is what the renderer's hardware-acceleration check reads (the window for the
     * keyguard bar, the icon area for the shade header); [logClass] is the class name the success line
     * reports, which differs per bar and must stay stable.
     */
    private fun attachInto(
        name: String,
        target: ViewGroup,
        cap: View?,
        center: Boolean,
        elementRoot: View,
        stage: Int,
        logClass: String
    ): Boolean {
        val slot = ExtraBar(name, center)
        val candidate = createElement(elementRoot, stage)
        slot.container = target
        slot.bar = cap ?: target
        slot.basePx = geometry.measuredWidth(target)
        slot.element = candidate
        extras.add(slot)
        allowOverflow(target)
        val side = geometry.sidePx(target, slot.bar, slot.basePx)
        candidate.ui.layoutParams = layoutParamsFor(target, side)
        target.addView(candidate.ui)
        applyExtraLayout(slot)
        candidate.onReady {
            if (slot.element !== candidate) return@onReady
            hider.hideAllExcept(target, candidate.ui)
            candidate.reveal(settings.revealMs)
            L.i("Duo injected into $name ($logClass, ${side}px) - FR-03b")
        }
        candidate.onFailed {
            // Optional bar: drop the element and leave its stock icons alone.
            L.w("$name element did not bind - leaving its stock icons")
            runCatching { target.removeView(candidate.ui) }
            runCatching { candidate.teardown() }
            extras.remove(slot)
        }
        return true
    }

    /** The container decides the LayoutParams type: the strips are LinearLayouts, the shade header is not. */
    private fun layoutParamsFor(container: ViewGroup, side: Int): ViewGroup.LayoutParams = when (container) {
        is LinearLayout -> LinearLayout.LayoutParams(side, side)
        is android.widget.FrameLayout -> android.widget.FrameLayout.LayoutParams(side, side)
        else -> ViewGroup.LayoutParams(side, side)
    }

    private fun applyExtraLayout(slot: ExtraBar) {
        val target = slot.container ?: return
        val view = slot.element?.ui ?: return
        try {
            val side = geometry.sidePx(target, slot.bar, slot.basePx)
            val lp = view.layoutParams
            // Same rule as applyLayout: never request a layout pass unless the size actually changed.
            if (lp == null || lp.width != side || lp.height != side) {
                view.layoutParams = layoutParamsFor(target, side)
                view.requestLayout()
            }
            view.translationX = settings.offsetX * context.resources.displayMetrics.density
            view.translationY = if (slot.center) geometry.windowCenterShiftY(target, slot.bar) else 0f
        } catch (t: Throwable) {
            L.w("${slot.name} layout: ${t.message}")
        }
    }

    /** FR-16: whether the percentage should be drawn — asked by the state monitor on every render. */
    val showPercent: Boolean get() = settings.showPercent

    /** FR-25: how long an arrival takes, in ms — asked by the monitor when it fires one. */
    val revealMs: Int get() = settings.revealMs

    // The Animations section (FR-25). The master gates the three individual switches.
    val animationsEnabled: Boolean get() = settings.animationsEnabled
    val arrivalEnabled: Boolean get() = settings.animationsEnabled && settings.arrivalEnabled
    val departureEnabled: Boolean get() = settings.animationsEnabled && settings.departureEnabled
    val chargingEnabled: Boolean get() = settings.animationsEnabled && settings.chargingEnabled

    /**
     * Shows or hides every element's view outright.
     *
     * The always-on display is a different thing from the lock screen, and the element was trying to be
     * both: the AOD cycles doze -> suspend -> off -> on several times a second, and each cycle re-laid
     * out the bar the element lives in, so it flickered. The AOD has its own minimal status bar, so the
     * honest answer is to take the element off the display while it is dozing rather than leave it
     * half-drawn.
     */
    fun setElementsVisible(on: Boolean) {
        for (target in listOfNotNull(element) + extras.mapNotNull { it.element }) {
            try {
                target.ui.visibility = if (on) View.VISIBLE else View.GONE
            } catch (t: Throwable) {
                L.w("visibility: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /** FR-25: fires the arrival on every bar, so the lock screen wakes with the rest of the element. */
    fun revealAll(ms: Int) {
        for (target in listOfNotNull(element) + extras.mapNotNull { it.element }) {
            try {
                target.reveal(ms)
            } catch (t: Throwable) {
                L.w("reveal: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /**
     * Re-reads the user's settings and applies what can change while running (size, offset).
     * Called on attach and whenever the app says something changed, so no restart is needed.
     */
    fun refreshSettings(): ModuleSettings {
        val fresh = DuoSettingsClient.read(context)
        val changed = fresh != settings
        settings = fresh
        if (changed) {
            L.i("settings rev ${fresh.revision}: size ${fresh.sizePercent}%, offset ${fresh.offsetX}dp, " +
                    "percent=${fresh.showPercent}, rive=${fresh.useRive}, live=${fresh.liveApply}"
            )
            if (fresh.liveApply) {
                applyLayout()
                for (slot in extras) applyExtraLayout(slot)
            } else {
                // The safe path: size and position are saved but wait for the next start. Gestures and
                // the clock's font are not geometry, so they can still change live.
                element?.ui?.let { gestures.install(it, settings.tapAction, settings.doubleTapAction, settings.longPressAction) }
                L.i("live apply off - size/position take effect after Restart System UI")
            }
            clock.apply(root, settings.systemClockFont)
        }
        return fresh
    }

    /** Size and offset come from the settings, so reshaping the element needs no re-injection (FR-03/17). */
    private fun applyLayout() {
        val target = host ?: return
        val view = element?.ui ?: return
        try {
            val side = geometry.sidePx(target, root)
            val lp = view.layoutParams
            // Only touch the view's bounds when the size actually changes. Re-assigning layoutParams
            // (even to the same numbers) requests a layout pass, and a layout pass on the Rive
            // TextureView is what took System UI down; the size is restart-only now, so this normally
            // does nothing at all and a settings change can never reshape the drawing.
            if (lp == null || lp.width != side || lp.height != side) {
                view.layoutParams = LinearLayout.LayoutParams(side, side)
                view.requestLayout()
            }
            view.translationX = settings.offsetX * context.resources.displayMetrics.density
            // The strip sits low in the window, so centring on it wastes the space above. Centre the
            // element in the whole status bar instead, which is what lets it grow to the window height.
            view.translationY = geometry.windowCenterShiftY(target, root)
            gestures.install(view, settings.tapAction, settings.doubleTapAction, settings.longPressAction)
        } catch (t: Throwable) {
            L.w("applyLayout: ${t.message}")
        }
    }

    /** Lets the element draw outside the 61 px icon strip, up to the status bar window's bounds. */
    private fun allowOverflow(view: View) {
        var v: View? = view
        while (v is ViewGroup) {
            v.clipChildren = false
            v.clipToPadding = false
            v = v.parent as? View
        }
    }

    /**
     * Feeds a status-bar touch to the element's gestures when it lands on the element (FR-05/18).
     *
     * Called from a `dispatchTouchEvent` hook on the status bar, above the point where the bar swallows
     * touches, because the injected view never receives them. See [ElementGestures.handle].
     */
    fun handleElementTouch(event: MotionEvent): Boolean = gestures.handle(event, element?.ui)

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
                logOnce.once("gate") {
                    L.i("gated off - enable with: ${guard.enableHint}")
                }
                return false
            }
            val target = findStatusIconsHost(statusBarRoot)
            if (target == null) {
                L.w("system_icons not found - status bar left untouched")
                return false
            }
            root = statusBarRoot
            // Pin the slot width now, while the battery view still has its real width: once the stock
            // icons are hidden it reads 0 and the fallback would change the size mid-session.
            val basePx = geometry.measuredWidth(target)
            val candidate = createElement(statusBarRoot, stage)
            refreshSettings()
            // Capture the size once per process: live size changes are deferred to a restart (see
            // [SlotGeometry.appliedSize]) because resizing the Rive view live used to take System UI down.
            geometry.capture(settings.sizePercent, basePx)
            logOnce.once("facts") {
                DuoSbFacts.report(context, statusBarRoot, target, geometry.widthPx(target))
            }
            val side = geometry.sidePx(target, root)
            allowOverflow(target)
            candidate.ui.layoutParams = LinearLayout.LayoutParams(side, side)
            target.addView(candidate.ui)
            host = target
            element = candidate
            applyLayout()
            clock.apply(root, settings.systemClockFont)
            // The stock icons are hidden and the first reveal fires only once the element reports itself
            // live. A Rive state machine binds *after* this method returns (it needs the view attached to a
            // window), so hiding here would cover an empty slot; Canvas reports ready immediately.
            candidate.onReady { onElementReady(candidate, target) }
            candidate.onFailed { onElementFailed(candidate, target) }
            forgetAttemptsAfterSurvival(candidate)
            true
        } catch (t: Throwable) {
            L.e("attach failed: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /** The element is live: now it is safe to take the stock icons out and fire the reveal (FR-08/25). */
    private fun onElementReady(candidate: DuoElement, target: LinearLayout) {
        if (element !== candidate) return
        try {
            hider.hideAllExcept(target, candidate.ui)
            candidate.reveal(settings.revealMs)
            val width = candidate.ui.layoutParams?.width ?: 0
            L.i("Duo injected into ${target.javaClass.simpleName} (${width}px wide, ${settings.sizePercent}%)")
        } catch (t: Throwable) {
            L.w("onReady: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /**
     * The Rive element never bound its view model. Rather than leave an empty slot, it is replaced by the
     * no-native Canvas element, which draws the ring and the percentage from the same mapping (FR-21).
     */
    private fun onElementFailed(candidate: DuoElement, target: LinearLayout) {
        if (element !== candidate) return
        L.w("Rive element did not bind - falling back to Canvas")
        val canvas = try {
            DuoCanvasView(context).also { it.start() }
        } catch (t: Throwable) {
            L.e("Canvas fallback failed: ${t.javaClass.simpleName}: ${t.message}")
            return
        }
        try {
            candidate.teardown()
            target.removeView(candidate.ui)
        } catch (t: Throwable) {
            L.w("fallback remove: ${t.message}")
        }
        canvas.ui.layoutParams = LinearLayout.LayoutParams(geometry.widthPx(target), ViewGroup.LayoutParams.MATCH_PARENT)
        target.addView(canvas.ui)
        element = canvas
        applyLayout()
        canvas.onReady { onElementReady(canvas, target) }
        L.i("element: Canvas (fallback after Rive did not bind)")
    }

    private fun createElement(root: View, stage: Int): DuoElement {
        if (stage >= DuoGuard.RIVE) {
            riveElement(root)?.let { rive ->
                L.i("element: Rive (stage $stage)")
                return rive
            }
        }
        val canvas = DuoCanvasView(context)
        canvas.start()
        L.i("element: Canvas (stage $stage)")
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
            L.w("status bar window is not hardware accelerated - Rive needs a Surface, using Canvas")
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
        // FR-03b: the keyguard bar and the shade header are re-shown on every shade/lock transition,
        // so they get the same pass.
        reapplyExtraHiding()
        // The clock is re-inflated with the strip on some ROMs, so its font is re-applied here too.
        clock.apply(root, settings.systemClockFont)
        val target = host ?: return
        val keep = element?.ui ?: return
        // Never hide the stock icons over an element that is not drawing yet: a layout pass can arrive
        // before Rive has bound its view model, and hiding then would leave a blank stretch of status bar.
        if (!(element?.isReady ?: false)) return
        if (!ensureElementAttached()) {
            // Never leave a hole: if the element cannot live in the rebuilt strip, the stock icons come back
            // rather than an empty stretch of status bar. The next hide pass remembers them again.
            L.w("element could not be re-attached - restoring the stock icons instead of leaving a gap")
            hider.restore()
            return
        }
        try {
            hider.hideAllExcept(target, keep)
        } catch (t: Throwable) {
            L.w("reapplyHiding: ${t.message}")
        }
    }

    /**
     * Rotation (and a changed icon set on some ROMs) re-inflates the whole strip, so our view is gone from the
     * tree while `host` still points at the *old* container. Hiding the stock icons in the new strip at that
     * moment would produce an empty status bar — the one failure this module must never cause. So the element
     * is re-attached first, and if that is not possible the caller is told to put the stock icons back.
     */
    private fun ensureElementAttached(): Boolean {
        val view = element?.ui ?: return true
        val target = host ?: return false
        if (view.parent === target && view.isAttachedToWindow) return true
        return try {
            (view.parent as? ViewGroup)?.removeView(view)
            val fresh = root?.let { findStatusIconsHost(it) } ?: target
            host = fresh
            fresh.addView(view)
            applyLayout()
            L.i("element re-attached into ${fresh.javaClass.simpleName} after the strip was rebuilt")
            true
        } catch (t: Throwable) {
            L.w("re-attach failed: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /** Removes the element and puts the stock icons back exactly as they were. */
    fun teardown() {
        try {
            element?.let { host?.removeView(it.ui) }
            element?.teardown()
        } catch (_: Throwable) {
        }
        for (slot in extras) {
            try {
                slot.element?.let { slot.container?.removeView(it.ui) }
                slot.element?.teardown()
            } catch (_: Throwable) {
            }
        }
        extras.clear()
        element = null
        hider.restore()
        clock.restore()
        geometry.reset()
        host = null
        root = null
    }

    /** FR-03b: the same hide pass for every extra bar, once its own element is drawing. */
    private fun reapplyExtraHiding() {
        for (slot in extras) {
            val target = slot.container ?: continue
            val keep = slot.element?.ui ?: continue
            if (!(slot.element?.isReady ?: false)) continue
            try {
                hider.hideAllExcept(target, keep)
            } catch (t: Throwable) {
                L.w("${slot.name} hiding: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    // ----------------------------------------------------------------------------- internals

    /**
     * The keyguard's status bar itself - `com.android.systemui.statusbar.phone.KeyguardStatusBarView`,
     * read out of the device's SystemUI (`reverse/SystemUI-device.apk`, `layout/keyguard_status_bar.xml`),
     * not guessed. Walking up from the strip rather than looking the id up keeps it working on a ROM
     * that spells the id differently, and gives the right height to centre the element in.
     */
    private fun findBar(strip: View): View? {
        var view: View? = strip
        while (view != null) {
            val name = view.javaClass.simpleName
            if (name.contains("KeyguardStatusBar") || name.contains("ShadeHeader")) return view
            view = view.parent as? View
        }
        return null
    }

    private fun findStatusIconsHost(root: View): LinearLayout? {
        logOnce.once("rom") {
            L.i("ROM adapter: ${rom.id} (${rom.label}) - ${rom.notes}")
        }
        for (name in rom.containerIds) {
            val id = context.resources.getIdentifier(name, "id", rom.systemUiPackage)
            if (id == 0) continue
            val found = root.findViewById<View>(id)
            L.d("container $name -> ${found?.javaClass?.simpleName ?: "null"}")
            (found as? LinearLayout)?.let { return it }
        }
        L.w("no container id resolved (tried ${rom.containerIds}) - status bar left untouched")
        return null
    }

    private companion object {
        const val SURVIVAL_MS = 4_000L
    }
}
