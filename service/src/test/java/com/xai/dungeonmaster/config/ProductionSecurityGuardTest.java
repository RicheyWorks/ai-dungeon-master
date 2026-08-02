package com.xai.dungeonmaster.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductionSecurityGuardTest {

    @Test
    void skipsWhenNotProduction() {
        ProductionSecurityGuard g = guard(false, false, "", "memory", "memory", "memory", "");
        assertFalse(g.isProductionMode());
        g.run(null);
    }

    @Test
    void prodProfileActivates() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSecurityGuard g = hardened(
                env, false, true,
                "a-strong-production-jwt-secret-32+",
                "jdbc", "jdbc", "jdbc", "jdbc", "s3cure-db-pass-not-default",
                "https://play.example.com",
                "prod-admin-token-strong-enough!!",
                "redis");
        assertTrue(g.isProductionMode());
    }

    @Test
    void rejectsWeakJwtAndAuthOff() {
        ProductionSecurityGuard g = guard(
                true, false,
                "compose-dev-secret-change-me-32chars!",
                "jdbc", "jdbc", "jdbc", "s3cure-db-pass-not-default");
        List<String> problems = g.validate();
        assertTrue(problems.stream().anyMatch(p -> p.contains("auth.enabled")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("jwt.secret")), problems.toString());
    }

    @Test
    void rejectsShortJwt() {
        ProductionSecurityGuard g = guard(
                true, true, "too-short",
                "redis", "redis", "redis", "");
        List<String> problems = g.validate();
        assertTrue(problems.stream().anyMatch(p -> p.contains("at least 32")), problems.toString());
    }

    @Test
    void rejectsMemoryStoresInProd() {
        ProductionSecurityGuard g = guard(
                true, true,
                "a-strong-production-jwt-secret-32chars!!",
                "memory", "memory", "memory", "");
        List<String> problems = g.validate();
        assertTrue(problems.stream().anyMatch(p -> p.contains("session.store")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("entitlement.store")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("receipt-ledger.store")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("session-packs.store")), problems.toString());
    }

    @Test
    void rejectsDefaultJdbcPassword() {
        ProductionSecurityGuard g = guard(
                true, true,
                "a-strong-production-jwt-secret-32chars!!",
                "jdbc", "jdbc", "jdbc", "dm-dev-password");
        List<String> problems = g.validate();
        assertTrue(problems.stream().anyMatch(p -> p.contains("jdbc.password")), problems.toString());
    }

    @Test
    void rejectsWildcardCors() {
        ProductionSecurityGuard g = guard(
                true, true,
                "a-strong-production-jwt-secret-32chars!!",
                "jdbc", "jdbc", "jdbc", "s3cure-db-pass-not-default",
                "*");
        List<String> problems = g.validate();
        assertTrue(problems.stream().anyMatch(p -> p.contains("cors.allowed-origins")), problems.toString());
    }

    @Test
    void acceptsHardenedConfigWhenLiveStorefrontPresent() {
        System.setProperty("STOREFRONT_STEAM_PUBLISHER_KEY", "not-empty");
        try {
            ProductionSecurityGuard g = guard(
                    true, true,
                    "a-strong-production-jwt-secret-32chars!!",
                    "jdbc", "jdbc", "jdbc", "s3cure-db-pass-not-default");
            List<String> problems = g.validate();
            assertEquals(List.of(), problems, problems::toString);
        } finally {
            System.clearProperty("STOREFRONT_STEAM_PUBLISHER_KEY");
        }
    }

    @Test
    void rejectsWeakAdminTokenAndMemoryRateLimit() {
        ProductionSecurityGuard g = hardened(
                new StandardEnvironment(),
                true, true,
                "a-strong-production-jwt-secret-32chars!!",
                "jdbc", "jdbc", "jdbc", "jdbc", "s3cure-db-pass-not-default",
                "https://play.example.com",
                "short",
                "memory");
        List<String> problems = g.validate();
        assertTrue(problems.stream().anyMatch(p -> p.contains("admin.token")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("rate-limit.store")), problems.toString());
    }

    @Test
    void rejectsLenientPluginPolicyAndOpenMetrics() {
        ProductionSecurityGuard g = new ProductionSecurityGuard(
                new StandardEnvironment(),
                true, true,
                "a-strong-production-jwt-secret-32chars!!",
                "jdbc", "jdbc", "jdbc", "jdbc", "s3cure-db-pass-not-default",
                "https://play.example.com",
                "prod-admin-token-strong-enough!!",
                "redis",
                "LENIENT",
                true,
                "",
                "",
                "",
                true,
                false);
        System.setProperty("STOREFRONT_STEAM_PUBLISHER_KEY", "not-empty");
        try {
            List<String> problems = g.validate();
            assertTrue(problems.stream().anyMatch(p -> p.contains("signature.policy")), problems.toString());
            assertTrue(problems.stream().anyMatch(p -> p.contains("metrics.scrape-token")), problems.toString());
        } finally {
            System.clearProperty("STOREFRONT_STEAM_PUBLISHER_KEY");
        }
    }

    @Test
    void rejectsRemoteMarketplaceWithoutHmac() {
        ProductionSecurityGuard g = new ProductionSecurityGuard(
                new StandardEnvironment(),
                true, true,
                "a-strong-production-jwt-secret-32chars!!",
                "jdbc", "jdbc", "jdbc", "jdbc", "s3cure-db-pass-not-default",
                "https://play.example.com",
                "prod-admin-token-strong-enough!!",
                "redis",
                "REQUIRED",
                true,
                "metrics-scrape-token-16+",
                "https://cdn.example.com/index.json",
                "",
                true,
                false);
        System.setProperty("STOREFRONT_STEAM_PUBLISHER_KEY", "not-empty");
        try {
            List<String> problems = g.validate();
            assertTrue(problems.stream().anyMatch(p -> p.contains("remote-hmac-secret")), problems.toString());
        } finally {
            System.clearProperty("STOREFRONT_STEAM_PUBLISHER_KEY");
        }
    }

    private static ProductionSecurityGuard guard(
            boolean production,
            boolean auth,
            String jwt,
            String sessionStore,
            String entitlementStore,
            String receiptLedgerStore,
            String jdbcPassword) {
        return guard(production, auth, jwt, sessionStore, entitlementStore, receiptLedgerStore,
                jdbcPassword, "https://play.example.com");
    }

    private static ProductionSecurityGuard guard(
            boolean production,
            boolean auth,
            String jwt,
            String sessionStore,
            String entitlementStore,
            String receiptLedgerStore,
            String jdbcPassword,
            String corsOrigins) {
        return hardened(
                new StandardEnvironment(),
                production,
                auth,
                jwt,
                sessionStore,
                entitlementStore,
                receiptLedgerStore,
                receiptLedgerStore,
                jdbcPassword,
                corsOrigins,
                "prod-admin-token-strong-enough!!",
                "redis");
    }

    private static ProductionSecurityGuard hardened(
            org.springframework.core.env.Environment env,
            boolean production,
            boolean auth,
            String jwt,
            String sessionStore,
            String entitlementStore,
            String receiptLedgerStore,
            String sessionPacksStore,
            String jdbcPassword,
            String corsOrigins,
            String adminToken,
            String rateLimitStore) {
        return new ProductionSecurityGuard(
                env,
                production,
                auth,
                jwt,
                sessionStore,
                entitlementStore,
                receiptLedgerStore,
                sessionPacksStore,
                jdbcPassword,
                corsOrigins,
                adminToken,
                rateLimitStore,
                "REQUIRED",
                true,
                "metrics-scrape-token-16chars+",
                "",
                "",
                true,
                false);
    }
}
