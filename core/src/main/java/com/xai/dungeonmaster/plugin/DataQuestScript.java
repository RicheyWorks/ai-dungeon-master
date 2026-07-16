package com.xai.dungeonmaster.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Quest;

import java.util.List;

/**
 * A {@link QuestScript} backed by a JSON quest document instead of code —
 * the Phase-1 "authored skeleton" of the story-depth plan (ADR-001).
 *
 * Content packs ship quest files under {@code quests/*.json}; each file is a
 * {@link Quest} document (title, description, scenes with choices, optional
 * per-choice {@code nextSceneId} branch targets) plus a top-level {@code id}
 * for the {@link QuestScriptRegistry}. Packs get campaigns with zero code,
 * exactly as they ship items and monsters.
 *
 * The raw document is retained and re-materialized on every {@link #build},
 * so each call returns an independent Quest — mutating one play-through's
 * quest can never bleed into the next.
 */
public final class DataQuestScript implements QuestScript {

    private final String id;
    private final String displayName;
    private final JsonNode document;
    private final ObjectMapper mapper;

    private DataQuestScript(String id, String displayName, JsonNode document, ObjectMapper mapper) {
        this.id = id;
        this.displayName = displayName;
        this.document = document;
        this.mapper = mapper;
    }

    /**
     * Parse and lint a quest document.
     *
     * Requirements: a non-blank top-level {@code id}, at least one scene, and
     * a clean {@link Quest#validateGraph()} (no duplicate scene ids, no
     * dangling {@code nextSceneId}).
     *
     * @throws IllegalArgumentException with all problems listed, so loaders
     *         can surface every authoring error in one message.
     */
    public static DataQuestScript fromJson(JsonNode root, ObjectMapper mapper, String sourceName) {
        if (root == null || mapper == null) {
            throw new IllegalArgumentException("quest document " + sourceName + ": empty document");
        }

        // Quest documents carry loader-level fields (id, title) that the Quest
        // class doesn't know, and future schema versions may add more — always
        // materialize leniently, regardless of the caller's mapper settings.
        mapper = mapper.copy().configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        String id = root.path("id").asText("").trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException(
                    "quest document " + sourceName + ": missing required top-level 'id'");
        }

        Quest probe = materialize(root, mapper, sourceName);
        if (probe.getScenes().isEmpty()) {
            throw new IllegalArgumentException(
                    "quest document " + sourceName + ": no scenes defined");
        }

        List<String> problems = probe.validateGraph();
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(
                    "quest document " + sourceName + " failed graph lint: " + String.join("; ", problems));
        }

        String displayName = root.path("title").asText(id);
        return new DataQuestScript(id, displayName, root.deepCopy(), mapper);
    }

    private static Quest materialize(JsonNode root, ObjectMapper mapper, String sourceName) {
        try {
            return mapper.treeToValue(root, Quest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "quest document " + sourceName + ": not a valid Quest document — " + e.getMessage(), e);
        }
    }

    @Override public String id() { return id; }
    @Override public String displayName() { return displayName; }

    @Override
    public Quest build(DungeonMasterEngine engine, int difficulty, int chaos) {
        return materialize(document, mapper, id);
    }
}
