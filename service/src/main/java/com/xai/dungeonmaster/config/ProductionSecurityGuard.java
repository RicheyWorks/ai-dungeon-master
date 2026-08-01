package com.xai.dungeonmaster.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fails fast in production when insecure defaults are still configured.
 *
 * <p>Activated when {@code game.production=true} or the {@code prod} Spring
 * profile is active. Local dev is unaffected.
 *
 * <p>Checks:
 * <ul>
 *   <li>Auth is enabled</li>
 *   <li>JWT secret is set, ≥ 32 chars, not a known insecure default</li>
 *   <li>JDBC password is not the compose default when store=jdbc</li>
 *   <li>Storefront sandbox secrets are not still the shipped defaults
 *       (unless a live storefront credential is present)</li>
 * </ul>
 */
@Component
@Order(0)
public class ProductionSecurityGuard implements ApplicationRunner {

    static final Set<String> INSECURE_JWT_SECRETS = Set.of(
            "",
            "dev-insecure-secret-change-me-please-0123456789abcdef",
            "compose-dev-secret-change-me-32chars!",
            "change-me",
            "secret",
            "password"
    );

    static final Set<String> INSECURE_STOREFRONT_SECRETS = Set.of(
            "dev-storefront-insecure-secret-change-me",
            "google-play-sandbox-insecure-secret",
            "app-store-sandbox-insecure-secret",
            "steam-sandbox-insecure-secret"
    );

    static final Set<String> INSECURE_DB_PASSWORDS = Set.of(
            "",
            "dm-dev-password",
            "password",
            "postgres",
            "change-me"
    );

    private final Environment env;
    private final boolean productionFlag;
    private final boolean authEnabled;
    private final String jwtSecret;
    private final String sessionStore;
    private final String entitlementStore;
    private final String jdbcPassword;

    public ProductionSecurityGuard(
            Environment env,
            @Value("${game.production:false}") boolean productionFlag,
            @Value("${game.auth.enabled:false}") boolean authEnabled,
            @Value("${game.auth.jwt.secret:}") String jwtSecret,
            @Value("${game.auth.session.store:memory}") String sessionStore,
            @Value("${game.auth.entitlement.store:memory}") String entitlementStore,
            @Value("${game.auth.jdbc.password:}") String jdbcPassword) {
        this.env = env;
        this.productionFlag = productionFlag;
        this.authEnabled = authEnabled;
        this.jwtSecret = jwtSecret == null ? "" : jwtSecret;
        this.sessionStore = sessionStore == null ? "memory" : sessionStore;
        this.entitlementStore = entitlementStore == null ? "memory" : entitlementStore;
        this.jdbcPassword = jdbcPassword == null ? "" : jdbcPassword;
    }

    /** True when production mode is active (explicit flag or {@code prod} profile). */
    public boolean isProductionMode() {
        if (productionFlag) return true;
        for (String p : env.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProductionMode()) {
            return;
        }
        List<String> problems = validate();
        if (!problems.isEmpty()) {
            String msg = "Production security check failed:\n  - "
                    + String.join("\n  - ", problems)
                    + "\nSee docs/PRODUCTION.md";
            System.err.println(msg);
            throw new IllegalStateException(msg);
        }
        System.out.println("[security] production checks passed");
    }

    /** Package-visible for unit tests. */
    List<String> validate() {
        List<String> problems = new ArrayList<>();

        if (!authEnabled) {
            problems.add("game.auth.enabled must be true in production");
        }

        String jwt = jwtSecret.trim();
        if (jwt.isEmpty() || INSECURE_JWT_SECRETS.contains(jwt)
                || INSECURE_JWT_SECRETS.contains(jwt.toLowerCase(Locale.ROOT))) {
            problems.add("game.auth.jwt.secret is missing or is a known insecure default");
        } else if (jwt.length() < 32) {
            problems.add("game.auth.jwt.secret must be at least 32 characters (got " + jwt.length() + ")");
        }

        if ("memory".equalsIgnoreCase(sessionStore)) {
            problems.add("game.auth.session.store=memory is not multi-node safe; use jdbc, redis, or file");
        }
        if ("memory".equalsIgnoreCase(entitlementStore)) {
            problems.add("game.auth.entitlement.store=memory is not multi-node safe; use jdbc, redis, or file");
        }

        if (needsJdbc(sessionStore) || needsJdbc(entitlementStore)) {
            if (INSECURE_DB_PASSWORDS.contains(jdbcPassword)
                    || INSECURE_DB_PASSWORDS.contains(jdbcPassword.toLowerCase(Locale.ROOT))) {
                problems.add("game.auth.jdbc.password is missing or is a known insecure default");
            }
        }

        // Storefront sandbox secrets — only fail if still default AND no live credentials.
        if (!hasLiveStorefrontCreds()) {
            for (String name : List.of(
                    "STOREFRONT_DEV_SECRET",
                    "STOREFRONT_GOOGLE_SECRET",
                    "STOREFRONT_APPLE_SECRET",
                    "STOREFRONT_STEAM_SECRET")) {
                String v = firstNonBlank(System.getenv(name), System.getProperty(name));
                if (v == null) {
                    // unset → plugins use insecure defaults
                    problems.add(name + " is unset (sandbox plugins will use insecure defaults); "
                            + "set a strong secret or configure live storefront credentials");
                    break; // one message is enough
                }
                if (INSECURE_STOREFRONT_SECRETS.contains(v)) {
                    problems.add(name + " is still a shipped insecure default");
                }
            }
        }

        return problems;
    }

    private boolean hasLiveStorefrontCreds() {
        return nonBlank(System.getenv("STOREFRONT_GOOGLE_ACCESS_TOKEN"))
                || nonBlank(System.getProperty("STOREFRONT_GOOGLE_ACCESS_TOKEN"))
                || nonBlank(System.getenv("STOREFRONT_APPLE_SHARED_SECRET"))
                || nonBlank(System.getProperty("STOREFRONT_APPLE_SHARED_SECRET"))
                || nonBlank(System.getenv("STOREFRONT_STEAM_PUBLISHER_KEY"))
                || nonBlank(System.getProperty("STOREFRONT_STEAM_PUBLISHER_KEY"));
    }

    private static boolean needsJdbc(String kind) {
        return "jdbc".equalsIgnoreCase(kind) || "postgres".equalsIgnoreCase(kind);
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String firstNonBlank(String a, String b) {
        if (nonBlank(a)) return a.trim();
        if (nonBlank(b)) return b.trim();
        return null;
    }
}
