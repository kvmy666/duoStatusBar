package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
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

    // ------------------------------------------------------------------- cap colour parity

    @Test
    fun `battery cap is the fill colour at half alpha not the tint`() {
        // A fixed LOW state keeps batteryFillColor while a white system tint is captured; the cap must
        // follow the fill colour (inactive grey), not the tint.
        val visual = batteryVisual(level = 15, tint = Color.BLACK)
        val drawable = IosDrawables.create(IosIconKind.BATTERY, visual, 0, 0)
        drawable.setTint(Color.WHITE)

        val bitmap = render(drawable, BATTERY_W, BATTERY_H)
        // Cap centre is artboard (205.75, 51); BATTERY_W x BATTERY_H is a 1:1 fit. A 50 % alpha on a
        // transparent bitmap round-trips through premultiplication, so allow a few channel units.
        val c = bitmap.getPixel(206, 51)
        assertTrue(
            "cap red ${Color.red(c)} vs ${Color.red(IosMapping.COLOR_LOW)}",
            kotlin.math.abs(Color.red(c) - Color.red(IosMapping.COLOR_LOW)) <= 4
        )
        assertTrue(
            "cap green ${Color.green(c)} vs ${Color.green(IosMapping.COLOR_LOW)}",
            kotlin.math.abs(Color.green(c) - Color.green(IosMapping.COLOR_LOW)) <= 4
        )
        assertTrue(
            "cap blue ${Color.blue(c)} vs ${Color.blue(IosMapping.COLOR_LOW)}",
            kotlin.math.abs(Color.blue(c) - Color.blue(IosMapping.COLOR_LOW)) <= 4
        )
        assertTrue("cap colour is not the white tint", c != Color.WHITE)
        assertTrue("cap alpha ${Color.alpha(c)}", Color.alpha(c) in 120..135)
    }

    @Test
    fun `full battery cap is full ink in the fill colour`() {
        // PM rule 2026-11-22: at 100 % the cap is full ink, while every partial level stays at the
        // inactive 0.50 alpha. Render at a 1:1 artboard fit so the cap sample is exact.
        val visual = batteryVisual(level = 100, tint = Color.BLACK)
        val drawable = IosDrawables.create(IosIconKind.BATTERY, visual, 0, 0)

        val c = render(drawable, BATTERY_W, BATTERY_H).getPixel(206, 51)
        assertTrue("cap alpha ${Color.alpha(c)}", Color.alpha(c) in 250..255)
        assertEquals("cap red", Color.red(Color.BLACK), Color.red(c))
        assertEquals("cap green", Color.green(Color.BLACK), Color.green(c))
        assertEquals("cap blue", Color.blue(Color.BLACK), Color.blue(c))
    }

    @Test
    fun `partial battery cap stays at the inactive half alpha`() {
        // level 99 is below a full battery, so the cap keeps CAP_ALPHA (0.50), never full ink.
        val visual = batteryVisual(level = 99, tint = Color.BLACK)
        val drawable = IosDrawables.create(IosIconKind.BATTERY, visual, 0, 0)

        val c = render(drawable, BATTERY_W, BATTERY_H).getPixel(206, 51)
        assertTrue("cap alpha ${Color.alpha(c)}", Color.alpha(c) in 120..135)
    }

    // --------------------------------------------------------------------- user transform

    @Test
    fun `identity user transform draws the default pixels`() {
        val visuals = mapOf(
            IosIconKind.BATTERY to batteryVisual(level = 60),
            IosIconKind.WIFI to wifiVisual(),
            IosIconKind.CELL to cellVisual(IosCellMode.SINGLE, IosCellRole.DATA)
        )
        for ((kind, visual) in visuals) {
            val plain = IosDrawables.create(kind, visual, 0, 0)
            val explicit = IosDrawables.create(kind, visual, 0, 0, IosUserTransform(0f, 1f))
            assertArrayEquals(
                "${kind.name} identity pixels",
                pixels(render(plain, 160, 90)),
                pixels(render(explicit, 160, 90))
            )
            // The same identity through the setter must also be pixel-identical.
            plain.userTransform = IosUserTransform(0f, 1f)
            assertArrayEquals(
                "${kind.name} setter identity pixels",
                pixels(render(IosDrawables.create(kind, visual, 0, 0), 160, 90)),
                pixels(render(plain, 160, 90))
            )
        }
    }

    @Test
    fun `user transform dx shifts ink right without resizing it or the bounds`() {
        val drawable = IosDrawables.create(IosIconKind.BATTERY, batteryVisual(level = 60), 0, 0)
        val before = inkBox(render(drawable, 160, 90))

        drawable.setBounds(0, 0, 160, 90)
        drawable.userTransform = IosUserTransform(dxPx = 3f, scale = 1f)
        // The transform must not write the bounds (C2).
        assertEquals(0, drawable.bounds.left)
        assertEquals(0, drawable.bounds.top)
        assertEquals(160, drawable.bounds.right)
        assertEquals(90, drawable.bounds.bottom)

        val after = inkBox(render(drawable, 160, 90))
        assertEquals("shifted right", 3.0, (after.left - before.left).toDouble(), 1.0)
        assertTrue("width unchanged (${before.width} vs ${after.width})", kotlin.math.abs(before.width - after.width) <= 1)
    }

    @Test
    fun `user transform dx is independent of scale`() {
        // Android concatenates transforms as M' = M * X, so a translate() issued BEFORE a pivot
        // scale() is applied in the already-scaled (device) space: dx must not shrink with scale.
        // A regression to scale-then-translate would make the 0.5x shift ~3 px instead of 6 px.
        fun box(scale: Float, dx: Float): InkBox {
            val drawable = IosDrawables.create(IosIconKind.BATTERY, batteryVisual(level = 60), 0, 0)
            drawable.userTransform = IosUserTransform(dxPx = dx, scale = scale)
            return inkBox(render(drawable, 160, 90))
        }

        val fullBaseline = box(scale = 1f, dx = 0f)
        val fullShifted = box(scale = 1f, dx = 6f)
        val halfBaseline = box(scale = 0.5f, dx = 0f)
        val halfShifted = box(scale = 0.5f, dx = 6f)

        assertEquals(
            "dx shift at scale 1",
            6.0,
            (fullShifted.left - fullBaseline.left).toDouble(),
            1.0
        )
        assertEquals(
            "dx shift at scale 0.5 (scaled dx is the bug)",
            6.0,
            (halfShifted.left - halfBaseline.left).toDouble(),
            1.0
        )
        // Each draw still has its own scaled size (allow 1 px for the bounds clip at scale 1).
        assertEquals("scale-1 width", fullBaseline.width.toFloat(), fullShifted.width.toFloat(), 2f)
        assertEquals("scale-0.5 width", halfBaseline.width.toFloat(), halfShifted.width.toFloat(), 2f)
        assertEquals("half/full width", 0.5f, halfShifted.width.toFloat() / fullShifted.width, 0.08f)
        assertEquals("half/full height", 0.5f, halfShifted.height.toFloat() / fullShifted.height, 0.08f)
    }

    @Test
    fun `scale 0_5 shrinks the ink around the centre`() {
        val drawable = IosDrawables.create(IosIconKind.BATTERY, batteryVisual(level = 60), 0, 0)
        val full = inkBox(render(drawable, 200, 120))
        drawable.userTransform = IosUserTransform(0f, 0.5f)
        val half = inkBox(render(drawable, 200, 120))

        assertEquals(0.5f, half.width.toFloat() / full.width, 0.08f)
        assertEquals(0.5f, half.height.toFloat() / full.height, 0.08f)
        assertTrue(half.left >= 0 && half.right < 200 && half.top >= 0 && half.bottom < 120)
    }

    @Test
    fun `scale zero draws nothing and invalid values are safe`() {
        val drawable = IosDrawables.create(IosIconKind.WIFI, wifiVisual(), 0, 0)

        drawable.userTransform = IosUserTransform(0f, 0f)
        assertEquals(0, inkCount(render(drawable, 160, 90)))

        drawable.userTransform = IosUserTransform(0f, -1f)
        assertEquals(0, inkCount(render(drawable, 160, 90)))

        // Non-finite components fall back to identity (dx -> 0, scale -> 1).
        drawable.userTransform = IosUserTransform(Float.NaN, 1f)
        assertTrue(inkCount(render(drawable, 160, 90)) > 0)

        drawable.userTransform = IosUserTransform(0f, Float.POSITIVE_INFINITY)
        assertTrue(inkCount(render(drawable, 160, 90)) > 0)
    }

    @Test
    fun `user transform never writes the intrinsic size`() {
        val drawable = IosDrawables.create(
            IosIconKind.CELL,
            cellVisual(IosCellMode.SINGLE, IosCellRole.DATA),
            40,
            30
        )
        drawable.setBounds(5, 6, 45, 36)
        drawable.userTransform = IosUserTransform(dxPx = 4f, scale = 0.8f)

        assertEquals(40, drawable.intrinsicWidth)
        assertEquals(30, drawable.intrinsicHeight)
        assertEquals(5, drawable.bounds.left)
        assertEquals(6, drawable.bounds.top)
        assertEquals(45, drawable.bounds.right)
        assertEquals(36, drawable.bounds.bottom)
    }

    @Test
    fun `non-identity transform clips ink to the drawable bounds`() {
        val drawable = IosDrawables.create(IosIconKind.BATTERY, batteryVisual(level = 60), 0, 0)
        drawable.setBounds(20, 10, 180, 100)
        drawable.userTransform = IosUserTransform(dxPx = -100f, scale = 1f)

        val bitmap = Bitmap.createBitmap(220, 140, Bitmap.Config.ARGB_8888)
        drawable.draw(Canvas(bitmap))

        var inside = 0
        var outside = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) <= 8) continue
                val inBounds = x >= 20 && x < 180 && y >= 10 && y < 100
                if (inBounds) inside++ else outside++
            }
        }
        assertEquals("ink outside the drawable bounds", 0, outside)
        assertTrue("some ink remains inside the drawable bounds", inside > 0)
    }

    // ------------------------------------------------------------------- CONTAIN, no squash

    @Test
    fun `contain fit never squashes and stays centred in extreme slots`() {
        val visuals = listOf(
            IosIconKind.WIFI to wifiVisual(),
            IosIconKind.CELL to cellVisual(IosCellMode.SINGLE, IosCellRole.DATA)
        )
        for ((kind, visual) in visuals) {
            val reference = inkBox(render(IosDrawables.create(kind, visual, 0, 0), 300, 300))
            val referenceAspect = reference.width.toFloat() / reference.height
            for ((w, h) in listOf(200 to 40, 40 to 200)) {
                val box = inkBox(render(IosDrawables.create(kind, visual, 0, 0), w, h))
                val aspect = box.width.toFloat() / box.height
                assertEquals(
                    "${kind.name} ${w}x$h aspect (no squash)",
                    referenceAspect,
                    aspect,
                    referenceAspect * 0.04f
                )
                assertEquals("${kind.name} ${w}x$h centre x", w / 2f, box.centerX, 2f)
                // The cell ink sits slightly above the artboard centre by design (frozen 105.6
                // envelope vs the 96.5 bar baseline); 3 px covers it at these extreme slots.
                assertEquals("${kind.name} ${w}x$h centre y", h / 2f, box.centerY, 3f)
            }
        }
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

    private data class InkBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
    }

    /** The tight non-transparent bbox of [bitmap]. */
    private fun inkBox(bitmap: Bitmap): InkBox {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 8) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        require(maxX >= 0 && maxY >= 0) { "no ink in the bitmap" }
        return InkBox(minX, minY, maxX, maxY)
    }

    private fun pixels(bitmap: Bitmap): IntArray {
        val out = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(out, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return out
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
