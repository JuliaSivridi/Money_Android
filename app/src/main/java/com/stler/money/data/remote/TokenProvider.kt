package com.stler.money.data.remote

/** Implemented by [com.stler.money.auth.GoogleAuthRepository]; consumed by NetworkModule's OkHttp interceptor. */
interface TokenProvider {
    suspend fun getAccessToken(): String
    suspend fun refreshToken(): String?
}
