package io.github.kvmy666.duostatusbar.diagnostics

import io.github.kvmy666.duostatusbar.settings.DuoSettings
import io.github.kvmy666.duostatusbar.settings.ModuleHealth
import io.github.kvmy666.duostatusbar.settings.ModuleMode
import io.github.kvmy666.duostatusbar.settings.ModuleState
import io.github.kvmy666.duostatusbar.settings.SettingsHistory
import io.github.kvmy666.duostatusbar.ui.dumpVersion
import io.github.kvmy666.duostatusbar.ui.dumpWrittenAt

/** How one report section ended. [MISSING] is for app-side inputs the report could not find. */
internal enum class ReportSectionState { PRESENT, EMPTY, UNREADABLE, SKIPPED, MISSING }

/** One line of the completeness header: `section=<name> state=<state> reason=<why>`. */
internal data class ReportSection(
    val name: String,
    val state: ReportSectionState,
    val reason: String
) {
    fun render(): String =
        "section=$name state=${state.name.lowercase()} reason=$reason"
}

/** A capture section as read from a [RootCapture] or parsed back out of its rendered text. */
internal data class CaptureInfo(
    val title: String,
    val state: SectionState,
    val reason: String,
    val text: String
)

/**
 * The one report schema shared by the manual and (later) automatic reports. Every section has a state
 * and a reason; nothing is silently absent. Capture sections keep the state/reason that
 * `RootLogs.collectSections()` produced; app-side inputs are judged here with an explicit reason.
 */
internal object ReportCompleteness {
    const val HEADER = "report completeness:"

    val REQUIRED: List<String> = listOf(
        "app environment",
        "module load",
        "module status",
        "history",
        "settings",
        "module log",
        "diagnostic dump",
        "capture route",
        "LSPosed/framework logs",
        "safe mode",
        "LSPosed enabled/scope",
        "crash records",
        "tombstones",
        "crash buffer",
        "breadcrumb",
        "systemui deaths/uptime",
        "per-piece status"
    )

    private const val MODULE_LOG_MARKER =
        "=== module log (from the module, no root needed) ==="

    /**
     * The start of the module log section. The **last** marker wins, matching the app-side
     * `moduleLogFromDump`: a dump can quote an older header before the real section.
     */
    internal fun moduleLogMarkerIndex(dump: String): Int = dump.lastIndexOf(MODULE_LOG_MARKER)

    fun build(
        status: String,
        history: List<String>,
        dump: String,
        moduleLoad: String,
        capture: RootCapture? = null,
        captureText: String = "",
        captureFailure: String? = null,
        settingsPresent: Boolean = true
    ): List<ReportSection> {
        val failure = captureFailure?.takeIf { it.isNotBlank() }
        val infos = captureInfos(capture, captureText).associateBy { it.title }

        fun captureSection(name: String, title: String, vararg fallbacks: String): ReportSection {
            if (failure != null) {
                return ReportSection(name, ReportSectionState.UNREADABLE, "capture failed: $failure")
            }
            val info = infos[title] ?: fallbacks.firstNotNullOfOrNull { infos[it] }
            if (info == null) {
                val why = if (capture == null && captureText.isBlank()) {
                    "no capture supplied to the report builder"
                } else {
                    "section '$title' absent from capture"
                }
                return ReportSection(name, ReportSectionState.MISSING, why)
            }
            return info.toReportSection(name)
        }

        fun routeSection(): ReportSection {
            if (failure != null) {
                return ReportSection(
                    "capture route",
                    ReportSectionState.UNREADABLE,
                    "capture failed: $failure"
                )
            }
            val info = infos["capture route"]
                ?: return ReportSection(
                    "capture route",
                    ReportSectionState.MISSING,
                    if (capture == null && captureText.isBlank()) {
                        "no capture supplied to the report builder"
                    } else {
                        "capture route absent from capture"
                    }
                )
            val fail = Regex("""captureFailure=(.+)""").find(info.text)?.groupValues?.get(1)?.trim()
            val skip = Regex("""captureSkipped=(.+)""").find(info.text)?.groupValues?.get(1)?.trim()
            return when {
                !fail.isNullOrBlank() -> ReportSection(
                    "capture route", ReportSectionState.UNREADABLE, fail
                )
                !skip.isNullOrBlank() -> ReportSection(
                    "capture route", ReportSectionState.SKIPPED, skip
                )
                info.state != SectionState.PRESENT -> info.toReportSection("capture route")
                else -> ReportSection(
                    "capture route",
                    ReportSectionState.PRESENT,
                    info.reason.ifBlank { "capture text present" }
                )
            }
        }

        fun lsposedSection(): ReportSection {
            if (failure != null) {
                return ReportSection(
                    "LSPosed/framework logs",
                    ReportSectionState.UNREADABLE,
                    "capture failed: $failure"
                )
            }
            val candidates = listOf(
                "LSPosed log files",
                "LSPosed newest modules log",
                "framework manager"
            ).mapNotNull { infos[it] }
            if (candidates.isEmpty()) {
                return ReportSection(
                    "LSPosed/framework logs",
                    ReportSectionState.MISSING,
                    if (capture == null && captureText.isBlank()) {
                        "no capture supplied to the report builder"
                    } else {
                        "no LSPosed framework section in the capture"
                    }
                )
            }
            val chosen = candidates.firstOrNull { it.state == SectionState.PRESENT }
                ?: candidates.first()
            return chosen.toReportSection("LSPosed/framework logs")
        }

        fun breadcrumbSection(): ReportSection {
            if (failure != null) {
                return ReportSection(
                    "breadcrumb", ReportSectionState.UNREADABLE, "capture failed: $failure"
                )
            }
            val root = infos["breadcrumb"]
            val app = infos["breadcrumb (app fallback)"]
            return when {
                root?.state == SectionState.PRESENT ->
                    root.toReportSection("breadcrumb")
                app?.state == SectionState.PRESENT -> ReportSection(
                    "breadcrumb",
                    ReportSectionState.PRESENT,
                    "app fallback readable" +
                        (root?.let { "; privileged ${it.state.name.lowercase()}: ${it.reason}" } ?: "")
                )
                root != null -> {
                    val reason = root.reason.ifBlank { "no breadcrumb" }
                    if (app != null && app.state == SectionState.EMPTY) {
                        ReportSection(
                            "breadcrumb",
                            SectionState.EMPTY.toReportState(),
                            "${reason}; app fallback ${app.reason.ifBlank { "empty" }}"
                        )
                    } else {
                        root.toReportSection("breadcrumb")
                    }
                }
                app != null -> app.toReportSection("breadcrumb")
                else -> ReportSection(
                    "breadcrumb",
                    ReportSectionState.MISSING,
                    if (capture == null && captureText.isBlank()) {
                        "no capture supplied to the report builder"
                    } else {
                        "breadcrumb section absent from capture"
                    }
                )
            }
        }

        val load = moduleLoad.trim()
        val loadSection = when {
            // A pending update is a readable load: the old build is running, the new one is installed.
            load.startsWith("update installed") -> ReportSection(
                "module load",
                ReportSectionState.PRESENT,
                "update pending; restart System UI to load this build"
            )
            load.startsWith("never") -> ReportSection(
                "module load",
                ReportSectionState.MISSING,
                "LSPosed has not injected the module into System UI"
            )
            load.startsWith("unknown") -> ReportSection(
                "module load",
                ReportSectionState.EMPTY,
                "module reported but no load stamp exists"
            )
            load.contains("not running now") -> ReportSection(
                "module load",
                ReportSectionState.PRESENT,
                "last load; module not running now"
            )
            load.isBlank() -> ReportSection(
                "module load", ReportSectionState.MISSING, "no load stamp or heartbeat"
            )
            else -> ReportSection(
                "module load", ReportSectionState.PRESENT, "load stamp or fresh heartbeat present"
            )
        }

        val dumpSection = if (dump.isBlank()) {
            ReportSection(
                "diagnostic dump",
                ReportSectionState.EMPTY,
                "dump absent (module never reported or old build)"
            )
        } else {
            ReportSection(
                "diagnostic dump", ReportSectionState.PRESENT, "stored module dump present"
            )
        }

        val moduleLogSection = run {
            val index = moduleLogMarkerIndex(dump)
            when {
                dump.isBlank() -> ReportSection(
                    "module log", ReportSectionState.MISSING,
                    "no diagnostic dump; module never reported"
                )
                index < 0 -> ReportSection(
                    "module log", ReportSectionState.MISSING,
                    "module log section absent from dump (old build or provider unreachable)"
                )
                dump.substring(index + MODULE_LOG_MARKER.length).trim().isEmpty() ->
                    ReportSection("module log", ReportSectionState.EMPTY, "module log section is empty")
                else -> ReportSection(
                    "module log", ReportSectionState.PRESENT, "embedded in the diagnostic dump"
                )
            }
        }

        val perPiece = if (status.isNotBlank() || dump.isNotBlank()) {
            ReportSection(
                "per-piece status", ReportSectionState.PRESENT, "parsed from status and dump"
            )
        } else {
            ReportSection(
                "per-piece status", ReportSectionState.EMPTY,
                "module never reported; see module load/mode"
            )
        }

        return listOf(
            ReportSection(
                "app environment",
                ReportSectionState.PRESENT,
                "app/device/ROM facts always readable"
            ),
            loadSection,
            if (status.isBlank()) {
                ReportSection(
                    "module status",
                    ReportSectionState.MISSING,
                    "module never reported; see module load/mode"
                )
            } else {
                ReportSection(
                    "module status", ReportSectionState.PRESENT, "reported by the module"
                )
            },
            if (history.isEmpty()) {
                ReportSection("history", ReportSectionState.EMPTY, "no status history yet")
            } else {
                ReportSection(
                    "history", ReportSectionState.PRESENT, "${history.size} stored report(s)"
                )
            },
            if (settingsPresent) {
                ReportSection(
                    "settings", ReportSectionState.PRESENT, "app settings for this orientation"
                )
            } else {
                ReportSection(
                    "settings", ReportSectionState.MISSING, "settings store could not be read"
                )
            },
            moduleLogSection,
            dumpSection,
            routeSection(),
            lsposedSection(),
            captureSection("safe mode", "safe mode"),
            captureSection(
                "LSPosed enabled/scope", "LSPosed config (enabled / scope)"
            ),
            captureSection("crash records", "crash records"),
            captureSection("tombstones", "tombstones"),
            captureSection("crash buffer", "logcat crash buffer"),
            breadcrumbSection(),
            captureSection("systemui deaths/uptime", "systemui deaths"),
            perPiece
        )
    }

    fun render(sections: List<ReportSection>): String = buildString {
        appendLine(HEADER)
        sections.forEach { appendLine(it.render()) }
    }.trimEnd()

    /** Parses the header back into `name -> "state reason=…"` (used by the ZIP manifest). */
    fun parseHeader(report: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val pattern = Regex("""^section=(.+?) state=(\w+) reason=(.*)$""")
        for (line in report.lineSequence()) {
            val match = pattern.find(line.trim()) ?: continue
            out[match.groupValues[1]] =
                "${match.groupValues[2]} reason=${match.groupValues[3]}"
        }
        return out
    }

    /** Reads every `=== title === / state=… reason=…` block from a rendered capture. */
    fun captureSections(text: String): List<CaptureInfo> {
        if (text.isBlank()) return emptyList()
        val out = ArrayList<CaptureInfo>()
        var title: String? = null
        var state: SectionState? = null
        var reason = ""
        val body = StringBuilder()

        fun flush() {
            val name = title ?: return
            out.add(
                CaptureInfo(
                    title = name,
                    state = state ?: SectionState.EMPTY,
                    reason = reason,
                    text = body.toString().trim('\n')
                )
            )
        }

        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("=== ") && trimmed.endsWith(" ===")) {
                flush()
                body.setLength(0)
                title = trimmed.removePrefix("=== ").removeSuffix(" ===")
                state = null
                reason = ""
                continue
            }
            if (title == null) continue
            if (state == null && trimmed.startsWith("state=")) {
                val stateName = trimmed.removePrefix("state=").substringBefore(' ').trim()
                state = runCatching {
                    SectionState.valueOf(stateName.uppercase())
                }.getOrDefault(SectionState.EMPTY)
                val parsed = Regex("""reason=(.*)$""")
                    .find(trimmed)?.groupValues?.get(1).orEmpty().trim()
                reason = if (parsed == "-") "" else parsed
                continue
            }
            body.appendLine(line)
        }
        flush()
        return out
    }
}

private fun captureInfos(capture: RootCapture?, text: String): List<CaptureInfo> =
    capture?.sections?.map {
        CaptureInfo(it.title, it.state, it.reason, it.text)
    } ?: ReportCompleteness.captureSections(text)

private fun CaptureInfo.toReportSection(name: String): ReportSection =
    when (state) {
        SectionState.PRESENT -> ReportSection(
            name, ReportSectionState.PRESENT, reason.ifBlank { "readable" }
        )
        SectionState.EMPTY -> ReportSection(
            name, ReportSectionState.EMPTY, reason.ifBlank { "no output" }
        )
        SectionState.UNREADABLE -> ReportSection(
            name, ReportSectionState.UNREADABLE, reason.ifBlank { "unreadable" }
        )
        SectionState.SKIPPED -> ReportSection(
            name, ReportSectionState.SKIPPED, reason.ifBlank { "skipped" }
        )
    }

private fun SectionState.toReportState(): ReportSectionState = when (this) {
    SectionState.PRESENT -> ReportSectionState.PRESENT
    SectionState.EMPTY -> ReportSectionState.EMPTY
    SectionState.UNREADABLE -> ReportSectionState.UNREADABLE
    SectionState.SKIPPED -> ReportSectionState.SKIPPED
}

/**
 * The per-piece evidence block. It only parses facts the module already wrote (status line and the
 * diagnostic dump); it never re-runs the Rive/iOS engines. Any absent fact prints `missing:<why>`.
 */
internal object ReportFacts {

    fun perPieceStatus(
        status: String,
        dump: String,
        settings: DuoSettings?,
        health: ModuleHealth?,
        lastChange: SettingsHistory.Record?,
        revision: Long,
        capture: RootCapture? = null,
        captureText: String = "",
        captureFailure: String? = null,
        formatTimestamp: (Long) -> String = { it.toString() },
        appVersion: String = "",
        dumpAt: Long = 0L
    ): String = buildString {
        appendLine("=== per-piece status ===")

        // A pending update makes every stored status/dump line evidence from the previous build. The
        // report states the one true fact and does not print per-piece `missing:` for code that has
        // not loaded yet (2.1.2 app, 2.1.1 module in the field report).
        if (health?.state == ModuleState.NEEDS_RESTART) {
            val old = health.runningVersion?.trim()?.takeIf { it.isNotEmpty() }
                ?: dumpVersion(dump)
                ?: "the previous build"
            val new = appVersion.ifBlank { "this build" }
            appendLine(
                "update pending: System UI still runs $old; app is $new; " +
                    "restart System UI or reboot to load $new"
            )
            appendLine("mode: ${health.state} (${health.mode.label})")
            return@buildString
        }

        // A loaded/current module whose stored dump is from the previous build (the owner field case:
        // app 2.1.2, dump 2.1.1). The live status is still this run, so it is printed; nothing is taken
        // from the old dump, and the mode comes from the live owner rather than a stale health state.
        val runningBuild = health?.runningVersion?.trim()?.takeIf { it.isNotEmpty() }
        val dumpBuild = dumpVersion(dump)?.trim()?.takeIf { it.isNotEmpty() }
        if (runningBuild != null && dumpBuild != null && dumpBuild != runningBuild) {
            appendLine(liveStatusBlock(status))
            // The module's own dump stamp survives a missing/truncated embedded log; prefer it.
            val writtenAt = dumpAt.takeIf { it > 0L } ?: dumpWrittenAt(dump)
            val written = writtenAt?.let { " written ${formatTimestamp(it)}" }
                ?: " written at an unknown time"
            appendLine(
                "dump: STALE diagnostic dump from $dumpBuild$written; per-piece facts unavailable"
            )
            val liveMode = liveMode(status, health)
            // A live status proves the module is running; NEVER_LOADED/NOT_REPORTING describe the
            // heartbeat channel, not this status line, and must not contradict it in the same report.
            val liveState = if (status.isNotBlank() &&
                (health?.state == null ||
                    health.state == ModuleState.NEVER_LOADED ||
                    health.state == ModuleState.NOT_REPORTING)
            ) {
                ModuleState.OK
            } else {
                health?.state ?: ModuleState.OK
            }
            appendLine("mode: $liveState (${liveMode.label})")
            return@buildString
        }

        val process = field(status, "process") ?: dumpField(dump, "process")
        val pid = field(status, "pid") ?: dumpField(dump, "pid")
        if (process == null && pid == null) {
            appendLine("process: missing:no module status or dump systemui line (module never reported)")
        } else {
            appendLine(
                "process: process=${process ?: "missing:not in status/dump"} " +
                    "pid=${pid ?: "missing:not in status/dump"}"
            )
        }

        val attached = field(status, "attached")
        val stage = field(status, "stage") ?: dumpField(dump, "stage")
        val renderer = field(status, "renderer") ?: dumpField(dump, "renderer")
        val owner = field(status, "owner")
        val root = dumpField(dump, "stageSource")
        val provider = dumpField(dump, "provider")
        appendLine(
            "duo attach: " +
                "attached=${attached ?: "missing:status absent"} " +
                "stage=${stage ?: "missing:status/dump absent"} " +
                "renderer=${renderer ?: "missing:status/dump absent"} " +
                "owner=${owner ?: "missing:status absent"} " +
                "root=${root ?: "missing:dump absent"} " +
                "provider=${provider ?: "missing:dump absent"}"
        )

        val bind = field(status, "riveBind")
            ?: dumpLine(dump, "rive bind:")?.removePrefix("rive bind:")?.trim()
        appendLine(
            "rive bind: " + when {
                bind.isNullOrBlank() ->
                    "missing:no Rive bind failure recorded (bound or not attempted)"
                bind.startsWith("reason=") -> bind
                else -> "reason=$bind"
            }
        )

        val timing = dumpLine(dump, "ios v2 timing:")
        if (timing == null) {
            appendLine(
                "ios engine: missing:no ios v2 timing line in dump " +
                    "(iOS inactive or build predates evidence)"
            )
        } else {
            val facts = fields(timing)
            appendLine(
                "ios engine: " +
                    "calls=${facts["calls"] ?: "missing"} " +
                    "hits=${facts["hits"] ?: "missing"} " +
                    "misses=${facts["misses"] ?: "missing"} " +
                    "callsBeforeMap=${facts["callsBeforeMap"] ?: "missing"} " +
                    "activatedAt=${facts["activatedAt"] ?: "missing"} " +
                    "mapReadyAt=${facts["mapReadyAt"] ?: "missing"} " +
                    "buildMs=${facts["buildMs"] ?: "missing"}"
            )
        }

        val coverage = dump.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("ios v2 coverage") }
            .joinToString("; ")
        appendLine(
            "ios coverage: " + coverage.ifBlank {
                "missing:no ios v2 coverage line in dump (iOS inactive or old build)"
            }
        )

        val delivery = dumpLine(dump, "ios v2 delivery:")
            ?.removePrefix("ios v2 delivery:")?.trim()
        appendLine(
            "ios delivery: " + delivery?.takeIf { it.isNotBlank() }
                .orEmpty().ifBlank { "missing:no ios v2 delivery line in dump" }
        )

        fun miss(piece: String, label: String) {
            val pattern = Regex("""\bpiece=${Regex.escape(piece)}\b""")
            val line = dump.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("ios v2 miss") && pattern.containsMatchIn(it) }
            appendLine(
                "$label: " + (line
                    ?: "missing:no $piece failure line in dump (piece ok or iOS inactive)")
            )
        }
        miss("battery", "battery")
        miss("stacked", "stacked")
        miss("extras", "extras")

        val hooks2 = dump.lineSequence().map { it.trim() }
            .firstOrNull { it.contains("hooks2 ") && it.contains("order=") }
        val counters = dump.lineSequence().map { it.trim() }
            .firstOrNull { it.contains("counters ") && it.contains("orderPasses=") }
        val order = hooks2?.let { fields(it)["order"] }
        val passes = counters?.let { fields(it)["orderPasses"] }
        val moves = counters?.let { fields(it)["orderMoves"] }
        if (order == null && passes == null && moves == null) {
            appendLine("order: missing:no order hooks/counters in dump")
        } else {
            appendLine(
                "order: hooks=${order ?: "missing"} passes=${passes ?: "missing"} " +
                    "moves=${moves ?: "missing"}"
            )
        }

        appendLine(
            "mode: " + if (health != null) {
                "${health.state} (${liveMode(status, health).label})"
            } else {
                "missing:module health unavailable"
            }
        )
        appendLine("design: " + (settings?.designStyle ?: "missing:settings unavailable"))
        appendLine(
            "last settings change: " + SettingsHistory.render(lastChange, revision, formatTimestamp)
        )

        val deaths = captureInfo(capture, captureText, "systemui deaths")
        when {
            captureFailure != null -> {
                appendLine("uptime: missing:capture failed: $captureFailure")
                appendLine("systemui deaths this boot: missing:capture failed: $captureFailure")
            }
            deaths == null -> {
                appendLine("uptime: missing:capture section systemui deaths absent")
                appendLine(
                    "systemui deaths this boot: missing:capture section systemui deaths absent"
                )
            }
            deaths.state != SectionState.PRESENT -> {
                val why = "${deaths.state.name.lowercase()}: " +
                    deaths.reason.ifBlank { "no reason" }
                appendLine("uptime: missing:$why")
                appendLine("systemui deaths this boot: missing:$why")
            }
            else -> {
                val uptime = deaths.text.lineSequence().map { it.trim() }
                    .firstOrNull { it.startsWith("uptime=") }
                    ?.removePrefix("uptime=")?.trim()
                val count = deaths.text.lineSequence().map { it.trim() }
                    .firstOrNull { it.startsWith("deaths=") }
                    ?.removePrefix("deaths=")?.trim()
                appendLine(
                    "uptime: " + uptime?.takeIf { it.isNotBlank() }
                        .orEmpty().ifBlank { "missing:no uptime line in capture" }
                )
                when {
                    count.isNullOrBlank() ->
                        appendLine("systemui deaths this boot: missing:no deaths line in capture")
                    count.startsWith("unreadable:") -> appendLine(
                        "systemui deaths this boot: " +
                            "missing:${count.removePrefix("unreadable:")}"
                    )
                    else -> appendLine("systemui deaths this boot: deaths=$count")
                }
            }
        }
    }
}

/**
 * The two live facts lines for a stale-dump report. Only the status is read, so no fact here comes from
 * the previous build's dump; a field the status does not carry says `unknown`, never `missing:`.
 */
private fun liveStatusBlock(status: String): String {
    val process = field(status, "process") ?: "unknown"
    val pid = field(status, "pid") ?: "unknown"
    val attached = field(status, "attached") ?: "unknown"
    val stage = field(status, "stage") ?: "unknown"
    val renderer = field(status, "renderer") ?: "unknown"
    val owner = field(status, "owner") ?: "unknown"
    return "process: process=$process pid=$pid\n" +
        "duo attach: attached=$attached stage=$stage renderer=$renderer owner=$owner"
}

/** The live owner from the status line, falling back to the configured mode when it is absent. */
private fun liveMode(status: String, health: ModuleHealth?): ModuleMode =
    when (field(status, "owner")) {
        "duo" -> ModuleMode.DUO
        "ios" -> ModuleMode.IOS
        "none" -> ModuleMode.OFF
        else -> health?.mode ?: ModuleMode.OFF
    }

private fun field(line: String, key: String): String? =
    Regex("""(?:^|[\s·])${Regex.escape(key)}=([^\s·]+)""")
        .find(line)?.groupValues?.get(1)

private fun dumpField(dump: String, key: String): String? =
    dump.lineSequence().map { it.trim() }
        .firstNotNullOfOrNull { field(it, key) }

private fun dumpLine(dump: String, prefix: String): String? =
    dump.lineSequence().map { it.trim() }.firstOrNull { it.startsWith(prefix) }

private fun fields(line: String): Map<String, String> =
    Regex("""(\w+)=([^\s;]+)""")
        .findAll(line)
        .associate { it.groupValues[1] to it.groupValues[2] }

private fun captureInfo(
    capture: RootCapture?,
    text: String,
    title: String
): CaptureInfo? =
    capture?.sections?.firstOrNull { it.title == title }?.let {
        CaptureInfo(it.title, it.state, it.reason, it.text)
    } ?: ReportCompleteness.captureSections(text).firstOrNull { it.title == title }
