package com.xai.dungeonmaster.auth;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of player sessions and the source of their JWTs.
 *
 * A session is a stable player identity (a UUID) minted on first contact via
 * {@code POST /v2/session}. The JWT subject is the session id; {@link JwtAuthFilter}
 * resolves the subject back to a {@link Session} on each request. State is kept
 * in-memory for v1 (single process); tokens issued before a restart simply fail
 * to resolve and the client re-authenticates.
 */
@Service
public class SessionService {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final JwtService jwt;

    public SessionService(JwtService jwt) {
        this.jwt = jwt;
    }

    /** Create a new session and mint its first token. */
    public Issued createSession(String displayName) {
        String name = (displayName == null || displayName.isBlank()) ? "Adventurer" : displayName.trim();
        String id = UUID.randomUUID().toString();
        long now = Instant.now().getEpochSecond();
        Session session = new Session(id, name, now);
        sessions.put(id, session);
        JwtService.Token token = jwt.issue(id, Map.of("name", name));
        return new Issued(session, token.value(), token.expiresAtEpochSeconds());
    }

    public Optional<Session> find(String id) {
        return Optional.ofNullable(id == null ? null : sessions.get(id));
    }

    /** Mark a session as seen now; returns it if known. */
    public Optional<Session> touch(String id) {
        Session s = (id == null) ? null : sessions.get(id);
        if (s != null) {
            s.markSeen();
        }
        return Optional.ofNullable(s);
    }

    public int activeCount() {
        return sessions.size();
    }

    /** An in-memory player session. */
    public static final class Session {
        private final String id;
        private final String displayName;
        private final long createdAtEpoch;
        private volatile long lastSeenEpoch;

        Session(String id, String displayName, long createdAtEpoch) {
            this.id = id;
            this.displayName = displayName;
            this.createdAtEpoch = createdAtEpoch;
            this.lastSeenEpoch = createdAtEpoch;
        }

        void markSeen() {
            this.lastSeenEpoch = Instant.now().getEpochSecond();
        }

        public String id() { return id; }
        public String displayName() { return displayName; }
        public long createdAtEpoch() { return createdAtEpoch; }
        public long lastSeenEpoch() { return lastSeenEpoch; }
    }

    /** Result of creating a session: the session plus its freshly-minted token. */
    public record Issued(Session session, String token, long expiresAtEpochSeconds) {}
}
