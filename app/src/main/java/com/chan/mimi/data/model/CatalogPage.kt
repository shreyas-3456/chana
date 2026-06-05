// FILE: data/model/CatalogResponse.kt
package com.chan.mimi.data.model

import com.google.gson.annotations.SerializedName

data class CatalogPage(
    @SerializedName("page")
    val page    : Int,

    @SerializedName("threads")
    val threads : List<ThreadDto>
)

data class ThreadDto(
    @SerializedName("no")
    val id : Long,

    @SerializedName("name")
    val name : String?,          // ← nullable now

    @SerializedName("com")
    val comment : String?,       // ← nullable now

    @SerializedName("tim")
    val imageId : Long? = null,

    @SerializedName("ext")
    val imageExt : String? = null,

    @SerializedName("replies")
    val replyCount : Int?,       // ← nullable now

    @SerializedName("images")
    val imageCount : Int?,       // ← nullable now

    @SerializedName("time")
    val unixTime : Long?,        // ← nullable now

    @SerializedName("sub")
    val subject : String?        // ← nullable now
) {
    // Safe getters with fallbacks — use these everywhere in the UI
    fun safeName()       = name       ?: "Anonymous"
    fun safeComment()    = comment    ?: ""
    fun safeSubject()    = subject    ?: ""
    fun safeReplyCount() = replyCount ?: 0
    fun safeImageCount() = imageCount ?: 0
}

// Replies in a thread — from /{board}/thread/{no}.json
data class ThreadReplyResponse(
    @SerializedName("posts")
    val posts: List<PostDto>
)

data class PostDto(
    @SerializedName("no")
    val id: Long,

    @SerializedName("name")
    val name: String?,

    @SerializedName("com")
    val comment: String?,

    @SerializedName("tim")
    val imageId: Long? = null,

    @SerializedName("ext")
    val imageExt: String? = null,

    @SerializedName("time")
    val unixTime: Long?,

    @SerializedName("sub")
    val subject: String?,

    @SerializedName("filename")
    val filename: String?,

    @SerializedName("fsize")
    val fileSize: Long?,

    @SerializedName("w")
    val imageWidth: Int?,

    @SerializedName("h")
    val imageHeight: Int?,

    @SerializedName("resto")
    val replyToId: Long?,   // 0 = OP post

    // Not from API — set in-memory when a post disappears from a subsequent fetch
    val isDeleted: Boolean = false
) {
    fun safeName()    = name    ?: "Anonymous"
    fun safeComment() = comment ?: ""
    fun safeSubject() = subject ?: ""
    fun isOp()        = replyToId == 0L
    fun hasImage()    = imageId != null && imageExt != null
    fun imageUrl(boardTag: String) =
        if (hasImage()) "https://i.4cdn.org/$boardTag/$imageId$imageExt" else null
    fun fileSizeKb()  = fileSize?.let { "%.2f KB".format(it / 1024f) } ?: ""
    fun repliesTo(postId: Long): Boolean {
        val com = safeComment()
        return com.contains(">>$postId") || com.contains("&gt;&gt;$postId")
    }
    /** Return a copy of this post stamped as deleted. */
    fun asDeleted() = copy(isDeleted = true)
}