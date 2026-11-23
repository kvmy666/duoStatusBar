package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    @Before
    fun setUp() {
        IosSlotLayout.resetForTest()
        IosSlotLayout.screenWidthOverrideForTest = 1272
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
        assertEquals(77, lp(tree.wifiOuter).width)
        assertEquals(63, lp(tree.wifiOuter).height)
        assertEquals(77, lp(tree.wifiGroup).width)
        assertEquals(77, lp(tree.wifiCombo).width)
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
    fun `live settings change the real wifi size without replacing the drawable`() {
        val tree = Tree(context)
        assertTrue(IosSlotLayout.registerWifi(tree.wifiSignal))
        val drawable = tree.wifiSignal.drawable

        IosSlotLayout.reapply(ModuleSettings.DEFAULT.copy(iosWifiScale = 200))
        assertEquals(133, lp(tree.wifiOuter).width)
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

        assertEquals(157, lp(tree.mobileOuter).width)
        assertEquals(67, lp(tree.signal).width)
        assertEquals(53, lp(tree.label).width)
        assertEquals(94, (tree.label.layoutParams as FrameLayout.LayoutParams).leftMargin)
        // GONE wifi must not add a second slot to the container.
        assertEquals(157, lp(tree.container).width)
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
