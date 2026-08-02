package com.xai.dungeonmaster.content;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local session pack overrides. */
public final class MemorySessionPackStore implements SessionPackStore {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>> bySession =
            new ConcurrentHashMap<>();

    @Override
    public Optional<Boolean> get(String sessionId, String packId) {
        if (sessionId == null || packId == null) return Optional.empty();
        ConcurrentHashMap<String, Boolean> m = bySession.get(sessionId);
        if (m == null) return Optional.empty();
        return Optional.ofNullable(m.get(packId));
    }

    @Override
    public Map<String, Boolean> all(String sessionId) {
        if (sessionId == null) return Map.of();
        ConcurrentHashMap<String, Boolean> m = bySession.get(sessionId);
        return m == null ? Map.of() : Map.copyOf(m);
    }

    @Override
    public void put(String sessionId, String packId, Boolean enabled) {
        if (sessionId == null || sessionId.isBlank() || packId == null || packId.isBlank()) {
            return;
        }
        if (enabled == null) {
            ConcurrentHashMap<String, Boolean> m = bySession.get(sessionId);
            if (m != null) {
                m.remove(packId);
                if (m.isEmpty()) bySession.remove(sessionId, m);
            }
            return;
        }
        bySession.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(packId, enabled);
    }

    @Override
    public void clear(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        bySession.remove(sessionId);
    }
}
