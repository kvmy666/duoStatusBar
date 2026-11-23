package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * R7 P3: the data-type placement math and the idempotent `FrameLayout.LayoutParams` write.
 *
 * These tests pin the zero-layout-loop contract (`.pm/R7-P0-map.md:135-149`): the stock-intrinsic
 * drawable drives the gap, the second call reuses the same `LayoutParams` instances, visibility is
 * never touched, and only OxygenOS is allowed to act.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosMobileTypePlacementTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A drawable whose only job is to report the stock intrinsic size. */
    private class FixedIntrinsicDrawable(
        private val widthPx: Int,
        private val heightPx: Int
    ) : Drawable() {
        override fun getIntrinsicWidth(): Int = widthPx

        override fun getIntrinsicHeight(): Int = heightPx

        override fun draw(canvas: Canvas) = Unit

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private class Fixture(
        val frame: FrameLayout,
        val signal: ImageView,
        val type: ImageView
    )

    private fun fixture(
        intrinsicWidth: Int = 60,
        intrinsicHeight: Int = 51,
        typeVisibility: Int = View.GONE
    ): Fixture {
        val frame = FrameLayout(context)
        val signal = ImageView(context).apply {
            setImageDrawable(FixedIntrinsicDrawable(intrinsicWidth, intrinsicHeight))
        }
        val type = ImageView(context).apply { visibility = typeVisibility }
        frame.addView(
            signal,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        frame.addView(
            type,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return Fixture(frame, signal, type)
    }

    @Before
    fun setUp() {
        IosMobileTypePlacement.deactivate()
    }

    @After
    fun tearDown() {
        IosMobileTypePlacement.deactivate()
    }

    // ------------------------------------------------------------------------------- placement

    @Test
    fun `place puts the label after the signal at the scaled measured gap`() {
        val f = fixture()
        f.frame.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val gap = IosMobileTypePlacement.gapPx(60, 51)
        assertEquals(21, gap)

        assertTrue(IosMobileTypePlacement.place(f.signal, f.type, 60, 51))

        val signalLp = f.signal.layoutParams as FrameLayout.LayoutParams
        val typeLp = f.type.layoutParams as FrameLayout.LayoutParams
        val gravity = Gravity.START or Gravity.CENTER_VERTICAL
        assertEquals(gravity, signalLp.gravity)
        assertEquals(0, signalLp.marginStart)
        assertEquals(0, signalLp.marginEnd)
        assertEquals(gravity, typeLp.gravity)
        assertEquals(60 + gap, typeLp.marginStart)
        // The system owns visibility; placement never writes it.
        assertEquals(View.GONE, f.type.visibility)
        // The measured container is explicitly LTR; placement never changes direction.
        assertEquals(View.LAYOUT_DIRECTION_LTR, f.frame.layoutDirection)
    }

    @Test
    fun `a second place call keeps the same LayoutParams instances`() {
        val f = fixture()
        assertTrue(IosMobileTypePlacement.place(f.signal, f.type, 60, 51))

        val signalLp = f.signal.layoutParams
        val typeLp = f.type.layoutParams
        assertTrue(IosMobileTypePlacement.place(f.signal, f.type, 60, 51))
        assertSame(signalLp, f.signal.layoutParams)
        assertSame(typeLp, f.type.layoutParams)
    }

    @Test
    fun `deactivate restores the exact original LayoutParams instances and values`() {
        val f = fixture()
        val signalBefore = f.signal.layoutParams as FrameLayout.LayoutParams
        val typeBefore = f.type.layoutParams as FrameLayout.LayoutParams
        signalBefore.gravity = Gravity.END
        signalBefore.marginStart = 3
        typeBefore.gravity = Gravity.TOP
        typeBefore.marginStart = 7

        assertTrue(IosMobileTypePlacement.place(f.signal, f.type, 60, 51))
        assertFalse(f.signal.layoutParams === signalBefore)
        assertFalse(f.type.layoutParams === typeBefore)

        // R7 P5: the stock instances are captured on the first write and put back on deactivate.
        IosMobileTypePlacement.deactivate()

        assertSame(signalBefore, f.signal.layoutParams)
        assertSame(typeBefore, f.type.layoutParams)
        assertEquals(Gravity.END, signalBefore.gravity)
        assertEquals(3, signalBefore.marginStart)
        assertEquals(Gravity.TOP, typeBefore.gravity)
        assertEquals(7, typeBefore.marginStart)
    }

    @Test
    fun `place uses the explicit type when the mobile_type id is absent`() {
        // Robolectric has no SystemUI resource table, so the name lookup cannot resolve; the measured
        // XML order (signal at 0, type at 1) is the fallback and the explicit type is what it yields.
        val f = fixture()
        assertTrue(IosMobileTypePlacement.place(f.signal, f.type, 60, 51))
        val typeLp = f.type.layoutParams as FrameLayout.LayoutParams
        assertEquals(60 + IosMobileTypePlacement.gapPx(60, 51), typeLp.marginStart)
    }

    // ------------------------------------------------------------------ user transform (R7 P4)

    private fun cellGlyph(transform: IosUserTransform): IosGlyphDrawable = IosDrawables.create(
        IosIconKind.CELL,
        IosVisual(part = IosPart.CELL, cellBars = 4f, userTransform = transform),
        60,
        51,
        relativeSize = true,
        captureColorFilter = true
    )

    @Test
    fun `an identity glyph keeps the exact p3 margin`() {
        val f = fixture()
        f.signal.setImageDrawable(cellGlyph(IosUserTransform.IDENTITY))

        assertTrue(IosMobileTypePlacement.place(f.signal, f.type, 60, 51))
        assertEquals(0, IosMobileTypePlacement.extraExtensionPx(f.signal.drawable, 60, 51))
        val typeLp = f.type.layoutParams as FrameLayout.LayoutParams
        assertEquals(60 + IosMobileTypePlacement.gapPx(60, 51), typeLp.marginStart)
    }

    @Test
    fun `a scaled or offset glyph pushes the label past its drawn right edge`() {
        val f = fixture()
        val transform = IosUserTransform(dxPx = 7f, scale = 1.5f)
        f.signal.setImageDrawable(cellGlyph(transform))

        assertTrue(IosMobileTypePlacement.place(f.signal, f.type, 60, 51))

        val inner = IosGeometry.Relative.sizeFor(
            IosIconKind.CELL, 60, 51, dualCell = false
        ).first
        val right = 60 / 2f + inner * transform.scale / 2f + transform.dxPx
        val extra = max(0f, right - 60).roundToInt()
        assertTrue(extra > 0)
        assertEquals(extra, IosMobileTypePlacement.extraExtensionPx(f.signal.drawable, 60, 51))
        val typeLp = f.type.layoutParams as FrameLayout.LayoutParams
        assertEquals(
            60 + extra + IosMobileTypePlacement.gapPx(60, 51),
            typeLp.marginStart
        )
    }

    // ------------------------------------------------------------------------------------ guards

    @Test
    fun `zero size is never placed`() {
        val f = fixture()
        assertFalse(IosMobileTypePlacement.place(f.signal, f.type, 0, 51))
        assertFalse(IosMobileTypePlacement.place(f.signal, f.type, 60, 0))
    }

    @Test
    fun `a non-FrameLayout parent is never placed`() {
        val column = LinearLayout(context)
        val signal = ImageView(context)
        val type = ImageView(context)
        column.addView(signal, LinearLayout.LayoutParams(60, 51))
        column.addView(type, LinearLayout.LayoutParams(20, 16))
        assertFalse(IosMobileTypePlacement.place(signal, type, 60, 51))
    }

    // ------------------------------------------------------------------------------------- gap

    @Test
    fun `gap table follows the cell artboard scale`() {
        assertEquals(21, IosMobileTypePlacement.gapPx(60, 51))
        assertEquals(0, IosMobileTypePlacement.gapPx(0, 51))
        assertEquals(7, IosMobileTypePlacement.gapPx(19, 16))
        assertEquals(42, IosMobileTypePlacement.gapPx(120, 102))
    }

    // ------------------------------------------------------------------------------ activation

    @Test
    fun `only oxygenos activates placement`() {
        IosMobileTypePlacement.activate(null, "com.android.systemui")
        assertFalse(IosMobileTypePlacement.isActive)

        IosMobileTypePlacement.activate("samsung", "com.android.systemui")
        assertFalse(IosMobileTypePlacement.isActive)

        IosMobileTypePlacement.activate("oxygenos", "com.example.systemui")
        assertTrue(IosMobileTypePlacement.isActive)
        // R7 P5: the caller's package is used for the mobile_type lookup, never a hard-coded one.
        assertEquals("com.example.systemui", IosMobileTypePlacement.packageForTest)

        IosMobileTypePlacement.deactivate()
        assertFalse(IosMobileTypePlacement.isActive)
    }
}
