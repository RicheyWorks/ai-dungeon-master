package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Entity;
import com.xai.dungeonmaster.Spell;
import com.xai.dungeonmaster.plugin.SpellEffect;

/** Damage spell scaling with INT. Was case "VOID_BOLT" in Spell.cast(). */
public final class VoidBoltEffect implements SpellEffect {
    @Override public String id() { return "VOID_BOLT"; }
    @Override public String displayName() { return "Void Bolt"; }

    @Override
    public String execute(DungeonMasterEngine engine, Adventurer caster, Entity target, Spell spell) {
        if (target == null) return "The spell has no target.";
        if (!caster.useMana(spell.getManaCost())) {
            return caster.getName() + " fails to stabilize enough mana for " + spell.getName() + ".";
        }
        int damage = Math.max(1, spell.getPower() + (caster.getStat("INT") / 2));
        target.takeDamage(damage);
        return String.format("%s launches a bolt of pure entropy! %s takes %d damage.",
                caster.getName(), target.getName(), damage);
    }
}
