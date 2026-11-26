package io.github.kvmy666.duostatusbar.hook.rom

import android.content.Context
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.hook.LogOnce

/**
 * Resolves a SystemUI resource id against the ROM adapter's package, so every call site goes through
 * one place and an unreadable resources object yields 0 instead of throwing.
 */
internal object RomResources {

    private val logOnce = LogOnce()

    /** The id for a resource [name] in [rom]'s SystemUI package, or 0 when it cannot be resolved. */
    fun id(context: Context, rom: RomAdapter, name: String): Int = try {
        context.resources.getIdentifier(name, "id", rom.systemUiPackage)
    } catch (t: Throwable) {
        logOnce.once("id:$name") {
            L.w("RomResources.id($name) failed: ${t.javaClass.simpleName}: ${t.message}")
        }
        0
    }
}
