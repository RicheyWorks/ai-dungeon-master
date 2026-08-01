package com.xai.dungeonmaster.config;

import com.xai.dungeonmaster.auth.FileSessionStore;
import com.xai.dungeonmaster.auth.InMemorySessionStore;
import com.xai.dungeonmaster.auth.JdbcSessionStore;
import com.xai.dungeonmaster.auth.RedisSessionStore;
import com.xai.dungeonmaster.auth.SessionStore;
import com.xai.dungeonmaster.entitlement.EntitlementStore;
import com.xai.dungeonmaster.entitlement.FileEntitlementStore;
import com.xai.dungeonmaster.entitlement.InMemoryEntitlementStore;
import com.xai.dungeonmaster.entitlement.JdbcEntitlementStore;
import com.xai.dungeonmaster.entitlement.RedisEntitlementStore;
import com.xai.dungeonmaster.store.JedisRedisOps;
import com.xai.dungeonmaster.store.RedisOps;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.file.Paths;

/**
 * Auth-related beans. Selects the {@link SessionStore} and {@link EntitlementStore}
 * implementations from config:
 * <ul>
 *   <li>{@code game.auth.session.store = memory|file|redis|jdbc}</li>
 *   <li>{@code game.auth.entitlement.store = memory|file|redis|jdbc}</li>
 *   <li>{@code game.auth.redis.*} when either is redis</li>
 *   <li>{@code game.auth.jdbc.*} when either is jdbc (or postgres alias)</li>
 * </ul>
 * Game engines remain process-local — sticky sessions or shared {@code game.saves.dir}.
 */
@Configuration
public class AuthConfig {

    @Bean(destroyMethod = "close")
    public RedisOps redisOps(
            @Value("${game.auth.session.store:memory}") String sessionKind,
            @Value("${game.auth.entitlement.store:memory}") String entitlementKind,
            @Value("${game.rate-limit.store:memory}") String rateLimitStore,
            @Value("${game.marketplace.jobs.store:memory}") String marketplaceJobsStore,
            @Value("${game.auth.redis.url:redis://127.0.0.1:6379}") String redisUrl) {
        if (needsRedis(sessionKind)
                || needsRedis(entitlementKind)
                || needsRedis(rateLimitStore)
                || needsRedis(marketplaceJobsStore)) {
            System.out.println("[auth] redis ops: " + redisUrl
                    + (needsRedis(rateLimitStore) ? " (rate-limit)" : "")
                    + (needsRedis(marketplaceJobsStore) ? " (marketplace-jobs)" : ""));
            return new JedisRedisOps(redisUrl);
        }
        return noopRedis();
    }

    /**
     * Shared DataSource for JDBC auth stores. Only opens a pool when at least
     * one store is {@code jdbc}/{@code postgres}.
     */
    @Bean(destroyMethod = "close")
    public DataSource authDataSource(
            @Value("${game.auth.session.store:memory}") String sessionKind,
            @Value("${game.auth.entitlement.store:memory}") String entitlementKind,
            @Value("${game.auth.jdbc.url:}") String url,
            @Value("${game.auth.jdbc.username:}") String username,
            @Value("${game.auth.jdbc.password:}") String password,
            @Value("${game.auth.jdbc.driver:}") String driver) {
        if (!(needsJdbc(sessionKind) || needsJdbc(entitlementKind))) {
            return new com.xai.dungeonmaster.store.UnusedDataSource();
        }
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "game.auth.jdbc.url is required when session/entitlement store is jdbc");
        }
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        if (username != null && !username.isBlank()) cfg.setUsername(username);
        if (password != null) cfg.setPassword(password);
        if (driver != null && !driver.isBlank()) cfg.setDriverClassName(driver);
        cfg.setPoolName("dm-auth");
        cfg.setMaximumPoolSize(8);
        cfg.setMinimumIdle(0);
        System.out.println("[auth] jdbc pool: " + url);
        return new HikariDataSource(cfg);
    }

    @Bean
    public SessionStore sessionStore(
            @Value("${game.auth.session.store:memory}") String kind,
            @Value("${game.auth.session.file:sessions.json}") String file,
            @Value("${game.auth.redis.key-prefix:dm}") String redisPrefix,
            RedisOps redisOps,
            DataSource authDataSource) {
        if ("file".equalsIgnoreCase(kind)) {
            System.out.println("[auth] session store: file (" + file + ")");
            return new FileSessionStore(Paths.get(file));
        }
        if ("redis".equalsIgnoreCase(kind)) {
            System.out.println("[auth] session store: redis (prefix=" + redisPrefix + ")");
            return new RedisSessionStore(redisOps, redisPrefix);
        }
        if (needsJdbc(kind)) {
            System.out.println("[auth] session store: jdbc");
            return new JdbcSessionStore(authDataSource);
        }
        return new InMemorySessionStore();
    }

    @Bean
    public EntitlementStore entitlementStore(
            @Value("${game.auth.entitlement.store:memory}") String kind,
            @Value("${game.auth.entitlement.file:entitlements.json}") String file,
            @Value("${game.auth.redis.key-prefix:dm}") String redisPrefix,
            RedisOps redisOps,
            DataSource authDataSource) {
        if ("file".equalsIgnoreCase(kind)) {
            System.out.println("[auth] entitlement store: file (" + file + ")");
            return new FileEntitlementStore(Paths.get(file));
        }
        if ("redis".equalsIgnoreCase(kind)) {
            System.out.println("[auth] entitlement store: redis (prefix=" + redisPrefix + ")");
            return new RedisEntitlementStore(redisOps, redisPrefix);
        }
        if (needsJdbc(kind)) {
            System.out.println("[auth] entitlement store: jdbc");
            return new JdbcEntitlementStore(authDataSource);
        }
        return new InMemoryEntitlementStore();
    }

    private static boolean needsRedis(String kind) {
        return "redis".equalsIgnoreCase(kind);
    }

    private static boolean needsJdbc(String kind) {
        return "jdbc".equalsIgnoreCase(kind) || "postgres".equalsIgnoreCase(kind);
    }

    private static RedisOps noopRedis() {
        return new RedisOps() {
            @Override public void hset(String key, java.util.Map<String, String> fields) {
                throw new IllegalStateException("Redis not configured");
            }
            @Override public java.util.Map<String, String> hgetAll(String key) {
                throw new IllegalStateException("Redis not configured");
            }
            @Override public void sadd(String key, String... members) {
                throw new IllegalStateException("Redis not configured");
            }
            @Override public void srem(String key, String... members) {
                throw new IllegalStateException("Redis not configured");
            }
            @Override public java.util.Set<String> smembers(String key) {
                throw new IllegalStateException("Redis not configured");
            }
            @Override public void del(String key) {
                throw new IllegalStateException("Redis not configured");
            }
            @Override public long incr(String key) {
                throw new IllegalStateException("Redis not configured");
            }
            @Override public void expire(String key, int seconds) {
                throw new IllegalStateException("Redis not configured");
            }
            @Override public void close() { /* no-op */ }
        };
    }
}
