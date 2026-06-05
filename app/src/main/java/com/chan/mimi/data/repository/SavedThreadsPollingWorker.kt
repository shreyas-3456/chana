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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chan.mimi.AppLifecycleTracker
import com.chan.mimi.MainActivity
import com.chan.mimi.data.model.PostDto

class SavedThreadsPollingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_BOARD_TAG = "board_tag"
        const val KEY_THREAD_NO = "thread_no"

        private const val CONTENT_CHANGES_CHANNEL_ID = "saved_threads_content_changes"
        private const val CONTENT_CHANGES_NOTIF_BASE_ID = 10000
    }

    override suspend fun doWork(): Result {
        val pollingSettings = PollingSettingsRepository.getInstance(applicationContext)
        val intervalSeconds = pollingSettings.getIntervalSeconds()

        if (intervalSeconds <= 0) {
            Log.d("SavedThreadsWorker", "Polling is disabled. Skipping worker execution.")
            SavedThreadsPollingScheduler.cancelPolling(applicationContext)
            return Result.success()
        }

        val repository = SavedThreadsRepository.getInstance(applicationContext)
        val boardTagInput = inputData.getString(KEY_BOARD_TAG)
        val threadNoInput = inputData.getLong(KEY_THREAD_NO, 0L)
        val detail = repository.savedThreadsFlow.value.find {
            it.boardTag == boardTagInput && it.thread.id == threadNoInput
        }

        if (boardTagInput.isNullOrBlank() || threadNoInput <= 0L) {
            Log.d("SavedThreadsWorker", "Worker missing thread input. Rescheduling enabled threads.")
            SavedThreadsPollingScheduler.schedulePolling(applicationContext, intervalSeconds, replace = false)
            return Result.success()
        }

        if (detail == null || detail.is404 || !detail.pollingEnabled) {
            Log.d("SavedThreadsWorker", "Thread $boardTagInput/$threadNoInput is not enabled for polling. Cancelling its work.")
            SavedThreadsPollingScheduler.cancelPollingForThread(applicationContext, boardTagInput, threadNoInput)
            return Result.success()
        }

        if (AppLifecycleTracker.isAppInForeground) {
            Log.d("SavedThreadsWorker", "App is in foreground. Saved screen owns foreground refresh.")
            SavedThreadsPollingScheduler.schedulePollingForThread(
                applicationContext,
                detail.boardTag,
                detail.thread.id,
                intervalSeconds,
                replace = true
            )
            return Result.success()
        }

        val boardTag = detail.boardTag
        val threadNo = detail.thread.id
        Log.d("SavedThreadsWorker", "Starting background update for $boardTag/$threadNo.")

        ChanRepository.getThread(boardTag, threadNo, forceRefresh = true).fold(
            onSuccess = { apiPosts ->
                val merged = mergePosts(detail.posts, apiPosts)
                val apiPostIds = apiPosts.map { it.id }.toHashSet()
                val savedPostIds = detail.posts.map { it.id }.toHashSet()
                val addedCount = apiPosts.count { it.id !in savedPostIds }
                val deletedCount = detail.posts.count { !it.isDeleted && it.id !in apiPostIds }

                if (addedCount > 0 || deletedCount > 0) {
                    Log.d(
                        "SavedThreadsWorker",
                        "Saved thread $boardTag/$threadNo changed: +$addedCount, -$deletedCount."
                    )
                    showContentChangeNotification(
                        boardTag = boardTag,
                        threadNo = threadNo,
                        title = detail.thread.safeSubject().ifEmpty { threadNo.toString() },
                        addedCount = addedCount,
                        deletedCount = deletedCount
                    )
                }
                repository.updateSavedPosts(boardTag, threadNo, merged)
            },
            onFailure = { throwable ->
                val isHttp404 = throwable is retrofit2.HttpException && throwable.code() == 404
                if (isHttp404) {
                    repository.markAs404(boardTag, threadNo)
                    SavedThreadsPollingScheduler.cancelPollingForThread(applicationContext, boardTag, threadNo)
                    Log.d("SavedThreadsWorker", "Thread $boardTag/$threadNo returned 404; marked as 404.")
                }
            }
        )

        SavedThreadsPollingScheduler.schedulePollingForThread(applicationContext, boardTag, threadNo, intervalSeconds, replace = true)
        return Result.success()
    }

    private fun mergePosts(existing: List<PostDto>, newPosts: List<PostDto>): List<PostDto> {
        if (existing.isEmpty()) return newPosts

        val newById = newPosts.associateBy { it.id }
        val merged = mutableListOf<PostDto>()

        for (local in existing) {
            val fresh = newById[local.id]
            if (fresh != null) {
                merged.add(fresh)
            } else {
                merged.add(local.asDeleted())
            }
        }

        val existingIds = existing.map { it.id }.toHashSet()
        for (post in newPosts) {
            if (post.id !in existingIds) merged.add(post)
        }

        return merged
    }

    private fun createContentChangesChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CONTENT_CHANGES_CHANNEL_ID,
                "Saved Thread Changes",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts you when bookmarked threads add or delete content"
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showContentChangeNotification(
        boardTag: String,
        threadNo: Long,
        title: String,
        addedCount: Int,
        deletedCount: Int
    ) {
        createContentChangesChannel()
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            Log.d("SavedThreadsWorker", "Notifications are disabled; content change notification not shown.")
            return
        }

        val tapIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("board_tag", boardTag)
            putExtra("thread_no", threadNo)
            putExtra("is_saved", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            (boardTag + threadNo).hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val changes = buildList {
            if (addedCount == 1) add("1 new reply")
            if (addedCount > 1) add("$addedCount new replies")
            if (deletedCount == 1) add("something was deleted")
            if (deletedCount > 1) add("$deletedCount things were deleted")
        }
        val notificationTitle = when {
            addedCount > 0 && deletedCount == 0 -> "New reply on /$boardTag/ thread"
            addedCount == 0 && deletedCount > 0 -> "Something was deleted on /$boardTag/"
            else -> "Saved thread changed in /$boardTag/"
        }
        val bodyText = "${changes.joinToString(", ")} - $title"

        val notification = NotificationCompat.Builder(applicationContext, CONTENT_CHANGES_CHANNEL_ID)
            .setContentTitle(notificationTitle)
            .setContentText(bodyText)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notifId = CONTENT_CHANGES_NOTIF_BASE_ID + kotlin.math.abs((boardTag + threadNo).hashCode() % 1000)
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)
    }
}
