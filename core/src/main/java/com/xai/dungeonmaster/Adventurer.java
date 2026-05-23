package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise-grade Adventurer model.
 * Features:
 * - Thread-safe vital tracking
 * - Status effect processing
 * - Inventory integration
 * - XP / level progression
 * - Spell learning / known spells
 * - Backward-compatible helper methods for spell/combat systems
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Adventurer implements Entity {

    private final String name;
    private final String role;

    private int level = 1;

    // --- Progression / XP System ---
    private final AtomicInteger xp = new AtomicInteger(0);
    private final AtomicInteger xpToNext = new AtomicInteger(100);

    private boolean ascendant = false;

    // Core vitals
    private final AtomicInteger hp = new AtomicInteger();
    private final AtomicInteger maxHp = new AtomicInteger();
    private final AtomicInteger mana = new AtomicInteger();
    private final AtomicInteger maxMana = new AtomicInteger();

    // Core stats
    private int strength;
    private int dexterity;
    private int constitution;
    private int intelligence;
    private int wisdom;
    private int charisma;

    private final List<Item> inventory = Collections.synchronizedList(new ArrayList<>());
    private final List<StatusEffect> activeEffects = Collections.synchronizedList(new ArrayList<>());

    // --- Spell System ---
    private final List<Spell> knownSpells = Collections.synchronizedList(new ArrayList<>());

    @JsonCreator
    public Adventurer(
            @JsonProperty("name") String name,
            @JsonProperty("strength") int strength,
            @JsonProperty("dexterity") int dexterity,
            @JsonProperty("constitution") int constitution,
            @JsonProperty("intelligence") int intelligence,
            @JsonProperty("wisdom") int wisdom,
            @JsonProperty("charisma") int charisma,
            @JsonProperty("hp") int health,
            @JsonProperty("role") String role) {

        this.name = Objects.requireNonNullElse(name, "Unknown Adventurer").trim();
        this.role = Objects.requireNonNullElse(role, "Wanderer").trim();

        this.strength = Math.max(1, strength);
        this.dexterity = Math.max(1, dexterity);
        this.constitution = Math.max(1, constitution);
        this.intelligence = Math.max(1, intelligence);
        this.wisdom = Math.max(1, wisdom);
        this.charisma = Math.max(1, charisma);

        int safeHealth = Math.max(1, health);
        this.maxHp.set(safeHealth);
        this.hp.set(safeHealth);

        int derivedMana = Math.max(0, 50 + (this.intelligence * 5));
        this.maxMana.set(derivedMana);
        this.mana.set(derivedMana);
    }

    // --- Entity implementation ---

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getHp() {
        return hp.get();
    }

    @Override
    public int getMaxHp() {
        return maxHp.get();
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public int getAC() {
        return 10 + getModifier(dexterity);
    }

    @Override
    public boolean isAlive() {
        return hp.get() > 0;
    }

    @Override
    public void takeDamage(int damage) {
        if (damage <= 0) {
            return;
        }
        hp.updateAndGet(current -> Math.max(0, current - damage));
    }

    @Override
    public void heal(int amount) {
        if (amount <= 0) {
            return;
        }
        hp.updateAndGet(current -> Math.min(maxHp.get(), current + amount));
    }

    @Override
    public void addEffect(StatusEffect effect) {
        if (effect != null) {
            activeEffects.add(effect);
        }
    }

    @Override
    public void clearExpiredEffects() {
        synchronized (activeEffects) {
            activeEffects.removeIf(effect -> effect == null || effect.isExpired());
        }
    }

    @Override
    public Collection<StatusEffect> getActiveEffects() {
        synchronized (activeEffects) {
            return List.copyOf(activeEffects);
        }
    }

    @Override
    public void onTurnStart(DungeonMasterEngine engine) {
        List<StatusEffect> snapshot;

        synchronized (activeEffects) {
            activeEffects.removeIf(effect -> effect == null || effect.isExpired());
            snapshot = new ArrayList<>(activeEffects);
        }

        for (StatusEffect effect : snapshot) {
            effect.applyTick(engine, this);
        }

        clearExpiredEffects();
    }

    // --- Adventurer-specific behavior ---

    public String getRole() {
        return role;
    }

    public boolean isAscendant() {
        return ascendant;
    }

    public void ascend() {
        this.ascendant = true;
    }

    public int getMana() {
        return mana.get();
    }

    public int getMaxMana() {
        return maxMana.get();
    }

    public List<Item> getInventory() {
        synchronized (inventory) {
            return List.copyOf(inventory);
        }
    }

    public void addItem(Item item) {
        if (item != null) {
            inventory.add(item);
        }
    }

    public boolean removeItem(Item item) {
        if (item == null) {
            return false;
        }
        return inventory.remove(item);
    }

    // --- Spell System ---

    public List<Spell> getKnownSpells() {
        synchronized (knownSpells) {
            return List.copyOf(knownSpells);
        }
    }

    public void learnSpell(Spell spell) {
        if (spell != null) {
            knownSpells.add(spell);
        }
    }

    // --- Progression / XP System ---

    public String gainXp(int amount) {
        if (amount <= 0) {
            return "";
        }

        int newXp = xp.addAndGet(amount);

        StringBuilder result = new StringBuilder();
        result.append(name).append(" gains ").append(amount).append(" XP.");

        while (newXp >= xpToNext.get()) {
            newXp -= xpToNext.get();
            xp.set(newXp);

            levelUp();

            xpToNext.updateAndGet(val -> Math.max(100, (int) (val * 1.5)));

            result.append("\nLEVEL UP! ")
                    .append(name)
                    .append(" is now level ")
                    .append(level)
                    .append("!");
        }

        return result.toString();
    }

    public void levelUp() {
        level++;

        int hpGain = Math.max(5, 10 + getModifier(constitution));
        int manaGain = Math.max(3, 8 + getModifier(intelligence));

        maxHp.addAndGet(hpGain);
        maxMana.addAndGet(manaGain);

        strength += randomStat();
        dexterity += randomStat();
        constitution += randomStat();
        intelligence += randomStat();
        wisdom += randomStat();
        charisma += randomStat();

        hp.set(maxHp.get());
        mana.set(maxMana.get());
    }

    private int randomStat() {
        return ThreadLocalRandom.current().nextInt(1, 3);
    }

    public int getXp() {
        return xp.get();
    }

    public int getXpToNext() {
        return xpToNext.get();
    }

    public int getModifier(int stat) {
        return Math.floorDiv(stat - 10, 2);
    }

    public void restoreMana(int amount) {
        if (amount <= 0) {
            return;
        }
        mana.updateAndGet(current -> Math.min(maxMana.get(), current + amount));
    }

    public void spendMana(int amount) {
        if (amount <= 0) {
            return;
        }
        mana.updateAndGet(current -> Math.max(0, current - amount));
    }

    /**
     * Backward-compatible alias for older code that expects useMana().
     */
    public boolean useMana(int amount) {
        if (amount <= 0) {
            return true;
        }

        while (true) {
            int current = mana.get();

            if (current < amount) {
                return false;
            }

            if (mana.compareAndSet(current, current - amount)) {
                return true;
            }
        }
    }

    /**
     * Backward-compatible generic stat accessor used by Spell and other systems.
     */
    public int getStat(String statName) {
        if (statName == null || statName.isBlank()) {
            return 0;
        }

        return switch (statName.trim().toLowerCase(Locale.ROOT)) {
            case "str", "strength" -> strength;
            case "dex", "dexterity" -> dexterity;
            case "con", "constitution" -> constitution;
            case "int", "intelligence" -> intelligence;
            case "wis", "wisdom" -> wisdom;
            case "cha", "charisma" -> charisma;
            case "hp", "health" -> getHp();
            case "maxhp", "max_health", "max health" -> getMaxHp();
            case "mana" -> getMana();
            case "maxmana", "max_mana", "max mana" -> getMaxMana();
            case "level" -> level;
            case "xp" -> getXp();
            case "xptonext", "xp_to_next", "xp to next" -> getXpToNext();
            case "knownspells", "known_spells", "known spells", "spells" -> getKnownSpells().size();
            case "ac", "armorclass", "armor_class", "armor class" -> getAC();
            default -> 0;
        };
    }

    public int getStrength() {
        return strength;
    }

    public int getDexterity() {
        return dexterity;
    }

    public int getConstitution() {
        return constitution;
    }

    public int getIntelligence() {
        return intelligence;
    }

    public int getWisdom() {
        return wisdom;
    }

    public int getCharisma() {
        return charisma;
    }

    @Override
    public String toString() {
        return "Adventurer{" +
                "name='" + name + '\'' +
                ", role='" + role + '\'' +
                ", level=" + level +
                ", xp=" + xp.get() + "/" + xpToNext.get() +
                ", hp=" + hp.get() + "/" + maxHp.get() +
                ", mana=" + mana.get() + "/" + maxMana.get() +
                ", knownSpells=" + knownSpells.size() +
                ", ascendant=" + ascendant +
                '}';
    }
}
