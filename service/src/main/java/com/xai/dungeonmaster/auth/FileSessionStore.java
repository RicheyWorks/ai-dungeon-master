package com.xai.dungeonmaster.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A {@link SessionStore} that persists sessions as a JSON array on disk, so a
 * JWT issued before a restart still resolves to its session afterwards. Loaded
 * into memory on construction; every {@link #save} rewrites the file. Adequate
 * for single-process v1 scale; a shared datastore is the multi-node upgrade.
 */
public final class FileSessionStore implements SessionStore {

    private final Path file;
    private final Map<String, SessionService.Session> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public FileSessionStore(Path file) {
        this.file = file;
        loadFromDisk();
    }

    private void loadFromDisk() {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) {
                return;
            }
            List<Persisted> list = mapper.readValue(bytes, new TypeReference<List<Persisted>>() {});
            for (Persisted p : list) {
                if (p != null && p.id() != null) {
                    sessions.put(p.id(),
                            new SessionService.Session(p.id(), p.displayName(), p.createdAtEpoch(), p.lastSeenEpoch()));
                }
            }
        } catch (IOException e) {
            System.err.println("WARN: could not read session store " + file + ": " + e.getMessage());
        }
    }

    private synchronized void flush() {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<Persisted> list = sessions.values().stream()
                    .map(s -> new Persisted(s.id(), s.displayName(), s.createdAtEpoch(), s.lastSeenEpoch()))
                    .collect(Collectors.toList());
            Files.write(file, mapper.writeValueAsBytes(list));
        } catch (IOException e) {
            System.err.println("WARN: could not write session store " + file + ": " + e.getMessage());
        }
    }

    @Override
    public void save(SessionService.Session session) {
        if (session != null && session.id() != null) {
            sessions.put(session.id(), session);
            flush();
        }
    }

    @Override
    public Optional<SessionService.Session> load(String id) {
        return Optional.ofNullable(id == null ? null : sessions.get(id));
    }

    @Override
    public Collection<SessionService.Session> all() {
        return List.copyOf(sessions.values());
    }

    @Override
    public int size() {
        return sessions.size();
    }

    /** JSON shape for one persisted session. */
    record Persisted(String id, String displayName, long createdAtEpoch, long lastSeenEpoch) {}
}
