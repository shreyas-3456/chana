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