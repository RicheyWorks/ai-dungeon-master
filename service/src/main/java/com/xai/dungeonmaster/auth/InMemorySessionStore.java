package com.xai.dungeonmaster.auth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link SessionStore}: sessions live only for the process lifetime.
 * Tokens issued before a restart simply stop resolving and the client
 * re-authenticates — fine for single-process dev and stateless deployments.
 */
public final class InMemorySessionStore implements SessionStore {

    private final Map<String, SessionService.Session> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(SessionService.Session session) {
        if (session != null && session.id() != null) {
            sessions.put(session.id(), session);
        }
    }

    @Override
    public Optional<SessionService.Session> load(String id) {
        return Optional.ofNullable(id == null ? null : sessions.get(id));
    }

    @Override
    public Collection<SessionService.Session> all() {
        return List.copyOf(sessions.values());
    }

    @Override
    public int size() {
        return sessions.size();
    }
}
