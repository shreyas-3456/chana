package com.chan.mimi.ui.screens.saved

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chan.mimi.data.repository.SavedThreadDetail
import com.chan.mimi.data.repository.SavedThreadsRepository
import com.chan.mimi.data.repository.PollingSettingsRepository
import com.chan.mimi.data.repository.SavedThreadsPollingScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SavedViewModel(application: Application) : AndroidViewModel(application) {

    private val repository      = SavedThreadsRepository.getInstance(application)
    private val pollingSettings = PollingSettingsRepository.getInstance(application)

    val savedThreads: StateFlow<List<SavedThreadDetail>> = repository.savedThreadsFlow

    /** Shared polling interval in seconds. 0 = Off. */
    val pollingInterval: StateFlow<Int> = pollingSettings.intervalSecondsFlow

    private val _secondsToNextPoll = MutableStateFlow(0)
    val secondsToNextPoll: StateFlow<Int> = _secondsToNextPoll.asStateFlow()

    init {
        startCountdown()
    }

    private fun startCountdown() {
        viewModelScope.launch {
            while (true) {
                val intervalSeconds = pollingSettings.getIntervalSeconds()
                val anyEnabled = savedThreads.value.any { it.pollingEnabled }
                if (anyEnabled && intervalSeconds > 0) {
                    for (sec in intervalSeconds downTo 1) {
                        _secondsToNextPoll.value = sec
                        delay(1000L)
                        // Break if configuration or enabled state changes
                        if (pollingSettings.getIntervalSeconds() != intervalSeconds) break
                        if (savedThreads.value.any { it.pollingEnabled } != anyEnabled) break
                    }
                    _secondsToNextPoll.value = 0
                } else {
                    _secondsToNextPoll.value = 0
                    delay(1000L)
                }
            }
        }
    }

    fun togglePolling(boardTag: String, threadId: Long, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && pollingSettings.getIntervalSeconds() <= 0) {
                pollingSettings.setIntervalSeconds(PollingSettingsRepository.DEFAULT_SECONDS)
            }
            repository.setPollingEnabled(boardTag, threadId, enabled)
            SavedThreadsPollingScheduler.updatePersistentNotification(getApplication())
        }
    }

    /**
     * Set the shared background polling interval (seconds).
     * Reschedules both the saved-threads sync worker and the watched-threads
     * notification worker (the latter only if at least one watched thread has
     * background polling enabled).
     */
    fun setPollingInterval(seconds: Int) {
        pollingSettings.setIntervalSeconds(seconds)

        // Saved-threads background sync
        val anyEnabled = savedThreads.value.any { it.pollingEnabled }
        if (anyEnabled && seconds > 0) {
            SavedThreadsPollingScheduler.schedulePolling(getApplication(), seconds, replace = true)
        } else {
            SavedThreadsPollingScheduler.cancelPolling(getApplication())
        }
    }

    fun unsaveThread(boardTag: String, threadId: Long) {
        viewModelScope.launch {
            repository.deleteThread(boardTag, threadId)
            val anyEnabled = savedThreads.value.any { it.pollingEnabled }
            val seconds = pollingSettings.getIntervalSeconds()
            if (anyEnabled && seconds > 0) {
                SavedThreadsPollingScheduler.schedulePolling(getApplication(), seconds, replace = true)
            } else {
                SavedThreadsPollingScheduler.cancelPolling(getApplication())
            }
        }
    }
}
