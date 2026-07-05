package com.xai.dungeonmaster.dto;

import java.util.List;

/**
 * Payload for the {@code type = "catalog"} envelope: a snapshot of everything
 * installed and dispatchable in the running engine — content packs, plugins per
 * SPI, and the narration backend. This is the read model behind an in-game
 * content-pack / mod browser.
 */
public record CatalogPayload(
        List<PackInfo> contentPacks,
        PluginSummary plugins,
        NarrationInfo narration) {}
