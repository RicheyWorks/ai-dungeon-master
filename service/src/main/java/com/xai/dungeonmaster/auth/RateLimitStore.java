package com.xai.dungeonmaster.auth;

/**
 * Shared or local fixed-window counter for {@link RateLimitFilter}.
 */
public interface RateLimitStore {

    /**
     * Increment the counter for {@code key} in the current minute window.
     *
     * @return result with count after increment and seconds until window reset
     */
    Result hit(String key);

    record Result(long count, long retryAfterSeconds) {}
}
