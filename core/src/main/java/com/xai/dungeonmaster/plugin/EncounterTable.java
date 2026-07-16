package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Enemy;

import java.util.List;
import java.util.Random;

/**
 * Procedurally generates an encounter (one or more enemies) appropriate
 * for the current biome / difficulty / chaos level. Replaces the hardcoded
 * enemy-name arrays in DungeonGenerator.
 *
 * Content packs register one EncounterTable per biome (Hearthwood Cozy,
 * Black Hollows Horror, etc.). Mods can register additional tables that
 * coexist with the bundled ones — the engine asks the registry for a
 * table matching a tag (biome name) and falls back to a "default" table
 * if no match.
 */
public interface EncounterTable extends Plugin {

    /**
     * The biome / theme tag this table applies to (e.g., "cozy", "scifi").
     */
    String biome();

    /**
     * Roll an encounter for the given difficulty and chaos.
     *
     * @param random      seeded RNG — use this, don't create your own
     * @param difficulty  engine difficulty setting (>= 1)
     * @param chaos       engine chaos level (>= 0)
     * @param isBoss      true if the caller specifically wants a boss spawn
     * @return one or more enemies; never null, never empty
     */
    List<Enemy> roll(Random random, int difficulty, int chaos, boolean isBoss);

    /**
     * World-aware variant (ADR-001 Phase 4 follow-up): the engine passes its
     * {@link com.xai.dungeonmaster.WorldState} so tables can read faction
     * reputation ({@code faction:<id>:reputation} flags), quest outcomes, and
     * story flags — e.g. spawning hostile parish revenants only after the
     * party burned the weeping tree.
     *
     * Additive default: tables that predate world context ignore it and keep
     * their 4-arg behavior. {@code world} may be null (no engine context).
     */
    default List<Enemy> roll(Random random, int difficulty, int chaos, boolean isBoss,
                             com.xai.dungeonmaster.WorldState world) {
        return roll(random, difficulty, chaos, isBoss);
    }
}
