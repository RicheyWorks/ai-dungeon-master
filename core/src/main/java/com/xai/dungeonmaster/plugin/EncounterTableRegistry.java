package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Enemy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of {@link EncounterTable} plugins, keyed by biome
 * (lowercased). Mirrors {@code SpellEffectRegistry}'s design — ServiceLoader
 * discovery on first use plus explicit {@link #register} for the Spring layer.
 *
 * The engine asks {@link #dispatch} for an encounter matching a biome tag and
 * transparently falls back to the always-present {@code "default"} table
 * (bundled {@code DefaultEncounterTable}) when no themed table matches.
 */
public final class EncounterTableRegistry {

    /** Biome key of the always-available bundled fallback table. */
    public static final String DEFAULT_BIOME = "default";

    private static final Map<String, EncounterTable> TABLES = new ConcurrentHashMap<>();
    private static volatile boolean serviceLoaderRan = false;

    private EncounterTableRegistry() {}

    /** Register a table. Idempotent — last-write-wins, keyed by lowercased biome. */
    public static void register(EncounterTable table) {
        if (table == null || table.biome() == null) return;
        String key = table.biome().toLowerCase(Locale.ROOT);
        EncounterTable existing = TABLES.put(key, table);
        if (existing != null && existing != table) {
            System.err.println("WARN: EncounterTable '" + key + "' replaced by " + table.getClass().getName());
        }
    }

    /**
     * Roll an encounter for the given biome. Resolves the biome-specific table,
     * falling back to the {@code "default"} table when none matches. Returns an
     * empty list only if no table (not even the default) is registered — callers
     * should treat that as "generate nothing" and apply their own safety net.
     */
    public static List<Enemy> dispatch(Random random, int difficulty, int chaos,
                                       boolean isBoss, String biome) {
        return dispatch(random, difficulty, chaos, isBoss, biome, null);
    }

    /**
     * World-aware dispatch: passes the engine's {@link com.xai.dungeonmaster.WorldState}
     * to the table's 5-arg roll so faction reputation and story flags can shape
     * the encounter. Tables without the override fall back to their 4-arg roll.
     */
    public static List<Enemy> dispatch(Random random, int difficulty, int chaos,
                                       boolean isBoss, String biome,
                                       com.xai.dungeonmaster.WorldState world) {
        ensureServiceLoader();
        EncounterTable table = resolve(biome);
        if (table == null) {
            return List.of();
        }
        List<Enemy> rolled = table.roll(
                random != null ? random : new Random(),
                Math.max(1, difficulty),
                Math.max(0, chaos),
                isBoss,
                world);
        return (rolled == null) ? List.of() : rolled;
    }

    private static EncounterTable resolve(String biome) {
        if (biome != null) {
            EncounterTable t = TABLES.get(biome.toLowerCase(Locale.ROOT));
            if (t != null) return t;
        }
        return TABLES.get(DEFAULT_BIOME);
    }

    /** True if a table is registered for the given biome. */
    public static boolean isRegistered(String biome) {
        if (biome == null) return false;
        ensureServiceLoader();
        return TABLES.containsKey(biome.toLowerCase(Locale.ROOT));
    }

    /** Snapshot of all registered biome keys. */
    public static Set<String> registeredBiomes() {
        ensureServiceLoader();
        return Set.copyOf(TABLES.keySet());
    }

    /** Drop everything. Test-only — call from @BeforeEach/@AfterEach to reset state. */
    public static void clearForTests() {
        TABLES.clear();
        serviceLoaderRan = false;
    }

    private static void ensureServiceLoader() {
        if (serviceLoaderRan) return;
        synchronized (EncounterTableRegistry.class) {
            if (serviceLoaderRan) return;
            for (EncounterTable found : java.util.ServiceLoader.load(EncounterTable.class)) {
                register(found);
            }
            serviceLoaderRan = true;
        }
    }
}
