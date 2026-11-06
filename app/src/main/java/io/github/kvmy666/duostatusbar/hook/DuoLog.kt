package io.github.kvmy666.duostatusbar.hook

import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.L

/**
 * Structured, single-line event logger for the hook/attach pipeline (T1a).
 *
 * Emits one stable line through [L.i] (no new sink) so the existing diag/report plumbing keeps working:
 *   ui=<v> surface=<v> stage=<v> class=<v> id=<v> hook=<v> result=<v> reason=<v> detail=<v> build=<v>
 *
 * Values are sanitised: single line, trimmed, control chars stripped/flattened, length-capped
 * (240 chars for `detail`, 160 for everything else) and obvious secrets redacted. This object never
 * accepts or logs TextView text - callers pass ids/bounds/reason codes only.
 */
internal object DuoLog {

    // Stable reason codes (current + future use).
    const val RESOLVE_NO_CANDIDATE = "RESOLVE_NO_CANDIDATE"
    const val HOOK_ABSENT = "HOOK_ABSENT"
    const val HOOK_INSTALL_FAILED = "HOOK_INSTALL_FAILED"
    const val ATTACH_ZERO_WIDTH = "ATTACH_ZERO_WIDTH"
    const val PANEL_HOOK_ABSENT = "PANEL_HOOK_ABSENT"
    const val PANEL_ALIAS_UNVERIFIED = "PANEL_ALIAS_UNVERIFIED"
    const val SURFACE_DISABLED = "SURFACE_DISABLED"
    const val RESTORE_FAILED = "RESTORE_FAILED"
    const val PROFILE_ASSET_MISSING = "PROFILE_ASSET_MISSING"

    private const val DETAIL_LIMIT = 240
    private const val FIELD_LIMIT = 160

    private val SECRET_KEYS = listOf(
        "ssid", "imei", "serial", "token", "password", "secret", "account", "email"
    )

    private val EMAIL_REGEX = Regex(
        "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"
    )

    fun event(
        ui: String,
        surface: String,
        stage: String,
        clazz: String = "-",
        id: String = "-",
        hook: String = "-",
        result: String,
        reason: String = "-",
        detail: String = "-",
        build: String = BuildConfig.VERSION_NAME
    ) {
        val line = buildString {
            append("ui=").append(sanitize(ui, FIELD_LIMIT))
            append(" surface=").append(sanitize(surface, FIELD_LIMIT))
            append(" stage=").append(sanitize(stage, FIELD_LIMIT))
            append(" class=").append(sanitize(clazz, FIELD_LIMIT))
            append(" id=").append(sanitize(id, FIELD_LIMIT))
            append(" hook=").append(sanitize(hook, FIELD_LIMIT))
            append(" result=").append(sanitize(result, FIELD_LIMIT))
            append(" reason=").append(sanitize(reason, FIELD_LIMIT))
            append(" detail=").append(sanitize(detail, DETAIL_LIMIT))
            append(" build=").append(sanitize(build, FIELD_LIMIT))
        }
        L.i(line)
    }

    internal fun sanitize(value: String, limit: Int): String {
        // Flatten to a single line and strip control chars.
        var s = value.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ')
        val sb = StringBuilder(s.length)
        for (c in s) {
            if (c.code < 0x20 || c.code == 0x7F) {
                // drop control chars other than the already-flattened newlines
                continue
            }
            sb.append(c)
        }
        s = sb.toString().trim()
        // Redact obvious secrets (case-insensitive).
        for (key in SECRET_KEYS) {
            val pattern = Regex(
                "(?i)(" + Regex.escape(key) + ")\\s*=\\s*(\"[^\"]*\"|'[^']*'|\\S+)"
            )
            s = pattern.replace(s) { m -> "${m.groupValues[1]}=[redacted]" }
        }
        // Redact email addresses.
        s = EMAIL_REGEX.replace(s, "[redacted]")
        if (s.length > limit) s = s.take(limit)
        if (s.isEmpty()) s = "-"
        return s
    }
}