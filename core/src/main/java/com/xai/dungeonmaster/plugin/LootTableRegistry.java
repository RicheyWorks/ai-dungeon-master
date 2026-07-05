package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Item;

import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of {@link LootTable} plugins, keyed by biome
 * (lowercased). Mirrors {@code EncounterTableRegistry}: ServiceLoader discovery
 * plus explicit {@link #register}, with a {@code "default"} biome fallback
 * provided by the bundled {@code DefaultLootTable}.
 */
public final class LootTableRegistry {

    /** Biome key of the always-available bundled fallback table. */
    public static final String DEFAULT_BIOME = "default";

    private static final Map<String, LootTable> TABLES = new ConcurrentHashMap<>();
    private static volatile boolean serviceLoaderRan = false;

    private LootTableRegistry() {}

    /** Register a table. Idempotent — last-write-wins, keyed by lowercased biome. */
    public static void register(LootTable table) {
        if (table == null || table.biome() == null) return;
        String key = table.biome().toLowerCase(Locale.ROOT);
        LootTable existing = TABLES.put(key, table);
        if (existing != null && existing != table) {
            System.err.println("WARN: LootTable '" + key + "' replaced by " + table.getClass().getName());
        }
    }

    /**
     * Roll a single loot drop for the given biome, falling back to the
     * {@code "default"} table. May return {@code null} to indicate "no drop"
     * (or when no table at all is registered).
     */
    public static Item dispatch(Random random, int difficulty, int chaos, String biome) {
        ensureServiceLoader();
        LootTable table = resolve(biome);
        if (table == null) {
            return null;
        }
        return table.roll(
                random != null ? random : new Random(),
                Math.max(1, difficulty),
                Math.max(0, chaos));
    }

    private static LootTable resolve(String biome) {
        if (biome != null) {
            LootTable t = TABLES.get(biome.toLowerCase(Locale.ROOT));
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
        synchronized (LootTableRegistry.class) {
            if (serviceLoaderRan) return;
            for (LootTable found : java.util.ServiceLoader.load(LootTable.class)) {
                register(found);
            }
            serviceLoaderRan = true;
        }
    }
}
