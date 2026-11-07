package com.jinger.app.data.api

import com.jinger.app.BuildConfig
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface JingerApi {
    @GET("jobs")
    suspend fun jobs(@Query("limit") limit: Int = 20, @Query("exclude") exclude: String = ""): List<JobDto>

    @POST("jobs/seen")
    suspend fun markSeen(@Body body: SeenRequest): OkResponse

    @GET("profile")
    suspend fun profile(): ProfileDto?

    @POST("profile")
    suspend fun saveProfile(@Body profile: ProfileDto): ProfileDto

    @Multipart
    @POST("profile/parse")
    suspend fun parseCv(@Part file: MultipartBody.Part): ProfileDto

    @POST("fill-plan")
    suspend fun fillPlan(@Body body: FillPlanRequest): FillPlanDto

    companion object {
        fun create(baseUrl: String = BuildConfig.BACKEND_URL): JingerApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS) // LLM calls can be slow
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                .build()
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(JingerApi::class.java)
        }
    }
}
