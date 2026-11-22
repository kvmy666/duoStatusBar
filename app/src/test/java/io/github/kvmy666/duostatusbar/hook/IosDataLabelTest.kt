package io.github.kvmy666.duostatusbar.hook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.security.MessageDigest

/**
 * R4B data-type label: the contract, the derived metrics, and the rendered ink at the production
 * Wi-Fi path (`IosDrawables.create(WIFI, ..., 80, 50, relativeSize = true)`, 4x supersample).
 *
 * No iOS reference shows this label. The owner rule is "match the digit cap to the Wi-Fi glyph
 * height"; the assertions below pin that, plus the horizontal fit inside the 50x31 inner box.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosDataLabelTest {

    private val slotWidth = 80
    private val slotHeight = 50
    private val ss = 4

    // ------------------------------------------------------------------------ contract and metrics

    @Test
    fun `every supported label has non-empty ink`() {
        for (label in IosGeometry.DataLabel.LABELS) {
            val ink = inkCount(render(labelVisual(label)))
            assertTrue("$label drew no ink", ink > 0)
        }
    }

    @Test
    fun `NONE and unsupported labels draw nothing`() {
        val none = IosVisual(
            part = IosPart.WIFI,
            tint = Color.BLACK,
            wifiLevel = 3f,
            wifiSlot = IosWifiSlot.None
        )
        assertEquals("NONE drew ink", 0, inkCount(render(none)))

        assertEquals("unsupported label drew ink", 0, inkCount(render(labelVisual("XX"))))
        assertEquals("unsupported label drew ink", 0, inkCount(render(labelVisual("LTE2"))))
    }

    @Test
    fun `WIFI still draws the exact Wi-Fi glyph`() {
        val default = IosVisual(part = IosPart.WIFI, tint = Color.BLACK, wifiLevel = 3f)
        val explicit = default.copy(wifiSlot = IosWifiSlot.Wifi)
        val bitmap = render(default)
        assertTrue("default WIFI drew no ink", inkCount(bitmap) > 0)
        assertTrue("explicit WIFI drew no ink", inkCount(render(explicit)) > 0)

        // Golden baseline of the default Wi-Fi render through the production path
        // (IosDrawables.create(WIFI, ..., 80*4, 50*4, relativeSize = true), 4x supersample,
        // transparent background). Any change to the Wi-Fi glyph or its fit fails here.
        assertEquals("Wi-Fi 4x ink bbox", "77,39,244,162", rawInkBox(bitmap))
        assertEquals(
            "Wi-Fi alpha channel SHA-256",
            "4d1f3883bf5ebac169f192d17428651edead76107eb9ce9bf39f785b35104373",
            alphaSha256(bitmap)
        )

        assertEquals(
            IosGeometry.Wifi.ARTBOARD_W,
            IosGlyphRenderer.artboardWidth(IosIconKind.WIFI, default),
            0.0001f
        )
        assertEquals(
            IosGeometry.Wifi.ARTBOARD_H,
            IosGlyphRenderer.artboardHeight(IosIconKind.WIFI, default),
            0.0001f
        )
    }

    @Test
    fun `label artboard width and height stay consistent`() {
        val d = IosGeometry.DataLabel
        val wifi = IosGeometry.Wifi
        for (label in d.LABELS) {
            val ink = d.widthOf(label)
            assertNotNull(label, ink)
            val artboard = d.artboardWidth(label)
            assertNotNull(label, artboard)
            assertEquals(
                "$label ink + margins",
                ink!! + 2f * wifi.ARTBOARD_MARGIN,
                artboard!!,
                0.0001f
            )
            assertEquals(
                "$label renderer artboard width",
                artboard,
                IosGlyphRenderer.artboardWidth(IosIconKind.WIFI, labelVisual(label)),
                0.0001f
            )
            assertEquals(
                "$label artboard height stays the Wi-Fi height",
                wifi.ARTBOARD_H,
                IosGlyphRenderer.artboardHeight(IosIconKind.WIFI, labelVisual(label)),
                0.0001f
            )
        }
    }

    @Test
    fun `supports accepts only the measured labels`() {
        val d = IosGeometry.DataLabel
        for (label in d.LABELS) assertTrue(label, d.supports(label))
        for (label in listOf("", "L", "WIFI", "lte", "5G++", "NR", "H+")) {
            assertFalse("$label must be unsupported", d.supports(label))
            assertNull("$label has no ink width", d.widthOf(label))
            assertNull("$label has no artboard", d.artboardWidth(label))
        }
    }

    @Test
    fun `stroke is the digit weight and the cap is the Wi-Fi ink height`() {
        val d = IosGeometry.DataLabel
        assertEquals(94.77f, d.CAP_HEIGHT, 0.0001f)
        assertEquals(18.954f, d.STROKE, 0.001f)
        assertEquals(11.40f / 57f, d.STROKE / d.CAP_HEIGHT, 0.0001f)
    }

    @Test
    fun `digits in labels match IosDigits scaled by the cap`() {
        val d = IosGeometry.DataLabel
        val scale = d.CAP_HEIGHT / IosDigits.CAP_HEIGHT
        for (ch in charArrayOf('3', '4', '5')) {
            val glyph = IosDigits.glyph(ch)
            assertEquals("$ch width", glyph.width * scale, d.inkWidth(ch), 0.0001f)
            assertEquals("$ch advance", glyph.advance * scale, d.advance(ch), 0.0001f)
            assertEquals(
                "$ch bearing",
                IosDigits.bearing(glyph) * scale,
                d.bearing(ch),
                0.0001f
            )
            assertTrue("$ch must be a scaled digit", d.isDigit(ch))
        }
        assertTrue(
            "the label set must exercise the digit path",
            d.LABELS.any { label -> label.any { d.isDigit(it) } }
        )
    }

    // ---------------------------------------------------------------------- production rendered ink

    @Test
    fun `label ink height matches the Wi-Fi ink height at the production box`() {
        val wifiInk = inkBox(render(baseWifiVisual()))
        assertTrue("Wi-Fi drew no ink", wifiInk.height > 0f)

        // The drawable insets the production 80x50 slot at the 4x render scale; derive the inner box
        // from the same call instead of hard-coding it, then compare in 1x pixels.
        val (innerW4x, innerH4x) = IosGeometry.Relative.sizeFor(
            IosIconKind.WIFI, slotWidth * ss, slotHeight * ss
        )
        val innerW = innerW4x / ss.toFloat()
        val innerH = innerH4x / ss.toFloat()
        val aaBleed = 0.5f
        for (label in IosGeometry.DataLabel.LABELS) {
            val visual = labelVisual(label)
            val ink = inkBox(render(visual))
            val artboard = IosGlyphRenderer.artboardWidth(IosIconKind.WIFI, visual)
            val scaleByWidth = innerW4x / artboard
            val scaleByHeight = innerH4x / IosGeometry.Wifi.ARTBOARD_H
            val limitedBy = if (scaleByWidth < scaleByHeight) "width" else "height"
            val ratio = ink.height / wifiInk.height

            if (label == "5G+") {
                // The widest label is width-limited in a narrow slot; legibility is the bar.
                assertTrue(
                    "5G+ ink/Wi-Fi ratio $ratio (limited by $limitedBy) must be >= 0.80",
                    ratio >= 0.8f
                )
                println("5G+ label ink/Wi-Fi ink = $ratio (limited by $limitedBy)")
            } else {
                assertEquals(
                    "$label ink height vs Wi-Fi (limited by $limitedBy, ratio $ratio)",
                    wifiInk.height,
                    ink.height,
                    wifiInk.height * 0.03f
                )
            }
            assertTrue(
                "$label ink width ${ink.width} bleeds past the ${innerW}px inner box " +
                    "(allowed AA bleed $aaBleed)",
                ink.width <= innerW + aaBleed
            )
            assertTrue(
                "$label ink height ${ink.height} bleeds past the ${innerH}px inner box " +
                    "(allowed AA bleed $aaBleed)",
                ink.height <= innerH + aaBleed
            )
        }
    }

    // ------------------------------------------------------------------------------------ helpers

    private fun baseWifiVisual(): IosVisual =
        IosVisual(part = IosPart.WIFI, tint = Color.BLACK, wifiLevel = 3f)

    private fun labelVisual(label: String): IosVisual = IosVisual(
        part = IosPart.WIFI,
        tint = Color.BLACK,
        wifiLevel = 3f,
        wifiSlot = IosWifiSlot.DataType(label)
    )

    private fun render(
        visual: IosVisual,
        background: Int? = null,
        relativeSize: Boolean = true
    ): Bitmap {
        val bw = slotWidth * ss
        val bh = slotHeight * ss
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (background != null) canvas.drawColor(background)
        val drawable = IosDrawables.create(
            IosIconKind.WIFI, visual, bw, bh, relativeSize = relativeSize
        )
        drawable.setBounds(0, 0, bw, bh)
        drawable.draw(canvas)
        return bitmap
    }

    private fun inkCount(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.alpha(it) > 8 }
    }

    private data class InkBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /** The tight opaque bbox in raw bitmap pixels: `left,top,right,bottom` with exclusive edges. */
    private fun rawInkBox(bitmap: Bitmap): String {
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
        return "$minX,$minY,${maxX + 1},${maxY + 1}"
    }

    /** SHA-256 of the alpha channel, row-major, one byte per pixel. */
    private fun alphaSha256(bitmap: Bitmap): String {
        val md = MessageDigest.getInstance("SHA-256")
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixel in pixels) {
            md.update((Color.alpha(pixel) and 0xFF).toByte())
        }
        return md.digest().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    /** The tight opaque bbox in 1x pixels; the bitmap is rendered at [ss]x. */
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
        return InkBox(
            minX / ss.toFloat(),
            minY / ss.toFloat(),
            (maxX + 1) / ss.toFloat(),
            (maxY + 1) / ss.toFloat()
        )
    }
}
