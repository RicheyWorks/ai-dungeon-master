package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Entity;
import com.xai.dungeonmaster.Spell;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for the SpellEffect plugin SPI.
 *
 * The bundled SpellEffects (VOID_BOLT, TEMPORAL_HEAL, etc.) are auto-loaded
 * by ServiceLoader on first registry access, so we exercise dispatch end-to-end
 * without manually constructing them.
 */
class SpellEffectRegistryTest {

    @Test
    void bundledSpellEffectsAreDiscoveredViaServiceLoader() {
        // ServiceLoader fires on first lookup
        assertTrue(SpellEffectRegistry.isRegistered("VOID_BOLT"));
        assertTrue(SpellEffectRegistry.isRegistered("TEMPORAL_HEAL"));
        assertTrue(SpellEffectRegistry.isRegistered("CHRONO_STUN"));
        assertTrue(SpellEffectRegistry.isRegistered("RIFT_SHIELD"));
        assertTrue(SpellEffectRegistry.isRegistered("ARCANE_BLAST"));
        assertTrue(SpellEffectRegistry.isRegistered("GROUP_HEAL"));
    }

    @Test
    void dispatchReturnsNarrationForKnownSpellKey() {
        DungeonMasterEngine engine = new DungeonMasterEngine(
                3, 3,
                new String[] { "Kael" },
                new String[] { "Mage" });
        Adventurer caster = engine.getCombatState().getLivingPartyMembers().stream()
                .findFirst().orElseGet(() -> new Adventurer("Test", 12, 12, 12, 12, 12, 12, 100, "Mage"));
        // Target can be null for a self-target spell test (RIFT_SHIELD)
        Spell shield = new Spell("Rift Shield", 5, "RIFT_SHIELD", 10);

        String result = shield.cast(engine, caster, null);
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void unknownSpellKeyFallsBackToFizzleMessage() {
        DungeonMasterEngine engine = new DungeonMasterEngine(
                3, 3,
                new String[] { "Kael" },
                new String[] { "Mage" });
        Adventurer caster = new Adventurer("TestCaster", 12, 12, 12, 12, 12, 12, 100, "Mage");
        // Make sure caster has the mana to pass preflight, so we exercise dispatch
        caster.restoreMana(50);
        Spell unknown = new Spell("Mystery Spell", 1, "NOT_A_REAL_KEY", 5);

        String result = unknown.cast(engine, caster, null);
        assertNotNull(result);
        assertTrue(result.toLowerCase().contains("rift") || result.toLowerCase().contains("silent"),
                "Fallback narration should hint that the spell didn't resolve. Got: " + result);
    }

    @Test
    void registerOverridesExistingId() {
        SpellEffect dummy = new SpellEffect() {
            @Override public String id() { return "RIFT_SHIELD"; }
            @Override
            public String execute(DungeonMasterEngine engine, Adventurer caster, Entity target, Spell spell) {
                return "OVERRIDE FIRED";
            }
        };
        try {
            SpellEffectRegistry.register(dummy);
            DungeonMasterEngine engine = new DungeonMasterEngine(
                    3, 3, new String[] { "Kael" }, new String[] { "Mage" });
            Adventurer caster = new Adventurer("X", 12, 12, 12, 12, 12, 12, 100, "Mage");
            caster.restoreMana(50);
            Spell shield = new Spell("Rift Shield", 5, "RIFT_SHIELD", 10);
            String result = shield.cast(engine, caster, null);
            assertEquals("OVERRIDE FIRED", result);
        } finally {
            // Restore the bundled effect so other tests don't see the override
            SpellEffectRegistry.clearForTests();
        }
    }
}
