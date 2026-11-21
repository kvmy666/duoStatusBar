package io.github.kvmy666.duostatusbar.hook

import android.content.res.ColorStateList
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.kvmy666.duostatusbar.L
import java.util.IdentityHashMap

/**
 * Same-call adoption of an OEM icon push (IOS-R2 0d).
 *
 * The per-leaf pre-draw listener in [IosDrawableSwap] is the safety net: it runs once per frame and only
 * replaces a foreign drawable just before the frame draws. This object adds the fast path: an
 * after-hook on the two methods an OEM uses to push a stock glyph (`ImageView#setImageDrawable`, which
 * `setImageResource` also ends in) calls [IosDrawableSwap.onForeignPush] in the same call. The hook runs
 * after the OEM has written its drawable, not before it, and the swap puts ours back before the next
 * frame draws, so the shade/QS level updates cannot look like a re-assert fight.
 *
 * The tint hooks are the other half of item 6: whenever the system applies the icon colour
 * (`setImageTintList`/`setColorFilter`), the host re-reads it and repaints the Wi-Fi/cell glyph. The
 * callbacks only post a debounced refresh; nothing draws from the hook.
 *
 * The registries are pure identity maps, so they are exercised directly by unit tests. The Xposed
 * install itself is guarded twice: it only runs when [XposedBridge.getXposedVersion] says the runtime is
 * live, and every lookup/hook is inside a catch, so a build or test without Xposed stays a no-op.
 */
internal object IosPushHooks {

    /** One swap per leaf instance; the same class is instantiated on home, keyguard and shade. */
    private val swaps = IdentityHashMap<ImageView, IosDrawableSwap>()

    /** Tint listeners per view; a host registers the same callback for each record it owns. */
    private val tints = IdentityHashMap<View, MutableList<() -> Unit>>()

    private val logOnce = LogOnce()
    private var installed = false

    /** Routes `setImageDrawable` on [view] to [swap]'s same-call adoption. Idempotent. */
    @Synchronized
    fun watch(view: ImageView, swap: IosDrawableSwap) {
        swaps[view] = swap
        installOnce()
    }

    /** Stops routing pushes for [view]. Idempotent. */
    @Synchronized
    fun unwatch(view: ImageView) {
        swaps.remove(view)
    }

    /** Registers [callback] for the OEM tint writes on [view]. The same instance can be removed later. */
    @Synchronized
    fun onTint(view: View, callback: () -> Unit) {
        val list = tints[view] ?: ArrayList<() -> Unit>().also { tints[view] = it }
        if (list.none { it === callback }) list.add(callback)
        installOnce()
    }

    /** Removes exactly [callback] from [view]; a missing entry is a no-op. */
    @Synchronized
    fun offTint(view: View, callback: () -> Unit) {
        val list = tints[view] ?: return
        list.removeAll { it === callback }
        if (list.isEmpty()) tints.remove(view)
    }

    /** True when [view] has a drawable swap registered (test seam). */
    internal fun watchedForTest(view: View): Boolean = synchronized(this) { swaps[view] != null }

    /** Number of tint listeners on [view] (test seam). */
    internal fun tintCountForTest(view: View): Int = synchronized(this) { tints[view]?.size ?: 0 }

    /** Runs the drawable-push delivery without Xposed (test seam). */
    internal fun deliverDrawablePushForTest(view: View) {
        (view as? ImageView)?.let { deliverForeignPush(it) }
    }

    /** Runs the tint delivery without Xposed (test seam). */
    internal fun deliverTintForTest(view: View) = deliverTint(view)

    /** Drops every registration. Test seam only. */
    @Synchronized
    internal fun clearForTest() {
        swaps.clear()
        tints.clear()
    }

    // ----------------------------------------------------------------------------- delivery

    private fun deliverForeignPush(view: ImageView) {
        val swap = synchronized(this) { swaps[view] } ?: return
        try {
            swap.onForeignPush()
        } catch (_: Throwable) {
            // A push hook must never take System UI down; the pre-draw listener is still armed.
        }
    }

    private fun deliverTint(view: View) {
        val callbacks = synchronized(this) { tints[view]?.toList() } ?: return
        for (callback in callbacks) {
            try {
                callback()
            } catch (_: Throwable) {
            }
        }
    }

    // ----------------------------------------------------------------------------- install

    private fun installOnce() {
        if (installed) return
        installed = true
        if (!xposedActive()) return
        try {
            installHooks()
        } catch (t: Throwable) {
            logOnce.once("ios-push-install") {
                L.w("iOS push hooks not installed: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /**
     * `setImageDrawable` is the single drawable entry point: `setImageResource` ends in it, so hooking
     * the base method once covers both and cannot double-adopt. Tints are the `ImageView` setters plus
     * the `View` background/foreground setters an OPLUS composite uses.
     */
    private fun installHooks() {
        val cls = try {
            XposedHelpers.findClass("android.widget.ImageView", ImageView::class.java.classLoader)
        } catch (_: Throwable) {
            null
        } ?: return

        hookAfter(cls, "setImageDrawable", Drawable::class.java) { param ->
            (param.thisObject as? ImageView)?.let { deliverForeignPush(it) }
        }
        hookAfter(cls, "setImageTintList", ColorStateList::class.java) { param ->
            (param.thisObject as? View)?.let { deliverTint(it) }
        }
        hookAfter(cls, "setColorFilter", ColorFilter::class.java) { param ->
            (param.thisObject as? View)?.let { deliverTint(it) }
        }

        // Some ROM composites (OPLUS) colour the icon through the outer view's background or
        // foreground tint instead of an ImageView tint, so both View setters must refresh too.
        val viewCls = try {
            XposedHelpers.findClass("android.view.View", View::class.java.classLoader)
        } catch (_: Throwable) {
            null
        }
        if (viewCls != null) {
            hookAfter(viewCls, "setBackgroundTintList", ColorStateList::class.java) { param ->
                (param.thisObject as? View)?.let { deliverTint(it) }
            }
            hookAfter(viewCls, "setForegroundTintList", ColorStateList::class.java) { param ->
                (param.thisObject as? View)?.let { deliverTint(it) }
            }
        }
    }

    private fun hookAfter(
        cls: Class<*>,
        method: String,
        parameter: Class<*>,
        action: (XC_MethodHook.MethodHookParam) -> Unit
    ) {
        try {
            val target = cls.getDeclaredMethod(method, parameter)
            XposedBridge.hookMethod(target, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        action(param)
                    } catch (_: Throwable) {
                    }
                }
            })
        } catch (_: Throwable) {
            // One missing setter must not stop the other hooks; the pre-draw listener remains.
        }
    }

    /** False in a plain JVM unit test, where the native version call is not linked. */
    private fun xposedActive(): Boolean = try {
        XposedBridge.getXposedVersion() > 0
    } catch (_: Throwable) {
        false
    }
}
