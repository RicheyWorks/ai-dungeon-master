package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Entity;
import com.xai.dungeonmaster.Spell;
import com.xai.dungeonmaster.StatusEffect;
import com.xai.dungeonmaster.plugin.SpellEffect;

/** Single-turn stun. Was case "CHRONO_STUN" in Spell.cast(). */
public final class ChronoStunEffect implements SpellEffect {
    @Override public String id() { return "CHRONO_STUN"; }
    @Override public String displayName() { return "Chrono Stun"; }

    @Override
    public String execute(DungeonMasterEngine engine, Adventurer caster, Entity target, Spell spell) {
        if (target == null) return "The stun spell has no target.";
        if (!caster.useMana(spell.getManaCost())) {
            return caster.getName() + " cannot gather enough mana to cast " + spell.getName() + ".";
        }
        target.addEffect(new StatusEffect("Time Lock", StatusEffect.EffectType.STUN, 0, 1));
        return target.getName() + " is frozen in a localized time loop!";
    }
}
