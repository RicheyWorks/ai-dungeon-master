package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Robust Narrative Node.
 * Represents a specific location or moment in a Quest, handling transitions
 * and environmental triggers.
 */
public class Scene {

    private static final Random RANDOM = new Random();

    private static final String[] CHAOS_FLAVORS = {
            "The torches burn sideways as reality forgets which way is up.",
            "A low hum vibrates through your bones. Something ancient is watching.",
            "The walls ripple like wet canvas. The dungeon is dreaming again.",
            "Your shadow moves half a second late.",
            "A whisper crawls across the stones: 'Choose carefully...'"
    };

    private static final String[] ENTRY_FLAVORS = {
            "Dust swirls around your boots as the party steps forward.",
            "The air tastes like iron, old magic, and bad decisions.",
            "Somewhere deeper in the dungeon, something laughs.",
            "Your instincts sharpen. This place wants blood.",
            "The silence here feels manufactured."
    };

    private final String id;
    private final String description;
    private final List<Choice> choices;
    private final boolean finalScene;

    /**
     * Standard constructor.
     */
    public Scene(String id, String description, List<Choice> choices) {
        this(id, description, choices, false);
    }

    @JsonCreator
    public Scene(
            @JsonProperty("id") String id,
            @JsonProperty("description") String description,
            @JsonProperty("choices") List<Choice> choices,
            @JsonProperty("isFinalScene") boolean isFinalScene) {

        this.id = normalize(id, "Unknown Scene");
        this.description = normalize(description, "");
        this.choices = new ArrayList<>(choices != null ? choices : Collections.emptyList());
        this.finalScene = isFinalScene;
    }

    /**
     * Triggered when the party enters this scene.
     */
    public void onEnter(DungeonMasterEngine engine) {
        if (engine == null) {
            return;
        }

        engine.log("");
        engine.log("════════════════════════════════════");
        engine.log("Entering: " + id);
        engine.log("════════════════════════════════════");

        if (!description.isBlank()) {
            engine.log(description);
        }

        engine.log(randomLine(ENTRY_FLAVORS));

        if (engine.getChaosLevel() > 4) {
            engine.log(randomLine(CHAOS_FLAVORS));
        }

        if (finalScene) {
            engine.log("This feels like the end of the path... or the start of something worse.");
        }

        if (!choices.isEmpty()) {
            engine.log("Choices available: " + choices.size());
        } else {
            engine.log("No obvious exits reveal themselves.");
        }
    }

    // --- Mutators ---

    public void addChoice(Choice choice) {
        if (choice != null) {
            choices.add(choice);
        }
    }

    public boolean removeChoice(Choice choice) {
        return choice != null && choices.remove(choice);
    }

    // --- Getters ---

    public String getId() {
        return id;
    }

    /**
     * Backward-compatible alias for code that expects getName().
     */
    public String getName() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public List<Choice> getChoices() {
        return Collections.unmodifiableList(choices);
    }

    @JsonProperty("isFinalScene")
    public boolean isFinalScene() {
        return finalScene;
    }

    public boolean isValidChoice(int index) {
        return index >= 0 && index < choices.size();
    }

    public int getChoiceCount() {
        return choices.size();
    }

    private static String normalize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String randomLine(String[] lines) {
        if (lines == null || lines.length == 0) {
            return "";
        }
        return lines[RANDOM.nextInt(lines.length)];
    }

    @Override
    public String toString() {
        return "Scene{" +
                "id='" + id + '\'' +
                ", finalScene=" + finalScene +
                ", choices=" + choices.size() +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, description, choices, finalScene);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Scene other)) return false;
        return finalScene == other.finalScene
                && Objects.equals(id, other.id)
                && Objects.equals(description, other.description)
                && Objects.equals(choices, other.choices);
    }
}
