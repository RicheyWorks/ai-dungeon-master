package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * High-level party location and discovered rift ledger (ADR-001 Phase 2 follow-up).
 *
 * The campaign owns quest order; the WorldMap owns where the party is and which
 * rifts they've opened. Wired into {@link DungeonMasterEngine}: quest starts set
 * {@code currentLocation}, completions discover the quest title as a rift.
 * Location state is additive in saves (saveVersion 4).
 */
public class WorldMap {

    public static final String DEFAULT_LOCATION = "The Nexus of Realms";

    private final DungeonGenerator generator;
    private String currentLocation;
    private final List<String> discoveredRifts = new ArrayList<>();

    public WorldMap(DungeonGenerator generator) {
        this.generator = generator;
        this.currentLocation = DEFAULT_LOCATION;
        this.discoveredRifts.add("The Whispering Void");
        this.discoveredRifts.add("The Iron Singularity");
    }

    /**
     * Restore location state from a save without regenerating the generator.
     */
    public void restore(String location, List<String> rifts) {
        if (location != null && !location.isBlank()) {
            this.currentLocation = location.trim();
        }
        if (rifts != null && !rifts.isEmpty()) {
            this.discoveredRifts.clear();
            for (String rift : rifts) {
                String normalized = normalize(rift, null);
                if (normalized != null && !discoveredRifts.contains(normalized)) {
                    discoveredRifts.add(normalized);
                }
            }
        }
    }

    /**
     * Entry point for procedural dungeon generation.
     */
    public Quest enterDungeon(String name) {
        String destination = normalize(name, "Unnamed Rift");
        setCurrentLocation(destination);

        if (generator == null) {
            return generateStarterQuest();
        }

        int dungeonSize = 3 + generator.getDifficulty();
        return generator.generateCustomRift(destination, dungeonSize, generator.getDifficulty());
    }

    /**
     * Generates a story-driven starter quest if the generator is unavailable.
     */
    public Quest generateStarterQuest() {
        List<Scene> scenes = new ArrayList<>();

        List<Choice> entranceChoices = new ArrayList<>();
        entranceChoices.add(new Choice(
                "Search for traps",
                "You find nothing but dust and ancient copper wires."
        ));
        entranceChoices.add(new Choice(
                "Charge forward",
                "The echoes of your footsteps alert the horrors in the deep."
        ));

        Scene entrance = new Scene(
                "The First Gate",
                "The heavy doors of the Rift creak open. Dust motes dance in the pale light of an artificial sun.",
                entranceChoices,
                false
        );

        scenes.add(entrance);

        return new Quest(
                "The First Breach",
                "Your initial steps into the multiversal collapse.",
                scenes
        );
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    /** Move the party to a named location (quest start, travel). */
    public void setCurrentLocation(String location) {
        this.currentLocation = normalize(location, DEFAULT_LOCATION);
    }

    public List<String> getDiscoveredRifts() {
        return Collections.unmodifiableList(discoveredRifts);
    }

    public void discoverNewRift(String name) {
        String normalized = normalize(name, null);
        if (normalized != null && !discoveredRifts.contains(normalized)) {
            discoveredRifts.add(normalized);
        }
    }

    @JsonIgnore
    public DungeonGenerator getGenerator() {
        return generator;
    }

    private static String normalize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    /**
     * Snapshot for persistence — generator is not serialized.
     */
    public static final class Snapshot {
        public final String currentLocation;
        public final List<String> discoveredRifts;

        @JsonCreator
        public Snapshot(
                @JsonProperty("currentLocation") String currentLocation,
                @JsonProperty("discoveredRifts") List<String> discoveredRifts) {
            this.currentLocation = currentLocation;
            this.discoveredRifts = discoveredRifts != null
                    ? List.copyOf(discoveredRifts) : List.of();
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(currentLocation, List.copyOf(discoveredRifts));
    }
}
