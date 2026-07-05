package com.xai.dungeonmaster;

import com.xai.dungeonmaster.plugin.EncounterTableRegistry;
import com.xai.dungeonmaster.plugin.LootTableRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Procedural content generator.
 *
 * Enemy and loot generation are dispatchable SPIs: {@link #generateEnemy} and
 * {@link #generateLoot} route through the {@link EncounterTableRegistry} and
 * {@link LootTableRegistry} respectively, resolving the {@code "default"} biome
 * table bundled with the engine. Those built-in tables draw from the
 * {@code ContentRegistry} (populated by ContentPacks at startup) and fall back
 * to legacy hardcoded pools when no pack is loaded — so unit tests still get
 * valid encounters without any resource loading, and content packs can register
 * biome-keyed tables that override generation without editing the engine.
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
        // Loot generation is now a dispatchable SPI: route through the
        // LootTableRegistry's "default" table (bundled DefaultLootTable), which
        // draws from the ContentRegistry and falls back to the legacy
        // rarity-weighted generator. Content packs can register biome-keyed
        // tables that override this without touching the engine.
        Item item = LootTableRegistry.dispatch(
                random, difficulty, chaosLevel, LootTableRegistry.DEFAULT_BIOME);
        if (item != null) {
            return item;
        }
        // Safety net: the bundled DefaultLootTable is ServiceLoader-registered,
        // so this is only reached if the registry was cleared at runtime.
        return new Item("Rift Scrap", "Useful scrap from a damaged timeline.",
                Item.ItemType.CONSUMABLE, Item.Rarity.COMMON, "HEAL", 15);
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
        // Enemy generation is now a dispatchable SPI: route through the
        // EncounterTableRegistry's "default" table (bundled DefaultEncounterTable),
        // which draws monster templates from the ContentRegistry and scales them
        // by difficulty and chaos. Content packs can register biome-keyed tables
        // that override this without touching the engine.
        List<Enemy> rolled = EncounterTableRegistry.dispatch(
                random, difficulty, chaosLevel, isBoss, EncounterTableRegistry.DEFAULT_BIOME);
        if (!rolled.isEmpty()) {
            return rolled.get(0);
        }
        // Safety net: the bundled DefaultEncounterTable is ServiceLoader-registered,
        // so this is only reached if the registry was cleared at runtime.
        int hp = (isBoss ? 320 : 60) * difficulty;
        Enemy enemy = new Enemy(isBoss ? "Harbinger of Entropy" : "Rift Stalker",
                hp, isBoss ? 20 : 12, 4, difficulty);
        if (isBoss) {
            enemy.setDamageDice("2d12");
        }
        return enemy;
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
