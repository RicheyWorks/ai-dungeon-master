package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Item;
import com.xai.dungeonmaster.plugin.ItemEffect;

/** Null effect — used as default for items without behavior. Was case "NONE". */
public final class NoneItemEffect implements ItemEffect {
    @Override public String id() { return "NONE"; }
    @Override public String displayName() { return "No Effect"; }

    @Override
    public String execute(DungeonMasterEngine engine, Adventurer user, Item item) {
        return "[" + item.getRarity() + "] " + item.getName() + ": Nothing happens.";
    }
}
