package com.xai.dungeonmaster.store;

import java.util.Map;
import java.util.Set;

/**
 * Minimal Redis surface used by multi-node auth stores and shared rate limits.
 * Production wires {@link JedisRedisOps}; tests use {@link MemoryRedisOps}.
 */
public interface RedisOps extends AutoCloseable {

    /** Replace the entire hash at {@code key} with {@code fields}. */
    void hset(String key, Map<String, String> fields);

    /** Read the full hash (empty map if missing). */
    Map<String, String> hgetAll(String key);

    /** Add members to a set. */
    void sadd(String key, String... members);

    /** Remove members from a set. */
    void srem(String key, String... members);

    /** Members of a set (empty if missing). */
    Set<String> smembers(String key);

    /** Delete a key. */
    void del(String key);

    /**
     * Atomically increment {@code key} by 1 and return the new value.
     * Creates the key at 0 if missing.
     */
    default long incr(String key) {
        throw new UnsupportedOperationException("incr not supported");
    }

    /** Set TTL in seconds (no-op if key missing on some backends). */
    default void expire(String key, int seconds) {
        throw new UnsupportedOperationException("expire not supported");
    }

    /**
     * Connectivity probe for readiness checks.
     * Default: best-effort no-op success (in-memory / unused).
     */
    default boolean ping() {
        return true;
    }

    /** True when this ops instance is backed by a real network Redis. */
    default boolean isNetworked() {
        return false;
    }

    @Override
    void close();
}
