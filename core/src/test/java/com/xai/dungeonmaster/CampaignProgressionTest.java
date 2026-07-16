package com.xai.dungeonmaster;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xai.dungeonmaster.plugin.QuestScript;
import com.xai.dungeonmaster.plugin.QuestScriptRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ADR-001 Phase 2: world state, declarative choice effects/conditions, and
 * flag-gated campaign progression through the live engine.
 *
 * Engines are built with chaos 0 so random combat can't interrupt the
 * exploration path under test.
 */
class CampaignProgressionTest {

    /** A registrable two-scene quest whose ending sets a flag. */
    private static QuestScript scriptedQuest(String id, String flagArg) {
        return new QuestScript() {
            @Override public String id() { return id; }
            @Override public String displayName() { return id; }
            @Override public Quest build(DungeonMasterEngine engine, int difficulty, int chaos) {
                Scene first = new Scene(id + "-first", "Opening of " + id, List.of(
                        new Choice("Proceed", null, "Onward.", null,
                                flagArg == null ? null : List.of(new ChoiceEffect("SET_FLAG", flagArg)), null)));
                Scene last = new Scene(id + "-last", "Ending of " + id, List.of(
                        new Choice("Finish", "Done.")), true);
                return new Quest(id, "Test quest " + id, List.of(first, last));
            }
        };
    }

    private static DungeonMasterEngine engine() {
        return new DungeonMasterEngine(1, 0, new String[]{"Kael"}, new String[]{"Warrior"});
    }

    /** Take the first available choice {@code steps} times. */
    private static void playSteps(DungeonMasterEngine engine, int steps) {
        for (int i = 0; i < steps && !engine.getCurrentAvailableChoices().isEmpty(); i++) {
            engine.handleChoice(engine.getCurrentAvailableChoices().get(0));
        }
    }

    @BeforeEach
    @AfterEach
    void reset() {
        QuestScriptRegistry.clearForTests();
        CampaignRegistry.clearForTests();
    }

    // ─── Effects ─────────────────────────────────────────────────────────────

    @Test
    void setFlagAndAddFlagEffectsMutateWorldState() {
        DungeonMasterEngine eng = engine();
        new ChoiceEffect("SET_FLAG", "tree_fate=2").apply(eng, null);
        new ChoiceEffect("ADD_FLAG", "hope").apply(eng, null);
        new ChoiceEffect("ADD_FLAG", "hope=2").apply(eng, null);

        assertEquals(2, eng.getWorldState().getFlag("tree_fate"));
        assertEquals(3, eng.getWorldState().getFlag("hope"));
    }

    @Test
    void legacyActionKeyChoicesStillExecute() {
        DungeonMasterEngine eng = engine();
        Adventurer kael = new Adventurer("Kael", 12, 12, 12, 12, 12, 12, 100, "Warrior");
        kael.takeDamage(30);
        int hurtHp = kael.getHp();

        Choice legacy = new Choice("Rest", "REST_PARTY", "You rest.");
        assertEquals("You rest.", legacy.execute(eng, kael));
        assertTrue(kael.getHp() > hurtHp, "legacy REST_PARTY should still heal");
    }

    @Test
    void effectsRunInsteadOfLegacySwitch() {
        DungeonMasterEngine eng = engine();
        Adventurer kael = new Adventurer("Kael", 12, 12, 12, 12, 12, 12, 100, "Warrior");
        Choice flagged = new Choice("Mark", "REST_PARTY", "Marked.", null,
                List.of(new ChoiceEffect("SET_FLAG", "marked=1")), null);

        assertEquals("Marked.", flagged.execute(eng, kael));
        assertEquals(1, eng.getWorldState().getFlag("marked"));
    }

    // ─── Conditions ──────────────────────────────────────────────────────────

    @Test
    void conditionGatedChoicesAreHiddenUntilFlagSet() {
        DungeonMasterEngine eng = engine();
        ChoiceCondition needsKey = new ChoiceCondition("FLAG", "has_key", "GTE", 1);
        Scene door = new Scene("door", "A locked door.", List.of(
                new Choice("Force it", "You slam into the door."),
                new Choice("Unlock it", null, "The key turns.", null, null, needsKey)));
        Quest quest = new Quest("Door", "One door.", List.of(door,
                new Scene("past", "Past the door.", List.of(), true)));

        QuestScriptRegistry.register(new QuestScript() {
            @Override public String id() { return "door-quest"; }
            @Override public String displayName() { return "door-quest"; }
            @Override public Quest build(DungeonMasterEngine e, int d, int c) { return quest; }
        });
        eng.startQuestById("door-quest");

        assertEquals(1, eng.getCurrentAvailableChoices().size());

        eng.getWorldState().setFlag("has_key", 1);
        assertEquals(2, eng.getCurrentAvailableChoices().size());
    }

    // ─── Campaign progression ───────────────────────────────────────────────

    @Test
    void campaignChainsQuestsAndSkipsIneligibleNodes() {
        QuestScriptRegistry.register(scriptedQuest("q-one", "path=2"));
        QuestScriptRegistry.register(scriptedQuest("q-locked", null));
        QuestScriptRegistry.register(scriptedQuest("q-open", null));

        Campaign arc = new Campaign("arc", "Test Arc", List.of(
                new Campaign.QuestNode("q-one", null, Map.of("arc_step", 1)),
                new Campaign.QuestNode("q-locked", Map.of("path", 3), null),
                new Campaign.QuestNode("q-open", Map.of("path", 2), null)));

        DungeonMasterEngine eng = engine();
        eng.setCampaign(arc);
        assertEquals("q-one", eng.startQuest().replace("Entering: ", ""));

        playSteps(eng, 2);

        // q-one finished: outcome recorded, grants applied, gated q-locked
        // (path>=3) skipped, q-open (path>=2) started.
        assertTrue(eng.getWorldState().isQuestFinished("q-one"));
        assertEquals(1, eng.getWorldState().getFlag("arc_step"));
        assertEquals(2, eng.getWorldState().getFlag("path"));
        assertEquals("q-open", eng.startQuest().replace("Entering: ", ""));

        playSteps(eng, 2);
        assertTrue(eng.getWorldState().isQuestFinished("q-open"));
        assertFalse(eng.getWorldState().isQuestFinished("q-locked"));
        assertTrue(eng.getTurnHistory().stream().anyMatch(l -> l.contains("CAMPAIGN COMPLETE")));
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    @Test
    void worldStateAndCampaignSurviveSaveLoad(@TempDir Path dir) {
        QuestScriptRegistry.register(scriptedQuest("q-one", "path=2"));
        Campaign arc = new Campaign("arc", "Test Arc",
                List.of(new Campaign.QuestNode("q-one", null, null)));
        CampaignRegistry.register(arc);

        DungeonMasterEngine eng = engine();
        eng.setCampaign(arc);
        eng.getWorldState().setFlag("tree_fate", 2);
        eng.getWorldState().recordQuestOutcome("earlier-quest", true, "Earlier Quest");

        String path = dir.resolve("save.json").toString();
        eng.saveGame(path);

        DungeonMasterEngine restored = engine();
        restored.loadGame(path);

        assertEquals(2, restored.getWorldState().getFlag("tree_fate"));
        assertTrue(restored.getWorldState().isQuestFinished("earlier-quest"));
        assertNotNull(restored.getCampaign());
        assertEquals("arc", restored.getCampaign().getId());
    }

    @Test
    void legacySaveWithoutPhase2FieldsLoadsCleanly(@TempDir Path dir) throws Exception {
        String legacySave = """
                { "party": [], "chaosLevel": 3, "difficulty": 2,
                  "currentQuest": { "title": "Old", "description": "Legacy.",
                    "scenes": [ { "id": "s1", "description": "Only.", "choices": [], "isFinalScene": true } ],
                    "currentSceneIndex": 0, "completed": false, "failed": false } }
                """;
        Path save = dir.resolve("legacy.json");
        java.nio.file.Files.writeString(save, legacySave);

        DungeonMasterEngine eng = engine();
        eng.loadGame(save.toString());

        assertNotNull(eng.getWorldState());
        assertEquals(0, eng.getWorldState().getFlag("anything"));
        assertNull(eng.getCampaign());
        assertEquals("Old", eng.startQuest().replace("Entering: ", ""));
    }

    @Test
    void gameStateDataRoundTripKeepsSaveVersion(@TempDir Path dir) throws Exception {
        DungeonMasterEngine eng = engine();
        String path = dir.resolve("v2.json").toString();
        eng.saveGame(path);

        ObjectMapper lenient = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        com.fasterxml.jackson.databind.JsonNode root =
                lenient.readTree(new java.io.File(path));

        assertTrue(root.path("saveVersion").asInt() >= 2,
                "phase-2+ saves must carry a saveVersion of at least 2");
        assertTrue(root.has("worldState"));
    }
}
