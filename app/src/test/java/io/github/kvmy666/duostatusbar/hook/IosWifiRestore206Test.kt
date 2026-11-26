package io.github.kvmy666.duostatusbar.hook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.min

/**
 * R10A item 3 proof: the Wi-Fi glyph is restored verbatim to 2.0.6, and the renderer draws the same
 * pixels from the restored constants as a local 2.0.6-equivalent renderer.
 *
 * Part (a) pins every 2.0.6 constant and the exact 32-point [IosGeometry.Wifi.DOT_POINTS] polygon.
 * Part (b) renders the current glyph and a local copy of the 2.0.6 draw at 8x (and at the R9 leaf
 * size 57x43) and requires zero differing pixels. With `IOS_RENDER_OUT` set, the two renders and the
 * (black) diff are written for the report.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosWifiRestore206Test {

    private companion object {
        const val SS = 8
        const val LEAF_W = 57
        const val LEAF_H = 43

        // The exact 2.0.6 values (git show c41898d:.../IosGeometry.kt, object Wifi).
        const val ARTBOARD_W = 129.53f
        const val ARTBOARD_H = 96.77f
        const val ARTBOARD_MARGIN = 1f
        const val CENTRE_X = 64.809f
        const val CENTRE_Y = 93.522f
        const val OUTER_RADIUS = 83.843f
        const val OUTER_STROKE = 16.903f
        const val OUTER_TRIM_END = 0.232575f
        const val OUTER_TRIM_OFFSET = 0.884175f
        const val INNER_RADIUS = 50.053f
        const val INNER_STROKE = 17.794f
        const val INNER_TRIM_END = 0.220078f
        const val INNER_TRIM_OFFSET = 0.889917f

        val DOT_POINTS: FloatArray = floatArrayOf(
            80.91f, 82.43f,
            79.08f, 85.27f,
            76.87f, 87.42f,
            74.81f, 89.10f,
            73.28f, 90.89f,
            71.49f, 92.41f,
            69.60f, 93.96f,
            67.35f, 95.15f,
            64.82f, 95.77f,
            62.29f, 95.15f,
            60.04f, 93.96f,
            58.15f, 92.41f,
            56.36f, 90.89f,
            54.83f, 89.10f,
            52.77f, 87.42f,
            50.56f, 85.27f,
            48.73f, 82.43f,
            47.50f, 78.98f,
            48.56f, 75.69f,
            50.79f, 73.05f,
            53.94f, 71.54f,
            56.53f, 70.03f,
            59.44f, 69.45f,
            62.19f, 69.20f,
            64.82f, 69.09f,
            67.45f, 69.20f,
            70.20f, 69.45f,
            73.11f, 70.03f,
            75.70f, 71.54f,
            78.85f, 73.05f,
            81.08f, 75.69f,
            82.14f, 78.98f
        )
    }

    @Test
    fun `wifi constants and dot polygon are the exact 2_0_6 values`() {
        val w = IosGeometry.Wifi
        assertEquals(ARTBOARD_W, w.ARTBOARD_W, 0f)
        assertEquals(ARTBOARD_H, w.ARTBOARD_H, 0f)
        assertEquals(ARTBOARD_MARGIN, w.ARTBOARD_MARGIN, 0f)
        assertEquals(CENTRE_X, w.CENTRE_X, 0f)
        assertEquals(CENTRE_Y, w.CENTRE_Y, 0f)
        assertEquals(OUTER_RADIUS, w.OUTER_RADIUS, 0f)
        assertEquals(OUTER_STROKE, w.OUTER_STROKE, 0f)
        assertEquals(OUTER_TRIM_END, w.OUTER_TRIM_END, 0f)
        assertEquals(OUTER_TRIM_OFFSET, w.OUTER_TRIM_OFFSET, 0f)
        assertEquals(INNER_RADIUS, w.INNER_RADIUS, 0f)
        assertEquals(INNER_STROKE, w.INNER_STROKE, 0f)
        assertEquals(INNER_TRIM_END, w.INNER_TRIM_END, 0f)
        assertEquals(INNER_TRIM_OFFSET, w.INNER_TRIM_OFFSET, 0f)
        assertArrayEquals(DOT_POINTS, w.DOT_POINTS, 0f)
        assertEquals(32, w.DOT_POINTS.size / 2)

        // The public DOT_W/DOT_H mirror the restored polygon bbox (kept for the Rive parity test).
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in w.DOT_POINTS.indices step 2) {
            minX = minOf(minX, w.DOT_POINTS[i])
            maxX = maxOf(maxX, w.DOT_POINTS[i])
            minY = minOf(minY, w.DOT_POINTS[i + 1])
            maxY = maxOf(maxY, w.DOT_POINTS[i + 1])
        }
        assertEquals(47.50f, minX, 0f)
        assertEquals(82.14f, maxX, 0f)
        assertEquals(69.09f, minY, 0f)
        assertEquals(95.77f, maxY, 0f)
        assertEquals(maxX - minX, w.DOT_W, 0.001f)
        assertEquals(maxY - minY, w.DOT_H, 0.001f)
    }

    @Test
    fun `current wifi render equals the 2_0_6 reference render pixel for pixel`() {
        val cases = listOf(
            "ss8" to (ceil(ARTBOARD_W * SS).toInt() to ceil(ARTBOARD_H * SS).toInt()),
            "leaf57x43" to (LEAF_W to LEAF_H),
            "leaf57x43-ss8" to (LEAF_W * SS to LEAF_H * SS)
        )
        for ((name, size) in cases) {
            val (width, height) = size
            val current = renderCurrent(width, height)
            val reference = render206(width, height)
            val differing = differingPixels(current, reference)
            assertEquals("$name ($width x $height) differing pixels", 0, differing)
            writeEvidence(name, current, reference)
        }
    }

    // ------------------------------------------------------------------------------------ render

    private fun renderCurrent(width: Int, height: Int): Bitmap {
        val visual = IosVisual(tint = Color.BLACK, wifiLevel = 3f)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val drawable = IosDrawables.create(IosIconKind.WIFI, visual, width, height)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    /** A local copy of 2.0.6's drawWifi: the same CONTAIN fit, arcs and old 32-gon dot. */
    private fun render206(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = min(width / ARTBOARD_W, height / ARTBOARD_H)
        val dx = (width - ARTBOARD_W * scale) / 2f
        val dy = (height - ARTBOARD_H * scale) / 2f

        val saved = canvas.save()
        try {
            canvas.translate(dx, dy)
            canvas.scale(scale, scale)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = Color.BLACK
            }
            arc(canvas, stroke, OUTER_RADIUS, OUTER_STROKE, OUTER_TRIM_END, OUTER_TRIM_OFFSET)
            arc(canvas, stroke, INNER_RADIUS, INNER_STROKE, INNER_TRIM_END, INNER_TRIM_OFFSET)

            val dot = Path().apply {
                moveTo(DOT_POINTS[0], DOT_POINTS[1])
                var i = 2
                while (i < DOT_POINTS.size) {
                    lineTo(DOT_POINTS[i], DOT_POINTS[i + 1])
                    i += 2
                }
                close()
            }
            canvas.drawPath(
                dot,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.BLACK
                }
            )
        } finally {
            canvas.restoreToCount(saved)
        }
        return bitmap
    }

    private fun arc(
        canvas: Canvas,
        stroke: Paint,
        radius: Float,
        thickness: Float,
        trimEnd: Float,
        trimOffset: Float
    ) {
        val bounds = RectF(
            CENTRE_X - radius,
            CENTRE_Y - radius,
            CENTRE_X + radius,
            CENTRE_Y + radius
        )
        stroke.strokeWidth = thickness
        val start = -90f + trimOffset * 360f
        canvas.drawArc(bounds, start, trimEnd * 360f, false, stroke)
    }

    // ----------------------------------------------------------------------------------- compare

    private fun differingPixels(a: Bitmap, b: Bitmap): Int {
        require(a.width == b.width && a.height == b.height) { "size mismatch" }
        val pa = IntArray(a.width * a.height)
        val pb = IntArray(b.width * b.height)
        a.getPixels(pa, 0, a.width, 0, 0, a.width, a.height)
        b.getPixels(pb, 0, b.width, 0, 0, b.width, b.height)
        var differing = 0
        for (i in pa.indices) {
            if (pa[i] != pb[i]) differing++
        }
        assertTrue("renders must contain ink", pa.any { Color.alpha(it) > 8 })
        return differing
    }

    private fun writeEvidence(name: String, current: Bitmap, reference: Bitmap) {
        val dir = System.getenv("IOS_RENDER_OUT")?.takeIf { it.isNotBlank() } ?: return
        val out = File(dir).apply { mkdirs() }
        writePng(current, File(out, "wifi-head-$name.png"))
        writePng(reference, File(out, "wifi-206-$name.png"))
        val diff = Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888)
        val pa = IntArray(current.width * current.height)
        val pb = IntArray(reference.width * reference.height)
        current.getPixels(pa, 0, current.width, 0, 0, current.width, current.height)
        reference.getPixels(pb, 0, reference.width, 0, 0, reference.width, reference.height)
        val outPixels = IntArray(pa.size) { i -> if (pa[i] == pb[i]) Color.BLACK else Color.RED }
        diff.setPixels(outPixels, 0, current.width, 0, 0, current.width, current.height)
        writePng(diff, File(out, "wifi-206-vs-head-fixture.png"))
        diff.recycle()
    }

    private fun writePng(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
