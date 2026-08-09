package com.xai.dungeonmaster;

import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.plugin.QuestScriptRegistry;
import com.xai.dungeonmaster.plugin.builtin.DefaultQuestScript;
import com.xai.dungeonmaster.util.ResourceLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Goal G2 — session recap, epithets, scars; save/load retains memory inputs.
 */
class StoryMemoryTest {

    @BeforeEach
    @AfterEach
    void reset() {
        ContentRegistry.clearForTests();
        QuestScriptRegistry.clearForTests();
    }

    @Test
    void recapSummarizesRecentMilestones() {
        Chronicle c = new Chronicle();
        c.record("quest_started", "The Sealed Letter", "");
        c.record("oath", "letter_fate", "Burned the sealed letter");
        c.record("boss_slain", "Grave Warden", "by Ryn");

        List<String> recap = c.renderRecap(3);
        assertEquals(3, recap.size());
        assertTrue(recap.get(0).contains("Sealed Letter") || recap.get(0).contains("Quest"));
        assertTrue(recap.stream().anyMatch(s -> s.toLowerCase().contains("burned")
                || s.toLowerCase().contains("oath")
                || s.toLowerCase().contains("letter")));
        assertTrue(recap.get(recap.size() - 1).contains("Grave Warden")
                || recap.stream().anyMatch(s -> s.contains("Grave")));
    }

    @Test
    void emptyChronicleHasGentleRecap() {
        List<String> recap = new Chronicle().renderRecap(3);
        assertEquals(1, recap.size());
        assertTrue(recap.get(0).toLowerCase().contains("new tale")
                || recap.get(0).toLowerCase().contains("begun"));
    }

    @Test
    void letterFateYieldsEpithetAndTitle() {
        WorldState w = new WorldState();
        w.setFlag("letter_fate", 2);
        w.setFlag("city_heat", 2);
        Chronicle c = new Chronicle();
        c.record("oath", "letter_fate", "Burned the sealed letter");

        List<String> epithets = StoryMemory.epithets(w, c, List.of());
        assertTrue(epithets.contains("Ash-Handed"), epithets.toString());
        List<String> scars = StoryMemory.scars(w, c);
        assertTrue(scars.stream().anyMatch(s -> s.toLowerCase().contains("watch")
                || s.toLowerCase().contains("ash")), scars.toString());

        Adventurer a = new Adventurer("Ryn", 10, 10, 10, 10, 10, 10, 20, "Rogue");
        String title = StoryMemory.partyTitle(List.of(a), epithets);
        assertTrue(title.contains("Ryn") && title.toLowerCase().contains("ash"), title);
    }

    @Test
    void burnLetterGrantsEpithetInLiveEngineAndSurvivesSave(@TempDir Path tmp) throws Exception {
        Path packs = locateContentPacks();
        assumeTrue(packs != null, "content-packs/first-light required");
        ResourceLoader.scanContentPacks(packs);
        assertTrue(QuestScriptRegistry.isRegistered(DefaultQuestScript.FIRST_LIGHT_SCRIPT)
                || QuestScriptRegistry.isRegistered("default"));

        DungeonMasterEngine engine = new DungeonMasterEngine(
                3, 0, new String[] { "Ryn" }, new String[] { "Rogue" });
        // Prefer first light
        assumeTrue("The Sealed Letter".equals(engine.getCurrentQuest().getTitle()),
                "need First Light opener; got " + engine.getCurrentQuest().getTitle());

        Choice burn = engine.getCurrentAvailableChoices().stream()
                .filter(c -> c.getLabel().toLowerCase().contains("burn"))
                .findFirst()
                .orElseThrow();
        engine.handleChoice(burn);

        assertEquals(2, engine.getWorldState().getFlag("letter_fate"));
        List<String> epithets = StoryMemory.epithets(
                engine.getWorldState(), engine.getChronicle(), engine.getParty());
        assertTrue(epithets.contains("Ash-Handed"), epithets.toString());
        List<String> recap = engine.getChronicle().renderRecap(3);
        assertFalse(recap.isEmpty());
        assertTrue(recap.stream().anyMatch(s -> s.toLowerCase().contains("burn")
                || s.toLowerCase().contains("letter")
                || s.toLowerCase().contains("quest")), recap.toString());

        Path save = tmp.resolve("g2.json");
        engine.saveGame(save.toString());

        DungeonMasterEngine loaded = new DungeonMasterEngine(
                3, 0, new String[] { "Other" }, new String[] { "Warrior" });
        loaded.loadGame(save.toString());
        assertEquals(2, loaded.getWorldState().getFlag("letter_fate"));
        List<String> loadedEpithets = StoryMemory.epithets(
                loaded.getWorldState(), loaded.getChronicle(), loaded.getParty());
        assertTrue(loadedEpithets.contains("Ash-Handed"), loadedEpithets.toString());
        assertEquals(
                engine.getChronicle().renderRecap(3),
                loaded.getChronicle().renderRecap(3));
    }

    private static Path locateContentPacks() {
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
