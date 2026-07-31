package com.xai.dungeonmaster.android

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight persistence for the guest session + preferred server URL.
 * Backed by app-private SharedPreferences (not encrypted — fine for guest JWT).
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadBaseUrl(default: String): String =
        prefs.getString(KEY_BASE_URL, default)?.takeIf { it.isNotBlank() } ?: default

    fun saveBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url).apply()
    }

    fun loadSession(): SessionInfo? {
        val id = prefs.getString(KEY_SESSION_ID, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        if (id.isBlank() || token.isBlank()) return null
        return SessionInfo(
            sessionId = id,
            token = token,
            displayName = prefs.getString(KEY_DISPLAY_NAME, "Guest") ?: "Guest",
            expiresAtEpochSeconds = prefs.getLong(KEY_EXPIRES, 0L),
            createdAtEpochSeconds = prefs.getLong(KEY_CREATED, 0L),
        )
    }

    fun saveSession(info: SessionInfo) {
        prefs.edit()
            .putString(KEY_SESSION_ID, info.sessionId)
            .putString(KEY_TOKEN, info.token)
            .putString(KEY_DISPLAY_NAME, info.displayName)
            .putLong(KEY_EXPIRES, info.expiresAtEpochSeconds)
            .putLong(KEY_CREATED, info.createdAtEpochSeconds)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_SESSION_ID)
            .remove(KEY_TOKEN)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_EXPIRES)
            .remove(KEY_CREATED)
            .apply()
    }

    companion object {
        private const val PREFS = "dm_session"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_TOKEN = "token"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_EXPIRES = "expires_at"
        private const val KEY_CREATED = "created_at"
    }
}
