package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Enemy;
import com.xai.dungeonmaster.Item;
import com.xai.dungeonmaster.Quest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for the four SPIs wired in this pass — EncounterTable, LootTable,
 * QuestScript, and StorefrontIntegration. Each mirrors the SpellEffect pattern:
 * bundled built-ins are auto-discovered by ServiceLoader on first registry
 * access, dispatch resolves a biome/id (falling back to the default), and
 * explicit register() overrides a binding.
 */
class SpiRegistryTest {

    // ── EncounterTable ────────────────────────────────────────────────────────

    @Test
    void encounterBuiltinDiscoveredAndDispatches() {
        assertTrue(EncounterTableRegistry.isRegistered("default"),
                "DefaultEncounterTable should register via ServiceLoader");

        List<Enemy> mob = EncounterTableRegistry.dispatch(
                new Random(1), 2, 1, false, "default");
        assertFalse(mob.isEmpty(), "default encounter table must yield at least one enemy");
        assertTrue(mob.get(0).getMaxHp() > 0);

        // Unknown biome falls back to the default table rather than returning empty.
        List<Enemy> fallback = EncounterTableRegistry.dispatch(
                new Random(1), 2, 1, false, "no-such-biome");
        assertFalse(fallback.isEmpty(), "unknown biome should fall back to the default table");
    }

    @Test
    void encounterOverrideReplacesDefaultBiome() {
        EncounterTable dummy = new EncounterTable() {
            @Override public String id() { return "ENCOUNTER_TEST"; }
            @Override public String biome() { return "default"; }
            @Override public List<Enemy> roll(Random random, int difficulty, int chaos, boolean isBoss) {
                return List.of(new Enemy("Sentinel Override", 99, 10, 1, 1));
            }
        };
        try {
            EncounterTableRegistry.register(dummy);
            List<Enemy> rolled = EncounterTableRegistry.dispatch(new Random(), 1, 0, false, "default");
            assertEquals("Sentinel Override", rolled.get(0).getName());
        } finally {
            EncounterTableRegistry.clearForTests(); // restores the ServiceLoader built-in
        }
    }

    // ── LootTable ─────────────────────────────────────────────────────────────

    @Test
    void lootBuiltinDiscoveredAndDispatches() {
        assertTrue(LootTableRegistry.isRegistered("default"),
                "DefaultLootTable should register via ServiceLoader");

        Item drop = LootTableRegistry.dispatch(new Random(2), 3, 2, "default");
        assertNotNull(drop, "default loot table should produce a drop");
        assertTrue(drop.getPower() > 0);

        // Unknown biome falls back to the default table.
        Item fallback = LootTableRegistry.dispatch(new Random(2), 3, 2, "no-such-biome");
        assertNotNull(fallback, "unknown biome should fall back to the default table");
    }

    // ── QuestScript ───────────────────────────────────────────────────────────

    @Test
    void questBuiltinDiscoveredAndBuilds() {
        assertTrue(QuestScriptRegistry.isRegistered("default"),
                "DefaultQuestScript should register via ServiceLoader");

        Quest quest = QuestScriptRegistry.dispatch("default", null, 3, 3);
        assertNotNull(quest);
        assertFalse(quest.getScenes().isEmpty(), "the default quest must contain scenes");

        // Unknown id falls back to the default script.
        Quest fallback = QuestScriptRegistry.dispatch("no-such-script", null, 3, 3);
        assertNotNull(fallback, "unknown script id should fall back to the default script");
        assertFalse(fallback.getScenes().isEmpty());
    }

    // ── StorefrontIntegration ─────────────────────────────────────────────────

    @Test
    void storefrontFallbackAlwaysAvailable() {
        assertTrue(StorefrontRegistry.isRegistered("none"),
                "NoOpStorefront should register via ServiceLoader");

        StorefrontIntegration active = StorefrontRegistry.getActive();
        assertNotNull(active, "getActive() must never return null");
        assertFalse(active.currentIdentity().signedIn, "offline storefront is not signed in");
        assertFalse(active.verifyReceipt("anything"), "offline storefront cannot verify receipts");
        assertFalse(active.openCloudSave("slot0").isAvailable(), "offline cloud save is unavailable");
    }

    @Test
    void storefrontSelectionFallsBackForUnknownId() {
        // Selecting an unknown store leaves the always-available no-op active.
        assertFalse(StorefrontRegistry.setActive("steam-not-installed"));
        assertNotNull(StorefrontRegistry.getActive());
    }
}
