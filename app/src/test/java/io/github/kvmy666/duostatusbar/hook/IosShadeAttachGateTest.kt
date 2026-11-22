package io.github.kvmy666.duostatusbar.hook

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/** R3 A1: a failed qs/shade strip is memoised for 5 s instead of being retried on every pass. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosShadeAttachGateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a failure blocks retries for five seconds and then expires`() {
        val gate = IosShadeAttachGate(5_000L)
        val window = View(context)

        assertTrue(gate.shouldAttempt(window, 1_000L))
        gate.noteFailure(window, 1_000L)
        assertFalse(gate.shouldAttempt(window, 1_001L))
        assertFalse(gate.shouldAttempt(window, 5_999L))
        assertTrue("the memo expires at 5 s", gate.shouldAttempt(window, 6_000L))
    }

    @Test
    fun `a different window is attempted immediately`() {
        val gate = IosShadeAttachGate(5_000L)
        val first = View(context)
        val second = View(context)

        gate.noteFailure(first, 1_000L)
        assertFalse(gate.shouldAttempt(first, 1_500L))
        assertTrue(gate.shouldAttempt(second, 1_500L))
    }

    @Test
    fun `a panel change reset clears the memo`() {
        val gate = IosShadeAttachGate(5_000L)
        val window = View(context)
        gate.noteFailure(window, 1_000L)

        gate.reset()
        assertTrue(gate.shouldAttempt(window, 1_001L))
    }

    @Test
    fun `a success clears the memo`() {
        val gate = IosShadeAttachGate(5_000L)
        val window = View(context)
        gate.noteFailure(window, 1_000L)

        gate.noteSuccess()
        assertTrue(gate.shouldAttempt(window, 1_001L))
    }

    // ---------------------------------------------------------------- R3 A1 re-target decision

    @Test
    fun `not attached never re-targets`() {
        assertFalse(
            IosReplaceHost.retarget(false, false, true, false, Long.MAX_VALUE)
        )
    }

    @Test
    fun `a usable attachment is kept`() {
        assertFalse(
            IosReplaceHost.retarget(true, true, true, false, Long.MAX_VALUE)
        )
    }

    @Test
    fun `no usable copy keeps the hidden attachment`() {
        assertFalse(
            "a closing panel must be reused on the next open",
            IosReplaceHost.retarget(true, false, false, false, Long.MAX_VALUE)
        )
    }

    @Test
    fun `an attached strip that is the usable copy is kept`() {
        assertFalse(
            IosReplaceHost.retarget(true, false, true, true, Long.MAX_VALUE)
        )
    }

    @Test
    fun `a visible copy re-targets once`() {
        assertTrue(
            IosReplaceHost.retarget(true, false, true, false, Long.MAX_VALUE)
        )
    }

    @Test
    fun `a re-target inside one second is throttled`() {
        assertFalse(
            IosReplaceHost.retarget(true, false, true, false, 999L)
        )
    }

    @Test
    fun `a re-target at exactly one second is allowed`() {
        assertTrue(
            IosReplaceHost.retarget(
                true,
                false,
                true,
                false,
                IosReplaceHost.MIN_RETARGET_INTERVAL_MS
            )
        )
    }

    @Test
    fun `two alternating copies cause at most one teardown per second`() {
        var now = 10_000L
        var last: Long? = null
        val retargets = ArrayList<Long>()
        repeat(10) {
            val decision = IosReplaceHost.retarget(
                attached = true,
                attachedUsable = false,
                usableExists = true,
                currentStripIsUsableCopy = false,
                msSinceLastRetarget = last?.let { at -> now - at } ?: Long.MAX_VALUE
            )
            if (decision) {
                retargets.add(now)
                last = now
            }
            now += 500L
        }
        assertEquals("one re-target per second over the 5 s window: $retargets", 5, retargets.size)
        for (i in 1 until retargets.size) {
            assertTrue(
                "re-targets are at least 1 s apart: $retargets",
                retargets[i] - retargets[i - 1] >= IosReplaceHost.MIN_RETARGET_INTERVAL_MS
            )
        }
    }

    // ------------------------------------------------------- R3 A1 re-target on a real view tree

    @Suppress("DEPRECATION")
    private class FakeResources(
        parent: Resources,
        private val names: Map<Int, String>
    ) : Resources(parent.assets, parent.displayMetrics, parent.configuration) {
        override fun getResourceEntryName(id: Int): String =
            names[id] ?: super.getResourceEntryName(id)
    }

    private open class NamedGroup(context: Context, entry: String) : LinearLayout(context) {
        private var fake: Resources? = null

        init {
            id = NEXT_NAMED_ID.getAndIncrement()
            fake = FakeResources(context.resources, mapOf(id to entry))
        }

        override fun getResources(): Resources = fake ?: super.getResources()
    }

    private class NamedImageView(context: Context, entry: String) : ImageView(context) {
        private var fake: Resources? = null

        init {
            id = NEXT_NAMED_ID.getAndIncrement()
            fake = FakeResources(context.resources, mapOf(id to entry))
        }

        override fun getResources(): Resources = fake ?: super.getResources()
    }

    /** Exactly the measured OPLUS battery outer name, so the planner resolves its leaf row. */
    private class StatBatteryMeterView(context: Context) : FrameLayout(context)

    private class BatteryStrip(
        val strip: LinearLayout,
        val outer: FrameLayout,
        val leaf: ImageView,
        val original: Drawable
    )

    private fun batteryStrip(entry: String = "system_icons"): BatteryStrip {
        val strip = NamedGroup(context, entry)
        val outer = StatBatteryMeterView(context)
        val leaf = NamedImageView(context, "battery_icon_view")
        val original = ColorDrawable(Color.RED)
        leaf.setImageDrawable(original)
        outer.addView(leaf, FrameLayout.LayoutParams(79, 48))
        strip.addView(outer, LinearLayout.LayoutParams(90, 48))
        return BatteryStrip(strip, outer, leaf, original)
    }

    private fun layoutStrip(tree: BatteryStrip) {
        tree.strip.layout(0, 0, 200, 48)
        tree.outer.layout(0, 0, 90, 48)
        tree.leaf.layout(0, 0, 79, 48)
    }

    @Test
    fun `a shown copy re-targets a hidden attachment once and keeps the live one`() {
        IosSurfaceOwners.clearForTest()
        IosSurfaceThrash.clearForTest()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        val hidden = batteryStrip()
        val shown = batteryStrip()
        hidden.strip.visibility = View.INVISIBLE
        root.addView(hidden.strip, FrameLayout.LayoutParams(200, 48))
        root.addView(shown.strip, FrameLayout.LayoutParams(200, 48))
        root.layout(0, 0, 1000, 100)
        layoutStrip(hidden)
        layoutStrip(shown)

        val host = IosReplaceHost(
            context = context,
            classLoader = null,
            isMainBar = false,
            surface = "retarget test",
            batterySource = { IosBatteryReading(50, false, 0) }
        )
        try {
            assertTrue(host.attachIn(hidden.strip, null))
            assertFalse("the attached copy is not shown", hidden.strip.isShown)
            assertTrue("the hidden copy adopted while attached", hidden.leaf.drawable is IosBatteryDrawable)
            assertSame("the shown copy is still stock", shown.original, shown.leaf.drawable)
            assertSame(
                "the shown copy is the usable one",
                shown.strip,
                host.findUsableStripForTest(root, "system_icons")
            )

            assertTrue(host.attachSurface("retarget test", root, "system_icons"))
            assertTrue(host.attachedIn(root))
            assertSame("the hidden copy was restored once", hidden.original, hidden.leaf.drawable)
            val installed = shown.leaf.drawable
            assertTrue("the shown copy now draws", installed is IosBatteryDrawable)

            val records = host.recordCountForTest()
            assertTrue(host.attachSurface("retarget test", root, "system_icons"))
            assertEquals("no second teardown/re-attach", records, host.recordCountForTest())
            assertSame("the live drawable is kept", installed, shown.leaf.drawable)
            assertSame("the hidden copy stays restored", hidden.original, hidden.leaf.drawable)
        } finally {
            host.teardown()
            IosSurfaceOwners.clearForTest()
            IosSurfaceThrash.clearForTest()
        }
    }

    private companion object {
        val NEXT_NAMED_ID = AtomicInteger(0x7f0e0000.toInt())
    }
}
