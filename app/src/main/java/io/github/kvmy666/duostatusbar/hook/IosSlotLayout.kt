package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter
import io.github.kvmy666.duostatusbar.hook.rom.RomResources
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

    /**
     * R9 design row height: 63 px at the owner's 1272 px width. Every slot kind uses this one row,
     * never a live stock view/LayoutParams height (a slot's own write used to be re-read as 94/63/51/
     * 47, so the outers disagreed about the row).
     */
    const val ROW_HEIGHT_PER_SCREEN_WIDTH = 63f / 1272f

    /**
     * R11 size owner: every icon ink height is `1.08 x` the host bar's clock cap (the measured iPhone
     * dark reference is `41 / 38 = 1.079`). The clock cap is read from the live `TextView`, so our
     * icons follow the owner's own bar instead of the iPhone screen width (the R9 "huge" bug).
     */
    internal const val HOST_INK_PER_CLOCK_CAP = 1.08f

    /** R11 equal ink gap: `0.50 x` the icon ink height (the requested ratio; see the docs caveat). */
    internal const val HOST_GAP_PER_INK_HEIGHT = 0.50f

    /** R11 Wi-Fi ink height: `0.98 x` the battery ink (the cell dual block = battery exactly). */
    internal const val WIFI_INK_TO_BASE_INK = 0.98f

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

    /**
     * Test seam: when set, the host path uses this target ink (px) instead of reading the clock view.
     * Cleared by [resetForTest]; production keeps it null.
     */
    @Volatile
    internal var hostInkOverrideForTest: Float? = null

    /** The clock id resolved by [configure]; 0 means no host clock and the legacy path. */
    @Volatile
    private var clockResId: Int = 0

    /** The live host clock, re-resolved when it is detached or its text size changes. */
    @Volatile
    private var clockRef: WeakReference<TextView>? = null

    @Volatile
    private var clockTextSize: Float = Float.NaN

    private enum class Kind { WIFI, MOBILE, BATTERY }

    /** Weak references only: the bar, keyguard and QS may drop any of these views at any time. */
    private class Slot(
        val kind: Kind,
        val outer: WeakReference<View>,
        val group: WeakReference<View>?,
        val combo: WeakReference<View>?,
        val leaf: WeakReference<ImageView>,
        val label: WeakReference<ImageView>?
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

    /**
     * Target `[width, height]` this module installed on a view. [fitContainer] sums these instead of
     * reading a live child width, so MATCH_PARENT / weighted siblings (which have no intrinsic width)
     * can never grow the container on a repeated apply.
     */
    private val targets: MutableMap<View, IntArray> =
        Collections.synchronizedMap(WeakHashMap<View, IntArray>())

    /** Monotonic re-entry guard: a layout request from inside our write must not start a second pass. */
    private var applying = false

    // ----------------------------------------------------------------------------------- metrics

    /** The design unit `s` for [screenW] (px per design unit). */
    fun unit(screenW: Int): Float = screenW.coerceAtLeast(1) * UNIT_PER_SCREEN_WIDTH / 100f

    /** The equal slot gap in px. */
    fun gap(screenW: Int): Float = screenW.coerceAtLeast(1) * GAP_PER_SCREEN_WIDTH

    /** The one R9 row height for [screenW] in whole px (63 at 1272), never a live stock height. */
    fun rowHeight(screenW: Int): Int =
        (screenW.coerceAtLeast(1) * ROW_HEIGHT_PER_SCREEN_WIDTH).roundToInt().coerceAtLeast(1)

    /**
     * One icon's real slot. The outer is `ink + gap` wide and at least the design row high; the leaf
     * is the artboard box, positioned so the ink starts at `gap / 2` inside the outer. The user
     * offset is clamped inside the slot, never past a neighbour.
     *
     * The leaf is artboard-sized, and every artboard has a 1-unit margin, so the artboard centre is
     * the ink centre within 1 px. Above ~142% the artboard is taller than the 63 px row, so
     * [outerHeight] follows R9's `max(63, artboard * scale)` and the stock parent (which centres its
     * children vertically) keeps every leaf on the one row centre line.
     *
     * [stockRowHeight] is the R9 design row ([rowHeight]), passed as the same value for every kind;
     * it is kept as a parameter so the pure rule stays unit-testable at any screen width.
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
        val leafH = artHpx.roundToInt().coerceAtLeast(1)
        val outerW = (inkW + gapPx).roundToInt().coerceAtLeast(1)
        val outerH = max(stockRowHeight.coerceAtLeast(0), leafH).coerceAtLeast(1)
        val dx = (offsetPercent.coerceIn(DuoPrefs.MIN_ICON_OFFSET, DuoPrefs.MAX_ICON_OFFSET) /
            100f * inkW).coerceIn(-gapPx / 2f, gapPx / 2f)
        return IosSlotMetrics(
            outerWidth = outerW,
            outerHeight = outerH,
            leaf = IosSlotBox(
                width = artWpx.roundToInt().coerceAtLeast(1),
                height = leafH,
                left = (gapPx / 2f - ink.left * unitPx * k + dx).roundToInt().coerceAtLeast(0),
                top = ((outerH - leafH) / 2f).roundToInt().coerceAtLeast(0)
            )
        )
    }

    /**
     * The label-mode mobile slot: the outer holds the cell then the data-type label with one [gap]
     * between their inks. The label uses the Wi-Fi slot's scale/offset because it replaces that slot.
     * [stockRowHeight] is the one R9 design row, exactly as for [metrics].
     *
     * Both leaves are artboard boxes (1-unit margin, so artboard-centre == ink-centre within 1 px)
     * and each is centred in [IosSlotMetrics.outerHeight] with `top = round((outerH - leafH) / 2)`.
     * A cell above ~142% therefore makes the outer taller than the row, and the stock parent's
     * vertical centring keeps the cell and the label on the same centre line as the battery.
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
        val cellLeafH = cellArtHpx.roundToInt().coerceAtLeast(1)
        val labelLeafH = labelArtHpx.roundToInt().coerceAtLeast(1)
        val outerW = (cellInkW + gapPx + labelInkW + gapPx).roundToInt().coerceAtLeast(1)
        val outerH = max(
            max(stockRowHeight.coerceAtLeast(0), cellLeafH),
            labelLeafH
        ).coerceAtLeast(1)
        return IosSlotMetrics(
            outerWidth = outerW,
            outerHeight = outerH,
            leaf = IosSlotBox(
                width = (cellArtW * unitPx * cellK).roundToInt().coerceAtLeast(1),
                height = cellLeafH,
                left = (gapPx / 2f - cellInk.left * unitPx * cellK + cellDx).roundToInt().coerceAtLeast(0),
                top = ((outerH - cellLeafH) / 2f).roundToInt().coerceAtLeast(0)
            ),
            label = IosSlotBox(
                width = (labelArtW * unitPx * labelK).roundToInt().coerceAtLeast(1),
                height = labelLeafH,
                left = (gapPx / 2f + cellInkW + gapPx - labelInk.left * unitPx * labelK + labelDx)
                    .roundToInt().coerceAtLeast(0),
                top = ((outerH - labelLeafH) / 2f).roundToInt().coerceAtLeast(0)
            )
        )
    }

    // ------------------------------------------------------------------------- host-ink metrics

    /**
     * R11 one icon sized from the host clock: [baseInkPx] is the target battery-body ink height, and
     * every kind derives its px-per-artboard-unit from it:
     *
     * - battery `baseInkPx / ink.height`;
     * - cell `baseInkPx / 92.9` (single) or `/ 103.3` (dual stacked block);
     * - Wi-Fi `0.98 * baseInkPx / ink.height`; a data-type label `baseInkPx / 100` (cap 0.714 x base).
     *
     * The ink gap is [gapPx] for every ink and is NOT multiplied by the user scale. The leaf is the
     * artboard x base x user scale, and its top puts the ink centre on the outer centre line:
     * `round(outerH / 2 - (ink.top + ink.height / 2) * base * scale)`. This is the new path only;
     * [metrics] keeps the legacy R9 top rule as the fallback.
     */
    fun metricsAtInk(
        kind: IosIconKind,
        visual: IosVisual,
        baseInkPx: Float,
        gapPx: Float,
        scalePercent: Int,
        offsetPercent: Int,
        stockRowHeight: Int
    ): IosSlotMetrics {
        val k = scalePercent.coerceIn(DuoPrefs.MIN_ICON_SCALE, DuoPrefs.MAX_ICON_SCALE) / 100f
        val ink = IosGeometry.inkBox(kind, visual)
        val base = basePxPerUnit(kind, visual, baseInkPx)
        val artW = IosGlyphRenderer.artboardWidth(kind, visual)
        val artH = IosGlyphRenderer.artboardHeight(kind, visual)
        val inkW = ink.width * base * k
        val leafH = (artH * base * k).roundToInt().coerceAtLeast(1)
        val outerW = (inkW + gapPx).roundToInt().coerceAtLeast(1)
        val outerH = max(stockRowHeight.coerceAtLeast(0), leafH).coerceAtLeast(1)
        val dx = (offsetPercent.coerceIn(DuoPrefs.MIN_ICON_OFFSET, DuoPrefs.MAX_ICON_OFFSET) /
            100f * inkW).coerceIn(-gapPx / 2f, gapPx / 2f)
        return IosSlotMetrics(
            outerWidth = outerW,
            outerHeight = outerH,
            leaf = IosSlotBox(
                width = (artW * base * k).roundToInt().coerceAtLeast(1),
                height = leafH,
                left = (gapPx / 2f - ink.left * base * k + dx).roundToInt().coerceAtLeast(0),
                top = (outerH / 2f - (ink.top + ink.height / 2f) * base * k)
                    .roundToInt().coerceAtLeast(0)
            )
        )
    }

    /**
     * R11 label-mode mobile slot sized from the host clock. The cell uses the cell denominator and
     * the label the data-label one; both leaves are centred on the one row centre line (the 5G label
     * included), and the ink gap between cell, label and the next slot is the same [gapPx].
     */
    fun mobileMetricsAtInk(
        cellVisual: IosVisual,
        labelVisual: IosVisual?,
        baseInkPx: Float,
        gapPx: Float,
        cellScalePercent: Int,
        cellOffsetPercent: Int,
        labelScalePercent: Int,
        labelOffsetPercent: Int,
        stockRowHeight: Int
    ): IosSlotMetrics {
        if (labelVisual == null) {
            return metricsAtInk(
                IosIconKind.CELL,
                cellVisual,
                baseInkPx,
                gapPx,
                cellScalePercent,
                cellOffsetPercent,
                stockRowHeight
            )
        }
        val cellK = cellScalePercent.coerceIn(DuoPrefs.MIN_ICON_SCALE, DuoPrefs.MAX_ICON_SCALE) / 100f
        val labelK = labelScalePercent.coerceIn(DuoPrefs.MIN_ICON_SCALE, DuoPrefs.MAX_ICON_SCALE) / 100f
        val cellInk = IosGeometry.inkBox(IosIconKind.CELL, cellVisual)
        val labelInk = IosGeometry.inkBox(IosIconKind.WIFI, labelVisual)
        val cellBase = basePxPerUnit(IosIconKind.CELL, cellVisual, baseInkPx)
        val labelBase = basePxPerUnit(IosIconKind.WIFI, labelVisual, baseInkPx)
        val cellInkW = cellInk.width * cellBase * cellK
        val labelInkW = labelInk.width * labelBase * labelK
        val cellDx = (cellOffsetPercent.coerceIn(DuoPrefs.MIN_ICON_OFFSET, DuoPrefs.MAX_ICON_OFFSET) /
            100f * cellInkW).coerceIn(-gapPx / 2f, gapPx / 2f)
        val labelDx = (labelOffsetPercent.coerceIn(DuoPrefs.MIN_ICON_OFFSET, DuoPrefs.MAX_ICON_OFFSET) /
            100f * labelInkW).coerceIn(-gapPx / 2f, gapPx / 2f)
        val cellLeafH = (IosGlyphRenderer.artboardHeight(IosIconKind.CELL, cellVisual) * cellBase * cellK)
            .roundToInt().coerceAtLeast(1)
        val labelLeafH = (IosGlyphRenderer.artboardHeight(IosIconKind.WIFI, labelVisual) * labelBase * labelK)
            .roundToInt().coerceAtLeast(1)
        val outerW = (cellInkW + gapPx + labelInkW + gapPx).roundToInt().coerceAtLeast(1)
        val outerH = max(max(stockRowHeight.coerceAtLeast(0), cellLeafH), labelLeafH)
            .coerceAtLeast(1)
        return IosSlotMetrics(
            outerWidth = outerW,
            outerHeight = outerH,
            leaf = IosSlotBox(
                width = (IosGlyphRenderer.artboardWidth(IosIconKind.CELL, cellVisual) * cellBase * cellK)
                    .roundToInt().coerceAtLeast(1),
                height = cellLeafH,
                left = (gapPx / 2f - cellInk.left * cellBase * cellK + cellDx)
                    .roundToInt().coerceAtLeast(0),
                top = (outerH / 2f - (cellInk.top + cellInk.height / 2f) * cellBase * cellK)
                    .roundToInt().coerceAtLeast(0)
            ),
            label = IosSlotBox(
                width = (IosGlyphRenderer.artboardWidth(IosIconKind.WIFI, labelVisual) * labelBase * labelK)
                    .roundToInt().coerceAtLeast(1),
                height = labelLeafH,
                left = (gapPx / 2f + cellInkW + gapPx - labelInk.left * labelBase * labelK + labelDx)
                    .roundToInt().coerceAtLeast(0),
                top = (outerH / 2f - (labelInk.top + labelInk.height / 2f) * labelBase * labelK)
                    .roundToInt().coerceAtLeast(0)
            )
        )
    }

    /** R11 px per artboard unit for [kind], derived from the target battery-body ink [baseInkPx]. */
    private fun basePxPerUnit(kind: IosIconKind, visual: IosVisual, baseInkPx: Float): Float {
        val ink = IosGeometry.inkBox(kind, visual)
        val base = when (kind) {
            IosIconKind.BATTERY -> baseInkPx / ink.height
            IosIconKind.CELL -> baseInkPx / if (visual.cellMode == IosCellMode.SINGLE) {
                IosGeometry.Cell.SINGLE_BAR_BOTTOM - IosGeometry.Cell.TOP
            } else {
                IosGeometry.Cell.DUAL_BLOCK_H
            }
            IosIconKind.WIFI -> if (visual.wifiSlot is IosWifiSlot.DataType) {
                baseInkPx / IosGeometry.Battery.BODY_H
            } else {
                WIFI_INK_TO_BASE_INK * baseInkPx / ink.height
            }
        }
        return base.takeIf { it.isFinite() && it > 0f } ?: (baseInkPx / IosGeometry.Battery.BODY_H)
    }

    // ------------------------------------------------------------------------------ host clock

    /**
     * R11 host clock: [clockId] is already resolved by the caller through [RomResources.id] (one
     * lookup route, AGENTS m1); [systemUiPackage] is only used for the warning. The clock itself is
     * looked up lazily from each leaf's root view, so one configure covers home, keyguard and QS.
     */
    @Synchronized
    internal fun configure(clockId: Int, systemUiPackage: String = "") {
        clockRef = null
        clockTextSize = Float.NaN
        clockResId = clockId
        if (clockId == 0) {
            logOnce.once("ios-host-clock-id") {
                L.w("$TAG host clock id not resolved in $systemUiPackage; screen-width fallback")
            }
        }
    }

    /** Test-only convenience: resolve [clockIdName] in [systemUiPackage], then configure it. */
    @Synchronized
    internal fun configure(context: Context, systemUiPackage: String, clockIdName: String) {
        configure(
            RomResources.id(
                context,
                RomAdapter(
                    id = "test",
                    label = "test",
                    systemUiPackage = systemUiPackage,
                    containerIds = emptyList(),
                    batteryId = "",
                    notes = "test-only clock id lookup"
                ),
                clockIdName
            ),
            systemUiPackage
        )
    }

    /**
     * The host target ink in px (`1.08 x` the clock digit cap), or null when the clock view or its
     * paint is unreadable (the caller then keeps the legacy screen-width metrics). Cached and
     * re-resolved when the clock detaches or its text size changes.
     */
    internal fun hostInkPx(leaf: View): Float? {
        val clock = clockView(leaf)
        val cap = clock?.let { clockCapPx(it) }?.takeIf { it > 0f }
        if (cap == null) {
            logOnce.once("ios-host-clock-fallback") {
                L.w("$TAG host clock unreadable; screen-width fallback")
            }
            return null
        }
        val ink = HOST_INK_PER_CLOCK_CAP * cap
        logOnce.once("ios-host-clock") {
            L.i("$TAG host clock cap=${cap.roundToInt()}px target ink=${ink.roundToInt()}px")
        }
        return ink
    }

    private fun clockView(leaf: View): TextView? {
        val cached = clockRef?.get()
        if (cached != null && cached.isAttachedToWindow && cached.textSize == clockTextSize) {
            return cached
        }
        val id = clockResId
        if (id == 0) return null
        val found = try {
            leaf.rootView.findViewById<View>(id) as? TextView
        } catch (_: Throwable) {
            null
        }
        if (found != null) {
            clockRef = WeakReference(found)
            clockTextSize = found.textSize
        }
        return found
    }

    /** The digit cap in px from the clock's own paint ("0", then "H"); 0 when unreadable. */
    private fun clockCapPx(clock: TextView): Float {
        val paint = clock.paint ?: return 0f
        val bounds = Rect()
        return try {
            paint.getTextBounds("0", 0, 1, bounds)
            if (bounds.height() > 0) {
                bounds.height().toFloat()
            } else {
                paint.getTextBounds("H", 0, 1, bounds)
                if (bounds.height() > 0) bounds.height().toFloat() else 0f
            }
        } catch (_: Throwable) {
            0f
        }
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
            null
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
            type?.let { WeakReference(it) }
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
            null
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
        val label = slot.label?.get()
        val labelVisual = if (slot.kind == Kind.MOBILE) {
            val labelGlyph = label?.drawable as? IosGlyphDrawable
            labelGlyph
                ?.takeIf { it.kind == IosIconKind.WIFI && label.visibility != View.GONE }
                ?.visualForTest
                ?.takeIf { it.wifiSlot is IosWifiSlot.DataType }
        } else {
            null
        }
        val screenW = screenWidth(leaf)
        val designRowHeight = rowHeight(screenW)
        // R11: the host clock owns the size; a null read keeps the legacy screen-width path exactly.
        val hostInk = (hostInkOverrideForTest ?: hostInkPx(leaf))
            ?.takeIf { it.isFinite() && it > 0f }
        val hostGap = hostInk?.times(HOST_GAP_PER_INK_HEIGHT)
        val slotMetrics = if (hostInk != null && hostGap != null) {
            when (slot.kind) {
                Kind.WIFI -> metricsAtInk(
                    IosIconKind.WIFI,
                    visual,
                    hostInk,
                    hostGap,
                    scalePercent(settings, IosIconKind.WIFI),
                    offsetPercent(settings, IosIconKind.WIFI),
                    designRowHeight
                )
                Kind.MOBILE -> mobileMetricsAtInk(
                    visual,
                    labelVisual,
                    hostInk,
                    hostGap,
                    scalePercent(settings, IosIconKind.CELL),
                    offsetPercent(settings, IosIconKind.CELL),
                    scalePercent(settings, IosIconKind.WIFI),
                    offsetPercent(settings, IosIconKind.WIFI),
                    designRowHeight
                )
                Kind.BATTERY -> metricsAtInk(
                    IosIconKind.BATTERY,
                    visual,
                    hostInk,
                    hostGap,
                    scalePercent(settings, IosIconKind.BATTERY),
                    offsetPercent(settings, IosIconKind.BATTERY),
                    designRowHeight
                )
            }
        } else {
            when (slot.kind) {
                Kind.WIFI -> metrics(
                    IosIconKind.WIFI,
                    visual,
                    screenW,
                    scalePercent(settings, IosIconKind.WIFI),
                    offsetPercent(settings, IosIconKind.WIFI),
                    designRowHeight
                )
                Kind.MOBILE -> mobileMetrics(
                    visual,
                    labelVisual,
                    screenW,
                    scalePercent(settings, IosIconKind.CELL),
                    offsetPercent(settings, IosIconKind.CELL),
                    scalePercent(settings, IosIconKind.WIFI),
                    offsetPercent(settings, IosIconKind.WIFI),
                    designRowHeight
                )
                Kind.BATTERY -> metrics(
                    IosIconKind.BATTERY,
                    visual,
                    screenW,
                    scalePercent(settings, IosIconKind.BATTERY),
                    offsetPercent(settings, IosIconKind.BATTERY),
                    designRowHeight
                )
            }
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

    /** One write per view, only when a value differs; the target is recorded for [fitContainer]. */
    private fun applyBox(view: View, width: Int, height: Int) {
        val params = view.layoutParams ?: return
        capture(view, params)
        targets[view] = intArrayOf(width, height)
        if (params.width == width && params.height == height) return
        params.width = width
        params.height = height
        view.requestLayout()
    }

    /**
     * Sizes the artboard leaf and positions its ink at the slot's left margin. `FIT_XY` is required:
     * the stock `wifi_signal` is `scaleType=center` and the other leaves default to `FIT_CENTER`, both
     * of which would re-scale/draw the glyph inside the new artboard-sized leaf.
     *
     * R11: the measured battery parent (`StatBatteryMeterView`, a `LinearLayout` with
     * `gravity=start|center`) contributes a +7/+9 padding of its own, so the leaf margins subtract the
     * immediate parent's `paddingLeft`/`paddingTop` and the gravity is pinned to `START|TOP`. The leaf
     * then lands at the computed top on every parent, including the padded battery one.
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
            val parent = view.parent as? View
            val left = box.left - (parent?.paddingLeft ?: 0)
            val top = box.top - (parent?.paddingTop ?: 0)
            if (margins.leftMargin != left || margins.marginStart != left) {
                margins.leftMargin = left
                margins.marginStart = left
                changed = true
            }
            if (margins.topMargin != top) {
                margins.topMargin = top
                changed = true
            }
            val gravity = Gravity.START or Gravity.TOP
            when (params) {
                is FrameLayout.LayoutParams -> if (params.gravity != gravity) {
                    params.gravity = gravity
                    changed = true
                }
                is LinearLayout.LayoutParams -> if (params.gravity != gravity) {
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

    /**
     * Sizes [container] to max(stock, visible children) so no slot is clipped by an old box.
     *
     * A child contributes its recorded target, else only a positive fixed `params.width`/`height`.
     * `MATCH_PARENT` (-1), weighted (`0` + weight), and not-yet-laid-out children contribute nothing:
     * reading a live `child.width` here made the container grow on every reassert once such a child
     * had been measured. With immutable inputs a second apply writes the same width/height.
     */
    private fun fitContainer(container: ViewGroup) {
        var width = 0
        var height = 0
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) ?: continue
            if (child.visibility == View.GONE) continue
            val target = targets[child]
            val params = child.layoutParams
            val w = target?.get(0) ?: params?.width?.takeIf { it > 0 } ?: 0
            val h = target?.get(1) ?: params?.height?.takeIf { it > 0 } ?: 0
            if (w > 0) width += w
            if (h > height) height = h
        }
        if (width <= 0 || height <= 0) return
        val stock = originals[container]
        val params = container.layoutParams
        val minWidth = stock?.width?.takeIf { it > 0 } ?: params?.width?.takeIf { it > 0 } ?: 0
        val minHeight = stock?.height?.takeIf { it > 0 } ?: params?.height?.takeIf { it > 0 } ?: 0
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
     * An OEM may replace the params instance while we run, so the captured values are written to the
     * CURRENT instance (and to the original one when it is a different object), never dropped.
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
        targets.clear()
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
        targets.remove(target)
        val original = originals.remove(target)
        if (original == null) {
            originalScaleType.remove(target)
            return
        }
        // The OEM may have swapped the params instance; restore the current one, and keep the
        // captured instance stock too in case it is reused.
        target.layoutParams?.let { applyOriginal(it, original) }
        if (original.params !== target.layoutParams) applyOriginal(original.params, original)
        target.requestLayout()
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

    /** Writes the captured stock size/margins/gravity onto [params]. */
    private fun applyOriginal(params: ViewGroup.LayoutParams, original: Original) {
        params.width = original.width
        params.height = original.height
        val margins = params as? ViewGroup.MarginLayoutParams
        if (margins != null) {
            margins.leftMargin = original.leftMargin
            margins.topMargin = original.topMargin
            margins.rightMargin = original.rightMargin
            margins.bottomMargin = original.bottomMargin
            margins.marginStart = original.marginStart
            margins.marginEnd = original.marginEnd
        }
        when (params) {
            is FrameLayout.LayoutParams -> params.gravity = original.gravity
            is LinearLayout.LayoutParams -> params.gravity = original.gravity
        }
    }

    private fun gravityOf(params: ViewGroup.LayoutParams): Int =
        (params as? FrameLayout.LayoutParams)?.gravity
            ?: (params as? LinearLayout.LayoutParams)?.gravity
            ?: 0

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
            gravity = gravityOf(params),
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

    // ----------------------------------------------------------------------------------- test seams

    /** Test seam: reset every registry without writing any view. */
    @Synchronized
    internal fun resetForTest() {
        slots.clear()
        originals.clear()
        originalScaleType.clear()
        targets.clear()
        currentSettings = null
        screenWidthOverrideForTest = null
        hostInkOverrideForTest = null
        clockResId = 0
        clockRef = null
        clockTextSize = Float.NaN
        applying = false
    }

    /** Test seam: number of registered slots. */
    internal val registeredForTest: Int get() = synchronized(slots) { slots.size }

    /** Test seam: the target size recorded for [view], or null when nothing was applied to it. */
    internal fun targetForTest(view: View): IntArray? =
        synchronized(targets) { targets[view]?.copyOf() }
}
