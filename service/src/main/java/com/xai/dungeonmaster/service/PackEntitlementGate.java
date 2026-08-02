package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.entitlement.EntitlementService;
import com.xai.dungeonmaster.plugin.ContentPack;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Decides whether a session may enable a content pack based on
 * {@link ContentPack#requiredProductIds()}.
 */
@Component
public class PackEntitlementGate {

    private final EntitlementService entitlements;
    private final boolean enabled;

    @org.springframework.beans.factory.annotation.Autowired
    public PackEntitlementGate(
            EntitlementService entitlements,
            @Value("${game.content.entitlement-gates:true}") boolean enabled) {
        this.entitlements = entitlements;
        this.enabled = enabled;
    }

    /** Visible for tests. */
    public PackEntitlementGate(EntitlementService entitlements) {
        this(entitlements, true);
    }

    public boolean gatesEnabled() {
        return enabled;
    }

    public List<String> requiredProductIds(String packId) {
        ContentPack pack = ContentRegistry.packs().get(packId);
        if (pack == null) return List.of();
        return pack.requiredProductIds() == null ? List.of() : pack.requiredProductIds();
    }

    public boolean isGated(String packId) {
        if (!enabled) return false;
        return !requiredProductIds(packId).isEmpty();
    }

    /**
     * @return null if allowed; otherwise a human-readable denial reason
     */
    public String denyReason(String sessionId, String packId) {
        if (!enabled) return null;
        ContentPack pack = ContentRegistry.packs().get(packId);
        if (pack == null) return "Unknown content pack: " + packId;
        List<String> required = pack.requiredProductIds();
        if (required == null || required.isEmpty()) return null;
        if (sessionId == null || sessionId.isBlank()) {
            return "Authentication required to enable pack '" + packId + "'";
        }
        Set<String> owned = entitlements.entitlements(sessionId);
        if (pack.requireAllProducts()) {
            for (String sku : required) {
                if (!owned.contains(sku)) {
                    return "Missing entitlement '" + sku + "' for pack '" + packId + "'";
                }
            }
            return null;
        }
        for (String sku : required) {
            if (owned.contains(sku)) return null;
        }
        return "Requires one of entitlements " + required + " for pack '" + packId + "'";
    }

    public boolean isEntitled(String sessionId, String packId) {
        return denyReason(sessionId, packId) == null;
    }
}
