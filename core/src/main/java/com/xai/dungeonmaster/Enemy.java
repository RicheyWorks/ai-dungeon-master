package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Robust implementation of a multiversal adversary.
 * Supports JSON serialization, automated status ticking, desperation behavior,
 * chaos surges, damage dice, enemy AI attacks, and boss phase transitions.
 */
public class Enemy implements Entity {

    private final String name;
    private int hp;
    private int maxHp;
    private int ac;
    private final int attackBonus;
    private final int level;
    private String damageDice;

    // --- Boss Phase System ---
    private final boolean boss;
    private int phase = 1;

    private final List<StatusEffect> effects = Collections.synchronizedList(new ArrayList<>());

    @JsonCreator
    public Enemy(
            @JsonProperty("name") String name,
            @JsonProperty("hp") int hp,
            @JsonProperty("ac") int ac,
            @JsonProperty("attackBonus") int attackBonus,
            @JsonProperty("level") int level) {

        this.name = Objects.requireNonNullElse(name, "Unknown Enemy").trim();
        this.hp = Math.max(1, hp);
        this.maxHp = this.hp;
        this.ac = Math.max(0, ac);
        this.attackBonus = attackBonus;
        this.level = Math.max(1, level);
        this.damageDice = determineDamageDice(this.level);

        this.boss = this.name.toLowerCase().contains("harbinger")
                || this.name.toLowerCase().contains("boss")
                || this.name.toLowerCase().contains("guardian");
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
        return level;
    }

    @Override
    public boolean isAlive() {
        return hp > 0;
    }

    @Override
    public void takeDamage(int damage) {
        if (damage <= 0) {
            return;
        }

        hp = Math.max(0, hp - damage);
    }

    @Override
    public void heal(int amount) {
        if (amount <= 0) {
            return;
        }

        hp = Math.min(maxHp, hp + amount);
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
    public void onTurnStart(DungeonMasterEngine engine) {
        List<StatusEffect> snapshot;

        synchronized (effects) {
            effects.removeIf(effect -> effect == null || effect.isExpired());
            snapshot = new ArrayList<>(effects);
        }

        for (StatusEffect effect : snapshot) {
            effect.applyTick(engine, this);
        }

        if (!isAlive()) {
            clearExpiredEffects();
            return;
        }

        if (hp < (maxHp * 0.25)) {
            handleDesperation(engine);
        }

        checkPhase(engine);

        performAttack(engine);

        clearExpiredEffects();
    }

    private void performAttack(DungeonMasterEngine engine) {
        if (engine == null || !isAlive()) {
            return;
        }

        Entity target = engine.getCombatState().getRandomPartyMember();

        if (target == null || !target.isAlive()) {
            engine.log(name + " snarls but finds no worthy foe.");
            return;
        }

        int attackRoll = ThreadLocalRandom.current().nextInt(1, 21) + attackBonus;

        if (attackRoll < target.getAC()) {
            engine.log(name + " attacks " + target.getName() + " but misses.");
            return;
        }

        int damage = rollDamageDice(damageDice);

        if (engine.getChaosLevel() > 5 && ThreadLocalRandom.current().nextInt(100) < 25) {
            int chaosBonus = ThreadLocalRandom.current().nextInt(5, 12);
            damage += chaosBonus;
            engine.log("CHAOS SURGE: " + name + "'s attack mutates mid-swing.");
        }

        target.takeDamage(damage);

        engine.log(name + " hits " + target.getName() + " for " + damage + " damage.");

        if (!target.isAlive()) {
            engine.log("DEFEATED: " + target.getName() + " has fallen.");
        }
    }

    private int rollDamageDice(String dice) {
        if (dice == null || dice.isBlank()) {
            return 4 + ThreadLocalRandom.current().nextInt(6);
        }

        try {
            String[] parts = dice.toLowerCase().trim().split("d");

            if (parts.length != 2) {
                return 4 + ThreadLocalRandom.current().nextInt(6);
            }

            int count = Integer.parseInt(parts[0].trim());
            int sides = Integer.parseInt(parts[1].trim());

            if (count <= 0 || sides <= 0) {
                return 4 + ThreadLocalRandom.current().nextInt(6);
            }

            int total = 0;

            for (int i = 0; i < count; i++) {
                total += ThreadLocalRandom.current().nextInt(1, sides + 1);
            }

            return total;

        } catch (RuntimeException e) {
            return 4 + ThreadLocalRandom.current().nextInt(6);
        }
    }

    private void handleDesperation(DungeonMasterEngine engine) {
        boolean alreadyEnraged;

        synchronized (effects) {
            alreadyEnraged = effects.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(e -> "Enraged".equalsIgnoreCase(e.getName()));
        }

        if (!alreadyEnraged) {
            if (engine != null) {
                engine.log(name + " enters a localized temporal frenzy!");
            }

            addEffect(new StatusEffect(
                    "Enraged",
                    StatusEffect.EffectType.STAT_BUFF,
                    3,
                    2
            ));

            ac += 2;
        }
    }

    private void checkPhase(DungeonMasterEngine engine) {

        if (!boss || maxHp <= 0) {
            return;
        }

        double hpRatio = (double) hp / maxHp;

        if (phase == 1 && hpRatio < 0.60) {
            phase = 2;
            ac += 2;
            setDamageDice("3d10");

            addEffect(new StatusEffect(
                    "Rift Frenzy",
                    StatusEffect.EffectType.STAT_BUFF,
                    4,
                    99
            ));

            if (engine != null) {
                engine.log(name + " enters PHASE 2: RIFT FRENZY!");
            }
        } else if (phase == 2 && hpRatio < 0.30) {
            phase = 3;
            ac += 3;
            setDamageDice("4d12");

            addEffect(new StatusEffect(
                    "Apocalyptic Frenzy",
                    StatusEffect.EffectType.STAT_BUFF,
                    8,
                    99
            ));

            if (engine != null) {
                engine.log(name + " enters PHASE 3: TOTAL ENTROPY!");
            }
        }
    }

    @Override
    public void onDeath(DungeonMasterEngine engine) {
        if (engine != null) {
            engine.log("Cosmic Echo: " + name + " has been erased from the current timeline.");
        }
    }

    public int getAttackBonus() {
        return attackBonus;
    }

    public String getDamageDice() {
        return damageDice;
    }

    public int getExpValue() {
        return level * 150;
    }

    public boolean isBoss() {
        return boss;
    }

    public int getPhase() {
        return phase;
    }

    private String determineDamageDice(int level) {
        if (level < 5) return "1d8";
        if (level < 10) return "2d6";
        if (level < 15) return "3d8";
        return "4d10";
    }

    public void setDamageDice(String damageDice) {
        if (damageDice != null && !damageDice.isBlank()) {
            this.damageDice = damageDice.trim();
        }
    }

    @Override
    public String toString() {
        return "Enemy{" +
                "name='" + name + '\'' +
                ", hp=" + hp +
                "/" + maxHp +
                ", ac=" + ac +
                ", attackBonus=" + attackBonus +
                ", level=" + level +
                ", damageDice='" + damageDice + '\'' +
                ", boss=" + boss +
                ", phase=" + phase +
                '}';
    }
}
