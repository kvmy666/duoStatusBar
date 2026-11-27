package io.github.kvmy666.duostatusbar.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * The consent question must not look like two neutral choices: the recommended answer is a primary
 * button (the app's wine), the refusal stays a neutral text button, and neither works before the
 * five-second countdown ends. The colour check reads the rendered pixels, not the source.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConsentDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val wine = Color(0xFF7B1E3A)

    private fun openDialog(onAnswer: (Boolean) -> Unit) {
        // Stop the clock before the first composition so the countdown still reads 5 at the first assert.
        compose.mainClock.autoAdvance = false
        compose.setContent { DuoTheme { ConsentDialog(onAnswer = onAnswer) } }
        compose.waitForIdle()
    }

    private fun passCountdown() {
        compose.mainClock.advanceTimeBy(5_500)
        compose.waitForIdle()
    }

    /**
     * Advances the paused clock by exactly one second and pumps the frame that composes the second's
     * delay resumption; the clock stays paused, so without the frame the label would not update.
     */
    private fun advanceOneSecond() {
        compose.mainClock.advanceTimeBy(1_000, ignoreFrameDuration = true)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    /**
     * Robolectric cannot service the window-capture redraw that [captureToImage] waits for, so the
     * fallback draws the dialog's real Compose host view into a bitmap and crops the node's bounds.
     * The pixels are the same composition the phone renders; only the copy mechanism differs.
     */
    private fun captureNode(tag: String): ImageBitmap {
        return try {
            compose.onNodeWithTag(tag).captureToImage()
        } catch (timeout: ComposeTimeoutException) {
            renderNodeFromDialog(tag)
        } catch (unsupported: UnsupportedOperationException) {
            renderNodeFromDialog(tag)
        }
    }

    private fun renderNodeFromDialog(tag: String): ImageBitmap {
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
        val dialog = ShadowDialog.getLatestDialog()
            ?: error("no dialog is showing for tag $tag")
        val host = findComposeHost(dialog.window!!.decorView)
            ?: error("the dialog has no Compose host view")
        if (host.width == 0 || host.height == 0) {
            val width = (360 * host.resources.displayMetrics.density).roundToInt()
            val height = (640 * host.resources.displayMetrics.density).roundToInt()
            host.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            host.layout(0, 0, host.measuredWidth, host.measuredHeight)
        }
        val full = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        host.draw(Canvas(full))

        val bounds = node.boundsInRoot
        val left = kotlin.math.floor(bounds.left.toDouble()).toInt().coerceIn(0, full.width - 1)
        val top = kotlin.math.floor(bounds.top.toDouble()).toInt().coerceIn(0, full.height - 1)
        val right = kotlin.math.ceil(bounds.right.toDouble()).toInt().coerceIn(left + 1, full.width)
        val bottom = kotlin.math.ceil(bounds.bottom.toDouble()).toInt().coerceIn(top + 1, full.height)
        return Bitmap.createBitmap(full, left, top, right - left, bottom - top).asImageBitmap()
    }

    private fun findComposeHost(view: View): View? = when (view) {
        is AbstractComposeView -> view
        is ViewGroup -> (0 until view.childCount)
            .asSequence()
            .mapNotNull { findComposeHost(view.getChildAt(it)) }
            .firstOrNull()
        else -> null
    }

    private fun Color.channel(component: Float): Int = (component * 255f).roundToInt()

    private fun Color.isNear(other: Color, tolerance: Int = 8): Boolean =
        abs(channel(red) - other.channel(other.red)) <= tolerance &&
            abs(channel(green) - other.channel(other.green)) <= tolerance &&
            abs(channel(blue) - other.channel(other.blue)) <= tolerance

    private fun Color.isNearWhite(): Boolean =
        channel(red) >= 240 && channel(green) >= 240 && channel(blue) >= 240

    private fun Color.asHex(): String =
        "#%02X%02X%02X".format(channel(red), channel(green), channel(blue))

    private fun ImageBitmap.pixels(): List<Color> {
        val map = toPixelMap()
        return buildList(map.width * map.height) {
            for (y in 0 until map.height) for (x in 0 until map.width) add(map[x, y])
        }
    }

    private data class PixelStats(
        val dominant: Color,
        val dominantCount: Int,
        val opaqueCount: Int,
        val total: Int
    ) {
        val dominantShareOfAll: Double get() = dominantCount.toDouble() / total
    }

    /** Most frequent fully opaque pixel, ignoring white (and near-white) label glyphs. */
    private fun ImageBitmap.stats(): PixelStats {
        val all = pixels()
        val counts = HashMap<Int, Int>()
        var best = 0
        var bestCount = 0
        var opaque = 0
        all.forEach { color ->
            if (color.alpha >= 0.99f) opaque++
            if (color.alpha < 0.99f || color.isNearWhite()) return@forEach
            val packed = (color.channel(color.red) shl 16) or
                (color.channel(color.green) shl 8) or
                color.channel(color.blue)
            val count = (counts[packed] ?: 0) + 1
            counts[packed] = count
            if (count > bestCount) {
                bestCount = count
                best = packed
            }
        }
        assertTrue("the captured region contains no opaque coloured pixels", bestCount > 0)
        return PixelStats(Color(0xFF000000L or best.toLong()), bestCount, opaque, all.size)
    }

    @Test
    fun `the no button counts down beside its label and opens at five seconds`() {
        openDialog {}

        // At zero seconds the No button itself says how long it stays locked.
        compose.onNodeWithTag("consent_no").assertTextEquals("No (5)")
        compose.onNodeWithTag("consent_yes").assertIsNotEnabled()
        compose.onNodeWithTag("consent_no").assertIsNotEnabled()

        listOf(4, 3, 2, 1).forEach { remaining ->
            advanceOneSecond()
            compose.onNodeWithTag("consent_no").assertTextEquals("No ($remaining)")
            compose.onNodeWithTag("consent_yes").assertIsNotEnabled()
            compose.onNodeWithTag("consent_no").assertIsNotEnabled()
        }

        // The fifth second ends the countdown: the label drops the number and both answers unlock.
        advanceOneSecond()
        compose.onNodeWithTag("consent_no").assertTextEquals("No")
        compose.onNodeWithTag("consent_yes").assertIsEnabled()
        compose.onNodeWithTag("consent_no").assertIsEnabled()
    }

    @Test
    fun `both buttons stay disabled until the five second countdown ends`() {
        openDialog {}

        compose.onNodeWithTag("consent_yes").assertIsNotEnabled()
        compose.onNodeWithTag("consent_no").assertIsNotEnabled()

        passCountdown()

        compose.onNodeWithTag("consent_yes").assertIsEnabled()
        compose.onNodeWithTag("consent_no").assertIsEnabled()
    }

    @Test
    fun `the yes button is the app primary colour`() {
        openDialog {}
        passCountdown()

        val yes = compose.onNodeWithTag("consent_yes")
        yes.assertIsDisplayed()
        val stats = captureNode("consent_yes").stats()
        println("consent_yes pixels: $stats")

        assertTrue(
            "yes button fill ${stats.dominant.asHex()} is not within 8 per channel of wine " +
                "${wine.asHex()} ($stats)",
            stats.dominant.isNear(wine)
        )
        assertTrue(
            "the primary colour only covers ${"%.1f".format(stats.dominantShareOfAll * 100)}% of the " +
                "button, so it is a label colour, not a primary fill ($stats)",
            stats.dominantShareOfAll >= 0.30
        )
    }

    @Test
    fun `the no button is neutral and contains no primary fill`() {
        openDialog {}
        passCountdown()

        val no = compose.onNodeWithTag("consent_no")
        no.assertIsDisplayed()
        val image = captureNode("consent_no")
        val stats = image.stats()
        val offending = image.pixels().filter { it.alpha >= 0.99f && it.isNear(wine) }
        println("consent_no pixels: $stats winePixels=${offending.size}")

        assertTrue(
            "no button contains ${offending.size} pixel(s) within 8 per channel of wine " +
                "${wine.asHex()} (dominant ${stats.dominant.asHex()}, $stats)",
            offending.isEmpty()
        )
    }

    @Test
    fun `yes answers true once unlocked`() {
        var answered: Boolean? = null
        openDialog { answered = it }
        passCountdown()

        compose.onNodeWithTag("consent_yes").performClick()

        assertEquals(true, answered)
    }

    @Test
    fun `no answers false once unlocked`() {
        var answered: Boolean? = null
        openDialog { answered = it }
        passCountdown()

        compose.onNodeWithTag("consent_no").performClick()

        assertEquals(false, answered)
    }
}
