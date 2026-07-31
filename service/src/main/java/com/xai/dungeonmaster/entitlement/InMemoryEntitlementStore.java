package com.xai.dungeonmaster.entitlement;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link EntitlementStore}: grants live only for the process lifetime.
 * Fine for single-process dev; multi-node / restart-safe deployments should use
 * {@link FileEntitlementStore}.
 */
public final class InMemoryEntitlementStore implements EntitlementStore {

    private final Map<String, Set<String>> owned = new ConcurrentHashMap<>();

    @Override
    public Set<String> products(String sessionId) {
        if (sessionId == null) return Set.of();
        Set<String> s = owned.get(sessionId);
        return s == null ? Set.of() : Set.copyOf(s);
    }

    @Override
    public void grant(String sessionId, String productId) {
        if (sessionId == null || sessionId.isBlank() || productId == null || productId.isBlank()) {
            return;
        }
        owned.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(productId);
    }
}
