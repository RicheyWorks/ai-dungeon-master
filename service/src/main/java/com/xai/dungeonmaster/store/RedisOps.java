package com.xai.dungeonmaster.store;

import java.util.Map;
import java.util.Set;

/**
 * Minimal Redis surface used by multi-node auth stores. Production wires
 * {@link JedisRedisOps}; tests use {@link MemoryRedisOps}.
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
