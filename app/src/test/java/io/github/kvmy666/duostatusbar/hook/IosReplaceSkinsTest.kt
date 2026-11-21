package io.github.kvmy666.duostatusbar.hook

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
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
 * The in-place drawing helpers: [IosDrawableSwap] and [IosSiblingSwap].
 *
 * The leaf is attached to a real Robolectric window because the per-draw guard deliberately respects
 * `View.isShown()` and the OEM's visibility: a detached test view would never re-assert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosReplaceSkinsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // ------------------------------------------------------------------------ leaf ImageView swap

    @Test
    fun `apply pins the stock intrinsic size and leaves layout and visibility untouched`() {
        val original = ColorDrawable(Color.RED)
        val leaf = ImageView(context).apply { setImageDrawable(original) }
        host(leaf)
        leaf.layout(0, 0, 79, 48)

        val ours = batteryDrawable()
        var reasserts = 0
        val swap = IosDrawableSwap(
            leaf = leaf,
            drawable = ours,
            onReassert = { reasserts++ },
            restoreDrawable = original
        )
        val layoutParams = leaf.layoutParams
        val visibility = leaf.visibility

        assertTrue(swap.apply())
        assertSame(ours, leaf.drawable)
        assertEquals(79, ours.intrinsicWidth)
        assertEquals(48, ours.intrinsicHeight)
        assertSame(layoutParams, leaf.layoutParams)
        assertEquals(visibility, leaf.visibility)
        assertEquals(79, leaf.width)
        assertEquals(48, leaf.height)
        assertEquals(0, reasserts)
    }

    @Test
    fun `reassert replaces only a foreign non-null drawable`() {
        val original = ColorDrawable(Color.RED)
        val leaf = ImageView(context).apply { setImageDrawable(original) }
        host(leaf)
        leaf.layout(0, 0, 79, 48)

        val ours = batteryDrawable()
        var reasserts = 0
        val swap = IosDrawableSwap(leaf, ours, onReassert = { reasserts++ }, restoreDrawable = original)
        assertTrue(swap.apply())

        leaf.setImageDrawable(ColorDrawable(Color.BLACK))
        assertTrue(swap.reassert())
        assertSame(ours, leaf.drawable)
        // A successful write is progress, not a failed re-assert: it must not count.
        assertEquals(0, reasserts)

        // Ours is already installed: no write, no callback.
        assertFalse(swap.reassert())
        assertEquals(0, reasserts)

        // A null drawable is the OEM hiding the icon; never resurrect it.
        leaf.setImageDrawable(null)
        assertFalse(swap.reassert())
        assertNull(leaf.drawable)
        assertEquals(0, reasserts)

        leaf.setImageDrawable(ours)
        assertFalse(swap.reassert())
        assertEquals(0, reasserts)
    }

    @Test
    fun `restore puts the original back is idempotent and never overwrites a newer OEM push`() {
        val original = ColorDrawable(Color.RED)
        val leaf = ImageView(context).apply { setImageDrawable(original) }
        host(leaf)
        leaf.layout(0, 0, 79, 48)

        val ours = batteryDrawable()
        val swap = IosDrawableSwap(leaf, ours, restoreDrawable = original)
        assertTrue(swap.apply())

        swap.restore()
        assertSame(original, leaf.drawable)
        swap.restore()
        assertSame(original, leaf.drawable)

        // After restore the swap is inert even if a pre-draw were dispatched late.
        val foreign = ColorDrawable(Color.GREEN)
        leaf.setImageDrawable(foreign)
        assertFalse(swap.reassert())
        assertSame(foreign, leaf.drawable)
    }

    @Test
    fun `restore leaves a newer OEM drawable in place`() {
        val original = ColorDrawable(Color.RED)
        val leaf = ImageView(context).apply { setImageDrawable(original) }
        host(leaf)
        leaf.layout(0, 0, 79, 48)

        val ours = batteryDrawable()
        val swap = IosDrawableSwap(leaf, ours, restoreDrawable = original)
        assertTrue(swap.apply())

        val newer = ColorDrawable(Color.GREEN)
        leaf.setImageDrawable(newer)
        swap.restore()
        assertSame(newer, leaf.drawable)
    }

    @Test
    fun `apply captures the original then and restores that, not a stale constructor snapshot`() {
        val stale = ColorDrawable(Color.RED)
        val leaf = ImageView(context).apply { setImageDrawable(stale) }
        host(leaf)
        leaf.layout(0, 0, 79, 48)

        val current = ColorDrawable(Color.GREEN)
        leaf.setImageDrawable(current)

        val ours = batteryDrawable()
        val swap = IosDrawableSwap(leaf, ours, restoreDrawable = stale)
        assertTrue(swap.apply())
        assertSame(ours, leaf.drawable)

        swap.restore()
        assertSame("the drawable present at apply wins", current, leaf.drawable)
    }

    @Test
    fun `a stale constructor snapshot pins the apply-time drawable intrinsic and restores it`() {
        val stale = SizedDrawable(Color.RED, 11, 13)
        val leaf = ImageView(context).apply { setImageDrawable(stale) }
        host(leaf)
        leaf.layout(0, 0, 79, 48)

        val current = SizedDrawable(Color.GREEN, 31, 27)
        leaf.setImageDrawable(current)

        val ours = batteryDrawable()
        val swap = IosDrawableSwap(leaf, ours, restoreDrawable = stale)
        assertTrue(swap.apply())
        assertSame(ours, leaf.drawable)
        assertEquals("intrinsic comes from the drawable captured at apply", 31, ours.intrinsicWidth)
        assertEquals("intrinsic comes from the drawable captured at apply", 27, ours.intrinsicHeight)

        swap.restore()
        assertSame("restore uses the drawable captured at apply", current, leaf.drawable)
    }

    @Test
    fun `a failing restore never blanks a non-null leaf`() {
        val original = ColorDrawable(Color.RED)
        val leaf = ThrowingImageView(context).apply { setImageDrawable(original) }
        host(leaf)
        leaf.layout(0, 0, 79, 48)

        val ours = batteryDrawable()
        val swap = IosDrawableSwap(leaf, ours, restoreDrawable = original)
        assertTrue(swap.apply())
        assertSame(ours, leaf.drawable)

        leaf.failSetImageDrawable = true
        swap.restore()

        assertSame("the leaf keeps ours instead of becoming blank", ours, leaf.drawable)
    }

    @Test
    fun `restore clears the drawable detach listener`() {
        val ours = batteryDrawable()

        val leaf = ImageView(context).apply { setImageDrawable(ColorDrawable(Color.RED)) }
        host(leaf)
        leaf.layout(0, 0, 79, 48)
        val swap = IosDrawableSwap(leaf, ours)
        assertTrue(swap.apply())

        // Arm the signal after the install: ImageView.setImageDrawable() calls setVisible(false) on an
        // unattached test view, and that is the OEM-detach signal this listener carries.
        var fired = 0
        ours.detachListener = { fired++ }
        swap.restore()
        ours.notifyDetached()
        assertEquals(0, fired)
    }

    @Test
    fun `apply refuses a leaf with no measurable size`() {
        val leaf = ImageView(context).apply { setImageDrawable(ColorDrawable(Color.RED)) }
        val swap = IosDrawableSwap(leaf, batteryDrawable())
        assertFalse(swap.apply())
    }

    // --------------------------------------------------------------------- sibling (empty/INVISIBLE)

    @Test
    fun `an ImageView sibling gets an alpha zero drawable and is reasserted`() {
        val original = ColorDrawable(Color.BLUE)
        val sibling = ImageView(context).apply { setImageDrawable(original) }
        host(sibling)
        sibling.layout(0, 0, 20, 20)

        val empty = IosDrawables.create(IosIconKind.BATTERY, null, 0, 0)
        val swap = IosSiblingSwap(sibling, empty)
        assertTrue(swap.apply())
        assertSame(empty, sibling.drawable)
        assertEquals(0, empty.alpha)
        assertEquals(20, empty.intrinsicWidth)
        assertEquals(20, empty.intrinsicHeight)

        sibling.setImageDrawable(ColorDrawable(Color.GREEN))
        swap.reassert()
        assertSame(empty, sibling.drawable)

        swap.restore()
        assertSame(original, sibling.drawable)
    }

    @Test
    fun `a sibling swap registers the same-call push repair and unregisters on restore`() {
        val original = ColorDrawable(Color.BLUE)
        val sibling = ImageView(context).apply { setImageDrawable(original) }
        host(sibling)
        sibling.layout(0, 0, 20, 20)

        val empty = IosDrawables.create(IosIconKind.BATTERY, null, 0, 0)
        val swap = IosSiblingSwap(sibling, empty)
        IosPushHooks.clearForTest()
        try {
            assertTrue(swap.apply())
            assertTrue("apply registers the push repair", IosPushHooks.pushWatchedForTest(sibling))

            sibling.setImageDrawable(ColorDrawable(Color.GREEN))
            IosPushHooks.deliverDrawablePushForTest(sibling)
            assertSame("the OEM push is repaired in the same call", empty, sibling.drawable)

            swap.restore()
            assertFalse("restore removes the registration", IosPushHooks.pushWatchedForTest(sibling))
        } finally {
            swap.restore()
            IosPushHooks.clearForTest()
        }
    }

    @Test
    fun `a GONE sibling is left completely untouched`() {
        val original = ColorDrawable(Color.BLUE)
        val sibling = ImageView(context).apply {
            setImageDrawable(original)
            visibility = View.GONE
        }
        val empty = IosDrawables.create(IosIconKind.BATTERY, null, 0, 0)
        val swap = IosSiblingSwap(sibling, empty)

        assertTrue(swap.apply())
        assertEquals(View.GONE, sibling.visibility)
        assertSame(original, sibling.drawable)

        swap.restore()
        assertEquals(View.GONE, sibling.visibility)
        assertSame(original, sibling.drawable)
    }

    @Test
    fun `a text sibling is made INVISIBLE and restored to its exact visibility`() {
        val sibling = TextView(context)
        val swap = IosSiblingSwap(sibling, null)
        assertEquals(View.VISIBLE, sibling.visibility)

        assertTrue(swap.apply())
        assertEquals(View.INVISIBLE, sibling.visibility)

        sibling.visibility = View.VISIBLE
        swap.reassert()
        assertEquals(View.INVISIBLE, sibling.visibility)

        swap.restore()
        assertEquals(View.VISIBLE, sibling.visibility)
    }

    @Test
    fun `sibling apply captures the current original, not a stale constructor snapshot`() {
        val stale = ColorDrawable(Color.RED)
        val sibling = ImageView(context).apply { setImageDrawable(stale) }
        host(sibling)
        sibling.layout(0, 0, 20, 20)

        val current = ColorDrawable(Color.GREEN)
        sibling.setImageDrawable(current)

        val empty = IosDrawables.create(IosIconKind.BATTERY, null, 0, 0)
        val swap = IosSiblingSwap(sibling, empty, originalDrawable = stale)
        assertTrue(swap.apply())
        assertSame(empty, sibling.drawable)

        swap.restore()
        assertSame("the drawable present at apply wins", current, sibling.drawable)
    }

    @Test
    fun `sibling apply pins the apply-time drawable intrinsic, not the stale snapshot`() {
        val stale = SizedDrawable(Color.RED, 11, 13)
        val sibling = ImageView(context).apply { setImageDrawable(stale) }
        host(sibling)
        sibling.layout(0, 0, 20, 20)

        val current = SizedDrawable(Color.GREEN, 31, 27)
        sibling.setImageDrawable(current)

        val empty = IosDrawables.create(IosIconKind.BATTERY, null, 0, 0)
        val swap = IosSiblingSwap(sibling, empty, originalDrawable = stale)
        assertTrue(swap.apply())
        assertSame(empty, sibling.drawable)
        assertEquals("intrinsic comes from the drawable captured at apply", 31, empty.intrinsicWidth)
        assertEquals("intrinsic comes from the drawable captured at apply", 27, empty.intrinsicHeight)

        swap.restore()
        assertSame("restore uses the drawable captured at apply", current, sibling.drawable)
    }

    // ----------------------------------------------------------------------------------- no fight

    @Test
    fun `the pre-draw safety net cannot loop and successful writes do not count`() {
        val original = ColorDrawable(Color.RED)
        val leaf = ImageView(context).apply { setImageDrawable(original) }
        host(leaf)
        leaf.layout(0, 0, 79, 48)

        val ours = batteryDrawable()
        var reasserts = 0
        val swap = IosDrawableSwap(leaf, ours, onReassert = { reasserts++ }, restoreDrawable = original)
        assertTrue(swap.apply())

        var swapped = 0
        repeat(100) {
            leaf.setImageDrawable(ColorDrawable(Color.RED))
            if (swap.reassert()) swapped++
        }
        // One OEM push -> one swap. Never a chain inside a single reassert, and progress is not a fight.
        assertEquals(100, swapped)
        assertEquals(0, reasserts)

        // The next pre-draw sees ours and does nothing.
        assertFalse(swap.reassert())
        assertEquals(0, reasserts)
    }

    @Test
    fun `a successful OEM push is adopted without counting a re-assert`() {
        val original = ColorDrawable(Color.RED)
        val leaf = ImageView(context).apply { setImageDrawable(original) }
        host(leaf)
        leaf.layout(0, 0, 79, 48)

        val ours = batteryDrawable()
        var reasserts = 0
        val swap = IosDrawableSwap(leaf, ours, onReassert = { reasserts++ }, restoreDrawable = original)
        assertTrue(swap.apply())

        var adopted = 0
        repeat(100) {
            leaf.setImageDrawable(ColorDrawable(Color.RED))
            if (swap.onForeignPush()) adopted++
        }
        // One OEM push -> one same-call adoption, and none of them is a fight.
        assertEquals(100, adopted)
        assertEquals(0, reasserts)
        assertSame(ours, leaf.drawable)

        // The next pre-draw sees ours and does nothing.
        assertFalse(swap.reassert())
        assertEquals(0, reasserts)
    }

    @Test
    fun `a write that never sticks counts every reassert as no progress`() {
        val original = ColorDrawable(Color.RED)
        val leaf = NoProgressImageView(context, original)
        host(leaf)
        leaf.layout(0, 0, 79, 48)
        leaf.setImageDrawable(original)

        val ours = batteryDrawable()
        var reasserts = 0
        val swap = IosDrawableSwap(leaf, ours, onReassert = { reasserts++ }, restoreDrawable = original)
        assertTrue(swap.apply())

        // The OEM immediately rewrites its own drawable after every set: ours never stays.
        leaf.swallowOurDrawable = true
        repeat(8) {
            leaf.setImageDrawable(ColorDrawable(Color.BLACK))
            assertFalse(swap.reassert())
        }
        assertEquals(8, reasserts)
        assertSame(original, leaf.drawable)

        // onForeignPush is the push fast path and never counts, even when the write does not stick.
        repeat(8) {
            leaf.setImageDrawable(ColorDrawable(Color.BLACK))
            swap.onForeignPush()
        }
        assertEquals(8, reasserts)
    }

    @Test
    fun `the push registry adopts in the same call and unregisters exactly`() {
        val original = ColorDrawable(Color.RED)
        val leaf = ImageView(context).apply { setImageDrawable(original) }
        host(leaf)
        leaf.layout(0, 0, 79, 48)

        val ours = batteryDrawable()
        var reasserts = 0
        val swap = IosDrawableSwap(leaf, ours, onReassert = { reasserts++ }, restoreDrawable = original)
        assertTrue(swap.apply())

        IosPushHooks.clearForTest()
        try {
            IosPushHooks.watch(leaf, swap)
            assertTrue(IosPushHooks.watchedForTest(leaf))

            leaf.setImageDrawable(ColorDrawable(Color.BLACK))
            IosPushHooks.deliverDrawablePushForTest(leaf)
            assertSame(ours, leaf.drawable)
            assertEquals(0, reasserts)

            var tints = 0
            val callback: () -> Unit = { tints++ }
            IosPushHooks.onTint(leaf, callback)
            assertEquals(1, IosPushHooks.tintCountForTest(leaf))
            IosPushHooks.deliverTintForTest(leaf)
            assertEquals(1, tints)

            IosPushHooks.offTint(leaf, callback)
            assertEquals(0, IosPushHooks.tintCountForTest(leaf))
            IosPushHooks.deliverTintForTest(leaf)
            assertEquals(1, tints)

            IosPushHooks.unwatch(leaf)
            assertFalse(IosPushHooks.watchedForTest(leaf))
            leaf.setImageDrawable(ColorDrawable(Color.BLACK))
            IosPushHooks.deliverDrawablePushForTest(leaf)
            assertEquals("an unwatched leaf keeps the OEM drawable", Color.BLACK, (leaf.drawable as ColorDrawable).color)
        } finally {
            swap.restore()
            IosPushHooks.clearForTest()
        }
    }

    // ------------------------------------------------------------------------------------ helpers

    private fun host(view: View): Activity {
        val controller = Robolectric.buildActivity(Activity::class.java).create()
        val activity = controller.get()
        val root = FrameLayout(activity)
        root.addView(view)
        activity.setContentView(root)
        controller.start().resume().visible()
        return activity
    }

    private fun batteryDrawable(): IosGlyphDrawable {
        val visual = IosMapping.battery(
            level = 100,
            lowThreshold = 20,
            saver = false,
            plugged = false,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING,
            tint = Color.BLACK
        )
        return IosDrawables.create(IosIconKind.BATTERY, visual, 0, 0)
    }

    /** An ImageView whose [setImageDrawable] can be made to fail, to prove restore never blanks. */
    private class ThrowingImageView(context: Context) : ImageView(context) {
        var failSetImageDrawable = false

        override fun setImageDrawable(drawable: Drawable?) {
            if (failSetImageDrawable) throw IllegalStateException("setImageDrawable failed")
            super.setImageDrawable(drawable)
        }
    }

    /**
     * A leaf that refuses our drawable: every write of it is replaced by [original], the true
     * "no progress" fight the anti-thrash guard exists for.
     */
    private class NoProgressImageView(context: Context, private val original: Drawable) :
        ImageView(context) {
        var swallowOurDrawable = false

        override fun setImageDrawable(drawable: Drawable?) {
            if (swallowOurDrawable && drawable is IosGlyphDrawable) {
                super.setImageDrawable(original)
                return
            }
            super.setImageDrawable(drawable)
        }
    }

    /** A drawable with a real intrinsic size, so the apply-time capture is observable. */
    private class SizedDrawable(color: Int, private val w: Int, private val h: Int) :
        ColorDrawable(color) {
        override fun getIntrinsicWidth(): Int = w
        override fun getIntrinsicHeight(): Int = h
    }
}
