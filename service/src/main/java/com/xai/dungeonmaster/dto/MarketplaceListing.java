package com.xai.dungeonmaster.dto;

/**
 * One content pack available from the local and/or remote marketplace.
 *
 * @param source {@code local} (on-disk under content-packs) or {@code remote}
 *               (discovered via remote index JSON)
 * @param downloadUrl zip URL for remote packs; null for local directory packs
 */
public record MarketplaceListing(
        String id,
        String displayName,
        String version,
        String minEngineVersion,
        String description,
        boolean installed,
        boolean enabled,
        String sourcePath,
        String source,
        String downloadUrl
) {
    public static MarketplaceListing local(
            String id,
            String displayName,
            String version,
            String minEngineVersion,
            String description,
            boolean installed,
            boolean enabled,
            String sourcePath) {
        return new MarketplaceListing(
                id, displayName, version, minEngineVersion, description,
                installed, enabled, sourcePath, "local", null);
    }

    public static MarketplaceListing remote(
            String id,
            String displayName,
            String version,
            String minEngineVersion,
            String description,
            boolean installed,
            boolean enabled,
            String downloadUrl) {
        return new MarketplaceListing(
                id, displayName, version, minEngineVersion, description,
                installed, enabled, downloadUrl, "remote", downloadUrl);
    }
}
