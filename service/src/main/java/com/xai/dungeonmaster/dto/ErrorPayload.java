package com.xai.dungeonmaster.dto;

/** Payload for v2 error envelopes ({@code type = "error"}). */
public record ErrorPayload(String message) {}
