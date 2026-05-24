package com.chan.mimi.ui.screens.threads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chan.mimi.data.model.ThreadDto
import com.chan.mimi.data.repository.ChanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ThreadUiState {
    object Loading                                   : ThreadUiState()
    data class Success(val threads: List<ThreadDto>) : ThreadUiState()
    data class Error(val message: String)            : ThreadUiState()
}

class ThreadViewModel : ViewModel() {

    private val repository = ChanRepository()

    private val _uiState = MutableStateFlow<ThreadUiState>(ThreadUiState.Loading)
    val uiState: StateFlow<ThreadUiState> = _uiState

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
}