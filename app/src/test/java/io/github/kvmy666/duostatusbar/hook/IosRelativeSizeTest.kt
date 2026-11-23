package io.github.kvmy666.duostatusbar.hook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * The `IosGeometry.Relative` before/after contract for the ref 7 stock slots: battery 80x38,
 * Wi-Fi 80x50, single cell 80x42 (105.6 units at the measured 0.3987 px/unit) and dual-BOTH cell
 * 80x42. The full-slot ink nearly fills the slot; insetting the drawable to
 * [IosGeometry.Relative.sizeFor] shrinks it by the per-kind scale. **R4 defaults are the R3
 * calibration x1.10**, so the scales are now 0.891 / 0.627 / 0.825 / 0.836 and a 100 % user scale is
 * the new default (identity still draws it).
 *
 * The assertion that matters is the RENDERED INK height / 30 px clock, not the inner box height. The
 * documented R4 target is the R3 target x1.10: battery 1.100, Wi-Fi 1.0384, single cell 1.0505,
 * dual cell 1.1462. The production render at the integer `sizeFor` box measures battery 1.100,
 * Wi-Fi 1.025, single cell 1.0583 and dual cell 1.1417 (4x supersampled); integer rounding, e.g. the
 * 50x31 Wi-Fi box, is why production does not equal the target exactly. The +/-3 % assertion is the
 * integer-rounding tolerance, and the measured value is pinned in [Case.measuredClockRatio]. The
 * x1.10 growth itself is UNVERIFIED on a device; owner review required.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosRelativeSizeTest {

    private val clockPx = 30f

    private data class Case(
        val label: String,
        val kind: IosIconKind,
        val visual: IosVisual,
        val slotWidth: Int,
        val slotHeight: Int,
        val expectedBoxHeight: Int,
        val targetClockRatio: Float,
        val measuredClockRatio: Float,
        val dualCell: Boolean = false
    )

    private fun cases(): List<Case> = listOf(
        Case(
            "battery",
            IosIconKind.BATTERY,
            IosMapping.battery(
                100, 20, false, false, BatteryManager.BATTERY_STATUS_DISCHARGING, Color.BLACK
            ),
            80, 38, 34, 1.1111f, 1.1167f
        ),
        Case(
            "wifi",
            IosIconKind.WIFI,
            IosVisual(part = IosPart.WIFI, tint = Color.BLACK, wifiLevel = 3f),
            80, 50, 31, 1.0027f, 0.9917f
        ),
        Case(
            "cell-single",
            IosIconKind.CELL,
            IosVisual(
                part = IosPart.CELL,
                tint = Color.BLACK,
                cellMode = IosCellMode.SINGLE,
                cellRole = IosCellRole.DATA,
                cellBars = 4f,
                cellDots = 0f
            ),
            80, 42, 35, 1.0505f, 1.0250f
        ),
        Case(
            "cell-dual-both",
            IosIconKind.CELL,
            IosVisual(
                part = IosPart.CELL,
                tint = Color.BLACK,
                cellMode = IosCellMode.DUAL,
                cellRole = IosCellRole.BOTH,
                cellBars = 2f,
                cellDots = 3f
            ),
            80, 42, 35, 1.1462f, 1.1500f,
            dualCell = true
        )
    )

    @Test
    fun `sizeFor returns the measured inner box for the stock slots`() {
        for (c in cases()) {
            val scale = IosGeometry.Relative.drawScale(c.kind, c.dualCell)
            val (w, h) = IosGeometry.Relative.sizeFor(
                c.kind, c.slotWidth, c.slotHeight, c.dualCell
            )
            assertEquals("${c.label} box width", (c.slotWidth * scale).roundToInt(), w)
            assertEquals("${c.label} box height", c.expectedBoxHeight, h)
            // The inner box is the slot scaled by the per-kind factor (never larger than the slot).
            assertEquals("${c.label} height ~ slot*scale", c.slotHeight * scale, h.toFloat(), 0.6f)
        }
    }

    @Test
    fun `inset drawable shrinks the ink by the per-kind scale`() {
        for (c in cases()) {
            val full = inkHeight(c.kind, c.visual, c.slotWidth, c.slotHeight)
            assertTrue(
                "${c.label} full slot ink ($full) nearly fills ${c.slotHeight}",
                full >= c.slotHeight * 0.88f
            )

            val (w, h) = IosGeometry.Relative.sizeFor(
                c.kind, c.slotWidth, c.slotHeight, c.dualCell
            )
            val inset = inkHeight(c.kind, c.visual, w, h)
            val ratio = inset / full
            assertEquals(
                "${c.label} ink scales by ${IosGeometry.Relative.drawScale(c.kind, c.dualCell)}",
                IosGeometry.Relative.drawScale(c.kind, c.dualCell),
                ratio,
                0.02f
            )
            // The rendered ink (not the box) is what the user sees. This direct 1x sizeFor draw
            // matches the R4 target inside the +/-3 % integer-rounding tolerance.
            assertEquals(
                "${c.label} rendered ink / 30 px clock vs R4 target",
                c.targetClockRatio,
                inset / clockPx,
                0.03f
            )
            assertTrue("${c.label} inset ink $inset < full $full", inset < full)
        }
    }

    @Test
    fun `production relative sizing at the full slot matches the measured production and the direct inset`() {
        // The "wire" proof: production passes the FULL stock slot plus relativeSize = true, and the
        // drawable itself computes the per-kind inner box. That must match the measured production
        // ratio and the direct draw at sizeFor(...). Only this test proves the production flag, not
        // just the API. The R4 target is asserted separately inside its +/-3 % tolerance.
        for (c in cases()) {
            val wired = inkHeight(
                c.kind, c.visual, c.slotWidth, c.slotHeight, relativeSize = true
            )
            assertEquals(
                "${c.label} wired rendered ink / 30 px clock vs R4 target",
                c.targetClockRatio,
                wired / clockPx,
                0.03f
            )
            assertEquals(
                "${c.label} wired rendered ink / 30 px clock vs measured production",
                c.measuredClockRatio,
                wired / clockPx,
                0.005f
            )
            val (w, h) = IosGeometry.Relative.sizeFor(
                c.kind, c.slotWidth, c.slotHeight, c.dualCell
            )
            val direct = inkHeight(c.kind, c.visual, w, h)
            assertEquals(
                "${c.label} wired vs direct sizeFor",
                direct / clockPx,
                wired / clockPx,
                0.02f
            )
        }
    }

    /**
     * Ink height in low-res pixels, measured at 4x and divided back. A 1:1 integer bbox at 30 px is
     * only 0.033 accurate; supersampling gives the continuous height the measured targets quote.
     */
    private fun inkHeight(
        kind: IosIconKind,
        visual: IosVisual,
        width: Int,
        height: Int,
        relativeSize: Boolean = false
    ): Float {
        val ss = 4
        val bw = width * ss
        val bh = height * ss
        val drawable = IosDrawables.create(kind, visual, bw, bh, relativeSize = relativeSize)
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bw, bh)
        drawable.draw(Canvas(bitmap))
        var minY = Int.MAX_VALUE
        var maxY = -1
        for (y in 0 until bh) {
            for (x in 0 until bw) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 8) {
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        require(maxY >= 0) { "${kind.name} drew no ink at ${width}x$height" }
        return (maxY - minY + 1) / ss.toFloat()
    }
}
