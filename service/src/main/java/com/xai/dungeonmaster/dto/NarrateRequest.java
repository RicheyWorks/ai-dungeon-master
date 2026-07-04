package com.xai.dungeonmaster.dto;

/**
 * Request body for POST /v2/narrate.
 * Example JSON: { "prompt": "I search the altar for traps" }
 */
public record NarrateRequest(String prompt) {}
