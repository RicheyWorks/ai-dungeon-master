package com.xai.dungeonmaster.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Idle TTL reaping, capacity LRU eviction, and auto-save on destroy. */
class GameInstanceEvictionTest {

    private GameEngineFactory factory() {
        return new GameEngineFactory(
                3, 3, new String[]{"Kael"}, new String[]{"Warrior"},
                "", "local-stub", 4000, null);
    }

    @Test
    void idleEnginesAreEvicted(@TempDir Path tmp) {
        GameInstanceService.Policy policy = new GameInstanceService.Policy(1, 100, false);
        GameInstanceService svc = new GameInstanceService(factory(), factory().createDefault(), tmp, policy);

        svc.forSession("old");
        svc.forSession("fresh");
        assertEquals(2, svc.sessionCount());

        // Force "old" into the past; keep "fresh" current.
        long now = System.currentTimeMillis();
        // Touch fresh now, then evict with a clock far in the future so only
        // sessions not recently touched would die — re-touch fresh after ageing.
        // Simpler: age everything then touch fresh before reaping.
        int n = svc.evictIdle(now + 5_000); // 5s later, TTL=1s → both idle
        assertEquals(2, n);
        assertEquals(0, svc.sessionCount());
    }

    @Test
    void recentAccessSurvivesReap(@TempDir Path tmp) throws Exception {
        GameInstanceService.Policy policy = new GameInstanceService.Policy(10, 100, false);
        GameInstanceService svc = new GameInstanceService(factory(), factory().createDefault(), tmp, policy);
        svc.forSession("keep");
        // Sleep briefly then touch — lastAccess is now.
        Thread.sleep(20);
        svc.forSession("keep");
        // Reap with now = lastAccess + 1s (TTL 10s) → keep lives.
        long last = svc.lastAccessMs("keep").orElseThrow();
        assertEquals(0, svc.evictIdle(last + 1_000));
        assertEquals(1, svc.sessionCount());
        // Far future → gone.
        assertEquals(1, svc.evictIdle(last + 60_000));
        assertEquals(0, svc.sessionCount());
    }

    @Test
    void capacityEvictsLeastRecentlyUsed(@TempDir Path tmp) throws Exception {
        GameInstanceService.Policy policy = new GameInstanceService.Policy(3600, 2, false);
        GameInstanceService svc = new GameInstanceService(factory(), factory().createDefault(), tmp, policy);

        svc.forSession("a");
        Thread.sleep(5);
        svc.forSession("b");
        Thread.sleep(5);
        // Touch b again so a is LRU.
        svc.forSession("b");
        // Creating c should evict a.
        svc.forSession("c");
        assertEquals(2, svc.sessionCount());
        assertTrue(svc.peek("b").isPresent());
        assertTrue(svc.peek("c").isPresent());
        assertTrue(svc.peek("a").isEmpty(), "LRU session a should have been evicted");
    }

    @Test
    void destroyAutoSavesWhenConfigured(@TempDir Path tmp) {
        GameInstanceService.Policy policy = new GameInstanceService.Policy(3600, 100, true);
        GameInstanceService svc = new GameInstanceService(factory(), factory().createDefault(), tmp, policy);
        svc.forSession("s1");
        Path path = svc.savePath("s1");
        svc.destroy("s1");
        assertTrue(Files.isRegularFile(path), "eviction should write save: " + path);
        assertEquals(0, svc.sessionCount());
    }

    @Test
    void zeroTtlDisablesIdleEviction(@TempDir Path tmp) {
        GameInstanceService.Policy policy = new GameInstanceService.Policy(0, 100, false);
        GameInstanceService svc = new GameInstanceService(factory(), factory().createDefault(), tmp, policy);
        svc.forSession("x");
        assertEquals(0, svc.evictIdle(System.currentTimeMillis() + 999_999_999L));
        assertEquals(1, svc.sessionCount());
    }
}
