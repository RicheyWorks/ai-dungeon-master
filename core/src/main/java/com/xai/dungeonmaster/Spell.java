package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xai.dungeonmaster.plugin.SpellEffectRegistry;

import java.util.Objects;

/**
 * Spell data — name, mana cost, spell key, base power.
 *
 * The actual behavior is no longer inline in this class. Each spellKey
 * resolves to a SpellEffect plugin via SpellEffectRegistry, so mods and
 * content packs can add new spells without recompiling the engine.
 *
 * Bundled spell keys: VOID_BOLT, TEMPORAL_HEAL, CHRONO_STUN, RIFT_SHIELD,
 * ARCANE_BLAST, GROUP_HEAL. See com.xai.dungeonmaster.plugin.builtin.* for
 * the corresponding effect implementations.
 */
public class Spell {

    private final String name;
    private final int manaCost;
    private final String spellKey;
    private final int power;

    @JsonCreator
    public Spell(
            @JsonProperty("name") String name,
            @JsonProperty("manaCost") int manaCost,
            @JsonProperty("spellKey") String spellKey,
            @JsonProperty("power") int power) {

        this.name = normalize(name, "Unknown Spell");
        this.manaCost = Math.max(0, manaCost);
        this.spellKey = normalize(spellKey, "NONE");
        this.power = Math.max(0, power);
    }

    /**
     * Executes the spell. Performs caster/mana preflight checks, then routes
     * to the SpellEffect plugin registered under {@link #getSpellKey()}.
     */
    public String cast(DungeonMasterEngine engine, Adventurer caster, Entity target) {
        if (caster == null) {
            return "No caster is present to shape the spell.";
        }
        if (!caster.isAlive()) {
            return caster.getName() + " cannot cast while defeated.";
        }
        if (caster.getMana() < manaCost) {
            return caster.getName() + " attempts to channel " + name + " but lacks the chronal energy!";
        }
        // Plugin dispatch — the effect itself calls useMana, applies damage/heal, etc.
        return SpellEffectRegistry.dispatch(engine, caster, target, this);
    }

    private static String normalize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    public String getName() {
        return name;
    }

    public int getManaCost() {
        return manaCost;
    }

    public String getSpellKey() {
        return spellKey;
    }

    public int getPower() {
        return power;
    }

    @Override
    public String toString() {
        return "Spell{" +
                "name='" + name + '\'' +
                ", manaCost=" + manaCost +
                ", spellKey='" + spellKey + '\'' +
                ", power=" + power +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, manaCost, spellKey, power);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Spell other)) return false;
        return manaCost == other.manaCost
                && power == other.power
                && Objects.equals(name, other.name)
                && Objects.equals(spellKey, other.spellKey);
    }
}
