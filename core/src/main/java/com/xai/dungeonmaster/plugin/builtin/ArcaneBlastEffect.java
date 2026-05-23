package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Entity;
import com.xai.dungeonmaster.Spell;
import com.xai.dungeonmaster.plugin.SpellEffect;

/** Stronger damage spell scaling 1:1 with INT. Was case "ARCANE_BLAST". */
public final class ArcaneBlastEffect implements SpellEffect {
    @Override public String id() { return "ARCANE_BLAST"; }
    @Override public String displayName() { return "Arcane Blast"; }

    @Override
    public String execute(DungeonMasterEngine engine, Adventurer caster, Entity target, Spell spell) {
        if (target == null) return "The blast dissipates into nothing.";
        if (!caster.useMana(spell.getManaCost())) {
            return caster.getName() + " fails to gather arcane force.";
        }
        int damage = spell.getPower() + caster.getStat("INT");
        target.takeDamage(damage);
        return caster.getName() + " unleashes ARCANE BLAST for " + damage + " damage!";
    }
}
