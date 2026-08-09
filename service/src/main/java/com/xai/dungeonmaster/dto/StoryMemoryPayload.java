package com.xai.dungeonmaster.dto;

import java.util.List;

/**
 * Player-facing story memory for status (Goal G2).
 */
public record StoryMemoryPayload(
        String partyTitle,
        List<String> epithets,
        List<String> scars,
        List<String> recap
) {
    public StoryMemoryPayload {
        epithets = epithets == null ? List.of() : List.copyOf(epithets);
        scars = scars == null ? List.of() : List.copyOf(scars);
        recap = recap == null ? List.of() : List.copyOf(recap);
    }
}
