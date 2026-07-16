package com.xai.dungeonmaster;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A non-player character (ADR-001 Phase 4). Pure data, shipped by content
 * packs as {@code npcs/*.json} and merged into the ContentRegistry like
 * items and monsters.
 *
 * Live disposition is not stored here — it accumulates in the WorldState
 * flag {@code npc:<id>:disposition} via MODIFY_DISPOSITION choice effects,
 * so it persists in saves and can gate choices. {@code baseDisposition} is
 * the authored starting stance, and {@code persona} is the character sheet
 * the narrator weaves into dialogue scenes.
 */
public class Npc {

    private final String id;
    private final String displayName;
    private final String role;
    private final String factionId;
    private final String persona;
    private final int baseDisposition;

    @JsonCreator
    public Npc(
            @JsonProperty("id") String id,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("role") String role,
            @JsonProperty("factionId") String factionId,
            @JsonProperty("persona") String persona,
            @JsonProperty("baseDisposition") int baseDisposition) {
        this.id = id != null ? id.trim() : "";
        this.displayName = (displayName != null && !displayName.isBlank()) ? displayName : this.id;
        this.role = role != null ? role : "";
        this.factionId = factionId != null ? factionId : "";
        this.persona = persona != null ? persona : "";
        this.baseDisposition = baseDisposition;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
    public String getFactionId() { return factionId; }
    public String getPersona() { return persona; }
    public int getBaseDisposition() { return baseDisposition; }

    /** WorldState flag key tracking accumulated disposition changes. */
    public static String dispositionFlag(String npcId) {
        return "npc:" + npcId + ":disposition";
    }

    /** WorldState flag key marking that the party has met this NPC. */
    public static String metFlag(String npcId) {
        return "npc:" + npcId + ":met";
    }

    /** Compact sheet for narrator context. */
    public String renderFact() {
        StringBuilder sb = new StringBuilder("Present: ").append(displayName);
        if (!role.isEmpty()) sb.append(", ").append(role);
        if (!factionId.isEmpty()) sb.append(" of ").append(factionId);
        if (!persona.isEmpty()) sb.append(" — ").append(persona);
        return sb.toString();
    }
}
