package io.github.kvmy666.duostatusbar.hook

import android.graphics.Canvas
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.IdentityHashMap

/**
 * One registered replacement for a view that owns its own drawing (`OUTER_DRAW`/`LEAF_DRAW`).
 *
 * [bounds] returns `left, top, right, bottom` in the hooked view's coordinate space; [translateX]
 * replicates the measured OPLUS composite translation when the OEM exposes it. [skipOriginal] is true
 * for a true replacement: the stock children/paint must not draw underneath our glyph.
 */
internal class IosDrawSession(
    val drawable: IosGlyphDrawable,
    val bounds: () -> IntArray,
    val translateX: (() -> Float)? = null,
    val skipOriginal: Boolean = true
)

/**
 * The pure view→session registry behind [IosDrawHookInstaller]. No Xposed imports: a hook callback
 * only asks [render] whether it drew, so the whole registry is unit-testable without a device.
 *
 * Sessions are keyed by view identity: the same class is instantiated several times (home, keyguard,
 * shade, fake icons) and each copy has its own visuals.
 */
internal object IosDrawingHooks {

    private val sessions = IdentityHashMap<View, IosDrawSession>()

    /** Registers (or replaces) the session for [view]. */
    fun register(view: View, session: IosDrawSession) {
        synchronized(sessions) {
            sessions[view] = session
        }
    }

    /** Removes [view]'s session; a view without one is a no-op. */
    fun unregister(view: View) {
        synchronized(sessions) {
            sessions.remove(view)
        }
    }

    /** The session for [view], or null when iOS does not own that instance. */
    fun sessionFor(view: View): IosDrawSession? = synchronized(sessions) {
        sessions[view]
    }

    /**
     * Draws [view]'s session on [canvas]. Returns true when the glyph was drawn and the caller should
     * skip the stock drawing; false for no session, a missing canvas, empty bounds, or any failure (in
     * which case the original must run, rule 5). The [IosDrawSession.bounds] provider fills a
     * caller-owned array, and [render] treats it read-only, so a draw allocates nothing.
     */
    fun render(view: View, canvas: Canvas?): Boolean {
        val session = sessionFor(view) ?: return false
        val target = canvas ?: return false
        return try {
            val saved = target.save()
            try {
                val translateX = session.translateX?.invoke() ?: 0f
                if (translateX != 0f) target.translate(translateX, 0f)

                val bounds = session.bounds()
                if (bounds.size < 4) return false
                val left = bounds[0]
                val top = bounds[1]
                val width = bounds[2] - left
                val height = bounds[3] - top
                if (width <= 0 || height <= 0) return false

                target.translate(left.toFloat(), top.toFloat())
                session.drawable.setBounds(0, 0, width, height)
                session.drawable.draw(target)
                true
            } finally {
                target.restoreToCount(saved)
            }
        } catch (_: Throwable) {
            false
        }
    }

    /** Exactly [render]; named separately so a test states its intent. */
    fun renderForTest(view: View, canvas: Canvas?): Boolean = render(view, canvas)

    /** Drops every session. Test seam only; production teardown unregisters each record. */
    fun clearForTest() {
        synchronized(sessions) {
            sessions.clear()
        }
    }

    /** True when no view is registered; [IosDrawHookInstaller.releaseWhenEmpty] uses it. */
    internal fun isEmpty(): Boolean = synchronized(sessions) {
        sessions.isEmpty()
    }
}

/**
 * The only class in this feature that touches Xposed. It installs the two proven drawing methods —
 * `dispatchDraw(Canvas)` on a composite outer and `onDraw(Canvas)` on a custom leaf — and never hooks a
 * generated/lambda class.
 *
 * One hook per `className#method`; a missing class or method returns false and leaves the caller to its
 * approved fallback. The callback is a before-hook: [IosDrawingHooks.render] draws our glyph and, when
 * the session owns the drawing, `param.result = null` skips the stock draw. Every callback is guarded
 * so a failure leaves the original running.
 */
internal object IosDrawHookInstaller {

    private val installed = LinkedHashMap<String, XC_MethodHook.Unhook>()

    /**
     * Ensures `className#method` is hooked once. Returns false for a blank/generated name, a missing
     * class or an undeclared `(Canvas)` member. Deduplicated by name, not by view.
     */
    fun ensure(classLoader: ClassLoader?, className: String, method: String): Boolean {
        if (className.isBlank() || method.isBlank()) return false
        if (isGeneratedClass(className)) return false

        val key = "$className#$method"
        synchronized(installed) {
            if (installed.containsKey(key)) return true
        }

        // The whole hook setup is inside the guard: on a build without the Xposed runtime (unit
        // tests) even constructing the callback throws, and the plan must simply fall back.
        val unhook = try {
            val cls = XposedHelpers.findClass(className, classLoader) ?: return false
            val target = cls.getDeclaredMethod(method, Canvas::class.java)
            val hook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val view = param.thisObject as? View ?: return
                        val canvas = param.args?.let { it.getOrNull(0) as? Canvas } ?: return
                        val session = IosDrawingHooks.sessionFor(view) ?: return
                        if (IosDrawingHooks.render(view, canvas) && session.skipOriginal) {
                            // A void method's result is the "skip the original" switch.
                            param.result = null
                        }
                    } catch (_: Throwable) {
                        // Never let a draw hook take System UI down; the original keeps drawing.
                    }
                }
            }
            XposedBridge.hookMethod(target, hook)
        } catch (_: Throwable) {
            return false
        }

        synchronized(installed) {
            installed[key] = unhook
        }
        return true
    }

    /** Unhooks every draw method once no session remains; a no-op while any view is still registered. */
    fun releaseWhenEmpty() {
        if (!IosDrawingHooks.isEmpty()) return
        val snapshot = synchronized(installed) {
            if (installed.isEmpty()) return
            val all = installed.values.toList()
            installed.clear()
            all
        }
        for (unhook in snapshot) {
            try {
                unhook.unhook()
            } catch (_: Throwable) {
            }
        }
    }

    /** True for compiler-generated and lambda classes, which are unstable across any OTA. */
    private fun isGeneratedClass(className: String): Boolean =
        className.contains("$$") ||
            className.contains("ExternalSyntheticLambda", ignoreCase = true) ||
            className.contains("SyntheticLambda", ignoreCase = true) ||
            className.contains("\$Lambda")
}
