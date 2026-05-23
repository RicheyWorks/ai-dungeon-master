package com.xai.dungeonmaster;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for DungeonMasterEngine.
 *
 * Verifies the basic invariants we rely on after Phase 0 hygiene work:
 *  - the engine boots without Spring, without Swing, without a server
 *  - addUiListener is additive (does not evict previously-registered listeners)
 *  - setUiUpdater still works as a single-listener reset (legacy contract)
 *  - startQuest returns a non-empty string after construction
 *  - getCurrentAvailableChoices never returns null
 */
class DungeonMasterEngineSmokeTest {

    private DungeonMasterEngine newEngine() {
        return new DungeonMasterEngine(
                3, 3,
                new String[] { "Kael", "Lira" },
                new String[] { "Warrior", "Mage" });
    }

    @Test
    void engineBootsHeadlessWithoutSpring() {
        DungeonMasterEngine engine = newEngine();
        assertNotNull(engine);
        assertTrue(engine.isServer(), "Engine should report server-mode true");
        assertTrue(engine.getChaosLevel() > 0);
    }

    @Test
    void startQuestReturnsNonEmpty() {
        DungeonMasterEngine engine = newEngine();
        String start = engine.startQuest();
        assertNotNull(start);
        assertFalse(start.isBlank(), "startQuest should return a non-empty banner");
    }

    @Test
    void availableChoicesNeverNull() {
        DungeonMasterEngine engine = newEngine();
        List<Choice> choices = engine.getCurrentAvailableChoices();
        assertNotNull(choices, "Choices list must never be null");
    }

    @Test
    void addUiListenerIsAdditive() {
        DungeonMasterEngine engine = newEngine();
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();

        engine.addUiListener(msg -> a.incrementAndGet());
        engine.addUiListener(msg -> b.incrementAndGet());

        engine.broadcast("test event");

        assertEquals(1, a.get(), "First listener should fire once");
        assertEquals(1, b.get(), "Second listener should also fire once — addUiListener must not evict");
    }

    @Test
    @SuppressWarnings("deprecation")
    void setUiUpdaterStillResetsListenersForLegacyCallers() {
        DungeonMasterEngine engine = newEngine();
        AtomicInteger original = new AtomicInteger();
        AtomicInteger replacement = new AtomicInteger();

        engine.addUiListener(msg -> original.incrementAndGet());
        engine.setUiUpdater(msg -> replacement.incrementAndGet());

        engine.broadcast("after reset");

        assertEquals(0, original.get(), "Original listener should be evicted by setUiUpdater (legacy semantics)");
        assertEquals(1, replacement.get(), "Replacement listener should receive the broadcast");
    }
}
