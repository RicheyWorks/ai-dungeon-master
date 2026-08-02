package com.xai.dungeonmaster.content;

import java.util.Map;
import java.util.Optional;

/**
 * Per-session content-pack enable overrides. Missing entry → use process default
 * ({@link com.xai.dungeonmaster.plugin.ContentRegistry#isProcessEnabled}).
 */
public interface SessionPackStore {

    /** Explicit override for pack, if any. */
    Optional<Boolean> get(String sessionId, String packId);

    /** All overrides for a session (packId → enabled). */
    Map<String, Boolean> all(String sessionId);

    /** Set or clear (null removes) an override. */
    void put(String sessionId, String packId, Boolean enabled);
}
