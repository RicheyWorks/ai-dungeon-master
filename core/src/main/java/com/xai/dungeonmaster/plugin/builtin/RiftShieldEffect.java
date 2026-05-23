package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Entity;
import com.xai.dungeonmaster.Spell;
import com.xai.dungeonmaster.StatusEffect;
import com.xai.dungeonmaster.plugin.SpellEffect;

/** Self-buff. Was case "RIFT_SHIELD" in Spell.cast(). */
public final class RiftShieldEffect implements SpellEffect {
    @Override public String id() { return "RIFT_SHIELD"; }
    @Override public String displayName() { return "Rift Shield"; }

    @Override
    public String execute(DungeonMasterEngine engine, Adventurer caster, Entity target, Spell spell) {
        if (!caster.useMana(spell.getManaCost())) {
            return caster.getName() + " cannot gather enough mana to cast " + spell.getName() + ".";
        }
        caster.addEffect(new StatusEffect("Rift Armor", StatusEffect.EffectType.STAT_BUFF, spell.getPower(), 3));
        return caster.getName() + " bends space around them, increasing defense!";
    }
}
