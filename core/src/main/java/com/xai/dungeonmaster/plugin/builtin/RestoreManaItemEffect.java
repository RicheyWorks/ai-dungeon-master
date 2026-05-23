package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Item;
import com.xai.dungeonmaster.plugin.ItemEffect;

/** Restores mana. Was case "RESTORE_MANA" in Item.use(). */
public final class RestoreManaItemEffect implements ItemEffect {
    @Override public String id() { return "RESTORE_MANA"; }
    @Override public String displayName() { return "Restore Mana"; }

    @Override
    public String execute(DungeonMasterEngine engine, Adventurer user, Item item) {
        user.restoreMana(item.getPower());
        return "[" + item.getRarity() + "] " + item.getName() + ": "
                + user.getName() + " restored " + item.getPower() + " mana.";
    }
}
