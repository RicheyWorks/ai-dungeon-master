package com.xai.dungeonmaster.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Session store + persistence tests. The file store must survive a "restart"
 * (a fresh store/service instance over the same file), while the in-memory
 * store must not — proving persistence is actually doing something.
 */
class SessionStoreTest {

    @Test
    void inMemoryStoreRoundTrips() {
        InMemorySessionStore store = new InMemorySessionStore();
        assertEquals(0, store.size());
        store.save(new SessionService.Session("s1", "Kael", 1000L));
        assertTrue(store.load("s1").isPresent());
        assertEquals("Kael", store.load("s1").get().displayName());
        assertEquals(1, store.size());
        assertTrue(store.load("missing").isEmpty());
    }

    @Test
    void fileStorePersistsAcrossReopen(@TempDir Path tmp) {
        Path file = tmp.resolve("sessions.json");
        FileSessionStore store = new FileSessionStore(file);
        store.save(new SessionService.Session("abc", "Lira", 1234L, 5678L));

        // Simulate a restart: a brand-new store reading the same file.
        FileSessionStore reopened = new FileSessionStore(file);
        Optional<SessionService.Session> got = reopened.load("abc");
        assertTrue(got.isPresent(), "file store should reload the session after reopen");
        assertEquals("Lira", got.get().displayName());
        assertEquals(1234L, got.get().createdAtEpoch());
        assertEquals(5678L, got.get().lastSeenEpoch(), "lastSeen should round-trip through JSON");
    }

    @Test
    void sessionServiceSurvivesRestartWithFileStore(@TempDir Path tmp) {
        Path file = tmp.resolve("sessions.json");
        JwtService jwt = new JwtService("persistence-secret-abcdefghijklmnop", 3600);

        SessionService before = new SessionService(jwt, new FileSessionStore(file));
        SessionService.Issued issued = before.createSession("Kael");
        String id = issued.session().id();

        // Restart: new service + new store over the same file.
        SessionService after = new SessionService(jwt, new FileSessionStore(file));
        Optional<SessionService.Session> found = after.find(id);
        assertTrue(found.isPresent(), "session should persist across a restart");
        assertEquals("Kael", found.get().displayName());

        // The original token still verifies and points at the persisted session.
        var claims = jwt.verify(issued.token());
        assertTrue(claims.isPresent());
        assertEquals(id, claims.get().get("sub"));
    }

    @Test
    void inMemoryServiceDoesNotPersistAcrossInstances() {
        JwtService jwt = new JwtService("mem-secret-abcdefghijklmnopqrstuv", 3600);
        SessionService before = new SessionService(jwt, new InMemorySessionStore());
        String id = before.createSession("Lira").session().id();

        SessionService after = new SessionService(jwt, new InMemorySessionStore());
        assertTrue(after.find(id).isEmpty(), "in-memory sessions must not carry across instances");
    }

    @Test
    void touchIsPersisted(@TempDir Path tmp) {
        Path file = tmp.resolve("sessions.json");
        SessionService svc = new SessionService(new JwtService("s-abcdefghijklmnopqrstuvwx", 3600),
                new FileSessionStore(file));
        String id = svc.createSession("Kael").session().id();

        assertTrue(svc.touch(id).isPresent());
        // Reopen and confirm the touched session is still there.
        FileSessionStore reopened = new FileSessionStore(file);
        assertTrue(reopened.load(id).isPresent(), "touched session should remain persisted");
    }
}
