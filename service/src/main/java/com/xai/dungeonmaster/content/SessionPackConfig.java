package com.xai.dungeonmaster.content;

import com.xai.dungeonmaster.store.RedisOps;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class SessionPackConfig {

    @Bean
    public SessionPackStore sessionPackStore(
            @Value("${game.content.session-packs.store:memory}") String kind,
            @Value("${game.auth.redis.key-prefix:dm}") String redisPrefix,
            RedisOps redisOps,
            DataSource authDataSource) {
        if ("redis".equalsIgnoreCase(kind)) {
            System.out.println("[content] session-packs store: redis (prefix=" + redisPrefix + ")");
            return new RedisSessionPackStore(redisOps, redisPrefix);
        }
        if ("jdbc".equalsIgnoreCase(kind) || "postgres".equalsIgnoreCase(kind)) {
            System.out.println("[content] session-packs store: jdbc");
            return new JdbcSessionPackStore(authDataSource);
        }
        return new MemorySessionPackStore();
    }
}
