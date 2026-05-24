package com.chan.mimi.data.api

import com.chan.mimi.data.model.BoardResponse
import com.chan.mimi.data.model.CatalogPage
import com.chan.mimi.data.model.ThreadReplyResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

interface ChanApi {
    @GET("boards.json")
    suspend fun getBoards(): BoardResponse

    @GET("{board}/catalog.json")
    suspend fun getCatalog(
        @Path("board") board: String
    ): List<CatalogPage>

    @GET("{board}/thread/{no}.json")
    suspend fun getThread(
        @Path("board") board: String,
        @Path("no")    no:    Long
    ): ThreadReplyResponse
}

object ChanApiProvider {
    private const val BASE_URL = "https://a.4cdn.org/"

    private val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val api: ChanApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(logger)
                    .build()
            )
            .build()
            .create(ChanApi::class.java)
    }
}