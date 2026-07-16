package com.xai.dungeonmaster;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xai.dungeonmaster.plugin.LLMProvider;
import com.xai.dungeonmaster.plugin.QuestScript;
import com.xai.dungeonmaster.plugin.QuestScriptRegistry;
import com.xai.dungeonmaster.plugin.builtin.LocalStubProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ADR-001 Phase 3: structured narrative memory — Chronicle recording,
 * compaction, bounded fact rendering, engine event emission, offline-stub
 * recap, and save/load compatibility.
 */
class ChronicleTest {

    @BeforeEach
    @AfterEach
    void reset() {
        QuestScriptRegistry.clearForTests();
        CampaignRegistry.clearForTests();
    }

    private static DungeonMasterEngine engine() {
        return new DungeonMasterEngine(1, 0, new String[]{"Kael"}, new String[]{"Warrior"});
    }

    // ─── Chronicle mechanics ────────────────────────────────────────────────

    @Test
    void recordsAndRendersRecentEvents() {
        Chronicle c = new Chronicle();
        c.record("quest_started", "The Weeping Tree", "");
        c.record("boss_slain", "Grave Warden", "by Kael");

        List<String> facts = c.renderFacts(6);

        assertEquals(2, facts.size());
        assertEquals("Quest begun: The Weeping Tree", facts.get(0));
        assertEquals("Boss slain: Grave Warden (by Kael)", facts.get(1));
    }

    @Test
    void oldEventsCompactIntoTally() {
        Chronicle c = new Chronicle();
        for (int i = 0; i < Chronicle.MAX_RECENT + 7; i++) {
            c.record("enemy_slain", "Husk " + i, "");
        }

        assertEquals(Chronicle.MAX_RECENT, c.getRecentEvents().size());
        assertEquals(7, c.getTally().get("enemy_slain"));

        List<String> facts = c.renderFacts(3);
        assertTrue(facts.get(0).startsWith("Earlier in this tale: 7 foes slain"));
        assertEquals(3, facts.size());
    }

    @Test
    void factRenderingIsBoundedRegardlessOfHistorySize() {
        Chronicle c = new Chronicle();
        for (int i = 0; i < 500; i++) {
            c.record("enemy_slain", "Endless Husk Variant Number " + i + " Of The Deep Dark", "");
        }

        List<String> facts = c.renderFacts(Integer.MAX_VALUE);

        assertTrue(facts.size() <= Chronicle.MAX_FACTS, "fact count must stay capped");
        int total = facts.stream().mapToInt(String::length).sum();
        assertTrue(total <= Chronicle.MAX_FACTS * Chronicle.MAX_FACT_LENGTH,
                "rendered facts must stay within the char budget (got " + total + ")");
        // Well under the 4000-token narration ceiling even at ~1 token / 4 chars.
        assertTrue(total / 4 < 4000);
    }

    @Test
    void chronicleSurvivesSerializationRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Chronicle c = new Chronicle();
        for (int i = 0; i < Chronicle.MAX_RECENT + 2; i++) c.record("enemy_slain", "Husk " + i, "");
        c.record("quest_completed", "The Weeping Tree", "");

        Chronicle restored = mapper.readValue(mapper.writeValueAsString(c), Chronicle.class);

        assertEquals(c.getRecentEvents().size(), restored.getRecentEvents().size());
        assertEquals(c.getTally(), restored.getTally());
        assertEquals(c.getSequence(), restored.getSequence());
        assertEquals(c.renderFacts(6), restored.renderFacts(6));
    }

    // ─── Engine emission ────────────────────────────────────────────────────

    @Test
    void engineRecordsQuestLifecycleEvents() {
        QuestScriptRegistry.register(new QuestScript() {
            @Override public String id() { return "mini"; }
            @Override public String displayName() { return "mini"; }
            @Override public Quest build(DungeonMasterEngine e, int d, int c) {
                return new Quest("Mini Quest", "Tiny.", List.of(
                        new Scene("only", "Only scene.", List.of(new Choice("End", "Done.")), true)));
            }
        });

        DungeonMasterEngine eng = engine();
        eng.startQuestById("mini");
        eng.handleChoice(eng.getCurrentAvailableChoices().get(0));

        List<String> facts = eng.getChronicle().renderFacts(8);
        assertTrue(facts.stream().anyMatch(f -> f.equals("Quest begun: Mini Quest")));
        assertTrue(facts.stream().anyMatch(f -> f.equals("Quest completed: Mini Quest")));
    }

    // ─── Narration integration ──────────────────────────────────────────────

    @Test
    void stubNarrationRecapsTheLatestFact() {
        DungeonMasterEngine eng = engine();
        eng.getChronicle().record("boss_slain", "Grave Warden", "");
        eng.setNarrator(new LocalStubProvider());

        LLMProvider.NarrativeResponse resp = eng.narrate("search the altar");

        assertTrue(resp.text.contains("You remember: Boss slain: Grave Warden"),
                "stub should weave the latest chronicle fact into narration, got: " + resp.text);
    }

    @Test
    void stubWithoutFactsHasNoRecap() {
        LLMProvider.NarrativeResponse resp = new LocalStubProvider().generate(
                new LLMProvider.NarrativePrompt("look around", "the rift", 256));
        assertFalse(resp.text.contains("You remember"));
    }

    // ─── Persistence ────────────────────────────────────────────────────────

    @Test
    void chronicleSurvivesSaveLoad(@TempDir Path dir) {
        DungeonMasterEngine eng = engine();
        eng.getChronicle().record("boss_slain", "Grave Warden", "by Kael");
        String path = dir.resolve("save.json").toString();
        eng.saveGame(path);

        DungeonMasterEngine restored = engine();
        restored.loadGame(path);

        assertTrue(restored.getChronicle().renderFacts(8).stream()
                .anyMatch(f -> f.contains("Boss slain: Grave Warden")));
    }

    @Test
    void legacySaveWithoutChronicleLoadsWithEmptyMemory(@TempDir Path dir) throws Exception {
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

        assertNotNull(eng.getChronicle());
        assertTrue(eng.getChronicle().isEmpty());
    }
}
