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

    private fun layout(root: View) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(ROW, View.MeasureSpec.EXACTLY)
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

    @Test
    fun `QS header row is left to the constraint solver`() {
        val main = MainBarFixture(context)
        val qs = QsHeaderFixture(context)
        assertTrue(IosSlotLayout.registerMobile(main.chain.signal, main.chain.label))
        assertTrue(IosSlotLayout.registerBattery(main.battery.container, main.battery.leaf))
        assertTrue(IosSlotLayout.registerMobile(qs.chain.signal, qs.chain.label))
        assertTrue(IosSlotLayout.registerBattery(qs.battery.container, qs.battery.leaf))

        layout(main.root)
        layout(qs.root)

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
        assertEquals(
            "the battery slot's parent must not be resized to fill",
            CLUSTER_WIDTH,
            qs.cluster.layoutParams.width
        )
    }

    @Test
    fun `linear main bar row is still sized and keeps the ink gaps`() {
        val main = MainBarFixture(context)
        assertTrue(IosSlotLayout.registerMobile(main.chain.signal, main.chain.label))
        assertTrue(IosSlotLayout.registerBattery(main.battery.container, main.battery.leaf))
        layout(main.root)

        val statusWidth = main.statusIcons.layoutParams.width
        val batteryWidth = main.battery.container.layoutParams.width
        assertTrue("main row was sized: ${main.row.layoutParams.width}", main.row.layoutParams.width > 0)
        // Mobile applies before the battery on this surface, so the row already includes the stock
        // battery width and is never smaller than the two applied slots.
        assertTrue(
            "main row fits both slots: row=${main.row.layoutParams.width} " +
                "status=$statusWidth battery=$batteryWidth",
            main.row.layoutParams.width >= statusWidth + batteryWidth
        )
        assertEquals(ROW, main.row.layoutParams.height)
        assertEquals(GAP, inkGap(main.row, main.chain, main.battery), 1.5f)
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
    private class QsHeaderFixture(context: Context) {
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
            cluster.addView(icons, FrameLayout.LayoutParams(0, ROW))
            cluster.addView(battery.container, FrameLayout.LayoutParams(89, ROW))
            root.addView(cluster, FrameLayout.LayoutParams(CLUSTER_WIDTH, ROW))
        }
    }

    /**
     * A local stand-in for the stock `quick_qs_status_icons` ConstraintLayout: the class name ends in
     * `ConstraintLayout` (the production detector) and the layout reproduces the measured chain. A
     * 0dp row fills `parent - batteryWidth` and packs against the battery; a fixed row spreads the
     * leftover into the three equal gaps ConstraintLayout's default chain style produced.
     */
    private class FakeConstraintLayout(context: Context) : FrameLayout(context) {

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val icons = getChildAt(0)
            val battery = getChildAt(1)
            measureExact(battery, battery.layoutParams.width, height)
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
            if (icons.layoutParams.width == 0) {
                icons.layout(0, 0, iconsWidth, height)
                battery.layout(width - batteryWidth, 0, width, height)
            } else {
                val gap = (width - iconsWidth - batteryWidth) / 3
                icons.layout(gap, 0, gap + iconsWidth, height)
                val batteryLeft = gap + iconsWidth + gap
                battery.layout(batteryLeft, 0, batteryLeft + batteryWidth, height)
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
