package com.chan.mimi.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.chan.mimi.MainActivity
import java.util.concurrent.TimeUnit

object WatchedThreadsPollingScheduler {
    private const val UNIQUE_WORK_NAME = "WatchedThreadsPollingWork"
    
    private const val STATUS_NOTIF_ID = 8814
    private const val STATUS_CHANNEL_ID = "watched_threads_polling_status"
    private const val STATUS_CHANNEL_NAME = "Watched Threads Polling Status"

    fun schedulePolling(context: Context, intervalSeconds: Int, replace: Boolean = false) {
        val workManager = WorkManager.getInstance(context)

        if (intervalSeconds <= 0) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.d("WatchedPollingScheduler", "Polling disabled. Cancelled background work.")
            updatePersistentNotification(context)
            return
        }

        val workRequest = OneTimeWorkRequestBuilder<WatchedThreadsPollingWorker>()
            .setInitialDelay(intervalSeconds.toLong(), TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            workRequest
        )
        Log.d("WatchedPollingScheduler", "Enqueued work with interval: ${intervalSeconds}s (replace=$replace).")
        updatePersistentNotification(context)
    }

    fun cancelPolling(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        Log.d("WatchedPollingScheduler", "Watched threads polling cancelled.")
        updatePersistentNotification(context)
    }

    fun updatePersistentNotification(context: Context) {
        PollingStatusNotifier.update(context)
    }

    private fun createStatusChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                STATUS_CHANNEL_ID,
                STATUS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of watched threads background polling"
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
