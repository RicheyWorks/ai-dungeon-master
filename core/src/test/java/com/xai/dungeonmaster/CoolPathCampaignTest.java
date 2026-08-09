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

/** Goal G9 — First Light campaign chains cold-open → noon without confusion. */
class CoolPathCampaignTest {

    @BeforeEach
    @AfterEach
    void reset() {
        ContentRegistry.clearForTests();
        QuestScriptRegistry.clearForTests();
        CampaignRegistry.clearForTests();
    }

    @Test
    void campaignAutoChainsToNoonAfterLetterQuest() {
        Path packs = locate();
        assumeTrue(packs != null);
        ResourceLoader.scanContentPacks(packs);
        // Registry needs default script for any fallback
        QuestScriptRegistry.register(new com.xai.dungeonmaster.plugin.builtin.DefaultQuestScript());

        DungeonMasterEngine engine = new DungeonMasterEngine(
                3, 0, new String[] { "Kael" }, new String[] { "Warrior" });
        Campaign arc = CampaignRegistry.get("first-light-arc");
        assertNotNull(arc, "first-light-arc must load");
        engine.setCampaign(arc);

        assertEquals("first-light-cold-open", engine.getCurrentQuestScriptId());
        assertNotNull(engine.getCurrentQuest());
        assertTrue(engine.getCurrentQuest().getTitle().toLowerCase().contains("letter")
                || engine.getCurrentQuest().getTitle().toLowerCase().contains("sealed"));

        // Drive letter quest only until campaign advances script id
        int guard = 0;
        while ("first-light-cold-open".equals(engine.getCurrentQuestScriptId()) && guard++ < 40) {
            List<Choice> choices = engine.getCurrentAvailableChoices();
            assertFalse(choices.isEmpty(), "stuck with no choices at " + sceneLabel(engine));
            Choice pick = choices.stream()
                    .filter(c -> !c.getLabel().equalsIgnoreCase("Attack")
                            && !c.getLabel().toLowerCase().contains("push"))
                    .findFirst()
                    .orElse(choices.get(0));
            engine.handleChoice(pick);
            if (engine.getCombatState().isActive()) {
                engine.handleChoice(new Choice("Flee", "escape"));
            }
        }
        assertTrue(guard < 40, "letter quest should finish and chain");

        assertEquals("first-light-noon-square", engine.getCurrentQuestScriptId(),
                "expected noon after letter; script=" + engine.getCurrentQuestScriptId()
                        + " quest=" + (engine.getCurrentQuest() != null
                        ? engine.getCurrentQuest().getTitle() : null));
        assertNotNull(engine.getCurrentQuest());
        assertFalse(engine.getCurrentQuest().isFinished(), "noon should be a fresh chapter");
        assertNotNull(engine.playHint());
        assertFalse(engine.playHint().isBlank());
    }

    @Test
    void ambientCombatSkippedDuringCampaign() {
        Path packs = locate();
        assumeTrue(packs != null);
        ResourceLoader.scanContentPacks(packs);
        QuestScriptRegistry.register(new com.xai.dungeonmaster.plugin.builtin.DefaultQuestScript());

        // High chaos would normally fire combat often
        DungeonMasterEngine engine = new DungeonMasterEngine(
                3, 10, new String[] { "Kael" }, new String[] { "Warrior" });
        engine.setCampaign(CampaignRegistry.get("first-light-arc"));

        for (int i = 0; i < 8; i++) {
            List<Choice> choices = engine.getCurrentAvailableChoices();
            if (choices.isEmpty()) break;
            if (engine.getCurrentQuest() != null && engine.getCurrentQuest().isFinished()) break;
            engine.handleChoice(choices.get(0));
            assertFalse(engine.getCombatState().isActive(),
                    "ambient combat must not interrupt campaign story at step " + i);
        }
    }

    private static String sceneLabel(DungeonMasterEngine engine) {
        Quest q = engine.getCurrentQuest();
        if (q == null) return "null-quest";
        Scene sc = q.getCurrentScene();
        return q.getTitle() + "/" + (sc != null ? sc.getId() : "?");
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
