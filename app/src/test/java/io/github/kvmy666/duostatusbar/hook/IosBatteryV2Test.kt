package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.BatteryManager
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R7 P2: the battery controller (no Xposed), the visual mapping it delegates to, and the object's
 * activation gate/diagnostics. The hook installation itself is only compiled here; it needs SystemUI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IosBatteryV2Test {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        IosSlotLayout.resetForTest()
    }

    @After
    fun tearDown() {
        IosSlotLayout.resetForTest()
    }

    private fun rom(id: String) = RomAdapter(
        id = id,
        label = id,
        systemUiPackage = "com.android.systemui",
        containerIds = emptyList(),
        batteryId = "battery",
        notes = "test"
    )

    private fun stockDrawable(): BitmapDrawable = BitmapDrawable(
        context.resources,
        Bitmap.createBitmap(60, 40, Bitmap.Config.ARGB_8888)
    )

    private class Fixture(
        val root: FrameLayout,
        val image: ImageView,
        val original: BitmapDrawable
    ) {
        fun controller(
            percentInside: Boolean = false,
            lowThreshold: Int = 20,
            boltIds: IntArray = IntArray(0)
        ): IosBatteryV2.Controller = IosBatteryV2.Controller(
            root,
            image,
            boltIds,
            root.context,
            percentInside,
            lowThreshold
        )
    }

    private fun fixture(): Fixture {
        val root = FrameLayout(context)
        val image = ImageView(context)
        val original = stockDrawable()
        image.setImageDrawable(original)
        root.addView(image)
        return Fixture(root, image, original)
    }

    private fun reading(
        level: Int,
        plugged: Boolean = false,
        status: Int = BatteryManager.BATTERY_STATUS_UNKNOWN
    ) = IosBatteryReading(level = level, plugged = plugged, status = status)

    // ------------------------------------------------------------------ install / restore

    @Test
    fun `install without a sticky reading keeps the stock drawable`() {
        val f = fixture()
        val controller = f.controller()

        // R7 P5: a null reading must not install an invisible glyph over the stock icon.
        assertFalse(controller.install(null))
        assertFalse(controller.installedForTest)
        assertSame(f.original, f.image.drawable)
    }

    @Test
    fun `install with the first reading draws the glyph without a prior update`() {
        val f = fixture()
        val controller = f.controller()

        // Production ordering (`controllerFor`): construct, then install(reading, saver); the old tests
        // called update() first, which hid this path (the controller refused to install on device).
        assertTrue(controller.install(reading(level = 50), saver = false))

        assertTrue(controller.installedForTest)
        assertTrue(f.image.drawable is IosBatteryDrawable)
        val visual = controller.visualForTest!!
        assertTrue(visual.visible)
        assertEquals(0.5f, visual.batteryLevel, 0.0001f)
        assertEquals(IosBatteryState.NORMAL, visual.batteryState)
    }

    @Test
    fun `install replaces the stock drawable and restore returns the exact original`() {
        val f = fixture()
        val controller = f.controller()

        assertTrue(controller.install(reading(level = 50), saver = false))
        assertTrue(controller.installedForTest)
        assertNotSame(f.original, f.image.drawable)
        assertTrue(f.image.drawable is IosBatteryDrawable)
        // The stock intrinsic size is copied, never guessed (C2).
        assertEquals(60, f.image.drawable.intrinsicWidth)
        assertEquals(40, f.image.drawable.intrinsicHeight)
        assertTrue(controller.visualForTest!!.visible)

        controller.restore()
        assertSame(f.original, f.image.drawable)
        assertFalse(controller.installedForTest)
    }

    @Test
    fun `install is a no-op when the stock size is unusable`() {
        val root = FrameLayout(context)
        val image = ImageView(context)
        root.addView(image)
        val controller = IosBatteryV2.Controller(root, image, IntArray(0), context, false, 20)

        assertFalse(controller.install(reading(level = 50), saver = false))
        assertFalse(controller.installedForTest)
        assertNull(image.drawable)
        assertEquals("stock size 0x0 <= 0", controller.installFailure)
    }

    // ------------------------------------------------------------------ R11 retry accounting

    @Test
    fun `a zero-size install records a retry reason once and is not a failure`() {
        IosBatteryV2.resetCountersForTest()
        try {
            val root = FrameLayout(context)
            val image = ImageView(context)
            root.addView(image)
            val controller = IosBatteryV2.Controller(root, image, IntArray(0), context, false, 20)

            assertFalse(controller.install(reading(level = 50), saver = false))
            // Production (controllerFor) applies this accounting on a refused install; the host
            // retries on every layout/binder event until the icon has a real box.
            IosBatteryV2.noteInstallRefused(controller)
            IosBatteryV2.noteInstallRefused(controller)

            val line = IosBatteryV2.diagnosticLines().single()
            assertTrue("retries not counted: $line", line.contains("retries=2"))
            assertTrue("an expected retry counted as a failure: $line", line.contains("failures=0"))
            assertTrue(
                "first reason missing: $line",
                line.contains("firstFailure=retry_stock_size_0x0_<=_0")
            )
        } finally {
            IosBatteryV2.resetCountersForTest()
        }
    }

    @Test
    fun `a no-reading install records its own retry reason`() {
        IosBatteryV2.resetCountersForTest()
        try {
            val f = fixture()
            val controller = f.controller()

            assertFalse(controller.install(null, saver = false))
            assertEquals("no reading", controller.installFailure)
            IosBatteryV2.noteInstallRefused(controller)

            val line = IosBatteryV2.diagnosticLines().single()
            assertTrue("retry not counted: $line", line.contains("retries=1"))
            assertTrue("a no-reading retry counted as a failure: $line", line.contains("failures=0"))
            assertTrue(
                "first reason missing: $line",
                line.contains("firstFailure=retry_no_reading")
            )
        } finally {
            IosBatteryV2.resetCountersForTest()
        }
    }

    // ------------------------------------------------------------------ user transform (R7 P4)

    @Test
    fun `install keeps the drawable transform identity because the layout owns the scale`() {
        val f = fixture()
        val settings = ModuleSettings.DEFAULT.copy(iosBatteryOffset = 10, iosBatteryScale = 200)
        val controller = IosBatteryV2.Controller(
            f.root, f.image, IntArray(0), context, false, 20, settings
        )

        assertTrue(controller.install(reading(level = 50), saver = false))

        // R9: a V2 drawable never bakes iosBatteryScale into its transform; IosSlotLayout resizes
        // the real leaf instead, so a slider tick reaches the installed view.
        val actual = controller.visualForTest!!.userTransform
        assertEquals(1f, actual.scale, 0.0001f)
        assertEquals(0f, actual.dxPx, 0.0001f)
    }

    @Test
    fun `applySettings updates an already-installed controller`() {
        val f = fixture()
        val a = ModuleSettings.DEFAULT
        val controller = IosBatteryV2.Controller(
            f.root, f.image, IntArray(0), context, false, 20, a
        )
        assertTrue(controller.install(reading(level = 50), saver = false))
        assertEquals(1f, controller.visualForTest!!.userTransform.scale, 0.0001f)
        assertFalse(controller.visualForTest!!.percentInside)

        // R7 P5: constructor values used to freeze the settings; a later apply must reach the icon.
        // R9: the scale now reaches the layout, not the drawable transform.
        val b = ModuleSettings.DEFAULT.copy(iosBatteryScale = 200)
        controller.applySettings(b, percentInside = true, lowThreshold = 20, IosUserTransform.IDENTITY)

        assertEquals(1f, controller.visualForTest!!.userTransform.scale, 0.0001f)
        assertTrue(controller.visualForTest!!.percentInside)
    }

    @Test
    fun `an explicit transform is the fallback when no settings snapshot is supplied`() {
        val f = fixture()
        val transform = IosUserTransform(dxPx = 3f, scale = 0.5f)
        val controller = IosBatteryV2.Controller(
            f.root, f.image, IntArray(0), context, false, 20, null, transform
        )

        assertTrue(controller.install(reading(level = 50), saver = false))

        assertEquals(transform, controller.visualForTest!!.userTransform)
    }

    // ------------------------------------------------------------------ update / states

    @Test
    fun `update maps the normal battery state and level`() {
        val f = fixture()
        val controller = f.controller()
        controller.install(reading(level = 50), saver = false)

        val visual = controller.visualForTest!!
        assertTrue(visual.visible)
        assertEquals(0.5f, visual.batteryLevel, 0.0001f)
        assertEquals(IosBatteryState.NORMAL, visual.batteryState)
        assertEquals(0f, visual.boltOpacity, 0.0001f)
        assertFalse(visual.percentInside)
    }

    @Test
    fun `update maps charging, low, saver and plugged-not-charging`() {
        val f = fixture()
        val controller = f.controller(lowThreshold = 20)
        controller.install(reading(level = 50), saver = false)

        controller.update(
            reading(level = 50, plugged = true, status = BatteryManager.BATTERY_STATUS_CHARGING),
            saver = false
        )
        controller.visualForTest!!.let {
            assertEquals(IosBatteryState.CHARGING, it.batteryState)
            assertEquals(1f, it.boltOpacity, 0.0001f)
            assertEquals(IosMapping.COLOR_CHARGING, it.batteryFillColor)
        }

        controller.update(reading(level = 10), saver = false)
        controller.visualForTest!!.let {
            assertEquals(IosBatteryState.LOW, it.batteryState)
            assertEquals(IosMapping.COLOR_LOW, it.batteryFillColor)
            assertEquals(0f, it.boltOpacity, 0.0001f)
        }

        controller.update(reading(level = 50), saver = true)
        controller.visualForTest!!.let {
            assertEquals(IosBatteryState.SAVER, it.batteryState)
            assertEquals(IosMapping.COLOR_SAVER, it.batteryFillColor)
        }

        controller.update(
            reading(level = 50, plugged = true, status = BatteryManager.BATTERY_STATUS_CHARGING),
            saver = true
        )
        assertEquals(IosBatteryState.CHARGING_SAVER, controller.visualForTest!!.batteryState)

        controller.update(
            reading(
                level = 100,
                plugged = true,
                status = BatteryManager.BATTERY_STATUS_NOT_CHARGING
            ),
            saver = false
        )
        controller.visualForTest!!.let {
            assertEquals(IosBatteryState.PLUGGED_NOT_CHARGING, it.batteryState)
            assertEquals(IosMapping.COLOR_PLUGGED_NOT_CHARGING, it.batteryFillColor)
            assertEquals(1f, it.boltOpacity, 0.0001f)
        }
    }

    @Test
    fun `update keeps the last level when the broadcast is unreadable`() {
        val f = fixture()
        val controller = f.controller()
        controller.install(reading(level = 42), saver = false)

        controller.update(reading(level = 42), saver = false)
        controller.update(null, saver = false)

        assertEquals(0.42f, controller.visualForTest!!.batteryLevel, 0.0001f)
    }

    @Test
    fun `percentInside is carried onto the visual`() {
        val f = fixture()
        val controller = f.controller(percentInside = true)
        controller.install(reading(level = 30), saver = false)

        controller.update(reading(level = 30), saver = false)

        assertTrue(controller.visualForTest!!.percentInside)
    }

    // ------------------------------------------------------------------ tint (M17)

    @Test
    fun `setTint ignores a zero-alpha int and applies a resolved colour`() {
        val f = fixture()
        val controller = f.controller()
        controller.install(reading(level = 50), saver = false)
        val initial = controller.visualForTest!!.tint

        controller.setTint(0x00AB12FF)
        assertEquals(initial, controller.visualForTest!!.tint)

        controller.setTint(0xFF336699.toInt())
        assertEquals(0xFF336699.toInt(), controller.visualForTest!!.tint)
        assertEquals(0xFF336699.toInt(), controller.visualForTest!!.batteryFillColor)
    }

    // ------------------------------------------------------------------ reassert

    @Test
    fun `reassert restores our drawable after a foreign push`() {
        val f = fixture()
        val controller = f.controller()
        controller.install(reading(level = 50), saver = false)
        val ours = f.image.drawable

        f.image.setImageDrawable(stockDrawable())
        assertTrue(controller.reassert())
        assertSame(ours, f.image.drawable)
        assertFalse(controller.reassert())
    }

    @Test
    fun `reassert makes a GONE icon visible and restore returns the captured visibility`() {
        val f = fixture()
        val controller = f.controller()
        assertTrue(controller.install(reading(level = 50), saver = false))
        val ours = f.image.drawable
        assertEquals(View.VISIBLE, f.image.visibility)

        // `bind$hideBatteryIcon` sets the icon ImageView to 8 and clears its drawable after install.
        f.image.visibility = View.GONE
        f.image.setImageDrawable(null)
        assertTrue(controller.reassert())
        assertEquals(View.VISIBLE, f.image.visibility)
        assertSame(ours, f.image.drawable)

        controller.restore()
        // The visibility captured at install is VISIBLE, not the GONE the binder wrote afterwards.
        assertEquals(View.VISIBLE, f.image.visibility)
        assertSame(f.original, f.image.drawable)
    }

    @Test
    fun `install captures a hidden icon and restore returns that visibility`() {
        val f = fixture()
        // The measured binder can hide the leaf before our first install on a reused view.
        f.image.visibility = View.GONE
        val controller = f.controller()
        assertTrue(controller.install(reading(level = 50), saver = false))
        assertEquals(View.VISIBLE, f.image.visibility)

        controller.restore()
        assertEquals(View.GONE, f.image.visibility)
        assertSame(f.original, f.image.drawable)
    }

    @Test
    fun `reassert hides a bolt the binder made visible again`() {
        val f = fixture()
        val forward = ImageView(context).apply {
            id = 0x7f0a0211
            visibility = View.VISIBLE
        }
        f.root.addView(forward)
        val controller = f.controller(boltIds = intArrayOf(0x7f0a0211))
        assertTrue(controller.install(reading(level = 50), saver = false))
        assertEquals(View.GONE, forward.visibility)

        // `bind$updateChargingView` makes the OEM bolt visible again; reassert must re-hide it.
        forward.visibility = View.VISIBLE
        controller.reassert()
        assertEquals(View.GONE, forward.visibility)

        controller.restore()
        // The original visibility is recorded once, so the restore is VISIBLE, not the GONE we wrote.
        assertEquals(View.VISIBLE, forward.visibility)
    }

    // ------------------------------------------------------------------ R17C density rebuild

    @Test
    fun `a density change rebuilds the glyph at the new stock size and restores the new stock`() {
        val f = fixture()
        val controller = f.controller()
        assertTrue(controller.install(reading(level = 50), saver = false))
        assertEquals(60, f.image.drawable.intrinsicWidth)
        assertEquals(40, f.image.drawable.intrinsicHeight)

        // Samsung's BatteryMeterViewController$1.onDensityOrFontScaleChanged sets a fresh
        // SamsungBatteryMeterDrawable on the same ImageView (smali:116-120, :265-273).
        val newStock = BitmapDrawable(
            context.resources,
            Bitmap.createBitmap(90, 60, Bitmap.Config.ARGB_8888)
        )
        f.image.setImageDrawable(newStock)

        assertTrue(controller.reapplyAfterConfig())
        assertTrue(f.image.drawable is IosBatteryDrawable)
        assertEquals(90, f.image.drawable.intrinsicWidth)
        assertEquals(60, f.image.drawable.intrinsicHeight)

        // The captured stock must be the one this listener minted, not the install-time drawable.
        controller.restore()
        assertSame(newStock, f.image.drawable)
    }

    @Test
    fun `the icon leaf is found by field type when mBatteryIconView is absent`() {
        val root = FakeBatteryView(context)
        assertEquals(root.alternateIcon, IosBatteryV2.iconFieldByType(root))
    }

    private class FakeBatteryView(context: Context) : FrameLayout(context) {
        @JvmField
        val alternateIcon = ImageView(context)
    }

    // ------------------------------------------------------------------ bolts

    @Test
    fun `bolt views are hidden while installed and restored exactly`() {
        val f = fixture()
        val forward = ImageView(context).apply {
            id = 0x7f0a0201
            visibility = View.VISIBLE
        }
        val reverse = ImageView(context).apply {
            id = 0x7f0a0202
            visibility = View.INVISIBLE
        }
        f.root.addView(forward)
        f.root.addView(reverse)
        // A third id that does not resolve must be ignored, not throw.
        val controller = f.controller(boltIds = intArrayOf(0x7f0a0201, 0x7f0a0202, 0x7f0a02ff))

        assertTrue(controller.install(reading(level = 50), saver = false))
        assertEquals(View.GONE, forward.visibility)
        assertEquals(View.GONE, reverse.visibility)

        controller.restore()
        assertEquals(View.VISIBLE, forward.visibility)
        assertEquals(View.INVISIBLE, reverse.visibility)
    }

    // ------------------------------------------------------------------ gate + diagnostics

    @Test
    fun `activation is refused on a non-v2 family`() {
        assertFalse(IosV2Profile.batteryFamily("aosp"))

        IosBatteryV2.activate(context, rom("aosp"), percentInside = false, lowThreshold = 20)

        assertFalse(IosBatteryV2.isActive)
        assertFalse(IosBatteryV2.installedFor("aosp"))
        assertTrue(IosBatteryV2.diagnosticLines().single().contains("active=false"))
    }

    @Test
    fun `diagnostic line has the exact shape`() {
        val line = IosBatteryV2.diagnosticLines().single()
        val pattern = Regex(
            "^ios v2 battery: active=(true|false) installed=(true|false) views=\\d+ " +
                "installs=\\d+ reasserts=\\d+ retries=\\d+ failures=\\d+ firstFailure=\\S+ " +
                "level=-?\\d+ state=[A-Z_]+$"
        )
        assertTrue("unexpected line: $line", pattern.matches(line))
    }

    @Test
    fun `installed is a working constructor hook, not a resolved class`() {
        assertFalse(batteryHookInstalled(oplusConstructorHook = false, aospConstructorHook = false))
        assertTrue(batteryHookInstalled(oplusConstructorHook = true, aospConstructorHook = false))
        assertTrue(batteryHookInstalled(oplusConstructorHook = false, aospConstructorHook = true))
    }

    // ------------------------------------------------------------------ R4 per-family owner

    @Test
    fun `oxygenos probes the Oplus classes and samsung probes only its AOSP owner`() {
        assertEquals(
            listOf(
                IosBatteryProbe.OPLUS_VIEW,
                IosBatteryProbe.OPLUS_BINDER,
                IosBatteryProbe.AOSP_VIEW
            ),
            batteryProbePlan("oxygenos")
        )
        // R17D: ColorOS is the same measured Oplus battery owner, so it probes the same classes.
        assertEquals(
            "coloros probes the same Oplus battery classes as oxygenos",
            listOf(
                IosBatteryProbe.OPLUS_VIEW,
                IosBatteryProbe.OPLUS_BINDER,
                IosBatteryProbe.AOSP_VIEW
            ),
            batteryProbePlan("coloros")
        )
        assertEquals(batteryProbePlan("oxygenos"), batteryProbePlan("coloros"))
        // Samsung must not probe the Oplus view/binder: the absent classes are not Samsung failures
        // and must not show as foreign `missing:` entries in `hooks ... battery=`.
        assertEquals(listOf(IosBatteryProbe.AOSP_VIEW), batteryProbePlan("samsung"))
        // An empty/unknown family keeps today's any-class probe.
        assertEquals(batteryProbePlan("oxygenos"), batteryProbePlan(""))
        assertEquals(batteryProbePlan("oxygenos"), batteryProbePlan("hyperos"))
    }

    @Test
    fun `coloros uses the Oplus battery owner and ignores the AOSP hook`() {
        assertTrue(IosV2Profile.batteryOwnerInstalled("coloros", oplusHooked = true, aospHooked = false))
        assertFalse(IosV2Profile.batteryOwnerInstalled("coloros", oplusHooked = false, aospHooked = true))
    }

    @Test
    fun `coloros needs the oplus binder hook but oxygenos does not`() {
        IosBatteryV2.setOwnerHooksForTest(
            oplusHooked = true,
            aospHooked = false,
            oplusBinderHooked = false
        )
        try {
            // The view hook alone is not enough for ColorOS: the binder must also have installed.
            assertFalse(IosBatteryV2.installedFor("coloros"))
            // OxygenOS keeps its measured view-only rule (freeze).
            assertTrue(IosBatteryV2.installedFor("oxygenos"))
        } finally {
            IosBatteryV2.setOwnerHooksForTest(oplusHooked = false, aospHooked = false)
        }

        IosBatteryV2.setOwnerHooksForTest(
            oplusHooked = true,
            aospHooked = false,
            oplusBinderHooked = true
        )
        try {
            assertTrue(IosBatteryV2.installedFor("coloros"))
        } finally {
            IosBatteryV2.setOwnerHooksForTest(oplusHooked = false, aospHooked = false)
        }
    }

    @Test
    fun `a Samsung owner-only install satisfies samsung but not oxygenos`() {
        IosBatteryV2.setOwnerHooksForTest(oplusHooked = false, aospHooked = true)
        try {
            assertTrue(IosBatteryV2.installedFor("samsung"))
            assertFalse(IosBatteryV2.installedFor("oxygenos"))
        } finally {
            IosBatteryV2.setOwnerHooksForTest(oplusHooked = false, aospHooked = false)
        }
    }

    @Test
    fun `activation is refused when only the foreign owner installed`() {
        IosBatteryV2.setOwnerHooksForTest(oplusHooked = true, aospHooked = false)
        try {
            // Samsung's owner is AOSP, so an Oplus-only install must not activate its battery.
            IosBatteryV2.activate(context, rom("samsung"), percentInside = false, lowThreshold = 20)
            assertFalse(IosBatteryV2.isActive)
        } finally {
            IosBatteryV2.setOwnerHooksForTest(oplusHooked = false, aospHooked = false)
            IosBatteryV2.deactivate()
        }
    }
}
