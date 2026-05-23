package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Entity;
import com.xai.dungeonmaster.Spell;
import com.xai.dungeonmaster.plugin.SpellEffect;

/** Party-wide heal. Was case "GROUP_HEAL" in Spell.cast(). */
public final class GroupHealEffect implements SpellEffect {
    @Override public String id() { return "GROUP_HEAL"; }
    @Override public String displayName() { return "Group Heal"; }

    @Override
    public String execute(DungeonMasterEngine engine, Adventurer caster, Entity target, Spell spell) {
        if (!caster.useMana(spell.getManaCost())) {
            return caster.getName() + " fails to channel group healing.";
        }
        if (engine == null) {
            return "The spell fizzles — no timeline anchor.";
        }
        int healed = 0;
        for (Adventurer ally : engine.getCombatState().getLivingPartyMembers()) {
            ally.heal(spell.getPower());
            healed++;
        }
        return caster.getName() + " restores " + healed + " allies with temporal healing!";
    }
}
