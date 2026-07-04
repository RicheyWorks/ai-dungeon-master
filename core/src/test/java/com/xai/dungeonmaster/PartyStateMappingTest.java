package com.xai.dungeonmaster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link DungeonMasterEngine#getPartyState()} maps the live party into
 * typed {@link MemberState} value objects consistent with the legacy string
 * summary.
 */
class PartyStateMappingTest {

    private DungeonMasterEngine newEngine() {
        return new DungeonMasterEngine(
                3, 3,
                new String[] { "Kael", "Lira" },
                new String[] { "Warrior", "Mage" });
    }

    @Test
    void getPartyStateReturnsOneMemberPerAdventurer() {
        PartyState ps = newEngine().getPartyState();
        assertNotNull(ps);
        assertEquals(2, ps.members().size(), "one MemberState per adventurer");
    }

    @Test
    void memberStateCarriesStructuredFields() {
        MemberState m = newEngine().getPartyState().members().get(0);
        assertEquals("Kael", m.name());
        assertEquals("Warrior", m.role());
        assertTrue(m.maxHp() > 0, "maxHp should be populated");
        assertTrue(m.hp() > 0 && m.hp() <= m.maxHp(), "hp within [1, maxHp]");
        assertTrue(m.alive());
        assertNotNull(m.statuses(), "statuses never null");
    }

    @Test
    void structuredPartyAgreesWithLegacySummary() {
        DungeonMasterEngine engine = newEngine();
        PartyState ps = engine.getPartyState();
        String summary = engine.getPartySummary();
        // Every structured member name must appear in the legacy string,
        // proving the two views describe the same party.
        for (MemberState m : ps.members()) {
            assertTrue(summary.contains(m.name()),
                    "legacy summary should mention " + m.name());
        }
        // All members start alive, so aliveCount tracks the roster size.
        assertEquals(ps.members().size(), ps.aliveCount());
    }
}
