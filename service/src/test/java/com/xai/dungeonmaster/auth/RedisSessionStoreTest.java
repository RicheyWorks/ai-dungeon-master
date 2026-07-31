package com.xai.dungeonmaster.auth;

import com.xai.dungeonmaster.store.MemoryRedisOps;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RedisSessionStoreTest {

    @Test
    void roundTripAndIndex() {
        MemoryRedisOps redis = new MemoryRedisOps();
        RedisSessionStore store = new RedisSessionStore(redis, "test");

        store.save(new SessionService.Session("s1", "Kael", 100L, 200L));
        Optional<SessionService.Session> got = store.load("s1");
        assertTrue(got.isPresent());
        assertEquals("Kael", got.get().displayName());
        assertEquals(100L, got.get().createdAtEpoch());
        assertEquals(200L, got.get().lastSeenEpoch());
        assertEquals(1, store.size());
        assertTrue(store.load("missing").isEmpty());
    }

    @Test
    void twoClientsShareState() {
        MemoryRedisOps redis = new MemoryRedisOps();
        RedisSessionStore nodeA = new RedisSessionStore(redis, "dm");
        RedisSessionStore nodeB = new RedisSessionStore(redis, "dm");

        nodeA.save(new SessionService.Session("shared", "Lira", 1L, 2L));
        assertEquals("Lira", nodeB.load("shared").orElseThrow().displayName());

        nodeB.save(new SessionService.Session("shared", "Lira", 1L, 99L));
        assertEquals(99L, nodeA.load("shared").orElseThrow().lastSeenEpoch());
        assertEquals(1, nodeA.all().size());
    }

    @Test
    void sessionServiceWithRedisStore() {
        MemoryRedisOps redis = new MemoryRedisOps();
        JwtService jwt = new JwtService("redis-test-secret-abcdefghijklmnop", 3600);
        SessionService svc = new SessionService(jwt, new RedisSessionStore(redis));
        SessionService.Issued issued = svc.createSession("Kael");

        SessionService otherNode = new SessionService(jwt, new RedisSessionStore(redis));
        assertTrue(otherNode.find(issued.session().id()).isPresent());
        assertEquals("Kael", otherNode.find(issued.session().id()).get().displayName());
    }
}
