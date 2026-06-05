package com.chan.mimi.ui.screens.saved

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chan.mimi.data.repository.SavedThreadDetail
import com.chan.mimi.data.repository.SavedThreadsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SavedThreadDetailUiState {
    object Loading : SavedThreadDetailUiState()
    data class Success(val detail: SavedThreadDetail) : SavedThreadDetailUiState()
    data class Error(val message: String) : SavedThreadDetailUiState()
}

class SavedThreadDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SavedThreadsRepository.getInstance(application)

    private val _uiState = MutableStateFlow<SavedThreadDetailUiState>(SavedThreadDetailUiState.Loading)
    val uiState: StateFlow<SavedThreadDetailUiState> = _uiState.asStateFlow()

    fun loadSavedThread(boardTag: String, threadNo: Long) {
        viewModelScope.launch {
            _uiState.value = SavedThreadDetailUiState.Loading
            val list = repository.savedThreadsFlow.value
            val found = list.find { it.boardTag == boardTag && it.thread.id == threadNo }
            if (found != null) {
                _uiState.value = SavedThreadDetailUiState.Success(found)
            } else {
                _uiState.value = SavedThreadDetailUiState.Error("Saved thread not found on device.")
            }
        }
    }

    fun unsaveThread(boardTag: String, threadNo: Long) {
        viewModelScope.launch {
            repository.deleteThread(boardTag, threadNo)
        }
    }
}
