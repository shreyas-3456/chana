package com.chan.mimi.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the background polling interval (in seconds).
 * Both [SavedThreadsPollingWorker] and [WatchedThreadsPollingWorker] read from here.
 *
 * Special values:
 *   0 or negative → polling disabled (Off)
 */
class PollingSettingsRepository private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("polling_settings", Context.MODE_PRIVATE)

    private val _intervalSecondsFlow = MutableStateFlow(getIntervalSeconds())
    val intervalSecondsFlow: StateFlow<Int> = _intervalSecondsFlow.asStateFlow()

    fun getIntervalSeconds(): Int =
        prefs.getInt(PREF_INTERVAL_SECONDS, DEFAULT_SECONDS)

    fun setIntervalSeconds(seconds: Int) {
        prefs.edit().putInt(PREF_INTERVAL_SECONDS, seconds).apply()
        _intervalSecondsFlow.value = seconds
    }

    companion object {
        private const val PREF_INTERVAL_SECONDS = "polling_interval_seconds"
        const val DEFAULT_SECONDS = 900   // 15 minutes

        @Volatile private var INSTANCE: PollingSettingsRepository? = null

        fun getInstance(context: Context): PollingSettingsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PollingSettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
