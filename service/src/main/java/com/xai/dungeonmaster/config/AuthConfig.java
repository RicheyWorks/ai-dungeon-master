package com.xai.dungeonmaster.config;

import com.xai.dungeonmaster.auth.FileSessionStore;
import com.xai.dungeonmaster.auth.InMemorySessionStore;
import com.xai.dungeonmaster.auth.SessionStore;
import com.xai.dungeonmaster.entitlement.EntitlementStore;
import com.xai.dungeonmaster.entitlement.FileEntitlementStore;
import com.xai.dungeonmaster.entitlement.InMemoryEntitlementStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

/**
 * Auth-related beans. Selects the {@link SessionStore} and {@link EntitlementStore}
 * implementations from config:
 * <ul>
 *   <li>{@code game.auth.session.store = memory|file} (+ {@code game.auth.session.file})</li>
 *   <li>{@code game.auth.entitlement.store = memory|file} (+ {@code game.auth.entitlement.file})</li>
 * </ul>
 * File-backed stores use cross-process locks so multi-node deployments that share
 * a volume see each other's sessions and grants.
 */
@Configuration
public class AuthConfig {

    @Bean
    public SessionStore sessionStore(
            @Value("${game.auth.session.store:memory}") String kind,
            @Value("${game.auth.session.file:sessions.json}") String file) {
        if ("file".equalsIgnoreCase(kind)) {
            System.out.println("[auth] session store: file (" + file + ")");
            return new FileSessionStore(Paths.get(file));
        }
        return new InMemorySessionStore();
    }

    @Bean
    public EntitlementStore entitlementStore(
            @Value("${game.auth.entitlement.store:memory}") String kind,
            @Value("${game.auth.entitlement.file:entitlements.json}") String file) {
        if ("file".equalsIgnoreCase(kind)) {
            System.out.println("[auth] entitlement store: file (" + file + ")");
            return new FileEntitlementStore(Paths.get(file));
        }
        return new InMemoryEntitlementStore();
    }
}
