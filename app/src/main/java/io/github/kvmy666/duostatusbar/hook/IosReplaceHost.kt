package io.github.kvmy666.duostatusbar.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import de.robv.android.xposed.XposedHelpers
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.rom.RomDetection
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.roundToInt

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
    private val cellSource: (Context) -> CellReading = { CellReader.read(it, airplane = false) },
    private val wifiSource: (Context, Int, Boolean, Boolean, Boolean) -> WifiSample =
        { ctx, level, on, active, validated ->
            WifiReader.sample(ctx, level, on, active, validated)
        },
    private val survivalDelayMs: Long = 5_000L,
    private val idleDelayMs: Long = 3_000L
) {

    private companion object {
        const val REFRESH_DEBOUNCE_MS = 120L
        const val ATTACH_RETRY_MS = 400L
        const val ATTACH_MAX_ATTEMPTS = 5
        const val LOW_THRESHOLD_RESOURCE = "config_lowBatteryWarningLevel"
        const val ACTION_SERVICE_STATE = "android.intent.action.SERVICE_STATE"
        const val ACTION_ANY_DATA_STATE = "android.intent.action.ANY_DATA_STATE"
        const val ACTION_WIFI_RSSI = "android.net.wifi.RSSI_CHANGED"
        const val ACTION_WIFI_STATE = "android.net.wifi.WIFI_STATE_CHANGED"
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
        val tintViews: List<View> = emptyList()
    ) {
        val drawTarget: View?
            get() = when (mechanism) {
                IosReplaceMechanism.OUTER_DRAW -> outer
                IosReplaceMechanism.LEAF_DRAW -> leaf
                else -> null
            }
    }

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

    private var receiverRegistered = false
    private var updating = false
    private var attachRetry: Runnable? = null
    private var attachAttempts = 0

    private var lastCellPlan: IosCellPlan = IosCellPlan.None
    private var lastCellSignature = ""

    private var cachedLowThreshold: IosMapping.LowThreshold? = null
    private var lastWifiLevel = 0
    private var lastWifiOn = false
    private var lastWifiActive = false
    private var lastWifiValidated = false

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

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED,
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED,
                    ConnectivityManager.CONNECTIVITY_ACTION,
                    ACTION_SERVICE_STATE,
                    ACTION_ANY_DATA_STATE,
                    ACTION_WIFI_RSSI,
                    ACTION_WIFI_STATE -> scheduleRefresh()

                    Intent.ACTION_SCREEN_OFF -> handler.removeCallbacks(refreshRunnable)

                    Intent.ACTION_SCREEN_ON -> if (isAttached) scheduleRefresh()

                    else -> Unit
                }
            } catch (t: Throwable) {
                logOnce.once("receiver") {
                    L.w("iOS replace receiver: ${t.javaClass.simpleName}: ${t.message}")
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
        if (isAttached && attachedAlive(root)) return true
        if (isAttached) teardown()
        if (thrashTripped) return false
        val result = try {
            val found = findByEntryName(root, stripId) as? ViewGroup
            if (found == null) {
                logOnce.once("no-strip:$name:$stripId") {
                    L.i("iOS replace $name: $stripId not found - stock icons untouched")
                }
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
        if (isAttached && attachedAlive(cluster)) return true
        if (isAttached) teardown()
        val result = try {
            val root = cluster.rootView ?: cluster
            attachToStrip(surface, cluster, root, null, battery)
        } catch (t: Throwable) {
            failAttach(t)
        }
        return finishAttach(result) { attachCluster(surface, cluster, battery, source) }
    }

    /** True while this attachment still belongs to [root]'s window. Read-only. */
    internal fun attachedAlive(root: View): Boolean {
        composed?.let { return it.attachedAlive(root) }
        return IosAttachDecision.alive(
            isAttached = isAttached,
            stripAttached = strip?.isAttachedToWindow == true,
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
     * True when [stripId] resolves under [root] and the strip has a replaceable plan or an overlay
     * parent. Read-only; any failure is "not attachable" (rule 5).
     */
    internal fun canAttachSurface(root: View, stripId: String): Boolean {
        composed?.let { return it.canAttachSurface(root, stripId) }
        return try {
            val found = findByEntryName(root, stripId) as? ViewGroup ?: return false
            val inventory = inventoryOf(found, null)
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
        if (changed) resetThrash()
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
        try {
            // A failed collapse restore is retried before the plan reads the slot visibility again.
            retryPendingCollapses()
            for (record in records.toList()) {
                if (record.outer.parent == null) dropRecord(record, restore = true)
            }
            val inventory = inventoryOf(strip ?: return, null)
            val batteryIcon = selectBest(inventory, StockIconKind.BATTERY)
            val wifiIcon = selectBest(inventory, StockIconKind.WIFI)
            val cellIcons = inventory.filter { it.kind == StockIconKind.CELLULAR }

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

            // Wi-Fi: one record. Off/unvalidated restores the stock drawing (M6), never a blank slot.
            if (wifiRecord != null) {
                if (wifi == null || !wifi.visible) {
                    dropRecord(wifiRecord, restore = true)
                } else {
                    applyVisual(wifiRecord, wifi)
                }
            } else if (wifiIcon != null && wifi != null && wifi.visible) {
                adoptOrDefer(IosIconKind.WIFI, wifiIcon, wifi)
            }

            // Cellular: the plan signature decides whether records are rebuilt (1->2->1).
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
                    IosMapping.simDisplay(settings.simDisplay)
                )
            }
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
        } catch (t: Throwable) {
            failRefresh(t)
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
        teardownInternal(countLifecycle = true)
    }

    // ----------------------------------------------------------------------------- attach machinery

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
            return false
        }
        logCellSubIds(inventory)

        // All-overlay strips use the approved hide-in-place + Canvas fallback. No Rive is created:
        // IosIconHost only builds the Canvas element (IOS-R2 0c).
        val plans = inventory.map { planForSafe(it.view) }
        val allOverlayOrSkip = plans.all {
            it.mechanism == IosReplaceMechanism.OVERLAY || it.mechanism == IosReplaceMechanism.SKIP
        }
        if (allOverlayOrSkip && plans.any { it.mechanism == IosReplaceMechanism.OVERLAY }) {
            val parent = overlayParent
                ?: (finder.findOverlayContainer(targetStrip, root) as? FrameLayout)
                ?: return false
            val delegate = IosIconHost(
                context = context,
                sizeOwner = sizeOwner,
                isMainBar = isMainBar,
                surface = "$surface overlay",
                batterySource = batterySource,
                cellSource = cellSource,
                wifiSource = wifiSource
            )
            if (!delegate.attachIn(targetStrip, parent)) return false
            composed = delegate
            strip = targetStrip
            rootView = root
            this.overlayParent = parent
            isAttached = true
            L.i("iOS owner $surface: overlay host attached")
            noteLifecycle(root, attach = true)
            if (thrashTripped) {
                teardownInternal(countLifecycle = false)
                return false
            }
            return true
        }

        strip = targetStrip
        rootView = root
        this.overlayParent = overlayParent
        addLayoutListener(targetStrip)
        for (icon in inventory) addLayoutListener(icon.view)
        registerReceivers()
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
                    teardownInternal(countLifecycle = false)
                    return false
                }
                return true
            }
        }
        if (records.isEmpty() || thrashTripped) {
            teardownInternal(countLifecycle = false)
            return false
        }
        L.i("iOS owner $surface: replace host attached")
        noteLifecycle(root, attach = true)
        if (thrashTripped) {
            teardownInternal(countLifecycle = false)
            return false
        }
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
        val entries = cells.mapIndexed { index, icon ->
            val entry = entryName(icon.view) ?: icon.label
            "$entry#${index + 1}=${subIdOf(icon.view)}"
        }
        val subIds = cells.map { subIdOf(it.view) }.joinToString(",", "[", "]")
        L.i("iOS replace subIds: ${entries.joinToString(" ")} subIds=$subIds")
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
     * Only the AOSP/Samsung `BatteryMeterView` family may fall back to its first ImageView
     * (`mBatteryIconView` has no id). A named HyperOS/Oplus/leaf id that is absent is not a reason to
     * grab an arbitrary sibling (`mobile_type` on rodin/newer layouts).
     */
    private fun isAospBattery(plan: IosReplacePlan): Boolean =
        plan.family == IosSkinFamily.SAMSUNG_BATTERY

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
        return record
    }

    private fun adoptOverlay(kind: IosIconKind, outer: View, leaf: View?, visual: IosVisual): Record? {
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
        if (!skin.suppress(outer)) return null
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
            val drawnWidth = drawnWidthFor(kind, size.first, size.second, visual)
            val transformed = tinted.copy(userTransform = userTransformFor(kind, drawnWidth))
            canvas.render(transformed)
            for (view in tintViews) IosPushHooks.onTint(view, tintCallback)
            val params = FrameLayout.LayoutParams(size.first, size.second).apply {
                leftMargin = bounds[0]
                topMargin = bounds[1]
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
                visual = transformed,
                oemTint = oemTint,
                tintViews = tintViews
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
        val drawnWidth = drawnWidthFor(IosIconKind.CELL, size.first, size.second, baseVisual)
        val visual = baseVisual.copy(userTransform = userTransformFor(IosIconKind.CELL, drawnWidth))

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
            val params = FrameLayout.LayoutParams(size.first, size.second).apply {
                leftMargin = bounds[0]
                topMargin = bounds[1]
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
                clusterOuters = if (secondSkin != null) listOf(otherIcon.view) else emptyList()
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
        val sample = wifiSource(context, lastWifiLevel, lastWifiOn, lastWifiActive, lastWifiValidated)
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
        val transformed = visual.copy(
            userTransform = userTransformFor(record.kind, drawnWidthFor(record, visual))
        )
        val changed = record.visual != transformed
        record.visual = transformed
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
            IosReplaceMechanism.OVERLAY -> record.overlay?.render(transformed)
            IosReplaceMechanism.SKIP -> Unit
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
                        if (bounds[2] > 0 && bounds[3] > 0) placeOverlay(record, bounds)
                    }
                    if (record.mechanism == IosReplaceMechanism.LEAF_DRAW) shiftLeafIfNeeded(record)
                } catch (_: Throwable) {
                }
            }
        } finally {
            updating = false
        }
        if (!isAttached) return
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

    /** Places an overlay without touching its pinned size (M15); only margins move. */
    private fun placeOverlay(record: Record, bounds: IntArray) {
        val overlay = record.overlay ?: return
        val params = record.overlayParams ?: return
        if (params.leftMargin == bounds[0] && params.topMargin == bounds[1]) return
        params.leftMargin = bounds[0]
        params.topMargin = bounds[1]
        overlay.layoutParams = params
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

    private fun teardownInternal(countLifecycle: Boolean) {
        val wasAttached = isAttached || layer != null || records.isNotEmpty()
        val stripRootView = strip?.rootView
        compositionCleanup()
        // Always idempotent: a failed attach may have called this already and released.
        IosSurfaceOwners.release(surface, this)
        attachRetry?.let { handler.removeCallbacks(it) }
        attachRetry = null
        attachAttempts = 0
        handler.removeCallbacks(refreshRunnable)
        handler.removeCallbacks(tintRefreshRunnable)
        isAttached = false
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
        lastCellPlan = IosCellPlan.None
        lastCellSignature = ""
        cachedLowThreshold = null
        lastWifiLevel = 0
        lastWifiOn = false
        lastWifiActive = false
        lastWifiValidated = false
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
            if (sibling.visibility != View.VISIBLE) continue
            val name = entryName(sibling) ?: id
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

    private fun translateXProvider(outer: View, field: String?): (() -> Float)? {
        if (field.isNullOrBlank()) return null
        return {
            try {
                XposedHelpers.getFloatField(outer, field)
            } catch (_: Throwable) {
                0f
            }
        }
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

    private fun registerReceivers() {
        if (receiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                addAction(ACTION_SERVICE_STATE)
                addAction(ACTION_ANY_DATA_STATE)
                addAction(ACTION_WIFI_RSSI)
                addAction(ACTION_WIFI_STATE)
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
                L.w("iOS replace receiver not registered: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    private fun scheduleRefresh() {
        if (!isAttached || thrashTripped) return
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, REFRESH_DEBOUNCE_MS)
    }
}
