package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Entity;
import com.xai.dungeonmaster.Spell;
import com.xai.dungeonmaster.plugin.SpellEffect;

/** Single-target heal. Was case "TEMPORAL_HEAL" in Spell.cast(). */
public final class TemporalHealEffect implements SpellEffect {
    @Override public String id() { return "TEMPORAL_HEAL"; }
    @Override public String displayName() { return "Temporal Heal"; }

    @Override
    public String execute(DungeonMasterEngine engine, Adventurer caster, Entity target, Spell spell) {
        if (target == null) return "The healing spell has no target.";
        if (!caster.useMana(spell.getManaCost())) {
            return caster.getName() + " cannot gather enough mana to cast " + spell.getName() + ".";
        }
        target.heal(spell.getPower());
        return String.format("%s rewinds time for %s, sealing wounds for %d HP.",
                caster.getName(), target.getName(), spell.getPower());
    }
}
