package com.xai.dungeonmaster;

import com.xai.dungeonmaster.plugin.ContentPack;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.util.ResourceLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end load tests for the shipped content packs. Scans the real
 * content-packs/ directory on disk (walking up from the working dir so it works
 * from either the repo root or a module dir), parses each pack via
 * ResourceLoader, and asserts monsters/items/strings merge with stats intact.
 */
class ContentPackLoadTest {

    /** Effect keys the engine ships bundled ItemEffects for. */
    private static final Set<String> KNOWN_EFFECTS = Set.of("HEAL", "RESTORE_MANA", "BUFF_AC", "CLEANSE", "NONE");

    /** Packs expected to ship in the repo. */
    private static final List<String> SHIPPED_PACKS =
            List.of("black-hollows", "dnd-classic", "sci-fi", "cozy-hearthwood");

    @AfterEach
    void reset() {
        ContentRegistry.clearForTests();
    }

    /** Walk up from the working directory to find the repo's content-packs dir. */
    private static Path locateContentPacks() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("content-packs");
            if (Files.isDirectory(candidate.resolve("black-hollows"))) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    @Test
    void blackHollowsPackLoadsAndMergesFromDisk() {
        Path packsRoot = locateContentPacks();
        assumeTrue(packsRoot != null,
                "content-packs/black-hollows not found from " + Paths.get("").toAbsolutePath());

        List<ContentPack> packs = ResourceLoader.scanContentPacks(packsRoot);
        ContentPack pack = packs.stream()
                .filter(p -> "black-hollows".equals(p.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("black-hollows pack was not scanned"));

        assertEquals("1.0.0", pack.version());
        assertFalse(pack.monsters().isEmpty(), "pack should ship monsters");
        assertFalse(pack.items().isEmpty(), "pack should ship items");

        Enemy boss = pack.monsters().values().stream()
                .filter(Enemy::isBoss)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a boss in the pack"));
        assertTrue(boss.getMaxHp() >= 500, "boss baseHp should load; got " + boss.getMaxHp());

        ContentRegistry.register(pack);
        assertTrue(ContentRegistry.monsters().values().stream()
                .anyMatch(e -> e.getName().equals("Hollow Wretch")), "Hollow Wretch should be registered");
        assertTrue(ContentRegistry.items().values().stream()
                .anyMatch(i -> i.getName().contains("Grave Salt")), "Grave Salt should be registered");
        assertNotEquals("pack.black-hollows.name",
                ContentRegistry.string("pack.black-hollows.name"),
                "en.properties strings should merge into the registry");
    }

    @Test
    void allShippedPacksLoadWithValidContent() {
        Path packsRoot = locateContentPacks();
        assumeTrue(packsRoot != null, "content-packs not found from " + Paths.get("").toAbsolutePath());

        List<ContentPack> packs = ResourceLoader.scanContentPacks(packsRoot);
        Map<String, ContentPack> byId = new HashMap<>();
        for (ContentPack p : packs) {
            byId.put(p.id(), p);
        }

        for (String id : SHIPPED_PACKS) {
            ContentPack p = byId.get(id);
            assertNotNull(p, "expected pack '" + id + "' to load; found " + byId.keySet());
            assertFalse(p.monsters().isEmpty(), id + " should ship monsters");
            assertFalse(p.items().isEmpty(), id + " should ship items");
            assertTrue(p.monsters().values().stream().anyMatch(Enemy::isBoss),
                    id + " should ship a boss encounter");
            for (Item item : p.items().values()) {
                assertTrue(KNOWN_EFFECTS.contains(item.getEffectKey()),
                        id + " item '" + item.getName() + "' uses unknown effectKey '" + item.getEffectKey() + "'");
            }
        }
    }
}
