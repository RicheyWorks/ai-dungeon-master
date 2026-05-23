package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Item;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of ItemEffect plugins. Mirrors SpellEffectRegistry's
 * design — ServiceLoader + explicit register() — applied to items.
 */
public final class ItemEffectRegistry {

    private static final Map<String, ItemEffect> EFFECTS = new ConcurrentHashMap<>();
    private static volatile boolean serviceLoaderRan = false;

    private ItemEffectRegistry() {}

    public static void register(ItemEffect effect) {
        if (effect == null || effect.id() == null) return;
        String key = effect.id().toUpperCase(Locale.ROOT);
        ItemEffect existing = EFFECTS.put(key, effect);
        if (existing != null && existing != effect) {
            System.err.println("WARN: ItemEffect '" + key + "' replaced by " + effect.getClass().getName());
        }
    }

    public static String dispatch(DungeonMasterEngine engine,
                                  Adventurer user,
                                  Item item) {
        ensureServiceLoader();
        if (item == null || item.getEffectKey() == null) {
            return "The item disintegrates without effect.";
        }
        String key = item.getEffectKey().toUpperCase(Locale.ROOT);
        ItemEffect effect = EFFECTS.get(key);
        String logPrefix = "[" + item.getRarity() + "] " + item.getName() + ": ";
        if (effect == null) {
            return logPrefix + "The item hums with a strange frequency, but nothing happens in this dimension.";
        }
        return effect.execute(engine, user, item);
    }

    public static boolean isRegistered(String id) {
        if (id == null) return false;
        ensureServiceLoader();
        return EFFECTS.containsKey(id.toUpperCase(Locale.ROOT));
    }

    public static java.util.Set<String> registeredIds() {
        ensureServiceLoader();
        return java.util.Set.copyOf(EFFECTS.keySet());
    }

    public static void clearForTests() {
        EFFECTS.clear();
        serviceLoaderRan = false;
    }

    private static void ensureServiceLoader() {
        if (serviceLoaderRan) return;
        synchronized (ItemEffectRegistry.class) {
            if (serviceLoaderRan) return;
            for (ItemEffect found : java.util.ServiceLoader.load(ItemEffect.class)) {
                register(found);
            }
            serviceLoaderRan = true;
        }
    }
}
