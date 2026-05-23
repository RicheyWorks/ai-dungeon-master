package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Enemy;
import com.xai.dungeonmaster.Item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide aggregator for all loaded ContentPacks.
 *
 * Content packs are layered: bundled content (items.json, monsters.json in
 * the core jar) loads first, then any packs discovered under content-packs/
 * are merged on top — later packs override earlier ones for the same id.
 *
 * DungeonGenerator queries this registry instead of using hardcoded arrays,
 * so adding a theme (Cozy, Sci-Fi, Horror) means dropping a pack folder,
 * not editing code.
 *
 * Thread-safety: ConcurrentHashMap. Packs are typically registered at
 * startup and read for the lifetime of the JVM.
 */
public final class ContentRegistry {

    private static final Map<String, Item> ITEMS = new ConcurrentHashMap<>();
    private static final Map<String, Enemy> MONSTERS = new ConcurrentHashMap<>();
    private static final Map<String, String> STRINGS = new ConcurrentHashMap<>();
    private static final Map<String, ContentPack> PACKS = new ConcurrentHashMap<>();

    private ContentRegistry() {}

    /**
     * Merge a content pack into the registry. Later packs win on id collisions.
     * Safe to call multiple times — re-registering a pack just replaces its
     * previous contributions.
     */
    public static void register(ContentPack pack) {
        if (pack == null || pack.id() == null) return;
        PACKS.put(pack.id(), pack);
        for (Map.Entry<String, Item> e : pack.items().entrySet()) {
            if (e.getKey() != null && e.getValue() != null) ITEMS.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Enemy> e : pack.monsters().entrySet()) {
            if (e.getKey() != null && e.getValue() != null) MONSTERS.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : pack.strings().entrySet()) {
            if (e.getKey() != null && e.getValue() != null) STRINGS.put(e.getKey(), e.getValue());
        }
    }

    /** Unmodifiable snapshot of the merged item set. */
    public static Map<String, Item> items() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(ITEMS));
    }

    /** Unmodifiable snapshot of the merged monster set. */
    public static Map<String, Enemy> monsters() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(MONSTERS));
    }

    /** Localized string lookup. Falls back to the key if missing. */
    public static String string(String key) {
        if (key == null) return "";
        String value = STRINGS.get(key);
        return value != null ? value : key;
    }

    /** Currently-loaded packs. */
    public static Map<String, ContentPack> packs() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(PACKS));
    }

    /** True if any content is registered. */
    public static boolean isLoaded() {
        return !ITEMS.isEmpty() || !MONSTERS.isEmpty();
    }

    /** Test-only reset. */
    public static void clearForTests() {
        ITEMS.clear();
        MONSTERS.clear();
        STRINGS.clear();
        PACKS.clear();
    }
}
