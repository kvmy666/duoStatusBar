package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.content.res.Configuration
import io.github.kvmy666.duostatusbar.L

/**
 * What colour the status bar's own icons are right now, so the Duo element can match them like a
 * chameleon (black on a light bar, white on a dark one).
 *
 * SystemUI tints its icons through `StatusBarIconView.onDarkChanged(...)` / `setIconColor(...)` as the
 * window behind the bar changes; [install] captures that tint and [fgColor] answers with black or white.
 * The capture is best-effort and ROM-guarded: when it never fires the system day/night setting is the
 * fallback, and a manual black/white override in the app wins over both.
 */
internal object BarTint {

    const val BLACK = DuoMapping.BLACK
    const val WHITE = DuoMapping.WHITE

    /** Normalised to black or white; 0 until something is captured. */
    @Volatile
    private var normalized: Int = 0

    @Volatile
    private var captured = false

    private val logOnce = LogOnce()

    /**
     * Records a tint SystemUI applied to its icons. Returns true when the black/white decision actually
     * changed, so a caller can redraw only then (the dark/light transition animates through many values).
     */
    fun update(color: Int): Boolean {
        if (color == 0) return false
        val next = if (isDark(color)) BLACK else WHITE
        val changed = !captured || next != normalized
        normalized = next
        captured = true
        if (changed) {
            logOnce.once("bar") {
                L.i("status bar icons are ${if (next == BLACK) "dark" else "light"} - matching them")
            }
        }
        return changed
    }

    /** Black or white to draw the element in. Captured bar colour first, system day/night as fallback. */
    fun fgColor(context: Context): Int {
        if (captured) return normalized
        return try {
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            if (night) WHITE else BLACK
        } catch (t: Throwable) {
            L.w("bar colour fallback: ${t.javaClass.simpleName}: ${t.message}")
            WHITE
        }
    }

    /** Perceived brightness, so a mid-grey tint still picks the better extreme. */
    private fun isDark(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000 < 128
    }
}
