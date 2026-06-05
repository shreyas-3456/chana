package com.chan.mimi.ui.screens.saved

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chan.mimi.data.repository.SavedThreadDetail
import com.chan.mimi.data.repository.SavedThreadsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SavedViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SavedThreadsRepository.getInstance(application)

    val savedThreads: StateFlow<List<SavedThreadDetail>> = repository.savedThreadsFlow

    fun unsaveThread(boardTag: String, threadId: Long) {
        viewModelScope.launch {
            repository.deleteThread(boardTag, threadId)
        }
    }
}
