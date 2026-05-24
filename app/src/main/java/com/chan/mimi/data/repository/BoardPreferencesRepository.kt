// FILE: data/repository/BoardPreferencesRepository.kt
package com.chan.mimi.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// This creates a single DataStore instance tied to the app context
// Think of it like a tiny persistent key-value database
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chan_prefs")

class BoardPreferencesRepository(private val context: Context) {

    companion object {
        // The key under which we store saved board tags
        // e.g. setOf("tv", "v", "a", "g")
        private val SAVED_BOARDS_KEY = stringSetPreferencesKey("saved_boards")

        // Default boards shown on first launch — matches your screenshot
        val DEFAULT_BOARDS = setOf("tv", "b", "wsg", "gif", "hc", "v")
    }

    // Flow — emits a new value every time saved boards change
    // The screen observes this and redraws automatically
    val savedBoardTags: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[SAVED_BOARDS_KEY] ?: DEFAULT_BOARDS
        }

    // Add a board to saved list
    suspend fun addBoard(tag: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[SAVED_BOARDS_KEY] ?: DEFAULT_BOARDS
            preferences[SAVED_BOARDS_KEY] = current + tag
        }
    }

    // Remove a board from saved list
    suspend fun removeBoard(tag: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[SAVED_BOARDS_KEY] ?: DEFAULT_BOARDS
            preferences[SAVED_BOARDS_KEY] = current - tag
        }
    }

    // Check if a board is already saved
    // Used to show add/remove state in EditBoardsScreen
    suspend fun toggleBoard(tag: String, currentSaved: Set<String>) {
        if (tag in currentSaved) removeBoard(tag)
        else addBoard(tag)
    }
}