package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.Enemy;
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
 * This is the canonical enemy generation logic — {@code DungeonGenerator}
 * routes {@code generateEnemy} through {@link com.xai.dungeonmaster.plugin.EncounterTableRegistry}
 * to this table, mirroring how {@code Spell.cast} routes through the
 * {@code SpellEffectRegistry}.
 */
public final class DefaultEncounterTable implements EncounterTable {

    @Override public String id() { return "ENCOUNTER_DEFAULT"; }
    @Override public String displayName() { return "Default Encounter Table"; }
    @Override public String biome() { return "default"; }

    @Override
    public List<Enemy> roll(Random random, int difficulty, int chaos, boolean isBoss) {
        Random rng = (random != null) ? random : new Random();
        int diff = Math.max(1, difficulty);
        int chaosLevel = Math.max(0, chaos);

        // Draw a template from the loaded content registry when available so
        // monsters.json stats (HP/AC/attack) and boss flags actually drive
        // generation; fall back to legacy constants when nothing is loaded.
        Enemy template = pickMonsterTemplate(rng, isBoss);

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
        int hp = baseHp * diff + (rng.nextInt(30) * Math.max(1, chaosLevel));
        int ac = baseAc + (diff / 2);
        int atk = baseAtk + diff + (chaosLevel / 2);

        if (chaosLevel > 4 && !name.startsWith("Corrupted ")) {
            name = "Corrupted " + name;
        }

        Enemy enemy = new Enemy(name, hp, ac, atk, diff);
        if (isBoss) {
            enemy.setDamageDice("2d12");
        }
        return List.of(enemy);
    }

    /**
     * Pick a monster template from the registry, preferring one whose boss flag
     * matches the request. Returns null when no content is loaded.
     */
    private Enemy pickMonsterTemplate(Random random, boolean isBoss) {
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
}
