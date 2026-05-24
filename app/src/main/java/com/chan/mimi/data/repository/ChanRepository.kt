// FILE: data/repository/ChanRepository.kt
package com.chan.mimi.data.repository

import com.chan.mimi.data.api.ChanApiProvider
import com.chan.mimi.data.model.BoardDto
import com.chan.mimi.data.model.ThreadDto

// Repository is the single source of truth for data
// ViewModels talk to Repository, never directly to the API
// This makes it easy to swap API for a database later

class ChanRepository {

    private val api = ChanApiProvider.api

    // Wraps result in a Result<T> so errors are handled cleanly
    suspend fun getBoards() : Result<List<BoardDto>> {
        return try {
            val response = api.getBoards()
            Result.success(response.boards)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCatalog(board: String) : Result<List<ThreadDto>> {
        return try {
            val pages   = api.getCatalog(board)
            val threads = pages.flatMap { it.threads } // flatten pages into one list
            Result.success(threads)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}