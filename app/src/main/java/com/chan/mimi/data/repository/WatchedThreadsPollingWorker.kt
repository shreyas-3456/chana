package com.chan.mimi.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chan.mimi.AppLifecycleTracker
import com.chan.mimi.MainActivity
import com.chan.mimi.data.api.ChanApiProvider

class WatchedThreadsPollingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val CHANNEL_ID = "watched_threads_new_posts"
        private const val CHANNEL_NAME = "Watched Thread Alerts"
        private const val NOTIF_BASE_ID = 9000
    }

    override suspend fun doWork(): Result {
        val pollingSettings = PollingSettingsRepository.getInstance(applicationContext)
        val intervalSeconds = pollingSettings.getIntervalSeconds()

        if (intervalSeconds <= 0) {
            Log.d("WatchedThreadsWorker", "Polling disabled. Exiting.")
            return Result.success()
        }

        val repo = WatchedThreadsRepository.getInstance(applicationContext)
        val enabledThreads = repo.allThreads.value.filter { it.pollingEnabled }

        if (enabledThreads.isEmpty()) {
            Log.d("WatchedThreadsWorker", "No threads with polling enabled. Cancelling work.")
            WatchedThreadsPollingScheduler.cancelPolling(applicationContext)
            return Result.success()
        }

        if (AppLifecycleTracker.isAppInForeground) {
            Log.d("WatchedThreadsWorker", "App is in foreground; skipping background poll.")
            WatchedThreadsPollingScheduler.schedulePolling(applicationContext, intervalSeconds, replace = true)
            return Result.success()
        }

        createNotificationChannel()

        val api = ChanApiProvider.api
        for (watched in enabledThreads) {
            try {
                val response = api.getThread(watched.boardTag, watched.threadNo)
                val newCount = response.posts.size
                val oldCount = watched.lastPostCount

                if (newCount > oldCount && oldCount > 0) {
                    val newPostCount = newCount - oldCount
                    Log.d("WatchedThreadsWorker", "Thread ${watched.boardTag}/${watched.threadNo}: $newPostCount new posts")

                    repo.setHasNewPosts(watched.boardTag, watched.threadNo, true)
                    showNewPostsNotification(watched.boardTag, watched.threadNo, watched.title, newPostCount)
                }

                repo.updatePostCount(watched.boardTag, watched.threadNo, newCount)
            } catch (e: Exception) {
                Log.w("WatchedThreadsWorker", "Failed to poll ${watched.boardTag}/${watched.threadNo}: ${e.message}")
            }
        }

        WatchedThreadsPollingScheduler.schedulePolling(applicationContext, intervalSeconds, replace = true)
        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts you when watched threads receive new replies"
                enableLights(true)
                enableVibration(true)
            }
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun showNewPostsNotification(
        boardTag: String,
        threadNo: Long,
        title: String,
        newPostCount: Int
    ) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val tapIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("board_tag", boardTag)
            putExtra("thread_no", threadNo)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            (boardTag + threadNo).hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = if (newPostCount == 1) {
            "1 new reply on /$boardTag/ - $title"
        } else {
            "$newPostCount new replies on /$boardTag/ - $title"
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("New posts in /$boardTag/")
            .setContentText(bodyText)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notifId = NOTIF_BASE_ID + kotlin.math.abs((boardTag + threadNo).hashCode() % 1000)
        nm.notify(notifId, notification)
    }
}
