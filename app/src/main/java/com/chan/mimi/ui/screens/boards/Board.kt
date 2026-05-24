// FILE: ui/screens/boards/Board.kt
package com.chan.mimi.ui.screens.boards

// A board is just a name and description
// e.g. /tv/ — Television & Film
data class Board(
    val tag         : String,  // e.g. "tv"
    val name        : String,  // e.g. "Television & Film"
    val description : String,  // e.g. "Discuss movies, shows..."
    val isNsfw      : Boolean = false
)

// Fake data — mimicking real 4chan boards
val sampleBoards = listOf(
    Board("tv",  "Television & Film",  "Discuss movies, TV shows and celebrities"),
    Board("v",   "Video Games",        "Video games and gaming culture"),
    Board("a",   "Anime & Manga",      "Anime, manga and Japanese culture"),
    Board("g",   "Technology",         "Computers, phones and technology"),
    Board("mu",  "Music",              "Music and audio"),
    Board("sp",  "Sports",             "Athletics and physical fitness"),
    Board("fit", "Fitness",            "Health, fitness and nutrition"),
    Board("biz", "Business & Finance", "Business, finance and crypto"),
    Board("lit", "Literature",         "Books and writing"),
    Board("sci", "Science & Math",     "Science, mathematics and academic topics"),
    Board("his", "History & Humanities","History and humanities"),
    Board("int", "International",      "International culture and language"),
)