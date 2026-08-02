package com.xai.dungeonmaster.content;

import com.xai.dungeonmaster.store.RedisOps;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Redis-backed {@link SessionPackStore}.
 * Key: {@code {prefix}:session-packs:{sessionId}} hash of packId → "1"|"0".
 */
public final class RedisSessionPackStore implements SessionPackStore {

    private final RedisOps redis;
    private final String prefix;

    public RedisSessionPackStore(RedisOps redis) {
        this(redis, "dm");
    }

    public RedisSessionPackStore(RedisOps redis, String keyPrefix) {
        this.redis = redis;
        this.prefix = (keyPrefix == null || keyPrefix.isBlank()) ? "dm" : keyPrefix.trim();
    }

    @Override
    public Optional<Boolean> get(String sessionId, String packId) {
        if (sessionId == null || packId == null) return Optional.empty();
        Map<String, String> fields = redis.hgetAll(key(sessionId));
        if (fields == null || !fields.containsKey(packId)) return Optional.empty();
        return Optional.of(parseBool(fields.get(packId)));
    }

    @Override
    public Map<String, Boolean> all(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return Map.of();
        Map<String, String> fields = redis.hgetAll(key(sessionId));
        if (fields == null || fields.isEmpty()) return Map.of();
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (e.getKey() != null) out.put(e.getKey(), parseBool(e.getValue()));
        }
        return Map.copyOf(out);
    }

    @Override
    public void put(String sessionId, String packId, Boolean enabled) {
        if (sessionId == null || sessionId.isBlank() || packId == null || packId.isBlank()) {
            return;
        }
        String k = key(sessionId);
        if (enabled == null) {
            Map<String, String> fields = new LinkedHashMap<>(redis.hgetAll(k));
            if (fields.remove(packId) != null) {
                if (fields.isEmpty()) {
                    redis.del(k);
                } else {
                    redis.hset(k, fields);
                }
            }
            return;
        }
        Map<String, String> fields = new LinkedHashMap<>(redis.hgetAll(k));
        fields.put(packId, enabled ? "1" : "0");
        redis.hset(k, fields);
    }

    private String key(String sessionId) {
        return prefix + ":session-packs:" + sessionId;
    }

    private static boolean parseBool(String v) {
        if (v == null) return false;
        return "1".equals(v) || "true".equalsIgnoreCase(v.trim());
    }
}
