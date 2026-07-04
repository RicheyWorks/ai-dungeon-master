package com.xai.dungeonmaster.dto;

/** v2 payload for a single streamed narration chunk ({@code type = "narrative_chunk"}). */
public record NarrativeChunkPayload(String text) {}
