package com.xai.dungeonmaster.dto;

import java.util.List;

/**
 * Payload for entitlement envelopes. On {@code /verify}, {@code granted}/{@code reason}
 * describe the attempt; {@code owned} always lists every product the session holds.
 */
public record EntitlementPayload(
        boolean granted,
        String productId,
        String storefront,
        String reason,
        List<String> owned) {}
