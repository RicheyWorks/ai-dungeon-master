package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.store.RedisOps;
import com.xai.dungeonmaster.store.UnusedDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Probes auth-store backends for readiness. Only checks dependencies that the
 * current config actually uses (memory stores report {@code not_configured}).
 */
@Component
public class AuthDependencyProbe {

    private final DataSource dataSource;
    private final RedisOps redisOps;
    private final String sessionStore;
    private final String entitlementStore;
    private final String sessionFile;
    private final String entitlementFile;

    public AuthDependencyProbe(
            DataSource authDataSource,
            RedisOps redisOps,
            @Value("${game.auth.session.store:memory}") String sessionStore,
            @Value("${game.auth.entitlement.store:memory}") String entitlementStore,
            @Value("${game.auth.session.file:sessions.json}") String sessionFile,
            @Value("${game.auth.entitlement.file:entitlements.json}") String entitlementFile) {
        this.dataSource = authDataSource;
        this.redisOps = redisOps;
        this.sessionStore = sessionStore == null ? "memory" : sessionStore;
        this.entitlementStore = entitlementStore == null ? "memory" : entitlementStore;
        this.sessionFile = sessionFile;
        this.entitlementFile = entitlementFile;
    }

    /** Package-visible test constructor. */
    public AuthDependencyProbe(
            DataSource dataSource,
            RedisOps redisOps,
            String sessionStore,
            String entitlementStore) {
        this(dataSource, redisOps, sessionStore, entitlementStore, "sessions.json", "entitlements.json");
    }

    public Result probe() {
        Map<String, Object> checks = new LinkedHashMap<>();
        boolean ready = true;

        if (usesJdbc()) {
            Check c = checkJdbc();
            checks.put("jdbc", c.asMap());
            ready &= c.up;
        } else {
            checks.put("jdbc", notConfigured());
        }

        if (usesRedis()) {
            Check c = checkRedis();
            checks.put("redis", c.asMap());
            ready &= c.up;
        } else {
            checks.put("redis", notConfigured());
        }

        if (usesFile()) {
            Check c = checkFiles();
            checks.put("file", c.asMap());
            ready &= c.up;
        } else {
            checks.put("file", notConfigured());
        }

        return new Result(ready, checks);
    }

    private boolean usesJdbc() {
        return isJdbc(sessionStore) || isJdbc(entitlementStore);
    }

    private boolean usesRedis() {
        return isRedis(sessionStore) || isRedis(entitlementStore);
    }

    private boolean usesFile() {
        return isFile(sessionStore) || isFile(entitlementStore);
    }

    private Check checkJdbc() {
        if (dataSource instanceof UnusedDataSource) {
            return Check.down("jdbc selected but DataSource is unused placeholder");
        }
        try (Connection c = dataSource.getConnection()) {
            if (c.isValid(2)) {
                return Check.up("connection ok");
            }
            return Check.down("connection not valid");
        } catch (Exception e) {
            return Check.down(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private Check checkRedis() {
        try {
            if (redisOps.ping()) {
                return Check.up(redisOps.isNetworked() ? "PONG" : "in-memory ok");
            }
            return Check.down("ping failed");
        } catch (Exception e) {
            return Check.down(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private Check checkFiles() {
        try {
            if (isFile(sessionStore)) {
                ensureWritableParent(Paths.get(sessionFile));
            }
            if (isFile(entitlementStore)) {
                ensureWritableParent(Paths.get(entitlementFile));
            }
            return Check.up("paths writable");
        } catch (Exception e) {
            return Check.down(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static void ensureWritableParent(Path file) throws Exception {
        Path parent = file.toAbsolutePath().getParent();
        if (parent == null) {
            parent = Paths.get(".").toAbsolutePath();
        }
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        if (!Files.isWritable(parent)) {
            throw new IllegalStateException("not writable: " + parent);
        }
    }

    private static boolean isJdbc(String kind) {
        return "jdbc".equalsIgnoreCase(kind) || "postgres".equalsIgnoreCase(kind);
    }

    private static boolean isRedis(String kind) {
        return "redis".equalsIgnoreCase(kind);
    }

    private static boolean isFile(String kind) {
        return "file".equalsIgnoreCase(kind);
    }

    private static Map<String, Object> notConfigured() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "NOT_CONFIGURED");
        return m;
    }

    public record Result(boolean ready, Map<String, Object> checks) {}

    private record Check(boolean up, String detail) {
        static Check up(String detail) { return new Check(true, detail); }
        static Check down(String detail) { return new Check(false, detail); }

        Map<String, Object> asMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", up ? "UP" : "DOWN");
            if (detail != null && !detail.isBlank()) {
                m.put("detail", detail);
            }
            return m;
        }
    }
}
