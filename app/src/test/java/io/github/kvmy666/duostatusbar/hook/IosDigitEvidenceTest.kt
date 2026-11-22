package io.github.kvmy666.duostatusbar.hook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.BatteryManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * Evidence generator for the v4 digits: renders the REAL [IosGlyphRenderer] (through
 * [IosGlyphDrawable]) on the battery body and, when `IOS_DIGIT_EVIDENCE_OUT` is set, writes the
 * per-state PNGs plus `digits-shipped.json` so the IoU can be recomputed from the shipped contours.
 *
 * The test itself always checks that every state produced ink, so it is a real guard even when no
 * output directory is requested.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosDigitEvidenceTest {

    private companion object {
        const val SCALE = 4
        val W = (IosGeometry.Battery.ARTBOARD_W * SCALE).toInt()
        val H = (IosGeometry.Battery.ARTBOARD_H * SCALE).toInt()
    }

    @Test
    fun `real renderer draws every v4 digit and the reference strings`() {
        val cases = ArrayList<Triple<String, IosVisual, Int>>()
        for (d in 0..9) {
            cases.add(Triple("digit-$d", battery(d), d))
        }
        cases.add(
            Triple(
                "36-bolt",
                battery(
                    level = 36,
                    plugged = true,
                    status = BatteryManager.BATTERY_STATUS_CHARGING
                ),
                36
            )
        )
        cases.add(Triple("69", battery(69), 69))
        cases.add(Triple("100", battery(100), 100))

        val dir = System.getenv("IOS_DIGIT_EVIDENCE_OUT")?.takeIf { it.isNotBlank() }?.let {
            File(it).apply { mkdirs() }
        }
        for ((name, visual, pct) in cases) {
            val bitmap = render(visual)
            val ink = inkCount(bitmap)
            assertTrue("$name ($pct %) produced no ink", ink > 500)
            bitmap.recycle()
            if (dir != null) {
                val bitmap2 = render(visual)
                File(dir, "r3m-real-$name.png").outputStream().use {
                    bitmap2.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap2.recycle()
            }
        }
        if (dir != null) {
            File(dir, "digits-shipped.json").writeText(shippedJson())
        }
    }

    private fun battery(
        level: Int,
        plugged: Boolean = false,
        status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING
    ): IosVisual = IosMapping.battery(
        level = level,
        lowThreshold = 0,
        saver = false,
        plugged = plugged,
        status = status,
        tint = Color.BLACK,
        percentInside = true
    )

    private fun render(visual: IosVisual): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val drawable = IosDrawables.create(IosIconKind.BATTERY, visual, W, H)
        drawable.setBounds(0, 0, W, H)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun inkCount(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.alpha(it) > 8 }
    }

    /** The shipped contours, 2dp, so the private IoU script scores the runtime data, not the JSON. */
    private fun shippedJson(): String {
        val out = StringBuilder()
        out.append("{\"cap\":").append(f(IosDigits.CAP_HEIGHT)).append(",\"digits\":[")
        for (ch in '0'..'9') {
            val glyph = IosDigits.glyph(ch)
            if (ch != '0') out.append(',')
            out.append("{\"d\":").append(ch).append(",\"width\":").append(f(glyph.width))
                .append(",\"advance\":").append(f(glyph.advance)).append(",\"contours\":[")
            for ((ci, contour) in glyph.contours.withIndex()) {
                if (ci > 0) out.append(',')
                out.append('[')
                var i = 0
                while (i < contour.size) {
                    if (i > 0) out.append(',')
                    out.append('[').append(f(contour[i])).append(',').append(f(contour[i + 1]))
                        .append(']')
                    i += 2
                }
                out.append(']')
            }
            out.append("],\"roles\":[")
            for (ci in glyph.contours.indices) {
                if (ci > 0) out.append(',')
                out.append(if (ci == 0) "\"outer\"" else "\"hole\"")
            }
            out.append("]}")
        }
        out.append("]}")
        return out.toString()
    }

    private fun f(value: Float): String = "%.2f".format(Locale.ROOT, value)
}
