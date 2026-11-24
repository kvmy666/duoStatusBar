package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * R11 step 3 (sliders): the live `IosSlotLayout` boxes must follow the Wi-Fi and cell sliders on top
 * of the step-1 host base (`1.08 x` the clock cap = 33.48 px here, gap 16.74 px).
 *
 * The Wi-Fi case is reproduced first: the measured OxygenOS `bindEx` after-hook runs before the
 * image coroutine writes `wifi_signal`, so the first registration sees the stock drawable and the
 * bounded retry (0/50/150/400 ms) is what makes the slider reach the live view.
 *
 * Every case asserts the production `applySlot` path (via `registerWifi`/`registerMobile` + `reapply`)
 * against both the hard-coded absolute px at 70/100/150 % and the pure `metricsAtInk` /
 * `mobileMetricsAtInk` functions, so the test is not merely self-referential.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosSliderRegistrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val wifiVisual = IosVisual(part = IosPart.WIFI, wifiLevel = 3f)

    @Before
    fun setUp() {
        IosExtras.resetForTest()
        IosSlotLayout.resetForTest()
        IosSlotLayout.screenWidthOverrideForTest = 1272
        // Step 1 base: the host clock cap on the owner's phone (31 px cap -> 33.48 px ink).
        IosSlotLayout.hostInkOverrideForTest = BASE_INK
        IosExtras.idLookupForTest = { name -> if (name == "wifi_signal") WIFI_SIGNAL_ID else 0 }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        IosSlotLayout.restoreAll()
        IosSlotLayout.resetForTest()
        IosExtras.resetForTest()
    }

    private fun wifiTree(): Triple<FrameLayout, FrameLayout, ImageView> {
        val outer = FrameLayout(context)
        val group = LinearLayout(context)
        val combo = FrameLayout(context)
        val signal = ImageView(context)
        outer.layoutParams = FrameLayout.LayoutParams(70, 63)
        group.addView(combo, LinearLayout.LayoutParams(70, 51))
        combo.addView(signal, FrameLayout.LayoutParams(70, 51))
        outer.addView(group, FrameLayout.LayoutParams(70, 63))
        signal.id = WIFI_SIGNAL_ID
        signal.setImageDrawable(ColorDrawable(0xFFFFFFFF.toInt()))
        return Triple(outer, combo, signal)
    }

    private fun mobileTree(cellVisual: IosVisual): Triple<FrameLayout, FrameLayout, ImageView> {
        val outer = FrameLayout(context)
        val group = LinearLayout(context)
        val combo = FrameLayout(context)
        val signal = ImageView(context)
        outer.layoutParams = FrameLayout.LayoutParams(67, 47)
        combo.addView(signal, FrameLayout.LayoutParams(67, 47))
        group.addView(combo, LinearLayout.LayoutParams(67, 47))
        outer.addView(group, FrameLayout.LayoutParams(67, 47))
        signal.setImageDrawable(
            IosDrawables.create(
                IosIconKind.CELL,
                cellVisual,
                67, 47, relativeSize = false, captureColorFilter = true
            )
        )
        return Triple(outer, combo, signal)
    }

    /** One absolute row of the 70/100/150 % table, computed from the pure metrics. */
    private data class Expected(
        val scale: Int,
        val leafWidth: Int,
        val leafHeight: Int,
        val outerWidth: Int,
        val outerHeight: Int
    )

    private fun assertWifiBox(outer: FrameLayout, signal: ImageView, row: Expected) {
        val metrics = IosSlotLayout.metricsAtInk(
            IosIconKind.WIFI, wifiVisual, BASE_INK, GAP, row.scale, 0, ROW
        )
        // Cross-check: the pure function agrees with the hard-coded table.
        assertEquals("metrics outer width @${row.scale}", row.outerWidth, metrics.outerWidth)
        assertEquals("metrics outer height @${row.scale}", row.outerHeight, metrics.outerHeight)
        assertEquals("metrics leaf width @${row.scale}", row.leafWidth, metrics.leaf.width)
        assertEquals("metrics leaf height @${row.scale}", row.leafHeight, metrics.leaf.height)

        // Production applySlot wrote exactly these px onto the live stock views.
        assertEquals("outer width @${row.scale}", row.outerWidth, outer.layoutParams.width)
        assertEquals("outer height @${row.scale}", row.outerHeight, outer.layoutParams.height)
        assertArrayEquals(
            "outer target @${row.scale}",
            intArrayOf(row.outerWidth, row.outerHeight),
            IosSlotLayout.targetForTest(outer)
        )
        assertEquals("leaf width @${row.scale}", row.leafWidth, signal.layoutParams.width)
        assertEquals("leaf height @${row.scale}", row.leafHeight, signal.layoutParams.height)
    }

    private fun assertCellScales(visual: IosVisual, table: List<Expected>) {
        val (outer, _, signal) = mobileTree(visual)
        assertTrue(IosSlotLayout.registerMobile(signal))
        for (row in table) {
            IosSlotLayout.reapply(ModuleSettings.DEFAULT.copy(iosCellScale = row.scale))
            val metrics = IosSlotLayout.mobileMetricsAtInk(
                visual, null, BASE_INK, GAP, row.scale, 0, 100, 0, ROW
            )
            // Cross-check: the pure function agrees with the hard-coded table.
            assertEquals("metrics outer width @${row.scale}", row.outerWidth, metrics.outerWidth)
            assertEquals("metrics outer height @${row.scale}", row.outerHeight, metrics.outerHeight)
            assertEquals("metrics leaf width @${row.scale}", row.leafWidth, metrics.leaf.width)
            assertEquals("metrics leaf height @${row.scale}", row.leafHeight, metrics.leaf.height)

            // Production registerMobile -> reapply wrote exactly these px.
            assertEquals("outer width @${row.scale}", row.outerWidth, outer.layoutParams.width)
            assertEquals("outer height @${row.scale}", row.outerHeight, outer.layoutParams.height)
            assertArrayEquals(
                "outer target @${row.scale}",
                intArrayOf(row.outerWidth, row.outerHeight),
                IosSlotLayout.targetForTest(outer)
            )
            assertEquals("leaf width @${row.scale}", row.leafWidth, signal.layoutParams.width)
            assertEquals("leaf height @${row.scale}", row.leafHeight, signal.layoutParams.height)
        }
    }

    @Test
    fun `wifi slider registration reproduces the bind race and recovers on retry`() {
        val (outer, _, signal) = wifiTree()
        IosSlotLayout.reapply(ModuleSettings.DEFAULT.copy(iosWifiScale = 98))

        // The stock drawable is what `bindEx`'s after-hook sees: no slot, no target.
        assertFalse(IosExtras.registerWifiFrom(outer))
        assertEquals(0, IosSlotLayout.registeredForTest)
        assertNull(IosSlotLayout.targetForTest(outer))

        // The image coroutine lands afterwards; the bounded retry must register the live slot.
        signal.setImageDrawable(
            IosDrawables.create(
                IosIconKind.WIFI,
                wifiVisual,
                70, 51, relativeSize = false, captureColorFilter = true
            )
        )
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

        assertEquals(1, IosSlotLayout.registeredForTest)
        assertWifiBox(outer, signal, Expected(98, 44, 33, 61, 63))

        IosSlotLayout.reapply(ModuleSettings.DEFAULT.copy(iosWifiScale = 100))
        assertWifiBox(outer, signal, Expected(100, 45, 34, 61, 63))
        IosSlotLayout.reapply(ModuleSettings.DEFAULT.copy(iosWifiScale = 70))
        assertWifiBox(outer, signal, Expected(70, 31, 24, 48, 63))
        IosSlotLayout.reapply(ModuleSettings.DEFAULT.copy(iosWifiScale = 150))
        assertWifiBox(outer, signal, Expected(150, 67, 50, 84, 63))
    }

    @Test
    fun `single cell slider resizes the live leaf and outer at 70 100 150`() {
        val visual = IosVisual(part = IosPart.CELL, cellMode = IosCellMode.SINGLE, cellBars = 4f)
        assertCellScales(
            visual,
            listOf(
                Expected(70, 38, 27, 53, 63),
                Expected(100, 54, 38, 68, 63),
                Expected(150, 81, 57, 94, 63)
            )
        )
    }

    @Test
    fun `stacked dual cell slider resizes the live leaf and outer at 70 100 150`() {
        val visual = IosVisual(
            part = IosPart.CELL,
            cellMode = IosCellMode.DUAL,
            cellRole = IosCellRole.BOTH,
            cellBars = 4f,
            cellDots = 1f
        )
        assertCellScales(
            visual,
            listOf(
                Expected(70, 34, 24, 50, 63),
                Expected(100, 49, 34, 65, 63),
                Expected(150, 73, 51, 89, 63)
            )
        )
    }

    @Test
    fun `a registration exception warns once, not per bind`() {
        val (outer, _, _) = wifiTree()
        IosExtras.registerWifiForTest = { throw IllegalStateException("boom") }

        assertFalse(IosExtras.registerWifiFrom(outer))
        assertFalse(IosExtras.registerWifiFrom(outer))

        assertEquals(1, IosExtras.warningCountForTest)
    }

    private companion object {
        const val WIFI_SIGNAL_ID = 0x7f0f0001
        const val BASE_INK = 33.48f
        const val GAP = 16.74f
        const val ROW = 63
    }
}
