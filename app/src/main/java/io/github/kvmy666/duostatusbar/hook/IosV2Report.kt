package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View

/**
 * REL210: the one additive iOS diagnostic block.
 *
 * It answers "why does this phone not draw the iOS icons?" in a single dump: the resolved px per
 * surface, the clock size/source, which hook classes were actually resolved, the audited failure
 * counters and the live settings. It is read-only and bounded (<=20 lines), emits nothing unless iOS
 * mode is on (so an off dump stays byte-identical), and leaves the existing `ios v2 …` lines and their
 * parsers untouched.
 */
internal object IosV2Report {

    private const val HEADER = "ios v2 diag:"

    /** True when the dump should carry the block: an explicit iOS snapshot wins over the cached read. */
    fun enabled(settings: ModuleSettings?): Boolean =
        settings?.iosMode ?: (DuoSettingsClient.lastRead()?.iosMode ?: false)

    /**
     * Builds the block. [home] is the status-bar root; [extras] are the other windows the module draws
     * into (the shade/keyguard window and the shade header). Never throws.
     */
    fun lines(
        context: Context?,
        settings: ModuleSettings?,
        home: View?,
        extras: List<Pair<String, View?>>
    ): List<String> = try {
        val roots = ArrayList<Pair<String, View?>>(extras.size + 1)
        roots.add("home" to home)
        for ((name, root) in extras) {
            if (root == null) continue
            roots.add(surfaceName(name, root) to root)
        }
        val out = ArrayList<String>(12)
        out.add(HEADER)
        for ((surface, slots) in IosSlotLayout.diagnosticSurfaces(roots)) {
            val body = if (slots.isEmpty()) "slots=none" else formatSlots(slots)
            out.add("  slots $surface: $body")
        }
        out.add("  clock " + clockField())
        out.add("  hooks engine=${IosResourceEngine.hookClassesForDiagnostics} " +
            "battery=${IosBatteryV2.hookClassesForDiagnostics}")
        out.add("  hooks2 stacked=${IosStackedSim.hookClassesForDiagnostics} " +
            "placement=${IosMobileTypePlacement.hookClassesForDiagnostics} " +
            "extras=${IosExtras.hookClassesForDiagnostics} " +
            "order=${IosIconOrder.hookClassesForDiagnostics}")
        out.add("  counters " + countersField(context))
        out.add("  settings " + settingsField(settings))
        out
    } catch (t: Throwable) {
        IosV2Log.failureOnce("report.block", t)
        emptyList()
    }

    /** The extra-root name, or "keyguard" when the root view class says it is the lock-screen bar. */
    private fun surfaceName(name: String, root: View): String {
        val className = try {
            root.javaClass.simpleName
        } catch (_: Throwable) {
            ""
        }
        if (name.contains("keyguard", ignoreCase = true) ||
            className.contains("Keyguard", ignoreCase = true)
        ) {
            return "keyguard"
        }
        return name.replace(Regex("\\s+"), "_").ifBlank { "root" }
    }

    private fun formatSlots(slots: List<IosSlotLayout.SlotDiag>): String =
        slots.take(6).joinToString(" ") { slot ->
            val label = if (slot.labelWidth > 0 && slot.labelHeight > 0) {
                " label=${slot.labelWidth}x${slot.labelHeight}"
            } else {
                ""
            }
            "${slot.kind}(outer=${slot.outerWidth}x${slot.outerHeight} " +
                "leaf=${slot.leafWidth}x${slot.leafHeight}$label)"
        }

    private fun clockField(): String {
        val id = IosSlotLayout.clockIdForDiagnostics
        val resolved = IosSlotLayout.clockResolvedForDiagnostics
        return "cap=${IosSlotLayout.clockCapForDiagnostics}px " +
            "source=${IosSlotLayout.clockSourceForDiagnostics} " +
            "id=${if (resolved) "resolved" else "missing"}" +
            if (resolved) "(0x${id.toString(16)})" else ""
    }

    private fun countersField(context: Context?): String {
        val guards = try {
            context?.let { IosGuard(it).attempts() } ?: 0
        } catch (_: Throwable) {
            0
        }
        return "failures=${IosV2Log.failures} retries=${IosV2Log.retries} " +
            "firstFailure=${IosV2Log.first} orderPasses=${IosIconOrder.passes} " +
            "orderMoves=${IosIconOrder.moves} extrasHidden=${IosExtras.hiddenCount()} " +
            "engineCalls=${IosResourceEngine.callsForDiagnostics} " +
            "engineHits=${IosResourceEngine.hitsForDiagnostics} " +
            "stackedSwaps=${IosStackedSim.swaps} guardAttempts=$guards"
    }

    private fun settingsField(settings: ModuleSettings?): String {
        val s = settings ?: ModuleSettings.DEFAULT
        return "batteryScale=${s.iosBatteryScale} batteryOffset=${s.iosBatteryOffset} " +
            "wifiScale=${s.iosWifiScale} wifiOffset=${s.iosWifiOffset} " +
            "cellScale=${s.iosCellScale} cellOffset=${s.iosCellOffset} " +
            "hideExtras=${s.hideExtras} simDisplay=${s.simDisplay.replace(Regex("\\s+"), "_")} " +
            "stacked=${IosStackedSim.stackedActive} percentInside=${s.iosPercentInside}"
    }
}
