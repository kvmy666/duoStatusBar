package io.github.kvmy666.duostatusbar.hook

import android.view.Gravity
import android.view.View
import android.graphics.drawable.Drawable
import android.widget.FrameLayout
import android.widget.ImageView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.kvmy666.duostatusbar.L
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * R7 P3: places the stock data-type label (`mobile_type`) after our iOS cell glyph instead of on top
 * of it, on OxygenOS only.
 *
 * The stock `mobile_combo_real` is a `FrameLayout` with `mobile_signal` at child 0 and `mobile_type`
 * at child 1, both `end|center`; the OEM never mutates those params (`.pm/R7-P0-map.md:119-154`,
 * `.pm/evidence/r7/build/A-oxygenos-names.md:272-337`). We do not replace the views: the resource
 * engine already owns the drawables, so this object only writes the two `FrameLayout.LayoutParams`
 * once per emission and leaves the rest to the framework.
 *
 * The write is idempotent by construction (the zero-layout-loop recipe,
 * `.pm/R7-P0-map.md:135-149`): the target params are compared before a copy is assigned, so a second
 * emission performs no allocation and triggers no layout pass. Width/height, visibility and alpha are
 * copied through untouched; the drawable's intrinsic size is the read side, never the measured view
 * width.
 */
internal object IosMobileTypePlacement {

    private const val TAG = "ios v2 placement"

    /** The family the measured Oplus binder belongs to (spec: `.pm/evidence/r7/build/D-design.md:240-266`). */
    private const val FAMILY = "oxygenos"

    /** Holders of the measured `bindCustEx$updateSignalIcon` (A evidence §3, lines 274-314). */
    private val BINDERS = listOf(
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder",
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.TMOStatusBarMobileViewBinder",
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.SprintStatusBarMobileViewBinder",
        "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.BigTypeStatusBarMobileViewBinder"
    )

    private const val METHOD = "bindCustEx\$updateSignalIcon"

    /** The label id is build-specific (16.00.14 `0x7f0a0823`, 16.99.12 `0x7f0a0856`), so resolve by name. */
    private const val TYPE_ID_NAME = "mobile_type"

    @Volatile
    private var installedFlag = false

    /** True once at least one measured binder method was hooked in this process. */
    val installed: Boolean get() = installedFlag

    @Volatile
    private var activeFamily: String = ""

    /** R7 P5: the live ROM's SystemUI package, passed by the caller (no hard-coded package, m4). */
    @Volatile
    private var activePackage: String = ""

    /** True only while OxygenOS owns the placement. */
    val isActive: Boolean get() = activeFamily == FAMILY

    private var installDone = false

    /** Resolved once per process/package; 0 until the first successful lookup, so the index fallback runs. */
    @Volatile
    private var cachedTypeId: Int = 0

    @Volatile
    private var cachedTypePackage: String = ""

    /**
     * R7 P5: the stock `LayoutParams` instance per view, captured on our first write. A weak key
     * because the bar/shade may drop a view; a dropped view needs no restore.
     */
    private val originalParams: MutableMap<View, FrameLayout.LayoutParams> =
        Collections.synchronizedMap(WeakHashMap<View, FrameLayout.LayoutParams>())

    /** The `LayoutParams` instance we installed; only this instance is ours to replace on deactivate. */
    private val installedParams: MutableMap<View, FrameLayout.LayoutParams> =
        Collections.synchronizedMap(WeakHashMap<View, FrameLayout.LayoutParams>())

    /** REL210: measured binder classes and whether they were resolved, for the diag block. */
    private val classFacts: MutableSet<String> =
        Collections.synchronizedSet(LinkedHashSet<String>())

    /** REL210: "ok:…/missing:…" for the four placement binders. */
    internal val hookClassesForDiagnostics: String
        get() = synchronized(classFacts) { classFacts.joinToString(",") }.ifBlank { "none" }

    private fun noteClass(name: String, resolved: Boolean) {
        classFacts.add((if (resolved) "ok:" else "missing:") + name)
    }

    // ---------------------------------------------------------------------------------- install

    /**
     * Installs the after-hook on the four measured binders. Idempotent and never throws: a missing
     * class or method is skipped and counted, and the stock layout stays exactly as it is.
     */
    @Synchronized
    fun install(classLoader: ClassLoader) {
        if (installDone) return
        installDone = true
        var classes = 0
        var methods = 0
        var missing = 0
        var failures = 0
        for (name in BINDERS) {
            val simple = name.substringAfterLast('.')
            val cls = try {
                XposedHelpers.findClass(name, classLoader)
            } catch (t: Throwable) {
                missing++
                noteClass(simple, false)
                IosV2Log.branchOnce(
                    "placement.findClass.$simple",
                    "class missing - stock layout kept"
                )
                continue
            }
            noteClass(simple, true)
            val hooked = try {
                XposedBridge.hookAllMethods(cls, METHOD, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) = onSignalIcon(param)
                })
            } catch (t: Throwable) {
                failures++
                IosV2Log.failureOnce("placement.hook.$simple", t)
                continue
            }
            if (hooked.isNotEmpty()) {
                classes++
                methods += hooked.size
            }
        }
        installedFlag = methods > 0
        L.i("$TAG: classes=$classes methods=$methods missing=$missing failures=$failures")
    }

    // --------------------------------------------------------------------------------- activate

    /**
     * [familyId] `null` or a non-OxygenOS id turns placement off. [systemUiPackage] is the live ROM's
     * SystemUI package, resolved from `RomAdapter` (R7 P5: never a hard-coded package, m4).
     */
    fun activate(familyId: String?, systemUiPackage: String) {
        activePackage = systemUiPackage
        if (cachedTypePackage != systemUiPackage) {
            cachedTypeId = 0
            cachedTypePackage = systemUiPackage
        }
        activeFamily = if (familyId != null && IosV2Profile.typePlacementFamily(familyId)) {
            familyId
        } else {
            ""
        }
    }

    /**
     * Stops placement and restores the two stock `LayoutParams` instances we replaced.
     *
     * Only a view whose current params are still the instance we installed is restored, so a later
     * OEM write is never overwritten with a stale copy.
     */
    fun deactivate() {
        activeFamily = ""
        restoreLayouts()
    }

    /** R7 P5 test seam: the package the next name lookup would use. */
    internal val packageForTest: String get() = activePackage

    // ------------------------------------------------------------------------------------- hook

    /**
     * The binder runs after every signal-icon emission; the label's non-null model is bound in the
     * same pass, so this is the one place the child exists with its final drawable.
     */
    private fun onSignalIcon(param: XC_MethodHook.MethodHookParam) {
        try {
            if (activeFamily != FAMILY) return
            val signal = param.args.firstOrNull() as? ImageView ?: return
            val parent = signal.parent as? FrameLayout ?: return
            // R8: the stacked two-SIM owner needs the final signal drawable, and a missing type
            // label must not skip the block.
            IosStackedSim.onSignalIcon(signal)
            val type = findType(parent, signal)
            val drawable = signal.drawable
            val iconWidth = drawable?.intrinsicWidth?.takeIf { it > 0 } ?: signal.width
            val iconHeight = drawable?.intrinsicHeight?.takeIf { it > 0 } ?: signal.height
            if (type == null) {
                // R9: the real slot owns the cell size even when the label is absent.
                IosSlotLayout.registerMobile(signal)
                return
            }
            place(signal, type, iconWidth, iconHeight)
        } catch (t: Throwable) {
            // Whole body guarded: any unexpected failure leaves the stock layout untouched.
            IosV2Log.failureOnce("placement.onSignalIcon", t)
        }
    }

    /** Name lookup first (cached), then the measured XML order: `mobile_type` follows `mobile_signal`. */
    private fun findType(parent: FrameLayout, signal: ImageView): ImageView? {
        val typeId = typeIdOf(signal)
        if (typeId != 0) {
            val byId = try {
                parent.findViewById<ImageView>(typeId)
            } catch (t: Throwable) {
                IosV2Log.failureOnce("placement.findType", t)
                null
            }
            if (byId != null) return byId
        } else {
            IosV2Log.branchOnce("placement.typeId", "id missing - index fallback")
        }
        val index = parent.indexOfChild(signal)
        if (index < 0) return null
        return parent.getChildAt(index + 1) as? ImageView
    }

    private fun typeIdOf(signal: ImageView): Int {
        val pkg = activePackage
        if (pkg.isEmpty()) return 0
        val cached = cachedTypeId
        if (cached != 0 && cachedTypePackage == pkg) return cached
        val id = try {
            signal.resources.getIdentifier(TYPE_ID_NAME, "id", pkg)
        } catch (t: Throwable) {
            IosV2Log.failureOnce("placement.typeIdentifier", t)
            0
        }
        if (id != 0) {
            cachedTypeId = id
            cachedTypePackage = pkg
        }
        return id
    }

    // -------------------------------------------------------------------------------- placement

    /**
     * Writes the two child params, and only when they differ from the target. Both children must
     * already be children of the same `FrameLayout` and carry `FrameLayout.LayoutParams`.
     *
     * @return true when the layout is placed or already correct, false when it cannot be placed.
     */
    internal fun place(
        signal: ImageView,
        type: ImageView,
        iconWidthPx: Int,
        iconHeightPx: Int
    ): Boolean {
        if (iconWidthPx <= 0 || iconHeightPx <= 0) return false
        val parent = signal.parent
        if (parent !is FrameLayout) return false
        if (type.parent !== parent) return false
        // R9: the layout owns the cell/label size and the label margin for our own glyph. The old
        // extension formula is kept only for a stock drawable (engine not active / test fixture).
        if (IosSlotLayout.registerMobile(signal, type)) return true
        val gap = gapPx(iconWidthPx, iconHeightPx)
        if (gap <= 0) return false

        val gravity = Gravity.START or Gravity.CENTER_VERTICAL
        // R7 P4: a user scale/offset can push our own glyph past the stock slot's right edge, so the
        // label clears the drawn glyph, not the slot. Identity adds 0 and keeps the P3 margin exact.
        val extra = extraExtensionPx(signal.drawable, iconWidthPx, iconHeightPx)
        val typeMarginStart = iconWidthPx + extra + gap
        val signalLp = signal.layoutParams as? FrameLayout.LayoutParams ?: return false
        val typeLp = type.layoutParams as? FrameLayout.LayoutParams ?: return false

        if (signalLp.gravity != gravity || signalLp.marginStart != 0 || signalLp.marginEnd != 0) {
            val copy = FrameLayout.LayoutParams(signalLp)
            copy.gravity = gravity
            copy.marginStart = 0
            copy.marginEnd = 0
            installParams(signal, signalLp, copy)
        }

        if (typeLp.gravity != gravity || typeLp.marginStart != typeMarginStart) {
            val copy = FrameLayout.LayoutParams(typeLp)
            copy.gravity = gravity
            copy.marginStart = typeMarginStart
            installParams(type, typeLp, copy)
        }
        return true
    }

    /**
     * Writes [copy] and records the stock instance the first time this view is touched (R7 P5).
     * A view that already carries our installed instance keeps the first captured original.
     */
    private fun installParams(view: View, current: FrameLayout.LayoutParams, copy: FrameLayout.LayoutParams) {
        synchronized(installedParams) {
            if (!installedParams.containsKey(view)) originalParams[view] = current
            installedParams[view] = copy
        }
        view.layoutParams = copy
    }

    /**
     * Puts every stock `LayoutParams` instance back, but only where our instance is still the live one.
     * A concurrent OEM write wins, and its own params are left alone.
     */
    private fun restoreLayouts() {
        val live: List<Pair<View, FrameLayout.LayoutParams>> = synchronized(installedParams) {
            installedParams.entries.map { entry -> entry.key to entry.value }
        }
        for ((view, ours) in live) {
            if (view.layoutParams !== ours) continue
            val original = synchronized(originalParams) { originalParams[view] } ?: continue
            try {
                view.layoutParams = original
            } catch (t: Throwable) {
                // A view that cannot accept its old params is left as the system has it.
                IosV2Log.failureOnce("placement.restoreLayout", t)
            }
        }
        synchronized(installedParams) { installedParams.clear() }
        synchronized(originalParams) { originalParams.clear() }
    }

    /**
     * Scales the measured ink gap by how much the stock slot shrinks our cell artboard. The stock
     * drawable's intrinsic size is the same size the resource engine copies (`ourBarsIntrinsicWidthPx`
     * read side, `D-design.md:240-266`); `0` when either dimension is unusable.
     */
    internal fun gapPx(iconWidthPx: Int, iconHeightPx: Int): Int {
        if (iconWidthPx <= 0 || iconHeightPx <= 0) return 0
        val scale = min(
            iconWidthPx / IosGeometry.Cell.ARTBOARD_W,
            iconHeightPx / IosGeometry.Cell.ARTBOARD_H
        )
        if (scale <= 0f) return 0
        return (IosGeometry.Strip.CELL_LABEL_GAP * scale).roundToInt().coerceAtLeast(1)
    }

    /**
     * How far our drawn glyph extends past the stock slot's right edge (R7 P4), as whole pixels.
     *
     * Only an [IosGlyphDrawable] carries a user transform; anything else (the stock drawable, a test
     * stub) returns 0. The measured scale around the glyph centre and the horizontal offset are the
     * same values the renderer applies, so the label always clears the visible ink:
     *
     *     right = iconW / 2 + innerW * scale / 2 + dx
     *
     * Identity (`scale = 1`, `dx = 0`) with the cell inner box is inside the slot, so this is 0 and
     * the P3 margin is byte-identical.
     */
    internal fun extraExtensionPx(
        drawable: Drawable?,
        iconWidthPx: Int,
        iconHeightPx: Int
    ): Int {
        val glyph = drawable as? IosGlyphDrawable ?: return 0
        if (iconWidthPx <= 0 || iconHeightPx <= 0) return 0
        val innerWidth = IosGeometry.Relative.sizeFor(
            IosIconKind.CELL,
            iconWidthPx,
            iconHeightPx,
            dualCell = glyph.visualForTest?.cellMode == IosCellMode.DUAL
        ).first
        if (innerWidth <= 0) return 0
        val transform = glyph.userTransform
        val right = iconWidthPx / 2f + innerWidth * transform.scale / 2f + transform.dxPx
        return max(0f, right - iconWidthPx).roundToInt()
    }
}
