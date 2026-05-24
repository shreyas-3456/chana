// FILE: data/api/ChanApi.kt
package com.chan.mimi.data.api

import com.chan.mimi.data.model.BoardResponse
import com.chan.mimi.data.model.CatalogPage
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

interface ChanApi {
    @GET("boards.json")
    suspend fun getBoards() : BoardResponse

    @GET("{board}/catalog.json")
    suspend fun getCatalog(
        @Path("board") board : String
    ) : List<CatalogPage>
}

object ChanApiProvider {
    private const val BASE_URL = "https://a.4cdn.org/"

    // Logger — shows every request/response in Logcat
    // Filter by tag "OkHttp" in Logcat to see all API calls
    private val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val api : ChanApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(logger)   // ← logs every request
                    .build()
            )
            .build()
            .create(ChanApi::class.java)
    }
}