package io.github.kvmy666.duostatusbar.ui

import io.github.kvmy666.duostatusbar.diagnostics.Redact
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * T3a: builds a self-contained bug-report ZIP. Pure JVM, no Android/Context dependency, so it can be
 * unit-tested directly. UI wiring lives elsewhere (not part of this task).
 */
internal data class DuoReportInputs(
    val generatedAt: String,
    val appVersion: String,
    val buildType: String,
    val device: Map<String, String>,
    val adapter: Map<String, String>,
    val diagnostics: String,
    val moduleLog: String,
    val logcat: String,
    val diagnosticsDump: String? = null,
    val screenshotPng: ByteArray? = null,
    /** Optional section -> `state reason=…` map, so an automatic report carries the completeness. */
    val completeness: Map<String, String> = emptyMap()
)

/**
 * Labels a diagnostic dump written by a different build than the one producing this report.
 *
 * A stored dump outlives the process that wrote it: one report carried `version: 1.4.2` while the app
 * was 2.0.2, and it read as if it described the current run. The dump stays intact (it is still
 * evidence) but is prefixed so nobody mistakes it for this run. A matching, unversioned or blank dump
 * is returned unchanged.
 */
internal fun labelStaleDump(dump: String, appVersion: String): String {
    if (dump.isBlank()) return dump
    val version = dumpVersion(dump) ?: return dump
    if (version == appVersion) return dump
    return "[stale diagnostic dump from $version; app is $appVersion; do not read it as this run]\n$dump"
}

/**
 * The same label, naming when the dump was written. A report can pass the fresh timestamp it read
 * ([io.github.kvmy666.duostatusbar.settings.DuoPrefs.dumpAt]) or 0 to fall back to the module log's own
 * last epoch ([dumpWrittenAt]). [now] rejects a nonsensical future stamp. It says both facts a reader
 * needs to judge the dump: which build wrote it, and how old it is.
 */
internal fun labelStaleDump(
    dump: String,
    appVersion: String,
    dumpAt: Long,
    now: Long = System.currentTimeMillis()
): String {
    if (dump.isBlank()) return dump
    val version = dumpVersion(dump) ?: return dump
    if (version == appVersion) return dump
    val writtenAt = when {
        dumpAt > 0L && (now <= 0L || dumpAt <= now) -> dumpAt
        else -> dumpWrittenAt(dump)
    }
    val written = writtenAt?.let {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(it))
    }
    val detail = if (written != null) "from $version written $written" else "from $version"
    return "[stale diagnostic dump $detail; app is $appVersion; " +
        "per-piece facts not derived from it]\n$dump"
}

/**
 * The first token after a leading `version:` line of a diagnostic dump, or null when it has none.
 * This is the build the *running* module wrote, which is what a pending update names as the old build.
 */
internal fun dumpVersion(dump: String): String? = dump.lineSequence()
    .mapNotNull { VERSION_LINE.find(it)?.groupValues?.get(1) }
    .firstOrNull()

/**
 * When the module wrote [dump], as the last leading 13-digit epoch in its embedded module-log section.
 * The app cannot read the module's ring buffer or a file mtime from System UI, so the log lines' own
 * timestamps are the best available proof. A dump with no such section has no readable time (null);
 * `DuoPrefs.dumpAt` is the explicit stamp for builds that write it.
 */
internal fun dumpWrittenAt(dump: String): Long? {
    if (dump.isBlank()) return null
    val index = dump.lastIndexOf(MODULE_LOG_MARKER)
    if (index < 0) return null
    return dump.substring(index + MODULE_LOG_MARKER.length)
        .lineSequence()
        .map { it.trim() }
        .mapNotNull { EPOCH_LINE.find(it)?.groupValues?.get(1)?.toLongOrNull() }
        .lastOrNull()
}

/** The first token after a leading `version:` line of a diagnostic dump. */
private val VERSION_LINE = Regex("""^\s*version:\s*(\S+)""")

/** A module-log line starts with its wall-clock epoch in milliseconds; 13 digits until the year 2286. */
private val EPOCH_LINE = Regex("""^(\d{13})(?:\s|$)""")

internal object DuoReport {

    /**
     * Writes a report ZIP to [destination]. Creates parent directories. Refuses to overwrite an
     * existing file (throws [IllegalArgumentException]) so a previous report is never silently lost.
     * All streams are closed via `use`.
     */
    fun writeZip(destination: File, inputs: DuoReportInputs) {
        if (destination.exists()) {
            throw IllegalArgumentException("destination already exists: ${destination.absolutePath}")
        }
        destination.parentFile?.mkdirs()

        val entryNames = mutableListOf("diagnostics.txt", "module-log.txt", "logcat.txt")
        if (inputs.diagnosticsDump != null) entryNames.add("diagnostics-dump.txt")
        if (inputs.screenshotPng != null) entryNames.add("screenshot.png")

        FileOutputStream(destination).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // Manifest first, so a reader sees the schema before the payloads.
                val manifest = JSONObject()
                manifest.put("schema", "duo-report/1")
                manifest.put("generatedAt", manifestValue(inputs.generatedAt))
                manifest.put("appVersion", manifestValue(inputs.appVersion))
                manifest.put("buildType", manifestValue(inputs.buildType))
                val device = JSONObject()
                for ((k, v) in inputs.device) device.put(k, manifestValue(v))
                manifest.put("device", device)
                val adapter = JSONObject()
                for ((k, v) in inputs.adapter) adapter.put(k, manifestValue(v))
                manifest.put("adapter", adapter)
                val entries = JSONArray()
                for (name in entryNames) entries.put(name)
                manifest.put("entries", entries)
                val completeness = JSONObject()
                for ((name, state) in inputs.completeness) completeness.put(name, manifestValue(state))
                manifest.put("completeness", completeness)

                val manifestText = try {
                    // The manifest is written outside writeTextEntry, so it must pass the same privacy
                    // choke point. Values are redacted first so a labelled value at the end of a JSON
                    // string cannot make the whole-text pass swallow the closing quote. A rule that
                    // still consumes a line (notification text) must not corrupt the schema, so the
                    // already-redacted values are kept when the text pass would not parse.
                    val raw = manifest.toString(2)
                    val redacted = Redact.text(raw)
                    if (isJson(redacted)) redacted else raw
                } catch (t: Throwable) {
                    throw IllegalStateException("manifest serialize failed: ${t.message}", t)
                }
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(manifestText.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                writeTextEntry(zos, "diagnostics.txt", inputs.diagnostics)
                writeTextEntry(zos, "module-log.txt", inputs.moduleLog)
                writeTextEntry(zos, "logcat.txt", inputs.logcat)
                if (inputs.diagnosticsDump != null) {
                    writeTextEntry(zos, "diagnostics-dump.txt", inputs.diagnosticsDump)
                }

                if (inputs.screenshotPng != null) {
                    val e = ZipEntry("screenshot.png")
                    zos.putNextEntry(e)
                    zos.write(inputs.screenshotPng)
                    zos.closeEntry()
                }
            }
        }
    }

    /**
     * Redacts one manifest value. A value Redact changed keeps a guard space, so the whole-manifest
     * pass cannot let a labelled value at the end of a JSON string swallow the closing quote.
     */
    private fun manifestValue(value: String): String {
        val redacted = Redact.text(value)
        return if (redacted == value) value else "$redacted "
    }

    /** Whether [text] is still a JSON object (the manifest's shape). */
    private fun isJson(text: String): Boolean = try {
        JSONObject(text)
        true
    } catch (_: Throwable) {
        false
    }

    private fun writeTextEntry(zos: ZipOutputStream, name: String, text: String) {
        zos.putNextEntry(ZipEntry(name))
        // The single privacy choke point for every ZIP text entry (diagnostics, module log, logcat,
        // dump). Redact is idempotent, so the builders' own pass is harmless.
        zos.write(Redact.text(text).toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    /**
     * Deletes the oldest files in [dir] whose name starts with [prefix], keeping the newest [keep].
     * No-op when [dir] is not a directory. Negative [keep] deletes everything.
     */
    fun prune(dir: File, prefix: String, keep: Int) {
        if (!dir.isDirectory) return
        val matching = dir.listFiles { f -> f.isFile && f.name.startsWith(prefix) }
            ?.sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })
            ?: return
        val deleteCount = (matching.size - keep).coerceAtLeast(0)
        for (i in 0 until deleteCount) {
            matching[i].delete()
        }
    }
}
