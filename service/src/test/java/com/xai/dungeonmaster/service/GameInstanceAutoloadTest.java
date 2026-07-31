package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.DungeonMasterEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reconnect restore: destroy (with save-on-evict) then forSession again must
 * reload the saved world when autoload is on.
 */
class GameInstanceAutoloadTest {

    private GameEngineFactory factory() {
        return new GameEngineFactory(
                3, 3, new String[]{"Kael", "Lira"}, new String[]{"Warrior", "Mage"},
                "", "local-stub", 4000, null);
    }

    @Test
    void reconnectAfterEvictRestoresSave(@TempDir Path tmp) {
        GameInstanceService.Policy policy = new GameInstanceService.Policy(3600, 100, true, true);
        GameInstanceService svc = new GameInstanceService(factory(), factory().createDefault(), tmp, policy);

        DungeonMasterEngine first = svc.forSession("hero");
        int partySize = first.getPartyState().members().size();
        assertTrue(partySize >= 1);

        // Evict with auto-save.
        svc.destroy("hero");
        assertTrue(svc.peek("hero").isEmpty());
        assertTrue(java.nio.file.Files.isRegularFile(svc.savePath("hero")));

        // Reconnect — new engine instance but same save restored.
        DungeonMasterEngine second = svc.forSession("hero");
        assertNotSame(first, second);
        assertEquals(partySize, second.getPartyState().members().size());
        assertEquals(first.getPartyState().members().get(0).name(),
                second.getPartyState().members().get(0).name());
    }

    @Test
    void autoloadOffStartsFresh(@TempDir Path tmp) {
        // save-on-evict true so a file exists, but autoload false → ignore it.
        GameInstanceService.Policy policy = new GameInstanceService.Policy(3600, 100, true, false);
        GameInstanceService svc = new GameInstanceService(factory(), factory().createDefault(), tmp, policy);

        svc.forSession("hero");
        svc.destroy("hero");
        assertTrue(java.nio.file.Files.isRegularFile(svc.savePath("hero")));

        // Mint with autoload off still creates a new engine; load not required to fail —
        // we just verify forSession succeeds and file is still there for manual load.
        DungeonMasterEngine engine = svc.forSession("hero");
        assertNotNull(engine);
        assertEquals(1, svc.sessionCount());
    }

    @Test
    void resetDoesNotAutoload(@TempDir Path tmp) {
        GameInstanceService.Policy policy = new GameInstanceService.Policy(3600, 100, true, true);
        GameInstanceService svc = new GameInstanceService(factory(), factory().createDefault(), tmp, policy);

        DungeonMasterEngine first = svc.forSession("hero");
        first.saveGame(svc.savePath("hero").toString());

        // reset always mints fresh without loading.
        DungeonMasterEngine reset = svc.reset("hero");
        assertNotSame(first, reset);
        assertNotNull(reset.getPartyState());
    }

    @Test
    void missingSaveIsNoOp(@TempDir Path tmp) {
        GameInstanceService.Policy policy = new GameInstanceService.Policy(3600, 100, true, true);
        GameInstanceService svc = new GameInstanceService(factory(), factory().createDefault(), tmp, policy);
        DungeonMasterEngine engine = svc.forSession("brand-new");
        assertNotNull(engine);
        assertFalse(java.nio.file.Files.isRegularFile(svc.savePath("brand-new")));
    }
}
