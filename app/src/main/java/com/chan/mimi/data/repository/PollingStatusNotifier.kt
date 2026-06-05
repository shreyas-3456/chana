package com.chan.mimi.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chan.mimi.MainActivity

object PollingStatusNotifier {
    private const val STATUS_NOTIF_ID = 8812
    private const val STATUS_CHANNEL_ID = "mimi_polling_status"
    private const val STATUS_CHANNEL_NAME = "Mimi Polling Status"

    private const val OLD_SAVED_STATUS_NOTIF_ID = 8812
    private const val OLD_WATCHED_STATUS_NOTIF_ID = 8814
    private const val OLD_WORKER_SYNC_NOTIF_ID = 8813
    private const val OLD_WATCHED_WORKER_SYNC_NOTIF_ID = 9001

    fun update(context: Context) {
        val pollingSettings = PollingSettingsRepository.getInstance(context)
        val intervalSeconds = pollingSettings.getIntervalSeconds()
        val savedCount = SavedThreadsRepository.getInstance(context)
            .savedThreadsFlow.value
            .count { !it.is404 && it.pollingEnabled }
        val watchedCount = WatchedThreadsRepository.getInstance(context)
            .allThreads.value
            .count { it.pollingEnabled }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(OLD_WATCHED_STATUS_NOTIF_ID)
        nm.cancel(OLD_WORKER_SYNC_NOTIF_ID)
        nm.cancel(OLD_WATCHED_WORKER_SYNC_NOTIF_ID)

        if (intervalSeconds <= 0 || (savedCount == 0 && watchedCount == 0)) {
            nm.cancel(STATUS_NOTIF_ID)
            Log.d("PollingStatusNotifier", "Cancelled persistent polling notification.")
            return
        }

        createStatusChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.d("PollingStatusNotifier", "Notifications are disabled; status notification not shown.")
            return
        }

        val intervalStr = formatInterval(intervalSeconds)
        val parts = buildList {
            if (savedCount == 1) add("1 saved thread")
            if (savedCount > 1) add("$savedCount saved threads")
            if (watchedCount == 1) add("1 watched thread")
            if (watchedCount > 1) add("$watchedCount watched threads")
        }
        val text = "Polling ${parts.joinToString(" and ")} every $intervalStr"

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            STATUS_NOTIF_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
            .setContentTitle("Mimi Polling Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        nm.notify(STATUS_NOTIF_ID, notification)
        Log.d("PollingStatusNotifier", "Updated persistent polling notification: $text")
    }

    fun cancel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(STATUS_NOTIF_ID)
        nm.cancel(OLD_SAVED_STATUS_NOTIF_ID)
        nm.cancel(OLD_WATCHED_STATUS_NOTIF_ID)
        nm.cancel(OLD_WORKER_SYNC_NOTIF_ID)
        nm.cancel(OLD_WATCHED_WORKER_SYNC_NOTIF_ID)
    }

    private fun createStatusChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                STATUS_CHANNEL_ID,
                STATUS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of Mimi background polling"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun formatInterval(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return when {
            m > 0 && s > 0 -> "${m}m ${s}s"
            m > 0 -> "${m}m"
            else -> "${seconds}s"
        }
    }
}
