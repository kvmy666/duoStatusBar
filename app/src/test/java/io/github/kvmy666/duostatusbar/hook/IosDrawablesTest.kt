package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The in-place [IosGlyphDrawable]: intrinsic size (C2), drawing, captured tint, fixed state colours,
 * alpha and the dual-SIM row artboards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosDrawablesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

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
    fun `captureColorFilter takes a PorterDuff colour and ignores anything else`() {
        val visual = wifiVisual(tint = Color.BLACK)

        // The v2 engine opts in; a system PorterDuff filter becomes the dynamic colour.
        val captured = IosDrawables.create(
            IosIconKind.WIFI, visual, 0, 0, captureColorFilter = true
        )
        captured.setColorFilter(PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN))
        assertEquals(Color.WHITE, sample(captured, visual, WIFI_W, WIFI_H, 64.82f, 82.4f))

        // Any other ColorFilter is not a resolved colour and must be ignored.
        val ignored = IosDrawables.create(
            IosIconKind.WIFI, visual, 0, 0, captureColorFilter = true
        )
        ignored.setColorFilter(object : ColorFilter() {})
        assertEquals(Color.BLACK, sample(ignored, visual, WIFI_W, WIFI_H, 64.82f, 82.4f))

        // Default false keeps the old host's behaviour exactly.
        val legacy = IosDrawables.create(IosIconKind.WIFI, visual, 0, 0)
        legacy.setColorFilter(PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN))
        assertEquals(Color.BLACK, sample(legacy, visual, WIFI_W, WIFI_H, 64.82f, 82.4f))
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
    fun `battery cap is neutral bar ink at the LOW alpha not the state colour`() {
        // A fixed LOW state keeps its red fill, but the cap is NEUTRAL ink: with a white system tint
        // it is white at the R11 LOW alpha (0.30, measured on the red critical reference), never
        // COLOR_LOW.
        val visual = batteryVisual(level = 15, tint = Color.BLACK)
        val drawable = IosDrawables.create(IosIconKind.BATTERY, visual, 0, 0)
        drawable.setTint(Color.WHITE)

        val bitmap = render(drawable, BATTERY_W, BATTERY_H)
        // Cap centre is artboard (205.75, 51); BATTERY_W x BATTERY_H is a 1:1 fit. A 30 % alpha on a
        // transparent bitmap round-trips through premultiplication, so allow a few channel units.
        val c = bitmap.getPixel(206, 51)
        assertTrue(
            "cap red ${Color.red(c)} vs white",
            kotlin.math.abs(Color.red(c) - Color.red(Color.WHITE)) <= 4
        )
        assertTrue(
            "cap green ${Color.green(c)} vs white",
            kotlin.math.abs(Color.green(c) - Color.green(Color.WHITE)) <= 4
        )
        assertTrue(
            "cap blue ${Color.blue(c)} vs white",
            kotlin.math.abs(Color.blue(c) - Color.blue(Color.WHITE)) <= 4
        )
        assertTrue("cap is not the red state colour", c != IosMapping.COLOR_LOW)
        assertTrue("cap alpha ${Color.alpha(c)}", Color.alpha(c) in 72..82)
    }

    @Test
    fun `full battery cap is full neutral ink`() {
        // PM rule 2026-11-22: at 100 % the cap is full ink, while every partial level stays at the
        // inactive 0.50 alpha. The ink is neutral (the black bar tint), not a state colour.
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
        // level 99 is below a full battery, so the nub keeps the R9 luminance alpha (0.30 for
        // dark ink), never full ink.
        val visual = batteryVisual(level = 99, tint = Color.BLACK)
        val drawable = IosDrawables.create(IosIconKind.BATTERY, visual, 0, 0)

        val c = render(drawable, BATTERY_W, BATTERY_H).getPixel(206, 51)
        // R9 luminance rule: black ink -> 0.30 alpha.
        assertTrue("cap alpha ${Color.alpha(c)}", Color.alpha(c) in 72..82)
    }

    @Test
    fun `coloured charging fill still has a neutral cap`() {
        // Green charging fill, black bar tint: the cap is black at half alpha, so all three channels
        // agree even though bodyFill is COLOR_CHARGING.
        val visual = batteryVisual(
            level = 60,
            plugged = true,
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            tint = Color.BLACK
        )
        val drawable = IosDrawables.create(IosIconKind.BATTERY, visual, 0, 0)

        val c = render(drawable, BATTERY_W, BATTERY_H).getPixel(206, 51)
        // R9 luminance rule: black ink -> 0.30 alpha.
        assertTrue("cap alpha ${Color.alpha(c)}", Color.alpha(c) in 72..82)
        val spread = maxOf(Color.red(c), Color.green(c), Color.blue(c)) -
            minOf(Color.red(c), Color.green(c), Color.blue(c))
        assertTrue("cap channels differ by $spread", spread <= 3)
        assertTrue("cap is not the green fill", c != IosMapping.COLOR_CHARGING)
    }

    // ----------------------------------------------------------------- percentage inside body

    @Test
    fun `percentInside off leaves the digit seat free of background ink`() {
        // With the setting off nothing is cut out: the "69" seat is solid body ink (no transparent
        // pixel) and no guessed white/black knockout is painted.
        val visual = batteryVisual(level = 69, tint = Color.BLACK)
        val drawable = IosDrawables.create(IosIconKind.BATTERY, visual, 0, 0)
        val bitmap = render(drawable, BATTERY_W, BATTERY_H)

        val seat = digitSeat("69")
        assertEquals(0, countTransparent(bitmap, seat[0], seat[1], seat[2], seat[3]))
        assertEquals(0, countBackgroundInk(bitmap, seat[0], seat[1], seat[2], seat[3]))
        val fill = bitmap.getPixel(25, 20)
        assertEquals("stroke seat alpha", 255, Color.alpha(fill))
        assertTrue("stroke seat red ${Color.red(fill)}", Color.red(fill) <= 2)
        assertTrue("stroke seat green ${Color.green(fill)}", Color.green(fill) <= 2)
        assertTrue("stroke seat blue ${Color.blue(fill)}", Color.blue(fill) <= 2)
    }

    @Test
    fun `percentInside clears the digits through the body`() {
        // R6 item 6: the digit ink is a hole, so on a transparent canvas those pixels are alpha 0.
        val visual = batteryVisual(level = 69, tint = Color.BLACK).copy(percentInside = true)
        val drawable = IosDrawables.create(IosIconKind.BATTERY, visual, 0, 0)
        val bitmap = render(drawable, BATTERY_W, BATTERY_H)

        val seat = digitSeat("69")
        val holes = countTransparent(bitmap, seat[0], seat[1], seat[2], seat[3])
        assertTrue("no cleared digit ink inside the body ($holes)", holes > 100)
        // The body itself is still drawn: a fill sample away from the digit holes is opaque.
        val fill = bitmap.getPixel(25, 20)
        assertEquals("body fill alpha", 255, Color.alpha(fill))
    }

    @Test
    fun `percentInside at 100 percent keeps every digit inside the body`() {
        val visual = batteryVisual(level = 100, tint = Color.BLACK).copy(percentInside = true)
        val drawable = IosDrawables.create(IosIconKind.BATTERY, visual, 0, 0)
        val bitmap = render(drawable, BATTERY_W, BATTERY_H)

        val b = IosGeometry.Battery
        val left = b.BODY_LEFT.roundToInt()
        val top = b.BODY_TOP.roundToInt()
        val right = (b.BODY_LEFT + b.BODY_W).roundToInt() - 1
        val bottom = (b.BODY_TOP + b.BODY_H).roundToInt() - 1
        var count = 0
        for (y in top..bottom) {
            for (x in left..right) {
                if (Color.alpha(bitmap.getPixel(x, y)) == 0) count++
            }
        }
        assertTrue("no cleared digit ink", count > 100)
        // The holes are inside the body: its straight left/right edges stay inked (the rounded
        // corners are outside the body shape, so only the straight middle band is checked).
        val inset = b.BODY_RADIUS.roundToInt() + 1
        for (y in (top + inset)..(bottom - inset)) {
            assertTrue("body left edge cleared at y=$y", Color.alpha(bitmap.getPixel(left, y)) > 0)
            assertTrue("body right edge cleared at y=$y", Color.alpha(bitmap.getPixel(right, y)) > 0)
        }
    }

    @Test
    fun `percentInside charging draws a solid white inline bolt and no large bolt`() {
        val visual = batteryVisual(
            level = 60,
            plugged = true,
            status = BatteryManager.BATTERY_STATUS_CHARGING,
            tint = Color.BLACK
        ).copy(percentInside = true)
        val drawable = IosDrawables.create(IosIconKind.BATTERY, visual, 0, 0)
        val bitmap = render(drawable, BATTERY_W, BATTERY_H)

        // The large bolt (when the setting is off) is white in the top strip; inside mode puts the
        // small bolt after the digits, so that strip must stay solid body ink (no hole).
        assertEquals(0, countTransparent(bitmap, 60, 4, 135, 18))
        // Derive the inline bolt's own box from the measured v4 layout instead of hard-coding a seat.
        val b = IosGeometry.Battery
        val group = IosDigits.groupWidth("60", withBolt = true)
        val left = b.BODY_LEFT + b.BODY_W / 2f - group / 2f
        val boltLeft = left + IosDigits.textInkWidth("60") + IosDigits.INLINE_BOLT_GAP
        val boltRight = boltLeft + IosDigits.INLINE_BOLT_W
        val boltTop = b.BODY_TOP + (b.BODY_H - IosDigits.INLINE_BOLT_H) / 2f
        val boltBottom = boltTop + IosDigits.INLINE_BOLT_H
        val x0 = boltLeft.toInt() + 2
        val y0 = boltTop.toInt() + 2
        val x1 = kotlin.math.ceil(boltRight).toInt() - 2
        val y1 = kotlin.math.ceil(boltBottom).toInt() - 2
        // R11: the charge bolt is solid white over the fill/remainder, not a knockout hole.
        assertEquals(
            "inline bolt must not be cleared",
            0,
            countTransparent(bitmap, x0, y0, x1, y1)
        )
        assertTrue(
            "inline bolt is not solid white",
            countBackgroundInk(bitmap, x0, y0, x1, y1) > 50
        )
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
        val before = inkBox(render(drawable, 400, 220))

        drawable.setBounds(0, 0, 400, 220)
        drawable.userTransform = IosUserTransform(dxPx = 3f, scale = 1f)
        // The transform must not write the bounds (C2).
        assertEquals(0, drawable.bounds.left)
        assertEquals(0, drawable.bounds.top)
        assertEquals(400, drawable.bounds.right)
        assertEquals(220, drawable.bounds.bottom)

        val after = inkBox(render(drawable, 400, 220))
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
            return inkBox(render(drawable, 900, 460))
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
    fun `non-identity transform is not self-clipped and paints a substantial share outside`() {
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
        // Item 5: the renderer must not self-clip to its draw box; the host is the only owner of the
        // overflow room (ancestor unclip / enlarged overlay canvas). With this -100 px shift the
        // native render produced inside=3037 / outside=1480, so a third of the ink is the safe floor:
        // one anti-aliased pixel would still pass a "> 0" check and hide a reintroduced clip.
        assertTrue("some ink remains inside the drawable bounds", inside > 0)
        assertTrue(
            "a third of the shifted ink must paint outside the old bounds (was 1480/3037)",
            outside >= inside / 3
        )
        assertTrue("the overflow must not collapse to a few pixels (was 1480)", outside >= 1000)
    }

    @Test
    fun `identity transform keeps the glyph inside the drawable bounds`() {
        val drawable = IosDrawables.create(IosIconKind.BATTERY, batteryVisual(level = 60), 0, 0)
        drawable.setBounds(20, 10, 180, 100)

        val bitmap = Bitmap.createBitmap(220, 140, Bitmap.Config.ARGB_8888)
        drawable.draw(Canvas(bitmap))

        var outside = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) <= 8) continue
                if (x < 20 || x >= 180 || y < 10 || y >= 100) outside++
            }
        }
        // Removing the transform clip must not change the identity path: the CONTAIN fit already
        // keeps the glyph within its own bounds.
        assertEquals("identity ink outside the drawable bounds", 0, outside)
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

    /** Counts near-white opaque pixels in an inclusive-exclusive artboard rectangle. */
    /** The pixel box that exactly covers [text] when it is centred inside the body. */
    private fun digitSeat(text: String): IntArray {
        val b = IosGeometry.Battery
        val group = IosDigits.groupWidth(text, withBolt = false)
        val left = b.BODY_LEFT + b.BODY_W / 2f - group / 2f
        val top = b.BODY_TOP + (b.BODY_H - IosDigits.CAP_HEIGHT) / 2f
        return intArrayOf(
            left.toInt(),
            top.toInt(),
            ceil(left + group).toInt(),
            ceil(top + IosDigits.CAP_HEIGHT).toInt()
        )
    }

    /** Counts fully transparent (alpha 0) pixels in an inclusive-exclusive bitmap rectangle. */
    private fun countTransparent(
        bitmap: Bitmap,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int
    ): Int {
        var count = 0
        for (y in y0 until minOf(y1, bitmap.height)) {
            for (x in x0 until minOf(x1, bitmap.width)) {
                if (Color.alpha(bitmap.getPixel(x, y)) == 0) count++
            }
        }
        return count
    }

    private fun countBackgroundInk(
        bitmap: Bitmap,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int
    ): Int {
        var count = 0
        for (y in y0 until minOf(y1, bitmap.height)) {
            for (x in x0 until minOf(x1, bitmap.width)) {
                val c = bitmap.getPixel(x, y)
                if (Color.alpha(c) > 200 &&
                    Color.red(c) > 245 &&
                    Color.green(c) > 245 &&
                    Color.blue(c) > 245
                ) {
                    count++
                }
            }
        }
        return count
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
