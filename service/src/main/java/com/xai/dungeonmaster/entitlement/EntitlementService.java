package com.xai.dungeonmaster.entitlement;

import com.xai.dungeonmaster.plugin.StorefrontIntegration;
import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Server-side purchase-receipt validation and entitlement tracking — the thin
 * counterpart to the client {@link StorefrontIntegration} plugins. A client
 * forwards a receipt; this service routes it to the matching storefront's
 * {@link StorefrontIntegration#verifyReceipt}, and on success records the
 * product against the player's session via a pluggable {@link EntitlementStore}.
 *
 * Default store is in-memory; file-backed (and locked) stores survive restarts
 * and multi-process deployments that share a volume — mirroring the session store.
 */
@Service
public class EntitlementService {

    private final EntitlementStore store;

    /** Convenience constructor for tests/embedders (in-memory store). */
    public EntitlementService() {
        this(new InMemoryEntitlementStore());
    }

    @Autowired
    public EntitlementService(EntitlementStore store) {
        this.store = (store != null) ? store : new InMemoryEntitlementStore();
    }

    /** Verify a receipt through the named storefront and, if valid, grant the product. */
    public Grant verifyAndGrant(String sessionId, String storefrontId, String productId, String receipt) {
        if (sessionId == null || sessionId.isBlank()) {
            return new Grant(false, productId, storefrontId, "no session");
        }
        if (productId == null || productId.isBlank()) {
            return new Grant(false, productId, storefrontId, "productId is required");
        }
        StorefrontIntegration storefront = (storefrontId == null || storefrontId.isBlank())
                ? StorefrontRegistry.getActive()
                : StorefrontRegistry.get(storefrontId);
        if (storefront == null) {
            return new Grant(false, productId, storefrontId, "unknown storefront '" + storefrontId + "'");
        }
        if (!storefront.verifyReceipt(receipt)) {
            return new Grant(false, productId, storefront.id(), "receipt failed verification");
        }
        store.grant(sessionId, productId);
        return new Grant(true, productId, storefront.id(), "granted");
    }

    /** Products the session currently owns. */
    public Set<String> entitlements(String sessionId) {
        return store.products(sessionId);
    }

    public boolean isEntitled(String sessionId, String productId) {
        return store.owns(sessionId, productId);
    }

    /** Outcome of a verify-and-grant attempt. */
    public record Grant(boolean granted, String productId, String storefront, String reason) {}
}
