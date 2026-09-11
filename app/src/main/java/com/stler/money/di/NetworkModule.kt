package com.stler.money.di

import com.google.gson.Gson
import com.stler.money.BuildConfig
import com.stler.money.data.remote.SheetsApi
import com.stler.money.data.remote.TokenProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://sheets.googleapis.com/v4/"

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenProvider: TokenProvider): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = runBlocking { tokenProvider.getAccessToken() }
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                )
            }
            .authenticator { _, response ->
                // Bail after one retry — without this, a revoked grant whose refresh call still
                // "succeeds" (returns a token Google then rejects again) retried forever instead
                // of surfacing an error.
                if (responseCount(response) >= 2) return@authenticator null
                val newToken = runBlocking { tokenProvider.refreshToken() }
                    ?: return@authenticator null
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    // BASIC still logged full request URLs (including spreadsheet ids) in release
                    // builds. Debug-only now.
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideSheetsApi(retrofit: Retrofit): SheetsApi =
        retrofit.create(SheetsApi::class.java)

    /** Standard OkHttp recipe for counting how many times a request has already been retried via [Response.priorResponse]. */
    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
