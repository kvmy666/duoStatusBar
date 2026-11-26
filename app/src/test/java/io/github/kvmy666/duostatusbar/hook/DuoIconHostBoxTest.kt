package io.github.kvmy666.duostatusbar.hook

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L11: [DuoIconHost.hasBox] is the readiness gate for hiding the stock icons. A view with a size but
 * an INVISIBLE/GONE ancestor is not shown, and hiding the stock icons then would leave a blank strip,
 * so readiness must require [View.isShown] on top of the size and attachment checks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DuoIconHostBoxTest {

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun content(): FrameLayout {
        val root = FrameLayout(activity)
        activity.setContentView(root)
        root.layout(0, 0, 200, 100)
        return root
    }

    private fun child(parent: FrameLayout, size: Int = 50): View =
        View(activity).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            parent.addView(this)
            layout(0, 0, size, size)
        }

    @Test
    fun `a shown laid out child has a box`() {
        val parent = content()
        val child = child(parent)

        assertTrue(DuoIconHost.hasBox(child))
        assertTrue(DuoIconHost.hasDrawableBox(child))
    }

    @Test
    fun `an invisible ancestor removes the box`() {
        val parent = content()
        val child = child(parent)

        parent.visibility = View.INVISIBLE

        assertFalse(DuoIconHost.hasBox(child))
        assertFalse(DuoIconHost.hasDrawableBox(child))
    }

    @Test
    fun `a detached child has no box`() {
        val parent = content()
        val child = child(parent)

        parent.removeView(child)

        assertFalse(DuoIconHost.hasBox(child))
        assertFalse(DuoIconHost.hasDrawableBox(child))
    }

    @Test
    fun `a zero sized child has no box`() {
        val parent = content()
        val child = View(activity)
        parent.addView(child)
        child.layout(0, 0, 0, 0)

        assertFalse(DuoIconHost.hasBox(child))
        assertFalse(DuoIconHost.hasDrawableBox(child))
    }

    @Test
    fun `alpha decides whether there is ink`() {
        val parent = content()
        val child = child(parent)
        child.alpha = 0f

        assertTrue(DuoIconHost.hasBox(child))
        assertFalse(DuoIconHost.hasDrawableBox(child))

        child.alpha = 1f
        assertTrue(DuoIconHost.hasDrawableBox(child))
    }
}
