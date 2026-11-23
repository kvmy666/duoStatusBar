package io.github.kvmy666.duostatusbar.hook

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R8 gate drawable: 0x0/no ink while hidden, the captured stock drawable when the toggle is off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosHiddenDrawableTest {

    private val resources get() = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    @After
    fun tearDown() {
        IosExtras.hidden = false
    }

    private fun state(): HiddenGateState {
        val stock = ColorDrawable(Color.RED)
        return IosExtras.gateState(stock.constantState, 10, 10) as HiddenGateState
    }

    @Test
    fun `hidden gate has no intrinsic size and draws no pixels`() {
        IosExtras.hidden = true
        val gate = state().newDrawable(resources, null)

        assertEquals(0, gate.intrinsicWidth)
        assertEquals(0, gate.intrinsicHeight)

        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        gate.setBounds(0, 0, 10, 10)
        gate.draw(Canvas(bitmap))
        assertEquals(0, bitmap.getPixel(5, 5)) // untouched/transparent
    }

    @Test
    fun `visible gate keeps the stock intrinsic and draws the stock badge`() {
        IosExtras.hidden = false
        val gate = state().newDrawable(resources, null)

        assertEquals(10, gate.intrinsicWidth)
        assertEquals(10, gate.intrinsicHeight)

        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        gate.setBounds(0, 0, 10, 10)
        gate.draw(Canvas(bitmap))
        assertEquals(Color.RED, bitmap.getPixel(5, 5))
    }

    @Test
    fun `a null stock state fails closed at zero size in both positions`() {
        val gateState = IosExtras.gateState(null, 10, 10)
        IosExtras.hidden = true
        val hidden = gateState.newDrawable(resources, null)
        assertEquals(0, hidden.intrinsicWidth)
        assertEquals(0, hidden.intrinsicHeight)

        IosExtras.hidden = false
        val visible = gateState.newDrawable(resources, null)
        assertEquals(0, visible.intrinsicWidth)
        assertEquals(0, visible.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        visible.setBounds(0, 0, 10, 10)
        visible.draw(Canvas(bitmap))
        assertEquals(0, bitmap.getPixel(5, 5))
    }

    @Test
    fun `paint properties are forwarded to the stock drawable without crashing`() {
        IosExtras.hidden = false
        val gate = state().newDrawable(resources, null)

        gate.alpha = 128
        gate.setTint(Color.GREEN)
        gate.setTintList(ColorStateList.valueOf(Color.GREEN))
        gate.setTintMode(PorterDuff.Mode.SRC_IN)
        gate.colorFilter = PorterDuffColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN)
        gate.setVisible(true, false)

        assertEquals(128, gate.alpha)
        assertTrue(gate.opacity == android.graphics.PixelFormat.TRANSLUCENT)
    }

    @Test
    fun `the constant state mints fresh drawables, never a shared one`() {
        val gateState = state()
        val first = gateState.newDrawable(resources, null)
        val second = gateState.newDrawable(resources, null)
        assertNotSame(first, second)
        assertTrue(first is HiddenGateDrawable)
        assertTrue(second is HiddenGateDrawable)
    }
}
