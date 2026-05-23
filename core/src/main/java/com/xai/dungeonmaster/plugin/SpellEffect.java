package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Entity;
import com.xai.dungeonmaster.Spell;

/**
 * Behavior for a single spell key (e.g., "VOID_BOLT", "TEMPORAL_HEAL").
 *
 * Replaces the inline {@code switch (spellKey)} in {@link Spell#cast}.
 * Each registered SpellEffect handles exactly one id; the engine routes
 * {@code Spell.cast()} through {@link SpellEffectRegistry#dispatch} which
 * looks up the matching effect by id.
 *
 * Spell metadata (name, mana cost, base power) still lives on the Spell
 * data object — this interface only carries the behavior.
 */
public interface SpellEffect extends Plugin {

    /**
     * Executes the spell behavior.
     *
     * @param engine  the live engine (for engine-aware effects like group heal)
     * @param caster  the adventurer casting; never null
     * @param target  the target entity, may be null for self-target spells
     * @param spell   the spell data being cast (provides name, manaCost, power)
     * @return human-readable narration of what happened
     */
    String execute(DungeonMasterEngine engine,
                   Adventurer caster,
                   Entity target,
                   Spell spell);
}
