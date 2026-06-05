package com.chan.mimi.ui.screens.threads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chan.mimi.data.api.ChanApiProvider
import com.chan.mimi.data.model.WatchedThread
import com.chan.mimi.data.repository.PollingSettingsRepository
import com.chan.mimi.data.repository.SavedThreadsPollingScheduler
import com.chan.mimi.data.repository.WatchedThreadsPollingScheduler
import com.chan.mimi.data.repository.WatchedThreadsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WatchedThreadsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo            = WatchedThreadsRepository.getInstance(application)
    private val api             = ChanApiProvider.api
    private val pollingSettings = PollingSettingsRepository.getInstance(application)

    /** All watched threads across all boards – observe in UI and filter per board. */
    val allWatchedThreads: StateFlow<List<WatchedThread>> = repo.allThreads

    /** Shared polling interval in seconds (0 = Off). Observed by the SavedScreen controls. */
    val pollingIntervalSeconds: StateFlow<Int> = pollingSettings.intervalSecondsFlow

    init {
        startForegroundPolling()
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun addThread(
        boardTag     : String,
        threadNo     : Long,
        title        : String,
        thumbnailUrl : String,
        postCount    : Int
    ) {
        repo.addOrUpdate(
            WatchedThread(
                boardTag      = boardTag,
                threadNo      = threadNo,
                title         = title,
                thumbnailUrl  = thumbnailUrl,
                lastPostCount = postCount,
                hasNewPosts   = false
            )
        )
    }

    fun removeThread(boardTag: String, threadNo: Long) {
        repo.remove(boardTag, threadNo)
    }

    /** Call when user opens / focuses a watched thread – clears the yellow dot. */
    fun markSeen(boardTag: String, threadNo: Long) {
        repo.setHasNewPosts(boardTag, threadNo, false)
    }

    /**
     * Toggle background WorkManager polling for a specific watched thread.
     * When enabled and the interval is > 0, the background worker will fire push
     * notifications even when the app is force-closed.
     */
    fun togglePolling(boardTag: String, threadNo: Long, enabled: Boolean) {
        repo.setPollingEnabled(getApplication(), boardTag, threadNo, enabled)
    }

    /**
     * Set the shared polling interval (in seconds) for both:
     * - Background WorkManager saved-threads sync
     * - Background WorkManager watched-threads new-post notifications
     *
     * Setting [seconds] to 0 cancels both workers.
     */
    fun setPollingInterval(seconds: Int) {
        pollingSettings.setIntervalSeconds(seconds)

        // Reschedule saved-thread worker
        SavedThreadsPollingScheduler.schedulePolling(getApplication(), seconds, replace = true)

        // Reschedule watched-thread worker only if any thread has polling enabled
        val anyEnabled = allWatchedThreads.value.any { it.pollingEnabled }
        if (anyEnabled && seconds > 0) {
            WatchedThreadsPollingScheduler.schedulePolling(getApplication(), seconds, replace = true)
        } else {
            WatchedThreadsPollingScheduler.cancelPolling(getApplication())
        }
    }

    // ── Foreground polling (while app is open) ─────────────────────────────────
    // This lightweight coroutine loop still handles the in-app yellow-dot indicator.
    // Heavy lifting for background (force-closed / screen-off) is done by WorkManager.

    private fun startForegroundPolling() {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                pollAllWatchedThreads()
            }
        }
    }

    private suspend fun pollAllWatchedThreads() {
        val snapshot = allWatchedThreads.value
        for (watched in snapshot) {
            try {
                val response  = api.getThread(watched.boardTag, watched.threadNo)
                val newCount  = response.posts.size
                val oldCount  = watched.lastPostCount
                if (newCount > oldCount && oldCount > 0) {
                    repo.setHasNewPosts(watched.boardTag, watched.threadNo, true)
                }
                repo.updatePostCount(watched.boardTag, watched.threadNo, newCount)
            } catch (_: Exception) {
                // Silently ignore network errors during background poll
            }
        }
    }
}
