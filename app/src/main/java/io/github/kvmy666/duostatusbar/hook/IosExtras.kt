package io.github.kvmy666.duostatusbar.hook

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.kvmy666.duostatusbar.L
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * R8 "Hide extra icons" (`hide_extras`, `.pm/R8-P0.md` decision 4).
 *
 * The resource engine already replaces the three extra leaves' drawables at load time; this object
 * owns the live part: a process-wide [hidden] gate read by the gate drawables, and a weak registry of
 * the leaf `ImageView`s so a toggle is one gate flip plus `requestLayout()`/`invalidate()` (no
 * rebind, no overlay, no attach).
 *
 * iOS off is zero effect: [hidden] defaults to false, and only the Oplus families (OxygenOS/ColorOS)
 * map [IosResourceKind.HIDDEN] entries, so every other ROM keeps its stock badge/arrows untouched.
 */
internal object IosExtras {

    private const val TAG = "ios v2 extras"

    /** The measured Wi-Fi badge/arrow host (`wifi_left`/`wifi_inout` in the same group). */
    private const val WIFI_CLASS = "com.oplus.systemui.statusbar.pipeline.OplusWifiSignalExImpl"
    private const val WIFI_METHOD = "bindEx"

    /**
     * The Wi-Fi outer composite that owns the `wifi_group -> wifi_combo -> wifi_signal` chain
     * (`res/layout/new_status_bar_wifi_group.xml:2`). Verified against the decoded 16.99.12 device
     * SystemUI (`reverse/decoded/oneplus-phone`, `apktool.yml:10-11`, matching
     * `.pm/device/r11/last_dump.txt:17`), so the FQCN is measured, not guessed. It is still resolved
     * by [XposedHelpers.findClass] and only one warning is logged when absent.
     */
    private const val WIFI_VIEW_CLASS =
        "com.oplus.systemui.statusbar.phone.signal.widget.OplusModernStatusBarWifiView"

    /**
     * `bindEx` starts the image coroutine via `repeatWhenAttached`, so the after-hook usually runs
     * before our glyph is set on `wifi_signal`. Four bounded retries (absolute timings, ms) bridge
     * that race; they stop on success or when the leaf leaves its parent.
     */
    private val WIFI_RETRY_AT_MS = longArrayOf(0L, 50L, 150L, 400L)

    /** The four measured mobile binders; the same call sets the cell activity arrow (`data_inout`). */
    private val MOBILE_BINDERS = listOf(
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder",
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.TMOStatusBarMobileViewBinder",
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.SprintStatusBarMobileViewBinder",
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.BigTypeStatusBarMobileViewBinder"
    )
    private const val MOBILE_METHOD = "bindCustEx\$updateSignalIcon"

    private const val WIFI_LEFT = "wifi_left"
    private const val WIFI_INOUT = "wifi_inout"
    private const val DATA_INOUT = "data_inout"
    private const val WIFI_SIGNAL = "wifi_signal"

    private var logOnce = LogOnce()

    /** Signals with a bounded `wifi_signal` retry chain already running (never two chains). */
    private val retrying: MutableSet<ImageView> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ImageView, Boolean>()))

    /** The retry task posted per leaf, so [deactivate] can remove the pending callback. */
    private val retryTasks: MutableMap<ImageView, Runnable> =
        Collections.synchronizedMap(WeakHashMap<ImageView, Runnable>())

    /**
     * True between [apply]`(true)` and [apply]`(false)`/[deactivate]. Every hook body reads it first:
     * a bind or retry that lands after iOS is switched off must not re-register or write layout.
     */
    @Volatile
    private var active = false

    /** Test seam: force the `wifi_signal` id lookup (Robolectric has no SystemUI resources). */
    internal var idLookupForTest: ((String) -> Int)? = null

    /** Test seam: replace the slot registration so a test can pin or force-fail one path. */
    internal var registerWifiForTest: ((ImageView) -> Boolean)? = null

    /** Test seam: how many once-only exception warnings really reached [L]. */
    internal var warningCountForTest: Int = 0
        private set

    /**
     * The global hide-extras gate. Default false so a non-iOS bar is byte-for-byte stock; the
     * resource gate drawables read it directly, which is what makes the toggle live.
     */
    @Volatile
    var hidden: Boolean = false

    private var installDone = false

    /**
     * The ROM's SystemUI package (`rom.systemUiPackage`): the ids are resolved there first, because
     * a system window's context package is not always the package that owns the status-bar ids.
     */
    @Volatile
    private var systemUiPackage: String = ""

    /** Live leaves; a weak key because the bar/shade may drop a view (never cleared on deactivate). */
    private val views: MutableSet<ImageView> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ImageView, Boolean>()))

    private val idCache = ConcurrentHashMap<String, Int>()

    /** REL210: measured class names and whether they were resolved, for the diag block. */
    private val classFacts: MutableSet<String> =
        Collections.synchronizedSet(LinkedHashSet<String>())

    /** REL210: "ok:…/missing:…" for the extras hook classes. */
    internal val hookClassesForDiagnostics: String
        get() = synchronized(classFacts) { classFacts.joinToString(",") }.ifBlank { "none" }

    // B2 miss-line evidence: measured extras classes whose hook is absent (empty when resolved).
    internal val missingBindersForDiagnostics: String
        get() = synchronized(classFacts) {
            classFacts.filter { it.startsWith("missing:") }
                .joinToString(",") { it.removePrefix("missing:") }
        }

    private fun noteClass(name: String, resolved: Boolean) {
        classFacts.add((if (resolved) "ok:" else "missing:") + name)
    }

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    /** Leaves currently collapsed by the gate (diagnostics). */
    val hiddenViewCount: Int get() = if (hidden) views.size else 0

    /** Resolved hide-extras entries in the live resource map while the gate is on (diagnostics). */
    fun hiddenCount(): Int = if (hidden) IosResourceEngine.hiddenEntries else 0

    // ---------------------------------------------------------------------------------- install

    /**
     * Installs the Wi-Fi and mobile after-hooks. Idempotent and never throws: a missing class,
     * method or id logs once and leaves the stock extra drawable untouched.
     */
    @Synchronized
    fun install(classLoader: ClassLoader, systemUiPackage: String = "") {
        if (systemUiPackage.isNotEmpty()) this.systemUiPackage = systemUiPackage
        if (installDone) return
        installDone = true
        var methods = 0
        var failures = 0
        try {
            val wifi = XposedHelpers.findClass(WIFI_CLASS, classLoader)
            noteClass(WIFI_CLASS.substringAfterLast('.'), true)
            val hooked = XposedBridge.hookAllMethods(wifi, WIFI_METHOD, imagesHook(this::onWifiBind))
            if (hooked.isNotEmpty()) {
                methods++
            } else {
                logOnce.once("ios-extras-wifi-method") { L.w("$TAG $WIFI_METHOD not found; stock kept") }
            }
        } catch (t: Throwable) {
            failures++
            noteClass(WIFI_CLASS.substringAfterLast('.'), false)
            logOnce.once("ios-extras-wifi-class") {
                L.w("$TAG wifi binder unavailable: ${t.javaClass.simpleName}")
            }
        }
        // The outer view's attach/layout is the second, independent trigger: on OxygenOS the bind
        // coroutine may set the image after `bindEx` returns, and a relayout follows it. Missing
        // class/method is normal off OxygenOS, so it stays one warning and the bind retry still runs.
        try {
            val outer = XposedHelpers.findClass(WIFI_VIEW_CLASS, classLoader)
            noteClass(WIFI_VIEW_CLASS.substringAfterLast('.'), true)
            val attached = XposedBridge.hookAllMethods(
                outer,
                "onAttachedToWindow",
                viewHook { registerWifiFrom(it) }
            )
            val laidOut = XposedBridge.hookAllMethods(
                outer,
                "onLayout",
                viewHook { registerWifiFrom(it) }
            )
            if (attached.isNotEmpty() || laidOut.isNotEmpty()) {
                methods++
            } else {
                logOnce.once("ios-extras-wifi-view-method") {
                    L.w("$TAG $WIFI_VIEW_CLASS attach/layout not found; bind retry kept")
                }
            }
        } catch (t: Throwable) {
            noteClass(WIFI_VIEW_CLASS.substringAfterLast('.'), false)
            logOnce.once("ios-extras-wifi-view") {
                L.w("$TAG wifi outer unavailable: ${brief(t)}; bind retry kept")
            }
        }
        for (name in MOBILE_BINDERS) {
            // One binder family missing is normal across OEM variants; the others still work.
            val simple = name.substringAfterLast('.')
            val cls = try {
                XposedHelpers.findClass(name, classLoader)
            } catch (t: Throwable) {
                noteClass(simple, false)
                logOnce.once("ios-extras-mobile-class-$name") {
                    L.w("$TAG binder ${name.substringAfterLast('.')} unavailable: ${t.javaClass.simpleName}")
                }
                continue
            }
            noteClass(simple, true)
            try {
                val hooked = XposedBridge.hookAllMethods(cls, MOBILE_METHOD, imagesHook(this::onMobileBind))
                if (hooked.isEmpty()) {
                    logOnce.once("ios-extras-mobile-method-$name") {
                        L.w("$TAG $name.$MOBILE_METHOD not found; stock kept")
                    }
                } else {
                    methods++
                }
            } catch (t: Throwable) {
                logOnce.once("ios-extras-mobile-hook-$name") {
                    L.w("$TAG ${name.substringAfterLast('.')} hook failed: ${t.javaClass.simpleName}")
                }
            }
        }
        L.i("$TAG: methods=$methods failures=$failures")
    }

    private fun imagesHook(
        body: (XC_MethodHook.MethodHookParam) -> Unit
    ): XC_MethodHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) = body(param)
    }

    /** After-hook on the Wi-Fi outer view; the body is always guarded so SystemUI keeps running. */
    private fun viewHook(body: (ViewGroup) -> Unit): XC_MethodHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            try {
                if (!active) {
                    refuseInactive("wifi view event")
                    return
                }
                val root = param.thisObject as? ViewGroup ?: return
                body(root)
            } catch (t: Throwable) {
                warnOnce("ios-extras-wifi-view-event") {
                    "$TAG wifi outer event failed: ${brief(t)}"
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------- hook

    private fun onWifiBind(param: XC_MethodHook.MethodHookParam) {
        try {
            val group = param.args.firstOrNull { it is ViewGroup } as? ViewGroup ?: return
            bindWifi(group)
        } catch (t: Throwable) {
            warnOnce("ios-extras-wifi-bind") { "$TAG wifi bind failed: ${brief(t)}" }
        }
    }

    /** The Wi-Fi bind body; shared with [bindWifiForTest] so a test runs the real hook path. */
    private fun bindWifi(group: ViewGroup) {
        if (!active) {
            refuseInactive("wifi bind")
            return
        }
        registerById(group, WIFI_LEFT)
        registerById(group, WIFI_INOUT)
        // R9: the group's own leaf drives the real slot size; iOS off restores it exactly.
        registerWifiFrom(group)
    }

    /** Test seam: runs the same body the `bindEx` after-hook runs. */
    internal fun bindWifiForTest(group: ViewGroup) {
        bindWifi(group)
    }

    private fun onMobileBind(param: XC_MethodHook.MethodHookParam) {
        try {
            if (!active) {
                refuseInactive("mobile bind")
                return
            }
            val signal = param.args.firstOrNull() as? ImageView ?: return
            val parent = signal.parent as? ViewGroup ?: return
            registerById(parent, DATA_INOUT)
            // R9: same chain the placement owner uses; the label is picked up from the measured order.
            IosSlotLayout.registerMobile(signal)
        } catch (t: Throwable) {
            warnOnce("ios-extras-mobile-bind") { "$TAG mobile bind failed: ${brief(t)}" }
        }
    }

    /**
     * Finds `wifi_signal` under [root] and registers the live slot. Called from the `bindEx`
     * after-hook and from the Wi-Fi outer view's attach event; returns true once the slot is applied.
     */
    internal fun registerWifiFrom(root: ViewGroup): Boolean {
        if (!active) {
            refuseInactive("wifi register")
            return false
        }
        val signal = imageById(root, WIFI_SIGNAL) ?: return false
        if (tryRegisterWifi(signal)) return true
        if (!hasOurWifiGlyph(signal)) scheduleWifiRetry(signal)
        return false
    }

    /**
     * One registration attempt. A false result while the leaf still carries the stock drawable is the
     * bind race (`bindEx` starts its image coroutine after the hook), so the caller retries.
     */
    private fun tryRegisterWifi(signal: ImageView): Boolean {
        val seam = registerWifiForTest
        val registered = try {
            if (seam != null) seam(signal) else IosSlotLayout.registerWifi(signal)
        } catch (t: Throwable) {
            warnOnce("ios-extras-wifi-register") { "$TAG wifi register failed: ${brief(t)}" }
            return false
        }
        if (registered) {
            retrying.remove(signal)
            logOnce.once("ios-extras-wifi-registered") { L.i("$TAG wifi slot registered") }
        }
        return registered
    }

    /** Starts one bounded retry chain per leaf; a second trigger while it runs is a no-op. */
    private fun scheduleWifiRetry(signal: ImageView) {
        if (!active) {
            refuseInactive("wifi retry schedule")
            return
        }
        if (!retrying.add(signal)) return
        postWifiRetry(signal, 0)
    }

    /**
     * Posts the next retry at its absolute time (0/50/150/400 ms). The chain stops when the slot
     * registers, when the leaf leaves its parent, or after the four tries; never an open loop.
     */
    private fun postWifiRetry(signal: ImageView, index: Int) {
        if (!active) {
            retrying.remove(signal)
            retryTasks.remove(signal)
            refuseInactive("wifi retry")
            return
        }
        if (index >= WIFI_RETRY_AT_MS.size) {
            retrying.remove(signal)
            retryTasks.remove(signal)
            warnOnce("ios-extras-wifi-retry") {
                "$TAG wifi slot still stock after ${WIFI_RETRY_AT_MS.size} retries"
            }
            return
        }
        val delay = if (index == 0) {
            WIFI_RETRY_AT_MS[0]
        } else {
            WIFI_RETRY_AT_MS[index] - WIFI_RETRY_AT_MS[index - 1]
        }
        val retry = Runnable {
            retryTasks.remove(signal)
            if (!active) {
                retrying.remove(signal)
                refuseInactive("wifi retry")
                return@Runnable
            }
            if (signal.parent == null) {
                retrying.remove(signal)
            } else if (!tryRegisterWifi(signal)) {
                postWifiRetry(signal, index + 1)
            }
        }
        retryTasks[signal] = retry
        try {
            mainHandler.postDelayed(retry, delay)
        } catch (t: Throwable) {
            retrying.remove(signal)
            retryTasks.remove(signal)
            warnOnce("ios-extras-wifi-retry-post") {
                "$TAG wifi retry post failed: ${brief(t)}"
            }
        }
    }

    /** Removes every retry callback this object posted and forgets the chains. */
    private fun cancelWifiRetries() {
        val tasks = try {
            synchronized(retryTasks) { retryTasks.values.toList() }
        } catch (t: Throwable) {
            IosV2Log.failureOnce("extras.retrySnapshot", t)
            emptyList()
        }
        for (task in tasks) {
            try {
                mainHandler.removeCallbacks(task)
            } catch (t: Throwable) {
                IosV2Log.failureOnce("extras.retryCancel", t)
            }
        }
        retryTasks.clear()
        retrying.clear()
    }

    /** A late hook body while iOS is off: counted/logged once, then the caller returns untouched. */
    private fun refuseInactive(where: String) {
        IosV2Log.branchOnce("ios-extras-inactive", "$where after deactivate - ignored")
    }

    /** The stock leaf has not been replaced by our Wi-Fi glyph yet (the retry condition). */
    private fun hasOurWifiGlyph(signal: ImageView): Boolean {
        val glyph = signal.drawable as? IosGlyphDrawable ?: return false
        return glyph.kind == IosIconKind.WIFI
    }

    /** A once-only warning: exception class + a short message, never the view tree or user data. */
    private fun warnOnce(key: String, message: () -> String) {
        logOnce.once(key) {
            warningCountForTest++
            L.w(message())
        }
    }

    /** Class + first line of the message, bounded so a native exception cannot flood the log. */
    private fun brief(t: Throwable): String {
        val text = t.message?.substringBefore('\n')?.trim()?.take(80)
        return if (text.isNullOrEmpty()) t.javaClass.simpleName else "${t.javaClass.simpleName}: $text"
    }

    private fun registerById(root: ViewGroup, name: String) {
        imageById(root, name)?.let { register(it) }
    }

    private fun imageById(root: ViewGroup, name: String): ImageView? {
        val id = idOf(root, name)
        if (id == 0) {
            logOnce.once("ios-extras-id-$name") { L.w("$TAG id '$name' not found; stock kept") }
            return null
        }
        return try {
            root.findViewById<View>(id) as? ImageView
        } catch (t: Throwable) {
            warnOnce("ios-extras-find-$name") { "$TAG '$name' lookup failed: ${brief(t)}" }
            null
        }
    }

    private fun idOf(view: View, name: String): Int {
        idLookupForTest?.let { return it(name) }
        // The ROM's own SystemUI package first; the view context's package is only a fallback.
        val fallback = try {
            view.context.packageName
        } catch (t: Throwable) {
            IosV2Log.failureOnce("extras.contextPackage", t)
            ""
        }
        val romPackage = systemUiPackage
        val packages = when {
            romPackage.isEmpty() -> listOf(fallback)
            fallback.isEmpty() || fallback == romPackage -> listOf(romPackage)
            else -> listOf(romPackage, fallback)
        }
        for (pkg in packages) {
            if (pkg.isEmpty()) continue
            val key = "$pkg/$name"
            val cached = idCache[key]
            if (cached != null && cached != 0) return cached
            val id = try {
                view.resources.getIdentifier(name, "id", pkg)
            } catch (t: Throwable) {
                IosV2Log.failureOnce("extras.identifier.$name", t)
                0
            }
            if (id != 0) {
                idCache[key] = id
                return id
            }
        }
        return 0
    }

    /** Registers a leaf whose drawable is (or will be) a gate. */
    internal fun register(view: ImageView) {
        try {
            views.add(view)
        } catch (t: Throwable) {
            IosV2Log.failureOnce("extras.register", t)
        }
    }

    // ---------------------------------------------------------------------------------- toggle

    /**
     * Flips the gate and asks every registered leaf to re-measure on the UI thread. No view
     * visibility, layout params or drawable is written here: the gate drawable supplies the pixels.
     */
    fun apply(hidden: Boolean) {
        val wasActive = active
        active = hidden
        this.hidden = hidden
        if (!hidden) cancelWifiRetries()
        // Deactivating an already-inactive object is a no-op (no second layout round).
        if (!hidden && !wasActive) return
        val refresh = Runnable { refreshAll() }
        val main = Looper.getMainLooper()
        if (main != null && Looper.myLooper() == main) {
            refresh.run()
        } else {
            try {
                mainHandler.post(refresh)
            } catch (t: Throwable) {
                // No main looper (unexpected): the next real layout still reads the new gate.
                IosV2Log.failureOnce("extras.applyPost", t)
            }
        }
    }

    /** Leaves iOS: the existing gate drawables redraw the stock extra; the registry is kept. */
    fun deactivate() {
        apply(false)
    }

    private fun refreshAll() {
        val live = try {
            synchronized(views) { views.toList() }
        } catch (t: Throwable) {
            IosV2Log.failureOnce("extras.refreshSnapshot", t)
            emptyList()
        }
        for (view in live) {
            try {
                view.requestLayout()
                view.invalidate()
            } catch (t: Throwable) {
                // A detached/stale view is harmless; the weak registry drops it.
                IosV2Log.failureOnce("extras.refreshView", t)
            }
        }
    }

    // ------------------------------------------------------------------------------------ gate

    /** The state for one resolved [IosResourceSlot.Hidden] entry (fresh drawable per mint). */
    internal fun gateState(
        stock: Drawable.ConstantState?,
        widthPx: Int,
        heightPx: Int
    ): Drawable.ConstantState = HiddenGateState(stock, widthPx, heightPx)

    /** Test seam: drops the registry and the gate; production never calls this. */
    internal fun resetForTest() {
        active = false
        cancelWifiRetries()
        hidden = false
        views.clear()
        idCache.clear()
        retrying.clear()
        retryTasks.clear()
        idLookupForTest = null
        registerWifiForTest = null
        warningCountForTest = 0
        logOnce = LogOnce()
    }
}

/**
 * A constant state that mints a fresh [HiddenGateDrawable] (never a shared drawable). A null stock
 * state stays 0x0 in both gate positions: fail closed, no crash.
 */
internal class HiddenGateState(
    private val stock: Drawable.ConstantState?,
    internal val stockWidthPx: Int,
    internal val stockHeightPx: Int
) : Drawable.ConstantState() {

    override fun newDrawable(): Drawable = newDrawable(null, null)

    override fun newDrawable(res: Resources?, theme: Resources.Theme?): Drawable {
        val fresh = try {
            stock?.newDrawable(res, theme)
        } catch (t: Throwable) {
            IosV2Log.failureOnce("extras.gateState", t)
            null
        }
        return HiddenGateDrawable(this, fresh)
    }

    override fun getChangingConfigurations(): Int =
        try {
            stock?.changingConfigurations ?: 0
        } catch (t: Throwable) {
            IosV2Log.failureOnce("extras.gateConfigs", t)
            0
        }
}

/**
 * The R8 hide-extras gate: intrinsic 0x0 and no ink while [IosExtras.hidden], the captured stock
 * drawable at its stock intrinsic otherwise. Every paint property is forwarded to the stock drawable,
 * so the system's tint/alpha while the gate is off is exactly the ROM's.
 */
internal class HiddenGateDrawable(
    private val state: HiddenGateState,
    private val stock: Drawable?
) : Drawable() {

    private val stockWidth: Int
        get() = state.stockWidthPx.takeIf { it > 0 } ?: stock?.intrinsicWidth ?: 0

    private val stockHeight: Int
        get() = state.stockHeightPx.takeIf { it > 0 } ?: stock?.intrinsicHeight ?: 0

    override fun getIntrinsicWidth(): Int =
        if (IosExtras.hidden || stock == null) 0 else stockWidth

    override fun getIntrinsicHeight(): Int =
        if (IosExtras.hidden || stock == null) 0 else stockHeight

    override fun draw(canvas: Canvas) {
        if (IosExtras.hidden) return
        val sprite = stock ?: return
        val width = stockWidth
        val height = stockHeight
        if (width <= 0 || height <= 0) return
        val bounds = bounds
        val left = if (bounds.width() > 0) bounds.left else 0
        val top = if (bounds.height() > 0) bounds.top else 0
        sprite.setBounds(left, top, left + width, top + height)
        sprite.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        stock?.alpha = alpha
    }

    override fun getAlpha(): Int = stock?.alpha ?: 255

    override fun setTint(tintColor: Int) {
        stock?.setTint(tintColor)
    }

    override fun setTintList(tint: ColorStateList?) {
        stock?.setTintList(tint)
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        stock?.setTintMode(tintMode)
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        stock?.colorFilter = colorFilter
    }

    override fun getColorFilter(): ColorFilter? = stock?.colorFilter

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean =
        stock?.setVisible(visible, restart) ?: false

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getConstantState(): Drawable.ConstantState = state
}
