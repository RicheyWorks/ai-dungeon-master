package com.xai.dungeonmaster.content;

import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.service.GameInstanceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires idle player sessions and clears their content-pack overrides so
 * multi-tenant pack prefs do not leak forever after players leave.
 */
@Component
public class SessionHygieneReaper {

    private final SessionService sessions;
    private final SessionPackService packs;
    private final GameInstanceService games;
    private final boolean enabled;
    private final long idleTtlSeconds;

    public SessionHygieneReaper(
            SessionService sessions,
            SessionPackService packs,
            GameInstanceService games,
            @Value("${game.auth.session.hygiene.enabled:true}") boolean enabled,
            @Value("${game.auth.session.idle-ttl-seconds:86400}") long idleTtlSeconds) {
        this.sessions = sessions;
        this.packs = packs;
        this.games = games;
        this.enabled = enabled;
        this.idleTtlSeconds = idleTtlSeconds;
    }

    @Scheduled(fixedDelayString = "${game.auth.session.hygiene-interval-ms:300000}")
    public void reap() {
        if (!enabled || idleTtlSeconds <= 0) return;
        int purged = 0;
        long now = java.time.Instant.now().getEpochSecond();
        long cutoff = now - idleTtlSeconds;
        for (SessionService.Session s : sessions.allSessions()) {
            if (s == null || s.id() == null) continue;
            if (s.lastSeenEpoch() < cutoff) {
                String id = s.id();
                sessions.delete(id);
                packs.clearSession(id);
                if (games != null) {
                    try {
                        games.destroy(id, false);
                    } catch (Exception ignored) {
                        // engine may not exist
                    }
                }
                purged++;
            }
        }
        if (purged > 0) {
            System.out.println("[session-hygiene] purged " + purged
                    + " idle session(s); remaining=" + sessions.activeCount());
        }
    }

    /** Visible for tests. */
    public int purgeNow(long nowEpochSeconds) {
        if (idleTtlSeconds <= 0) return 0;
        int purged = 0;
        long cutoff = nowEpochSeconds - idleTtlSeconds;
        for (SessionService.Session s : sessions.allSessions()) {
            if (s == null || s.id() == null) continue;
            if (s.lastSeenEpoch() < cutoff) {
                sessions.delete(s.id());
                packs.clearSession(s.id());
                purged++;
            }
        }
        return purged;
    }
}
