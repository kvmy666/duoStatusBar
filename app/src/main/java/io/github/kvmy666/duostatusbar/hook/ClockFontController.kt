package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.TextView
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.rom.RomAdapter
import java.io.File

/**
 * Draws the status-bar clock in the phone's own system font, matching the element's digits (FR-16/28).
 *
 * The module never hides or moves the clock - this only swaps its `Typeface`, remembers the original so
 * switching off restores it exactly, and does nothing when the setting is off or the clock cannot be
 * found. The same OEM font the Rive digits bundle (`SysSans`) is preferred, falling back to the platform
 * `sans-serif` so the feature still works on a ROM that spells its font differently.
 */
internal class ClockFontController(private val context: Context, private val rom: RomAdapter) {

    private var clockView: TextView? = null
    private var clockOriginalTypeface: Typeface? = null

    /**
     * Applies the setting: the system font when [enabled], otherwise the remembered original. Safe to
     * call repeatedly (every layout pass); it only logs when the clock view itself changes.
     */
    fun apply(root: View?, enabled: Boolean) {
        if (!enabled) {
            restore()
            return
        }
        try {
            val view = root ?: return
            val id = context.resources.getIdentifier(rom.clockId, "id", rom.systemUiPackage)
            if (id == 0) return
            val clock = view.findViewById<View>(id) as? TextView ?: return
            if (clockView !== clock) {
                clockView = clock
                clockOriginalTypeface = clock.typeface
                L.i("clock font -> system (${clock.javaClass.simpleName} id=${rom.clockId})")
            }
            clock.typeface = systemTypeface()
        } catch (t: Throwable) {
            L.w("clock font: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Puts the clock's original typeface back. */
    fun restore() {
        val clock = clockView ?: return
        try {
            clock.typeface = clockOriginalTypeface
            L.i("clock font restored")
        } catch (t: Throwable) {
            L.w("clock restore: ${t.message}")
        }
        clockView = null
        clockOriginalTypeface = null
    }

    private fun systemTypeface(): Typeface = try {
        val file = File("/system/fonts/SysSans-En-Regular.ttf")
        if (file.exists()) Typeface.createFromFile(file)
        else Typeface.create("sans-serif", Typeface.NORMAL)
    } catch (t: Throwable) {
        Typeface.create("sans-serif", Typeface.NORMAL)
    }
}
