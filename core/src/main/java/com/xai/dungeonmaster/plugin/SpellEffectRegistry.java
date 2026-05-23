package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Entity;
import com.xai.dungeonmaster.Spell;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of SpellEffect plugins, keyed by id (uppercased).
 *
 * Discovery order:
 *   1. ServiceLoader: anything declaring META-INF/services/com.xai.dungeonmaster.plugin.SpellEffect
 *      is picked up automatically on first use. This is how bundled effects
 *      register without any Spring wiring — fine for pure-Java unit tests.
 *   2. Explicit register(): the Spring service layer also calls this for any
 *      SpellEffect beans it finds, so DI-style registration works too.
 *
 * Thread-safety: ConcurrentHashMap. Registration is idempotent — registering
 * the same id twice replaces the previous binding (with a warning log).
 */
public final class SpellEffectRegistry {

    private static final Map<String, SpellEffect> EFFECTS = new ConcurrentHashMap<>();
    private static volatile boolean serviceLoaderRan = false;

    private SpellEffectRegistry() {}

    /**
     * Register a SpellEffect. Idempotent — last-write-wins.
     */
    public static void register(SpellEffect effect) {
        if (effect == null || effect.id() == null) return;
        String key = effect.id().toUpperCase(Locale.ROOT);
        SpellEffect existing = EFFECTS.put(key, effect);
        if (existing != null && existing != effect) {
            System.err.println("WARN: SpellEffect '" + key + "' replaced by " + effect.getClass().getName());
        }
    }

    /**
     * Run the built-in dispatch for a Spell.cast() call.
     * Falls back to a "spell fizzles" narration if no effect is registered
     * for the spell's key.
     */
    public static String dispatch(DungeonMasterEngine engine,
                                  Adventurer caster,
                                  Entity target,
                                  Spell spell) {
        ensureServiceLoader();
        if (spell == null || spell.getSpellKey() == null) {
            return "The spell unravels before it can take shape.";
        }
        String key = spell.getSpellKey().toUpperCase(Locale.ROOT);
        SpellEffect effect = EFFECTS.get(key);
        if (effect == null) {
            return (caster != null ? caster.getName() : "Someone")
                    + " chants an ancient rhyme, but the rift remains silent.";
        }
        return effect.execute(engine, caster, target, spell);
    }

    /** True if the id is registered. */
    public static boolean isRegistered(String id) {
        if (id == null) return false;
        ensureServiceLoader();
        return EFFECTS.containsKey(id.toUpperCase(Locale.ROOT));
    }

    /** Snapshot of all registered ids. Useful for admin / debug endpoints. */
    public static java.util.Set<String> registeredIds() {
        ensureServiceLoader();
        return java.util.Set.copyOf(EFFECTS.keySet());
    }

    /** Drop everything. Test-only — call from @BeforeEach to reset state. */
    public static void clearForTests() {
        EFFECTS.clear();
        serviceLoaderRan = false;
    }

    private static void ensureServiceLoader() {
        if (serviceLoaderRan) return;
        synchronized (SpellEffectRegistry.class) {
            if (serviceLoaderRan) return;
            for (SpellEffect found : java.util.ServiceLoader.load(SpellEffect.class)) {
                register(found);
            }
            serviceLoaderRan = true;
        }
    }
}
