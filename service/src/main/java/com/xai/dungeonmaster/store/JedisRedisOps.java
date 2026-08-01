package com.xai.dungeonmaster.store;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Jedis-backed {@link RedisOps}. Connection URL forms:
 * {@code redis://[:password@]host:port[/db]}, {@code rediss://…} for TLS.
 */
public final class JedisRedisOps implements RedisOps {

    private final JedisPool pool;

    public JedisRedisOps(String url) {
        this(createPool(url));
    }

    public JedisRedisOps(JedisPool pool) {
        this.pool = pool;
    }

    private static JedisPool createPool(String url) {
        String effective = (url == null || url.isBlank()) ? "redis://127.0.0.1:6379" : url.trim();
        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(16);
        cfg.setMaxIdle(8);
        cfg.setMinIdle(0);
        cfg.setMaxWait(Duration.ofSeconds(2));
        cfg.setTestOnBorrow(true);
        URI uri = URI.create(effective);
        return new JedisPool(cfg, uri);
    }

    @Override
    public void hset(String key, Map<String, String> fields) {
        if (key == null) return;
        try (Jedis j = pool.getResource()) {
            if (fields == null || fields.isEmpty()) {
                j.del(key);
                return;
            }
            Map<String, String> safe = new HashMap<>();
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    safe.put(e.getKey(), e.getValue());
                }
            }
            if (safe.isEmpty()) {
                j.del(key);
            } else {
                j.hset(key, safe);
            }
        }
    }

    @Override
    public Map<String, String> hgetAll(String key) {
        if (key == null) return Map.of();
        try (Jedis j = pool.getResource()) {
            Map<String, String> m = j.hgetAll(key);
            return m == null || m.isEmpty() ? Map.of() : Map.copyOf(m);
        }
    }

    @Override
    public void sadd(String key, String... members) {
        if (key == null || members == null || members.length == 0) return;
        try (Jedis j = pool.getResource()) {
            j.sadd(key, members);
        }
    }

    @Override
    public void srem(String key, String... members) {
        if (key == null || members == null || members.length == 0) return;
        try (Jedis j = pool.getResource()) {
            j.srem(key, members);
        }
    }

    @Override
    public Set<String> smembers(String key) {
        if (key == null) return Set.of();
        try (Jedis j = pool.getResource()) {
            Set<String> m = j.smembers(key);
            if (m == null || m.isEmpty()) return Set.of();
            return Set.copyOf(new HashSet<>(m));
        }
    }

    @Override
    public void del(String key) {
        if (key == null) return;
        try (Jedis j = pool.getResource()) {
            j.del(key);
        }
    }

    @Override
    public boolean ping() {
        try (Jedis j = pool.getResource()) {
            String pong = j.ping();
            return pong != null && pong.equalsIgnoreCase("PONG");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isNetworked() {
        return true;
    }

    @Override
    public void close() {
        pool.close();
    }

    public JedisPool pool() {
        return pool;
    }
}
