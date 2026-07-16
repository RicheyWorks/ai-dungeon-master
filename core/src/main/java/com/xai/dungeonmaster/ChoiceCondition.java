package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;
import java.util.Objects;

/**
 * A declarative, serializable availability gate for a Choice (ADR-001
 * Phase 2). Replaces the runtime-only {@code Predicate<Adventurer>}
 * requirement — which silently vanished on save/load — with data that
 * survives persistence and that packs can author:
 *
 * <pre>
 *   { "type": "FLAG",  "key": "tree_fate", "op": "GTE", "value": 2 }
 *   { "type": "LEVEL", "op": "GTE", "value": 3 }
 * </pre>
 *
 * Supported types: {@code FLAG} (world-state flag by key), {@code LEVEL}
 * (acting adventurer's level), {@code DISPOSITION} (key = NPC id, reads the
 * {@code npc:<id>:disposition} flag), and {@code REPUTATION} (key = faction
 * id, reads {@code faction:<id>:reputation}). Ops: EQ, NE, GT, GTE, LT, LTE
 * (default GTE). Unknown types evaluate to true so packs degrade gracefully.
 */
public final class ChoiceCondition {

    private final String type;
    private final String key;
    private final String op;
    private final int value;

    @JsonCreator
    public ChoiceCondition(
            @JsonProperty("type") String type,
            @JsonProperty("key") String key,
            @JsonProperty("op") String op,
            @JsonProperty("value") int value) {
        this.type = type != null ? type.trim().toUpperCase(Locale.ROOT) : "";
        this.key = key != null ? key.trim() : "";
        this.op = op != null ? op.trim().toUpperCase(Locale.ROOT) : "GTE";
        this.value = value;
    }

    public String getType() { return type; }
    public String getKey() { return key; }
    public String getOp() { return op; }
    public int getValue() { return value; }

    /** Evaluate against the world and the acting adventurer. Null-safe. */
    public boolean evaluate(WorldState world, Adventurer actor) {
        int actual;
        switch (type) {
            case "FLAG" -> actual = (world != null) ? world.getFlag(key) : 0;
            case "DISPOSITION" -> actual = (world != null) ? world.getFlag(Npc.dispositionFlag(key)) : 0;
            case "REPUTATION" -> actual = (world != null) ? world.getFlag(Faction.reputationFlag(key)) : 0;
            case "LEVEL" -> {
                if (actor == null) return false;
                actual = actor.getLevel();
            }
            default -> {
                return true;
            }
        }
        return switch (op) {
            case "EQ" -> actual == value;
            case "NE" -> actual != value;
            case "GT" -> actual > value;
            case "LT" -> actual < value;
            case "LTE" -> actual <= value;
            default -> actual >= value; // GTE
        };
    }

    @Override
    public String toString() {
        return "ChoiceCondition{" + type + " " + key + " " + op + " " + value + "}";
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, key, op, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ChoiceCondition other)) return false;
        return value == other.value
                && Objects.equals(type, other.type)
                && Objects.equals(key, other.key)
                && Objects.equals(op, other.op);
    }
}
