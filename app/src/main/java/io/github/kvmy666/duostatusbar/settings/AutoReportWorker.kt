package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.kvmy666.duostatusbar.L
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One automatic-report pulse. It reads only what the module/app already wrote, sends nothing without
 * consent, and treats a reporter failure as a problem to retry on the next pulse rather than losing it.
 */
internal enum class PulseAttempt { SUCCESS, RETRY }

/**
 * The worker's result rule, kept pure so it is unit-tested without WorkManager.
 *
 * A failed pulse — whether it throws or returns [PulseAction.FAILED] — must be retried through
 * backoff and must **not** mark the first pulse done: doing so pushed the retry to the 24 h periodic
 * run. Persistence is itself guarded; if it throws, the nested failure is logged and the pulse still
 * retries.
 */
internal fun finishPulse(
    failure: Throwable?,
    markFirstPulseDone: () -> Unit,
    persistFailure: (Throwable) -> Unit,
    log: (String) -> Unit
): PulseAttempt {
    if (failure == null) {
        markFirstPulseDone()
        return PulseAttempt.SUCCESS
    }
    log("auto report pulse failed: ${failure.javaClass.simpleName}: ${failure.message}")
    try {
        persistFailure(failure)
    } catch (nested: Throwable) {
        log(
            "auto report pulse failure not persisted: " +
                "${nested.javaClass.simpleName}: ${nested.message}"
        )
    }
    return PulseAttempt.RETRY
}

/** A returned [PulseAction.FAILED] retries like a thrown failure; any other action succeeded. */
internal fun pulseOutcome(result: PulseResult): PulseAttempt =
    if (result.action == PulseAction.FAILED) PulseAttempt.RETRY else PulseAttempt.SUCCESS

class AutoReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!ConsentGate.enabled(AutoReportStore.consent(context))) return Result.success()
        return try {
            val result = withContext(Dispatchers.IO) { AutoReportManager.runPulse(context) }
            L.i(result.logLine())
            if (pulseOutcome(result) == PulseAttempt.RETRY) {
                L.w("auto report pulse failed: ${result.detail}")
                // The pulse already persisted its own collect/zip/upload failure.
                return Result.retry()
            }
            finishPulse(
                failure = null,
                markFirstPulseDone = { AutoReportStore.markFirstPulseDone(context) },
                persistFailure = { AutoReportManager.recordReporterFailure(context, "pulse", it) },
                log = { L.w(it) }
            )
            Result.success()
        } catch (t: CancellationException) {
            // The OS stopped us; the unique work stays scheduled and the next pulse runs normally.
            throw t
        } catch (t: Throwable) {
            finishPulse(
                failure = t,
                markFirstPulseDone = { AutoReportStore.markFirstPulseDone(context) },
                persistFailure = { AutoReportManager.recordReporterFailure(context, "pulse", it) },
                log = { L.w(it) }
            )
            // Backoff retries the pulse; the first-pulse stamp stays pending, so it is not the 24 h run.
            Result.retry()
        }
    }
}
