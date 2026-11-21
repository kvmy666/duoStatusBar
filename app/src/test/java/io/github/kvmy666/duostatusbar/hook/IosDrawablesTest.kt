package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The in-place [IosGlyphDrawable]: intrinsic size (C2), drawing, captured tint, fixed state colours,
 * alpha, the detach signal and the dual-SIM row artboards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosDrawablesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private class RecordingCallback : Drawable.Callback {
        var invalidations = 0
        override fun invalidateDrawable(who: Drawable) {
            invalidations++
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = Unit
        override fun unscheduleDrawable(who: Drawable, what: Runnable) = Unit
    }

    // ------------------------------------------------------------------ intrinsic size and C2

    @Test
    fun `intrinsic size follows the stock size and falls back to the artboard`() {
        val stock = IosDrawables.create(IosIconKind.BATTERY, batteryVisual(), 40, 30)
        assertEquals(40, stock.intrinsicWidth)
        assertEquals(30, stock.intrinsicHeight)

        val battery = IosDrawables.create(IosIconKind.BATTERY, batteryVisual(), 0, 0)
        assertEquals(IosGeometry.Battery.ARTBOARD_W.roundToInt(), battery.intrinsicWidth)
        assertEquals(IosGeometry.Battery.ARTBOARD_H.roundToInt(), battery.intrinsicHeight)

        val wifi = IosDrawables.create(IosIconKind.WIFI, wifiVisual(), 0, 0)
        assertEquals(IosGeometry.Wifi.ARTBOARD_W.roundToInt(), wifi.intrinsicWidth)
        assertEquals(IosGeometry.Wifi.ARTBOARD_H.roundToInt(), wifi.intrinsicHeight)

        val cell = IosDrawables.create(
            IosIconKind.CELL,
            cellVisual(IosCellMode.SINGLE, IosCellRole.DATA),
            0,
            0
        )
        assertEquals(IosGeometry.Cell.ARTBOARD_W.roundToInt(), cell.intrinsicWidth)
        assertEquals(IosGeometry.Cell.ARTBOARD_H.roundToInt(), cell.intrinsicHeight)
    }

    @Test
    fun `setStockSize invalidates only when it changed`() {
        val drawable = IosDrawables.create(IosIconKind.BATTERY, batteryVisual(), 40, 30)
        val callback = RecordingCallback()
        drawable.setCallback(callback)

        drawable.setStockSize(40, 30)
        assertEquals(0, callback.invalidations)

        drawable.setStockSize(41, 30)
        assertEquals(1, callback.invalidations)

        drawable.setStockSize(41, 30)
        assertEquals(1, callback.invalidations)
    }

    // ----------------------------------------------------------------------------- drawing

    @Test
    fun `draw produces ink and tolerates a null visual or empty bounds`() {
        val visuals = mapOf(
            IosIconKind.BATTERY to batteryVisual(),
            IosIconKind.WIFI to wifiVisual(),
            IosIconKind.CELL to cellVisual(IosCellMode.SINGLE, IosCellRole.DATA)
        )
        for ((kind, visual) in visuals) {
            val drawable = IosDrawables.create(kind, visual, 0, 0)
            assertTrue(kind.name, inkCount(render(drawable, 160, 90)) > 0)
        }

        val nullVisual = IosDrawables.create(IosIconKind.BATTERY, null, 0, 0)
        assertEquals(0, inkCount(render(nullVisual, 160, 90)))
        // A null visual still exposes an artboard fallback for the intrinsic size.
        assertEquals(IosGeometry.Battery.ARTBOARD_W.roundToInt(), nullVisual.intrinsicWidth)

        val emptyBounds = IosDrawables.create(IosIconKind.BATTERY, batteryVisual(), 0, 0)
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        emptyBounds.setBounds(0, 0, 0, 0)
        emptyBounds.draw(Canvas(bitmap))
        assertEquals(0, inkCount(bitmap))
    }

    // ------------------------------------------------------------------------------- tint

    @Test
    fun `tint list and setTint are captured for the dynamic parts`() {
        val white = Color.WHITE

        val batterySnapshot = batteryVisual(level = 100, tint = Color.BLACK)
        val battery = IosDrawables.create(IosIconKind.BATTERY, batterySnapshot, 0, 0)
        battery.setTintList(ColorStateList.valueOf(white))
        assertEquals(
            white,
            sample(battery, batterySnapshot, BATTERY_W, BATTERY_H, 20f, 50f)
        )

        val wifiSnapshot = wifiVisual(tint = Color.BLACK)
        val wifi = IosDrawables.create(IosIconKind.WIFI, wifiSnapshot, 0, 0)
        wifi.setTint(white)
        assertEquals(white, sample(wifi, wifiSnapshot, WIFI_W, WIFI_H, 64.82f, 82.4f))

        val cellSnapshot = cellVisual(IosCellMode.SINGLE, IosCellRole.DATA, bars = 4f, tint = Color.BLACK)
        val cell = IosDrawables.create(IosIconKind.CELL, cellSnapshot, 0, 0)
        cell.setTint(white)
        assertEquals(white, sample(cell, cellSnapshot, CELL_W, CELL_H, 136.55f, 50f))
    }

    @Test
    fun `fixed battery state colours survive a white tint`() {
        val cases = listOf(
            Triple("low", batteryVisual(level = 15, tint = Color.BLACK), IosMapping.COLOR_LOW),
            Triple("saver", batteryVisual(level = 60, saver = true, tint = Color.BLACK), IosMapping.COLOR_SAVER),
            Triple("charging", batteryVisual(level = 60, plugged = true, status = 2, tint = Color.BLACK), IosMapping.COLOR_CHARGING),
            Triple(
                "plugged-not-charging",
                batteryVisual(level = 60, plugged = true, status = 4, tint = Color.BLACK),
                IosMapping.COLOR_PLUGGED_NOT_CHARGING
            )
        )
        for ((name, visual, expected) in cases) {
            val drawable = IosDrawables.create(IosIconKind.BATTERY, visual, 0, 0)
            drawable.setTint(Color.WHITE)
            assertEquals(name, expected, sample(drawable, visual, BATTERY_W, BATTERY_H, 10f, 50f))
        }
    }

    @Test
    fun `drawable alpha multiplies the drawn alpha`() {
        // Sample the uncharged body at level 50: only the remainder layer is drawn there (the charged
        // fill ends at half the body), so the sampled alpha is the colour's own alpha times ours, with
        // no source-over compositing on top.
        val visual = batteryVisual(level = 50, tint = Color.WHITE)
        val drawable = IosDrawables.create(IosIconKind.BATTERY, visual, 0, 0)

        drawable.setAlpha(255)
        val opaque = sample(drawable, visual, BATTERY_W, BATTERY_H, 150f, 50f)
        drawable.setAlpha(128)
        val half = sample(drawable, visual, BATTERY_W, BATTERY_H, 150f, 50f)

        assertEquals(128, Color.alpha(opaque))
        assertEquals(64, Color.alpha(half))
    }

    // ------------------------------------------------------------------------- detach signal

    @Test
    fun `detach listener fires once and not after clearing it`() {
        // `Drawable.setCallback` is final on Android 33-36, so the host signals a replacement through
        // `notifyDetached` (or a real view detach via setVisible(false)); both share this listener.
        val drawable = IosDrawables.create(IosIconKind.BATTERY, batteryVisual(), 0, 0)
        var fired = 0
        drawable.detachListener = { fired++ }

        drawable.notifyDetached()
        assertEquals(1, fired)
        drawable.notifyDetached()
        assertEquals(1, fired)

        drawable.clearDetachListener()
        drawable.notifyDetached()
        assertEquals(1, fired)

        drawable.detachListener = { fired++ }
        drawable.setVisible(false, false)
        assertEquals(2, fired)
        drawable.setVisible(false, false)
        assertEquals(2, fired)

        drawable.clearDetachListener()
        drawable.setVisible(true, false)
        drawable.setVisible(false, false)
        assertEquals(2, fired)
    }

    // --------------------------------------------------------------------- dual-SIM row artboards

    @Test
    fun `dual rows use the row artboard and draw only their row`() {
        val dataVisual = cellVisual(IosCellMode.DUAL, IosCellRole.DATA, bars = 4f, dots = 0f)
        val otherVisual = cellVisual(IosCellMode.DUAL, IosCellRole.OTHER, bars = 0f, dots = 4f)
        val data = IosDrawables.create(IosIconKind.CELL, dataVisual, 0, 0)
        val other = IosDrawables.create(IosIconKind.CELL, otherVisual, 0, 0)

        assertEquals(IosGeometry.Cell.ROW_ARTBOARD_W.roundToInt(), data.intrinsicWidth)
        assertEquals(IosGeometry.Cell.DATA_ROW_ARTBOARD_H.roundToInt(), data.intrinsicHeight)
        assertEquals(IosGeometry.Cell.ROW_ARTBOARD_W.roundToInt(), other.intrinsicWidth)
        assertEquals(IosGeometry.Cell.OTHER_ROW_ARTBOARD_H.roundToInt(), other.intrinsicHeight)

        val dataSpan = inkRowSpan(render(data, data.intrinsicWidth, data.intrinsicHeight))
        val otherSpan = inkRowSpan(render(other, other.intrinsicWidth, other.intrinsicHeight))

        // DATA draws the compressed bar row: its ink fills the row, no dot row below it.
        assertTrue("data bar row span $dataSpan", dataSpan >= (IosGeometry.Cell.DUAL_ROW_H - 2).roundToInt())
        // OTHER draws only dots: its ink height is the dot height, not the bar row.
        assertTrue("other dot row span $otherSpan", otherSpan <= IosGeometry.Cell.DOT_H.roundToInt() + 2)
        assertTrue("data row taller than other row", dataSpan > otherSpan * 2)
    }

    // ----------------------------------------------------------------------------- helpers

    private fun batteryVisual(
        level: Int = 100,
        saver: Boolean = false,
        plugged: Boolean = false,
        status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING,
        tint: Int = Color.BLACK
    ): IosVisual = IosMapping.battery(level, 20, saver, plugged, status, tint)

    private fun wifiVisual(level: Float = 3f, tint: Int = Color.BLACK): IosVisual =
        IosVisual(part = IosPart.WIFI, tint = tint, wifiLevel = level)

    private fun cellVisual(
        mode: IosCellMode,
        role: IosCellRole,
        bars: Float = 4f,
        dots: Float = 3f,
        tint: Int = Color.BLACK
    ): IosVisual = IosVisual(
        part = IosPart.CELL,
        tint = tint,
        cellMode = mode,
        cellRole = role,
        cellBars = bars,
        cellDots = dots
    )

    private fun render(drawable: IosGlyphDrawable, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    /** Samples the bitmap at an artboard coordinate using the renderer's own fit. */
    private fun sample(
        drawable: IosGlyphDrawable,
        visual: IosVisual,
        width: Int,
        height: Int,
        artboardX: Float,
        artboardY: Float
    ): Int {
        val bitmap = render(drawable, width, height)
        val aw = IosGlyphRenderer.artboardWidth(drawable.kind, visual)
        val ah = IosGlyphRenderer.artboardHeight(drawable.kind, visual)
        val scale = min(width / aw, height / ah)
        val dx = (width - aw * scale) / 2f
        val dy = (height - ah * scale) / 2f
        val x = (artboardX * scale + dx).toInt().coerceIn(0, width - 1)
        val y = (artboardY * scale + dy).toInt().coerceIn(0, height - 1)
        return bitmap.getPixel(x, y)
    }

    private fun inkCount(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.alpha(it) > 8 }
    }

    private fun inkRowSpan(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var minY = Int.MAX_VALUE
        var maxY = -1
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (Color.alpha(pixels[y * bitmap.width + x]) > 8) {
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                    break
                }
            }
        }
        return if (maxY < 0) 0 else maxY - minY + 1
    }

    private companion object {
        val BATTERY_W = IosGeometry.Battery.ARTBOARD_W.roundToInt()
        val BATTERY_H = IosGeometry.Battery.ARTBOARD_H.roundToInt()
        val WIFI_W = IosGeometry.Wifi.ARTBOARD_W.roundToInt()
        val WIFI_H = IosGeometry.Wifi.ARTBOARD_H.roundToInt()
        val CELL_W = IosGeometry.Cell.ARTBOARD_W.roundToInt()
        val CELL_H = IosGeometry.Cell.ARTBOARD_H.roundToInt()
    }
}
