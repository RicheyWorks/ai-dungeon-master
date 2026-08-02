package com.xai.dungeonmaster.content;

import com.xai.dungeonmaster.auth.InMemorySessionStore;
import com.xai.dungeonmaster.auth.JwtService;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.plugin.ContentPack;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SessionHygieneTest {

    @BeforeEach
    void setUp() {
        ContentRegistry.clearForTests();
        ContentRegistry.register(new Pack("dlc"));
        ContentRegistry.setEnabled("dlc", false);
    }

    @AfterEach
    void tearDown() {
        ContentRegistry.clearForTests();
    }

    @Test
    void purgeClearsPackOverrides() {
        SessionService sessions = new SessionService(
                new JwtService("test-secret-at-least-32-characters!!", 3600L),
                new InMemorySessionStore());
        SessionPackService packs = new SessionPackService(new MemorySessionPackStore(), true);
        SessionService.Issued issued = sessions.createSession("Alice");
        String id = issued.session().id();
        packs.setEnabled(id, "dlc", true);
        assertTrue(packs.isEnabled(id, "dlc"));

        SessionHygieneReaper reaper = new SessionHygieneReaper(
                sessions, packs, null, true, 1L);
        long farFuture = issued.session().lastSeenEpoch() + 10_000L;
        int n = reaper.purgeNow(farFuture);
        assertEquals(1, n);
        assertTrue(sessions.find(id).isEmpty());
        assertFalse(packs.isEnabled(id, "dlc"));
    }

    @Test
    void storeClearRemovesAll() {
        MemorySessionPackStore store = new MemorySessionPackStore();
        store.put("s1", "a", true);
        store.put("s1", "b", false);
        store.clear("s1");
        assertTrue(store.all("s1").isEmpty());
    }

    private static final class Pack implements ContentPack {
        private final String id;
        Pack(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public String displayName() { return id; }
        @Override public String version() { return "1"; }
        @Override public Map<String, com.xai.dungeonmaster.Item> items() { return Collections.emptyMap(); }
        @Override public Map<String, com.xai.dungeonmaster.Enemy> monsters() { return Collections.emptyMap(); }
    }
}
