// FILE: ui/screens/home/HomeViewModel.kt
package com.chan.mimi.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chan.mimi.data.model.BoardDto
import com.chan.mimi.data.repository.BoardPreferencesRepository
import com.chan.mimi.data.repository.ChanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val chanRepository  = ChanRepository()
    private val prefsRepository = BoardPreferencesRepository(application)

    private val _allBoards = MutableStateFlow<List<BoardDto>>(emptyList())
    val allBoards: StateFlow<List<BoardDto>> = _allBoards

    private val _savedBoards = MutableStateFlow<List<BoardDto>>(emptyList())
    val savedBoards: StateFlow<List<BoardDto>> = _savedBoards

    private val _savedTags = MutableStateFlow<Set<String>>(emptySet())
    val savedTags: StateFlow<Set<String>> = _savedTags

    init {
        loadAllBoards()
        observeSavedTags()
    }

    // Private — only called internally and via loadAllBoardsIfNeeded
    private fun loadAllBoards() {
        viewModelScope.launch {
            val result = chanRepository.getBoards()
            result.onSuccess { boards ->
                _allBoards.value = boards
                updateSavedBoards(_savedTags.value)
            }
        }
    }

    // Public — called from EditBoardsScreen if list is empty
    fun loadAllBoardsIfNeeded() {
        if (_allBoards.value.isEmpty()) {
            loadAllBoards()
        }
    }

    private fun observeSavedTags() {
        viewModelScope.launch {
            prefsRepository.savedBoardTags.collectLatest { tags ->
                _savedTags.value = tags
                updateSavedBoards(tags)
            }
        }
    }

    private fun updateSavedBoards(tags: Set<String>) {
        _savedBoards.value = tags
            .mapNotNull { tag -> _allBoards.value.find { it.tag == tag } }
    }

    fun toggleBoard(tag: String) {
        viewModelScope.launch {
            prefsRepository.toggleBoard(tag, _savedTags.value)
        }
    }
}