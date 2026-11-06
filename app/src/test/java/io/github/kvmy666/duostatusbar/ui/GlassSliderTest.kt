package io.github.kvmy666.duostatusbar.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassSliderTest {
    @Test
    fun `slider value maps pointer position across the travel`() {
        val thumb = 28f
        val width = 128
        assertEquals(0f, sliderValueAt(0f, width, thumb, 0f, 100f, 0), 0.001f)
        assertEquals(100f, sliderValueAt(width - thumb, width, thumb, 0f, 100f, 0), 0.001f)
        assertEquals(50f, sliderValueAt((width - thumb) / 2f, width, thumb, 0f, 100f, 0), 0.001f)
    }

    @Test
    fun `slider value snaps to steps`() {
        assertEquals(33.333f, sliderValueAt(40f, 120, 20f, 0f, 100f, 2), 0.01f)
    }

    @Test
    fun `slider value clamps out of range`() {
        assertEquals(0f, sliderValueAt(-10f, 120, 20f, 0f, 100f, 0), 0.001f)
        assertEquals(100f, sliderValueAt(1000f, 120, 20f, 0f, 100f, 0), 0.001f)
    }
}
