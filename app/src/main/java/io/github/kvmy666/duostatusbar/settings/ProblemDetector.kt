package io.github.kvmy666.duostatusbar.settings

import io.github.kvmy666.duostatusbar.RootLogs
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
    /** True when Duo itself is switched on (portrait or landscape); iOS alone is not Duo. */
    val duoEnabled: Boolean = false,
    val iosActive: Boolean = false,
    /**
     * True when the installed APK is newer than the module System UI runs. The stored status/dump/log
     * then describe the previous build and must not be read as this run's attach/hook/iOS evidence.
     */
    val updatePending: Boolean = false,
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

    private val ATTACH_EXHAUSTED = Regex("""(?i)attach failed after \d+ attempts""")
    private val OWNER_DUO = Regex("""(?i)owner=duo\b""")
    private val SAFE_MODE_ON = Regex("""(?i)safeMode\s*=\s*(entered|yes|true)\b""")

    fun detect(inputs: ProblemInputs): List<DetectedProblem> {
        val found = ArrayList<DetectedProblem>()
        // While an update is pending, the stored status/dump/log are the previous build's evidence.
        // Reading them would report the old build's attach/hook/iOS failures as this run's problems.
        val records = if (inputs.updatePending) {
            ""
        } else {
            buildString {
                appendLine(inputs.status)
                appendLine(inputs.dump)
                append(inputs.moduleLog)
            }
        }

        moduleProblems(inputs)?.let(found::add)
        crashProblems(inputs)?.let(found::add)
        // DuoHook writes this marker as the current status only after every attach retry was spent. A
        // stale attached=false line in the dump/history is a normal stage change, not this failure.
        if (!inputs.updatePending && !inputs.iosActive &&
            OWNER_DUO.containsMatchIn(inputs.status) &&
            ATTACH_EXHAUSTED.containsMatchIn(inputs.status)
        ) {
            found += DetectedProblem("attach", LOCATION_ATTACH)
        }
        if (!inputs.updatePending &&
            (inputs.fallback.isNotBlank() ||
                // Only the current status is a current fact. The dump carries a history block, so a
                // recovered bind's old failure line must not re-trigger fallback on every pulse.
                containsAny(inputs.status, "rive bind failed", "rive bind: reason=", "rivebind="))
        ) {
            found += DetectedProblem("fallback", LOCATION_RIVE)
        }
        if (!inputs.updatePending) found += hookProblems(records)
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

    /**
     * One short, factual line for a `type|location` fingerprint, so a "why sent" section says what the
     * user-facing problem is. Pure and ASCII-only: it never reads a device and never loads a resource.
     */
    fun humanReason(fingerprint: String): String {
        val parts = fingerprint.split('|')
        val type = parts.getOrNull(0).orEmpty()
        val location = parts.getOrNull(1).orEmpty()
        return when {
            type == "attach" -> "Duo owns the bar but did not attach after its retries"
            type == "fallback" -> "Rive did not bind; the bar fell back to Canvas"
            type == "hook" && location == LOCATION_HOOK -> "A required System UI hook failed to install"
            type == "hook" && location == LOCATION_PANEL ->
                "A panel hook missed; the panel fell back to attach order"
            type == "hook" -> "A System UI hook failed to install"
            type == "module" && location == LOCATION_MODULE_LOAD -> "The module never loaded in System UI"
            type == "module" && location == LOCATION_REPORT ->
                "The module is enabled but its state is unreadable"
            type == "module" -> "The module reported a problem"
            type == "crash" && location == LOCATION_SAFE_MODE ->
                "LSPosed entered safe mode after repeated System UI crashes"
            type == "crash" && location == LOCATION_SYSTEMUI ->
                "System UI crashed above the LSPosed threshold"
            type == "crash" -> "System UI crashed"
            type == "ios_miss" -> "An iOS-mode piece missed its runtime owner"
            type == "collector" -> "The diagnostic collector could not read anything"
            type == "reporter" -> reporterReason(location)
            else -> "Reported problem: $type ($location)"
        }
    }

    private fun reporterReason(location: String): String {
        val stage = location.substringBefore(':').ifBlank { "report" }
        val error = location.substringAfter(':', "").ifBlank { "unknown error" }
        return "The report $stage step failed ($error)"
    }

    private fun moduleProblems(inputs: ProblemInputs): DetectedProblem? {
        if (!inputs.moduleEnabled) return null
        // An enabled module whose state the app could not read is a reporting failure, not "healthy".
        if (inputs.moduleState == null) {
            return DetectedProblem("module", LOCATION_REPORT, "state unreadable")
        }
        return when (inputs.moduleState) {
            // A pending update is a normal, loaded module: the app is just newer than System UI.
            ModuleState.NEEDS_RESTART -> null
            // Never loaded is only a Duo problem when Duo is wanted and LSPosed's own config proves the
            // module is enabled and scoped to System UI. Without that proof the report guides the user.
            ModuleState.NEVER_LOADED -> if (inputs.duoEnabled && lsposedProvesLoaded(inputs)) {
                DetectedProblem("module", LOCATION_MODULE_LOAD, "state=${inputs.moduleState}")
            } else {
                null
            }
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
        // Safe mode is the only crash trigger. A counter below LSPosed's threshold, a death count or an
        // old crash record is information for the report, never a reason to send an automatic report.
        val entered = SAFE_MODE_ON.containsMatchIn(safeText) ||
            containsAny(safeText, "enter safe mode", "too many times", "stop all modules")
        return if (entered) {
            DetectedProblem("crash", LOCATION_SAFE_MODE, "safeMode=entered")
        } else {
            null
        }
    }

    /** True only when LSPosed's config was actually queried and proves this module is enabled+scoped. */
    private fun lsposedProvesLoaded(inputs: ProblemInputs): Boolean {
        val text = section(
            inputs.capture?.sections.orEmpty(),
            "LSPosed config (enabled / scope)"
        )?.text ?: return false
        val parsed = RootLogs.parseModuleScope(text)
        return parsed.queried &&
            parsed.enabled == true &&
            parsed.scope?.contains("com.android.systemui", ignoreCase = true) == true
    }

    private fun hookProblems(records: String): List<DetectedProblem> {
        val found = ArrayList<DetectedProblem>()
        // Only a genuine install failure is a hook problem. A missing optional panel hook is recorded
        // in the report (panelFallback=attach-order); the panel simply falls back to attach order.
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
        // A pending update is itself readable evidence (the health state), so the empty status/dump
        // that the collector intentionally passes is not a collector failure.
        val readAnything = inputs.updatePending ||
            inputs.status.isNotBlank() ||
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
