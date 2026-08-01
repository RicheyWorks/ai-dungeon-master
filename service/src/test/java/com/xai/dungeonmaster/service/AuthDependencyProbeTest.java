package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.store.MemoryRedisOps;
import com.xai.dungeonmaster.store.RedisOps;
import com.xai.dungeonmaster.store.UnusedDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthDependencyProbeTest {

    @Test
    void memoryStoresAreReadyWithoutDeps() {
        AuthDependencyProbe probe = new AuthDependencyProbe(
                new UnusedDataSource(), new MemoryRedisOps(), "memory", "memory");
        AuthDependencyProbe.Result r = probe.probe();
        assertTrue(r.ready());
        assertEquals("NOT_CONFIGURED", status(r, "jdbc"));
        assertEquals("NOT_CONFIGURED", status(r, "redis"));
        assertEquals("NOT_CONFIGURED", status(r, "file"));
    }

    @Test
    void jdbcUpWhenH2Works() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:ready_probe;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(2);
        try (HikariDataSource ds = new HikariDataSource(cfg)) {
            AuthDependencyProbe probe = new AuthDependencyProbe(ds, new MemoryRedisOps(), "jdbc", "jdbc");
            AuthDependencyProbe.Result r = probe.probe();
            assertTrue(r.ready(), r.checks()::toString);
            assertEquals("UP", status(r, "jdbc"));
        }
    }

    @Test
    void redisDownWhenPingFails() {
        RedisOps dead = new RedisOps() {
            @Override public void hset(String key, Map<String, String> fields) {}
            @Override public Map<String, String> hgetAll(String key) { return Map.of(); }
            @Override public void sadd(String key, String... members) {}
            @Override public void srem(String key, String... members) {}
            @Override public java.util.Set<String> smembers(String key) { return java.util.Set.of(); }
            @Override public void del(String key) {}
            @Override public boolean ping() { return false; }
            @Override public boolean isNetworked() { return true; }
            @Override public void close() {}
        };
        AuthDependencyProbe probe = new AuthDependencyProbe(
                new UnusedDataSource(), dead, "redis", "redis");
        AuthDependencyProbe.Result r = probe.probe();
        assertFalse(r.ready());
        assertEquals("DOWN", status(r, "redis"));
    }

    @Test
    void fileStoreChecksParentWritable(@TempDir Path tmp) {
        Path session = tmp.resolve("sessions.json");
        Path ents = tmp.resolve("ents.json");
        AuthDependencyProbe probe = new AuthDependencyProbe(
                new UnusedDataSource(),
                new MemoryRedisOps(),
                "file",
                "file",
                session.toString(),
                ents.toString());
        AuthDependencyProbe.Result r = probe.probe();
        assertTrue(r.ready(), r.checks()::toString);
        assertEquals("UP", status(r, "file"));
    }

    @SuppressWarnings("unchecked")
    private static String status(AuthDependencyProbe.Result r, String name) {
        Map<String, Object> m = (Map<String, Object>) r.checks().get(name);
        return (String) m.get("status");
    }
}
