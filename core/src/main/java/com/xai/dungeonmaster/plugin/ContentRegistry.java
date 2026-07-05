package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Enemy;
import com.xai.dungeonmaster.Item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Process-wide aggregator for all loaded ContentPacks.
 *
 * Content packs are layered: registration order defines precedence — the
 * bundled pack registers first, external packs on top, later packs overriding
 * earlier ones for the same id. The active item/monster/string pools are
 * computed from the currently ENABLED packs, so a pack can be toggled off at
 * runtime (e.g. from the mod browser) and its content disappears without
 * touching the others.
 *
 * DungeonGenerator queries this registry instead of using hardcoded arrays.
 *
 * Thread-safety: all mutation and pool computation happen under a single lock;
 * merged views are cached and published as immutable snapshots, so readers on
 * the generation path always see a consistent pool, never a half-applied toggle.
 */
public final class ContentRegistry {

    private static final Object LOCK = new Object();

    /** Registration-ordered packs (insertion order = precedence). */
    private static final LinkedHashMap<String, ContentPack> PACKS = new LinkedHashMap<>();
    /** Ids explicitly disabled; a registered pack not in here is enabled. */
    private static final java.util.HashSet<String> DISABLED = new java.util.HashSet<>();

    // Cached merged views over the enabled packs; invalidated on any change.
    private static volatile Map<String, Item> itemsCache;
    private static volatile Map<String, Enemy> monstersCache;
    private static volatile Map<String, String> stringsCache;

    private ContentRegistry() {}

    /**
     * Merge a content pack into the registry (enabled by default). Later packs
     * win on id collisions. Re-registering the same id replaces the pack and
     * re-enables it.
     */
    public static void register(ContentPack pack) {
        if (pack == null || pack.id() == null) return;
        synchronized (LOCK) {
            PACKS.put(pack.id(), pack);
            DISABLED.remove(pack.id());
            invalidate();
        }
    }

    /**
     * Enable or disable a registered pack, recomputing the active pools. No-op
     * for an unknown pack id. Returns true if the pack is known (and the state
     * was applied), false otherwise.
     */
    public static boolean setEnabled(String id, boolean enabled) {
        if (id == null) return false;
        synchronized (LOCK) {
            if (!PACKS.containsKey(id)) return false;
            if (enabled) DISABLED.remove(id); else DISABLED.add(id);
            invalidate();
            return true;
        }
    }

    /** True if the pack is registered and enabled. */
    public static boolean isEnabled(String id) {
        if (id == null) return false;
        synchronized (LOCK) {
            return PACKS.containsKey(id) && !DISABLED.contains(id);
        }
    }

    /** True if the pack id is registered (enabled or not). */
    public static boolean isKnown(String id) {
        if (id == null) return false;
        synchronized (LOCK) {
            return PACKS.containsKey(id);
        }
    }

    /** Unmodifiable snapshot of the merged item set (enabled packs only). */
    public static Map<String, Item> items() {
        Map<String, Item> cached = itemsCache;
        if (cached != null) return cached;
        synchronized (LOCK) {
            if (itemsCache == null) {
                LinkedHashMap<String, Item> merged = new LinkedHashMap<>();
                for (Map.Entry<String, ContentPack> e : PACKS.entrySet()) {
                    if (DISABLED.contains(e.getKey())) continue;
                    for (Map.Entry<String, Item> it : e.getValue().items().entrySet()) {
                        if (it.getKey() != null && it.getValue() != null) merged.put(it.getKey(), it.getValue());
                    }
                }
                itemsCache = Collections.unmodifiableMap(merged);
            }
            return itemsCache;
        }
    }

    /** Unmodifiable snapshot of the merged monster set (enabled packs only). */
    public static Map<String, Enemy> monsters() {
        Map<String, Enemy> cached = monstersCache;
        if (cached != null) return cached;
        synchronized (LOCK) {
            if (monstersCache == null) {
                LinkedHashMap<String, Enemy> merged = new LinkedHashMap<>();
                for (Map.Entry<String, ContentPack> e : PACKS.entrySet()) {
                    if (DISABLED.contains(e.getKey())) continue;
                    for (Map.Entry<String, Enemy> m : e.getValue().monsters().entrySet()) {
                        if (m.getKey() != null && m.getValue() != null) merged.put(m.getKey(), m.getValue());
                    }
                }
                monstersCache = Collections.unmodifiableMap(merged);
            }
            return monstersCache;
        }
    }

    /** Localized string lookup over enabled packs. Falls back to the key if missing. */
    public static String string(String key) {
        if (key == null) return "";
        String value = stringsMap().get(key);
        return value != null ? value : key;
    }

    private static Map<String, String> stringsMap() {
        Map<String, String> cached = stringsCache;
        if (cached != null) return cached;
        synchronized (LOCK) {
            if (stringsCache == null) {
                LinkedHashMap<String, String> merged = new LinkedHashMap<>();
                for (Map.Entry<String, ContentPack> e : PACKS.entrySet()) {
                    if (DISABLED.contains(e.getKey())) continue;
                    for (Map.Entry<String, String> s : e.getValue().strings().entrySet()) {
                        if (s.getKey() != null && s.getValue() != null) merged.put(s.getKey(), s.getValue());
                    }
                }
                stringsCache = Collections.unmodifiableMap(merged);
            }
            return stringsCache;
        }
    }

    /** Currently-registered packs, in registration order (enabled or not). */
    public static Map<String, ContentPack> packs() {
        synchronized (LOCK) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(PACKS));
        }
    }

    /** Ids of registered packs that are currently disabled. */
    public static Set<String> disabledPackIds() {
        synchronized (LOCK) {
            return Set.copyOf(DISABLED);
        }
    }

    /** True if any content is currently active. */
    public static boolean isLoaded() {
        return !items().isEmpty() || !monsters().isEmpty();
    }

    /** Test-only reset. */
    public static void clearForTests() {
        synchronized (LOCK) {
            PACKS.clear();
            DISABLED.clear();
            invalidate();
        }
    }

    private static void invalidate() {
        itemsCache = null;
        monstersCache = null;
        stringsCache = null;
    }
}
