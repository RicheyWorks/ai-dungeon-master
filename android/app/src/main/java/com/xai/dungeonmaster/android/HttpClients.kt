package com.xai.dungeonmaster.android

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared OkHttp client factory that injects the current Bearer token into every
 * request. The generated [com.xai.dungeonmaster.client.apis.V2Api] accepts a
 * client instance; pairing it with this factory makes all `/v2/*` calls
 * session-scoped once a token is set.
 */
object HttpClients {

    private val tokenRef = AtomicReference<String?>(null)

    fun setToken(token: String?) {
        tokenRef.set(token)
    }

    fun token(): String? = tokenRef.get()

    fun clearToken() {
        tokenRef.set(null)
    }

    fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(bearerInterceptor())
        .build()

    private fun bearerInterceptor(): Interceptor = Interceptor { chain ->
        val token = tokenRef.get()
        val request = if (!token.isNullOrBlank()) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }
}
