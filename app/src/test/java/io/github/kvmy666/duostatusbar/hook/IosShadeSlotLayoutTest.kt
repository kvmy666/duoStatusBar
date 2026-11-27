package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * R18 item 1: the QS/shade header must use the same ink gap as the main bar.
 *
 * Owner CPH2747 dump (`.pm/evidence/owner-214/diagnostics-dump.txt:1019-1122`):
 * `quick_qs_status_icons 279x63`, `icons 128x63 @888`, `batteryRemainingIcon 89x63 @1036` ->
 * the 0dp `icons` row was rewritten to a fixed 128 px, and ConstraintLayout spread the leftover
 * 279-128-89=62 px into three ~20 px gaps (label ink -> battery ink 36 px instead of 16.74).
 * The main bar (`system_icons 229 = statusIcons 140 + battery 89`) is a LinearLayout and stays packed.
 *
 * This fixture mirrors the measured chain without the ConstraintLayout artifact: a local parent whose
 * class name ends in `ConstraintLayout` fills a 0dp row up to the battery and otherwise spreads the
 * leftover exactly like the stock chain. It is not a dependency on androidx.constraintlayout.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosShadeSlotLayoutTest {

    private companion object {
        const val BASE_INK = 33.48f
        const val GAP = 16.74f
        const val ROW = 63
        const val SHADE_HEIGHT = 101
        const val CLUSTER_WIDTH = 279
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val cellBoth = IosVisual(
        cellMode = IosCellMode.DUAL,
        cellRole = IosCellRole.BOTH,
        cellBars = 4f,
        cellDots = 1f
    )
    private val label5g = IosVisual(wifiSlot = IosWifiSlot.DataType("5G"))
    private val battery = IosVisual(batteryLevel = 1f)

    @Before
    fun setUp() {
        IosSlotLayout.resetForTest()
        IosSlotLayout.screenWidthOverrideForTest = 1272
        IosSlotLayout.hostInkOverrideForTest = BASE_INK
        // Production calls activate before a bind registers a slot (DuoHook).
        IosSlotLayout.activate(ModuleSettings.DEFAULT)
    }

    @After
    fun tearDown() {
        IosSlotLayout.restoreAll()
        IosSlotLayout.resetForTest()
    }

    private fun layout(root: View, width: Int = 2000, height: Int = ROW) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

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

    private fun leftIn(parent: View, view: View): Int {
        var left = view.left
        var cursor: View = view
        while (cursor !== parent) {
            val next = cursor.parent as View
            left += next.left
            cursor = next
        }
        return left
    }

    /** The measured ink gap from the 5G label's right edge to the battery body's left edge. */
    private fun inkGap(parent: View, chain: MobileChain, batterySlot: BatterySlot): Float {
        val labelInk = IosGeometry.inkBox(IosIconKind.WIFI, label5g)
        val labelScale = pxPerUnit(IosIconKind.WIFI, label5g)
        val labelLeft = leftIn(parent, chain.label) + labelInk.left * labelScale
        val labelRight = labelLeft + labelInk.width * labelScale
        val batteryLeft = leftIn(parent, batterySlot.leaf) +
            IosGeometry.Battery.BODY_LEFT * pxPerUnit(IosIconKind.BATTERY, battery)
        return batteryLeft - labelRight
    }

    /** The drawn-ink centre Y of [view] relative to [parent], from the same ink box the applier uses. */
    private fun inkCentreY(
        parent: View,
        view: View,
        kind: IosIconKind,
        visual: IosVisual
    ): Float {
        var top = view.top
        var cursor: View = view
        while (cursor !== parent) {
            val next = cursor.parent as View
            top += next.top
            cursor = next
        }
        val ink = IosGeometry.inkBox(kind, visual)
        return top + (ink.top + ink.height / 2f) * pxPerUnit(kind, visual)
    }

    @Test
    fun `QS header row is left to the constraint solver`() {
        val main = MainBarFixture(context)
        val qs = QsHeaderFixture(context)
        assertTrue(IosSlotLayout.registerMobile(main.chain.signal, main.chain.label))
        assertTrue(IosSlotLayout.registerBattery(main.battery.container, main.battery.leaf))
        assertTrue(IosSlotLayout.registerMobile(qs.chain.signal, qs.chain.label))
        assertTrue(IosSlotLayout.registerBattery(qs.battery.container, qs.battery.leaf))

        layout(main.root)
        // The production `quick_qs_status_icons` is match_parent inside a 279 px constraint row.
        layout(qs.root, CLUSTER_WIDTH)

        val mainGap = inkGap(main.row, main.chain, main.battery)
        val shadeGap = inkGap(qs.cluster, qs.chain, qs.battery)
        val rowWidth = qs.icons.layoutParams.width
        val containerGap = qs.battery.container.left - qs.icons.right

        assertEquals(
            "QS row must stay match-constraint (0dp): rowWidth=$rowWidth " +
                "containerGap=$containerGap shadeInkGap=$shadeGap mainInkGap=$mainGap",
            0,
            rowWidth
        )
        assertEquals("icons->battery container gap", 0f, containerGap.toFloat(), 1f)
        assertEquals("shade label->battery ink gap must equal the main bar", mainGap, shadeGap, 1f)
        assertEquals("main bar label->battery ink gap is the R11 0.50 x ink rule", GAP, mainGap, 1.5f)
        // R18: the production row is match_parent; before its first measure it must stay so, and
        // nothing pins it to a pixel width (the constraint pass owns it).
        assertEquals(
            "an unmeasured match-parent row must stay match-parent",
            ViewGroup.LayoutParams.MATCH_PARENT,
            qs.cluster.layoutParams.width
        )
    }

    @Test
    fun `an unmeasured match-parent cluster is never pinned to the slot sum`() {
        val qs = QsHeaderFixture(context)
        // measuredWidth is 0 until the parent/constraint pass measures the row.
        assertEquals(0, qs.cluster.measuredWidth)
        assertTrue(IosSlotLayout.registerBattery(qs.battery.container, qs.battery.leaf))
        assertEquals(
            "the slot sum must not pin an unmeasured match-parent row",
            ViewGroup.LayoutParams.MATCH_PARENT,
            qs.cluster.layoutParams.width
        )

        // After the real 279 px measure, the existing measured path may size the row.
        layout(qs.root, CLUSTER_WIDTH)
        IosSlotLayout.reapply(ModuleSettings.DEFAULT)
        assertEquals(CLUSTER_WIDTH, qs.cluster.layoutParams.width)
        // The battery slot keeps the R11 host metrics (IosR11LayoutTest pins 86 / 70x34).
        assertEquals(86, qs.battery.container.layoutParams.width)
        assertEquals(70, qs.battery.leaf.layoutParams.width)
        assertEquals(34, qs.battery.leaf.layoutParams.height)
    }

    @Test
    fun `linear main bar row is still sized and keeps the ink gaps`() {
        val main = MainBarFixture(context)
        // The packed row has exactly two children. Register the battery first so the stock 89 px
        // container is replaced before the row is sized; the row then measures exactly the sum.
        assertTrue(IosSlotLayout.registerBattery(main.battery.container, main.battery.leaf))
        assertTrue(IosSlotLayout.registerMobile(main.chain.signal, main.chain.label))
        layout(main.root)

        val statusWidth = main.statusIcons.layoutParams.width
        val batteryWidth = main.battery.container.layoutParams.width
        assertEquals(
            "main row is exactly its two packed children: row=${main.row.layoutParams.width} " +
                "status=$statusWidth battery=$batteryWidth",
            statusWidth + batteryWidth,
            main.row.layoutParams.width
        )
        assertEquals(ROW, main.row.layoutParams.height)
        assertEquals(GAP, inkGap(main.row, main.chain, main.battery), 0.5f)
        // R18 item 1: the main bar is a packed 63 px LinearLayout; signal, 5G and battery share its centre.
        val signalY = inkCentreY(main.row, main.chain.signal, IosIconKind.CELL, cellBoth)
        val labelY = inkCentreY(main.row, main.chain.label, IosIconKind.WIFI, label5g)
        val batteryY = inkCentreY(main.row, main.battery.leaf, IosIconKind.BATTERY, battery)
        val spread = maxOf(signalY, labelY, batteryY) - minOf(signalY, labelY, batteryY)
        assertTrue(
            "main bar ink centres must stay on one line: signal=$signalY label=$labelY battery=$batteryY",
            spread <= 1f
        )
        // R11 host metrics, not the fixture's stock 89 / 72x35.
        assertEquals(86, batteryWidth)
        assertEquals(70, main.battery.leaf.layoutParams.width)
        assertEquals(34, main.battery.leaf.layoutParams.height)
    }

    @Test
    fun `shade 101 row centres signal, 5G and battery on one line`() {
        val qs = QsHeaderFixture(context, SHADE_HEIGHT)
        assertTrue(IosSlotLayout.registerMobile(qs.chain.signal, qs.chain.label))
        assertTrue(IosSlotLayout.registerBattery(qs.battery.container, qs.battery.leaf))
        layout(qs.root, CLUSTER_WIDTH, SHADE_HEIGHT)

        val signalY = inkCentreY(qs.cluster, qs.chain.signal, IosIconKind.CELL, cellBoth)
        val labelY = inkCentreY(qs.cluster, qs.chain.label, IosIconKind.WIFI, label5g)
        val batteryY = inkCentreY(qs.cluster, qs.battery.leaf, IosIconKind.BATTERY, battery)
        val spread = maxOf(signalY, labelY, batteryY) - minOf(signalY, labelY, batteryY)
        val gap = inkGap(qs.cluster, qs.chain, qs.battery)

        assertTrue(
            "the 101 px shade row must put signal/label/battery on one line: " +
                "signal=$signalY label=$labelY battery=$batteryY spread=$spread",
            spread <= 1f
        )
        assertEquals("shade label->battery ink gap must stay the R11 rule", GAP, gap, 1f)
    }

    @Test
    fun `QS 63 row stays unchanged`() {
        val qs = QsHeaderFixture(context, ROW)
        assertTrue(IosSlotLayout.registerMobile(qs.chain.signal, qs.chain.label))
        assertTrue(IosSlotLayout.registerBattery(qs.battery.container, qs.battery.leaf))
        layout(qs.root, CLUSTER_WIDTH, ROW)

        val signalY = inkCentreY(qs.cluster, qs.chain.signal, IosIconKind.CELL, cellBoth)
        val labelY = inkCentreY(qs.cluster, qs.chain.label, IosIconKind.WIFI, label5g)
        val batteryY = inkCentreY(qs.cluster, qs.battery.leaf, IosIconKind.BATTERY, battery)
        val spread = maxOf(signalY, labelY, batteryY) - minOf(signalY, labelY, batteryY)
        val gap = inkGap(qs.cluster, qs.chain, qs.battery)

        assertTrue(
            "the 63 px QS row must stay on one line: " +
                "signal=$signalY label=$labelY battery=$batteryY spread=$spread",
            spread <= 1f
        )
        assertEquals("QS label->battery ink gap must stay the R11 rule", GAP, gap, 1f)
    }

    @Test
    fun `restoreAll restores the stock statusIcons gravity`() {
        val qs = QsHeaderFixture(context, SHADE_HEIGHT)
        val params = qs.statusIcons.layoutParams as LinearLayout.LayoutParams
        val stockGravity = params.gravity

        assertTrue(IosSlotLayout.registerMobile(qs.chain.signal, qs.chain.label))
        assertEquals(
            "the fix must centre the icon container in the taller row",
            Gravity.CENTER_VERTICAL,
            params.gravity and Gravity.VERTICAL_GRAVITY_MASK
        )

        IosSlotLayout.restoreAll()
        assertEquals(
            "restoreAll must put the stock statusIcons gravity back",
            stockGravity,
            params.gravity
        )
        // tearDown calls restoreAll again; a second restore must stay a no-op.
        IosSlotLayout.restoreAll()
        assertEquals(stockGravity, params.gravity)
    }

    private class MobileChain(context: Context) {
        val outer = FrameLayout(context)
        val group = LinearLayout(context)
        val combo = FrameLayout(context)
        val signal = ImageView(context)
        val label = ImageView(context)

        init {
            outer.clipChildren = true
            group.clipChildren = true
            combo.clipChildren = true
            group.orientation = LinearLayout.HORIZONTAL
            // Stock sizes from the CPH2747 dump: outer 124x63, signal 50x35, label 41x33.
            outer.addView(group, FrameLayout.LayoutParams(124, ROW))
            group.addView(combo, LinearLayout.LayoutParams(124, ROW))
            combo.addView(signal, FrameLayout.LayoutParams(50, 35))
            combo.addView(label, FrameLayout.LayoutParams(41, 33))
            signal.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.CELL,
                    IosVisual(
                        cellMode = IosCellMode.DUAL,
                        cellRole = IosCellRole.BOTH,
                        cellBars = 4f,
                        cellDots = 1f
                    ),
                    50, 35, relativeSize = false, captureColorFilter = true
                )
            )
            label.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.WIFI,
                    IosVisual(wifiSlot = IosWifiSlot.DataType("5G")),
                    41, 33, relativeSize = false, captureColorFilter = true
                )
            )
        }
    }

    private class BatterySlot(context: Context) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(7, 9, 0, 0)
        }
        val leaf = ImageView(context)

        init {
            // Stock sizes from the dump: container 89x63, leaf 72x35.
            container.addView(leaf, LinearLayout.LayoutParams(72, 35))
            leaf.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.BATTERY,
                    IosVisual(batteryLevel = 1f),
                    72, 35, relativeSize = false, captureColorFilter = true
                )
            )
        }
    }

    /** Main bar: `system_icons` is a packed LinearLayout (229 = 140 + 89 in the dump). */
    private class MainBarFixture(context: Context) {
        val root = FrameLayout(context)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val statusIcons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val chain = MobileChain(context)
        val battery = BatterySlot(context)

        init {
            statusIcons.addView(chain.outer, LinearLayout.LayoutParams(124, ROW))
            row.addView(statusIcons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ROW))
            row.addView(battery.container, LinearLayout.LayoutParams(89, ROW))
            root.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ROW))
        }
    }

    /** QS header: `quick_qs_status_icons` ConstraintLayout chain with a 0dp `icons` row. */
    private class QsHeaderFixture(context: Context, rowHeight: Int = ROW) {
        val root = FrameLayout(context)
        val cluster = FakeConstraintLayout(context)
        val icons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.TOP
        }
        val statusIcons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val chain = MobileChain(context)
        val battery = BatterySlot(context)

        init {
            statusIcons.addView(chain.outer, LinearLayout.LayoutParams(124, ROW))
            icons.addView(statusIcons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ROW))
            cluster.addView(icons, FrameLayout.LayoutParams(0, rowHeight))
            cluster.addView(battery.container, FrameLayout.LayoutParams(89, ROW))
            // Production XML: the QS status-icons row is match_parent inside the constraint row.
            root.addView(
                cluster,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight)
            )
        }
    }

    /**
     * A local stand-in for the stock `quick_qs_status_icons` ConstraintLayout: the class name ends in
     * `ConstraintLayout` (the production detector) and the layout reproduces the measured chain. A
     * 0dp row fills `parent - batteryWidth` and is measured at the full cluster height (the real row
     * is 63 QS / 101 shade); the fixed 63 px battery is centred vertically in the cluster, exactly as
     * the stock ConstraintLayout centres it. A fixed row spreads the leftover into the three equal
     * gaps the default chain style produced.
     */
    private class FakeConstraintLayout(context: Context) : FrameLayout(context) {

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val icons = getChildAt(0)
            val battery = getChildAt(1)
            // The battery view keeps its stock 63 px height; the cluster centres it, never stretches it.
            measureExact(battery, battery.layoutParams.width, battery.layoutParams.height)
            val iconsWidth = if (icons.layoutParams.width == 0) {
                width - battery.measuredWidth
            } else {
                icons.layoutParams.width
            }
            measureExact(icons, iconsWidth, height)
            setMeasuredDimension(width, height)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val width = r - l
            val height = b - t
            val icons = getChildAt(0)
            val battery = getChildAt(1)
            val iconsWidth = icons.measuredWidth
            val batteryWidth = battery.measuredWidth
            val batteryHeight = battery.measuredHeight
            val batteryTop = (height - batteryHeight) / 2
            if (icons.layoutParams.width == 0) {
                icons.layout(0, 0, iconsWidth, height)
                battery.layout(width - batteryWidth, batteryTop, width, batteryTop + batteryHeight)
            } else {
                val gap = (width - iconsWidth - batteryWidth) / 3
                icons.layout(gap, 0, gap + iconsWidth, height)
                val batteryLeft = gap + iconsWidth + gap
                battery.layout(
                    batteryLeft,
                    batteryTop,
                    batteryLeft + batteryWidth,
                    batteryTop + batteryHeight
                )
            }
        }

        private fun measureExact(view: View, width: Int, height: Int) {
            view.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            )
        }
    }
}
