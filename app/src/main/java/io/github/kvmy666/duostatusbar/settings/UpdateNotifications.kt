package io.github.kvmy666.duostatusbar.settings

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.R
import java.io.File

/**
 * The update notifications: "new release" (with a Download action), "downloading", "downloaded — tap to
 * install", and "download failed". Tapping the first opens the release page; the Download action hands
 * the APK to [UpdateDownloadWorker]; the ready notification opens the system installer.
 */
internal object UpdateNotifications {

    /** Alerts pop as heads-up. A channel's importance cannot be raised after creation, hence a new id. */
    internal const val CHANNEL_ALERTS = "duo_updates_alerts"

    /** The download's foreground notification stays quiet (low importance). */
    internal const val CHANNEL_PROGRESS = "duo_updates_progress"

    private const val ID = 0x5A18

    /** Android 13+ only shows a notification if the app was granted POST_NOTIFICATIONS. */
    fun permitted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Posted by the periodic worker when a newer release exists. */
    fun notify(context: Context, info: UpdateInfo) {
        if (!permitted(context)) return
        try {
            ensureAlertsChannel(context)
            val open = PendingIntent.getActivity(
                context,
                ID,
                Intent(Intent.ACTION_VIEW, Uri.parse(info.url)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(context.getString(R.string.update_notification_text, info.version))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
            if (info.apkUrl.isNotBlank()) {
                builder.addAction(
                    android.R.drawable.stat_sys_download,
                    context.getString(R.string.update_action_download),
                    downloadPendingIntent(context, info)
                )
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(ID, builder.build())
        } catch (t: Throwable) {
            // A missing notification must never matter more than the status bar.
            L.w("update notification: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** The Download action: enqueues the worker with everything it needs. */
    private fun downloadPendingIntent(context: Context, info: UpdateInfo): PendingIntent {
        val intent = Intent(context, UpdateDownloadReceiver::class.java).apply {
            action = UpdateDownloadReceiver.ACTION
            putExtra(UpdateDownloadWorker.KEY_VERSION, info.version)
            putExtra(UpdateDownloadWorker.KEY_APK_URL, info.apkUrl)
            putExtra(UpdateDownloadWorker.KEY_SHA, info.sha256)
        }
        return PendingIntent.getBroadcast(
            context,
            ID + 1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Posted once the APK is downloaded and verified; tapping it opens the system installer. */
    fun install(context: Context, file: File, version: String) {
        if (!permitted(context)) return
        try {
            ensureAlertsChannel(context)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pending = PendingIntent.getActivity(
                context,
                ID,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.update_ready_title))
                .setContentText(context.getString(R.string.update_ready_text, version))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(ID, notification)
        } catch (t: Throwable) {
            L.w("update install notification: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Posted when the download or its verification failed. */
    fun downloadFailed(context: Context, version: String) {
        if (!permitted(context)) return
        try {
            ensureAlertsChannel(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.update_failed_title))
                .setContentText(context.getString(R.string.update_failed_text, version))
                .setAutoCancel(true)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(ID, notification)
        } catch (t: Throwable) {
            L.w("update failed notification: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** High importance, so a new release and a ready install pop as heads-up, not only in the shade. */
    internal fun ensureAlertsChannel(context: Context) = channel(
        context, CHANNEL_ALERTS, "Update alerts", NotificationManager.IMPORTANCE_HIGH,
        "Alerts for new releases and downloaded updates."
    )

    /** Low importance: the foreground download should not interrupt. Called by the download worker. */
    internal fun ensureProgressChannel(context: Context) = channel(
        context, CHANNEL_PROGRESS, "Update download", NotificationManager.IMPORTANCE_LOW,
        "Shows progress while an update downloads."
    )

    private fun channel(
        context: Context,
        id: String,
        name: String,
        importance: Int,
        description: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(id) != null) return
        manager.createNotificationChannel(
            NotificationChannel(id, name, importance).apply { this.description = description }
        )
    }
}
