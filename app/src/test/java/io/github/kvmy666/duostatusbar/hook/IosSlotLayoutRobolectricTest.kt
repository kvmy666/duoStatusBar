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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * R9 §System-view layout on real Android parents: the stock views are resized by LayoutParams, the
 * live slider re-sizes the same views, iOS off restores the exact stock params/clip flags/scale type,
 * and 50..200 % never clips the glyph or lets it overlap its neighbour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosSlotLayoutRobolectricTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** `system_icons` -> `StatusIconContainer` -> mobile/wifi outer chains (+ battery sibling). */
    private class Tree(context: Context) {
        val root = LinearLayout(context)
        val container = LinearLayout(context)
        val mobileOuter = FrameLayout(context)
        val mobileGroup = LinearLayout(context)
        val mobileCombo = FrameLayout(context)
        val signal = ImageView(context)
        val label = ImageView(context)
        val wifiOuter = FrameLayout(context)
        val wifiGroup = LinearLayout(context)
        val wifiCombo = FrameLayout(context)
        val wifiSignal = ImageView(context)
        val battery = FrameLayout(context)
        val batteryLeaf = ImageView(context)

        init {
            root.orientation = LinearLayout.HORIZONTAL
            root.clipChildren = true
            root.clipToPadding = true
            container.orientation = LinearLayout.HORIZONTAL
            container.clipChildren = true
            root.addView(container, LinearLayout.LayoutParams(138, 63))
            root.addView(battery, LinearLayout.LayoutParams(86, 63))

            mobileOuter.clipChildren = true
            mobileGroup.clipChildren = true
            mobileCombo.clipChildren = true
            container.addView(mobileOuter, LinearLayout.LayoutParams(63, 63))
            mobileOuter.addView(mobileGroup, FrameLayout.LayoutParams(60, 63))
            mobileGroup.addView(mobileCombo, LinearLayout.LayoutParams(60, 63))
            mobileCombo.addView(signal, FrameLayout.LayoutParams(60, 51))
            mobileCombo.addView(label, FrameLayout.LayoutParams(20, 16))
            label.visibility = View.GONE

            wifiOuter.clipChildren = true
            wifiGroup.clipChildren = true
            wifiCombo.clipChildren = true
            container.addView(wifiOuter, LinearLayout.LayoutParams(70, 63))
            wifiOuter.addView(wifiGroup, FrameLayout.LayoutParams(70, 63))
            wifiGroup.addView(wifiCombo, LinearLayout.LayoutParams(70, 51))
            wifiCombo.addView(wifiSignal, FrameLayout.LayoutParams(70, 51))

            battery.addView(batteryLeaf, FrameLayout.LayoutParams(82, 51))

            signal.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.CELL,
                    IosVisual(part = IosPart.CELL, cellBars = 4f),
                    60, 51, relativeSize = false, captureColorFilter = true
                )
            )
            wifiSignal.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.WIFI,
                    IosVisual(part = IosPart.WIFI, wifiLevel = 3f),
                    70, 51, relativeSize = false, captureColorFilter = true
                )
            )
            batteryLeaf.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.BATTERY,
                    IosVisual(part = IosPart.BATTERY, batteryLevel = 1f),
                    82, 51, relativeSize = false, captureColorFilter = true
                )
            )
        }

        fun labelMode() {
            label.visibility = View.VISIBLE
            wifiOuter.visibility = View.GONE
            label.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.WIFI,
                    IosVisual(part = IosPart.WIFI, wifiSlot = IosWifiSlot.DataType("5G")),
                    20, 16, relativeSize = false, captureColorFilter = true
                )
            )
        }
    }

    /**
     * The measured issue dump tree: a 316x94 `StatusIconContainer` row, the visible 224x94 mobile
     * chain (`mobile_signal` 133x94, the data-type label 52x42), the GONE 0x0 second-SIM outer
     * (84x47 / 67x47) and the 113x63 battery container with its 93x45 leaf.
     */
    private class DumpTree(context: Context) {
        val row = LinearLayout(context)
        val container = LinearLayout(context)
        val mobileOuter = FrameLayout(context)
        val mobileGroup = LinearLayout(context)
        val mobileCombo = FrameLayout(context)
        val signal = ImageView(context)
        val label = ImageView(context)
        val hiddenOuter = FrameLayout(context)
        val hiddenGroup = LinearLayout(context)
        val hiddenCombo = FrameLayout(context)
        val hiddenSignal = ImageView(context)
        val battery = FrameLayout(context)
        val batteryLeaf = ImageView(context)

        init {
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            container.orientation = LinearLayout.HORIZONTAL
            container.gravity = Gravity.CENTER_VERTICAL
            row.addView(container, LinearLayout.LayoutParams(316, 94))
            row.addView(battery, LinearLayout.LayoutParams(113, 63))

            mobileOuter.clipChildren = true
            mobileGroup.gravity = Gravity.CENTER_VERTICAL
            mobileCombo.clipChildren = true
            container.addView(mobileOuter, LinearLayout.LayoutParams(224, 94))
            mobileOuter.addView(mobileGroup, FrameLayout.LayoutParams(224, 94))
            mobileGroup.addView(mobileCombo, LinearLayout.LayoutParams(224, 94))
            mobileCombo.addView(
                signal,
                FrameLayout.LayoutParams(133, 94).apply { leftMargin = 9 }
            )
            mobileCombo.addView(
                label,
                FrameLayout.LayoutParams(52, 42).apply {
                    leftMargin = 162
                    topMargin = 26
                }
            )

            // The hidden second-SIM owner: GONE plus 0x0, still carrying its 84x47 / 67x47 chain.
            hiddenOuter.visibility = View.GONE
            container.addView(hiddenOuter, LinearLayout.LayoutParams(0, 0))
            hiddenOuter.addView(hiddenGroup, FrameLayout.LayoutParams(84, 47))
            hiddenGroup.addView(hiddenCombo, LinearLayout.LayoutParams(84, 47))
            hiddenCombo.addView(hiddenSignal, FrameLayout.LayoutParams(67, 47))

            battery.addView(batteryLeaf, FrameLayout.LayoutParams(93, 45))

            signal.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.CELL,
                    IosVisual(
                        part = IosPart.CELL,
                        cellMode = IosCellMode.DUAL,
                        cellRole = IosCellRole.BOTH,
                        cellBars = 4f,
                        cellDots = 1f
                    ),
                    133, 94, relativeSize = false, captureColorFilter = true
                )
            )
            label.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.WIFI,
                    IosVisual(part = IosPart.WIFI, wifiSlot = IosWifiSlot.DataType("5G")),
                    52, 42, relativeSize = false, captureColorFilter = true
                )
            )
            hiddenSignal.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.CELL,
                    IosVisual(part = IosPart.CELL, cellBars = 4f),
                    67, 47, relativeSize = false, captureColorFilter = true
                )
            )
            batteryLeaf.setImageDrawable(
                IosDrawables.create(
                    IosIconKind.BATTERY,
                    IosVisual(part = IosPart.BATTERY, batteryLevel = 1f),
                    93, 45, relativeSize = false, captureColorFilter = true
                )
            )
        }
    }

    @Before
    fun setUp() {
        IosSlotLayout.resetForTest()
        IosSlotLayout.screenWidthOverrideForTest = 1272
        // Production publishes the settings snapshot before a bind installs a slot (DuoHook).
        IosSlotLayout.reapply(ModuleSettings.DEFAULT)
    }

    @After
    fun tearDown() {
        IosSlotLayout.restoreAll()
        IosSlotLayout.resetForTest()
    }

    private fun layout(root: View) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun lp(view: View): ViewGroup.LayoutParams = view.layoutParams

    @Test
    fun `wifi apply sizes the real chain and restore returns the exact stock params`() {
        val tree = Tree(context)
        val stockOuter = tree.wifiOuter.layoutParams
        val stockCombo = tree.wifiCombo.layoutParams
        val stockSignal = tree.wifiSignal.layoutParams as FrameLayout.LayoutParams
        val stockScale = tree.wifiSignal.scaleType

        assertTrue(IosSlotLayout.registerWifi(tree.wifiSignal))
        assertEquals(78, lp(tree.wifiOuter).width)
        assertEquals(63, lp(tree.wifiOuter).height)
        assertEquals(78, lp(tree.wifiGroup).width)
        assertEquals(78, lp(tree.wifiCombo).width)
        assertEquals(57, lp(tree.wifiSignal).width)
        assertEquals(43, lp(tree.wifiSignal).height)
        assertEquals(10, (tree.wifiSignal.layoutParams as FrameLayout.LayoutParams).leftMargin)
        assertEquals(ImageView.ScaleType.FIT_XY, tree.wifiSignal.scaleType)
        assertFalse(tree.wifiCombo.clipChildren)
        assertTrue(lp(tree.container).width >= 138)

        IosSlotLayout.restoreAll()

        assertSameParams(stockOuter, tree.wifiOuter.layoutParams)
        assertSameParams(stockCombo, tree.wifiCombo.layoutParams)
        assertSameParams(stockSignal, tree.wifiSignal.layoutParams)
        assertEquals(stockScale, tree.wifiSignal.scaleType)
        assertTrue(tree.wifiCombo.clipChildren)
        assertTrue(tree.container.clipChildren)
    }

    private fun assertSameParams(expected: ViewGroup.LayoutParams, actual: ViewGroup.LayoutParams) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        val a = expected as? ViewGroup.MarginLayoutParams
        val b = actual as? ViewGroup.MarginLayoutParams
        if (a != null && b != null) {
            assertEquals(a.leftMargin, b.leftMargin)
            assertEquals(a.topMargin, b.topMargin)
            assertEquals(a.marginStart, b.marginStart)
        }
    }

    @Test
    fun `container with MATCH_PARENT and weighted children is stable across re-applies`() {
        val tree = Tree(context)
        val matchParent = FrameLayout(context)
        val weighted = FrameLayout(context)
        tree.container.addView(
            matchParent,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 63)
        )
        tree.container.addView(weighted, LinearLayout.LayoutParams(0, 63, 1f))

        assertTrue(IosSlotLayout.registerMobile(tree.signal, null))
        assertTrue(IosSlotLayout.registerWifi(tree.wifiSignal))

        val mobile = IosSlotLayout.metrics(
            IosIconKind.CELL,
            IosVisual(part = IosPart.CELL, cellBars = 4f),
            1272, 100, 0, 63
        )
        val wifi = IosSlotLayout.metrics(
            IosIconKind.WIFI,
            IosVisual(part = IosPart.WIFI, wifiLevel = 3f),
            1272, 100, 0, 63
        )
        val expected = mobile.outerWidth + wifi.outerWidth
        // The negative-width and weighted children add nothing; only the registered slots count.
        assertEquals(expected, lp(tree.container).width)
        assertEquals(84, mobile.outerWidth)
        assertEquals(78, wifi.outerWidth)

        // Lay the tree out so a MATCH_PARENT/weighted child would have a live width if we read it.
        layout(tree.root)
        IosSlotLayout.reapply(ModuleSettings.DEFAULT)
        assertEquals("second apply grew the container", expected, lp(tree.container).width)

        layout(tree.root)
        IosSlotLayout.reapply(ModuleSettings.DEFAULT)
        assertEquals("third apply grew the container", expected, lp(tree.container).width)
        assertNotNull(IosSlotLayout.targetForTest(tree.container))
    }

    @Test
    fun `restoreAll clears every recorded target`() {
        val tree = Tree(context)
        assertTrue(IosSlotLayout.registerWifi(tree.wifiSignal))
        assertNotNull(IosSlotLayout.targetForTest(tree.wifiOuter))
        assertNotNull(IosSlotLayout.targetForTest(tree.container))

        IosSlotLayout.restoreAll()

        assertNull(IosSlotLayout.targetForTest(tree.wifiOuter))
        assertNull(IosSlotLayout.targetForTest(tree.container))
    }

    @Test
    fun `a late register after restoreAll writes nothing`() {
        val tree = Tree(context)
        assertTrue(IosSlotLayout.registerWifi(tree.wifiSignal))
        assertEquals(78, lp(tree.wifiOuter).width)
        assertEquals(ImageView.ScaleType.FIT_XY, tree.wifiSignal.scaleType)

        IosSlotLayout.restoreAll()
        val stockOuter = tree.wifiOuter.layoutParams
        val stockSignal = tree.wifiSignal.layoutParams
        val stockContainer = tree.container.layoutParams
        val stockScale = tree.wifiSignal.scaleType

        // A bind that lands after the mode switch must not resurrect the iOS geometry.
        assertFalse(IosSlotLayout.registerWifi(tree.wifiSignal))

        assertEquals(70, lp(tree.wifiOuter).width)
        assertSameParams(stockOuter, tree.wifiOuter.layoutParams)
        assertSameParams(stockSignal, tree.wifiSignal.layoutParams)
        assertSameParams(stockContainer, tree.container.layoutParams)
        assertEquals(stockScale, tree.wifiSignal.scaleType)
        assertTrue(tree.wifiCombo.clipChildren)
        assertTrue(tree.container.clipChildren)
        assertNull(IosSlotLayout.targetForTest(tree.wifiOuter))
        assertNull(IosSlotLayout.targetForTest(tree.container))
        assertEquals(0, IosSlotLayout.registeredForTest)
    }

    @Test
    fun `restoreAll is idempotent and reapply reactivates the layout`() {
        val tree = Tree(context)
        assertTrue(IosSlotLayout.registerWifi(tree.wifiSignal))

        IosSlotLayout.restoreAll()
        IosSlotLayout.restoreAll()
        assertEquals(70, lp(tree.wifiOuter).width)
        assertEquals(0, IosSlotLayout.registeredForTest)
        assertNull(IosSlotLayout.targetForTest(tree.wifiOuter))
        assertFalse("a register after restore must stay refused", IosSlotLayout.registerWifi(tree.wifiSignal))

        IosSlotLayout.reapply(ModuleSettings.DEFAULT)
        assertTrue("reapply must reopen the layout", IosSlotLayout.registerWifi(tree.wifiSignal))
        assertEquals(78, lp(tree.wifiOuter).width)
        assertNotNull(IosSlotLayout.targetForTest(tree.wifiOuter))
    }

    @Test
    fun `restore applies stock params when the OEM replaced the LayoutParams instance`() {
        val tree = Tree(context)
        val stockScale = tree.wifiSignal.scaleType
        val stockGravity = (tree.wifiSignal.layoutParams as FrameLayout.LayoutParams).gravity
        assertTrue(IosSlotLayout.registerWifi(tree.wifiSignal))

        // The OEM swaps in a new params object while the layout is active.
        tree.wifiSignal.layoutParams = FrameLayout.LayoutParams(999, 999).apply {
            leftMargin = 21
            topMargin = 13
            rightMargin = 7
            bottomMargin = 5
            marginStart = 21
            marginEnd = 7
            gravity = Gravity.BOTTOM or Gravity.END
        }
        tree.wifiCombo.layoutParams = FrameLayout.LayoutParams(999, 999)

        IosSlotLayout.restoreAll()

        val signal = tree.wifiSignal.layoutParams as FrameLayout.LayoutParams
        assertEquals(70, signal.width)
        assertEquals(51, signal.height)
        assertEquals(0, signal.leftMargin)
        assertEquals(0, signal.topMargin)
        assertEquals(0, signal.rightMargin)
        assertEquals(0, signal.bottomMargin)
        assertEquals(0, signal.marginStart)
        assertEquals(0, signal.marginEnd)
        assertEquals(stockGravity, signal.gravity)
        assertEquals(stockScale, tree.wifiSignal.scaleType)
        assertEquals(70, lp(tree.wifiCombo).width)
        assertEquals(51, lp(tree.wifiCombo).height)
        assertTrue(tree.wifiCombo.clipChildren)
    }

    @Test
    fun `live settings change the real wifi size without replacing the drawable`() {
        val tree = Tree(context)
        assertTrue(IosSlotLayout.registerWifi(tree.wifiSignal))
        val drawable = tree.wifiSignal.drawable

        IosSlotLayout.reapply(ModuleSettings.DEFAULT.copy(iosWifiScale = 200))
        // Restored 2.0.6 Wi-Fi ink 57.17 px: round(57.17 * 2 + 20.59) = 135.
        assertEquals(135, lp(tree.wifiOuter).width)
        assertEquals(86, lp(tree.wifiOuter).height)
        assertEquals(115, lp(tree.wifiSignal).width)
        assertEquals(86, lp(tree.wifiSignal).height)
        assertTrue(tree.wifiSignal.drawable === drawable)

        IosSlotLayout.reapply(ModuleSettings.DEFAULT.copy(iosWifiScale = 50))
        assertEquals(49, lp(tree.wifiOuter).width)
        assertEquals(63, lp(tree.wifiOuter).height)
        assertEquals(29, lp(tree.wifiSignal).width)
        assertEquals(21, lp(tree.wifiSignal).height)
        assertTrue(tree.wifiSignal.drawable === drawable)
    }

    @Test
    fun `cell and battery sliders change the per-icon sizes independently`() {
        val tree = Tree(context)
        val batteryScale = tree.batteryLeaf.scaleType
        assertTrue(IosSlotLayout.registerMobile(tree.signal, null))
        assertTrue(IosSlotLayout.registerBattery(tree.battery, tree.batteryLeaf))
        assertFalse(tree.battery.clipChildren)

        IosSlotLayout.reapply(
            ModuleSettings.DEFAULT.copy(iosCellScale = 200, iosBatteryScale = 50)
        )
        assertEquals(147, lp(tree.mobileOuter).width)
        assertEquals(133, lp(tree.signal).width)
        assertEquals(67, lp(tree.battery).width)
        assertEquals(46, lp(tree.batteryLeaf).width)
        assertEquals(ImageView.ScaleType.FIT_XY, tree.batteryLeaf.scaleType)

        IosSlotLayout.reapply(
            ModuleSettings.DEFAULT.copy(iosCellScale = 50, iosBatteryScale = 200)
        )
        assertEquals(52, lp(tree.mobileOuter).width)
        assertEquals(33, lp(tree.signal).width)
        assertEquals(205, lp(tree.battery).width)
        assertEquals(186, lp(tree.batteryLeaf).width)

        IosSlotLayout.restoreAll()
        assertTrue(tree.battery.clipChildren)
        assertEquals(batteryScale, tree.batteryLeaf.scaleType)
    }

    @Test
    fun `label mode sizes the mobile outer to cell plus label`() {
        val tree = Tree(context)
        tree.labelMode()
        assertTrue(IosSlotLayout.registerMobile(tree.signal, tree.label))

        assertEquals(156, lp(tree.mobileOuter).width)
        assertEquals(67, lp(tree.signal).width)
        assertEquals(53, lp(tree.label).width)
        assertEquals(94, (tree.label.layoutParams as FrameLayout.LayoutParams).leftMargin)
        // GONE wifi must not add a second slot to the container.
        assertEquals(156, lp(tree.container).width)
    }

    @Test
    fun `real dump tree gets the one-row R9 spec at 1272`() {
        val tree = DumpTree(context)
        val cellVisual = IosVisual(
            part = IosPart.CELL,
            cellMode = IosCellMode.DUAL,
            cellRole = IosCellRole.BOTH,
            cellBars = 4f,
            cellDots = 1f
        )
        val labelVisual = IosVisual(part = IosPart.WIFI, wifiSlot = IosWifiSlot.DataType("5G"))
        val batteryVisual = IosVisual(part = IosPart.BATTERY, batteryLevel = 1f)

        assertTrue(IosSlotLayout.registerMobile(tree.signal, tree.label))
        assertTrue(IosSlotLayout.registerBattery(tree.battery, tree.batteryLeaf))
        IosSlotLayout.reapply(
            ModuleSettings.DEFAULT.copy(iosCellScale = 100, iosWifiScale = 98, iosBatteryScale = 100)
        )

        val s = IosSlotLayout.unit(1272)
        val gap = IosSlotLayout.gap(1272)
        assertEquals(20.59f, gap, 0.01f)

        // One 63 px row for the role, not the live 94/63/51/47 stock heights.
        for (view in listOf(tree.mobileOuter, tree.mobileGroup, tree.mobileCombo, tree.battery)) {
            assertEquals("${view.javaClass.simpleName} height", 63, lp(view).height)
        }

        val cellParams = tree.signal.layoutParams as FrameLayout.LayoutParams
        val labelParams = tree.label.layoutParams as FrameLayout.LayoutParams
        val batteryParams = tree.batteryLeaf.layoutParams as FrameLayout.LayoutParams
        val cellInk = IosGeometry.inkBox(IosIconKind.CELL, cellVisual)
        val labelInk = IosGeometry.inkBox(IosIconKind.WIFI, labelVisual)
        val batteryInk = IosGeometry.inkBox(IosIconKind.BATTERY, batteryVisual)

        // Leaf sizes: the stacked BOTH cell artboard, the 98 % label artboard and the battery.
        assertEquals(67, cellParams.width)
        assertEquals(47, cellParams.height)
        assertEquals(52, labelParams.width)
        assertEquals(42, labelParams.height)
        assertEquals(93, batteryParams.width)
        assertEquals(45, batteryParams.height)

        // Leaf left margins put the same G/2 pad before each ink.
        assertEquals(
            (gap / 2f - cellInk.left * s).roundToInt().coerceAtLeast(0),
            cellParams.leftMargin
        )
        val cellInkW = cellInk.width * s
        val labelInkW = labelInk.width * s * 0.98f
        assertEquals(
            (gap / 2f + cellInkW + gap - labelInk.left * s * 0.98f).roundToInt().coerceAtLeast(0),
            labelParams.leftMargin
        )
        assertEquals(
            (gap / 2f - batteryInk.left * s).roundToInt().coerceAtLeast(0),
            batteryParams.leftMargin
        )

        // Exact R9 outer widths: ink x scale + the equal gap (mobile holds cell + label).
        val mobileOuterW = (cellInkW + gap + labelInkW + gap).roundToInt()
        val batteryOuterW = (batteryInk.width * s + gap).roundToInt()
        assertEquals(mobileOuterW, lp(tree.mobileOuter).width)
        assertEquals(mobileOuterW, lp(tree.mobileGroup).width)
        assertEquals(mobileOuterW, lp(tree.mobileCombo).width)
        assertEquals(158, mobileOuterW)
        assertEquals(113, lp(tree.battery).width)
        assertEquals(batteryOuterW, lp(tree.battery).width)

        // Every leaf is vertically centred in its outer: top = round((outerH - leafH) / 2).
        assertEquals(((63 - cellParams.height) / 2f).roundToInt(), cellParams.topMargin)
        assertEquals(((63 - labelParams.height) / 2f).roundToInt(), labelParams.topMargin)
        assertEquals(((63 - batteryParams.height) / 2f).roundToInt(), batteryParams.topMargin)
        assertCentred(cellParams.topMargin, cellParams.height, 63)
        assertCentred(labelParams.topMargin, labelParams.height, 63)
        assertCentred(batteryParams.topMargin, batteryParams.height, 63)

        // Ink gaps: cell -> label and label -> battery are both the equal 20.59 px slot gap.
        val cellInkLeft = cellParams.leftMargin + cellInk.left * s
        val cellInkRight = cellInkLeft + cellInkW
        val labelInkLeft = labelParams.leftMargin + labelInk.left * s * 0.98f
        val labelInkRight = labelInkLeft + labelInkW
        val batteryInkLeft =
            mobileOuterW + batteryParams.leftMargin + batteryInk.left * s
        assertEquals("cell->label ink gap", gap, labelInkLeft - cellInkRight, 1f)
        assertEquals("label->battery ink gap", gap, batteryInkLeft - labelInkRight, 1f)
    }

    @Test
    fun `at 200 percent every leaf centre stays on the stock row centre`() {
        val cellVisual = IosVisual(part = IosPart.CELL, cellBars = 4f)
        val labelVisual = IosVisual(part = IosPart.WIFI, wifiSlot = IosWifiSlot.DataType("5G"))
        val batteryVisual = IosVisual(part = IosPart.BATTERY, batteryLevel = 1f)
        val mobile = IosSlotLayout.mobileMetrics(
            cellVisual, labelVisual, 1272, 200, 0, 98, 0, 63
        )
        val battery = IosSlotLayout.metrics(IosIconKind.BATTERY, batteryVisual, 1272, 100, 0, 63)

        // The dump mix: label-mode mobile outer 94, a GONE Wi-Fi slot, battery outer 63.
        val labelRow = row(94)
        val mobileOuter = FrameLayout(context)
        val wifiOuter = FrameLayout(context)
        val batteryOuter = FrameLayout(context)
        labelRow.addView(mobileOuter, LinearLayout.LayoutParams(mobile.outerWidth, mobile.outerHeight))
        labelRow.addView(wifiOuter, LinearLayout.LayoutParams(78, 63))
        labelRow.addView(batteryOuter, LinearLayout.LayoutParams(battery.outerWidth, battery.outerHeight))
        wifiOuter.visibility = View.GONE
        val cellLeaf = addLeaf(mobileOuter, mobile.leaf)
        val labelLeaf = addLeaf(mobileOuter, mobile.label!!)
        val batteryLeaf = addLeaf(batteryOuter, battery.leaf)
        layoutRow(labelRow, 94)

        assertEquals("the 200 % cell outer fills the row", 0, mobileOuter.top)
        assertTrue(abs(batteryOuter.top - (94 - battery.outerHeight) / 2f) <= 1f)
        assertEquals(94, cellLeaf.height)
        assertEquals(42, labelLeaf.height)
        assertEquals(63, batteryOuter.height)
        assertOnRowCentre(
            labelRow,
            mobileOuter to cellLeaf,
            mobileOuter to labelLeaf,
            batteryOuter to batteryLeaf
        )

        // The three-outers row: cell 200 % (outer 94), Wi-Fi 98 % and battery 100 % (outer 63 each).
        val wifi = IosSlotLayout.metrics(
            IosIconKind.WIFI,
            IosVisual(part = IosPart.WIFI, wifiLevel = 3f),
            1272, 98, 0, 63
        )
        val singleMobile = IosSlotLayout.metrics(IosIconKind.CELL, cellVisual, 1272, 200, 0, 63)
        val threeRow = row(94)
        val singleMobileOuter = FrameLayout(context)
        val wifiLeafOuter = FrameLayout(context)
        val singleBatteryOuter = FrameLayout(context)
        threeRow.addView(
            singleMobileOuter,
            LinearLayout.LayoutParams(singleMobile.outerWidth, singleMobile.outerHeight)
        )
        threeRow.addView(wifiLeafOuter, LinearLayout.LayoutParams(wifi.outerWidth, wifi.outerHeight))
        threeRow.addView(
            singleBatteryOuter,
            LinearLayout.LayoutParams(battery.outerWidth, battery.outerHeight)
        )
        val singleCellLeaf = addLeaf(singleMobileOuter, singleMobile.leaf)
        val wifiLeaf = addLeaf(wifiLeafOuter, wifi.leaf)
        val singleBatteryLeaf = addLeaf(singleBatteryOuter, battery.leaf)
        layoutRow(threeRow, 94)

        assertEquals(94, singleMobileOuter.height)
        assertEquals(63, wifiLeafOuter.height)
        assertEquals(63, singleBatteryOuter.height)
        assertOnRowCentre(
            threeRow,
            singleMobileOuter to singleCellLeaf,
            wifiLeafOuter to wifiLeaf,
            singleBatteryOuter to singleBatteryLeaf
        )
    }

    private fun row(height: Int): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height)
    }

    private fun addLeaf(outer: FrameLayout, box: IosSlotBox): ImageView {
        val leaf = ImageView(context)
        outer.addView(
            leaf,
            FrameLayout.LayoutParams(box.width, box.height).apply {
                leftMargin = box.left
                topMargin = box.top
                gravity = Gravity.START or Gravity.TOP
            }
        )
        return leaf
    }

    private fun layoutRow(row: LinearLayout, height: Int) {
        row.measure(
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)
    }

    /** The leaf's absolute vertical centre, including every ancestor's top down to [row]. */
    private fun leafCentreInRow(row: View, leaf: View): Float {
        var centre = leaf.top + leaf.height / 2f
        var cursor: View = leaf
        while (cursor !== row) {
            val parent = cursor.parent as View
            centre += parent.top
            cursor = parent
        }
        return centre
    }

    private fun assertOnRowCentre(row: LinearLayout, vararg pairs: Pair<View, View>) {
        val rowCentre = row.height / 2f
        for ((_, leaf) in pairs) {
            val centre = leafCentreInRow(row, leaf)
            assertTrue(
                "${leaf.javaClass.simpleName} centre $centre vs row centre $rowCentre",
                abs(centre - rowCentre) <= 1f
            )
        }
    }

    private fun assertCentred(top: Int, leafH: Int, outerH: Int) {
        assertTrue(
            "leaf centre ${top + leafH / 2f} vs outer centre ${outerH / 2f}",
            abs((top * 2 + leafH) - outerH) <= 1
        )
    }

    @Test
    fun `no glyph is clipped or overlaps at 50 100 130 150 200`() {
        val tree = Tree(context)
        assertTrue(IosSlotLayout.registerMobile(tree.signal, null))
        assertTrue(IosSlotLayout.registerWifi(tree.wifiSignal))
        assertTrue(IosSlotLayout.registerBattery(tree.battery, tree.batteryLeaf))

        for (scale in listOf(50, 100, 130, 150, 200)) {
            IosSlotLayout.reapply(
                ModuleSettings.DEFAULT.copy(
                    iosWifiScale = scale,
                    iosCellScale = scale,
                    iosBatteryScale = scale
                )
            )
            layout(tree.root)

            // The leaf is wholly inside its frame, so no ancestor needs to clip it.
            assertInside(tree.wifiSignal, tree.wifiCombo)
            assertInside(tree.signal, tree.mobileCombo)
            assertInside(tree.batteryLeaf, tree.battery)

            // Wi-Fi ink ends before the battery slot starts; the gap is the equal slot gap.
            val wifiLeafLeft = tree.wifiOuter.left + tree.wifiGroup.left + tree.wifiCombo.left +
                (tree.wifiSignal.layoutParams as FrameLayout.LayoutParams).leftMargin
            val wifiInkLeftAbs = wifiLeafLeft +
                IosGeometry.inkBox(IosIconKind.WIFI, IosVisual(part = IosPart.WIFI)).left *
                IosSlotLayout.unit(1272) * scale / 100f
            val wifiInkRightAbs = wifiInkLeftAbs +
                IosGeometry.inkBox(IosIconKind.WIFI, IosVisual(part = IosPart.WIFI)).width *
                IosSlotLayout.unit(1272) * scale / 100f
            val batteryLeafLeft = tree.battery.left +
                (tree.batteryLeaf.layoutParams as FrameLayout.LayoutParams).leftMargin
            val batteryInkLeftAbs = batteryLeafLeft +
                IosGeometry.Battery.BODY_LEFT * IosSlotLayout.unit(1272) * scale / 100f
            assertTrue(
                "wifi ink $wifiInkLeftAbs..$wifiInkRightAbs ends before battery ink $batteryInkLeftAbs",
                wifiInkRightAbs <= batteryInkLeftAbs
            )
        }
    }

    private fun assertInside(child: View, parent: View) {
        assertTrue("${child.javaClass.simpleName} left ${child.left} < 0", child.left >= 0)
        assertTrue(child.right <= parent.width)
        assertTrue(child.top >= 0)
        assertTrue(child.bottom <= parent.height)
    }
}
