package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Robust implementation of an Ethereal Summon.
 * Now includes real AI behaviors: AGGRESSOR, MENDER, PROTECTOR.
 */
public class Summon implements Entity {

    private final String name;
    private int hp;
    private int maxHp;
    private int ac;
    private final String behaviorKey;
    private final int power;

    private final List<StatusEffect> effects = Collections.synchronizedList(new ArrayList<>());

    @JsonCreator
    public Summon(
            @JsonProperty("name") String name,
            @JsonProperty("hp") int hp,
            @JsonProperty("ac") int ac,
            @JsonProperty("behaviorKey") String behaviorKey,
            @JsonProperty("power") int power) {

        this.name = normalize(name, "Ethereal Familiar");
        this.hp = Math.max(1, hp);
        this.maxHp = this.hp;
        this.ac = Math.max(0, ac);
        this.behaviorKey = normalize(behaviorKey, "AGGRESSOR").toUpperCase();
        this.power = Math.max(0, power);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getHp() {
        return hp;
    }

    @Override
    public int getMaxHp() {
        return maxHp;
    }

    @Override
    public int getAC() {
        return ac;
    }

    @Override
    public int getLevel() {
        return 1;
    }

    @Override
    public boolean isAlive() {
        return hp > 0;
    }

    @Override
    public void takeDamage(int damage) {
        if (damage <= 0) return;
        hp = Math.max(0, hp - damage);
    }

    @Override
    public void heal(int amount) {
        if (amount <= 0) return;
        hp = Math.min(maxHp, hp + amount);
    }

    @Override
    public void onTurnStart(DungeonMasterEngine engine) {

        if (!isAlive()) return;

        // --- Tick status effects ---
        List<StatusEffect> snapshot;
        synchronized (effects) {
            effects.removeIf(e -> e == null || e.isExpired());
            snapshot = new ArrayList<>(effects);
        }

        for (StatusEffect e : snapshot) {
            e.applyTick(engine, this);
        }

        // --- REAL AI BEHAVIOR ---
        executeBehavior(engine);

        clearExpiredEffects();
    }

    private void executeBehavior(DungeonMasterEngine engine) {

        if (engine == null) return;

        switch (behaviorKey) {

            case "AGGRESSOR" -> {
                Entity target = engine.getCombatState().getRandomEnemy();

                if (target != null) {
                    target.takeDamage(power);
                    engine.log(name + " rips through " + target.getName()
                            + " for " + power + " ethereal damage!");
                } else {
                    engine.log(name + " searches for a target, but none remain.");
                }
            }

            case "MENDER" -> {
                Entity ally = engine.getCombatState().getRandomPartyMember();

                if (ally != null) {
                    ally.heal(power);
                    engine.log(name + " mends " + ally.getName()
                            + " for " + power + " HP with chronal threads.");
                } else {
                    engine.log(name + " has no ally to mend.");
                }
            }

            case "PROTECTOR" -> {
                Entity ally = engine.getCombatState().getRandomPartyMember();

                if (ally != null) {
                    ally.addEffect(new StatusEffect(
                            "Rift Ward",
                            StatusEffect.EffectType.STAT_BUFF,
                            power,
                            2
                    ));

                    engine.log(name + " raises a shimmering rift barrier on "
                            + ally.getName() + "!");
                } else {
                    engine.log(name + " cannot find an ally to protect.");
                }
            }

            default -> engine.log(name + " waits patiently in the rift.");
        }
    }

    @Override
    public void addEffect(StatusEffect effect) {
        if (effect != null) {
            effects.add(effect);
        }
    }

    @Override
    public void clearExpiredEffects() {
        synchronized (effects) {
            effects.removeIf(effect -> effect == null || effect.isExpired());
        }
    }

    @Override
    public Collection<StatusEffect> getActiveEffects() {
        synchronized (effects) {
            return List.copyOf(effects);
        }
    }

    @Override
    public void onDeath(DungeonMasterEngine engine) {
        if (engine != null) {
            engine.log(name + " dissipates back into the ethereal rift.");
        }
    }

    public String getBehaviorKey() {
        return behaviorKey;
    }

    public int getPower() {
        return power;
    }

    @Override
    public String toString() {
        return "Summon{" +
                "name='" + name + '\'' +
                ", hp=" + hp + "/" + maxHp +
                ", ac=" + ac +
                ", behaviorKey='" + behaviorKey + '\'' +
                ", power=" + power +
                '}';
    }

    private static String normalize(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
