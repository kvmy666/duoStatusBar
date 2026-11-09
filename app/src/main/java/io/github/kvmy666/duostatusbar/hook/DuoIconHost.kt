package io.github.kvmy666.duostatusbar.hook

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.util.WeakHashMap
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.hook.rom.RomDetection
import io.github.kvmy666.duostatusbar.hook.rom.RomProfiles
import io.github.kvmy666.duostatusbar.hook.events.LiveEventType
import io.github.kvmy666.duostatusbar.hook.rom.RomResources
import io.github.kvmy666.duostatusbar.settings.DuoPrefs

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
/** A shade cluster is safe to attach to only once it has a real width and is shown and attached. */
internal fun shadeClusterReady(width: Int, visibility: Int, attached: Boolean, shown: Boolean): Boolean =
    width > 0 && visibility == View.VISIBLE && attached && shown

/**
 * The slot one shade route owns. A cluster taller than it is wide is the QS surface, whatever source
 * handed it over; the OnePlus simple header is also the QS surface even when it is laid out wide.
 * Everything else (the split shade header and the generic fallback) owns the shade header. The names
 * are independent, so a QS owner can no longer block the shade and vice versa.
 */
internal fun shadeSlotName(source: String?, originalCluster: ViewGroup): String =
    shadeSlotName(source, originalCluster.width, originalCluster.height)

/** Width/height form, so the naming rule can be tested without a view. */
internal fun shadeSlotName(source: String?, width: Int, height: Int): String = when {
    width < height -> "qs header"
    source == "OplusQSSimpleHeader" -> "qs header"
    else -> "shade header"
}

/** Pure ownership test: a slot owns a host only when its container, its element's parent and its
 *  attached flag all agree with [target]. Testable without a DuoIconHost instance. */
internal fun slotOwnsHost(container: View?, elementParent: View?, elementAttached: Boolean, target: View): Boolean =
    container === target && elementParent === target && elementAttached

internal fun slotInSameWindow(elementRoot: View?, windowRoot: View?): Boolean =
    elementRoot != null && windowRoot != null && elementRoot === windowRoot

/**
 * True only when a cached element is still attached to the same window the new slot was resolved in.
 *
 * [elementRoot] and [windowRoot] are `View.rootView` identities, never parent identities: a shade
 * layout pass may legitimately resolve a different container inside the same window, while a cached
 * view left in the old rotation window must never be adopted by the new one.
 */
internal fun slotSharesWindow(elementAttached: Boolean, elementRoot: View?, windowRoot: View?): Boolean =
    elementAttached && slotInSameWindow(elementRoot, windowRoot)

/**
 * Whether the main ring leaves the icon strip for the centre of the status bar.
 *
 * Camera/Compact set [ModuleSettings.lockRing] on its own; [ModuleSettings.splitIndicators] only decides
 * where the inner icons are drawn, never whether the ring is pinned.
 */
internal fun ModuleSettings.ringLocked(): Boolean = lockRing

/** Gives an injected view a stable, non-NO_ID id exactly once, before it is added to a parent. */
internal fun ensureInjectedViewId(view: View, parent: ViewGroup?) {
    if (view.id == View.NO_ID) view.id = View.generateViewId()
    if (!BuildConfig.DEBUG || parent == null) return
    // The measured crash was ConstraintSet.clone() refusing a child with NO_ID. The assignment above
    // prevents it; this detector reports the remaining unsafe case (a duplicate id in the same parent)
    // without changing the injected hierarchy.
    if (view.id == View.NO_ID) {
        L.w("injected view has NO_ID in ${parent.javaClass.simpleName}")
        return
    }
    for (i in 0 until parent.childCount) {
        val sibling = parent.getChildAt(i)
        if (sibling !== view && sibling.id == view.id) {
            L.w(
                "injected view id 0x${view.id.toString(16)} duplicates sibling " +
                    "${sibling.javaClass.simpleName} in ${parent.javaClass.simpleName}"
            )
        }
    }
}

internal class DuoIconHost(private val context: Context) {

    /** Where the element view is actually added. Usually the strip; the lock frame when the ring is pinned. */
    private var host: ViewGroup? = null

    /**
     * Where the ring lives while it follows the icon strip. [host] moves to the status bar when the ring
     * is locked; this stays the strip (or the overlay frame) so the ring can come back.
     */
    private var flowHost: ViewGroup? = null

    /** The status-bar frame the locked ring is centered in. Null while the ring follows the strip. */
    private var lockFrame: FrameLayout? = null

    /**
     * True once a lock was attempted against a measured bar. Stops the layout listener from retrying
     * a missing frame on every clock tick.
     */
    private var lockAttempted = false

    /** Stops a strip repair from re-entering itself when adding the spacer requests a layout. */
    private var repairing = false

    /** True while the ring is being moved onto the status bar, so a layout pass cannot re-attach it halfway. */
    private var holding = false

    /**
     * Holds the strip space the ring used to occupy, so sound and vibrate stay put while the ring
     * itself is drawn on the center of the screen. Null in overlay mode, where the ring never took a slot.
     */
    private var ringSpacer: View? = null

    /**
     * The middle slot last given a strip margin. -1 until the first snapshot, so the first real
     * occupant still gets a layout pass.
     */
    private var laidOutMiddle = -1

    private var root: View? = null
    private var element: DuoElement? = null

    /**
     * Bounded retry for the QS cluster. The shade header is built before the shade is expanded and the
     * cluster measures 0x0 until then (measured), so the element cannot be attached - and the stock
     * views must not be hidden - until a laid-out cluster exists. Every tick re-resolves the cluster
     * from the shade window, because the header holds a second, permanently 0x0 instance. One bounded
     * delayed retry, removed on success, detach, exhaustion or teardown; never a loop.
     */
    private var shadeRetryView: View? = null
    private var shadeRetryRunnable: Runnable? = null
    private var shadeRetryAttempts = 0
    private var shadeRetryStartedAt = 0L
    private var shadeRetrySeenAttached = false

    /** The pending "Rive survived" check, so teardown can invalidate it instead of a dead view doing so. */
    private var survivalView: View? = null
    private var survivalRunnable: Runnable? = null

    /**
     * The inner icons, drawn by a second view of the same element when [ModuleSettings.splitIndicators]
     * is on. Null in the original layout, where [element] draws the ring and the icons together.
     */
    private var indicators: DuoElement? = null

    /**
     * The strip the stock icons live in and are hidden from. Normally the same as [host]; on a ROM whose
     * strip is a custom container that refuses foreign children (HyperOS's `MiuiStatusBatteryContainer`
     * measured the element 0x0 and nothing drew), [host] is a standard ancestor and this stays the strip.
     */
    private var hideStrip: ViewGroup? = null

    /**
     * True when the element is drawn *outside* the icon strip and positioned over the battery. HyperOS 3
     * is the measured case: `system_icons` is a `MiuiStatusBatteryContainer` that lays out only its own
     * children, so the element was silently 0x0. The element instead goes into the bar's plain
     * `FrameLayout` and is anchored to the battery, whose layout is kept (INVISIBLE) for the anchor.
     */
    private var overlay = false

    /** The battery the overlay is anchored to; null unless [overlay]. */
    private var anchorBattery: View? = null

    /**
     * True when the overlay anchor is Samsung One UI 8's `CombinedStatusView` rather than a battery. It is
     * not a child of the hidden strip, so it is hidden directly ([anchorOriginalVisibility] remembers what
     * to put back) and it has to be re-found on re-attach instead of falling back to the stub battery.
     */
    private var overlayOnCombined = false

    /** The anchor's visibility before the overlay hid it, so teardown can put the stock cluster back. */
    private var anchorOriginalVisibility: Int? = null

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

        /** The inner-icon view for this bar, present only while the split layout is on. */
        var indicators: DuoElement? = null

        /** Target of the last alpha fade for this slot; null once it has settled. */
        var alphaRequest: Float? = null

        /**
         * The slot width to draw at. It is the width pinned for the main bar at attach — one size for
         * every bar — falling back to this bar's own measurement only when the main bar captured none.
         */
        var basePx = 0
        val stock = StockIconHider()

        /**
         * True when this slot is the QS cluster (`quick_qs_status_icons`). Its stock icons are two
         * named views, not "every child of the strip", and the element is anchored to the battery's
         * end instead of being centred in the cluster.
         */
        var qsCluster = false

        /** The QS cluster's stock views, hidden on ready and restored with [stock]. */
        var stockStatusIcons: View? = null
        var stockBattery: View? = null
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
            if (view === element?.ui || view === indicators?.ui ||
                extras.any { it.element?.ui === view || it.indicators?.ui === view }
            ) return
            var parent: View? = view.parent as? View
            while (parent != null) {
                val extra = extras.firstOrNull { it.container === parent }
                if (parent === host || parent === hideStrip || extra != null) {
                    val container = parent
                    val owner = extra?.stock ?: hider
                    // Never hide the last stock icon over an element that is not drawing in this very
                    // container: the ROM can add an icon before Rive binds or while the slot is being
                    // rebuilt, and hiding then leaves a blank status bar.
                    val drawing = if (extra != null) extra.element else element
                    val ready = drawing?.isReady == true &&
                        drawing.ui.parent === container && drawing.ui.isAttachedToWindow
                    if (!ready) {
                        logOnce.once("not-ready:${view.javaClass.simpleName}:${container.javaClass.simpleName}") {
                            L.i("keeping a status icon until Duo draws: ${view.javaClass.simpleName} in " +
                                    "${container.javaClass.simpleName}")
                        }
                        return
                    }
                    logOnce.once("added:${view.javaClass.simpleName}") {
                        L.i("hiding a status icon as it arrives: ${view.javaClass.simpleName} in " +
                                "${container.javaClass.simpleName}")
                    }
                    // FR-08b/A4: hide by stable key. "Hide all" keeps the legacy all-or-nothing
                    // behaviour; per-icon mode hides the replaced icons plus exactly the user's keys.
                    try {
                        hideArrivedIcon(view, container, owner)
                    } catch (t: Throwable) {
                        L.w("icon hide: ${t.javaClass.simpleName}: ${t.message}")
                        // A partial hide must not leave a hole: put this container's stock views back.
                        try {
                            owner.restore()
                        } catch (r: Throwable) {
                            L.w("icon restore after hide failure: ${r.javaClass.simpleName}: ${r.message}")
                        }
                    }
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

    /**
     * Hides one icon that arrived after the last hiding pass. The same stable key the inventory
     * publishes decides it, so `id:mobile_combo#2` means the second SIM here as well. Ignored views
     * (Duo, containers, inner signal parts, status dots) are always left alone, and the battery is
     * kept laid out as the overlay anchor instead of being GONE.
     */
    private fun hideArrivedIcon(view: View, container: View, owner: StockIconHider) {
        if (StockIconKey.isIgnored(view)) return
        val kind = StockIconKey.kindOf(view)
        val entry = StockIconKey.find(container, view)
        val hide = when {
            // Legacy "hide all" still hides every stock icon, but never a container added to the bar.
            settings.hideOtherIcons -> kind != StockIconKind.IGNORED || view !is ViewGroup
            kind.replaced -> true
            entry != null -> entry.key in hiddenStockKeys
            else -> false
        }
        if (!hide) {
            logOnce.once("kept:${view.javaClass.simpleName}") {
                L.i("keeping a status icon: ${view.javaClass.simpleName} - FR-08b")
            }
            return
        }
        if (kind == StockIconKind.BATTERY || entry?.kind == StockIconKind.BATTERY) {
            owner.hideKeepLayout(view)
        } else {
            owner.hide(view)
        }
    }

    private val extras = ArrayList<ExtraBar>()

    /**
     * The shade/QS cluster the current shade slot draws in. The OnePlus simple-header and split-header
     * hooks can both fire for the same view (and the generic header fallback may follow), so an attach
     * is keyed by the cluster's identity instead of the number of callbacks.
     */
    /**
     * Fallback owner used only before SystemUI's panel state arrives. Once [panelOwner] is known it
     * is authoritative; this field remains for boot/attach ordering and the older ROM path.
     */
    private var shadeOpen = false

    /** Last owner logged, so the next device log shows the actual QS<->shade transitions. */
    private var lastSurfaceOwner: String? = null

    /**
     * Authoritative surface state from SystemUI's panel expansion callbacks. Route attaches are only
     * layout timing; the panel state is what actually says QS vs notification shade vs collapsed.
     */
    private var panelStateKnown = false
    private var panelOwner: String? = null

    /**
     * The running collapse slide of the main copy: its resting translation and the animator that is
     * carrying it back there. Only [View.getTranslationX] is touched — alpha, y, size and parent are
     * never changed by the hand-off.
     */
    private var handoffView: View? = null
    private var handoffBaseX = 0f
    private var handoffAnimator: ObjectAnimator? = null

    /** Last requested main-bar visibility, re-applied to a newly created split indicator. */
    private var mainBarVisible = true

    /**
     * HUD -> QS/shade: a hide was requested, but the incoming copy has not been confirmed drawable.
     * The main copy stays visible until the one-frame guard sees it (or fails silent).
     */
    private var mainCoverPending = false
    private var mainCoverGuardView: View? = null
    private var mainCoverGuardAttempts = 0

    /** QS/shade -> HUD: the outgoing extra is held while the main copy has not drawn yet. */
    private var mainRevealGuardView: View? = null
    private var mainRevealGuardAttempts = 0

    /** QS/shade -> HUD: the outgoing copy is latched out once the main copy owns the seat. */
    private val drawOrder = PanelHandoff.OutgoingLatch()

    private val guard = DuoGuard(context)
    private val rom = RomDetection.forThisRom(
        Build.MANUFACTURER.orEmpty(),
        Build.BRAND.orEmpty(),
        Build.PRODUCT.orEmpty(),
        Build.DISPLAY.orEmpty(),
        // Measured profiles from assets may override; only `measured` ones are accepted.
        RomProfiles.load(context),
        Build.VERSION.SDK_INT
    )

    /** Resolves the strip and the element's parent view from the tree; see [ContainerFinder]. */
    private val finder = ContainerFinder(context, rom, logOnce)
    private var settings = ModuleSettings.DEFAULT
    /** The drawing the main element was created with; extras stay on it until a restart. */
    private var appliedDesignStyle = settings.designStyle

    /**
     * The exact status icons the user selected in the per-icon list (A4), as stable
     * [StockIconKey] keys. Kept in step with the settings so an arrival, a layout pass and the
     * settings screen all agree on which view a key names. Empty means "only the replaced icons".
     */
    private var hiddenStockKeys: Set<String> =
        DuoPrefs.parseHiddenStockIcons(ModuleSettings.DEFAULT.hiddenStockIcons).toSet()

    /** Last inventory JSON sent to the app, so a layout pass cannot re-broadcast the same list (A4). */
    private var lastStockIconJson: String? = null
    private var lastStockPublishAt = 0L

    /** Size and position maths; owns the size/slot base captured once at attach (FR-03/17). */
    private val geometry = SlotGeometry(context, rom)

    /** Hides and restores the stock views (FR-08/21); owns their remembered original state. */
    private val hider = StockIconHider()

    /** Restyles the status-bar clock and remembers its original so switching off restores it. */
    private val clock = ClockFontController(context, rom)

    /** Ring and detached-icon gestures are independent while split mode is on. */
    private val ringGestures = ElementGestures(context) { handleDownloadRingTap() }
    private val indicatorGestures = ElementGestures(context)
    private var downloadTapActive = false
    private var onDownloadRingTap: (() -> Unit)? = null
    private var touchPart: DuoPart? = null

    /** Per-view 20 % charge shake, so a second trigger cancels the first cleanly. */
    private val shakes = WeakHashMap<View, Animator>()

    val duo: DuoElement? get() = element

    fun eventEnabled(type: LiveEventType): Boolean {
        val bit = when (type) {
            LiveEventType.ALARM -> DuoPrefs.EVENT_ALARM
            LiveEventType.FLASHLIGHT -> DuoPrefs.EVENT_FLASHLIGHT
            LiveEventType.SCREENSHOT -> DuoPrefs.EVENT_SCREENSHOT
            LiveEventType.MIC -> DuoPrefs.EVENT_MIC
            LiveEventType.LOCATION -> DuoPrefs.EVENT_LOCATION
            LiveEventType.VOLUME -> DuoPrefs.EVENT_VOLUME
            LiveEventType.UNLOCK -> DuoPrefs.EVENT_UNLOCK
            LiveEventType.CALL -> DuoPrefs.EVENT_CALL
        }
        return DuoPrefs.eventEnabled(settings.eventMask, bit)
    }

    /** Which occupant the middle of the ring shows: "auto", "wifi", "cell" or "percent". */
    val middleSlot: String get() = settings.middleSlot

    /** The ring and, when split, the icon cluster, on every bar. */
    private fun allElements(): List<DuoElement> =
        listOfNotNull(element, indicators) + extras.flatMap { listOfNotNull(it.element, it.indicators) }

    /** Views this module added, so a hiding pass never treats them as stock icons. */
    private fun ourViews(): List<View> = allElements().map { it.ui } + listOfNotNull(ringSpacer)

    /** The ring is pinned to the status-bar centre whenever Camera/Compact asks for it, split or not. */
    private fun ringLocked(): Boolean = settings.ringLocked()

    /**
     * Where the icon cluster is added. Locked mode keeps it in the strip, so it still slides with the
     * clock; the ring is the view that leaves. Overlay mode has no strip slot, so the cluster stays
     * with the ring's frame and is anchored on the battery as before.
     */
    private fun clusterHome(): ViewGroup? = when {
        ringLocked() && !overlay -> hideStrip ?: flowHost ?: host
        else -> host
    }

    /** Pushes a snapshot at every element that is drawing, in every bar. */
    fun render(v: DuoVisual) {
        if (v.middleMode != laidOutMiddle) {
            laidOutMiddle = v.middleMode
            // Margins only. A full relayout here would re-seat a centered ring, and resizing the Rive
            // view is what used to take System UI down. The icon's padding is a margin, not a new size.
            applyMiddleSlotSpace()
            syncIndicators()
            for (slot in extras) syncExtraIndicators(slot)
        }
        for (target in allElements()) {
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
        val existing = extras.firstOrNull { it.name == name }
        if (existing != null) {
            // A live element is never dropped or rebuilt: a shade layout pass can resolve a different
            // container while the view is still attached to its previous (live) parent in this same
            // window. Adopt that parent instead of recreating the element (which would re-import Rive).
            // A view left in the old rotation window is not live here and must be dropped.
            val ui = existing.element?.ui
            val liveParent = ui?.parent as? ViewGroup
            val window = root.rootView
            if (ui != null && liveParent != null &&
                slotSharesWindow(ui.isAttachedToWindow, ui.rootView, window)
            ) {
                existing.container = liveParent
                val id = RomResources.id(context, rom, stripId)
                val resolved = if (id != 0) root.findViewById<View>(id) as? ViewGroup else null
                if (resolved != null) {
                    existing.bar = finder.findBar(resolved) ?: resolved
                }
                applyExtraLayout(existing)
                syncExtraIndicators(existing)
                logOnce.once("reuse:$name:${System.identityHashCode(liveParent)}") {
                    L.i("$name reuse attached slot parent=${liveParent.javaClass.simpleName}")
                }
                return true
            }
            // Detached, or still attached to the old rotation window: only in those cases may the slot
            // be dropped and rebuilt, once, in this window.
            L.i("$name dropping stale slot from another window")
            dropSlot(existing)
        }
        if (element == null) return false
        return try {
            val stage = guard.stage()
            if (stage == DuoGuard.OFF) return false
            val id = RomResources.id(context, rom, stripId)
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
            if (!containerUsable(target)) {
                logOnce.once("not-ready:$name") {
                    L.i("$name: $stripId is not laid out or not attached yet (id=$id) - waiting")
                }
                return false
            }
            // Centre on the *bar*, not the window it lives in: the shade window is the whole screen, so
            // centring on it put the element 1300 px down the lock screen (measured).
            val bar = finder.findBar(target) ?: target
            attachInto(name, target, bar, center = true, elementRoot = root, stage = stage, logClass = bar.javaClass.simpleName)
        } catch (t: Throwable) {
            L.e("$name attach: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /**
     * Whether [cluster] is the real shade header, not the QS surface. The measured CPH2747 has two
     * wide `quick_qs_status_icons` clusters (QS 297x56 and shade 297x101); the shade one is under
     * `header_container`. On a ROM without that id, keep the old source/dimension rule.
     */
    private fun isShadeSurface(cluster: ViewGroup, source: String?): Boolean {
        // The split shade controller hands over the wide QS cluster (297x56), not the shade
        // header. On a ROM without `header_container`, dimensions alone would misname it as
        // "shade header" and hide the QS copy.
        if (source == "OplusSplitShade") return false
        val id = RomResources.id(context, rom, "header_container")
        if (id != 0) return finder.hasAncestorId(cluster, id)
        return source != "OplusQSSimpleHeader" && shadeSlotName(source, cluster) == "shade header"
    }

    /**
     * FR-03b, the shade header. It is not in the shade window's tree at all - `ShadeHeaderController`
     * builds it - so it is handed over directly by the hook instead of being searched for.
     *
     * The QS cluster is found in [window], not in [header]: the measured CPH2747 tree (2026-10-27) has
     * a second `quick_qs_status_icons` inside `split_shade_status_bar` that stays 0x0/GONE, while the
     * drawn one is under `qs_frame`/`header_container`. [header] remains what the non-QS fallback
     * (`shade_header_system_icons`, the parent of `statusIcons`) is resolved from.
     */
    fun attachShadeHeader(header: View, window: View? = null): Boolean {
        // The whole path is guarded: it runs from a SystemUI lifecycle callback (the header controller's
        // constructor, a ViewStub inflate, a shade layout pass), so anything it throws would surface in
        // SystemUI itself, not here. A failure returns false and leaves the stock header untouched -
        // never a half-attached extra bar (FR-21).
        return try {
            val already = extras.firstOrNull { it.name == "shade header" }
            if (already != null && slotElementAttached(already, already.container)) return true
            // Never remove/reparent a live Duo from a layout/hook callback. Ownership is checked
            // against the *resolved* container below; a live QS slot must not block the real shade
            // header (the boot-time split header can be the QS surface).
            val cluster = window?.let { finder.findVisibleQuickQsCluster(it) }
            if (cluster is ViewGroup) {
                ShadeProbe.logOnce(
                    "shadeProbe:reResolve:${ShadeProbe.key(cluster)}",
                    "shadeProbe reResolve: visible cluster ${ShadeProbe.describe(cluster)}"
                )
                // A tall/narrow cluster is the QS surface, owned by its vendor route; the generic
                // shade path must not consume the shade slot with it before the shade opens.
                if (cluster.width < cluster.height) {
                    logOnce.once("shadeNarrow:${System.identityHashCode(cluster)}") {
                        L.i("shade route skipping narrow QS cluster " +
                            "(${cluster.width}x${cluster.height}) - waiting for the wide shade cluster")
                    }
                    markSurface("qs header")
                    scheduleShadeRetry(header, window)
                    return false
                }
                val attached = attachGenericShadeCluster(cluster, header)
                if (!attached) scheduleShadeRetry(header, window)
                return attached
            }
            val area = finder.findShadeIconsArea(header)
            if (area == null) {
                logOnce.once("missing:shade header") {
                    L.w("shade header: no icon area found in ${header.javaClass.simpleName}")
                }
                scheduleShadeRetry(header, window)
                return false
            }
            val quick = finder.isQuickQsCluster(area)
            var previous = extras.firstOrNull { it.name == "shade header" }
            if (previous?.container === area) {
                if (slotElementAttached(previous, area)) {
                    clearShadeRetry()
                    markSurface("shade header")
                    return true
                }
                dropSlot(previous)
                previous = null
            }
            // Measured on the CPH2747: `quick_qs_status_icons` is 0x0 until the shade expands. Laying
            // Duo out there now would leave it invisible while the hide pass removed the stock battery.
            // The fallback area is gated the same way: wait for the first layout with a real width,
            // visible, attached and shown, then attach exactly once. The working slot is kept until
            // this replacement has passed the check.
            if (!shadeClusterReady(area.width, area.visibility, area.isAttachedToWindow, area.isShown)) {
                ShadeProbe.log(
                    "shadeProbe retry: shade icon area not usable " +
                        "(${ShadeProbe.describe(area)}) - waiting for the shade to expand"
                )
                scheduleShadeRetry(header, window)
                return false
            }
            // The generic route owns the shade header only. A tall/narrow cluster is the QS surface;
            // attaching it here would consume the shade slot before the wide shade cluster exists.
            if (area.width < area.height) {
                ShadeProbe.log(
                    "shadeProbe generic: header cluster is narrow " +
                        "(${ShadeProbe.describe(area)}) - waiting for the wide shade cluster"
                )
                markSurface("qs header")
                scheduleShadeRetry(header, window)
                return false
            }
            if (element == null) {
                ShadeProbe.log("shadeProbe generic: main bar not attached yet - waiting")
                scheduleShadeRetry(header, window)
                return false
            }
            // The resolved area may change inside the same shade window (header re-inflation). Reuse
            // the attached slot there instead of replacing it; only a stale view from the old window
            // or a detached one is dropped before the one new surface is created below.
            if (previous != null) {
                val ui = previous.element?.ui
                val liveParent = ui?.parent as? ViewGroup
                if (ui != null && liveParent != null &&
                    slotSharesWindow(ui.isAttachedToWindow, ui.rootView, area.rootView)
                ) {
                    previous.container = liveParent
                    previous.bar = header
                    applyExtraLayout(previous)
                    syncExtraIndicators(previous)
                    clearShadeRetry()
                    markSurface("shade header")
                    logOnce.once("reuse:shade header:${System.identityHashCode(liveParent)}") {
                        L.i("shade header reuse attached slot parent=${liveParent.javaClass.simpleName}")
                    }
                    return true
                }
                L.i("shade header dropping stale slot before one replacement")
                dropSlot(previous)
                previous = null
            }
            // Centred on the header, not the icon area: the area is a 0x0 strip at the header's end
            // (measured - it is laid out later), and the header is what has a real height to cap against.
            val attached = replaceSlot(previous) {
                attachExtraView(
                    "shade header", area, header, center = false, qsCluster = quick
                )
            }
            if (attached) {
                clearShadeRetry()
                markSurface("shade header")
            } else {
                scheduleShadeRetry(header, window)
            }
            attached
        } catch (t: Throwable) {
            L.e("shade header attach: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /**
     * Window-only route: resolves the laid-out QS cluster from the shade window when the vendor header
     * controller has not produced a header yet (the live enable-from-off path runs after the vendor
     * `onInit`, so the cluster is already in the window). Never falls back to the header search here.
     */
    fun attachShadeWindow(window: View): Boolean = try {
        val cluster = finder.findVisibleShadeArea(window)
        if (cluster == null) {
            ShadeProbe.logOnce(
                "shadeProbe:attachShadeWindow:absent",
                "attachShadeWindow: no visible shade area in ${ShadeProbe.describe(window)}"
            )
            false
        } else {
            attachShadeCluster(cluster, battery = null, source = "window")
        }
    } catch (t: Throwable) {
        L.e("shade window attach: ${t.javaClass.simpleName}: ${t.message}")
        false
    }

    /**
     * Applies the owner after a QS/shade attach. Before panel state arrives the attach route is the
     * fallback; afterwards the panel owner wins so a late attach cannot flip the visible copy.
     */
    private fun markSurface(slotName: String) {
        // Before the panel reports a state, the attach route is the best information. After that,
        // the panel state is authoritative: a late QS/shade attach must not flip the visible copy.
        if (!panelStateKnown) {
            shadeOpen = slotName == "shade header"
        }
        showSurfaceOwner()
        // A late attach is the second chance for a cover whose extra was not drawable on the first
        // frame. The view may already be visible now; re-check once.
        if (mainCoverPending) armMainCoverGuard(force = true)
        val effective = if (panelStateKnown) panelOwner ?: "none" else slotName
        if (lastSurfaceOwner != effective) {
            lastSurfaceOwner = effective
            L.i("shade surface owner -> $effective (attach=$slotName, panelState=$panelStateKnown)")
        }
    }

    /** Panel callback entry: "qs header", "shade header", or null when collapsed. */
    fun setPanelOwner(owner: String?) {
        val previous = panelOwner
        val wasKnown = panelStateKnown
        val changed = !wasKnown || previous != owner
        panelStateKnown = true
        panelOwner = owner
        shadeOpen = owner == "shade header"
        if (changed) {
            // A new expansion owns the seat again: an old collapse slide must never run into it.
            // On a collapse, onPanelCollapsed may already have started the slide; cancelling it here
            // restored the HUD x before the first frame and removed the measured hand-off.
            if (owner != null) {
                cancelMainHandoff()
                drawOrder.onExpand()
            }
            // Collapse hand-off: start the slide while the main copy is still hidden, then lock the
            // outgoing copy out and reveal the main copy opaque. Doing it in that order removes the
            // measured inversion (main at the HUD x, then the old copy at the shade x again).
            if (owner == null && wasKnown && previous != null && handoffAnimator == null) {
                collapseHandoff(previous)
            }
            showSurfaceOwner()
            // HUD -> QS/shade: the panel is now known. Never hide the main copy until this owner's
            // copy is actually drawable; DuoHook may have requested the hide before this callback.
            if (owner != null && surfaceEnabled(owner) && mainBarVisible) {
                mainCoverGuardAttempts = 0
                mainCoverPending = true
                armMainCoverGuard(force = true)
            }
            L.i("panel owner -> ${owner ?: "collapsed"}")
        }
    }

    /**
     * Panel collapse entry (SystemUI's coverage hook). Runs before [setPanelOwner] when that callback
     * comes second, so the main copy is positioned on the outgoing x before it is revealed. Both
     * callbacks are idempotent: once the main copy is visible the slide is not restarted.
     */
    fun onPanelCollapsed() {
        val owner = activeSurfaceOwner()
        if (owner != null) {
            collapseHandoff(owner)
        } else {
            mainCoverPending = false
            setMainBarVisible(true, immediateReveal = true)
        }
        showSurfaceOwner()
    }

    /**
     * The single collapse order: position the hidden main copy on the outgoing copy's last x, latch
     * the outgoing copy out, then reveal the main copy opaque in the same frame. If the main copy
     * cannot draw, the outgoing copy is kept (fail silent, never a blackout). If it is already
     * visible, the slide is skipped: a second slide would be the measured backwards jump.
     */
    private fun collapseHandoff(owner: String) {
        mainCoverPending = false
        if (!surfaceEnabled(owner)) {
            if (mainCopyReady()) setMainBarVisible(true, immediateReveal = true)
            return
        }
        if (mainCopyReady()) {
            if (!mainBarVisible) startMainHandoff(owner)
            lockOutgoingSurface(owner)
            setMainBarVisible(true, immediateReveal = true)
            return
        }
        // No drawable main copy: keep the outgoing copy and clear any old latch.
        drawOrder.onCollapse(owner, mainReady = false)
        L.i("panel reveal: keeping the outgoing extra (main copy not drawable)")
        if (!outgoingSurfaceVisible()) {
            L.w("Duo box: 0x0 main on collapse - falling back to Canvas")
            ensureMainFallback()
        }
        // Ensure a fallback/canvas during collapse is visible once the panel is closed.
        if (!panelIsOpen()) setMainBarVisible(true, immediateReveal = true)
    }

    /**
     * Hard-hides the outgoing QS/shade copy and latches it out until the next panel expansion. Alpha
     * zero, not a fade: the owner capture showed the outgoing copy still drawing at the shade x for
     * ~32 ms after the main copy had reached the HUD x. The latch also ignores late redraws.
     */
    private fun lockOutgoingSurface(name: String) {
        val already = drawOrder.isLocked(name)
        if (!drawOrder.onCollapse(name, mainReady = true)) return
        val slot = extras.firstOrNull { it.name == name }
        if (slot != null) {
            slot.alphaRequest = null
            for (view in listOfNotNull(slot.element?.ui, slot.indicators?.ui)) {
                try {
                    view.animate().cancel()
                    view.alpha = 0f
                } catch (t: Throwable) {
                    L.w("panel reveal: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
            slot.element?.setRenderActive(false)
            slot.indicators?.setRenderActive(false)
        }
        if (!already) L.i("panel reveal: outgoing $name locked out")
    }

    /**
     * True when the main copy can actually draw, even while it is still hidden for the hand-off.
     * This is the structural gate for locking the outgoing copy; alpha is deliberately not part of
     * it because the main copy is revealed only after the slide has been positioned.
     */
    private fun mainCopyReady(): Boolean {
        if (element?.isReady != true) return false
        return mainViews().any { hasBox(it) }
    }

    /**
     * Carries the main copy from the outgoing QS/shade copy's last x, so the collapse does not snap
     * sideways. Only a bounded translation is applied: y stays host-driven, and the view is neither
     * moved, resized nor reparented. A skip is logged once and leaves the main copy untouched.
     */
    private fun startMainHandoff(owner: String) {
        val main = element?.ui ?: return
        val slot = extras.firstOrNull { it.name == owner } ?: return
        val extraAlpha = listOfNotNull(slot.element?.ui, slot.indicators?.ui)
            .maxOfOrNull { it.alpha } ?: 0f
        val stable = mainHandoffBaseStable(main) && extraAlpha > PanelHandoff.EPSILON
        val maxPx = PanelHandoff.HANDOFF_MAX_DP * context.resources.displayMetrics.density
        when (val decision = PanelHandoff.horizontalHandoff(
            extraCenterX = centerXOnScreen(slot.element?.ui ?: slot.indicators?.ui),
            mainCenterX = centerXOnScreen(main),
            baseStable = stable,
            maxPx = maxPx
        )) {
            is PanelHandoff.Handoff.Skip ->
                L.i("panel handoff: no x slide (${decision.reason})")
            is PanelHandoff.Handoff.Slide -> slideMainX(main, decision)
        }
    }

    /** True only when the main view's resting translation can be captured and restored safely. */
    private fun mainHandoffBaseStable(main: View): Boolean = try {
        element?.isReady == true &&
            main.isAttachedToWindow && main.width > 0 && main.height > 0 &&
            main.translationX.isFinite() &&
            handoffAnimator == null &&
            (shakes[main]?.isRunning != true)
    } catch (t: Throwable) {
        L.w("panel handoff base: ${t.javaClass.simpleName}: ${t.message}")
        false
    }

    private fun centerXOnScreen(view: View?): Float? {
        view ?: return null
        return try {
            if (!view.isAttachedToWindow || view.width <= 0) return null
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            location[0] + view.width / 2f
        } catch (t: Throwable) {
            L.w("panel handoff centre: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    private fun slideMainX(main: View, slide: PanelHandoff.Handoff.Slide) {
        val base = main.translationX
        if (!base.isFinite()) {
            L.i("panel handoff: no x slide (base-not-finite)")
            return
        }
        cancelMainHandoff()
        handoffView = main
        handoffBaseX = base
        // Start where the outgoing copy was, then settle on the geometry-owned seat.
        main.translationX = base + slide.deltaPx
        val animator = ObjectAnimator.ofFloat(main, "translationX", base + slide.deltaPx, base).apply {
            duration = slide.durationMs
            interpolator = HANDOFF_X
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // An older animator must not clear a newer slide's base.
                if (handoffAnimator !== animation) return
                handoffAnimator = null
                handoffView = null
                try {
                    main.translationX = handoffBaseX
                } catch (t: Throwable) {
                    L.w("panel handoff end: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
        })
        handoffAnimator = animator
        try {
            animator.start()
            L.i("panel handoff: x slide ${Math.round(slide.deltaPx)} px over ${slide.durationMs} ms")
        } catch (t: Throwable) {
            handoffAnimator = null
            handoffView = null
            main.translationX = base
            L.w("panel handoff start: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Stops the slide and puts the main copy back on its geometry-owned seat. */
    private fun cancelMainHandoff() {
        val animator = handoffAnimator
        val view = handoffView
        val base = handoffBaseX
        handoffAnimator = null
        handoffView = null
        if (animator != null) {
            try {
                animator.cancel()
            } catch (t: Throwable) {
                L.w("panel handoff cancel: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        if (view != null) {
            try {
                view.translationX = base
            } catch (t: Throwable) {
                L.w("panel handoff restore: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /**
     * Hides/shows the main status-bar copy and its split indicator; extras are untouched.
     *
     * Hiding is a short panel fade (≤100 ms) so the copy does not vanish a frame before the panel
     * covers it. A panel hand-off reveals immediately: the measured QS/shade → collapsed blackout was
     * the 50 ms delay plus the 320 ms alpha-in, while the outgoing extra was already hidden. The
     * initial reveal still uses the ROM's own motion (320 ms, `Interpolators.ALPHA_IN`, 50 ms delay).
     */
    fun setMainBarVisible(visible: Boolean, immediateReveal: Boolean = false) {
        if (!visible && mainViews().none { hasBox(it) } && mainViews().isNotEmpty()) {
            L.w("setMainBarVisible: main not drawable - restoring stock icons")
            hider.restore()
            return
        }
        // HUD -> QS/shade: the incoming copy is revealed first. The main copy is only faded out once
        // the guard below has seen that copy draw; a missing extra keeps the main visible (fail silent)
        // instead of opening a one-frame hole.
        if (!visible && mainBarVisible && mainViews().isNotEmpty() && panelCoverExpected()) {
            // An incoming copy that is already opaque needs no delay. A copy that is merely attached
            // or still fading in buys the one-frame hold (and a missing one keeps the main, fail
            // silent). A new expansion owns the seat again, so an old collapse latch is cleared first.
            val owner = activeSurfaceOwner()
            drawOrder.onExpand()
            if (owner != null) makeSurfaceOpaque(owner)
            if (PanelHandoff.coverOrder(
                    extraCopyDrawable(owner), extraCopyAlpha(owner), waitedFrames = 0
                ) == PanelHandoff.SwapOrder.SWAP
            ) {
                mainCoverPending = false
                applyMainBarVisible(false, immediateReveal = false)
                return
            }
            mainCoverGuardAttempts = 0
            mainCoverPending = true
            showSurfaceOwner()
            armMainCoverGuard()
            return
        }
        if (visible) mainCoverPending = false
        applyMainBarVisible(visible, immediateReveal)
    }

    private fun applyMainBarVisible(visible: Boolean, immediateReveal: Boolean) {
        if (visible == mainBarVisible && !(visible && immediateReveal)) return
        mainBarVisible = visible
        val views = mainViews()
        if (views.isEmpty()) return
        for (view in views) {
            when (val change = PanelHandoff.main(visible, immediateReveal, view.alpha)) {
                is PanelHandoff.Change.Keep -> Unit
                is PanelHandoff.Change.Set -> {
                    view.animate().cancel()
                    view.alpha = change.alpha
                }
                is PanelHandoff.Change.Fade -> {
                    view.animate().cancel()
                    view.animate()
                        .alpha(change.alpha)
                        .setDuration(change.durationMs)
                        .setStartDelay(change.startDelayMs)
                        .setInterpolator(MAIN_ALPHA_IN)
                        .start()
                }
            }
        }
        // A hidden main copy must not keep a looping Rive renderer alive. It is re-armed the moment
        // the copy is revealed again; writing snapshots while hidden still keeps the view model current.
        element?.setRenderActive(visible)
        indicators?.setRenderActive(visible)
    }

    private fun mainViews(): List<View> = listOfNotNull(element?.ui, indicators?.ui)

    /** The surface Duo is expected to be drawing in, or null while the panel is collapsed. */
    private fun activeSurfaceOwner(): String? = when {
        panelStateKnown -> panelOwner
        shadeOpen -> "shade header"
        else -> null
    }

    private fun panelIsOpen(): Boolean = activeSurfaceOwner() != null

    /**
     * Whether a panel expansion owns the current transition. Before the panel callback arrives, any
     * enabled header surface is reason to hold the main copy for one frame rather than hide it blind.
     */
    private fun panelCoverExpected(): Boolean {
        val owner = activeSurfaceOwner()
        if (owner != null) return surfaceEnabled(owner)
        return settings.showInQs || settings.showInShade
    }

    /**
     * True only when the view is attached, shown, laid out and actually has ink to draw. This is the
     * structural proof the one-frame guard uses; it never touches size, parent or renderer state.
     */
    private fun copyDrawable(view: View): Boolean = try {
        hasDrawableBox(view)
    } catch (t: Throwable) {
        L.w("draw guard: ${t.javaClass.simpleName}: ${t.message}")
        false
    }

    private fun mainCopyDrawable(): Boolean {
        if (element?.isReady != true) return false
        return mainViews().any { copyDrawable(it) }
    }

    private fun extraCopyDrawable(name: String?): Boolean {
        name ?: return false
        if (!surfaceEnabled(name)) return false
        val slot = extras.firstOrNull { it.name == name } ?: return false
        if (slot.element?.isReady != true) return false
        return listOfNotNull(slot.element?.ui, slot.indicators?.ui).any { copyDrawable(it) }
    }

    /** Highest alpha of an extra's ring and indicators; 0 when the slot is absent. */
    private fun extraCopyAlpha(name: String?): Float {
        name ?: return 0f
        val slot = extras.firstOrNull { it.name == name } ?: return 0f
        return listOfNotNull(slot.element?.ui, slot.indicators?.ui)
            .maxOfOrNull { it.alpha } ?: 0f
    }

    /**
     * Makes the incoming panel copy opaque in this frame. The main copy may only be hidden once the
     * copy underneath is fully drawn; an attached copy that is still fading in showed a 60 % frame in
     * the owner capture. A locked outgoing copy is left at zero: the latch owns it.
     */
    private fun makeSurfaceOpaque(name: String) {
        if (drawOrder.isLocked(name) || !surfaceEnabled(name)) return
        val slot = extras.firstOrNull { it.name == name } ?: return
        slot.alphaRequest = null
        for (view in listOfNotNull(slot.element?.ui, slot.indicators?.ui)) {
            try {
                view.animate().cancel()
                view.alpha = 1f
            } catch (t: Throwable) {
                L.w("panel cover opaque: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /** A currently visible outgoing copy that must not vanish before the main copy draws. */
    private fun outgoingSurfaceVisible(): Boolean = extras.any { slot ->
        surfaceEnabled(slot.name) &&
            listOfNotNull(slot.element?.ui, slot.indicators?.ui)
                .any { it.alpha > PanelHandoff.EPSILON }
    }

    /**
     * Runs [action] once on the next frame: a pre-draw listener sees the frame's final alpha, and a
     * single post-on-animation fallback covers a view whose traversal does not happen. There is no
     * retry loop; the caller decides whether a missed frame is a hold or a fail-silent stop.
     */
    private fun onNextFrame(view: View, action: () -> Unit) {
        var fired = false
        var listener: ViewTreeObserver.OnPreDrawListener? = null
        fun finish() {
            if (fired) return
            fired = true
            val pending = listener
            listener = null
            if (pending != null) {
                try {
                    val observer = view.viewTreeObserver
                    if (observer.isAlive) observer.removeOnPreDrawListener(pending)
                } catch (_: Throwable) {
                }
            }
            action()
        }
        try {
            val observer = view.viewTreeObserver
            if (observer.isAlive) {
                val pending = object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        finish()
                        return true
                    }
                }
                listener = pending
                observer.addOnPreDrawListener(pending)
            }
            if (view.isAttachedToWindow) view.postOnAnimation { finish() } else view.post { finish() }
        } catch (t: Throwable) {
            L.w("one-frame guard: ${t.javaClass.simpleName}: ${t.message}")
            view.post { finish() }
        }
    }

    /**
     * HUD -> QS/shade guard: hide the main copy only after the owner's copy has drawn. The incoming
     * copy may settle over more than one frame, so the check is retried for a bounded window; when it
     * never becomes drawable the main stays visible and the pending cover is cleared (fail silent).
     */
    private fun armMainCoverGuard(force: Boolean = false) {
        if (!mainCoverPending) return
        val view = mainViews().firstOrNull() ?: return
        if (!force && mainCoverGuardView === view) return
        mainCoverGuardView = view
        onNextFrame(view) {
            if (mainCoverGuardView === view) mainCoverGuardView = null
            if (!mainCoverPending) return@onNextFrame
            val owner = activeSurfaceOwner()
            if (owner != null) makeSurfaceOpaque(owner)
            when (PanelHandoff.coverOrder(
                    extraCopyDrawable(owner), extraCopyAlpha(owner), waitedFrames = 1
                )) {
                PanelHandoff.SwapOrder.SWAP -> {
                    mainCoverGuardAttempts = 0
                    mainCoverPending = false
                    applyMainBarVisible(false, immediateReveal = false)
                    L.i("panel cover: main hidden after the incoming copy drew")
                }
                else -> {
                    if (mainCoverGuardAttempts < HANDOFF_GUARD_MAX_FRAMES) {
                        mainCoverGuardAttempts++
                        armMainCoverGuard(force = true)
                    } else {
                        mainCoverGuardAttempts = 0
                        mainCoverPending = false
                        L.i("panel cover: keeping the main copy after $HANDOFF_GUARD_MAX_FRAMES frames")
                    }
                }
            }
        }
    }

    /**
     * QS/shade -> HUD guard: the outgoing extra is held while the main copy has not drawn. The check
     * is retried for a bounded window; after that the extra is kept (fail silent).
     */
    private fun armMainRevealGuard() {
        val view = mainViews().firstOrNull() ?: return
        if (mainRevealGuardView === view) return
        mainRevealGuardView = view
        onNextFrame(view) {
            if (mainRevealGuardView === view) mainRevealGuardView = null
            if (panelIsOpen()) {
                mainRevealGuardAttempts = 0
                return@onNextFrame
            }
            val waited = if (mainRevealGuardAttempts == 0) 0 else 1
            when (PanelHandoff.swapOrder(mainCopyDrawable(), waitedFrames = waited)) {
                PanelHandoff.SwapOrder.SWAP -> {
                    mainRevealGuardAttempts = 0
                    showSurfaceOwner()
                }
                else -> {
                    if (mainRevealGuardAttempts < HANDOFF_GUARD_MAX_FRAMES) {
                        mainRevealGuardAttempts++
                        armMainRevealGuard()
                    } else {
                        mainRevealGuardAttempts = 0
                        if (outgoingSurfaceVisible()) {
                            L.i("panel reveal: keeping the outgoing extra (main copy not drawable after $HANDOFF_GUARD_MAX_FRAMES frames)")
                        } else if (element?.ui?.let { !hasDrawableBox(it) } == true) {
                            L.w("Duo box: 0x0 main on reveal - falling back to Canvas")
                            ensureMainFallback()
                        } else {
                            L.i("panel reveal: keeping the outgoing extra (main copy not drawable after $HANDOFF_GUARD_MAX_FRAMES frames)")
                        }
                    }
                }
            }
        }
    }

    /** Whether the user allows Duo in one of the optional header surfaces. */
    private fun surfaceEnabled(name: String): Boolean = when (name) {
        "qs header" -> settings.showInQs
        "shade header" -> settings.showInShade
        else -> true
    }

    /**
     * The copy the panel is expected to show. While a cover is pending and the owner callback has not
     * arrived yet, the last attached surface is the best prediction; showing it for that frame keeps
     * the call from fading the incoming copy to zero just before the owner names it.
     */
    private fun displaySurfaceOwner(): String? = when {
        panelStateKnown -> panelOwner ?: when {
            !mainCoverPending -> null
            shadeOpen -> "shade header"
            else -> "qs header"
        }
        shadeOpen -> "shade header"
        else -> "qs header"
    }

    /** Reveals exactly the current owner (QS or shade); the other copy is always hidden. */
    fun showSurfaceOwner() {
        val owner = displaySurfaceOwner()
        val qs = owner == "qs header" && settings.showInQs
        val shade = owner == "shade header" && settings.showInShade
        // QS/shade -> HUD: the main copy is already opaque (setMainBarVisible(true, immediateReveal)),
        // but if it has no drawable view yet the outgoing copy is held for one frame instead of being
        // faded out over an empty seat.
        if (!panelIsOpen() && !mainCopyDrawable() && outgoingSurfaceVisible()) {
            armMainRevealGuard()
            return
        }
        val qsTarget = if (qs) 1f else 0f
        val shadeTarget = if (shade) 1f else 0f
        // Apply the incoming copy first. `anotherCopyVisible` reads the current alphas, so hiding the
        // outgoing copy first turned the QS -> shade swap into a hard cut at two different positions;
        // the reverse direction faded, which made the six transition directions asymmetric.
        if (shadeTarget > qsTarget) {
            setSurfaceAlpha("shade header", shadeTarget)
            setSurfaceAlpha("qs header", qsTarget)
        } else {
            setSurfaceAlpha("qs header", qsTarget)
            setSurfaceAlpha("shade header", shadeTarget)
        }
    }

    /**
     * Applies a live QS/shade enable toggle. Disabling hides our element and restores the stock
     * header icons in place; enabling re-hides them. No live Duo is removed or reparented.
     */
    private fun applySurfaceSettings() {
        for (slot in extras) {
            try {
                if (!surfaceEnabled(slot.name)) {
                    slot.alphaRequest = null
                    slot.element?.ui?.animate()?.cancel()
                    slot.indicators?.ui?.animate()?.cancel()
                    slot.element?.ui?.alpha = 0f
                    slot.indicators?.ui?.alpha = 0f
                    slot.element?.setRenderActive(false)
                    slot.indicators?.setRenderActive(false)
                    slot.stock.restore()
                } else {
                    val target = slot.container ?: continue
                    hideSlotStock(slot, target, slot.element?.ui)
                }
            } catch (t: Throwable) {
                L.w("${slot.name} surface setting: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        showSurfaceOwner()
    }

    /**
     * Applies the same alpha to the ring and its split indicator view for one extra surface.
     *
     * A fade is used only while another copy can bridge the swap; a disabled surface and the first
     * attach with nothing visible are hard-set, so the stock header is never left behind a fade.
     */
    private fun setSurfaceAlpha(name: String, alpha: Float) {
        if (drawOrder.isLocked(name)) {
            // The collapse latch owns this copy: hard-zero, never fade, until the next expansion.
            for (slot in extras) {
                if (slot.name != name) continue
                slot.alphaRequest = null
                for (view in listOfNotNull(slot.element?.ui, slot.indicators?.ui)) {
                    try {
                        view.animate().cancel()
                        view.alpha = 0f
                    } catch (t: Throwable) {
                        L.w("${slot.name} latch: ${t.javaClass.simpleName}: ${t.message}")
                    }
                }
                slot.element?.setRenderActive(false)
                slot.indicators?.setRenderActive(false)
            }
            return
        }
        val enabled = surfaceEnabled(name)
        val target = if (enabled) alpha else 0f
        val coexist = anotherCopyVisible(name)
        for (slot in extras) {
            if (slot.name != name) continue
            val views = listOfNotNull(slot.element?.ui, slot.indicators?.ui)
            if (views.isEmpty()) continue
            val current = views.maxOf { it.alpha }
            when (val change = PanelHandoff.extra(target, enabled, current, slot.alphaRequest, coexist)) {
                is PanelHandoff.Change.Keep -> Unit
                is PanelHandoff.Change.Set -> {
                    slot.alphaRequest = null
                    for (view in views) {
                        view.animate().cancel()
                        view.alpha = change.alpha
                    }
                }
                is PanelHandoff.Change.Fade -> {
                    slot.alphaRequest = change.alpha
                    for (view in views) {
                        view.animate().cancel()
                        view.animate()
                            .alpha(change.alpha)
                            .setDuration(change.durationMs)
                            .withEndAction {
                                if (slot.alphaRequest == change.alpha) slot.alphaRequest = null
                            }
                            .start()
                    }
                }
            }
            // Keep the renderer in step with the copy's target: a zero-alpha QS/shade copy that keeps
            // restarting on every status change is the same drain as an off-screen main bar.
            val renderActive = target > PanelHandoff.EPSILON
            slot.element?.setRenderActive(renderActive)
            slot.indicators?.setRenderActive(renderActive)
        }
    }

    /**
     * True when a copy other than [name] is visible enough to cross-fade with. The main bar counts even
     * while it is fading out (its view alpha is still above zero), which covers panel expansion.
     */
    private fun anotherCopyVisible(name: String): Boolean {
        val mainAlpha = listOfNotNull(element?.ui, indicators?.ui)
            .maxOfOrNull { it.alpha } ?: 0f
        if (mainAlpha > PanelHandoff.EPSILON) return true
        return extras.any { slot ->
            slot.name != name && listOfNotNull(slot.element?.ui, slot.indicators?.ui)
                .any { it.alpha > PanelHandoff.EPSILON }
        }
    }

    /**
     * OnePlus/QS route: attaches Duo to the cluster a vendor hook handed over, with the battery that
     * hook read (the split header keeps it beside the cluster, not inside it). The cluster must have
     * been laid out; at 0x0 a bounded retry runs instead of hiding anything.
     */
    fun attachShadeCluster(cluster: ViewGroup, battery: View?, source: String): Boolean {
        return try {
        // Never remove/reparent a live Duo from a layout/hook callback; leave stock and log instead.
        // The measured CPH2747 squeezes the QS cluster to 9x63 inside the wide header; draw in the
        // header instead so the ring and the Wi-Fi/cell dots are not squeezed into 9 px. The slot name
        // is chosen from the *original* cluster, before that normalization: 9x63 is the QS surface and
        // 386x101 is the shade, so the two routes can never consume one another's slot.
        // The measured QS cluster is wide too (297x56), so dimensions alone are not enough: the
        // shade surface is the one under `header_container`. Everything else (the QS panel and the
        // vendor simple header) owns "qs header", so a QS owner can no longer block the shade.
        val slotName = if (isShadeSurface(cluster, source)) "shade header" else "qs header"
        val target = finder.usableShadeHost(cluster)
        logOnce.once("shadeRoute:$slotName:${System.identityHashCode(target)}") {
            L.i("shade route slot=$slotName cluster=${cluster.width}x${cluster.height} " +
                "target=${target.javaClass.name}[${target.width}x${target.height}] source=$source")
        }
        if (target === host) return true
        // One owner per host: QS and shade can both normalize to the same header. A second view in
        // that header is the duplicate TextureView this change set exists to remove.
        dropDetachedSlotsFor(target)
        liveSlotIn(target)?.let { owner ->
            logOnce.once("shadeOwner:$slotName:${System.identityHashCode(target)}") {
                L.i("shade host already owned by ${owner.name} " +
                    "at ${target.javaClass.simpleName}[${target.width}x${target.height}] - " +
                    "not adding a second Duo")
            }
            if (battery != null) owner.stockBattery = battery
            clearShadeRetry()
            markSurface(owner.name)
            return true
        }
        // A live same-name slot wins even when its container is not this target: adopt its live parent,
        // never create a second element or re-add the same view.
        liveSlotFor(slotName, cluster)?.let { slot ->
            val liveParent = slot.element?.ui?.parent as? ViewGroup
            if (liveParent != null) slot.container = liveParent
            if (battery != null) slot.stockBattery = battery
            applyExtraLayout(slot)
            syncExtraIndicators(slot)
            clearShadeRetry()
            markSurface(slot.name)
            logOnce.once("reuse:$slotName:${System.identityHashCode(liveParent)}") {
                L.i("$slotName reuse attached slot parent=${liveParent?.javaClass?.simpleName}")
            }
            return true
        }
        // A live same-name slot wins. A detached one is only bookkeeping; it may be discarded before
        // this safe rebuild, but a live Duo is never removed or reparented. Still update the owner:
        // the QS slot can live in an overlay parent while this route's target is the stock strip, so
        // liveSlotIn(target) above does not always find it.
        if (!ensureSlotAvailable(slotName, cluster)) {
            markSurface(slotName)
            return true
        }
        when {
            element == null -> {
                ShadeProbe.log("shadeProbe $source: main bar not attached yet - waiting")
                scheduleShadeRetryForCluster(target, battery, source)
                false
            }
            // The host can be a wider ancestor while the original cluster is still 0x0. Keep waiting on
            // the cluster itself, or Duo would be attached (and the stock battery hidden) before layout.
            !shadeClusterReady(cluster.width, cluster.visibility, cluster.isAttachedToWindow, cluster.isShown) -> {
                ShadeProbe.log(
                    "shadeProbe $source: cluster not laid out " +
                        "(${ShadeProbe.describe(cluster)}) - waiting"
                )
                scheduleShadeRetryForCluster(target, battery, source)
                false
            }
            !containerUsable(target) -> {
                ShadeProbe.log("shadeProbe $source: shade target not usable - waiting")
                scheduleShadeRetryForCluster(target, battery, source)
                false
            }
            else -> attachShadeSlot(target, target, battery, source, slotName)
        }
    } catch (t: Throwable) {
        L.e("shade cluster attach: ${t.javaClass.simpleName}: ${t.message}")
        false
    }
    }

    /** Generic header fallback: the controller's header and the wide shade cluster found in its window. */
    private fun attachGenericShadeCluster(cluster: ViewGroup, header: View): Boolean {
        val target = finder.usableShadeHost(cluster)
        val slotName = if (isShadeSurface(cluster, null)) "shade header" else "qs header"
        logOnce.once("shadeRoute:generic:${System.identityHashCode(target)}") {
            L.i("shade route slot=$slotName cluster=${cluster.width}x${cluster.height} " +
                "target=${target.javaClass.name}[${target.width}x${target.height}] source=generic")
        }
        if (target === host) return true
        // A wide QS cluster (297x56) is still the QS surface; only `header_container` owns the shade.
        dropDetachedSlotsFor(target)
        liveSlotIn(target)?.let { markSurface(it.name); return true }
        liveSlotFor(slotName, cluster)?.let { slot ->
            val liveParent = slot.element?.ui?.parent as? ViewGroup
            if (liveParent != null) slot.container = liveParent
            applyExtraLayout(slot)
            syncExtraIndicators(slot)
            markSurface(slot.name)
            logOnce.once("reuse:$slotName:${System.identityHashCode(liveParent)}") {
                L.i("$slotName reuse attached slot parent=${liveParent?.javaClass?.simpleName}")
            }
            return true
        }
        if (!ensureSlotAvailable(slotName, cluster)) {
            markSurface(slotName)
            return true
        }
        return attachShadeSlot(target, header, battery = null, source = null, slotName = slotName)
    }

    /**
     * The shared shade slot attach. [cap] is what the element's height is capped by (the vendor cluster
     * for the OnePlus routes, the header for the generic fallback) and [battery] is passed only when a
     * hook read it outside the cluster; otherwise it is resolved inside [cluster]. [slotName] is the
     * owner this route writes to ("qs header" or "shade header").
     */
    private fun attachShadeSlot(
        cluster: ViewGroup,
        cap: View,
        battery: View?,
        source: String?,
        slotName: String
    ): Boolean {
        if (!surfaceEnabled(slotName)) {
            L.i("$slotName disabled by setting - leaving stock header")
            return true
        }
        if (cluster === host) return true
        if (!shadeClusterReady(cluster.width, cluster.visibility, cluster.isAttachedToWindow, cluster.isShown)) {
            ShadeProbe.log(
                "shadeProbe ${source ?: "shade"}: cluster not laid out " +
                    "(${ShadeProbe.describe(cluster)}) - waiting"
            )
            if (source != null) scheduleShadeRetryForCluster(cluster, battery, source)
            return false
        }
        // One owner per host: a live Duo in this exact cluster wins, whichever slot name it used.
        liveSlotIn(cluster)?.let { owner ->
            if (battery != null) owner.stockBattery = battery
            clearShadeRetry()
            markSurface(owner.name)
            return true
        }
        // A live same-name slot wins even when its container is not this target.
        liveSlotFor(slotName, cluster)?.let { slot ->
            val liveParent = slot.element?.ui?.parent as? ViewGroup
            if (liveParent != null) slot.container = liveParent
            if (battery != null) slot.stockBattery = battery
            applyExtraLayout(slot)
            syncExtraIndicators(slot)
            clearShadeRetry()
            markSurface(slot.name)
            logOnce.once("reuse:$slotName:${System.identityHashCode(liveParent)}") {
                L.i("$slotName reuse attached slot parent=${liveParent?.javaClass?.simpleName}")
            }
            return true
        }
        // A live same-name slot wins. A detached one is only bookkeeping; drop it and rebuild.
        // Update the owner even when the live slot is in an overlay parent rather than this target.
        if (!ensureSlotAvailable(slotName, cluster)) {
            markSurface(slotName)
            return true
        }
        // One size owner: the measured QS header is only 63 high, but Duo keeps the main-bar size
        // (114 at 133%) and overflows the header rather than shrinking to a tiny ring. Cap against
        // the window, not the 63 px stock strip.
        val capView = (cluster.rootView as? View) ?: cap
        val attached = attachExtraView(
            slotName, cluster, capView, center = false, qsCluster = true,
            battery = battery, source = source
        )
        if (attached) {
            clearShadeRetry()
            markSurface(slotName)
        }
        return attached
    }

    /** Attaches into an already-known container (the shade header's icon area). */
    private fun attachExtraView(
        slotName: String,
        target: ViewGroup,
        cap: View? = null,
        center: Boolean = true,
        qsCluster: Boolean = false,
        battery: View? = null,
        source: String? = null
    ): Boolean {
        if (!surfaceEnabled(slotName)) return true
        // The measured QS chain puts the cluster inside a custom `OplusQuickStatusBarHeader`; a
        // foreign child there measured 0 px wide. Draw in the nearest plain frame instead, while the
        // stock icons are still hidden inside the original container.
        val base = if (geometry.slotBasePx > 0) geometry.slotBasePx else geometry.measuredWidth(target)
        val capView = (cap as? View) ?: target
        val desired = geometry.sidePx(target, capView, base)
        var elementParent = if (finder.isStandardLayout(target)) target
        else finder.findOverlayContainer(target, target.rootView)
        // The QS status strip is 63 px high but Duo keeps its 114 px main-bar size (129 px drawing
        // height). A 63 px parent measured the view 63 px high - the tiny, squashed QS ring. Move
        // just this surface to the nearest taller QS ancestor; it is still hidden with the QS panel.
        // Only move out for a real shortfall. The shade header is 140 px while the main bar is 141 px;
        // a 1 px difference used to select the 688 px QS container, which changed both size and
        // position. A 1 px overflow is harmless and keeps the stock header as the anchor.
        if (target.height > 0 && target.height * 10 < desired * 9) {
            val parent = target.parent as? ViewGroup
            if (parent != null && parent.height >= desired) elementParent = parent
        }
        dropDetachedSlotsFor(target)
        if (elementParent !== target) dropDetachedSlotsFor(elementParent)
        liveSlotIn(target)?.let { markSurface(it.name); return true }
        if (elementParent !== target) {
            liveSlotIn(elementParent)?.let { markSurface(it.name); return true }
        }
        liveSlotFor(slotName, target)?.let { slot ->
            val liveParent = slot.element?.ui?.parent as? ViewGroup
            if (liveParent != null) slot.container = liveParent
            applyExtraLayout(slot)
            syncExtraIndicators(slot)
            markSurface(slot.name)
            logOnce.once("reuse:$slotName:${System.identityHashCode(liveParent)}") {
                L.i("$slotName reuse attached slot parent=${liveParent?.javaClass?.simpleName}")
            }
            return true
        }
        if (!ensureSlotAvailable(slotName, target)) {
            markSurface(slotName)
            return true
        }
        if (element == null) {
            // The shade header and the QS cluster are built before the main bar binds; keep the bounded
            // retry alive on every route instead of giving up on the first early callback.
            if (source != null) scheduleShadeRetryForCluster(target, battery, source)
            else scheduleShadeRetry(cap ?: target, null)
            return false
        }
        return try {
            val stage = guard.stage()
            if (stage == DuoGuard.OFF) return false
            if (elementParent === host) return true
            if (elementParent.height <= 0 || target.height <= 0) {
                if (source != null) scheduleShadeRetryForCluster(target, battery, source)
                else scheduleShadeRetry(cap ?: target, null)
                return false
            }
            attachInto(
                slotName, elementParent, cap, center, elementRoot = elementParent, stage = stage,
                logClass = target.javaClass.simpleName, qsCluster = qsCluster,
                battery = battery, source = source, stockTarget = target
            )
        } catch (t: Throwable) {
            L.e("$slotName attach: ${t.javaClass.simpleName}: ${t.message}")
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
        logClass: String,
        qsCluster: Boolean = false,
        battery: View? = null,
        source: String? = null,
        stockTarget: ViewGroup? = null
    ): Boolean {
        // Reuse a live same-named slot instead of creating a second element/re-adding the view.
        liveSlotFor(name, target)?.let { slot ->
            val liveParent = slot.element?.ui?.parent as? ViewGroup
            if (liveParent != null) slot.container = liveParent
            if (battery != null) slot.stockBattery = battery
            applyExtraLayout(slot)
            syncExtraIndicators(slot)
            logOnce.once("reuse:$name:${System.identityHashCode(liveParent)}") {
                L.i("$name reuse attached slot parent=${liveParent?.javaClass?.simpleName}")
            }
            return true
        }
        val stock = stockTarget ?: target
        if (!containerUsable(target) || !containerUsable(stock)) {
            ShadeProbe.log(
                "shadeProbe attach: $name target not usable " +
                    "(${ShadeProbe.describe(target)} / stock ${ShadeProbe.describe(stock)}) - waiting"
            )
            return false
        }
        val slot = ExtraBar(name, center)
        val candidate = createElement(elementRoot, stage, ringPart())
        slot.container = target
        slot.bar = cap ?: stock
        slot.qsCluster = qsCluster
        if (qsCluster) {
            slot.stockStatusIcons = finder.findShadeStatusIcons(stock)
            slot.stockBattery = battery ?: finder.findShadeBattery(stock)
            ShadeProbe.log(
                "shadeProbe container: ${ShadeProbe.describe(target)} stock=${ShadeProbe.describe(stock)} " +
                    "statusIcons=${ShadeProbe.describe(slot.stockStatusIcons)} " +
                    "battery=${ShadeProbe.describe(slot.stockBattery)} " +
                    "source=${source ?: "generic"}"
            )
        }
        // One size for every bar: reuse the width pinned for the main bar at attach, so the lock screen
        // and the shade header cannot draw the ring at a different size than the home bar (reported: "the
        // icon is bigger on the lock screen"). Falls back to this bar's own measurement only when the main
        // bar never captured one. The bar-height cap in [SlotGeometry.sidePx] still applies per bar.
        slot.basePx = if (geometry.slotBasePx > 0) geometry.slotBasePx else geometry.measuredWidth(stock)
        slot.element = candidate
        extras.add(slot)
        try {
            allowOverflow(target)
            // A 0-height cap is the boot-time header; fall back to the stock container's height so a
            // 114 px element is never laid out inside the measured 63 px QS header.
            val capView = slot.bar?.takeIf { it.height > 0 } ?: stock
            val side = geometry.extraSidePx(target, capView, slot.basePx)
            candidate.ui.layoutParams = layoutParamsFor(target, side)
            ensureInjectedViewId(candidate.ui, target)
            target.addView(candidate.ui)
            applyExtraLayout(slot)
            syncExtraIndicators(slot)
            candidate.onReady {
                if (slot.element !== candidate) return@onReady
                // Wait for a real measured box before hiding anything. A 0-width child in a custom
                // header was the measured QS failure; leaving stock is safer than an empty slot.
                candidate.ui.post {
                    if (slot.element !== candidate || !candidate.ui.isAttachedToWindow) return@post
                    val width = candidate.ui.width
                    val height = candidate.ui.height
                    L.i(
                        "$name layout target=${target.javaClass.name}[${target.width}x${target.height}] " +
                            "stock=${stock.javaClass.simpleName}[${stock.width}x${stock.height}] " +
                            "view=${width}x$height side=$side parent=" +
                            "${candidate.ui.parent?.javaClass?.simpleName ?: "none"}"
                    )
                    if (width <= 0 || height <= 0) {
                        L.w("$name element measured ${width}x$height - leaving stock")
                        runCatching { (candidate.ui.parent as? ViewGroup)?.removeView(candidate.ui) }
                        runCatching { candidate.teardown() }
                        slot.stock.restore()
                        extras.remove(slot)
                        return@post
                    }
                    // The ready callback arrives from the Rive instance poll, i.e. outside attachExtraView's try.
                    // Guard it here so a hiding failure in an optional bar can never propagate into SystemUI.
                    try {
                        hideSlotStock(slot, stock, candidate.ui)
                        candidate.reveal(settings.revealMs)
                        // A cover that was held because this copy was not drawable gets its second
                        // check once the copy is live. Still one frame, still no retry loop.
                        if (mainCoverPending) armMainCoverGuard(force = true)
                        // Hiding the stock battery keeps its INVISIBLE frame, but the cluster may still have
                        // moved; re-anchor after the hide pass so the element sits on the battery's end.
                        if (qsCluster) {
                            applyExtraLayout(slot)
                            syncExtraIndicators(slot)
                        }
                        ShadeProbe.log(
                            "shadeProbe attach qs=$qsCluster source=${source ?: "generic"}: $name " +
                                "container=${target.javaClass.simpleName} side=${side}px"
                        )
                        val sourceText = source?.let { " source=$it" } ?: ""
                        L.i("Duo injected into $name ($logClass, ${side}px$sourceText) - FR-03b")
                    } catch (t: Throwable) {
                        L.w("$name ready: ${t.javaClass.simpleName}: ${t.message}")
                    }
                }
            }
            candidate.onFailed {
                // Optional bar: drop the element and leave its stock icons alone.
                try {
                    L.w("$name element did not bind - leaving its stock icons")
                    runCatching { target.removeView(candidate.ui) }
                    runCatching { candidate.teardown() }
                    slot.stock.restore()
                    extras.remove(slot)
                } catch (t: Throwable) {
                    L.w("$name failed: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
            return true
        } catch (t: Throwable) {
            // The slot must never outlive a failed injection: it would hide the stock icons with no Duo
            // drawing over them. Roll it back completely.
            L.e("$name attach body: ${t.javaClass.simpleName}: ${t.message}")
            runCatching { target.removeView(candidate.ui) }
            dropSlot(slot)
            return false
        }
    }

    /**
     * The container decides the LayoutParams type: the strips are LinearLayouts, the shade header is not.
     * Height is taller than [side] so the percentage can draw above the ring without moving the ring.
     */
    /** The ring's layout: full drawing size, with the strip keeping only the edge-spacing setting. */
    private fun layoutParamsFor(container: ViewGroup, side: Int): ViewGroup.LayoutParams =
        layoutParamsFor(
            container,
            side,
            RingGeometry.elementHeightPx(side),
            edgeInsetPx(side, settings.edgePadding)
        )

    /**
     * [inset] is the start margin, zero or negative. 0 keeps [width] in the strip. A negative margin
     * hands that much back, so icons Duo does not replace (sound, vibrate, alarm) can sit closer to
     * the screen edge. The icon cluster passes the full negative width and reserves nothing.
     */
    private fun layoutParamsFor(
        container: ViewGroup,
        width: Int,
        height: Int,
        inset: Int
    ): ViewGroup.LayoutParams {
        // Ask the container for its own params type. This is what keeps a ConstraintLayout parent (the
        // measured QS cluster) from being handed a base ViewGroup.LayoutParams it casts and rejects.
        val lp = try {
            container.generateLayoutParams(null as AttributeSet?).apply {
                this.width = width
                this.height = height
            }
        } catch (_: Throwable) {
            when (container) {
                is LinearLayout -> LinearLayout.LayoutParams(width, height)
                is FrameLayout -> FrameLayout.LayoutParams(width, height)
                else -> ViewGroup.LayoutParams(width, height)
            }
        }
        // Fresh params start at 0, so a zero inset is already correct. Only a flowing strip uses it.
        if (inset != 0 && container is LinearLayout && lp is ViewGroup.MarginLayoutParams) {
            if (container.layoutDirection == View.LAYOUT_DIRECTION_RTL) lp.rightMargin = inset
            else lp.leftMargin = inset
        }
        return lp
    }

    /** False when [lp] belongs to a different parent type (a cross-container move kept stale params). */
    private fun paramsFit(container: ViewGroup, lp: ViewGroup.LayoutParams): Boolean = when (container) {
        is LinearLayout -> lp is LinearLayout.LayoutParams
        is FrameLayout -> lp is FrameLayout.LayoutParams
        else -> try {
            lp.javaClass == container.generateLayoutParams(null as AttributeSet?)?.javaClass
        } catch (_: Throwable) {
            true
        }
    }

    /**
     * Gives [view] its drawing size and the strip margin [inset].
     *
     * Re-assigning [View.layoutParams] requests a layout pass, and a layout pass that resizes the Rive
     * view is what used to take System UI down. The size is therefore touched only when it changed.
     * A spacing change updates the margin alone, which does not resize the drawing.
     */
    private fun assignBox(view: View, container: ViewGroup, side: Int, inset: Int) {
        val height = RingGeometry.elementHeightPx(side)
        val lp = view.layoutParams
        if (lp == null || !paramsFit(container, lp) || lp.width != side || lp.height != height) {
            view.layoutParams = layoutParamsFor(container, side, height, inset)
            view.requestLayout()
            return
        }
        if (container !is LinearLayout || lp !is ViewGroup.MarginLayoutParams) return
        val rtl = container.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val leading = if (rtl) lp.rightMargin else lp.leftMargin
        val trailing = if (rtl) lp.leftMargin else lp.rightMargin
        if (leading == inset && trailing == 0) return
        lp.leftMargin = if (rtl) 0 else inset
        lp.rightMargin = if (rtl) inset else 0
        view.layoutParams = lp
    }

    private fun applyExtraLayout(slot: ExtraBar) {
        val target = slot.container ?: return
        val view = slot.element?.ui ?: return
        try {
            val side = geometry.extraSidePx(target, slot.bar, slot.basePx)
            assignBox(view, target, side, ringInset(side))
            val battery = slot.stockBattery
            if (slot.qsCluster && battery != null) {
                // Same size as the main bar (slot.basePx is pinned there), never the cluster's 386 px.
                // The battery stays INVISIBLE with its real frame, so the element can sit on its end.
                anchorOnBattery(view, target, side, settings.offsetX, battery, alignEnd = true)
            } else if (slot.name == "shade header") {
                // No battery to anchor over: seat the shade copy on the physical right like the main
                // bar instead of the flow/left fallback, which put it at the header's start.
                place(view, target, slot.bar, side, settings.offsetX, slot.center, rightSeat = true)
            } else {
                place(view, target, slot.bar, side, settings.offsetX, slot.center)
            }
        } catch (t: Throwable) {
            L.w("${slot.name} layout: ${t.message}")
        }
    }

    /** FR-16: whether the percentage should be drawn — asked by the state monitor on every render. */
    val showPercent: Boolean get() = settings.showPercent

    /** 0 is the original percentage seat; 100 is fully raised. Asked on every render, so it moves live. */
    val percentHeight: Int get() = settings.percentHeight

    /** FR-15b: "auto", "black" or "white" — asked by the state monitor on every render. */
    val iconColor: String get() = settings.iconColor

    /** Whether Airplane mode may take the middle of the ring. Asked on every render. */
    val showAirplane: Boolean get() = settings.showAirplane

    /** How Do Not Disturb is drawn: "off", "middle", or "dots". Asked on every render. */
    val dndMode: String get() = settings.dndMode

    /** Whether Do Not Disturb may take the middle of the ring. Asked on every render. */
    val showDnd: Boolean get() = settings.showDnd

    /** Which cellular line the spheres follow on a dual-SIM phone: "auto", "sim1" or "sim2". */
    val simChoice: String get() = settings.simChoice

    /** When true, airplane mode turns the spheres into Wi-Fi, hiding them if Wi-Fi is off too. */
    val wifiDots: Boolean get() = settings.wifiDots

    /** Whether the Wi-Fi glyph inside the ring is drawn at all. */
    val showWifiGlyph: Boolean get() = settings.showWifiGlyph

    /** Whether the four cellular dots inside the ring are drawn at all. */
    val showCellDots: Boolean get() = settings.showCellDots

    /**
     * FR-08/08b: hides the stock views per the user's choice — everything, or only what Duo replaces.
     * One place so the main bar, the keyguard bar and the shade header can never disagree.
     */
    private fun hideStock(container: ViewGroup, keep: View?, keepLayout: View? = null, owner: StockIconHider = hider) {
        val ours = ourViews()
        if (settings.hideOtherIcons) {
            owner.hideAllExcept(container, keep, keepLayout, ours)
        } else {
            owner.hideReplaced(container, keep, keepLayout, ours, hiddenStockKeys)
        }
    }

    /**
     * Hides the stock views of one extra bar.
     *
     * The QS cluster is not a strip whose every child Duo replaces: it holds the `statusIcons`
     * container and the battery (`batteryRemainingIcon`). Both are hidden explicitly through the
     * slot's own [StockIconHider], so each is restored exactly when the slot is dropped. The battery is
     * kept laid out (`INVISIBLE`) because its end is what the element is anchored to; the icons are
     * `GONE` + 0x0 like the main bar. A tree where neither view resolves falls back to the normal pass
     * rather than covering stock icons the module cannot restore.
     */
    private fun hideSlotStock(slot: ExtraBar, container: ViewGroup, keep: View?) {
        if (!slot.qsCluster) {
            hideStock(container, keep, owner = slot.stock)
            return
        }
        val icons = (slot.stockStatusIcons ?: finder.findShadeStatusIcons(container).also {
            slot.stockStatusIcons = it
        }) as? ViewGroup
        val battery = slot.stockBattery ?: finder.findShadeBattery(container).also {
            slot.stockBattery = it
        }
        // Never hide the whole `statusIcons` container: it carries every icon in the cluster, so a
        // GONE container removes icons the user did not select and cannot be restored per icon.
        // Run the same per-icon pass as the main bar inside it instead.
        if (icons != null && icons !== keep) {
            hideStock(icons, keep, keepLayout = battery, owner = slot.stock)
        }
        if (battery != null && battery !== keep) slot.stock.hideKeepLayout(battery)
        logOnce.once("shadeStock:${System.identityHashCode(container)}") {
            L.i("shade stock hidden statusIcons=${icons != null} battery=${battery != null} " +
                "container=${container.javaClass.simpleName}[${container.width}x${container.height}]")
        }
        if (icons == null || battery == null) {
            // Keep a resolved battery laid out: the element is anchored to its end, so the fallback
            // pass must not turn it GONE + 0x0 while the other view could not be resolved.
            hideStock(container, keep, keepLayout = battery, owner = slot.stock)
        }
    }

    /** FR-25: how long an arrival takes, in ms — asked by the monitor when it fires one. */
    val revealMs: Int get() = settings.revealMs

    // The Animations section (FR-25). The master gates the three individual switches.
    val animationsEnabled: Boolean get() = settings.animationsEnabled
    val arrivalEnabled: Boolean get() = settings.animationsEnabled && settings.arrivalEnabled
    val departureEnabled: Boolean get() = settings.animationsEnabled && settings.departureEnabled
    val chargingEnabled: Boolean get() = settings.animationsEnabled && settings.chargingEnabled

    /** FR-15: whether the charging bolt is drawn at all. Asked by the monitor on every render. */
    val showChargingBolt: Boolean get() = settings.showChargingBolt

    /** Whether a Punch Hole download may take over the ring. Asked by the monitor on every render. */
    val downloadProgressEnabled: Boolean get() = settings.downloadProgressEnabled

    /** Whether the main ring alternates its seat by one pixel each hour. Asked on every clock tick. */
    val burnInEnabled: Boolean get() = settings.burnInEnabled

    /** The main element's current burn-in nudge, derived from the wall clock and the toggle. */
    private fun burnInShiftNow(): Float =
        burnInShiftPx(settings.burnInEnabled, System.currentTimeMillis() / BURN_IN_HOUR_MS)

    /**
     * Re-applies the main ring's horizontal seat for the current burn-in hour.
     *
     * Called from the existing `ACTION_TIME_TICK` (and when the toggle changes), so the one-pixel
     * move costs one translation write and needs no timer, wake lock or render loop. It writes only
     * the translation: the view is never resized or reparented, and the QS/shade extras are untouched.
     */
    fun refreshBurnIn() {
        val view = element?.ui ?: return
        val target = host ?: return
        try {
            val side = geometry.sidePx(hideStrip ?: target, root)
            val frame = lockFrame
            if (ringLocked() && frame != null) {
                placeLocked(view, frame, side, settings.offsetX)
            } else {
                place(view, target, root, side, settings.offsetX, center = true, burnIn = true, rightSeat = true)
            }
        } catch (t: Throwable) {
            L.w("burn-in: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

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
        if (!on) cancelMainHandoff()
        for (target in allElements()) {
            try {
                target.ui.visibility = if (on) View.VISIBLE else View.GONE
                // Off screen means no drawing at all: a looping Rive idle animation would otherwise keep
                // advancing and drawing into a hidden view for the whole process lifetime (battery).
                target.setRenderActive(on)
            } catch (t: Throwable) {
                L.w("visibility: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /** FR-25: fires the arrival on every bar, so the lock screen wakes with the rest of the element. */
    fun revealAll(ms: Int) {
        for (target in allElements()) {
            try {
                target.reveal(ms)
            } catch (t: Throwable) {
                L.w("reveal: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /**
     * RIV-003: a 200 ms shake when charging first reaches 20 %. It is applied to the view, not to the
     * artboard, so Rive and Canvas shake by exactly the same amount; the per-state motion (the bolt's
     * journey and settle pulse) remains Rive's.
     */
    fun shakeAll() {
        if (!settings.animationsEnabled) return
        // The shake owns translationX while it runs; a collapse slide must not fight it.
        cancelMainHandoff()
        val amp = ChargeMotion.SHAKE_AMPLITUDE_DP * context.resources.displayMetrics.density
        for (target in allElements()) {
            val view = target.ui
            try {
                shakes.remove(view)?.cancel()
                val shake = ObjectAnimator.ofFloat(
                    view,
                    "translationX",
                    0f, -amp, amp, -amp * 0.6f, amp * 0.6f, 0f
                ).apply {
                    duration = ChargeMotion.SHAKE_MS
                    interpolator = LinearInterpolator()
                }
                shakes[view] = shake
                shake.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        view.translationX = 0f
                        if (shakes[view] === animation) shakes.remove(view)
                    }
                })
                shake.start()
            } catch (t: Throwable) {
                view.translationX = 0f
                L.w("charge shake: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /**
     * The live stock-icon inventory as the compact JSON the app lists (A4), or null before a bar
     * exists. The strip is preferred over the whole bar so the list is exactly what the hiding pass
     * walks; [root] is only a fallback for the first attach.
     */
    private fun stockIconInventory(): String? {
        val container = hideStrip ?: root ?: return null
        return try {
            StockIconKey.inventorySummary(container)
        } catch (t: Throwable) {
            L.w("stock icon inventory: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    /**
     * Publishes [stockIconInventory] to the app once it changes. Every caller (attach, settings
     * refresh, hiding pass) may run on each layout, so the cached JSON is what keeps the channel quiet.
     */
    private fun publishStockIcons() {
        val now = SystemClock.uptimeMillis()
        if (now - lastStockPublishAt < 750L) return
        lastStockPublishAt = now
        val json = stockIconInventory() ?: return
        if (json == lastStockIconJson) return
        lastStockIconJson = json
        DuoSettingsClient.reportStockIcons(context, json)
    }

    /**
     * Re-reads the user's settings and applies what can change while running (size, offset).
     * Called on attach and whenever the app says something changed, so no restart is needed.
     */
    fun refreshSettings(applySavedSize: Boolean = false): ModuleSettings {
        val fresh = DuoSettingsClient.read(context)
        val changed = fresh != settings
        val previousDesign = settings.designStyle
        val designChanged = changed && fresh.designStyle != previousDesign
        val hideModeChanged = changed &&
            (fresh.hideOtherIcons != settings.hideOtherIcons ||
                fresh.hiddenStockIcons != settings.hiddenStockIcons)
        val surfaceModeChanged = changed &&
            (fresh.showInQs != settings.showInQs || fresh.showInShade != settings.showInShade)
        val burnInChanged = changed && fresh.burnInEnabled != settings.burnInEnabled
        settings = fresh
        hiddenStockKeys = DuoPrefs.parseHiddenStockIcons(fresh.hiddenStockIcons).toSet()
        if (designChanged) {
            // A live DuoRiveView loaded one drawing at start and has no reload path; swapping the art
            // would mean recreating (and reparenting) it, which is the resize path that took System UI
            // down. Keep the current art and scale until the next start.
            if (element == null) {
                // No main element yet: any extra created from now on must use the current drawing,
                // otherwise a QS/shade copy can be built with the default classic art and sit/size
                // differently from the main bar.
                geometry.applyDesignStyle(fresh.designStyle)
                appliedDesignStyle = fresh.designStyle
            } else {
                L.i("design change needs restart ($previousDesign -> ${fresh.designStyle})")
            }
        }
        if (changed) {
            L.i("settings rev ${fresh.revision}: size ${fresh.sizePercent}%, offset ${fresh.offsetX}dp, " +
                    "percent=${fresh.showPercent}, percentHeight=${fresh.percentHeight}%, " +
                    "split=${fresh.splitIndicators}, lock=${fresh.lockRing}, icons=${fresh.indicatorsOffsetX}dp, " +
                    "edge=${fresh.edgePadding}%, " +
                    "rive=${fresh.useRive}, live=${fresh.liveApply}"
            )
            // A drag must not resize the Rive view (that restart loop is why size is restart-only).
            // Rotation is one saved size for the orientation now on screen, so it is applied here.
            if (applySavedSize) geometry.applySize(fresh.sizePercent)
            if (fresh.liveApply || applySavedSize) {
                applyLayout()
                syncIndicators()
                for (slot in extras) {
                    applyExtraLayout(slot)
                    syncExtraIndicators(slot)
                }
            } else {
                // The safe path: size and position are saved but wait for the next start. Gestures and
                // the clock's font are not geometry, so they can still change live.
                installAllGestures()
                L.i("live apply off - size/position take effect after Restart System UI")
            }
            // The toggle is a translation-only change, so it can take effect even when size and
            // position are waiting for the next start.
            if (burnInChanged && !(fresh.liveApply || applySavedSize)) refreshBurnIn()
            clock.apply(root, settings.systemClockFont)
            if (hideModeChanged) applyHidingMode()
            if (surfaceModeChanged) applySurfaceSettings()
        }
        publishStockIcons()
        return fresh
    }

    /**
     * FR-08b: re-applies hiding after the user changed whether the other icons stay visible. The stock
     * views are put back first, so an icon that should now show is not left hidden, then the new pass
     * runs; the element stays exactly where it is.
     */
    private fun applyHidingMode() {
        if (!(element?.isReady ?: false)) return
        L.i("hiding mode changed (hideOtherIcons=${settings.hideOtherIcons}) - re-applying")
        hider.restore()
        extras.forEach { it.stock.restore() }
        reapplyHiding()
        publishStockIcons()
    }

    /** Size and offset come from the settings, so reshaping the element needs no re-injection (FR-03/17). */
    private fun applyLayout() {
        val view = element?.ui ?: return
        val measureFrom = hideStrip ?: host ?: return
        try {
            val side = geometry.sidePx(measureFrom, root)
            geometry.noteMainSide(side)
            // assignBox only rewrites layoutParams when the box actually changed. Re-assigning them
            // (even to the same numbers) requests a layout pass, and a layout pass on the Rive
            // TextureView is what took System UI down on rotation.
            if (!ringLocked() || !holdRing(view, side)) {
                releaseRing(view, side)
                val target = host ?: return
                assignBox(view, target, side, ringInset(side))
                place(view, target, root, side, settings.offsetX, center = true, burnIn = true, rightSeat = true)
            }
            installRingGestures(view)
            indicators?.ui?.let { installIndicatorGestures(it) }
        } catch (t: Throwable) {
            L.w("applyLayout: ${t.message}")
        }
    }

    /**
     * Centers the ring in the status bar and leaves a spacer in the strip.
     *
     * The translation is written once, from the bar's own center. Nothing on the clock tick reads it
     * back: the bar does not move when the clock gets wider, so the ring does not either.
     * Returns false when that frame is not available yet, and the caller leaves the ring in the strip.
     */
    private fun holdRing(view: View, side: Int): Boolean {
        val barRoot = root ?: return false
        val strip = hideStrip
        holding = true
        try {
            return holdRingNow(view, side, barRoot, strip)
        } finally {
            holding = false
        }
    }

    private fun holdRingNow(view: View, side: Int, barRoot: View, strip: ViewGroup?): Boolean {
        val frame = finder.findLockFrame(strip ?: view, barRoot)
        if (frame == null) {
            if (barRoot.width > 0) {
                lockAttempted = true
                logOnce.once("lock-frame") {
                    L.w("lock ring: no full-width status bar frame - the ring stays in the icon strip")
                }
            }
            return false
        }
        lockFrame = frame
        lockAttempted = true
        host = frame
        allowOverflow(frame)
        if (view.parent !== frame) {
            (view.parent as? ViewGroup)?.removeView(view)
            assignLockedBox(view, side)
            ensureInjectedViewId(view, frame)
            frame.addView(view)
        } else {
            assignLockedBox(view, side)
        }
        if (overlay) dropSpacer() else strip?.let { ensureSpacer(it, side) }
        placeLocked(view, frame, side, settings.offsetX)
        logOnce.once("lock-on") {
            L.i("ring locked to ${frame.javaClass.simpleName} ${frame.width}x${frame.height}")
        }
        return true
    }

    /** Puts the ring back in the icon strip and removes the spacer that was holding its slot. */
    private fun releaseRing(view: View, side: Int) {
        dropSpacer()
        lockFrame = null
        // A failed lock while the switch is still on must keep [lockAttempted], or the clock-tick
        // listener would retry the move forever. Turning the lock off is what clears it.
        if (!ringLocked()) lockAttempted = false
        val parent = flowHost ?: hideStrip ?: host ?: return
        if (view.parent !== parent) {
            (view.parent as? ViewGroup)?.removeView(view)
            // The lock frame used FrameLayout params; the strip is a LinearLayout. Regenerate before the
            // cross-container add or the strip receives params of the wrong type.
            view.layoutParams = layoutParamsFor(parent, side, RingGeometry.elementHeightPx(side), ringInset(side))
            ensureInjectedViewId(view, parent)
            parent.addView(view)
        }
        host = parent
        flowHost = parent
    }

    /** Top-left gravity, so the locked translation is measured from a stable edge in both LTR and RTL. */
    private fun assignLockedBox(view: View, side: Int) {
        val height = RingGeometry.elementHeightPx(side)
        val gravity = Gravity.LEFT or Gravity.TOP
        val lp = view.layoutParams as? FrameLayout.LayoutParams
        if (lp != null && lp.width == side && lp.height == height && lp.gravity == gravity &&
            lp.leftMargin == 0 && lp.rightMargin == 0 && lp.topMargin == 0 && lp.bottomMargin == 0
        ) return
        view.layoutParams = FrameLayout.LayoutParams(side, height).apply { this.gravity = gravity }
    }

    private fun placeLocked(view: View, frame: FrameLayout, side: Int, offsetDp: Int) {
        if (frame.width <= 0 || frame.height <= 0) return
        if (view === element?.ui) cancelMainHandoff()
        val density = context.resources.displayMetrics.density
        view.translationX = lockedTranslationX(
            0, frame.width, 0, frame.paddingLeft, side, offsetDp * density
        ) + burnInShiftNow()
        view.translationY = lockedTranslationY(
            0, frame.height, 0, frame.paddingTop, side
        )
    }

    /** An invisible slot with the ring's width and edge inset, inserted ahead of the icon cluster. */
    private fun ensureSpacer(strip: ViewGroup, side: Int) {
        val spacer = ringSpacer ?: View(context).also { created ->
            created.visibility = View.INVISIBLE
            created.isClickable = false
            created.isFocusable = false
            created.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            ringSpacer = created
        }
        if (spacer.parent !== strip) {
            (spacer.parent as? ViewGroup)?.removeView(spacer)
            // A rebuilt strip can differ in type from the one the spacer last lived in; give it params
            // generated by this parent before adding it.
            spacer.layoutParams = layoutParamsFor(
                strip, side, RingGeometry.elementHeightPx(side), iconSlotInset(side)
            )
            val cluster = indicators?.ui
            val index = if (cluster != null && cluster.parent === strip) strip.indexOfChild(cluster) else -1
            ensureInjectedViewId(spacer, strip)
            if (index >= 0) strip.addView(spacer, index) else strip.addView(spacer)
        }
        assignBox(spacer, strip, side, iconSlotInset(side))
    }

    /**
     * Updates the strip margin for the current middle occupant, without moving or resizing the ring.
     */
    private fun applyMiddleSlotSpace() {
        try {
            val measureFrom = hideStrip ?: host
            val main = element?.ui
            if (measureFrom != null && main != null) {
                val side = geometry.sidePx(measureFrom, root)
                if (ringLocked()) hideStrip?.let { ensureSpacer(it, side) }
                else host?.let { assignBox(main, it, side, ringInset(side)) }
            }
            for (slot in extras) {
                val target = slot.container ?: continue
                val view = slot.element?.ui ?: continue
                val side = geometry.extraSidePx(target, slot.bar, slot.basePx)
                assignBox(view, target, side, ringInset(side))
            }
        } catch (t: Throwable) {
            L.w("middle slot space: ${t.message}")
        }
    }

    /**
     * The ring's own strip margin.
     *
     * With a middle icon showing, this is the edge-spacing setting. With the slot empty, the margin
     * outside the ring's ink is handed back as well: that padding belonged to the icon.
     */
    private fun ringInset(side: Int): Int {
        val base = edgeInsetPx(side, settings.edgePadding)
        return if (middleSlotEmpty()) base - RingGeometry.outerSlackPx(side) else base
    }

    /**
     * The spacer left in the strip while the ring is centered.
     *
     * It exists to hold the middle icon. An empty slot keeps none of it, so the other status icons
     * close up instead of sitting beside a blank gap. A visible icon keeps the edge-spacing setting.
     */
    private fun iconSlotInset(side: Int): Int =
        edgeInsetPx(side, if (middleSlotEmpty()) 0 else settings.edgePadding)

    private fun middleSlotEmpty(): Boolean = laidOutMiddle == DuoMapping.MIDDLE_OFF

    /** Keeps the cluster drawing where it was after the spacer stops reserving width. */
    private fun holdIconSlot(view: View, container: ViewGroup, side: Int) {
        val hold = iconSlotHoldPx(side)
        if (hold == 0) return
        val rtl = container.layoutDirection == View.LAYOUT_DIRECTION_RTL
        view.translationX += if (rtl) -hold else hold
    }

    /**
     * How far the icon cluster has to slide to stay put when [iconSlotInset] stops reserving the spacer.
     *
     * The cluster is the next child after the spacer and overlaps it with a negative margin. Collapsing
     * the spacer moves that child by the width that was reserved; this puts the drawing back.
     */
    private fun iconSlotHoldPx(side: Int): Int {
        if (!middleSlotEmpty() || ringSpacer?.parent == null) return 0
        return side * settings.edgePadding.coerceIn(0, 100) / 100
    }

    private fun dropSpacer() {
        val spacer = ringSpacer ?: return
        (spacer.parent as? ViewGroup)?.removeView(spacer)
        ringSpacer = null
    }

    /**
     * Moves [view] by [offsetDp] and, unless this is an overlay, centres it the way the ring is centred.
     * The ring and the icon cluster share this so a split layout cannot drift from the original one.
     */
    private fun place(
        view: View,
        container: ViewGroup,
        windowRoot: View?,
        side: Int,
        offsetDp: Int,
        center: Boolean,
        /** Only the main ring asks for this; extra surfaces keep their fixed seat. */
        burnIn: Boolean = false,
        /** Explicit right-seat (physical right edge) instead of the flow nudge. */
        rightSeat: Boolean = false
    ) {
        // A layout pass is the new owner of the seat: stop the slide before writing its resting value.
        if (view === element?.ui) cancelMainHandoff()
        val burnInShift = if (burnIn) burnInShiftNow() else 0f
        if (overlay && container === host) {
            // The parent is a plain FrameLayout that places children top-left, so the element is moved
            // onto the battery by translation. On One UI 8 the anchor is not a child of the hidden
            // strip, so this is what actually removes the stock cluster.
            hideOverlayAnchor()
            anchorOnBattery(view, container, side, offsetDp, burnIn = burnInShift)
            return
        }
        // Normal mode is right-anchored: 0 sits on the physical right edge, negatives move left.
        // The seat is computed from the parent's actual width and the view's current left (a delta, so
        // an already flow-positioned view is not double-counted); the edge spacing is carried as the
        // right-hand padding. burnIn stays additive.
        val density = context.resources.displayMetrics.density
        val offsetPx = (offsetDp * density).toInt()
        val rtl = container.layoutDirection == View.LAYOUT_DIRECTION_RTL
        // edgeInsetPx is <= 0; flip it to a positive right-hand padding for the seat formula.
        val endPadding = -(if (container is LinearLayout) 0 else edgeInsetPx(side, settings.edgePadding))
        val parentWidth = container.width
        // The right seam (parent-width based seat) applies only when explicitly requested.
        // Indicators, and QS/shade extras that are battery-anchored, keep their flow nudge and never
        // recompute against the parent width.
        val seat: Int = if (rightSeat && parentWidth > 0) {
            normalSeatTranslationX(parentWidth, side, endPadding, offsetPx, rtl) - view.left
        } else {
            // Keep the unmeasured-parent fallback for the main view, and the old flow nudge for
            // non-main views (indicators/QS/shade extras).
            offsetPx + (if (container is LinearLayout) 0 else edgeInsetPx(side, settings.edgePadding))
        }
        view.translationX = seat + burnInShift
        // The strip sits low in the window, so centring on it wastes the space above. Centre the
        // ring in the whole status bar instead, which is what lets it grow to the window height.
        // The view is taller than the ring; the extra shift keeps the ring put and leaves the
        // raised percentage in the space above it.
        view.translationY = (if (center) geometry.windowCenterShiftY(container, windowRoot) else 0f) +
            RingGeometry.ringAnchorShiftY(side)
    }

    private fun installRingGestures(view: View) {
        ringGestures.install(
            view,
            settings.tapAction,
            settings.doubleTapAction,
            settings.longPressAction,
            singleTapOverrideActive = downloadTapActive
        )
    }

    private fun installIndicatorGestures(view: View) {
        indicatorGestures.install(
            view,
            settings.indicatorsTapAction,
            settings.indicatorsDoubleTapAction,
            settings.indicatorsLongPressAction
        )
    }

    private fun installAllGestures() {
        element?.ui?.let(::installRingGestures)
        indicators?.ui?.let(::installIndicatorGestures)
        for (slot in extras) {
            slot.element?.ui?.let(::installRingGestures)
            slot.indicators?.ui?.let(::installIndicatorGestures)
        }
    }

    /** While a download is visible, a ring single-tap belongs to the battery/download toggle. */
    fun setDownloadTap(active: Boolean, onTap: () -> Unit) {
        onDownloadRingTap = onTap
        if (downloadTapActive == active) return
        downloadTapActive = active
        installAllGestures()
    }

    private fun handleDownloadRingTap(): Boolean {
        if (!downloadTapActive) return false
        onDownloadRingTap?.invoke() ?: return false
        return true
    }

    /** The part the ring's view should draw for the current settings. */
    private fun ringPart(): DuoPart = if (settings.splitIndicators) DuoPart.RING else DuoPart.ALL

    /**
     * Adds or removes the icon-cluster view on the main bar. The ring's view stays where it was;
     * only its [DuoPart] changes, so turning the mode on does not rebuild the Rive surface that
     * already survived attach.
     */
    private fun syncIndicators() {
        val ring = element ?: return
        val container = clusterHome() ?: return
        ring.part = ringPart()
        if (!settings.splitIndicators) {
            drop(indicators, container)
            indicators = null
            return
        }
        val stageRoot = root ?: return
        indicators = ensureCluster(
            indicators,
            container,
            ring.ui,
            root,
            stageRoot,
            basePx = 0,
            center = true
        ) { indicators = it }
        // A settings change can create this view while QS is open; do not let its default alpha
        // show a second main-bar cluster over the QS surface.
        indicators?.ui?.alpha = if (mainBarVisible) 1f else 0f
    }

    /** The same cluster, for a keyguard bar or the shade header. */
    private fun syncExtraIndicators(slot: ExtraBar) {
        val ring = slot.element ?: return
        val container = slot.container ?: return
        ring.part = ringPart()
        if (!settings.splitIndicators) {
            drop(slot.indicators, container)
            slot.indicators = null
            return
        }
        val stageRoot = slot.bar ?: container
        slot.indicators = ensureCluster(
            slot.indicators,
            container,
            ring.ui,
            slot.bar,
            stageRoot,
            slot.basePx,
            slot.center,
            alignOverlay = true,
            clampSize = slot.name != "qs header"
        ) { slot.indicators = it }
    }

    /**
     * Returns the icon-cluster view for one bar, creating it the first time the split layout is on.
     * [assign] publishes it before the ready callback, so a hiding pass can already see the view.
     */
    private fun ensureCluster(
        existing: DuoElement?,
        container: ViewGroup,
        ringView: View,
        windowRoot: View?,
        stageRoot: View,
        basePx: Int,
        center: Boolean,
        alignOverlay: Boolean = false,
        clampSize: Boolean = false,
        assign: (DuoElement?) -> Unit
    ): DuoElement? {
        fun sideFor(container: ViewGroup, windowRoot: View?): Int =
            if (clampSize) geometry.extraSidePx(container, windowRoot, basePx)
            else geometry.sidePx(container, windowRoot, basePx)

        existing?.let { cluster ->
            val ui = cluster.ui
            val liveParent = ui.parent as? ViewGroup
            if (liveParent != null && slotInSameWindow(ui.rootView, container.rootView)) {
                // Live element in this window: do not seatBeside/remove/add. Adopt its current parent
                // and only place/align. `seatBeside` is the only path that may reorder, so it is never
                // used for a cached Rive view.
                val liveContainer = liveParent
                val side = sideFor(liveContainer, windowRoot)
                place(ui, liveContainer, windowRoot, side, settings.indicatorsOffsetX, center, rightSeat = false)
                if (alignOverlay) alignIndicatorOverlay(ui, ringView, liveContainer)
                holdIconSlot(ui, liveContainer, side)
                installIndicatorGestures(ui)
                logOnce.once("reuse:cluster:${System.identityHashCode(liveParent)}") {
                    L.i("cluster reuse attached slot parent=${liveParent.javaClass.simpleName}")
                }
                return cluster
            }
            // Detached, or left in the old rotation window: a cached Rive view is never reparented.
            // Drop it once and fall through to create exactly one replacement in this window.
            L.i(
                "cluster dropping stale view (attached=${ui.isAttachedToWindow}, " +
                    "parent=${liveParent?.javaClass?.simpleName ?: "none"})"
            )
            drop(cluster, container)
            assign(null)
        }
        val side = sideFor(container, windowRoot)
        val stage = guard.stage()
        if (stage == DuoGuard.OFF) return null
        val candidate = createElement(stageRoot, stage, DuoPart.INDICATORS)
        // A LinearLayout would otherwise reserve a second slot and shove the clock. The cluster keeps
        // no strip space of its own; edge spacing belongs to the ring. Overlay parents stack children,
        // so the margin is only for the strip.
        candidate.ui.layoutParams = layoutParamsFor(
            container, side, RingGeometry.elementHeightPx(side), edgeInsetPx(side, 0)
        )
        val index = container.indexOfChild(ringView)
        ensureInjectedViewId(candidate.ui, container)
        if (index >= 0) container.addView(candidate.ui, index + 1) else container.addView(candidate.ui)
        assign(candidate)
        allowOverflow(container)
        place(candidate.ui, container, windowRoot, side, settings.indicatorsOffsetX, center, rightSeat = false)
        if (alignOverlay) alignIndicatorOverlay(candidate.ui, ringView, container)
        holdIconSlot(candidate.ui, container, side)
        installIndicatorGestures(candidate.ui)
        candidate.onReady {
            candidate.reveal(settings.revealMs)
            L.i(
                "icon cluster injected (${side}px, offset ${settings.indicatorsOffsetX}dp, " +
                    "split=${settings.splitIndicators}, view=${candidate.ui.width}x${candidate.ui.height})"
            )
        }
        candidate.onFailed {
            L.w("icon cluster did not bind - drawing it with Canvas")
            val canvas = try {
                DuoCanvasView(context, DuoPart.INDICATORS, appliedDesignStyle).also { it.start() }
            } catch (t: Throwable) {
                L.e("icon cluster fallback failed: ${t.javaClass.simpleName}: ${t.message}")
                drop(candidate, container)
                assign(null)
                return@onFailed
            }
            swap(container, candidate, canvas, ringView)
            assign(canvas)
            place(canvas.ui, container, windowRoot, side, settings.indicatorsOffsetX, center, rightSeat = false)
            if (alignOverlay) alignIndicatorOverlay(canvas.ui, ringView, container)
            installIndicatorGestures(canvas.ui)
            canvas.onReady { canvas.reveal(settings.revealMs) }
        }
        return candidate
    }

    /**
     * On an overlay parent the Rive indicator cluster must share the ring's position; a flow parent
     * seats it beside the ring. Without this the QS 5G/cell icons drew at the left edge over the
     * carrier name (measured on CPH2747).
     */
    private fun alignIndicatorOverlay(view: View, ring: View, container: ViewGroup) {
        // Extras are anchored, not flowed: the QS/shade indicator view must sit exactly on the ring
        // even when the parent is a LinearLayout (the shade header is one). Setting x/y after layout
        // is what removes the measured 30 px offset (QS) and the left-edge 5G (shade).
        val align = Runnable {
            try {
                if (view.isAttachedToWindow && ring.isAttachedToWindow && view.parent === ring.parent) {
                    view.x = ring.x
                    view.y = ring.y
                } else {
                    view.translationX = ring.translationX +
                        settings.indicatorsOffsetX * context.resources.displayMetrics.density
                    view.translationY = ring.translationY
                }
            } catch (t: Throwable) {
                L.w("indicator align: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        view.post(align)
        // The ring may be anchored once more after this first post (the QS hide pass runs after
        // layout), which left the indicator view exactly 31 px behind it in the measured QS tree.
        // One delayed pass after that final anchor aligns both; it is not a per-frame tracker.
        view.postDelayed(align, 120L)
    }

    /** Replaces [from] with [to] in [container], keeping it next to the ring. */
    private fun swap(container: ViewGroup, from: DuoElement, to: DuoElement, ringView: View) {
        val index = container.indexOfChild(from.ui).takeIf { it >= 0 }
            ?: (container.indexOfChild(ringView) + 1).takeIf { it > 0 }
        try {
            from.teardown()
            container.removeView(from.ui)
        } catch (t: Throwable) {
            L.w("cluster swap remove: ${t.message}")
        }
        to.ui.layoutParams = from.ui.layoutParams
        ensureInjectedViewId(to.ui, container)
        if (index != null && index <= container.childCount) container.addView(to.ui, index)
        else container.addView(to.ui)
    }

    private fun drop(view: DuoElement?, container: ViewGroup?) {
        view ?: return
        try {
            (view.ui.parent as? ViewGroup ?: container)?.removeView(view.ui)
            view.teardown()
        } catch (_: Throwable) {
        }
    }

    /** A container this host may inject into: laid out, shown and in the window. */
    private fun containerUsable(container: ViewGroup?): Boolean =
        container != null && container.width > 0 && container.visibility == View.VISIBLE &&
            container.isAttachedToWindow

    /** True only while the slot's element is still the container's child and attached to a window. */
    private fun slotElementAttached(slot: ExtraBar?, container: ViewGroup?): Boolean {
        val ui = slot?.element?.ui ?: return false
        if (container == null) return false
        return slotOwnsHost(container, ui.parent as? View, ui.isAttachedToWindow, container)
    }

    /** The one live slot currently owning [target], if any. */
    private fun liveSlotIn(target: ViewGroup): ExtraBar? =
        extras.firstOrNull { slotElementAttached(it, target) }

    /**
     * A same-named slot whose element is still attached to [windowRoot]'s window, regardless of its
     * container inside that window. A view left in the old rotation window is not live for this one;
     * [ensureSlotAvailable] drops it so the new window can build its one replacement.
     */
    private fun liveSlotFor(name: String, windowRoot: View?): ExtraBar? {
        val window = windowRoot?.rootView ?: return null
        val slot = extras.firstOrNull { it.name == name } ?: return null
        val ui = slot.element?.ui ?: return null
        val parent = ui.parent as? ViewGroup ?: return null
        if (!slotSharesWindow(ui.isAttachedToWindow, ui.rootView, window)) return null
        // Adopt the live parent instead of reparenting or recreating.
        slot.container = parent
        return slot
    }

    /** Drop only detached bookkeeping slots bound to [target]; never a live Duo. */
    private fun dropDetachedSlotsFor(target: ViewGroup) {
        for (slot in extras.toList()) {
            if (slot.container === target && !slotElementAttached(slot, target)) {
                L.i("shade dropping detached slot ${slot.name} from " +
                    "${target.javaClass.simpleName} - no live Duo touched")
                dropSlot(slot)
            }
        }
    }

    /**
     * Ensure a same-named slot is available: true if none or dropped; false if still live in this
     * window. A slot attached to another (old rotation) window is stale here and must be dropped, or
     * it would block the new window forever and leave its ring in the dead window.
     */
    private fun ensureSlotAvailable(slotName: String, windowRoot: View?): Boolean {
        val slot = extras.firstOrNull { it.name == slotName } ?: return true
        val ui = slot.element?.ui
        val liveParent = ui?.parent as? ViewGroup
        val window = windowRoot?.rootView
        if (ui != null && liveParent != null &&
            slotSharesWindow(ui.isAttachedToWindow, ui.rootView, window)
        ) {
            // Live in this window regardless of the resolved target: adopt the live parent.
            slot.container = liveParent
            logOnce.once("reuse:$slotName:${System.identityHashCode(liveParent)}") {
                L.i("$slotName reuse attached slot parent=${liveParent.javaClass.simpleName}")
            }
            return false
        }
        L.i("shade dropping stale slot $slotName - no live Duo in this window")
        dropSlot(slot)
        return true
    }

    /** Removes a slot completely: its views, its element, its hidden stock views and its bookkeeping. */
    private fun dropSlot(slot: ExtraBar) {
        try {
            drop(slot.indicators, slot.container)
            slot.indicators = null
            drop(slot.element, slot.container)
            slot.element = null
            slot.stock.restore()
        } finally {
            extras.remove(slot)
        }
    }

    /**
     * Runs [attach] for a replacement while keeping the working slot available: the old slot leaves the
     * bookkeeping only for the attempt, is torn down once the new one is in place, and is put back when
     * the attempt fails. That way a failed replacement can neither hide stock icons nor lose the old one.
     */
    private fun replaceSlot(previous: ExtraBar?, attach: () -> Boolean): Boolean {
        previous ?: return attach()
        extras.remove(previous)
        val attached = try {
            attach()
        } catch (t: Throwable) {
            L.w("${previous.name} replacement: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
        if (attached) {
            dropSlot(previous)
        } else {
            extras.add(previous)
        }
        return attached
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
    fun handleElementTouch(event: MotionEvent): Boolean {
        val ringViews = listOfNotNull(element?.ui)
        val indicatorViews = listOfNotNull(indicators?.ui)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // The two views can overlap at their transparent artboard edges. The detached icon view
            // is visually on top, so it owns that overlap and a ring action cannot also run there.
            touchPart = when {
                settings.splitIndicators && indicatorGestures.contains(
                    event,
                    indicatorViews,
                    ElementGestures.HitRegion.INDICATORS
                ) ->
                    DuoPart.INDICATORS
                ringGestures.contains(
                    event,
                    ringViews,
                    if (settings.splitIndicators) ElementGestures.HitRegion.RING
                    else ElementGestures.HitRegion.VIEW
                ) -> DuoPart.RING
                else -> null
            }
        }
        val handled = when (touchPart) {
            DuoPart.INDICATORS -> indicatorGestures.handle(event, indicatorViews)
            DuoPart.RING -> ringGestures.handle(event, ringViews)
            else -> false
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            touchPart = null
        }
        return handled
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
                logOnce.once("gate") {
                    L.i("gated off - enable with: ${guard.enableHint}")
                }
                return false
            }
            var target = finder.findStatusIconsHost(statusBarRoot)
            var composeAnchor: View? = null
            if (target == null) {
                // Android 17 AOSP draws the status bar icons with Compose: there is no View strip to
                // inject into, only a `ComposeView`. The element is drawn in the surrounding ViewGroup
                // and anchored on that Compose view, which is hidden with INVISIBLE so it keeps its place.
                val compose = finder.findComposeIconView(statusBarRoot)
                val parent = compose?.parent as? ViewGroup
                if (compose != null && parent != null) {
                    L.i("Compose status bar: no View strip; drawing in ${parent.javaClass.simpleName} " +
                            "and hiding ${compose.javaClass.simpleName}")
                    composeAnchor = compose
                    target = parent
                }
            }
            if (target == null) {
                L.w("system_icons not found - status bar left untouched")
                return false
            }
            root = statusBarRoot
            hideStrip = target
            // Pin the slot width now, while the battery view still has its real width: once the stock
            // icons are hidden it reads 0 and the fallback would change the size mid-session.
            val basePx = geometry.measuredWidth(target)
            refreshSettings()
            // The drawing is captured for this run just like the slot base: the Rive view loads its
            // artwork once, so the optical scale must match the art it was created with.
            geometry.applyDesignStyle(settings.designStyle)
            appliedDesignStyle = settings.designStyle
            val candidate = createElement(statusBarRoot, stage, ringPart())
            // Capture the size once per process: live size changes are deferred to a restart (see
            // [SlotGeometry.appliedSize]) because resizing the Rive view live used to take System UI down.
            geometry.capture(settings.sizePercent, basePx)
            logOnce.once("facts") {
                DuoSbFacts.report(context, statusBarRoot, target, geometry.widthPx(target))
            }
            // The element's real parent: the strip, unless the ROM's strip is a custom container that
            // refuses foreign children. HyperOS 3's MiuiStatusBatteryContainer measured it 0x0 - the icons
            // hid and nothing drew - so there the element goes into a plain FrameLayout ancestor and is
            // anchored over the battery.
            var parent = finder.chooseElementParent(target, statusBarRoot)
            overlay = parent !== target
            // Anchor on the battery when there is one; on a Compose bar there is no battery id, so the
            // Compose icon view is the anchor instead (it keeps its layout, hidden with INVISIBLE).
            anchorBattery = if (overlay) (finder.findBattery(target) ?: composeAnchor) else null
            if (overlay && anchorBattery == null) {
                // No battery to anchor to: fall back to the strip rather than draw at a random spot.
                overlay = false
            }
            // Samsung One UI 8 (Android 16): the AOSP `system_icons` is a dead stub and the real cluster is
            // a `CombinedStatusView` drawn beside it. Injecting into the stub drew a 0x0 element and hid
            // nothing (SM-S948N). When the strip is a stub and that view exists, anchor on it and draw in
            // the bar's own FrameLayout layer. Gated on both conditions, so no other ROM reaches this path.
            val combined = finder.findCombinedStatusView(statusBarRoot)
            if (combined != null && finder.isStubStrip(target)) {
                parent = finder.findOverlayContainer(target, statusBarRoot)
                overlay = true
                overlayOnCombined = true
                anchorBattery = combined
                L.i("One UI 8 layout: strip ${target.javaClass.simpleName} is a stub - drawing in " +
                        "${parent.javaClass.simpleName} anchored over ${combined.javaClass.simpleName}")
            }
            L.i("element container: ${parent.javaClass.simpleName}" +
                    if (overlay) " (overlay on ${anchorBattery?.javaClass?.simpleName}, strip ${target.javaClass.simpleName})" else "")
            // Capture the anchor's visibility before the first hide, not after it has been set INVISIBLE.
            rememberAnchorVisibility()
            val container = if (overlay) parent else target
            val side = geometry.sidePx(target, root)
            allowOverflow(container)
            candidate.ui.layoutParams = layoutParamsFor(container, side)
            ensureInjectedViewId(candidate.ui, container)
            container.addView(candidate.ui)
            host = container
            flowHost = container
            element = candidate
            container.requestLayout()
            candidate.ui.requestLayout()
            candidate.ui.alpha = if (mainBarVisible) 1f else 0f
            applyLayout()
            syncIndicators()
            clock.apply(root, settings.systemClockFont)
            // The stock icons are hidden and the first reveal fires only once the element reports itself
            // live. A Rive state machine binds *after* this method returns (it needs the view attached to a
            // window), so hiding here would cover an empty slot; Canvas reports ready immediately.
            candidate.onReady {
                if (hasBox(candidate.ui) && (candidate.ui.parent === container || candidate.ui.parent === target)) {
                    onElementReady(candidate, target)
                } else {
                    verifyAttachedBox(candidate, target, 0)
                }
            }
            candidate.onFailed { onElementFailed(candidate, target) }
            verifyAttachedBox(candidate, target, 0)
            forgetAttemptsAfterSurvival(candidate)
            true
        } catch (t: Throwable) {
            L.e("attach failed: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /** The element is live: now it is safe to take the stock icons out and fire the reveal (FR-08/25). */
    private fun onElementReady(candidate: DuoElement, target: ViewGroup) {
        if (element !== candidate) return
        if (!hasBox(candidate.ui) || !(candidate.ui.parent === target || candidate.ui.parent === host)) {
            L.w("Duo box: ${candidate.ui.width}x${candidate.ui.height} parent=${candidate.ui.parent?.javaClass?.simpleName ?: "none"} - not ready; scheduling fallback")
            onElementFailed(candidate, target)
            return
        }
        try {
            // Capture the anchor before hideStock/hideOverlayAnchor can make it INVISIBLE.
            rememberAnchorVisibility()
            // In overlay mode the element is not a child of the strip, so nothing is "kept" there; the
            // battery keeps its layout instead so the anchor stays valid (and survives rotation).
            hideStock(
                target,
                if (candidate.ui.parent === target) candidate.ui else null,
                if (overlay) anchorBattery else null
            )
            hideOverlayAnchor()
            // Hiding may have changed the strip's layout, so the overlay anchor is re-read after it.
            applyLayout()
            candidate.reveal(settings.revealMs)
            publishStockIcons()
            val width = candidate.ui.layoutParams?.width ?: 0
            L.i("Duo injected into ${target.javaClass.simpleName} (${width}px wide, ${settings.sizePercent}%)" +
                    if (overlay) " - overlaid over the battery" else "")
        } catch (t: Throwable) {
            L.w("onReady: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /**
     * The Rive element never bound its view model. Rather than leave an empty slot, it is replaced by the
     * no-native Canvas element, which draws the ring and the percentage from the same mapping (FR-21).
     */
    private fun onElementFailed(candidate: DuoElement, target: ViewGroup) {
        if (element !== candidate) return
        L.w("Rive element did not bind - falling back to Canvas")
        // Tell the app so it can ask the user to send the log; this is the failure we most need evidence for.
        DuoSettingsClient.reportFallback(context, "Rive did not bind on ${android.os.Build.MODEL}; using the simple drawing")
        val canvas = try {
            DuoCanvasView(context, ringPart(), appliedDesignStyle).also { it.start() }
        } catch (t: Throwable) {
            L.e("Canvas fallback failed: ${t.javaClass.simpleName}: ${t.message}")
            return
        }
        val container = host ?: target
        try {
            candidate.teardown()
            (candidate.ui.parent as? ViewGroup)?.removeView(candidate.ui)
        } catch (t: Throwable) {
            L.w("fallback remove: ${t.message}")
        }
        val width = geometry.widthPx(target)
        canvas.ui.layoutParams = layoutParamsFor(
            container, width, ViewGroup.LayoutParams.MATCH_PARENT, edgeInsetPx(width, settings.edgePadding)
        )
        ensureInjectedViewId(canvas.ui, container)
        container.addView(canvas.ui)
        container.requestLayout()
        canvas.ui.requestLayout()
        element = canvas
        // DuoCanvasView.reveal() is a no-op, and setMainBarVisible() is edge/idempotent. Apply the
        // current visibility here or a fallback that happens while QS/shade is open stays alpha=1.
        // A fallback during collapse (main hidden, panel closed) must be visible; keep panel-open
        // behavior (alpha 0 then revealed) intact.
        canvas.ui.alpha = if (mainBarVisible || !panelIsOpen()) 1f else 0f
        applyLayout()
        syncIndicators()
        canvas.onReady {
            if (hasBox(canvas.ui) && (canvas.ui.parent === container || canvas.ui.parent === target)) {
                onElementReady(canvas, target)
            } else {
                ensureCanvasBox(canvas, target, 0)
            }
        }
        ensureCanvasBox(canvas, target, 0)
        L.i("element: Canvas (fallback after Rive did not bind)")
    }

    /** Bounded post-layout validation for an attached element. Falls back to Canvas on a 0x0 box. */
    private fun verifyAttachedBox(candidate: DuoElement, target: ViewGroup, frame: Int) {
        if (element !== candidate) return
        val parentOk = candidate.ui.parent === host || candidate.ui.parent === target
        if (parentOk && hasBox(candidate.ui)) return
        if (frame < 6) {
            (host ?: target).requestLayout()
            candidate.ui.requestLayout()
            candidate.ui.postOnAnimation { verifyAttachedBox(candidate, target, frame + 1) }
        } else {
            L.w("Duo box: ${candidate.ui.width}x${candidate.ui.height} parent=${candidate.ui.parent?.javaClass?.simpleName ?: "none"}")
            onElementFailed(candidate, target)
        }
    }

    /** Bounded validation for the Canvas fallback; restores stock icons if it never gets a box. */
    private fun ensureCanvasBox(canvas: DuoElement, target: ViewGroup, frame: Int) {
        if (element !== canvas) return
        val parentOk = canvas.ui.parent === host || canvas.ui.parent === target
        if (parentOk && hasBox(canvas.ui)) {
            onElementReady(canvas, target)
            return
        }
        if (frame < 6) {
            (host ?: target).requestLayout()
            canvas.ui.requestLayout()
            canvas.ui.postOnAnimation { ensureCanvasBox(canvas, target, frame + 1) }
        } else {
            L.w("fallback box: ${canvas.ui.width}x${canvas.ui.height} parent=${canvas.ui.parent?.javaClass?.simpleName ?: "none"}")
            try {
                (canvas.ui.parent as? ViewGroup)?.removeView(canvas.ui)
                canvas.teardown()
            } catch (_: Throwable) {
            }
            element = null
            hider.restore()
        }
    }

    /** Triggers the Canvas fallback for the main element when it has no drawable box. */
    private fun ensureMainFallback() {
        val candidate = element ?: return
        val target = hideStrip ?: (host as? ViewGroup) ?: return
        onElementFailed(candidate, target)
    }

    private fun createElement(root: View, stage: Int, part: DuoPart = DuoPart.ALL): DuoElement {
        if (stage >= DuoGuard.RIVE) {
            riveElement(root, part)?.let { rive ->
                L.i("element: Rive (stage $stage, $part)")
                return rive
            }
        }
        val canvas = DuoCanvasView(context, part, appliedDesignStyle)
        canvas.start()
        L.i("element: Canvas (stage $stage, $part)")
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
    private fun riveElement(root: View, part: DuoPart): DuoElement? {
        if (!root.isHardwareAccelerated) {
            L.w("status bar window is not hardware accelerated - Rive needs a Surface, using Canvas")
            DuoSettingsClient.reportFallback(context, "no hardware acceleration on ${android.os.Build.MODEL}; using the simple drawing")
            return null
        }
        if (!guard.riveAllowed()) {
            DuoSettingsClient.reportFallback(context, "Rive disabled after earlier failures; using the simple drawing")
            return null
        }
        guard.noteRiveAttempt()
        val rive = DuoRiveView(context, part, appliedDesignStyle)
        if (!rive.start()) {
            // A clean failure, not a death: hand the attempt back so the breaker only counts crashes.
            guard.clearRiveAttempts()
            rive.teardown()
            DuoSettingsClient.reportFallback(context, "Rive could not start on ${android.os.Build.MODEL}; using the simple drawing")
            return null
        }
        return rive
    }

    /** Once the drawing has survived a few seconds, clear the counter: the breaker tracks deaths only. */
    private fun forgetAttemptsAfterSurvival(element: DuoElement) {
        if (element !is DuoRiveView) return
        cancelSurvivalTimer()
        val view = element.ui
        val check = object : Runnable {
            override fun run() {
                if (survivalRunnable !== this) return
                survivalView = null
                survivalRunnable = null
                try {
                    if (this@DuoIconHost.element === element && element.isReady) {
                        guard.clearRiveAttempts()
                    }
                } catch (t: Throwable) {
                    L.w("survival timer: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
        }
        survivalView = view
        survivalRunnable = check
        try {
            view.postDelayed(check, SURVIVAL_MS)
        } catch (t: Throwable) {
            L.w("survival timer schedule: ${t.javaClass.simpleName}: ${t.message}")
            cancelSurvivalTimer()
        }
    }

    /** Invalidates the pending survival check; teardown must not leave it referencing a dead view. */
    private fun cancelSurvivalTimer() {
        val view = survivalView
        val check = survivalRunnable
        if (view != null && check != null) {
            try {
                view.removeCallbacks(check)
            } catch (t: Throwable) {
                L.w("survival timer cancel: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        survivalView = null
        survivalRunnable = null
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
        // Never hide the stock icons over an element that is not drawing yet: a layout pass can arrive
        // before Rive has bound its view model, and hiding then would leave a blank stretch of status bar.
        if (!(element?.isReady ?: false)) return
        if (element?.ui?.let { !hasBox(it) } == true) {
            L.w("Duo box: 0x0 on reapply - restoring stock icons")
            hider.restore()
            return
        }
        if (!ensureElementAttached()) {
            // Never leave a hole: if the element cannot live in the rebuilt strip, the stock icons come back
            // rather than an empty stretch of status bar. The next hide pass remembers them again.
            L.w("element could not be re-attached - restoring the stock icons instead of leaving a gap")
            hider.restore()
            return
        }
        // Re-read after the re-attach: rotation replaces the strip, so the one captured before it points
        // at the dead container and the hide pass would miss the live icons.
        val strip = hideStrip ?: host
        if (strip == null) {
            L.w("no icon strip after re-attach - restoring the stock icons instead of leaving a gap")
            hider.restore()
            return
        }
        try {
            rememberAnchorVisibility()
            // Hide in the strip, never in the element's own (overlay) parent.
            val ring = element?.ui
            hideStock(
                strip,
                if (ring != null && ring.parent === strip) ring else null,
                if (overlay) anchorBattery else null
            )
            hideOverlayAnchor()
            publishStockIcons()
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
        if (holding || repairing) return true
        val view = element?.ui ?: return true
        val target = host ?: return false
        val iconsDetached = indicators != null &&
            (indicators?.ui?.parent == null || indicators?.ui?.isAttachedToWindow == false)
        val spacerDetached = ringSpacer != null &&
            (ringSpacer?.parent == null || ringSpacer?.isAttachedToWindow == false)
        if (view.parent === target && view.isAttachedToWindow && !iconsDetached && !spacerDetached) {
            // The bar may not have been measured at attach. One retry, after it has a width, and then
            // stop: the layout listener runs on every clock tick and must not move the ring again.
            val bar = root
            if (ringLocked() && lockFrame == null && !lockAttempted && bar != null && bar.width > 0) {
                lockAttempted = true
                view.post { if (ringLocked() && lockFrame == null) applyLayout() }
            }
            publishStockIcons()
            return true
        }
        if (view.parent === target && view.isAttachedToWindow && (iconsDetached || spacerDetached)) {
            repairStrip()
            publishStockIcons()
            return true
        }
        return try {
            (view.parent as? ViewGroup)?.removeView(view)
            val barRoot = root
            val fresh = barRoot?.let { finder.findStatusIconsHost(it) } ?: hideStrip ?: target
            hideStrip = fresh
            val combined = if (overlayOnCombined && barRoot != null) {
                finder.findCombinedStatusView(barRoot)
            } else {
                null
            }
            val parent = when {
                combined != null -> {
                    // A fresh CombinedStatusView after a rotation/re-inflate: re-anchor on it, in the bar's
                    // own layer, exactly as the first attach did.
                    if (anchorBattery !== combined) anchorOriginalVisibility = null
                    anchorBattery = combined
                    barRoot?.let { finder.findOverlayContainer(fresh, it) } ?: fresh
                }
                overlay && barRoot != null -> {
                    anchorBattery = finder.findBattery(fresh) ?: anchorBattery
                    finder.chooseElementParent(fresh, barRoot)
                }
                else -> fresh
            }
            host = parent
            flowHost = parent
            val side = geometry.sidePx(parent, barRoot)
            view.layoutParams = layoutParamsFor(
                parent, side, RingGeometry.elementHeightPx(side), ringInset(side)
            )
            ensureInjectedViewId(view, parent)
            parent.addView(view)
            indicators?.ui?.let { icon ->
                (icon.parent as? ViewGroup)?.removeView(icon)
                icon.layoutParams = layoutParamsFor(
                    parent, side, RingGeometry.elementHeightPx(side), edgeInsetPx(side, 0)
                )
                val index = parent.indexOfChild(view)
                ensureInjectedViewId(icon, parent)
                if (index >= 0) parent.addView(icon, index + 1) else parent.addView(icon)
            }
            rememberAnchorVisibility()
            applyLayout()
            syncIndicators()
            L.i("element re-attached into ${parent.javaClass.simpleName} after the strip was rebuilt")
            publishStockIcons()
            true
        } catch (t: Throwable) {
            L.w("re-attach failed: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    /**
     * The locked ring stays on the status bar when the icon strip is rebuilt. Only the spacer and the
     * icon cluster lived in that strip, so they are put back and the ring's translation is left alone.
     */
    private fun repairStrip() {
        if (repairing) return
        repairing = true
        try {
            val barRoot = root ?: return
            val fresh = finder.findStatusIconsHost(barRoot) ?: return
            if (!fresh.isAttachedToWindow) return
            hideStrip = fresh
            if (!overlay) flowHost = fresh
            if (ringLocked() && !overlay) ensureSpacer(fresh, geometry.sidePx(fresh, barRoot))
            val icons = indicators
            if (icons != null && (icons.ui.parent == null || !icons.ui.isAttachedToWindow)) {
                try {
                    icons.teardown()
                } catch (_: Throwable) {
                }
                indicators = null
            }
            syncIndicators()
        } finally {
            repairing = false
        }
    }

    /** Removes the element and puts the stock icons back exactly as they were. */
    fun teardown() {
        clearShadeRetry()
        cancelSurvivalTimer()
        cancelMainHandoff()
        try {
            drop(indicators, host)
            indicators = null
            element?.let { host?.removeView(it.ui) }
            element?.teardown()
        } catch (_: Throwable) {
        }
        for (slot in extras) {
            try {
                drop(slot.indicators, slot.container)
                slot.indicators = null
                slot.element?.let { slot.container?.removeView(it.ui) }
                slot.element?.teardown()
                slot.stock.restore()
            } catch (_: Throwable) {
            }
        }
        extras.clear()
        panelStateKnown = false
        panelOwner = null
        shadeOpen = false
        lastSurfaceOwner = null
        mainBarVisible = true
        dropSpacer()
        element = null
        hider.restore()
        // The One UI 8 anchor is hidden directly (it is not in the strip the hider restores), so its own
        // visibility is put back here or the stock cluster would be left invisible after teardown.
        restoreAnchorVisibility()
        clock.restore()
        geometry.reset()
        host = null
        hideStrip = null
        overlay = false
        overlayOnCombined = false
        anchorBattery = null
        flowHost = null
        lockFrame = null
        lockAttempted = false
        repairing = false
        holding = false
        lastStockIconJson = null
        root = null
    }

    /** FR-03b: the same hide pass for every extra bar, once its own element is drawing. */
    private fun reapplyExtraHiding() {
        val stale = ArrayList<ExtraBar>()
        for (slot in extras) {
            val target = slot.container ?: continue
            val keep = slot.element?.ui ?: continue
            // A rebuilt bar (or a detached shade window) leaves the old element behind; never hide for it.
            if (keep.parent !== target || !keep.isAttachedToWindow) {
                stale.add(slot)
                continue
            }
            if (!(slot.element?.isReady ?: false)) continue
            if (!surfaceEnabled(slot.name)) {
                // The user disabled this surface; leave the stock header exactly as it was, not a
                // hidden-stock hole behind an invisible Duo. Hard-zero: no fade may re-show it.
                slot.alphaRequest = null
                slot.element?.ui?.animate()?.cancel()
                slot.indicators?.ui?.animate()?.cancel()
                slot.element?.ui?.alpha = 0f
                slot.indicators?.ui?.alpha = 0f
                slot.stock.restore()
                continue
            }
            try {
                hideSlotStock(slot, target, keep)
                // Do not re-anchor here. This pass runs on every layout tick while the shade/QS is
                // dragged; re-anchoring the ring alone made it follow the battery while the split
                // indicator view stayed put (reported: ring moves, 5G does not). The ring keeps the
                // box it got at attach; the surface is hidden during the drag and shown at settle.
            } catch (t: Throwable) {
                L.w("${slot.name} hiding: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        // Drop outside the iteration: dropSlot mutates the list this loop walks.
        for (slot in stale) {
            try {
                dropSlot(slot)
            } catch (t: Throwable) {
                L.w("${slot.name} stale drop: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    // ----------------------------------------------------------------------------- internals

    /**
     * Records the overlay anchor's visibility before anything hides it. The stock hider may hide the
     * same battery afterwards; capturing here, before both passes, keeps the original (a post-hide
     * INVISIBLE here would leave the battery missing after teardown).
     */
    private fun rememberAnchorVisibility() {
        if (!overlay) return
        val anchor = anchorBattery ?: return
        if (anchorOriginalVisibility != null) return
        try {
            anchorOriginalVisibility = anchor.visibility
        } catch (t: Throwable) {
            L.w("anchor remember: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Puts the overlay anchor back from the one value captured before the first hide. */
    private fun restoreAnchorVisibility() {
        try {
            anchorOriginalVisibility?.let { anchorBattery?.visibility = it }
        } catch (t: Throwable) {
            L.w("anchor restore: ${t.javaClass.simpleName}: ${t.message}")
        }
        anchorOriginalVisibility = null
    }

    /**
     * Keeps the overlay anchor out of sight without tearing down its layout.
     *
     * On the One UI 8 path the anchor (`CombinedStatusView`) is not a child of the hidden strip, so the
     * strip's hide pass never reaches it — this is what actually removes the stock cluster. On the other
     * overlay paths the anchor is a child of the strip and is already set INVISIBLE by the hide pass, so
     * this is a harmless re-assertion.
     */
    private fun hideOverlayAnchor() {
        if (!overlay) return
        val anchor = anchorBattery ?: return
        try {
            rememberAnchorVisibility()
            // The ROM can re-show the stock battery/cluster on its own - an OEM charging animation
            // re-inflates the bar when the charger is plugged in - and this is the only moment we see it.
            // The line is the evidence for "the battery appears over Duo while charging".
            if (anchor.visibility == View.VISIBLE) {
                logOnce.once("anchorReshown") {
                    L.i("stock anchor ${anchor.javaClass.simpleName} was re-shown by the ROM - hiding it again")
                }
            }
            anchor.visibility = View.INVISIBLE
        } catch (t: Throwable) {
            L.w("anchor hide: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /**
     * Moves [view] so its centre sits on the battery's centre, measured in the element parent's
     * coordinates. The battery is hidden with INVISIBLE (not GONE) in overlay mode, so its frame is real
     * and survives a rotation; [offsetDp] is the user's horizontal nudge.
     */
    private fun anchorOnBattery(
        view: View,
        parent: ViewGroup,
        side: Int,
        offsetDp: Int,
        battery: View? = anchorBattery,
        alignEnd: Boolean = false,
        /** Physical-pixel burn-in nudge; only the main ring passes a non-zero value. */
        burnIn: Float = 0f
    ) {
        battery ?: return
        try {
            val parentLocation = IntArray(2)
            val batteryLocation = IntArray(2)
            parent.getLocationInWindow(parentLocation)
            battery.getLocationInWindow(batteryLocation)
            val density = context.resources.displayMetrics.density
            val height = RingGeometry.elementHeightPx(side)
            // The overlay parent is a FrameLayout, so a start margin is ignored: the edge-spacing
            // setting is applied as translation here instead. Default 100 % is a zero inset, so an
            // untouched device does not move; on HyperOS 3 a lower value now actually pulls the element
            // left off the battery (reported: "icon is all the way to the right", with no effect).
            // The QS cluster asks for [alignEnd]: its element must end where the battery ends, not be
            // centred, because the cluster is 386 px wide and Duo keeps the main bar's size.
            val seat: Float = if (alignEnd) {
                (batteryLocation[0] + battery.width - side - parentLocation[0]).toFloat()
            } else {
                batteryLocation[0] + battery.width / 2f - parentLocation[0] - side / 2f
            }
            view.translationX = seat + offsetDp * density + edgeInsetPx(side, settings.edgePadding) +
                burnIn
            // height/2 is the view centre; the ring sits below that, so the anchor shift brings the
            // ring (not the empty space above it) onto the battery.
            view.translationY = batteryLocation[1] + battery.height / 2f - parentLocation[1] -
                    height / 2f + RingGeometry.ringAnchorShiftY(side)
        } catch (t: Throwable) {
            L.w("overlay anchor: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /**
     * Starts (or keeps) one bounded retry for the QS cluster, posted on the shade window when it is
     * known (otherwise on the header).
     *
     * The header is attached long before the shade expands, and its own cluster is 0x0/GONE until then
     * (measured). A short delayed recheck is used instead of hiding the stock battery inside an
     * unlaid-out view; every tick re-resolves the *visible* cluster from [window], because the header's
     * instance is a different, permanently dead one. The retry stops after [MAX_SHADE_RETRIES] or
     * [SHADE_RETRY_TIMEOUT_MS], on success, detach, or teardown.
     */
    private fun scheduleShadeRetry(header: View, window: View?) {
        startShadeRetry(window ?: header, "window=${window != null}") {
            onShadeRetryTick(header, window)
        }
    }

    /**
     * The same bounded retry for a cluster handed over directly by an OnePlus vendor hook. Its anchor is
     * the cluster itself and every tick asks that exact view whether it has been laid out yet; the
     * generic fallback's re-resolution from the shade window would miss the split header's sibling
     * battery and could choose the dead 0x0 duplicate.
     */
    private fun scheduleShadeRetryForCluster(cluster: ViewGroup, battery: View?, source: String) {
        startShadeRetry(cluster, "source=$source") {
            onShadeClusterRetryTick(cluster, battery, source)
        }
    }

    /**
     * The shared bounded delayed retry: attempts, attachment and wall time are tracked in one place so
     * the generic header resolution and the OnePlus cluster hand-over cannot run unlimited loops.
     */
    private fun startShadeRetry(anchor: View, label: String, tick: () -> Unit) {
        // A retry for this exact anchor is already live; re-posting it here is what used to duplicate
        // ticks and reset the counters. Its own pending post (or the tick that is running now) covers it.
        if (shadeRetryView === anchor && shadeRetryRunnable != null) return
        val sameAnchor = shadeRetryView === anchor
        val attempts = shadeRetryAttempts
        val startedAt = shadeRetryStartedAt
        val seenAttached = if (sameAnchor) shadeRetrySeenAttached else anchor.isAttachedToWindow
        cancelShadeRetryCallbacks()
        val retry = object : Runnable {
            override fun run() {
                if (shadeRetryRunnable !== this) return
                try {
                    shadeRetryAttempts++
                    if (anchor.isAttachedToWindow) shadeRetrySeenAttached = true
                    if (!anchor.isAttachedToWindow && shadeRetrySeenAttached) {
                        ShadeProbe.log(
                            "shadeProbe retry: window detached after $shadeRetryAttempts attempts - stop"
                        )
                        clearShadeRetry()
                        return
                    }
                    val expired = SystemClock.uptimeMillis() - shadeRetryStartedAt >= SHADE_RETRY_TIMEOUT_MS
                    if (shadeRetryAttempts > MAX_SHADE_RETRIES || expired) {
                        ShadeProbe.log(
                            "shadeProbe retry: giving up after $shadeRetryAttempts attempts (expired=$expired)"
                        )
                        clearShadeRetry()
                        return
                    }
                    tick()
                } catch (t: Throwable) {
                    L.w("shade retry: ${t.javaClass.simpleName}: ${t.message}")
                    clearShadeRetry()
                }
            }
        }
        shadeRetryView = anchor
        shadeRetryRunnable = retry
        shadeRetryAttempts = attempts
        shadeRetryStartedAt = if (startedAt > 0L) startedAt else SystemClock.uptimeMillis()
        shadeRetrySeenAttached = seenAttached || anchor.isAttachedToWindow
        try {
            // A re-schedule must never leave a second copy of the same callback queued.
            anchor.removeCallbacks(retry)
            anchor.postDelayed(retry, SHADE_RETRY_DELAY_MS)
            ShadeProbe.log(
                "shadeProbe retry: scheduled (${SHADE_RETRY_DELAY_MS}ms, max $MAX_SHADE_RETRIES, $label)"
            )
        } catch (t: Throwable) {
            L.w("shade retry schedule: ${t.javaClass.simpleName}: ${t.message}")
            clearShadeRetry()
        }
    }

    /** One bounded retry pass. Re-resolves the visible cluster, attaches once it exists, then stops. */
    private fun onShadeRetryTick(header: View, window: View?) {
        val retry = shadeRetryRunnable ?: return
        val anchor = shadeRetryView ?: return
        // Re-resolve on every tick: the header's own `quick_qs_status_icons` (split_shade_status_bar)
        // is the 0x0 duplicate that kept hiding the retry from the laid-out one in qs_frame.
        val cluster = window?.let { finder.findVisibleQuickQsCluster(it) }
        ShadeProbe.log(
            "shadeProbe reResolve $shadeRetryAttempts/$MAX_SHADE_RETRIES: " +
                "cluster=${ShadeProbe.describe(cluster)}"
        )
        if (cluster != null) {
            ShadeProbe.log(
                "shadeProbe retry $shadeRetryAttempts/$MAX_SHADE_RETRIES: visible cluster - attaching"
            )
            if (attachShadeHeader(header, window)) {
                clearShadeRetry()
                return
            }
        } else if (window == null) {
            // No shade window was handed over (an early header hook): keep the old header-only check.
            val area = finder.findShadeIconsArea(header)
            if (area != null && area.width > 0 && area.visibility == View.VISIBLE) {
                ShadeProbe.log(
                    "shadeProbe retry $shadeRetryAttempts/$MAX_SHADE_RETRIES: header cluster visible - attaching"
                )
                if (attachShadeHeader(header)) {
                    clearShadeRetry()
                    return
                }
            }
        }
        ShadeProbe.log(
            "shadeProbe retry $shadeRetryAttempts/$MAX_SHADE_RETRIES: cluster not visible yet - waiting"
        )
        // A failed attach may already have re-scheduled (possibly on a different anchor); only this
        // runnable may post itself, and only while it is still the current one.
        if (shadeRetryRunnable !== retry) return
        try {
            anchor.removeCallbacks(retry)
            anchor.postDelayed(retry, SHADE_RETRY_DELAY_MS)
        } catch (t: Throwable) {
            L.w("shade retry post: ${t.javaClass.simpleName}: ${t.message}")
            clearShadeRetry()
        }
    }

    /**
     * One bounded retry pass for a vendor-handled cluster. Only the exact view is checked: once it has a
     * real width the cluster is handed back to [attachShadeCluster]; if the main bar is not ready yet
     * that call schedules the same retry again, still bounded by [startShadeRetry].
     */
    private fun onShadeClusterRetryTick(cluster: ViewGroup, battery: View?, source: String) {
        val retry = shadeRetryRunnable ?: return
        if (shadeClusterReady(cluster.width, cluster.visibility, cluster.isAttachedToWindow, cluster.isShown)) {
            ShadeProbe.log(
                "shadeProbe retry $shadeRetryAttempts/$MAX_SHADE_RETRIES: $source cluster laid out - attaching"
            )
            if (attachShadeCluster(cluster, battery, source)) {
                clearShadeRetry()
                return
            }
        }
        ShadeProbe.log(
            "shadeProbe retry $shadeRetryAttempts/$MAX_SHADE_RETRIES: $source cluster not laid out - waiting"
        )
        if (shadeRetryRunnable !== retry) return
        try {
            cluster.removeCallbacks(retry)
            cluster.postDelayed(retry, SHADE_RETRY_DELAY_MS)
        } catch (t: Throwable) {
            L.w("shade cluster retry post: ${t.javaClass.simpleName}: ${t.message}")
            clearShadeRetry()
        }
    }

    /** Drops the pending callback without touching the bounded-attempt counters. */
    private fun cancelShadeRetryCallbacks() {
        val view = shadeRetryView
        val retry = shadeRetryRunnable
        if (view != null && retry != null) {
            try {
                view.removeCallbacks(retry)
            } catch (_: Throwable) {
            }
        }
        shadeRetryView = null
        shadeRetryRunnable = null
    }

    /** Removes the pending retry and ends its bounded lifecycle. Safe from a callback or teardown. */
    private fun clearShadeRetry() {
        cancelShadeRetryCallbacks()
        shadeRetryAttempts = 0
        shadeRetryStartedAt = 0L
        shadeRetrySeenAttached = false
    }

    private companion object {
        // box = structural validity (attached/visible/laid out) - used for readiness/attach/fallback;
        // drawable = ink/alpha on top of box - used for handoff ordering only.
        /** True only when the view is attached, visible and laid out (structural validity, no alpha). */
        internal fun hasBox(view: View): Boolean = try {
            view.isAttachedToWindow && view.visibility == View.VISIBLE && view.width > 0 && view.height > 0
        } catch (_: Throwable) { false }

        /** True only when the view is attached, visible, laid out and has ink to draw. */
        internal fun hasDrawableBox(view: View): Boolean = try {
            view.isAttachedToWindow && view.visibility == View.VISIBLE &&
                view.width > 0 && view.height > 0 && view.alpha > PanelHandoff.EPSILON
        } catch (_: Throwable) {
            false
        }

        const val SURVIVAL_MS = 4_000L

        /** Bounded retry for the panel hand-off guards: 6 frames, about 100 ms at 60 Hz. */
        const val HANDOFF_GUARD_MAX_FRAMES = 6

        /** Matches the ROM status-bar reveal animation (CollapsedStatusBarFragmentExImpl). */
        val MAIN_ALPHA_IN = PathInterpolator(0.4f, 0f, 1f, 1f)

        /** Decelerating x slide for the panel hand-off; alpha and y are not part of it. */
        val HANDOFF_X = PathInterpolator(0.2f, 0f, 0f, 1f)

        /** Short recheck for the QS cluster, bounded by count and wall time (measured 0x0 at attach). */
        const val SHADE_RETRY_DELAY_MS = 50L
        const val MAX_SHADE_RETRIES = 60
        const val SHADE_RETRY_TIMEOUT_MS = 3_000L
    }
}
