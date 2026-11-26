package io.github.kvmy666.duostatusbar.diagnostics

/**
 * The shared privacy filter for every report line: manual diagnostic dumps, the module log, logcat,
 * root/Shizuku captures and view-tree text. Pure Kotlin, no Android dependency, so the rules are
 * unit-tested on the JVM and can be reused by the automatic reporter.
 *
 * Two properties matter:
 *  - **Labels survive.** `ssid=[redacted]` still tells the reader which field was removed, so the report
 *    stays useful for diagnosis.
 *  - **Normal diagnostics survive.** Class names, version strings, ISO timestamps, hex resource ids,
 *    pids, package ids and `uid:pid:tid` are not touched; the false-positive test pins that.
 *
 * [line] and [text] are idempotent: redacting an already-redacted report changes nothing.
 */
internal object Redact {

    // Notification content is removed first: it may contain anything, including spaces and digits.
    private val NOTIFICATION = Regex(
        """(?i)(\bandroid\.text\s*=\s*|\btickerText\s*=\s*|\bnotification text:\s*)(.*)"""
    )

    // A view-tree value is user-visible text (a notification, a name, a message) and is removed whole.
    private val VIEW_TEXT = Regex("""(?i)\b(text|content-desc)=(['"])(.*?)\2""")

    // Labelled identifiers. Longest spellings first so `carrierName` is not read as `carrier`.
    private val KEY_VALUE = Regex(
        """(?i)\b(ssid|wifi_ssid|carrierName|mCarrierName|carrier|operator|accountName|account|user|serialno|serial|imei|android_id|bssid)(\s*[:=]\s*)(".*?"|'.*?'|\S+)"""
    )

    // getprop prints `[ro.serialno]: [value]`, so the label and both brackets must be kept.
    private val RO_SERIALNO = Regex("""(?i)(\bro\.serialno\b\s*\]?\s*[:=]?\s*)(\[?)([^\]\s,;]+)""")

    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val MAC = Regex("""(?i)\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\b""")
    private val IMEI = Regex("""(?<!\w)\d{15}(?!\w)""")
    private val ANDROID_ID = Regex("""(?i)(?<!\w)[0-9a-f]{16}(?!\w)""")

    private val PHONE_INTL = Regex("""(?<![\w+])\+\d[\d\s().\-]{6,}\d(?!\w)""")
    private val PHONE_PARENS = Regex("""(?<![\w.])\(\d{2,4}\)\s*\d{3,4}[\s\-]?\d{3,4}(?![\w.])""")
    private val PHONE_LEADING_ZERO = Regex("""(?<![\w.])0\d{1,4}[\s\-]\d{3,4}[\s\-]?\d{3,4}(?![\w.])""")
    private val PHONE_US = Regex("""(?<![\w.])\d{3}[\s\-]\d{3}[\s\-]\d{4}(?![\w.])""")

    /** Redacts one line. */
    fun line(s: String): String {
        var out = s
        out = NOTIFICATION.replace(out) { m -> m.groupValues[1] + "[redacted]" }
        out = VIEW_TEXT.replace(out) { m ->
            "${m.groupValues[1]}=${m.groupValues[2]}[redacted]${m.groupValues[2]}"
        }
        out = KEY_VALUE.replace(out) { m ->
            m.groupValues[1] + m.groupValues[2] + "[redacted]"
        }
        out = RO_SERIALNO.replace(out) { m ->
            m.groupValues[1] + m.groupValues[2] + "[redacted]"
        }
        out = EMAIL.replace(out, "[redacted]")
        out = MAC.replace(out, "[redacted]")
        out = IMEI.replace(out, "[redacted]")
        out = ANDROID_ID.replace(out, "[redacted]")
        out = PHONE_INTL.replace(out, "[redacted]")
        out = PHONE_PARENS.replace(out, "[redacted]")
        out = PHONE_LEADING_ZERO.replace(out, "[redacted]")
        out = PHONE_US.replace(out, "[redacted]")
        return out
    }

    /** Redacts every line, preserving the line structure exactly (including a trailing newline). */
    fun text(s: String): String = s.split('\n').joinToString("\n") { line(it) }
}
