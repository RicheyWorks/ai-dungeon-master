package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.auth.SessionService;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-session game isolation.
 *
 * Historically the process held a single {@link DungeonMasterEngine} bean, so
 * every client shared one party / quest / chronicle. This service keeps that
 * default engine for legacy {@code /api/game/*}, the Swing GUI, and
 * unauthenticated v2 calls, while minting an isolated engine for each
 * authenticated session id (lazy, on first touch).
 *
 * Saves land under {@code game.saves.dir}/{sessionId}.json (or
 * {@code default.json} for the shared engine).
 */
public class GameInstanceService {

    public static final String DEFAULT_SAVE_NAME = "default.json";

    private final GameEngineFactory factory;
    private final DungeonMasterEngine defaultEngine;
    private final Path savesDir;
    private final ConcurrentHashMap<String, DungeonMasterEngine> bySession = new ConcurrentHashMap<>();

    public GameInstanceService(GameEngineFactory factory,
                               DungeonMasterEngine defaultEngine,
                               Path savesDir) {
        this.factory = factory;
        this.defaultEngine = defaultEngine;
        this.savesDir = savesDir != null ? savesDir : Paths.get("saves");
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

    /** Lazy per-session engine. */
    public DungeonMasterEngine forSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return defaultEngine;
        }
        if (factory == null) {
            return defaultEngine;
        }
        return bySession.computeIfAbsent(sessionId, factory::create);
    }

    /**
     * Replace the session's engine with a fresh one (new party/quest). The
     * default engine is rewound via {@link DungeonMasterEngine#startQuest()}.
     */
    public DungeonMasterEngine reset(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            defaultEngine.startQuest();
            return defaultEngine;
        }
        if (factory == null) {
            return defaultEngine;
        }
        DungeonMasterEngine fresh = factory.create(sessionId);
        bySession.put(sessionId, fresh);
        return fresh;
    }

    public DungeonMasterEngine reset(SessionService.Session session) {
        return reset(session != null ? session.id() : null);
    }

    /** Drop a session's engine (e.g. logout / eviction). */
    public void destroy(String sessionId) {
        if (sessionId != null) {
            bySession.remove(sessionId);
        }
    }

    /** Number of live per-session engines (excludes the default). */
    public int sessionCount() {
        return bySession.size();
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
        return Optional.ofNullable(bySession.get(sessionId));
    }
}
