package com.xai.dungeonmaster.dto;

import java.util.List;

/**
 * Payload for {@code type = "session"} envelopes. {@code token} is populated
 * only when a session is first created or refreshed; it is null on
 * {@code GET /v2/session/me} so a token is never reflected back to the caller.
 * {@code lastSeenEpochSeconds} / {@code enabledPackIds} are set on {@code /me}.
 */
public record SessionPayload(
        String sessionId,
        String token,
        String displayName,
        long expiresAtEpochSeconds,
        long createdAtEpochSeconds,
        Long lastSeenEpochSeconds,
        List<String> enabledPackIds
) {
    /** Create / refresh / rename (no pack inventory). */
    public SessionPayload(
            String sessionId,
            String token,
            String displayName,
            long expiresAtEpochSeconds,
            long createdAtEpochSeconds) {
        this(sessionId, token, displayName, expiresAtEpochSeconds, createdAtEpochSeconds, null, null);
    }
}
