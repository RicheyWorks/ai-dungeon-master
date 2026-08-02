package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.content.SessionPackService;
import com.xai.dungeonmaster.entitlement.EntitlementStore;
import com.xai.dungeonmaster.plugin.ContentPack;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * After a store grant, enable any installed content packs whose
 * {@link ContentPack#requiredProductIds()} are fully satisfied for the session.
 */
@Component
public class PackAutoEnabler {

    private final EntitlementStore entitlementStore;
    private final SessionPackService sessionPacks;
    private final boolean enabled;

    @org.springframework.beans.factory.annotation.Autowired
    public PackAutoEnabler(
            EntitlementStore entitlementStore,
            SessionPackService sessionPacks,
            @Value("${game.content.auto-enable-on-grant:true}") boolean enabled) {
        this.entitlementStore = entitlementStore;
        this.sessionPacks = sessionPacks != null ? sessionPacks : new SessionPackService();
        this.enabled = enabled;
    }

    /** Visible for tests. */
    public PackAutoEnabler(EntitlementStore entitlementStore) {
        this(entitlementStore, new SessionPackService(), true);
    }

    public PackAutoEnabler(EntitlementStore entitlementStore, SessionPackService sessionPacks) {
        this(entitlementStore, sessionPacks, true);
    }

    public PackAutoEnabler(EntitlementStore entitlementStore, boolean enabled) {
        this(entitlementStore, new SessionPackService(), enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enable every registered pack gated by {@code productId} once required SKUs
     * are owned. Returns pack ids newly enabled.
     */
    public List<String> enableForGrant(String sessionId, String productId) {
        if (!enabled || sessionId == null || sessionId.isBlank() || productId == null || productId.isBlank()) {
            return List.of();
        }
        Set<String> owned = entitlementStore.products(sessionId);
        List<String> enabledPacks = new ArrayList<>();
        for (ContentPack pack : ContentRegistry.packs().values()) {
            List<String> required = pack.requiredProductIds();
            if (required == null || required.isEmpty()) continue;
            if (!required.contains(productId)) continue;
            if (!satisfies(pack, owned)) continue;
            if (sessionPacks.isEnabled(sessionId, pack.id())) continue;
            if (sessionPacks.setEnabled(sessionId, pack.id(), true)) {
                enabledPacks.add(pack.id());
            }
        }
        return List.copyOf(enabledPacks);
    }

    private static boolean satisfies(ContentPack pack, Set<String> owned) {
        List<String> required = pack.requiredProductIds();
        if (required == null || required.isEmpty()) return true;
        if (pack.requireAllProducts()) {
            for (String sku : required) {
                if (!owned.contains(sku)) return false;
            }
            return true;
        }
        for (String sku : required) {
            if (owned.contains(sku)) return true;
        }
        return false;
    }
}
