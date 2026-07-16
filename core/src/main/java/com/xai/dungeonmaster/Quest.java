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

    /**
     * Linear advancement (legacy path). Equivalent to
     * {@code advance(engine, null)} — kept so existing call sites and tests
     * compile unchanged.
     */
    public void advance(DungeonMasterEngine engine) {
        advance(engine, null);
    }

    /**
     * Advance the quest, honoring the taken choice's branch target.
     *
     * If {@code taken} names a {@code nextSceneId} that resolves to a scene in
     * this quest, the quest jumps there (branching). Otherwise — null choice,
     * null target, or a dangling id — it falls back to the historical linear
     * {@code currentSceneIndex + 1} walk, so pre-branching quests and old save
     * files behave exactly as before.
     */
    public void advance(DungeonMasterEngine engine, Choice taken) {
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

        if (taken != null && taken.getNextSceneId() != null) {
            int target = indexOfScene(taken.getNextSceneId());
            if (target >= 0) {
                currentSceneIndex = target;
                Scene next = getCurrentScene();
                if (engine != null && next != null) {
                    engine.log("Transitioning to: " + next.getName());
                    next.onEnter(engine);
                }
                return;
            }
            if (engine != null) {
                engine.log("WARN: choice '" + taken.getLabel() + "' points to unknown scene '"
                        + taken.getNextSceneId() + "' — advancing linearly.");
            }
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

    /** Index of the scene with the given id, or -1. First match wins. */
    private int indexOfScene(String sceneId) {
        if (sceneId == null) return -1;
        for (int i = 0; i < scenes.size(); i++) {
            if (sceneId.equals(scenes.get(i).getId())) return i;
        }
        return -1;
    }

    /**
     * Load-time graph lint. Returns a list of problems (empty = valid):
     * duplicate scene ids and choices whose {@code nextSceneId} doesn't
     * resolve to any scene. Loaders should surface these before a broken
     * graph reaches a player.
     */
    public List<String> validateGraph() {
        List<String> problems = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Scene scene : scenes) {
            if (!seen.add(scene.getId())) {
                problems.add("duplicate scene id: '" + scene.getId() + "'");
            }
        }
        for (Scene scene : scenes) {
            for (Choice choice : scene.getChoices()) {
                String target = choice.getNextSceneId();
                if (target != null && indexOfScene(target) < 0) {
                    problems.add("scene '" + scene.getId() + "' choice '" + choice.getLabel()
                            + "' points to unknown scene '" + target + "'");
                }
            }
        }
        return problems;
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
