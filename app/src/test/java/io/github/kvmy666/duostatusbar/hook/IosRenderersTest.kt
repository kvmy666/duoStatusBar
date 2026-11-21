package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The renderer contracts the host depends on.
 *
 * The Canvas renderer is pure platform drawing and is proven pixel-by-pixel in
 * [IosCanvasRasterTest]. The Rive renderer cannot load its native library on the JVM, so here we pin
 * only the safe lifecycle: no native object before [IosRiveView.start], `canDraw` follows the start
 * result, and teardown is idempotent. The Rive scene itself is proven by the offline build + hash gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosRenderersTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val snapshot = IosMapping.battery(
        level = 50,
        lowThreshold = 20,
        saver = false,
        plugged = false,
        status = 0,
        tint = 0xFF123456.toInt()
    )

    @Test
    fun `canvas starts, reports drawable and takes a snapshot`() {
        for (kind in IosIconKind.values()) {
            val element = IosCanvasView(context, kind)
            assertEquals("Canvas", element.rendererName)
            assertFalse("nothing is drawn before start", element.isReady)
            assertTrue("the platform Canvas can always draw", element.canDraw)
            assertTrue(element.start())
            assertTrue(element.isReady)
            element.render(snapshot)
            element.setRenderActive(true)
            element.setRenderActive(false)
            element.teardown()
            element.teardown()
            assertFalse(element.isReady)
        }
    }

    @Test
    fun `rive creates no child before start and is safe to tear down`() {
        val element = IosRiveView(context, IosIconKind.BATTERY)
        assertEquals("Rive", element.rendererName)
        assertFalse(element.canDraw)
        assertFalse(element.isReady)
        assertEquals(0, element.childCount)

        val started = element.start()
        // On the JVM the native library is normally unavailable: start is false and nothing was
        // suppressed. On a host where it loads, start is true and the Rive child exists.
        assertEquals("canDraw must follow the start result", started, element.canDraw)
        element.render(snapshot)
        element.setRenderActive(true)
        element.setRenderActive(false)
        element.teardown()
        element.teardown()
        assertFalse(element.canDraw)
        assertEquals(0, element.childCount)
    }

    @Test
    fun `factory returns one element per renderer and kind`() {
        for (kind in IosIconKind.values()) {
            val rive = IosRenderers.create(context, kind)
            val canvas = IosRenderers.canvas(context, kind)
            assertEquals("Rive", rive.rendererName)
            assertEquals("Canvas", canvas.rendererName)
            assertEquals(IosRiveView::class.java, rive.javaClass)
            assertEquals(IosCanvasView::class.java, canvas.javaClass)
            assertEquals(kind, (rive as IosRiveView).kind)
            assertEquals(kind, (canvas as IosCanvasView).kind)
            rive.teardown()
            canvas.teardown()
        }
    }

    @Test
    fun `ios guard is the rive breaker`() {
        val breaker: IosRiveBreaker = IosGuard(context)
        assertEquals(IosGuard::class.java, breaker.javaClass)
        assertTrue(IosRiveBreaker::class.java.isAssignableFrom(IosGuard::class.java))
    }

    @Test
    fun `rive is a container so it can host its internal canvas fallback`() {
        val view = IosRiveView(context, IosIconKind.WIFI)
        assertTrue(ViewGroup::class.java.isAssignableFrom(view.javaClass))
    }
}
