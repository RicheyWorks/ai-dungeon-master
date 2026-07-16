package com.xai.dungeonmaster;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase-1 story-depth tests (ADR-001): per-choice {@code nextSceneId}
 * branching in Quest.advance, linear fallback, dangling-target fallback,
 * graph lint, and backward compatibility with pre-branching save JSON.
 */
class QuestBranchingTest {

    private static Quest branchingQuest() {
        Scene gate = new Scene("gate", "The gate.", List.of(
                new Choice("Left door", null, "You go left.", "left-hall"),
                new Choice("Right door", null, "You go right.", "right-hall"),
                new Choice("Wait", "You wait.")));
        Scene left = new Scene("left-hall", "Left hall.", List.of(
                new Choice("Onward", null, "Deeper.", "end")));
        Scene right = new Scene("right-hall", "Right hall.", List.of(
                new Choice("Onward", null, "Deeper.", "end")));
        Scene end = new Scene("end", "The end.", List.of(), true);
        return new Quest("Branch Test", "A forked path.", List.of(gate, left, right, end));
    }

    @Test
    void choiceWithNextSceneIdJumpsToThatScene() {
        Quest quest = branchingQuest();
        Choice right = quest.getCurrentScene().getChoices().get(1);

        quest.advance(null, right);

        assertEquals("right-hall", quest.getCurrentScene().getId());
        assertFalse(quest.isFinished());
    }

    @Test
    void branchingCanSkipScenes() {
        Quest quest = branchingQuest();
        // Jump gate -> right-hall (index 2), skipping left-hall entirely.
        quest.advance(null, quest.getCurrentScene().getChoices().get(1));
        // right-hall -> end.
        quest.advance(null, quest.getCurrentScene().getChoices().get(0));

        assertEquals("end", quest.getCurrentScene().getId());
    }

    @Test
    void choiceWithoutNextSceneIdAdvancesLinearly() {
        Quest quest = branchingQuest();
        Choice wait = quest.getCurrentScene().getChoices().get(2);

        quest.advance(null, wait);

        assertEquals("left-hall", quest.getCurrentScene().getId());
    }

    @Test
    void nullChoiceKeepsLegacyLinearBehavior() {
        Quest quest = branchingQuest();

        quest.advance(null);

        assertEquals("left-hall", quest.getCurrentScene().getId());
    }

    @Test
    void danglingNextSceneIdFallsBackToLinear() {
        Scene a = new Scene("a", "A.", List.of(
                new Choice("Go", null, "Going.", "no-such-scene")));
        Scene b = new Scene("b", "B.", List.of(), true);
        Quest quest = new Quest("Dangling", "Bad link.", List.of(a, b));

        quest.advance(null, a.getChoices().get(0));

        assertEquals("b", quest.getCurrentScene().getId());
    }

    @Test
    void finalSceneCompletesQuestRegardlessOfBranchTarget() {
        Quest quest = branchingQuest();
        quest.advance(null, quest.getCurrentScene().getChoices().get(0)); // -> left-hall
        quest.advance(null, quest.getCurrentScene().getChoices().get(0)); // -> end (final)
        quest.advance(null, null);                                        // final scene completes

        assertTrue(quest.isCompleted());
    }

    @Test
    void validateGraphFlagsDanglingTargetsAndDuplicateIds() {
        Scene a1 = new Scene("a", "A.", List.of(
                new Choice("Go", null, "Going.", "missing")));
        Scene a2 = new Scene("a", "A again.", List.of());
        Quest quest = new Quest("Broken", "Lint me.", List.of(a1, a2));

        List<String> problems = quest.validateGraph();

        assertEquals(2, problems.size());
        assertTrue(problems.stream().anyMatch(p -> p.contains("duplicate scene id")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("unknown scene 'missing'")));
    }

    @Test
    void validateGraphPassesForCleanBranchingQuest() {
        assertTrue(branchingQuest().validateGraph().isEmpty());
    }

    @Test
    void legacySaveJsonWithoutNextSceneIdStillLoads() throws Exception {
        String legacy = """
                { "title": "Old Save", "description": "Pre-branching quest.",
                  "scenes": [
                    { "id": "s1", "description": "First.",
                      "choices": [ { "label": "Go", "actionKey": "DEFAULT_ACTION", "resultText": "Gone." } ],
                      "isFinalScene": false },
                    { "id": "s2", "description": "Last.", "choices": [], "isFinalScene": true }
                  ],
                  "currentSceneIndex": 0, "completed": false, "failed": false }
                """;

        Quest quest = new ObjectMapper().readValue(legacy, Quest.class);

        assertEquals("s1", quest.getCurrentScene().getId());
        assertNull(quest.getCurrentScene().getChoices().get(0).getNextSceneId());

        quest.advance(null, quest.getCurrentScene().getChoices().get(0));
        assertEquals("s2", quest.getCurrentScene().getId());
    }

    @Test
    void branchStateSurvivesSerializationRoundTrip() throws Exception {
        // Mirror the engine's persistence mapper (ResourceLoader.getMapper()),
        // which tolerates unknown properties like Scene's serialized "name" alias.
        ObjectMapper mapper = new ObjectMapper().configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Quest quest = branchingQuest();
        quest.advance(null, quest.getCurrentScene().getChoices().get(1)); // -> right-hall

        Quest restored = mapper.readValue(mapper.writeValueAsString(quest), Quest.class);

        assertEquals("right-hall", restored.getCurrentScene().getId());
        assertEquals("end", restored.getCurrentScene().getChoices().get(0).getNextSceneId());
    }
}
