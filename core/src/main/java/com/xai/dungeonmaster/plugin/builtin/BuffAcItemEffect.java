package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Item;
import com.xai.dungeonmaster.StatusEffect;
import com.xai.dungeonmaster.plugin.ItemEffect;

/** Temporary defense buff. Was case "BUFF_AC" in Item.use(). */
public final class BuffAcItemEffect implements ItemEffect {
    @Override public String id() { return "BUFF_AC"; }
    @Override public String displayName() { return "Defense Buff"; }

    @Override
    public String execute(DungeonMasterEngine engine, Adventurer user, Item item) {
        user.addEffect(new StatusEffect(
                "Hardened Shell", StatusEffect.EffectType.STAT_BUFF, item.getPower(), 3));
        return "[" + item.getRarity() + "] " + item.getName() + ": "
                + user.getName() + "'s defense increased by " + item.getPower() + " for 3 turns.";
    }
}
