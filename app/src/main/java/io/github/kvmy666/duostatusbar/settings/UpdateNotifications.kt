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
import io.github.kvmy666.duostatusbar.L
import io.github.kvmy666.duostatusbar.R

/**
 * Posts the "new release available" notification. Tapping it opens the release page on GitHub.
 *
 * The notification is the background half of the update toggle: the worker finds a newer release and
 * this puts it where the user will see it, without any server of ours.
 */
internal object UpdateNotifications {

    private const val CHANNEL = "duo_updates"
    private const val ID = 0x5A18

    /** Android 13+ only shows a notification if the app was granted POST_NOTIFICATIONS. */
    fun permitted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notify(context: Context, info: UpdateInfo) {
        if (!permitted(context)) return
        try {
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context,
                ID,
                Intent(Intent.ACTION_VIEW, Uri.parse(info.url)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(context.getString(R.string.update_notification_text, info.version))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(ID, notification)
        } catch (t: Throwable) {
            // A missing notification must never matter more than the status bar.
            L.w("update notification: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A new Duo Status Bar release is available"
            }
        )
    }
}
