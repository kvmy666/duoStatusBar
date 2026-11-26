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
class AutoReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!ConsentGate.enabled(AutoReportStore.consent(context))) return Result.success()
        return try {
            val result = withContext(Dispatchers.IO) { AutoReportManager.runPulse(context) }
            L.i(result.logLine())
            AutoReportStore.markFirstPulseDone(context)
            Result.success()
        } catch (t: CancellationException) {
            // The OS stopped us; the unique work stays scheduled and the next pulse runs normally.
            throw t
        } catch (t: Throwable) {
            // A failed pulse is not "no problems": persist the reporter failure so the next pulse retries.
            runCatching { AutoReportManager.recordReporterFailure(context, "pulse", t) }
            L.w("auto report pulse failed: ${t.javaClass.simpleName}: ${t.message}")
            AutoReportStore.markFirstPulseDone(context)
            Result.success()
        }
    }
}
