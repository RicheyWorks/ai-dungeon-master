package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.DungeonGenerator;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Quest;
import com.xai.dungeonmaster.plugin.QuestScript;

import java.util.Random;

/**
 * The bundled {@code "default"} quest script. Produces a standard multiversal
 * rift — a run of unstable layers capped by a boss chamber — matching the
 * engine's historical "Genesis Rift" opener.
 *
 * The scene/choice structure is delegated to
 * {@link DungeonGenerator#generateCustomRift(String, int, int)} so the bundled
 * script and the parameterized world-map generator share one implementation.
 * {@code DungeonMasterEngine} routes its opening quest through
 * {@link com.xai.dungeonmaster.plugin.QuestScriptRegistry} to this script,
 * mirroring how {@code Spell.cast} routes through the {@code SpellEffectRegistry}.
 */
public final class DefaultQuestScript implements QuestScript {

    /** Default rift size (number of scenes, including the boss chamber). */
    private static final int DEFAULT_RIFT_SIZE = 4;
    private static final String DEFAULT_TITLE = "Genesis Rift";

    @Override public String id() { return "default"; }
    @Override public String displayName() { return "Genesis Rift"; }

    @Override
    public Quest build(DungeonMasterEngine engine, int difficulty, int chaos) {
        int diff = Math.max(1, difficulty);
        int chaosLevel = Math.max(0, chaos);
        // generateCustomRift is deterministic in its scene layout; a fresh
        // generator is enough to build the quest and keeps this script stateless.
        DungeonGenerator generator = new DungeonGenerator(new Random(), diff, chaosLevel);
        return generator.generateCustomRift(DEFAULT_TITLE, DEFAULT_RIFT_SIZE, diff);
    }
}
