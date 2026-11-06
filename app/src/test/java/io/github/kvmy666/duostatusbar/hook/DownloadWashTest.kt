package io.github.kvmy666.duostatusbar.hook

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

/**
 * The download wash, pinned to the concept: white stays, and clockwise progress paints from the
 * left cap toward the right cap. 28 % is 28/50 of the left half. 100 % meets the far cap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadWashTest {

    @Test
    fun `legacy broadcast remains a solid wash`() {
        val state = DownloadProgress.from(Intent().putExtra(DownloadProgress.EXTRA_ACTIVE, true))

        assertNull(state.gradient)
    }

    @Test
    fun `gradient broadcast accepts two through five colours and normalizes its angle`() {
        val colors = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
        val intent = Intent()
            .putExtra(DownloadProgress.EXTRA_GRADIENT_ENABLED, true)
            .putExtra(DownloadProgress.EXTRA_GRADIENT_COLORS, colors)
            .putExtra(DownloadProgress.EXTRA_GRADIENT_ANGLE, 450)

        val gradient = DownloadProgress.from(intent).gradient!!

        assertEquals(colors.toList(), gradient.colors)
        assertEquals(90, gradient.normalizedAngle)
        assertEquals(listOf(0f, 0.5f, 1f, 1f, 1f), gradient.rivePositions())
        assertEquals(5, gradient.riveColors().size)
    }

    @Test
    fun `invalid gradient broadcast falls back to its solid colour`() {
        val intent = Intent()
            .putExtra(DownloadProgress.EXTRA_GRADIENT_ENABLED, true)
            .putExtra(DownloadProgress.EXTRA_GRADIENT_COLORS, intArrayOf(0xFFFF0000.toInt()))

        assertNull(DownloadProgress.from(intent).gradient)
    }

    @Test
    fun `ring taps alternate download and battery without progress updates resetting the choice`() {
        val selection = DownloadProgress.DisplaySelection()
        val progress = DownloadProgress.State(active = true, progress = 37, showLabel = true)

        selection.update(true)
        assertTrue(selection.apply(progress).active)
        assertTrue(selection.toggle())
        assertFalse(selection.apply(progress).active)
        assertFalse(selection.apply(progress).showLabel)

        selection.update(true)
        assertFalse("another progress broadcast must keep battery mode", selection.apply(progress.copy(progress = 42)).active)
        assertTrue(selection.toggle())
        assertTrue(selection.apply(progress).active)
    }

    @Test
    fun `a new download resets the ring to download mode`() {
        val selection = DownloadProgress.DisplaySelection()
        val progress = DownloadProgress.State(active = true, progress = 20)

        selection.update(true)
        selection.toggle()
        selection.update(false)
        assertFalse(selection.toggle())
        selection.update(true)

        assertTrue(selection.apply(progress).active)
    }

    @Test
    fun `download count takes the battery seat even while the battery charges`() {
        val download = DownloadProgress.State(
            active = true,
            progress = 37,
            showLabel = true
        )

        assertEquals(37, download.displayedPercent())
        assertNull(download.copy(active = false).displayedPercent())
        assertNull(download.copy(showLabel = false).displayedPercent())
    }

    private fun wash(
        progress: Int,
        clockwise: Boolean = true,
        charging: Boolean = false,
        showPercent: Boolean = true,
        active: Boolean = true,
    ) = DownloadWash.of(progress, clockwise, charging, showPercent, active)

    @Test
    fun `an idle download draws nothing`() {
        val idle = wash(64, active = false)
        assertEquals(0f, idle.opacity, 0f)
        assertEquals(0f, idle.leftEnd, 0f)
        assertEquals(0f, idle.rightEnd, 0f)
    }

    @Test
    fun `clockwise 28 percent covers part of the left half only`() {
        val w = wash(28)
        assertEquals(RingGeometry.trimLeft(28, charging = false, closed = false), w.leftEnd, 0.0001f)
        assertEquals(0f, w.rightEnd, 0.0001f)
        assertEquals(RingGeometry.LEFT_START, w.leftOffset, 0.0001f)
        assertEquals(1f, w.opacity, 0f)
    }

    @Test
    fun `clockwise 64 percent fills the left half and part of the right`() {
        val w = wash(64)
        assertEquals(RingGeometry.trimLeft(64, charging = false, closed = false), w.leftEnd, 0.0001f)
        assertEquals(RingGeometry.trimRight(64, charging = false, closed = false), w.rightEnd, 0.0001f)
        assertEquals(RingGeometry.RIGHT_START, w.rightOffset, 0.0001f)
    }

    @Test
    fun `clockwise 100 percent meets the right cap`() {
        val w = wash(100)
        val half = RingGeometry.halfArc(charging = false)
        assertEquals(half, w.leftEnd, 0.0001f)
        assertEquals(half, w.rightEnd, 0.0001f)
        assertEquals(RingGeometry.LEFT_START, w.leftOffset, 0.0001f)
        assertEquals(RingGeometry.RIGHT_START, w.rightOffset, 0.0001f)
    }

    @Test
    fun `counter-clockwise 28 percent grows back from the right cap`() {
        val w = wash(28, clockwise = false)
        val rightArc = RingGeometry.rightArc(charging = false, showPercent = true)
        val cap = RingGeometry.RIGHT_START + rightArc
        assertEquals(0f, w.leftEnd, 0.0001f)
        assertEquals(rightArc * 28f / 50f, w.rightEnd, 0.0001f)
        assertEquals(cap, w.rightOffset + w.rightEnd, 0.0001f)
    }

    @Test
    fun `counter-clockwise 100 percent meets the left cap`() {
        val w = wash(100, clockwise = false)
        val leftArc = RingGeometry.leftArc(charging = false, showPercent = true)
        val rightArc = RingGeometry.rightArc(charging = false, showPercent = true)
        assertEquals(RingGeometry.LEFT_START, w.leftOffset, 0.0001f)
        assertEquals(leftArc, w.leftEnd, 0.0001f)
        assertEquals(rightArc, w.rightEnd, 0.0001f)
        assertEquals(RingGeometry.RIGHT_START, w.rightOffset, 0.0001f)
    }

    @Test
    fun `a closed ring maps clockwise progress along the single window`() {
        val w = wash(100, showPercent = false)
        assertEquals(RingGeometry.DRAWN_ARC, w.leftEnd, 0.0001f)
        assertEquals(0f, w.rightEnd, 0.0001f)
        assertEquals(RingGeometry.LEFT_START, w.leftOffset, 0.0001f)
    }

    @Test
    fun `a closed ring walks counter-clockwise back to the left cap`() {
        val w = wash(100, clockwise = false, showPercent = false)
        assertEquals(RingGeometry.LEFT_START, w.leftOffset, 0.0001f)
        assertEquals(RingGeometry.DRAWN_ARC, w.leftEnd, 0.0001f)
        assertEquals(0f, w.rightEnd, 0.0001f)
    }

    @Test
    fun `mapping a visual with no download leaves the wash hidden`() {
        val visual = DuoMapping.visual(
            level = 72,
            charging = false,
            saver = false,
            showPercent = true,
            wifiLevel = 3,
            cellLevel = 4,
            airplane = false,
        )
        assertEquals(0f, visual.downloadOpacity, 0f)
        assertEquals(0f, visual.downloadLeftEnd, 0f)
        assertEquals(0f, visual.downloadRightEnd, 0f)
        assertEquals(RingGeometry.trimLeft(72, charging = false, closed = false), visual.trimLeftEnd, 0.0001f)
    }
}
