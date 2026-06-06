package com.chan.mimi.ui.screens.threads

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chan.mimi.data.model.PostDto
import com.chan.mimi.data.model.ThreadDto
import com.chan.mimi.data.repository.ChanRepository
import com.chan.mimi.data.repository.SavedThreadsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class ThreadDetailUiState {
    object Loading                               : ThreadDetailUiState()
    data class Success(val posts: List<PostDto>) : ThreadDetailUiState()
    data class Error(val message: String)        : ThreadDetailUiState()
}

class ThreadDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChanRepository
    private val savedThreadsRepository = SavedThreadsRepository.getInstance(application)

    private val _uiState     = MutableStateFlow<ThreadDetailUiState>(ThreadDetailUiState.Loading)
    val uiState: StateFlow<ThreadDetailUiState> = _uiState

    private val _isSaved     = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved

    private val _isSaving    = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _hasNewPosts = MutableStateFlow(false)
    val hasNewPosts: StateFlow<Boolean> = _hasNewPosts

    private val _pollCountdown = MutableStateFlow(30)
    val pollCountdown: StateFlow<Int> = _pollCountdown

    private val _is404 = MutableStateFlow(false)
    val is404: StateFlow<Boolean> = _is404

    private var lastPostCount   = 0
    private var pollingJob: Job? = null
    private var currentBoard    = ""
    private var currentThreadNo = 0L

    /**
     * The locally-merged post list. Deleted posts are preserved here with isDeleted = true
     * so they remain visible in the UI even after the API stops returning them.
     */
    private var localPosts: List<PostDto> = emptyList()

    fun initializeWithCache(board: String, threadNo: Long) {
        if (currentBoard == board && currentThreadNo == threadNo) return
        currentBoard = board
        currentThreadNo = threadNo
        lastPostCount = 0
        _hasNewPosts.value = false
        _is404.value = false

        val cached = repository.getCachedThread(board, threadNo)
        if (cached != null) {
            localPosts         = cached
            _uiState.value     = ThreadDetailUiState.Success(cached)
        } else {
            localPosts         = emptyList()
            _uiState.value     = ThreadDetailUiState.Loading
        }
    }

    fun startPolling(board: String, threadNo: Long, forceRestart: Boolean = false) {
        // Already polling this exact thread — don't restart
        if (!forceRestart && currentBoard == board && currentThreadNo == threadNo && pollingJob?.isActive == true) return

        val switchingThread = currentBoard != board || currentThreadNo != threadNo
        currentBoard    = board
        currentThreadNo = threadNo
        pollingJob?.cancel()

        if (switchingThread) {
            lastPostCount      = 0
            _hasNewPosts.value = false
            _is404.value       = false

            // Check the shared singleton cache synchronously first.
            // If we already have data, show it immediately — no Loading flash,
            // no blank frame during the slide animation.
            val cached = repository.getCachedThread(board, threadNo)
            if (cached != null) {
                localPosts         = cached
                _uiState.value     = ThreadDetailUiState.Success(cached)
            } else {
                localPosts         = emptyList()
                _uiState.value     = ThreadDetailUiState.Loading
            }
        }

        // Check if saved
        viewModelScope.launch {
            _isSaved.value = savedThreadsRepository.isThreadSaved(board, threadNo)
        }

        pollingJob = viewModelScope.launch {
            // First load — cache-first, shows immediately if cached
            if (!forceRestart) {
                fetchThread(board, threadNo, forceRefresh = false, applyImmediately = true)
            }

            // Poll every 30s, ticking the countdown each second
            while (isActive) {
                for (secondsLeft in 29 downTo 0) {
                    _pollCountdown.value = secondsLeft
                    delay(1_000L)
                    if (!isActive) return@launch
                }
                _pollCountdown.value = 30
                fetchThread(board, threadNo, forceRefresh = true, applyImmediately = false)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
    }

    /** Manual reload triggered by the refresh button — always applies immediately. */
    fun reloadNow() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.invalidateThread(currentBoard, currentThreadNo)
            fetchThread(currentBoard, currentThreadNo, forceRefresh = true, applyImmediately = true)
            _isRefreshing.value = false
            // Restart polling to reset the 30s countdown timer
            startPolling(currentBoard, currentThreadNo, forceRestart = true)
        }
    }

    fun applyNewPosts(board: String, threadNo: Long) {
        _hasNewPosts.value = false
        repository.invalidateThread(board, threadNo)
        viewModelScope.launch {
            fetchThread(board, threadNo, forceRefresh = true, applyImmediately = true)
        }
    }

    /**
     * Merge [newPosts] from API with [localPosts]:
     * - Posts in localPosts missing from newPosts → preserved as isDeleted = true
     * - Posts present in newPosts → kept/updated with isDeleted = false
     * - New posts (not yet in localPosts) → appended at the end
     * The result keeps the original ordering with deleted stubs in place.
     */
    private fun mergePosts(localPosts: List<PostDto>, newPosts: List<PostDto>): List<PostDto> {
        if (localPosts.isEmpty()) return newPosts

        val newById = newPosts.associateBy { it.id }
        val merged  = mutableListOf<PostDto>()

        // Walk existing local list in order, marking anything missing as deleted
        for (local in localPosts) {
            val fresh = newById[local.id]
            if (fresh != null) {
                merged.add(fresh) // updated from API, isDeleted = false
            } else {
                merged.add(local.asDeleted()) // dropped by API → mark deleted
            }
        }

        // Append genuinely new posts (IDs not seen in local list)
        val localIds = localPosts.map { it.id }.toHashSet()
        for (post in newPosts) {
            if (post.id !in localIds) merged.add(post)
        }

        return merged
    }

    private suspend fun fetchThread(
        board: String,
        threadNo: Long,
        forceRefresh: Boolean,
        applyImmediately: Boolean
    ) {
        val result = repository.getThread(board, threadNo, forceRefresh)

        result.fold(
            onSuccess = { apiPosts ->
                _is404.value = false
                val merged   = mergePosts(localPosts, apiPosts)
                val newCount = apiPosts.size  // count only live posts, not deleted stubs
                when {
                    // First load or user tapped banner / reload — always apply
                    applyImmediately -> {
                        localPosts         = merged
                        _uiState.value     = ThreadDetailUiState.Success(merged)
                        lastPostCount      = newCount
                        _hasNewPosts.value = false
                    }
                    // Background poll — only notify if we have more live posts than before
                    newCount > lastPostCount -> {
                        // Apply the merge silently so deleted stubs are tracked,
                        // but show the bell banner so user can acknowledge
                        localPosts         = merged
                        _hasNewPosts.value = true
                        // Don't update lastPostCount — wait for user to tap banner
                    }
                    // Same or fewer live posts (possible deletion) — apply silently
                    else -> {
                        localPosts     = merged
                        _uiState.value = ThreadDetailUiState.Success(merged)
                        lastPostCount  = newCount
                    }
                }
            },
            onFailure = { throwable ->
                if (throwable is kotlinx.coroutines.CancellationException) return@fold
                
                val isHttp404 = throwable is retrofit2.HttpException && throwable.code() == 404
                if (isHttp404) {
                    _is404.value = true
                }
                
                // Never wipe existing posts on a background poll failure
                if (_uiState.value !is ThreadDetailUiState.Success) {
                    if (isHttp404) {
                        _uiState.value = ThreadDetailUiState.Error("Thread not found (404)")
                    } else {
                        _uiState.value = ThreadDetailUiState.Error(throwable.message ?: "Unknown error")
                    }
                }
            }
        )
    }

    fun toggleSave() {
        val posts = (uiState.value as? ThreadDetailUiState.Success)?.posts ?: return
        val opPost = posts.firstOrNull { it.id == currentThreadNo } ?: posts.firstOrNull() ?: return
        val replyCount = posts.size - 1
        val imageCount = posts.count { it.hasImage() }
        val threadDto = opPost.toThreadDto(replyCount, imageCount)

        viewModelScope.launch {
            if (_isSaved.value) {
                // Delete saved thread
                savedThreadsRepository.deleteThread(currentBoard, currentThreadNo)
                _isSaved.value = false
                android.widget.Toast.makeText(getApplication(), "Thread removed from saved", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                // Save thread
                _isSaving.value = true
                android.widget.Toast.makeText(getApplication(), "Saving thread offline...", android.widget.Toast.LENGTH_SHORT).show()
                try {
                    savedThreadsRepository.saveThread(currentBoard, threadDto, posts)
                    _isSaved.value = true
                    android.widget.Toast.makeText(getApplication(), "Thread saved offline!", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: CancellationException) {
                    Log.d("ThreadDetailViewModel", "Save thread cancelled.")
                    throw e
                } catch (e: Exception) {
                    Log.e("ThreadDetailViewModel", "Failed to save thread: ${e.message}", e)
                    android.widget.Toast.makeText(getApplication(), "Failed to save thread: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    _isSaving.value = false
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}

private fun PostDto.toThreadDto(replyCount: Int, imageCount: Int): ThreadDto {
    return ThreadDto(
        id = id,
        name = name,
        comment = comment,
        imageId = imageId,
        imageExt = imageExt,
        replyCount = replyCount,
        imageCount = imageCount,
        unixTime = unixTime,
        subject = subject
    )
}
