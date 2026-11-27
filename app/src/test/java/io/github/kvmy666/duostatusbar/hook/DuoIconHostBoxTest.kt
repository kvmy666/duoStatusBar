package io.github.kvmy666.duostatusbar.hook

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    private class DuoOrphanView(context: Context) : View(context)

    private class DuoOwnedView(context: Context) : View(context)

    @Test
    fun `removeOrphanDuoViews removes unowned Duo children only`() {
        val parent = content()
        val orphan = DuoOrphanView(activity)
        val owned = DuoOwnedView(activity)
        val normal = View(activity)
        parent.addView(orphan)
        parent.addView(owned)
        parent.addView(normal)

        val removed = DuoIconHost.removeOrphanDuoViews(parent, listOf(owned))

        assertEquals(1, removed)
        assertNull("the orphan Duo view is removed", orphan.parent)
        assertSame("an owned Duo view is kept", parent, owned.parent)
        assertSame("a normal child is kept", parent, normal.parent)
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

    /**
     * The fallback decision is wider than [DuoIconHost.hasBox], which stays strict, but only for an
     * attached Rive: a not-shown bar is deferred instead of treated as a bind failure. Canvas and
     * detached views keep the strict rule. Evidence: the 2026-11-27 LG LM-V600, OnePlus PLK110 and
     * Samsung SM-S948Q reports, where Rive bound before the main bar was shown.
     */
    @Test
    fun `a not-shown attached Rive defers the box judgement`() {
        assertTrue(
            DuoIconHost.attachBoxAcceptable(
                parentOk = true, hasBox = false, shown = false, attached = true, deferHidden = true
            )
        )
        assertTrue(
            DuoIconHost.attachBoxAcceptable(
                parentOk = true, hasBox = true, shown = false, attached = true, deferHidden = true
            )
        )
        assertTrue(
            DuoIconHost.attachBoxAcceptable(
                parentOk = true, hasBox = true, shown = true, attached = true, deferHidden = true
            )
        )
        assertFalse(
            "shown 0x0 still falls back",
            DuoIconHost.attachBoxAcceptable(
                parentOk = true, hasBox = false, shown = true, attached = true, deferHidden = true
            )
        )
        assertFalse(
            "a wrong parent is never accepted",
            DuoIconHost.attachBoxAcceptable(
                parentOk = false, hasBox = true, shown = false, attached = true, deferHidden = true
            )
        )
        assertFalse(
            "a detached Rive is not deferred",
            DuoIconHost.attachBoxAcceptable(
                parentOk = true, hasBox = false, shown = false, attached = false, deferHidden = true
            )
        )
        assertFalse(
            "Canvas keeps the strict rule",
            DuoIconHost.attachBoxAcceptable(
                parentOk = true, hasBox = false, shown = false, attached = true, deferHidden = false
            )
        )
    }

    @Test
    fun `the unready guard removes only a never-bound element with no box`() {
        assertFalse(
            "a ready element is never removed",
            DuoIconHost.shouldRemoveUnready(isReady = true, attached = false, width = 0, height = 0)
        )
        assertTrue(
            "never bound and detached",
            DuoIconHost.shouldRemoveUnready(isReady = false, attached = false, width = 50, height = 50)
        )
        assertTrue(
            "never bound and attached with no box",
            DuoIconHost.shouldRemoveUnready(isReady = false, attached = true, width = 0, height = 0)
        )
        assertFalse(
            "a boxed element is left to the bound callbacks",
            DuoIconHost.shouldRemoveUnready(isReady = false, attached = true, width = 50, height = 50)
        )
        assertFalse(
            "a ready element with no box is left alone",
            DuoIconHost.shouldRemoveUnready(isReady = true, attached = true, width = 0, height = 0)
        )
        assertFalse(
            "one positive dimension still counts as a box",
            DuoIconHost.shouldRemoveUnready(isReady = false, attached = true, width = 50, height = 0)
        )
    }
}
