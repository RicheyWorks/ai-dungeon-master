package com.xai.dungeonmaster.android

/**
 * Player identity returned by `POST /v2/session`.
 * Persisted via [SessionStore] so relaunches can restore the same world.
 */
data class SessionInfo(
    val sessionId: String,
    val token: String,
    val displayName: String,
    val expiresAtEpochSeconds: Long = 0L,
    val createdAtEpochSeconds: Long = 0L,
) {
    /** Short id for the UI chrome (first 8 chars). */
    fun shortId(): String = if (sessionId.length <= 8) sessionId else sessionId.take(8)

    fun isExpired(nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): Boolean {
        if (expiresAtEpochSeconds <= 0L) return false
        // Small skew so we re-mint before the server rejects the JWT.
        return nowEpochSeconds >= (expiresAtEpochSeconds - 30)
    }

    /** Seconds until JWT expiry (0 if missing/expired). */
    fun secondsUntilExpiry(nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): Long {
        if (expiresAtEpochSeconds <= 0L) return 0L
        return (expiresAtEpochSeconds - nowEpochSeconds).coerceAtLeast(0L)
    }
}
