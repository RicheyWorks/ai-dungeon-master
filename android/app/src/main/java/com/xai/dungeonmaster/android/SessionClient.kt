package com.xai.dungeonmaster.android

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Hand-written client for session identity + optional save/load/reset.
 *
 * Session endpoints ship on main but are not yet in the generated Kotlin SDK
 * (regenerate from openapi when ready). Until then this tiny client covers the
 * multi-player identity path the Android app needs.
 */
class SessionClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val sessionEnvelopeAdapter = moshi.adapter(SessionEnvelope::class.java)
    private val saveEnvelopeAdapter = moshi.adapter(SaveEnvelope::class.java)
    private val statusEnvelopeAdapter = moshi.adapter(StatusEnvelope::class.java)
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun createSession(displayName: String? = null): SessionInfo {
        val bodyJson = if (displayName.isNullOrBlank()) {
            "{}"
        } else {
            """{"displayName":${jsonString(displayName)}}"""
        }
        val request = Request.Builder()
            .url(url("/v2/session"))
            .post(bodyJson.toRequestBody(jsonMedia))
            .header("Accept", "application/json")
            .build()
        val envelope = execute(request, sessionEnvelopeAdapter)
            ?: throw IOException("Empty session response")
        val p = envelope.payload ?: throw IOException("Session payload missing")
        val token = p.token ?: throw IOException("Session token missing")
        val id = p.sessionId ?: throw IOException("Session id missing")
        return SessionInfo(
            sessionId = id,
            token = token,
            displayName = p.displayName ?: "Guest",
            expiresAtEpochSeconds = p.expiresAtEpochSeconds ?: 0L,
            createdAtEpochSeconds = p.createdAtEpochSeconds ?: 0L,
        )
    }

    fun me(token: String): SessionInfo {
        val request = Request.Builder()
            .url(url("/v2/session/me"))
            .get()
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .build()
        val envelope = execute(request, sessionEnvelopeAdapter)
            ?: throw IOException("Empty /session/me response")
        val p = envelope.payload ?: throw IOException("Session payload missing")
        return SessionInfo(
            sessionId = p.sessionId ?: "",
            token = token,
            displayName = p.displayName ?: "Guest",
            expiresAtEpochSeconds = p.expiresAtEpochSeconds ?: 0L,
            createdAtEpochSeconds = p.createdAtEpochSeconds ?: 0L,
        )
    }

    /** Requires server with `POST /v2/save` (multi-player isolation PR). */
    fun save(token: String): SaveResult {
        val request = Request.Builder()
            .url(url("/v2/save"))
            .post("{}".toRequestBody(jsonMedia))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .build()
        val envelope = execute(request, saveEnvelopeAdapter)
            ?: throw IOException("Empty save response")
        val p = envelope.payload
        return SaveResult(
            saved = p?.saved == true,
            path = p?.path,
            sessionScoped = p?.sessionScoped == true,
        )
    }

    /** Requires server with `POST /v2/load`. */
    fun load(token: String): Boolean {
        val request = Request.Builder()
            .url(url("/v2/load"))
            .post("{}".toRequestBody(jsonMedia))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .build()
        val envelope = execute(request, statusEnvelopeAdapter)
        return envelope?.type == "game_status"
    }

    /** Requires server with `POST /v2/reset`. */
    fun reset(token: String): Boolean {
        val request = Request.Builder()
            .url(url("/v2/reset"))
            .post("{}".toRequestBody(jsonMedia))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .build()
        val envelope = execute(request, statusEnvelopeAdapter)
        return envelope?.type == "game_status"
    }

    private fun url(path: String): String = baseUrl.trimEnd('/') + path

    private fun <T> execute(request: Request, adapter: com.squareup.moshi.JsonAdapter<T>): T? {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${body.take(200)}")
            }
            if (body.isBlank()) return null
            return adapter.fromJson(body)
        }
    }

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

data class SessionInfo(
    val sessionId: String,
    val token: String,
    val displayName: String,
    val expiresAtEpochSeconds: Long = 0L,
    val createdAtEpochSeconds: Long = 0L,
) {
    /** Short id for the UI chrome (first 8 chars). */
    fun shortId(): String = if (sessionId.length <= 8) sessionId else sessionId.take(8)
}

data class SaveResult(
    val saved: Boolean,
    val path: String?,
    val sessionScoped: Boolean,
)

// ── wire models (Moshi reflection; no kapt) ──────────────────────────────────

data class SessionEnvelope(
    val type: String? = null,
    val version: Int? = null,
    val payload: SessionPayloadDto? = null,
    val requestId: String? = null,
)

data class SessionPayloadDto(
    val sessionId: String? = null,
    val token: String? = null,
    val displayName: String? = null,
    val expiresAtEpochSeconds: Long? = null,
    val createdAtEpochSeconds: Long? = null,
)

data class SaveEnvelope(
    val type: String? = null,
    val version: Int? = null,
    val payload: SavePayloadDto? = null,
    val requestId: String? = null,
)

data class SavePayloadDto(
    val saved: Boolean? = null,
    val path: String? = null,
    val sessionScoped: Boolean? = null,
)

data class StatusEnvelope(
    val type: String? = null,
    val version: Int? = null,
    val requestId: String? = null,
)
