package com.xai.dungeonmaster;

import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.plugin.QuestScriptRegistry;
import com.xai.dungeonmaster.util.ResourceLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Goal G4 — First Light flagship pack validates and has multi-ending climax. */
class FlagshipPackTest {

    @BeforeEach
    @AfterEach
    void reset() {
        ContentRegistry.clearForTests();
        QuestScriptRegistry.clearForTests();
        CampaignRegistry.clearForTests();
    }

    @Test
    void firstLightShipsBossItemsAndTwoQuests() {
        Path packs = locate();
        assumeTrue(packs != null);
        var loaded = ResourceLoader.scanContentPacks(packs);
        var pack = loaded.stream().filter(p -> "first-light".equals(p.id())).findFirst()
                .orElseThrow(() -> new AssertionError("first-light missing: " + loaded.stream().map(p -> p.id()).toList()));

        assertTrue(QuestScriptRegistry.isRegistered("first-light-cold-open"));
        assertTrue(QuestScriptRegistry.isRegistered("first-light-noon-square"));
        assertTrue(CampaignRegistry.registeredIds().stream()
                .anyMatch(id -> id.contains("first-light")),
                "campaign ids: " + CampaignRegistry.registeredIds());

        assertTrue(pack.monsters().values().stream().anyMatch(Enemy::isBoss),
                "flagship needs a boss; monsters=" + pack.monsters().keySet());
        assertFalse(pack.items().isEmpty());

        Quest noon = QuestScriptRegistry.dispatch("first-light-noon-square", null, 3, 0);
        assertNotNull(noon);
        assertTrue(noon.validateGraph().isEmpty(), noon.validateGraph().toString());
        long finals = noon.getScenes().stream().filter(Scene::isFinalScene).count();
        assertTrue(finals >= 2, "need ≥2 endings, got " + finals);
    }

    @Test
    void noonSquareHasSkillCheckAndCombatPaths() {
        Path packs = locate();
        assumeTrue(packs != null);
        ResourceLoader.scanContentPacks(packs);
        Quest noon = QuestScriptRegistry.dispatch("first-light-noon-square", null, 3, 0);
        assertNotNull(noon);
        boolean skill = noon.getScenes().stream()
                .flatMap(s -> s.getChoices().stream())
                .flatMap(c -> c.getEffects().stream())
                .anyMatch(e -> "SKILL_CHECK".equals(e.getType()));
        boolean combat = noon.getScenes().stream()
                .flatMap(s -> s.getChoices().stream())
                .flatMap(c -> c.getEffects().stream())
                .anyMatch(e -> "TRIGGER_COMBAT".equals(e.getType()));
        assertTrue(skill, "skill check path");
        assertTrue(combat, "combat path");
    }

    private static Path locate() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("content-packs/first-light"))) {
                return dir.resolve("content-packs");
            }
            dir = dir.getParent();
        }
        return null;
    }
}
