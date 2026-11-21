package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.BatteryManager
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
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
 * The "one renderer" proof: [IosCanvasView] and [IosGlyphDrawable] must draw the same pixels.
 *
 * The matrix covers the dynamic tints, single/dual-BOTH/dual-DATA/dual-OTHER cells and every battery
 * state. The reference-scale test repeats the composition used by [IosCanvasRasterTest] for the two
 * acceptance states and, when `IOS_RENDER_OUT` is set, writes `drawable-<state>.png` for the report.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IosDrawableParityTest {

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
        const val BATTERY_W = 160
        const val BATTERY_H = 90
        const val WIFI_W = 120
        const val WIFI_H = 90
        const val CELL_W = 120
        const val CELL_H = 90
    }

    @Test
    fun `drawable and canvas draw identical pixels across the matrix`() {
        var compared = 0
        for (tint in listOf(Color.BLACK, Color.WHITE)) {
            for ((name, visual) in batteryStates(tint)) {
                assertSamePixels(IosIconKind.BATTERY, visual, BATTERY_W, BATTERY_H, "battery-$name")
                compared++
            }
            for (level in 0..3) {
                assertSamePixels(IosIconKind.WIFI, wifiVisual(tint, level), WIFI_W, WIFI_H, "wifi-$level")
                compared++
            }
            for ((name, visual) in cellStates(tint)) {
                assertSamePixels(IosIconKind.CELL, visual, CELL_W, CELL_H, "cell-$name")
                compared++
            }
        }
        assertTrue("parity matrix was empty", compared > 0)
    }

    @Test
    fun `reference scale acceptance states match and write drawable evidence`() {
        val states = listOf(
            lightSingle() to "light-single-normal",
            darkDual() to "dark-dual-normal"
        )
        for ((prog, name) in states) {
            val canvas = compose(prog, useDrawable = false)
            val drawable = compose(prog, useDrawable = true)
            assertArrayEquals("$name reference-scale parity", pixels(canvas), pixels(drawable))
            writeIfRequested(drawable, "drawable-$name.png")
            canvas.recycle()
            drawable.recycle()
        }
    }

    // ------------------------------------------------------------------------- state builders

    private fun tint(dark: Boolean): Int = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

    private fun batteryStates(tint: Int): List<Pair<String, IosVisual>> = listOf(
        "normal" to IosMapping.battery(100, 20, false, false, BatteryManager.BATTERY_STATUS_DISCHARGING, tint),
        "low" to IosMapping.battery(15, 20, false, false, BatteryManager.BATTERY_STATUS_DISCHARGING, tint),
        "saver" to IosMapping.battery(60, 20, true, false, BatteryManager.BATTERY_STATUS_DISCHARGING, tint),
        "charging" to IosMapping.battery(60, 20, false, true, BatteryManager.BATTERY_STATUS_CHARGING, tint),
        "not-charging" to IosMapping.battery(60, 20, false, true, BatteryManager.BATTERY_STATUS_NOT_CHARGING, tint)
    )

    private fun wifiVisual(tint: Int, level: Int): IosVisual =
        IosVisual(part = IosPart.WIFI, tint = tint, wifiLevel = level.toFloat())

    private fun cellStates(tint: Int): List<Pair<String, IosVisual>> = listOf(
        "single" to IosVisual(
            part = IosPart.CELL,
            tint = tint,
            cellMode = IosCellMode.SINGLE,
            cellRole = IosCellRole.DATA,
            cellBars = 4f
        ),
        "dual-both" to dualCell(tint, IosCellRole.BOTH),
        "dual-data" to dualCell(tint, IosCellRole.DATA),
        "dual-other" to dualCell(tint, IosCellRole.OTHER)
    )

    private fun dualCell(tint: Int, role: IosCellRole): IosVisual = IosVisual(
        part = IosPart.CELL,
        tint = tint,
        cellMode = IosCellMode.DUAL,
        cellRole = role,
        cellBars = 2f,
        cellDots = 3f
    )

    private fun lightSingle() = Prog(
        dark = false, simDual = false, batteryLevel = 100,
        saver = false, plugged = false, status = BatteryManager.BATTERY_STATUS_DISCHARGING
    )

    private fun darkDual() = Prog(
        dark = true, simDual = true, batteryLevel = 59,
        saver = false, plugged = false, status = BatteryManager.BATTERY_STATUS_DISCHARGING
    )

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

    // ----------------------------------------------------------------------------- rendering

    private fun assertSamePixels(
        kind: IosIconKind,
        visual: IosVisual,
        width: Int,
        height: Int,
        label: String
    ) {
        assertArrayEquals(
            "$label drawable != canvas",
            canvasPixels(kind, visual, width, height),
            drawablePixels(kind, visual, width, height)
        )
    }

    private fun canvasPixels(kind: IosIconKind, visual: IosVisual, width: Int, height: Int): IntArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val view = IosCanvasView(context, kind)
        view.start()
        view.render(visual)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        view.draw(Canvas(bitmap))
        view.teardown()
        return pixels(bitmap).also { bitmap.recycle() }
    }

    private fun drawablePixels(kind: IosIconKind, visual: IosVisual, width: Int, height: Int): IntArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val drawable = IosDrawables.create(kind, visual, width, height)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return pixels(bitmap).also { bitmap.recycle() }
    }

    /** The reference-scale composition, identical to [IosCanvasRasterTest] except for the renderer. */
    private fun compose(prog: Prog, useDrawable: Boolean): Bitmap {
        val dark = prog.dark
        val s = if (dark) 0.7128f else 0.8893f
        val placements = arrayListOf(
            Placement(
                IosIconKind.CELL,
                cell(prog),
                (if (dark) 108.8f else 60.3f) - 1f * s,
                (if (dark) 32.5f else 30.7f) - 1f * s
            ),
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

        val big = Bitmap.createBitmap(664 * SS, 173 * SS, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(big)
        canvas.drawColor(if (dark) darkBg else lightBg)
        for (p in placements) {
            val aw = IosGlyphRenderer.artboardWidth(p.kind, p.visual)
            val ah = IosGlyphRenderer.artboardHeight(p.kind, p.visual)
            val w = ceil(aw * s * SS).toInt()
            val h = ceil(ah * s * SS).toInt()
            val renderScale = min(w / aw, h / ah)
            val correction = (s * SS) / renderScale
            val contentDx = (w - aw * renderScale) / 2f
            val contentDy = (h - ah * renderScale) / 2f

            canvas.save()
            canvas.translate(p.x * SS, p.y * SS)
            canvas.scale(correction, correction)
            canvas.translate(-contentDx, -contentDy)
            if (useDrawable) {
                val drawable = IosDrawables.create(p.kind, p.visual, w, h)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(canvas)
            } else {
                val view = IosCanvasView(context, p.kind)
                view.start()
                view.render(p.visual)
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, w, h)
                view.draw(canvas)
                view.teardown()
            }
            canvas.restore()
        }

        val bitmap = Bitmap.createScaledBitmap(big, 664, 173, true)
        big.recycle()
        return bitmap
    }

    private fun pixels(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
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
