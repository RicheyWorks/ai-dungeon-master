package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Collection;

/**
 * Enterprise-grade base for any interactive being in the multiverse.
 * Defines the shared combat/status contract for all living participants.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "entityType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = Adventurer.class, name = "adventurer"),
        @JsonSubTypes.Type(value = Enemy.class, name = "enemy"),
        @JsonSubTypes.Type(value = Summon.class, name = "summon")
})
public interface Entity {

    // --- Core Identity / Stats ---

    String getName();

    int getHp();

    int getMaxHp();

    int getLevel();

    /**
     * Armor Class: defensive threshold for incoming attacks.
     */
    int getAC();

    // --- State Changes ---

    void takeDamage(int damage);

    void heal(int amount);

    default boolean isAlive() {
        return getHp() > 0;
    }

    default boolean isDead() {
        return !isAlive();
    }

    // --- Status Effects ---

    void addEffect(StatusEffect effect);

    void clearExpiredEffects();

    Collection<StatusEffect> getActiveEffects();

    // --- Lifecycle Hooks ---

    /**
     * Runs at the start of the entity's turn.
     * Concrete implementations may override for richer AI/behavior.
     */
    default void onTurnStart(DungeonMasterEngine engine) {
        clearExpiredEffects();

        Collection<StatusEffect> effects = getActiveEffects();
        if (effects == null || effects.isEmpty()) {
            return;
        }

        for (StatusEffect effect : effects) {
            if (effect != null) {
                effect.applyTick(engine, this);
            }
        }

        clearExpiredEffects();
    }

    /**
     * Hook for weapon procs, life steal, combat logs, etc.
     */
    default void onAttackSuccess(Entity target) {
        // no-op by default
    }

    /**
     * Triggered when HP reaches zero.
     */
    default void onDeath(DungeonMasterEngine engine) {
        if (engine != null) {
            engine.log(getName() + " has fallen in the multiversal rift.");
        }
    }

    /**
     * Optional helper for percentage-based health logic.
     */
    default double getHealthRatio() {
        return getMaxHp() <= 0 ? 0.0 : (double) getHp() / getMaxHp();
    }
}
