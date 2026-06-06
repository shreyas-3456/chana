package com.chan.mimi.data.repository

import android.content.Context
import android.util.Log
import com.chan.mimi.data.model.PostDto
import com.chan.mimi.data.model.ThreadDto
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class SavedThreadDetail(
    val boardTag: String,
    val thread: ThreadDto,
    val posts: List<PostDto>,
    val saveTime: Long,
    val is404: Boolean = false,
    val pollingEnabled: Boolean = false
)

class SavedThreadsRepository private constructor(private val context: Context) {

    private val gson = Gson()
    private val client = OkHttpClient()

    /** Shared polling interval (seconds) — delegates to [PollingSettingsRepository]. */
    private val pollingSettings = PollingSettingsRepository.getInstance(context)
    val pollingIntervalFlow: StateFlow<Int> = pollingSettings.intervalSecondsFlow

    fun getPollingInterval(): Int = pollingSettings.getIntervalSeconds()

    private val _savedThreadsFlow = MutableStateFlow<List<SavedThreadDetail>>(emptyList())
    val savedThreadsFlow: StateFlow<List<SavedThreadDetail>> = _savedThreadsFlow.asStateFlow()

    init {
        loadSavedThreads()
    }

    fun loadSavedThreads() {
        val baseDir = File(context.filesDir, "saved_threads")
        if (!baseDir.exists()) {
            _savedThreadsFlow.value = emptyList()
            return
        }

        val list = mutableListOf<SavedThreadDetail>()
        val folders = baseDir.listFiles { file -> file.isDirectory }
        if (folders != null) {
            for (folder in folders) {
                val gzFile  = File(folder, "thread.json.gz")
                val jsonFile = File(folder, "thread.json")
                try {
                    when {
                        gzFile.exists() -> {
                            // Normal path: read compressed file
                            val json = readGzip(gzFile)
                            val detail = gson.fromJson(json, SavedThreadDetail::class.java)
                            list.add(detail)
                        }
                        jsonFile.exists() -> {
                            // Migration path: compress old plain-text file
                            Log.i("SavedThreadsRepository", "Migrating ${folder.name}/thread.json → .gz")
                            val json = jsonFile.readText()
                            writeGzip(gzFile, json)
                            jsonFile.delete()
                            val detail = gson.fromJson(json, SavedThreadDetail::class.java)
                            list.add(detail)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SavedThreadsRepository", "Failed to load thread in folder ${folder.name}: ${e.message}")
                }
            }
        }
        // Sort by saveTime descending
        _savedThreadsFlow.value = list.sortedByDescending { it.saveTime }
    }

    suspend fun isThreadSaved(boardTag: String, threadNo: Long): Boolean = withContext(Dispatchers.IO) {
        val threadDir = SavedThreadsHelper.getThreadDir(context, boardTag, threadNo)
        // Accept either compressed or legacy plain file
        File(threadDir, "thread.json.gz").exists() || File(threadDir, "thread.json").exists()
    }

    suspend fun saveThread(boardTag: String, thread: ThreadDto, posts: List<PostDto>) = withContext(Dispatchers.IO) {
        val threadDir = SavedThreadsHelper.getThreadDir(context, boardTag, thread.id)
        val imagesDir = File(threadDir, "images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }

        // 1. Write the metadata JSON
        val detail = SavedThreadDetail(
            boardTag = boardTag,
            thread = thread,
            posts = posts,
            saveTime = System.currentTimeMillis()
        )
        // Write compressed JSON
        val gzFile = File(threadDir, "thread.json.gz")
        writeGzip(gzFile, gson.toJson(detail))
        // Remove any legacy uncompressed file
        File(threadDir, "thread.json").takeIf { it.exists() }?.delete()

        // 2. Download all media resources asynchronously
        for (post in posts) {
            if (post.hasImage()) {
                val postId = post.id
                val imageExt = post.imageExt ?: ""

                // Thumbnail
                val thumbUrl = "https://t.4cdn.org/$boardTag/${post.imageId}s.jpg"
                val thumbFile = SavedThreadsHelper.getLocalThumbFile(context, boardTag, thread.id, postId)
                if (!thumbFile.exists()) {
                    try {
                        downloadFileTo(thumbUrl, thumbFile)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("SavedThreadsRepository", "Failed to download thumb for post $postId: ${e.message}")
                    }
                }

                // Full Media
                val fullUrl = "https://i.4cdn.org/$boardTag/${post.imageId}$imageExt"
                val fullFile = SavedThreadsHelper.getLocalFullFile(context, boardTag, thread.id, postId, imageExt)
                if (!fullFile.exists()) {
                    try {
                        downloadFileTo(fullUrl, fullFile)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("SavedThreadsRepository", "Failed to download full media for post $postId: ${e.message}")
                    }
                }
            }
        }

        // Reload from disk to update flow
        loadSavedThreads()
    }

    /**
     * Update only the posts list of an already-saved thread (preserves images on disk).
     * Called by the polling logic to keep the saved data fresh without re-downloading media.
     */
    suspend fun updateSavedPosts(boardTag: String, threadNo: Long, posts: List<PostDto>) = withContext(Dispatchers.IO) {
        val threadDir = SavedThreadsHelper.getThreadDir(context, boardTag, threadNo)
        val gzFile    = File(threadDir, "thread.json.gz")
        if (!gzFile.exists()) return@withContext          // not saved — nothing to update

        try {
            val existing = gson.fromJson(readGzip(gzFile), SavedThreadDetail::class.java)
            val livePosts = posts.filter { !it.isDeleted }
            val updatedThread = existing.thread.copy(
                replyCount = (livePosts.size - 1).coerceAtLeast(0),
                imageCount = livePosts.count { it.hasImage() }
            )
            val updated  = existing.copy(thread = updatedThread, posts = posts, is404 = false)
            writeGzip(gzFile, gson.toJson(updated))

            // Also download images for any brand-new posts that have media
            val existingIds = existing.posts.map { it.id }.toHashSet()
            val imagesDir   = File(threadDir, "images")
            if (!imagesDir.exists()) imagesDir.mkdirs()

            for (post in posts) {
                if (post.id in existingIds || !post.hasImage()) continue
                val imageExt = post.imageExt ?: ""
                val thumbFile = SavedThreadsHelper.getLocalThumbFile(context, boardTag, threadNo, post.id)
                val fullFile  = SavedThreadsHelper.getLocalFullFile(context, boardTag, threadNo, post.id, imageExt)
                try {
                    if (!thumbFile.exists()) downloadFileTo("https://t.4cdn.org/$boardTag/${post.imageId}s.jpg", thumbFile)
                    if (!fullFile.exists())  downloadFileTo("https://i.4cdn.org/$boardTag/${post.imageId}$imageExt", fullFile)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("SavedThreadsRepository", "Could not download media for new post ${post.id}: ${e.message}")
                }
            }

            loadSavedThreads()
        } catch (e: Exception) {
            Log.e("SavedThreadsRepository", "updateSavedPosts failed: ${e.message}")
        }
    }

    suspend fun markAs404(boardTag: String, threadNo: Long) = withContext(Dispatchers.IO) {
        val threadDir = SavedThreadsHelper.getThreadDir(context, boardTag, threadNo)
        val gzFile    = File(threadDir, "thread.json.gz")
        if (!gzFile.exists()) return@withContext

        try {
            val existing = gson.fromJson(readGzip(gzFile), SavedThreadDetail::class.java)
            val updated  = existing.copy(is404 = true, pollingEnabled = false)
            writeGzip(gzFile, gson.toJson(updated))
            loadSavedThreads()
            SavedThreadsPollingScheduler.cancelPollingForThread(context, boardTag, threadNo)
            val anyEnabled = _savedThreadsFlow.value.any { !it.is404 && it.pollingEnabled }
            val intervalSeconds = getPollingInterval()
            if (anyEnabled && intervalSeconds > 0) {
                SavedThreadsPollingScheduler.schedulePolling(context, intervalSeconds, replace = true)
            } else {
                SavedThreadsPollingScheduler.cancelPolling(context)
            }
        } catch (e: Exception) {
            Log.e("SavedThreadsRepository", "markAs404 failed: ${e.message}")
        }
    }

    suspend fun setPollingEnabled(boardTag: String, threadNo: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        val threadDir = SavedThreadsHelper.getThreadDir(context, boardTag, threadNo)
        val gzFile = File(threadDir, "thread.json.gz")
        if (gzFile.exists()) {
            try {
                val json = readGzip(gzFile)
                val detail = gson.fromJson(json, SavedThreadDetail::class.java)
                val updated = detail.copy(pollingEnabled = enabled)
                writeGzip(gzFile, gson.toJson(updated))
                loadSavedThreads()
            } catch (e: Exception) {
                Log.e("SavedThreadsRepository", "setPollingEnabled failed: ${e.message}")
            }
        }

        // Reschedule or cancel background sync worker
        val anyEnabled = _savedThreadsFlow.value.any { !it.is404 && it.pollingEnabled }
        val intervalSeconds = getPollingInterval()
        if (enabled && intervalSeconds > 0) {
            SavedThreadsPollingScheduler.schedulePollingForThread(context, boardTag, threadNo, intervalSeconds, replace = true)
        } else {
            SavedThreadsPollingScheduler.cancelPollingForThread(context, boardTag, threadNo)
        }

        if (!anyEnabled || intervalSeconds <= 0) {
            SavedThreadsPollingScheduler.cancelPolling(context)
        } else {
            SavedThreadsPollingScheduler.updatePersistentNotification(context)
        }
    }

    suspend fun deleteThread(boardTag: String, threadNo: Long) = withContext(Dispatchers.IO) {
        val threadDir = SavedThreadsHelper.getThreadDir(context, boardTag, threadNo)
        if (threadDir.exists()) {
            deleteRecursively(threadDir)
        }
        SavedThreadsPollingScheduler.cancelPollingForThread(context, boardTag, threadNo)
        loadSavedThreads()
        val anyEnabled = _savedThreadsFlow.value.any { !it.is404 && it.pollingEnabled }
        val intervalSeconds = getPollingInterval()
        if (anyEnabled && intervalSeconds > 0) {
            SavedThreadsPollingScheduler.schedulePolling(context, intervalSeconds, replace = true)
        } else {
            SavedThreadsPollingScheduler.cancelPolling(context)
        }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteRecursively(child)
                }
            }
        }
        file.delete()
    }

    private fun downloadFileTo(url: String, destFile: File) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", "https://boards.4chan.org/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected HTTP response: $response")
            response.body?.byteStream()?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    // ── Compression helpers ────────────────────────────────────────────────────

    private fun writeGzip(file: File, text: String) {
        GZIPOutputStream(file.outputStream().buffered()).use { gz ->
            gz.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    private fun readGzip(file: File): String {
        return GZIPInputStream(file.inputStream().buffered()).use { gz ->
            BufferedReader(InputStreamReader(gz, Charsets.UTF_8)).readText()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SavedThreadsRepository? = null

        fun getInstance(context: Context): SavedThreadsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SavedThreadsRepository(context).also { INSTANCE = it }
            }
        }
    }
}
