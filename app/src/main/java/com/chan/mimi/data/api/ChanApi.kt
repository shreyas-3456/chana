// FILE: data/api/ChanApi.kt
package com.chan.mimi.data.api

import com.chan.mimi.data.model.BoardResponse
import com.chan.mimi.data.model.CatalogPage
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

// The interface defines available API endpoints
// Retrofit implements this automatically — no code needed
interface ChanApi {

    @GET("boards.json")
    suspend fun getBoards() : BoardResponse

    @GET("{board}/catalog.json")
    suspend fun getCatalog(
        @Path("board") board : String  // e.g. "tv"
    ) : List<CatalogPage>
}

// Singleton that creates and holds one instance of ChanApi
object ChanApiProvider {

    private const val BASE_URL = "https://a.4cdn.org/"

    val api : ChanApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                OkHttpClient.Builder()
                    .build()
            )
            .build()
            .create(ChanApi::class.java)
    }
}