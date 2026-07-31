package com.xai.dungeonmaster.auth;

import com.xai.dungeonmaster.store.RedisOps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Redis-backed {@link SessionStore} for multi-node deployments that share a
 * Redis instance (and a shared JWT secret). Keys:
 * <ul>
 *   <li>{@code {prefix}:session:{id}} — hash (displayName, createdAt, lastSeen)</li>
 *   <li>{@code {prefix}:sessions} — set of session ids</li>
 * </ul>
 * Game engines remain process-local; put a sticky load balancer in front or
 * accept that a session's world may cold-start on another node (autoload from
 * shared saves dir still works).
 */
public final class RedisSessionStore implements SessionStore {

    private final RedisOps redis;
    private final String prefix;

    public RedisSessionStore(RedisOps redis) {
        this(redis, "dm");
    }

    public RedisSessionStore(RedisOps redis, String keyPrefix) {
        this.redis = redis;
        this.prefix = (keyPrefix == null || keyPrefix.isBlank()) ? "dm" : keyPrefix.trim();
    }

    @Override
    public void save(SessionService.Session session) {
        if (session == null || session.id() == null) return;
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("displayName", session.displayName() == null ? "Guest" : session.displayName());
        fields.put("createdAt", Long.toString(session.createdAtEpoch()));
        fields.put("lastSeen", Long.toString(session.lastSeenEpoch()));
        redis.hset(sessionKey(session.id()), fields);
        redis.sadd(indexKey(), session.id());
    }

    @Override
    public Optional<SessionService.Session> load(String id) {
        if (id == null) return Optional.empty();
        Map<String, String> fields = redis.hgetAll(sessionKey(id));
        if (fields.isEmpty()) return Optional.empty();
        return Optional.of(fromFields(id, fields));
    }

    @Override
    public Collection<SessionService.Session> all() {
        Set<String> ids = redis.smembers(indexKey());
        if (ids.isEmpty()) return List.of();
        List<SessionService.Session> out = new ArrayList<>();
        for (String id : ids) {
            Map<String, String> fields = redis.hgetAll(sessionKey(id));
            if (!fields.isEmpty()) {
                out.add(fromFields(id, fields));
            }
        }
        return List.copyOf(out);
    }

    @Override
    public int size() {
        return redis.smembers(indexKey()).size();
    }

    private String sessionKey(String id) {
        return prefix + ":session:" + id;
    }

    private String indexKey() {
        return prefix + ":sessions";
    }

    private static SessionService.Session fromFields(String id, Map<String, String> fields) {
        String name = fields.getOrDefault("displayName", "Guest");
        long created = parseLong(fields.get("createdAt"), 0L);
        long lastSeen = parseLong(fields.get("lastSeen"), created);
        return new SessionService.Session(id, name, created, lastSeen);
    }

    private static long parseLong(String s, long fallback) {
        if (s == null || s.isBlank()) return fallback;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
