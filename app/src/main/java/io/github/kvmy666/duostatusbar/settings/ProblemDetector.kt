package io.github.kvmy666.duostatusbar.settings

import io.github.kvmy666.duostatusbar.diagnostics.CaptureSection
import io.github.kvmy666.duostatusbar.diagnostics.RootCapture
import io.github.kvmy666.duostatusbar.diagnostics.SectionState

/**
 * One detected problem, with a stable type and location so its fingerprint does not drift between
 * builds. [detail] is for humans only and is never part of the fingerprint.
 */
internal data class DetectedProblem(
    val type: String,
    val location: String,
    val detail: String = ""
)

/** Everything the detector may read. The collector fills it from stores the module already wrote. */
internal data class ProblemInputs(
    val status: String = "",
    val dump: String = "",
    val fallback: String = "",
    val moduleLog: String = "",
    val moduleState: ModuleState? = null,
    val moduleEnabled: Boolean = false,
    val iosActive: Boolean = false,
    val capture: RootCapture? = null,
    val captureFailure: String? = null
)

/**
 * Detects automatic-report-worthy problems from the module's already-written status, dump, fallback and
 * the shared root capture. Pure: no Android call, no collection, no hot path.
 *
 * Every rule has a stable location code. A rule that cannot name a piece falls back to a general code,
 * so a fingerprint never depends on free-form text.
 */
internal object ProblemDetector {

    /** Stable location codes used in fingerprints. */
    const val LOCATION_HOOK = "hook"
    const val LOCATION_PANEL = "panel"
    const val LOCATION_BATTERY = "battery"
    const val LOCATION_RIVE = "rive"
    const val LOCATION_ATTACH = "attach"
    const val LOCATION_MODULE_LOAD = "module-load"
    const val LOCATION_REPORT = "report"
    const val LOCATION_CAPTURE = "capture"
    const val LOCATION_UPLOAD = "upload"
    const val LOCATION_ZIP = "zip"
    const val LOCATION_SAFE_MODE = "safe-mode"
    const val LOCATION_SYSTEMUI = "systemui"

    private val CRASH_SECTION_TITLES = setOf("crash records", "tombstones", "logcat crash buffer")
    private val PIECE = Regex("""piece=([A-Za-z0-9_-]+)""")
    private val RUNTIME_MISS = Regex("""([A-Za-z0-9_]+)=(\d+)""")
    private val SAFE_COUNTER = Regex("""(?i)counter=(\d+)""")
    private val DEATHS = Regex("""(?i)deaths=(\d+)""")

    fun detect(inputs: ProblemInputs): List<DetectedProblem> {
        val found = ArrayList<DetectedProblem>()
        val records = buildString {
            appendLine(inputs.status)
            appendLine(inputs.dump)
            append(inputs.moduleLog)
        }

        moduleProblems(inputs)?.let(found::add)
        crashProblems(inputs)?.let(found::add)
        if (containsAny(records, "attached=false", "not attached", "step=attach:failed")) {
            found += DetectedProblem("attach", LOCATION_ATTACH)
        }
        if (inputs.fallback.isNotBlank() ||
            containsAny(records, "rive bind failed", "rive bind: reason=", "rivebind=")
        ) {
            found += DetectedProblem("fallback", LOCATION_RIVE)
        }
        if (inputs.iosActive) found += iosMisses(records)
        found += hookProblems(records)
        collectorProblem(inputs)?.let(found::add)

        // One problem per stable type|location; the first detail is kept.
        return found.distinctBy { "${it.type}|${it.location}" }
    }

    /** A fingerprint is the problem identity on this build and device: type|location|version|model. */
    fun fingerprint(problem: DetectedProblem, version: String, model: String): String =
        "${problem.type}|${problem.location}|$version|$model"

    /** The fingerprint for a failure of the reporter itself; the error class keeps it stable. */
    fun reporterFailure(stage: String, errorClass: String): DetectedProblem {
        val safeStage = stage.trim().lowercase().ifBlank { "unknown" }
        val safeClass = errorClass.trim().replace(Regex("""[|\s]"""), "_").ifBlank { "unknown" }
        return DetectedProblem("reporter", "$safeStage:$safeClass")
    }

    private fun moduleProblems(inputs: ProblemInputs): DetectedProblem? {
        if (!inputs.moduleEnabled) return null
        // An enabled module whose state the app could not read is a reporting failure, not "healthy".
        if (inputs.moduleState == null) {
            return DetectedProblem("module", LOCATION_REPORT, "state unreadable")
        }
        return when (inputs.moduleState) {
            ModuleState.NEVER_LOADED -> DetectedProblem(
                "module",
                LOCATION_MODULE_LOAD,
                "state=${inputs.moduleState}"
            )
            // Loaded but the settings channel is rejected: a real failure, not a turned-off module.
            ModuleState.NOT_REPORTING -> DetectedProblem(
                "module",
                LOCATION_REPORT,
                "state=${inputs.moduleState}"
            )
            else -> null
        }
    }

    private fun crashProblems(inputs: ProblemInputs): DetectedProblem? {
        val sections = inputs.capture?.sections.orEmpty()
        val safe = section(sections, "safe mode")
        val safeText = safe?.text.orEmpty()
        val safeCounter = SAFE_COUNTER.find(safeText)?.groupValues?.get(1)?.toIntOrNull()
        if (safeText.contains("safeMode=entered") || (safeCounter ?: 0) > 0) {
            return DetectedProblem("crash", LOCATION_SAFE_MODE, "safeMode=entered counter=${safeCounter ?: "?"}")
        }
        val crashPresent = sections.any {
            it.title in CRASH_SECTION_TITLES && it.state == SectionState.PRESENT && it.text.isNotBlank()
        }
        val deaths = DEATHS.find(section(sections, "systemui deaths")?.text.orEmpty())
            ?.groupValues?.get(1)?.toIntOrNull()
        return if (crashPresent || (deaths ?: 0) > 0) {
            DetectedProblem("crash", LOCATION_SYSTEMUI, "deaths=${deaths ?: "records"}")
        } else {
            null
        }
    }

    private fun iosMisses(records: String): List<DetectedProblem> {
        val found = ArrayList<DetectedProblem>()
        records.lineSequence().forEach { line ->
            if (!line.contains("ios v2", ignoreCase = true)) return@forEach
            if (!line.contains("miss", ignoreCase = true) &&
                !line.contains("runtimemisses", ignoreCase = true)
            ) {
                return@forEach
            }
            if (line.contains("runtimemisses", ignoreCase = true)) {
                RUNTIME_MISS.findAll(line).forEach { match ->
                    val piece = match.groupValues[1]
                    if (piece.equals("family", ignoreCase = true)) return@forEach
                    if ((match.groupValues[2].toIntOrNull() ?: 0) > 0) {
                        found += DetectedProblem("ios_miss", piece.lowercase(), "runtimeMisses=$piece")
                    }
                }
            } else {
                val piece = PIECE.find(line)?.groupValues?.get(1)
                found += DetectedProblem(
                    "ios_miss",
                    piece?.lowercase() ?: LOCATION_HOOK,
                    line.trim().take(160)
                )
            }
        }
        return found
    }

    private fun hookProblems(records: String): List<DetectedProblem> {
        val found = ArrayList<DetectedProblem>()
        if (records.contains("panelfallback=", ignoreCase = true)) {
            found += DetectedProblem("hook", LOCATION_PANEL)
        }
        if (containsAny(records, "hook failed", "install failed")) {
            found += DetectedProblem("hook", LOCATION_HOOK)
        }
        return found
    }

    private fun collectorProblem(inputs: ProblemInputs): DetectedProblem? {
        val failure = inputs.captureFailure
        if (!failure.isNullOrBlank()) {
            return DetectedProblem("collector", LOCATION_CAPTURE, failure)
        }
        val sections = inputs.capture?.sections.orEmpty()
        val route = section(sections, "capture route")
        if (route?.state == SectionState.UNREADABLE) {
            return DetectedProblem("collector", LOCATION_CAPTURE, route.reason)
        }
        val readAnything = inputs.status.isNotBlank() ||
            inputs.dump.isNotBlank() ||
            sections.any { it.state == SectionState.PRESENT && it.text.isNotBlank() }
        if (!readAnything) {
            val reasons = sections
                .filter { it.state != SectionState.PRESENT }
                .map { "${it.title}: ${it.reason.ifBlank { it.state.name.lowercase() }}" }
                .take(4)
                .joinToString("; ")
            return DetectedProblem(
                "collector",
                LOCATION_CAPTURE,
                reasons.ifBlank { "no readable status, dump or capture section" }
            )
        }
        return null
    }

    private fun section(sections: List<CaptureSection>, title: String): CaptureSection? =
        sections.firstOrNull { it.title == title }

    private fun containsAny(text: String, vararg needles: String): Boolean =
        needles.any { text.contains(it, ignoreCase = true) }
}
