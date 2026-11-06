package io.github.kvmy666.duostatusbar.ui

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
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
    val screenshotPng: ByteArray? = null
)

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
                manifest.put("generatedAt", inputs.generatedAt)
                manifest.put("appVersion", inputs.appVersion)
                manifest.put("buildType", inputs.buildType)
                val device = JSONObject()
                for ((k, v) in inputs.device) device.put(k, v)
                manifest.put("device", device)
                val adapter = JSONObject()
                for ((k, v) in inputs.adapter) adapter.put(k, v)
                manifest.put("adapter", adapter)
                val entries = JSONArray()
                for (name in entryNames) entries.put(name)
                manifest.put("entries", entries)

                val manifestText = try {
                    manifest.toString(2)
                } catch (t: Throwable) {
                    throw IllegalStateException("manifest serialize failed: ${t.message}", t)
                }
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write((manifestText ?: "{}").toByteArray(Charsets.UTF_8))
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

    private fun writeTextEntry(zos: ZipOutputStream, name: String, text: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(text.toByteArray(Charsets.UTF_8))
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
