package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;
import java.util.Objects;

/**
 * One declarative engine-side effect of taking a Choice (ADR-001 Phase 2).
 *
 * Replaces the hardcoded actionKey switch with an open, serializable verb
 * list that content packs can compose freely:
 *
 * <pre>
 *   { "type": "SET_FLAG",       "arg": "tree_fate=2" }
 *   { "type": "ADD_FLAG",       "arg": "parish_hope" }      // +1, or "key=delta"
 *   { "type": "TRIGGER_COMBAT" }                            // actually starts combat
 *   { "type": "GIVE_LOOT" }                                 // actually rolls loot
 *   { "type": "REST_PARTY" }
 *   { "type": "INCREASE_CHAOS" }
 *   { "type": "START_QUEST",    "arg": "black-hollows-embers" }
 *   { "type": "MODIFY_DISPOSITION", "arg": "mother-brine=1" }  // NPC disposition
 *   { "type": "MODIFY_REPUTATION",  "arg": "parish-survivors=1" }
 * </pre>
 *
 * Unknown types log and do nothing, so packs targeting newer engines degrade
 * gracefully on older ones.
 */
public final class ChoiceEffect {

    private final String type;
    private final String arg;

    @JsonCreator
    public ChoiceEffect(
            @JsonProperty("type") String type,
            @JsonProperty("arg") String arg) {
        this.type = type != null ? type.trim().toUpperCase(Locale.ROOT) : "";
        this.arg = arg != null ? arg.trim() : "";
    }

    public String getType() { return type; }
    public String getArg() { return arg; }

    /** Execute this effect against the live engine. Null-safe on both args. */
    public void apply(DungeonMasterEngine engine, Adventurer actor) {
        if (engine == null) return;

        switch (type) {
            case "SET_FLAG" -> {
                KeyValue kv = parseArg(1);
                if (kv != null) engine.getWorldState().setFlag(kv.key, kv.value);
            }
            case "ADD_FLAG" -> {
                KeyValue kv = parseArg(1);
                if (kv != null) engine.getWorldState().addFlag(kv.key, kv.value);
            }
            case "TRIGGER_COMBAT" -> {
                engine.log("A rift opens! Combat is inevitable.");
                engine.triggerCombatEncounter();
            }
            case "GIVE_LOOT" -> {
                if (actor != null) engine.grantLoot(actor);
            }
            case "REST_PARTY" -> {
                if (actor != null) {
                    actor.heal(20);
                    engine.log(actor.getName() + " takes a moment to recover.");
                }
            }
            case "INCREASE_CHAOS" -> {
                engine.log("The multiverse trembles as entropy rises...");
                engine.bumpChaos(1);
            }
            case "START_QUEST" -> {
                if (!arg.isEmpty()) engine.startQuestById(arg);
            }
            case "MODIFY_DISPOSITION" -> {
                KeyValue kv = parseArg(1);
                if (kv != null) engine.getWorldState().addFlag(Npc.dispositionFlag(kv.key), kv.value);
            }
            case "MODIFY_REPUTATION" -> {
                KeyValue kv = parseArg(1);
                if (kv != null) engine.getWorldState().addFlag(Faction.reputationFlag(kv.key), kv.value);
            }
            default -> engine.log("Unknown choice effect: " + type);
        }
    }

    /** Parse "key" or "key=value" args; {@code defaultValue} covers the bare-key form. */
    private KeyValue parseArg(int defaultValue) {
        if (arg.isEmpty()) return null;
        int eq = arg.indexOf('=');
        if (eq < 0) return new KeyValue(arg, defaultValue);
        String key = arg.substring(0, eq).trim();
        if (key.isEmpty()) return null;
        try {
            return new KeyValue(key, Integer.parseInt(arg.substring(eq + 1).trim()));
        } catch (NumberFormatException e) {
            return new KeyValue(key, defaultValue);
        }
    }

    private record KeyValue(String key, int value) {}

    @Override
    public String toString() {
        return "ChoiceEffect{" + type + (arg.isEmpty() ? "" : " " + arg) + "}";
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, arg);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ChoiceEffect other)) return false;
        return Objects.equals(type, other.type) && Objects.equals(arg, other.arg);
    }
}
