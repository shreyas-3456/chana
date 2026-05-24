package com.chan.mimi.data.repository

import com.chan.mimi.data.api.ChanApiProvider
import com.chan.mimi.data.model.BoardDto
import com.chan.mimi.data.model.PostDto
import com.chan.mimi.data.model.ThreadDto

class ChanRepository {

    private val api = ChanApiProvider.api

    // ── Catalog cache ─────────────────────────────────────────
    // Key = board tag, Value = cached thread list
    // Survives for the lifetime of the repository instance (process lifetime)
    private val catalogCache = mutableMapOf<String, List<ThreadDto>>()

    // ── Thread cache ──────────────────────────────────────────
    // Key = "board/threadNo", Value = cached post list
    private val threadCache = mutableMapOf<String, List<PostDto>>()

    suspend fun getBoards(): Result<List<BoardDto>> {
        return try {
            Result.success(api.getBoards().boards)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCatalog(board: String, forceRefresh: Boolean = false): Result<List<ThreadDto>> {
        // Return cache immediately if available and not forced
        if (!forceRefresh) {
            catalogCache[board]?.let { return Result.success(it) }
        }
        return try {
            val threads = api.getCatalog(board).flatMap { it.threads }
            catalogCache[board] = threads   // store in cache
            Result.success(threads)
        } catch (e: Exception) {
            // On failure return stale cache if we have it
            catalogCache[board]?.let { return Result.success(it) }
            Result.failure(e)
        }
    }

    suspend fun getThread(
        board: String,
        threadNo: Long,
        forceRefresh: Boolean = false
    ): Result<List<PostDto>> {
        val key = "$board/$threadNo"
        if (!forceRefresh) {
            threadCache[key]?.let { return Result.success(it) }
        }
        return try {
            val posts = api.getThread(board, threadNo).posts
            threadCache[key] = posts        // store in cache
            Result.success(posts)
        } catch (e: Exception) {
            threadCache[key]?.let { return Result.success(it) }
            Result.failure(e)
        }
    }

    // Call this when you explicitly want to bust the thread cache
    // e.g. when user taps the new posts banner
    fun invalidateThread(board: String, threadNo: Long) {
        threadCache.remove("$board/$threadNo")
    }
}