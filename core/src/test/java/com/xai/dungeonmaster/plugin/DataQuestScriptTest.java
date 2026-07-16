package com.xai.dungeonmaster.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xai.dungeonmaster.Quest;
import com.xai.dungeonmaster.util.ResourceLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the data-driven quest pipeline (ADR-001 Phase 1): DataQuestScript
 * parsing + lint, per-build independence, and ResourceLoader's quests/ scan.
 */
class DataQuestScriptTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String VALID_QUEST = """
            { "id": "test-quest", "title": "Test Quest", "description": "A test.",
              "scenes": [
                { "id": "start", "description": "Start here.",
                  "choices": [ { "label": "Go", "resultText": "Gone.", "nextSceneId": "finish" } ] },
                { "id": "finish", "description": "Done.", "choices": [], "isFinalScene": true }
              ] }
            """;

    @AfterEach
    void reset() {
        QuestScriptRegistry.clearForTests();
    }

    @Test
    void validDocumentParsesAndBuilds() throws Exception {
        DataQuestScript script = DataQuestScript.fromJson(MAPPER.readTree(VALID_QUEST), MAPPER, "test");

        assertEquals("test-quest", script.id());
        assertEquals("Test Quest", script.displayName());

        Quest quest = script.build(null, 4, 4);
        assertEquals("Test Quest", quest.getTitle());
        assertEquals(2, quest.getScenes().size());
        assertEquals("finish", quest.getScenes().get(0).getChoices().get(0).getNextSceneId());
    }

    @Test
    void eachBuildReturnsAnIndependentQuest() throws Exception {
        DataQuestScript script = DataQuestScript.fromJson(MAPPER.readTree(VALID_QUEST), MAPPER, "test");

        Quest first = script.build(null, 4, 4);
        first.advance(null, first.getCurrentScene().getChoices().get(0));
        first.advance(null, null);
        assertTrue(first.isCompleted());

        Quest second = script.build(null, 4, 4);
        assertNotSame(first, second);
        assertFalse(second.isCompleted());
        assertEquals("start", second.getCurrentScene().getId());
    }

    @Test
    void missingIdIsRejected() {
        String noId = VALID_QUEST.replace("\"id\": \"test-quest\",", "");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DataQuestScript.fromJson(MAPPER.readTree(noId), MAPPER, "test"));
        assertTrue(ex.getMessage().contains("missing required top-level 'id'"));
    }

    @Test
    void danglingBranchTargetFailsLint() {
        String dangling = VALID_QUEST.replace("\"nextSceneId\": \"finish\"", "\"nextSceneId\": \"nowhere\"");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DataQuestScript.fromJson(MAPPER.readTree(dangling), MAPPER, "test"));
        assertTrue(ex.getMessage().contains("failed graph lint"));
        assertTrue(ex.getMessage().contains("nowhere"));
    }

    @Test
    void registerQuestScriptsScansDirAndSkipsInvalidFiles(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("good.json"), VALID_QUEST);
        Files.writeString(dir.resolve("bad.json"), "{ \"title\": \"No id, no scenes\" }");
        Files.writeString(dir.resolve("not-json.json"), "this is not json");

        int registered = ResourceLoader.registerQuestScripts(dir, "test-pack");

        assertEquals(1, registered);
        assertTrue(QuestScriptRegistry.isRegistered("test-quest"));
        Quest quest = QuestScriptRegistry.dispatch("test-quest", null, 4, 4);
        assertNotNull(quest);
        assertEquals("Test Quest", quest.getTitle());
    }

    @Test
    void missingQuestsDirIsANoOp(@TempDir Path dir) {
        assertEquals(0, ResourceLoader.registerQuestScripts(dir.resolve("does-not-exist"), "p"));
    }
}
