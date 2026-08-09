package com.xai.dungeonmaster;

import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.plugin.QuestScriptRegistry;
import com.xai.dungeonmaster.plugin.builtin.DefaultQuestScript;
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

/**
 * Goal G1 — legendary first run: free First Light pack is the default opener,
 * first choice is irreversible (letter_fate flag), delayed callback at the-knock
 * branches on that flag.
 */
class LegendaryFirstRunTest {

    @BeforeEach
    @AfterEach
    void reset() {
        ContentRegistry.clearForTests();
        QuestScriptRegistry.clearForTests();
    }

    private static Path locateContentPacks() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("content-packs");
            if (Files.isDirectory(candidate.resolve("first-light"))) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static void loadFirstLight() {
        Path packs = locateContentPacks();
        assumeTrue(packs != null, "content-packs/first-light not found");
        ResourceLoader.scanContentPacks(packs);
        // Trigger ServiceLoader for DefaultQuestScript
        assertTrue(QuestScriptRegistry.isRegistered("default"));
    }

    @Test
    void defaultOpenerIsSealedLetterWhenPackLoaded() {
        loadFirstLight();
        assertTrue(QuestScriptRegistry.isRegistered(DefaultQuestScript.FIRST_LIGHT_SCRIPT));

        DungeonMasterEngine engine = new DungeonMasterEngine(
                3, 0, new String[] { "Ryn" }, new String[] { "Rogue" });
        Quest quest = engine.getCurrentQuest();
        assertNotNull(quest);
        assertEquals("The Sealed Letter", quest.getTitle());
        Scene scene = quest.getCurrentScene();
        assertNotNull(scene);
        assertEquals("rain-alley", scene.getId());
        assertTrue(scene.getDescription().toLowerCase().contains("already"),
                "cold open should start mid-action: " + scene.getDescription());
        assertTrue(scene.getChoices().size() >= 3, "three opening paths");
    }

    @Test
    void burningLetterIsIrreversibleAndCallbacksDiffer() {
        loadFirstLight();
        DungeonMasterEngine burn = new DungeonMasterEngine(
                3, 0, new String[] { "Ash" }, new String[] { "Rogue" });
        choose(burn, "Burn the letter in the baker's oven");
        assertEquals(2, burn.getWorldState().getFlag("letter_fate"));
        advanceToScene(burn, "the-knock");
        List<String> knockLabels = labels(burn);
        assertTrue(
                knockLabels.stream().anyMatch(l -> l.toLowerCase().contains("empty of proof")
                        || l.toLowerCase().contains("hands empty")),
                "burn path callback missing: " + knockLabels + " at " + sceneId(burn));
        assertFalse(
                knockLabels.stream().anyMatch(l -> l.toLowerCase().contains("still sealed")),
                "kept-letter callback should be hidden after burn: " + knockLabels);

        DungeonMasterEngine keep = new DungeonMasterEngine(
                3, 0, new String[] { "Keep" }, new String[] { "Rogue" });
        choose(keep, "Keep the letter — run for the river");
        assertEquals(1, keep.getWorldState().getFlag("letter_fate"));
        advanceToScene(keep, "the-knock");
        List<String> keepKnock = labels(keep);
        assertTrue(
                keepKnock.stream().anyMatch(l -> l.toLowerCase().contains("still sealed")),
                "kept-letter callback missing: " + keepKnock + " at " + sceneId(keep));
    }

    @Test
    void graphLintPassesForAuthoredQuest() {
        loadFirstLight();
        Quest q = QuestScriptRegistry.dispatch(DefaultQuestScript.FIRST_LIGHT_SCRIPT, null, 3, 1);
        assertNotNull(q);
        List<String> problems = q.validateGraph();
        assertTrue(problems.isEmpty(), "graph problems: " + problems);
        assertTrue(q.getScenes().size() >= 5, "at least five beats");
    }

    private static void choose(DungeonMasterEngine engine, String label) {
        Choice match = engine.getCurrentAvailableChoices().stream()
                .filter(c -> label.equals(c.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "choice not available: " + label + " in " + labels(engine)
                                + " at " + sceneId(engine)));
        engine.handleChoice(match);
    }

    private static void advanceToScene(DungeonMasterEngine engine, String targetSceneId) {
        for (int i = 0; i < 12; i++) {
            if (targetSceneId.equals(sceneId(engine))) return;
            if (engine.getCombatState().isActive()) {
                fail("entered combat before " + targetSceneId + " at " + sceneId(engine)
                        + " choices=" + labels(engine));
            }
            List<Choice> opts = engine.getCurrentAvailableChoices();
            assertFalse(opts.isEmpty(), "no choices at " + sceneId(engine));
            engine.handleChoice(opts.get(0));
        }
        fail("never reached " + targetSceneId + "; stuck at " + sceneId(engine)
                + " choices=" + labels(engine));
    }

    private static List<String> labels(DungeonMasterEngine engine) {
        return engine.getCurrentAvailableChoices().stream().map(Choice::getLabel).toList();
    }

    private static String sceneId(DungeonMasterEngine engine) {
        Quest q = engine.getCurrentQuest();
        if (q == null || q.getCurrentScene() == null) return "?";
        return q.getCurrentScene().getId();
    }
}
