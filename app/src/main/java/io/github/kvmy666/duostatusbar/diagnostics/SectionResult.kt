package io.github.kvmy666.duostatusbar.diagnostics

/** How a capture section ended. A section is never silently dropped. */
enum class SectionState { PRESENT, EMPTY, UNREADABLE, SKIPPED }

/**
 * One section of a capture. [reason] is required whenever [state] is not [SectionState.PRESENT], so a
 * report can say *why* it has no data instead of looking like there was nothing to report.
 */
data class CaptureSection(
    val title: String,
    val state: SectionState,
    val reason: String = "",
    val text: String = ""
) {
    /** `=== <title> ===` plus `state=… reason=…`; the body follows when there is one. */
    fun render(): String = buildString {
        appendLine("=== $title ===")
        append("state=").append(state.name.lowercase())
        append(" reason=").append(reason.ifBlank { "-" })
        if (text.isNotBlank()) {
            appendLine()
            append(text)
        }
    }
}

/**
 * A whole capture: which route produced it, plus every section. [render] prints every section even when
 * it is empty, because a missing section is exactly the evidence a future report needs.
 */
data class RootCapture(val route: String, val sections: List<CaptureSection>) {
    fun render(): String = buildString {
        appendLine("=== capture route ===")
        appendLine(route)
        sections.forEach {
            appendLine()
            append(it.render())
            appendLine()
        }
    }.trimEnd() + "\n"
}
