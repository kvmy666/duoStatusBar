package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuffColorFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.rom.RomDetection
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.roundToInt

/**
 * R5M: the absolute floor for the free cell->battery gap before a `mobile_type` label is moved. R6
 * raises the effective requirement to the label's own box width: a gap narrower than that cannot hold
 * the label without sharing a column with the cell bars.
 */
private const val LABEL_MIN_GAP_PX = 28

/**
 * R6 audit FIX 2: a shared, read-only zero box returned by a native label whose anchor is hidden or
 * transparent. [IosDrawingHooks.render] only reads it, so no frame allocates and no label ink is drawn.
 */
private val HIDDEN_LABEL_BOX = IntArray(4)

/**
 * R6 audit FIX 2: the label's alpha mirrors its anchor. Non-finite values (a broken animation) draw at
 * full opacity rather than becoming invisible or crashing the canvas.
 */
private fun labelAlpha(view: View): Float {
    val alpha = view.alpha
    return if (alpha.isFinite()) alpha.coerceIn(0f, 1f) else 1f
}

/**
 * R5M/R6 runner: horizontal translation (px) that moves a `mobile_type`-anchored label into the free
 * horizontal gap between the data cell and the battery.
 *
 * The stock boxes are `[left, top, width, height]` in the frame's coordinates. The shift is zero when
 * either box is unknown, when the type box does not overlap the cell box horizontally (1 px tolerance
 * keeps a flush edge from counting as an overlap), or when the free gap cannot hold the whole label
 * box (R6: a partially fitted label would share a column with the cell bars, so it fails closed instead;
 * [LABEL_MIN_GAP_PX] remains the absolute floor). The label centre is the type box centre.
 */
internal fun labelShiftPx(
    typeBox: IntArray,
    cellBox: IntArray,
    cellOuterBox: IntArray,
    batteryBox: IntArray,
    canvasLeft: Float,
    canvasWidth: Int
): Float {
    if (typeBox[2] <= 0 || cellBox[2] <= 0 || batteryBox[2] <= 0) return 0f
    val typeLeft = typeBox[0]
    val typeRight = typeBox[0] + typeBox[2]
    val cellLeft = cellBox[0]
    val cellRight = cellBox[0] + cellBox[2]
    if (typeLeft >= cellRight - 1 || typeRight <= cellLeft + 1) return 0f
    val gapLeft = maxOf(cellRight, cellOuterBox[0] + cellOuterBox[2])
    val gapRight = batteryBox[0]
    val needed = maxOf(LABEL_MIN_GAP_PX, typeBox[2])
    if (gapRight - gapLeft < needed) return 0f
    val center = if (canvasWidth > 0) {
        canvasLeft + canvasWidth / 2f
    } else {
        (typeLeft + typeRight) / 2f
    }
    return (gapLeft + gapRight) / 2f - center
}

/**
 * The true in-place replacement host (IOS-REPLACE Phase C2).
 *
 * Unlike [IosIconHost], which paints a transparent glyph layer over a hidden stock icon, this host
 * replaces the stock drawing **inside the system's own views**: a [IosGlyphDrawable] on the leaf
 * `ImageView` ([IosReplaceMechanism.IMAGE_DRAWABLE]), the proven `dispatchDraw` of a composite outer
 * ([IosReplaceMechanism.OUTER_DRAW]) or the proven `onDraw` of a custom battery leaf
 * ([IosReplaceMechanism.LEAF_DRAW]). The slot, its size, visibility, order and SIM id stay the
 * system's. [IosReplaceMechanism.OVERLAY] is the approved hide-in-place fallback only; there is no
 * Rive layer at runtime.
 *
 * ## State ownership
 *
 * The stock view supplies slot, bounds and the SIM id; the state comes from Duo's readers (sticky
 * battery, [DeviceStateReader], validated-only [WifiReader] and [CellReader]) mapped through
 * [IosMapping]. Refresh is debounced on the main handler and driven by broadcasts; screen-off does no
 * work and screen-on refreshes. There is no polling and no Choreographer callback.
 *
 * ## Fail silent
 *
 * Every hook and view write is guarded. A record that cannot be drawn is never created; a lost leaf or
 * a null Wi-Fi/cell reading restores the stock drawing. [ThrashGuard] counts both attach/teardown and
 * drawable re-asserts, and a trip restores every stock icon and backs off until a real change
 * (settings, a new window/root, or a fresh attach). See the measured replacement map (device dumps) §0.
 *
 * ## Size (M15)
 *
 * The main bar pins each adopted record's stock content size in the shared [IosSizeOwner] (and the
 * dual block size); extra surfaces draw at [IosSizeOwner.sizeFor]/[IosSizeOwner.sizeForBlock], so the
 * lock-screen and shade glyphs cannot be larger than the home bar's. No stock layout is ever written
 * for a drawing leaf (C2).
 */
internal class IosReplaceHost(
    private val context: Context,
    private val classLoader: ClassLoader?,
    private val sizeOwner: IosSizeOwner = IosSizeOwner(),
    private val isMainBar: Boolean = true,
    private val surface: String = if (isMainBar) "main bar" else "extra",
    private val batterySource: (Context) -> IosBatteryReading? = ::readStickyBattery,
    private val cellSource: (Context) -> CellReading = { IosReadings.cell(it) },
    private val wifiSource: (Context, Int, Boolean, Boolean, Boolean) -> WifiSample =
        { ctx, level, on, active, validated ->
            IosReadings.wifi(ctx, level, on, active, validated)
        },
    private val survivalDelayMs: Long = 5_000L,
    private val idleDelayMs: Long = 3_000L
) : IosDiagnosticSource, IosBroadcastTarget {

    internal companion object {
        const val REFRESH_DEBOUNCE_MS = 120L
        const val ATTACH_RETRY_MS = 400L
        const val ATTACH_MAX_ATTEMPTS = 5
        const val LOW_THRESHOLD_RESOURCE = "config_lowBatteryWarningLevel"
        const val ACTION_SERVICE_STATE = "android.intent.action.SERVICE_STATE"
        const val ACTION_ANY_DATA_STATE = "android.intent.action.ANY_DATA_STATE"
        const val ACTION_WIFI_RSSI = "android.net.wifi.RSSI_CHANGED"
        const val ACTION_WIFI_STATE = "android.net.wifi.WIFI_STATE_CHANGED"

        /** R3 A1: a strip-id oscillation may tear down/re-attach at most once per second. */
        const val MIN_RETARGET_INTERVAL_MS = 1_000L

        /**
         * R3 A1: may [attachSurface] tear a live host down to move to the usable copy of its strip id?
         *
         * Pure and Android-free. A hidden attachment is kept when no usable copy exists (a closing
         * panel must be reused on the next open), and a re-target is throttled so two copies that
         * alternate their visibility cannot tear the host down on every layout pass.
         */
        internal fun retarget(
            attached: Boolean,
            attachedUsable: Boolean,
            usableExists: Boolean,
            currentStripIsUsableCopy: Boolean,
            msSinceLastRetarget: Long
        ): Boolean {
            if (!attached) return false
            if (attachedUsable) return false
            if (!usableExists) return false
            if (currentStripIsUsableCopy) return false
            return msSinceLastRetarget >= MIN_RETARGET_INTERVAL_MS
        }
    }

    /**
     * One stock icon this host owns.
     *
     * [leaf] is the drawing/bounds leaf; [mechanism] is the final mechanism actually applied (an
     * [IosReplaceMechanism.OUTER_DRAW] record may instead hold the leaf swap after the fallback).
     * [drawSession] says the drawing lives in [IosDrawingHooks] (for both OUTER_DRAW and LEAF_DRAW).
     */
    private class Record(
        val kind: IosIconKind,
        val outer: View,
        val leaf: View?,
        val mechanism: IosReplaceMechanism,
        val drawable: IosGlyphDrawable?,
        val swap: IosDrawableSwap?,
        val drawSession: Boolean,
        val skin: IosSkin?,
        val overlay: IosCanvasView?,
        val overlayParams: FrameLayout.LayoutParams?,
        val siblings: List<IosSiblingSwap>,
        var visual: IosVisual?,
        var oemTint: Int = 0,
        /** Cellular only: the row this record draws and the SIM slot it occupies. */
        var cellRole: IosCellRole? = null,
        var cellSlot: Int = -1,
        var subId: Int = 0,
        /** Dual-SIM item 2: the other stock slot this one block removes (restored exactly). */
        var collapse: IosCollapse? = null,
        /** A dual-SIM overlay skins both outers; the primary is [outer]. */
        var clusterSkins: List<IosSkin> = emptyList(),
        var clusterOuters: List<View> = emptyList(),
        /** Extra views whose tint writes refresh this record (an overlay's optional leaf). */
        val tintViews: List<View> = emptyList(),
        /**
         * R4A item 5: an OVERLAY's unscaled `[left, top, width, height]` slot box. The transformed
         * canvas box is derived from it; it replaces the (already scaled) layout params as the source
         * for the 100 % drawn width, so the percent offset cannot drift on a second refresh.
         */
        var baseBox: IntArray? = null,
        /**
         * R5A item 1-runtime: this WIFI record draws the cellular data-type label (drawn inside the
         * native mobile view's own pass, or on the stock Wi-Fi slot), never the Wi-Fi glyph. R6: a
         * WIFI record can switch glyph<->label, so this is refreshed in [applyVisual] from the visual's
         * `wifiSlot`.
         */
        var labelSlot: Boolean = false,
        /** R5M runner: horizontal translation applied to a mobile_type label. */
        var labelShift: Float = 0f,
        var labelGapLeft: Int = 0,
        var labelGapRight: Int = 0,
        /**
         * R6 item 1: the label drawn inside the owning CELL record's native draw pass. Only the
         * `OUTER_DRAW` path uses this; the drawable-swap path uses [swap] on [outer] instead.
         */
        var labelDraw: IosDrawLabel? = null,
        /** The `mobile_type` view's translationX before we shifted it (swap path only). */
        var labelBaseTranslation: Float = 0f,
        /**
         * `[left, top, width, height]` in the owning CELL outer's coordinate space. The draw closure
         * reads this exact array, so the per-frame draw allocates nothing.
         */
        var labelBox: IntArray? = null,
        /** Precomputed offset of [outer] inside the owning CELL outer; [labelBox] adds the shift. */
        var labelOffset: IntArray? = null,
        /**
         * R6 audit FIX 3: whether the last layout actually left the label on screen, and why it did
         * not. The diagnostic must not claim `anchor=cell-type` for a hidden label.
         */
        var labelShown: Boolean = true,
        var labelHiddenReason: String? = null
    ) {
        val drawTarget: View?
            get() = when (mechanism) {
                IosReplaceMechanism.OUTER_DRAW -> outer
                IosReplaceMechanism.LEAF_DRAW -> leaf
                IosReplaceMechanism.NATIVE_LABEL -> null
                else -> null
            }

        /** The view whose ancestor chain must not clip an overflowed glyph. */
        val overflowTarget: View?
            get() = when (mechanism) {
                IosReplaceMechanism.IMAGE_DRAWABLE, IosReplaceMechanism.LEAF_DRAW -> leaf
                IosReplaceMechanism.OUTER_DRAW -> outer
                IosReplaceMechanism.NATIVE_LABEL -> outer
                else -> null
            }
    }

    /**
     * One line of the R3 A5.3 self-diagnosis. Built once per attach from the stock inventory (so an
     * icon that was never adopted still appears), then joined with the live records at dump time. No
     * tree walk happens per frame; only this snapshot and the readers' last values are read.
     */
    private class IconReport(
        val kind: IosIconKind,
        val view: View,
        val family: IosSkinFamily,
        val plannedMechanism: IosReplaceMechanism,
        val cellSlot: Int = -1,
        val subId: Int = 0,
        var reason: String = "not-adopted",
        var slot: IntArray? = null
    )

    private val logOnce = LogOnce()
    private val handler = Handler(Looper.getMainLooper())
    private val records = ArrayList<Record>()

    /** Every view carrying [layoutListener], so teardown removes exactly what attach added. */
    private val layoutHosts = ArrayList<View>()

    /** Views whose adoption failed at the recorded content area; a size change retries. */
    private val deferred: MutableSet<View> =
        Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
    private val deferredArea = IdentityHashMap<View, Int>()

    /** Collapses whose restore failed, retried on the next refresh/teardown (item 3). */
    private val pendingCollapses = ArrayList<IosCollapse>()

    /** The slots still collapsed by a deferred restore; the plan must keep seeing them as present. */
    private val pendingCollapseTargets: MutableSet<View> =
        Collections.newSetFromMap(IdentityHashMap<View, Boolean>())

    private var isAttached = false
    val attached: Boolean get() = isAttached

    /**
     * R3 A1: when this host last tore down to re-target another copy of its strip id
     * ([SystemClock.uptimeMillis]); null means never, or reset by teardown/settings.
     */
    private var lastSurfaceRetargetAt: Long? = null

    private var settings = ModuleSettings.DEFAULT
    private var hiddenStockKeys: Set<String> = emptySet()
    /** Item 8-runtime: hides the selected stock icons, and every non-replaced one when asked. */
    private val hider = StockIconHider()
    private var strip: ViewGroup? = null
    private var overlayParent: FrameLayout? = null
    private var layer: FrameLayout? = null
    private var rootView: View? = null

    /** All-overlay strips delegate to [IosIconHost]; the delegate draws with Canvas only (IOS-R2 0c). */
    private var composed: IosIconHost? = null

    private var updating = false
    private var attachRetry: Runnable? = null
    private var attachAttempts = 0

    /** R6 item 5: layouts since the last per-layout inventory scan; the scan is throttled. */
    private var layoutsSinceScan = 0

    /** R4 item 7: one shared-receiver subscription per host, plus its own power accounting. */
    private val power = IosPowerCounters(clock = { SystemClock.uptimeMillis() })

    /**
     * R5A item 3: true only after [power.onAttach] counted a successful attach. A failed attach may
     * still set [isAttached] before its empty-record teardown, so `wasAttached` alone cannot tell a
     * real lifecycle teardown from that failure.
     */
    private var countedAttach = false
    private var lastRefreshAt = 0L

    /** A one-per-minute refresh is cheap; screen-off still never reads. */
    private var screenOn = true

    private var lastCellPlan: IosCellPlan = IosCellPlan.None
    private var lastCellSignature = ""

    private var cachedLowThreshold: IosMapping.LowThreshold? = null
    private var lastWifiLevel = 0
    private var lastWifiOn = false
    private var lastWifiActive = false
    private var lastWifiValidated = false

    /**
     * The last snapshot this host actually drew, kept for the diagnostics task and because a value
     * must never be re-read per surface (R3 A4): every glyph on this host follows one sample.
     */
    private var lastWifiSample: WifiSample? = null
    private var lastCellReading: CellReading? = null
    private var lastBatteryReading: IosBatteryReading? = null

    /** R4A item 3: the middle Wi-Fi slot state copied onto every visual (R4B draws the label). */
    private var wifiSlotState: IosWifiSlot = IosWifiSlot.None

    /**
     * R5A item 1-runtime: one parseable line naming the label decision and its anchor, refreshed with
     * [wifiSlotState] and emitted by [diagnosticLines]. It is a dump line, not a log line, so a normal
     * refresh never writes to logcat (m2).
     */
    private var labelReport: String = ""

    /** R3 A5.3: one entry per battery/Wi-Fi/cellular stock icon, rebuilt on every attach. */
    private val iconReports = ArrayList<IconReport>()

    /**
     * The inventory/plan signature the current [iconReports] were built from. A later refresh that
     * sees a different signature (a SIM added/removed, an icon added by the ROM) rebuilds the report
     * off the same tree read it already did; unchanged refreshes never rebuild.
     */
    private var lastReportSignature = ""

    /** IOS-LAG: attach/teardown churn and drawable re-assert fights are backed off per surface. */
    private val thrashTripped: Boolean
        get() = IosSurfaceThrash.isTripped(surface)
    private var thrashTripInProgress = false

    private val refreshRunnable = Runnable { refresh() }

    /** One debounced repaint per burst of OEM tint writes; nothing draws from the hook itself. */
    private val tintRefreshRunnable = Runnable { if (isAttached) refresh() }
    private val tintCallback: () -> Unit = {
        try {
            handler.removeCallbacks(tintRefreshRunnable)
            handler.post(tintRefreshRunnable)
        } catch (_: Throwable) {
        }
    }

    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        onLayoutChanged()
    }

    /**
     * R4 item 7: one subscription to the process-wide [IosBroadcastHub] (not a private receiver).
     *
     * The hub owns the single `IosReadings.invalidate()` per received event; this host only counts
     * that event and routes it through the same screen/invalidate logic the old private receiver had.
     */
    override fun onIosBroadcast(action: String) {
        try {
            when (action) {
                Intent.ACTION_BATTERY_CHANGED,
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED,
                ConnectivityManager.CONNECTIVITY_ACTION,
                ACTION_SERVICE_STATE,
                ACTION_ANY_DATA_STATE,
                ACTION_WIFI_RSSI,
                ACTION_WIFI_STATE -> {
                    power.onInvalidate()
                    scheduleRefresh()
                }

                // R3 A4: a low-cost freshness tick; the platform sends it once a minute.
                Intent.ACTION_TIME_TICK -> if (screenOn) scheduleRefresh()

                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    handler.removeCallbacks(refreshRunnable)
                }

                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    if (isAttached) scheduleRefresh()
                }

                else -> Unit
            }
        } catch (t: Throwable) {
            logOnce.once("receiver") {
                L.w("iOS replace receiver: ${t.javaClass.simpleName}: ${t.message}")
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

    // ----------------------------------------------------------------------------- attach

    /** Attaches over the home status bar. A live attachment for the same window is kept as-is. */
    fun attach(root: View): Boolean {
        composed?.let { return it.attach(root) }
        if (thrashBlocked(root)) return false
        if (isAttached && attachedAlive(root)) return true
        if (isAttached) teardown()
        val result = try {
            val found = finder.findStatusIconsHost(root)
            if (found == null) {
                logOnce.once("no-strip") {
                    L.i("iOS replace: no status icon strip - stock icons untouched")
                }
                recordNoStrip()
                false
            } else {
                attachToStrip(null, found, root, null, null)
            }
        } catch (t: Throwable) {
            failAttach(t)
        }
        return finishAttach(result) { attach(root) }
    }

    /**
     * Attaches over an extra surface (keyguard, shade header) by its strip entry name.
     *
     * A live attachment for the same window is never torn down just because another strip id was
     * requested in that window (IOS-LAG: the shade fires a layout pass per frame).
     */
    fun attachSurface(name: String, root: View, stripId: String = "system_icons"): Boolean {
        composed?.let { return it.attachSurface(name, root, stripId) }
        if (thrashBlocked(root)) return false
        // R3 A1: a strip entry name can exist several times in the shade window; the first DFS hit is
        // often an invisible header copy. Resolve by usability, and re-target a host that is still on
        // an invisible copy once a visible one appears.
        val usable = findUsableStrip(root, stripId)
        val now = SystemClock.uptimeMillis()
        val current = strip
        val decision = retarget(
            attached = isAttached,
            attachedUsable = stripUsable(current),
            usableExists = usable != null,
            currentStripIsUsableCopy = current != null && current === usable,
            msSinceLastRetarget = lastSurfaceRetargetAt?.let { now - it } ?: Long.MAX_VALUE
        )
        if (decision) {
            teardownInternal(countLifecycle = false)
            lastSurfaceRetargetAt = now
        }
        if (!decision && isAttached && attachedAlive(root)) return true
        // R6 item 7: a closing panel may hide (or transiently detach) this strip while the same window
        // survives. Keeping the records avoids the teardown/re-adopt stock frame; only a new
        // window/identity (or a detached overlay layer) rebuilds.
        //
        // Same-strip close/open is kept: the next attachSurface (or the panel's pre-draw) simply finds
        // the same host and returns. A ROM that inflates a NEW header view necessarily re-adopts once —
        // a removed view cannot be owned — and the attach happens inside that frame's pre-draw rather
        // than a posted runnable, so no stock frame is shown. No liveness timer is needed: if the strip
        // never returns the host is inert, and teardown/settings/a new root clears it explicitly.
        if (!decision && IosAttachDecision.keepHidden(
                isAttached = isAttached,
                sameWindow = sameWindowAs(root),
                layerAttached = layerAttached()
            )
        ) {
            return true
        }
        if (isAttached) teardown()
        if (thrashTripped) return false
        val result = try {
            val found = usable
            if (found == null) {
                logOnce.once("no-strip:$name:$stripId") {
                    L.i("iOS replace $name: $stripId not visible - stock icons untouched")
                }
                recordNoStrip()
                false
            } else {
                attachToStrip(name, found, root, null, null)
            }
        } catch (t: Throwable) {
            failAttach(t)
        }
        return finishAttach(result) { attachSurface(name, root, stripId) }
    }

    /**
     * Attaches over a vendor QS cluster handed over directly (OnePlus shade). The [battery] sibling is
     * only added when it is a real battery icon outside the cluster; it is never written.
     */
    fun attachCluster(surface: String, cluster: ViewGroup, battery: View?, source: String): Boolean {
        composed?.let { return it.attachCluster(surface, cluster, battery, source) }
        if (thrashBlocked(cluster)) return false
        // R5A item 3: only the strip this host already owns may take the fast path. A different
        // cluster handed over while the old strip is still parented in the same root (a freshly
        // inflated QS header) must be adopted, so the old strip is restored once first.
        if (isAttached && strip === cluster && attachedAlive(cluster)) return true
        if (isAttached) teardown()
        val result = try {
            val root = cluster.rootView ?: cluster
            attachToStrip(surface, cluster, root, null, battery)
        } catch (t: Throwable) {
            failAttach(t)
        }
        return finishAttach(result) { attachCluster(surface, cluster, battery, source) }
    }

    /**
     * True while this attachment still belongs to [root]'s window. Read-only.
     *
     * A strip still parented in the same window root is alive even when the window transiently
     * detached (a closing shade): the measured header host was rebuilt 20 times in 31 minutes because
     * the old predicate required `isAttachedToWindow` (`.pm/device/r4/diag-lsposed-key.txt:286-322`).
     * A replaced strip or a different window root still fails and rebuilds once.
     */
    internal fun attachedAlive(root: View): Boolean {
        composed?.let { return it.attachedAlive(root) }
        return IosAttachDecision.alive(
            isAttached = isAttached,
            stripInTree = strip?.let { it.parent != null || it === root } == true,
            layerAttached = layerAttached(),
            stripRoot = strip?.rootView,
            rootRoot = root.rootView
        )
    }

    /** True while this attachment really lives under [root]. Read-only. */
    internal fun attachedIn(root: View): Boolean {
        composed?.let { return it.attachedIn(root) }
        return isAttached && strip?.let { isUnder(it, root) } == true && layerAttached()
    }

    /**
     * R6 item 7: [root] is the same window this host attached under. The stored attach root remains a
     * stable identity while a closing panel detaches the strip (a detached view reports itself as its
     * own `rootView`), so the comparison never falls through to a rebuild.
     */
    internal fun sameWindowAs(root: View): Boolean {
        val current = rootView ?: return false
        if (current === root) return true
        val currentRoot = current.rootView
        return currentRoot != null && currentRoot === root.rootView
    }

    /**
     * True while this attachment lives under [root] *and* its strip is still usable (shown, non-zero).
     *
     * The callers used [attachedIn] alone, so a host stuck on a hidden/zero-area copy short-circuited
     * before the re-target path in [attachSurface] could move it to the visible copy (A1). A composed
     * overlay host keeps its own usability handling, so it keeps the existing [IosIconHost] answer.
     */
    internal fun attachedUsableIn(root: View): Boolean {
        composed?.let { return it.attachedIn(root) }
        return attachedIn(root) && stripUsable(strip)
    }

    /**
     * True when [stripId] resolves under [root] and the strip has a replaceable plan or an overlay
     * parent. Read-only; any failure is "not attachable" (rule 5).
     */
    internal fun canAttachSurface(root: View, stripId: String): Boolean {
        composed?.let { return it.canAttachSurface(root, stripId) }
        return try {
            val found = findUsableStrip(root, stripId) ?: return false
            val inventory = effectiveInventory(inventoryOf(found, null))
            val plans = inventory.map { planForSafe(it.view) }
            val replaceable = plans.any {
                it.mechanism != IosReplaceMechanism.OVERLAY && it.mechanism != IosReplaceMechanism.SKIP
            }
            replaceable || finder.findOverlayContainer(found, root) is FrameLayout
        } catch (_: Throwable) {
            false
        }
    }

    /** Test seam: the anti-thrash trip state. */
    internal fun isThrashTrippedForTest(): Boolean = thrashTripped

    /** Test seam: how many stock icons this host currently owns. */
    internal fun recordCountForTest(): Int = records.size

    /** Test seam: the cellular row role a slot is drawing, or null when stock. */
    internal fun cellRoleForTest(view: View): IosCellRole? =
        records.firstOrNull { it.kind == IosIconKind.CELL && it.outer === view }?.cellRole

    /** Test seam: the tint in the visual currently applied to [view]'s record. */
    internal fun visualTintForTest(view: View): Int? =
        records.firstOrNull { it.leaf === view || it.outer === view }?.visual?.tint

    /** Test seam: the kinds of the records currently owned. */
    internal fun recordKindsForTest(): List<IosIconKind> = records.map { it.kind }

    /** Test seam: the visual currently applied to [view]'s record, if any. */
    internal fun visualForTest(view: View): IosVisual? =
        records.firstOrNull { it.leaf === view || it.outer === view }?.visual

    /** Test seam: the mechanism the current record for [kind] draws with, or null when none. */
    internal fun mechanismForTest(kind: IosIconKind): IosReplaceMechanism? =
        recordOf(kind)?.mechanism

    /** Test seam: true when the current WIFI record is a data-type label, not a Wi-Fi glyph. */
    internal fun labelSlotForTest(): Boolean =
        records.firstOrNull { it.kind == IosIconKind.WIFI }?.labelSlot == true

    /** Test seam: the view the current record for [kind] is anchored to, if any. */
    internal fun recordOuterForTest(kind: IosIconKind): View? = recordOf(kind)?.outer

    /** Test seam: the overlay canvas the current record for [kind] owns, or null. */
    internal fun overlayForTest(kind: IosIconKind): IosCanvasView? = recordOf(kind)?.overlay

    /** Test seam: how many attach retries have been consumed (never resets on a failed attach). */
    internal fun attachAttemptsForTest(): Int = attachAttempts

    /** Test seam: the exact Wi-Fi snapshot this host last drew (same instance for every surface). */
    internal fun lastWifiSampleForTest(): WifiSample? = lastWifiSample

    /** Test seam: the exact cellular reading this host last drew. */
    internal fun lastCellReadingForTest(): CellReading? = lastCellReading

    /**
     * Test seam: recomputes every record's tint from its current leaf/outer, without re-reading any
     * system state, and repaints the visuals. This is exactly the tint path a real tint callback feeds.
     */
    internal fun refreshTintForTest() {
        if (!isAttached) return
        for (record in records.toList()) {
            val current = record.visual ?: continue
            val tint = tintFor(record.leaf, record.outer, record.oemTint)
            applyVisual(record, current.withTint(tint))
        }
    }

    /**
     * Test seam: attaches into an explicit strip/overlay pair, skipping ROM-specific resolution. A null
     * [overlayParent] is allowed; only OVERLAY records need one.
     */
    internal fun attachIn(strip: ViewGroup, overlayParent: FrameLayout?): Boolean {
        composed?.let { delegate -> return delegate.attachIn(strip, overlayParent ?: return false) }
        if (isAttached) {
            if (this.strip === strip) return true
            teardownInternal(countLifecycle = false)
        }
        val root = strip.rootView ?: strip
        return try {
            attachToStrip(null, strip, root, overlayParent, null)
        } catch (t: Throwable) {
            failAttach(t)
        }
    }

    // ----------------------------------------------------------------------------- lifecycle

    /** Stores the user's iOS options and repaints from the last snapshot. A real change resets backoff. */
    fun refreshSettings(settings: ModuleSettings) {
        val previous = this.settings
        val changed = previous != settings
        if (changed) {
            resetThrash()
            lastSurfaceRetargetAt = null
        }
        this.settings = settings
        hiddenStockKeys = DuoPrefs.parseHiddenStockIcons(settings.hiddenStockIcons).toSet()
        composed?.let {
            it.refreshSettings(settings)
            return
        }
        val hideChanged = changed &&
            (previous.hideOtherIcons != settings.hideOtherIcons ||
                previous.hiddenStockIcons != settings.hiddenStockIcons)
        if (hideChanged && isAttached) {
            // Put the stock views back first, so an icon that should now show is not left hidden,
            // then re-apply the new selection and only then let refresh adopt what is left.
            hider.restore()
            strip?.let { applyHiding(it) }
            dropUserHiddenRecords()
            strip?.let { applyHiding(it) }
        }
        refresh()
    }

    /** A stock icon appeared after attach. Only the debounced refresh is scheduled (C2). */
    fun onStockIconAdded(view: View) {
        composed?.let {
            it.onStockIconAdded(view)
            return
        }
        if (!isAttached) return
        deferred.remove(view)
        deferredArea.remove(view)
        val strip = strip ?: return
        if (!isUnder(view, strip)) return
        scheduleRefresh()
    }

    /** A stock icon was removed: drop its record, restore it, and let the plan re-evaluate. */
    fun onStockIconRemoved(view: View) {
        composed?.let {
            it.onStockIconRemoved(view)
            return
        }
        if (!isAttached) return
        deferred.remove(view)
        deferredArea.remove(view)
        val record = records.firstOrNull { r ->
            r.outer === view || r.leaf === view || r.clusterOuters.any { it === view }
        } ?: return
        dropRecord(record, restore = true)
        scheduleRefresh()
    }

    /** Reads one snapshot and updates every adopted drawing. Debounced from broadcasts/layout. */
    fun refresh() {
        composed?.let {
            it.refresh()
            return
        }
        if (!isAttached || thrashTripped) return
        lastRefreshAt = SystemClock.uptimeMillis()
        power.onRefresh()
        try {
            // A failed collapse restore is retried before the plan reads the slot visibility again.
            retryPendingCollapses()
            for (record in records.toList()) {
                if (record.outer.parent == null) dropRecord(record, restore = true)
            }
            val inventory = inventoryOf(strip ?: return, null)
            val batteryIcon = selectBest(inventory, StockIconKind.BATTERY)
            val wifiIcon = selectBest(inventory, StockIconKind.WIFI)
            val cellIcons = mappedCells(inventory)

            // Cellular: the plan is read BEFORE the Wi-Fi slot because a data-type label needs the
            // data SIM's own slot. Reading it here keeps the one-read-per-refresh rule; the later
            // cell adoption and report reuse these values instead of reading again.
            val reading = if (cellIcons.isNotEmpty() || records.any { it.kind == IosIconKind.CELL }) {
                readCell()
            } else {
                null
            }
            val plan = if (reading == null) {
                IosCellPlan.None
            } else {
                IosMapping.cellPlan(
                    cellSignals(reading),
                    reading.defaultSub,
                    cellSlots(cellIcons),
                    IosMapping.simDisplay(settings.simDisplay),
                    samples = reading.samples
                )
            }

            val batteryRecord = recordOf(IosIconKind.BATTERY)
            val wifiRecord = recordOf(IosIconKind.WIFI)

            val batteryTint = tintFor(
                batteryRecord?.leaf,
                batteryRecord?.outer ?: batteryIcon?.view,
                batteryRecord?.oemTint ?: 0
            )
            val wifiTint = tintFor(
                wifiRecord?.leaf,
                wifiRecord?.outer ?: wifiIcon?.view,
                wifiRecord?.oemTint ?: 0
            )
            val battery = if (batteryRecord != null || batteryIcon != null) batteryVisual(batteryTint) else null
            val wifi = if (wifiRecord != null || wifiIcon != null) wifiVisual(wifiTint) else null

            // R4A item 3: one shared data-path snapshot for the middle slot. It is computed before any
            // applyVisual so every record of this refresh carries the same state (R4B draws the label).
            val dataSlot = try {
                IosReadings.dataSlot(context)
            } catch (_: Throwable) {
                DataLabelSample("", false, DataLabelReader.SRC_UNAVAILABLE)
            }
            wifiSlotState = IosMapping.wifiSlotState(
                wifiVisible = wifi?.visible == true,
                mobileActive = dataSlot.mobileActive,
                label = dataSlot.label
            )

            // Battery: one record. A null read keeps the last snapshot (M7); never a blank glyph.
            if (batteryRecord != null) {
                if (battery == null) {
                    if (batteryRecord.visual == null) dropRecord(batteryRecord, restore = true)
                } else {
                    applyVisual(batteryRecord, battery)
                }
            } else if (batteryIcon != null && battery != null && battery.visible) {
                adoptOrDefer(IosIconKind.BATTERY, batteryIcon, battery)
            }

            // Wi-Fi slot (item 3): the validated glyph, a data-type label drawn by our own overlay when
            // Wi-Fi is off, or the stock drawing. One WIFI record at most, shared by every surface.
            when (val slot = wifiSlotState) {
                IosWifiSlot.Wifi -> {
                    // R5A item 1-runtime: a label is not a glyph. Drop it before the glyph path runs so
                    // the normal Wi-Fi drawing owns the slot again (and no overlay is mistaken for it).
                    for (record in records.toList()) {
                        if (record.kind == IosIconKind.WIFI && record.labelSlot) {
                            dropRecord(record, restore = true)
                        }
                    }
                    val glyph = recordOf(IosIconKind.WIFI)
                    if (glyph != null) {
                        if (wifi == null || !wifi.visible) {
                            dropRecord(glyph, restore = true)
                        } else {
                            ensureWifiSuppression(glyph)
                            applyVisual(glyph, wifi)
                        }
                    } else if (wifiIcon != null && wifi != null && wifi.visible) {
                        adoptOrDefer(IosIconKind.WIFI, wifiIcon, wifi)
                    }
                    labelReport = "ios surface=$surface label state=Wifi"
                }
                is IosWifiSlot.DataType -> {
                    val labelVisual = labelVisualFor(slot, wifiTint)
                    // Only the stock Wi-Fi slot is adopted here; the data SIM's `mobile_type` label is
                    // owned by the post-cell reconcile. R6: a label is never an overlay, so a Wi-Fi
                    // slot whose plan would need one stays stock and the cell label owns the slot.
                    val existing = records.firstOrNull {
                        it.kind == IosIconKind.WIFI && !isMobileType(it.outer) &&
                            it.mechanism != IosReplaceMechanism.OVERLAY
                    }
                    if (existing != null && existing.outer.parent != null && hasRealBox(existing)) {
                        // Exactly one WIFI record: anything else (including a stale type label) is
                        // restored.
                        for (record in records.toList()) {
                            if (record.kind == IosIconKind.WIFI && record !== existing) {
                                dropRecord(record, restore = true)
                            }
                        }
                        ensureWifiSuppression(existing)
                        applyVisual(existing, labelVisual)
                        labelReport = labelLine(slot, existing)
                    } else {
                        // Drop any non-type WIFI record (glyph or stale Wi-Fi-slot label). A live
                        // `mobile_type` label is left for the reconcile to keep or drop.
                        for (record in records.toList()) {
                            if (record.kind == IosIconKind.WIFI && !isMobileType(record.outer)) {
                                dropRecord(record, restore = true)
                            }
                        }
                        val stock = selectWifiSlotStock(inventory)?.takeIf {
                            it.view.visibility == View.VISIBLE &&
                                planForSafe(it.view).mechanism != IosReplaceMechanism.OVERLAY
                        }
                        if (stock != null) {
                            // The Wi-Fi slot owns the label; a mobile_type label must not coexist.
                            for (record in records.toList()) {
                                if (record.kind == IosIconKind.WIFI) dropRecord(record, restore = true)
                            }
                            adoptOrDefer(IosIconKind.WIFI, stock, labelVisual)
                            val record = recordOf(IosIconKind.WIFI)
                            if (record != null) {
                                applyVisual(record, labelVisual)
                                labelReport = labelLine(slot, record)
                            } else {
                                labelReport = labelAbsentLine(slot, "no-wifi-slot")
                            }
                        } else {
                            // No visible stock Wi-Fi view (Wi-Fi off on this ROM): the reconcile below
                            // is the single owner of the data SIM's `mobile_type` anchor.
                            labelReport = labelAbsentLine(slot, "deferred-cell")
                        }
                    }
                }
                IosWifiSlot.None -> {
                    // R5A item 1-runtime: the empty slot keeps no label (and no glyph) either.
                    for (record in records.toList()) {
                        if (record.kind == IosIconKind.WIFI) dropRecord(record, restore = true)
                    }
                    labelReport = "ios surface=$surface label state=None"
                }
            }

            // R3 A5.3: an icon added later (or a live 1→2 SIM transition) must gain its own line.
            // The signature check runs off the tree read above, so an unchanged refresh does nothing.
            maybeRebuildIconReports(inventory, plan)
            reportCellPlan(plan, reading, cellIcons)
            val signature = cellSignature(plan)
            lastCellPlan = plan
            if (signature != lastCellSignature) {
                lastCellSignature = signature
                dropCellRecords(restore = true)
                adoptCellPlan(plan, cellIcons)
            } else if (!cellRecordsMatch(plan, cellIcons)) {
                dropCellRecords(restore = true)
                adoptCellPlan(plan, cellIcons)
            } else {
                updateCellRecords(plan)
            }
            // R5A item 1-runtime: the cell plan may have replaced the data outer (1→2 SIM, plan
            // change). A `mobile_type` label must follow the current CELL record, never stay over an
            // old slot; a plan that became None leaves no label (the stock type stays visible).
            reconcileWifiLabel(plan, cellIcons, wifiTint)
            updateStockReasons(battery, wifi)
            logStockReasons()
        } catch (t: Throwable) {
            failRefresh(t)
        }
    }

    /**
     * R3 A5.3: names the reason the cellular plan left the stock icons, and logs the `unavailable`
     * veto once per process. The plan itself is computed above from the same inventory; no tree walk.
     */
    private fun reportCellPlan(
        plan: IosCellPlan,
        reading: CellReading?,
        cellIcons: List<StockIcon>
    ) {
        if (plan != IosCellPlan.None || reading == null) return
        val reason = cellNoneReason(reading, cellIcons)
        if (reason == "plan-unavailable") {
            logOnce.once("cell-plan-unavailable") {
                L.i("iOS replace cell plan=none reason=unavailable")
            }
        }
        for (report in iconReports) {
            if (report.kind == IosIconKind.CELL) report.reason = reason
        }
    }

    private fun cellNoneReason(reading: CellReading, cellIcons: List<StockIcon>): String {
        if (reading.samples.any { !it.available }) return "plan-unavailable"
        if (reading.samples.isEmpty()) return "no-active-sim"
        val subs = cellIcons.map { subIdOf(it.view) }
        if (subs.size > 1 && (subs.any { it <= 0 } || subs.distinct().size != subs.size)) {
            return "carrier-duplicate-subId0"
        }
        return "plan-NONE"
    }

    /**
     * Records why a battery/Wi-Fi icon stayed stock. The reasons are observed decisions from the same
     * refresh that already read the tree and the readers; nothing is re-read and no view is written.
     */
    private fun updateStockReasons(battery: IosVisual?, wifi: IosVisual?) {
        for (report in iconReports) {
            if (recordForReport(report) != null) continue
            when (report.kind) {
                IosIconKind.BATTERY -> if (battery == null) report.reason = "battery-unreadable"
                IosIconKind.WIFI ->
                    if (wifi != null && !wifi.visible) report.reason = "wifi-hidden"
                IosIconKind.CELL -> Unit
            }
        }
    }

    /** Logs each stock/fallback reason once per surface and icon class, never per layout pass. */
    private fun logStockReasons() {
        for (report in iconReports) {
            if (recordForReport(report) != null) continue
            val reason = report.reason
            if (reason == "-" || reason == "not-adopted" || reason == "plan-unavailable") continue
            logOnce.once("stock-reason:$surface:${report.kind}:${report.view.javaClass.name}:$reason") {
                L.i("iOS surface=$surface ${report.kind.name} state=stock reason=$reason")
            }
        }
    }

    /** Removes every record, hook, listener and receiver and restores the stock drawing. Idempotent. */
    fun teardown() {
        val delegate = composed
        composed = null
        if (delegate != null) {
            try {
                delegate.teardown()
            } catch (_: Throwable) {
            }
        }
        teardownInternal(countLifecycle = true, resetAttempts = true)
    }

    // ----------------------------------------------------------------------------- attach machinery

    private fun finishAttach(success: Boolean, retry: () -> Boolean): Boolean {
        if (success) {
            attachAttempts = 0
            attachRetry?.let { handler.removeCallbacks(it) }
            attachRetry = null
            power.onAttach()
            countedAttach = true
            return true
        }
        scheduleAttachRetry(retry)
        return false
    }

    /** One pending retry, bounded to [ATTACH_MAX_ATTEMPTS], never past teardown or a tripped guard. */
    private fun scheduleAttachRetry(retry: () -> Boolean) {
        if (isAttached || thrashTripped) return
        if (attachRetry != null) return
        if (attachAttempts >= ATTACH_MAX_ATTEMPTS - 1) return
        attachAttempts++
        val runnable = Runnable {
            attachRetry = null
            if (isAttached || thrashTripped) return@Runnable
            try {
                retry()
            } catch (t: Throwable) {
                failAttach(t)
            }
        }
        attachRetry = runnable
        handler.postDelayed(runnable, ATTACH_RETRY_MS)
    }

    private fun attachToStrip(
        name: String?,
        targetStrip: ViewGroup,
        root: View,
        overlayParent: FrameLayout?,
        extraBattery: View?
    ): Boolean {
        if (thrashBlocked(root)) return false
        // Rule 7: claim before any view write. A refused claim leaves the tree exactly as stock.
        if (!IosSurfaceOwners.claim(surface, targetStrip, this)) {
            logOnce.once("owner-refused:$surface") {
                val owner = IosSurfaceOwners.ownerOf(targetStrip)
                    ?: IosSurfaceOwners.ownerOfSurface(surface)
                    ?: "another host"
                L.i("iOS owner $surface: refused, already owned by $owner")
            }
            return false
        }
        var keep = false
        try {
            keep = attachToStripClaimed(name, targetStrip, root, overlayParent, extraBattery)
            return keep
        } finally {
            if (!keep) IosSurfaceOwners.release(surface, this)
        }
    }

    private fun attachToStripClaimed(
        name: String?,
        targetStrip: ViewGroup,
        root: View,
        overlayParent: FrameLayout?,
        extraBattery: View?
    ): Boolean {
        val inventory = inventoryOf(targetStrip, extraBattery)
        if (inventory.isEmpty()) {
            logOnce.once("no-icons:$name") {
                L.i("iOS replace ${name ?: "main bar"}: no battery/Wi-Fi/cellular icon - stock icons untouched")
            }
            // A5.3: a strip with no recognised icon (e.g. an id-less Compose leaf) used to vanish from
            // the dump entirely. Keep one compact stock line so a user report is diagnosable.
            recordNoIcons()
            return false
        }
        logCellSubIds(inventory)

        // Build the per-icon report before anything can decide to tear this host down (R3 A5.3): a
        // strip where every icon fails has no records to describe, so the inventory snapshot is the
        // only evidence, and it must exist before the records-empty/thrash decision.
        strip = targetStrip
        rootView = root
        layoutsSinceScan = 0
        this.overlayParent = overlayParent
        buildIconReports(inventory)
        lastReportSignature = reportSignature(inventory, IosCellPlan.None)

        // All-overlay strips use the approved hide-in-place + Canvas fallback. No Rive is created:
        // IosIconHost only builds the Canvas element (IOS-R2 0c).
        val plans = inventory.map { planForSafe(it.view) }
        val allOverlayOrSkip = plans.all {
            it.mechanism == IosReplaceMechanism.OVERLAY || it.mechanism == IosReplaceMechanism.SKIP
        }
        if (allOverlayOrSkip && plans.any { it.mechanism == IosReplaceMechanism.OVERLAY }) {
            val parent = overlayParent
                ?: (finder.findOverlayContainer(targetStrip, root) as? FrameLayout)
                ?: run {
                    persistAttempt()
                    return false
                }
            val delegate = IosIconHost(
                context = context,
                sizeOwner = sizeOwner,
                isMainBar = isMainBar,
                surface = "$surface overlay",
                batterySource = batterySource,
                cellSource = cellSource,
                wifiSource = wifiSource
            )
            if (!delegate.attachIn(targetStrip, parent)) {
                persistAttempt()
                return false
            }
            composed = delegate
            this.overlayParent = parent
            isAttached = true
            IosDiagnostics.register(this)
            IosDiagnostics.clearAttempt(surface)
            L.i("iOS owner $surface: overlay host attached")
            noteLifecycle(root, attach = true)
            if (thrashTripped) {
                teardownInternal(countLifecycle = false)
                return false
            }
            return true
        }

        addLayoutListener(targetStrip)
        for (icon in inventory) addLayoutListener(icon.view)
        IosBroadcastHub.subscribe(context, this)
        isAttached = true
        // Item 8-runtime: hide the user's selected stock icons (and, with the legacy switch, the
        // non-replaced ones) before the first refresh, so a selected replaced icon is never adopted.
        applyHiding(targetStrip)
        refresh()
        if (records.isEmpty() && !thrashTripped) {
            // Item 8-runtime: when the user hid every replaceable icon there is nothing to draw, but
            // the hide list is still the feature. Keep the attachment (so teardown restores them) only
            // if no replaceable stock icon is left visible; otherwise the ROM had nothing to replace.
            val visibleLeft = inventory.any { it.view.visibility == View.VISIBLE }
            if (!visibleLeft && inventory.any { it.view.visibility != View.VISIBLE }) {
                L.i("iOS owner $surface: stock icons hidden by the user - nothing to draw")
                noteLifecycle(root, attach = true)
                if (thrashTripped) {
                    persistAttempt()
                    teardownInternal(countLifecycle = false)
                    return false
                }
                IosDiagnostics.register(this)
                IosDiagnostics.clearAttempt(surface)
                return true
            }
        }
        if (records.isEmpty() || thrashTripped) {
            // The stock snapshot is the only line a total failure can produce; keep it before the
            // teardown unregisters this host (review A5 MAJOR 1).
            persistAttempt()
            teardownInternal(countLifecycle = false)
            return false
        }
        L.i("iOS owner $surface: replace host attached")
        noteLifecycle(root, attach = true)
        if (thrashTripped) {
            persistAttempt()
            teardownInternal(countLifecycle = false)
            return false
        }
        IosDiagnostics.register(this)
        IosDiagnostics.clearAttempt(surface)
        return true
    }

    private fun inventoryOf(targetStrip: ViewGroup, extraBattery: View?): List<StockIcon> {
        val out = ArrayList(
            StockIconKey.inventory(targetStrip).filter {
                it.kind == StockIconKind.BATTERY ||
                    it.kind == StockIconKind.WIFI ||
                    it.kind == StockIconKind.CELLULAR
            }
        )
        if (extraBattery != null && extraBattery.visibility != View.GONE &&
            !isUnder(extraBattery, targetStrip) && out.none { it.view === extraBattery }
        ) {
            val kind = try {
                StockIconKey.kindOf(extraBattery)
            } catch (_: Throwable) {
                StockIconKind.OTHER
            }
            if (kind == StockIconKind.BATTERY) {
                out.add(
                    StockIcon(
                        key = StockIconKey.keyOf(extraBattery),
                        label = StockIconKey.labelOf(extraBattery),
                        kind = StockIconKind.BATTERY,
                        view = extraBattery
                    )
                )
            }
        }
        return out
    }

    /**
     * IOS-R2 0e: the per-view SIM identity actually used by the cell plan. The mapping already reads
     * each view's proven `getSubId()` ([cellSlots]); this line makes that mapping visible in a report.
     * Built once per attach, never on a draw path.
     */
    private fun logCellSubIds(inventory: List<StockIcon>) {
        val cells = inventory.filter { it.kind == StockIconKind.CELLULAR }
        if (cells.isEmpty()) return
        val mapped = mappedCells(inventory)
        val entries = mapped.mapIndexed { index, icon ->
            val entry = entryName(icon.view) ?: icon.label
            "$entry#${index + 1}=${subIdOf(icon.view)}"
        }
        val subIds = mapped.map { subIdOf(it.view) }.joinToString(",", "[", "]")
        L.i(
            "iOS replace subIds: ${entries.joinToString(" ")} subIds=$subIds " +
                "cells=${mapped.size}/${cells.size}"
        )
    }

    /**
     * R3 A2: only the cellular slots with a readable subscription id take part in the plan.
     *
     * The visible QS/shade strip also carries carrier-group rows (`mobile_in`/`mobile_out`/
     * `mobile_roaming`) whose `getSubId()` is 0; the mapping needs exactly the two SIM slots and used
     * to reject all 14 entries and leave both stock towers. When no slot exposes an id the full list is
     * kept, which is the existing single unreadable-SIM fallback.
     */
    private fun mappedCells(inventory: List<StockIcon>): List<StockIcon> {
        val cells = inventory.filter { it.kind == StockIconKind.CELLULAR }
        val mapped = cells.filter { subIdOf(it.view) > 0 }
        return mapped.ifEmpty { cells }
    }

    /** The inventory the plan reads: unmapped cellular rows never reach it. */
    private fun effectiveInventory(inventory: List<StockIcon>): List<StockIcon> {
        val mapped = mappedCells(inventory)
        return inventory.filter { icon ->
            icon.kind != StockIconKind.CELLULAR || mapped.any { it.view === icon.view }
        }
    }

    // ----------------------------------------------------------------------------- self-diagnosis

    private fun iosKindOf(kind: StockIconKind): IosIconKind? = when (kind) {
        StockIconKind.BATTERY -> IosIconKind.BATTERY
        StockIconKind.WIFI -> IosIconKind.WIFI
        StockIconKind.CELLULAR -> IosIconKind.CELL
        else -> null
    }

    /**
     * Builds the per-surface report once, from the same inventory and plans attach uses.
     *
     * The records cannot describe an icon that was never adopted (SKIP, no leaf, zero size), so the
     * stock inventory is the source of truth here. It is read only; no view is written and no reader
     * is called.
     */
    private fun buildIconReports(inventory: List<StockIcon>) {
        iconReports.clear()
        val mapped = mappedCells(inventory)
        var cellIndex = 0
        // A2/A5.3: the QS dump must describe the two mapped SIMs, not the 12 subId-0 carrier rows that
        // share the same strip. BATTERY/WIFI are unchanged; only the CELL entries are filtered.
        for (icon in effectiveInventory(inventory)) {
            val kind = iosKindOf(icon.kind) ?: continue
            val plan = planForSafe(icon.view)
            val cellSlot = if (kind == IosIconKind.CELL) {
                val mappedIndex = mapped.indexOfFirst { it.view === icon.view }
                if (mappedIndex >= 0) mappedIndex else cellIndex
            } else {
                -1
            }
            iconReports.add(
                IconReport(
                    kind = kind,
                    view = icon.view,
                    family = plan.family,
                    plannedMechanism = plan.mechanism,
                    cellSlot = cellSlot,
                    subId = subIdOf(icon.view),
                    reason = initialStockReason(icon.view, plan),
                    slot = slotSnapshot(icon.view, plan)
                )
            )
            if (kind == IosIconKind.CELL) cellIndex++
        }
    }

    /**
     * The identity of the inventory/plan the report was built from. View identity (not a class name)
     * is included so an icon the ROM adds later forces a rebuild, and the cell plan string makes a
     * 1→2 SIM transition visible even when the same two views keep their roles.
     */
    private fun reportSignature(inventory: List<StockIcon>, plan: IosCellPlan): String = buildString {
        for (icon in inventory) {
            val kind = iosKindOf(icon.kind) ?: continue
            append(kind.name).append(':')
                .append(System.identityHashCode(icon.view)).append(':')
                .append(subIdOf(icon.view)).append('|')
        }
        append('#').append(cellSignature(plan))
    }

    /** Rebuilds the per-icon snapshot only when the inventory/plan signature actually changed. */
    private fun maybeRebuildIconReports(inventory: List<StockIcon>, plan: IosCellPlan) {
        val signature = reportSignature(inventory, plan)
        if (signature == lastReportSignature) return
        lastReportSignature = signature
        buildIconReports(inventory)
    }

    /**
     * Keeps the last stock snapshot of a failed attach in [IosDiagnostics] before its teardown
     * unregisters the host. Strings only, so no view is retained by the process-wide registry.
     */
    private fun persistAttempt() {
        try {
            val lines = diagnosticLines()
            if (lines.isNotEmpty()) IosDiagnostics.recordAttempt(surface, lines)
        } catch (_: Throwable) {
        }
    }

    /** A strip that never resolved still gets one honest line instead of disappearing from the dump. */
    private fun recordNoStrip() {
        IosDiagnostics.recordAttempt(
            surface,
            listOf("ios surface=$surface state=stock reason=no-strip")
        )
    }

    /** A strip that resolved but carries no recognised icon still gets one honest stock line (A5.3). */
    private fun recordNoIcons() {
        IosDiagnostics.recordAttempt(
            surface,
            listOf("ios surface=$surface state=stock reason=no-icons")
        )
    }

    /**
     * The slot rectangle of an icon that was never adopted, using the same leaf geometry the draw path
     * would: the leaf's offset rect when the plan names one, otherwise the stock outer's rect.
     */
    private fun slotSnapshot(view: View, plan: IosReplacePlan): IntArray {
        val leaf = when (plan.mechanism) {
            IosReplaceMechanism.IMAGE_DRAWABLE ->
                findLeaf(view, plan.leafIds, allowFirstImageView = isAospBattery(plan))
            IosReplaceMechanism.OUTER_DRAW ->
                findLeaf(view, plan.leafIds, allowFirstImageView = false)
            IosReplaceMechanism.LEAF_DRAW -> findVisibleSizedLeaf(view, plan.leafIds)
            else -> null
        }
        return if (leaf != null) leafBounds(leaf, view) else rectSnapshot(view, strip ?: view)
    }

    /** The reason a stock icon stays stock before any adoption has happened. */
    private fun initialStockReason(view: View, plan: IosReplacePlan): String {
        if (hiddenStockKeys.contains(stockKeyOf(view))) return "user-hidden"
        if (view.visibility != View.VISIBLE) return "not-visible"
        return when (plan.mechanism) {
            IosReplaceMechanism.SKIP -> "plan-SKIP"
            IosReplaceMechanism.OVERLAY -> "no-overlay-parent"
            IosReplaceMechanism.IMAGE_DRAWABLE -> {
                val leaf = findLeaf(view, plan.leafIds, allowFirstImageView = isAospBattery(plan))
                when {
                    leaf == null -> "no-leaf"
                    leaf.width <= 0 || leaf.height <= 0 -> "zero-size"
                    else -> "not-adopted"
                }
            }
            IosReplaceMechanism.OUTER_DRAW -> {
                val leaf = findLeaf(view, plan.leafIds, allowFirstImageView = false)
                when {
                    leaf == null -> "no-leaf"
                    (leaf.width <= 0 || leaf.height <= 0) &&
                        (view.width <= 0 || view.height <= 0) -> "zero-size"
                    else -> "not-adopted"
                }
            }
            IosReplaceMechanism.LEAF_DRAW -> {
                if (findVisibleSizedLeaf(view, plan.leafIds) == null) "no-leaf" else "not-adopted"
            }
            // A label is an adjunct of an adopted CELL record, never a planner mechanism (kept for
            // exhaustiveness only).
            IosReplaceMechanism.NATIVE_LABEL -> "not-adopted"
        }
    }

    /**
     * One compact, parseable line per stock icon, one label-decision line (R5A item 1-runtime) and one
     * power/watchdog line (R4 item 7).
     */
    override fun diagnosticLines(): List<String> {
        val receivers = IosBroadcastHub.receiverCountForTest()
        composed?.let { return it.diagnosticLines() + power.line(surface, receivers) }
        val label = if (labelReport.isEmpty()) emptyList() else listOf(labelReport)
        if (iconReports.isEmpty()) return label + power.line(surface, receivers)
        val ordered = iconReports.withIndex().sortedWith(
            compareBy({ kindOrder(it.value.kind) }, { it.index })
        )
        val lines = ordered.map { (_, report) ->
            val record = recordForReport(report)
            // The plan asked for one mechanism; the record says what was actually applied. A silent
            // fallback (OVERLAY, or OUTER_DRAW downgraded to the leaf swap) must never claim replaced.
            val state = when {
                record == null -> "stock"
                record.mechanism == IosReplaceMechanism.OVERLAY -> "fallback"
                record.mechanism != report.plannedMechanism -> "fallback"
                else -> "replaced"
            }
            val mechanism = record?.mechanism ?: report.plannedMechanism
            val reason = when {
                record != null -> "-"
                report.view.parent == null -> "detached"
                hiddenStockKeys.contains(stockKeyOf(report.view)) -> "user-hidden"
                else -> report.reason
            }
            val slot = record?.let { slotOf(it) } ?: report.slot ?: intArrayOf(0, 0, 0, 0)
            val tint = tintReport(record, report)
            buildString {
                append("ios surface=").append(surface)
                append(' ').append(report.kind.name)
                append(" family=").append(report.family.name)
                append(" mech=").append(mechanism.name)
                append(" state=").append(state)
                append(" slot=").append(slot[0]).append(',').append(slot[1])
                    .append(',').append(slot[2]).append(',').append(slot[3])
                append(" tint=").append(tint.first).append(':').append(argbHex(tint.second))
                append(" level=").append(levelReport(report))
                append(" reason=").append(reason)
                // Two dual-SIM rows can otherwise be byte-identical; the subscription id keeps the
                // exact-text dedupe from collapsing them into one line.
                if (report.kind == IosIconKind.CELL) {
                    append(" subId=").append(if (report.subId > 0) report.subId else "-")
                }
            }
        }
        return lines + label + power.line(surface, receivers)
    }

    private fun kindOrder(kind: IosIconKind): Int = when (kind) {
        IosIconKind.BATTERY -> 0
        IosIconKind.WIFI -> 1
        IosIconKind.CELL -> 2
    }

    private fun recordForReport(report: IconReport): Record? = records.firstOrNull { record ->
        record.kind == report.kind && (
            record.outer === report.view ||
                record.leaf === report.view ||
                record.collapse?.target === report.view ||
                record.clusterOuters.any { it === report.view }
            )
    }

    /** The record's draw rectangle: the leaf's offset rect, or the overlay's pinned margins. */
    private fun slotOf(record: Record): IntArray {
        if (record.mechanism == IosReplaceMechanism.OVERLAY) {
            record.overlayParams?.let {
                return intArrayOf(
                    it.leftMargin,
                    it.topMargin,
                    it.leftMargin + it.width,
                    it.topMargin + it.height
                )
            }
        }
        val leaf = record.leaf
        return if (leaf != null) {
            leafBounds(leaf, record.outer)
        } else {
            intArrayOf(0, 0, record.outer.width, record.outer.height)
        }
    }

    private fun rectSnapshot(view: View, ancestor: View): IntArray {
        val bounds = boundsIn(view, ancestor)
        return intArrayOf(bounds[0], bounds[1], bounds[0] + bounds[2], bounds[1] + bounds[3])
    }

    private fun stockKeyOf(view: View): String = try {
        StockIconKey.keyOf(view)
    } catch (_: Throwable) {
        ""
    }

    /** The tint source the draw uses, plus its resolved ARGB value. */
    private fun tintReport(record: Record?, report: IconReport): Pair<String, Int> {
        val leaf = record?.leaf ?: report.view
        val outer = record?.outer ?: (report.view.parent as? View)
        val oemTint = record?.oemTint ?: 0
        val (source, resolved) = tintSourceOf(leaf, outer, oemTint)
        val value = record?.visual?.tint ?: resolved
        return source to value
    }

    /**
     * Mirrors [oemTintOf] but names the winning source, so a report can say why the glyph is white
     * (the OEM's ImageTintList) rather than black (the bar colour). Never throws.
     */
    private fun tintSourceOf(leaf: View?, outer: View?, oemTint: Int): Pair<String, Int> {
        try {
            val image = leaf as? ImageView
            if (image != null) {
                val fromList = image.imageTintList?.defaultColor ?: 0
                if (Color.alpha(fromList) != 0) return "imageTint" to fromList
                val filter = image.colorFilter as? PorterDuffColorFilter
                val fromFilter = if (filter != null) {
                    try {
                        (filter.javaClass.getMethod("getColor").invoke(filter) as? Int) ?: 0
                    } catch (_: Throwable) {
                        0
                    }
                } else {
                    0
                }
                if (Color.alpha(fromFilter) != 0) return "porterDuff" to fromFilter
            }
            if (outer != null) {
                val fromImage = (outer as? ImageView)?.imageTintList?.defaultColor ?: 0
                if (Color.alpha(fromImage) != 0) return "imageTint" to fromImage
                val fromBackground = outer.backgroundTintList?.defaultColor ?: 0
                if (Color.alpha(fromBackground) != 0) return "backgroundTint" to fromBackground
                val fromForeground = outer.foregroundTintList?.defaultColor ?: 0
                if (Color.alpha(fromForeground) != 0) return "foregroundTint" to fromForeground
            }
        } catch (_: Throwable) {
        }
        if (Color.alpha(oemTint) != 0) return "oemTint" to oemTint
        val bar = try {
            BarTint.fgColor(context)
        } catch (_: Throwable) {
            0xFFFFFFFF.toInt()
        }
        return "barTint" to bar
    }

    private fun levelReport(report: IconReport): String = when (report.kind) {
        IosIconKind.BATTERY -> lastBatteryReading?.let { "battery:sticky:${it.level}" } ?: "-"
        IosIconKind.WIFI -> lastWifiSample?.let { "wifi:${it.source}:${it.dots}" } ?: "-"
        IosIconKind.CELL -> {
            val reading = lastCellReading
            val sample = reading?.samples?.firstOrNull {
                report.subId > 0 && it.subscriptionId == report.subId
            } ?: reading?.samples?.getOrNull(report.cellSlot)
            when {
                sample != null -> "cell:${sample.src}:${sample.raw}:${sample.dots}"
                reading != null ->
                    "cell:${reading.defaultSrc}:${reading.defaultRaw}:${reading.defaultDots}"
                else -> "-"
            }
        }
    }

    private fun argbHex(value: Int): String {
        val text = Integer.toHexString(value).uppercase()
        return "0".repeat((8 - text.length).coerceAtLeast(0)) + text
    }

    // ----------------------------------------------------------------------------- adoption

    private fun adoptOrDefer(kind: IosIconKind, icon: StockIcon, visual: IosVisual) {
        val record = try {
            adoptIcon(kind, icon, visual)
        } catch (t: Throwable) {
            logOnce.once("adopt-fail:$kind:${icon.view.javaClass.simpleName}") {
                L.w("iOS replace adopt failed: ${t.javaClass.simpleName}: ${t.message}")
            }
            null
        }
        if (record == null) {
            deferred.add(icon.view)
            deferredArea[icon.view] = contentArea(icon.view)
            return
        }
        deferred.remove(icon.view)
        deferredArea.remove(icon.view)
    }

    /**
     * Adopts one stock icon with its plan, falling back exactly as the plan says.
     *
     * The user transform is not computed from the outer slot here: the record is created with the plain
     * [visual] and [applyVisual] then derives it from that record's actual draw box, the same call every
     * later refresh uses. Computing it from the outer at adoption made the offset jump once a refresh
     * ran (an OPLUS battery is 90 px outer but a 79 px leaf).
     */
    private fun adoptIcon(kind: IosIconKind, icon: StockIcon, visual: IosVisual): Record? {
        if (icon.view.visibility != View.VISIBLE) return null
        val plan = planForSafe(icon.view)
        val record = when (plan.mechanism) {
            IosReplaceMechanism.IMAGE_DRAWABLE -> {
                val leaf = findLeaf(icon.view, plan.leafIds, allowFirstImageView = isAospBattery(plan))
                if (leaf != null) {
                    adoptImage(kind, icon.view, leaf, visual, plan.siblingIds)
                } else {
                    adoptFallback(kind, icon.view, plan, visual)
                }
            }
            IosReplaceMechanism.OUTER_DRAW -> {
                val leaf = findLeaf(icon.view, plan.leafIds, allowFirstImageView = false)
                if (leaf == null) {
                    null
                } else if (installOuterHook(icon.view, plan)) {
                    adoptOuter(kind, icon.view, leaf, visual, plan)
                } else {
                    // OPLUS composites draw in/out/type siblings in the same outer; a leaf-only
                    // fallback must blank them or they sit over our glyph (A1 §wifi/mobile).
                    val image = leaf as? ImageView ?: return null
                    adoptImage(kind, icon.view, image, visual, plan.fallbackSiblingIds)
                }
            }
            IosReplaceMechanism.LEAF_DRAW -> {
                val leaf = findVisibleSizedLeaf(icon.view, plan.leafIds)
                if (leaf == null) {
                    null
                } else if (ensureLeafHook(plan, leaf)) {
                    adoptLeaf(kind, icon.view, leaf, visual, plan)
                } else {
                    adoptOverlay(kind, icon.view, null, visual)
                }
            }
            IosReplaceMechanism.OVERLAY -> adoptOverlay(kind, icon.view, null, visual)
            // Never planned; a NATIVE_LABEL record is created only by adoptNativeLabel.
            IosReplaceMechanism.NATIVE_LABEL -> null
            IosReplaceMechanism.SKIP -> null
        }
        if (record != null) applyVisual(record, record.visual ?: visual)
        return record
    }

    private fun adoptFallback(
        kind: IosIconKind,
        outer: View,
        plan: IosReplacePlan,
        visual: IosVisual
    ): Record? = when (plan.fallback) {
        IosReplaceMechanism.IMAGE_DRAWABLE -> {
            val leaf = findLeaf(outer, plan.leafIds, allowFirstImageView = isAospBattery(plan))
            if (leaf != null) adoptImage(kind, outer, leaf, visual, plan.fallbackSiblingIds) else null
        }
        IosReplaceMechanism.OVERLAY -> adoptOverlay(kind, outer, null, visual)
        else -> null
    }

    /**
     * Only an AOSP-shaped battery (`BatteryMeterView` and descendants) may fall back to its first
     * ImageView (`mBatteryIconView` has no id). A named HyperOS/Oplus/leaf id that is absent is not a
     * reason to grab an arbitrary sibling (`mobile_type` on rodin/newer layouts). The shape flag keeps
     * a vendor subclass reported under its measured family (UNKNOWN for Infinix `TrBatteryMeterView`).
     */
    private fun isAospBattery(plan: IosReplacePlan): Boolean = plan.allowFirstImageView

    private fun adoptImage(
        kind: IosIconKind,
        outer: View,
        leaf: View,
        visual: IosVisual,
        siblingIds: List<String>
    ): Record? {
        val image = leaf as? ImageView ?: return null
        val original = image.drawable
        val width = original?.intrinsicWidth?.takeIf { it > 0 } ?: image.width
        val height = original?.intrinsicHeight?.takeIf { it > 0 } ?: image.height
        if (width <= 0 || height <= 0) return null

        // Item 6: capture the colour the system applied to this very icon, then draw with it.
        val oemTint = oemTintOf(image, outer)
        val tinted = visual.withTint(tintFor(image, outer, oemTint))
        val drawable = IosDrawables.create(kind, tinted, width, height, relativeSize = true)
        val swap = IosDrawableSwap(
            leaf = image,
            drawable = drawable,
            onReassert = { noteReassert() },
            restoreDrawable = original
        )
        if (!swap.apply()) return null

        // Item 0d: adopt the OEM's own push in the same call instead of waiting for the next frame.
        IosPushHooks.watch(image, swap)
        IosPushHooks.onTint(image, tintCallback)
        IosPushHooks.onTint(outer, tintCallback)

        val siblings = try {
            applySiblings(outer, image, siblingIds, kind, width, height)
        } catch (t: Throwable) {
            // A half-adopted record must not leave our drawable installed without a record to restore.
            IosPushHooks.unwatch(image)
            IosPushHooks.offTint(image, tintCallback)
            IosPushHooks.offTint(outer, tintCallback)
            try {
                swap.restore()
            } catch (_: Throwable) {
            }
            failAdopt(kind, t)
            return null
        }
        val record = Record(
            kind = kind,
            outer = outer,
            leaf = image,
            mechanism = IosReplaceMechanism.IMAGE_DRAWABLE,
            drawable = drawable,
            swap = swap,
            drawSession = false,
            skin = null,
            overlay = null,
            overlayParams = null,
            siblings = siblings,
            visual = tinted,
            oemTint = oemTint
        )
        records.add(record)
        addLayoutListener(outer)
        addLayoutListener(image)
        if (isMainBar) sizeOwner.pin(kind, width, height)
        if (userTransformFor(kind, drawnWidthFor(kind, width, height, tinted)) != IosUserTransform.IDENTITY) {
            allowGlyphOverflow(image)
        }
        return record
    }

    private fun installOuterHook(outer: View, plan: IosReplacePlan): Boolean {
        val method = plan.drawMethod ?: "dispatchDraw"
        return IosDrawHookInstaller.ensure(classLoader, outer.javaClass.name, method)
    }

    private fun adoptOuter(
        kind: IosIconKind,
        outer: View,
        leaf: View,
        visual: IosVisual,
        plan: IosReplacePlan
    ): Record? {
        val width = leaf.width.takeIf { it > 0 } ?: outer.width
        val height = leaf.height.takeIf { it > 0 } ?: outer.height
        if (width <= 0 || height <= 0) return null

        val oemTint = oemTintOf(leaf, outer)
        val tinted = visual.withTint(tintFor(leaf, outer, oemTint))
        val drawable = IosDrawables.create(kind, tinted, width, height, relativeSize = true)
        // One bounds/offset array per session: the draw hook must not allocate per frame.
        val boundsOut = IntArray(4)
        val offsetOut = IntArray(2)
        val session = IosDrawSession(
            drawable = drawable,
            bounds = { leafBounds(leaf, outer, boundsOut, offsetOut) },
            translateX = translateXProvider(outer, plan.transXField),
            skipOriginal = true
        )
        IosDrawingHooks.register(outer, session)
        IosPushHooks.onTint(leaf, tintCallback)
        IosPushHooks.onTint(outer, tintCallback)
        val siblings = try {
            applySiblings(outer, leaf, plan.siblingIds, kind, width, height)
        } catch (t: Throwable) {
            IosDrawingHooks.unregister(outer)
            IosPushHooks.offTint(leaf, tintCallback)
            IosPushHooks.offTint(outer, tintCallback)
            IosDrawHookInstaller.releaseWhenEmpty()
            failAdopt(kind, t)
            return null
        }
        val record = Record(
            kind = kind,
            outer = outer,
            leaf = leaf,
            mechanism = IosReplaceMechanism.OUTER_DRAW,
            drawable = drawable,
            swap = null,
            drawSession = true,
            skin = null,
            overlay = null,
            overlayParams = null,
            siblings = siblings,
            visual = tinted,
            oemTint = oemTint
        )
        records.add(record)
        addLayoutListener(outer)
        addLayoutListener(leaf)
        if (isMainBar) sizeOwner.pin(kind, width, height)
        if (userTransformFor(kind, drawnWidthFor(kind, width, height, tinted)) != IosUserTransform.IDENTITY) {
            allowGlyphOverflow(outer)
        }
        return record
    }

    /**
     * Hooks the chosen leaf's own class. The plan names both HyperOS battery leaves because the OEM
     * flips them by display style; the signal only fires for the instance whose class was hooked.
     */
    private fun ensureLeafHook(plan: IosReplacePlan, leaf: View): Boolean {
        val method = plan.drawMethod ?: "onDraw"
        var ownClassHooked = false
        for (className in plan.hookClasses) {
            val installed = IosDrawHookInstaller.ensure(classLoader, className, method)
            if (installed && className == leaf.javaClass.name) ownClassHooked = true
        }
        return ownClassHooked
    }

    private fun adoptLeaf(
        kind: IosIconKind,
        outer: View,
        leaf: View,
        visual: IosVisual,
        plan: IosReplacePlan
    ): Record? {
        val width = leaf.width
        val height = leaf.height
        if (width <= 0 || height <= 0) return null
        val oemTint = oemTintOf(leaf, outer)
        val tinted = visual.withTint(tintFor(leaf, outer, oemTint))
        val drawable = IosDrawables.create(kind, tinted, width, height, relativeSize = true)
        val boundsOut = IntArray(4)
        val session = IosDrawSession(
            drawable = drawable,
            bounds = {
                boundsOut[0] = 0
                boundsOut[1] = 0
                boundsOut[2] = leaf.width
                boundsOut[3] = leaf.height
                boundsOut
            },
            translateX = null,
            skipOriginal = true
        )
        IosDrawingHooks.register(leaf, session)
        IosPushHooks.onTint(leaf, tintCallback)
        IosPushHooks.onTint(outer, tintCallback)
        val siblings = try {
            applySiblings(outer, leaf, plan.siblingIds, kind, width, height)
        } catch (t: Throwable) {
            IosDrawingHooks.unregister(leaf)
            IosPushHooks.offTint(leaf, tintCallback)
            IosPushHooks.offTint(outer, tintCallback)
            IosDrawHookInstaller.releaseWhenEmpty()
            failAdopt(kind, t)
            return null
        }
        val record = Record(
            kind = kind,
            outer = outer,
            leaf = leaf,
            mechanism = IosReplaceMechanism.LEAF_DRAW,
            drawable = drawable,
            swap = null,
            drawSession = true,
            skin = null,
            overlay = null,
            overlayParams = null,
            siblings = siblings,
            visual = tinted,
            oemTint = oemTint
        )
        records.add(record)
        addLayoutListener(outer)
        addLayoutListener(leaf)
        if (isMainBar) sizeOwner.pin(kind, width, height)
        if (userTransformFor(kind, drawnWidthFor(kind, width, height, tinted)) != IosUserTransform.IDENTITY) {
            allowGlyphOverflow(leaf)
        }
        return record
    }

    private fun adoptOverlay(
        kind: IosIconKind,
        outer: View,
        leaf: View?,
        visual: IosVisual
    ): Record? {
        val parent = resolveOverlayParent() ?: return null
        val layer = ensureLayer(parent) ?: return null
        val bounds = overlayBounds(outer, leaf, layer)
        if (bounds[2] <= 0 || bounds[3] <= 0) return null
        val size = if (isMainBar) {
            Pair(bounds[2], bounds[3])
        } else {
            sizeOwner.sizeFor(kind, bounds[2], bounds[3])
        }
        if (size.first <= 0 || size.second <= 0) return null

        val skin = IosSkin()
        // R6: the data-type label never uses this overlay path, so every overlay suppresses a visible
        // stock drawing before our canvas is added (a hidden/zero slot is refused by the caller).
        if (!skin.suppress(outer)) {
            return null
        }
        var overlay: IosCanvasView? = null
        var added = false
        // The colour may arrive on the outer (background/foreground tint) or on the leaf.
        val tintViews = listOfNotNull(outer, leaf)
        try {
            val canvas = IosCanvasView(context, kind)
            overlay = canvas
            canvas.relativeSize = true
            if (!canvas.start()) {
                skin.restore()
                return null
            }
            val oemTint = oemTintOf(leaf, outer)
            val tinted = visual.withTint(tintFor(leaf, outer, oemTint))
            // R4A item 5: keep the unscaled slot box and enlarge the canvas to the transformed glyph
            // box; the renderer is then asked for identity pixels inside the larger canvas.
            val baseBox = intArrayOf(bounds[0], bounds[1], size.first, size.second)
            val drawnWidth = drawnWidthFor(kind, size.first, size.second, visual)
            val transform = userTransformFor(kind, drawnWidth)
            val box = overlayBox(
                baseBox[0],
                baseBox[1],
                baseBox[2],
                baseBox[3],
                transform.dxPx,
                transform.scale
            )
            val rendered = tinted.copy(userTransform = IosUserTransform(0f, 1f))
            canvas.render(rendered)
            for (view in tintViews) IosPushHooks.onTint(view, tintCallback)
            val params = FrameLayout.LayoutParams(box[2], box[3]).apply {
                leftMargin = box[0]
                topMargin = box[1]
            }
            added = try {
                layer.addView(canvas, params)
                true
            } catch (_: Throwable) {
                false
            }
            if (!added) {
                skin.restore()
                for (view in tintViews) IosPushHooks.offTint(view, tintCallback)
                canvas.teardown()
                return null
            }
            if (isMainBar) sizeOwner.pin(kind, bounds[2], bounds[3])
            val record = Record(
                kind = kind,
                outer = outer,
                leaf = null,
                mechanism = IosReplaceMechanism.OVERLAY,
                drawable = null,
                swap = null,
                drawSession = false,
                skin = skin,
                overlay = canvas,
                overlayParams = params,
                siblings = emptyList(),
                visual = rendered,
                oemTint = oemTint,
                tintViews = tintViews,
                baseBox = baseBox
            )
            records.add(record)
            addLayoutListener(outer)
            return record
        } catch (t: Throwable) {
            // Rule 5: the stock view was already suppressed, so a failure here must put every write
            // back rather than leave it hidden with no record to restore it.
            val canvas = overlay
            if (canvas != null) {
                if (added) {
                    try {
                        (canvas.parent as? ViewGroup)?.removeView(canvas)
                    } catch (_: Throwable) {
                    }
                }
                try {
                    canvas.teardown()
                } catch (_: Throwable) {
                }
            }
            for (view in tintViews) IosPushHooks.offTint(view, tintCallback)
            try {
                skin.restore()
            } catch (_: Throwable) {
            }
            failAdopt(kind, t)
            return null
        }
    }

    // ----------------------------------------------------------------------------- cellular

    private fun adoptCellPlan(plan: IosCellPlan, cellIcons: List<StockIcon>) {
        when (plan) {
            IosCellPlan.None -> dropCellRecords(restore = true)
            is IosCellPlan.Single -> {
                val icon = cellIcons.getOrNull(plan.slot) ?: return
                val visual = IosMapping.cellVisual(plan, tintFor(null, icon.view, 0)) ?: return
                val record = adoptIcon(IosIconKind.CELL, icon, visual) ?: return
                record.cellRole = IosCellRole.DATA
                record.cellSlot = plan.slot
                record.subId = plan.subId
                val hiddenSlot = plan.hiddenSlot
                if (hiddenSlot != null) {
                    val otherIcon = cellIcons.getOrNull(hiddenSlot)
                    if (otherIcon == null || !suppressDualOther(record, icon, otherIcon)) {
                        // Never leave one iOS row beside one stock icon: restore the adopted slot and
                        // keep BOTH stock icons (rule 5).
                        dropRecord(record, restore = true)
                    }
                }
            }
            is IosCellPlan.Dual -> {
                val dataIcon = cellIcons.getOrNull(plan.dataSlot) ?: return
                val otherIcon = cellIcons.getOrNull(plan.otherSlot) ?: return
                val dataPlan = planForSafe(dataIcon.view)
                if (isTrueReplacement(dataPlan)) {
                    // Item 2: ONE stacked BOTH block in the data SIM's slot; the other slot is removed.
                    val visual = IosMapping.cellVisual(plan, tintFor(null, dataIcon.view, 0)) ?: return
                    val record = adoptIcon(IosIconKind.CELL, dataIcon, visual)
                    if (record != null) {
                        record.cellRole = IosCellRole.BOTH
                        record.cellSlot = plan.dataSlot
                        record.subId = plan.dataSubId
                        if (!suppressDualOther(record, dataIcon, otherIcon)) {
                            // The other slot could not be removed or suppressed: never leave one iOS
                            // row beside one stock icon. Fall back to the one-block overlay.
                            dropRecord(record, restore = true)
                            adoptCellOverlay(plan, dataIcon, otherIcon)
                        }
                    } else {
                        dropCellRecords(restore = true)
                        adoptCellOverlay(plan, dataIcon, otherIcon)
                    }
                } else {
                    // The data slot cannot be truly replaced: one BOTH overlay at the DATA bounds,
                    // skinning both outers, or both stock. Never one iOS row beside one stock icon.
                    adoptCellOverlay(plan, dataIcon, otherIcon)
                }
            }
        }
    }

    /**
     * Removes the other SIM's slot for the one BOTH record. Collapsing is only allowed for a visible,
     * measurable sibling of the data view; otherwise the approved [IosSkin] INVISIBLE suppression keeps
     * the slot rather than guessing at a layout write (C2).
     */
    private fun suppressDualOther(record: Record, dataIcon: StockIcon, otherIcon: StockIcon): Boolean {
        val otherView = otherIcon.view
        val collapse = collapseSibling(dataIcon.view, otherView)
        if (collapse != null) {
            record.collapse = collapse
            IosPushHooks.onTint(otherView, tintCallback)
            addLayoutListener(otherView)
            L.i(
                "iOS dual: one slot on ${entryName(dataIcon.view) ?: dataIcon.label}, " +
                    "collapsed ${entryName(otherView) ?: otherIcon.label}"
            )
            return true
        }
        val skin = IosSkin()
        if (skin.suppress(otherView)) {
            record.clusterSkins = listOf(skin)
            record.clusterOuters = listOf(otherView)
            IosPushHooks.onTint(otherView, tintCallback)
            addLayoutListener(otherView)
            return true
        }
        return false
    }

    private fun collapseSibling(data: View, other: View): IosCollapse? {
        val parent = data.parent ?: return null
        if (other.parent !== parent) return null
        if (other.visibility != View.VISIBLE) return null
        if (contentArea(data) <= 0) return null
        if (contentArea(other) <= 0) return null
        val collapse = IosCollapse(other)
        return if (collapse.apply()) collapse else null
    }

    private fun adoptCellOverlay(plan: IosCellPlan.Dual, dataIcon: StockIcon, otherIcon: StockIcon) {
        val parent = resolveOverlayParent() ?: return
        val layer = ensureLayer(parent) ?: return
        // Item 2: anchor the one BOTH block to the DATA slot, never the two-slot union.
        val bounds = boundsIn(dataIcon.view, layer)
        if (bounds[2] <= 0 || bounds[3] <= 0) return
        val size = if (isMainBar) {
            Pair(bounds[2], bounds[3])
        } else {
            sizeOwner.sizeForBlock(bounds[2], bounds[3])
        }
        if (size.first <= 0 || size.second <= 0) return
        val oemTint = oemTintOf(null, dataIcon.view)
        val baseVisual = IosMapping.cellVisual(plan, tintFor(null, dataIcon.view, oemTint)) ?: return
        // R4A item 5: same enlarged-canvas contract as the single-slot overlay.
        val baseBox = intArrayOf(bounds[0], bounds[1], size.first, size.second)
        val drawnWidth = drawnWidthFor(IosIconKind.CELL, size.first, size.second, baseVisual)
        val transform = userTransformFor(IosIconKind.CELL, drawnWidth)
        val box = overlayBox(
            baseBox[0],
            baseBox[1],
            baseBox[2],
            baseBox[3],
            transform.dxPx,
            transform.scale
        )
        val visual = baseVisual.copy(userTransform = IosUserTransform(0f, 1f))

        val collapse = collapseSibling(dataIcon.view, otherIcon.view)
        val firstSkin = IosSkin()
        if (!firstSkin.suppress(dataIcon.view)) {
            collapse?.restore()
            return
        }
        val secondSkin = if (collapse == null) {
            val skin = IosSkin()
            if (!skin.suppress(otherIcon.view)) {
                firstSkin.restore()
                return
            }
            skin
        } else {
            null
        }
        var overlay: IosCanvasView? = null
        var added = false
        try {
            val canvas = IosCanvasView(context, IosIconKind.CELL)
            overlay = canvas
            canvas.relativeSize = true
            if (!canvas.start()) {
                firstSkin.restore()
                secondSkin?.restore()
                collapse?.restore()
                return
            }
            canvas.render(visual)
            IosPushHooks.onTint(dataIcon.view, tintCallback)
            IosPushHooks.onTint(otherIcon.view, tintCallback)
            val params = FrameLayout.LayoutParams(box[2], box[3]).apply {
                leftMargin = box[0]
                topMargin = box[1]
            }
            added = try {
                layer.addView(canvas, params)
                true
            } catch (_: Throwable) {
                false
            }
            if (!added) {
                firstSkin.restore()
                secondSkin?.restore()
                collapse?.restore()
                IosPushHooks.offTint(dataIcon.view, tintCallback)
                IosPushHooks.offTint(otherIcon.view, tintCallback)
                canvas.teardown()
                return
            }
            if (isMainBar) sizeOwner.pinBlock(bounds[2], bounds[3])
            if (collapse != null) {
                L.i(
                    "iOS dual: one slot on ${entryName(dataIcon.view) ?: dataIcon.label}, " +
                        "collapsed ${entryName(otherIcon.view) ?: otherIcon.label}"
                )
            }
            val record = Record(
                kind = IosIconKind.CELL,
                outer = dataIcon.view,
                leaf = null,
                mechanism = IosReplaceMechanism.OVERLAY,
                drawable = null,
                swap = null,
                drawSession = false,
                skin = firstSkin,
                overlay = canvas,
                overlayParams = params,
                siblings = emptyList(),
                visual = visual,
                oemTint = oemTint,
                cellRole = IosCellRole.BOTH,
                cellSlot = plan.dataSlot,
                subId = plan.dataSubId,
                collapse = collapse,
                clusterSkins = if (secondSkin != null) listOf(secondSkin) else emptyList(),
                clusterOuters = if (secondSkin != null) listOf(otherIcon.view) else emptyList(),
                baseBox = baseBox
            )
            records.add(record)
            addLayoutListener(dataIcon.view)
            addLayoutListener(otherIcon.view)
        } catch (t: Throwable) {
            // Rule 5: a failure after either slot was suppressed must put every write back rather than
            // leave the other SIM GONE/0x0 with no record to restore it.
            val canvas = overlay
            if (canvas != null) {
                if (added) {
                    try {
                        (canvas.parent as? ViewGroup)?.removeView(canvas)
                    } catch (_: Throwable) {
                    }
                }
                try {
                    canvas.teardown()
                } catch (_: Throwable) {
                }
            }
            IosPushHooks.offTint(dataIcon.view, tintCallback)
            IosPushHooks.offTint(otherIcon.view, tintCallback)
            try {
                firstSkin.restore()
            } catch (_: Throwable) {
            }
            try {
                secondSkin?.restore()
            } catch (_: Throwable) {
            }
            try {
                collapse?.restore()
            } catch (_: Throwable) {
            }
            failAdopt(IosIconKind.CELL, t)
        }
    }

    private fun updateCellRecords(plan: IosCellPlan) {
        for (record in records.toList()) {
            if (record.kind != IosIconKind.CELL) continue
            // Each record follows its own leaf/outer tint (e.g. the keyguard's ImageTintList).
            val tint = tintFor(record.leaf, record.outer, record.oemTint)
            val role = record.cellRole
            val visual = when (plan) {
                IosCellPlan.None -> null
                is IosCellPlan.Single -> IosMapping.cellVisual(plan, tint)
                is IosCellPlan.Dual ->
                    if (role == IosCellRole.BOTH || role == null) {
                        // Item 2: one stacked block, both rows, in the data slot.
                        IosMapping.cellVisual(plan, tint)
                    } else {
                        dualVisual(plan, role, tint)
                    }
            }
            if (visual == null) {
                dropRecord(record, restore = true)
            } else if (visual.visible) {
                applyVisual(record, visual)
            } else {
                dropRecord(record, restore = true)
            }
        }
    }

    private fun cellRecordsMatch(plan: IosCellPlan, cellIcons: List<StockIcon>): Boolean {
        return when (plan) {
            IosCellPlan.None -> records.none { it.kind == IosIconKind.CELL }
            is IosCellPlan.Single -> {
                val target = cellIcons.getOrNull(plan.slot)?.view ?: return false
                val cellRecords = records.filter { it.kind == IosIconKind.CELL }
                if (cellRecords.size != 1) return false
                val record = cellRecords[0]
                if (record.outer !== target || record.cellRole != IosCellRole.DATA) return false
                val hiddenSlot = plan.hiddenSlot
                if (hiddenSlot == null) {
                    record.collapse == null
                } else {
                    // A forced single row must still suppress the other mapped slot exactly like Dual.
                    val other = cellIcons.getOrNull(hiddenSlot)?.view ?: return false
                    val collapsed = record.collapse?.let { it.isApplied && it.target === other } == true
                    val skinned = record.clusterOuters.any { it === other } &&
                        record.clusterSkins.isNotEmpty()
                    collapsed || skinned
                }
            }
            is IosCellPlan.Dual -> {
                val data = cellIcons.getOrNull(plan.dataSlot)?.view ?: return false
                val other = cellIcons.getOrNull(plan.otherSlot)?.view ?: return false
                val record = records.filter { it.kind == IosIconKind.CELL }.singleOrNull()
                    ?: return false
                if (record.outer !== data || record.cellRole != IosCellRole.BOTH) return false
                val collapsed = record.collapse?.let { it.isApplied && it.target === other } == true
                val skinned = record.clusterOuters.any { it === other } &&
                    record.clusterSkins.isNotEmpty()
                collapsed || skinned
            }
        }
    }

    private fun dropCellRecords(restore: Boolean) {
        for (record in records.toList()) {
            if (record.kind == IosIconKind.CELL) dropRecord(record, restore)
        }
    }

    private fun cellSignature(plan: IosCellPlan): String = when (plan) {
        IosCellPlan.None -> "none"
        is IosCellPlan.Single -> "s:${plan.slot}:${plan.subId}:${plan.hiddenSlot ?: "-"}"
        is IosCellPlan.Dual ->
            "d:${plan.dataSlot}:${plan.otherSlot}:${plan.dataSubId}:${plan.otherSubId}"
    }

    private fun cellSlots(cellIcons: List<StockIcon>): List<CellSlot> =
        cellIcons.map { CellSlot(subId = subIdOf(it.view), visible = cellSlotVisible(it.view)) }

    private fun cellSlotVisible(view: View): Boolean {
        // A slot whose restore failed is still collapsed and would read GONE; keep the plan seeing it
        // as present until the retry actually succeeds, or dual -> single would oscillate.
        if (pendingCollapseTargets.any { it === view }) return true
        val record = records.firstOrNull {
            it.kind == IosIconKind.CELL &&
                (it.outer === view || it.collapse?.target === view ||
                    it.clusterOuters.any { other -> other === view })
        }
        // Our own collapse removes the slot; it is not the ROM hiding the SIM. The plan must keep
        // seeing the slot as present or dual -> single would oscillate.
        if (record?.collapse?.target === view) return true
        when (view.visibility) {
            View.VISIBLE -> return contentArea(view) > 0
            View.GONE -> return false
        }
        // INVISIBLE may be our own OVERLAY suppression (single or dual cluster), so the record's
        // active visual is the truth rather than the stock view's current visibility.
        if (record != null && record.mechanism == IosReplaceMechanism.OVERLAY) {
            return record.visual?.visible == true
        }
        val skin = when {
            record == null -> null
            record.outer === view -> record.skin
            else -> record.clusterSkins.getOrNull(record.clusterOuters.indexOfFirst { it === view })
        }
        return skin?.oemVisible == true
    }

    private fun cellSignals(reading: CellReading): List<SimSignal> =
        reading.samples.map { SimSignal(it.slot, it.subscriptionId, it.dots) }

    private fun dualVisual(plan: IosCellPlan.Dual, role: IosCellRole, tint: Int): IosVisual =
        IosVisual(
            part = IosPart.CELL,
            visible = true,
            tint = tint,
            cellMode = IosCellMode.DUAL,
            cellRole = role,
            cellBars = if (role == IosCellRole.DATA) plan.bars else 0f,
            cellDots = if (role == IosCellRole.OTHER) plan.dots else 0f
        )

    // ----------------------------------------------------------------------------- state

    private fun batteryVisual(tint: Int): IosVisual? {
        val reading = try {
            batterySource(context)
        } catch (_: Throwable) {
            null
        } ?: return null
        lastBatteryReading = reading
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
            tint = tint,
            percentInside = settings.iosPercentInside
        )
    }

    private fun wifiVisual(tint: Int): IosVisual {
        val sample = wifiSource(context, lastWifiLevel, lastWifiOn, lastWifiActive, lastWifiValidated)
        lastWifiSample = sample
        lastWifiLevel = sample.dots
        lastWifiOn = sample.on
        lastWifiActive = sample.active
        lastWifiValidated = sample.validated
        return IosMapping.wifi(sample, tint)
    }

    private fun readCell(): CellReading? = try {
        cellSource(context).also { lastCellReading = it }
    } catch (_: Throwable) {
        null
    }

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
        }
        cachedLowThreshold = resolved
        return resolved
    }

    /**
     * Item 6: the same colour source the system applies to this very icon, per view and surface.
     *
     * Order: leaf `imageTintList`, the leaf's [PorterDuffColorFilter], the outer's
     * image/background/foreground tint lists, the tint captured at adoption, then the bar colour. A
     * zero-alpha value is "not a resolved colour" (M17) and the next source wins.
     */
    private fun tintFor(leaf: View?, outer: View?, oemTint: Int): Int {
        val live = oemTintOf(leaf, outer)
        if (Color.alpha(live) != 0) return live
        if (Color.alpha(oemTint) != 0) return oemTint
        return try {
            BarTint.fgColor(context)
        } catch (_: Throwable) {
            0xFFFFFFFF.toInt()
        }
    }

    /** The live OEM colour of [leaf]/[outer], or 0 when neither carries one. Never throws. */
    private fun oemTintOf(leaf: View?, outer: View?): Int {
        try {
            val image = leaf as? ImageView
            if (image != null) {
                val fromList = image.imageTintList?.defaultColor ?: 0
                if (Color.alpha(fromList) != 0) return fromList
                // PorterDuffColorFilter.getColor() is public on the device but hidden in the SDK
                // stubs, so read it reflectively and fail silent when the platform blocks it.
                val filter = image.colorFilter as? PorterDuffColorFilter
                val fromFilter = if (filter != null) {
                    try {
                        (filter.javaClass.getMethod("getColor").invoke(filter) as? Int) ?: 0
                    } catch (_: Throwable) {
                        0
                    }
                } else {
                    0
                }
                if (Color.alpha(fromFilter) != 0) return fromFilter
            }
            if (outer != null) {
                val fromImage = (outer as? ImageView)?.imageTintList?.defaultColor ?: 0
                if (Color.alpha(fromImage) != 0) return fromImage
                val fromBackground = outer.backgroundTintList?.defaultColor ?: 0
                if (Color.alpha(fromBackground) != 0) return fromBackground
                val fromForeground = outer.foregroundTintList?.defaultColor ?: 0
                if (Color.alpha(fromForeground) != 0) return fromForeground
            }
        } catch (_: Throwable) {
        }
        return 0
    }

    /** Recolours a visual, keeping the battery's fixed state fill (low/saver/charging) intact. */
    private fun IosVisual.withTint(tint: Int): IosVisual = copy(
        tint = tint,
        batteryFillColor = if (part == IosPart.BATTERY && batteryState == IosBatteryState.NORMAL) {
            tint
        } else {
            batteryFillColor
        }
    )

    /**
     * The user's per-glyph offset/scale at [drawnWidthPx], the glyph's 100 % draw width. Failure is
     * identity, never a missing glyph. Internal so the settings -> client -> host seam is unit-tested.
     */
    internal fun userTransformFor(kind: IosIconKind, drawnWidthPx: Float): IosUserTransform = try {
        settings.iosTransform(kind, drawnWidthPx)
    } catch (_: Throwable) {
        IosUserTransform.IDENTITY
    }

    /**
     * The glyph's 100 % width from a stock slot: the [IosGeometry.Relative.sizeFor] inner box of the
     * slot. A non-positive slot returns 0, so the offset is 0 while a scale still applies.
     */
    private fun drawnWidthFor(
        kind: IosIconKind,
        width: Int,
        height: Int,
        visual: IosVisual
    ): Float {
        if (width <= 0 || height <= 0) return 0f
        return IosGeometry.Relative.sizeFor(
            kind,
            width,
            height,
            dualCell = visual.cellMode == IosCellMode.DUAL
        ).first.toFloat()
    }

    /**
     * The glyph's 100 % width from a live record. Sources in order: the drawable's stock intrinsic
     * size, the overlay's pinned layout params, the drawing leaf, the outer. 0 means "unknown", never a
     * guess and never a layout write (C2).
     */
    private fun drawnWidthFor(record: Record, visual: IosVisual): Float {
        // R4A item 5: an overlay's layout params are the SCALED canvas box; the percent offset must be
        // computed from the unscaled slot box or it drifts on every refresh.
        if (record.mechanism == IosReplaceMechanism.OVERLAY) {
            val base = record.baseBox
            if (base != null && base[2] > 0 && base[3] > 0) {
                return drawnWidthFor(record.kind, base[2], base[3], visual)
            }
        }
        val sources = sequenceOf(
            record.drawable?.intrinsicWidth to record.drawable?.intrinsicHeight,
            record.overlay?.layoutParams?.width to record.overlay?.layoutParams?.height,
            record.leaf?.width to record.leaf?.height,
            record.outer.width to record.outer.height
        )
        for ((width, height) in sources) {
            if (width != null && height != null && width > 0 && height > 0) {
                return drawnWidthFor(record.kind, width, height, visual)
            }
        }
        return 0f
    }

    private fun applyVisual(record: Record, visual: IosVisual) {
        // R4A/R4B contract: the Wi-Fi slot state (R4B draws the label). R6: a WIFI record switches
        // between glyph and data-type label from this single source of truth.
        val current = visual.copy(wifiSlot = wifiSlotState)
        if (record.kind == IosIconKind.WIFI) {
            record.labelSlot = current.wifiSlot is IosWifiSlot.DataType
        }
        if (record.mechanism == IosReplaceMechanism.OVERLAY) {
            applyOverlayVisual(record, current)
            return
        }
        if (record.mechanism == IosReplaceMechanism.NATIVE_LABEL) {
            applyNativeLabelVisual(record, current)
            return
        }
        val transform = userTransformFor(record.kind, drawnWidthFor(record, current))
        val transformed = current.copy(userTransform = transform)
        val changed = record.visual != transformed
        record.visual = transformed
        if (transform != IosUserTransform.IDENTITY) allowGlyphOverflow(record.overflowTarget)
        when (record.mechanism) {
            IosReplaceMechanism.IMAGE_DRAWABLE -> {
                record.swap?.update(transformed)
                record.swap?.reassert()
                // A detach/reattach replaces the ViewTreeObserver; re-arm every guard.
                record.swap?.ensureListener()
                record.siblings.forEach { it.ensureListener() }
            }
            IosReplaceMechanism.OUTER_DRAW,
            IosReplaceMechanism.LEAF_DRAW -> {
                record.drawable?.update(transformed)
                if (changed) record.drawTarget?.invalidate()
            }
            IosReplaceMechanism.OVERLAY -> Unit
            IosReplaceMechanism.NATIVE_LABEL -> Unit
            IosReplaceMechanism.SKIP -> Unit
        }
    }

    /**
     * R6 item 1: a `mobile_type` label never has its own canvas. The native-draw path mutates only the
     * label snapshot inside the owning CELL session; the drawable-swap path updates the type view's
     * drawable. Only a real change invalidates the owning draw target.
     */
    private fun applyNativeLabelVisual(record: Record, current: IosVisual) {
        val changed = record.visual != current
        record.visual = current
        val label = record.labelDraw
        if (label != null) {
            label.visual = current
            if (changed) {
                val owner = records.firstOrNull {
                    it.kind == IosIconKind.CELL && isUnder(record.outer, it.outer)
                }
                owner?.drawTarget?.invalidate()
            }
            return
        }
        record.swap?.update(current)
        record.swap?.ensureListener()
    }

    /**
     * R4A item 5: render an OVERLAY inside the transformed glyph box.
     *
     * The canvas is our own view (never a stock view), so the host may resize it around the slot
     * centre: [overlayBox] grows/shrinks the box by the user's scale and shifts it by the offset. The
     * visual is then rendered with identity user transform, because the enlarged canvas already
     * provides the scale. **R4B contract:** the renderer must stop clipping its ink to the draw box for
     * a non-identity transform; this host side only guarantees the canvas is big enough.
     */
    private fun applyOverlayVisual(record: Record, visual: IosVisual) {
        val overlay = record.overlay ?: return
        val base = record.baseBox
        if (base == null) {
            // No slot box was recorded (a record from before the item-5 wiring): keep the old draw.
            val transformed = visual.copy(
                userTransform = userTransformFor(record.kind, drawnWidthFor(record, visual))
            )
            record.visual = transformed
            overlay.render(transformed)
            return
        }
        val transform = userTransformFor(record.kind, drawnWidthFor(record, visual))
        val box = overlayBox(
            base[0],
            base[1],
            base[2],
            base[3],
            transform.dxPx,
            transform.scale
        )
        val params = record.overlayParams
        if (params != null &&
            (params.width != box[2] || params.height != box[3] ||
                params.leftMargin != box[0] || params.topMargin != box[1])
        ) {
            params.width = box[2]
            params.height = box[3]
            params.leftMargin = box[0]
            params.topMargin = box[1]
            overlay.layoutParams = params
        }
        val rendered = visual.copy(userTransform = IosUserTransform(0f, 1f))
        record.visual = rendered
        overlay.render(rendered)
    }

    /**
     * R4A item 5: let a scaled glyph draw outside the stock slot's clip.
     *
     * Walks from [view] through its parents, including the strip, and stops before the window root
     * (the strip's parent), so nothing above the strip is touched. Only `clipChildren`/
     * `clipToPadding` flags change; no stock view is resized or re-laid out (C2). Idempotent, guarded.
     */
    internal fun allowGlyphOverflow(view: View?) {
        if (view == null) return
        val stop = (strip?.parent as? View) ?: rootView
        // A draw target may be a leaf ImageView (not a ViewGroup): start at its first parent then.
        var current: View? = if (view is ViewGroup) view else view.parent as? View
        while (current is ViewGroup) {
            if (current === stop) break
            try {
                current.clipChildren = false
                current.clipToPadding = false
            } catch (_: Throwable) {
            }
            current = current.parent as? View
        }
    }

    // ----------------------------------------------------------------------------- geometry

    /**
     * The leaf rect as `[left, top, right, bottom]` in [outer]'s coordinates (what
     * [IosDrawSession.bounds] expects). [out]/[offset] are caller-owned scratch arrays so a per-frame
     * draw allocates nothing.
     */
    private fun leafBounds(
        leaf: View,
        outer: View,
        out: IntArray = IntArray(4),
        offset: IntArray = IntArray(2)
    ): IntArray {
        if (leaf.visibility != View.VISIBLE || leaf.width <= 0 || leaf.height <= 0) {
            out[0] = 0
            out[1] = 0
            out[2] = 0
            out[3] = 0
            return out
        }
        if (!fillOffsetIn(leaf, outer, offset)) {
            out[0] = 0
            out[1] = 0
            out[2] = 0
            out[3] = 0
            return out
        }
        out[0] = offset[0]
        out[1] = offset[1]
        out[2] = offset[0] + leaf.width
        out[3] = offset[1] + leaf.height
        return out
    }

    /** Test seam: the leaf rectangle in [outer]'s coordinates as `[left, top, right, bottom]`. */
    internal fun leafBoundsForTest(leaf: View, outer: View): IntArray = leafBounds(leaf, outer)

    private fun overlayBounds(outer: View, leaf: View?, layer: View): IntArray {
        val target = leaf ?: outer
        val bounds = boundsIn(target, layer)
        if (bounds[2] > 0 && bounds[3] > 0) return bounds
        return boundsIn(outer, layer)
    }

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
        val out = IntArray(2)
        return if (fillOffsetIn(view, ancestor, out)) out else null
    }

    /** Fills [out] with the offset of [view] inside [ancestor]; false when it is not an ancestor. */
    private fun fillOffsetIn(view: View, ancestor: View, out: IntArray): Boolean {
        var x = 0
        var y = 0
        var current: View = view
        while (current !== ancestor) {
            val parent = current.parent as? View ?: return false
            x += current.left - parent.scrollX + current.translationX.roundToInt()
            y += current.top - parent.scrollY + current.translationY.roundToInt()
            current = parent
        }
        out[0] = x
        out[1] = y
        return true
    }

    private fun contentArea(view: View): Int {
        if (view.visibility != View.VISIBLE) return 0
        var best = if (view.width > 0 && view.height > 0) {
            view.width.toLong() * view.height
        } else {
            0L
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                best = maxOf(best, contentArea(view.getChildAt(i)).toLong())
            }
        }
        return best.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun selectionScore(icon: StockIcon): Long {
        if (icon.view.visibility != View.VISIBLE) return -1
        val area = contentArea(icon.view).toLong()
        if (area <= 0) return -1
        val plan = planForSafe(icon.view)
        val composeBonus = if (
            plan.family == IosSkinFamily.COMPOSE_BATTERY || plan.family == IosSkinFamily.COMPOSE_ICON
        ) {
            1_000_000_000L
        } else {
            0L
        }
        return composeBonus + area
    }

    private fun selectBest(inventory: List<StockIcon>, kind: StockIconKind): StockIcon? {
        var best: StockIcon? = null
        var bestScore = -1L
        for (icon in inventory) {
            if (icon.kind != kind) continue
            val score = selectionScore(icon)
            if (score > bestScore) {
                bestScore = score
                best = icon
            }
        }
        return best
    }

    /**
     * R4 item 3: the Wi-Fi stock view for the data-type label. Unlike [selectBest] this accepts an
     * INVISIBLE entry (Wi-Fi off); it still needs a real laid-out box so the overlay can align with
     * the slot. A GONE/never-laid-out view has no box, so the caller keeps the safe stock behaviour.
     *
     * The measured OPLUS composite outer (`OplusModernStatusBarWifiView`) is 0x0 with its content in
     * `wifi_group`; the box therefore comes from the same geometry child the draw path uses, not from
     * the outer's own size.
     */
    private fun selectWifiSlotStock(inventory: List<StockIcon>): StockIcon? {
        var best: StockIcon? = null
        var bestArea = -1L
        for (icon in inventory) {
            if (icon.kind != StockIconKind.WIFI) continue
            if (icon.view.visibility == View.GONE) continue
            val area = if (icon.view.width > 0 && icon.view.height > 0) {
                icon.view.width.toLong() * icon.view.height
            } else {
                wifiSlotGeometryArea(icon.view)
            }
            if (area <= 0) continue
            if (area > bestArea) {
                bestArea = area
                best = icon
            }
        }
        return best
    }

    /**
     * The measured geometry child of a 0x0 Wi-Fi composite (e.g. OPLUS `wifi_group`). GONE is
     * excluded: a collapsed slot has no trustworthy box even when Android still carries the child's
     * last measured size.
     */
    private fun wifiSlotGeometryArea(outer: View): Long {
        val plan = try {
            IosSkinPlanner.planFor(outer)
        } catch (_: Throwable) {
            return 0L
        }
        for (id in plan.geometryIds) {
            val view = findByEntryName(outer, id) ?: continue
            if (view.visibility != View.GONE && view.width > 0 && view.height > 0) {
                return view.width.toLong() * view.height
            }
        }
        return 0L
    }

    /** R5A item 1-runtime: the WIFI label visual for a data-type slot. */
    private fun labelVisualFor(slot: IosWifiSlot.DataType, tint: Int): IosVisual = IosVisual(
        part = IosPart.WIFI,
        visible = true,
        tint = tint,
        wifiSlot = slot
    )

    /**
     * The stock outer of the SIM the plan draws as the data row: [IosCellPlan.Single.slot] or
     * [IosCellPlan.Dual.dataSlot]. [cellIcons] is the same mapped list the plan was built from.
     */
    private fun dataCellOuter(plan: IosCellPlan, cellIcons: List<StockIcon>): View? = when (plan) {
        is IosCellPlan.Single -> cellIcons.getOrNull(plan.slot)?.view
        is IosCellPlan.Dual -> cellIcons.getOrNull(plan.dataSlot)?.view
        IosCellPlan.None -> null
    }

    /**
     * The data SIM's own `mobile_type` view, or null when it is absent/GONE/never laid out. Measured as
     * a real, visible-or-hidden but sized descendant of the data outer (evidence:
     * `.pm/evidence/r5/report/diagnostics.txt:154-178`, 66×51 `ImageView id=mobile_type`).
     */
    /**
     * The data SIM's `mobile_type` view, only when it is actually drawable. R6 audit FIX 2: an
     * INVISIBLE-but-sized view is not an anchor (the old code accepted it and the label appeared over
     * nothing); GONE, zero-sized, INVISIBLE and alpha=0 anchors are handled by the caller's
     * `anchor-hidden`/`absent` path.
     */
    private fun mobileTypeView(cellOuter: View?): View? {
        val outer = cellOuter ?: return null
        val type = findByEntryName(outer, "mobile_type") ?: return null
        if (type.visibility != View.VISIBLE) return null
        if (type.width <= 0 || type.height <= 0) return null
        return type
    }

    private fun isMobileType(view: View): Boolean = entryName(view) == "mobile_type"

    /**
     * R6 item 1: anchors the data-type label on the data SIM's `mobile_type`, inside the native view
     * tree, with no overlay.
     *
     * - The measured OPLUS composite already owns its draw (`OUTER_DRAW`): the label is attached to
     *   that same [IosDrawSession] and drawn in its `dispatchDraw` pass, so it inherits position,
     *   visibility, translationY and alpha (`.pm/R6-PULL-report.md` §1).
     * - Every other family replaces the `mobile_type` drawable with our Wi-Fi label drawable, so the
     *   stock type cannot draw twice and no stock view is resized (C2).
     *
     * Returns null (no fallback to an overlay) when neither is possible.
     */
    private fun adoptNativeLabel(typeView: View, visual: IosVisual, cellRecord: Record): Record? {
        val cellOuter = cellRecord.outer
        return try {
            when (cellRecord.mechanism) {
                IosReplaceMechanism.OUTER_DRAW -> {
                    val session = IosDrawingHooks.sessionFor(cellOuter) ?: return null
                    val offset = IntArray(2)
                    if (!fillOffsetIn(typeView, cellOuter, offset)) return null
                    val box = IntArray(4)
                    box[0] = offset[0]
                    box[1] = offset[1]
                    box[2] = typeView.width
                    box[3] = typeView.height
                    val record = Record(
                        kind = IosIconKind.WIFI,
                        outer = typeView,
                        leaf = null,
                        mechanism = IosReplaceMechanism.NATIVE_LABEL,
                        drawable = null,
                        swap = null,
                        drawSession = false,
                        skin = null,
                        overlay = null,
                        overlayParams = null,
                        siblings = emptyList(),
                        visual = visual,
                        labelSlot = true,
                        labelBox = box,
                        labelOffset = offset
                    )
                    val label = IosDrawLabel(
                        visual = visual,
                        // R6 audit FIX 2: the label must disappear/fade exactly when its anchor does,
                        // even before the next layout pass.
                        bounds = {
                            if (typeView.visibility == View.VISIBLE && labelAlpha(typeView) > 0f) {
                                box
                            } else {
                                HIDDEN_LABEL_BOX
                            }
                        },
                        alpha = { labelAlpha(typeView) }
                    )
                    record.labelDraw = label
                    session.label = label
                    records.add(record)
                    addLayoutListener(typeView)
                    logNativeLabelOnce()
                    record
                }
                IosReplaceMechanism.IMAGE_DRAWABLE -> {
                    val image = typeView as? ImageView ?: return null
                    if (image.width <= 0 || image.height <= 0) return null
                    val drawable = IosDrawables.create(
                        IosIconKind.WIFI,
                        visual,
                        image.width,
                        image.height,
                        relativeSize = true
                    )
                    val swap = IosDrawableSwap(
                        leaf = image,
                        drawable = drawable,
                        onReassert = { noteReassert() },
                        restoreDrawable = image.drawable
                    )
                    if (!swap.apply()) return null
                    IosPushHooks.watch(image, swap)
                    IosPushHooks.onTint(image, tintCallback)
                    val record = Record(
                        kind = IosIconKind.WIFI,
                        outer = typeView,
                        leaf = typeView,
                        mechanism = IosReplaceMechanism.NATIVE_LABEL,
                        drawable = drawable,
                        swap = swap,
                        drawSession = false,
                        skin = null,
                        overlay = null,
                        overlayParams = null,
                        siblings = emptyList(),
                        visual = visual,
                        labelSlot = true,
                        labelBaseTranslation = image.translationX
                    )
                    records.add(record)
                    addLayoutListener(typeView)
                    logNativeLabelOnce()
                    record
                }
                else -> null
            }
        } catch (t: Throwable) {
            logOnce.once("native-label-adopt") {
                L.w("iOS replace native label adopt failed: ${t.javaClass.simpleName}: ${t.message}")
            }
            null
        }
    }

    private fun logNativeLabelOnce() {
        logOnce.once("ios-label-native") {
            L.i("iOS label: native draw anchor=cell-type")
        }
    }

    /** A live label record has a real unscaled box (the native draw or drawable has something to draw). */
    private fun hasRealBox(record: Record): Boolean {
        val (width, height) = labelBoxSize(record)
        return width > 0 && height > 0
    }

    /**
     * R6 audit FIX 5: the label is drawn by the native anchor, so its reported box is the native
     * draw's `labelBox` when it has one, else the anchor leaf/view size (`mobile_type` or the stock
     * Wi-Fi leaf). No overlay/base-box fallback survives.
     */
    private fun labelBoxSize(record: Record): Pair<Int, Int> {
        val label = record.labelBox
        if (label != null && label[2] > 0 && label[3] > 0) return label[2] to label[3]
        val anchor = record.leaf ?: record.outer
        val width = if (anchor.width > 0) anchor.width else record.outer.width
        val height = if (anchor.height > 0) anchor.height else record.outer.height
        return width to height
    }

    private fun labelLine(slot: IosWifiSlot.DataType, record: Record): String {
        val (width, height) = labelBoxSize(record)
        val mobileType = isMobileType(record.outer)
        val anchor = if (mobileType) "cell-type" else "wifi-slot"
        val shift = if (mobileType) {
            " shift=${record.labelShift.roundToInt()}px " +
                "gap=${record.labelGapLeft}..${record.labelGapRight}"
        } else {
            ""
        }
        return "ios surface=$surface label state=DataType(${slot.label}) " +
            "anchor=$anchor:${width}x$height$shift"
    }

    /**
     * R5M runner, reworked for R6: slides the native label into the free cell->battery gap when the
     * stock `mobile_type` box overlaps the cell leaf.
     *
     * The label stays anchored to `mobile_type` (one WIFI record, CELL ownership unchanged). The
     * native-draw path moves the precomputed `labelBox`; the drawable-swap path writes the type view's
     * `translationX`, which only changes drawing (C2). No overlay layer is required any more.
     *
     * R6 audit FIX 2/3: returns whether the label is actually on screen and records the reason when it
     * is not, so the dump never claims `anchor=cell-type` for a hidden label. With no battery the
     * anchor is used as-is (there is no gap to shift into); [Record.labelShown] stays true.
     */
    private fun updateLabelShift(record: Record): Boolean {
        if (record.mechanism != IosReplaceMechanism.NATIVE_LABEL) return record.labelShown
        if (record.kind != IosIconKind.WIFI || !record.labelSlot) return record.labelShown
        if (!isMobileType(record.outer)) return record.labelShown
        if (record.outer.visibility != View.VISIBLE || labelAlpha(record.outer) <= 0f) {
            hideLabel(record, "anchor-hidden")
            return false
        }
        val frame = layer ?: strip
        if (frame == null) {
            hideLabel(record, "no-cell")
            return false
        }
        val cellRecord = records.firstOrNull {
            it.kind == IosIconKind.CELL && isUnder(record.outer, it.outer)
        }
        if (cellRecord == null) {
            hideLabel(record, "no-cell")
            return false
        }
        val batteryRecord = records.firstOrNull { it.kind == IosIconKind.BATTERY }
        if (batteryRecord == null) {
            // No battery means no gap to measure; the anchor itself is the position (the stock type
            // would draw exactly there), so the label is shown rather than falsely reported absent.
            setLabelShift(record, 0f)
            return true
        }
        val cellBox = boundsIn(cellRecord.leaf ?: cellRecord.outer, frame)
        val cellOuterBox = boundsIn(cellRecord.outer, frame)
        // The swap path writes the type view's translationX; measuring it now would feed the applied
        // shift back into the next calculation. Subtract our own shift to recover the base box.
        val applied = if (record.labelDraw == null) {
            record.outer.translationX - record.labelBaseTranslation
        } else {
            0f
        }
        val rawTypeBox = boundsIn(record.outer, frame)
        val typeBox = if (applied == 0f) {
            rawTypeBox
        } else {
            intArrayOf(
                rawTypeBox[0] - applied.roundToInt(),
                rawTypeBox[1],
                rawTypeBox[2],
                rawTypeBox[3]
            )
        }
        val batteryBox = boundsIn(batteryRecord.leaf ?: batteryRecord.outer, frame)
        val shift = labelShiftPx(
            typeBox = typeBox,
            cellBox = cellBox,
            cellOuterBox = cellOuterBox,
            batteryBox = batteryBox,
            canvasLeft = typeBox[0].toFloat(),
            canvasWidth = typeBox[2]
        )
        record.labelGapLeft = maxOf(cellBox[0] + cellBox[2], cellOuterBox[0] + cellOuterBox[2])
        record.labelGapRight = batteryBox[0]
        val overlaps = typeBox[0] < cellBox[0] + cellBox[2] - 1 &&
            typeBox[0] + typeBox[2] > cellBox[0] + 1
        val gapW = record.labelGapRight - record.labelGapLeft
        val needed = maxOf(LABEL_MIN_GAP_PX, typeBox[2])
        if (overlaps && gapW < needed) {
            // R6 fails closed: a label that does not fit the free gap would share a column with the
            // cell bars. Draw nothing rather than an overlapping label (never resize the stock view).
            logOnce.once("label-shift-gap-too-narrow") {
                L.w("iOS label shift: gap-too-narrow (${gapW}px)")
            }
            hideLabel(record, "gap-too-narrow")
            return false
        }
        setLabelShift(record, shift)
        return true
    }

    /** Hides the label without an overlay: zero bounds for the native draw, an invisible drawable. */
    private fun hideLabel(record: Record, reason: String) {
        record.labelShift = 0f
        record.labelShown = false
        record.labelHiddenReason = reason
        // `anchor-hidden` needs no write: the native bounds closure checks visibility/alpha every draw,
        // and an ImageView applies its own alpha/visibility, so the label returns immediately when the
        // anchor does. The other reasons need an explicit hidden drawing.
        if (reason == "anchor-hidden") return
        val labelBox = record.labelBox
        if (labelBox != null) {
            labelBox[0] = 0
            labelBox[1] = 0
            labelBox[2] = 0
            labelBox[3] = 0
            val owner = records.firstOrNull {
                it.kind == IosIconKind.CELL && isUnder(record.outer, it.outer)
            }
            owner?.drawTarget?.invalidate()
        } else {
            record.visual?.let { record.swap?.update(it.copy(visible = false)) }
        }
    }

    /** Applies one label shift to whichever native mechanism this record owns. */
    private fun setLabelShift(record: Record, shift: Float) {
        record.labelShift = shift
        record.labelShown = true
        record.labelHiddenReason = null
        val labelBox = record.labelBox
        if (labelBox != null) {
            // Native-draw path: the CELL outer's draw pass reads this array on every frame.
            val cellRecord = records.firstOrNull {
                it.kind == IosIconKind.CELL && isUnder(record.outer, it.outer)
            }
            val offset = record.labelOffset
            if (cellRecord != null && offset != null && fillOffsetIn(record.outer, cellRecord.outer, offset)) {
                labelBox[0] = offset[0] + shift.roundToInt()
                labelBox[1] = offset[1]
                labelBox[2] = record.outer.width
                labelBox[3] = record.outer.height
            }
        } else {
            // Drawable-swap path: translationX changes drawing only, never layout (C2). Restore the
            // visible snapshot in case a previous hidden layout made it invisible.
            record.visual?.let { record.swap?.update(it) }
            try {
                record.outer.translationX = record.labelBaseTranslation + shift
            } catch (_: Throwable) {
            }
        }
        if (shift != 0f) allowGlyphOverflow(record.outer)
    }

    private fun labelAbsentLine(slot: IosWifiSlot.DataType, reason: String): String =
        "ios surface=$surface label state=DataType(${slot.label}) anchor=absent reason=$reason"

    /** A NATIVE_LABEL record is live only while it still belongs to the current CELL record. */
    private fun isLiveNativeLabel(label: Record, cellRecord: Record, type: View): Boolean {
        if (label.mechanism != IosReplaceMechanism.NATIVE_LABEL || !label.labelSlot) return false
        if (label.outer !== type) return false
        if (!isUnder(label.outer, cellRecord.outer)) return false
        val draw = label.labelDraw
        if (draw != null) {
            // The CELL record may have been re-adopted (new session) since this label was created.
            if (IosDrawingHooks.sessionFor(cellRecord.outer)?.label !== draw) return false
        } else if (label.swap == null) {
            return false
        }
        return hasRealBox(label)
    }

    /**
     * R6 item 1: the single owner of the `mobile_type` label, run only after the CELL plan is adopted.
     * The label is kept while it still belongs to the current CELL record; otherwise it is dropped and
     * re-adopted natively. An overlay is never created: a failure reports `no-native-anchor`.
     *
     * The stock Wi-Fi-slot label has no CELL owner and is left to that slot's own path.
     */
    private fun reconcileWifiLabel(plan: IosCellPlan, cellIcons: List<StockIcon>, tint: Int) {
        val slot = wifiSlotState
        if (slot !is IosWifiSlot.DataType) return
        val label = records.firstOrNull {
            it.kind == IosIconKind.WIFI && it.labelSlot && isMobileType(it.outer)
        }
        val otherLabel = records.firstOrNull {
            it.kind == IosIconKind.WIFI && it.labelSlot && !isMobileType(it.outer)
        }
        // A Wi-Fi-slot label already owns the data-type slot; do not add a second one.
        if (otherLabel != null) {
            label?.let { dropRecord(it, restore = true) }
            return
        }
        val cellOuter = dataCellOuter(plan, cellIcons)
        val cellRecord = cellOuter?.let { data ->
            records.firstOrNull { it.kind == IosIconKind.CELL && it.outer === data }
        }
        if (cellRecord == null) {
            label?.let { dropRecord(it, restore = true) }
            labelReport = labelAbsentLine(slot, "no-native-anchor")
            return
        }
        val type = mobileTypeView(cellRecord.outer)
        if (type == null) {
            label?.let { dropRecord(it, restore = true) }
            labelReport = labelAbsentLine(slot, "no-mobile-type")
            return
        }
        if (label != null && isLiveNativeLabel(label, cellRecord, type)) {
            applyVisual(label, labelVisualFor(slot, tint))
            val shown = updateLabelShift(label)
            labelReport = if (shown) {
                labelLine(slot, label)
            } else {
                labelAbsentLine(slot, label.labelHiddenReason ?: "anchor-hidden")
            }
            return
        }
        label?.let { dropRecord(it, restore = true) }
        val adopted = adoptNativeLabel(type, labelVisualFor(slot, tint), cellRecord)
        if (adopted != null) {
            applyVisual(adopted, labelVisualFor(slot, tint))
            val shown = updateLabelShift(adopted)
            labelReport = if (shown) {
                labelLine(slot, adopted)
            } else {
                labelAbsentLine(slot, adopted.labelHiddenReason ?: "anchor-hidden")
            }
        } else {
            labelReport = labelAbsentLine(slot, "no-native-anchor")
        }
    }

    /**
     * Keeps the stock Wi-Fi view suppressed on a reused overlay record. The label path may have adopted
     * over an already-hidden slot without a skin; if the system later shows the stock view, this
     * suppresses it again before our glyph/label is drawn.
     *
     * A label anchored to the data SIM's `mobile_type` is the exception: its anchor has no measured
     * skin plan and is already blanked by the CELL record's sibling swap, so a stock write here would
     * touch an unrelated inner view (and can never succeed). The stock Wi-Fi-slot label keeps the call.
     */
    private fun ensureWifiSuppression(record: Record) {
        // R6: the data-type label is never an OVERLAY, so only a real overlay icon needs its skin.
        if (record.mechanism != IosReplaceMechanism.OVERLAY) return
        try {
            record.skin?.suppress(record.outer)
        } catch (_: Throwable) {
        }
    }

    /** The overlay parent is resolved lazily: only a record that really needs an overlay asks for one. */
    private fun resolveOverlayParent(): FrameLayout? {
        overlayParent?.let { return it }
        val target = strip ?: return null
        val root = rootView ?: target.rootView ?: target
        val parent = try {
            finder.findOverlayContainer(target, root) as? FrameLayout
        } catch (_: Throwable) {
            null
        } ?: return null
        overlayParent = parent
        return parent
    }

    private fun ensureLayer(parent: FrameLayout): FrameLayout? {
        val existing = layer
        if (existing != null && existing.parent === parent) return existing
        if (existing != null) {
            try {
                (existing.parent as? ViewGroup)?.removeView(existing)
            } catch (_: Throwable) {
            }
            layer = null
        }
        val created = FrameLayout(context).apply {
            background = null
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val added = try {
            parent.addView(
                created,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            true
        } catch (_: Throwable) {
            false
        }
        if (!added) return null
        layer = created
        overlayParent = parent
        return created
    }

    // ----------------------------------------------------------------------------- layout

    private fun onLayoutChanged() {
        if (updating || !isAttached || composed != null) return
        updating = true
        try {
            for (record in records.toList()) {
                try {
                    // A detach/reattach replaces the ViewTreeObserver; re-arm the guards.
                    record.swap?.ensureListener()
                    record.siblings.forEach { it.ensureListener() }
                    if (record.mechanism == IosReplaceMechanism.OVERLAY) {
                        val layer = layer ?: continue
                        // Item 2: a dual BOTH block is anchored to the DATA slot, never the union.
                        val bounds = if (record.kind == IosIconKind.CELL &&
                            record.cellRole == IosCellRole.BOTH
                        ) {
                            overlayBounds(record.outer, record.leaf, layer)
                        } else if (record.clusterOuters.isEmpty()) {
                            overlayBounds(record.outer, record.leaf, layer)
                        } else {
                            unionBoundsIn(listOf(record.outer) + record.clusterOuters, layer)
                        }
                        if (bounds[2] > 0 && bounds[3] > 0) {
                            placeOverlay(record, bounds)
                        }
                    }
                    if (record.mechanism == IosReplaceMechanism.NATIVE_LABEL) {
                        val shown = updateLabelShift(record)
                        val slotNow = record.visual?.wifiSlot
                        if (slotNow is IosWifiSlot.DataType) {
                            labelReport = if (shown) {
                                labelLine(slotNow, record)
                            } else {
                                labelAbsentLine(slotNow, record.labelHiddenReason ?: "anchor-hidden")
                            }
                        }
                    }
                    if (record.mechanism == IosReplaceMechanism.LEAF_DRAW) shiftLeafIfNeeded(record)
                } catch (_: Throwable) {
                }
            }
        } finally {
            updating = false
        }
        if (!isAttached) return
        // R6 item 5: the inventory DFS is only useful while an icon is deferred or as an occasional
        // safety net; the per-record loop above still repositions every layout.
        if (!IosLayoutScan.shouldScan(deferred.size, layoutsSinceScan)) {
            layoutsSinceScan++
            return
        }
        layoutsSinceScan = 0
        try {
            val inventory = inventoryOf(strip ?: return, null)
            var pending = false
            for (icon in inventory) {
                if (records.any { it.outer === icon.view || it.leaf === icon.view }) continue
                val area = contentArea(icon.view)
                if (area <= 0) continue
                val previous = deferredArea[icon.view]
                if (!deferred.contains(icon.view) || previous == null || previous != area) {
                    deferred.remove(icon.view)
                    deferredArea.remove(icon.view)
                    pending = true
                }
            }
            if (pending) scheduleRefresh()
        } catch (_: Throwable) {
        }
    }

    /**
     * Refreshes an overlay's unscaled slot box from fresh bounds and re-applies the current transform.
     *
     * The base size still honours [IosSizeOwner] on extra surfaces (M15), so a lock-screen/shade
     * overlay cannot grow to its own slot width. Only our canvas params move; the stock outer is
     * never written (C2).
     */
    private fun placeOverlay(record: Record, bounds: IntArray) {
        if (record.overlay == null) return
        val size = overlayBaseSize(record, bounds)
        val base = record.baseBox
        if (base == null) {
            record.baseBox = intArrayOf(bounds[0], bounds[1], size.first, size.second)
        } else {
            base[0] = bounds[0]
            base[1] = bounds[1]
            base[2] = size.first
            base[3] = size.second
        }
        val current = record.visual ?: return
        applyVisual(record, current)
    }

    /** The unscaled overlay box size from fresh bounds, preserving the M15 size owner. */
    private fun overlayBaseSize(record: Record, bounds: IntArray): Pair<Int, Int> =
        if (isMainBar) {
            bounds[2] to bounds[3]
        } else if (record.kind == IosIconKind.CELL && record.cellRole == IosCellRole.BOTH) {
            sizeOwner.sizeForBlock(bounds[2], bounds[3])
        } else {
            sizeOwner.sizeFor(record.kind, bounds[2], bounds[3])
        }

    /** The OEM flips the two HyperOS battery leaves by display style: follow the visible one. */
    private fun shiftLeafIfNeeded(record: Record) {
        val leaf = record.leaf ?: return
        if (leaf.visibility == View.VISIBLE && leaf.width > 0 && leaf.height > 0) return
        val plan = planForSafe(record.outer)
        if (plan.mechanism != IosReplaceMechanism.LEAF_DRAW) return
        val visible = findVisibleSizedLeaf(record.outer, plan.leafIds) ?: return
        if (visible === leaf) return
        dropRecord(record, restore = true)
        scheduleRefresh()
    }

    // ----------------------------------------------------------------------------- restore

    private fun dropRecord(record: Record, restore: Boolean) {
        if (!records.remove(record)) return
        if (record.mechanism == IosReplaceMechanism.NATIVE_LABEL) {
            dropNativeLabel(record, restore)
            return
        }
        try {
            IosDrawingHooks.unregister(record.outer)
        } catch (_: Throwable) {
        }
        record.leaf?.let {
            try {
                IosDrawingHooks.unregister(it)
            } catch (_: Throwable) {
            }
        }
        if (restore) {
            try {
                record.swap?.restore()
            } catch (_: Throwable) {
            }
            try {
                record.skin?.restore()
            } catch (_: Throwable) {
            }
            for (skin in record.clusterSkins) {
                try {
                    skin.restore()
                } catch (_: Throwable) {
                }
            }
            for (sibling in record.siblings) {
                try {
                    sibling.restore()
                } catch (_: Throwable) {
                }
            }
            try {
                record.collapse?.restore()
            } catch (_: Throwable) {
            }
            record.collapse?.let { collapse ->
                if (collapse.isApplied && pendingCollapses.none { it === collapse }) {
                    pendingCollapses.add(collapse)
                    pendingCollapseTargets.add(collapse.target)
                    logOnce.once("collapse-restore-deferred") {
                        L.w(
                            "iOS collapse restore deferred: " +
                                "${collapse.target.javaClass.simpleName}"
                        )
                    }
                }
            }
        }
        // Item 0d/6: every view this record hooked stops routing pushes/tints here.
        (record.leaf as? ImageView)?.let { IosPushHooks.unwatch(it) }
        record.leaf?.let { IosPushHooks.offTint(it, tintCallback) }
        IosPushHooks.offTint(record.outer, tintCallback)
        record.clusterOuters.forEach { IosPushHooks.offTint(it, tintCallback) }
        record.collapse?.let { IosPushHooks.offTint(it.target, tintCallback) }
        record.tintViews.forEach { IosPushHooks.offTint(it, tintCallback) }
        val overlay = record.overlay
        if (overlay != null) {
            try {
                overlay.teardown()
            } catch (_: Throwable) {
            }
            try {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
            } catch (_: Throwable) {
            }
        }
        removeLayoutListener(record.outer)
        record.leaf?.let { removeLayoutListener(it) }
        record.clusterOuters.forEach { removeLayoutListener(it) }
        record.collapse?.let { removeLayoutListener(it.target) }
        IosDrawHookInstaller.releaseWhenEmpty()
    }

    /**
     * R6 item 1: drops a native label without touching the owning CELL session (its glyph still
     * draws) and without restoring a stock drawable the label never owned on the session path.
     */
    private fun dropNativeLabel(record: Record, restore: Boolean) {
        val label = record.labelDraw
        if (label != null) {
            val owner = records.firstOrNull {
                it.kind == IosIconKind.CELL && isUnder(record.outer, it.outer)
            }
            val session = owner?.let { IosDrawingHooks.sessionFor(it.outer) }
            if (session?.label === label) {
                session.label = null
                owner?.drawTarget?.invalidate()
            }
        }
        // The swap path owns the type drawable; the session path never wrote it.
        if (restore) {
            try {
                record.swap?.restore()
            } catch (_: Throwable) {
            }
        }
        try {
            record.outer.translationX = record.labelBaseTranslation
        } catch (_: Throwable) {
        }
        (record.leaf as? ImageView)?.let { IosPushHooks.unwatch(it) }
        record.leaf?.let { IosPushHooks.offTint(it, tintCallback) }
        IosPushHooks.offTint(record.outer, tintCallback)
        removeLayoutListener(record.outer)
        record.leaf?.let { removeLayoutListener(it) }
        IosDrawHookInstaller.releaseWhenEmpty()
    }

    /** Retries collapsed slots whose restore failed; each success releases its pending visibility. */
    private fun retryPendingCollapses() {
        if (pendingCollapses.isEmpty()) return
        val iterator = pendingCollapses.iterator()
        while (iterator.hasNext()) {
            val collapse = iterator.next()
            try {
                collapse.restore()
            } catch (_: Throwable) {
            }
            if (!collapse.isApplied) {
                pendingCollapseTargets.remove(collapse.target)
                iterator.remove()
            }
        }
    }

    private fun teardownInternal(countLifecycle: Boolean, resetAttempts: Boolean = false) {
        val wasAttached = isAttached || layer != null || records.isNotEmpty()
        val stripRootView = strip?.rootView
        // R5A item 3: only a counted attach can be torn down. A failed attach sets isAttached before
        // its empty-record teardown, so `wasAttached` alone would count a teardown that never existed.
        if (countedAttach) {
            power.onTeardown()
            countedAttach = false
        }
        compositionCleanup()
        IosDiagnostics.unregister(this)
        // Always idempotent: a failed attach may have called this already and released.
        IosSurfaceOwners.release(surface, this)
        attachRetry?.let { handler.removeCallbacks(it) }
        attachRetry = null
        // R3 A1: a failed attach must not reset the budget, or the retry loop can never be bounded.
        // Only the public teardown (a real lifecycle change) starts a fresh attach budget.
        if (resetAttempts) attachAttempts = 0
        handler.removeCallbacks(refreshRunnable)
        handler.removeCallbacks(tintRefreshRunnable)
        isAttached = false
        IosBroadcastHub.unsubscribe(this)
        for (view in layoutHosts.toList()) {
            try {
                view.removeOnLayoutChangeListener(layoutListener)
            } catch (_: Throwable) {
            }
        }
        layoutHosts.clear()
        // Item 8-runtime: put the user-hidden stock views back before dropping records, so a record
        // that restores a collapsed slot cannot leave a selected icon visible.
        try {
            hider.restore()
        } catch (_: Throwable) {
        }
        for (record in records.toList()) dropRecord(record, restore = true)
        retryPendingCollapses()
        records.clear()
        deferred.clear()
        deferredArea.clear()
        val currentLayer = layer
        layer = null
        try {
            (currentLayer?.parent as? ViewGroup)?.removeView(currentLayer)
        } catch (_: Throwable) {
        }
        strip = null
        rootView = null
        overlayParent = null
        lastSurfaceRetargetAt = null
        lastCellPlan = IosCellPlan.None
        lastCellSignature = ""
        layoutsSinceScan = 0
        cachedLowThreshold = null
        lastWifiLevel = 0
        lastWifiOn = false
        lastWifiActive = false
        lastWifiValidated = false
        lastWifiSample = null
        lastCellReading = null
        lastBatteryReading = null
        wifiSlotState = IosWifiSlot.None
        labelReport = ""
        IosDrawHookInstaller.releaseWhenEmpty()
        if (wasAttached) L.i("iOS replace teardown: stock icons restored")
        if (countLifecycle && wasAttached) noteLifecycle(stripRootView, attach = false)
    }

    /** Drops the all-overlay delegate's state, restoring its stock icons too. */
    private fun compositionCleanup() {
        val delegate = composed ?: return
        composed = null
        try {
            delegate.teardown()
        } catch (_: Throwable) {
        }
    }

    // ----------------------------------------------------------------------------- backoff

    private fun thrashBlocked(root: View): Boolean {
        if (!thrashTripped) return false
        return IosSurfaceThrash.shouldBlock(surface, root, SystemClock.uptimeMillis())
    }

    private fun resetThrash() {
        IosSurfaceThrash.reset(surface)
    }

    private fun noteLifecycle(root: View?, attach: Boolean) {
        if (thrashTripInProgress || thrashTripped) return
        val now = SystemClock.uptimeMillis()
        // A normal shade open/close is spaced by seconds; only rapid churn reaches the guard.
        if (!IosSurfaceThrash.lifecycleEvent(surface, attach, now)) return
        val guard = IosSurfaceThrash.lifecycle(surface)
        if (guard.record(now)) {
            IosSurfaceThrash.trip(surface, root?.rootView)
            L.w(
                "iOS replace anti-thrash: ${guard.describe()} attach/teardown - " +
                    "restoring stock icons and backing off until a real change"
            )
            thrashTripInProgress = true
            try {
                teardownInternal(countLifecycle = false)
            } finally {
                thrashTripInProgress = false
            }
        }
    }

    private fun noteReassert() {
        if (thrashTripInProgress || thrashTripped) return
        if (IosSurfaceThrash.reassert(surface).record(SystemClock.uptimeMillis())) {
            // The callback runs inside a pre-draw; never tear a view down from there.
            handler.post { tripForReassert() }
        }
    }

    private fun tripForReassert() {
        if (thrashTripped) return
        IosSurfaceThrash.trip(surface, rootView?.rootView)
        L.w(
            "iOS replace anti-thrash: ${IosSurfaceThrash.reassert(surface).describe()} drawable re-asserts - " +
                "restoring stock icons and backing off until a real change"
        )
        thrashTripInProgress = true
        try {
            teardownInternal(countLifecycle = false)
        } finally {
            thrashTripInProgress = false
        }
    }

    // ----------------------------------------------------------------------------- helpers

    /**
     * Item 8-runtime: hide the stock icons the user selected, plus (with [ModuleSettings.hideOtherIcons])
     * every non-replaced icon. The replacement candidates the user did *not* select are preserved, so a
     * selected battery/Wi-Fi/cell icon is hidden and never adopted, while the others can still attach.
     */
    private fun applyHiding(targetStrip: ViewGroup) {
        try {
            val selected = hiddenStockKeys
            val inventory = inventoryOf(targetStrip, null)
            val preserve = inventory.filter { it.key !in selected }.map { it.view }
            hider.hideForIos(targetStrip, preserve, selected, settings.hideOtherIcons)
        } catch (t: Throwable) {
            logOnce.once("hide-ios-fail") {
                L.w("iOS hide list failed: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /** Drops a record whose stock view the user has just selected hidden (never fights the hider). */
    private fun dropUserHiddenRecords() {
        val target = strip ?: return
        val hidden = try {
            inventoryOf(target, null)
                .filter { it.key in hiddenStockKeys }
                .map { it.view }
        } catch (_: Throwable) {
            emptyList()
        }
        if (hidden.isEmpty()) return
        for (record in records.toList()) {
            val hiddenView = hidden.any {
                it === record.outer || it === record.leaf ||
                    it === record.collapse?.target || record.clusterOuters.any { other -> other === it }
            }
            if (hiddenView) dropRecord(record, restore = true)
        }
    }

    private fun failAttach(t: Throwable): Boolean {
        logOnce.once("attach-fail") {
            L.w("iOS replace attach failed: ${t.javaClass.simpleName}: ${t.message}")
        }
        try {
            teardownInternal(countLifecycle = false)
        } catch (_: Throwable) {
        }
        return false
    }

    private fun failRefresh(t: Throwable) {
        try {
            for (record in records.toList()) dropRecord(record, restore = true)
        } catch (_: Throwable) {
        }
        lastCellSignature = ""
        logOnce.once("refresh") {
            L.w("iOS replace refresh: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** A non-fatal adoption failure after some view/hook writes; the caller already restored them. */
    private fun failAdopt(kind: IosIconKind, t: Throwable) {
        logOnce.once("adopt-partial:$kind") {
            L.w("iOS replace adopt $kind: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun recordOf(kind: IosIconKind): Record? =
        records.firstOrNull { it.kind == kind && it.mechanism != IosReplaceMechanism.SKIP }

    private fun planForSafe(view: View): IosReplacePlan = try {
        IosReplacePlanner.planFor(view)
    } catch (_: Throwable) {
        IosReplacePlan(IosSkinFamily.UNKNOWN, IosReplaceMechanism.SKIP)
    }

    private fun isTrueReplacement(plan: IosReplacePlan): Boolean =
        plan.mechanism == IosReplaceMechanism.IMAGE_DRAWABLE ||
            plan.mechanism == IosReplaceMechanism.OUTER_DRAW ||
            plan.mechanism == IosReplaceMechanism.LEAF_DRAW

    private fun findLeaf(outer: View, ids: List<String>, allowFirstImageView: Boolean): View? {
        for (id in ids) findByEntryName(outer, id)?.let { return it }
        return if (allowFirstImageView) firstImageView(outer) else null
    }

    private fun findVisibleSizedLeaf(outer: View, ids: List<String>): View? {
        for (id in ids) {
            val found = findByEntryName(outer, id) ?: continue
            if (found.visibility == View.VISIBLE && found.width > 0 && found.height > 0) return found
        }
        return null
    }

    private fun firstImageView(outer: View): View? {
        if (outer is ImageView) return outer
        if (outer !is ViewGroup) return null
        for (i in 0 until outer.childCount) {
            firstImageView(outer.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun applySiblings(
        outer: View,
        leaf: View,
        ids: List<String>,
        kind: IosIconKind,
        width: Int,
        height: Int
    ): List<IosSiblingSwap> {
        if (ids.isEmpty()) return emptyList()
        val out = ArrayList<IosSiblingSwap>()
        for (id in ids) {
            val sibling = findByEntryName(outer, id) ?: continue
            if (sibling === leaf) continue
            // Never hide an ancestor of the main leaf (Samsung `wifi_iconset` contains `wifi_signal`).
            if (isUnder(leaf, sibling)) continue
            val name = entryName(sibling) ?: id
            // R6 item 1: `mobile_type` is the label's own native anchor, never a sibling to blank.
            // The native label path either draws inside the CELL pass or replaces this very drawable;
            // blanking it here would hide the label (or the stock type without a label).
            if (name == "mobile_type") continue
            if (sibling.visibility != View.VISIBLE) continue
            if (isForbiddenSibling(name)) continue
            val empty = if (sibling is ImageView) {
                IosDrawables.create(kind, null, width, height)
            } else {
                null
            }
            val swap = IosSiblingSwap(sibling, empty)
            if (swap.apply()) out.add(swap)
        }
        return out
    }

    private fun isForbiddenSibling(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith("battery_percentage_") ||
            lower == "battery_digit" ||
            lower == "status_bar_dot"
    }

    private fun subIdOf(view: View): Int = try {
        (view.javaClass.getMethod("getSubId").invoke(view) as? Int) ?: 0
    } catch (_: Throwable) {
        0
    }

    /**
     * R6 item 5: the measured OPLUS translation field is resolved once when the session is created; the
     * per-frame closure only calls `field.getFloat`, never a reflective lookup. A missing/foreign field
     * yields no provider, which reads as 0f (no translation), exactly as before.
     */
    private fun translateXProvider(outer: View, field: String?): (() -> Float)? {
        if (field.isNullOrBlank()) return null
        val reader = IosFloatField.reader(outer.javaClass, field) ?: return null
        return { reader.read(outer) }
    }

    private fun entryName(view: View): String? = try {
        if (view.id == View.NO_ID) null else view.resources.getResourceEntryName(view.id)
    } catch (_: Throwable) {
        null
    }

    private fun findByEntryName(view: View, name: String): View? {
        if (entryName(view) == name) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findByEntryName(view.getChildAt(i), name)?.let { return it }
            }
        }
        return null
    }

    /**
     * All copies of [name] under [root], in depth-first order. The shade window can hold the same id
     * more than once (R3 A1: an invisible header copy and the visible QS strip).
     */
    private fun findAllByEntryName(view: View, name: String, out: MutableList<View>) {
        if (entryName(view) == name) out.add(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findAllByEntryName(view.getChildAt(i), name, out)
            }
        }
    }

    /** The shown, non-empty copy of [name] under [root], or null when every copy is invisible/0×0. */
    private fun findUsableStrip(root: View, name: String): ViewGroup? {
        val copies = ArrayList<View>()
        findAllByEntryName(root, name, copies)
        val usable = IosSurfaceRouting.firstUsable(
            copies,
            shown = { it.isShown },
            area = { contentArea(it) }
        )
        return usable as? ViewGroup
    }

    /** True when [view] is a strip this host may draw into right now. */
    private fun stripUsable(view: ViewGroup?): Boolean =
        view != null && view.isShown && contentArea(view) > 0

    /** Test seam: the usable copy selection, without attaching. */
    internal fun findUsableStripForTest(root: View, name: String): ViewGroup? =
        findUsableStrip(root, name)

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

    private fun isUnder(view: View, ancestor: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    private fun layerAttached(): Boolean {
        if (records.none { it.mechanism == IosReplaceMechanism.OVERLAY }) return true
        return layer?.parent != null
    }

    private fun scheduleRefresh() {
        if (!isAttached || thrashTripped) return
        handler.removeCallbacks(refreshRunnable)
        val now = SystemClock.uptimeMillis()
        val delay = nextRefreshDelayMs(lastRefreshAt, now, REFRESH_DEBOUNCE_MS, MIN_REFRESH_SPACING_MS)
        // A request the minimum spacing pushes past the debounce is coalesced with the pending one.
        if (delay > REFRESH_DEBOUNCE_MS) power.onSuppressedRefresh()
        handler.postDelayed(refreshRunnable, delay)
    }
}
