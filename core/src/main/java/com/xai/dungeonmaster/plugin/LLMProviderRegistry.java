package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.plugin.builtin.LocalStubProvider;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of {@link LLMProvider} plugins, keyed by id (lowercased).
 *
 * Mirrors {@code SpellEffectRegistry}: ServiceLoader discovery on first use
 * (anything declaring META-INF/services/com.xai.dungeonmaster.plugin.LLMProvider)
 * plus explicit {@link #register} for the Spring layer.
 *
 * {@link #getActive()} returns the currently selected narration provider and is
 * guaranteed non-null: it falls back to the bundled offline
 * {@link LocalStubProvider} when nothing else is registered or the selected
 * provider reports DOWN health.
 */
public final class LLMProviderRegistry {

    /** Id of the always-available offline fallback. */
    public static final String FALLBACK_ID = LocalStubProvider.ID;

    private static final Map<String, LLMProvider> PROVIDERS = new ConcurrentHashMap<>();
    private static final LLMProvider FALLBACK = new LocalStubProvider();
    private static volatile String activeId = FALLBACK_ID;
    private static volatile boolean serviceLoaderRan = false;

    private LLMProviderRegistry() {}

    /** Register a provider. Idempotent — last-write-wins, keyed by lowercased id. */
    public static void register(LLMProvider provider) {
        if (provider == null || provider.id() == null) return;
        String key = provider.id().toLowerCase(Locale.ROOT);
        LLMProvider existing = PROVIDERS.put(key, provider);
        if (existing != null && existing != provider) {
            System.err.println("WARN: LLMProvider '" + key + "' replaced by " + provider.getClass().getName());
        }
    }

    /** Select the active provider by id. Unknown ids leave the selection unchanged. */
    public static boolean setActive(String id) {
        if (id == null) return false;
        ensureServiceLoader();
        String key = id.toLowerCase(Locale.ROOT);
        if (PROVIDERS.containsKey(key) || key.equals(FALLBACK_ID)) {
            activeId = key;
            return true;
        }
        return false;
    }

    /**
     * The active provider, never null. Falls back to the offline stub when the
     * selected provider is absent or reports DOWN health.
     */
    public static LLMProvider getActive() {
        ensureServiceLoader();
        LLMProvider p = PROVIDERS.get(activeId);
        if (p == null) p = FALLBACK;
        return (p.health() == LLMProvider.HealthStatus.DOWN) ? FALLBACK : p;
    }

    /** Look up a provider by id, or null if not registered. */
    public static LLMProvider get(String id) {
        if (id == null) return null;
        ensureServiceLoader();
        return PROVIDERS.get(id.toLowerCase(Locale.ROOT));
    }

    /** Snapshot of all registered ids. Useful for admin / debug endpoints. */
    public static Set<String> registeredIds() {
        ensureServiceLoader();
        return Set.copyOf(PROVIDERS.keySet());
    }

    /** Drop everything. Test-only — call from @BeforeEach to reset state. */
    public static void clearForTests() {
        PROVIDERS.clear();
        activeId = FALLBACK_ID;
        serviceLoaderRan = false;
    }

    private static void ensureServiceLoader() {
        if (serviceLoaderRan) return;
        synchronized (LLMProviderRegistry.class) {
            if (serviceLoaderRan) return;
            for (LLMProvider found : java.util.ServiceLoader.load(LLMProvider.class)) {
                register(found);
            }
            // The offline fallback must always be available.
            PROVIDERS.putIfAbsent(FALLBACK_ID, FALLBACK);
            serviceLoaderRan = true;
        }
    }
}
