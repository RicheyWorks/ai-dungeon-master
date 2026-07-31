package com.xai.dungeonmaster.store;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process stand-in for Redis — used by unit tests and by the multi-node
 * store tests without a real server.
 */
public final class MemoryRedisOps implements RedisOps {

    private final Map<String, Map<String, String>> hashes = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sets = new ConcurrentHashMap<>();

    @Override
    public void hset(String key, Map<String, String> fields) {
        if (key == null) return;
        Map<String, String> copy = new LinkedHashMap<>();
        if (fields != null) {
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    copy.put(e.getKey(), e.getValue());
                }
            }
        }
        hashes.put(key, copy);
    }

    @Override
    public Map<String, String> hgetAll(String key) {
        Map<String, String> m = hashes.get(key);
        return m == null ? Map.of() : Map.copyOf(m);
    }

    @Override
    public void sadd(String key, String... members) {
        if (key == null || members == null) return;
        Set<String> set = sets.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        for (String m : members) {
            if (m != null) set.add(m);
        }
    }

    @Override
    public void srem(String key, String... members) {
        if (key == null || members == null) return;
        Set<String> set = sets.get(key);
        if (set == null) return;
        for (String m : members) {
            if (m != null) set.remove(m);
        }
    }

    @Override
    public Set<String> smembers(String key) {
        Set<String> set = sets.get(key);
        if (set == null || set.isEmpty()) return Set.of();
        return Set.copyOf(set);
    }

    @Override
    public void del(String key) {
        if (key == null) return;
        hashes.remove(key);
        sets.remove(key);
    }

    @Override
    public void close() {
        hashes.clear();
        sets.clear();
    }

    /** Snapshot of known set keys (test helper). */
    public Set<String> setKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(sets.keySet()));
    }
}
