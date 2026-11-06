package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import io.github.kvmy666.duostatusbar.L

/**
 * Resolves a usable Application/context for the SystemUI process and finds status-bar windows that
 * were added before the module was injected.
 */
internal object AppContextResolver {

    const val TYPE_STATUS_BAR = 2000

    /** `WindowManager.LayoutParams.TYPE_NOTIFICATION_SHADE`, which is @hide. */
    const val TYPE_NOTIFICATION_SHADE = 2040

    /**
     * Resolves a usable Application/context for the SystemUI process.
     *
     * `AndroidAppHelper.currentApplication()` is the documented route, but on a framework that injects the
     * module after `Application.onCreate` it can be null (issue #9, Vector). Two reflection fallbacks read
     * the context straight off `ActivityThread`, and the status-bar window's own context is the last resort.
     */
    fun resolveAppContext(): Context? {
        try {
            android.app.AndroidAppHelper.currentApplication()?.let { return it }
        } catch (t: Throwable) {
            L.w("currentApplication unreadable: ${t.javaClass.simpleName}: ${t.message}")
        }
        try {
            val thread = Class.forName("android.app.ActivityThread")
            val current = thread.getMethod("currentActivityThread").invoke(null)
            if (current != null) {
                // Note: `mSystemContext` is deliberately NOT used. Its package is "android" while the
                // process uid is SystemUI, so a ContentResolver call from it is rejected with
                // "Given calling package android does not match caller's uid" - which broke both the
                // Settings.Global override and the settings provider read. Only contexts whose package is
                // the app are usable here.
                val field = thread.getDeclaredField("mInitialApplication").also { it.isAccessible = true }
                (field.get(current) as? Context)?.let { if (it.packageName != "android") return it }
                val bound = thread.getDeclaredField("mBoundApplication").also { it.isAccessible = true }
                    .get(current)
                if (bound != null) {
                    val app = bound.javaClass.getDeclaredField("appContext").also { it.isAccessible = true }
                        .get(bound) as? Context
                    if (app != null && app.packageName != "android") return app
                }
            }
        } catch (t: Throwable) {
            L.w("ActivityThread context unreadable: ${t.javaClass.simpleName}: ${t.message}")
        }
        return null
    }

    /**
     * Adopts a status-bar window that was added before the module was injected.
     *
     * Kept for compatibility; the shade window has its own type, so use [findExistingWindow] directly.
     */
    fun findExistingStatusBarWindow(): View? = findExistingWindow(TYPE_STATUS_BAR)

    /**
     * Adopts a window of [type] that was added before the module was injected.
     *
     * When injection is late, `WindowManagerImpl.addView` already fired and the addView hook will never see
     * the window, so it is read back from `WindowManagerGlobal.mViews` - the process's own list of added
     * windows - by its layout-params type.
     */
    fun findExistingWindow(type: Int): View? = try {
        val cls = Class.forName("android.view.WindowManagerGlobal")
        val instance = cls.getMethod("getInstance").invoke(null)
        val roots = cls.getDeclaredField("mViews").also { it.isAccessible = true }.get(instance) as? List<*>
        roots?.firstNotNullOfOrNull { root ->
            val view = when (root) {
                is View -> root
                null -> null
                else -> try {
                    root.javaClass.getDeclaredField("mView").also { it.isAccessible = true }
                        .get(root) as? View
                } catch (_: Throwable) {
                    null
                }
            }
            val rootType = (view?.layoutParams as? android.view.WindowManager.LayoutParams)?.type
            if (view != null && rootType == type) view else null
        }
    } catch (t: Throwable) {
        L.w("existing window scan: ${t.javaClass.simpleName}: ${t.message}")
        null
    }
}
