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
 * R11 step 3 (sliders): the live `IosSlotLayout` boxes must follow the Wi-Fi and cell sliders.
 *
 * The Wi-Fi case is reproduced first: the measured OxygenOS `bindEx` after-hook runs before the
 * image coroutine writes `wifi_signal`, so the first registration sees the stock drawable and the
 * bounded retry (0/50/150/400 ms) is what makes the slider reach the live view.
 *
 * The mobile cases prove the existing cell registration: single and stacked dual (`DUAL`/`BOTH`)
 * leaves/outers must match [IosSlotLayout.metrics] at 70/100/150 %.
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

    private fun assertWifiBox(outer: FrameLayout, signal: ImageView, scale: Int) {
        val metrics = IosSlotLayout.metrics(
            IosIconKind.WIFI, wifiVisual, 1272, scale, 0, 63
        )
        assertEquals("outer width @$scale", metrics.outerWidth, outer.layoutParams.width)
        assertEquals("outer height @$scale", metrics.outerHeight, outer.layoutParams.height)
        assertArrayEquals(
            "outer target @$scale",
            intArrayOf(metrics.outerWidth, metrics.outerHeight),
            IosSlotLayout.targetForTest(outer)
        )
        assertEquals("leaf width @$scale", metrics.leaf.width, signal.layoutParams.width)
        assertEquals("leaf height @$scale", metrics.leaf.height, signal.layoutParams.height)
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
        assertWifiBox(outer, signal, 98)

        IosSlotLayout.reapply(ModuleSettings.DEFAULT.copy(iosWifiScale = 100))
        assertWifiBox(outer, signal, 100)
        IosSlotLayout.reapply(ModuleSettings.DEFAULT.copy(iosWifiScale = 70))
        assertWifiBox(outer, signal, 70)
        IosSlotLayout.reapply(ModuleSettings.DEFAULT.copy(iosWifiScale = 150))
        assertWifiBox(outer, signal, 150)
    }

    @Test
    fun `single cell slider resizes the live leaf and outer at 70 100 150`() {
        val visual = IosVisual(part = IosPart.CELL, cellBars = 4f)
        assertCellScales(visual)
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
        assertCellScales(visual)
    }

    private fun assertCellScales(visual: IosVisual) {
        val (outer, _, signal) = mobileTree(visual)
        assertTrue(IosSlotLayout.registerMobile(signal))
        for (scale in listOf(70, 100, 150)) {
            IosSlotLayout.reapply(ModuleSettings.DEFAULT.copy(iosCellScale = scale))
            val metrics = IosSlotLayout.metrics(IosIconKind.CELL, visual, 1272, scale, 0, 63)
            assertEquals("outer width @$scale", metrics.outerWidth, outer.layoutParams.width)
            assertEquals("outer height @$scale", metrics.outerHeight, outer.layoutParams.height)
            assertArrayEquals(
                "outer target @$scale",
                intArrayOf(metrics.outerWidth, metrics.outerHeight),
                IosSlotLayout.targetForTest(outer)
            )
            assertEquals("leaf width @$scale", metrics.leaf.width, signal.layoutParams.width)
            assertEquals("leaf height @$scale", metrics.leaf.height, signal.layoutParams.height)
        }
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
    }
}
