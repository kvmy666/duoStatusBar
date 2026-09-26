package io.github.kvmy666.duostatusbar.settings

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.kvmy666.duostatusbar.L
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The background half of the update toggle: every few hours it asks GitHub whether a newer release
 * exists and, if so and it has not been announced yet, posts a notification that opens the release.
 *
 * There is no push service, so this is a periodic poll (Android's floor for periodic work is 15 min;
 * six hours is plenty for a status-bar module). WorkManager keeps it across reboots once enqueued.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!DuoPrefs.checkUpdates(context)) return Result.success()
        val info = withContext(Dispatchers.IO) { UpdateChecker.updateAvailable() } ?: return Result.success()
        // Only announce each release once; a re-check for the same version stays quiet.
        if (DuoPrefs.updateNotified(context) == info.version) return Result.success()
        UpdateNotifications.notify(context, info)
        DuoPrefs.writeUpdateNotified(context, info.version)
        return Result.success()
    }

    companion object {
        private const val NAME = "duo-update-check"
        private const val INTERVAL_HOURS = 6L

        /** Schedules the periodic check, or cancels it, to match the user's toggle. Never throws. */
        fun apply(context: Context) {
            try {
                val workManager = WorkManager.getInstance(context)
                if (!DuoPrefs.checkUpdates(context)) {
                    workManager.cancelUniqueWork(NAME)
                    return
                }
                val request = PeriodicWorkRequestBuilder<UpdateWorker>(INTERVAL_HOURS, TimeUnit.HOURS).build()
                workManager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
            } catch (t: Throwable) {
                L.w("update worker schedule: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }
}
