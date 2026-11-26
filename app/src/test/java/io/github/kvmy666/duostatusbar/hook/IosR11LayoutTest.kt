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
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * R11 step 1 + 2: the icon ink height follows the host clock cap (target ink = 1.08 x cap), the equal
 * ink gap is 0.50 x that ink, and every ink (including the 5G label) sits on one row centre line.
 *
 * Evidence: `.pm/device/r11/last_dump.txt` (clock 113x94, system_icons 415x63, battery leaf +17/+18
 * from its container) and `.pm/out-r11/measurements.md` (now.png clock cap 31, iPhone 38/41).
 */
class IosR11LayoutTest {

    private companion object {
        const val BASE_INK = 33.48f
        const val GAP = 16.74f
        const val ROW = 63
        const val ROW_CENTRE = ROW / 2f
    }

    private val cellBoth = IosVisual(
        cellMode = IosCellMode.DUAL,
        cellRole = IosCellRole.BOTH,
        cellBars = 4f,
        cellDots = 1f
    )
    private val cellSingle = IosVisual(cellMode = IosCellMode.SINGLE, cellBars = 4f)
    private val wifi = IosVisual(wifiLevel = 3f)
    private val label5g = IosVisual(wifiSlot = IosWifiSlot.DataType("5G"))
    private val battery = IosVisual(batteryLevel = 1f)

    /** R11 px per artboard unit for [kind], independent of the production helper. */
    private fun pxPerUnit(kind: IosIconKind, visual: IosVisual): Float {
        val ink = IosGeometry.inkBox(kind, visual)
        return when (kind) {
            IosIconKind.BATTERY -> BASE_INK / ink.height
            IosIconKind.CELL -> BASE_INK / if (visual.cellMode == IosCellMode.SINGLE) {
                IosGeometry.Cell.SINGLE_BAR_BOTTOM - IosGeometry.Cell.TOP
            } else {
                IosGeometry.Cell.DUAL_BLOCK_H
            }
            IosIconKind.WIFI -> if (visual.wifiSlot is IosWifiSlot.DataType) {
                BASE_INK / IosGeometry.Battery.BODY_H
            } else {
                IosSlotLayout.WIFI_INK_TO_BASE_INK * BASE_INK / ink.height
            }
        }
    }

    private fun inkCentre(
        box: IosSlotBox,
        kind: IosIconKind,
        visual: IosVisual,
        scalePercent: Int = 100
    ): Float {
        val ink = IosGeometry.inkBox(kind, visual)
        return box.top + (ink.top + ink.height / 2f) * pxPerUnit(kind, visual) * scalePercent / 100f
    }

    private fun inkLeft(box: IosSlotBox, kind: IosIconKind, visual: IosVisual, scale: Int = 100): Float =
        box.left + IosGeometry.inkBox(kind, visual).left * pxPerUnit(kind, visual) * scale / 100f

    private fun inkRight(box: IosSlotBox, kind: IosIconKind, visual: IosVisual, scale: Int = 100): Float =
        inkLeft(box, kind, visual, scale) +
            IosGeometry.inkBox(kind, visual).width * pxPerUnit(kind, visual) * scale / 100f

    @Test
    fun `battery ink is 1_08 x the clock cap and sits on the row centre`() {
        val slot = IosSlotLayout.metricsAtInk(
            IosIconKind.BATTERY, battery, BASE_INK, GAP, 100, 0, ROW
        )
        assertEquals(86, slot.outerWidth)
        assertEquals(63, slot.outerHeight)
        assertEquals(70, slot.leaf.width)
        assertEquals(34, slot.leaf.height)

        val ink = IosGeometry.inkBox(IosIconKind.BATTERY, battery)
        assertEquals(BASE_INK, ink.height * pxPerUnit(IosIconKind.BATTERY, battery), 0.01f)
        assertEquals(ROW_CENTRE, inkCentre(slot.leaf, IosIconKind.BATTERY, battery), 1f)
    }

    @Test
    fun `cell dual BOTH has the battery ink height and the centred stacked block`() {
        val slot = IosSlotLayout.metricsAtInk(
            IosIconKind.CELL, cellBoth, BASE_INK, GAP, 100, 0, ROW
        )
        assertEquals(65, slot.outerWidth)
        assertEquals(63, slot.outerHeight)
        assertEquals(49, slot.leaf.width)
        assertEquals(34, slot.leaf.height)

        val ink = IosGeometry.inkBox(IosIconKind.CELL, cellBoth)
        assertEquals(33.5f, ink.height * pxPerUnit(IosIconKind.CELL, cellBoth), 0.6f)
        assertEquals(ROW_CENTRE, inkCentre(slot.leaf, IosIconKind.CELL, cellBoth), 1f)
    }

    @Test
    fun `cell single is 92_9 units tall and keeps the battery ink height`() {
        val slot = IosSlotLayout.metricsAtInk(
            IosIconKind.CELL, cellSingle, BASE_INK, GAP, 100, 0, ROW
        )
        assertEquals(68, slot.outerWidth)
        assertEquals(63, slot.outerHeight)
        assertEquals(54, slot.leaf.width)
        assertEquals(38, slot.leaf.height)

        val ink = IosGeometry.inkBox(IosIconKind.CELL, cellSingle)
        assertEquals(BASE_INK, ink.height * pxPerUnit(IosIconKind.CELL, cellSingle), 0.6f)
        assertEquals(ROW_CENTRE, inkCentre(slot.leaf, IosIconKind.CELL, cellSingle), 1f)
    }

    @Test
    fun `wi-fi is 0_98 of the battery ink and the same centred row`() {
        val slot = IosSlotLayout.metricsAtInk(IosIconKind.WIFI, wifi, BASE_INK, GAP, 100, 0, ROW)
        assertTrue("wifi outer ${slot.outerWidth}", slot.outerWidth in 61..62)
        assertEquals(63, slot.outerHeight)
        assertEquals(45, slot.leaf.width)
        assertEquals(34, slot.leaf.height)

        val ink = IosGeometry.inkBox(IosIconKind.WIFI, wifi)
        assertEquals(32.8f, ink.height * pxPerUnit(IosIconKind.WIFI, wifi), 0.6f)
        assertEquals(ROW_CENTRE, inkCentre(slot.leaf, IosIconKind.WIFI, wifi), 1f)
    }

    @Test
    fun `5G label is 0_714 of the base ink and shares the centre line`() {
        val slot = IosSlotLayout.mobileMetricsAtInk(
            cellBoth, label5g, BASE_INK, GAP, 100, 0, 100, 0, ROW
        )
        assertTrue("mobile outer ${slot.outerWidth}", slot.outerWidth in 120..122)
        assertEquals(63, slot.outerHeight)
        assertEquals(49, slot.leaf.width)
        assertEquals(34, slot.leaf.height)

        val label = slot.label!!
        assertEquals(40, label.width)
        assertEquals(32, label.height)
        assertEquals(ROW_CENTRE, inkCentre(label, IosIconKind.WIFI, label5g), 1f)

        val labelInk = IosGeometry.inkBox(IosIconKind.WIFI, label5g)
        assertEquals(0.714f * BASE_INK, labelInk.height * pxPerUnit(IosIconKind.WIFI, label5g), 0.6f)
    }

    @Test
    fun `every ink gap is 0_50 x the icon ink`() {
        val cell = IosSlotLayout.metricsAtInk(IosIconKind.CELL, cellBoth, BASE_INK, GAP, 100, 0, ROW)
        val wifiSlot = IosSlotLayout.metricsAtInk(IosIconKind.WIFI, wifi, BASE_INK, GAP, 100, 0, ROW)
        val batterySlot = IosSlotLayout.metricsAtInk(IosIconKind.BATTERY, battery, BASE_INK, GAP, 100, 0, ROW)
        val mobile = IosSlotLayout.mobileMetricsAtInk(
            cellBoth, label5g, BASE_INK, GAP, 100, 0, 100, 0, ROW
        )

        // cell -> label inside the mobile outer.
        val labelGap = inkLeft(mobile.label!!, IosIconKind.WIFI, label5g) -
            inkRight(mobile.leaf, IosIconKind.CELL, cellBoth)
        assertEquals(GAP, labelGap, 1.5f)

        // label -> next slot (the mobile outer's trailing half gap + the next leading half gap).
        val nextAfterLabel = mobile.outerWidth + inkLeft(wifiSlot.leaf, IosIconKind.WIFI, wifi)
        assertEquals(GAP, nextAfterLabel - inkRight(mobile.label!!, IosIconKind.WIFI, label5g), 1.5f)

        // wifi -> battery.
        assertEquals(
            GAP,
            mobile.outerWidth + wifiSlot.outerWidth + inkLeft(batterySlot.leaf, IosIconKind.BATTERY, battery) -
                (mobile.outerWidth + inkRight(wifiSlot.leaf, IosIconKind.WIFI, wifi)),
            1.5f
        )

        // The raw cell/battery slot abutment is the same gap.
        assertEquals(GAP, cell.outerWidth + inkLeft(batterySlot.leaf, IosIconKind.BATTERY, battery) -
            inkRight(cell.leaf, IosIconKind.CELL, cellBoth), 1.5f)
    }

    @Test
    fun `user scale multiplies the glyph but never the gap`() {
        for (scale in listOf(70, 100, 150)) {
            val slot = IosSlotLayout.metricsAtInk(IosIconKind.BATTERY, battery, BASE_INK, GAP, scale, 0, ROW)
            val inkW = IosGeometry.inkBox(IosIconKind.BATTERY, battery).width *
                pxPerUnit(IosIconKind.BATTERY, battery) * scale / 100f
            assertEquals("scale=$scale outer", inkW + GAP, slot.outerWidth.toFloat(), 1f)
            assertEquals(
                "scale=$scale leaf",
                (IosGlyphRenderer.artboardWidth(IosIconKind.BATTERY, battery) *
                    pxPerUnit(IosIconKind.BATTERY, battery) * scale / 100f).roundToInt().toFloat(),
                slot.leaf.width.toFloat(),
                1f
            )
        }
        val at100 = IosSlotLayout.metricsAtInk(IosIconKind.BATTERY, battery, BASE_INK, GAP, 100, 0, ROW)
        val at150 = IosSlotLayout.metricsAtInk(IosIconKind.BATTERY, battery, BASE_INK, GAP, 150, 0, ROW)
        // (outer - gap) is the ink width, so it scales exactly; the gap itself does not.
        assertEquals(
            1.5f * (at100.outerWidth - GAP),
            at150.outerWidth - GAP,
            1.5f
        )
    }

    @Test
    fun `a user offset is clamped to half the host gap`() {
        for (scale in listOf(70, 100, 150)) {
            for (percent in listOf(-25, -10, 0, 10, 25)) {
                val slot = IosSlotLayout.metricsAtInk(
                    IosIconKind.BATTERY, battery, BASE_INK, GAP, scale, percent, ROW
                )
                val base = pxPerUnit(IosIconKind.BATTERY, battery) * scale / 100f
                val inkLeftUnits = IosGeometry.Battery.BODY_LEFT * base
                val dx = slot.leaf.left - (GAP / 2f - inkLeftUnits)
                assertTrue("dx=$dx scale=$scale offset=$percent", abs(dx) <= GAP / 2f + 1f)
                assertTrue(slot.leaf.left >= 0)
            }
        }
    }

    @Test
    fun `the legacy metrics stay the unchanged fallback`() {
        val legacy = IosSlotLayout.metrics(IosIconKind.BATTERY, battery, 1272, 100, 0, ROW)
        assertEquals(113, legacy.outerWidth)
        assertEquals(63, legacy.outerHeight)
        assertEquals(93, legacy.leaf.width)
        assertEquals(45, legacy.leaf.height)
        assertEquals(10, legacy.leaf.left)
        assertEquals(0.4438f, IosSlotLayout.unit(1272), 0.0001f)
        assertEquals(20.59f, IosSlotLayout.gap(1272), 0.01f)
        assertEquals(63, IosSlotLayout.rowHeight(1272))
    }
}

/**
 * The same R11 rules on real Android parents. The battery parent is the measured
 * `StatBatteryMeterView`: a horizontal `LinearLayout` with `start|center` gravity and 7/9 padding. The
 * pre-fix run of [batteryInkIsOnTheRowCentreWithTheRealPaddedParent] (legacy margins, no compensation)
 * is saved in `.pm/out-r11/pre-fix-layout.txt` (centre 44.5 px vs the 31.5 px row centre).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosR11LayoutRobolectricTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val cellBoth = IosVisual(
        cellMode = IosCellMode.DUAL,
        cellRole = IosCellRole.BOTH,
        cellBars = 4f,
        cellDots = 1f
    )
    private val label5g = IosVisual(wifiSlot = IosWifiSlot.DataType("5G"))
    private val wifi = IosVisual(wifiLevel = 3f)
    private val battery = IosVisual(batteryLevel = 1f)

    @Before
    fun setUp() {
        IosSlotLayout.resetForTest()
        IosSlotLayout.screenWidthOverrideForTest = 1272
        IosSlotLayout.hostInkOverrideForTest = 33.48f
        // Production calls activate before a bind registers a slot (DuoHook).
        IosSlotLayout.activate(ModuleSettings.DEFAULT)
    }

    @After
    fun tearDown() {
        IosSlotLayout.restoreAll()
        IosSlotLayout.resetForTest()
    }

    private fun layout(root: View, height: Int = 63) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun topInRow(row: View, view: View): Int {
        var top = view.top
        var cursor: View = view
        while (cursor !== row) {
            val parent = cursor.parent as View
            top += parent.top
            cursor = parent
        }
        return top
    }

    private fun leftInRow(row: View, view: View): Int {
        var left = view.left
        var cursor: View = view
        while (cursor !== row) {
            val parent = cursor.parent as View
            left += parent.left
            cursor = parent
        }
        return left
    }

    private fun pxPerUnit(kind: IosIconKind, visual: IosVisual): Float {
        val ink = IosGeometry.inkBox(kind, visual)
        return when (kind) {
            IosIconKind.BATTERY -> 33.48f / ink.height
            IosIconKind.CELL -> 33.48f / if (visual.cellMode == IosCellMode.SINGLE) {
                IosGeometry.Cell.SINGLE_BAR_BOTTOM - IosGeometry.Cell.TOP
            } else {
                IosGeometry.Cell.DUAL_BLOCK_H
            }
            IosIconKind.WIFI -> if (visual.wifiSlot is IosWifiSlot.DataType) {
                33.48f / IosGeometry.Battery.BODY_H
            } else {
                IosSlotLayout.WIFI_INK_TO_BASE_INK * 33.48f / ink.height
            }
        }
    }

    private fun inkCentreInRow(
        row: View,
        leaf: View,
        kind: IosIconKind,
        visual: IosVisual,
        scale: Int = 100
    ): Float {
        val ink = IosGeometry.inkBox(kind, visual)
        return topInRow(row, leaf) + (ink.top + ink.height / 2f) * pxPerUnit(kind, visual) * scale / 100f
    }

    private class RealTree(context: Context) {
        val row = LinearLayout(context)
        val mobileOuter = FrameLayout(context)
        val mobileGroup = LinearLayout(context)
        val mobileCombo = FrameLayout(context)
        val signal = ImageView(context)
        val label = ImageView(context)
        val wifiOuter = FrameLayout(context)
        val wifiGroup = LinearLayout(context)
        val wifiCombo = FrameLayout(context)
        val wifiSignal = ImageView(context)
        val battery = LinearLayout(context)
        val batteryLeaf = ImageView(context)

        init {
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL

            mobileOuter.clipChildren = true
            mobileGroup.clipChildren = true
            mobileCombo.clipChildren = true
            mobileOuter.addView(mobileGroup, FrameLayout.LayoutParams(84, 63))
            mobileGroup.addView(mobileCombo, LinearLayout.LayoutParams(84, 63))
            mobileCombo.addView(signal, FrameLayout.LayoutParams(67, 47))
            mobileCombo.addView(label, FrameLayout.LayoutParams(20, 16))
            row.addView(mobileOuter, LinearLayout.LayoutParams(84, 63))

            wifiOuter.clipChildren = true
            wifiGroup.clipChildren = true
            wifiCombo.clipChildren = true
            wifiOuter.addView(wifiGroup, FrameLayout.LayoutParams(70, 63))
            wifiGroup.addView(wifiCombo, LinearLayout.LayoutParams(70, 51))
            wifiCombo.addView(wifiSignal, FrameLayout.LayoutParams(70, 51))
            row.addView(wifiOuter, LinearLayout.LayoutParams(78, 63))

            // The measured StatBatteryMeterView: start|center, +7/+9 parent padding.
            battery.orientation = LinearLayout.HORIZONTAL
            battery.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            battery.setPadding(7, 9, 0, 0)
            battery.addView(batteryLeaf, LinearLayout.LayoutParams(93, 45))
            row.addView(battery, LinearLayout.LayoutParams(113, 63))

            signal.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.CELL,
                    IosVisual(
                        cellMode = IosCellMode.DUAL,
                        cellRole = IosCellRole.BOTH,
                        cellBars = 4f,
                        cellDots = 1f
                    ),
                    67, 47, relativeSize = false, captureColorFilter = true
                )
            )
            label.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.WIFI,
                    IosVisual(wifiSlot = IosWifiSlot.DataType("5G")),
                    20, 16, relativeSize = false, captureColorFilter = true
                )
            )
            wifiSignal.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.WIFI,
                    IosVisual(wifiLevel = 3f),
                    70, 51, relativeSize = false, captureColorFilter = true
                )
            )
            batteryLeaf.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.BATTERY,
                    IosVisual(batteryLevel = 1f),
                    93, 45, relativeSize = false, captureColorFilter = true
                )
            )
        }
    }

    /**
     * Post-fix regression of the pre-fix failure: the padded `StatBatteryMeterView` now places the
     * leaf at the computed top (parent padding subtracted, gravity START|TOP) so the battery ink is on
     * the row centre, and the host metrics give the 70x34 leaf / 86-wide outer.
     */
    @Test
    fun batteryInkIsOnTheRowCentreWithTheRealPaddedParent() {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val batterySlot = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(7, 9, 0, 0)
        }
        val leaf = ImageView(context)
        leaf.setImageDrawable(
            IosDrawables.create(
                IosIconKind.BATTERY,
                IosVisual(batteryLevel = 1f),
                93, 45, relativeSize = false, captureColorFilter = true
            )
        )
        batterySlot.addView(leaf, LinearLayout.LayoutParams(93, 45))
        row.addView(batterySlot, LinearLayout.LayoutParams(113, 63))

        assertTrue(IosSlotLayout.registerBattery(batterySlot, leaf))
        layout(row)

        val expected = IosSlotLayout.metricsAtInk(
            IosIconKind.BATTERY, battery, 33.48f, 16.74f, 100, 0, 63
        )
        assertEquals(86, batterySlot.layoutParams.width)
        assertEquals(63, batterySlot.layoutParams.height)
        assertEquals(70, leaf.layoutParams.width)
        assertEquals(34, leaf.layoutParams.height)
        val params = leaf.layoutParams as LinearLayout.LayoutParams
        assertEquals(expected.leaf.left - 7, params.leftMargin)
        assertEquals(expected.leaf.top - 9, params.topMargin)
        assertEquals(Gravity.START or Gravity.TOP, params.gravity)
        assertEquals(31.5f, inkCentreInRow(row, leaf, IosIconKind.BATTERY, battery), 1f)
    }

    @Test
    fun theHostClockCapDrivesTheTargetInk() {
        val root = FrameLayout(context)
        val clock = TextView(context).apply {
            id = android.R.id.text1
            textSize = 48f
            text = "3:55"
        }
        val leaf = ImageView(context)
        root.addView(clock, FrameLayout.LayoutParams(200, 94))
        root.addView(leaf, FrameLayout.LayoutParams(10, 10))

        IosSlotLayout.configure(android.R.id.text1, "android")
        val bounds = Rect()
        clock.paint.getTextBounds("0", 0, 1, bounds)
        val expected = 1.08f * bounds.height()
        assertEquals(expected, IosSlotLayout.hostInkPx(leaf)!!, 0.001f)

        // The cache re-reads when the clock text size changes.
        clock.textSize = 64f
        clock.paint.getTextBounds("0", 0, 1, bounds)
        val bigger = 1.08f * bounds.height()
        assertTrue(bigger > expected)
        assertEquals(bigger, IosSlotLayout.hostInkPx(leaf)!!, 0.001f)
    }

    @Test
    fun anUnresolvedClockIdFallsBackToTheLegacyMetrics() {
        IosSlotLayout.configure(0, "android")
        val root = FrameLayout(context)
        val leaf = ImageView(context)
        root.addView(leaf, FrameLayout.LayoutParams(10, 10))
        assertNull(IosSlotLayout.hostInkPx(leaf))
    }

    @Test
    fun theRealDumpTreeUsesTheHostBoxesOnOneCentreLine() {
        val tree = RealTree(context)
        assertTrue(IosSlotLayout.registerMobile(tree.signal, tree.label))
        assertTrue(IosSlotLayout.registerWifi(tree.wifiSignal))
        assertTrue(IosSlotLayout.registerBattery(tree.battery, tree.batteryLeaf))
        IosSlotLayout.reapply(
            ModuleSettings.DEFAULT.copy(iosCellScale = 100, iosWifiScale = 98, iosBatteryScale = 100)
        )
        layout(tree.row)

        val cellSlot = IosSlotLayout.mobileMetricsAtInk(
            cellBoth, label5g, 33.48f, 16.74f, 100, 0, 98, 0, 63
        )
        val wifiSlot = IosSlotLayout.metricsAtInk(IosIconKind.WIFI, wifi, 33.48f, 16.74f, 98, 0, 63)
        val batterySlot = IosSlotLayout.metricsAtInk(
            IosIconKind.BATTERY, battery, 33.48f, 16.74f, 100, 0, 63
        )

        // Applied outers and leaves are the host boxes.
        assertEquals(cellSlot.outerWidth, tree.mobileOuter.layoutParams.width)
        assertEquals(cellSlot.outerWidth, tree.mobileGroup.layoutParams.width)
        assertEquals(cellSlot.outerWidth, tree.mobileCombo.layoutParams.width)
        assertEquals(wifiSlot.outerWidth, tree.wifiOuter.layoutParams.width)
        assertEquals(batterySlot.outerWidth, tree.battery.layoutParams.width)
        assertEquals(cellSlot.leaf.width, tree.signal.layoutParams.width)
        assertEquals(cellSlot.leaf.height, tree.signal.layoutParams.height)
        assertEquals(cellSlot.label!!.width, tree.label.layoutParams.width)
        assertEquals(cellSlot.label!!.height, tree.label.layoutParams.height)
        assertEquals(wifiSlot.leaf.width, tree.wifiSignal.layoutParams.width)
        assertEquals(wifiSlot.leaf.height, tree.wifiSignal.layoutParams.height)
        assertEquals(batterySlot.leaf.width, tree.batteryLeaf.layoutParams.width)
        assertEquals(batterySlot.leaf.height, tree.batteryLeaf.layoutParams.height)
        assertTrue("mobile outer ${cellSlot.outerWidth}", cellSlot.outerWidth in 120..122)
        assertEquals(44, wifiSlot.leaf.width)
        assertEquals(33, wifiSlot.leaf.height)
        assertEquals(70, batterySlot.leaf.width)
        assertEquals(34, batterySlot.leaf.height)

        // Absolute ink centres: every one is the 63 px row centre within 1 px (the 5G label too).
        assertEquals(31.5f, inkCentreInRow(tree.row, tree.signal, IosIconKind.CELL, cellBoth), 1f)
        assertEquals(31.5f, inkCentreInRow(tree.row, tree.label, IosIconKind.WIFI, label5g), 1f)
        assertEquals(31.5f, inkCentreInRow(tree.row, tree.wifiSignal, IosIconKind.WIFI, wifi, 98), 1f)
        assertEquals(31.5f, inkCentreInRow(tree.row, tree.batteryLeaf, IosIconKind.BATTERY, battery), 1f)

        // Equal ink gaps: cell -> label -> wifi -> battery are all 16.74 +/- 1.5 px.
        val cellInkLeft = leftInRow(tree.row, tree.signal) +
            IosGeometry.inkBox(IosIconKind.CELL, cellBoth).left * pxPerUnit(IosIconKind.CELL, cellBoth)
        val cellInkRight = cellInkLeft + IosGeometry.inkBox(IosIconKind.CELL, cellBoth).width *
            pxPerUnit(IosIconKind.CELL, cellBoth)
        val labelInkLeft = leftInRow(tree.row, tree.label) +
            IosGeometry.inkBox(IosIconKind.WIFI, label5g).left * pxPerUnit(IosIconKind.WIFI, label5g)
        val labelInkRight = labelInkLeft + IosGeometry.inkBox(IosIconKind.WIFI, label5g).width *
            pxPerUnit(IosIconKind.WIFI, label5g)
        val wifiInkLeft = leftInRow(tree.row, tree.wifiSignal) +
            IosGeometry.inkBox(IosIconKind.WIFI, wifi).left * pxPerUnit(IosIconKind.WIFI, wifi) * 0.98f
        val wifiInkRight = wifiInkLeft + IosGeometry.inkBox(IosIconKind.WIFI, wifi).width *
            pxPerUnit(IosIconKind.WIFI, wifi) * 0.98f
        val batteryInkLeft = leftInRow(tree.row, tree.batteryLeaf) +
            IosGeometry.Battery.BODY_LEFT * pxPerUnit(IosIconKind.BATTERY, battery)

        assertEquals(16.74f, labelInkLeft - cellInkRight, 1.5f)
        assertEquals(16.74f, wifiInkLeft - labelInkRight, 1.5f)
        assertEquals(16.74f, batteryInkLeft - wifiInkRight, 1.5f)
    }

    @Test
    fun theLegacyFallbackStillUsesTheR9Boxes() {
        IosSlotLayout.hostInkOverrideForTest = null
        val tree = RealTree(context)
        assertTrue(IosSlotLayout.registerMobile(tree.signal, tree.label))
        assertTrue(IosSlotLayout.registerWifi(tree.wifiSignal))
        assertTrue(IosSlotLayout.registerBattery(tree.battery, tree.batteryLeaf))
        IosSlotLayout.reapply(ModuleSettings.DEFAULT.copy(iosWifiScale = 98))

        // The legacy metrics are untouched: battery 113/93x45, cell 67x47, Wi-Fi at 98 % 56x42.
        assertEquals(113, tree.battery.layoutParams.width)
        assertEquals(93, tree.batteryLeaf.layoutParams.width)
        assertEquals(45, tree.batteryLeaf.layoutParams.height)
        assertEquals(67, tree.signal.layoutParams.width)
        assertEquals(56, tree.wifiSignal.layoutParams.width)
        assertNotNull(tree.batteryLeaf.layoutParams)
    }
}
