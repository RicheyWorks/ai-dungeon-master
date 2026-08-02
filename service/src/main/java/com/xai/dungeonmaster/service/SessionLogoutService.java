package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.content.SessionPackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Explicit logout: drop session identity, pack overrides, and live engine.
 */
@Service
public class SessionLogoutService {

    private final SessionService sessions;
    private final SessionPackService packs;
    private final GameInstanceService games;

    @Autowired
    public SessionLogoutService(
            SessionService sessions,
            SessionPackService packs,
            @Autowired(required = false) GameInstanceService games) {
        this.sessions = sessions;
        this.packs = packs;
        this.games = games;
    }

    /**
     * @return true if a session id was processed (may already have been absent)
     */
    public boolean logout(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return false;
        sessions.delete(sessionId);
        packs.clearSession(sessionId);
        if (games != null) {
            try {
                games.destroy(sessionId, false);
            } catch (Exception e) {
                System.err.println("[logout] engine destroy failed for " + sessionId + ": " + e.getMessage());
            }
        }
        return true;
    }
}
