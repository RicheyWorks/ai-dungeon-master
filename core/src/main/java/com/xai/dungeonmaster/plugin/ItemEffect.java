package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Item;

/**
 * Behavior for a single item effect key (e.g., "HEAL", "RESTORE_MANA").
 *
 * Replaces the inline {@code switch (effectKey)} in {@link Item#use}.
 * Each registered ItemEffect handles exactly one id; the engine routes
 * {@code Item.use()} through {@link ItemEffectRegistry#dispatch} which
 * looks up the matching effect by id.
 */
public interface ItemEffect extends Plugin {

    /**
     * Executes the item effect.
     *
     * @param engine the live engine (for effects that need party-wide context)
     * @param user   the adventurer using the item; never null
     * @param item   the item being used (provides name, rarity, power)
     * @return human-readable narration of what happened
     */
    String execute(DungeonMasterEngine engine, Adventurer user, Item item);
}
