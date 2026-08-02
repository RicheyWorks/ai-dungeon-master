package com.xai.dungeonmaster.content;

import com.xai.dungeonmaster.plugin.ContentPack;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Session-scoped content-pack enablement. When
 * {@code game.content.session-scoped-enable=true} (default), catalog toggles and
 * auto-enable write per-session overrides instead of mutating the process-wide
 * {@link ContentRegistry}. A request filter installs those ids as a ThreadLocal
 * overlay so dungeon generation only sees that session's packs.
 */
@Service
public class SessionPackService {

    private final SessionPackStore store;
    private final boolean sessionScoped;

    @org.springframework.beans.factory.annotation.Autowired
    public SessionPackService(
            SessionPackStore store,
            @Value("${game.content.session-scoped-enable:true}") boolean sessionScoped) {
        this.store = store != null ? store : new MemorySessionPackStore();
        this.sessionScoped = sessionScoped;
    }

    public SessionPackService() {
        this(new MemorySessionPackStore(), true);
    }

    public boolean isSessionScoped() {
        return sessionScoped;
    }

    public boolean isEnabled(String sessionId, String packId) {
        if (!sessionScoped || sessionId == null || sessionId.isBlank()) {
            return ContentRegistry.isProcessEnabled(packId);
        }
        return store.get(sessionId, packId).orElseGet(() -> ContentRegistry.isProcessEnabled(packId));
    }

    /**
     * Toggle a pack for the session (or process when session-scoped is off).
     * @return false if pack unknown
     */
    public boolean setEnabled(String sessionId, String packId, boolean enabled) {
        if (!ContentRegistry.isKnown(packId)) return false;
        if (!sessionScoped || sessionId == null || sessionId.isBlank()) {
            return ContentRegistry.setEnabled(packId, enabled);
        }
        store.put(sessionId, packId, enabled);
        return true;
    }

    /** Pack ids enabled for this session (or process defaults). */
    public Set<String> enabledPackIds(String sessionId) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (ContentPack pack : ContentRegistry.packs().values()) {
            if (isEnabled(sessionId, pack.id())) {
                out.add(pack.id());
            }
        }
        return Set.copyOf(out);
    }

    /** Drop all pack overrides for a session (call when the session expires). */
    public void clearSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        store.clear(sessionId);
    }

    /** Explicit overrides only (ops inventory). */
    public Map<String, Boolean> overrides(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return Map.of();
        return store.all(sessionId);
    }
}
