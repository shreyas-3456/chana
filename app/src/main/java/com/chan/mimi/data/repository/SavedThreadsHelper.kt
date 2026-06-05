package com.chan.mimi.data.repository

import android.content.Context
import java.io.File

object SavedThreadsHelper {
    fun getLocalThumbFile(context: Context, boardTag: String, threadNo: Long, postId: Long): File {
        return File(context.filesDir, "saved_threads/${boardTag}_${threadNo}/images/thumb_${postId}.jpg")
    }

    fun getLocalFullFile(context: Context, boardTag: String, threadNo: Long, postId: Long, ext: String): File {
        return File(context.filesDir, "saved_threads/${boardTag}_${threadNo}/images/full_${postId}${ext}")
    }

    fun getThreadDir(context: Context, boardTag: String, threadNo: Long): File {
        return File(context.filesDir, "saved_threads/${boardTag}_${threadNo}")
    }
}
