package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.Enemy;
import com.xai.dungeonmaster.Faction;
import com.xai.dungeonmaster.WorldState;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.plugin.EncounterTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The bundled {@code "default"} encounter table. Draws a monster template from
 * the {@link ContentRegistry} (so monsters.json stats drive generation) and
 * scales it by difficulty and chaos, falling back to synthetic constants when
 * no content pack is loaded.
 *
 * When a {@link WorldState} is available, templates tagged with a
 * {@code factionId} are weighted by the party's effective reputation with that
 * faction — hostile standing favors those monsters, friendly standing deprioritizes
 * them (ADR-001 Phase 4 production path).
 */
public final class DefaultEncounterTable implements EncounterTable {

    @Override public String id() { return "ENCOUNTER_DEFAULT"; }
    @Override public String displayName() { return "Default Encounter Table"; }
    @Override public String biome() { return "default"; }

    @Override
    public List<Enemy> roll(Random random, int difficulty, int chaos, boolean isBoss) {
        return roll(random, difficulty, chaos, isBoss, null);
    }

    @Override
    public List<Enemy> roll(Random random, int difficulty, int chaos, boolean isBoss,
                            WorldState world) {
        Random rng = (random != null) ? random : new Random();
        int diff = Math.max(1, difficulty);
        int chaosLevel = Math.max(0, chaos);

        Enemy template = pickMonsterTemplate(rng, isBoss, world);

        String name;
        int baseHp, baseAc, baseAtk;
        String factionId = null;
        if (template != null) {
            name = template.getName();
            baseHp = template.getMaxHp();
            baseAc = template.getAC();
            baseAtk = template.getAttackBonus();
            factionId = template.getFactionId();
        } else {
            name = isBoss ? "Harbinger of Entropy" : "Rift Stalker";
            baseHp = isBoss ? 320 : 60;
            baseAc = 12 + (isBoss ? 8 : 0);
            baseAtk = 4;
        }

        // Scale the JSON/legacy baseline by difficulty and chaos.
        int hp = baseHp * diff + (rng.nextInt(30) * Math.max(1, chaosLevel));
        int ac = baseAc + (diff / 2);
        int atk = baseAtk + diff + (chaosLevel / 2);

        if (chaosLevel > 4 && !name.startsWith("Corrupted ")) {
            name = "Corrupted " + name;
        }

        Enemy enemy = new Enemy(name, hp, ac, atk, diff);
        if (isBoss) {
            enemy.setDamageDice("2d12");
            enemy.setBoss(true);
        } else if (template != null && template.isBoss()) {
            enemy.setBoss(true);
        }
        if (factionId != null) {
            enemy.setFactionId(factionId);
        }
        return List.of(enemy);
    }

    /**
     * Pick a monster template from the registry, preferring one whose boss flag
     * matches the request. When {@code world} is present, weight by faction
     * reputation. Returns null when no content is loaded.
     */
    private Enemy pickMonsterTemplate(Random random, boolean isBoss, WorldState world) {
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

        if (world == null || pool.size() == 1) {
            return pool.get(random.nextInt(pool.size()));
        }
        return weightedPick(random, pool, world);
    }

    /**
     * Weighted random pick: hostile factions get higher weight, friendly lower.
     * Untagged monsters stay at weight 1. Weight is always at least 1 so every
     * template remains reachable.
     */
    static Enemy weightedPick(Random random, List<Enemy> pool, WorldState world) {
        int[] weights = new int[pool.size()];
        int total = 0;
        for (int i = 0; i < pool.size(); i++) {
            int w = weightFor(pool.get(i), world);
            weights[i] = w;
            total += w;
        }
        int roll = random.nextInt(Math.max(1, total));
        int cumulative = 0;
        for (int i = 0; i < pool.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                return pool.get(i);
            }
        }
        return pool.get(pool.size() - 1);
    }

    /**
     * Effective reputation = pack base + accumulated WorldState delta.
     * Weight ladder: rep <= -3 → 8, <= -1 → 4, 0 → 2, >= 1 → 1.
     * Untagged templates always weigh 1.
     */
    public static int weightFor(Enemy template, WorldState world) {

        if (template == null || world == null) return 1;
        String factionId = template.getFactionId();
        if (factionId == null || factionId.isBlank()) return 1;

        int base = 0;
        Faction faction = ContentRegistry.factions().get(factionId);
        if (faction != null) {
            base = faction.getBaseReputation();
        }
        int rep = base + world.getFlag(Faction.reputationFlag(factionId));
        if (rep <= -3) return 8;
        if (rep <= -1) return 4;
        if (rep <= 0) return 2;
        return 1;
    }
}
