package io.github.kvmy666.duostatusbar.hook

import android.content.Context
import android.view.View
import io.github.kvmy666.duostatusbar.hook.rom.RomResolver

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
        out.add("  counters " + countersField())
        out.add("  settings " + settingsField(settings))
        appendEvidence(out, context, home)
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

    private fun countersField(): String {
        // The iOS Rive breaker is gone; the token stays 0 for dump compatibility.
        val guards = 0
        return "failures=${IosV2Log.failures} retries=${IosV2Log.retries} " +
            "firstFailure=${IosV2Log.first} orderPasses=${IosIconOrder.passes} " +
            "orderMoves=${IosIconOrder.moves} extrasHidden=${IosExtras.hiddenCount()} " +
            "engineCalls=${IosResourceEngine.callsForDiagnostics} " +
            "engineHits=${IosResourceEngine.hitsForDiagnostics} " +
            "stackedSwaps=${IosStackedSim.swaps} guardAttempts=$guards"
    }

    private fun settingsField(settings: ModuleSettings?): String {
        val s = settings ?: DuoSettingsClient.lastRead() ?: ModuleSettings.DEFAULT
        return "batteryScale=${s.iosBatteryScale} batteryOffset=${s.iosBatteryOffset} " +
            "wifiScale=${s.iosWifiScale} wifiOffset=${s.iosWifiOffset} " +
            "cellScale=${s.iosCellScale} cellOffset=${s.iosCellOffset} " +
            "hideExtras=${s.hideExtras} simDisplay=${s.simDisplay.replace(Regex("\\s+"), "_")} " +
            "stacked=${IosStackedSim.stackedActive} percentInside=${s.iosPercentInside}"
    }

    /**
     * B2: one evidence tail per dump. It answers "did V2 see the bar, which pieces resolved, and which
     * loader path was used" without changing any drawing. Bounded: at most four coverage lines, the
     * miss lines they imply, and one line each for battery/stacked/extras/delivery.
     */
    private fun appendEvidence(out: ArrayList<String>, context: Context?, home: View?) {
        out.add("ios v2 timing: " + IosResourceEngine.timingForDiagnostics)

        val family = familyId(context)
        val coverage = IosResourceEngine.coverageForDiagnostics
        val misses = IosResourceEngine.runtimeMissesForDiagnostics()
        out.addAll(IosResourceEngine.coverageReport(coverage, family, misses))
        if (coverage.isNotEmpty()) {
            out.add(runtimeMissLine(family, misses))
        }

        if (family.isNotEmpty() && IosV2Profile.batteryFamily(family) &&
            IosBatteryV2.firstFailureForDiagnostics != null
        ) {
            out.add(batteryMissLine(family))
        }
        if (IosV2Profile.stackedFamily(family)) {
            val stacked = IosStackedSim.missingBindersForDiagnostics
            if (stacked.isNotEmpty()) out.add(stackedMissLine(family, stacked))
        }
        if (IosV2Profile.extrasFamily(family)) {
            val extras = IosExtras.missingBindersForDiagnostics
            if (extras.isNotEmpty()) out.add(extrasMissLine(family, extras))
        }

        // Total hook calls, including the ones that arrived before the map was published: those are
        // still proof the bar used ResourcesImpl. A true zero states the path was never observed.
        val hookCalls = try {
            IosResourceEngine.callsForDiagnostics + IosResourceEngine.callsBeforeMapForDiagnostics
        } catch (_: Throwable) {
            0L
        }
        out.add(deliveryLine(hookCalls, Diag.iconInventory(home)))
    }

    /** The real family id: the activated engine's family, else the same resolver the dump uses. */
    private fun familyId(context: Context?): String {
        val active = IosResourceEngine.activeFamilyForDiagnostics
        if (active.isNotEmpty()) return active
        if (context == null) return ""
        return try {
            // R17D: the shared resolver merges the install-time Oplus marker, so the iOS report cannot
            // contradict the runtime family the hooks were installed for.
            RomResolver.resolve(context).id
        } catch (_: Throwable) {
            ""
        }
    }
}

// ----------------------------------------------------------------------- B2 evidence (pure)
// These helpers carry no Android state: the engine calls them at map build (coverage lines) and this
// report calls them per dump (miss/delivery lines), so the exact text is unit-tested without a device.

/** One engine piece's build-time coverage: how many specs resolved and the first unresolved names. */
internal data class IosPieceCoverage(
    val piece: String,
    val resolved: Int,
    val total: Int,
    val tried: List<String>
)

/** Piece ids in the same order as [computePieceCoverage]; runtime counters use this order. */
internal val IOS_PIECES: List<String> = listOf("wifi", "cell", "label", "hidden")

/** The measured battery hook targets, tried verbatim (OxygenOS view/binder + AOSP view). */
internal const val BATTERY_TRIED = "StatBatteryMeterView,BatteryViewBinder,BatteryMeterView"

private fun pieceOf(kind: IosResourceKind): String = when (kind) {
    IosResourceKind.WIFI -> "wifi"
    IosResourceKind.CELL -> "cell"
    IosResourceKind.LABEL -> "label"
    IosResourceKind.HIDDEN -> "hidden"
}

private fun specName(spec: IosResourceSpec): String = when {
    spec.name.isNotBlank() -> spec.name
    spec.rawId != 0 -> "raw_0x" + spec.rawId.toString(16).padStart(8, '0')
    else -> "raw"
}

private fun triedText(names: List<String>): String =
    names.take(8).joinToString(",").ifBlank { "none" }

/**
 * Per-piece coverage for [specs]: every spec counts in its piece's total, [resolvedSpecs] counts as
 * resolved, and the first eight unresolved names are kept in spec order. A piece with no specs is
 * omitted (a family without hide-extras must not look like a hidden-piece miss).
 */
internal fun computePieceCoverage(
    specs: List<IosResourceSpec>,
    resolvedSpecs: Set<IosResourceSpec>
): List<IosPieceCoverage> {
    val out = ArrayList<IosPieceCoverage>(4)
    for (kind in IosResourceKind.entries) {
        val ofKind = specs.filter { it.kind == kind }
        if (ofKind.isEmpty()) continue
        val unresolved = ofKind.filter { it !in resolvedSpecs }
        out.add(
            IosPieceCoverage(
                piece = pieceOf(kind),
                resolved = ofKind.size - unresolved.size,
                total = ofKind.size,
                tried = unresolved.map(::specName).take(8)
            )
        )
    }
    return out
}

internal fun coverageLine(family: String, coverage: IosPieceCoverage): String =
    "ios v2 coverage family=$family piece=${coverage.piece} " +
        "resolved=${coverage.resolved}/${coverage.total} tried=${triedText(coverage.tried)}"

internal fun missLine(family: String, piece: String, tried: List<String>): String =
    "ios v2 miss family=$family piece=$piece tried=${triedText(tried)}"

internal fun runtimeMissLine(family: String, misses: LongArray): String = buildString {
    append("ios v2 runtimeMisses: family=").append(family.ifBlank { "none" })
    for (i in IOS_PIECES.indices) {
        append(' ').append(IOS_PIECES[i]).append('=')
        append(if (i < misses.size) misses[i] else 0L)
    }
}

/**
 * The loader-path evidence. `ResourcesImpl.loadDrawable` is the only path this engine observes; a
 * zero call count states that the bar's icons never came through it, without guessing whether the ROM
 * used `setImageResource`, `getShadowDrawable`, an `IconViewBinder` or Compose.
 */
internal fun deliveryLine(calls: Long, viewIcons: String): String {
    val inventory = viewIcons.ifBlank { "none" }
    val base = "ios v2 delivery: resourcesImpl calls=$calls; viewIcons=$inventory"
    return if (calls == 0L) {
        "$base (bar not observed through ResourcesImpl.loadDrawable; " +
            "setImageResource/shadow/Compose path is not observed)"
    } else {
        base
    }
}

/**
 * The battery hook classes that [family]'s probe actually tries. Samsung's `batteryProbePlan` is
 * AOSP-only ([IosBatteryV2]), so a Samsung miss line must not name the OxygenOS classes.
 *
 * Measured 2.1.1 Fold evidence was stale and reported the OxygenOS probe as Samsung's `firstFailure`;
 * 2.1.3 probes only the AOSP owner, so the report says exactly that.
 */
internal fun batteryTried(family: String): String =
    if (family == "samsung") "BatteryMeterView" else BATTERY_TRIED

internal fun batteryMissLine(family: String): String =
    missLine(family, "battery", batteryTried(family).split(','))

internal fun stackedMissLine(family: String, tried: String): String =
    missLine(family, "stacked", tried.split(',').map { it.trim() }.filter { it.isNotEmpty() })

internal fun extrasMissLine(family: String, tried: String): String =
    missLine(family, "extras", tried.split(',').map { it.trim() }.filter { it.isNotEmpty() })
