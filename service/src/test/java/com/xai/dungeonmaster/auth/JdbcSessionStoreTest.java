package com.xai.dungeonmaster.auth;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JdbcSessionStoreTest {

    private HikariDataSource ds;

    @BeforeEach
    void open() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:dm_sessions_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
    }

    @AfterEach
    void close() {
        ds.close();
    }

    @Test
    void roundTripAndShare() {
        JdbcSessionStore a = new JdbcSessionStore(ds);
        JdbcSessionStore b = new JdbcSessionStore(ds);

        a.save(new SessionService.Session("s1", "Kael", 10L, 20L));
        Optional<SessionService.Session> got = b.load("s1");
        assertTrue(got.isPresent());
        assertEquals("Kael", got.get().displayName());
        assertEquals(10L, got.get().createdAtEpoch());
        assertEquals(20L, got.get().lastSeenEpoch());

        b.save(new SessionService.Session("s1", "Kael", 10L, 99L));
        assertEquals(99L, a.load("s1").orElseThrow().lastSeenEpoch());
        assertEquals(1, a.size());
        assertEquals(1, a.all().size());
    }

    @Test
    void sessionServiceAcrossNodes() {
        JwtService jwt = new JwtService("jdbc-test-secret-abcdefghijklmnop", 3600);
        SessionService n1 = new SessionService(jwt, new JdbcSessionStore(ds));
        SessionService.Issued issued = n1.createSession("Lira");

        SessionService n2 = new SessionService(jwt, new JdbcSessionStore(ds));
        assertTrue(n2.find(issued.session().id()).isPresent());
        assertEquals("Lira", n2.find(issued.session().id()).get().displayName());
    }
}
