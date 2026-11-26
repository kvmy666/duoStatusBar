package io.github.kvmy666.duostatusbar.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.kvmy666.duostatusbar.L
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * R8 problem a: the one owner of the two-SIM iOS cluster.
 *
 * iOS draws both SIMs as one block (data bars + other dots), but the stock bar keeps one
 * `OplusModernStatusBarMobileView` per subslot, each with its own `mobile_signal` ImageView. This
 * object gives the data SIM's `mobile_signal` a single [IosCellRole.BOTH] drawable and hides the
 * other outer views in the same bind pass. Hiding needs both `GONE` and width 0 (a gone view keeps
 * its slot, `.pm/R8-P0.md` P0-B); the recipe is reversible and idempotent.
 *
 * Identity is the measured public `ModernStatusBarMobileView.getSubId()`. The data SIM is
 * `SubscriptionManager.getDefaultDataSubscriptionId()` (synchronous, `.pm/R8-P0.md` P0-B). Every
 * signal bind and every per-SIM emission reconciles, so a data-SIM switch or SIM2->1->2 swaps the
 * block; the default-data broadcast is only a supplement.
 *
 * The pure [plan] is unit-tested; the runtime is fail-silent and every hook is guarded. iOS off
 * calls [deactivate], which restores every view/drawable this object still owns.
 */
internal object IosStackedSim {

    private const val TAG = "ios v2 stacked sim"

    /** The default-data switch broadcast; the per-bind reconcile is the primary signal. */
    private const val ACTION_DEFAULT_DATA =
        "android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED"

    /** The same four measured binder families the placement hook covers (`.pm/R8-P0.md` P0-B). */
    private val BINDERS = listOf(
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder",
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.TMOStatusBarMobileViewBinder",
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.SprintStatusBarMobileViewBinder",
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.BigTypeStatusBarMobileViewBinder"
    )

    /** The outer-visibility collector carries the outer view as `$view`. */
    private const val OUTER_LAMBDA = "\$bindCustEx\$1\$1\$1\$1\$1"

    /** The per-SIM emission carries the leaf as `$iconView`; it fires on a data switch. */
    private const val EMIT_LAMBDA = "\$bindCustEx\$1\$2\$1\$2\$1"

    // ------------------------------------------------------------------------------------ plan

    /** The pure two-SIM decision, independent of Android/Xposed so it is tested as data. */
    internal sealed class Plan {
        object None : Plan()

        /** One normal row on [dataSubId]; [hideSubIds] are the other SIMs to collapse. */
        data class Single(val dataSubId: Int, val hideSubIds: List<Int>) : Plan()

        /** One stacked block on [dataSubId]; [otherSubId] supplies the dots. */
        data class Stacked(
            val dataSubId: Int,
            val otherSubId: Int,
            val hideSubIds: List<Int>
        ) : Plan()
    }

    /**
     * The rule table (`/` = no hide, `x` = hide every non-data SIM):
     *
     * | SIMs | data unknown | SINGLE      | AUTO / DUAL      |
     * |------|--------------|-------------|------------------|
     * | 0/1  | None /       | Single      | Single           |
     * | 2+   | None /       | Single x    | Stacked x (data + first other) |
     *
     * The data view is never in [Plan.Single.hideSubIds] or [Plan.Stacked.hideSubIds].
     */
    internal fun plan(
        subIds: List<Int>,
        dataSubId: Int,
        display: IosSimDisplay
    ): Plan {
        val ids = subIds.filter { it > 0 }.distinct()
        if (dataSubId <= 0 || dataSubId !in ids) return Plan.None
        val others = ids.filter { it != dataSubId }
        if (others.isEmpty()) return Plan.Single(dataSubId, emptyList())
        return when (display) {
            IosSimDisplay.SINGLE -> Plan.Single(dataSubId, others)
            IosSimDisplay.AUTO,
            IosSimDisplay.DUAL -> Plan.Stacked(dataSubId, others.first(), others)
        }
    }

    /** The stacked visual: the data row keeps its bars, the other row becomes dots. */
    internal fun stackedVisual(base: IosVisual, otherBars: Float): IosVisual =
        base.copy(
            cellMode = IosCellMode.DUAL,
            cellRole = IosCellRole.BOTH,
            cellDots = otherBars.coerceIn(0f, 4f)
        )

    /** The forced/only-SIM visual: one data row, no dots. */
    internal fun singleVisual(base: IosVisual): IosVisual =
        base.copy(
            cellMode = IosCellMode.SINGLE,
            cellRole = IosCellRole.DATA,
            cellDots = 0f
        )

    private fun Plan.dataSub(): Int = when (this) {
        Plan.None -> 0
        is Plan.Single -> dataSubId
        is Plan.Stacked -> dataSubId
    }

    private fun Plan.hiddenSubs(): List<Int> = when (this) {
        Plan.None -> emptyList()
        is Plan.Single -> hideSubIds
        is Plan.Stacked -> hideSubIds
    }

    // -------------------------------------------------------------------------------- install

    @Volatile
    private var installedFlag = false

    /** True once at least one measured lambda was hooked in this process. */
    val installed: Boolean get() = installedFlag

    private var installDone = false

    @Synchronized
    fun install(classLoader: ClassLoader) {
        if (installDone) return
        installDone = true
        var classes = 0
        var methods = 0
        var missing = 0
        for (binder in BINDERS) {
            val outerHooks = hookLambda(classLoader, binder + OUTER_LAMBDA, "\$view")
            val emitHooks = hookLambda(classLoader, binder + EMIT_LAMBDA, "\$iconView")
            if (outerHooks < 0) missing++ else if (outerHooks > 0) {
                classes++
                methods += outerHooks
            }
            if (emitHooks < 0) missing++ else if (emitHooks > 0) {
                classes++
                methods += emitHooks
            }
        }
        installedFlag = methods > 0
        L.i("$TAG: classes=$classes methods=$methods missing=$missing failures=$failureCount")
    }

    /** @return the hooked overload count, 0 when the class exists but `emit` was not hookable, -1 if absent. */
    private fun hookLambda(classLoader: ClassLoader, className: String, fieldName: String): Int {
        val simple = className.substringAfterLast('.').substringBefore("\$")
        val cls = try {
            XposedHelpers.findClass(className, classLoader)
        } catch (t: Throwable) {
            noteClass(simple, false)
            IosV2Log.branchOnce(
                "stacked.findClass.$simple",
                "class missing - single-SIM rows kept"
            )
            return -1
        }
        return try {
            val hooks = XposedBridge.hookAllMethods(cls, "emit", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) =
                    onEmit(param, fieldName)
            })
            noteClass(simple, hooks.isNotEmpty())
            hooks.size
        } catch (t: Throwable) {
            failureCount++
            noteClass("$simple.$fieldName", false)
            IosV2Log.failureOnce("stacked.hook.$simple", t)
            0
        }
    }

    // ------------------------------------------------------------------------------- activate

    @Volatile
    private var active = false

    /** True while this object owns the two-SIM cluster (set by [activate], cleared by [deactivate]). */
    val isActive: Boolean get() = active

    @Volatile
    private var contextRef: Context? = null

    @Volatile
    private var display: IosSimDisplay = IosSimDisplay.AUTO

    private var receiver: BroadcastReceiver? = null
    private var receiverContext: Context? = null

    @Volatile
    private var stackedActiveFlag = false

    @Volatile
    private var lastDataSubId = 0

    @Volatile
    private var lastHiddenSubId = 0

    @Volatile
    private var swapCount = 0

    @Volatile
    private var failureCount = 0

    /** The last data subId that produced a real plan; used only for the [swaps] counter. */
    private var lastPlanData = 0

    /** REL210: measured binder classes and whether their emit lambda was hookable. */
    private val classFacts: MutableSet<String> =
        Collections.synchronizedSet(LinkedHashSet<String>())

    internal val hookClassesForDiagnostics: String
        get() = synchronized(classFacts) { classFacts.joinToString(",") }.ifBlank { "none" }

    // B2 miss-line evidence: measured binder classes whose hook is absent (empty when resolved).
    internal val missingBindersForDiagnostics: String
        get() = synchronized(classFacts) {
            classFacts.filter { it.startsWith("missing:") }
                .joinToString(",") { it.removePrefix("missing:") }
        }

    private fun noteClass(name: String, resolved: Boolean) {
        classFacts.add((if (resolved) "ok:" else "missing:") + name)
    }

    private val lock = Any()

    private val tracked: MutableMap<View, Tracked> =
        Collections.synchronizedMap(WeakHashMap<View, Tracked>())

    private class Tracked(var subId: Int) {
        var previousDrawable: Drawable? = null
        var installedDrawable: Drawable? = null
        var hide: Hide? = null
    }

    private class Hide(
        val visibility: Int,
        val params: ViewGroup.LayoutParams,
        val width: Int,
        val height: Int,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val marginStart: Int,
        val marginEnd: Int
    )

    @Synchronized
    fun activate(context: Context, settings: ModuleSettings?) {
        val app = try {
            context.applicationContext ?: context
        } catch (t: Throwable) {
            IosV2Log.failureOnce("stacked.applicationContext", t)
            context
        }
        active = true
        contextRef = app
        display = displayOf(settings)
        swapCount = 0
        lastPlanData = 0
        registerReceiver(app)
        reconcileAll()
    }

    @Synchronized
    fun applySettings(settings: ModuleSettings?) {
        display = displayOf(settings)
        if (active) {
            reconcileAll()
            // The stacked BOTH glyph has a different artboard/ink than the single data row.
            IosSlotLayout.reapply(settings)
        }
    }

    @Synchronized
    fun deactivate() {
        active = false
        unregisterReceiver()
        restoreAll()
        contextRef = null
        display = IosSimDisplay.AUTO
        stackedActiveFlag = false
        lastDataSubId = 0
        lastHiddenSubId = 0
        lastPlanData = 0
    }

    private fun displayOf(settings: ModuleSettings?): IosSimDisplay =
        if (settings == null) IosSimDisplay.AUTO else IosMapping.simDisplay(settings.simDisplay)

    private fun registerReceiver(context: Context) {
        if (receiver != null) return
        val next = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                reconcileAll()
            }
        }
        try {
            val filter = IntentFilter(ACTION_DEFAULT_DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    next,
                    filter,
                    null,
                    Handler(Looper.getMainLooper()),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(next, filter, null, Handler(Looper.getMainLooper()))
            }
            receiver = next
            receiverContext = context
        } catch (t: Throwable) {
            failureCount++
            IosV2Log.failureOnce("stacked.registerReceiver", t)
            receiver = null
            receiverContext = null
        }
    }

    private fun unregisterReceiver() {
        val current = receiver
        val context = receiverContext
        receiver = null
        receiverContext = null
        if (current != null && context != null) {
            try {
                context.unregisterReceiver(current)
            } catch (t: Throwable) {
                // Already unregistered/destroyed; nothing to restore.
                IosV2Log.failureOnce("stacked.unregisterReceiver", t)
            }
        }
    }

    private fun reconcileAll() {
        if (!active) return
        val views = synchronized(tracked) { tracked.keys.toList() }
        for (view in views) reconcile(view)
    }

    private fun restoreAll() {
        val live = synchronized(tracked) {
            tracked.entries.map { it.key to it.value }
        }
        for ((view, entry) in live) {
            try {
                restoreHidden(view)
                restoreDrawable(view, entry)
            } catch (t: Throwable) {
                failureCount++
                IosV2Log.failureOnce("stacked.restoreAll", t)
            }
        }
        synchronized(tracked) { tracked.clear() }
    }

    // ---------------------------------------------------------------------------------- hooks

    /** Called by [IosMobileTypePlacement] after the OEM set the signal image, before the type lookup. */
    internal fun onSignalIcon(signal: ImageView) {
        if (!active) return
        val outer = findOuter(signal) ?: return
        reconcile(outer)
    }

    private fun onEmit(param: XC_MethodHook.MethodHookParam, fieldName: String) {
        try {
            if (!active) return
            val thiz = param.thisObject ?: return
            val field = fieldOf(thiz.javaClass, fieldName) ?: return
            val target = field.get(thiz) as? View ?: return
            reconcile(findOuter(target) ?: return)
        } catch (t: Throwable) {
            failureCount++
            IosV2Log.failureOnce("stacked.onEmit", t)
        }
    }

    private fun reconcile(anchor: View) {
        synchronized(lock) {
            if (!active) return
            try {
                val outer = findOuter(anchor) ?: return
                val parent = outer.parent as? ViewGroup ?: return
                reconcileContainer(parent, readDataSubId(), display)
            } catch (t: Throwable) {
                failureCount++
                IosV2Log.failureOnce("stacked.reconcile", t)
            }
        }
    }

    /**
     * Test/production seam: reconcile [anchor]'s container with explicit state, without Xposed or
     * the telephony service. Returns true when a stacked block is live.
     */
    internal fun reconcileViews(
        anchor: View,
        dataSubId: Int,
        display: IosSimDisplay
    ): Boolean = synchronized(lock) {
        try {
            val outer = findOuter(anchor) ?: return false
            val parent = outer.parent as? ViewGroup ?: return false
            reconcileContainer(parent, dataSubId, display)
        } catch (t: Throwable) {
            failureCount++
            IosV2Log.failureOnce("stacked.reconcileViews", t)
            false
        }
    }

    // ------------------------------------------------------------------------------- reconcile

    private fun reconcileContainer(
        parent: ViewGroup,
        dataSubId: Int,
        display: IosSimDisplay
    ): Boolean {
        lastDataSubId = dataSubId
        val outers = collectOuters(parent)
        // A tracked view this object already wrote must be restored as soon as it stops being a
        // collected SIM outer (e.g. its getSubId() now returns 0), even while another outer keeps the
        // block live. A transient lookup failure must not leave a zeroed view stuck at 0x0 either.
        restoreOrphans(parent, outers)
        if (outers.isEmpty()) {
            stackedActiveFlag = false
            lastHiddenSubId = 0
            return false
        }
        val ids = outers.mapNotNull { trackedFor(it)?.subId }
        val decision = plan(ids, dataSubId, display)
        val planData = decision.dataSub()
        if (planData > 0) {
            if (lastPlanData > 0 && lastPlanData != planData) swapCount++
            lastPlanData = planData
        }
        if (decision == Plan.None) {
            IosV2Log.branchOnce("stacked.plan", "no data SIM - stock rows kept")
            failSafe(outers)
            return false
        }
        val dataOuter = outers.firstOrNull { trackedFor(it)?.subId == planData }
        if (dataOuter == null || !applyPlan(decision, outers, dataOuter)) {
            IosV2Log.branchOnce("stacked.plan", "plan not applicable - stock rows kept")
            failSafe(outers)
            return false
        }
        return true
    }

    /**
     * Restores anything this object wrote under [parent] that is not in [keep] (the current SIM
     * outers), so a view that dropped out of the readable set cannot stay hidden or 0x0.
     */
    private fun restoreOrphans(parent: ViewGroup, keep: List<View> = emptyList()) {
        val views = synchronized(tracked) { tracked.keys.toList() }
        for (view in views) {
            if (view.parent !== parent) continue
            if (keep.any { it === view }) continue
            val entry = trackedFor(view) ?: continue
            restoreHidden(view)
            restoreDrawable(view, entry)
        }
    }

    /** Every direct child that exposes a positive `getSubId()` is a SIM outer. */
    private fun collectOuters(parent: ViewGroup): List<View> {
        val out = ArrayList<View>(2)
        val count = parent.childCount
        for (i in 0 until count) {
            val child = parent.getChildAt(i) ?: continue
            val subId = subIdOf(child)
            if (subId > 0) {
                track(child, subId)
                out.add(child)
            }
        }
        return out
    }

    private fun applyPlan(plan: Plan, outers: List<View>, dataOuter: View): Boolean {
        val dataSignal = findCellView(dataOuter) ?: return false
        val dataDrawable = dataSignal.drawable as? IosGlyphDrawable ?: return false
        if (dataDrawable.kind != IosIconKind.CELL) return false
        val base = dataDrawable.visualForTest ?: return false

        val otherBars: Float
        if (plan is Plan.Stacked) {
            val otherOuter = outers.firstOrNull {
                trackedFor(it)?.subId == plan.otherSubId
            } ?: return false
            val otherSignal = findCellView(otherOuter) ?: return false
            val otherDrawable = otherSignal.drawable as? IosGlyphDrawable ?: return false
            if (otherDrawable.kind != IosIconKind.CELL) return false
            otherBars = otherDrawable.visualForTest?.cellBars ?: return false
        } else {
            otherBars = 0f
        }

        val wanted = if (plan is Plan.Stacked) {
            stackedVisual(base, otherBars)
        } else {
            singleVisual(base)
        }
        val dataTracked = trackedFor(dataOuter) ?: return false
        if (!replaceDrawable(dataSignal, dataDrawable, wanted, dataTracked)) return false

        val hideIds = plan.hiddenSubs()
        var firstHidden = 0
        for (view in outers) {
            if (view === dataOuter) continue
            val entry = trackedFor(view) ?: continue
            // A view that stops being the data SIM goes back to its engine single row before the
            // hide lands in the same pass, so a visibility flicker cannot flash the stacked block.
            restoreDrawable(view, entry)
            if (entry.subId in hideIds) {
                hide(view, entry)
                if (firstHidden == 0) firstHidden = entry.subId
            } else {
                restoreHidden(view)
            }
        }
        // A previous plan may have hidden the view that is now the data SIM. The data view must be
        // visible even when the data switch did not fire the OEM outer visibility collector.
        restoreHidden(dataOuter, show = true)
        lastHiddenSubId = firstHidden
        stackedActiveFlag = plan is Plan.Stacked
        return true
    }

    /** A missing/foreign drawable must leave the stock row(s): restore first, then report failure. */
    private fun failSafe(outers: List<View>) {
        for (view in outers) {
            val entry = trackedFor(view) ?: continue
            restoreHidden(view)
            restoreDrawable(view, entry)
        }
        stackedActiveFlag = false
        lastHiddenSubId = 0
    }

    private fun replaceDrawable(
        signal: ImageView,
        current: IosGlyphDrawable,
        wanted: IosVisual,
        entry: Tracked
    ): Boolean {
        // Same visual: either our instance already, or the engine's own single row is exact.
        if (current.visualForTest == wanted) return true
        val width = current.intrinsicWidth
        val height = current.intrinsicHeight
        if (width <= 0 || height <= 0) return false
        if (entry.installedDrawable !== current) entry.previousDrawable = current
        // R9: the layout owns the scale; the stacked drawable stays identity and fills its artboard.
        val laid = wanted.copy(userTransform = IosUserTransform(scale = IosGeometry.Relative.V2_DRAW_SCALE))
        val next = IosDrawables.create(
            IosIconKind.CELL,
            laid,
            width,
            height,
            userTransform = laid.userTransform,
            relativeSize = false,
            captureColorFilter = true
        )
        signal.setImageDrawable(next)
        entry.installedDrawable = next
        // The BOTH artboard is 150.1 x 105.6, not the single row: size the data slot from it.
        IosSlotLayout.registerMobile(signal)
        return true
    }

    private fun restoreDrawable(view: View, entry: Tracked) {
        val installed = entry.installedDrawable ?: return
        val signal = findCellView(view) ?: return
        if (signal.drawable !== installed) {
            // The engine/OEM already replaced our instance; nothing of ours is live.
            entry.installedDrawable = null
            entry.previousDrawable = null
            return
        }
        val previous = entry.previousDrawable ?: return
        signal.setImageDrawable(previous)
        entry.installedDrawable = null
        entry.previousDrawable = null
    }

    private fun hide(view: View, entry: Tracked) {
        val current = view.layoutParams
        var state = entry.hide
        if (state != null && state.params !== current) {
            // The OEM replaced the LayoutParams instance; remember the OEM's values, not ours.
            entry.hide = null
            state = null
        }
        val active = state ?: run {
            val params = current ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (current == null) view.layoutParams = params
            val margins = params as? ViewGroup.MarginLayoutParams
            Hide(
                visibility = view.visibility,
                params = params,
                width = params.width,
                height = params.height,
                left = margins?.leftMargin ?: 0,
                top = margins?.topMargin ?: 0,
                right = margins?.rightMargin ?: 0,
                bottom = margins?.bottomMargin ?: 0,
                marginStart = margins?.marginStart ?: 0,
                marginEnd = margins?.marginEnd ?: 0
            ).also { entry.hide = it }
        }

        var changed = false
        if (active.params.width != 0) {
            active.params.width = 0
            changed = true
        }
        if (active.params.height != 0) {
            active.params.height = 0
            changed = true
        }
        val margins = active.params as? ViewGroup.MarginLayoutParams
        if (margins != null) {
            if (margins.leftMargin != 0 || margins.topMargin != 0 ||
                margins.rightMargin != 0 || margins.bottomMargin != 0 ||
                margins.marginStart != 0 || margins.marginEnd != 0
            ) {
                changed = true
            }
            margins.leftMargin = 0
            margins.topMargin = 0
            margins.rightMargin = 0
            margins.bottomMargin = 0
            margins.marginStart = 0
            margins.marginEnd = 0
        }
        if (view.visibility != View.GONE) {
            view.visibility = View.GONE
            changed = true
        }
        if (changed) {
            if (view.layoutParams !== active.params) view.layoutParams = active.params
            view.requestLayout()
        }
        // The hidden SIM must not be revived by a later live-layout reapply.
        findCellView(view)?.let { IosSlotLayout.forget(it) }
    }

    /**
     * Restores the saved width/height whenever the live `LayoutParams` is still the instance we
     * zeroed, regardless of the view's current visibility: an OEM collector may have set it
     * `VISIBLE` before this after-hook runs, and returning then left the view permanently 0x0. An
     * OEM-replaced instance is never written (its own values win), but visibility is still restored
     * (or forced to [View.VISIBLE] with [show]) and [Tracked.hide] is cleared only after the writes.
     */
    private fun restoreHidden(view: View, show: Boolean = false) {
        val entry = trackedFor(view) ?: return
        val state = entry.hide
        var changed = false
        if (state != null && view.layoutParams === state.params) {
            state.params.width = state.width
            state.params.height = state.height
            val margins = state.params as? ViewGroup.MarginLayoutParams
            if (margins != null) {
                margins.leftMargin = state.left
                margins.topMargin = state.top
                margins.rightMargin = state.right
                margins.bottomMargin = state.bottom
                margins.marginStart = state.marginStart
                margins.marginEnd = state.marginEnd
            }
            changed = true
        }
        val wanted = if (show) View.VISIBLE else state?.visibility
        if (wanted != null && view.visibility != wanted) {
            view.visibility = wanted
            changed = true
        }
        if (changed) view.requestLayout()
        entry.hide = null
    }

    // ----------------------------------------------------------------------------- reflection

    private val subIdMethods = ConcurrentHashMap<Class<*>, Method>()

    /** Classes already proven to have no `getSubId()`; skips the exception on every later walk. */
    private val noSubId: MutableSet<Class<*>> = ConcurrentHashMap.newKeySet()
    private val viewFields = ConcurrentHashMap<Class<*>, Field>()
    private val iconFields = ConcurrentHashMap<Class<*>, Field>()

    private fun subIdOf(view: View): Int {
        val cls = view.javaClass
        if (cls in noSubId) return 0
        val method = subIdMethods[cls] ?: try {
            cls.getMethod("getSubId").also {
                it.isAccessible = true
                subIdMethods[cls] = it
            }
        } catch (t: Throwable) {
            noSubId.add(cls)
            IosV2Log.branchOnce(
                "stacked.subIdOf.${cls.simpleName}",
                "no getSubId - not a SIM outer"
            )
            return 0
        }
        return try {
            (method.invoke(view) as? Int) ?: 0
        } catch (t: Throwable) {
            failureCount++
            IosV2Log.failureOnce("stacked.subIdInvoke", t)
            0
        }
    }

    private fun fieldOf(cls: Class<*>, name: String): Field? {
        val cache = if (name == "\$view") viewFields else iconFields
        cache[cls]?.let { return it }
        return try {
            cls.getDeclaredField(name).apply { isAccessible = true }.also { cache[cls] = it }
        } catch (t: Throwable) {
            IosV2Log.branchOnce("stacked.field.$name", "field missing - stock rows kept")
            null
        }
    }

    /** Walk the measured chain (signal -> combo_real -> group -> outer), up to five levels. */
    private fun findOuter(start: View): View? {
        var current: View? = start
        var depth = 0
        while (current != null && depth < 5) {
            if (subIdOf(current) > 0) return current
            current = current.parent as? View
            depth++
        }
        return null
    }

    private fun findCellView(root: View): ImageView? {
        if (root is ImageView) {
            val glyph = root.drawable as? IosGlyphDrawable
            if (glyph?.kind == IosIconKind.CELL) return root
        }
        if (root is ViewGroup) {
            val count = root.childCount
            for (i in 0 until count) {
                val child = root.getChildAt(i) ?: continue
                val found = findCellView(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun track(view: View, subId: Int): Tracked {
        synchronized(tracked) {
            val existing = tracked[view]
            if (existing != null) {
                existing.subId = subId
                return existing
            }
            val fresh = Tracked(subId)
            tracked[view] = fresh
            return fresh
        }
    }

    private fun trackedFor(view: View): Tracked? = synchronized(tracked) { tracked[view] }

    private fun readDataSubId(): Int = try {
        SubscriptionManager.getDefaultDataSubscriptionId()
    } catch (t: Throwable) {
        failureCount++
        IosV2Log.failureOnce("stacked.readDataSubId", t)
        0
    }

    // ---------------------------------------------------------------------------- diagnostics

    /** True while the current plan is a live stacked block. */
    val stackedActive: Boolean get() = stackedActiveFlag

    /** The last data subscription id this object read (0 when unknown). */
    val dataSubId: Int get() = lastDataSubId

    /** The subId of the first view currently hidden by this object (0 when none). */
    val hiddenSubId: Int get() = lastHiddenSubId

    /** Outer mobile views seen so far; weak keys, so a dropped bar drops out. */
    val trackedViews: Int get() = synchronized(tracked) { tracked.size }

    /** Data-SIM switches observed by a successful plan. */
    val swaps: Int get() = swapCount

    /** Process-lifetime guard failures (install/lookup/apply); never reset by activation. */
    val failures: Int get() = failureCount
}
