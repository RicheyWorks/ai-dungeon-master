package com.xai.dungeonmaster;

import com.xai.dungeonmaster.plugin.ContentRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Procedural content generator.
 *
 * After Phase 1, loot and enemy pools come from the {@link ContentRegistry}
 * (populated by ContentPacks at startup). When the registry is empty —
 * for example in unit tests that don't load any pack — DungeonGenerator
 * falls back to its legacy hardcoded arrays so the engine still produces
 * valid encounters and tests run without any resource loading.
 */
public class DungeonGenerator {

    private final Random random;
    private final int difficulty;
    private final int chaosLevel;

    public DungeonGenerator(Random random, int difficulty, int chaosLevel) {
        this.random = random != null ? random : new Random();
        this.difficulty = Math.max(1, difficulty);
        this.chaosLevel = Math.max(0, chaosLevel);
    }

    public Item generateLoot() {
        Map<String, Item> registered = ContentRegistry.items();
        if (!registered.isEmpty()) {
            Item[] pool = registered.values().toArray(new Item[0]);
            Item template = pool[random.nextInt(pool.length)];
            // Re-roll power based on current difficulty/chaos so static JSON
            // entries don't feel flat at high chaos levels.
            int power = Math.max(
                    template.getPower(),
                    15 + (template.getRarity().ordinal() * 12) + random.nextInt(20));
            return new Item(
                    template.getName(),
                    template.getDescription(),
                    template.getType(),
                    template.getRarity(),
                    template.getEffectKey(),
                    power);
        }
        return generateLegacyLoot();
    }

    private Item generateLegacyLoot() {
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

    public String randomRole() {
        String[] roles = {"Warrior", "Mage", "Thief", "Cleric", "Summoner"};
        return roles[random.nextInt(roles.length)];
    }

    public Adventurer generateAlly() {
        String name = "Lost Soul #" + (random.nextInt(9000) + 1000);
        String role = randomRole();
        int base = 8 + difficulty;
        return new Adventurer(
                name,
                base + random.nextInt(6),
                base + random.nextInt(6),
                base + random.nextInt(6),
                base + random.nextInt(6),
                base + random.nextInt(6),
                base + random.nextInt(6),
                50 + (difficulty * 15),
                role);
    }

    public Enemy generateEnemy(boolean isBoss) {
        // Draw a template from the loaded content registry when available so
        // monsters.json stats (HP/AC/attack) and boss flags actually drive
        // generation; fall back to legacy constants when nothing is loaded.
        Enemy template = pickMonsterTemplate(isBoss);

        String name;
        int baseHp, baseAc, baseAtk;
        if (template != null) {
            name = template.getName();
            baseHp = template.getMaxHp();
            baseAc = template.getAC();
            baseAtk = template.getAttackBonus();
        } else {
            name = isBoss ? "Harbinger of Entropy" : "Rift Stalker";
            baseHp = isBoss ? 320 : 60;
            baseAc = 12 + (isBoss ? 8 : 0);
            baseAtk = 4;
        }

        // Scale the JSON/legacy baseline by difficulty and chaos.
        int hp = baseHp * difficulty + (random.nextInt(30) * Math.max(1, chaosLevel));
        int ac = baseAc + (difficulty / 2);
        int atk = baseAtk + difficulty + (chaosLevel / 2);

        if (chaosLevel > 4 && !name.startsWith("Corrupted ")) {
            name = "Corrupted " + name;
        }

        Enemy enemy = new Enemy(name, hp, ac, atk, difficulty);
        if (isBoss) {
            enemy.setDamageDice("2d12");
        }
        return enemy;
    }

    /**
     * Pick a monster template from the registry, preferring one whose boss flag
     * matches the request. Returns null when no content is loaded.
     */
    private Enemy pickMonsterTemplate(boolean isBoss) {
        Map<String, Enemy> registered = ContentRegistry.monsters();
        if (registered.isEmpty()) {
            return null;
        }
        List<Enemy> matching = new ArrayList<>();
        for (Enemy e : registered.values()) {
            if (e.isBoss() == isBoss) {
                matching.add(e);
            }
        }
        List<Enemy> pool = matching.isEmpty()
                ? new ArrayList<>(registered.values())
                : matching;
        return pool.get(random.nextInt(pool.size()));
    }

    public Summon generateSummon() {
        String[] behaviors = {"AGGRESSOR", "MENDER", "PROTECTOR"};
        return new Summon(
                "Ethereal Familiar",
                30 + (difficulty * 5),
                12 + (difficulty / 2),
                behaviors[random.nextInt(behaviors.length)],
                5 + difficulty + chaosLevel);
    }

    public Quest generateCustomRift(String title, int size, int riftDiff) {
        List<Scene> scenes = new ArrayList<>();
        for (int i = 0; i < size - 1; i++) {
            scenes.add(new Scene(
                    "Rift Layer " + (i + 1),
                    "The atmosphere crackles with unstable energy.",
                    generateGenericChoices()));
        }
        Scene bossScene = new Scene(
                "Heart of the Rift",
                "Entropy peaks. The guardian emerges.",
                generateBossChoices(),
                true);
        scenes.add(bossScene);
        return new Quest(title, "A multiversal tear threatening local reality.", scenes);
    }

    private List<Choice> generateGenericChoices() {
        List<Choice> choices = new ArrayList<>();
        choices.add(new Choice("Scavenge for parts", "You sift through debris and salvage useful tech."));
        choices.add(new Choice("Push deeper into the rift", "You descend further into unstable reality."));
        if (chaosLevel > 2) {
            choices.add(new Choice("Meditate on the void", "Your mind synchronizes with cosmic silence."));
        }
        return choices;
    }

    private List<Choice> generateBossChoices() {
        return List.of(
                new Choice("Challenge the Guardian", "You draw your weapon. The rift screams."),
                new Choice("Attempt to Seal the Rift", "You begin the stabilization ritual."));
    }

    public int getDifficulty() {
        return difficulty;
    }

    public int getChaosLevel() {
        return chaosLevel;
    }
}
