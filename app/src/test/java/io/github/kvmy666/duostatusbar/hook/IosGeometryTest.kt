package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the R9 measured proportions that both renderers consume. A drift here is a silent visual
 * regression, so each family is checked against `.pm/out-r9/R9-design.md` (body H = 100).
 */
class IosGeometryTest {

    @Test
    fun `battery body matches the R9 aspect and radius`() {
        val b = IosGeometry.Battery
        assertEquals(1.893f, b.BODY_W / b.BODY_H, 0.002f)
        assertEquals(0.286f, b.BODY_RADIUS / b.BODY_H, 0.002f)
        assertEquals(0.143f, b.CAP_W / b.BODY_H, 0.001f)
        assertEquals(0.321f, b.CAP_H / b.BODY_H, 0.001f)
        assertEquals(0.036f, b.CAP_GAP / b.BODY_H, 0.001f)
        assertEquals(209.2f, b.ARTBOARD_W, 0.001f)
        assertEquals(102f, b.ARTBOARD_H, 0.001f)
        assertEquals(193.9f, b.CAP_LEFT, 0.001f)
        assertEquals(34.95f, b.CAP_TOP, 0.001f)
        // The artboard contains the body and the nub with a 1-unit margin on both sides.
        assertTrue(b.CAP_LEFT + b.CAP_W <= b.ARTBOARD_W)
        assertTrue(b.BODY_TOP + b.BODY_H <= b.ARTBOARD_H - 1f)
    }

    @Test
    fun `battery fill clamps and follows the level`() {
        assertEquals(0f, IosGeometry.Battery.fillWidth(-0.2f), 0.0001f)
        assertEquals(0f, IosGeometry.Battery.fillWidth(0f), 0.0001f)
        assertEquals(
            IosGeometry.Battery.BODY_W * 0.586f,
            IosGeometry.Battery.fillWidth(0.586f),
            0.0001f
        )
        assertEquals(IosGeometry.Battery.BODY_W, IosGeometry.Battery.fillWidth(1f), 0.0001f)
        assertEquals(IosGeometry.Battery.BODY_W, IosGeometry.Battery.fillWidth(1.4f), 0.0001f)
    }

    @Test
    fun `single cell bars use the R9 heights and baseline`() {
        val heights = IosGeometry.Cell.SINGLE_BAR_HEIGHTS
        assertEquals(4, heights.size)
        assertEquals(listOf(46.4f, 60.7f, 75.0f, 92.9f), heights.toList())
        heights.toList().zipWithNext().forEach { (a, b) -> assertTrue(b > a) }
        assertEquals(0.929f, heights.last() / IosGeometry.UNIT_HEIGHT, 0.001f)
        assertEquals(93.9f, IosGeometry.Cell.SINGLE_BAR_BOTTOM, 0.001f)
        assertEquals(IosGeometry.Cell.TOP + heights.last(), IosGeometry.Cell.SINGLE_BAR_BOTTOM, 0.001f)
        assertEquals(25f, IosGeometry.Cell.BAR_W, 0.001f)
        assertEquals(39.3f, IosGeometry.Cell.PITCH, 0.001f)
        assertEquals(11.75f, IosGeometry.Cell.CORNER_RADIUS, 0.001f)
        assertEquals(142.9f, IosGeometry.Cell.BLOCK_W, 0.001f)
    }

    @Test
    fun `dual cell rows are compressed and keep the R9 block`() {
        val c = IosGeometry.Cell
        val heights = c.DUAL_BAR_HEIGHTS
        assertEquals(listOf(37.9f, 46.1f, 52.9f, 64.3f), heights.toList())
        heights.toList().zipWithNext().forEach { (a, b) -> assertTrue(b > a) }
        assertTrue(heights.last() < c.SINGLE_BAR_HEIGHTS.last())
        assertEquals(10f, c.DUAL_GAP, 0.0001f)
        assertEquals(27f, c.DUAL_BAR_W, 0.001f)
        assertEquals(40.3f, c.DUAL_PITCH, 0.001f)
        assertEquals(147.9f, c.DUAL_BLOCK_W, 0.001f)
        assertEquals(
            c.DUAL_ROW_H + c.DUAL_GAP + c.DOT_H,
            c.DUAL_BLOCK_H,
            0.0001f
        )
        assertEquals(103.3f, c.DUAL_BLOCK_H, 0.001f)
        assertEquals(105.6f, c.ARTBOARD_H, 0.0001f)
        assertEquals(75.3f, c.DOT_TOP, 0.0001f)
        assertEquals(27f, c.DOT_W, 0.001f)
        assertEquals(29f, c.DOT_H, 0.001f)
        assertEquals(12.69f, c.DUAL_CORNER_RADIUS, 0.001f)
        assertTrue(c.DOT_TOP + c.DOT_H <= c.ARTBOARD_H)
    }

    @Test
    fun `dual row artboards fill a slot without shrinking the shared block`() {
        val c = IosGeometry.Cell
        assertEquals(c.ARTBOARD_W - (c.DUAL_BLOCK_W + c.LEFT), 1.2f, 0.01f)
        assertEquals(c.DUAL_BLOCK_W + 2f, c.ROW_ARTBOARD_W, 0.0001f)
        assertEquals(149.9f, c.ROW_ARTBOARD_W, 0.001f)
        assertEquals(c.DUAL_ROW_H + 2f, c.DATA_ROW_ARTBOARD_H, 0.0001f)
        assertEquals(66.3f, c.DATA_ROW_ARTBOARD_H, 0.001f)
        assertEquals(c.DOT_H + 2f, c.OTHER_ROW_ARTBOARD_H, 0.0001f)
        assertEquals(31f, c.OTHER_ROW_ARTBOARD_H, 0.001f)
        assertEquals(-(c.DOT_TOP - c.TOP), c.otherRowOffsetY(), 0.0001f)
        assertEquals(-74.3f, c.otherRowOffsetY(), 0.001f)
        assertEquals(c.TOP, c.DOT_TOP + c.otherRowOffsetY(), 0.0001f)
        assertEquals(
            c.OTHER_ROW_ARTBOARD_H - 1f,
            c.DOT_TOP + c.otherRowOffsetY() + c.DOT_H,
            0.0001f
        )
    }

    @Test
    fun `inactive alpha and the ink luminance rule match R9`() {
        assertEquals(0.25f, IosGeometry.Cell.INACTIVE_ALPHA, 0.0001f)
        assertEquals(0.30f, IosGeometry.Battery.REMAINDER_ALPHA_DARK_INK, 0.0001f)
        assertEquals(0.50f, IosGeometry.Battery.REMAINDER_ALPHA_LIGHT_INK, 0.0001f)
        assertEquals(0.30f, IosGeometry.remainderAlpha(0xFF000000.toInt()), 0.0001f)
        assertEquals(0.50f, IosGeometry.remainderAlpha(0xFFFFFFFF.toInt()), 0.0001f)
        assertEquals(4, IosGeometry.Wifi.OUTER_ALPHA.size)
        assertEquals(4, IosGeometry.Wifi.INNER_ALPHA.size)
        assertEquals(1f, IosGeometry.Wifi.OUTER_ALPHA[3], 0.0001f)
        assertEquals(1f, IosGeometry.Wifi.INNER_ALPHA[3], 0.0001f)
    }

    @Test
    fun `wifi uses the R9 measured geometry`() {
        val w = IosGeometry.Wifi
        assertEquals(129.53f, w.ARTBOARD_W, 0.001f)
        assertEquals(96.77f, w.ARTBOARD_H, 0.001f)
        assertEquals(1f, w.ARTBOARD_MARGIN, 0.0001f)

        assertEquals(64.809f, w.CENTRE_X, 0.001f)
        assertEquals(95.022f, w.CENTRE_Y, 0.001f)
        assertEquals(81.2f, w.OUTER_RADIUS, 0.001f)
        assertEquals(18.6f, w.OUTER_STROKE, 0.001f)
        assertEquals(83.727f, w.OUTER_TRIM_END * 360f, 0.01f)
        assertEquals(318.303f, w.OUTER_TRIM_OFFSET * 360f, 0.01f)
        assertEquals(47.4f, w.INNER_RADIUS, 0.001f)
        assertEquals(19.4f, w.INNER_STROKE, 0.001f)
        assertEquals(79.228f, w.INNER_TRIM_END * 360f, 0.01f)
        assertEquals(320.370f, w.INNER_TRIM_OFFSET * 360f, 0.01f)

        assertEquals(32, w.DOT_POINTS.size / 2)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in w.DOT_POINTS.indices step 2) {
            val x = w.DOT_POINTS[i]
            val y = w.DOT_POINTS[i + 1]
            assertTrue("x=$x", x >= w.ARTBOARD_MARGIN && x <= w.ARTBOARD_W - w.ARTBOARD_MARGIN)
            assertTrue("y=$y", y >= w.ARTBOARD_MARGIN && y <= w.ARTBOARD_H - w.ARTBOARD_MARGIN)
            minX = minOf(minX, x); maxX = maxOf(maxX, x)
            minY = minOf(minY, y); maxY = maxOf(maxY, y)
        }
        assertEquals(45.809f, minX, 0.01f)
        assertEquals(83.809f, maxX, 0.01f)
        assertEquals(67.77f, minY, 0.01f)
        assertEquals(95.77f, maxY, 0.01f)
        assertEquals(64.809f, (minX + maxX) / 2f, 0.01f)
        assertEquals(38f, maxX - minX, 0.01f)
        assertEquals(28f, maxY - minY, 0.01f)
    }

    @Test
    fun `battery nub is the R9 25-gon inside the artboard`() {
        val b = IosGeometry.Battery
        assertEquals(25, b.CAP_POINTS.size / 2)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in b.CAP_POINTS.indices step 2) {
            val x = b.CAP_POINTS[i]
            val y = b.CAP_POINTS[i + 1]
            assertTrue("x=$x", x >= 1f && x <= b.ARTBOARD_W)
            assertTrue("y=$y", y >= 1f && y <= b.ARTBOARD_H - 1f)
            minX = minOf(minX, x); maxX = maxOf(maxX, x)
            minY = minOf(minY, y); maxY = maxOf(maxY, y)
        }
        assertEquals(b.CAP_LEFT, minX, 0.01f)
        assertEquals(b.CAP_LEFT + b.CAP_W, maxX, 0.01f)
        assertEquals(b.CAP_TOP, minY, 0.01f)
        assertEquals(b.CAP_TOP + b.CAP_H, maxY, 0.01f)
    }

    @Test
    fun `strip gaps and width match the R9 composition`() {
        assertEquals(57.5f, IosGeometry.Strip.CELL_WIFI_GAP, 0.0001f)
        assertEquals(59.77f, IosGeometry.Strip.WIFI_BATTERY_GAP, 0.0001f)
        assertEquals(594.9f, IosGeometry.Strip.WIDTH, 0.1f)
        assertEquals(
            IosGeometry.Cell.BLOCK_W + IosGeometry.Strip.CELL_WIFI_GAP,
            IosGeometry.Strip.WIFI_X,
            0.0001f
        )
        assertEquals(
            IosGeometry.Strip.WIFI_X + 127.53f + IosGeometry.Strip.WIFI_BATTERY_GAP,
            IosGeometry.Strip.BATTERY_X,
            0.0001f
        )
    }

    @Test
    fun `label strip uses the measured ink gaps and label X formulas`() {
        val s = IosGeometry.Strip
        val d = IosGeometry.DataLabel
        val b = IosGeometry.Battery

        assertEquals(52.07f, s.CELL_LABEL_GAP, 0.0001f)
        assertEquals(54.04f, s.LABEL_BATTERY_GAP, 0.0001f)

        // The label strip's cell is the dual block; the label ink starts at +1 margin.
        assertEquals(IosGeometry.Cell.DUAL_BLOCK_W + s.CELL_LABEL_GAP, s.LABEL_X, 0.0001f)
        val labelInk = requireNotNull(d.widthOf("5G")) { "5G must have ink" }
        assertEquals(
            s.LABEL_X + IosGeometry.Wifi.ARTBOARD_MARGIN + labelInk + s.LABEL_BATTERY_GAP,
            s.LABEL_BATTERY_X_5G + b.BODY_LEFT,
            0.0001f
        )
        val cellInkRight = IosGeometry.Cell.DUAL_BLOCK_W + IosGeometry.Cell.LEFT
        val labelInkLeft = s.LABEL_X + IosGeometry.Wifi.ARTBOARD_MARGIN
        assertEquals(s.CELL_LABEL_GAP, labelInkLeft - cellInkRight, 0.0001f)

        // R9: 52.07 + 117.94 (5G ink at cap 71.4) + 54.04 = 224.05, replacing the pre-R9 238.58.
        val span = s.CELL_LABEL_GAP + labelInk + s.LABEL_BATTERY_GAP
        assertEquals(224.05f, span, 1.5f)

        assertEquals(
            s.LABEL_BATTERY_X_5G + b.BODY_W + b.CAP_GAP + b.CAP_W,
            s.LABEL_WIDTH_5G,
            0.0001f
        )
    }

    @Test
    fun `label battery X is per-label and rejects an unsupported label`() {
        val s = IosGeometry.Strip
        val x5 = s.labelBatteryX("5G")
        assertTrue("labelBatteryX(5G) must be finite", x5.isFinite())
        assertEquals("the 5G constant is the per-label call", s.LABEL_BATTERY_X_5G, x5, 0.0f)
        assertTrue("5G+ is wider than 5G", s.labelBatteryX("5G+") > x5)
        assertTrue("E strip battery X is finite", s.labelBatteryX("E").isFinite())

        try {
            s.labelBatteryX("WIFI")
            org.junit.Assert.fail("an unsupported label must be rejected, not collapsed")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "the error must name the unsupported label",
                e.message.orEmpty().contains("WIFI")
            )
        }
    }

    @Test
    fun `bolt points stay inside the battery body`() {
        val points = IosGeometry.Battery.BOLT_POINTS
        assertEquals(42, points.size)
        for (i in points.indices step 2) {
            val x = points[i]
            val y = points[i + 1]
            assertTrue("x=$x", x > IosGeometry.Battery.BODY_LEFT)
            assertTrue("x=$x", x < IosGeometry.Battery.BODY_LEFT + IosGeometry.Battery.BODY_W)
            assertTrue("y=$y", y > IosGeometry.Battery.BODY_TOP)
            assertTrue("y=$y", y < IosGeometry.Battery.BODY_TOP + IosGeometry.Battery.BODY_H)
        }
    }

    @Test
    fun `nub polygon is ordered and symmetric about the body centre`() {
        val b = IosGeometry.Battery
        val centre = b.CAP_TOP + b.CAP_H / 2f
        assertEquals(51f, centre, 0.0001f)
        var upper = 0
        for (i in b.CAP_POINTS.indices step 2) {
            if (b.CAP_POINTS[i + 1] <= centre + 0.0001f) upper++
        }
        assertEquals(13, upper)
        assertEquals(b.CAP_LEFT, b.CAP_POINTS.first(), 0.0001f)
        assertEquals(b.CAP_TOP, b.CAP_POINTS[1], 0.0001f)
        assertEquals(b.CAP_LEFT, b.CAP_POINTS[b.CAP_POINTS.size - 2], 0.0001f)
        assertEquals(b.CAP_TOP + b.CAP_H, b.CAP_POINTS.last(), 0.0001f)
        assertEquals(b.CAP_LEFT + b.CAP_W, b.CAP_POINTS[24], 0.0001f)
        assertEquals(centre, b.CAP_POINTS[25], 0.0001f)
    }

    @Test
    fun `ink boxes match the R9 drawn extents`() {
        val battery = IosGeometry.inkBox(
            IosIconKind.BATTERY,
            IosMapping.battery(50, 20, false, false, 3, 0xFF000000.toInt())
        )
        assertEquals(IosGeometry.Battery.BODY_LEFT, battery.left, 0.001f)
        assertEquals(IosGeometry.Battery.BODY_TOP, battery.top, 0.001f)
        assertEquals(IosGeometry.Battery.CAP_LEFT + IosGeometry.Battery.CAP_W, battery.right, 0.001f)
        assertEquals(100f, battery.height, 0.001f)
        assertEquals(2.072f, battery.width / battery.height, 0.01f)

        val single = IosGeometry.inkBox(
            IosIconKind.CELL,
            IosVisual(part = IosPart.CELL, cellMode = IosCellMode.SINGLE)
        )
        assertEquals(142.9f, single.width, 0.001f)
        assertEquals(92.9f, single.height, 0.001f)

        val dual = IosGeometry.inkBox(
            IosIconKind.CELL,
            IosVisual(part = IosPart.CELL, cellMode = IosCellMode.DUAL, cellRole = IosCellRole.BOTH)
        )
        assertEquals(147.9f, dual.width, 0.001f)
        assertEquals(103.3f, dual.height, 0.001f)

        val wifi = IosGeometry.inkBox(
            IosIconKind.WIFI,
            IosVisual(part = IosPart.WIFI, wifiLevel = 3f)
        )
        assertEquals(0.9125f, wifi.height / IosGeometry.UNIT_HEIGHT, 0.02f)
        assertTrue("wifi ink ${wifi.width} x ${wifi.height}", wifi.width in 124f..129f)

        val label = IosGeometry.inkBox(
            IosIconKind.WIFI,
            IosVisual(part = IosPart.WIFI, wifiSlot = IosWifiSlot.DataType("5G"))
        )
        assertEquals(IosGeometry.Wifi.ARTBOARD_MARGIN, label.left, 0.001f)
        assertEquals(IosGeometry.DataLabel.TOP, label.top, 0.001f)
        assertEquals(IosGeometry.DataLabel.CAP_HEIGHT, label.height, 0.001f)
    }

    @Test
    fun `relative sizes and per-kind scales encode the recovered refs`() {
        val r = IosGeometry.Relative
        assertEquals(1.00f, r.CLOCK_CAP, 0.0001f)
        assertEquals(1.00f, r.BATTERY_H, 0.0001f)
        assertEquals(0.944f, r.WIFI_H, 0.0001f)
        assertEquals(1.256f, r.WIFI_W, 0.0001f)
        assertEquals(0.955f, r.CELL_SINGLE_H, 0.0001f)
        assertEquals(1.042f, r.CELL_DUAL_H, 0.0001f)
        assertEquals(0.260f, r.CAP_H, 0.0001f)
        assertEquals(0.095f, r.CAP_W, 0.0001f)
        assertEquals(0.075f, r.CAP_GAP, 0.0001f)

        assertEquals(0.891f, r.BATTERY_DRAW_SCALE, 0.0001f)
        assertEquals(0.627f, r.WIFI_DRAW_SCALE, 0.0001f)
        assertEquals(0.825f, r.CELL_DRAW_SCALE, 0.0001f)
        assertEquals(0.836f, r.CELL_DUAL_DRAW_SCALE, 0.0001f)
        assertEquals(1f, r.V2_DRAW_SCALE, 0.0001f)
        assertEquals(0.891f, r.drawScale(IosIconKind.BATTERY), 0.0001f)
        assertEquals(0.627f, r.drawScale(IosIconKind.WIFI), 0.0001f)
    }

    @Test
    fun `sizeFor scales the slot and is safe for an empty slot`() {
        val r = IosGeometry.Relative
        assertEquals(71 to 34, r.sizeFor(IosIconKind.BATTERY, 80, 38))
        assertEquals(50 to 31, r.sizeFor(IosIconKind.WIFI, 80, 50))
        assertEquals(66 to 33, r.sizeFor(IosIconKind.CELL, 80, 40))
        assertEquals(67 to 33, r.sizeFor(IosIconKind.CELL, 80, 40, dualCell = true))
        val scaled = r.sizeFor(IosIconKind.CELL, 80, 40)
        assertTrue(scaled.first <= 80 && scaled.second <= 40)
        assertEquals(0 to 0, r.sizeFor(IosIconKind.BATTERY, 0, 38))
        assertEquals(0 to 0, r.sizeFor(IosIconKind.BATTERY, 80, -1))
    }

    @Test
    fun `R4 default scales are exactly 1_10x the R3 calibration`() {
        val r = IosGeometry.Relative
        val r3Battery = 0.81f
        val r3Wifi = 0.57f
        val r3Cell = 0.75f
        val r3CellDual = 0.76f
        assertEquals(1.10f, r.BATTERY_DRAW_SCALE / r3Battery, 0.0001f)
        assertEquals(1.10f, r.WIFI_DRAW_SCALE / r3Wifi, 0.0001f)
        assertEquals(1.10f, r.CELL_DRAW_SCALE / r3Cell, 0.0001f)
        assertEquals(1.10f, r.CELL_DUAL_DRAW_SCALE / r3CellDual, 0.0001f)
    }

    @Test
    fun `identity user transform keeps the new default and 110 percent scales by 1_1`() {
        val r = IosGeometry.Relative
        assertEquals(0f, IosUserTransform.IDENTITY.dxPx, 0.0001f)
        assertEquals(1f, IosUserTransform.IDENTITY.scale, 0.0001f)

        val at100 = iosUserTransform(0, 100, 40f)
        assertEquals(IosUserTransform.IDENTITY, at100)
        assertEquals(71 to 34, r.sizeFor(IosIconKind.BATTERY, 80, 38))
        assertEquals(50 to 31, r.sizeFor(IosIconKind.WIFI, 80, 50))
        assertEquals(66 to 33, r.sizeFor(IosIconKind.CELL, 80, 40))
        assertEquals(67 to 33, r.sizeFor(IosIconKind.CELL, 80, 40, dualCell = true))
        assertEquals(1.1f, iosUserTransform(0, 110, 40f).scale, 0.0001f)
    }
}
