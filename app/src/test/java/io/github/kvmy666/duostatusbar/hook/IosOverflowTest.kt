package io.github.kvmy666.duostatusbar.hook

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** R4A item 5: the pure overlay box used to enlarge an overlay canvas around the slot centre. */
class IosOverflowTest {

    @Test
    fun `identity keeps the slot box`() {
        assertArrayEquals(
            intArrayOf(10, 20, 100, 50),
            overlayBox(10, 20, 100, 50, dxPx = 0f, scale = 1f)
        )
    }

    @Test
    fun `growing is centred and keeps the width times scale`() {
        // width 150 (100*1.5), height 75 (50*1.5); left 10-(50/2)=-15, top 20-(25/2)=8.
        assertArrayEquals(
            intArrayOf(-15, 8, 150, 75),
            overlayBox(10, 20, 100, 50, dxPx = 0f, scale = 1.5f)
        )
    }

    @Test
    fun `shrinking keeps the centre`() {
        // width 50, height 25; left 10-(-50/2)=35, top 20-(-25/2)=32.
        assertArrayEquals(
            intArrayOf(35, 32, 50, 25),
            overlayBox(10, 20, 100, 50, dxPx = 0f, scale = 0.5f)
        )
    }

    @Test
    fun `a negative offset shifts the box left`() {
        assertArrayEquals(
            intArrayOf(-10, 20, 100, 50),
            overlayBox(10, 20, 100, 50, dxPx = -20f, scale = 1f)
        )
    }

    @Test
    fun `a positive offset shifts the box right after the centre`() {
        assertArrayEquals(
            intArrayOf(-13, 8, 150, 75),
            overlayBox(10, 20, 100, 50, dxPx = 2.4f, scale = 1.5f)
        )
    }

    @Test
    fun `the box is at least one pixel`() {
        assertArrayEquals(
            intArrayOf(10, 20, 1, 1),
            overlayBox(10, 20, 0, 0, dxPx = 0f, scale = 1f)
        )
        assertArrayEquals(
            intArrayOf(10, 20, 1, 1),
            overlayBox(10, 20, 1, 1, dxPx = 0f, scale = 0.001f)
        )
    }
}
