package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * R9 §System-view layout: the pure slot formula at the owner's 1272 px width.
 *
 * At 1272 the design unit is `s = 0.4438` px and the equal slot gap is `G = 20.59` px. These tests pin
 * the target boxes for each glyph at 50/100/130/150/200 %, the label-mode mobile slot, the offset
 * clamp and the "ink never leaves its slot / never overlaps the neighbour" contract.
 */
class IosSlotLayoutTest {

    private val screenW = 1272
    private val s = IosSlotLayout.unit(screenW)
    private val gap = IosSlotLayout.gap(screenW)
    private val stockRowH = 63

    private fun wifi() = IosSlotLayout.metrics(
        IosIconKind.WIFI,
        IosVisual(part = IosPart.WIFI, wifiLevel = 3f),
        screenW, 100, 0, stockRowH
    )

    private fun cell() = IosSlotLayout.metrics(
        IosIconKind.CELL,
        IosVisual(part = IosPart.CELL, cellBars = 4f),
        screenW, 100, 0, stockRowH
    )

    private fun battery(scale: Int = 100, offset: Int = 0) = IosSlotLayout.metrics(
        IosIconKind.BATTERY,
        IosVisual(part = IosPart.BATTERY, batteryLevel = 1f),
        screenW, scale, offset, stockRowH
    )

    private fun labelMode(cellScale: Int = 100, cellOffset: Int = 0, labelScale: Int = 100) =
        IosSlotLayout.mobileMetrics(
            IosVisual(part = IosPart.CELL, cellBars = 4f),
            IosVisual(part = IosPart.WIFI, wifiSlot = IosWifiSlot.DataType("5G")),
            screenW, cellScale, cellOffset, labelScale, 0, stockRowH
        )

    /** The drawn ink box of [metrics]' trailing leaf in outer-slot pixels. */
    private fun inkWidth(kind: IosIconKind, visual: IosVisual, scale: Int): Float =
        IosGeometry.inkBox(kind, visual).width * s * scale / 100f

    @Test
    fun `design unit and gap are the owner's 1272 values`() {
        assertEquals(0.4438f, s, 0.0001f)
        assertEquals(20.59f, gap, 0.01f)
    }

    @Test
    fun `battery slot grows with the slider and never shrinks below the stock row`() {
        val at100 = battery()
        assertEquals(113, at100.outerWidth)
        assertEquals(63, at100.outerHeight)
        assertEquals(93, at100.leaf.width)
        assertEquals(45, at100.leaf.height)
        assertEquals(10, at100.leaf.left)

        val at200 = battery(scale = 200)
        assertEquals(205, at200.outerWidth)
        assertEquals(91, at200.outerHeight)
        assertEquals(186, at200.leaf.width)
        assertEquals(91, at200.leaf.height)
        assertEquals(9, at200.leaf.left)
        // 130/150/200: the slot grows, so the glyph cannot overdraw Wi-Fi.
        assertTrue(battery(scale = 130).outerWidth > at100.outerWidth)
        assertTrue(battery(scale = 150).outerWidth > battery(scale = 130).outerWidth)
        assertTrue(at200.outerWidth > battery(scale = 150).outerWidth)
    }

    @Test
    fun `wi-fi and cell slots match the design boxes at 100 percent`() {
        val wifi = wifi()
        // Restored 2.0.6 Wi-Fi ink 57.17 px at 1272: round(57.17 + 20.59) = 78.
        assertEquals(78, wifi.outerWidth)
        assertEquals(63, wifi.outerHeight)
        assertEquals(57, wifi.leaf.width)
        assertEquals(43, wifi.leaf.height)
        assertEquals(10, wifi.leaf.left)

        val cell = cell()
        assertEquals(84, cell.outerWidth)
        assertEquals(63, cell.outerHeight)
        assertEquals(67, cell.leaf.width)
        assertEquals(47, cell.leaf.height)
        assertEquals(10, cell.leaf.left)
    }

    @Test
    fun `label mode puts the label one full gap after the cell ink`() {
        val slot = labelMode()
        assertEquals(156, slot.outerWidth)
        assertEquals(63, slot.outerHeight)
        assertEquals(67, slot.leaf.width)
        assertEquals(47, slot.leaf.height)
        assertEquals(10, slot.leaf.left)
        val label = slot.label!!
        // The 5G artboard is the ink-left 116.921-unit ink + 2 x 1-unit margin = 118.921 units.
        assertEquals(53, label.width)
        assertEquals(43, label.height)
        assertEquals(94, label.left)
        assertEquals(10, label.top)
        // The label leaf ink at 1272 is 116.921 x 0.4438 = 51.89 px.
        assertEquals(
            51.89f,
            IosGeometry.inkBox(
                IosIconKind.WIFI,
                IosVisual(part = IosPart.WIFI, wifiSlot = IosWifiSlot.DataType("5G"))
            ).width * s,
            0.01f
        )

        val cellInk = inkWidth(IosIconKind.CELL, IosVisual(part = IosPart.CELL, cellBars = 4f), 100)
        val cellInkLeft = slot.leaf.left + IosGeometry.inkBox(IosIconKind.CELL, IosVisual(part = IosPart.CELL)).left * s
        val labelInkLeft = label.left + IosGeometry.inkBox(
            IosIconKind.WIFI,
            IosVisual(part = IosPart.WIFI, wifiSlot = IosWifiSlot.DataType("5G"))
        ).left * s
        assertTrue(abs((labelInkLeft - (cellInkLeft + cellInk)) - gap) <= 1.5f)
    }

    @Test
    fun `no ink leaves its own slot and no neighbour overlaps at 50 100 130 150 200`() {
        for (scale in listOf(50, 100, 130, 150, 200)) {
            val wifi = wifi(scale)
            val wInk = inkWidth(IosIconKind.WIFI, IosVisual(part = IosPart.WIFI), scale)
            assertTrue("wifi scale=$scale", wifi.leaf.left + wInk <= wifi.outerWidth + 1)

            val cell = cell(scale)
            val cInk = inkWidth(IosIconKind.CELL, IosVisual(part = IosPart.CELL, cellBars = 4f), scale)
            assertTrue("cell scale=$scale", cell.leaf.left + cInk <= cell.outerWidth + 1)

            val battery = battery(scale)
            val bInk = inkWidth(IosIconKind.BATTERY, IosVisual(part = IosPart.BATTERY), scale)
            assertTrue("battery scale=$scale", battery.leaf.left + bInk <= battery.outerWidth + 1)

            // The slots abut; the ink gap is exactly G because each ink has G/2 padding in its slot.
            val wifiInk = IosGeometry.inkBox(IosIconKind.WIFI, IosVisual(part = IosPart.WIFI))
            val leftPad = wifi.leaf.left + wifiInk.left * s * scale / 100f
            val rightPad = wifi.outerWidth - (wifi.leaf.left + wInk)
            assertEquals(gap, leftPad + rightPad, 1.5f)
        }
    }

    @Test
    fun `a user offset is clamped inside half the slot gap`() {
        for (scale in listOf(50, 100, 130, 150, 200)) {
            for (percent in listOf(-25, -10, 0, 10, 25)) {
                val slot = battery(scale, percent)
                val inkLeftUnits = IosGeometry.Battery.BODY_LEFT * s * scale / 100f
                val dx = slot.leaf.left - (gap / 2f - inkLeftUnits)
                assertTrue("dx=$dx scale=$scale offset=$percent", abs(dx) <= gap / 2f + 1f)
                assertTrue(slot.leaf.left >= 0)
            }
        }
    }

    private fun wifi(scale: Int): IosSlotMetrics = IosSlotLayout.metrics(
        IosIconKind.WIFI, IosVisual(part = IosPart.WIFI), screenW, scale, 0, stockRowH
    )

    private fun cell(scale: Int): IosSlotMetrics = IosSlotLayout.metrics(
        IosIconKind.CELL, IosVisual(part = IosPart.CELL, cellBars = 4f), screenW, scale, 0, stockRowH
    )

    @Test
    fun `battery 50 and 150 always read as whole artboard px`() {
        val low = battery(scale = 50)
        assertEquals(67, low.outerWidth)
        assertEquals(63, low.outerHeight)
        assertEquals(46, low.leaf.width)
        assertEquals(23, low.leaf.height)

        val high = battery(scale = 150)
        assertEquals(159, high.outerWidth)
        assertEquals(68, high.outerHeight)
        assertEquals(139, high.leaf.width)
        assertEquals(68, high.leaf.height)
    }

    @Test
    fun `200 percent outers grow and every leaf stays centred in its own outer`() {
        val cellVisual = IosVisual(part = IosPart.CELL, cellBars = 4f)
        val labelVisual = IosVisual(part = IosPart.WIFI, wifiSlot = IosWifiSlot.DataType("5G"))

        // The cell artboard is 105.6 units, so 200 % is 94 px and fills its own outer.
        val cell200 = IosSlotLayout.metrics(IosIconKind.CELL, cellVisual, screenW, 200, 0, stockRowH)
        assertEquals(94, cell200.outerHeight)
        assertEquals(133, cell200.leaf.width)
        assertEquals(94, cell200.leaf.height)
        assertEquals(0, cell200.leaf.top)
        assertCentred(cell200.leaf, cell200.outerHeight)

        // The battery artboard is 102 units: 45 px at 100 %, 91 px at 200 % (above R9's 63 px row).
        val battery200 = battery(scale = 200)
        assertEquals(91, battery200.outerHeight)
        assertEquals(186, battery200.leaf.width)
        assertEquals(91, battery200.leaf.height)
        assertEquals(0, battery200.leaf.top)
        assertCentred(battery200.leaf, battery200.outerHeight)

        // The realistic dump mix: 200 % cell, 98 % label (Wi-Fi slot), 100 % battery.
        val mobile = IosSlotLayout.mobileMetrics(cellVisual, labelVisual, screenW, 200, 0, 98, 0, stockRowH)
        assertEquals(94, mobile.outerHeight)
        assertEquals(133, mobile.leaf.width)
        assertEquals(94, mobile.leaf.height)
        assertEquals(0, mobile.leaf.top)
        val label = mobile.label!!
        assertEquals(52, label.width)
        assertEquals(42, label.height)
        assertEquals(26, label.top)
        assertCentred(mobile.leaf, mobile.outerHeight)
        assertCentred(label, mobile.outerHeight)

        val battery100 = battery()
        assertEquals(63, battery100.outerHeight)
        assertEquals(93, battery100.leaf.width)
        assertEquals(45, battery100.leaf.height)
        assertEquals(9, battery100.leaf.top)
        assertCentred(battery100.leaf, battery100.outerHeight)

        val wifi98 = wifi(98)
        assertEquals(63, wifi98.outerHeight)
        assertEquals(56, wifi98.leaf.width)
        assertEquals(42, wifi98.leaf.height)
        assertEquals(11, wifi98.leaf.top)
        assertCentred(wifi98.leaf, wifi98.outerHeight)
    }

    /** `top = round((outerH - leafH) / 2)`, so the doubled top plus the leaf height matches within 1 px. */
    private fun assertCentred(leaf: IosSlotBox, outerHeight: Int) {
        assertTrue(
            "leaf ${leaf.height}px at top ${leaf.top} is centred in $outerHeight",
            abs((leaf.top * 2 + leaf.height) - outerHeight) <= 1
        )
    }

    @Test
    fun `label mode with a scaled cell keeps the label at one gap after the scaled ink`() {
        val slot = labelMode(cellScale = 200)
        val cellInk = IosGeometry.inkBox(IosIconKind.CELL, IosVisual(part = IosPart.CELL)).width * s * 2f
        val labelInkLeft = slot.label!!.left + IosGeometry.inkBox(
            IosIconKind.WIFI,
            IosVisual(part = IosPart.WIFI, wifiSlot = IosWifiSlot.DataType("5G"))
        ).left * s
        val cellInkLeft = slot.leaf.left + IosGeometry.inkBox(IosIconKind.CELL, IosVisual(part = IosPart.CELL)).left * s * 2f
        assertTrue(abs((labelInkLeft - (cellInkLeft + cellInk)) - gap) <= 1.5f)
        assertEquals((cellInk + gap + 51.89f + gap).roundToInt(), slot.outerWidth)
    }
}
