package com.xai.dungeonmaster.dto;

import java.util.List;

/**
 * Payload for entitlement envelopes. On {@code /verify}, {@code granted}/{@code reason}
 * describe the attempt; {@code owned} always lists every product the session holds.
 * {@code enabledPacks} lists content packs auto-enabled because of this grant (if any).
 */
public record EntitlementPayload(
        boolean granted,
        String productId,
        String storefront,
        String reason,
        List<String> owned,
        List<String> enabledPacks) {

    /** Backward-compatible ctor (no auto-enable list). */
    public EntitlementPayload(
            boolean granted,
            String productId,
            String storefront,
            String reason,
            List<String> owned) {
        this(granted, productId, storefront, reason, owned, List.of());
    }
}
