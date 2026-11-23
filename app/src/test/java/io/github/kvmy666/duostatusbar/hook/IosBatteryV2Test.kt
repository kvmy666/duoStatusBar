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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
    }

    // ------------------------------------------------------------------ user transform (R7 P4)

    @Test
    fun `install bakes the settings transform from the stock inner size`() {
        val f = fixture()
        val settings = ModuleSettings.DEFAULT.copy(iosBatteryOffset = 10, iosBatteryScale = 150)
        val controller = IosBatteryV2.Controller(
            f.root, f.image, IntArray(0), context, false, 20, settings
        )

        assertTrue(controller.install(reading(level = 50), saver = false))

        val inner = IosGeometry.Relative.sizeFor(
            IosIconKind.BATTERY, 60, 40, dualCell = false
        ).first
        val expected = settings.iosTransform(IosIconKind.BATTERY, inner.toFloat())
        val actual = controller.visualForTest!!.userTransform
        assertEquals(expected.scale, actual.scale, 0.0001f)
        assertEquals(expected.dxPx, actual.dxPx, 0.0001f)
        assertNotEquals(1f, actual.scale)
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
        val b = ModuleSettings.DEFAULT.copy(iosBatteryScale = 150)
        controller.applySettings(b, percentInside = true, lowThreshold = 20, IosUserTransform.IDENTITY)

        assertEquals(1.5f, controller.visualForTest!!.userTransform.scale, 0.0001f)
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
        assertFalse(IosBatteryV2.installed)
        assertTrue(IosBatteryV2.diagnosticLines().single().contains("active=false"))
    }

    @Test
    fun `diagnostic line has the exact shape`() {
        val line = IosBatteryV2.diagnosticLines().single()
        val pattern = Regex(
            "^ios v2 battery: active=(true|false) installed=(true|false) views=\\d+ " +
                "installs=\\d+ reasserts=\\d+ failures=\\d+ level=-?\\d+ state=[A-Z_]+$"
        )
        assertTrue("unexpected line: $line", pattern.matches(line))
    }

    @Test
    fun `installed is a working constructor hook, not a resolved class`() {
        assertFalse(batteryHookInstalled(oplusConstructorHook = false, aospConstructorHook = false))
        assertTrue(batteryHookInstalled(oplusConstructorHook = true, aospConstructorHook = false))
        assertTrue(batteryHookInstalled(oplusConstructorHook = false, aospConstructorHook = true))
    }
}
