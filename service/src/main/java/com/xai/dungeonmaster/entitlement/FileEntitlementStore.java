package com.xai.dungeonmaster.entitlement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xai.dungeonmaster.store.LockedJsonFile;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * File-backed {@link EntitlementStore}. Document shape:
 * <pre>{ "session-id": ["sku_a", "sku_b"], ... }</pre>
 *
 * Every read/write takes a cross-process file lock and reloads, so two service
 * instances sharing the path see each other's grants (shared volume multi-node).
 */
public final class FileEntitlementStore implements EntitlementStore {

    private final LockedJsonFile<Map<String, Set<String>>> file;

    public FileEntitlementStore(Path path) {
        this.file = new LockedJsonFile<>(
                path,
                new TypeReference<Map<String, Set<String>>>() {},
                Collections.emptyMap());
    }

    @Override
    public Set<String> products(String sessionId) {
        if (sessionId == null) return Set.of();
        Map<String, Set<String>> all = file.read();
        Set<String> s = all.get(sessionId);
        return s == null ? Set.of() : Set.copyOf(s);
    }

    @Override
    public void grant(String sessionId, String productId) {
        if (sessionId == null || sessionId.isBlank() || productId == null || productId.isBlank()) {
            return;
        }
        file.update(current -> {
            Map<String, Set<String>> next = new LinkedHashMap<>();
            if (current != null) {
                for (Map.Entry<String, Set<String>> e : current.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        next.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
                    }
                }
            }
            next.computeIfAbsent(sessionId, k -> new LinkedHashSet<>()).add(productId);
            return next;
        });
    }

    /** Test/ops helper: path of the JSON document. */
    public Path path() {
        return file.path();
    }
}
