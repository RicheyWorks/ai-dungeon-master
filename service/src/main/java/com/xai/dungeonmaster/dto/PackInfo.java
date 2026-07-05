package com.xai.dungeonmaster.dto;

/** One content pack in the {@code /v2/catalog} listing, with its enabled state. */
public record PackInfo(
        String id,
        String displayName,
        String version,
        int monsters,
        int items,
        boolean enabled) {}
