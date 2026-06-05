package com.chan.mimi

import android.app.Application
import com.chan.mimi.data.repository.PollingSettingsRepository
import com.chan.mimi.data.repository.SavedThreadsPollingScheduler
import com.chan.mimi.data.repository.SavedThreadsRepository
import com.chan.mimi.data.repository.WatchedThreadsPollingScheduler
import com.chan.mimi.data.repository.WatchedThreadsRepository

class MimiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(AppLifecycleTracker)

        val pollingSettings = PollingSettingsRepository.getInstance(this)
        val intervalSeconds = pollingSettings.getIntervalSeconds()

        // 1. Schedule saved-threads background sync (only if enabled)
        val savedRepo = SavedThreadsRepository.getInstance(this)
        val anySavedEnabled = savedRepo.savedThreadsFlow.value.any { it.pollingEnabled }
        if (anySavedEnabled && intervalSeconds > 0) {
            SavedThreadsPollingScheduler.schedulePolling(this, intervalSeconds, replace = false)
        }
        SavedThreadsPollingScheduler.updatePersistentNotification(this)

        // 2. Schedule watched-threads background notifications (only if any thread has it enabled)
        val watchedRepo     = WatchedThreadsRepository.getInstance(this)
        val anyWatchEnabled = watchedRepo.allThreads.value.any { it.pollingEnabled }
        if (anyWatchEnabled && intervalSeconds > 0) {
            WatchedThreadsPollingScheduler.schedulePolling(this, intervalSeconds, replace = false)
        }
        WatchedThreadsPollingScheduler.updatePersistentNotification(this)
    }
}
