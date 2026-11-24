package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.os.Looper
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** R8 extras toggle: one gate flip plus a layout request on the registered leaves. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosExtrasTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class FakeView(context: Context) : ImageView(context) {
        var layouts = 0
        var invalidations = 0

        override fun requestLayout() {
            layouts++
            super.requestLayout()
        }

        override fun invalidate() {
            invalidations++
            super.invalidate()
        }
    }

    @Before
    fun setUp() {
        IosExtras.resetForTest()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        IosExtras.resetForTest()
    }

    @Test
    fun `apply flips the gate and requests layout on a registered leaf`() {
        val view = FakeView(context)
        IosExtras.register(view)

        IosExtras.apply(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(IosExtras.hidden)
        assertTrue(view.layouts >= 1)
        assertTrue(view.invalidations >= 1)

        IosExtras.apply(false)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(IosExtras.hidden)
        assertTrue(view.layouts >= 2)
    }

    @Test
    fun `deactivate restores stock pixels but keeps the weak registry`() {
        val view = FakeView(context)
        IosExtras.register(view)
        IosExtras.apply(true)
        shadowOf(Looper.getMainLooper()).idle()

        IosExtras.deactivate()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(IosExtras.hidden)
    }

    @Test
    fun `hiddenCount is zero while the toggle is off`() {
        IosExtras.apply(true)
        IosExtras.apply(false)
        assertEquals(0, IosExtras.hiddenCount())
        assertEquals(0, IosExtras.hiddenViewCount)
    }

    @Test
    fun `install is idempotent and never throws`() {
        val loader = javaClass.classLoader ?: ClassLoader.getSystemClassLoader()
        IosExtras.install(loader)
        IosExtras.install(loader)
    }
}
