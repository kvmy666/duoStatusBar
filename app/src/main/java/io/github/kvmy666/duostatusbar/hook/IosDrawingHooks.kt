package io.github.kvmy666.duostatusbar.hook

import android.graphics.Canvas
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.IdentityHashMap

/**
 * One label drawn inside another record's native draw pass (`NATIVE_LABEL`).
 *
 * R6 item 1: the data-type label must inherit the mobile view's position, visibility, translationY
 * and alpha. For the measured OPLUS composite the outer's `dispatchDraw` already owns the draw
 * (`OUTER_DRAW`), so the label is an adjunct of that session rather than a separate overlay canvas
 * (`.pm/R6-PULL-report.md` §1).
 *
 * [bounds] returns `left, top, width, height` in the hooked view's coordinate space and must fill a
 * caller-owned array (never allocate or walk the tree) so the draw path allocates nothing.
 * [alpha] mirrors the anchor's own alpha so a fading/hidden native view fades/hides the label too
 * (R6 audit FIX 2); it must also be allocation-free.
 * [visual] is replaced by the owning record when the data-type label changes.
 */
internal class IosDrawLabel(
    var visual: IosVisual,
    val bounds: () -> IntArray,
    val alpha: () -> Float = { 1f }
)

/**
 * One registered replacement for a view that owns its own drawing (`OUTER_DRAW`/`LEAF_DRAW`).
 *
 * [bounds] returns `left, top, right, bottom` in the hooked view's coordinate space; [translateX]
 * replicates the measured OPLUS composite translation when the OEM exposes it. [skipOriginal] is true
 * for a true replacement: the stock children/paint must not draw underneath our glyph.
 *
 * [label] is an optional data-type label drawn first, inside this same native pass, so it follows the
 * view's translation/visibility/alpha automatically (R6 item 1).
 */
internal class IosDrawSession(
    val drawable: IosGlyphDrawable,
    val bounds: () -> IntArray,
    val translateX: (() -> Float)? = null,
    val skipOriginal: Boolean = true
) {
    var label: IosDrawLabel? = null
}

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
     * Draws [view]'s session on [canvas]. Returns true when the label OR the glyph was drawn and the
     * caller should skip the stock drawing; false for no session, a missing canvas, empty bounds, or
     * any failure (in which case the original must run, rule 5). The [IosDrawSession.bounds] and
     * [IosDrawLabel.bounds] providers fill caller-owned arrays, and [render] treats them read-only, so
     * a draw allocates nothing.
     *
     * The label is drawn first so the main glyph (the cell bars) always stays on top if ink ever
     * intersects (R6 item 2).
     */
    fun render(view: View, canvas: Canvas?): Boolean {
        val session = sessionFor(view) ?: return false
        val target = canvas ?: return false
        return try {
            val saved = target.save()
            var drew = false
            try {
                val translateX = session.translateX?.invoke() ?: 0f
                if (translateX != 0f) target.translate(translateX, 0f)

                session.label?.let { label ->
                    val box = label.bounds()
                    if (box.size >= 4) {
                        val labelWidth = box[2]
                        val labelHeight = box[3]
                        if (labelWidth > 0 && labelHeight > 0) {
                            val labelSaved = target.save()
                            try {
                                target.translate(box[0].toFloat(), box[1].toFloat())
                                IosGlyphRenderer.draw(
                                    target,
                                    IosIconKind.WIFI,
                                    label.visual,
                                    labelWidth,
                                    labelHeight,
                                    alpha = label.alpha()
                                )
                                drew = true
                            } finally {
                                target.restoreToCount(labelSaved)
                            }
                        }
                    }
                }

                val bounds = session.bounds()
                if (bounds.size >= 4) {
                    val left = bounds[0]
                    val top = bounds[1]
                    val width = bounds[2] - left
                    val height = bounds[3] - top
                    if (width > 0 && height > 0) {
                        target.translate(left.toFloat(), top.toFloat())
                        session.drawable.setBounds(0, 0, width, height)
                        session.drawable.draw(target)
                        drew = true
                    }
                }
                drew
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
     * Test-only: class#method keys reported as installed without the Xposed runtime. The real hook
     * cannot be installed inside a Robolectric JVM, yet the `OUTER_DRAW` code path (and the native
     * label that rides in its draw pass) must still be exercised. Never populated in production.
     */
    private val forced = HashSet<String>()

    /** Test seam: makes [ensure] report this member as installed and record no Xposed hook. */
    internal fun forceForTest(className: String, method: String) {
        if (className.isBlank() || method.isBlank()) return
        synchronized(installed) {
            forced.add("$className#$method")
        }
    }

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
            if (forced.contains(key)) return true
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
            forced.clear()
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
