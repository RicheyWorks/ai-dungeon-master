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
     * Optional branch target: the id of the Scene this choice leads to.
     * Null means "no explicit branch" — the quest advances linearly, which
     * preserves the behavior of every pre-branching quest and save file.
     */
    private final String nextSceneId;

    /**
     * Declarative engine-side effects (ADR-001 Phase 2). When non-empty these
     * run instead of the legacy actionKey switch; empty/null keeps the legacy
     * path so old quests and saves behave identically.
     */
    private final java.util.List<ChoiceEffect> effects;

    /**
     * Optional serializable availability gate. Unlike the legacy Predicate
     * requirement it survives save/load and can be authored in pack JSON.
     */
    private final ChoiceCondition condition;

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
     * Constructor for action-registry choices without an explicit branch.
     */
    public Choice(String label, String actionKey, String resultText) {
        this(label, actionKey, resultText, null, null, null);
    }

    /** Constructor for branching choices without effects/conditions. */
    public Choice(String label, String actionKey, String resultText, String nextSceneId) {
        this(label, actionKey, resultText, nextSceneId, null, null);
    }

    /**
     * Primary constructor for full integration with the action registry.
     * {@code nextSceneId}, {@code effects}, and {@code condition} are all
     * optional (additive for save compatibility): old saves and old call
     * sites deserialize/compile unchanged.
     */
    @JsonCreator
    public Choice(
            @JsonProperty("label") String label,
            @JsonProperty("actionKey") String actionKey,
            @JsonProperty("resultText") String resultText,
            @JsonProperty("nextSceneId") String nextSceneId,
            @JsonProperty("effects") java.util.List<ChoiceEffect> effects,
            @JsonProperty("condition") ChoiceCondition condition) {

        this.label = normalize(label, "Unnamed Choice");
        this.actionKey = normalize(actionKey, DEFAULT_ACTION);
        this.resultText = normalize(resultText, "");
        this.nextSceneId = (nextSceneId != null && !nextSceneId.isBlank()) ? nextSceneId.trim() : null;
        this.effects = (effects != null) ? java.util.List.copyOf(effects) : java.util.List.of();
        this.condition = condition;
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
     * True when this choice should be offered right now: the declarative
     * condition (if any) holds for the current world state and actor. The
     * engine filters scene choices through this before exposing them.
     */
    public boolean isAvailable(DungeonMasterEngine engine, Adventurer actor) {
        if (condition == null) return true;
        WorldState world = (engine != null) ? engine.getWorldState() : null;
        return condition.evaluate(world, actor);
    }

    /**
     * Executes the choice, applies engine-side effects, and returns the narrative text.
     */
    public String execute(DungeonMasterEngine engine, Adventurer actor) {
        if (!canSelect(actor) || !isAvailable(engine, actor)) {
            return requirementFailMessage;
        }

        if (!effects.isEmpty()) {
            for (ChoiceEffect effect : effects) {
                effect.apply(engine, actor);
            }
        } else {
            processEngineAction(engine, actor);
        }
        return resultText;
    }

    /**
     * Maps action keys to engine behavior (legacy path — used only when no
     * declarative {@code effects} are present).
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

    /** Branch target scene id, or null for linear advancement. */
    public String getNextSceneId() {
        return nextSceneId;
    }

    /** Declarative effects; empty for legacy actionKey choices. */
    public java.util.List<ChoiceEffect> getEffects() {
        return effects;
    }

    /** Declarative availability gate, or null when ungated. */
    public ChoiceCondition getCondition() {
        return condition;
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
        return Objects.hash(label, actionKey, resultText, nextSceneId, effects, condition);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Choice other)) return false;
        return Objects.equals(label, other.label)
                && Objects.equals(actionKey, other.actionKey)
                && Objects.equals(resultText, other.resultText)
                && Objects.equals(nextSceneId, other.nextSceneId)
                && Objects.equals(effects, other.effects)
                && Objects.equals(condition, other.condition);
    }
}
