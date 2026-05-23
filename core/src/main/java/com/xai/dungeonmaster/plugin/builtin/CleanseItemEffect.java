package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Item;
import com.xai.dungeonmaster.plugin.ItemEffect;

/** Clears expired status effects. Was case "CLEANSE" in Item.use(). */
public final class CleanseItemEffect implements ItemEffect {
    @Override public String id() { return "CLEANSE"; }
    @Override public String displayName() { return "Cleanse"; }

    @Override
    public String execute(DungeonMasterEngine engine, Adventurer user, Item item) {
        user.clearExpiredEffects();
        return "[" + item.getRarity() + "] " + item.getName() + ": "
                + "Negative rift residue around " + user.getName() + " was disrupted.";
    }
}
