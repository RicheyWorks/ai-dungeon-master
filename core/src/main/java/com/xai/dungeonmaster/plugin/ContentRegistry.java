package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Enemy;
import com.xai.dungeonmaster.Item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

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
 * <p>For multi-tenant servers, {@link #pushEnabledOverride(Set)} installs a
 * request-scoped enabled-pack set (ThreadLocal). While set, {@link #isEnabled}
 * and the merged pools use that set instead of the process DISABLED set — so
 * one session can enable DLC without exposing it to every other player.
 *
 * DungeonGenerator queries this registry instead of using hardcoded arrays.
 *
 * Thread-safety: all mutation and pool computation happen under a single lock;
 * merged views are cached and published as immutable snapshots, so readers on
 * the generation path always see a consistent pool, never a half-applied toggle.
 * Request overrides bypass the process cache and merge live.
 */
public final class ContentRegistry {

    private static final Object LOCK = new Object();

    /** Registration-ordered packs (insertion order = precedence). */
    private static final LinkedHashMap<String, ContentPack> PACKS = new LinkedHashMap<>();
    /** Ids explicitly disabled; a registered pack not in here is enabled. */
    private static final java.util.HashSet<String> DISABLED = new java.util.HashSet<>();

    /**
     * When non-null, only packs in this set are treated as enabled for the
     * current thread (session-scoped multi-tenant view).
     */
    private static final ThreadLocal<Set<String>> ENABLED_OVERRIDE = new ThreadLocal<>();

    // Cached merged views over the enabled packs; invalidated on any change.
    private static volatile Map<String, Item> itemsCache;
    private static volatile Map<String, Enemy> monstersCache;
    private static volatile Map<String, String> stringsCache;
    private static volatile Map<String, com.xai.dungeonmaster.Npc> npcsCache;
    private static volatile Map<String, com.xai.dungeonmaster.Faction> factionsCache;

    private ContentRegistry() {}

    /**
     * Install a request-scoped enabled-pack set for this thread. Pass
     * {@code null} or use {@link #clearEnabledOverride()} to restore process defaults.
     */
    public static void pushEnabledOverride(Set<String> enabledPackIds) {
        if (enabledPackIds == null) {
            ENABLED_OVERRIDE.remove();
        } else {
            ENABLED_OVERRIDE.set(Set.copyOf(enabledPackIds));
        }
    }

    /** Clear the request-scoped enabled-pack override. */
    public static void clearEnabledOverride() {
        ENABLED_OVERRIDE.remove();
    }

    /** True when this thread has a session-scoped pack overlay active. */
    public static boolean hasEnabledOverride() {
        return ENABLED_OVERRIDE.get() != null;
    }

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
     *
     * <p>This mutates the <em>process</em> default. Session overrides live in
     * {@code SessionPackService} and do not call this for multi-tenant toggles.
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

    /** True if the pack is registered and enabled (honours request override). */
    public static boolean isEnabled(String id) {
        if (id == null) return false;
        Set<String> override = ENABLED_OVERRIDE.get();
        if (override != null) {
            return override.contains(id);
        }
        synchronized (LOCK) {
            return PACKS.containsKey(id) && !DISABLED.contains(id);
        }
    }

    /** Process-default enabled state (ignores request override). */
    public static boolean isProcessEnabled(String id) {
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
        Set<String> override = ENABLED_OVERRIDE.get();
        if (override != null) {
            return mergeLive(override, ContentPack::items);
        }
        Map<String, Item> cached = itemsCache;
        if (cached != null) return cached;
        synchronized (LOCK) {
            if (itemsCache == null) {
                itemsCache = mergeProcess(ContentPack::items);
            }
            return itemsCache;
        }
    }

    /** Unmodifiable snapshot of the merged monster set (enabled packs only). */
    public static Map<String, Enemy> monsters() {
        Set<String> override = ENABLED_OVERRIDE.get();
        if (override != null) {
            return mergeLive(override, ContentPack::monsters);
        }
        Map<String, Enemy> cached = monstersCache;
        if (cached != null) return cached;
        synchronized (LOCK) {
            if (monstersCache == null) {
                monstersCache = mergeProcess(ContentPack::monsters);
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
        Set<String> override = ENABLED_OVERRIDE.get();
        if (override != null) {
            return mergeLive(override, ContentPack::strings);
        }
        Map<String, String> cached = stringsCache;
        if (cached != null) return cached;
        synchronized (LOCK) {
            if (stringsCache == null) {
                stringsCache = mergeProcess(ContentPack::strings);
            }
            return stringsCache;
        }
    }

    /** Unmodifiable snapshot of the merged NPC set (enabled packs only). */
    public static Map<String, com.xai.dungeonmaster.Npc> npcs() {
        Set<String> override = ENABLED_OVERRIDE.get();
        if (override != null) {
            return mergeLive(override, ContentPack::npcs);
        }
        Map<String, com.xai.dungeonmaster.Npc> cached = npcsCache;
        if (cached != null) return cached;
        synchronized (LOCK) {
            if (npcsCache == null) {
                npcsCache = mergeProcess(ContentPack::npcs);
            }
            return npcsCache;
        }
    }

    /** Unmodifiable snapshot of the merged faction set (enabled packs only). */
    public static Map<String, com.xai.dungeonmaster.Faction> factions() {
        Set<String> override = ENABLED_OVERRIDE.get();
        if (override != null) {
            return mergeLive(override, ContentPack::factions);
        }
        Map<String, com.xai.dungeonmaster.Faction> cached = factionsCache;
        if (cached != null) return cached;
        synchronized (LOCK) {
            if (factionsCache == null) {
                factionsCache = mergeProcess(ContentPack::factions);
            }
            return factionsCache;
        }
    }

    /** Currently-registered packs, in registration order (enabled or not). */
    public static Map<String, ContentPack> packs() {
        synchronized (LOCK) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(PACKS));
        }
    }

    /** Ids of registered packs that are currently disabled (process default). */
    public static Set<String> disabledPackIds() {
        synchronized (LOCK) {
            return Set.copyOf(DISABLED);
        }
    }

    /**
     * Pack ids currently considered enabled on this thread (override or process).
     */
    public static Set<String> enabledPackIds() {
        Set<String> override = ENABLED_OVERRIDE.get();
        if (override != null) return override;
        LinkedHashSet<String> out = new LinkedHashSet<>();
        synchronized (LOCK) {
            for (String id : PACKS.keySet()) {
                if (!DISABLED.contains(id)) out.add(id);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** True if any content is currently active. */
    public static boolean isLoaded() {
        return !items().isEmpty() || !monsters().isEmpty();
    }

    /** Test-only reset. */
    public static void clearForTests() {
        ENABLED_OVERRIDE.remove();
        synchronized (LOCK) {
            PACKS.clear();
            DISABLED.clear();
            invalidate();
        }
    }

    private static <T> Map<String, T> mergeProcess(Function<ContentPack, Map<String, T>> extractor) {
        LinkedHashMap<String, T> merged = new LinkedHashMap<>();
        for (Map.Entry<String, ContentPack> e : PACKS.entrySet()) {
            if (DISABLED.contains(e.getKey())) continue;
            putAll(merged, extractor.apply(e.getValue()));
        }
        return Collections.unmodifiableMap(merged);
    }

    private static <T> Map<String, T> mergeLive(
            Set<String> enabled, Function<ContentPack, Map<String, T>> extractor) {
        LinkedHashMap<String, T> merged = new LinkedHashMap<>();
        synchronized (LOCK) {
            for (Map.Entry<String, ContentPack> e : PACKS.entrySet()) {
                if (!enabled.contains(e.getKey())) continue;
                putAll(merged, extractor.apply(e.getValue()));
            }
        }
        return Collections.unmodifiableMap(merged);
    }

    private static <T> void putAll(Map<String, T> dest, Map<String, T> part) {
        if (part == null) return;
        for (Map.Entry<String, T> it : part.entrySet()) {
            if (it.getKey() != null && it.getValue() != null) {
                dest.put(it.getKey(), it.getValue());
            }
        }
    }

    private static void invalidate() {
        itemsCache = null;
        monstersCache = null;
        stringsCache = null;
        npcsCache = null;
        factionsCache = null;
    }
}
