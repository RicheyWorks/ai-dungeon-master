package com.xai.dungeonmaster.dto;

/**
 * Payload for {@code type = "session"} envelopes. {@code token} is populated
 * only when a session is first created (POST /v2/session); it is null on the
 * /v2/session/me echo so a token is never reflected back to the caller.
 */
public record SessionPayload(
        String sessionId,
        String token,
        String displayName,
        long expiresAtEpochSeconds,
        long createdAtEpochSeconds) {}
