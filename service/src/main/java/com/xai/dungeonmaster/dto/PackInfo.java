package com.xai.dungeonmaster.dto;

/** One installed content pack in the {@code /v2/catalog} listing. */
public record PackInfo(
        String id,
        String displayName,
        String version,
        int monsters,
        int items) {}
