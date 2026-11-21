package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * IOS-R2 0c: no Rive view may be created inside SystemUI.
 *
 * This pins the runtime half of the promise: the production factory returns the Canvas element, and
 * never an element whose class is a Rive view. The source half is a build gate
 * (`grep -rn "IosRiveView" app/src/main` must be empty); a source-tree assertion in a JVM test would
 * only restate the grep, so it is deliberately not used here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosRiveAbsenceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `production renderer is the Canvas element, not a Rive view`() {
        for (kind in IosIconKind.values()) {
            val element = IosRenderers.create(context, kind)
            assertEquals("IosCanvasView", element.javaClass.simpleName)
            assertNotEquals("Rive", element.rendererName)
            element.teardown()
        }
    }
}
