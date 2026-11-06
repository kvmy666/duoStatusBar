package io.github.kvmy666.duostatusbar

import android.content.Context
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.StockIconHider
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import rikka.shizuku.Shizuku

/**
 * App-side diagnostics capture that works on **every** root method and on none at all.
 *
 * The old capture ran a single `su -c …` and, when that failed (no `su` on `PATH`, or SELinux denying the
 * app), returned an error and left the report with nothing. That is why most user reports arrived as a
 * few hundred bytes (measured: `Cannot run program "su": error=2` / `error=13`). The module's own log is
 * pushed without root, but on a device where the module never loaded even that is absent, so the report
 * had no evidence at all.
 *
 * This capture is now multi-route and never makes one route's failure the whole report's failure:
 *
 *   1. **Root** — only after the user taps *Allow root access* (an explicit `su -c id`). Reads the
 *      filtered logcat and LSPosed's own log (`/data/adb/lspd/log`), the sink that survives OEM log
 *      filtering. This is the only route that can read LSPosed's file, so it stays the best one.
 *   2. **Shizuku / Sui** — a binder, not a binary, so it works regardless of how the phone is rooted (and
 *      on a non-rooted phone started over adb). Runs the same tag-filtered commands as shell.
 *   3. **Module log** — already delivered by the module through the settings channel, no privilege needed.
 *   4. **Device facts** — always appended, so a report is never empty.
 *
 * Every command is filtered to this module (`-s DuoSB`, `grep duostatusbar`); a full logcat is never
 * captured, so no other app's data can leak into a report.
 *
 * [RootProbe] decides which route is available from measured facts; this object only gathers them.
 */
internal object RootLogs {

    /**
     * Where a root shell can live. `su` is not guaranteed to be on the app's `PATH`: locked-bootloader
     * root frameworks (KernelSU, APatch, some Samsung "no unlock" roots) keep it somewhere the app
     * process does not search, and the old code then failed with "Cannot run program su: No such file
     * or directory" — every report from those users came back empty. Each absolute path is tried in
     * turn, then a plain `su` so an ordinary PATH lookup is still the last resort.
     */
    private val SU_CANDIDATES = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/debug_ramdisk/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su",
        "/data/adb/magisk/su",
        "su"
    )

    /** How long a root capture may take before it is abandoned, so a hung prompt cannot freeze the app. */
    private const val ROOT_TIMEOUT_SECONDS = 25L

    /**
     * A single root-grant check is bounded tighter, so trying several spellings cannot stall the UI.
     * KernelSU-family managers do not prompt and may leave the `su` call waiting when the app is not
     * allowlisted, so this must be short.
     */
    private const val ROOT_REQUEST_TIMEOUT_SECONDS = 8L

    /** A re-check of an already-granted root is bounded tighter still, so the guide appears quickly. */
    private const val ROOT_RECHECK_TIMEOUT_SECONDS = 3L

    /** A Shizuku binder round-trip is fast; bound it so a stalled service cannot hang the report. */
    private const val SHIZUKU_TIMEOUT_SECONDS = 20L

    /**
     * The first root shell that actually exists, preferring known absolute paths over a `PATH` lookup.
     * Pure given the `exists` predicate, so the ordering rule is unit-tested rather than only on a phone.
     */
    internal fun firstExisting(candidates: List<String>, exists: (String) -> Boolean): String =
        candidates.firstOrNull { it != "su" && exists(it) } ?: "su"

    /** The first root shell on this device, or `"su"` when none of the known paths exists. */
    internal fun findSuBinary(): String = firstExisting(SU_CANDIDATES) { File(it).exists() }

    /**
     * The `su` spellings to actually try, in order: every known absolute path that exists **and is
     * executable by this app**, then the plain `su` PATH lookup last. A path can exist yet refuse to run
     * for the app (e.g. `/data/adb/ksu/bin/su` is root-only); skipping it avoids reporting "denied" when
     * a different spelling would have worked.
     */
    internal fun suCandidates(): List<String> {
        val runnable = SU_CANDIDATES.filter { it != "su" && File(it).exists() && File(it).canExecute() }
        return runnable + "su"
    }

    /** Whether `id` printed uid 0 — the proof that a granted root shell really ran. */
    internal fun looksRooted(idOutput: String): Boolean {
        val text = idOutput.trim()
        return text.contains("uid=0") || text.substringBefore(' ').trim() == "0"
    }

    /**
     * Asks for root once, on the user's explicit action, and remembers the outcome. Tries each runnable
     * `su` spelling in turn, stopping at the first that prints uid 0 — a spelling that cannot run is not
     * the user's answer. Returns whether a root shell ran as uid 0. Never throws: a missing shell, a
     * denied prompt and a timeout all report `false`, and the report falls back to Shizuku / the module
     * log.
     */
    fun requestRoot(context: Context, timeoutSeconds: Long = ROOT_REQUEST_TIMEOUT_SECONDS): Boolean {
        for (candidate in suCandidates()) {
            val output = runRoot(candidate, "id", timeoutSeconds)
            if (looksRooted(output)) {
                DuoPrefs.writeRootAllowed(context, true)
                L.i("root access granted via $candidate")
                return true
            }
            // A timeout means the root manager is prompting; do not fire a second prompt behind it.
            if (output.contains("timed out")) break
        }
        DuoPrefs.writeRootAllowed(context, false)
        L.w("root access not granted (tried ${suCandidates().joinToString(",")})")
        return false
    }

    /**
     * Re-checks a previously granted root, so revoking it in the root manager (KernelSU Next/SukiSU) is
     * reflected here instead of the stored flag staying `true` forever and the guide never showing. Only
     * runs when the flag is already set, so it never prompts a user who never asked. Returns the answer.
     */
    fun recheckRoot(context: Context): Boolean {
        if (!DuoPrefs.rootAllowed(context)) return false
        return requestRoot(context, ROOT_RECHECK_TIMEOUT_SECONDS)
    }

    /** Shizuku's uid (0 root, 2000 shell), or -1 when it is not reachable. */
    private fun shizukuUid(): Int = try {
        if (Shizuku.pingBinder()) Shizuku.getUid() else -1
    } catch (_: Throwable) {
        -1
    }

    /** Whether the module should attempt a root capture: only after the user granted it once. */
    private fun rootUsable(context: Context): Boolean = DuoPrefs.rootAllowed(context)

    /**
     * Collects the whole capture as one text block. Every section is self-describing; a missing route
     * says so and why, and [nonRootFacts] is always appended. Never throws.
     */
    fun collect(context: Context): String {
        val su = findSuBinary()
        val suAbsolute = su != "su" && File(su).exists()
        val allowed = DuoPrefs.rootAllowed(context)
        val shizukuRunning = try {
            StockIconHider.isShizukuRunning()
        } catch (_: Throwable) {
            false
        }
        val shizukuGranted = try {
            StockIconHider.isPermissionGranted()
        } catch (_: Throwable) {
            false
        }

        val rootOutput = if (rootUsable(context)) runRoot(rootScript()) else null
        val shizukuOutput =
            if (shizukuRunning && shizukuGranted) shizukuExec(context, shellScript()) else null

        // Markers/modules come from whichever elevated capture succeeded; empty otherwise.
        val evidence = rootOutput ?: shizukuOutput ?: ""
        val facts = RootProbe.Facts(
            suCommand = su,
            suExistsAtAbsolutePath = suAbsolute,
            adbMarkers = section(evidence, "root framework").lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.contains(' ') }
                .toSet(),
            kernelModules = section(evidence, "kernel modules"),
            suiAvailable = false, // Sui is detected via the Shizuku binder; see the class note.
            shizukuRunning = shizukuRunning,
            shizukuUid = if (shizukuRunning) shizukuUid() else -1,
            rootAllowed = allowed
        )
        val probe = RootProbe.classify(facts)

        return buildString {
            appendLine("=== capture route ===")
            appendLine(probe.detail)
            if (rootOutput == null) {
                appendLine(
                    when {
                        !allowed -> "root capture skipped: tap \"Allow root access\" to include it."
                        !suAbsolute -> "root capture skipped: no root shell at a known path."
                        else -> "root capture skipped."
                    }
                )
            }
            appendLine()
            if (rootOutput != null) {
                appendLine("=== root log capture ===")
                appendLine(rootOutput)
            }
            appendLine()
            when {
                shizukuOutput != null -> {
                    appendLine("=== shizuku log capture ===")
                    appendLine(shizukuOutput)
                }
                !shizukuRunning -> appendLine("=== shizuku log capture (skipped) ===\nShizuku is not running.")
                !shizukuGranted -> appendLine(
                    "=== shizuku log capture (skipped) ===\nShizuku is running but access is not granted."
                )
                else -> appendLine("=== shizuku log capture (skipped) ===")
            }
            appendLine()
            append(nonRootFacts())
        }
    }

    /**
     * The evidence available with no privilege at all. Weaker than the elevated captures, but it records
     * the device/ROM and the installed module so a "nothing happens" report still identifies the target.
     */
    private fun nonRootFacts(): String = buildString {
        appendLine("=== app-side facts (no privilege) ===")
        appendLine("MANUFACTURER=${android.os.Build.MANUFACTURER} BRAND=${android.os.Build.BRAND} MODEL=${android.os.Build.MODEL}")
        appendLine("DEVICE=${android.os.Build.DEVICE} PRODUCT=${android.os.Build.PRODUCT}")
        appendLine("SDK=${android.os.Build.VERSION.SDK_INT} RELEASE=${android.os.Build.VERSION.RELEASE} DISPLAY=${android.os.Build.DISPLAY}")
        appendLine("selinux=${DeviceFacts.selinux()} abi=${DeviceFacts.abi()}")
        appendLine("rootShell=${findSuBinary()}")
        append("note=if the report lacks a module log, grant root (Allow root access) or run Shizuku; the module log itself needs no root once the module has loaded.")
    }

    /**
     * Restarts SystemUI through root. Used only when the module is not running: the module's own path (kill
     * its process from inside SystemUI) needs no root, so this is the fallback that still works before
     * LSPosed has injected anything — and it is the root prompt users of other modules expect.
     */
    fun restartSystemUi(): Boolean = try {
        // Different ROMs keep different tools, so every spelling is tried in turn: `pkill -f` (AOSP),
        // `killall` (some vendors) and `pidof` + `kill -9` (the one that survives SELinux-restricted
        // pkill). `true` makes the script succeed even when the process was already gone.
        val script = "pkill -f com.android.systemui; " +
            "killall com.android.systemui 2>/dev/null; " +
            "kill -9 ${'$'}(pidof com.android.systemui) 2>/dev/null; true"
        val finished = ProcessBuilder(findSuBinary(), "-c", script).redirectErrorStream(true).start()
            .waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) L.w("root restart timed out after ${ROOT_TIMEOUT_SECONDS}s")
        finished
    } catch (t: Throwable) {
        L.w("root restart failed: ${t.javaClass.simpleName}: ${t.message}")
        false
    }

    /**
     * Removes a leftover `duo_statusbar_stage` adb override and clears the Rive crash counter, so a
     * device that was once pinned to the simple drawing (stage 1) goes back to the app's own setting.
     * The override is a developer kill switch the app cannot write; this is the user-facing way back.
     * Returns true only when the root shell finished (the delete itself is best-effort — `settings`
     * reports nothing useful, so the caller re-reads the value).
     */
    fun clearStageOverride(): Boolean = try {
        val script = "settings delete global duo_statusbar_stage; " +
            "settings put global duo_statusbar_rive_attempts 0; true"
        val finished = ProcessBuilder(findSuBinary(), "-c", script).redirectErrorStream(true).start()
            .waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) L.w("clear override timed out after ${ROOT_TIMEOUT_SECONDS}s")
        finished
    } catch (t: Throwable) {
        L.w("clear override failed: ${t.javaClass.simpleName}: ${t.message}")
        false
    }

    /**
     * Reboots the phone through root, for the case a System UI restart alone does not clear (a module
     * update that needs a clean process). Returns whether the root shell was launched; the device then
     * reboots, so a true result does not mean the call completed.
     */
    fun reboot(): Boolean = try {
        val finished = ProcessBuilder(findSuBinary(), "-c", "reboot").redirectErrorStream(true).start()
            .waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) L.w("reboot timed out after ${ROOT_TIMEOUT_SECONDS}s")
        finished
    } catch (t: Throwable) {
        L.w("reboot failed: ${t.javaClass.simpleName}: ${t.message}")
        false
    }

    private const val FAILURE_PREFIX = "root log collection failed"

    private fun runRoot(script: String): String =
        runRoot(findSuBinary(), script, ROOT_TIMEOUT_SECONDS)

    /**
     * Runs [script] through [su]. stderr is merged into stdout and read in one pass (the old two-stream
     * read could deadlock on a large log), and the process is bounded by [timeoutSeconds] so a stalled
     * root prompt cannot hang the app. Never throws.
     */
    private fun runRoot(
        su: String,
        script: String,
        timeoutSeconds: Long = ROOT_TIMEOUT_SECONDS
    ): String {
        // The read itself can block forever if the root prompt never answers, so the whole
        // start-and-read runs on a worker that the caller abandons after the timeout. Keep the
        // Process itself: Future.cancel cannot interrupt a native stream read, and without
        // destroying the child every timed-out capture leaked a worker thread and an su process.
        val processRef = AtomicReference<Process?>()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "duo-root").apply { isDaemon = true }
        }
        var future: java.util.concurrent.Future<String>? = null
        return try {
            future = executor.submit<String> {
                val process = ProcessBuilder(su, "-c", script)
                    .redirectErrorStream(true)
                    .start()
                processRef.set(process)
                try {
                    val out = process.inputStream.bufferedReader().readText()
                    process.waitFor()
                    out.ifBlank { "$FAILURE_PREFIX: root shell ($su) returned no output - root denied?" }
                } finally {
                    processRef.compareAndSet(process, null)
                    runCatching { process.destroy() }
                }
            }
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (_: java.util.concurrent.TimeoutException) {
            future?.cancel(true)
            processRef.getAndSet(null)?.let { runCatching { it.destroyForcibly() } }
            "$FAILURE_PREFIX: root shell ($su) timed out after ${timeoutSeconds}s"
        } catch (t: Throwable) {
            future?.cancel(true)
            processRef.getAndSet(null)?.let { runCatching { it.destroyForcibly() } }
            "$FAILURE_PREFIX: ${t.javaClass.simpleName}: ${t.message} (tried $su)"
        } finally {
            executor.shutdownNow()
        }
    }

    /**
     * Runs [script] through the Shizuku user service (shell or root backend) and returns its output.
     * [StockIconHider.exec] is asynchronous, so this blocks the calling worker thread on a latch until the
     * callback arrives or the timeout fires. Never throws.
     */
    private fun shizukuExec(context: Context, script: String): String {
        val latch = CountDownLatch(1)
        val result = AtomicReference("")
        return try {
            StockIconHider.exec(context, script) { out ->
                result.set(out)
                latch.countDown()
            }
            if (latch.await(SHIZUKU_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                result.get().ifBlank { "(shizuku returned no output)" }
            } else {
                "(shizuku timed out after ${SHIZUKU_TIMEOUT_SECONDS}s)"
            }
        } catch (t: Throwable) {
            "(shizuku exec failed: ${t.javaClass.simpleName}: ${t.message})"
        }
    }

    /** The root capture: filtered logcat, LSPosed's own log, and the root-framework markers. */
    internal fun rootScript(): String = """
        echo '=== logcat (DuoSB) ==='
        logcat -d -t 3000 -s DuoSB 2>&1
        echo '=== LSPosed newest modules log ==='
        LOG=${'$'}(ls -t /data/adb/lspd/log/modules_*.log 2>/dev/null | head -1)
        echo "file=${'$'}LOG"
        if [ -n "${'$'}LOG" ]; then
          echo '-- matching DuoSB --'
          grep -a -i 'DuoSB\|duostatusbar' "${'$'}LOG" | tail -n 400
        fi
        echo '=== LSPosed config (enabled / scope) ==='
        tr -c '[:print:]' '\n' < /data/adb/lspd/config/modules_config.db 2>/dev/null | grep -a -i -B3 -A8 duostatusbar
        echo '=== root framework ==='
        ls /data/adb 2>/dev/null
        echo '=== kernel modules ==='
        grep -i -E 'kernelsu|ksu' /proc/modules 2>/dev/null
        echo '=== installed module package ==='
        pm path io.github.kvmy666.duostatusbar 2>&1
        dumpsys package io.github.kvmy666.duostatusbar 2>/dev/null | head -n 40
        echo '=== build props ==='
        getprop ro.product.manufacturer
        getprop ro.product.brand
        getprop ro.product.model
        getprop ro.build.version.sdk
        getprop ro.build.version.release
        getprop ro.build.display.id
    """.trimIndent()

    /**
     * The Shizuku capture: the same filtered evidence, limited to what shell (uid 2000) may read. The
     * LSPosed log directory usually needs root — the "Permission denied" that comes back is itself the
     * proof, and is what tells us a device has root but this route cannot read it.
     */
    private fun shellScript(): String = """
        echo '=== logcat (DuoSB) ==='
        logcat -d -t 3000 -s DuoSB 2>&1
        echo '=== root framework ==='
        ls /data/adb 2>&1
        echo '=== kernel modules ==='
        grep -i -E 'kernelsu|ksu' /proc/modules 2>/dev/null
        echo '=== LSPosed logs ==='
        ls -t /data/adb/lspd/log/modules_*.log 2>&1 | head -1
        echo '=== installed module package ==='
        pm path io.github.kvmy666.duostatusbar 2>&1
        echo '=== build props ==='
        getprop ro.product.manufacturer
        getprop ro.product.brand
        getprop ro.product.model
        getprop ro.build.version.sdk
        getprop ro.build.version.release
        getprop ro.build.display.id
    """.trimIndent()

    /**
     * The text of one `=== title ===` section, up to the next section header. Used to keep the markers
     * and the kernel modules apart, so a `ksu` path in the framework list cannot masquerade as an LKM
     * kernel module. Returns "" when the section is absent.
     */
    internal fun section(text: String, title: String): String {
        val lines = text.lines()
        val start = lines.indexOfFirst { it.trim() == "=== $title ===" }
        if (start < 0) return ""
        val out = ArrayList<String>()
        for (i in start + 1 until lines.size) {
            if (lines[i].trim().startsWith("=== ")) break
            out.add(lines[i])
        }
        return out.joinToString("\n").trim()
    }
}
