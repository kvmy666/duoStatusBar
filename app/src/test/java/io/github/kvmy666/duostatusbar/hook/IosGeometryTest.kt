package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the measured proportions that both renderers consume. A drift here is a silent visual
 * regression, so each family is checked against the reference ratios (H_bat = 100).
 */
class IosGeometryTest {

    @Test
    fun `battery body matches the measured aspect and radius`() {
        val aspect = IosGeometry.Battery.BODY_W / IosGeometry.Battery.BODY_H
        assertEquals(1.925f, aspect, 0.02f)
        assertEquals(0.33f, IosGeometry.Battery.BODY_RADIUS / IosGeometry.Battery.BODY_H, 0.01f)
        assertEquals(0.095f, IosGeometry.Battery.CAP_W / IosGeometry.Battery.BODY_H, 0.001f)
        assertEquals(0.260f, IosGeometry.Battery.CAP_H / IosGeometry.Battery.BODY_H, 0.001f)
        assertEquals(0.075f, IosGeometry.Battery.CAP_GAP / IosGeometry.Battery.BODY_H, 0.001f)
        assertEquals(0.50f, IosGeometry.Battery.CAP_ALPHA, 0.0001f)
        // The frozen envelope keeps the pre-R2 artboard so placements do not move.
        assertEquals(217.2f, IosGeometry.Battery.ARTBOARD_W, 0.001f)
        assertEquals(102f, IosGeometry.Battery.ARTBOARD_H, 0.001f)
        // The artboard contains the body and the cap with a 1-unit margin on both sides.
        assertTrue(IosGeometry.Battery.CAP_LEFT + IosGeometry.Battery.CAP_W <=
            IosGeometry.Battery.ARTBOARD_W - 1f)
        assertTrue(IosGeometry.Battery.BODY_TOP + IosGeometry.Battery.BODY_H <=
            IosGeometry.Battery.ARTBOARD_H - 1f)
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
    fun `single cell bars use the measured heights and baseline`() {
        val heights = IosGeometry.Cell.SINGLE_BAR_HEIGHTS
        assertEquals(4, heights.size)
        assertEquals(listOf(36.0f, 52.8f, 73.0f, 95.5f), heights.toList())
        heights.toList().zipWithNext().forEach { (a, b) -> assertTrue(b > a) }
        assertEquals(0.955f, heights.last() / IosGeometry.UNIT_HEIGHT, 0.001f)
        // Bar 4's top is exactly TOP and the baseline is the named 96.5 constant.
        assertEquals(96.5f, IosGeometry.Cell.SINGLE_BAR_BOTTOM, 0.001f)
        assertEquals(IosGeometry.Cell.TOP + heights.last(), IosGeometry.Cell.SINGLE_BAR_BOTTOM, 0.001f)
        assertTrue(IosGeometry.Cell.PITCH > IosGeometry.Cell.BAR_W)
        assertEquals(
            3f * IosGeometry.Cell.PITCH + IosGeometry.Cell.BAR_W,
            IosGeometry.Cell.BLOCK_W,
            0.0001f
        )
    }

    @Test
    fun `dual cell rows are compressed and keep the frozen artboard`() {
        val heights = IosGeometry.Cell.DUAL_BAR_HEIGHTS
        heights.toList().zipWithNext().forEach { (a, b) -> assertTrue(b > a) }
        assertTrue(heights.last() < IosGeometry.Cell.SINGLE_BAR_HEIGHTS.last())
        assertEquals(12.7f, IosGeometry.Cell.DUAL_GAP, 0.0001f)
        assertEquals(
            IosGeometry.Cell.DUAL_ROW_H + IosGeometry.Cell.DUAL_GAP + IosGeometry.Cell.DOT_H,
            IosGeometry.Cell.DUAL_BLOCK_H,
            0.0001f
        )
        assertEquals(102.8f, IosGeometry.Cell.DUAL_BLOCK_H, 0.001f)
        // The artboard height stays the pre-R2 literal, not DUAL_BLOCK_H + 2.
        assertEquals(105.6f, IosGeometry.Cell.ARTBOARD_H, 0.0001f)
        assertEquals(76.4f, IosGeometry.Cell.DOT_TOP, 0.0001f)
        assertTrue(IosGeometry.Cell.DUAL_BLOCK_H > IosGeometry.UNIT_HEIGHT)
        assertEquals(0.26f, IosGeometry.Cell.DOT_W / IosGeometry.UNIT_HEIGHT, 0.01f)
        assertEquals(0.274f, IosGeometry.Cell.DOT_H / IosGeometry.UNIT_HEIGHT, 0.005f)
        assertTrue(IosGeometry.Cell.DOT_TOP + IosGeometry.Cell.DOT_H <=
            IosGeometry.Cell.ARTBOARD_H)
    }

    @Test
    fun `dual row artboards fill a slot without shrinking the shared block`() {
        val c = IosGeometry.Cell
        // The row artboard keeps the full block width and the same 1-unit margin as the full artboard.
        assertEquals(c.ARTBOARD_W, c.ROW_ARTBOARD_W, 0.0001f)
        assertEquals(c.BLOCK_W + 2f, c.ROW_ARTBOARD_W, 0.0001f)
        assertEquals(150.1f, c.ROW_ARTBOARD_W, 0.001f)

        // DATA row: compressed bars plus the 1-unit margin, bottom at TOP + DUAL_ROW_H.
        assertEquals(c.DUAL_ROW_H + 2f, c.DATA_ROW_ARTBOARD_H, 0.0001f)
        assertEquals(64.7f, c.DATA_ROW_ARTBOARD_H, 0.001f)
        assertEquals(c.DATA_ROW_ARTBOARD_H - 1f, c.TOP + c.DUAL_ROW_H, 0.0001f)

        // OTHER row: the dot row plus the margin, translated to the top of its own artboard.
        assertEquals(c.DOT_H + 2f, c.OTHER_ROW_ARTBOARD_H, 0.0001f)
        assertEquals(29.4f, c.OTHER_ROW_ARTBOARD_H, 0.001f)
        assertEquals(-(c.DOT_TOP - c.TOP), c.otherRowOffsetY(), 0.0001f)
        assertEquals(-75.4f, c.otherRowOffsetY(), 0.001f)
        assertEquals(c.TOP, c.DOT_TOP + c.otherRowOffsetY(), 0.0001f)
        assertEquals(
            c.OTHER_ROW_ARTBOARD_H - 1f,
            c.DOT_TOP + c.otherRowOffsetY() + c.DOT_H,
            0.0001f
        )
    }

    @Test
    fun `inactive alpha and Wi-Fi level tables match the reference and Duo`() {
        assertEquals(0.25f, IosGeometry.Cell.INACTIVE_ALPHA, 0.0001f)
        assertEquals(0.50f, IosGeometry.Battery.REMAINDER_ALPHA, 0.0001f)
        assertEquals(4, IosGeometry.Wifi.OUTER_ALPHA.size)
        assertEquals(4, IosGeometry.Wifi.INNER_ALPHA.size)
        assertEquals(1f, IosGeometry.Wifi.OUTER_ALPHA[3], 0.0001f)
        assertEquals(1f, IosGeometry.Wifi.INNER_ALPHA[3], 0.0001f)
    }

    @Test
    fun `wifi uses the measured light geometry`() {
        val w = IosGeometry.Wifi
        assertEquals(129.53f, w.ARTBOARD_W, 0.001f)
        assertEquals(96.77f, w.ARTBOARD_H, 0.001f)
        assertEquals(1f, w.ARTBOARD_MARGIN, 0.0001f)

        // Both arcs are concentric; the trims are the measured spans as Rive fractions.
        assertEquals(64.809f, w.CENTRE_X, 0.001f)
        assertEquals(93.522f, w.CENTRE_Y, 0.001f)
        assertEquals(83.843f, w.OUTER_RADIUS, 0.001f)
        assertEquals(16.903f, w.OUTER_STROKE, 0.001f)
        assertEquals(83.727f, w.OUTER_TRIM_END * 360f, 0.01f)
        assertEquals(318.303f, w.OUTER_TRIM_OFFSET * 360f, 0.01f)
        assertEquals(50.053f, w.INNER_RADIUS, 0.001f)
        assertEquals(17.794f, w.INNER_STROKE, 0.001f)
        assertEquals(79.228f, w.INNER_TRIM_END * 360f, 0.01f)
        assertEquals(320.370f, w.INNER_TRIM_OFFSET * 360f, 0.01f)

        // The dot is the measured left-right symmetric 32-gon, wholly inside the artboard.
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
        assertEquals(47.50f, minX, 0.01f)
        assertEquals(82.14f, maxX, 0.01f)
        assertEquals(69.09f, minY, 0.01f)
        assertEquals(95.77f, maxY, 0.01f)
        // Mirror symmetry about the measured centre x.
        assertEquals(64.82f, (minX + maxX) / 2f, 0.01f)
    }

    @Test
    fun `battery cap is the measured 25-gon inside the artboard`() {
        val b = IosGeometry.Battery
        assertEquals(25, b.CAP_POINTS.size / 2)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in b.CAP_POINTS.indices step 2) {
            val x = b.CAP_POINTS[i]
            val y = b.CAP_POINTS[i + 1]
            assertTrue("x=$x", x >= 1f && x <= b.ARTBOARD_W - 1f)
            assertTrue("y=$y", y >= 1f && y <= b.ARTBOARD_H - 1f)
            minX = minOf(minX, x); maxX = maxOf(maxX, x)
            minY = minOf(minY, y); maxY = maxOf(maxY, y)
        }
        // The polygon's bbox is exactly the drawn cap rectangle used by parity/tests.
        assertEquals(b.CAP_LEFT, minX, 0.01f)
        assertEquals(b.CAP_LEFT + b.CAP_W, maxX, 0.01f)
        assertEquals(b.CAP_TOP, minY, 0.01f)
        assertEquals(b.CAP_TOP + b.CAP_H, maxY, 0.01f)
    }

    @Test
    fun `strip gaps and width match the reference composition`() {
        assertEquals(57.5f, IosGeometry.Strip.CELL_WIFI_GAP, 0.0001f)
        assertEquals(59.77f, IosGeometry.Strip.WIFI_BATTERY_GAP, 0.0001f)
        // The smaller cap shrinks the ink width by 5.7 units (was 608.1).
        assertEquals(602.4f, IosGeometry.Strip.WIDTH, 0.1f)
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
    fun `bolt points stay inside the battery body`() {
        val points = IosGeometry.Battery.BOLT_POINTS
        assertEquals(0, points.size % 2)
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
    fun `cap polygon is ordered and symmetric about the body centre`() {
        val b = IosGeometry.Battery
        val centre = b.CAP_TOP + b.CAP_H / 2f
        assertEquals(51f, centre, 0.0001f)
        // 13 vertices on the upper half (both ends included) and 12 on the lower half.
        var upper = 0
        for (i in b.CAP_POINTS.indices step 2) {
            if (b.CAP_POINTS[i + 1] <= centre + 0.0001f) upper++
        }
        assertEquals(13, upper)
        // First/last points are the flat base at the measured top/bottom.
        assertEquals(b.CAP_LEFT, b.CAP_POINTS.first(), 0.0001f)
        assertEquals(b.CAP_TOP, b.CAP_POINTS[1], 0.0001f)
        assertEquals(b.CAP_LEFT, b.CAP_POINTS[b.CAP_POINTS.size - 2], 0.0001f)
        assertEquals(b.CAP_TOP + b.CAP_H, b.CAP_POINTS.last(), 0.0001f)
        // The widest vertex is the artboard-right cap edge at the body centre.
        assertEquals(b.CAP_LEFT + b.CAP_W, b.CAP_POINTS[24], 0.0001f)
        assertEquals(centre, b.CAP_POINTS[25], 0.0001f)
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
        assertEquals(0.891f, r.drawScale(IosIconKind.BATTERY), 0.0001f)
        assertEquals(0.627f, r.drawScale(IosIconKind.WIFI), 0.0001f)
        assertEquals(0.825f, r.drawScale(IosIconKind.CELL), 0.0001f)
        assertEquals(0.836f, r.drawScale(IosIconKind.CELL, dualCell = true), 0.0001f)
    }

    @Test
    fun `sizeFor scales the slot and is safe for an empty slot`() {
        val r = IosGeometry.Relative
        // 80*0.891 = 71.28 -> 71, 38*0.891 = 33.858 -> 34
        assertEquals(71 to 34, r.sizeFor(IosIconKind.BATTERY, 80, 38))
        // 80*0.627 = 50.16 -> 50, 50*0.627 = 31.35 -> 31
        assertEquals(50 to 31, r.sizeFor(IosIconKind.WIFI, 80, 50))
        // 80*0.825 = 66, 40*0.825 = 33
        assertEquals(66 to 33, r.sizeFor(IosIconKind.CELL, 80, 40))
        // 80*0.836 = 66.88 -> 67, 40*0.836 = 33.44 -> 33
        assertEquals(67 to 33, r.sizeFor(IosIconKind.CELL, 80, 40, dualCell = true))
        // Never larger than the slot, and an empty slot is explicit rather than a crash.
        val scaled = r.sizeFor(IosIconKind.CELL, 80, 40)
        assertTrue(scaled.first <= 80 && scaled.second <= 40)
        assertEquals(0 to 0, r.sizeFor(IosIconKind.BATTERY, 0, 38))
        assertEquals(0 to 0, r.sizeFor(IosIconKind.BATTERY, 80, -1))
    }

    @Test
    fun `R4 default scales are exactly 1_10x the R3 calibration`() {
        val r = IosGeometry.Relative
        // The literal R3 constants, kept here so a future "tidy up" cannot hide a scale change.
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
        // The default user transform is identity: 0 px offset and scale 1.
        assertEquals(0f, IosUserTransform.IDENTITY.dxPx, 0.0001f)
        assertEquals(1f, IosUserTransform.IDENTITY.scale, 0.0001f)

        // 100 % is relative to 100 and therefore equals the new R4 default (not an extra inset).
        val at100 = iosUserTransform(0, 100, 40f)
        assertEquals(IosUserTransform.IDENTITY, at100)
        assertEquals(71 to 34, r.sizeFor(IosIconKind.BATTERY, 80, 38))
        assertEquals(50 to 31, r.sizeFor(IosIconKind.WIFI, 80, 50))
        assertEquals(66 to 33, r.sizeFor(IosIconKind.CELL, 80, 40))
        assertEquals(67 to 33, r.sizeFor(IosIconKind.CELL, 80, 40, dualCell = true))

        // 110 % is 1.1x, applied on top of the new default by the renderer.
        assertEquals(1.1f, iosUserTransform(0, 110, 40f).scale, 0.0001f)
    }
}
