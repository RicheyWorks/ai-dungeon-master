package com.xai.dungeonmaster.config;

import com.xai.dungeonmaster.auth.MemoryRateLimitStore;
import com.xai.dungeonmaster.auth.RateLimitStore;
import com.xai.dungeonmaster.auth.RedisRateLimitStore;
import com.xai.dungeonmaster.store.RedisOps;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimitStore rateLimitStore(
            RedisOps redisOps,
            @Value("${game.rate-limit.store:memory}") String kind,
            @Value("${game.auth.redis.key-prefix:dm}") String redisPrefix) {
        if ("redis".equalsIgnoreCase(kind)) {
            if (!redisOps.isNetworked() && !(redisOps instanceof com.xai.dungeonmaster.store.MemoryRedisOps)) {
                // noop redis throws — fail fast with a clear message
                try {
                    redisOps.ping();
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "game.rate-limit.store=redis requires a working Redis "
                                    + "(set game.auth.redis.url; also used when session store is redis)",
                            e);
                }
            }
            System.out.println("[rate-limit] store: redis (prefix=" + redisPrefix + ")");
            return new RedisRateLimitStore(redisOps, redisPrefix);
        }
        System.out.println("[rate-limit] store: memory (per-node)");
        return new MemoryRateLimitStore();
    }
}
