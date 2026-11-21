package io.github.kvmy666.duostatusbar.hook

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import io.github.kvmy666.duostatusbar.L

/**
 * The stock drawing surfaces the iOS skin can suppress in place (IOS-B1).
 *
 * A family is one measured vendor view anatomy, not a guess: every class name and leaf id below comes
 * from the measured iOS inventory (device dumps) §2.2/§2.5. Anything not measured stays [UNKNOWN] and is left untouched.
 */
internal enum class IosSkinFamily {
    OPLUS_BATTERY,
    OPLUS_WIFI,
    OPLUS_MOBILE,
    HYPEROS_BATTERY,
    HYPEROS_WIFI,
    HYPEROS_MOBILE,
    SAMSUNG_BATTERY,
    SAMSUNG_SBICON,
    COMPOSE_BATTERY,
    COMPOSE_ICON,
    OPAQUE_STAT_ICON,
    UNKNOWN
}

/**
 * How a family's stock drawing is stopped.
 *
 * [ALPHA] keeps the view VISIBLE and laid out; [CLEAR_DRAWABLE] drops an ImageView's drawable;
 * [INVISIBLE_KEEP_SLOT] hides a whole outer view and is used for Compose/opaque families and for the
 * measured OPLUS outers, where the outer owns its own `dispatchDraw` or draws extra siblings that a
 * leaf-only rule cannot cover. INVISIBLE keeps the slot: the OEM still lays the outer out, so the
 * glyph geometry does not move. [SKIP] means the family is not suppressed at all.
 */
internal enum class IosSkinStrategy {
    ALPHA,
    CLEAR_DRAWABLE,
    INVISIBLE_KEEP_SLOT,
    SKIP
}

/** Where the drawing leaf/leaves live relative to the stock outer view. */
internal enum class IosLeafSource {
    BY_ID,
    FIRST_IMAGE_VIEW,
    OUTER,
    NONE
}

/** A pure suppression decision: family + strategy + how to find the drawing surfaces. */
internal data class IosSkinPlan(
    val family: IosSkinFamily,
    val strategy: IosSkinStrategy,
    val leafIds: List<String> = emptyList(),
    val leafSource: IosLeafSource = IosLeafSource.NONE,
    /**
     * Measured content views, best-first, for a family whose outer is 0×0 or whose content sits in a
     * child (the measured OPLUS outers). Empty for families that carry their own bounds.
     */
    val geometryIds: List<String> = emptyList()
)

/**
 * Reads a measured stock view anatomy and returns its suppression plan. No Android state is changed.
 *
 * The string/kind overload is the test seam; [planFor] only reads the outer view's class, id, kind and
 * child ids and delegates to it. Detection order is deliberate:
 *  - Oplus first, because `OplusModernStatusBarWifiView` also contains `ModernStatusBarWifiView`;
 *  - Miui/Stat battery before the exact `BatteryMeterView` Samsung rule;
 *  - Compose icon before Compose battery, so a Compose icon view never becomes a battery slot.
 */
internal object IosSkinPlanner {

    fun planFor(outer: View): IosSkinPlan = try {
        val cls = outer.javaClass.simpleName.ifBlank { outer.javaClass.name }
        val id = resourceEntryName(outer)
        val kind = try {
            StockIconKey.kindOf(outer)
        } catch (_: Throwable) {
            StockIconKind.OTHER
        }
        val childIds = ArrayList<String>()
        collectIdNames(outer, childIds)
        planFor(cls, id, kind, childIds)
    } catch (_: Throwable) {
        IosSkinPlan(IosSkinFamily.UNKNOWN, IosSkinStrategy.SKIP)
    }

    internal fun planFor(
        className: String,
        idName: String?,
        kind: StockIconKind,
        childIdNames: List<String>
    ): IosSkinPlan {
        val cls = className.lowercase()
        val id = idName?.lowercase()
        val batteryId = id != null && id.contains("battery")

        // OPLUS draws extra siblings (`wifi_left`/`wifi_inout`, `mobile_type`/`data_inout`) and its own
        // dispatchDraw, so hiding one measured leaf left most of the stock drawing visible. Hide the
        // measured outer instead; INVISIBLE (not GONE) keeps the slot laid out and restorable.
        // Measured content ids: the measured OPLUS view tree (device dump lines 116-165):
        // `OplusModernStatusBarWifiView` is 0x0 with `wifi_group` 73x63; `OplusModernStatusBarMobileView`
        // is 0x0 with `mobile_group` 60x63; `StatBatteryMeterView` holds `battery_icon_view` 82x51.
        if (cls.contains("statbatterymeterview")) {
            return invisibleOuter(
                IosSkinFamily.OPLUS_BATTERY,
                listOf("battery_icon_view")
            )
        }
        if (cls.contains("oplusmodernstatusbarwifiview")) {
            return invisibleOuter(
                IosSkinFamily.OPLUS_WIFI,
                listOf("wifi_group", "wifi_signal")
            )
        }
        if (cls.contains("oplusmodernstatusbarmobileview")) {
            return invisibleOuter(
                IosSkinFamily.OPLUS_MOBILE,
                listOf("mobile_group", "mobile_signal")
            )
        }
        if (cls.contains("miuibatterymeterview")) {
            return alpha(
                IosSkinFamily.HYPEROS_BATTERY,
                listOf("hollow_battery_image", "battery_image")
            )
        }
        if (cls.contains("modernstatusbarwifiview")) {
            return alpha(IosSkinFamily.HYPEROS_WIFI, listOf("wifi_signal"))
        }
        if (cls.contains("modernstatusbarmobileview")) {
            return alpha(
                IosSkinFamily.HYPEROS_MOBILE,
                listOf("mobile_signal_container", "mobile_signal")
            )
        }
        // Checked before the battery rule: a Compose icon view is a whole icon, never a battery slot.
        if (cls.contains("singlebindablestatusbarcomposeiconview")) {
            return IosSkinPlan(
                IosSkinFamily.COMPOSE_ICON,
                IosSkinStrategy.INVISIBLE_KEEP_SLOT,
                leafSource = IosLeafSource.OUTER
            )
        }
        if (id == "battery_composable_view" ||
            (cls.contains("composeview") && (batteryId || kind == StockIconKind.BATTERY))
        ) {
            return IosSkinPlan(
                IosSkinFamily.COMPOSE_BATTERY,
                IosSkinStrategy.INVISIBLE_KEEP_SLOT,
                leafSource = IosLeafSource.OUTER
            )
        }
        if (cls.contains("singlebindablestatusbariconview")) {
            return alpha(IosSkinFamily.SAMSUNG_SBICON, listOf("icon_view"))
        }
        if (cls.contains("staticonview")) {
            return IosSkinPlan(IosSkinFamily.OPAQUE_STAT_ICON, IosSkinStrategy.SKIP)
        }
        // Exact simple name only: every other `*BatteryMeterView` (e.g. Infinix `TrBatteryMeterView`)
        // is unmeasured and stays UNKNOWN -> SKIP, leaving its stock drawing in place.
        if (cls == "batterymeterview") {
            return IosSkinPlan(
                IosSkinFamily.SAMSUNG_BATTERY,
                IosSkinStrategy.CLEAR_DRAWABLE,
                leafSource = IosLeafSource.FIRST_IMAGE_VIEW
            )
        }
        return IosSkinPlan(IosSkinFamily.UNKNOWN, IosSkinStrategy.SKIP)
    }

    private fun alpha(family: IosSkinFamily, ids: List<String>): IosSkinPlan =
        IosSkinPlan(family, IosSkinStrategy.ALPHA, ids, IosLeafSource.BY_ID)

    /** A measured OPLUS outer: hide it (INVISIBLE keeps the slot), and read geometry from [geometryIds]. */
    private fun invisibleOuter(family: IosSkinFamily, geometryIds: List<String>): IosSkinPlan =
        IosSkinPlan(
            family,
            IosSkinStrategy.INVISIBLE_KEEP_SLOT,
            leafSource = IosLeafSource.OUTER,
            geometryIds = geometryIds
        )

    /** The resource entry name, or null when the id is missing or unreadable (never throws). */
    private fun resourceEntryName(view: View): String? {
        if (view.id == View.NO_ID) return null
        return try {
            view.resources.getResourceEntryName(view.id)
        } catch (_: Throwable) {
            null
        }
    }

    private fun collectIdNames(view: View, out: MutableList<String>) {
        if (view !is ViewGroup) return
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            resourceEntryName(child)?.let { out.add(it) }
            collectIdNames(child, out)
        }
    }
}

/** A resolved suppression: the plan plus the concrete views it applies to. */
internal data class IosSkinTarget(
    val family: IosSkinFamily,
    val strategy: IosSkinStrategy,
    val leaves: List<View>,
    val outer: View
)

/**
 * Suppresses the stock drawing of one measured status-bar family and restores it exactly (IOS-B1).
 *
 * Nothing here resizes a view: no `layoutParams`, width or height is ever written (C2). ALPHA keeps
 * the leaf VISIBLE and laid out; CLEAR_DRAWABLE keeps the ImageView and its frame; INVISIBLE_KEEP_SLOT
 * only changes visibility, never to GONE unless the ROM itself wants the slot absent.
 *
 * A failure at any point restores everything touched and returns false (rule 5), so the stock bar is
 * left exactly as the ROM had it. Logging is one line per family+strategy suppression/restore, never
 * per re-assert (m2).
 */
internal class IosSkin {

    private val logOnce = LogOnce()

    private var target: IosSkinTarget? = null
    private var originalAlphas: FloatArray = FloatArray(0)
    private var originalDrawables: Array<Drawable?> = emptyArray()
    private var intendedVisibility: Int = View.VISIBLE

    val isSuppressing: Boolean get() = target != null

    /**
     * The OEM's own visibility intent for the stock slot, independent of our suppression.
     *
     * [IosSkinStrategy.INVISIBLE_KEEP_SLOT] deliberately makes the outer INVISIBLE, so its own
     * visibility no longer carries the ROM's intent: the tracked [intendedVisibility] does. Alpha and
     * drawable suppression leave visibility alone, so the outer is the truth there. No target means
     * this skin is not suppressing anything, and the stock is reported visible.
     */
    val oemVisible: Boolean
        get() {
            val t = target ?: return true
            return when (t.strategy) {
                IosSkinStrategy.INVISIBLE_KEEP_SLOT -> intendedVisibility == View.VISIBLE
                IosSkinStrategy.ALPHA,
                IosSkinStrategy.CLEAR_DRAWABLE -> t.outer.visibility == View.VISIBLE
                IosSkinStrategy.SKIP -> true
            }
        }

    /**
     * Plans, resolves and applies the suppression for [outer]. Returns false for a SKIP family, a
     * family with no resolvable leaf, or any failure; in every false case nothing is left changed.
     * Calling it twice on the same view re-asserts without capturing the OEM's animated state again.
     */
    fun suppress(outer: View): Boolean {
        val current = target
        if (current != null) {
            if (current.outer === outer) {
                reassert()
                return true
            }
            restore()
        }
        return try {
            val plan = IosSkinPlanner.planFor(outer)
            if (plan.strategy == IosSkinStrategy.SKIP) return false

            val resolved = resolveLeaves(outer, plan)
            if (resolved.isEmpty()) return false
            val leaves = if (plan.strategy == IosSkinStrategy.CLEAR_DRAWABLE) {
                resolved.filterIsInstance<ImageView>()
            } else {
                resolved
            }
            if (leaves.isEmpty()) return false

            // Capture first, then mutate: a throw during capture leaves nothing touched, and a throw
            // during apply can restore every leaf from a complete snapshot.
            val alphas = if (plan.strategy == IosSkinStrategy.ALPHA) {
                FloatArray(leaves.size) { leaves[it].alpha }
            } else {
                FloatArray(0)
            }
            val drawables = if (plan.strategy == IosSkinStrategy.CLEAR_DRAWABLE) {
                Array(leaves.size) { (leaves[it] as ImageView).drawable }
            } else {
                emptyArray()
            }
            val intended = if (plan.strategy == IosSkinStrategy.INVISIBLE_KEEP_SLOT) {
                // A slot the OEM already hid draws no stock glyph; forcing our glyph visible would show
                // an icon the system wanted hidden. Fail silent and leave the view exactly as it is.
                if (outer.visibility != View.VISIBLE) return false
                View.VISIBLE
            } else {
                View.VISIBLE
            }

            target = IosSkinTarget(plan.family, plan.strategy, leaves, outer)
            originalAlphas = alphas
            originalDrawables = drawables
            intendedVisibility = intended

            when (plan.strategy) {
                IosSkinStrategy.ALPHA -> {
                    for (i in leaves.indices) leaves[i].alpha = 0f
                }
                IosSkinStrategy.CLEAR_DRAWABLE -> {
                    for (leaf in leaves) (leaf as ImageView).setImageDrawable(null)
                }
                IosSkinStrategy.INVISIBLE_KEEP_SLOT -> {
                    if (intended == View.VISIBLE) outer.visibility = View.INVISIBLE
                }
                IosSkinStrategy.SKIP -> return false
            }

            logOnce.once("ios-skin-suppress:${plan.family}:${plan.strategy}") {
                L.i("iOS skin suppress: family=${plan.family} strategy=${plan.strategy} leaves=${leaves.size}")
            }
            true
        } catch (t: Throwable) {
            fail(t)
            false
        }
    }

    /**
     * Re-applies the suppression after an OEM write (alpha animation, drawable rebind). Allocation-free
     * in the steady state; never logs (m2).
     */
    fun reassert() {
        val t = target ?: return
        try {
            when (t.strategy) {
                IosSkinStrategy.ALPHA -> {
                    for (i in t.leaves.indices) {
                        val leaf = t.leaves[i]
                        if (leaf.alpha != 0f) leaf.alpha = 0f
                    }
                }
                IosSkinStrategy.CLEAR_DRAWABLE -> {
                    for (leaf in t.leaves) {
                        val image = leaf as? ImageView ?: continue
                        if (image.drawable != null) image.setImageDrawable(null)
                    }
                }
                IosSkinStrategy.INVISIBLE_KEEP_SLOT -> {
                    val outer = t.outer
                    when (outer.visibility) {
                        View.GONE -> intendedVisibility = View.GONE
                        View.VISIBLE -> {
                            intendedVisibility = View.VISIBLE
                            outer.visibility = View.INVISIBLE
                        }
                        else -> Unit // Still INVISIBLE: keep the recorded ROM intent.
                    }
                }
                IosSkinStrategy.SKIP -> Unit
            }
        } catch (e: Throwable) {
            fail(e)
        }
    }

    /**
     * Puts every suppressed surface back exactly as captured. Idempotent: a second call is a no-op
     * because the snapshot is dropped here. For INVISIBLE_KEEP_SLOT only a view still INVISIBLE is
     * restored; a slot the ROM has meanwhile made GONE stays gone (or visible) as the ROM set it.
     */
    fun restore() {
        val t = target ?: return
        try {
            when (t.strategy) {
                IosSkinStrategy.ALPHA -> {
                    for (i in t.leaves.indices) t.leaves[i].alpha = originalAlphas[i]
                }
                IosSkinStrategy.CLEAR_DRAWABLE -> {
                    for (i in t.leaves.indices) {
                        (t.leaves[i] as? ImageView)?.setImageDrawable(originalDrawables[i])
                    }
                }
                IosSkinStrategy.INVISIBLE_KEEP_SLOT -> {
                    if (t.outer.visibility == View.INVISIBLE) {
                        t.outer.visibility = intendedVisibility
                    }
                }
                IosSkinStrategy.SKIP -> Unit
            }
            logOnce.once("ios-skin-restore:${t.family}:${t.strategy}") {
                L.i("iOS skin restore: family=${t.family} strategy=${t.strategy} leaves=${t.leaves.size}")
            }
        } catch (e: Throwable) {
            logOnce.once("ios-skin-restore-fail") {
                L.w("iOS skin restore failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        } finally {
            target = null
            originalAlphas = FloatArray(0)
            originalDrawables = emptyArray()
            intendedVisibility = View.VISIBLE
        }
    }

    private fun resolveLeaves(outer: View, plan: IosSkinPlan): List<View> =
        when (plan.leafSource) {
            IosLeafSource.BY_ID -> collectByIds(outer, plan.leafIds)
            IosLeafSource.FIRST_IMAGE_VIEW -> firstImageView(outer)
            IosLeafSource.OUTER -> listOf(outer)
            IosLeafSource.NONE -> emptyList()
        }

    /** DFS the outer subtree for every child whose resource entry name matches, case-insensitively. */
    private fun collectByIds(outer: View, names: List<String>): List<View> {
        if (names.isEmpty() || outer !is ViewGroup) return emptyList()
        val wanted = names.mapTo(HashSet()) { it.lowercase() }
        val out = ArrayList<View>()
        collectByIdsInto(outer, wanted, out)
        return out
    }

    private fun collectByIdsInto(parent: ViewGroup, wanted: Set<String>, out: MutableList<View>) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val name = try {
                if (child.id == View.NO_ID) null
                else child.resources.getResourceEntryName(child.id)?.lowercase()
            } catch (_: Throwable) {
                null
            }
            if (name != null && wanted.contains(name)) out.add(child)
            if (child is ViewGroup) collectByIdsInto(child, wanted, out)
        }
    }

    private fun firstImageView(outer: View): List<View> {
        if (outer !is ViewGroup) return emptyList()
        for (i in 0 until outer.childCount) {
            val found = firstImageViewIn(outer.getChildAt(i))
            if (found != null) return listOf(found)
        }
        return emptyList()
    }

    private fun firstImageViewIn(node: View): View? {
        if (node is ImageView) return node
        if (node is ViewGroup) {
            for (i in 0 until node.childCount) {
                val found = firstImageViewIn(node.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun fail(t: Throwable) {
        try {
            restore()
        } catch (_: Throwable) {
        }
        logOnce.once("ios-skin-fail") {
            L.w("iOS skin failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
