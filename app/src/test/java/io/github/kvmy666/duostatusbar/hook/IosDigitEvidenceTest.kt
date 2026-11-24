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
 * Evidence generator for the R9 stroke digits: renders the REAL [IosGlyphRenderer] (through
 * [IosGlyphDrawable]) on the battery body and, when `IOS_DIGIT_EVIDENCE_OUT` is set, writes the
 * per-state PNGs plus `digits-shipped.json` so the inverse weight-normalised IoU can be recomputed
 * from the shipped contours.
 *
 * R6 item 6 makes `percentInside` a true knockout for NORMAL/SAVER; R11 makes LOW, CHARGING and
 * CHARGING_SAVER draw solid white digits (`.pm/out-r11/measurements.md` §3). The guard therefore
 * checks the body ink always, cleared holes for the knockout states and solid white digit ink for the
 * solid states. Every written PNG is rendered over a deterministic opaque multicolour background so
 * the holes are visible in the evidence (not hidden as transparent-on-transparent). It is a real
 * guard even without a directory.
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
    fun `real renderer draws every R9 digit and the reference strings`() {
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
        for (pct in listOf(69, 100, 18, 57, 24)) {
            cases.add(Triple(pct.toString(), battery(pct), pct))
        }

        val dir = System.getenv("IOS_DIGIT_EVIDENCE_OUT")?.takeIf { it.isNotBlank() }?.let {
            File(it).apply { mkdirs() }
        }
        for ((name, visual, pct) in cases) {
            val bitmap = render(visual)
            val ink = inkCount(bitmap)
            assertTrue("$name ($pct %) produced no body ink", ink > 500)
            val solid = visual.batteryState == IosBatteryState.LOW ||
                visual.batteryState == IosBatteryState.CHARGING ||
                visual.batteryState == IosBatteryState.CHARGING_SAVER
            if (solid) {
                assertTrue(
                    "$name ($pct %) produced no solid white digits",
                    solidWhiteInBody(bitmap) > 100
                )
            } else {
                assertTrue(
                    "$name ($pct %) produced no cleared digit holes",
                    clearedInsideBody(bitmap) > 100
                )
            }
            bitmap.recycle()
            if (dir != null) {
                val bitmap2 = render(visual, opaque = true)
                File(dir, "r3d-real-$name.png").outputStream().use {
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

    private fun render(visual: IosVisual, opaque: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        if (opaque) paintPhotoBackground(bitmap)
        val drawable = IosDrawables.create(IosIconKind.BATTERY, visual, W, H)
        drawable.setBounds(0, 0, W, H)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    /** A deterministic multi-colour "photo" so a knockout hole is visibly the background. */
    private fun paintPhotoBackground(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val r = (x * 7 + y * 3 + 40) and 0xFF
                val g = (x * 3 + y * 11 + 90) and 0xFF
                val b = (x * 13 + y * 5 + 150) and 0xFF
                pixels[y * bitmap.width + x] = Color.rgb(r, g, b)
            }
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    /** Fully cleared pixels inside the battery body at this 4x scale. */
    private fun clearedInsideBody(bitmap: Bitmap): Int {
        val b = IosGeometry.Battery
        val left = (b.BODY_LEFT * SCALE).toInt()
        val top = (b.BODY_TOP * SCALE).toInt()
        val right = ((b.BODY_LEFT + b.BODY_W) * SCALE).toInt() - 1
        val bottom = ((b.BODY_TOP + b.BODY_H) * SCALE).toInt() - 1
        var count = 0
        for (y in top..bottom) {
            for (x in left..right) {
                if (Color.alpha(bitmap.getPixel(x, y)) == 0) count++
            }
        }
        return count
    }

    /** Solid white digit ink inside the battery body (R11: LOW / CHARGING / CHARGING_SAVER). */
    private fun solidWhiteInBody(bitmap: Bitmap): Int {
        val b = IosGeometry.Battery
        val left = (b.BODY_LEFT * SCALE).toInt()
        val top = (b.BODY_TOP * SCALE).toInt()
        val right = ((b.BODY_LEFT + b.BODY_W) * SCALE).toInt() - 1
        val bottom = ((b.BODY_TOP + b.BODY_H) * SCALE).toInt() - 1
        var count = 0
        for (y in top..bottom) {
            for (x in left..right) {
                val c = bitmap.getPixel(x, y)
                if (Color.alpha(c) > 200 &&
                    Color.red(c) > 240 && Color.green(c) > 240 && Color.blue(c) > 240
                ) {
                    count++
                }
            }
        }
        return count
    }

    private fun inkCount(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.alpha(it) > 8 }
    }

    /** The R10 traced digit metrics, 2dp, so the evidence names the actual runtime source. */
    private fun shippedJson(): String {
        val out = StringBuilder()
        out.append("{\"cap\":").append(f(IosDigits.CAP_HEIGHT)).append(",\"digits\":[")
        for (ch in '0'..'9') {
            val glyph = IosDigits.glyph(ch)
            if (ch != '0') out.append(',')
            out.append("{\"d\":").append(ch).append(",\"width\":").append(f(glyph.width))
                .append(",\"advance\":").append(f(glyph.advance))
                .append(",\"advanceMeasured\":").append(glyph.advanceMeasured).append("}")
        }
        out.append("]}")
        return out.toString()
    }

    private fun f(value: Float): String = "%.2f".format(Locale.ROOT, value)
}
