package com.xai.dungeonmaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The WorldMap manages the high-level location of the party.
 * It serves as the gateway to the procedural DungeonGenerator.
 */
public class WorldMap {

    private final DungeonGenerator generator;
    private String currentLocation;
    private final List<String> discoveredRifts = new ArrayList<>();

    public WorldMap(DungeonGenerator generator) {
        this.generator = generator;
        this.currentLocation = "The Nexus of Realms";
        this.discoveredRifts.add("The Whispering Void");
        this.discoveredRifts.add("The Iron Singularity");
    }

    /**
     * Entry point for procedural dungeon generation.
     */
    public Quest enterDungeon(String name) {
        String destination = normalize(name, "Unnamed Rift");
        this.currentLocation = destination;

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

    public List<String> getDiscoveredRifts() {
        return Collections.unmodifiableList(discoveredRifts);
    }

    public void discoverNewRift(String name) {
        String normalized = normalize(name, null);
        if (normalized != null && !discoveredRifts.contains(normalized)) {
            discoveredRifts.add(normalized);
        }
    }

    private static String normalize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
