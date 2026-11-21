package io.github.kvmy666.duostatusbar.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.rom.RomDetection
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.roundToInt

/** One battery snapshot read from the sticky `ACTION_BATTERY_CHANGED` broadcast. */
internal data class IosBatteryReading(
    /** Charge percent, already divided by the broadcast's scale so it is always 0..100. */
    val level: Int,
    val plugged: Boolean,
    val status: Int
)

/**
 * The sticky battery broadcast, or null when it cannot be read.
 *
 * A null read must never be turned into 0 %: the caller keeps the previous snapshot instead, so a
 * provider that is momentarily unreadable cannot draw an empty battery (M7/FR-21).
 */
internal fun readStickyBattery(context: Context): IosBatteryReading? = try {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val raw = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    if (intent == null || raw < 0) {
        null
    } else {
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        IosBatteryReading(
            level = raw * 100 / scale,
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
        )
    }
} catch (_: Throwable) {
    null
}

/**
 * One size owner for every iOS surface (M15).
 *
 * The home bar pins each kind's stock size once; the lock screen and the shade header reuse it, so the
 * glyph cannot be drawn larger on the lock screen than on the home bar (the reported bug). Only the
 * extra bar's height caps it, proportionally: an extra bar is never allowed to grow the glyph.
 *
 * Pure and allocation-free: the host calls it on a layout pass, and it never touches a view (C2).
 */
internal class IosSizeOwner {

    private val pinned = HashMap<IosIconKind, IntArray>()
    private var pinnedBlock: IntArray? = null

    /** Records the main bar's stock size for [kind]. A non-positive size is not a measurement. */
    fun pin(kind: IosIconKind, width: Int, height: Int) {
        if (width > 0 && height > 0) pinned[kind] = intArrayOf(width, height)
    }

    /** Records the main bar's union size for the stacked two-SIM block. */
    fun pinBlock(width: Int, height: Int) {
        if (width > 0 && height > 0) pinnedBlock = intArrayOf(width, height)
    }

    /**
     * The glyph size for a stock icon measuring [ownWidth] x [ownHeight].
     *
     * No pin, or an unusable measurement, returns this bar's own size (clamped at zero). Otherwise the
     * main-bar size is reused; when this bar is shorter, both dimensions scale with its height so the
     * aspect ratio is kept. Zero and negative inputs are safe and never produce a negative size.
     */
    fun sizeFor(kind: IosIconKind, ownWidth: Int, ownHeight: Int): Pair<Int, Int> =
        fit(pinned[kind], ownWidth, ownHeight)

    /** The stacked block's size for an extra bar, using the same M15 math as [sizeFor]. */
    fun sizeForBlock(ownWidth: Int, ownHeight: Int): Pair<Int, Int> =
        fit(pinnedBlock, ownWidth, ownHeight)

    private fun fit(main: IntArray?, ownWidth: Int, ownHeight: Int): Pair<Int, Int> {
        val own = Pair(ownWidth.coerceAtLeast(0), ownHeight.coerceAtLeast(0))
        if (main == null) return own
        if (ownWidth <= 0 || ownHeight <= 0) return own
        if (main[0] <= 0 || main[1] <= 0) return own
        if (ownHeight >= main[1]) return Pair(main[0], main[1])
        val width = (main[0].toLong() * ownHeight / main[1]).toInt().coerceAtLeast(0)
        return Pair(width, ownHeight)
    }
}

/**
 * The pure liveness decision behind [IosIconHost.attachedAlive].
 *
 * An attachment is alive for a root only while it exists, its strip is still in a window, and that
 * strip belongs to the same window root. Comparing the window root (not the view path) is what stops
 * the shade loop: the shade header and the fallback strip are in the same shade window.
 */
internal object IosAttachDecision {
    fun alive(
        isAttached: Boolean,
        stripAttached: Boolean,
        layerAttached: Boolean,
        stripRoot: View?,
        rootRoot: View?
    ): Boolean = isAttached && stripAttached && layerAttached && stripRoot != null &&
        rootRoot != null && stripRoot === rootRoot
}

/**
 * One glyph and the stock icon(s) it stands in for.
 *
 * A normal icon has one stock/skin/subId; the two-SIM cluster has two and draws one stacked element
 * anchored to the data slot's bounds (the other slot is suppressed). [geometry] holds the measured
 * content views whose bounds position the glyph (the outer itself for unmeasured families, the content
 * group/icon for the measured OPLUS outers).
 */
private class IosGlyphRecord(
    val kind: IosIconKind,
    val stocks: List<View>,
    val strategies: List<IosSkinStrategy>,
    val subIds: List<Int>,
    val skins: List<IosSkin>,
    var element: IosElement,
    var glyphWidth: Int,
    var glyphHeight: Int,
    var lastVisual: IosVisual? = null,
    /** Bit [i] is set when stock [i] is OEM-visible (not GONE/foreign-INVISIBLE). */
    var oemVisibleMask: Int = 0,
    var geometry: List<View> = emptyList()
) {
    val isCluster: Boolean get() = stocks.size > 1
}

/**
 * The per-stock-icon iOS host (IOS-B1).
 *
 * The stock view stays exactly where the ROM put it: the host reads its bounds and visibility, adds one
 * transparent [FrameLayout] layer over the bar, and draws one [IosElement] glyph over each stock icon.
 * The stock drawing is suppressed leaf-first by [IosSkin]; nothing here resizes, hides or re-binds a
 * stock view (C2). Every failure restores the stock icon and draws nothing (rule 5). The only renderer
 * is the platform-Canvas [IosCanvasView]; no Rive view is created inside SystemUI (IOS-R2 0c).
 *
 * ## Drawing gate
 *
 * [IosRenderers.DRAWING_READY] is the capability switch. [drawingReady] defaults to it and is the
 * **only** way attach proceeds; if a build ships without the drawing, nothing is created, hidden or
 * measured. Even past the gate, an element must report [IosElement.canDraw] and start successfully
 * before a stock icon is suppressed, so a renderer that cannot draw can never blank a slot. The
 * parameter is a test capability seam.
 *
 * ## Size ownership (M15)
 *
 * The main bar (`isMainBar`) pins each kind's stock size in the shared [sizeOwner]; extra surfaces pass
 * the same owner and draw at [IosSizeOwner.sizeFor]. A live glyph is never resized: a size change tears
 * that glyph down and recreates it, while a position change only moves its margins.
 *
 * ## State
 *
 * The stock view supplies slot, bounds, visibility and the SIM id (`getSubId()`); the visual comes from
 * Duo's readers ([IosMapping]): sticky battery, [DeviceStateReader], [WifiReader] (validated only, M6)
 * and [CellReader]. Events are debounced through the main handler; there is no polling loop. Screen-off
 * pauses every glyph (C8) and screen-on resumes it; teardown removes everything and restores the skin.
 */
internal class IosIconHost(
    private val context: Context,
    private val sizeOwner: IosSizeOwner = IosSizeOwner(),
    private val isMainBar: Boolean = true,
    private val canvasRenderers: (Context, IosIconKind) -> IosElement = IosRenderers::canvas,
    private val drawingReady: Boolean = IosRenderers.DRAWING_READY,
    /**
     * The surface this host serves, keyed into [IosSurfaceThrash]. The IOS-REPLACE overlay delegate
     * passes a name derived from its parent so a new delegate instance cannot reset the parent's guard.
     */
    private val surface: String = "overlay",
    private val batterySource: (Context) -> IosBatteryReading? = ::readStickyBattery,
    private val cellSource: (Context) -> CellReading = { CellReader.read(it, airplane = false) },
    private val wifiSource: (Context, Int, Boolean, Boolean, Boolean) -> WifiSample =
        { ctx, level, on, active, validated ->
            WifiReader.sample(ctx, level, on, active, validated)
        },
    private val idleDelayMs: Long = IDLE_MS
) {

    private companion object {
        const val REFRESH_DEBOUNCE_MS = 120L
        const val IDLE_MS = 3_000L
        const val CLUSTER_RETRY_MS = 400L
        const val CLUSTER_MAX_ATTEMPTS = 5
        const val ATTACH_RETRY_MS = 400L
        const val ATTACH_MAX_ATTEMPTS = 5
        const val LOW_THRESHOLD_RESOURCE = "config_lowBatteryWarningLevel"
    }

    private val logOnce = LogOnce()
    private val handler = Handler(Looper.getMainLooper())
    private val records = ArrayList<IosGlyphRecord>()

    /** Stock views whose failure is deterministic and cannot heal without a new view. */
    private val unskinnable: MutableSet<View> =
        Collections.newSetFromMap(IdentityHashMap<View, Boolean>())

    /** Every view carrying [layoutListener], so teardown removes exactly what attach added. */
    private val layoutHosts = ArrayList<View>()

    private var isAttached = false
    val attached: Boolean get() = isAttached

    private var settings = ModuleSettings.DEFAULT
    private var strip: ViewGroup? = null
    private var layer: FrameLayout? = null
    private var receiverRegistered = false
    private var updating = false

    private var renderersActive = true
    private var screenPaused = false
    private var clusterRetry: Runnable? = null
    private var attachRetry: Runnable? = null
    private var attachAttempts = 0

    /** The cellular decision from the last refresh, so a layout pass can see a missing glyph. */
    private var lastCellPlan: IosCellPlan = IosCellPlan.None

    /** Which cell slots were OEM-visible at the last refresh/layout pass (unrecorded slots too). */
    private var lastCellVisibleMask = 0

    private var cachedLowThreshold: IosMapping.LowThreshold? = null
    private var lastWifiLevel = 0
    private var lastWifiOn = false
    private var lastWifiActive = false
    private var lastWifiValidated = false

    /** IOS-LAG: attach/teardown churn backoff, shared per surface so a new host cannot clear it. */
    private val thrash: ThrashGuard get() = IosSurfaceThrash.lifecycle(surface)

    private val refreshRunnable = Runnable { refresh() }

    private val idleRunnable = Runnable {
        renderersActive = false
        setElementsActive(false)
    }

    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        onLayoutChanged()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED,
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED,
                    ConnectivityManager.CONNECTIVITY_ACTION -> scheduleRefresh()

                    Intent.ACTION_SCREEN_OFF -> {
                        handler.removeCallbacks(refreshRunnable)
                        pauseRenderers()
                    }

                    Intent.ACTION_SCREEN_ON -> {
                        if (isLayerVisible()) {
                            activateRenderers()
                            scheduleRefresh()
                        }
                    }

                    else -> Unit
                }
            } catch (t: Throwable) {
                logOnce.once("receiver") {
                    L.w("iOS host receiver: ${t.javaClass.simpleName}: ${t.message}")
                }
            }
        }
    }

    private val finder: ContainerFinder by lazy {
        ContainerFinder(
            context,
            RomDetection.forThisRom(
                Build.MANUFACTURER.orEmpty(),
                Build.BRAND.orEmpty(),
                Build.PRODUCT.orEmpty(),
                Build.DISPLAY.orEmpty(),
                Build.VERSION.SDK_INT
            ),
            logOnce
        )
    }

    /**
     * Attaches over the home status bar. Returns false and leaves every stock icon untouched when the
     * drawing gate is closed, the strip cannot be found, the overlay layer cannot be placed, or no icon
     * could be safely suppressed.
     */
    fun attach(root: View): Boolean {
        if (isAttached && !reattachNeeded(root, null)) return true
        if (isAttached) teardown()
        if (!drawingReady) {
            drawingNotReadyGate()
            return false
        }
        val result = try {
            val strip = finder.findStatusIconsHost(root)
            if (strip == null) {
                logOnce.once("no-strip") {
                    L.i("iOS host: no status icon strip - stock icons untouched")
                }
                false
            } else {
                attachToStrip(null, strip, root)
            }
        } catch (t: Throwable) {
            failAttach(t)
            return false
        }
        return finishAttach(result) { attach(root) }
    }

    /**
     * Attaches over an extra surface (keyguard, shade header) by looking the strip up through its
     * resource entry name. [name] is only used for diagnostics. The caller passes the same [IosSizeOwner]
     * as the home bar so every surface draws the home bar's size (M15).
     */
    fun attachSurface(name: String, root: View, stripId: String = "system_icons"): Boolean {
        // IOS-LAG: a tripped guard backs off until a genuinely different window appears. No lookup,
        // no teardown and no attach while the same root is churning.
        if (IosSurfaceThrash.isTripped(surface)) {
            // A null root is an unknown window, not a new one: keep backing off rather than retrying.
            val tripRoot = IosSurfaceThrash.rootOf(surface)
            val same = tripRoot == null || root.rootView === tripRoot
            if (same) return false
            resetThrash() // a different window is a real change
        }
        if (isAttached && !reattachNeeded(root, stripId)) return true
        if (isAttached) teardown()
        // The teardown above is itself a lifecycle event and may have tripped the guard.
        if (IosSurfaceThrash.isTripped(surface)) return false
        if (!drawingReady) {
            drawingNotReadyGate()
            return false
        }
        val result = try {
            val strip = findByEntryName(root, stripId) as? ViewGroup
            if (strip == null) {
                logOnce.once("no-strip:$name:$stripId") {
                    L.i("iOS host $name: $stripId not found - stock icons untouched")
                }
                false
            } else {
                attachToStrip(name, strip, root)
            }
        } catch (t: Throwable) {
            failAttach(t)
            return false
        }
        return finishAttach(result) { attachSurface(name, root, stripId) }
    }

    /**
     * True when the current attachment still belongs to [root]'s window. Read-only and side-effect
     * free; the caller uses it to skip a re-attach that would tear down a live shade host (iOS-LAG).
     */
    internal fun attachedAlive(root: View): Boolean =
        IosAttachDecision.alive(
            isAttached = isAttached,
            stripAttached = strip?.isAttachedToWindow == true,
            layerAttached = layer?.parent != null,
            stripRoot = strip?.rootView,
            rootRoot = root.rootView
        )

    /**
     * True while this host's attachment really lives under [root]: attached flag, a live strip, a layer
     * still parented, and the strip below the same root. Read-only and side-effect free.
     */
    internal fun attachedIn(root: View): Boolean =
        isAttached && strip?.isAttachedToWindow == true && layer?.parent != null &&
            strip?.let { isUnder(it, root) } == true

    /**
     * True when [stripId] resolves under [root] and has a plain `FrameLayout` overlay ancestor, so an
     * attach would not fail on lookup. Read-only; any failure is "not attachable" (rule 5).
     */
    internal fun canAttachSurface(root: View, stripId: String): Boolean = try {
        val found = findByEntryName(root, stripId) as? ViewGroup
        found != null && finder.findOverlayContainer(found, root) is FrameLayout
    } catch (_: Throwable) {
        false
    }

    /** Test seam: the anti-thrash trip state, so a regression test can prove icon churn did not clear it. */
    internal fun isThrashTrippedForTest(): Boolean = IosSurfaceThrash.isTripped(surface)

    /**
     * Test seam: attaches into an explicit strip/overlay pair, skipping the ROM-specific strip and
     * overlay resolution. Production enters through [attach]/[attachSurface]; this pins the per-icon
     * path (bounds, skin, size) against a synthetic tree without a real SystemUI.
     */
    internal fun attachIn(strip: ViewGroup, overlayParent: FrameLayout): Boolean {
        if (isAttached) {
            if (this.strip === strip) return true
            // A different strip/root is a replaced surface (rotation, keyguard/shade recreation): the
            // old layer and suppression belong to a dead tree and must be torn down first (F2).
            teardown()
        }
        if (!drawingReady) {
            drawingNotReadyGate()
            return false
        }
        val result = try {
            attachOverStrip(null, strip, overlayParent)
        } catch (t: Throwable) {
            failAttach(t)
            return false
        }
        return finishAttach(result) { attachIn(strip, overlayParent) }
    }

    /** Stores the user's iOS options and repaints from the last snapshot. */
    fun refreshSettings(settings: ModuleSettings) {
        if (this.settings != settings) resetThrash()
        this.settings = settings
        refresh()
    }

    /**
     * A stock icon was added to the strip after attach (SIM on, Wi-Fi validated later, a recovered
     * battery read). Only the debounced refresh is scheduled: a hook callback never touches a view
     * (C2), and reconciliation inside [refresh] decides whether the icon can be adopted.
     *
     * This deliberately does **not** clear a tripped guard: the ROM repopulates the strip after every
     * hide pass, so an icon add is part of the very loop the guard backs off from (IOS-LAG).
     */
    fun onStockIconAdded(view: View) {
        if (!isAttached) return
        unskinnable.remove(view)
        val strip = strip ?: return
        if (!isInStrip(view, strip)) return
        scheduleRefresh()
    }

    /**
     * A stock icon was removed from the strip after attach. Its record is dropped and the stock
     * drawing restored; if the same view returns, [onStockIconAdded] re-adopts it once the reading
     * is valid again.
     *
     * This runs from an `afterHookedMethod` on `removeView`, so the view may already be detached and
     * no longer reachable from [strip]. A strip membership check would therefore make the call a
     * no-op; the record's stock identity is the guard instead.
     *
     * Like [onStockIconAdded], this does **not** clear a tripped guard: a remove can be part of the
     * same repopulate/hide loop, so only a real change may earn a retry (IOS-LAG).
     */
    fun onStockIconRemoved(view: View) {
        if (!isAttached) return
        unskinnable.remove(view)
        val record = records.firstOrNull { r -> r.stocks.any { it === view } } ?: return
        dropRecord(record, restore = true)
        // A lost half of the cluster must become a single row over the remaining slot.
        if (record.isCluster) scheduleRefresh()
    }

    /**
     * Reads one state snapshot, writes it to the matching glyphs and adopts any stock icon that is
     * still unrecorded but has a valid visual. Only the parts that have a stock icon are read, so a
     * bar with no Wi-Fi icon makes no Wi-Fi binder call. Never logs per refresh (m2) and never binds a
     * part's visual to another part's view.
     */
    fun refresh() {
        if (!isAttached) return
        try {
            val inventory = stockInventory()
            val hasBattery = records.any { it.kind == IosIconKind.BATTERY } ||
                inventory.any { it.kind == StockIconKind.BATTERY }
            val hasWifi = records.any { it.kind == IosIconKind.WIFI } ||
                inventory.any { it.kind == StockIconKind.WIFI }
            val cellViews = inventory.filter { it.kind == StockIconKind.CELLULAR }
            val hasCell = records.any { it.kind == IosIconKind.CELL } || cellViews.isNotEmpty()
            val tint = safeTint()
            val battery = if (hasBattery) batteryVisual(tint) else null
            val wifi = if (hasWifi) wifiVisual(tint) else null
            val cellReading = if (hasCell) readCell() else null
            val plan = cellPlanOf(cellReading, cellViews)
            lastCellPlan = plan
            val cell = IosMapping.cellVisual(plan, tint)

            for (record in records.toList()) {
                try {
                    val visual = when (record.kind) {
                        IosIconKind.BATTERY -> battery
                        IosIconKind.WIFI -> wifi
                        IosIconKind.CELL -> when {
                            record.isCluster && plan is IosCellPlan.Dual -> cell
                            !record.isCluster && plan is IosCellPlan.Single &&
                                record.subIds.firstOrNull() == plan.subId -> cell
                            else -> null
                        }
                    }
                    if (visual == null) {
                        // A null read is not a state. The battery keeps its last snapshot (M7); any
                        // other null (an unmappable cell, or a battery never read) restores the stock
                        // icon rather than leaving a suppressed-but-blank slot (rule 5).
                        if (record.kind == IosIconKind.BATTERY && record.lastVisual != null) continue
                        dropRecord(record, restore = true)
                        continue
                    }
                    if (!visual.visible) {
                        // The mapping deliberately hides this occupant (a connected-but-unvalidated
                        // Wi-Fi, M6). Restore the stock drawing so the system's own icon stays exactly
                        // as it drew it instead of suppressing it and rendering nothing.
                        dropRecord(record, restore = true)
                        continue
                    }
                    applyVisual(record, visual)
                } catch (t: Throwable) {
                    familyFailed(record, t)
                }
            }
            reconcile(inventory, battery, wifi, plan, cell)
            updateGlyphVisibility()
        } catch (t: Throwable) {
            // A failure mid-refresh must not leave a suppressed slot blank: restore every stock first,
            // then report once (F10).
            try {
                for (record in records.toList()) dropRecord(record, restore = true)
            } catch (_: Throwable) {
            }
            logOnce.once("refresh") {
                L.w("iOS host refresh: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /** The battery/Wi-Fi/cellular stock icons currently in the strip. Read-only. */
    private fun stockInventory(): List<StockIcon> {
        val inventory = strip?.let { StockIconKey.inventory(it) } ?: emptyList()
        return inventory.filter {
            it.kind == StockIconKind.BATTERY ||
                it.kind == StockIconKind.WIFI ||
                it.kind == StockIconKind.CELLULAR
        }
    }

    /**
     * Removes every glyph, listener, callback and receiver and restores every suppressed stock drawing
     * exactly. Idempotent; a second call does nothing. No stock icon is left hidden, transparent or
     * without its drawable (rule 5).
     */
    fun teardown() {
        val wasAttached = isAttached || layer != null
        val stripRootView = strip?.rootView
        clusterRetry?.let { handler.removeCallbacks(it) }
        clusterRetry = null
        attachRetry?.let { handler.removeCallbacks(it) }
        attachRetry = null
        attachAttempts = 0
        handler.removeCallbacks(idleRunnable)
        if (!isAttached && records.isEmpty() && layer == null && !receiverRegistered && layoutHosts.isEmpty()) {
            unskinnable.clear()
            lastCellPlan = IosCellPlan.None
            lastCellVisibleMask = 0
            return
        }
        isAttached = false
        handler.removeCallbacks(refreshRunnable)
        if (receiverRegistered) {
            receiverRegistered = false
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Throwable) {
            }
        }
        for (view in layoutHosts.toList()) {
            try {
                view.removeOnLayoutChangeListener(layoutListener)
            } catch (_: Throwable) {
            }
        }
        layoutHosts.clear()
        for (record in records.toList()) dropRecord(record, restore = true)
        records.clear()
        unskinnable.clear()
        val currentLayer = layer
        layer = null
        try {
            (currentLayer?.parent as? ViewGroup)?.removeView(currentLayer)
        } catch (_: Throwable) {
        }
        strip = null
        renderersActive = true
        cachedLowThreshold = null
        lastWifiLevel = 0
        lastWifiOn = false
        lastWifiActive = false
        lastWifiValidated = false
        lastCellPlan = IosCellPlan.None
        lastCellVisibleMask = 0
        L.i("iOS host teardown: stock icons restored")
        // Count the teardown as one lifecycle event, but never recurse: the tree is already restored.
        if (wasAttached) noteLifecycle(stripRootView, attach = false, restoreIfAttached = false)
    }

    // ----------------------------------------------------------------------------- anti-thrash

    private fun resetThrash() {
        IosSurfaceThrash.reset(surface)
    }

    /**
     * Records one attach/teardown lifecycle event. At the limit the guard backs off: the log names the
     * surface, and an attachment that is still live is torn down (restoring stock icons) unless the
     * caller is already inside [teardown] (`restoreIfAttached = false`).
     */
    private fun noteLifecycle(root: View?, attach: Boolean, restoreIfAttached: Boolean = true) {
        if (IosSurfaceThrash.isTripped(surface)) return
        val now = SystemClock.uptimeMillis()
        // A normal shade open/close is spaced by seconds; only rapid churn reaches the guard.
        if (!IosSurfaceThrash.lifecycleEvent(surface, attach, now)) return
        val guard = thrash
        if (guard.record(now)) {
            IosSurfaceThrash.trip(surface, root?.rootView)
            L.w(
                "iOS anti-thrash: ${guard.describe()} attach/teardown for " +
                    "${root?.javaClass?.simpleName ?: surface} - restoring stock icons and " +
                    "backing off until a real change"
            )
            if (restoreIfAttached && isAttached) {
                teardown()
            }
        }
    }

    // ----------------------------------------------------------------------------- attach

    private fun drawingNotReadyGate() {
        isAttached = false
        logOnce.once("not-ready") {
            L.i("iOS drawing is not ready - stock icons left untouched")
        }
    }

    private fun failAttach(t: Throwable): Boolean {
        logOnce.once("attach-fail") {
            L.w("iOS host attach failed: ${t.javaClass.simpleName}: ${t.message}")
        }
        try {
            teardown()
        } catch (_: Throwable) {
        }
        return false
    }

    /**
     * True when the attached strip no longer belongs to [root], or the tree now has a different strip.
     * The same strip means the same surface, so no teardown/re-attach churn (F2).
     */
    private fun reattachNeeded(root: View, stripId: String?): Boolean {
        val existing = strip ?: return true
        val found = try {
            if (stripId == null) {
                finder.findStatusIconsHost(root)
            } else {
                findByEntryName(root, stripId) as? ViewGroup
            }
        } catch (_: Throwable) {
            null
        }
        if (found === existing) return false
        // A failed lookup does not prove replacement when the old strip still hangs under this root.
        return !(found == null && isUnder(existing, root))
    }

    /** True when [view] is [ancestor] or sits anywhere below it. Read-only. */
    private fun isUnder(view: View, ancestor: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    /** Records a successful attach, or schedules the bounded retry that a 0×0 first pass needs (F3). */
    private fun finishAttach(success: Boolean, retry: () -> Boolean): Boolean {
        if (success) {
            attachAttempts = 0
            attachRetry?.let { handler.removeCallbacks(it) }
            attachRetry = null
            return true
        }
        scheduleAttachRetry(retry)
        return false
    }

    /**
     * One pending retry, at most [ATTACH_MAX_ATTEMPTS] attempts, never past teardown or a closed gate.
     * The strip/icon tree can be 0×0 on the first hook call; a later layout pass makes it adoptable.
     */
    private fun scheduleAttachRetry(retry: () -> Boolean) {
        if (!drawingReady || isAttached) return
        if (attachRetry != null) return
        if (attachAttempts >= ATTACH_MAX_ATTEMPTS - 1) return
        attachAttempts++
        val runnable = Runnable {
            attachRetry = null
            if (isAttached || !drawingReady) return@Runnable
            try {
                retry()
            } catch (t: Throwable) {
                failAttach(t)
            }
        }
        attachRetry = runnable
        handler.postDelayed(runnable, ATTACH_RETRY_MS)
    }

    private fun attachToStrip(name: String?, strip: ViewGroup, root: View): Boolean {
        // A tripped guard must not hide the stock icons again; check before any lookup or attach.
        if (IosSurfaceThrash.isTripped(surface)) return false
        val parent = finder.findOverlayContainer(strip, root)
        if (parent !is FrameLayout) {
            logOnce.once("no-overlay") {
                L.i("iOS host: no plain FrameLayout over ${strip.javaClass.simpleName} - stock icons untouched")
            }
            return false
        }
        val attached = attachOverStrip(name, strip, parent)
        if (!attached) return false
        noteLifecycle(root, attach = true)
        // If the guard is tripped, its teardown has restored the stock icons: report failure so the
        // caller does not treat a backed-off surface as live. The extra check covers a trip that
        // happened earlier in this same call (noteLifecycle then returns without tearing down).
        if (IosSurfaceThrash.isTripped(surface)) {
            if (isAttached) teardown()
            return false
        }
        return true
    }

    /**
     * Attaches over a vendor QS cluster that was handed over directly (OnePlus shade), optionally with
     * a battery view that sits beside the cluster rather than inside it (B1 §6 M7).
     *
     * The sibling battery is only used when it is really inside the overlay layer; otherwise it could
     * not be placed without writing a stock layout (C2). A cluster that has not been laid out yet is
     * retried a bounded number of times, because its icons are 0×0 until the first layout pass.
     */
    fun attachCluster(surface: String, cluster: ViewGroup, battery: View?, source: String): Boolean {
        if (isAttached) return true
        if (!drawingReady) {
            drawingNotReadyGate()
            return false
        }
        return try {
            val parent = finder.findOverlayContainer(cluster, cluster) as? FrameLayout
            if (parent == null) {
                logOnce.once("no-overlay:$surface") {
                    L.i("iOS host $surface: no plain FrameLayout over ${cluster.javaClass.simpleName} - stock icons untouched")
                }
                false
            } else {
                attachClusterAttempt(surface, cluster, battery, source, parent, attempt = 0)
            }
        } catch (t: Throwable) {
            failAttach(t)
        }
    }

    private fun attachClusterAttempt(
        surface: String,
        cluster: ViewGroup,
        battery: View?,
        source: String,
        parent: FrameLayout,
        attempt: Int
    ): Boolean {
        if (isAttached) return true
        val icons = clusterIcons(cluster, battery, parent)
        val anySized = icons.any { it.view.width > 0 && it.view.height > 0 }
        if (attachWithIcons(surface, cluster, parent, icons)) return true
        val laidOut = cluster.width > 0 && cluster.height > 0
        if ((!laidOut || !anySized) && attempt < CLUSTER_MAX_ATTEMPTS - 1) {
            scheduleClusterRetry(surface, cluster, battery, source, parent, attempt + 1)
        }
        return false
    }

    private fun scheduleClusterRetry(
        surface: String,
        cluster: ViewGroup,
        battery: View?,
        source: String,
        parent: FrameLayout,
        nextAttempt: Int
    ) {
        if (clusterRetry != null) return
        val retry = Runnable {
            clusterRetry = null
            if (isAttached) return@Runnable
            try {
                attachClusterAttempt(surface, cluster, battery, source, parent, nextAttempt)
            } catch (t: Throwable) {
                failAttach(t)
            }
        }
        clusterRetry = retry
        handler.postDelayed(retry, CLUSTER_RETRY_MS)
    }

    /** The cluster's own battery/Wi-Fi/cell icons plus the sibling battery, deduped by identity. */
    private fun clusterIcons(cluster: ViewGroup, battery: View?, parent: FrameLayout): List<StockIcon> {
        val icons = ArrayList(StockIconKey.inventory(cluster).filter {
            it.kind == StockIconKind.BATTERY ||
                it.kind == StockIconKind.WIFI ||
                it.kind == StockIconKind.CELLULAR
        })
        if (battery != null && battery.visibility != View.GONE &&
            offsetIn(battery, parent) != null && icons.none { it.view === battery }
        ) {
            icons.add(
                StockIcon(
                    key = StockIconKey.keyOf(battery),
                    label = StockIconKey.labelOf(battery),
                    kind = StockIconKind.BATTERY,
                    view = battery
                )
            )
        }
        return icons
    }

    private fun attachOverStrip(name: String?, strip: ViewGroup, parent: FrameLayout): Boolean {
        val inventory = StockIconKey.inventory(strip).filter {
            it.kind == StockIconKind.BATTERY ||
                it.kind == StockIconKind.WIFI ||
                it.kind == StockIconKind.CELLULAR
        }
        if (inventory.isEmpty()) {
            logOnce.once("no-icons") {
                L.i("iOS host: no battery/Wi-Fi/cellular icon in the strip - stock icons untouched")
            }
            return false
        }
        return attachWithIcons(name, strip, parent, inventory)
    }

    private fun attachWithIcons(
        name: String?,
        strip: ViewGroup,
        parent: FrameLayout,
        inventory: List<StockIcon>
    ): Boolean {
        if (inventory.isEmpty()) {
            logOnce.once("no-icons:$name") {
                L.i("iOS host ${name ?: "main bar"}: no battery/Wi-Fi/cellular icon - stock icons untouched")
            }
            return false
        }

        // Read one cellular snapshot before suppressing anything: a two-SIM bar must never hide a mobile
        // icon whose subId cannot be read (the planner then resolves to None).
        val cellIcons = inventory.filter { it.kind == StockIconKind.CELLULAR }
        val cell = if (cellIcons.isNotEmpty()) {
            try {
                cellSource(context)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
        val plan = cellPlanOf(cell, cellIcons)

        val layer = FrameLayout(context).apply {
            background = null
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        parent.addView(
            layer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        this.layer = layer
        this.strip = strip

        // Battery and Wi-Fi keep the per-icon path. Cellular is decided once by the plan so a two-SIM
        // bar never creates two side-by-side rows.
        for (icon in inventory) {
            val kind = iosKindOf(icon.kind) ?: continue
            if (kind == IosIconKind.CELL) continue
            val record = createGlyph(kind, icon.view, 0, layer) ?: continue
            records.add(record)
        }
        when (plan) {
            IosCellPlan.None -> Unit
            is IosCellPlan.Single -> {
                val icon = cellIcons.getOrNull(plan.slot)
                if (icon != null && !unskinnable.contains(icon.view)) {
                    createGlyph(IosIconKind.CELL, icon.view, plan.subId, layer)?.let { records.add(it) }
                }
            }
            is IosCellPlan.Dual -> {
                val dataIcon = cellIcons.getOrNull(plan.dataSlot)
                val otherIcon = cellIcons.getOrNull(plan.otherSlot)
                if (dataIcon != null && otherIcon != null &&
                    !unskinnable.contains(dataIcon.view) && !unskinnable.contains(otherIcon.view)
                ) {
                    val record = createCellCluster(plan, cellIcons, layer)
                    if (record != null) {
                        records.add(record)
                    } else {
                        // No per-slot fallback: a cluster that cannot be drawn leaves both stock icons
                        // alone and is not retried on every refresh.
                        unskinnable.add(dataIcon.view)
                        unskinnable.add(otherIcon.view)
                    }
                }
            }
        }

        if (records.isEmpty()) {
            try {
                parent.removeView(layer)
            } catch (_: Throwable) {
            }
            this.layer = null
            this.strip = null
            return false
        }

        addLayoutListener(strip)
        for (icon in inventory) addLayoutListener(icon.view)
        registerReceivers()
        isAttached = true
        refresh()
        L.i(
            "iOS host ${name ?: "main bar"} attached: icons=${records.size} " +
                "layer=${parent.javaClass.simpleName} strip=${strip.javaClass.simpleName}"
        )
        return true
    }

    /**
     * Creates one glyph and suppresses its stock drawing, or returns null having changed nothing.
     *
     * Order matters: the glyph is started and proven drawable **before** the skin is applied, and a skin
     * that cannot be applied removes the glyph again. The stock layout is only read, never written.
     */
    private fun createGlyph(
        kind: IosIconKind,
        stock: View,
        subId: Int,
        layer: FrameLayout
    ): IosGlyphRecord? {
        // INVISIBLE is the OEM saying "draw nothing": suppress it and we would resurrect a hidden icon.
        if (stock.visibility != View.VISIBLE) return null

        val plan = try {
            IosSkinPlanner.planFor(stock)
        } catch (_: Throwable) {
            IosSkinPlan(IosSkinFamily.UNKNOWN, IosSkinStrategy.SKIP)
        }
        if (plan.strategy == IosSkinStrategy.SKIP) {
            unskinnable.add(stock)
            noteSkip(kind, stock, "family ${plan.family} is not suppressed")
            return null
        }

        // The measured OPLUS outer is 0×0; its content view carries the size/position. Use the content
        // bounds for both, or the glyph would be skipped or stacked at the outer's useless origin.
        val geometry = glyphBounds(stock, plan, layer)
        val bounds = geometry.first
        if (bounds[2] <= 0 || bounds[3] <= 0) {
            noteSkip(kind, stock, "the stock content has no size yet")
            return null
        }

        val size = if (isMainBar) {
            Pair(bounds[2], bounds[3])
        } else {
            sizeOwner.sizeFor(kind, bounds[2], bounds[3])
        }
        if (size.first <= 0 || size.second <= 0) return null

        val element = startElement(kind) ?: run {
            unskinnable.add(stock)
            noteSkip(kind, stock, "no renderer could draw this icon")
            return null
        }
        val params = FrameLayout.LayoutParams(size.first, size.second).apply {
            leftMargin = bounds[0]
            topMargin = bounds[1]
        }
        val added = try {
            layer.addView(element.ui, params)
            true
        } catch (_: Throwable) {
            false
        }
        if (!added) {
            safeTeardown(element)
            return null
        }

        val skin = IosSkin()
        if (!skin.suppress(stock)) {
            removeGlyph(element)
            safeTeardown(element)
            unskinnable.add(stock)
            noteSkip(kind, stock, "the stock drawing could not be suppressed")
            return null
        }

        if (isMainBar) sizeOwner.pin(kind, bounds[2], bounds[3])
        val record = IosGlyphRecord(
            kind = kind,
            stocks = listOf(stock),
            strategies = listOf(plan.strategy),
            subIds = listOf(subId),
            skins = listOf(skin),
            element = element,
            glyphWidth = size.first,
            glyphHeight = size.second,
            geometry = geometry.second
        )
        record.oemVisibleMask = recordOemMask(record)
        updateGlyphVisibility(record)
        return record
    }

    /**
     * Creates the one element that draws the stacked two-SIM block anchored to the DATA slot.
     *
     * Order matters (rule 5/C9): the element starts and proves drawable before either stock drawing is
     * suppressed. If either suppression fails, the element is removed, the successful skin restored and
     * nothing is left half-attached. The caller marks both views unskinnable so this is not retried.
     * The block sits on the data slot's own bounds (the second slot is suppressed), matching
     * [IosReplaceHost]'s one-block overlay.
     */
    private fun createCellCluster(
        plan: IosCellPlan.Dual,
        icons: List<StockIcon>,
        layer: FrameLayout
    ): IosGlyphRecord? {
        val dataIcon = icons.getOrNull(plan.dataSlot) ?: return null
        val otherIcon = icons.getOrNull(plan.otherSlot) ?: return null
        val dataView = dataIcon.view
        val otherView = otherIcon.view
        if (dataView === otherView) return null
        if (dataView.visibility != View.VISIBLE || otherView.visibility != View.VISIBLE) return null

        val dataPlan = try {
            IosSkinPlanner.planFor(dataView)
        } catch (_: Throwable) {
            IosSkinPlan(IosSkinFamily.UNKNOWN, IosSkinStrategy.SKIP)
        }
        val otherPlan = try {
            IosSkinPlanner.planFor(otherView)
        } catch (_: Throwable) {
            IosSkinPlan(IosSkinFamily.UNKNOWN, IosSkinStrategy.SKIP)
        }
        if (dataPlan.strategy == IosSkinStrategy.SKIP || otherPlan.strategy == IosSkinStrategy.SKIP) {
            noteSkip(IosIconKind.CELL, dataView, "a cluster half is not suppressed")
            return null
        }

        // Each half contributes its own plan's content bounds; a 0×0 OPLUS outer must not veto the
        // cluster. The block and its size are anchored to the DATA slot, never the two-slot union.
        val dataGeometry = glyphBounds(dataView, dataPlan, layer)
        val otherGeometry = glyphBounds(otherView, otherPlan, layer)
        val bounds = dataGeometry.first
        if (bounds[2] <= 0 || bounds[3] <= 0) return null
        val geometry = dataGeometry.second + otherGeometry.second
        val size = if (isMainBar) {
            Pair(bounds[2], bounds[3])
        } else {
            sizeOwner.sizeForBlock(bounds[2], bounds[3])
        }
        if (size.first <= 0 || size.second <= 0) return null

        val element = startElement(IosIconKind.CELL) ?: return null
        val params = FrameLayout.LayoutParams(size.first, size.second).apply {
            leftMargin = bounds[0]
            topMargin = bounds[1]
        }
        val added = try {
            layer.addView(element.ui, params)
            true
        } catch (_: Throwable) {
            false
        }
        if (!added) {
            safeTeardown(element)
            return null
        }

        val dataSkin = IosSkin()
        val otherSkin = IosSkin()
        val dataSuppressed = try {
            dataSkin.suppress(dataView)
        } catch (_: Throwable) {
            false
        }
        if (!dataSuppressed) {
            discardCluster(element, dataSkin, otherSkin)
            return null
        }
        val otherSuppressed = try {
            otherSkin.suppress(otherView)
        } catch (_: Throwable) {
            false
        }
        if (!otherSuppressed) {
            discardCluster(element, dataSkin, otherSkin)
            return null
        }

        val record = IosGlyphRecord(
            kind = IosIconKind.CELL,
            stocks = listOf(dataView, otherView),
            strategies = listOf(dataPlan.strategy, otherPlan.strategy),
            subIds = listOf(plan.dataSubId, plan.otherSubId),
            skins = listOf(dataSkin, otherSkin),
            element = element,
            glyphWidth = size.first,
            glyphHeight = size.second,
            geometry = geometry
        )
        record.oemVisibleMask = recordOemMask(record)
        updateGlyphVisibility(record)
        if (isMainBar) sizeOwner.pinBlock(bounds[2], bounds[3])
        return record
    }

    /** Removes a half-applied cluster: glyph, renderer and any skin that did suppress. */
    private fun discardCluster(element: IosElement, vararg skins: IosSkin) {
        removeGlyph(element)
        safeTeardown(element)
        for (skin in skins) {
            try {
                skin.restore()
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Starts the best renderer this build can draw with (B1 §6 M1/M2).
     *
     * The production renderer is always the platform-Canvas element (IOS-R2 0c: no Rive view may be
     * created inside SystemUI). It must start and report [IosElement.canDraw] before the caller
     * suppresses a stock icon (C9/M13). Null means "nothing drew - leave the stock icon alone"
     * (rule 5).
     */
    private fun startElement(kind: IosIconKind): IosElement? {
        val canvas = try {
            canvasRenderers(context, kind)
        } catch (_: Throwable) {
            null
        } ?: return null
        val draws = try {
            canvas.start() && canvas.canDraw
        } catch (_: Throwable) {
            false
        }
        if (!draws) {
            safeTeardown(canvas)
            return null
        }
        // Production parity with the in-place drawables: every real glyph is inset to the per-kind
        // stock-slot scale. The element's view/layout is untouched (C2).
        (canvas as? IosCanvasView)?.relativeSize = true
        return canvas
    }

    // ----------------------------------------------------------------------------- state

    private fun batteryVisual(tint: Int): IosVisual? {
        val reading = try {
            batterySource(context)
        } catch (_: Throwable) {
            null
        } ?: return null
        val low = lowThreshold()
        val saver = try {
            DeviceStateReader.isPowerSaveOn(context)
        } catch (_: Throwable) {
            false
        }
        return IosMapping.battery(
            level = reading.level,
            lowThreshold = low.value,
            saver = saver,
            plugged = reading.plugged,
            status = reading.status,
            tint = tint
        )
    }

    private fun wifiVisual(tint: Int): IosVisual {
        val sample = wifiSource(
            context,
            lastWifiLevel,
            lastWifiOn,
            lastWifiActive,
            lastWifiValidated
        )
        lastWifiLevel = sample.dots
        lastWifiOn = sample.on
        lastWifiActive = sample.active
        lastWifiValidated = sample.validated
        return IosMapping.wifi(sample, tint)
    }

    private fun readCell(): CellReading? = try {
        cellSource(context)
    } catch (_: Throwable) {
        null
    }

    /**
     * The OEM's visibility intent for one stock in [record]: the skin's tracked intent for
     * INVISIBLE_KEEP_SLOT, the outer's visibility for alpha/drawable suppression. A re-assert is the
     * only way the skin learns a new OEM visibility, so it runs first for the visibility-only family.
     */
    private fun oemVisibleFor(record: IosGlyphRecord, index: Int): Boolean {
        val stock = record.stocks.getOrNull(index) ?: return false
        val skin = record.skins.getOrNull(index) ?: return stock.visibility == View.VISIBLE
        val strategy = record.strategies.getOrNull(index)
        if (strategy == IosSkinStrategy.INVISIBLE_KEEP_SLOT) {
            try {
                skin.reassert()
            } catch (_: Throwable) {
            }
            return skin.oemVisible
        }
        val visible = skin.oemVisible
        if (visible) {
            try {
                skin.reassert()
            } catch (_: Throwable) {
            }
        }
        return visible
    }

    /** True when this cell stock is OEM-visible, consulting its record's skin when recorded (F4). */
    private fun cellSlotVisible(view: View): Boolean {
        for (record in records) {
            if (record.kind != IosIconKind.CELL) continue
            val index = record.stocks.indexOfFirst { it === view }
            if (index >= 0) return oemVisibleFor(record, index)
        }
        return hasContentSize(view)
    }

    /** The planner's slots for the cellular inventory, in strip order. */
    private fun cellSlots(cellViews: List<StockIcon>): List<CellSlot> = cellViews.map { icon ->
        CellSlot(subId = subIdOf(icon.view), visible = cellSlotVisible(icon.view))
    }

    private fun cellMask(slots: List<CellSlot>): Int {
        var mask = 0
        for (i in slots.indices) {
            if (i >= Int.SIZE_BITS - 1) break
            if (slots[i].visible) mask = mask or (1 shl i)
        }
        return mask
    }

    private fun recordOemMask(record: IosGlyphRecord): Int {
        var mask = 0
        for (i in record.stocks.indices) {
            if (i >= Int.SIZE_BITS - 1) break
            if (oemVisibleFor(record, i)) mask = mask or (1 shl i)
        }
        return mask
    }

    /** The one cellular decision for this snapshot; an unreadable reading is never a guess. */
    private fun cellPlanOf(reading: CellReading?, cellViews: List<StockIcon>): IosCellPlan {
        val slots = cellSlots(cellViews)
        lastCellVisibleMask = cellMask(slots)
        if (reading == null) return IosCellPlan.None
        return IosMapping.cellPlan(
            cellSignals(reading),
            reading.defaultSub,
            slots,
            IosMapping.simDisplay(settings.simDisplay)
        )
    }

    private fun cellSignals(reading: CellReading): List<SimSignal> =
        reading.samples.map { SimSignal(it.slot, it.subscriptionId, it.dots) }

    private fun safeTint(): Int = try {
        BarTint.fgColor(context)
    } catch (_: Throwable) {
        0xFFFFFFFF.toInt()
    }

    /**
     * The per-icon user transform for [record] at its 100 % draw width. The element's own bounds are
     * preferred; before a layout pass, the first stock view stands in. A missing size is identity.
     */
    private fun userTransformFor(record: IosGlyphRecord, visual: IosVisual): IosUserTransform {
        val ui = record.element.ui
        var width = ui.width
        var height = ui.height
        if (width <= 0 || height <= 0) {
            val stock = record.stocks.firstOrNull()
            width = stock?.width ?: 0
            height = stock?.height ?: 0
        }
        if (width <= 0 || height <= 0) return IosUserTransform.IDENTITY
        val drawnWidth = IosGeometry.Relative.sizeFor(
            record.kind,
            width,
            height,
            dualCell = visual.cellMode == IosCellMode.DUAL
        ).first.toFloat()
        return try {
            settings.iosTransform(record.kind, drawnWidth)
        } catch (_: Throwable) {
            IosUserTransform.IDENTITY
        }
    }

    /**
     * Writes one snapshot, waking an idle renderer only when the snapshot actually changed (C8). An
     * identical snapshot is re-asserted only while the renderer is already running, so a paused renderer
     * is never woken for no visual change.
     */
    private fun applyVisual(record: IosGlyphRecord, visual: IosVisual) {
        val transformed = visual.copy(userTransform = userTransformFor(record, visual))
        val changed = record.lastVisual != transformed
        if (changed) record.lastVisual = transformed
        // A hidden glyph (keyguard/shade while not visible) stores the snapshot without waking a
        // renderer; updateGlyphVisibility repaints it when the element becomes VISIBLE again.
        if (record.element.ui.visibility == View.GONE) return
        if (changed) {
            if (!renderersActive && !screenPaused) {
                renderersActive = true
                setElementsActive(true)
            }
            if (renderersActive) scheduleIdleStop()
            record.element.render(transformed)
        } else if (renderersActive) {
            record.element.render(transformed)
        }
    }

    /** The framework's own low-battery warning level, read once and validated by [IosMapping]. */
    private fun lowThreshold(): IosMapping.LowThreshold {
        cachedLowThreshold?.let { return it }
        val raw = try {
            val id = context.resources.getIdentifier(LOW_THRESHOLD_RESOURCE, "integer", "android")
            if (id == 0) null else context.resources.getInteger(id)
        } catch (_: Throwable) {
            null
        }
        val resolved = IosMapping.lowThreshold(raw)
        if (resolved.fallback) {
            logOnce.once("low-threshold-fallback") {
                L.i("iOS low battery threshold unreadable - using fallback ${resolved.value}")
            }
        } else {
            logOnce.once("low-threshold") {
                L.i("iOS low battery threshold=$raw (framework $LOW_THRESHOLD_RESOURCE)")
            }
        }
        cachedLowThreshold = resolved
        return resolved
    }

    private fun registerReceivers() {
        if (receiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            receiverRegistered = true
        } catch (t: Throwable) {
            logOnce.once("receiver-register") {
                L.w("iOS host receiver not registered: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    private fun scheduleRefresh() {
        if (!isAttached) return
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, REFRESH_DEBOUNCE_MS)
    }

    // ----------------------------------------------------------------------------- geometry

    /**
     * Content bounds for [plan] in the layer's coordinate space, plus the views those bounds came from.
     *
     * The measured OPLUS outers are 0×0; their real content is the measured group/icon named in
     * [IosSkinPlan.geometryIds]. Families with no geometry ids keep the outer bounds exactly as before,
     * so unmeasured ROMs do not change. When an OPLUS id is missing (a rebuilt subtree), the fallback is
     * a union of visible, sized descendants rather than a useless 0×0 outer.
     */
    private fun glyphBounds(stock: View, plan: IosSkinPlan, layer: View): Pair<IntArray, List<View>> {
        val content = contentView(stock, plan)
        if (content != null) return Pair(boundsIn(content, layer), listOf(content))
        if (plan.geometryIds.isNotEmpty()) {
            val views = ArrayList<View>()
            collectVisibleSized(stock, views)
            if (views.isNotEmpty()) return Pair(unionBoundsIn(views, layer), views)
        }
        return Pair(boundsIn(stock, layer), listOf(stock))
    }

    /** The first direct/descendant view named by [plan]'s geometry ids, or null. Read-only. */
    private fun contentView(stock: View, plan: IosSkinPlan): View? {
        for (id in plan.geometryIds) {
            findByEntryName(stock, id)?.let { return it }
        }
        return null
    }

    /** Adds every visible, non-zero-size descendant of [parent] (depth-first). Read-only. */
    private fun collectVisibleSized(parent: View, out: MutableList<View>) {
        if (parent !is ViewGroup) return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.visibility == View.VISIBLE && child.width > 0 && child.height > 0) out.add(child)
            collectVisibleSized(child, out)
        }
    }

    /** True when [view] (or a visible sized descendant) carries drawable content. Read-only. */
    private fun hasContentSize(view: View): Boolean {
        if (view.visibility != View.VISIBLE) return false
        if (view.width > 0 && view.height > 0) return true
        val out = ArrayList<View>(1)
        collectVisibleSized(view, out)
        return out.isNotEmpty()
    }

    /**
     * Stock bounds in the layer's coordinate space. Read-only: nothing here writes a stock view.
     *
     * Offsets are accumulated through the parents rather than read from `getLocationOnScreen`, because a
     * bar built by a test (or adopted before its window attaches) has no window offset yet. Both views
     * share the layer's parent, so the difference is the same number in either case. `translationX/Y`
     * is part of the layout the OOS stock icons use, so it is accumulated with left/top.
     */
    private fun boundsIn(view: View, layer: View): IntArray {
        val common = layer.parent as? View
        val targetOffset = if (common != null) offsetIn(view, common) else null
        val originOffset = if (common != null) offsetIn(layer, common) else null
        if (targetOffset == null || originOffset == null) {
            return intArrayOf(0, 0, view.width, view.height)
        }
        return intArrayOf(
            targetOffset[0] - originOffset[0],
            targetOffset[1] - originOffset[1],
            view.width,
            view.height
        )
    }

    /**
     * The bounding box covering [views], as `[left, top, width, height]` in the layer's parent space.
     * An empty list (or a zero-size union) is never a usable measurement, so it is reported as zero.
     */
    private fun unionBoundsIn(views: List<View>, layer: View): IntArray {
        if (views.isEmpty()) return intArrayOf(0, 0, 0, 0)
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (view in views) {
            val bounds = boundsIn(view, layer)
            left = minOf(left, bounds[0])
            top = minOf(top, bounds[1])
            right = maxOf(right, bounds[0] + bounds[2])
            bottom = maxOf(bottom, bounds[1] + bounds[3])
        }
        val width = (right - left).coerceAtLeast(0)
        val height = (bottom - top).coerceAtLeast(0)
        return intArrayOf(left, top, width, height)
    }

    /** Offset of [view] inside [ancestor], or null when [ancestor] is not one of its parents. */
    private fun offsetIn(view: View, ancestor: View): IntArray? {
        var x = 0
        var y = 0
        var current: View = view
        while (current !== ancestor) {
            val parent = current.parent as? View ?: return null
            x += current.left - parent.scrollX + current.translationX.roundToInt()
            y += current.top - parent.scrollY + current.translationY.roundToInt()
            current = parent
        }
        return intArrayOf(x, y)
    }

    /** True when [view] is [strip] or sits anywhere below it. Read-only and null-safe. */
    private fun isInStrip(view: View, strip: ViewGroup): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === strip) return true
            current = current.parent as? View
        }
        return false
    }

    private fun onLayoutChanged() {
        if (updating || !isAttached) return
        updating = true
        try {
            for (record in records.toList()) {
                try {
                    updateRecord(record)
                } catch (t: Throwable) {
                    familyFailed(record, t)
                }
            }
        } finally {
            updating = false
        }
        if (!isAttached) return
        try {
            val inventory = stockInventory()
            // Only battery/Wi-Fi may be adopted by this generic check: an unrecorded cellular slot is
            // governed by the planner below, or a Single/None plan would schedule a refresh forever.
            val pendingBatteryWifi = inventory.any { icon ->
                (icon.kind == StockIconKind.BATTERY || icon.kind == StockIconKind.WIFI) &&
                    records.none { record -> record.stocks.any { it === icon.view } } &&
                    !unskinnable.contains(icon.view) &&
                    hasContentSize(icon.view)
            }
            val cellViews = inventory.filter { it.kind == StockIconKind.CELLULAR }
            val mask = cellMask(cellViews.map { CellSlot(0, cellSlotVisible(it.view)) })
            val cellVisibilityChanged = mask != lastCellVisibleMask
            if (cellVisibilityChanged) lastCellVisibleMask = mask
            val pendingCell = cellPlanPending(cellViews)
            if (pendingBatteryWifi || cellVisibilityChanged || pendingCell) scheduleRefresh()
        } catch (_: Throwable) {
        }
    }

    /**
     * A missing glyph the last cellular plan expects still needs one refresh. Bounded by the plan
     * itself, so a Single/None plan with an unrecorded extra slot cannot loop (F6).
     */
    private fun cellPlanPending(cellViews: List<StockIcon>): Boolean = when (val plan = lastCellPlan) {
        IosCellPlan.None -> false
        is IosCellPlan.Single -> {
            val icon = cellViews.getOrNull(plan.slot) ?: return false
            canAdoptCell(icon) && records.none { record ->
                !record.isCluster && record.kind == IosIconKind.CELL &&
                    record.stocks.firstOrNull() === icon.view
            }
        }
        is IosCellPlan.Dual -> {
            val data = cellViews.getOrNull(plan.dataSlot) ?: return false
            val other = cellViews.getOrNull(plan.otherSlot) ?: return false
            canAdoptCell(data) && canAdoptCell(other) &&
                records.none { it.isCluster && it.kind == IosIconKind.CELL }
        }
    }

    private fun canAdoptCell(icon: StockIcon): Boolean =
        !unskinnable.contains(icon.view) && hasContentSize(icon.view)

    private fun updateRecord(record: IosGlyphRecord) {
        val layer = layer
        if (record.stocks.any { it.parent == null } || layer == null ||
            record.element.ui.parent !== layer
        ) {
            dropRecord(record, restore = true)
            return
        }
        // A rebuilt OPLUS subtree replaces the measured content view: re-resolve a detached geometry
        // entry once, so positioning keeps using the live view rather than falling back to a 0x0 outer.
        for (i in record.stocks.indices) {
            val g = record.geometry.getOrNull(i) ?: continue
            if (g !== record.stocks[i] && g.parent == null) {
                val plan = try {
                    IosSkinPlanner.planFor(record.stocks[i])
                } catch (_: Throwable) {
                    null
                }
                val replacement = plan?.let { glyphBounds(record.stocks[i], it, layer).second.firstOrNull() }
                if (replacement != null) {
                    val next = record.geometry.toMutableList()
                    next[i] = replacement
                    record.geometry = next
                }
            }
        }
        if (record.kind == IosIconKind.CELL) {
            // A recorded slot the OEM hides or reveals changes the plan; schedule exactly one refresh
            // on the transition (the mask is stored, so unchanged layout passes do nothing). Return
            // before resizing/recreating this glyph: the refresh is what switches the plan, and
            // recreating here would add a throwaway glyph on the way to a Single/Dual switch (F6).
            val mask = recordOemMask(record)
            if (mask != record.oemVisibleMask) {
                record.oemVisibleMask = mask
                scheduleRefresh()
                return
            }
        }
        if (record.isCluster) {
            // A missing half means the plan must become a single row over the remaining slot; refresh
            // owns dropping the cluster and creating that row.
            if (record.oemVisibleMask == 0) {
                scheduleRefresh()
                return
            }
            updateGlyphVisibility(record)
            val bounds = unionBoundsIn(record.geometry.ifEmpty { record.stocks }, layer)
            if (bounds[2] <= 0 || bounds[3] <= 0) return
            val size = if (isMainBar) {
                Pair(bounds[2], bounds[3])
            } else {
                sizeOwner.sizeForBlock(bounds[2], bounds[3])
            }
            if (size.first <= 0 || size.second <= 0) return
            if (isMainBar) sizeOwner.pinBlock(bounds[2], bounds[3])
            if (size.first != record.glyphWidth || size.second != record.glyphHeight) {
                recreateGlyph(record, bounds, size)
            } else {
                placeGlyph(record, bounds)
            }
            return
        }

        updateGlyphVisibility(record)
        // Use the recorded content view(s): for the measured OPLUS families the outer is 0×0 and all
        // positioning comes from `wifi_group`/`mobile_group`/`battery_icon_view`. A fallback union can
        // hold more than one view, so it is unioned rather than truncated to the first.
        val geometryViews = record.geometry.ifEmpty { record.stocks }
        val bounds = if (geometryViews.size == 1) {
            boundsIn(geometryViews[0], layer)
        } else {
            unionBoundsIn(geometryViews, layer)
        }
        if (bounds[2] <= 0 || bounds[3] <= 0) return
        val size = if (isMainBar) {
            Pair(bounds[2], bounds[3])
        } else {
            sizeOwner.sizeFor(record.kind, bounds[2], bounds[3])
        }
        if (size.first <= 0 || size.second <= 0) return
        if (isMainBar) sizeOwner.pin(record.kind, bounds[2], bounds[3])

        if (size.first != record.glyphWidth || size.second != record.glyphHeight) {
            recreateGlyph(record, bounds, size)
        } else {
            placeGlyph(record, bounds)
        }
    }

    /** Positions the glyph; only the margins are written, and only when they actually changed. */
    private fun placeGlyph(record: IosGlyphRecord, bounds: IntArray) {
        val params = record.element.ui.layoutParams as? FrameLayout.LayoutParams ?: return
        if (params.leftMargin == bounds[0] && params.topMargin == bounds[1]) return
        params.leftMargin = bounds[0]
        params.topMargin = bounds[1]
        record.element.ui.layoutParams = params
    }

    /**
     * Replaces the glyph for a new size. A live glyph element is never resized (C2); the old element is
     * torn down and a fresh one is started at the new size. A failure restores this stock icon.
     */
    private fun recreateGlyph(record: IosGlyphRecord, bounds: IntArray, size: Pair<Int, Int>) {
        val layer = layer ?: return
        removeGlyph(record.element)
        safeTeardown(record.element)

        val element = startElement(record.kind)
        if (element == null) {
            dropRecord(record, restore = true)
            return
        }
        val params = FrameLayout.LayoutParams(size.first, size.second).apply {
            leftMargin = bounds[0]
            topMargin = bounds[1]
        }
        val added = try {
            layer.addView(element.ui, params)
            true
        } catch (_: Throwable) {
            false
        }
        if (!added) {
            safeTeardown(element)
            dropRecord(record, restore = true)
            return
        }
        record.element = element
        record.glyphWidth = size.first
        record.glyphHeight = size.second
        for (skin in record.skins) {
            try {
                skin.reassert()
            } catch (_: Throwable) {
            }
        }
        record.lastVisual?.let { visual ->
            try {
                element.render(visual)
            } catch (t: Throwable) {
                familyFailed(record, t)
            }
        }
        updateGlyphVisibility(record)
    }

    /**
     * Follows the stock outer's visibility. GONE means gone; VISIBLE means visible and the skin is
     * re-asserted (an OEM alpha animation or drawable rebind may have undone it). For
     * [IosSkinStrategy.INVISIBLE_KEEP_SLOT] the outer is INVISIBLE **because of the skin**, so the glyph
     * stays visible and the OEM's own intent is tracked inside [IosSkin]. The stock visibility itself is
     * never written here.
     */
    /**
     * Follows the OEM's visibility intent, not just the outer view's current visibility (F4).
     *
     * For [IosSkinStrategy.INVISIBLE_KEEP_SLOT] the outer is INVISIBLE **because of the skin**, so the
     * glyph stays visible while the tracked intent is VISIBLE. For alpha/drawable suppression an
     * INVISIBLE or GONE outer means the OEM wants no icon, so the glyph is gone rather than floating
     * over a hidden stock. Each OEM-visible stock re-asserts its own skin; the stock visibility itself
     * is never written here.
     */
    private fun updateGlyphVisibility(record: IosGlyphRecord) {
        if (record.isCluster) {
            // One block draws for the cluster: it is gone only when no stock is OEM-visible.
            var anyVisible = false
            for (i in record.stocks.indices) {
                if (oemVisibleFor(record, i)) anyVisible = true
            }
            applyGlyphVisibility(record, if (anyVisible) View.VISIBLE else View.GONE)
            return
        }
        val visible = oemVisibleFor(record, 0)
        applyGlyphVisibility(record, if (visible) View.VISIBLE else View.GONE)
    }

    /**
     * Follows a visibility transition: a hidden glyph stops rendering (C8), and a revealed one resumes
     * and repaints the last snapshot. An unchanged visibility is left alone, so a layout pass costs no
     * renderer call.
     */
    private fun applyGlyphVisibility(record: IosGlyphRecord, target: Int) {
        val ui = record.element.ui
        if (ui.visibility == target) return
        ui.visibility = target
        if (target == View.GONE) {
            record.element.setRenderActive(false)
        } else if (renderersActive) {
            record.element.setRenderActive(true)
            record.lastVisual?.let { record.element.render(it) }
        }
    }

    private fun updateGlyphVisibility() {
        for (record in records.toList()) {
            try {
                updateGlyphVisibility(record)
            } catch (t: Throwable) {
                familyFailed(record, t)
            }
        }
    }

    // ----------------------------------------------------------------------------- records

    /** The iOS occupant for a stock kind, or null for COMBINED/OTHER/IGNORED (left untouched). */
    private fun iosKindOf(kind: StockIconKind): IosIconKind? = when (kind) {
        StockIconKind.BATTERY -> IosIconKind.BATTERY
        StockIconKind.WIFI -> IosIconKind.WIFI
        StockIconKind.CELLULAR -> IosIconKind.CELL
        else -> null
    }

    private fun subIdOf(view: View): Int = try {
        (view.javaClass.getMethod("getSubId").invoke(view) as? Int) ?: 0
    } catch (_: Throwable) {
        0
    }

    /**
     * Adopts every unrecorded, mappable stock icon that has a visible visual. An icon without a valid
     * reading (Wi-Fi unvalidated, battery never read, SIM unmappable) is left exactly as it is, so
     * reconciliation never causes create/suppress/drop churn.
     */
    private fun reconcile(
        inventory: List<StockIcon>,
        battery: IosVisual?,
        wifi: IosVisual?,
        plan: IosCellPlan,
        cell: IosVisual?
    ) {
        try {
            val layer = layer ?: return
            for (icon in inventory) {
                if (icon.kind == StockIconKind.CELLULAR) continue
                if (records.any { record -> record.stocks.any { it === icon.view } }) continue
                if (unskinnable.contains(icon.view)) continue
                val kind = iosKindOf(icon.kind) ?: continue
                val visual = when (kind) {
                    IosIconKind.BATTERY -> battery
                    IosIconKind.WIFI -> wifi
                    IosIconKind.CELL -> null
                }
                if (visual == null || !visual.visible) continue
                val record = createGlyph(kind, icon.view, 0, layer) ?: continue
                records.add(record)
                applyVisual(record, visual)
            }
            reconcileCell(inventory, plan, cell, layer)
        } catch (_: Throwable) {
        }
    }

    /** Creates the planned cellular glyph or cluster; never a per-slot row when the plan is Dual. */
    private fun reconcileCell(
        inventory: List<StockIcon>,
        plan: IosCellPlan,
        cell: IosVisual?,
        layer: FrameLayout
    ) {
        val cellViews = inventory.filter { it.kind == StockIconKind.CELLULAR }
        when (plan) {
            IosCellPlan.None -> Unit
            is IosCellPlan.Single -> {
                val icon = cellViews.getOrNull(plan.slot) ?: return
                val hasRecord = records.any { record ->
                    !record.isCluster && record.kind == IosIconKind.CELL &&
                        record.stocks.firstOrNull() === icon.view
                }
                if (hasRecord || unskinnable.contains(icon.view)) return
                val record = createGlyph(IosIconKind.CELL, icon.view, plan.subId, layer) ?: return
                records.add(record)
                if (cell != null && cell.visible) applyVisual(record, cell)
            }
            is IosCellPlan.Dual -> {
                if (records.any { it.isCluster }) return
                val dataIcon = cellViews.getOrNull(plan.dataSlot) ?: return
                val otherIcon = cellViews.getOrNull(plan.otherSlot) ?: return
                if (unskinnable.contains(dataIcon.view) || unskinnable.contains(otherIcon.view)) return
                val record = createCellCluster(plan, cellViews, layer)
                if (record == null) {
                    unskinnable.add(dataIcon.view)
                    unskinnable.add(otherIcon.view)
                    return
                }
                records.add(record)
                if (cell != null && cell.visible) applyVisual(record, cell)
            }
        }
    }

    private fun dropRecord(record: IosGlyphRecord, restore: Boolean) {
        if (!records.remove(record)) return
        for (stock in record.stocks) removeLayoutListener(stock)
        removeGlyph(record.element)
        safeTeardown(record.element)
        if (restore) {
            for (skin in record.skins) {
                try {
                    skin.restore()
                } catch (t: Throwable) {
                    logOnce.once("restore-fail:${record.kind}") {
                        L.w("iOS host restore failed: ${t.javaClass.simpleName}: ${t.message}")
                    }
                }
            }
        }
    }

    private fun removeGlyph(element: IosElement) {
        try {
            (element.ui.parent as? ViewGroup)?.removeView(element.ui)
        } catch (_: Throwable) {
        }
    }

    private fun safeTeardown(element: IosElement) {
        try {
            element.teardown()
        } catch (_: Throwable) {
        }
    }

    /** Adds [layoutListener] to [view] once, by identity, so teardown removes exactly what attach added. */
    private fun addLayoutListener(view: View) {
        if (layoutHosts.any { it === view }) return
        layoutHosts.add(view)
        try {
            view.addOnLayoutChangeListener(layoutListener)
        } catch (_: Throwable) {
        }
    }

    private fun removeLayoutListener(view: View) {
        val index = layoutHosts.indexOfFirst { it === view }
        if (index >= 0) layoutHosts.removeAt(index)
        try {
            view.removeOnLayoutChangeListener(layoutListener)
        } catch (_: Throwable) {
        }
    }

    private fun setElementsActive(active: Boolean) {
        for (record in records.toList()) {
            try {
                // Never wake a hidden keyguard/shade glyph; it has no pixels to update.
                if (record.element.ui.visibility == View.VISIBLE) {
                    record.element.setRenderActive(active)
                }
            } catch (t: Throwable) {
                familyFailed(record, t)
            }
        }
    }

    private fun activateRenderers() {
        if (!isAttached) return
        screenPaused = false
        renderersActive = true
        setElementsActive(true)
        scheduleIdleStop()
    }

    private fun pauseRenderers() {
        handler.removeCallbacks(idleRunnable)
        screenPaused = true
        renderersActive = false
        setElementsActive(false)
    }

    private fun scheduleIdleStop() {
        handler.removeCallbacks(idleRunnable)
        if (renderersActive && isAttached) handler.postDelayed(idleRunnable, idleDelayMs)
    }

    private fun isLayerVisible(): Boolean = isAttached && layer?.visibility == View.VISIBLE

    private fun familyFailed(record: IosGlyphRecord, t: Throwable) {
        val stock = record.stocks.firstOrNull()
        val name = stock?.javaClass?.simpleName ?: "cluster"
        logOnce.once("family-fail:${record.kind}:$name") {
            L.w(
                "iOS ${record.kind} glyph failed ($name) - " +
                    "leaving that stock icon: ${t.javaClass.simpleName}: ${t.message}"
            )
        }
        dropRecord(record, restore = true)
    }

    private fun noteSkip(kind: IosIconKind, stock: View, reason: String) {
        logOnce.once("skip:$kind:${stock.javaClass.simpleName}:$reason") {
            L.i("iOS host: leaving ${kind.name} stock icon untouched - $reason")
        }
    }

    /** The view with the given resource entry name, depth-first. Read-only. */
    private fun findByEntryName(view: View, name: String): View? {
        val entry = try {
            if (view.id == View.NO_ID) null else view.resources.getResourceEntryName(view.id)
        } catch (_: Throwable) {
            null
        }
        if (entry == name) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findByEntryName(view.getChildAt(i), name)?.let { return it }
            }
        }
        return null
    }
}
