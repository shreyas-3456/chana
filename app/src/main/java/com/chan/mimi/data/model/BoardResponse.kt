// FILE: data/model/BoardResponse.kt
package com.chan.mimi.data.model

import com.google.gson.annotations.SerializedName

data class BoardResponse(
    @SerializedName("boards")
    val boards : List<BoardDto>
)

data class BoardDto(
    @SerializedName("board")
    val tag : String,          // e.g. "tv"

    @SerializedName("title")
    val title : String,        // e.g. "Television & Film"

    @SerializedName("ws_board")
    val isSfw : Int,           // 1 = safe, 0 = nsfw

    @SerializedName("meta_description")
    val description : String = ""
)