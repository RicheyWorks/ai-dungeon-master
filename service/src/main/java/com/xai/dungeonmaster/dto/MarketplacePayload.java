package com.xai.dungeonmaster.dto;

import java.util.List;

/** Payload for {@code GET /v2/marketplace}. */
public record MarketplacePayload(
        String root,
        String remoteIndexUrl,
        boolean remoteOk,
        String remoteError,
        int available,
        int installed,
        List<MarketplaceListing> packs
) {}
