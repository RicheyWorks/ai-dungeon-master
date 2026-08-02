package com.xai.dungeonmaster.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * STOMP {@code /app/action} budget (per session id). HTTP {@code POST /v2/action}
 * is limited by {@link RateLimitFilter} using the same {@code action-per-minute}
 * config (per client IP).
 */
@Component
public class ActionRateGuard {

    private final RateLimitStore store;
    private final RateLimitMetrics metrics;
    private final boolean enabled;
    private final int perMinute;

    public ActionRateGuard(
            RateLimitStore store,
            RateLimitMetrics metrics,
            @Value("${game.rate-limit.enabled:true}") boolean enabled,
            @Value("${game.rate-limit.action-per-minute:60}") int perMinute) {
        this.store = store;
        this.metrics = metrics != null ? metrics : new RateLimitMetrics();
        this.enabled = enabled;
        this.perMinute = Math.max(1, perMinute);
    }

    /** Test helper. */
    public ActionRateGuard(RateLimitStore store, boolean enabled, int perMinute) {
        this(store, new RateLimitMetrics(), enabled, perMinute);
    }

    public Decision check(String clientKey) {
        if (!enabled) {
            return Decision.allow(perMinute, perMinute);
        }
        String key = "action|" + (clientKey == null || clientKey.isBlank() ? "anon" : clientKey.trim());
        RateLimitStore.Result hit = store.hit(key);
        long n = hit.count();
        if (n > perMinute) {
            metrics.rejected("action_stomp");
            return Decision.deny(perMinute, hit.retryAfterSeconds());
        }
        metrics.allowed("action_stomp");
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
