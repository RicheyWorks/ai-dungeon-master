package com.xai.dungeonmaster.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registry of player sessions and the source of their JWTs, backed by a
 * pluggable {@link SessionStore}.
 *
 * A session is a stable player identity (a UUID) minted on first contact via
 * {@code POST /v2/session}. The JWT subject is the session id; {@link JwtAuthFilter}
 * resolves the subject back to a {@link Session} on each request. With the
 * in-memory store, tokens stop resolving after a restart; with the file store,
 * they survive it.
 */
@Service
public class SessionService {

    private final SessionStore store;
    private final JwtService jwt;

    /** Convenience constructor using an in-memory store (embedders / tests). */
    public SessionService(JwtService jwt) {
        this(jwt, new InMemorySessionStore());
    }

    @Autowired
    public SessionService(JwtService jwt, SessionStore store) {
        this.jwt = jwt;
        this.store = (store != null) ? store : new InMemorySessionStore();
    }

    /** Create a new session (persisted via the store) and mint its first token. */
    public Issued createSession(String displayName) {
        String name = (displayName == null || displayName.isBlank()) ? "Adventurer" : displayName.trim();
        String id = UUID.randomUUID().toString();
        long now = Instant.now().getEpochSecond();
        Session session = new Session(id, name, now);
        store.save(session);
        JwtService.Token token = jwt.issue(id, Map.of("name", name));
        return new Issued(session, token.value(), token.expiresAtEpochSeconds());
    }

    public Optional<Session> find(String id) {
        return store.load(id);
    }

    /** Mark a session as seen now (persisting the update); returns it if known. */
    public Optional<Session> touch(String id) {
        Optional<Session> found = store.load(id);
        found.ifPresent(s -> {
            s.markSeen();
            store.save(s);
        });
        return found;
    }

    public int activeCount() {
        return store.size();
    }

    /** A player session. */
    public static final class Session {
        private final String id;
        private final String displayName;
        private final long createdAtEpoch;
        private volatile long lastSeenEpoch;

        Session(String id, String displayName, long createdAtEpoch) {
            this(id, displayName, createdAtEpoch, createdAtEpoch);
        }

        /** Restore constructor used by persistent stores. */
        Session(String id, String displayName, long createdAtEpoch, long lastSeenEpoch) {
            this.id = id;
            this.displayName = displayName;
            this.createdAtEpoch = createdAtEpoch;
            this.lastSeenEpoch = lastSeenEpoch;
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
