package com.xai.dungeonmaster.dto;

/**
 * One content pack available from the local marketplace (content-packs dir).
 */
public record MarketplaceListing(
        String id,
        String displayName,
        String version,
        String minEngineVersion,
        String description,
        boolean installed,
        boolean enabled,
        String sourcePath
) {}
