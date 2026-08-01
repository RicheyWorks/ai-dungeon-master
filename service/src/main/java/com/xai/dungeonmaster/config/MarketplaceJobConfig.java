package com.xai.dungeonmaster.config;

import com.xai.dungeonmaster.service.MarketplaceJobStore;
import com.xai.dungeonmaster.service.MemoryMarketplaceJobStore;
import com.xai.dungeonmaster.service.RedisMarketplaceJobStore;
import com.xai.dungeonmaster.store.MemoryRedisOps;
import com.xai.dungeonmaster.store.RedisOps;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MarketplaceJobConfig {

    @Bean
    public MarketplaceJobStore marketplaceJobStore(
            RedisOps redisOps,
            @Value("${game.marketplace.jobs.store:memory}") String kind,
            @Value("${game.auth.redis.key-prefix:dm}") String prefix,
            @Value("${game.marketplace.jobs.ttl-seconds:3600}") int ttlSeconds) {
        String k = kind == null ? "memory" : kind.trim().toLowerCase();
        if ("redis".equals(k)) {
            if (!redisOps.isNetworked() && !(redisOps instanceof MemoryRedisOps)) {
                System.err.println(
                        "[marketplace] jobs.store=redis but RedisOps is not networked; using memory");
                return new MemoryMarketplaceJobStore();
            }
            System.out.println("[marketplace] install jobs store: redis (prefix=" + prefix + ")");
            return new RedisMarketplaceJobStore(redisOps, prefix, ttlSeconds);
        }
        return new MemoryMarketplaceJobStore();
    }
}
