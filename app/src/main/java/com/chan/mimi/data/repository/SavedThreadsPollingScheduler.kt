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
import androidx.work.ExistingWorkPolicy
import androidx.work.workDataOf
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.chan.mimi.MainActivity
import java.util.concurrent.TimeUnit

object SavedThreadsPollingScheduler {
    private const val UNIQUE_WORK_NAME_PREFIX = "SavedThreadsPollingWork"

    private const val STATUS_NOTIF_ID = 8812
    private const val STATUS_CHANNEL_ID = "saved_threads_polling_status"
    private const val STATUS_CHANNEL_NAME = "Saved Threads Polling Status"

    fun schedulePolling(context: Context, intervalSeconds: Int, replace: Boolean = false) {
        val workManager = WorkManager.getInstance(context)
        val enabledThreads = SavedThreadsRepository.getInstance(context)
            .savedThreadsFlow.value
            .filter { !it.is404 && it.pollingEnabled }

        // Storing <= 0 is interpreted as disabled (Off). No enabled saved thread means no work.
        if (intervalSeconds <= 0 || enabledThreads.isEmpty()) {
            cancelPolling(context)
            Log.d("SavedPollingScheduler", "Polling disabled. Cancelled background work.")
            return
        }

        enabledThreads.forEach { detail ->
            schedulePollingForThread(
                context = context,
                boardTag = detail.boardTag,
                threadNo = detail.thread.id,
                intervalSeconds = intervalSeconds,
                replace = replace
            )
        }
        updatePersistentNotification(context)
    }

    fun schedulePollingForThread(
        context: Context,
        boardTag: String,
        threadNo: Long,
        intervalSeconds: Int,
        replace: Boolean = false,
        append: Boolean = false
    ) {
        val workManager = WorkManager.getInstance(context)
        if (intervalSeconds <= 0) {
            cancelPollingForThread(context, boardTag, threadNo)
            updatePersistentNotification(context)
            return
        }

        val workRequest = OneTimeWorkRequestBuilder<SavedThreadsPollingWorker>()
            .setInputData(
                workDataOf(
                    SavedThreadsPollingWorker.KEY_BOARD_TAG to boardTag,
                    SavedThreadsPollingWorker.KEY_THREAD_NO to threadNo
                )
            )
            .setInitialDelay(intervalSeconds.toLong(), TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(boardTag, threadNo),
            when {
                append -> ExistingWorkPolicy.APPEND_OR_REPLACE
                replace -> ExistingWorkPolicy.REPLACE
                else -> ExistingWorkPolicy.KEEP
            },
            workRequest
        )
        Log.d(
            "SavedPollingScheduler",
            "Enqueued work for $boardTag/$threadNo with interval: ${intervalSeconds}s (replace=$replace, append=$append)."
        )
        updatePersistentNotification(context)
    }

    fun cancelPolling(context: Context) {
        val workManager = WorkManager.getInstance(context)
        SavedThreadsRepository.getInstance(context).savedThreadsFlow.value.forEach { detail ->
            workManager.cancelUniqueWork(uniqueWorkName(detail.boardTag, detail.thread.id))
        }
        Log.d("SavedPollingScheduler", "Saved threads polling cancelled.")
        updatePersistentNotification(context)
    }

    fun cancelPollingForThread(context: Context, boardTag: String, threadNo: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(boardTag, threadNo))
        Log.d("SavedPollingScheduler", "Saved thread polling cancelled for $boardTag/$threadNo.")
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
                description = "Shows status of saved threads background polling"
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

    private fun uniqueWorkName(boardTag: String, threadNo: Long): String =
        "${UNIQUE_WORK_NAME_PREFIX}_${boardTag}_$threadNo"
}
