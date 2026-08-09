package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.DungeonGenerator;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Quest;
import com.xai.dungeonmaster.plugin.QuestScript;
import com.xai.dungeonmaster.plugin.QuestScriptRegistry;

import java.util.Random;

/**
 * The bundled {@code "default"} quest script. Prefers the free
 * {@code first-light-cold-open} data quest when the First Light content pack
 * is loaded (Goal G1 — legendary opener); otherwise builds the classic
 * multiversal "Genesis Rift" via {@link DungeonGenerator#generateCustomRift}.
 */
public final class DefaultQuestScript implements QuestScript {

    /** Authored cold-open from content-packs/first-light (when registered). */
    public static final String FIRST_LIGHT_SCRIPT = "first-light-cold-open";

    /** Default rift size (number of scenes, including the boss chamber). */
    private static final int DEFAULT_RIFT_SIZE = 4;
    private static final String DEFAULT_TITLE = "Genesis Rift";

    @Override public String id() { return "default"; }
    @Override public String displayName() { return "Genesis Rift / First Light"; }

    @Override
    public Quest build(DungeonMasterEngine engine, int difficulty, int chaos) {
        int diff = Math.max(1, difficulty);
        int chaosLevel = Math.max(0, chaos);
        // Prefer the free legendary opener when the pack has registered it.
        if (QuestScriptRegistry.isRegistered(FIRST_LIGHT_SCRIPT)) {
            Quest authored = QuestScriptRegistry.dispatch(FIRST_LIGHT_SCRIPT, engine, diff, chaosLevel);
            if (authored != null) {
                return authored;
            }
        }
        DungeonGenerator generator = new DungeonGenerator(new Random(), diff, chaosLevel);
        return generator.generateCustomRift(DEFAULT_TITLE, DEFAULT_RIFT_SIZE, diff);
    }
}
