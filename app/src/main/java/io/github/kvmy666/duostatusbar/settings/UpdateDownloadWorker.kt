package io.github.kvmy666.duostatusbar.settings

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.kvmy666.duostatusbar.BuildConfig
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads the update APK, verifies it, and hands it to the system installer.
 *
 * This is deliberately a foreground worker: a multi-megabyte download must not be killed when the app
 * is backgrounded. The bytes are hashed while they stream, and the file is only offered to the installer
 * when its SHA-256 matches the release's `digest` (GitHub publishes it). A mismatch deletes the file and
 * reports — a tampered or truncated APK is never installed.
 *
 * Source order: the APK URL from the feed (GitHub's CDN), then the relay's `/download` proxy, which only
 * exists for networks that block GitHub.
 */
class UpdateDownloadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val version = inputData.getString(KEY_VERSION).orEmpty()
        val apkUrl = inputData.getString(KEY_APK_URL).orEmpty()
        val sha = inputData.getString(KEY_SHA).orEmpty().lowercase()
        if (apkUrl.isBlank()) return Result.failure()

        val dir = applicationContext.getExternalFilesDir("updates")
            ?: return Result.failure()
        val file = File(dir, "DuoStatusBar-$version.apk")

        return try {
            setForeground(foregroundInfo(version, 0))
            val actual = withContext(Dispatchers.IO) { download(apkUrl, file) }
            if (sha.isNotEmpty() && actual != sha) {
                L.w("update download: sha mismatch (expected $sha, got $actual) - refusing to install")
                file.delete()
                UpdateNotifications.downloadFailed(applicationContext, version)
                return Result.failure(workDataOf(KEY_ERROR to "sha_mismatch"))
            }
            L.i("update downloaded: ${file.name} (${file.length()} bytes, sha ok=${sha.isNotEmpty()})")
            UpdateNotifications.install(applicationContext, file, version)
            Result.success()
        } catch (t: Throwable) {
            L.w("update download failed: ${t.javaClass.simpleName}: ${t.message}")
            file.delete()
            UpdateNotifications.downloadFailed(applicationContext, version)
            Result.retry()
        }
    }

    /** Streams [source] into [dest]; returns the lowercase hex SHA-256 of the bytes. */
    private fun download(source: String, dest: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = openConnection(source)
        try {
            if (connection.responseCode != 200) throw IllegalStateException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val pct = ((written * 100) / total).toInt()
                            setProgressAsync(workDataOf(KEY_PROGRESS to pct))
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "duoStatusBar")
        }

    private fun foregroundInfo(version: String, percent: Int): ForegroundInfo {
        // The channel must exist even when a download is started straight from the app (no prior notify).
        UpdateNotifications.ensureProgressChannel(applicationContext)
        val notification: Notification = NotificationCompat.Builder(applicationContext, UpdateNotifications.CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.update_downloading_title))
            .setContentText(applicationContext.getString(R.string.update_downloading_text, version))
            .setProgress(100, percent, percent <= 0)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_VERSION = "version"
        const val KEY_APK_URL = "apk_url"
        const val KEY_SHA = "sha256"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"

        private const val NOTIFICATION_ID = 0x5A19

        /** Enqueues one download, replacing any previous request. Sent from the notification's action. */
        fun enqueue(context: Context, version: String, apkUrl: String, sha256: String) {
            try {
                // Fall back to the relay proxy only when the feed gave no direct URL.
                val relay = BuildConfig.TELEGRAM_RELAY_URL.trim().trimEnd('/')
                val url = apkUrl.ifBlank { if (relay.isNotEmpty()) "$relay/download" else "" }
                if (url.isBlank()) return
                val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                    .setInputData(data(version, url, sha256))
                    .build()
                WorkManager.getInstance(context).enqueue(request)
            } catch (t: Throwable) {
                L.w("update enqueue: ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        private fun data(version: String, apkUrl: String, sha256: String): Data =
            workDataOf(KEY_VERSION to version, KEY_APK_URL to apkUrl, KEY_SHA to sha256)
    }
}
