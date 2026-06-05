package com.chan.mimi.data.model

data class WatchedThread(
    val boardTag        : String,
    val threadNo        : Long,
    val title           : String,
    val thumbnailUrl    : String,
    val lastPostCount   : Int     = 0,
    val hasNewPosts     : Boolean = false,
    val addedAt         : Long    = System.currentTimeMillis(),
    /** When true this thread is included in the WorkManager background polling job. */
    val pollingEnabled  : Boolean = false
)
