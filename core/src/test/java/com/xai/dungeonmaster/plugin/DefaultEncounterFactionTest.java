package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.DungeonGenerator;
import com.xai.dungeonmaster.Enemy;
import com.xai.dungeonmaster.Faction;
import com.xai.dungeonmaster.WorldState;
import com.xai.dungeonmaster.plugin.builtin.DefaultEncounterTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production path for ADR-001 faction-aware encounters: the bundled
 * {@link DefaultEncounterTable} weights content-pack monsters by reputation
 * when a WorldState is provided.
 */
class DefaultEncounterFactionTest {

    @BeforeEach
    void seedPack() {
        ContentRegistry.clearForTests();
        EncounterTableRegistry.clearForTests();
        EncounterTableRegistry.registeredBiomes(); // discover default table

        Map<String, Enemy> monsters = new HashMap<>();
        Enemy husk = new Enemy("Marsh Husk", 40, 8, 3, 1);
        // untagged — neutral weight 1
        monsters.put("husk", husk);

        Enemy revenant = new Enemy("Vengeful Revenant", 60, 12, 4, 2);
        revenant.setFactionId("the-drowned");
        monsters.put("revenant", revenant);

        Map<String, Faction> factions = Map.of(
                "the-drowned",
                new Faction("the-drowned", "The Drowned", "the restless dead", -2));

        ContentRegistry.register(new ContentPack() {
            @Override public String id() { return "test-faction-pack"; }
            @Override public String displayName() { return "test"; }
            @Override public String version() { return "1.0"; }
            @Override public Map<String, com.xai.dungeonmaster.Item> items() { return Map.of(); }
            @Override public Map<String, Enemy> monsters() { return monsters; }
            @Override public Map<String, String> strings() { return Map.of(); }
            @Override public Map<String, com.xai.dungeonmaster.Npc> npcs() { return Map.of(); }
            @Override public Map<String, Faction> factions() { return factions; }
        });
    }

    @AfterEach
    void cleanup() {
        ContentRegistry.clearForTests();
        EncounterTableRegistry.clearForTests();
    }

    @Test
    void weightForFavorsHostileFactions() {
        WorldState world = new WorldState();
        // base -2 + flag 0 = -2 → weight 4 for drowned; untagged stays 1
        Enemy drowned = ContentRegistry.monsters().get("revenant");
        Enemy husk = ContentRegistry.monsters().get("husk");
        assertEquals(4, DefaultEncounterTable.weightFor(drowned, world));
        assertEquals(1, DefaultEncounterTable.weightFor(husk, world));

        world.addFlag(Faction.reputationFlag("the-drowned"), -2); // effective -4 → 8
        assertEquals(8, DefaultEncounterTable.weightFor(drowned, world));

        world.addFlag(Faction.reputationFlag("the-drowned"), 10); // effective +6 → 1
        assertEquals(1, DefaultEncounterTable.weightFor(drowned, world));
    }

    @Test
    void hostileReputationBiasesDefaultTableTowardFactionMonsters() {
        WorldState world = new WorldState();
        world.addFlag(Faction.reputationFlag("the-drowned"), -5); // base-2 + (-5) = -7

        int revenantHits = 0;
        int trials = 200;
        for (int i = 0; i < trials; i++) {
            List<Enemy> rolled = EncounterTableRegistry.dispatch(
                    new Random(i * 31L + 7), 2, 0, false, "default", world);
            if ("Vengeful Revenant".equals(rolled.get(0).getName())) {
                revenantHits++;
            }
        }
        // Weight 8 vs 1 → expected ~89% revenant; require clear majority.
        assertTrue(revenantHits > trials * 0.6,
                "hostile reputation should favor faction monsters; hits=" + revenantHits);
    }

    @Test
    void generatorPropagatesFactionIdOntoSpawnedEnemy() {
        WorldState world = new WorldState();
        world.addFlag(Faction.reputationFlag("the-drowned"), -5);

        DungeonGenerator gen = new DungeonGenerator(new Random(42), 2, 0);
        // Force many rolls until we see a faction-tagged spawn.
        boolean sawFaction = false;
        for (int i = 0; i < 50; i++) {
            Enemy e = gen.generateEnemy(false, world);
            if ("the-drowned".equals(e.getFactionId())) {
                sawFaction = true;
                break;
            }
        }
        assertTrue(sawFaction, "factionId should copy from template onto spawned enemy");
    }

    @Test
    void withoutWorldContextUntaggedAndTaggedStillRoll() {
        List<Enemy> rolled = EncounterTableRegistry.dispatch(
                new Random(1), 2, 0, false, "default", null);
        assertFalse(rolled.isEmpty());
        assertNotNull(rolled.get(0).getName());
    }
}
