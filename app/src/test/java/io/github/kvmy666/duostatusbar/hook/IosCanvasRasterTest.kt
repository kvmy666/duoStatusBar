package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.ceil
import kotlin.math.min

/**
 * Renders the real [IosCanvasView] into bitmaps at the reference scale.
 *
 * This is both a guard (the fallback must actually draw every glyph, C9/M13) and the evidence
 * generator for the overlays: when `IOS_RENDER_OUT` is set, each state is written as a 664x173 PNG
 * with the same composition as the owner's references.
 *
 * The two acceptance states use the measured placements:
 * * light: s = 88.93/100 = 0.8893; battery body left/top (409.9, 30.6), cell (60.3, 30.7),
 *   Wi-Fi (243.3, 30.6) in reference pixels;
 * * dark: s = 71.28/100 = 0.7128; battery (387.5, 35.5), cell (108.8, 32.5), Wi-Fi (256.0, 35.5).
 * The 1-unit artboard margin is subtracted from each origin.
 *
 * A dual state is ONE cell placement with `IosCellRole.BOTH` (the stacked block), matching the host's
 * single clustered view; there is no per-slot cell overlay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosCanvasRasterTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private data class Prog(
        val dark: Boolean,
        val simDual: Boolean,
        val batteryLevel: Int,
        val saver: Boolean,
        val plugged: Boolean,
        val status: Int
    )

    private data class Placement(
        val kind: IosIconKind,
        val visual: IosVisual,
        val x: Float,
        val y: Float
    )

    private val lightBg = 0xFFFFFFFF.toInt()
    private val darkBg = 0xFF100F19.toInt()

    private companion object {
        const val SS = 4
    }

    @Test
    fun `light single SIM reference state draws every glyph`() {
        val bitmap = render(lightSingle(), lightBg)
        assertInk(bitmap, dark = false)
        writeIfRequested(bitmap, "canvas-light-single-normal.png")
    }

    @Test
    fun `dark dual SIM reference state draws every glyph`() {
        val bitmap = render(darkDual(), darkBg)
        assertInk(bitmap, dark = true)
        writeIfRequested(bitmap, "canvas-dark-dual-normal.png")
    }

    @Test
    fun `all evidence states render without a blank region`() {
        for (prog in evidenceStates()) {
            val bitmap = render(prog, if (prog.dark) darkBg else lightBg)
            assertInk(bitmap, prog.dark)
            writeIfRequested(bitmap, name(prog))
        }
    }

    // ------------------------------------------------------------------------- state builders

    private fun tint(dark: Boolean): Int = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

    private fun battery(prog: Prog): IosVisual = IosMapping.battery(
        level = prog.batteryLevel,
        lowThreshold = 20,
        saver = prog.saver,
        plugged = prog.plugged,
        status = prog.status,
        tint = tint(prog.dark)
    )

    private fun cell(prog: Prog): IosVisual = IosVisual(
        part = IosPart.CELL,
        tint = tint(prog.dark),
        cellMode = if (prog.simDual) IosCellMode.DUAL else IosCellMode.SINGLE,
        cellRole = if (prog.simDual) IosCellRole.BOTH else IosCellRole.DATA,
        cellBars = if (prog.simDual) 2f else 4f,
        cellDots = if (prog.simDual) 3f else 0f
    )

    private fun wifi(prog: Prog): IosVisual =
        IosVisual(part = IosPart.WIFI, tint = tint(prog.dark), wifiLevel = 3f)

    private fun lightSingle() = Prog(
        dark = false, simDual = false, batteryLevel = 100,
        saver = false, plugged = false, status = 3
    )

    private fun darkDual() = Prog(
        dark = true, simDual = true, batteryLevel = 59,
        saver = false, plugged = false, status = 3
    )

    /** 2 tints x 2 SIM layouts x 6 battery states = 24 evidence images. */
    private fun evidenceStates(): List<Prog> {
        val out = ArrayList<Prog>(24)
        for (dark in listOf(false, true)) {
            for (dual in listOf(false, true)) {
                out.add(Prog(dark, dual, 100, false, false, 3))
                out.add(Prog(dark, dual, 15, false, false, 3))
                out.add(Prog(dark, dual, 60, true, false, 3))
                out.add(Prog(dark, dual, 60, false, true, 2))
                out.add(Prog(dark, dual, 60, true, true, 2))
                out.add(Prog(dark, dual, 90, false, true, 4))
            }
        }
        return out
    }

    private fun name(prog: Prog): String {
        val tone = if (prog.dark) "dark" else "light"
        val sim = if (prog.simDual) "dual" else "single"
        val state = when {
            prog.status == 4 -> "not-charging"
            prog.plugged && prog.saver -> "charging-saver"
            prog.plugged -> "charging"
            prog.saver -> "saver"
            prog.batteryLevel <= 20 -> "low"
            else -> "normal"
        }
        return "canvas-$tone-$sim-$state.png"
    }

    // ----------------------------------------------------------------------------- rendering

    private fun render(prog: Prog, background: Int): Bitmap {
        val dark = prog.dark
        val s = if (dark) 0.7128f else 0.8893f
        val cellX = (if (dark) 108.8f else 60.3f) - 1f * s
        val cellY = (if (dark) 32.5f else 30.7f) - 1f * s
        val placements = arrayListOf(
            Placement(IosIconKind.CELL, cell(prog), cellX, cellY),
            Placement(
                IosIconKind.WIFI,
                wifi(prog),
                (if (dark) 256.0f else 243.3f) - 1f * s,
                (if (dark) 35.5f else 30.6f) - 1f * s
            ),
            Placement(
                IosIconKind.BATTERY,
                battery(prog),
                (if (dark) 387.5f else 409.9f) - 1f * s,
                (if (dark) 35.5f else 30.6f) - 1f * s
            )
        )
        // One CELL view over both stock slots: the dual state draws the stacked BOTH block, so the
        // evidence needs exactly one cell placement (no per-slot extra).
        // Supersample the composition. The owner's screenshots are upscaled and therefore soft; a
        // 1x Canvas produces hard 50% edges that differ from the reference by one pixel on small
        // shapes. 4x + a filtered downscale is the same geometry with display-grade anti-aliasing.
        val big = Bitmap.createBitmap(664 * SS, 173 * SS, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(big)
        canvas.drawColor(background)
        for (p in placements) {
            val (aw, ah) = artboard(p.kind)
            val w = ceil(aw * s * SS).toInt()
            val h = ceil(ah * s * SS).toInt()
            val v = IosCanvasView(context, p.kind)
            v.start()
            v.render(p.visual)
            v.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            )
            v.layout(0, 0, w, h)
            // A View must be laid out at integer pixels, so its internal scale is not exactly s.
            // Correct it when drawing so the evidence image uses the reference scale, not ceil().
            val viewScale = min(w / aw, h / ah)
            val correction = (s * SS) / viewScale
            val contentDx = (w - aw * viewScale) / 2f
            val contentDy = (h - ah * viewScale) / 2f
            canvas.save()
            canvas.translate(p.x * SS, p.y * SS)
            canvas.scale(correction, correction)
            canvas.translate(-contentDx, -contentDy)
            v.draw(canvas)
            canvas.restore()
            v.teardown()
        }
        val bitmap = Bitmap.createScaledBitmap(big, 664, 173, true)
        big.recycle()
        return bitmap
    }

    private fun artboard(kind: IosIconKind): Pair<Float, Float> = when (kind) {
        IosIconKind.BATTERY -> IosGeometry.Battery.ARTBOARD_W to IosGeometry.Battery.ARTBOARD_H
        IosIconKind.WIFI -> IosGeometry.Wifi.ARTBOARD_W to IosGeometry.Wifi.ARTBOARD_H
        IosIconKind.CELL -> IosGeometry.Cell.ARTBOARD_W to IosGeometry.Cell.ARTBOARD_H
    }

    // ------------------------------------------------------------------------------- checks

    private fun assertInk(bitmap: Bitmap, dark: Boolean) {
        fun ink(x0: Int, y0: Int, x1: Int, y1: Int): Int {
            var count = 0
            for (y in y0 until y1) {
                for (x in x0 until x1) {
                    val c = bitmap.getPixel(x, y)
                    val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                    // Ink is any drawn colour, not just black/white: the battery states use red,
                    // yellow, green and grey. Light backgrounds are 255; dark backgrounds are ~16.
                    if (if (dark) lum > 40 else lum < 240) count++
                }
            }
            return count
        }
        assertTrue("cell region blank", ink(40, 20, 230, 130) > 800)
        assertTrue("wifi region blank", ink(235, 20, 370, 130) > 800)
        assertTrue("battery region blank", ink(380, 20, 610, 130) > 2500)
        assertTrue("whole strip blank", ink(0, 0, 664, 173) > 4500)
    }

    private fun writeIfRequested(bitmap: Bitmap, fileName: String) {
        val dir = System.getenv("IOS_RENDER_OUT")?.takeIf { it.isNotBlank() } ?: return
        val out = File(dir)
        out.mkdirs()
        File(out, fileName).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
