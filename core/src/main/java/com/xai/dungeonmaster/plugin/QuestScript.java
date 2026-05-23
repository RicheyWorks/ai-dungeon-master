package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Quest;

/**
 * Generates a Quest object on demand. Replaces hardcoded quest constructors
 * inside DungeonGenerator; lets content packs ship their own campaigns.
 *
 * The engine asks the QuestScript registry for an entry by id and calls
 * {@link #build} when a new quest is needed (game start, completion, etc.).
 */
public interface QuestScript extends Plugin {

    /**
     * Build a fresh Quest instance. The engine may call this multiple times
     * across a session; each call must return an independent Quest.
     */
    Quest build(DungeonMasterEngine engine, int difficulty, int chaos);
}
