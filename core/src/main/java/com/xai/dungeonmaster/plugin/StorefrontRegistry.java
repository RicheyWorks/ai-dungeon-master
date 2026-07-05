package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.plugin.builtin.NoOpStorefront;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of {@link StorefrontIntegration} plugins, keyed by id
 * (lowercased). Mirrors {@code LLMProviderRegistry}: ServiceLoader discovery
 * plus explicit {@link #register}, with an always-available offline fallback.
 *
 * Each client binary loads exactly one storefront (the store it shipped
 * through), so {@link #getActive()} returns a single selected integration and
 * is guaranteed non-null — it falls back to the bundled offline
 * {@link NoOpStorefront} when nothing else is registered. The server keeps a
 * thin counterpart that calls {@link StorefrontIntegration#verifyReceipt} on
 * the matching plugin to validate receipts.
 */
public final class StorefrontRegistry {

    /** Id of the always-available offline no-op storefront. */
    public static final String FALLBACK_ID = NoOpStorefront.ID;

    private static final Map<String, StorefrontIntegration> STOREFRONTS = new ConcurrentHashMap<>();
    private static final StorefrontIntegration FALLBACK = new NoOpStorefront();
    private static volatile String activeId = FALLBACK_ID;
    private static volatile boolean serviceLoaderRan = false;

    private StorefrontRegistry() {}

    /** Register a storefront. Idempotent — last-write-wins, keyed by lowercased id. */
    public static void register(StorefrontIntegration storefront) {
        if (storefront == null || storefront.id() == null) return;
        String key = storefront.id().toLowerCase(Locale.ROOT);
        StorefrontIntegration existing = STOREFRONTS.put(key, storefront);
        if (existing != null && existing != storefront) {
            System.err.println("WARN: StorefrontIntegration '" + key + "' replaced by " + storefront.getClass().getName());
        }
    }

    /** Select the active storefront by id. Unknown ids leave the selection unchanged. */
    public static boolean setActive(String id) {
        if (id == null) return false;
        ensureServiceLoader();
        String key = id.toLowerCase(Locale.ROOT);
        if (STOREFRONTS.containsKey(key) || key.equals(FALLBACK_ID)) {
            activeId = key;
            return true;
        }
        return false;
    }

    /** The active storefront, never null. Falls back to the offline no-op. */
    public static StorefrontIntegration getActive() {
        ensureServiceLoader();
        StorefrontIntegration s = STOREFRONTS.get(activeId);
        return (s != null) ? s : FALLBACK;
    }

    /** Look up a storefront by id, or null if not registered. */
    public static StorefrontIntegration get(String id) {
        if (id == null) return null;
        ensureServiceLoader();
        return STOREFRONTS.get(id.toLowerCase(Locale.ROOT));
    }

    /** True if a storefront is registered under the given id. */
    public static boolean isRegistered(String id) {
        if (id == null) return false;
        ensureServiceLoader();
        return STOREFRONTS.containsKey(id.toLowerCase(Locale.ROOT));
    }

    /** Snapshot of all registered ids. */
    public static Set<String> registeredIds() {
        ensureServiceLoader();
        return Set.copyOf(STOREFRONTS.keySet());
    }

    /** Drop everything. Test-only — call from @BeforeEach/@AfterEach to reset state. */
    public static void clearForTests() {
        STOREFRONTS.clear();
        activeId = FALLBACK_ID;
        serviceLoaderRan = false;
    }

    private static void ensureServiceLoader() {
        if (serviceLoaderRan) return;
        synchronized (StorefrontRegistry.class) {
            if (serviceLoaderRan) return;
            for (StorefrontIntegration found : java.util.ServiceLoader.load(StorefrontIntegration.class)) {
                register(found);
            }
            // The offline no-op must always be available.
            STOREFRONTS.putIfAbsent(FALLBACK_ID, FALLBACK);
            serviceLoaderRan = true;
        }
    }
}
