package com.xai.dungeonmaster.entitlement;

import com.xai.dungeonmaster.plugin.StorefrontIntegration;
import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side purchase-receipt validation and entitlement tracking — the thin
 * counterpart to the client {@link StorefrontIntegration} plugins. A client
 * forwards a receipt; this service routes it to the matching storefront's
 * {@link StorefrontIntegration#verifyReceipt}, and on success records the
 * product against the player's session.
 *
 * Entitlements are kept in memory (per session) for v1; a shared datastore is
 * the multi-node upgrade, mirroring the session store.
 */
@Service
public class EntitlementService {

    private final Map<String, Set<String>> owned = new ConcurrentHashMap<>();

    /** Verify a receipt through the named storefront and, if valid, grant the product. */
    public Grant verifyAndGrant(String sessionId, String storefrontId, String productId, String receipt) {
        if (sessionId == null || sessionId.isBlank()) {
            return new Grant(false, productId, storefrontId, "no session");
        }
        if (productId == null || productId.isBlank()) {
            return new Grant(false, productId, storefrontId, "productId is required");
        }
        StorefrontIntegration store = (storefrontId == null || storefrontId.isBlank())
                ? StorefrontRegistry.getActive()
                : StorefrontRegistry.get(storefrontId);
        if (store == null) {
            return new Grant(false, productId, storefrontId, "unknown storefront '" + storefrontId + "'");
        }
        if (!store.verifyReceipt(receipt)) {
            return new Grant(false, productId, store.id(), "receipt failed verification");
        }
        owned.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(productId);
        return new Grant(true, productId, store.id(), "granted");
    }

    /** Products the session currently owns. */
    public Set<String> entitlements(String sessionId) {
        Set<String> s = (sessionId == null) ? null : owned.get(sessionId);
        return (s == null) ? Set.of() : Set.copyOf(s);
    }

    public boolean isEntitled(String sessionId, String productId) {
        Set<String> s = (sessionId == null) ? null : owned.get(sessionId);
        return s != null && s.contains(productId);
    }

    /** Outcome of a verify-and-grant attempt. */
    public record Grant(boolean granted, String productId, String storefront, String reason) {}
}
