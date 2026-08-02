package com.xai.dungeonmaster.content;

import com.xai.dungeonmaster.plugin.ContentPack;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SessionPackServiceTest {

    @BeforeEach
    void setUp() {
        ContentRegistry.clearForTests();
        ContentRegistry.register(new Pack("free", List.of()));
        ContentRegistry.register(new Pack("dlc", List.of("sku_dlc")));
        ContentRegistry.setEnabled("dlc", false);
    }

    @AfterEach
    void tearDown() {
        ContentRegistry.clearForTests();
    }

    @Test
    void sessionsIsolated() {
        SessionPackService svc = new SessionPackService(new MemorySessionPackStore(), true);
        assertFalse(svc.isEnabled("alice", "dlc"));
        assertTrue(svc.setEnabled("alice", "dlc", true));
        assertTrue(svc.isEnabled("alice", "dlc"));
        assertFalse(svc.isEnabled("bob", "dlc"));
        assertFalse(ContentRegistry.isProcessEnabled("dlc"));
        assertTrue(svc.isEnabled("bob", "free"));
    }

    @Test
    void overridePoolsForGeneration() {
        SessionPackService svc = new SessionPackService(new MemorySessionPackStore(), true);
        svc.setEnabled("alice", "dlc", true);
        ContentRegistry.pushEnabledOverride(svc.enabledPackIds("alice"));
        try {
            assertTrue(ContentRegistry.isEnabled("dlc"));
            assertTrue(ContentRegistry.isEnabled("free"));
        } finally {
            ContentRegistry.clearEnabledOverride();
        }
        assertFalse(ContentRegistry.isEnabled("dlc"));
    }

    private static final class Pack implements ContentPack {
        private final String id;
        private final List<String> required;
        Pack(String id, List<String> required) {
            this.id = id;
            this.required = required;
        }
        @Override public String id() { return id; }
        @Override public String displayName() { return id; }
        @Override public String version() { return "1"; }
        @Override public List<String> requiredProductIds() { return required; }
        @Override public Map<String, com.xai.dungeonmaster.Item> items() { return Collections.emptyMap(); }
        @Override public Map<String, com.xai.dungeonmaster.Enemy> monsters() { return Collections.emptyMap(); }
    }
}
