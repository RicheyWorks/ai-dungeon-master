package com.xai.dungeonmaster.client.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LogoutPayload(
    @Json(name = "loggedOut") val loggedOut: Boolean? = null,
    @Json(name = "sessionId") val sessionId: String? = null,
)

@JsonClass(generateAdapter = true)
data class LogoutEnvelope(
    @Json(name = "type") val type: String? = null,
    @Json(name = "version") val version: Int? = null,
    @Json(name = "payload") val payload: LogoutPayload? = null,
    @Json(name = "requestId") val requestId: String? = null,
)
