package com.chan.mimi.ui.screens.saved

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chan.mimi.data.model.PostDto
import com.chan.mimi.data.repository.ChanRepository
import com.chan.mimi.data.repository.SavedThreadDetail
import com.chan.mimi.data.repository.SavedThreadsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class SavedThreadDetailUiState {
    object Loading : SavedThreadDetailUiState()
    data class Success(val detail: SavedThreadDetail) : SavedThreadDetailUiState()
    data class Error(val message: String) : SavedThreadDetailUiState()
}

class SavedThreadDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val savedRepository = SavedThreadsRepository.getInstance(application)
    private val liveRepository  = ChanRepository

    private val _uiState = MutableStateFlow<SavedThreadDetailUiState>(SavedThreadDetailUiState.Loading)
    val uiState: StateFlow<SavedThreadDetailUiState> = _uiState.asStateFlow()

    private val _isRefreshing   = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _pollCountdown  = MutableStateFlow(30)
    val pollCountdown: StateFlow<Int> = _pollCountdown.asStateFlow()

    private val _is404          = MutableStateFlow(false)
    val is404: StateFlow<Boolean> = _is404.asStateFlow()

    private var pollingJob: Job? = null

    /** In-memory merged post list — never loses posts, deleted ones become isDeleted=true. */
    private var localPosts: List<PostDto> = emptyList()

    private var currentBoard    = ""
    private var currentThreadNo = 0L

    // ── Public API ───────────────────────────────────────────────────────────

    fun startPolling(boardTag: String, threadNo: Long) {
        if (pollingJob?.isActive == true &&
            currentBoard == boardTag &&
            currentThreadNo == threadNo) return

        currentBoard    = boardTag
        currentThreadNo = threadNo
        pollingJob?.cancel()

        // Load saved data immediately so screen has something to show
        loadFromDisk(boardTag, threadNo)

        if (_is404.value) {
            _pollCountdown.value = 0
            return
        }

        pollingJob = viewModelScope.launch {
            // First fetch right away
            fetchLive(boardTag, threadNo)
            if (_is404.value) return@launch

            // Then poll every 30s
            while (isActive) {
                for (secondsLeft in 29 downTo 0) {
                    _pollCountdown.value = secondsLeft
                    delay(1_000L)
                    if (!isActive) return@launch
                }
                _pollCountdown.value = 30
                fetchLive(boardTag, threadNo)
                if (_is404.value) return@launch
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
    }

    fun reloadNow() {
        viewModelScope.launch {
            _isRefreshing.value = true
            liveRepository.invalidateThread(currentBoard, currentThreadNo)
            fetchLive(currentBoard, currentThreadNo)
            _isRefreshing.value = false
            // Restart to reset the countdown
            startPolling(currentBoard, currentThreadNo)
        }
    }

    fun unsaveThread(boardTag: String, threadNo: Long) {
        viewModelScope.launch {
            savedRepository.deleteThread(boardTag, threadNo)
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private fun loadFromDisk(boardTag: String, threadNo: Long) {
        val saved = savedRepository.savedThreadsFlow.value
            .find { it.boardTag == boardTag && it.thread.id == threadNo }
        if (saved != null) {
            localPosts     = saved.posts
            _is404.value   = saved.is404
            _uiState.value = SavedThreadDetailUiState.Success(saved)
        } else {
            _uiState.value = SavedThreadDetailUiState.Error("Saved thread not found on device.")
        }
    }

    private suspend fun fetchLive(boardTag: String, threadNo: Long) {
        val result = liveRepository.getThread(boardTag, threadNo, forceRefresh = true)

        result.fold(
            onSuccess = { apiPosts ->
                _is404.value  = false
                val merged    = mergePosts(localPosts, apiPosts)
                localPosts    = merged

                // Persist the merged post list back to disk so the save stays fresh
                persistMerge(boardTag, threadNo, merged)

                val current = _uiState.value
                if (current is SavedThreadDetailUiState.Success) {
                    _uiState.value = SavedThreadDetailUiState.Success(
                        current.detail.copy(posts = merged)
                    )
                } else {
                    // Recover: build a SavedThreadDetail from disk + merged posts
                    val saved = savedRepository.savedThreadsFlow.value
                        .find { it.boardTag == boardTag && it.thread.id == threadNo }
                    if (saved != null) {
                        _uiState.value = SavedThreadDetailUiState.Success(saved.copy(posts = merged))
                    }
                }
            },
            onFailure = { throwable ->
                if (throwable is kotlinx.coroutines.CancellationException) return@fold

                val isHttp404 = throwable is retrofit2.HttpException && throwable.code() == 404
                if (isHttp404) {
                    _is404.value = true
                    Log.d("SavedDetailVM", "Thread $boardTag/$threadNo returned 404 — keeping saved data")
                    savedRepository.markAs404(boardTag, threadNo)
                    stopPolling()
                }
                // Never wipe existing data on a network failure
            }
        )
    }

    /**
     * Merge [newPosts] from the live API into [existing] saved posts:
     * - Posts missing from API → kept as isDeleted = true  (never deleted from save)
     * - Posts present in API  → updated (isDeleted = false)
     * - Brand-new posts       → appended at the end
     */
    private fun mergePosts(existing: List<PostDto>, newPosts: List<PostDto>): List<PostDto> {
        if (existing.isEmpty()) return newPosts

        val newById = newPosts.associateBy { it.id }
        val merged  = mutableListOf<PostDto>()

        for (local in existing) {
            val fresh = newById[local.id]
            if (fresh != null) {
                merged.add(fresh)           // updated, still live
            } else {
                merged.add(local.asDeleted()) // gone from API → mark deleted, keep in save
            }
        }

        val existingIds = existing.map { it.id }.toHashSet()
        for (post in newPosts) {
            if (post.id !in existingIds) merged.add(post) // genuinely new
        }

        return merged
    }

    /**
     * Write the merged post list back to the saved repository so the offline
     * cache always reflects the freshest known state (including deleted stubs).
     */
    private suspend fun persistMerge(boardTag: String, threadNo: Long, mergedPosts: List<PostDto>) {
        try {
            savedRepository.updateSavedPosts(boardTag, threadNo, mergedPosts)
        } catch (e: Exception) {
            Log.w("SavedDetailVM", "Could not persist merged posts: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
