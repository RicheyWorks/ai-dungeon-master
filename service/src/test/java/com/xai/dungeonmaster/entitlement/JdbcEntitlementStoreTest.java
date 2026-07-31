package com.xai.dungeonmaster.entitlement;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JdbcEntitlementStoreTest {

    private HikariDataSource ds;

    @BeforeEach
    void open() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:dm_ents_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
    }

    @AfterEach
    void close() {
        ds.close();
    }

    @Test
    void grantIdempotentAndShared() {
        JdbcEntitlementStore a = new JdbcEntitlementStore(ds);
        JdbcEntitlementStore b = new JdbcEntitlementStore(ds);

        a.grant("sess", "sku_gold");
        a.grant("sess", "sku_gold");
        assertTrue(a.owns("sess", "sku_gold"));
        assertTrue(b.owns("sess", "sku_gold"));
        assertEquals(1, b.products("sess").size());
        assertFalse(b.owns("sess", "other"));
    }
}
