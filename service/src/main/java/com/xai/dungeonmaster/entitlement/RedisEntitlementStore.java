package com.xai.dungeonmaster.entitlement;

import com.xai.dungeonmaster.store.RedisOps;

import java.util.Set;

/**
 * Redis-backed {@link EntitlementStore}. Products for a session live in a set
 * at {@code {prefix}:entitlements:{sessionId}}.
 */
public final class RedisEntitlementStore implements EntitlementStore {

    private final RedisOps redis;
    private final String prefix;

    public RedisEntitlementStore(RedisOps redis) {
        this(redis, "dm");
    }

    public RedisEntitlementStore(RedisOps redis, String keyPrefix) {
        this.redis = redis;
        this.prefix = (keyPrefix == null || keyPrefix.isBlank()) ? "dm" : keyPrefix.trim();
    }

    @Override
    public Set<String> products(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return Set.of();
        return redis.smembers(key(sessionId));
    }

    @Override
    public void grant(String sessionId, String productId) {
        if (sessionId == null || sessionId.isBlank() || productId == null || productId.isBlank()) {
            return;
        }
        redis.sadd(key(sessionId), productId);
    }

    private String key(String sessionId) {
        return prefix + ":entitlements:" + sessionId;
    }
}
