package com.xai.dungeonmaster.auth;

import java.util.Collection;
import java.util.Optional;

/**
 * Persistence seam for player sessions. The default {@link InMemorySessionStore}
 * keeps sessions for the process lifetime; {@link FileSessionStore} writes them
 * to disk so tokens still resolve after a restart. Implementations must be
 * thread-safe.
 */
public interface SessionStore {

    /** Insert or update a session. */
    void save(SessionService.Session session);

    /** Look up a session by id. */
    Optional<SessionService.Session> load(String id);

    /** Snapshot of all known sessions. */
    Collection<SessionService.Session> all();

    /** Number of stored sessions. */
    default int size() {
        return all().size();
    }
}
