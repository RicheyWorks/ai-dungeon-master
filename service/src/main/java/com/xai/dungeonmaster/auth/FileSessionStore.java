package com.xai.dungeonmaster.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xai.dungeonmaster.store.LockedJsonFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link SessionStore} that persists sessions as a JSON array on disk, so a
 * JWT issued before a restart still resolves to its session afterwards.
 *
 * Every operation reloads under a cross-process file lock, so two service
 * instances sharing the path see each other's sessions (shared-volume multi-node).
 * Previously the store only flushed from an in-process cache — fine for single
 * process, wrong for multi-node.
 */
public final class FileSessionStore implements SessionStore {

    private final LockedJsonFile<List<Persisted>> file;

    public FileSessionStore(Path path) {
        this.file = new LockedJsonFile<>(
                path,
                new TypeReference<List<Persisted>>() {},
                Collections.emptyList());
    }

    @Override
    public void save(SessionService.Session session) {
        if (session == null || session.id() == null) return;
        file.update(current -> {
            Map<String, Persisted> byId = toMap(current);
            byId.put(session.id(), new Persisted(
                    session.id(),
                    session.displayName(),
                    session.createdAtEpoch(),
                    session.lastSeenEpoch()));
            return new ArrayList<>(byId.values());
        });
    }

    @Override
    public Optional<SessionService.Session> load(String id) {
        if (id == null) return Optional.empty();
        List<Persisted> all = file.read();
        for (Persisted p : all) {
            if (p != null && id.equals(p.id())) {
                return Optional.of(toSession(p));
            }
        }
        return Optional.empty();
    }

    @Override
    public Collection<SessionService.Session> all() {
        List<SessionService.Session> out = new ArrayList<>();
        for (Persisted p : file.read()) {
            if (p != null && p.id() != null) {
                out.add(toSession(p));
            }
        }
        return List.copyOf(out);
    }

    @Override
    public int size() {
        return all().size();
    }

    @Override
    public void delete(String id) {
        if (id == null) return;
        file.update(list -> {
            Map<String, Persisted> byId = toMap(list);
            byId.remove(id);
            return new ArrayList<>(byId.values());
        });
    }

    private static Map<String, Persisted> toMap(List<Persisted> list) {
        Map<String, Persisted> map = new LinkedHashMap<>();
        if (list == null) return map;
        for (Persisted p : list) {
            if (p != null && p.id() != null) {
                map.put(p.id(), p);
            }
        }
        return map;
    }

    private static SessionService.Session toSession(Persisted p) {
        return new SessionService.Session(p.id(), p.displayName(), p.createdAtEpoch(), p.lastSeenEpoch());
    }

    /** JSON shape for one persisted session. */
    record Persisted(String id, String displayName, long createdAtEpoch, long lastSeenEpoch) {}
}
