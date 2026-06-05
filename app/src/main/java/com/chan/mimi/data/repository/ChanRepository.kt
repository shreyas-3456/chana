package com.chan.mimi.data.repository

import com.chan.mimi.data.api.ChanApiProvider
import com.chan.mimi.data.model.BoardDto
import com.chan.mimi.data.model.PostDto
import com.chan.mimi.data.model.ThreadDto

/**
 * Singleton repository — all ViewModels share one cache.
 *
 * Previously this was a plain class, so each ViewModel created its own
 * instance with an empty cache.  Switching watched threads would create a
 * new ViewModel → new ChanRepository() → cache miss → full network fetch →
 * Loading state shown → blank slide animation.
 *
 * Now every ViewModel references the same object, so a thread fetched once
 * is available instantly to any ViewModel that asks for it next.
 */
object ChanRepository {

    private val api = ChanApiProvider.api

    // ── Catalog cache ──────────────────────────────────────────────────────
    // Key = board tag, Value = cached thread list
    private val catalogCache = mutableMapOf<String, List<ThreadDto>>()

    // ── Thread cache ───────────────────────────────────────────────────────
    // Key = "board/threadNo", Value = cached post list
    private val threadCache = mutableMapOf<String, List<PostDto>>()

    // ── Synchronous cache reads (non-suspending) ───────────────────────────

    /** Returns cached posts immediately, or null if not yet fetched. */
    fun getCachedThread(board: String, threadNo: Long): List<PostDto>? =
        threadCache["$board/$threadNo"]

    /** Returns cached catalog immediately, or null if not yet fetched. */
    fun getCachedCatalog(board: String): List<ThreadDto>? =
        catalogCache[board]

    // ── Network / cache-first fetches ─────────────────────────────────────

    suspend fun getBoards(): Result<List<BoardDto>> {
        return try {
            Result.success(api.getBoards().boards)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun getCatalog(board: String, forceRefresh: Boolean = false): Result<List<ThreadDto>> {
        if (!forceRefresh) {
            catalogCache[board]?.let { return Result.success(it) }
        }
        return try {
            val threads = api.getCatalog(board).flatMap { it.threads }
            catalogCache[board] = threads
            Result.success(threads)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
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
            threadCache[key] = posts
            Result.success(posts)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            threadCache[key]?.let { return Result.success(it) }
            Result.failure(e)
        }
    }

    /** Bust the thread cache — call when user explicitly wants fresh data. */
    fun invalidateThread(board: String, threadNo: Long) {
        threadCache.remove("$board/$threadNo")
    }
}