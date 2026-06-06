package com.chan.mimi.ui.screens.threads

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chan.mimi.data.model.ThreadDto
import com.chan.mimi.data.repository.ChanRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ThreadSortOption(val label: String) {
    REPLY_COUNT("Reply count"),
    NEWEST("Newest"),
    OLDEST("Oldest"),
    SUBJECT("Subject name"),
    IMAGE_COUNT("Image count")
}

sealed class ThreadUiState {
    object Loading                                   : ThreadUiState()
    data class Success(val threads: List<ThreadDto>) : ThreadUiState()
    data class Error(val message: String)            : ThreadUiState()
}

class ThreadViewModel : ViewModel() {
    companion object {
        private const val MIN_REFRESH_VISIBLE_MS = 400L
    }

    private val repository = ChanRepository

    private val _uiState = MutableStateFlow<ThreadUiState>(ThreadUiState.Loading)
    val uiState: StateFlow<ThreadUiState> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // Sort state for persistence
    private val _sortOption = MutableStateFlow(ThreadSortOption.REPLY_COUNT)
    val sortOption: StateFlow<ThreadSortOption> = _sortOption

    fun setSortOption(option: ThreadSortOption) {
        _sortOption.value = option
    }

    // Track which board is currently loaded so we never re-fetch the same one
    private var loadedBoard: String? = null

    fun loadCatalog(board: String) {
        // Already loaded this board — cache hit, do nothing
        if (loadedBoard == board && _uiState.value is ThreadUiState.Success) return

        viewModelScope.launch {
            _uiState.value = ThreadUiState.Loading
            val result = repository.getCatalog(board)
            _uiState.value = result.fold(
                onSuccess = {
                    loadedBoard = board
                    ThreadUiState.Success(it)
                },
                onFailure = { ThreadUiState.Error(it.message ?: "Unknown error") }
            )
        }
    }

    fun refreshCatalog(board: String) {
        if (_isRefreshing.value) return

        viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                _isRefreshing.value = true
                val result = repository.getCatalog(board, forceRefresh = true)
                val remainingMs = MIN_REFRESH_VISIBLE_MS - (SystemClock.elapsedRealtime() - startedAt)
                if (remainingMs > 0) {
                    delay(remainingMs)
                }
                _uiState.value = result.fold(
                    onSuccess = {
                        loadedBoard = board
                        ThreadUiState.Success(it)
                    },
                    onFailure = { ThreadUiState.Error(it.message ?: "Unknown error") }
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
