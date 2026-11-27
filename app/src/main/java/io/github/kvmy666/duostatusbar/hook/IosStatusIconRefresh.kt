package io.github.kvmy666.duostatusbar.hook

import android.graphics.drawable.DrawableWrapper
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import io.github.kvmy666.duostatusbar.L
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** R17C: one icon the reload pass may touch; [reload] returns true when `updateDrawable(true)` ran. */
internal data class RefreshTarget(
    val slot: String?,
    val mapped: Boolean,
    /** R17C: the view currently holds one of our drawables (possibly inside a wrapper). */
    val glyph: Boolean = false,
    val reload: () -> Boolean
)

/** R17C: counters for one coalesced reload pass. */
internal data class RefreshResult(val icons: Int, val reloaded: Int, val failed: Int)

/**
 * R17C: a signal slot's icons are reloaded even when the map did not claim the id, because the stock
 * drawable was already installed before the map existed. Samsung's slots are `wifi`, `mobile*`/`data*`;
 * `ethernet` is listed with them. Pure, so the rule is unit-tested without a device.
 */
internal fun isSignalSlot(slot: String?): Boolean {
    if (slot == null) return false
    val value = slot.lowercase()
    return value == "wifi" || value == "ethernet" ||
        value.startsWith("mobile") || value.startsWith("data")
}

/**
 * R17C: reloads the mapped, signal or glyph targets. A reload that throws counts as failed and the
 * pass continues, so one bad view can never stop the rest of the bar from picking up the published
 * map. [force] is the last-resort pass that refreshes every collected status target (see [runPosted]).
 */
internal fun refreshTargets(targets: List<RefreshTarget>, force: Boolean = false): RefreshResult {
    var icons = 0
    var reloaded = 0
    var failed = 0
    for (target in targets) {
        if (!force && !target.mapped && !isSignalSlot(target.slot) && !target.glyph) continue
        icons++
        val ok = try {
            target.reload()
        } catch (t: Throwable) {
            false
        }
        if (ok) reloaded++ else failed++
    }
    return RefreshResult(icons, reloaded, failed)
}

/**
 * R17C: the one main-thread reload pass after the iOS v2 map is published (or removed).
 *
 * The measured problem (`diagnostics-dump.txt:1058`) is that the map was published after 94 icon loads
 * and the bar did not reload: `calls=0 hits=0 misses=0` at map-ready. Samsung's icons are
 * `StatusBarIconView`s (`:985`), whose `set(StatusBarIcon)` does not reload an unchanged icon
 * (`StatusBarIconView.smali:1971`), while `updateDrawable(true)` (`:3670`) calls `getIcon` (`:593`) ->
 * `Icon.loadDrawableAsUser` -> `ResourcesImpl.loadDrawable`, so it bypasses the dedup and reaches the
 * live map.
 *
 * Requests are coalesced with [pending]: one post per publish/activation burst, never a loop. On
 * deactivation the engine is already inactive when the pass runs, so [lastMode] is "stock" and
 * `updateDrawable` reloads the stock drawables the same way. Every reflection site is guarded and the
 * whole pass fails silent.
 */
internal object IosStatusIconRefresh : IosDiagnosticSource {

    private const val FAMILY = "samsung"

    /** Lazy, so a plain JVM test of the pure functions never touches Android's main looper. */
    private val handler: Handler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var rootsProvider: (() -> List<View>)? = null

    private val pending = AtomicBoolean(false)

    @Volatile
    private var requests = 0
    @Volatile
    private var lastFamily = ""
    @Volatile
    private var lastIcons = 0
    @Volatile
    private var lastReloaded = 0
    @Volatile
    private var lastFailed = 0
    @Volatile
    private var lastMode = "stock"
    @Volatile
    private var lastFallback = false

    fun install() {
        try {
            IosDiagnostics.register(this)
        } catch (t: Throwable) {
            IosV2Log.failureOnce("refresh.install", t)
        }
    }

    fun bindRoots(provider: () -> List<View>) {
        try {
            rootsProvider = provider
        } catch (t: Throwable) {
            IosV2Log.failureOnce("refresh.bindRoots", t)
        }
    }

    /** Asks for one coalesced reload pass; other families are a no-op (OxygenOS stays frozen). */
    fun request(family: String) {
        if (family != FAMILY) return
        try {
            requests++
            lastFamily = family
            if (!pending.compareAndSet(false, true)) return
            try {
                handler.post { runPosted() }
            } catch (t: Throwable) {
                pending.set(false)
                IosV2Log.failureOnce("refresh.post", t)
            }
        } catch (t: Throwable) {
            IosV2Log.failureOnce("refresh.request", t)
        }
    }

    /** The posted body; package-visible for a test seam, but every Android call is guarded. */
    internal fun runPosted() {
        pending.set(false)
        try {
            lastMode = if (IosResourceEngine.isActive) "live" else "stock"
            val provider = rootsProvider
            val rootViews = try {
                provider?.invoke().orEmpty()
            } catch (t: Throwable) {
                IosV2Log.failureOnce("refresh.roots", t)
                emptyList()
            }
            val targets = try {
                collectTargets(rootViews)
            } catch (t: Throwable) {
                IosV2Log.failureOnce("refresh.collect", t)
                emptyList()
            }
            var result = refreshTargets(targets)
            var fallback = false
            // Last resort: when the slot-name heuristic and the id read find nothing, reload every
            // collected status target once, so a build with different Samsung slot strings still picks
            // up its icons. Still one coalesced pass, not a loop.
            if (result.icons == 0 && targets.isNotEmpty()) {
                fallback = true
                result = refreshTargets(targets, force = true)
            }
            lastFallback = fallback
            lastIcons = result.icons
            lastReloaded = result.reloaded
            lastFailed = result.failed
            if (result.icons > 0 || result.failed > 0) {
                L.i(
                    "ios v2 refresh: family=samsung mode=$lastMode fallback=$lastFallback " +
                        "icons=${result.icons} reloaded=${result.reloaded} failed=${result.failed}"
                )
            }
        } catch (t: Throwable) {
            IosV2Log.failureOnce("refresh.run", t)
        }
    }

    /**
     * Iterative walk of the bound roots, identity-deduplicated so a window listed twice (or a child
     * shared between roots) is visited once. Only the Samsung reload owner is targeted.
     */
    private fun collectTargets(rootViews: List<View>): List<RefreshTarget> {
        val seen = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
        val out = ArrayList<RefreshTarget>()
        val stack = ArrayDeque<View>()
        for (root in rootViews) {
            if (seen.add(root)) stack.addLast(root)
        }
        while (stack.isNotEmpty()) {
            val view = stack.removeLast()
            if (isStatusBarIconView(view)) {
                val slot = readSlot(view)
                val mapped = readMapped(view)
                val glyph = hasOurGlyph(view)
                out.add(RefreshTarget(slot, mapped, glyph) { reloadView(view) })
            }
            if (view !is ViewGroup) continue
            try {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i) ?: continue
                    if (seen.add(child)) stack.addLast(child)
                }
            } catch (t: Throwable) {
                IosV2Log.failureOnce("refresh.children", t)
            }
        }
        return out
    }

    /** True when the class hierarchy contains the measured reload owner (never a guessed subclass). */
    private fun isStatusBarIconView(view: View): Boolean {
        var cls: Class<*>? = view.javaClass
        while (cls != null) {
            if (cls.name == "com.android.systemui.statusbar.StatusBarIconView") return true
            cls = cls.superclass
        }
        return false
    }

    private fun readSlot(view: View): String? = try {
        view.javaClass.getField("mSlot").get(view) as? String
    } catch (t: Throwable) {
        null
    }

    /**
     * `mIcon.icon.getResId()`/`getResPackage()` identifies the stock resource even when the view still
     * holds the stock drawable, so the pass can prove the id is in the published map.
     */
    private fun readMapped(view: View): Boolean = try {
        val statusBarIcon = view.javaClass.getField("mIcon").get(view) ?: return false
        val icon = statusBarIcon.javaClass.getField("icon").get(statusBarIcon) ?: return false
        val resId = icon.javaClass.getMethod("getResId").invoke(icon) as? Int ?: return false
        val resPackage = icon.javaClass.getMethod("getResPackage").invoke(icon) as? String
        IosResourceEngine.isMappedForRefresh(resId, resPackage)
    } catch (t: Throwable) {
        false
    }

    /**
     * True when the view currently draws one of our glyphs. Samsung's `StatusBarIconView.getIcon`
     * wraps the drawable in a `ScalingDrawableWrapper`, so the glyph can sit a few wrappers deep;
     * the walk is bounded so an unexpected cycle can never hang the pass.
     */
    private fun hasOurGlyph(view: View): Boolean = try {
        var drawable = (view as? ImageView)?.drawable
        var depth = 0
        var found = false
        while (drawable != null && depth < 4) {
            if (drawable is IosGlyphDrawable) {
                found = true
                break
            }
            drawable = (drawable as? DrawableWrapper)?.drawable
            depth++
        }
        found
    } catch (t: Throwable) {
        false
    }

    /**
     * `updateDrawable(true)` is the reload owner; `set(mIcon)` cannot be used because the unchanged
     * icon short-circuits (`StatusBarIconView.smali:1971`). A missing field or method leaves the view
     * untouched.
     */
    private fun reloadView(view: View): Boolean {
        val statusBarIcon = try {
            view.javaClass.getField("mIcon").get(view)
        } catch (t: Throwable) {
            return false
        } ?: return false
        return try {
            view.javaClass
                .getMethod("updateDrawable", Boolean::class.javaPrimitiveType)
                .invoke(view, true)
            true
        } catch (t: Throwable) {
            IosV2Log.failureOnce("refresh.updateDrawable", t)
            false
        }
    }

    override fun diagnosticLines(): List<String> {
        if (lastFamily != FAMILY || requests <= 0 || lastIcons <= 0) return emptyList()
        return listOf(
            "ios v2 refresh: family=samsung mode=$lastMode fallback=$lastFallback requests=$requests " +
                "icons=$lastIcons reloaded=$lastReloaded failed=$lastFailed"
        )
    }
}
