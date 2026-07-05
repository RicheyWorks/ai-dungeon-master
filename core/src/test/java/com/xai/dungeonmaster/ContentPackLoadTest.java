package com.xai.dungeonmaster;

import com.xai.dungeonmaster.plugin.ContentPack;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.util.ResourceLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end load test for the shipped "Black Hollows" content pack. Scans the
 * real content-packs/ directory on disk (walking up from the working dir so it
 * works from either the repo root or a module dir), parses the pack via
 * ResourceLoader, and asserts its monsters/items/strings merge into the
 * ContentRegistry with their JSON stats intact.
 */
class ContentPackLoadTest {

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

        // isBoss flag + baseHp survive the JSON round-trip (guards the alias mapping).
        Enemy boss = pack.monsters().values().stream()
                .filter(Enemy::isBoss)
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a boss in the pack"));
        assertTrue(boss.getMaxHp() >= 500, "boss baseHp should load; got " + boss.getMaxHp());

        // Merge into the registry and confirm the pack's content is queryable.
        ContentRegistry.register(pack);
        assertTrue(ContentRegistry.monsters().values().stream()
                        .anyMatch(e -> e.getName().equals("Hollow Wretch")),
                "Hollow Wretch should be registered");
        assertTrue(ContentRegistry.items().values().stream()
                        .anyMatch(i -> i.getName().contains("Grave Salt")),
                "Grave Salt should be registered");

        // A localized string from strings/en.properties should resolve (not echo the key).
        assertNotEquals("pack.black-hollows.name",
                ContentRegistry.string("pack.black-hollows.name"),
                "en.properties strings should merge into the registry");
    }
}
