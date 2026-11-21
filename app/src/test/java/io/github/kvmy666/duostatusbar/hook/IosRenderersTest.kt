package io.github.kvmy666.duostatusbar.hook

import android.content.Context
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
 * The iOS runtime has exactly one renderer: the platform-Canvas element, proven pixel-by-pixel in
 * [IosCanvasRasterTest]. There is no Rive view to create inside SystemUI (IOS-R2 0c); the production
 * source grep for that class name is the second half of the gate and is checked outside this JVM test.
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
    fun `factory returns the Canvas element for every kind`() {
        for (kind in IosIconKind.values()) {
            val created = IosRenderers.create(context, kind)
            val canvas = IosRenderers.canvas(context, kind)
            assertEquals("Canvas", created.rendererName)
            assertEquals("Canvas", canvas.rendererName)
            assertEquals(IosCanvasView::class.java, created.javaClass)
            assertEquals(IosCanvasView::class.java, canvas.javaClass)
            assertEquals(kind, (created as IosCanvasView).kind)
            assertEquals(kind, (canvas as IosCanvasView).kind)
            created.teardown()
            canvas.teardown()
        }
    }
}
