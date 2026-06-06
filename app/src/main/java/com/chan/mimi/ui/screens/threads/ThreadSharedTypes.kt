package com.chan.mimi.ui.screens.threads

import com.chan.mimi.data.model.PostDto

data class ReplyPopup(
    val quotedPost: PostDto? = null,
    val sourcePost: PostDto? = null,
    val repliesToPost: PostDto? = null,
    val replies: List<PostDto> = emptyList()
)

enum class PostHighlightType {
    NONE,
    ADDED,
    DELETED
}

fun relativeTime(unixTime: Long): String {
    val diff = System.currentTimeMillis() / 1000L - unixTime
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60} minutes ago"
        diff < 86400 -> "${diff / 3600} hours ago"
        else -> "${diff / 86400} days ago"
    }
}
