package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.DungeonGenerator;
import com.xai.dungeonmaster.Enemy;
import com.xai.dungeonmaster.Faction;
import com.xai.dungeonmaster.WorldState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ADR-001 Phase 4 follow-up: faction-aware encounter context. The world-aware
 * roll overload lets tables read faction reputation and story flags; legacy
 * 4-arg tables keep working through the additive default.
 */
class FactionEncounterTest {

    /** Table that spawns revenants only when the Drowned hate the party. */
    private static final EncounterTable REPUTATION_TABLE = new EncounterTable() {
        @Override public String id() { return "rep-table"; }
        @Override public String displayName() { return "rep-table"; }
        @Override public String biome() { return "default"; }

        @Override
        public List<Enemy> roll(Random random, int difficulty, int chaos, boolean isBoss) {
            return List.of(new Enemy("Marsh Husk", 40, 8, 3, difficulty));
        }

        @Override
        public List<Enemy> roll(Random random, int difficulty, int chaos, boolean isBoss,
                                WorldState world) {
            if (world != null && world.getFlag(Faction.reputationFlag("the-drowned")) < 0) {
                return List.of(new Enemy("Vengeful Revenant", 60, 12, 4, difficulty));
            }
            return roll(random, difficulty, chaos, isBoss);
        }
    };

    /** Pre-world-context table: only implements the 4-arg roll. */
    private static final EncounterTable LEGACY_TABLE = new EncounterTable() {
        @Override public String id() { return "legacy-table"; }
        @Override public String displayName() { return "legacy-table"; }
        @Override public String biome() { return "default"; }
        @Override
        public List<Enemy> roll(Random random, int difficulty, int chaos, boolean isBoss) {
            return List.of(new Enemy("Plain Husk", 40, 8, 3, difficulty));
        }
    };

    @BeforeEach
    void reset() {
        EncounterTableRegistry.clearForTests();
        // Force ServiceLoader discovery NOW so the bundled default table can't
        // overwrite the per-test registration on first dispatch.
        EncounterTableRegistry.registeredBiomes();
    }

    @AfterEach
    void cleanup() {
        EncounterTableRegistry.clearForTests();
    }

    @Test
    void worldAwareTableSeesFactionReputation() {
        EncounterTableRegistry.register(REPUTATION_TABLE);
        WorldState world = new WorldState();

        List<Enemy> neutral = EncounterTableRegistry.dispatch(
                new Random(1), 2, 0, false, "default", world);
        assertEquals("Marsh Husk", neutral.get(0).getName());

        world.addFlag(Faction.reputationFlag("the-drowned"), -2);
        List<Enemy> hostile = EncounterTableRegistry.dispatch(
                new Random(1), 2, 0, false, "default", world);
        assertEquals("Vengeful Revenant", hostile.get(0).getName());
    }

    @Test
    void legacyTablesIgnoreWorldContextViaDefaultMethod() {
        EncounterTableRegistry.register(LEGACY_TABLE);
        WorldState world = new WorldState();
        world.addFlag(Faction.reputationFlag("the-drowned"), -5);

        List<Enemy> rolled = EncounterTableRegistry.dispatch(
                new Random(1), 2, 0, false, "default", world);
        assertEquals("Plain Husk", rolled.get(0).getName());
    }

    @Test
    void generatorPassesWorldThroughToTables() {
        EncounterTableRegistry.register(REPUTATION_TABLE);
        WorldState world = new WorldState();
        world.addFlag(Faction.reputationFlag("the-drowned"), -1);

        DungeonGenerator generator = new DungeonGenerator(new Random(1), 2, 0);
        Enemy enemy = generator.generateEnemy(false, world);

        assertEquals("Vengeful Revenant", enemy.getName());
    }

    @Test
    void fourArgDispatchStillWorksWithoutWorld() {
        EncounterTableRegistry.register(REPUTATION_TABLE);
        List<Enemy> rolled = EncounterTableRegistry.dispatch(new Random(1), 2, 0, false, "default");
        assertEquals("Marsh Husk", rolled.get(0).getName());
    }
}
