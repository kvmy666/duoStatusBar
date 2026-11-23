package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R6 item 4: only the captured status-bar tree may take the immediate first-frame attach. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosImmediateAttachTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a container under the status-bar root is eligible`() {
        val statusBar = FrameLayout(context)
        val container = LinearLayout(context)
        val grandChild = LinearLayout(context)
        statusBar.addView(container)
        container.addView(grandChild)

        assertTrue(IosImmediateAttach.isMainBarContainer(container, statusBar))
        assertTrue("the walk is not limited to direct children", IosImmediateAttach.isMainBarContainer(grandChild, statusBar))
        assertTrue("the root itself is eligible", IosImmediateAttach.isMainBarContainer(statusBar, statusBar))
    }

    @Test
    fun `a shade container or a missing root is never eligible`() {
        val statusBar = FrameLayout(context)
        val shade = FrameLayout(context)
        val container = LinearLayout(context)
        shade.addView(container)

        assertFalse("the shade must not style the main bar", IosImmediateAttach.isMainBarContainer(container, statusBar))
        assertFalse(IosImmediateAttach.isMainBarContainer(container, null))
        assertFalse(IosImmediateAttach.isMainBarContainer(null, statusBar))
        assertFalse("a detached container is not under the root", IosImmediateAttach.isMainBarContainer(FrameLayout(context), statusBar))
    }
}
