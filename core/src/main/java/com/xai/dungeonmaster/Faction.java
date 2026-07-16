package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A faction (ADR-001 Phase 4). Pure data from content packs'
 * {@code factions/*.json}, merged into the ContentRegistry.
 *
 * Live reputation accumulates in the WorldState flag
 * {@code faction:<id>:reputation} via MODIFY_REPUTATION choice effects.
 */
public class Faction {

    private final String id;
    private final String displayName;
    private final String description;
    private final int baseReputation;

    @JsonCreator
    public Faction(
            @JsonProperty("id") String id,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("description") String description,
            @JsonProperty("baseReputation") int baseReputation) {
        this.id = id != null ? id.trim() : "";
        this.displayName = (displayName != null && !displayName.isBlank()) ? displayName : this.id;
        this.description = description != null ? description : "";
        this.baseReputation = baseReputation;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getBaseReputation() { return baseReputation; }

    /** WorldState flag key tracking accumulated reputation changes. */
    public static String reputationFlag(String factionId) {
        return "faction:" + factionId + ":reputation";
    }
}
