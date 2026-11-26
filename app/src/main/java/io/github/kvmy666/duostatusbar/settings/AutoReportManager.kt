package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.RootLogs
import io.github.kvmy666.duostatusbar.hook.rom.RomMarkerSource
import io.github.kvmy666.duostatusbar.hook.rom.RomProfiles
import io.github.kvmy666.duostatusbar.ui.buildDiagnostics
import io.github.kvmy666.duostatusbar.ui.reportRom
import io.github.kvmy666.duostatusbar.ui.writeReportZip
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Owns the schedule and the real collector/zipper/sender for automatic reports.
 *
 * The schedule follows consent: one immediate unique pulse, then a unique 24-hour periodic pulse, or
 * nothing at all. Turning consent off cancels both unique work names immediately. The collector reads
 * only what the module already wrote; it never adds work to the module's hot path.
 */
internal object AutoReportManager {

    const val FIRST_PULSE = "duo-auto-report-first"
    const val PERIODIC_PULSE = "duo-auto-report-24h"
    const val PERIOD_HOURS = 24L

    /** Schedules or cancels work to match the stored consent. Idempotent; never throws. */
    fun apply(context: Context) {
        val app = context.applicationContext
        try {
            val workManager = WorkManager.getInstance(app)
            if (!ConsentGate.enabled(AutoReportStore.consent(app))) {
                cancel(workManager)
                return
            }
            val constraints = constraints()
            if (!AutoReportStore.firstPulseDone(app)) {
                // KEEP: an app start must not replace a first pulse that is still waiting/running.
                workManager.enqueueUniqueWork(
                    FIRST_PULSE,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<AutoReportWorker>()
                        .setConstraints(constraints)
                        .build()
                )
            }
            // KEEP: app starts must not reset the 24 h cadence. The first run is one full period later,
            // so consent produces exactly one immediate pulse and then the periodic one.
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_PULSE,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AutoReportWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                    .setInitialDelay(PERIOD_HOURS, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()
            )
        } catch (t: Throwable) {
            L.w("auto report schedule: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Stores the dialog/toggle answer and immediately starts or cancels the schedule. */
    fun answerConsent(context: Context, allow: Boolean) {
        val app = context.applicationContext
        AutoReportStore.writeConsent(app, if (allow) ConsentState.YES else ConsentState.NO)
        if (!allow) {
            try {
                cancel(WorkManager.getInstance(app))
            } catch (t: Throwable) {
                L.w("auto report cancel: ${t.javaClass.simpleName}: ${t.message}")
            }
            return
        }
        // A toggle-on asks for a fresh immediate pulse even if one already ran.
        AutoReportStore.markFirstPulsePending(app)
        try {
            WorkManager.getInstance(app).enqueueUniqueWork(
                FIRST_PULSE,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AutoReportWorker>()
                    .setConstraints(constraints())
                    .build()
            )
        } catch (t: Throwable) {
            L.w("auto report first pulse: ${t.javaClass.simpleName}: ${t.message}")
        }
        apply(app)
    }

    /** Runs one pulse synchronously; the worker calls this on its IO dispatcher. */
    fun runPulse(context: Context): PulseResult {
        val app = context.applicationContext
        val identity = identity(app)
        return AutoReportPulse(
            store = AutoReportStore.open(app),
            clock = { System.currentTimeMillis() },
            elapsedClock = { System.nanoTime() / 1_000_000L },
            identity = identity,
            collector = { collect(app) },
            zipper = { zip(app, it) },
            sender = RelaySender(app)
        ).run()
    }

    /** Persists a failure of the reporter itself so the next pulse retries it. */
    fun recordReporterFailure(context: Context, stage: String, error: Throwable) {
        val app = context.applicationContext
        val identity = identity(app)
        val store = AutoReportStore.open(app)
        store.save(
            AutoReportLedger.queueReporterFailure(
                store.load(),
                stage,
                error.javaClass.simpleName,
                identity.version,
                identity.model
            )
        )
    }

    private fun constraints(): Constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private fun cancel(workManager: WorkManager) {
        workManager.cancelUniqueWork(FIRST_PULSE)
        workManager.cancelUniqueWork(PERIODIC_PULSE)
    }

    private fun identity(context: Context): AutoReportIdentity {
        val rom = runCatching {
            reportRom(
                Build.MANUFACTURER.orEmpty(),
                Build.BRAND.orEmpty(),
                Build.PRODUCT.orEmpty(),
                Build.DISPLAY.orEmpty(),
                RomProfiles.load(context),
                Build.VERSION.SDK_INT,
                RomMarkerSource.read(context)
            ).id
        }.getOrDefault("unknown")
        return AutoReportIdentity(
            version = BuildConfig.VERSION_NAME,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            rom = rom
        )
    }

    /**
     * The one collector for manual and automatic reports: the same shared capture, the same diagnostics
     * builder. The automatic path adds no module work and no continuous collection.
     */
    private fun collect(context: Context): AutoReportCollected {
        val captureAttempt = runCatching { RootLogs.collectSections(context) }
        val capture = captureAttempt.getOrNull()
        val captureFailure = captureAttempt.exceptionOrNull()
            ?.let { "${it.javaClass.simpleName}: ${it.message}" }
        val status = DuoPrefs.status(context)
        val dump = DuoPrefs.dump(context)
        val fallback = DuoPrefs.fallback(context)
        val health = runCatching { ModuleHealthCheck.of(context) }.getOrNull()
        val current = DuoPrefs.read(context)
        val portrait = DuoPrefs.read(context, DuoOrientation.PORTRAIT)
        val landscape = DuoPrefs.read(context, DuoOrientation.LANDSCAPE)
        val moduleEnabled = portrait.enabled || landscape.enabled ||
            portrait.iosMode || landscape.iosMode
        val iosActive = portrait.iosMode || landscape.iosMode
        val moduleLog = runCatching { L.recentText() }.getOrDefault("")
        val problems = ProblemDetector.detect(
            ProblemInputs(
                status = status,
                dump = dump,
                fallback = fallback,
                moduleLog = moduleLog,
                moduleState = health?.state,
                moduleEnabled = moduleEnabled,
                iosActive = iosActive,
                capture = capture,
                captureFailure = captureFailure
            )
        )
        val logs = capture?.render().orEmpty()
        val report = buildDiagnostics(
            context = context,
            settings = current,
            status = status,
            history = DuoPrefs.statusHistory(context),
            dump = dump,
            moduleLoadAt = 0L,
            capture = capture,
            captureText = logs,
            captureFailure = captureFailure
        )
        return AutoReportCollected(
            report = report,
            logs = logs,
            status = status,
            dump = dump,
            sections = capture?.sections?.size ?: 0,
            problems = problems
        )
    }

    private fun zip(context: Context, collected: AutoReportCollected): ZipArtifact {
        val file = writeReportZip(
            context,
            collected.report,
            collected.logs,
            collected.status,
            collected.dump
        ) ?: throw IllegalStateException("zip unavailable")
        return ZipArtifact(file, file.length())
    }

    /** Relay-only sender: never the direct bot-token route, never a notification. */
    private class RelaySender(private val context: Context) : AutoReportSender {

        override fun sendZip(artifact: ZipArtifact, caption: String): SendOutcome {
            val file = artifact.file ?: return SendOutcome.Failed("no-file")
            return toOutcome(TelegramLog.sendRelayOnly(file, caption))
        }

        override fun sendSummary(text: String, caption: String): SendOutcome {
            val file = File(context.cacheDir, "duo-auto-summary-${System.currentTimeMillis()}.txt")
            return try {
                file.writeText(text)
                toOutcome(TelegramLog.sendRelayOnly(file, caption))
            } catch (t: Throwable) {
                SendOutcome.Failed(t.javaClass.simpleName)
            } finally {
                runCatching { file.delete() }
            }
        }

        private fun toOutcome(result: TelegramLog.RelaySendResult): SendOutcome =
            if (result.sent) {
                SendOutcome.Sent
            } else {
                SendOutcome.Failed(result.error.ifBlank { "upload" })
            }
    }
}
