package com.xai.dungeonmaster.dto;

import java.util.UUID;

/**
 * Versioned transport envelope for the v2 API. Every v2 REST response (and,
 * later, every WebSocket push) is wrapped in one of these so clients get a
 * stable, self-describing shape: a discriminator {@code type}, a schema
 * {@code version}, the typed {@code payload}, and a correlating
 * {@code requestId}.
 *
 * <pre>{ "type": "game_status", "version": 1, "payload": { ... }, "requestId": "..." }</pre>
 */
public record Envelope<T>(String type, int version, T payload, String requestId) {

    /** Current v2 schema version. Bump when the payload contract changes. */
    public static final int CURRENT_VERSION = 1;

    /**
     * Build an envelope, echoing the caller's requestId when supplied and
     * generating a fresh one otherwise.
     */
    public static <T> Envelope<T> of(String type, T payload, String requestId) {
        String id = (requestId == null || requestId.isBlank())
                ? UUID.randomUUID().toString()
                : requestId;
        return new Envelope<>(type, CURRENT_VERSION, payload, id);
    }

    /** Build an envelope with a freshly generated requestId. */
    public static <T> Envelope<T> of(String type, T payload) {
        return of(type, payload, null);
    }
}
