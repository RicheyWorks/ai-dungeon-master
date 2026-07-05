package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.Item;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.plugin.LootTable;

import java.util.Map;
import java.util.Random;

/**
 * The bundled {@code "default"} loot table. Prefers a template drawn from the
 * {@link ContentRegistry} (items.json), re-rolling its power for the current
 * difficulty/chaos, and falls back to the legacy rarity-weighted generator when
 * no content pack is loaded.
 *
 * This is the canonical loot generation logic — {@code DungeonGenerator} routes
 * {@code generateLoot} through {@link com.xai.dungeonmaster.plugin.LootTableRegistry}
 * to this table.
 */
public final class DefaultLootTable implements LootTable {

    @Override public String id() { return "LOOT_DEFAULT"; }
    @Override public String displayName() { return "Default Loot Table"; }
    @Override public String biome() { return "default"; }

    @Override
    public Item roll(Random random, int difficulty, int chaos) {
        Random rng = (random != null) ? random : new Random();
        int diff = Math.max(1, difficulty);
        int chaosLevel = Math.max(0, chaos);

        Map<String, Item> registered = ContentRegistry.items();
        if (!registered.isEmpty()) {
            Item[] pool = registered.values().toArray(new Item[0]);
            Item template = pool[rng.nextInt(pool.length)];
            // Re-roll power based on current difficulty/chaos so static JSON
            // entries don't feel flat at high chaos levels.
            int power = Math.max(
                    template.getPower(),
                    15 + (template.getRarity().ordinal() * 12) + rng.nextInt(20));
            return new Item(
                    template.getName(),
                    template.getDescription(),
                    template.getType(),
                    template.getRarity(),
                    template.getEffectKey(),
                    power);
        }
        return generateLegacyLoot(rng, diff, chaosLevel);
    }

    private Item generateLegacyLoot(Random random, int difficulty, int chaosLevel) {
        int rarityRoll = random.nextInt(100) + (chaosLevel * 7) + (difficulty * 3);

        Item.Rarity rarity;
        if (rarityRoll >= 95) {
            rarity = Item.Rarity.LEGENDARY;
        } else if (rarityRoll >= 80) {
            rarity = Item.Rarity.EPIC;
        } else if (rarityRoll >= 60) {
            rarity = Item.Rarity.RARE;
        } else if (rarityRoll >= 35) {
            rarity = Item.Rarity.UNCOMMON;
        } else {
            rarity = Item.Rarity.COMMON;
        }

        String[] legendaryNames = {"Chronal Vortex Core", "Riftforged Aegis", "Entropy Blade", "Ascendant Sigil"};
        String[] epicNames = {"Voidweaver Staff", "Reality Anchor", "Soulfire Amulet"};
        String[] rareNames = {"Fractured Star Shard", "Echoing Rune", "Nexus Crystal"};
        String[] commonNames = {"Health Draught", "Mana Shard", "Rift Scrap", "Worn Charm"};

        String name;
        String description;
        String effectKey;

        int power = 15 + (rarity.ordinal() * 12) + random.nextInt(20);

        switch (rarity) {
            case LEGENDARY -> {
                name = legendaryNames[random.nextInt(legendaryNames.length)];
                description = "A relic that bends the fabric of the multiverse.";
                effectKey = random.nextBoolean() ? "BUFF_AC" : "RESTORE_MANA";
                power += 30;
            }
            case EPIC -> {
                name = epicNames[random.nextInt(epicNames.length)];
                description = "Pulsing with raw rift energy.";
                effectKey = random.nextBoolean() ? "HEAL" : "RESTORE_MANA";
                power += 15;
            }
            case RARE -> {
                name = rareNames[random.nextInt(rareNames.length)];
                description = "A valuable fragment from a shattered timeline.";
                effectKey = random.nextBoolean() ? "HEAL" : "BUFF_AC";
            }
            case UNCOMMON -> {
                name = commonNames[random.nextInt(commonNames.length)] + " Plus";
                description = "A useful item touched by mild rift energy.";
                effectKey = random.nextBoolean() ? "HEAL" : "RESTORE_MANA";
            }
            default -> {
                name = commonNames[random.nextInt(commonNames.length)];
                description = "Useful scrap from a damaged timeline.";
                effectKey = random.nextBoolean() ? "HEAL" : "RESTORE_MANA";
            }
        }

        return new Item(name, description, Item.ItemType.CONSUMABLE, rarity, effectKey, power);
    }
}
