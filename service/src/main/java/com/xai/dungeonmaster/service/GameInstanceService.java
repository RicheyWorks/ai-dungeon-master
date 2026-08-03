package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.auth.SessionService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-session game isolation with idle eviction, capacity caps, and save
 * auto-load on reconnect.
 *
 * Historically the process held a single {@link DungeonMasterEngine} bean, so
 * every client shared one party / quest / chronicle. This service keeps that
 * default engine for legacy {@code /api/game/*}, the Swing GUI, and
 * unauthenticated v2 calls, while minting an isolated engine for each
 * authenticated session id (lazy, on first touch).
 *
 * Saves land under {@code game.saves.dir}/{sessionId}.json (or
 * {@code default.json} for the shared engine). Idle engines are reaped after
 * {@link Policy#idleTtlSeconds()} (auto-saved when {@link Policy#saveOnEvict()}
 * is true); when {@link Policy#maxSessions()} is reached the least-recently-used
 * instance is evicted first. On the next touch, {@link Policy#autoload()}
 * restores that save so reconnect after eviction continues the same adventure.
 */
public class GameInstanceService {

    public static final String DEFAULT_SAVE_NAME = "default.json";

    /** Tunables for capacity, idle reaping, and reconnect restore. */
    public record Policy(long idleTtlSeconds, int maxSessions, boolean saveOnEvict, boolean autoload) {
        /** Defaults: 1h idle TTL, 100 cap, save+autoload on. */
        public static Policy defaults() {
            return new Policy(3_600L, 100, true, true);
        }

        /** Back-compat constructor (autoload defaults to true). */
        public Policy(long idleTtlSeconds, int maxSessions, boolean saveOnEvict) {
            this(idleTtlSeconds, maxSessions, saveOnEvict, true);
        }

        public Policy {
            if (idleTtlSeconds < 0) idleTtlSeconds = 0;
            if (maxSessions < 1) maxSessions = 1;
        }
    }

    private static final class Entry {
        final DungeonMasterEngine engine;
        final AtomicLong lastAccessMs = new AtomicLong(System.currentTimeMillis());

        Entry(DungeonMasterEngine engine) {
            this.engine = engine;
        }

        void touch() {
            lastAccessMs.set(System.currentTimeMillis());
        }
    }

    private final GameEngineFactory factory;
    private final DungeonMasterEngine defaultEngine;
    private final Path savesDir;
    private final Policy policy;
    private final ConcurrentHashMap<String, Entry> bySession = new ConcurrentHashMap<>();

    public GameInstanceService(GameEngineFactory factory,
                               DungeonMasterEngine defaultEngine,
                               Path savesDir) {
        this(factory, defaultEngine, savesDir, Policy.defaults());
    }

    public GameInstanceService(GameEngineFactory factory,
                               DungeonMasterEngine defaultEngine,
                               Path savesDir,
                               Policy policy) {
        this.factory = factory;
        this.defaultEngine = defaultEngine;
        this.savesDir = savesDir != null ? savesDir : Paths.get("saves");
        this.policy = policy != null ? policy : Policy.defaults();
    }

    /** Convenience for unit tests — no factory (cannot mint new sessions). */
    public static GameInstanceService singleton(DungeonMasterEngine engine) {
        return new GameInstanceService(null, engine, Paths.get("saves")) {
            @Override
            public DungeonMasterEngine forSession(String sessionId) {
                return engine;
            }

            @Override
            public DungeonMasterEngine reset(String sessionId) {
                engine.startQuest();
                return engine;
            }
        };
    }

    public Policy policy() {
        return policy;
    }

    /** Process-default engine (legacy + unauthenticated). */
    public DungeonMasterEngine getDefault() {
        return defaultEngine;
    }

    /**
     * Resolve the engine for an optional auth session. Null session → default.
     */
    public DungeonMasterEngine resolve(SessionService.Session session) {
        if (session == null || session.id() == null || session.id().isBlank()) {
            return defaultEngine;
        }
        return forSession(session.id());
    }

    /** Lazy per-session engine; refreshes last-access for idle tracking. */
    public DungeonMasterEngine forSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return defaultEngine;
        }
        if (factory == null) {
            return defaultEngine;
        }
        Entry existing = bySession.get(sessionId);
        if (existing != null) {
            existing.touch();
            return existing.engine;
        }
        enforceCapacity();
        Entry created = new Entry(factory.create(sessionId));
        if (policy.autoload()) {
            tryAutoload(sessionId, created.engine);
        }
        Entry race = bySession.putIfAbsent(sessionId, created);
        if (race != null) {
            race.touch();
            return race.engine;
        }
        return created.engine;
    }

    /**
     * Replace the session's engine with a fresh one (new party/quest). Does
     * <strong>not</strong> auto-load a save — use {@link #forSession} after
     * destroy if you want reconnect restore. The default engine is rewound via
     * {@link DungeonMasterEngine#startQuest()}.
     */
    public DungeonMasterEngine reset(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            defaultEngine.startQuest();
            return defaultEngine;
        }
        if (factory == null) {
            return defaultEngine;
        }
        Entry fresh = new Entry(factory.create(sessionId));
        bySession.put(sessionId, fresh);
        return fresh.engine;
    }

    public DungeonMasterEngine reset(SessionService.Session session) {
        return reset(session != null ? session.id() : null);
    }

    /**
     * Drop a session's engine. When {@link Policy#saveOnEvict()} is true the
     * current state is written to the session save path first.
     */
    public void destroy(String sessionId) {
        destroy(sessionId, policy.saveOnEvict());
    }

    public void destroy(String sessionId, boolean saveFirst) {
        if (sessionId == null) return;
        Entry removed = bySession.remove(sessionId);
        if (removed != null && saveFirst) {
            persistQuietly(sessionId, removed.engine);
        }
    }

    /**
     * Evict engines whose last access is older than {@link Policy#idleTtlSeconds()}.
     * A TTL of 0 disables idle eviction. Returns how many were removed.
     */
    public int evictIdle() {
        return evictIdle(System.currentTimeMillis());
    }

    /** Test hook with injectable clock. */
    public int evictIdle(long nowMs) {
        long ttlSec = policy.idleTtlSeconds();
        if (ttlSec <= 0) {
            return 0;
        }
        long cutoff = nowMs - ttlSec * 1_000L;
        int removed = 0;
        for (var e : bySession.entrySet()) {
            if (e.getValue().lastAccessMs.get() < cutoff) {
                destroy(e.getKey(), policy.saveOnEvict());
                removed++;
            }
        }
        return removed;
    }

    /** Number of live per-session engines (excludes the default). */
    public int sessionCount() {
        return bySession.size();
    }

    /** True when a dedicated engine exists for the session id. */
    public boolean hasSession(String sessionId) {
        return sessionId != null && !sessionId.isBlank() && bySession.containsKey(sessionId);
    }

    /**
     * STOMP topic for a session's narrative stream. Unauthenticated / default
     * engines use the legacy {@code /topic/narrative}; authenticated sessions
     * get {@code /topic/narrative/{sessionId}}.
     */
    public static String narrativeTopic(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "/topic/narrative";
        }
        return "/topic/narrative/" + sessionId;
    }

    public Path savePath(String sessionId) {
        String name;
        if (sessionId == null || sessionId.isBlank()) {
            name = DEFAULT_SAVE_NAME;
        } else {
            // Keep only safe filename characters (no path separators or dots).
            String safe = sessionId.replaceAll("[^a-zA-Z0-9_-]", "_");
            if (safe.isBlank()) {
                safe = "session";
            }
            // Cap length so pathological ids can't create awkward paths.
            if (safe.length() > 64) {
                safe = safe.substring(0, 64);
            }
            name = safe + ".json";
        }
        return savesDir.resolve(name).normalize();
    }

    public Path savePath(SessionService.Session session) {
        return savePath(session != null ? session.id() : null);
    }

    public Path savesDir() {
        return savesDir;
    }

    public Optional<DungeonMasterEngine> peek(String sessionId) {
        if (sessionId == null) return Optional.empty();
        Entry e = bySession.get(sessionId);
        return Optional.ofNullable(e != null ? e.engine : null);
    }

    /** Last-access epoch millis for a live session, or empty. */
    public Optional<Long> lastAccessMs(String sessionId) {
        Entry e = sessionId == null ? null : bySession.get(sessionId);
        return e == null ? Optional.empty() : Optional.of(e.lastAccessMs.get());
    }

    // ── capacity / persistence ───────────────────────────────────────────────

    /** Evict least-recently-used sessions until there is room for one more. */
    private void enforceCapacity() {
        int max = policy.maxSessions();
        while (bySession.size() >= max) {
            String lru = findLruKey();
            if (lru == null) break;
            System.err.println("[game-instances] capacity " + max
                    + " reached — evicting least-recently-used session " + lru);
            destroy(lru, policy.saveOnEvict());
        }
    }

    private String findLruKey() {
        String best = null;
        long bestTs = Long.MAX_VALUE;
        for (var e : bySession.entrySet()) {
            long ts = e.getValue().lastAccessMs.get();
            if (ts < bestTs) {
                bestTs = ts;
                best = e.getKey();
            }
        }
        return best;
    }

    private void persistQuietly(String sessionId, DungeonMasterEngine engine) {
        if (engine == null) return;
        try {
            Path path = savePath(sessionId);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            engine.saveGame(path.toString());
        } catch (Exception e) {
            System.err.println("[game-instances] auto-save failed for " + sessionId
                    + ": " + e.getMessage());
        }
    }

    /**
     * Load {@code sessionId}'s save into {@code engine} when the file exists.
     * Returns true if a load was attempted (file present).
     */
    boolean tryAutoload(String sessionId, DungeonMasterEngine engine) {
        if (engine == null || sessionId == null) return false;
        Path path = savePath(sessionId);
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            engine.loadGame(path.toString());
            System.out.println("[game-instances] restored save for session " + sessionId
                    + " from " + path);
            return true;
        } catch (Exception e) {
            System.err.println("[game-instances] autoload failed for " + sessionId
                    + ": " + e.getMessage());
            return false;
        }
    }
}
