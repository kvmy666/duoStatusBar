package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R8 problem a: the pure two-SIM plan, the stacked visual, and the reversible hide/drawable owner.
 *
 * No Xposed here: [IosStackedSim.reconcileViews] is the explicit-state seam the production hook
 * calls with the telephony/settings values it read. The views are plain `FrameLayout`s whose public
 * `getSubId()` is the measured identity reflection uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosStackedSimTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** A stand-in outer: the measured identity plus one cell leaf. */
    private class FakeMobileView(context: Context, private val id: Int) : FrameLayout(context) {
        val signal = ImageView(context)

        /** Mutable so a test can simulate the ROM presenting no subId any more. */
        var currentId: Int = id

        init {
            addView(signal, LayoutParams(60, 63))
        }

        fun getSubId(): Int = currentId
    }

    private fun cell(level: Float): IosCellDrawable = IosDrawables.create(
        IosIconKind.CELL,
        IosVisual(
            part = IosPart.CELL,
            tint = 0xFFFFFFFF.toInt(),
            cellMode = IosCellMode.SINGLE,
            cellRole = IosCellRole.DATA,
            cellBars = level
        ),
        60,
        63,
        relativeSize = true,
        captureColorFilter = true
    ) as IosCellDrawable

    private inner class Fixture(context: Context) {
        val root = FrameLayout(context)
        val one = FakeMobileView(context, 1)
        val two = FakeMobileView(context, 2)
        val oneOriginal = cell(2f)
        val twoOriginal = cell(4f)

        init {
            root.addView(one, FrameLayout.LayoutParams(60, 63))
            root.addView(two, FrameLayout.LayoutParams(60, 63))
            one.signal.setImageDrawable(oneOriginal)
            two.signal.setImageDrawable(twoOriginal)
        }
    }

    @Before
    fun setUp() {
        IosStackedSim.deactivate()
        IosSlotLayout.resetForTest()
    }

    @After
    fun tearDown() {
        IosStackedSim.deactivate()
        IosSlotLayout.resetForTest()
    }

    // ------------------------------------------------------------------------------------ plan

    @Test
    fun `two sims auto and dual stack on the data sim and hide the other`() {
        val expected = IosStackedSim.Plan.Stacked(2, 1, listOf(1))
        assertEquals(expected, IosStackedSim.plan(listOf(1, 2), 2, IosSimDisplay.AUTO))
        assertEquals(expected, IosStackedSim.plan(listOf(1, 2), 2, IosSimDisplay.DUAL))
    }

    @Test
    fun `two sims single collapses to one normal data row`() {
        assertEquals(
            IosStackedSim.Plan.Single(2, listOf(1)),
            IosStackedSim.plan(listOf(1, 2), 2, IosSimDisplay.SINGLE)
        )
    }

    @Test
    fun `one sim is a single row with nothing to hide`() {
        assertEquals(
            IosStackedSim.Plan.Single(2, emptyList()),
            IosStackedSim.plan(listOf(2), 2, IosSimDisplay.AUTO)
        )
        assertEquals(
            IosStackedSim.Plan.Single(2, emptyList()),
            IosStackedSim.plan(listOf(2), 2, IosSimDisplay.SINGLE)
        )
    }

    @Test
    fun `unknown or absent data sim never hides or stacks`() {
        assertEquals(
            IosStackedSim.Plan.None,
            IosStackedSim.plan(listOf(1, 2), 0, IosSimDisplay.AUTO)
        )
        assertEquals(
            IosStackedSim.Plan.None,
            IosStackedSim.plan(listOf(1, 2), 99, IosSimDisplay.DUAL)
        )
        assertEquals(
            IosStackedSim.Plan.None,
            IosStackedSim.plan(listOf(1, 2), -1, IosSimDisplay.SINGLE)
        )
        assertEquals(IosStackedSim.Plan.None, IosStackedSim.plan(emptyList(), 0, IosSimDisplay.AUTO))
    }

    @Test
    fun `three sims stack on data and hide every other, never the data view`() {
        val plan = IosStackedSim.plan(listOf(1, 2, 3), 2, IosSimDisplay.AUTO)
        assertEquals(IosStackedSim.Plan.Stacked(2, 1, listOf(1, 3)), plan)
        assertFalse((plan as IosStackedSim.Plan.Stacked).hideSubIds.contains(2))
    }

    @Test
    fun `sim2 to sim1 to sim2 swaps the data row both ways`() {
        val first = IosStackedSim.plan(listOf(1, 2), 2, IosSimDisplay.AUTO) as IosStackedSim.Plan.Stacked
        val switched = IosStackedSim.plan(listOf(1, 2), 1, IosSimDisplay.AUTO) as IosStackedSim.Plan.Stacked
        assertEquals(2, first.dataSubId)
        assertEquals(1, first.otherSubId)
        assertEquals(1, switched.dataSubId)
        assertEquals(2, switched.otherSubId)
        assertEquals(first, IosStackedSim.plan(listOf(1, 2), 2, IosSimDisplay.AUTO))
    }

    // ---------------------------------------------------------------------------------- visual

    @Test
    fun `stacked visual keeps data bars and swaps in the other level as dots`() {
        val base = IosVisual(
            part = IosPart.CELL,
            tint = 0xFFFFFFFF.toInt(),
            cellBars = 4f,
            cellDots = 1f,
            userTransform = IosUserTransform(dxPx = 2f)
        )
        val stacked = IosStackedSim.stackedVisual(base, 2f)
        assertEquals(IosCellMode.DUAL, stacked.cellMode)
        assertEquals(IosCellRole.BOTH, stacked.cellRole)
        assertEquals(4f, stacked.cellBars, 0f)
        assertEquals(2f, stacked.cellDots, 0f)
        assertEquals(base.userTransform, stacked.userTransform)

        val single = IosStackedSim.singleVisual(stacked)
        assertEquals(IosCellMode.SINGLE, single.cellMode)
        assertEquals(IosCellRole.DATA, single.cellRole)
        assertEquals(4f, single.cellBars, 0f)
        assertEquals(0f, single.cellDots, 0f)
    }

    // --------------------------------------------------------------------------------- runtime

    @Test
    fun `two sims stack on the data view and hide the other in the same pass`() {
        val f = Fixture(context)

        assertTrue(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))

        val stacked = f.two.signal.drawable as IosGlyphDrawable
        val visual = stacked.visualForTest!!
        assertEquals(IosCellMode.DUAL, visual.cellMode)
        assertEquals(IosCellRole.BOTH, visual.cellRole)
        assertEquals(4f, visual.cellBars, 0f)
        assertEquals(2f, visual.cellDots, 0f)
        assertEquals(View.VISIBLE, f.two.visibility)
        assertEquals(View.GONE, f.one.visibility)
        assertEquals(0, f.one.layoutParams.width)
        assertEquals(0, f.one.layoutParams.height)
        assertSame(f.oneOriginal, f.one.signal.drawable)
        assertTrue(IosStackedSim.stackedActive)
        assertEquals(2, IosStackedSim.dataSubId)
        assertEquals(1, IosStackedSim.hiddenSubId)
        assertEquals(2, IosStackedSim.trackedViews)
    }

    @Test
    fun `forced single shows one row on data and hides the other`() {
        val f = Fixture(context)

        assertTrue(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.SINGLE))

        val visual = (f.two.signal.drawable as IosGlyphDrawable).visualForTest!!
        assertEquals(IosCellMode.SINGLE, visual.cellMode)
        assertEquals(IosCellRole.DATA, visual.cellRole)
        assertEquals(4f, visual.cellBars, 0f)
        assertEquals(0f, visual.cellDots, 0f)
        assertEquals(View.GONE, f.one.visibility)
        assertFalse(IosStackedSim.stackedActive)
    }

    @Test
    fun `single sim keeps the normal single row and hides nothing`() {
        val root = FrameLayout(context)
        val only = FakeMobileView(context, 2)
        val original = cell(3f)
        only.signal.setImageDrawable(original)
        root.addView(only, FrameLayout.LayoutParams(60, 63))

        assertTrue(IosStackedSim.reconcileViews(only, 2, IosSimDisplay.AUTO))

        assertSame(original, only.signal.drawable)
        assertEquals(IosCellMode.SINGLE, (only.signal.drawable as IosGlyphDrawable).visualForTest!!.cellMode)
        assertEquals(View.VISIBLE, only.visibility)
        assertEquals(0, IosStackedSim.hiddenSubId)
    }

    @Test
    fun `a second pass reuses the drawable and layout params`() {
        val f = Fixture(context)
        assertTrue(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))
        val drawable = f.two.signal.drawable
        val hiddenParams = f.one.layoutParams

        assertTrue(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))

        assertSame(drawable, f.two.signal.drawable)
        assertSame(hiddenParams, f.one.layoutParams)
        assertEquals(View.GONE, f.one.visibility)
    }

    @Test
    fun `the outer visibility rewrite is re-asserted`() {
        val f = Fixture(context)
        assertTrue(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))

        // The OEM's isVisible collector writes VISIBLE again; the after-hook must win.
        f.one.visibility = View.VISIBLE
        assertTrue(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))

        assertEquals(View.GONE, f.one.visibility)
        assertEquals(0, f.one.layoutParams.width)
    }

    @Test
    fun `a hidden view that drops out of the readable set is restored while the block stays live`() {
        val f = Fixture(context)
        assertTrue(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))
        assertEquals(View.GONE, f.one.visibility)
        assertEquals(0, f.one.layoutParams.width)

        // The ROM rebuilds the outer and getSubId() now reads 0, so collectOuters() no longer sees it.
        f.one.currentId = 0

        // The data SIM still stacks; the dropped outer must come back instead of staying hidden.
        assertTrue(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))

        assertEquals(View.VISIBLE, f.one.visibility)
        assertEquals(60, f.one.layoutParams.width)
        assertEquals(63, f.one.layoutParams.height)
        assertSame(f.oneOriginal, f.one.signal.drawable)
    }

    @Test
    fun `a hidden view that becomes the data sim is restored and drawn stacked`() {
        val f = Fixture(context)
        assertTrue(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))
        assertEquals(View.GONE, f.one.visibility)
        assertEquals(0, f.one.layoutParams.width)

        // The OEM's outer visibility collector sets the newly-data view VISIBLE before our hook.
        f.one.visibility = View.VISIBLE

        assertTrue(IosStackedSim.reconcileViews(f.one, 1, IosSimDisplay.AUTO))

        // The zeroed instance is ours, so the saved size must come back even though it is VISIBLE.
        assertEquals(60, f.one.layoutParams.width)
        assertEquals(63, f.one.layoutParams.height)
        assertEquals(View.VISIBLE, f.one.visibility)
        val visual = (f.one.signal.drawable as IosGlyphDrawable).visualForTest!!
        assertEquals(IosCellMode.DUAL, visual.cellMode)
        assertEquals(IosCellRole.BOTH, visual.cellRole)
        assertEquals(2f, visual.cellBars, 0f)
        assertEquals(4f, visual.cellDots, 0f)
    }

    @Test
    fun `deactivate restores the width of a view the oem set visible after hiding`() {
        val f = Fixture(context)
        assertTrue(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))
        assertEquals(0, f.one.layoutParams.width)

        // The OEM collector rewrites VISIBLE after our hide; the size is still ours to restore.
        f.one.visibility = View.VISIBLE

        IosStackedSim.deactivate()

        assertEquals(60, f.one.layoutParams.width)
        assertEquals(63, f.one.layoutParams.height)
    }

    @Test
    fun `an oem layoutparams overwrite is hidden again and restored to oem values`() {
        val f = Fixture(context)
        assertTrue(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))

        f.one.visibility = View.VISIBLE
        f.one.layoutParams = FrameLayout.LayoutParams(60, 63)
        assertTrue(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))
        assertEquals(View.GONE, f.one.visibility)
        assertEquals(0, f.one.layoutParams.width)
        assertEquals(0, f.one.layoutParams.height)

        // No plan: only the OEM-written instance is live, so its own values come back.
        assertFalse(IosStackedSim.reconcileViews(f.two, 0, IosSimDisplay.AUTO))
        assertEquals(View.VISIBLE, f.one.visibility)
        assertEquals(60, f.one.layoutParams.width)
        assertEquals(63, f.one.layoutParams.height)
    }

    @Test
    fun `restore leaves an oem-owned layout instance alone`() {
        val f = Fixture(context)
        assertTrue(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))

        // The OEM replaced both halves: visibility and the LayoutParams instance.
        f.one.visibility = View.VISIBLE
        f.one.layoutParams = FrameLayout.LayoutParams(60, 63)

        assertFalse(IosStackedSim.reconcileViews(f.two, 0, IosSimDisplay.AUTO))

        assertEquals(View.VISIBLE, f.one.visibility)
        assertEquals(60, f.one.layoutParams.width)
        assertEquals(63, f.one.layoutParams.height)
    }

    @Test
    fun `a data switch swaps the block back and forth`() {
        val f = Fixture(context)
        assertTrue(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))

        assertTrue(IosStackedSim.reconcileViews(f.two, 1, IosSimDisplay.AUTO))
        val onOne = (f.one.signal.drawable as IosGlyphDrawable).visualForTest!!
        assertEquals(2f, onOne.cellBars, 0f)
        assertEquals(4f, onOne.cellDots, 0f)
        assertEquals(View.GONE, f.two.visibility)
        assertSame(f.twoOriginal, f.two.signal.drawable)

        assertTrue(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))
        val onTwo = (f.two.signal.drawable as IosGlyphDrawable).visualForTest!!
        assertEquals(4f, onTwo.cellBars, 0f)
        assertEquals(2f, onTwo.cellDots, 0f)
        assertEquals(View.GONE, f.one.visibility)
        assertSame(f.oneOriginal, f.one.signal.drawable)
        assertTrue(IosStackedSim.swaps >= 2)
    }

    @Test
    fun `a missing or foreign drawable leaves both stock rows`() {
        val f = Fixture(context)
        f.one.signal.setImageDrawable(
            BitmapDrawable(context.resources, Bitmap.createBitmap(60, 63, Bitmap.Config.ARGB_8888))
        )

        assertFalse(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))

        assertEquals(View.VISIBLE, f.one.visibility)
        assertEquals(View.VISIBLE, f.two.visibility)
        assertSame(f.twoOriginal, f.two.signal.drawable)
        assertFalse(IosStackedSim.stackedActive)
    }

    @Test
    fun `a foreign data drawable is never replaced and hides nothing`() {
        val f = Fixture(context)
        val foreign = BitmapDrawable(
            context.resources,
            Bitmap.createBitmap(60, 63, Bitmap.Config.ARGB_8888)
        )
        f.two.signal.setImageDrawable(foreign)

        assertFalse(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))

        assertSame(foreign, f.two.signal.drawable)
        assertEquals(View.VISIBLE, f.one.visibility)
        assertSame(f.oneOriginal, f.one.signal.drawable)
    }

    @Test
    fun `deactivate restores every hidden view and replaced drawable`() {
        val f = Fixture(context)
        assertTrue(IosStackedSim.reconcileViews(f.two, 2, IosSimDisplay.AUTO))
        assertEquals(View.GONE, f.one.visibility)

        IosStackedSim.deactivate()

        assertEquals(View.VISIBLE, f.one.visibility)
        assertEquals(View.VISIBLE, f.two.visibility)
        assertSame(f.oneOriginal, f.one.signal.drawable)
        assertSame(f.twoOriginal, f.two.signal.drawable)
        assertFalse(IosStackedSim.stackedActive)
        assertEquals(0, IosStackedSim.trackedViews)
    }
}
