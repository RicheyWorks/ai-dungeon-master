package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Enterprise-grade interaction model.
 * Handles conditional availability, execution logic, and serialization safety.
 * This class bridges player input to engine-side state changes using action keys.
 */
public class Choice {

    private static final String DEFAULT_ACTION = "DEFAULT_ACTION";

    private final String label;
    private final String actionKey;
    private final String resultText;

    /**
     * Runtime-only requirement logic.
     * Predicates are not safely serializable, so this should be ignored by Jackson.
     */
    @JsonIgnore
    private Predicate<Adventurer> requirement;

    private String requirementFailMessage;

    /**
     * Standard constructor for quick narrative choice creation.
     */
    public Choice(String label, String resultText) {
        this(label, DEFAULT_ACTION, resultText);
    }

    /**
     * Primary constructor for full integration with the action registry.
     */
    @JsonCreator
    public Choice(
            @JsonProperty("label") String label,
            @JsonProperty("actionKey") String actionKey,
            @JsonProperty("resultText") String resultText) {

        this.label = normalize(label, "Unnamed Choice");
        this.actionKey = normalize(actionKey, DEFAULT_ACTION);
        this.resultText = normalize(resultText, "");
        this.requirement = actor -> true;
        this.requirementFailMessage = "Your current state prevents this action.";
    }

    /**
     * Re-establish transient/default runtime state after deserialization if needed.
     */
    private Predicate<Adventurer> requirement() {
        if (requirement == null) {
            requirement = actor -> true;
        }
        return requirement;
    }

    /**
     * Determines whether the given adventurer can select this option.
     */
    public boolean canSelect(Adventurer actor) {
        if (actor == null || !actor.isAlive()) {
            return false;
        }

        try {
            return requirement().test(actor);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Executes the choice, applies engine-side effects, and returns the narrative text.
     */
    public String execute(DungeonMasterEngine engine, Adventurer actor) {
        if (!canSelect(actor)) {
            return requirementFailMessage;
        }

        processEngineAction(engine, actor);
        return resultText;
    }

    /**
     * Maps action keys to engine behavior.
     */
    private void processEngineAction(DungeonMasterEngine engine, Adventurer actor) {
        if (engine == null || actor == null) {
            return;
        }

        String normalizedAction = actionKey.trim().toUpperCase(Locale.ROOT);

        switch (normalizedAction) {
            case DEFAULT_ACTION -> {
                // intentionally no-op
            }
            case "TRIGGER_COMBAT" -> engine.log("A rift opens! Combat is inevitable.");
            case "GIVE_LOOT" -> engine.log(actor.getName() + " found a glimmering artifact.");
            case "REST_PARTY" -> {
                actor.heal(20);
                engine.log(actor.getName() + " takes a moment to recover.");
            }
            case "INCREASE_CHAOS" -> engine.log("The multiverse trembles as entropy rises...");
            default -> engine.log("Unknown action key: " + actionKey);
        }
    }

    /**
     * Adds a requirement to the choice.
     */
    public Choice withRequirement(Predicate<Adventurer> req, String failMsg) {
        this.requirement = (req != null) ? req : actor -> true;
        this.requirementFailMessage = normalize(failMsg, "Your current state prevents this action.");
        return this;
    }

    private static String normalize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    public String getLabel() {
        return label;
    }

    public String getActionKey() {
        return actionKey;
    }

    public String getResultText() {
        return resultText;
    }

    public String getRequirementFailMessage() {
        return requirementFailMessage;
    }

    @Override
    public String toString() {
        return "Choice{" +
                "label='" + label + '\'' +
                ", actionKey='" + actionKey + '\'' +
                ", resultText='" + resultText + '\'' +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, actionKey, resultText);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Choice other)) return false;
        return Objects.equals(label, other.label)
                && Objects.equals(actionKey, other.actionKey)
                && Objects.equals(resultText, other.resultText);
    }
}
