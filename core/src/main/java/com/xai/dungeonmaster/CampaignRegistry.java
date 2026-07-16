package com.xai.dungeonmaster;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of loaded {@link Campaign}s, keyed by lowercased id.
 * Mirrors the plugin registries' shape (register / get / ids / clearForTests)
 * but holds pure data, not SPI implementations — campaigns arrive from
 * content packs' {@code campaigns/*.json}, not from ServiceLoader.
 */
public final class CampaignRegistry {

    private static final Map<String, Campaign> CAMPAIGNS = new ConcurrentHashMap<>();

    private CampaignRegistry() {}

    /** Register a campaign. Last-write-wins, keyed by lowercased id. */
    public static void register(Campaign campaign) {
        if (campaign == null || campaign.getId().isEmpty()) return;
        String key = campaign.getId().toLowerCase(Locale.ROOT);
        Campaign existing = CAMPAIGNS.put(key, campaign);
        if (existing != null && existing != campaign) {
            System.err.println("WARN: Campaign '" + key + "' replaced.");
        }
    }

    /** The campaign registered under this id, or null. */
    public static Campaign get(String id) {
        if (id == null || id.isBlank()) return null;
        return CAMPAIGNS.get(id.trim().toLowerCase(Locale.ROOT));
    }

    /** Snapshot of registered campaign ids. */
    public static Set<String> registeredIds() {
        return Set.copyOf(CAMPAIGNS.keySet());
    }

    /** Drop everything. Test-only. */
    public static void clearForTests() {
        CAMPAIGNS.clear();
    }
}
