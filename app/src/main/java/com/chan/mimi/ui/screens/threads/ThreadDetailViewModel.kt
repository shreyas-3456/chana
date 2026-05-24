package com.chan.mimi.ui.screens.threads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chan.mimi.data.model.PostDto
import com.chan.mimi.data.repository.ChanRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class ThreadDetailUiState {
    object Loading                               : ThreadDetailUiState()
    data class Success(val posts: List<PostDto>) : ThreadDetailUiState()
    data class Error(val message: String)        : ThreadDetailUiState()
}

class ThreadDetailViewModel : ViewModel() {

    private val repository = ChanRepository()

    private val _uiState     = MutableStateFlow<ThreadDetailUiState>(ThreadDetailUiState.Loading)
    val uiState: StateFlow<ThreadDetailUiState> = _uiState

    private val _isSaved     = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved

    private val _hasNewPosts = MutableStateFlow(false)
    val hasNewPosts: StateFlow<Boolean> = _hasNewPosts

    private val _pollCountdown = MutableStateFlow(30)
    val pollCountdown: StateFlow<Int> = _pollCountdown

    private var lastPostCount   = 0
    private var pollingJob: Job? = null
    private var currentBoard    = ""
    private var currentThreadNo = 0L

    fun startPolling(board: String, threadNo: Long) {
        // Already polling this exact thread — don't restart
        if (currentBoard == board && currentThreadNo == threadNo && pollingJob?.isActive == true) return

        val switchingThread = currentBoard != board || currentThreadNo != threadNo
        currentBoard    = board
        currentThreadNo = threadNo
        pollingJob?.cancel()

        // Only reset to Loading + clear counts when switching to a different thread
        if (switchingThread) {
            lastPostCount      = 0
            _hasNewPosts.value = false
            _uiState.value     = ThreadDetailUiState.Loading
        }

        pollingJob = viewModelScope.launch {
            // First load — cache-first, shows immediately if cached
            fetchThread(board, threadNo, forceRefresh = false, applyImmediately = true)

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

    fun applyNewPosts(board: String, threadNo: Long) {
        _hasNewPosts.value = false
        repository.invalidateThread(board, threadNo)
        viewModelScope.launch {
            fetchThread(board, threadNo, forceRefresh = true, applyImmediately = true)
        }
    }

    private suspend fun fetchThread(
        board: String,
        threadNo: Long,
        forceRefresh: Boolean,
        applyImmediately: Boolean
    ) {
        val result = repository.getThread(board, threadNo, forceRefresh)

        result.fold(
            onSuccess = { posts ->
                val newCount = posts.size
                when {
                    // First load or user tapped banner — always apply immediately
                    applyImmediately -> {
                        _uiState.value     = ThreadDetailUiState.Success(posts)
                        lastPostCount      = newCount
                        _hasNewPosts.value = false
                    }
                    // Background poll — only notify if we have more posts than before
                    newCount > lastPostCount -> {
                        _hasNewPosts.value = true
                        // Don't update lastPostCount here — wait for user to tap banner
                        // so the count difference stays valid until they acknowledge it
                    }
                    // Same count — nothing to do
                }
            },
            onFailure = {
                // Never wipe existing posts on a background poll failure
                if (_uiState.value !is ThreadDetailUiState.Success) {
                    _uiState.value = ThreadDetailUiState.Error(it.message ?: "Unknown error")
                }
            }
        )
    }

    fun toggleSave() { _isSaved.value = !_isSaved.value }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}