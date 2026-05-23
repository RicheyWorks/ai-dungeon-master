package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Item;

import java.util.Random;

/**
 * Procedurally generates a loot drop appropriate for the current biome /
 * difficulty / chaos level. Replaces the hardcoded item-name arrays in
 * DungeonGenerator.
 *
 * Like EncounterTable, biome-keyed so content packs can ship themed loot.
 */
public interface LootTable extends Plugin {

    String biome();

    /**
     * Roll a single item drop. May return null to indicate "no drop".
     */
    Item roll(Random random, int difficulty, int chaos);
}
