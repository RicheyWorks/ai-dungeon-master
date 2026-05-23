package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Item;
import com.xai.dungeonmaster.plugin.ItemEffect;

/** Restores HP to the user. Was case "HEAL" in Item.use(). */
public final class HealItemEffect implements ItemEffect {
    @Override public String id() { return "HEAL"; }
    @Override public String displayName() { return "Heal"; }

    @Override
    public String execute(DungeonMasterEngine engine, Adventurer user, Item item) {
        user.heal(item.getPower());
        return "[" + item.getRarity() + "] " + item.getName() + ": "
                + user.getName() + " recovered " + item.getPower() + " HP.";
    }
}
