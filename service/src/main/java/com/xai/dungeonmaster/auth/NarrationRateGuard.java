package com.xai.dungeonmaster.auth;

import org.springframework.stereotype.Component;

/**
 * Shared narration budget for STOMP {@code /app/narrate} (per session id).
 * HTTP {@code POST /v2/narrate} is limited separately by {@link RateLimitFilter}
 * (per client IP) using the same {@code narrate-per-minute} config.
 */
@Component
public class NarrationRateGuard {

    private final RateLimitStore store;
    private final RateLimitMetrics metrics;
    private final boolean enabled;
    private final int perMinute;

    @org.springframework.beans.factory.annotation.Autowired
    public NarrationRateGuard(RateLimitStore store, RateLimitMetrics metrics, RateLimitProperties props) {
        this.store = store;
        this.metrics = metrics != null ? metrics : new RateLimitMetrics();
        this.enabled = props != null && props.enabled();
        this.perMinute = props != null ? props.narratePerMinute() : 20;
    }

    /** Test helper. */
    public NarrationRateGuard(RateLimitStore store, boolean enabled, int perMinute) {
        this(store, new RateLimitMetrics(),
                RateLimitProperties.builder().enabled(enabled).narratePerMinute(perMinute).build());
    }

    /** Test helper with metrics. */
    public NarrationRateGuard(RateLimitStore store, RateLimitMetrics metrics, boolean enabled, int perMinute) {
        this(store, metrics,
                RateLimitProperties.builder().enabled(enabled).narratePerMinute(perMinute).build());
    }

    public int limitPerMinute() {
        return perMinute;
    }

    public Decision check(String clientKey) {
        if (!enabled) {
            return Decision.allow(perMinute, perMinute);
        }
        String key = "narrate|" + (clientKey == null || clientKey.isBlank() ? "anon" : clientKey.trim());
        RateLimitStore.Result hit = store.hit(key);
        long n = hit.count();
        if (n > perMinute) {
            metrics.rejected("narrate_stomp");
            return Decision.deny(perMinute, hit.retryAfterSeconds());
        }
        metrics.allowed("narrate_stomp");
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
