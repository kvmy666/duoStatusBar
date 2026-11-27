package io.github.kvmy666.duostatusbar

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import io.github.kvmy666.duostatusbar.diagnostics.CaptureSection
import io.github.kvmy666.duostatusbar.diagnostics.RootCapture
import io.github.kvmy666.duostatusbar.diagnostics.SectionState
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.StockIconHider
import io.github.kvmy666.duostatusbar.ui.RestartPolicy
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import rikka.shizuku.Shizuku

/**
 * The result of a root System UI restart: whether the shell finished, and why not when it did not.
 * [blockedYoung] marks the age guard: the process was too fresh to kill, so the app must not fall through
 * to another killer (Shizuku would hit the same fresh pid).
 */
internal data class RestartOutcome(
    val ok: Boolean,
    val reason: String,
    val blockedYoung: Boolean = false
)

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
     * Collects the whole capture as one text block. Kept source-compatible for the settings/report
     * screens: it is exactly [collectSections] rendered. Never throws.
     */
    fun collect(context: Context): String = collectSections(context).render()

    /**
     * The complete diagnostics capture, section by section. Every section carries a state and, when it
     * has no text, the reason: no root, Shizuku not running/not granted, a timeout, a denial, or simply
     * no output. No section is dropped, so a future report can always say what was and was not readable.
     *
     * One bounded merged shell script is used for the root route (single `su` prompt), the same script for
     * Shizuku where the shell uid can run it, and the app-side facts always run with no privilege.
     */
    fun collectSections(context: Context): RootCapture {
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

        // Prefer a capture that actually produced evidence over a failure string.
        val usableRoot = rootOutput?.takeUnless(::isRootCaptureFailure)
        val usableShizuku = shizukuOutput?.takeUnless(::isShizukuCaptureFailure)
        val evidence = usableRoot ?: usableShizuku ?: ""
        val failureReason = when {
            evidence.isNotBlank() -> null
            rootOutput != null && usableRoot == null -> firstLine(rootOutput)
            shizukuOutput != null && usableShizuku == null -> firstLine(shizukuOutput)
            else -> null
        }
        val skipReason = if (evidence.isBlank() && failureReason == null) {
            buildList {
                if (rootOutput == null) add(rootUnavailableReason(allowed, suAbsolute))
                if (shizukuOutput == null) add(shizukuUnavailableReason(shizukuRunning, shizukuGranted))
            }.joinToString("; ")
        } else {
            ""
        }

        // Markers/modules come from whichever elevated capture succeeded; empty otherwise.
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

        // This boot, and this build, are the only things a crash count may speak for. Evidence from an
        // earlier boot or an earlier install is history: it is reported, but it is not a current problem.
        val bootEpoch = bootEpochMillis(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        val crashThreshold = crashThresholdMillis(bootEpoch, appUpdatedAt(context))
        val safe = parseSafeMode(section(evidence, "safe mode"), bootEpoch)
        val privileged = privilegedSections(evidence, skipReason, failureReason, bootEpoch)
        val scoped = scopeCrashSections(privileged, crashThreshold)

        val sections = ArrayList<CaptureSection>()
        sections += CaptureSection(
            "capture route",
            SectionState.PRESENT,
            "",
            routeText(probe, rootOutput, shizukuOutput, skipReason, failureReason)
        )
        var historyAdded = false
        for (captured in scoped.sections) {
            sections += captured
            if (captured.title == "logcat crash buffer") {
                sections += scoped.history
                historyAdded = true
            }
        }
        if (!historyAdded) sections += scoped.history

        val crashReadable = scoped.sections.any {
            it.title in CRASH_SECTION_TITLES &&
                (it.state == SectionState.PRESENT || it.state == SectionState.EMPTY)
        }
        // A skipped route is not a failed capture: there the count is unknown, not unreadable.
        val deathUnreadable = if (failureReason != null && !safe.readable && !crashReadable) {
            failureReason
        } else {
            null
        }
        val counter = if (safe.readable) safe.counter ?: 0 else null
        val uptime = uptimeSeconds()
        val deaths = buildString {
            appendLine(deathSummary(counter, deathUnreadable))
            appendLine("safeMode=${safeModeLabel(safe)}")
            appendLine("safeModeCounter=${counter?.toString() ?: "unknown"}")
            appendLine("uptime=${uptime?.let { "${it}s" } ?: "unreadable"}")
            appendLine("crashRecords=${scoped.currentCount}")
        }.trimEnd()
        sections += CaptureSection("systemui deaths", SectionState.PRESENT, "", deaths)

        sections += appBreadcrumbSection(context)
        sections += CaptureSection(
            "app-side facts (no privilege)",
            SectionState.PRESENT,
            "",
            nonRootFacts()
        )

        return RootCapture(probe.detail, sections)
    }

    /** The app-side breadcrumb: readable without root, so it is never part of a privileged section. */
    private fun appBreadcrumbSection(context: Context): CaptureSection = try {
        val value = Settings.Global.getString(context.contentResolver, Breadcrumb.KEY)
        if (value.isNullOrBlank()) {
            CaptureSection(BREADCRUMB_APP_TITLE, SectionState.EMPTY, "unset")
        } else {
            CaptureSection(BREADCRUMB_APP_TITLE, SectionState.PRESENT, "", value)
        }
    } catch (t: Throwable) {
        CaptureSection(
            BREADCRUMB_APP_TITLE,
            SectionState.UNREADABLE,
            "${t.javaClass.simpleName}: ${t.message}"
        )
    }

    private fun routeText(
        probe: RootProbe.Result,
        rootOutput: String?,
        shizukuOutput: String?,
        skipReason: String,
        failureReason: String?
    ): String = buildString {
        appendLine(probe.detail)
        appendLine("rootAttempted=${rootOutput != null} shizukuAttempted=${shizukuOutput != null}")
        when {
            failureReason != null -> append("captureFailure=$failureReason")
            skipReason.isNotBlank() -> append("captureSkipped=$skipReason")
        }
    }.trimEnd()

    /**
     * The evidence available with no privilege at all. Weaker than the elevated captures, but it records
     * the device/ROM and the installed module so a "nothing happens" report still identifies the target.
     */
    private fun nonRootFacts(): String = buildString {
        appendLine("MANUFACTURER=${android.os.Build.MANUFACTURER} BRAND=${android.os.Build.BRAND} MODEL=${android.os.Build.MODEL}")
        appendLine("DEVICE=${android.os.Build.DEVICE} PRODUCT=${android.os.Build.PRODUCT}")
        appendLine("SDK=${android.os.Build.VERSION.SDK_INT} RELEASE=${android.os.Build.VERSION.RELEASE} DISPLAY=${android.os.Build.DISPLAY}")
        appendLine("selinux=${DeviceFacts.selinux()} abi=${DeviceFacts.abi()}")
        appendLine("rootShell=${findSuBinary()}")
        appendLine("uptime=${uptimeSeconds()?.let { "${it}s" } ?: "unreadable"}")
        append("note=if the report lacks a module log, grant root (Allow root access) or run Shizuku; the module log itself needs no root once the module has loaded.")
    }

    /**
     * Restarts SystemUI through root, with the reason kept, so the settings screen can say why the button
     * did nothing instead of leaving the user with silence. Never throws.
     */
    fun restartSystemUiDetailed(): RestartOutcome {
        var su = "su"
        return try {
            su = findSuBinary()
            val process = ProcessBuilder(su, "-c", restartScript())
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                L.w("root restart timed out after ${ROOT_TIMEOUT_SECONDS}s")
                RestartOutcome(false, restartTimeoutReason(su, ROOT_TIMEOUT_SECONDS))
            } else {
                val exit = process.exitValue()
                // The process has ended, so reading the merged stream cannot block.
                val output = process.inputStream.bufferedReader().readText().trim()
                if (exit == 0) {
                    RestartOutcome(true, "")
                } else {
                    val reason = restartExitReason(su, exit, output)
                    L.w("root restart failed: $reason")
                    RestartOutcome(false, reason, blockedYoung = isYoungRefusal(exit, output))
                }
            }
        } catch (t: Throwable) {
            val reason = rootRestartFailure(t, su)
            L.w("root restart failed: $reason")
            RestartOutcome(false, reason)
        }
    }

    /**
     * The pid-targeted restart script.
     *
     * It reads the *current* SystemUI pid and its age (start time from `/proc/<pid>/stat` field 22 against
     * `/proc/uptime`, using `CLK_TCK`), refuses before killing when the pid is younger than
     * [minAgeSeconds], and then kills exactly that pid. `pkill`/`killall` are deliberately absent: after
     * the kill they can match the replacement process Android starts, which is how the app's old fallback
     * took down the fresh SystemUI and drove LSPosed into safe mode.
     *
     * Exposed so the rules are unit-tested without a device or `su`.
     */
    internal fun restartScript(minAgeSeconds: Long = RestartPolicy.MIN_SYSTEMUI_AGE_S): String = """
        PID=${'$'}(pidof com.android.systemui 2>/dev/null | cut -d' ' -f1)
        if [ -z "${'$'}PID" ]; then
          echo "refused: systemui pid not found"
          exit 4
        fi
        STAT=${'$'}(cat /proc/${'$'}PID/stat 2>/dev/null)
        if [ -z "${'$'}STAT" ]; then
          echo "refused: systemui stat unreadable"
          exit 4
        fi
        # Field 22 is starttime; the comm field can contain spaces, so strip through the last ')' first.
        REST=${'$'}{STAT##*) }
        START=${'$'}(echo "${'$'}REST" | awk '{print ${'$'}20}' 2>/dev/null)
        TCK=${'$'}(getconf CLK_TCK 2>/dev/null || echo 100)
        case "${'$'}TCK" in ''|*[!0-9]*) TCK=100 ;; esac
        case "${'$'}START" in ''|*[!0-9]*) echo "refused: systemui starttime unreadable"; exit 4 ;; esac
        UP=${'$'}(cut -d. -f1 /proc/uptime 2>/dev/null)
        case "${'$'}UP" in ''|*[!0-9]*) echo "refused: systemui uptime unreadable"; exit 4 ;; esac
        # Round the start up, so a process just under the guard is never rounded over it.
        START_S=${'$'}(( (START + TCK - 1) / TCK ))
        AGE=${'$'}(( UP - START_S ))
        if [ "${'$'}AGE" -lt $minAgeSeconds ]; then
          echo "refused: systemui age ${'$'}{AGE}s < ${minAgeSeconds}s"
          exit 3
        fi
        kill -9 "${'$'}PID"
        exit 0
    """.trimIndent()

    /** The shell's exit code for "refused: the target is too young to kill". */
    internal const val EXIT_YOUNG = 3

    /** The first part of the shell's young-refusal message, shared with [isYoungRefusal]. */
    internal const val YOUNG_REFUSAL_PREFIX = "refused: systemui age"

    /** The exact young-refusal line for [ageSeconds], or null when the age passes the guard. */
    internal fun youngRefusalMessage(
        ageSeconds: Long,
        minAgeSeconds: Long = RestartPolicy.MIN_SYSTEMUI_AGE_S
    ): String? =
        if (ageSeconds < minAgeSeconds) {
            "$YOUNG_REFUSAL_PREFIX ${ageSeconds}s < ${minAgeSeconds}s"
        } else {
            null
        }

    /**
     * Whether a failed root restart was the age guard (exit 3, or the marker in the output when a shell
     * does not pass the status through). Such a failure must not fall through to Shizuku.
     */
    internal fun isYoungRefusal(exit: Int, output: String): Boolean =
        exit == EXIT_YOUNG || output.lineSequence().any { it.trim().startsWith(YOUNG_REFUSAL_PREFIX) }

    /** The failure text for a restart that could not run the shell, naming the binary that was tried. */
    internal fun rootRestartFailure(t: Throwable, su: String): String =
        "su $su: ${t.javaClass.simpleName}: ${t.message}"

    /** The failure text for a restart that was still running when the timeout fired. */
    internal fun restartTimeoutReason(su: String, seconds: Long): String =
        "su $su: timed out after ${seconds}s"

    /**
     * The failure text for a shell that ran but exited non-zero: the usual case is a root manager
     * denying the request (the prompt was refused or the app is not allowlisted), which exits at once.
     * The first non-blank line of the merged output is included when there is one, capped so a noisy
     * denial cannot fill the toast.
     */
    internal fun restartExitReason(su: String, exitCode: Int, output: String): String {
        val base = "su $su: exit $exitCode"
        val line = output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: return base
        return "$base: ${line.take(200)}"
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

    /**
     * The root capture: one bounded merged script, so a single `su` prompt covers every section.
     *
     * Log sources are allow-listed to the known framework log directories ([FRAMEWORK_LOG_DIRS]);
     * a generic per-package log sweep is deliberately absent, because it read KernelSU's own
     * logcat/su logs and unrelated data into the report. Each section is bounded with `head`/`tail`,
     * so a noisy log cannot exceed the report budget. The newest-log section names its candidates and
     * everything it scanned, so the next report can distinguish "no file matched" from "no file was
     * read" instead of ending at a bare `-- matching DuoSB --`. The existing commands are kept.
     */
    internal fun rootScript(): String = """
        echo '=== logcat (DuoSB) ==='
        logcat -d -t 3000 -s DuoSB 2>&1 | tail -n 1000
        echo '=== LSPosed newest modules log ==='
        CANDIDATES=${'$'}(ls -t ${frameworkLogGlob("modules_*.log")} 2>/dev/null | head -n 40)
        CANDIDATE_COUNT=${'$'}(printf '%s\n' "${'$'}CANDIDATES" | awk 'NF{n++} END{print n+0}')
        SCANNED=''
        FOUND=''
        for f in ${'$'}CANDIDATES; do
          SCANNED="${'$'}SCANNED ${'$'}f"
          if grep -a -i -q -E 'DuoSB|duostatusbar' "${'$'}f" 2>/dev/null; then
            FOUND="${'$'}f"
            break
          fi
        done
        echo "candidates=${'$'}CANDIDATE_COUNT"
        echo "scanned=${'$'}{SCANNED# }"
        echo "file=${'$'}FOUND"
        if [ -n "${'$'}FOUND" ]; then
          echo '-- matching DuoSB --'
          grep -a -i -E 'DuoSB|duostatusbar' "${'$'}FOUND" | tail -n 400
        else
          echo 'matchingDuoSB=0'
        fi
        echo '=== LSPosed log files ==='
        for d in ${frameworkLogDirs()}; do
          [ -d "${'$'}d" ] || continue
          echo "dir=${'$'}d"
          SEEN_FILES=''
          for f in ${'$'}(ls -t "${'$'}d"/modules_*.log "${'$'}d"/verbose_*.log "${'$'}d"/lspd.log 2>/dev/null | head -n 6); do
            SEEN_FILES="${'$'}SEEN_FILES ${'$'}f"
            echo "file=${'$'}f"
            MATCHES=${'$'}(grep -a -i -c -E 'DuoSB|duostatusbar' "${'$'}f" 2>/dev/null)
            echo "matches=${'$'}{MATCHES:-0}"
            grep -a -i -E 'DuoSB|duostatusbar' "${'$'}f" 2>/dev/null | tail -n 100
          done
          for f in ${'$'}(ls -t "${'$'}d"/*.log 2>/dev/null | head -n 4); do
            case " ${'$'}SEEN_FILES " in *" ${'$'}f "*) continue ;; esac
            echo "file=${'$'}f"
            MATCHES=${'$'}(grep -a -i -c -E 'DuoSB|duostatusbar' "${'$'}f" 2>/dev/null)
            echo "matches=${'$'}{MATCHES:-0}"
            grep -a -i -E 'DuoSB|duostatusbar' "${'$'}f" 2>/dev/null | tail -n 100
          done
        done
        echo '=== safe mode ==='
        SAFE_COUNT=0
        SAFE_READABLE=0
        for f in ${'$'}(ls -t ${frameworkLogGlob("verbose_*.log")} 2>/dev/null | head -n 8); do
          SAFE_COUNT=${'$'}(( SAFE_COUNT + 1 ))
          echo "file=${'$'}f"
          if head -n 1 "${'$'}f" >/dev/null 2>&1; then
            SAFE_READABLE=${'$'}(( SAFE_READABLE + 1 ))
          fi
          grep -a 'LSPosedService-SafeMode' "${'$'}f" 2>/dev/null | tail -n 40
        done
        echo "safeLogFiles=${'$'}SAFE_COUNT"
        echo "safeReadableFiles=${'$'}SAFE_READABLE"
        echo '=== LSPosed config (enabled / scope) ==='
        SQLITE=${'$'}(command -v sqlite3 2>/dev/null)
        if [ -z "${'$'}SQLITE" ]; then
          for c in /data/adb/magisk/sqlite3 /data/adb/ksu/bin/sqlite3; do
            if [ -x "${'$'}c" ]; then SQLITE="${'$'}c"; break; fi
          done
        fi
        DB=''
        for d in ${frameworkConfigDbs()}; do
          if [ -f "${'$'}d" ]; then DB="${'$'}d"; break; fi
        done
        echo "db=${'$'}{DB:-unknown}"
        if [ -n "${'$'}SQLITE" ] && [ -n "${'$'}DB" ]; then
          echo "sqlite=${'$'}SQLITE"
          "${'$'}SQLITE" -header -separator '|' "${'$'}DB" "SELECT * FROM modules WHERE module LIKE '%duostatusbar%';" 2>&1 | head -n 40
        elif [ -z "${'$'}SQLITE" ]; then
          echo 'sqlite3=absent'
        else
          echo "sqlite=${'$'}SQLITE"
          echo 'db not found; modules_config.db not queried'
        fi
        echo '=== framework manager ==='
        dumpsys package org.lsposed.manager 2>&1 | grep -a -E 'Package \[|versionName|versionCode|Unable to find|not found' | head -n 20
        echo '=== module.prop ==='
        for f in /data/adb/*/module.prop /data/adb/modules/*/module.prop; do
          [ -f "${'$'}f" ] || continue
          ID=${'$'}(grep -a -m1 '^id=' "${'$'}f" 2>/dev/null | cut -d= -f2- | tr -d '\r' | tr -d '"' | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')
          case "${'$'}ID" in
            *lsposed*|lspatch|zygisk_lspatch|vector|zygisksu|zygisk-next|rezygisk) ;;
            *) continue ;;
          esac
          echo "file=${'$'}f"
          grep -a -E '^(id|name|version|versionCode)=' "${'$'}f" | head -n 4
        done
        echo '=== /data/adb layout ==='
        ls -la /data/adb 2>&1 | head -n 80
        echo '=== root framework ==='
        ls /data/adb 2>/dev/null
        echo '=== kernel modules ==='
        grep -i -E 'kernelsu|ksu' /proc/modules 2>/dev/null
        echo '=== installed module package ==='
        pm path io.github.kvmy666.duostatusbar 2>&1
        dumpsys package io.github.kvmy666.duostatusbar 2>/dev/null | head -n 40
        echo '=== crash records ==='
        echo '-- dropbox system_app_crash --'
        dumpsys dropbox --print system_app_crash 2>&1 | grep -a -i -B8 -A10 'com.android.systemui' | tail -n 100
        echo '-- dropbox system_app_native_crash --'
        dumpsys dropbox --print system_app_native_crash 2>&1 | grep -a -i -B8 -A10 'com.android.systemui' | tail -n 100
        echo '-- dropbox SYSTEM_TOMBSTONE --'
        dumpsys dropbox --print SYSTEM_TOMBSTONE 2>&1 | grep -a -i -B8 -A10 'com.android.systemui' | tail -n 100
        echo '=== tombstones ==='
        for t in ${'$'}(ls -t /data/tombstones/tombstone_* 2>/dev/null | head -n 6); do
          if grep -a -q 'com.android.systemui' "${'$'}t" 2>/dev/null; then
            echo "tombstone=${'$'}t"
            grep -a -m1 'Timestamp:' "${'$'}t" 2>/dev/null | head -n 1
            grep -a -i -B2 -A6 'com.android.systemui' "${'$'}t" | head -n 120
          fi
        done
        echo '=== logcat crash buffer ==='
        logcat -d -b crash -t 500 2>&1 | grep -a -i -B2 -A4 'com.android.systemui' | tail -n 200
        echo '=== breadcrumb ==='
        settings get global duo_statusbar_breadcrumb 2>&1
        for f in /data/user_de/0/com.android.systemui/files/duo-breadcrumb.txt /data/data/com.android.systemui/files/duo-breadcrumb.txt; do
          echo "file=${'$'}f"
          cat "${'$'}f" 2>&1 | tail -n 12
        done
        echo '=== build props ==='
        getprop ro.product.manufacturer
        getprop ro.product.brand
        getprop ro.product.model
        getprop ro.build.version.sdk
        getprop ro.build.version.release
        getprop ro.build.display.id
    """.trimIndent()

    /**
     * The Shizuku capture. It runs the same bounded script through the shell user service, so the
     * section set is identical; what uid 2000 cannot read comes back as "Permission denied" and is
     * reported as unreadable instead of pretending the data was empty.
     */
    private fun shellScript(): String = rootScript()

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

    // --- Wave C1 stubs: the red tests pin the real rules first. ---

    // --- R15 C1: section capture helpers (pure, so the rules are unit-tested on the JVM). ---

    /** Per-section budget; anything longer is cut with a visible marker, never silently dropped. */
    internal const val MAX_SECTION_CHARS = 12_000

    /**
     * Both places SystemUI may keep the breadcrumb file. `/data/user_de/0` is the normal (device-encrypted)
     * location; `/data/data` is kept as the fallback for ROMs that do not use the user partition layout.
     */
    internal val BREADCRUMB_FILES: List<String> = listOf(
        "/data/user_de/0/com.android.systemui/files/duo-breadcrumb.txt",
        "/data/data/com.android.systemui/files/duo-breadcrumb.txt"
    )

    private const val BREADCRUMB_APP_TITLE = "breadcrumb (app fallback)"

    private val CRASH_SECTION_TITLES = setOf("crash records", "tombstones", "logcat crash buffer")

    /**
     * The only log directories a report may read. These are the frameworks' own logs (LSPosed,
     * Vector, LSPatch). KernelSU's `/data/adb/ksu/log` is deliberately absent: it is the root
     * manager's shell/logcat log, not framework evidence, and reading it leaked unrelated data.
     */
    internal val FRAMEWORK_LOG_DIRS: List<String> = listOf(
        "/data/adb/lspd/log",
        "/data/adb/vector/log",
        "/data/adb/lspatch/log"
    )

    private val FRAMEWORK_CONFIG_DBS: List<String> = FRAMEWORK_LOG_DIRS.map {
        it.removeSuffix("/log") + "/config/modules_config.db"
    }

    private const val CRASH_HISTORY_TITLE = "crash history (older boots)"

    /** Every privileged section the merged script emits. None of these may be dropped from a report. */
    private val PRIVILEGED_SECTIONS = listOf(
        "logcat (DuoSB)",
        "LSPosed log files",
        "LSPosed newest modules log",
        "safe mode",
        "LSPosed config (enabled / scope)",
        "framework manager",
        "module.prop",
        "/data/adb layout",
        "root framework",
        "kernel modules",
        "installed module package",
        "crash records",
        "tombstones",
        "logcat crash buffer",
        "breadcrumb",
        "build props"
    )

    private val UNREADABLE_MARKERS = listOf(
        "permission denied",
        "operation not permitted",
        "no such file or directory",
        "can't execute",
        "command not found",
        "timed out",
        "root log collection failed",
        "shizuku exec failed",
        "inaccessible"
    )

    private val SAFE_MODE_COUNTER = Regex("""(?i)System UI crashed\s+(\d+)\s+time""")

    private val SAFE_MODE_TAG = "LSPosedService-SafeMode"

    private val SAFE_LOG_FILES = Regex("""safeLogFiles=(\d+)""")

    private val SAFE_READABLE_FILES = Regex("""safeReadableFiles=(\d+)""")

    /** The `[ 2026-11-25T00:19:06.527 … ]` prefix LSPosed writes on every verbose line. */
    private val LSPD_LINE_TIME =
        Regex("""^\[\s*(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?)""")

    private val LOG_TIMESTAMP =
        Regex("""\d{4}[-_]\d{2}[-_]\d{2}[T _-]\d{2}[-_:.]\d{2}[-_:.]\d{2}""")

    private val FRAMEWORK_MANAGER_VERSION = Regex("""versionName=(\S+)""")

    private val DROPBOX_ENTRY_TIME =
        Regex("""^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2}:\d{2})""")

    private val TOMBSTONE_ENTRY = Regex("""^tombstone=\S+""")

    private val TOMBSTONE_TIME = Regex(
        """Timestamp:\s*(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2}:\d{2})(?:\.\d+)?\s*([+-])(\d{2}):?(\d{2})"""
    )

    private val LOGCAT_ENTRY_TIME =
        Regex("""^(\d{2})-(\d{2})\s+(\d{2}:\d{2}:\d{2}\.\d{3})""")

    private val SQLITE_FAILURES = listOf(
        "error:",
        "unable to open",
        "cannot open",
        "no such table",
        "permission denied",
        "sqlite3=absent",
        "not queried"
    )

    private val FRAMEWORK_MODULE_IDS = setOf(
        "zygisk_lsposed",
        "lsposed",
        "vector",
        "zygisksu",
        "zygisk-next",
        "rezygisk",
        "lspatch",
        "zygisk_lspatch"
    )

    internal data class SafeModeReport(
        val counter: Int?,
        val entered: Boolean,
        val lines: List<String>,
        val history: List<String> = emptyList(),
        val logFiles: Int = 0,
        val readableFiles: Int? = null
    ) {
        /** A count of zero is evidence only when a verbose log was actually read. */
        val readable: Boolean
            get() = when {
                readableFiles != null -> readableFiles > 0 || lines.isNotEmpty() || history.isNotEmpty()
                else -> logFiles > 0 || lines.isNotEmpty() || history.isNotEmpty()
            }
    }

    internal data class SectionVerdict(val state: SectionState, val reason: String = "")

    /** `entered` / `no` only when LSPosed's own log was readable; otherwise unknown, never "not found". */
    internal fun safeModeLabel(report: SafeModeReport): String = when {
        !report.readable -> "unknown"
        report.entered -> "entered"
        else -> "no"
    }

    /**
     * The safe-mode counter (highest current-boot `System UI crashed N time(s)`), whether safe mode was
     * entered, and the actual matching lines, quoted so a report shows LSPosed's own wording.
     *
     * Only lines carrying [SAFE_MODE_TAG] are considered. When [bootEpochMillis] is supplied, a line
     * whose leading LSPosed timestamp predates this boot is history, not this boot's counter: LSPosed
     * keeps its verbose log across boots, so an old "crashed 3 time(s)" must not report today's bar.
     */
    internal fun parseSafeMode(
        text: String,
        bootEpochMillis: Long? = null,
        zone: ZoneId = ZoneId.systemDefault()
    ): SafeModeReport {
        val logFiles = SAFE_LOG_FILES.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val readableFiles = SAFE_READABLE_FILES.find(text)?.groupValues?.get(1)?.toIntOrNull()
        val current = ArrayList<String>()
        val history = ArrayList<String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || !line.contains(SAFE_MODE_TAG)) continue
            val at = lspdLineEpoch(line, zone)
            // With a known boot time, an unparseable timestamp is not proof of "this boot": an old
            // safe-mode line must stay history and must never re-trigger a current crash problem.
            if (bootEpochMillis != null && (at == null || at < bootEpochMillis)) {
                history += line
            } else {
                current += line
            }
        }
        val currentText = current.joinToString("\n")
        val counter = SAFE_MODE_COUNTER.findAll(currentText)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .maxOrNull()
        val entered = current.any {
            it.contains("too many times", ignoreCase = true) ||
                it.contains("stop all modules", ignoreCase = true) ||
                it.contains("enter safe mode", ignoreCase = true)
        }
        return SafeModeReport(counter, entered, current, history, logFiles, readableFiles)
    }

    private fun lspdLineEpoch(line: String, zone: ZoneId): Long? {
        val stamp = LSPD_LINE_TIME.find(line)?.groupValues?.get(1) ?: return null
        return runCatching {
            LocalDateTime.parse(stamp).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()
    }

    /**
     * Framework log files from a `file=…` listing, newest first.
     *
     * Only files inside [FRAMEWORK_LOG_DIRS] are kept; a `dir=…` line is informational. The shell
     * already lists with `ls -t`; timestamped names are ordered here as well, and the sort is stable
     * so a mixed-directory listing cannot reorder equal keys.
     */
    internal fun discoverLogFiles(listing: String): List<String> {
        val files = ArrayList<String>()
        for (raw in listing.lineSequence()) {
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("dir=")) continue
            val path = line.removePrefix("file=").trim()
            if (path.isBlank()) continue
            if (isFrameworkLog(path)) files += path
        }
        // Stable sort: equal keys keep the shell's own `ls -t` order.
        return files.distinct().sortedWith(compareByDescending { logSortKey(it) })
    }

    /**
     * The first log in [filesNewestFirst] whose text names this module, or null when none does.
     *
     * LSPosed rotates `modules_<ISO>.log` and writes the module's own `DuoSB | …` lines wherever the
     * module was loaded; the newest file can be a header-only rotate while an older file holds the load
     * (owner layout: newest `modules_2026-11-27T00:00:49…log` had no DuoSB line, the module loaded at
     * 02:11). So the scan must keep going past a file with no match instead of trusting the newest file.
     * A file that cannot be read is skipped, not treated as a match: an unreadable log is not evidence.
     * The shell loop in [rootScript] mirrors this rule (scan newest-first, record, break on first match).
     */
    internal fun newestLogWithDuoB(
        filesNewestFirst: List<String>,
        read: (String) -> String
    ): String? = filesNewestFirst.firstOrNull { path ->
        val text = try {
            read(path)
        } catch (_: Throwable) {
            ""
        }
        text.contains("DuoSB", ignoreCase = true) ||
            text.contains("duostatusbar", ignoreCase = true)
    }

    private fun isFrameworkLog(path: String): Boolean {
        val dir = path.substringBeforeLast('/', "")
        if (dir.trimEnd('/') !in FRAMEWORK_LOG_DIRS) return false
        return path.substringAfterLast('/').endsWith(".log")
    }

    private fun logSortKey(path: String): String =
        LOG_TIMESTAMP.find(path.substringAfterLast('/'))?.value?.filter { it.isDigit() } ?: ""

    /** Crash records narrowed to this SystemUI; another package's death must not reach the report. */
    internal fun filterSystemUiCrashes(text: String, pkg: String = "com.android.systemui"): String =
        text.lineSequence().filter { it.contains(pkg, ignoreCase = true) }.joinToString("\n")

    internal data class CrashScope(
        val current: String,
        val history: String,
        val currentCount: Int,
        val historyCount: Int
    )

    internal data class ScopedCrashes(
        val sections: List<CaptureSection>,
        val history: CaptureSection,
        val currentCount: Int,
        val historyCount: Int
    )

    /**
     * Splits crash evidence into this boot/build and history.
     *
     * Dropbox entries start with `2026-11-24 16:50:39 system_app_crash …`, tombstones with
     * `tombstone=…` (their date is the `Timestamp: …+0300` line), and logcat crash lines with
     * `MM-dd HH:mm:ss.mmm`. An entry older than [thresholdMillis], or one whose date cannot be
     * parsed, is history: it is still reported, but it cannot count as a current problem.
     */
    internal fun scopeCrashText(
        text: String,
        thresholdMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): CrashScope {
        if (text.isBlank()) return CrashScope("", "", 0, 0)
        val lines = text.lines()
        val starts = ArrayList<Pair<Int, Long?>>()
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trimEnd()
            val dropbox = DROPBOX_ENTRY_TIME.find(trimmed)
            val logcat = LOGCAT_ENTRY_TIME.find(trimmed)
            when {
                dropbox != null -> starts += index to localEpoch(
                    dropbox.groupValues[1], dropbox.groupValues[2], zone
                )
                TOMBSTONE_ENTRY.containsMatchIn(trimmed) -> starts += index to null
                logcat != null -> starts += index to logcatEpoch(logcat, zone, nowMillis)
            }
        }
        // Only a recognised record start is an entry; header lines alone are not a crash record.
        if (starts.isEmpty()) return CrashScope("", "", 0, 0)
        val preamble = if (starts.first().first > 0) {
            lines.subList(0, starts.first().first)
        } else {
            emptyList()
        }
        val entries = ArrayList<Pair<Long?, String>>()
        for ((position, start) in starts.withIndex()) {
            val end = if (position + 1 < starts.size) starts[position + 1].first else lines.size
            val body = lines.subList(start.first, end)
            val entryLines = if (position == 0 && preamble.isNotEmpty()) preamble + body else body
            val entryText = entryLines.joinToString("\n").trimEnd()
            val isTombstone = body.firstOrNull()?.let(TOMBSTONE_ENTRY::containsMatchIn) == true
            val timestamp = if (isTombstone) tombstoneEpoch(entryText) else start.second
            entries += timestamp to entryText
        }
        val current = entries.filter { it.first != null && it.first!! >= thresholdMillis }
        val history = entries.filter { it.first == null || it.first!! < thresholdMillis }
        return CrashScope(
            current = current.joinToString("\n\n") { it.second }.trim(),
            history = history.joinToString("\n\n") { it.second }.trim(),
            currentCount = current.size,
            historyCount = history.size
        )
    }

    /**
     * Applies [scopeCrashText] to the three crash sections and builds the explicit history section.
     * The history title is deliberately not in [CRASH_SECTION_TITLES], so `ProblemDetector` can never
     * read an old crash as this boot's problem.
     */
    internal fun scopeCrashSections(
        sections: List<CaptureSection>,
        thresholdMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): ScopedCrashes {
        var historyText = ""
        var historyCount = 0
        var currentCount = 0
        val out = sections.map { section ->
            if (section.title !in CRASH_SECTION_TITLES || section.state != SectionState.PRESENT) {
                return@map section
            }
            val scoped = scopeCrashText(section.text, thresholdMillis, zone, nowMillis)
            currentCount += scoped.currentCount
            if (scoped.history.isNotBlank()) {
                historyText += (if (historyText.isEmpty()) "" else "\n\n") +
                    "-- ${section.title} --\n${scoped.history}"
                historyCount += scoped.historyCount
            }
            when {
                scoped.current.isNotBlank() -> section.copy(text = bounded(scoped.current))
                section.text.isBlank() -> section
                else -> section.copy(
                    state = SectionState.EMPTY,
                    reason = "no entries since ${crashThresholdText(thresholdMillis, zone)}",
                    text = ""
                )
            }
        }
        val crashSections = sections.filter { it.title in CRASH_SECTION_TITLES }
        val routeFailure = crashSections.firstOrNull {
            it.state == SectionState.SKIPPED || it.state == SectionState.UNREADABLE
        }
        val anyReadable = crashSections.any {
            it.state == SectionState.PRESENT || it.state == SectionState.EMPTY
        }
        val history = when {
            historyText.isNotBlank() -> CaptureSection(
                CRASH_HISTORY_TITLE,
                SectionState.PRESENT,
                "",
                bounded(historyText)
            )
            !anyReadable && routeFailure != null -> CaptureSection(
                CRASH_HISTORY_TITLE,
                routeFailure.state,
                routeFailure.reason,
                ""
            )
            else -> CaptureSection(
                CRASH_HISTORY_TITLE,
                SectionState.EMPTY,
                "no crash entries older than the current boot/build",
                ""
            )
        }
        return ScopedCrashes(out, history, currentCount, historyCount)
    }

    private fun localEpoch(date: String, time: String, zone: ZoneId): Long? = runCatching {
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    private fun tombstoneEpoch(text: String): Long? {
        val match = TOMBSTONE_TIME.find(text) ?: return null
        val local = runCatching {
            LocalDateTime.parse("${match.groupValues[1]}T${match.groupValues[2]}")
        }.getOrNull() ?: return null
        val sign = if (match.groupValues[3] == "-") -1 else 1
        val offset = sign *
            (match.groupValues[4].toInt() * 3600 + match.groupValues[5].toInt() * 60)
        return runCatching {
            local.toInstant(ZoneOffset.ofTotalSeconds(offset)).toEpochMilli()
        }.getOrNull()
    }

    private fun logcatEpoch(match: MatchResult, zone: ZoneId, nowMillis: Long): Long? {
        val year = Instant.ofEpochMilli(nowMillis).atZone(zone).year
        val month = match.groupValues[1]
        val day = match.groupValues[2]
        val time = match.groupValues[3]
        val guessed = localEpoch("$year-$month-$day", time, zone) ?: return null
        // A December/January boundary: a future date belongs to last year's buffer.
        return if (guessed > nowMillis + 86_400_000L) {
            localEpoch("${year - 1}-$month-$day", time, zone)
        } else {
            guessed
        }
    }

    private fun crashThresholdText(epochMillis: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    internal fun rootUnavailableReason(allowed: Boolean, suAbsolute: Boolean): String = when {
        !allowed -> "no root: root access was not granted"
        !suAbsolute -> "no root: no root shell at a known path"
        else -> "root capture skipped"
    }

    internal fun shizukuUnavailableReason(running: Boolean, granted: Boolean): String = when {
        !running -> "Shizuku is not running"
        !granted -> "Shizuku is running but access is not granted"
        else -> "Shizuku capture skipped"
    }

    /**
     * `deaths=<counter|unknown|unreadable:reason>`; a refused capture must never read as zero deaths,
     * and an unknown counter is never dressed up as `records`.
     */
    internal fun deathSummary(counter: Int?, unreadable: String?): String = when {
        !unreadable.isNullOrBlank() -> "deaths=unreadable:$unreadable"
        counter != null -> "deaths=$counter"
        else -> "deaths=unknown"
    }

    internal fun bounded(text: String, max: Int = MAX_SECTION_CHARS): String =
        if (text.length <= max) text else text.take(max) + "\n… [truncated ${text.length - max} chars]"

    internal fun parseUptime(text: String): Long? =
        text.trim().substringBefore(' ').toDoubleOrNull()?.toLong()

    /**
     * Uptime from the bounded monotonic clock, not `/proc/uptime` (which needs a readable procfs and
     * produced `uptime=unreadable` on the owner's device). The clock is injectable for the JVM test.
     */
    internal fun uptimeSeconds(clock: () -> Long = { SystemClock.elapsedRealtime() }): Long? = try {
        val millis = clock()
        if (millis < 0L) null else millis / 1000L
    } catch (_: Throwable) {
        null
    }

    /** Epoch millis of the wall-clock instant the phone booted: now - time since boot. */
    internal fun bootEpochMillis(nowMillis: Long, elapsedRealtimeMillis: Long): Long =
        nowMillis - elapsedRealtimeMillis.coerceAtLeast(0L)

    /** Crash evidence older than either this boot or this install is history, not a current problem. */
    internal fun crashThresholdMillis(bootEpochMillis: Long, appUpdatedMillis: Long): Long =
        maxOf(bootEpochMillis, appUpdatedMillis.coerceAtLeast(0L))

    private fun appUpdatedAt(context: Context): Long = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    } catch (_: Throwable) {
        0L
    }

    /** The framework module ids whose `module.prop` is evidence; the user's other modules stay private. */
    internal fun isFrameworkModuleId(id: String): Boolean {
        val value = id.trim().trim('"').trim().lowercase()
        if (value.isEmpty()) return false
        return value in FRAMEWORK_MODULE_IDS || value.contains("lsposed")
    }

    private fun frameworkLogDirs(): String = FRAMEWORK_LOG_DIRS.joinToString(" ")

    private fun frameworkLogGlob(name: String): String =
        FRAMEWORK_LOG_DIRS.joinToString(" ") { "$it/$name" }

    private fun frameworkConfigDbs(): String = FRAMEWORK_CONFIG_DBS.joinToString(" ")

    private fun isRootCaptureFailure(text: String): Boolean = text.startsWith(FAILURE_PREFIX)

    private fun isShizukuCaptureFailure(text: String): Boolean =
        text.startsWith("(shizuku timed out") || text.startsWith("(shizuku exec failed")

    private fun firstLine(text: String): String =
        text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(300) ?: "unreadable"

    private fun verdictFor(text: String): SectionVerdict {
        if (text.isBlank()) return SectionVerdict(SectionState.EMPTY, "no output")
        if (text.contains("Unable to find package", ignoreCase = true)) {
            return SectionVerdict(SectionState.EMPTY, "not installed")
        }
        val marker = UNREADABLE_MARKERS.firstOrNull { text.contains(it, ignoreCase = true) }
            ?: return SectionVerdict(SectionState.PRESENT)
        val line = text.lineSequence()
            .firstOrNull { it.contains(marker, ignoreCase = true) }
            ?.trim()
            ?.take(300)
            ?: marker
        return SectionVerdict(SectionState.UNREADABLE, line)
    }

    /**
     * Builds every privileged section from one capture. A missing route is [SectionState.SKIPPED] with
     * the route reason; a failed capture is [SectionState.UNREADABLE] with the failure; otherwise the
     * section's own text decides present/empty/unreadable. No title is ever dropped.
     */
    internal fun privilegedSections(
        evidence: String,
        skipReason: String,
        failureReason: String?,
        bootEpochMillis: Long? = null
    ): List<CaptureSection> = PRIVILEGED_SECTIONS.map { title ->
        val raw = bounded(section(evidence, title))
        val verdict = when {
            failureReason != null -> SectionVerdict(SectionState.UNREADABLE, failureReason)
            raw.isBlank() && skipReason.isNotBlank() -> SectionVerdict(SectionState.SKIPPED, skipReason)
            else -> verdictFor(raw)
        }
        augmentSection(CaptureSection(title, verdict.state, verdict.reason, raw), bootEpochMillis)
    }

    /** Adds the parsed safe-mode/framework/module lines without inventing anything the capture lacked. */
    private fun augmentSection(
        section: CaptureSection,
        bootEpochMillis: Long?
    ): CaptureSection = when (section.title) {
        "safe mode" -> withSafeMode(section, bootEpochMillis)
        "framework manager" -> withFrameworkManager(section)
        "LSPosed config (enabled / scope)" -> withModuleScope(section)
        else -> section
    }

    private fun withSafeMode(section: CaptureSection, bootEpochMillis: Long?): CaptureSection {
        if (section.state != SectionState.PRESENT) return section
        val report = parseSafeMode(section.text, bootEpochMillis)
        val body = buildString {
            appendLine("safeLogFiles=${report.logFiles}")
            appendLine("safeReadableFiles=${report.readableFiles ?: "unknown"}")
            appendLine("counter=${report.counter ?: if (report.readable) 0 else "unknown"}")
            appendLine("safeMode=${safeModeLabel(report)}")
            report.lines.forEach { appendLine(it) }
            if (report.history.isNotEmpty()) appendLine("historyLines=${report.history.size}")
        }
        return section.copy(text = bounded(body.trimEnd()))
    }

    private fun withFrameworkManager(section: CaptureSection): CaptureSection {
        if (section.state == SectionState.EMPTY) {
            return section.copy(reason = "not installed")
        }
        if (section.state != SectionState.PRESENT) return section
        val installed = section.text.contains("Package [")
        val version = FRAMEWORK_MANAGER_VERSION.find(section.text)?.groupValues?.get(1)
        val body = buildString {
            appendLine(
                "manager=org.lsposed.manager installed=$installed " +
                    "version=${version ?: "not found"}"
            )
            append(section.text)
        }
        return section.copy(text = bounded(body))
    }

    internal data class ModuleScope(
        val queried: Boolean,
        val enabled: Boolean?,
        val scope: String?,
        val row: String?
    )

    /**
     * Reads the LSPosed config query result. `modules_config.db` is a real SQLite file, so the only
     * trustworthy answer comes from an actual query (`-header -separator '|'`). Grepping the binary was
     * what produced "not found" for installed modules; that wording is gone.
     */
    internal fun parseModuleScope(text: String): ModuleScope {
        val lower = text.lowercase()
        val queried = text.contains("sqlite=") && SQLITE_FAILURES.none { lower.contains(it) }
        if (!queried) return ModuleScope(false, null, null, null)
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val rowIndex = lines.indexOfFirst {
            it.contains('|') && it.contains("duostatusbar", ignoreCase = true)
        }
        if (rowIndex < 0) return ModuleScope(true, null, null, null)
        val row = lines[rowIndex]
        val header = lines.subList(0, rowIndex).lastOrNull { it.contains('|') }
            ?: return ModuleScope(true, null, null, row)
        val columns = header.split('|').map { it.trim().lowercase() }
        val values = row.split('|').map { it.trim() }
        val enabled = columns.indexOf("enabled").takeIf { it >= 0 }
            ?.let { values.getOrNull(it) }
            ?.let {
                when (it.lowercase()) {
                    "1", "true", "yes" -> true
                    "0", "false", "no" -> false
                    else -> null
                }
            }
        val scope = columns.indexOf("scope").takeIf { it >= 0 }
            ?.let { values.getOrNull(it) }
            ?.takeIf { it.isNotBlank() }
        return ModuleScope(true, enabled, scope, row)
    }

    internal fun withModuleScope(section: CaptureSection): CaptureSection {
        if (section.state != SectionState.PRESENT && section.state != SectionState.UNREADABLE) {
            return section
        }
        val parsed = parseModuleScope(section.text)
        val body = buildString {
            if (parsed.queried) {
                appendLine("enabled=${parsed.enabled?.toString() ?: "unknown"}")
                appendLine("scope=${parsed.scope ?: "unknown"}")
                if (parsed.row != null) appendLine("moduleRow=${parsed.row}")
                if (parsed.row == null) appendLine("query=no duostatusbar row returned")
            } else {
                appendLine("enabled=unknown scope=unknown (modules_config.db not queried)")
            }
            append(section.text)
        }
        return section.copy(text = bounded(body))
    }
}
