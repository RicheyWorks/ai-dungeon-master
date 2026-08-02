package com.xai.dungeonmaster.content;

import com.xai.dungeonmaster.store.MemoryRedisOps;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionPackStoreBackendsTest {

    @Test
    void redisSharedAcrossNodes() {
        MemoryRedisOps redis = new MemoryRedisOps();
        SessionPackStore a = new RedisSessionPackStore(redis, "dm");
        SessionPackStore b = new RedisSessionPackStore(redis, "dm");
        a.put("alice", "dlc", true);
        assertEquals(true, b.get("alice", "dlc").orElse(false));
        assertTrue(b.all("alice").get("dlc"));
        a.put("alice", "dlc", null);
        assertTrue(b.get("alice", "dlc").isEmpty());
    }

    @Test
    void jdbcSharedAcrossNodes() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:dm_session_packs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        try (HikariDataSource ds = new HikariDataSource(cfg)) {
            SessionPackStore a = new JdbcSessionPackStore(ds);
            SessionPackStore b = new JdbcSessionPackStore(ds);
            a.put("bob", "horror", true);
            a.put("bob", "horror", true); // idempotent upsert
            assertEquals(true, b.get("bob", "horror").orElse(false));
            a.put("bob", "horror", false);
            assertEquals(false, b.get("bob", "horror").orElse(true));
            a.put("bob", "horror", null);
            assertTrue(b.get("bob", "horror").isEmpty());
        }
    }
}
