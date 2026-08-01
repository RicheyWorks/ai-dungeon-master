package com.xai.dungeonmaster.dto;

import java.util.List;

/** One content pack in the {@code /v2/catalog} listing, with its enabled state. */
public record PackInfo(
        String id,
        String displayName,
        String version,
        int monsters,
        int items,
        boolean enabled,
        List<String> requiredProductIds,
        boolean locked) {

    /** Backward-compatible ctor for free packs. */
    public PackInfo(
            String id,
            String displayName,
            String version,
            int monsters,
            int items,
            boolean enabled) {
        this(id, displayName, version, monsters, items, enabled, List.of(), false);
    }
}
