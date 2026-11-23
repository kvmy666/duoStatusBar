package io.github.kvmy666.duostatusbar.hook

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The tight ink box and artboard box of one R9 glyph, in whole pixels.
 *
 * [left] / [top] are the leaf margins inside its outer slot; the leaf itself is artboard sized.
 */
internal data class IosSlotBox(
    val width: Int,
    val height: Int,
    val left: Int,
    val top: Int
)

/**
 * One outer slot at a given user scale. [outerWidth]/[outerHeight] are the real LayoutParams the
 * stock view gets; [leaf] is the artboard-sized glyph inside it, [label] the optional second occupant.
 */
internal data class IosSlotMetrics(
    val outerWidth: Int,
    val outerHeight: Int,
    val leaf: IosSlotBox,
    val label: IosSlotBox? = null
)

/**
 * R9 system-view layout: the stock icon views keep their identity and are sized by real
 * `LayoutParams`, then the already-installed iOS drawable redraws into the new box.
 *
 * Design source: `.pm/out-r9/R9-design.md` §System-view layout. The formulas are pure and pinned by
 * `IosSlotLayoutTest` at the owner's 1272 px width; the applier is the only code that writes stock
 * `LayoutParams`/clip flags and it stores/restores every value it touches (AGENTS C2/M15).
 *
 * The live scale path is the fix for the R9 slider bug: `IosResourceEngine` used to bake
 * `iosWifiScale`/`iosCellScale` into a drawable transform that already-installed views never reloaded.
 * Here the *layout* owns the scale, so [reapply] only resizes the live views and the same drawable
 * redraws — no resource remap is needed for a slider tick.
 */
internal object IosSlotLayout {

    private const val TAG = "ios v2 layout"

    /** Design unit: battery body height target is 44.4 px at 1272 (`0.03489 * screenW / 100`). */
    const val UNIT_PER_SCREEN_WIDTH = 0.03489f

    /** Equal ink slot gap: 20.6 px at 1272 (`0.01619 * screenW`). */
    const val GAP_PER_SCREEN_WIDTH = 0.01619f

    private val logOnce = LogOnce()

    /** The live settings snapshot; set by [reapply] on every settings apply/bind. */
    @Volatile
    private var currentSettings: ModuleSettings? = null

    /**
     * Test seam: production always reads the display metrics. A test may pin a screen width so the
     * Robolectric sandbox (often 320 px) can still assert the owner's 1272 px numbers.
     */
    @Volatile
    internal var screenWidthOverrideForTest: Int? = null

    private enum class Kind { WIFI, MOBILE, BATTERY }

    /** Weak references only: the bar, keyguard and QS may drop any of these views at any time. */
    private class Slot(
        val kind: Kind,
        val outer: WeakReference<View>,
        val group: WeakReference<View>?,
        val combo: WeakReference<View>?,
        val leaf: WeakReference<ImageView>,
        val label: WeakReference<ImageView>?,
        var stockRowHeight: Int
    )

    private class Original(
        val params: ViewGroup.LayoutParams,
        val width: Int,
        val height: Int,
        val leftMargin: Int,
        val topMargin: Int,
        val rightMargin: Int,
        val bottomMargin: Int,
        val marginStart: Int,
        val marginEnd: Int,
        val gravity: Int,
        val clipChildren: Boolean?,
        val clipToPadding: Boolean?
    )

    private val slots: MutableMap<ImageView, Slot> =
        Collections.synchronizedMap(WeakHashMap<ImageView, Slot>())

    private val originals: MutableMap<View, Original> =
        Collections.synchronizedMap(WeakHashMap<View, Original>())

    private val originalScaleType: MutableMap<ImageView, ImageView.ScaleType> =
        Collections.synchronizedMap(WeakHashMap<ImageView, ImageView.ScaleType>())

    /** Monotonic re-entry guard: a layout request from inside our write must not start a second pass. */
    private var applying = false

    // ----------------------------------------------------------------------------------- metrics

    /** The design unit `s` for [screenW] (px per design unit). */
    fun unit(screenW: Int): Float = screenW.coerceAtLeast(1) * UNIT_PER_SCREEN_WIDTH / 100f

    /** The equal slot gap in px. */
    fun gap(screenW: Int): Float = screenW.coerceAtLeast(1) * GAP_PER_SCREEN_WIDTH

    /**
     * One icon's real slot. The outer is `ink + gap` wide and at least the stock row high; the leaf
     * is the artboard box, positioned so the ink starts at `gap / 2` inside the outer. The user
     * offset is clamped inside the slot, never past a neighbour.
     */
    fun metrics(
        kind: IosIconKind,
        visual: IosVisual,
        screenW: Int,
        scalePercent: Int,
        offsetPercent: Int,
        stockRowHeight: Int
    ): IosSlotMetrics {
        val unitPx = unit(screenW)
        val gapPx = gap(screenW)
        val k = scalePercent.coerceIn(DuoPrefs.MIN_ICON_SCALE, DuoPrefs.MAX_ICON_SCALE) / 100f
        val ink = IosGeometry.inkBox(kind, visual)
        val artW = IosGlyphRenderer.artboardWidth(kind, visual)
        val artH = IosGlyphRenderer.artboardHeight(kind, visual)
        val inkW = ink.width * unitPx * k
        val artWpx = artW * unitPx * k
        val artHpx = artH * unitPx * k
        val outerW = (inkW + gapPx).roundToInt().coerceAtLeast(1)
        val outerH = max(stockRowHeight.coerceAtLeast(0), artHpx.roundToInt()).coerceAtLeast(1)
        val dx = (offsetPercent.coerceIn(DuoPrefs.MIN_ICON_OFFSET, DuoPrefs.MAX_ICON_OFFSET) /
            100f * inkW).coerceIn(-gapPx / 2f, gapPx / 2f)
        return IosSlotMetrics(
            outerWidth = outerW,
            outerHeight = outerH,
            leaf = IosSlotBox(
                width = artWpx.roundToInt().coerceAtLeast(1),
                height = artHpx.roundToInt().coerceAtLeast(1),
                left = (gapPx / 2f - ink.left * unitPx * k + dx).roundToInt().coerceAtLeast(0),
                top = ((outerH - artHpx) / 2f).roundToInt().coerceAtLeast(0)
            )
        )
    }

    /**
     * The label-mode mobile slot: the outer holds the cell then the data-type label with one [gap]
     * between their inks. The label uses the Wi-Fi slot's scale/offset because it replaces that slot.
     */
    fun mobileMetrics(
        cellVisual: IosVisual,
        labelVisual: IosVisual?,
        screenW: Int,
        cellScalePercent: Int,
        cellOffsetPercent: Int,
        labelScalePercent: Int,
        labelOffsetPercent: Int,
        stockRowHeight: Int
    ): IosSlotMetrics {
        if (labelVisual == null) {
            return metrics(
                IosIconKind.CELL,
                cellVisual,
                screenW,
                cellScalePercent,
                cellOffsetPercent,
                stockRowHeight
            )
        }
        val unitPx = unit(screenW)
        val gapPx = gap(screenW)
        val cellK = cellScalePercent.coerceIn(DuoPrefs.MIN_ICON_SCALE, DuoPrefs.MAX_ICON_SCALE) / 100f
        val labelK = labelScalePercent.coerceIn(DuoPrefs.MIN_ICON_SCALE, DuoPrefs.MAX_ICON_SCALE) / 100f
        val cellInk = IosGeometry.inkBox(IosIconKind.CELL, cellVisual)
        val labelInk = IosGeometry.inkBox(IosIconKind.WIFI, labelVisual)
        val cellArtW = IosGlyphRenderer.artboardWidth(IosIconKind.CELL, cellVisual)
        val cellArtH = IosGlyphRenderer.artboardHeight(IosIconKind.CELL, cellVisual)
        val labelArtW = IosGlyphRenderer.artboardWidth(IosIconKind.WIFI, labelVisual)
        val labelArtH = IosGlyphRenderer.artboardHeight(IosIconKind.WIFI, labelVisual)
        val cellInkW = cellInk.width * unitPx * cellK
        val labelInkW = labelInk.width * unitPx * labelK
        val cellDx = (cellOffsetPercent.coerceIn(DuoPrefs.MIN_ICON_OFFSET, DuoPrefs.MAX_ICON_OFFSET) /
            100f * cellInkW).coerceIn(-gapPx / 2f, gapPx / 2f)
        val labelDx = (labelOffsetPercent.coerceIn(DuoPrefs.MIN_ICON_OFFSET, DuoPrefs.MAX_ICON_OFFSET) /
            100f * labelInkW).coerceIn(-gapPx / 2f, gapPx / 2f)
        val cellArtHpx = cellArtH * unitPx * cellK
        val labelArtHpx = labelArtH * unitPx * labelK
        val outerW = (cellInkW + gapPx + labelInkW + gapPx).roundToInt().coerceAtLeast(1)
        val outerH = max(
            max(stockRowHeight.coerceAtLeast(0), cellArtHpx.roundToInt()),
            labelArtHpx.roundToInt()
        ).coerceAtLeast(1)
        return IosSlotMetrics(
            outerWidth = outerW,
            outerHeight = outerH,
            leaf = IosSlotBox(
                width = (cellArtW * unitPx * cellK).roundToInt().coerceAtLeast(1),
                height = cellArtHpx.roundToInt().coerceAtLeast(1),
                left = (gapPx / 2f - cellInk.left * unitPx * cellK + cellDx).roundToInt().coerceAtLeast(0),
                top = ((outerH - cellArtHpx) / 2f).roundToInt().coerceAtLeast(0)
            ),
            label = IosSlotBox(
                width = (labelArtW * unitPx * labelK).roundToInt().coerceAtLeast(1),
                height = labelArtHpx.roundToInt().coerceAtLeast(1),
                left = (gapPx / 2f + cellInkW + gapPx - labelInk.left * unitPx * labelK + labelDx)
                    .roundToInt().coerceAtLeast(0),
                top = ((outerH - labelArtHpx) / 2f).roundToInt().coerceAtLeast(0)
            )
        )
    }

    // ----------------------------------------------------------------------------------- registry

    /**
     * Registers the Wi-Fi chain and applies its current box. The chain is resolved from the
     * `wifi_signal` leaf (`combo -> wifi_group -> outer -> container`) because the OEM bind argument
     * may be the outer rather than the group. Returns false (stock kept) when it is not our glyph.
     */
    @Synchronized
    internal fun registerWifi(signal: ImageView): Boolean {
        val glyph = glyphOf(signal, IosIconKind.WIFI) ?: return unresolved("wifi")
        val combo = signal.parent as? ViewGroup ?: return false
        val group = combo.parent as? ViewGroup ?: return false
        val outer = group.parent as? ViewGroup ?: return false
        val slot = Slot(
            Kind.WIFI,
            WeakReference(outer),
            WeakReference(group),
            WeakReference(combo),
            WeakReference(signal),
            null,
            rowHeightFor(signal, listOf(outer, group, combo))
        )
        slots[signal] = slot
        applySlot(slot, glyph)
        return true
    }

    /**
     * Registers the mobile chain (`mobile_combo_real` -> `mobile_group` -> outer) and applies the
     * cell, plus the data-type label when the label occupies the slot. Returns false when the stock
     * tree is not the measured three-level chain (a test fixture or an unknown layout).
     */
    @Synchronized
    internal fun registerMobile(signal: ImageView, label: ImageView? = null): Boolean {
        val glyph = glyphOf(signal, IosIconKind.CELL) ?: return unresolved("mobile")
        val combo = signal.parent as? ViewGroup ?: return false
        val group = combo.parent as? ViewGroup ?: return false
        val outer = group.parent as? ViewGroup ?: return false
        val type = label ?: (combo.getChildAt(combo.indexOfChild(signal) + 1) as? ImageView)
        val slot = Slot(
            Kind.MOBILE,
            WeakReference(outer),
            WeakReference(group),
            WeakReference(combo),
            WeakReference(signal),
            type?.let { WeakReference(it) },
            rowHeightFor(signal, listOf(outer, group, combo))
        )
        slots[signal] = slot
        applySlot(slot, glyph)
        return true
    }

    /** Registers the battery container/leaf pair. The stock container is `StatBatteryMeterView`. */
    @Synchronized
    internal fun registerBattery(container: View, leaf: ImageView): Boolean {
        val glyph = glyphOf(leaf, IosIconKind.BATTERY) ?: return unresolved("battery")
        val slot = Slot(
            Kind.BATTERY,
            WeakReference(container),
            null,
            null,
            WeakReference(leaf),
            null,
            rowHeightFor(leaf, listOf(container))
        )
        slots[leaf] = slot
        applySlot(slot, glyph)
        return true
    }

    /** Drops a slot (the stacked-SIM owner hides that outer; it must not be revived on reapply). */
    @Synchronized
    internal fun forget(leaf: View) {
        if (leaf is ImageView) slots.remove(leaf)
    }

    /** Restores and forgets the slot that owns [leaf] (battery restore, owner teardown). */
    @Synchronized
    internal fun restoreFor(leaf: View) {
        val image = leaf as? ImageView ?: return
        val slot = slots.remove(image) ?: return
        restoreSlot(slot)
    }

    // -------------------------------------------------------------------------------------- live

    /**
     * Stores the live settings and re-applies every registered slot. Called on every settings
     * broadcast/bind so a slider tick changes the real view size and the existing drawable redraws.
     */
    @Synchronized
    fun reapply(settings: ModuleSettings?) {
        currentSettings = settings
        val live = try {
            synchronized(slots) { slots.entries.map { it.key to it.value } }
        } catch (_: Throwable) {
            return
        }
        for ((leaf, slot) in live) {
            val glyph = glyphOf(leaf, kindOf(slot.kind))
            if (glyph != null) applySlot(slot, glyph)
        }
    }

    // ------------------------------------------------------------------------------------ apply

    private fun applySlot(slot: Slot, glyph: IosGlyphDrawable) {
        val leaf = slot.leaf.get() ?: return
        val outer = slot.outer.get() ?: return
        // A stacked-SIM owner hides the non-data outer with GONE + 0x0; never revive it.
        if (outer.visibility == View.GONE && outer.layoutParams?.width == 0) return
        val visual = glyph.visualForTest ?: return
        val settings = currentSettings
        val screenW = screenWidth(leaf)
        val slotMetrics = when (slot.kind) {
            Kind.WIFI -> metrics(
                IosIconKind.WIFI,
                visual,
                screenW,
                scalePercent(settings, IosIconKind.WIFI),
                offsetPercent(settings, IosIconKind.WIFI),
                slot.stockRowHeight
            )
            Kind.MOBILE -> {
                val label = slot.label?.get()
                val labelGlyph = label?.drawable as? IosGlyphDrawable
                val labelVisual = labelGlyph
                    ?.takeIf { it.kind == IosIconKind.WIFI && label.visibility != View.GONE }
                    ?.visualForTest
                    ?.takeIf { it.wifiSlot is IosWifiSlot.DataType }
                mobileMetrics(
                    visual,
                    labelVisual,
                    screenW,
                    scalePercent(settings, IosIconKind.CELL),
                    offsetPercent(settings, IosIconKind.CELL),
                    scalePercent(settings, IosIconKind.WIFI),
                    offsetPercent(settings, IosIconKind.WIFI),
                    slot.stockRowHeight
                )
            }
            Kind.BATTERY -> metrics(
                IosIconKind.BATTERY,
                visual,
                screenW,
                scalePercent(settings, IosIconKind.BATTERY),
                offsetPercent(settings, IosIconKind.BATTERY),
                slot.stockRowHeight
            )
        }
        if (!begin()) return
        try {
            applyBox(outer, slotMetrics.outerWidth, slotMetrics.outerHeight)
            slot.group?.get()?.let { applyBox(it, slotMetrics.outerWidth, slotMetrics.outerHeight) }
            slot.combo?.get()?.let { applyBox(it, slotMetrics.outerWidth, slotMetrics.outerHeight) }
            applyLeaf(leaf, slotMetrics.leaf)
            slotMetrics.label?.let { box -> slot.label?.get()?.let { applyLeaf(it, box) } }
            unclip(outer)
            slot.group?.get()?.let { unclip(it) }
            slot.combo?.get()?.let { unclip(it) }
            val container = outer.parent as? ViewGroup
            if (container != null) {
                fitContainer(container)
                // Wi-Fi/cell live inside StatusIconContainer and need their parent row grown too.
                // The battery's parent IS that row (`system_icons`), so one level is enough there.
                if (slot.kind != Kind.BATTERY) fitRow(container)
            }
        } catch (t: Throwable) {
            logOnce.once("ios-slot-apply-${slot.kind}") {
                L.w("$TAG apply failed (${slot.kind}): ${t.javaClass.simpleName}")
            }
        } finally {
            applying = false
        }
    }

    /** One write per view, only when a value differs. */
    private fun applyBox(view: View, width: Int, height: Int) {
        val params = view.layoutParams ?: return
        capture(view, params)
        if (params.width == width && params.height == height) return
        params.width = width
        params.height = height
        view.requestLayout()
    }

    /**
     * Sizes the artboard leaf and positions its ink at the slot's left margin. `FIT_XY` is required:
     * the stock `wifi_signal` is `scaleType=center` and the other leaves default to `FIT_CENTER`, both
     * of which would re-scale/draw the glyph inside the new artboard-sized leaf.
     */
    private fun applyLeaf(view: ImageView, box: IosSlotBox) {
        val params = view.layoutParams ?: return
        capture(view, params)
        var changed = false
        if (params.width != box.width) {
            params.width = box.width
            changed = true
        }
        if (params.height != box.height) {
            params.height = box.height
            changed = true
        }
        val margins = params as? ViewGroup.MarginLayoutParams
        if (margins != null) {
            if (margins.leftMargin != box.left || margins.marginStart != box.left) {
                margins.leftMargin = box.left
                margins.marginStart = box.left
                changed = true
            }
            if (margins.topMargin != box.top) {
                margins.topMargin = box.top
                changed = true
            }
            if (params is FrameLayout.LayoutParams) {
                val gravity = Gravity.START or Gravity.TOP
                if (params.gravity != gravity) {
                    params.gravity = gravity
                    changed = true
                }
            }
        }
        if (view.scaleType != ImageView.ScaleType.FIT_XY) {
            if (!originalScaleType.containsKey(view)) originalScaleType[view] = view.scaleType
            view.scaleType = ImageView.ScaleType.FIT_XY
            changed = true
        }
        if (changed) view.requestLayout()
    }

    /** The QS/keyguard containers default to clipping; the measured end-side ones do not. */
    private fun unclip(view: View) {
        val group = view as? ViewGroup ?: return
        val params = group.layoutParams ?: return
        capture(group, params)
        if (group.clipChildren || group.clipToPadding) {
            group.clipChildren = false
            group.clipToPadding = false
        }
    }

    /** Sizes [container] to max(stock, visible children) so no slot is clipped by an old box. */
    private fun fitContainer(container: ViewGroup) {
        var width = 0
        var height = 0
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) ?: continue
            if (child.visibility == View.GONE) continue
            val params = child.layoutParams ?: continue
            val w = if (params.width > 0) params.width else child.width
            val h = if (params.height > 0) params.height else child.height
            if (w > 0) width += w
            if (h > height) height = h
        }
        if (width <= 0 || height <= 0) return
        val stock = originals[container]
        val params = container.layoutParams
        val minWidth = stock?.width?.takeIf { it > 0 }
            ?: params?.width?.takeIf { it > 0 }
            ?: container.width
        val minHeight = stock?.height?.takeIf { it > 0 }
            ?: params?.height?.takeIf { it > 0 }
            ?: container.height
        applyBox(container, max(minWidth, width), max(minHeight, height))
    }

    /** Sizes the row above a slot container (e.g. `system_icons`) to its visible children. */
    private fun fitRow(container: ViewGroup) {
        val row = container.parent as? ViewGroup ?: return
        if (row.childCount <= 0) return
        fitContainer(row)
    }

    private fun begin(): Boolean {
        if (applying) return false
        applying = true
        return true
    }

    // ---------------------------------------------------------------------------------- restore

    /**
     * iOS off: every stock LayoutParams, clip flag and scale type returns to the exact captured value.
     * A view whose params instance the OEM replaced is left alone (its own values win).
     */
    @Synchronized
    fun restoreAll() {
        val slotList = try {
            synchronized(slots) { slots.values.toList() }
        } catch (_: Throwable) {
            emptyList()
        }
        for (slot in slotList) restoreSlot(slot)
        slots.clear()
        restoreCaptured()
        applying = false
    }

    private fun restoreSlot(slot: Slot) {
        restoreView(slot.outer.get())
        restoreView(slot.group?.get())
        restoreView(slot.combo?.get())
        restoreView(slot.leaf.get())
        restoreView(slot.label?.get())
    }

    private fun restoreView(view: View?) {
        val target = view ?: return
        val original = originals.remove(target)
        if (original == null) {
            originalScaleType.remove(target)
            return
        }
        if (target.layoutParams === original.params) {
            original.params.width = original.width
            original.params.height = original.height
            val margins = original.params as? ViewGroup.MarginLayoutParams
            if (margins != null) {
                margins.leftMargin = original.leftMargin
                margins.topMargin = original.topMargin
                margins.rightMargin = original.rightMargin
                margins.bottomMargin = original.bottomMargin
                margins.marginStart = original.marginStart
                margins.marginEnd = original.marginEnd
            }
            if (original.params is FrameLayout.LayoutParams) {
                original.params.gravity = original.gravity
            }
            target.requestLayout()
        }
        val group = target as? ViewGroup
        if (group != null) {
            val clipChildren = original.clipChildren
            val clipToPadding = original.clipToPadding
            if (clipChildren != null && group.clipChildren != clipChildren) {
                group.clipChildren = clipChildren
            }
            if (clipToPadding != null && group.clipToPadding != clipToPadding) {
                group.clipToPadding = clipToPadding
            }
        }
        originalScaleType.remove(target)?.let { scaleType ->
            val image = target as? ImageView
            if (image != null && image.scaleType != scaleType) image.scaleType = scaleType
        }
    }

    private fun restoreCaptured() {
        val views = try {
            synchronized(originals) { originals.keys.toList() }
        } catch (_: Throwable) {
            emptyList()
        }
        for (view in views) restoreView(view)
        originals.clear()
        originalScaleType.clear()
    }

    // ---------------------------------------------------------------------------------- helpers

    private fun capture(view: View, params: ViewGroup.LayoutParams) {
        if (originals.containsKey(view)) return
        val margins = params as? ViewGroup.MarginLayoutParams
        originals[view] = Original(
            params = params,
            width = params.width,
            height = params.height,
            leftMargin = margins?.leftMargin ?: 0,
            topMargin = margins?.topMargin ?: 0,
            rightMargin = margins?.rightMargin ?: 0,
            bottomMargin = margins?.bottomMargin ?: 0,
            marginStart = margins?.marginStart ?: 0,
            marginEnd = margins?.marginEnd ?: 0,
            gravity = (params as? FrameLayout.LayoutParams)?.gravity ?: 0,
            clipChildren = (view as? ViewGroup)?.clipChildren,
            clipToPadding = (view as? ViewGroup)?.clipToPadding
        )
    }

    private fun glyphOf(view: ImageView, kind: IosIconKind): IosGlyphDrawable? {
        val glyph = view.drawable as? IosGlyphDrawable ?: return null
        return if (glyph.kind == kind) glyph else null
    }

    private fun unresolved(what: String): Boolean {
        logOnce.once("ios-slot-unresolved-$what") {
            L.w("$TAG $what leaf is not our glyph; stock layout kept")
        }
        return false
    }

    private fun kindOf(kind: Kind): IosIconKind = when (kind) {
        Kind.WIFI -> IosIconKind.WIFI
        Kind.MOBILE -> IosIconKind.CELL
        Kind.BATTERY -> IosIconKind.BATTERY
    }

    private fun screenWidth(view: View): Int {
        screenWidthOverrideForTest?.let { return it }
        return try {
            view.resources.displayMetrics.widthPixels
        } catch (_: Throwable) {
            1272
        }
    }

    private fun scalePercent(settings: ModuleSettings?, kind: IosIconKind): Int {
        val value = when (kind) {
            IosIconKind.BATTERY -> settings?.iosBatteryScale
            IosIconKind.WIFI -> settings?.iosWifiScale
            IosIconKind.CELL -> settings?.iosCellScale
        } ?: DuoPrefs.DEFAULT_ICON_SCALE
        return value.coerceIn(DuoPrefs.MIN_ICON_SCALE, DuoPrefs.MAX_ICON_SCALE)
    }

    private fun offsetPercent(settings: ModuleSettings?, kind: IosIconKind): Int {
        val value = when (kind) {
            IosIconKind.BATTERY -> settings?.iosBatteryOffset
            IosIconKind.WIFI -> settings?.iosWifiOffset
            IosIconKind.CELL -> settings?.iosCellOffset
        } ?: 0
        return value.coerceIn(DuoPrefs.MIN_ICON_OFFSET, DuoPrefs.MAX_ICON_OFFSET)
    }

    /**
     * The stock row height for [leaf]. An existing slot keeps the value captured before our first
     * write; re-registering must never read back the size this layout already installed.
     */
    private fun rowHeightFor(leaf: ImageView, views: List<View>): Int {
        val captured = slots[leaf]?.stockRowHeight ?: 0
        return if (captured > 0) captured else stockRowHeight(views)
    }

    /** The stock row height before our first write: LP height, else the already-measured px. */
    private fun stockRowHeight(views: List<View>): Int {        var height = 0
        for (view in views) {
            val params = view.layoutParams
            val candidate = when {
                params != null && params.height > 0 -> params.height
                view.height > 0 -> view.height
                view.measuredHeight > 0 -> view.measuredHeight
                else -> 0
            }
            if (candidate > height) height = candidate
        }
        return height
    }

    // ----------------------------------------------------------------------------------- test seams

    /** Test seam: reset every registry without writing any view. */
    @Synchronized
    internal fun resetForTest() {
        slots.clear()
        originals.clear()
        originalScaleType.clear()
        currentSettings = null
        screenWidthOverrideForTest = null
        applying = false
    }

    /** Test seam: number of registered slots. */
    internal val registeredForTest: Int get() = synchronized(slots) { slots.size }
}
