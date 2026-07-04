package com.xai.dungeonmaster.dto;

/**
 * v2 payload for a generated dungeon-master narration
 * ({@code type = "narrative_update"}).
 */
public record NarrativePayload(
        String text,
        String provider,
        int tokensUsed,
        boolean fallback
) {}
