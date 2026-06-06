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
import com.chan.mimi.MainActivity
import com.chan.mimi.data.model.PostDto

class SavedThreadsPollingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_BOARD_TAG = "board_tag"
        const val KEY_THREAD_NO = "thread_no"
        const val EXTRA_IS_SAVED = "is_saved"
        const val EXTRA_HIGHLIGHT_POST_ID = "highlight_post_id"
        const val EXTRA_ADDED_POST_IDS = "added_post_ids"
        const val EXTRA_DELETED_POST_IDS = "deleted_post_ids"

        private const val CONTENT_CHANGES_CHANNEL_ID = "saved_threads_content_changes"
        private const val THREAD_STATUS_CHANNEL_ID = "saved_threads_status"
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

        val boardTag = detail.boardTag
        val threadNo = detail.thread.id
        Log.d("SavedThreadsWorker", "Starting background update for $boardTag/$threadNo.")
        var shouldReschedule = true

        ChanRepository.getThread(boardTag, threadNo, forceRefresh = true).fold(
            onSuccess = { apiPosts ->
                val merged = mergePosts(detail.posts, apiPosts)
                val apiPostIds = apiPosts.map { it.id }.toHashSet()
                val savedPostIds = detail.posts.map { it.id }.toHashSet()
                val addedPostIds = apiPosts.filter { it.id !in savedPostIds }.map { it.id }
                val deletedPostIds = detail.posts
                    .filter { !it.isDeleted && it.id !in apiPostIds }
                    .map { it.id }
                val addedCount = addedPostIds.size
                val deletedCount = deletedPostIds.size
                val changedPostIds = (addedPostIds + deletedPostIds).toHashSet()
                val highlightPostId = merged.firstOrNull { it.id in changedPostIds }?.id

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
                        deletedCount = deletedCount,
                        highlightPostId = highlightPostId,
                        addedPostIds = addedPostIds,
                        deletedPostIds = deletedPostIds
                    )
                }
                repository.updateSavedPosts(boardTag, threadNo, merged)
            },
            onFailure = { throwable ->
                val isHttp404 = throwable is retrofit2.HttpException && throwable.code() == 404
                if (isHttp404) {
                    repository.markAs404(boardTag, threadNo)
                    SavedThreadsPollingScheduler.cancelPollingForThread(applicationContext, boardTag, threadNo)
                    showThread404Notification(
                        boardTag = boardTag,
                        threadNo = threadNo,
                        title = detail.thread.safeSubject().ifEmpty { threadNo.toString() }
                    )
                    shouldReschedule = false
                    Log.d("SavedThreadsWorker", "Thread $boardTag/$threadNo returned 404; marked as 404.")
                }
            }
        )

        if (shouldReschedule) {
            SavedThreadsPollingScheduler.schedulePollingForThread(
                applicationContext,
                boardTag,
                threadNo,
                intervalSeconds,
                append = true
            )
        }
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

    private fun createThreadStatusChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                THREAD_STATUS_CHANNEL_ID,
                "Saved Thread Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts you when a saved thread is archived or deleted"
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
        deletedCount: Int,
        highlightPostId: Long?,
        addedPostIds: List<Long>,
        deletedPostIds: List<Long>
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
            putExtra(EXTRA_IS_SAVED, true)
            highlightPostId?.let { putExtra(EXTRA_HIGHLIGHT_POST_ID, it) }
            putExtra(EXTRA_ADDED_POST_IDS, addedPostIds.joinToString(","))
            putExtra(EXTRA_DELETED_POST_IDS, deletedPostIds.joinToString(","))
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

        // Use a fresh ID so each detected change posts its own alert instead of replacing the last one.
        val notifId = kotlin.math.abs((boardTag + threadNo + "_" + System.currentTimeMillis()).hashCode())
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)
    }

    private fun showThread404Notification(
        boardTag: String,
        threadNo: Long,
        title: String
    ) {
        createThreadStatusChannel()
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            Log.d("SavedThreadsWorker", "Notifications are disabled; 404 notification not shown.")
            return
        }

        val tapIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(KEY_BOARD_TAG, boardTag)
            putExtra(KEY_THREAD_NO, threadNo)
            putExtra(EXTRA_IS_SAVED, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            ("404_" + boardTag + threadNo).hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, THREAD_STATUS_CHANNEL_ID)
            .setContentTitle("Saved thread is 404 on /$boardTag/")
            .setContentText("Thread archived or deleted - $title")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notifId = kotlin.math.abs(("404_" + boardTag + threadNo).hashCode())
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)
    }
}
