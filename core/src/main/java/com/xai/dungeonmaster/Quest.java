package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The Quest Engine.
 * Manages narrative progression, state persistence, and victory conditions.
 * Acts as a finite state machine where each Scene is a state.
 */
public class Quest {

    private final String title;
    private final String description;
    private final List<Scene> scenes;

    private int currentSceneIndex;
    private boolean completed;
    private boolean failed;

    /**
     * Helper constructor for new Quest creation.
     */
    public Quest(String title, String description, List<Scene> scenes) {
        this(title, description, scenes, 0, false, false);
    }

    /**
     * Primary constructor for Jackson deserialization.
     */
    @JsonCreator
    public Quest(
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("scenes") List<Scene> scenes,
            @JsonProperty("currentSceneIndex") int currentSceneIndex,
            @JsonProperty("completed") Boolean completed,
            @JsonProperty("failed") Boolean failed) {

        this.title = normalize(title, "Untitled Quest");
        this.description = normalize(description, "No description.");
        this.scenes = (scenes != null) ? List.copyOf(scenes) : Collections.emptyList();
        this.currentSceneIndex = clampSceneIndex(currentSceneIndex, this.scenes);
        this.completed = completed != null && completed;
        this.failed = failed != null && failed;
    }

    @JsonIgnore
    public Scene getCurrentScene() {
        if (scenes.isEmpty()) {
            return null;
        }
        return scenes.get(clampSceneIndex(currentSceneIndex, scenes));
    }

    public void advance(DungeonMasterEngine engine) {
        if (isFinished()) {
            return;
        }

        Scene current = getCurrentScene();

        if (current == null) {
            completeQuest(engine);
            return;
        }

        if (current.isFinalScene()) {
            completeQuest(engine);
            return;
        }

        if (currentSceneIndex < scenes.size() - 1) {
            currentSceneIndex++;
            Scene next = getCurrentScene();

            if (engine != null && next != null) {
                engine.log("Transitioning to: " + next.getName());
                next.onEnter(engine);
            }
        } else {
            completeQuest(engine);
        }
    }

    private void completeQuest(DungeonMasterEngine engine) {
        if (completed) {
            return;
        }

        completed = true;

        if (engine != null) {
            engine.log("QUEST COMPLETE: " + title);
            engine.broadcast("The party has restored stability to this rift sector.");
        }
    }

    public void failQuest() {
        this.failed = true;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<Scene> getScenes() {
        return scenes;
    }

    public int getCurrentSceneIndex() {
        return currentSceneIndex;
    }

    @JsonIgnore
    public boolean isFinished() {
        return completed || failed;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isFailed() {
        return failed;
    }

    public double getProgressPercentage() {
        if (scenes.isEmpty()) {
            return 0.0;
        }
        return Math.min(1.0, (double) (clampSceneIndex(currentSceneIndex, scenes) + 1) / scenes.size());
    }

    private static int clampSceneIndex(int index, List<Scene> scenes) {
        if (scenes == null || scenes.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(index, scenes.size() - 1));
    }

    private static String normalize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    @Override
    public String toString() {
        return "Quest{" +
                "title='" + title + '\'' +
                ", currentSceneIndex=" + currentSceneIndex +
                ", completed=" + completed +
                ", failed=" + failed +
                ", totalScenes=" + scenes.size() +
                '}';
    }
}
