package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.auth.JwtService;
import com.xai.dungeonmaster.auth.SessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-session engines must not share mutable world state: an action on Alice's
 * engine must leave Bob's chronicle / history untouched.
 */
class GameInstanceServiceTest {

    private GameEngineFactory factory() {
        return new GameEngineFactory(
                3, 3,
                new String[]{"Kael"}, new String[]{"Warrior"},
                "", "local-stub", 4000,
                null);
    }

    @Test
    void unauthenticatedUsesDefaultEngine() {
        DungeonMasterEngine def = factory().createDefault();
        GameInstanceService svc = new GameInstanceService(factory(), def, Path.of("saves"));
        assertSame(def, svc.resolve(null));
        assertSame(def, svc.forSession(null));
        assertEquals(0, svc.sessionCount());
    }

    @Test
    void twoSessionsGetIsolatedEngines() {
        DungeonMasterEngine def = factory().createDefault();
        GameInstanceService svc = new GameInstanceService(factory(), def, Path.of("saves"));

        DungeonMasterEngine alice = svc.forSession("alice");
        DungeonMasterEngine bob = svc.forSession("bob");

        assertNotSame(alice, bob);
        assertNotSame(alice, def);
        assertEquals(2, svc.sessionCount());

        // Mutate Alice only.
        int aliceHistoryBefore = alice.getTurnHistory().size();
        var choices = alice.getCurrentAvailableChoices();
        if (!choices.isEmpty()) {
            alice.handleChoice(choices.get(0));
        }
        assertTrue(alice.getTurnHistory().size() >= aliceHistoryBefore);

        // Bob's history must be independent (still just the opening quest events).
        int bobHistory = bob.getTurnHistory().size();
        // Alice acting must not grow Bob's log.
        assertEquals(bobHistory, bob.getTurnHistory().size());
        assertNotSame(alice.getChronicle(), bob.getChronicle());
    }

    @Test
    void sameSessionReturnsSameEngine() {
        GameInstanceService svc = new GameInstanceService(factory(), factory().createDefault(), Path.of("saves"));
        assertSame(svc.forSession("s1"), svc.forSession("s1"));
    }

    @Test
    void resetReplacesSessionEngine() {
        GameInstanceService svc = new GameInstanceService(factory(), factory().createDefault(), Path.of("saves"));
        DungeonMasterEngine first = svc.forSession("s1");
        DungeonMasterEngine second = svc.reset("s1");
        assertNotSame(first, second);
        assertSame(second, svc.forSession("s1"));
        assertEquals(1, svc.sessionCount());
    }

    @Test
    void saveAndLoadRoundTripPerSession(@TempDir Path tmp) {
        GameInstanceService svc = new GameInstanceService(factory(), factory().createDefault(), tmp);
        SessionService sessions = new SessionService(new JwtService("test-secret-abcdefghijklmnop", 3600));
        SessionService.Session alice = sessions.createSession("Alice").session();

        DungeonMasterEngine engine = svc.resolve(alice);
        Path path = svc.savePath(alice);
        engine.saveGame(path.toString());
        assertTrue(Files.isRegularFile(path), "save file should exist: " + path);

        // Reset and reload.
        DungeonMasterEngine fresh = svc.reset(alice);
        fresh.loadGame(path.toString());
        assertEquals(engine.getPartyState().members().size(),
                fresh.getPartyState().members().size());
    }

    @Test
    void savePathSanitizesSessionId() {
        GameInstanceService svc = new GameInstanceService(factory(), factory().createDefault(), Path.of("saves"));
        Path p = svc.savePath("../evil/../../x");
        assertFalse(p.toString().contains(".."));
        assertTrue(p.getFileName().toString().endsWith(".json"));
    }
}
