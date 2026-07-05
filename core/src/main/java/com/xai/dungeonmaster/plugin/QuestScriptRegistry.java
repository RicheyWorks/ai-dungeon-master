package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Quest;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of {@link QuestScript} plugins, keyed by id
 * (lowercased). Mirrors {@code SpellEffectRegistry}: ServiceLoader discovery on
 * first use plus explicit {@link #register}.
 *
 * The engine asks {@link #dispatch} for a quest by script id and falls back to
 * the always-present {@code "default"} script (bundled {@code DefaultQuestScript})
 * when the requested id is unknown, so quest generation is never left without a
 * builder.
 */
public final class QuestScriptRegistry {

    /** Id of the always-available bundled fallback script. */
    public static final String DEFAULT_SCRIPT = "default";

    private static final Map<String, QuestScript> SCRIPTS = new ConcurrentHashMap<>();
    private static volatile boolean serviceLoaderRan = false;

    private QuestScriptRegistry() {}

    /** Register a script. Idempotent — last-write-wins, keyed by lowercased id. */
    public static void register(QuestScript script) {
        if (script == null || script.id() == null) return;
        String key = script.id().toLowerCase(Locale.ROOT);
        QuestScript existing = SCRIPTS.put(key, script);
        if (existing != null && existing != script) {
            System.err.println("WARN: QuestScript '" + key + "' replaced by " + script.getClass().getName());
        }
    }

    /**
     * Build a quest from the script registered under {@code scriptId}, falling
     * back to the {@code "default"} script. Returns {@code null} only if no
     * script (not even the default) is registered.
     */
    public static Quest dispatch(String scriptId, DungeonMasterEngine engine, int difficulty, int chaos) {
        ensureServiceLoader();
        QuestScript script = resolve(scriptId);
        if (script == null) {
            return null;
        }
        return script.build(engine, Math.max(1, difficulty), Math.max(0, chaos));
    }

    private static QuestScript resolve(String scriptId) {
        if (scriptId != null) {
            QuestScript s = SCRIPTS.get(scriptId.toLowerCase(Locale.ROOT));
            if (s != null) return s;
        }
        return SCRIPTS.get(DEFAULT_SCRIPT);
    }

    /** True if a script is registered under the given id. */
    public static boolean isRegistered(String scriptId) {
        if (scriptId == null) return false;
        ensureServiceLoader();
        return SCRIPTS.containsKey(scriptId.toLowerCase(Locale.ROOT));
    }

    /** Snapshot of all registered script ids. */
    public static Set<String> registeredIds() {
        ensureServiceLoader();
        return Set.copyOf(SCRIPTS.keySet());
    }

    /** Drop everything. Test-only — call from @BeforeEach/@AfterEach to reset state. */
    public static void clearForTests() {
        SCRIPTS.clear();
        serviceLoaderRan = false;
    }

    private static void ensureServiceLoader() {
        if (serviceLoaderRan) return;
        synchronized (QuestScriptRegistry.class) {
            if (serviceLoaderRan) return;
            for (QuestScript found : java.util.ServiceLoader.load(QuestScript.class)) {
                register(found);
            }
            serviceLoaderRan = true;
        }
    }
}
