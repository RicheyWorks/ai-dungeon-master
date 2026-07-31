package com.xai.dungeonmaster.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically reaps idle per-session game engines so multi-player memory use
 * stays bounded. Interval and TTL come from config; a TTL of 0 disables reaping.
 */
@Component
public class GameInstanceReaper {

    private final GameInstanceService games;
    private final boolean enabled;

    public GameInstanceReaper(
            GameInstanceService games,
            @Value("${game.instances.reaper.enabled:true}") boolean enabled) {
        this.games = games;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${game.instances.reap-interval-ms:60000}")
    public void reap() {
        if (!enabled) return;
        if (games.policy().idleTtlSeconds() <= 0) return;
        int n = games.evictIdle();
        if (n > 0) {
            System.out.println("[game-instances] reaped " + n + " idle engine(s); live="
                    + games.sessionCount());
        }
    }
}
