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
 * iOS off is zero effect: [hidden] defaults to false, and only OxygenOS maps
 * [IosResourceKind.HIDDEN] entries, so every other ROM keeps its stock badge/arrows untouched.
 */
internal object IosExtras {

    private const val TAG = "ios v2 extras"

    /** The measured Wi-Fi badge/arrow host (`wifi_left`/`wifi_inout` in the same group). */
    private const val WIFI_CLASS = "com.oplus.systemui.statusbar.pipeline.OplusWifiSignalExImpl"
    private const val WIFI_METHOD = "bindEx"

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

    private val logOnce = LogOnce()

    @Volatile
    private var installedFlag = false

    /** True once at least one measured hook was installed in this process. */
    val installed: Boolean get() = installedFlag

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

    @Volatile
    private var registeredTotal: Long = 0L

    private val idCache = ConcurrentHashMap<String, Int>()

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    /** Live registered leaves (diagnostics). */
    val registeredCount: Int get() = views.size

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
            val hooked = XposedBridge.hookAllMethods(wifi, WIFI_METHOD, imagesHook(this::onWifiBind))
            if (hooked.isNotEmpty()) {
                methods++
            } else {
                logOnce.once("ios-extras-wifi-method") { L.w("$TAG $WIFI_METHOD not found; stock kept") }
            }
        } catch (t: Throwable) {
            failures++
            logOnce.once("ios-extras-wifi-class") {
                L.w("$TAG wifi binder unavailable: ${t.javaClass.simpleName}")
            }
        }
        for (name in MOBILE_BINDERS) {
            // One binder family missing is normal across OEM variants; the others still work.
            val cls = try {
                XposedHelpers.findClass(name, classLoader)
            } catch (t: Throwable) {
                logOnce.once("ios-extras-mobile-class-$name") {
                    L.w("$TAG binder ${name.substringAfterLast('.')} unavailable: ${t.javaClass.simpleName}")
                }
                continue
            }
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
        installedFlag = methods > 0
        L.i("$TAG: methods=$methods failures=$failures")
    }

    private fun imagesHook(
        body: (XC_MethodHook.MethodHookParam) -> Unit
    ): XC_MethodHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) = body(param)
    }

    // ------------------------------------------------------------------------------------- hook

    private fun onWifiBind(param: XC_MethodHook.MethodHookParam) {
        try {
            val group = param.args.firstOrNull { it is ViewGroup } as? ViewGroup ?: return
            registerById(group, WIFI_LEFT)
            registerById(group, WIFI_INOUT)
            // R9: the group's own leaf drives the real slot size; iOS off restores it exactly.
            imageById(group, WIFI_SIGNAL)?.let { IosSlotLayout.registerWifi(it) }
        } catch (_: Throwable) {
            // Whole body guarded: any unexpected failure leaves the stock extra untouched.
        }
    }

    private fun onMobileBind(param: XC_MethodHook.MethodHookParam) {
        try {
            val signal = param.args.firstOrNull() as? ImageView ?: return
            val parent = signal.parent as? ViewGroup ?: return
            registerById(parent, DATA_INOUT)
            // R9: same chain the placement owner uses; the label is picked up from the measured order.
            IosSlotLayout.registerMobile(signal)
        } catch (_: Throwable) {
            // Whole body guarded.
        }
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
        } catch (_: Throwable) {
            null
        }
    }

    private fun idOf(view: View, name: String): Int {
        // The ROM's own SystemUI package first; the view context's package is only a fallback.
        val fallback = try {
            view.context.packageName
        } catch (_: Throwable) {
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
            } catch (_: Throwable) {
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
        val added = try {
            views.add(view)
        } catch (_: Throwable) {
            false
        }
        if (added) registeredTotal++
    }

    // ---------------------------------------------------------------------------------- toggle

    /**
     * Flips the gate and asks every registered leaf to re-measure on the UI thread. No view
     * visibility, layout params or drawable is written here: the gate drawable supplies the pixels.
     */
    fun apply(hidden: Boolean) {
        this.hidden = hidden
        val refresh = Runnable { refreshAll() }
        val main = Looper.getMainLooper()
        if (main != null && Looper.myLooper() == main) {
            refresh.run()
        } else {
            try {
                mainHandler.post(refresh)
            } catch (_: Throwable) {
                // No main looper (unexpected): the next real layout still reads the new gate.
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
        } catch (_: Throwable) {
            emptyList()
        }
        for (view in live) {
            try {
                view.requestLayout()
                view.invalidate()
            } catch (_: Throwable) {
                // A detached/stale view is harmless; the weak registry drops it.
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

    /** Test seam: fields the diagnostics expose without an Xposed install. */
    internal val registeredTotalForTest: Long get() = registeredTotal

    /** Test seam: drops the registry and the gate; production never calls this. */
    internal fun resetForTest() {
        hidden = false
        views.clear()
        idCache.clear()
        registeredTotal = 0L
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
        } catch (_: Throwable) {
            null
        }
        return HiddenGateDrawable(this, fresh)
    }

    override fun getChangingConfigurations(): Int =
        try {
            stock?.changingConfigurations ?: 0
        } catch (_: Throwable) {
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
