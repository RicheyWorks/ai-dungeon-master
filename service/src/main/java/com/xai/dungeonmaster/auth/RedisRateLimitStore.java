package com.xai.dungeonmaster.auth;

import com.xai.dungeonmaster.store.RedisOps;

/**
 * Multi-node fixed-window counters via Redis {@code INCR} + {@code EXPIRE}.
 * Key shape: {@code {prefix}:rl:{bucketKey}:{epochMinute}}.
 */
public final class RedisRateLimitStore implements RateLimitStore {

    private final RedisOps redis;
    private final String prefix;

    public RedisRateLimitStore(RedisOps redis, String prefix) {
        this.redis = redis;
        this.prefix = (prefix == null || prefix.isBlank()) ? "dm" : prefix.trim();
    }

    @Override
    public Result hit(String key) {
        long epochMinute = System.currentTimeMillis() / 60_000L;
        String redisKey = prefix + ":rl:" + sanitize(key) + ":" + epochMinute;
        long count = redis.incr(redisKey);
        if (count == 1L) {
            // keep slightly longer than the window so late scrapes still see the key
            redis.expire(redisKey, 120);
        }
        long now = System.currentTimeMillis();
        long windowEnd = (epochMinute + 1) * 60_000L;
        long retryAfterSec = Math.max(1L, (windowEnd - now + 999) / 1000L);
        return new Result(count, retryAfterSec);
    }

    private static String sanitize(String key) {
        if (key == null) return "unknown";
        return key.replace('|', ':').replace(' ', '_');
    }
}
