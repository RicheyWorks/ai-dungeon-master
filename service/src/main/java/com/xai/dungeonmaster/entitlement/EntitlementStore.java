package com.xai.dungeonmaster.entitlement;

import java.util.Set;

/**
 * Persistence seam for purchased product ids keyed by session id. Mirrors
 * {@link com.xai.dungeonmaster.auth.SessionStore}: in-memory for single-process
 * dev, file-backed (with cross-process locking) for restarts and multi-node
 * deployments that share a volume.
 */
public interface EntitlementStore {

    /** Products currently owned by the session (never null). */
    Set<String> products(String sessionId);

    /** Record that the session owns the product. Idempotent. */
    void grant(String sessionId, String productId);

    /** True if the session owns the product. */
    default boolean owns(String sessionId, String productId) {
        return productId != null && products(sessionId).contains(productId);
    }
}
