package com.chan.mimi.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.chan.mimi.data.model.WatchedThread
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WatchedThreadsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("watched_threads", Context.MODE_PRIVATE)
    private val gson = Gson()

    // All watched threads across all boards, in-memory
    private val _allThreads = MutableStateFlow<List<WatchedThread>>(emptyList())
    val allThreads: StateFlow<List<WatchedThread>> = _allThreads.asStateFlow()

    init {
        loadAll()
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    private fun loadAll() {
        val all = mutableListOf<WatchedThread>()
        prefs.all.keys.filter { it.startsWith("board_") }.forEach { key ->
            all += loadBoard(key.removePrefix("board_"))
        }
        _allThreads.value = all.sortedBy { it.addedAt }
    }

    private fun loadBoard(boardTag: String): List<WatchedThread> {
        val json = prefs.getString("board_$boardTag", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<WatchedThread>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getThreadsForBoard(boardTag: String): List<WatchedThread> =
        _allThreads.value.filter { it.boardTag == boardTag }

    // ── Write ─────────────────────────────────────────────────────────────────

    fun addOrUpdate(thread: WatchedThread) {
        val boardThreads = loadBoard(thread.boardTag).toMutableList()
        val idx = boardThreads.indexOfFirst { it.threadNo == thread.threadNo }
        if (idx == -1) {
            boardThreads.add(thread)
        } else {
            // Preserve hasNewPosts and addedAt when updating
            boardThreads[idx] = thread.copy(
                hasNewPosts = boardThreads[idx].hasNewPosts,
                addedAt     = boardThreads[idx].addedAt
            )
        }
        persistBoard(thread.boardTag, boardThreads)
    }

    fun remove(boardTag: String, threadNo: Long) {
        val boardThreads = loadBoard(boardTag).filter { it.threadNo != threadNo }
        persistBoard(boardTag, boardThreads)
    }

    fun setHasNewPosts(boardTag: String, threadNo: Long, hasNew: Boolean) {
        val boardThreads = loadBoard(boardTag).toMutableList()
        val idx = boardThreads.indexOfFirst { it.threadNo == threadNo }
        if (idx != -1) {
            boardThreads[idx] = boardThreads[idx].copy(hasNewPosts = hasNew)
            persistBoard(boardTag, boardThreads)
        }
    }

    /**
     * Enable or disable WorkManager background polling for a specific watched thread.
     * If at least one thread has [pollingEnabled]=true the background worker is scheduled;
     * if none do, the work is cancelled.
     */
    fun setPollingEnabled(context: Context, boardTag: String, threadNo: Long, enabled: Boolean) {
        val boardThreads = loadBoard(boardTag).toMutableList()
        val idx = boardThreads.indexOfFirst { it.threadNo == threadNo }
        if (idx != -1) {
            boardThreads[idx] = boardThreads[idx].copy(pollingEnabled = enabled)
            persistBoard(boardTag, boardThreads)
        }

        // Reschedule or cancel the background worker based on whether any thread has polling on
        val anyEnabled = _allThreads.value.any { it.pollingEnabled }
        val intervalSeconds = PollingSettingsRepository.getInstance(context).getIntervalSeconds()
        if (anyEnabled && intervalSeconds > 0) {
            WatchedThreadsPollingScheduler.schedulePolling(context, intervalSeconds, replace = true)
        } else {
            WatchedThreadsPollingScheduler.cancelPolling(context)
        }
    }


    fun updatePostCount(boardTag: String, threadNo: Long, count: Int) {
        val boardThreads = loadBoard(boardTag).toMutableList()
        val idx = boardThreads.indexOfFirst { it.threadNo == threadNo }
        if (idx != -1) {
            boardThreads[idx] = boardThreads[idx].copy(lastPostCount = count)
            persistBoard(boardTag, boardThreads)
        }
    }

    private fun persistBoard(boardTag: String, threads: List<WatchedThread>) {
        prefs.edit().putString("board_$boardTag", gson.toJson(threads)).apply()
        loadAll()
    }

    // ── Singleton ─────────────────────────────────────────────────────────────

    companion object {
        @Volatile private var INSTANCE: WatchedThreadsRepository? = null

        fun getInstance(context: Context): WatchedThreadsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WatchedThreadsRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
