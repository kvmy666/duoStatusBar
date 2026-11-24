package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.widget.FrameLayout
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
import java.time.Duration

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
        assertEquals(1, IosExtras.registeredCount)

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
        assertEquals(1, IosExtras.registeredCount)

        IosExtras.deactivate()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(IosExtras.hidden)
        assertEquals(1, IosExtras.registeredCount) // not cleared: a later toggle still reaches it
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
        assertEquals(0, IosExtras.registeredCount)
    }

    /** A Wi-Fi bind group with ids the Robolectric resources cannot resolve, so a seam maps them. */
    private fun wifiGroup(ourGlyph: Boolean): FrameLayout {
        val root = FrameLayout(context)
        root.addView(ImageView(context).apply { id = WIFI_LEFT_ID })
        root.addView(ImageView(context).apply { id = WIFI_INOUT_ID })
        root.addView(
            ImageView(context).apply {
                id = WIFI_SIGNAL_ID
                setImageDrawable(
                    if (ourGlyph) {
                        IosDrawables.create(
                            IosIconKind.WIFI,
                            IosVisual(part = IosPart.WIFI, wifiLevel = 3f),
                            70, 51, relativeSize = false, captureColorFilter = true
                        )
                    } else {
                        ColorDrawable(0xFFFFFFFF.toInt())
                    }
                )
            }
        )
        IosExtras.idLookupForTest = { name ->
            when (name) {
                "wifi_left" -> WIFI_LEFT_ID
                "wifi_inout" -> WIFI_INOUT_ID
                "wifi_signal" -> WIFI_SIGNAL_ID
                else -> 0
            }
        }
        return root
    }

    @Test
    fun `a wifi bind after deactivate is refused and does not grow the registry`() {
        val root = wifiGroup(ourGlyph = true)
        var registerCalls = 0
        IosExtras.registerWifiForTest = { registerCalls++; false }

        IosExtras.apply(true)
        shadowOf(Looper.getMainLooper()).idle()
        IosExtras.deactivate()
        shadowOf(Looper.getMainLooper()).idle()

        IosExtras.bindWifiForTest(root)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

        assertEquals("a refused bind must not reach the slot layout", 0, registerCalls)
        assertEquals("a refused bind must not grow the registry", 0, IosExtras.registeredCount)
    }

    @Test
    fun `deactivate cancels a wifi retry that was posted before it`() {
        val root = wifiGroup(ourGlyph = false)
        var registerCalls = 0
        IosExtras.registerWifiForTest = { registerCalls++; false }

        IosExtras.apply(true)
        shadowOf(Looper.getMainLooper()).idle()
        IosExtras.bindWifiForTest(root)
        assertEquals("the first bind attempt runs once", 1, registerCalls)

        IosExtras.deactivate()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

        assertEquals("a retry posted before deactivate must not run after it", 1, registerCalls)
    }

    @Test
    fun `reactivation after deactivate accepts a bind again`() {
        val root = wifiGroup(ourGlyph = true)
        var registerCalls = 0
        IosExtras.registerWifiForTest = { registerCalls++; registerCalls == 1 }

        IosExtras.apply(true)
        IosExtras.deactivate()
        shadowOf(Looper.getMainLooper()).idle()
        IosExtras.apply(true)
        shadowOf(Looper.getMainLooper()).idle()

        IosExtras.bindWifiForTest(root)
        assertEquals(1, registerCalls)
        assertTrue(IosExtras.registeredCount > 0)
    }

    @Test
    fun `deactivate twice is a no-op`() {
        val view = FakeView(context)
        IosExtras.register(view)
        IosExtras.apply(true)
        shadowOf(Looper.getMainLooper()).idle()

        IosExtras.deactivate()
        shadowOf(Looper.getMainLooper()).idle()
        val layouts = view.layouts
        IosExtras.deactivate()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(IosExtras.hidden)
        assertEquals(1, IosExtras.registeredCount)
        assertEquals("the second deactivate writes no layout", layouts, view.layouts)
    }

    private companion object {
        const val WIFI_LEFT_ID = 0x7f0f0001
        const val WIFI_INOUT_ID = 0x7f0f0002
        const val WIFI_SIGNAL_ID = 0x7f0f0003
    }
}
