package com.xai.dungeonmaster.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Shared narration budget for STOMP {@code /app/narrate} (per session id).
 * HTTP {@code POST /v2/narrate} is limited separately by {@link RateLimitFilter}
 * (per client IP) using the same {@code narrate-per-minute} config.
 */
@Component
public class NarrationRateGuard {

    private final RateLimitStore store;
    private final boolean enabled;
    private final int perMinute;

    public NarrationRateGuard(
            RateLimitStore store,
            @Value("${game.rate-limit.enabled:true}") boolean enabled,
            @Value("${game.rate-limit.narrate-per-minute:20}") int perMinute) {
        this.store = store;
        this.enabled = enabled;
        this.perMinute = Math.max(1, perMinute);
    }

    public int limitPerMinute() {
        return perMinute;
    }

    /**
     * @param clientKey session id (or {@code anon})
     * @return allow + remaining after this hit (or denied with retry-after)
     */
    public Decision check(String clientKey) {
        if (!enabled) {
            return Decision.allow(perMinute, perMinute);
        }
        String key = "narrate|" + (clientKey == null || clientKey.isBlank() ? "anon" : clientKey.trim());
        RateLimitStore.Result hit = store.hit(key);
        long n = hit.count();
        if (n > perMinute) {
            return Decision.deny(perMinute, hit.retryAfterSeconds());
        }
        return Decision.allow(perMinute, Math.max(0, perMinute - n));
    }

    public record Decision(boolean allowed, int limit, long remaining, long retryAfterSeconds) {
        static Decision allow(int limit, long remaining) {
            return new Decision(true, limit, remaining, 0L);
        }

        static Decision deny(int limit, long retryAfterSeconds) {
            return new Decision(false, limit, 0L, Math.max(1L, retryAfterSeconds));
        }
    }
}
