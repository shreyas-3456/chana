// FILE: ui/screens/boards/BoardViewModel.kt
package com.chan.mimi.ui.screens.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chan.mimi.data.model.BoardDto
import com.chan.mimi.data.repository.ChanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// UiState — three possible states the screen can be in
sealed class BoardUiState {
    object Loading                        : BoardUiState()
    data class Success(val boards: List<BoardDto>) : BoardUiState()
    data class Error(val message: String) : BoardUiState()
}

class BoardViewModel : ViewModel() {

    private val repository = ChanRepository()

    // StateFlow is like a stream that always has a current value
    // The screen observes this and redraws when it changes
    private val _uiState = MutableStateFlow<BoardUiState>(BoardUiState.Loading)
    val uiState : StateFlow<BoardUiState> = _uiState

    // Called once when ViewModel is created
    init {
        loadBoards()
    }

    fun loadBoards() {
        viewModelScope.launch {
            _uiState.value = BoardUiState.Loading
            val result = repository.getBoards()
            _uiState.value = result.fold(
                onSuccess = { BoardUiState.Success(it) },
                onFailure = { BoardUiState.Error(it.message ?: "Unknown error") }
            )
        }
    }
}