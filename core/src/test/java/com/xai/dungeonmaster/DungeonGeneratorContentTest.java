package com.xai.dungeonmaster;

import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.plugin.DefaultContentPack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that monster stats from monsters.json actually load and drive
 * DungeonGenerator enemy generation — not just the names. Guards against the
 * regression where pack keys (baseHp/baseAc/levelRequirement/isBoss) silently
 * failed to map onto Enemy and defaulted to hp=1.
 */
class DungeonGeneratorContentTest {

    @BeforeEach
    void loadBundledPack() {
        ContentRegistry.clearForTests();
        ContentRegistry.register(new DefaultContentPack());
    }

    @AfterEach
    void reset() {
        ContentRegistry.clearForTests();
    }

    @Test
    void monsterStatsLoadFromJsonViaAlias() {
        Map<String, Enemy> monsters = ContentRegistry.monsters();
        assertFalse(monsters.isEmpty(), "bundled monsters.json should load");

        Enemy stalker = monsters.values().stream()
                .filter(e -> e.getName().equals("Rift Stalker"))
                .findFirst().orElseThrow();
        assertEquals(40, stalker.getMaxHp(), "baseHp must map to hp (previously dropped -> 1)");
        assertEquals(12, stalker.getAC(), "baseAc must map to ac");
    }

    @Test
    void bossFlagLoadsFromJson() {
        Enemy fragment = ContentRegistry.monsters().values().stream()
                .filter(e -> e.getName().contains("Fragment"))
                .findFirst().orElseThrow();
        assertTrue(fragment.isBoss(), "isBoss must map from monsters.json");
    }

    @Test
    void generatorScalesFromLoadedBaseStats() {
        DungeonGenerator gen = new DungeonGenerator(new Random(1), 2, 1);
        Enemy boss = gen.generateEnemy(true);
        Enemy mob = gen.generateEnemy(false);
        // Boss baseHp 500 vs mob baseHp 40 -> boss must far outscale the mob,
        // proving the JSON stats (not synthetic constants) drove generation.
        assertTrue(boss.getMaxHp() > mob.getMaxHp(),
                "boss (JSON baseHp 500) should outscale the mob (baseHp 40)");
    }
}
