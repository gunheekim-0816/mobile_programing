package com.example.my_project.network

import com.example.my_project.data.GeminiRequest
import com.example.my_project.data.GeminiResponse
import com.example.my_project.data.KakaoSearchResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface KakaoApiService {
    @GET("v2/local/search/category.json")
    suspend fun getNearbyRestaurants(
        @Header("Authorization") authorizationHeader: String,
        @Query("x") longitude: String,
        @Query("y") latitude: String,
        @Query("radius") radius: Int = 2000,
        @Query("category_group_code") categoryGroupCode: String = "FD6",
        @Query("sort") sort: String = "distance",
        @Query("size") size: Int = 15
    ): KakaoSearchResponse
}

interface GeminiApiService {
    @POST("v1/models/gemini-2.5-flash:generateContent")
    suspend fun generateRecommendation(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object RetrofitClient {
    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val kakaoService: KakaoApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://dapi.kakao.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KakaoApiService::class.java)
    }

    val geminiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }
}
