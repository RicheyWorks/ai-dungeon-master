package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xai.dungeonmaster.plugin.ItemEffectRegistry;

import java.util.Objects;

/**
 * Item data — name, description, type, rarity, effect key, power.
 *
 * The actual behavior is no longer inline. Each effectKey resolves to an
 * ItemEffect plugin via ItemEffectRegistry, so mods and content packs can
 * add new item effects without recompiling.
 *
 * Bundled effect keys: HEAL, RESTORE_MANA, BUFF_AC, CLEANSE, NONE.
 * See com.xai.dungeonmaster.plugin.builtin.*ItemEffect.
 */
public class Item {

    public enum ItemType {
        CONSUMABLE,
        EQUIPMENT,
        KEY_ITEM
    }

    public enum Rarity {
        COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
    }

    private final String name;
    private final String description;
    private final ItemType type;
    private final Rarity rarity;
    private final String effectKey;
    private final int power;

    @JsonCreator
    public Item(
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("type") ItemType type,
            @JsonProperty("rarity") Rarity rarity,
            @JsonProperty("effectKey") String effectKey,
            @JsonProperty("power") int power) {

        this.name = normalize(name, "Unknown Item");
        this.description = normalize(description, "No description.");
        this.type = (type != null) ? type : ItemType.CONSUMABLE;
        this.rarity = (rarity != null) ? rarity : Rarity.COMMON;
        this.effectKey = normalize(effectKey, "NONE");
        this.power = Math.max(0, power);
    }

    /**
     * Executes the item's effect by routing to the ItemEffect plugin
     * registered under {@link #getEffectKey()}.
     */
    public String use(DungeonMasterEngine engine, Adventurer user) {
        if (user == null || !user.isAlive()) {
            return "The dead cannot utilize the living power of items.";
        }
        return ItemEffectRegistry.dispatch(engine, user, this);
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

    public String getDescription() {
        return description;
    }

    public ItemType getType() {
        return type;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public String getEffectKey() {
        return effectKey;
    }

    public int getPower() {
        return power;
    }

    public boolean isConsumable() {
        return type == ItemType.CONSUMABLE;
    }

    public boolean isEquipment() {
        return type == ItemType.EQUIPMENT;
    }

    public boolean isKeyItem() {
        return type == ItemType.KEY_ITEM;
    }

    @Override
    public String toString() {
        return "[" + rarity + "] " + name + " - " + description;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, type, rarity, effectKey, power);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Item other)) return false;
        return power == other.power
                && Objects.equals(name, other.name)
                && Objects.equals(description, other.description)
                && type == other.type
                && rarity == other.rarity
                && Objects.equals(effectKey, other.effectKey);
    }
}
