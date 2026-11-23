package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.BatteryManager
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The pure draw registry and its installer guard rails. No real Xposed hook is installed: the registry
 * is exercised through [IosDrawingHooks.renderForTest], and the installer only through its pure
 * generated-class rejection and the empty-release path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosDrawingHooksTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        IosDrawingHooks.clearForTest()
    }

    @Test
    fun `a registered session draws ink and unregister stops it`() {
        val view = sizedView()
        val session = IosDrawSession(
            drawable = batteryDrawable(),
            bounds = { intArrayOf(0, 0, 100, 50) }
        )
        IosDrawingHooks.register(view, session)

        val bitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        assertTrue(IosDrawingHooks.renderForTest(view, Canvas(bitmap)))
        assertTrue(inkCount(bitmap) > 0)

        IosDrawingHooks.unregister(view)
        assertNull(IosDrawingHooks.sessionFor(view))
        assertFalse(IosDrawingHooks.renderForTest(view, Canvas(bitmap)))
    }

    @Test
    fun `translateX shifts the drawn ink without changing the bounds`() {
        val view = sizedView()
        val base = Bitmap.createBitmap(140, 50, Bitmap.Config.ARGB_8888)
        IosDrawingHooks.register(
            view,
            IosDrawSession(batteryDrawable(), bounds = { intArrayOf(0, 0, 100, 50) })
        )
        assertTrue(IosDrawingHooks.renderForTest(view, Canvas(base)))

        val shifted = Bitmap.createBitmap(140, 50, Bitmap.Config.ARGB_8888)
        IosDrawingHooks.register(
            view,
            IosDrawSession(
                drawable = batteryDrawable(),
                bounds = { intArrayOf(0, 0, 100, 50) },
                translateX = { 20f }
            )
        )
        assertTrue(IosDrawingHooks.renderForTest(view, Canvas(shifted)))

        assertEquals(20, minInkX(shifted) - minInkX(base))
    }

    @Test
    fun `a missing canvas or empty bounds is a false and never an exception`() {
        val view = sizedView()
        IosDrawingHooks.register(
            view,
            IosDrawSession(batteryDrawable(), bounds = { intArrayOf(0, 0, 100, 50) })
        )
        assertFalse(IosDrawingHooks.renderForTest(view, null))

        IosDrawingHooks.register(
            view,
            IosDrawSession(batteryDrawable(), bounds = { intArrayOf(10, 10, 10, 20) })
        )
        val bitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        assertFalse(IosDrawingHooks.renderForTest(view, Canvas(bitmap)))
        assertEquals(0, inkCount(bitmap))
    }

    @Test
    fun `an unregistered view draws nothing`() {
        val bitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        assertFalse(IosDrawingHooks.renderForTest(sizedView(), Canvas(bitmap)))
    }

    @Test
    fun `a label draws inside the native session and follows its translation`() {
        val view = sizedView()
        val visual = IosVisual(
            part = IosPart.WIFI,
            visible = true,
            tint = Color.WHITE,
            wifiSlot = IosWifiSlot.DataType("5G")
        )
        val labelBox = intArrayOf(40, 5, 40, 30)
        val session = IosDrawSession(
            drawable = batteryDrawable(),
            // A label-only state: the glyph bounds are empty, so only the label draws and the stock
            // drawing is still skipped because render() returns true.
            bounds = { intArrayOf(0, 0, 0, 0) }
        )
        session.label = IosDrawLabel(visual, bounds = { labelBox })
        IosDrawingHooks.register(view, session)

        val base = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        assertTrue("a drawn label must suppress the stock pass", IosDrawingHooks.renderForTest(view, Canvas(base)))
        val baseLeft = minInkX(base)
        assertTrue("the label drew no ink", baseLeft >= 0)
        assertTrue("the label sits at its box", baseLeft >= labelBox[0])

        val shiftedSession = IosDrawSession(
            drawable = batteryDrawable(),
            bounds = { intArrayOf(0, 0, 0, 0) },
            translateX = { 25f }
        )
        shiftedSession.label = IosDrawLabel(visual, bounds = { labelBox })
        IosDrawingHooks.register(view, shiftedSession)
        val shifted = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        assertTrue(IosDrawingHooks.renderForTest(view, Canvas(shifted)))
        assertEquals(
            "the label moves with the native canvas translation",
            25,
            minInkX(shifted) - baseLeft
        )
    }

    @Test
    fun `releaseWhenEmpty only releases when no session remains`() {
        val view = sizedView()
        val session = IosDrawSession(batteryDrawable(), bounds = { intArrayOf(0, 0, 100, 50) })
        IosDrawingHooks.register(view, session)

        IosDrawHookInstaller.releaseWhenEmpty()
        assertSame(session, IosDrawingHooks.sessionFor(view))

        IosDrawingHooks.unregister(view)
        IosDrawHookInstaller.releaseWhenEmpty()
        IosDrawHookInstaller.releaseWhenEmpty()
        assertNull(IosDrawingHooks.sessionFor(view))
    }

    @Test
    fun `generated and lambda class names are never hooked`() {
        assertFalse(IosDrawHookInstaller.ensure(null, "com.example.Foo\$\$Lambda\$1", "onDraw"))
        assertFalse(
            IosDrawHookInstaller.ensure(
                null,
                "com.example.Foo\$ExternalSyntheticLambda0",
                "dispatchDraw"
            )
        )
        assertFalse(IosDrawHookInstaller.ensure(null, "com.example.Foo\$Lambda", "onDraw"))
        assertFalse(IosDrawHookInstaller.ensure(null, "", "onDraw"))
        assertFalse(IosDrawHookInstaller.ensure(null, "com.example.Foo", ""))
    }

    // ------------------------------------------------------------------------------------ helpers

    private fun sizedView(): View = View(context).apply {
        layout(0, 0, 100, 50)
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

    private fun inkCount(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.alpha(it) > 8 }
    }

    private fun minInkX(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (Color.alpha(pixels[y * bitmap.width + x]) > 8) return x
            }
        }
        return -1
    }
}
