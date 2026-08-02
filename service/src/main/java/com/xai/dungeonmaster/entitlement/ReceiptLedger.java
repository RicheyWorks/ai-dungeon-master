package com.xai.dungeonmaster.entitlement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * One-time purchase-token ledger. Fingerprints are SHA-256 over
 * {@code storefront + '\\n' + productId + '\\n' + receipt} so the same store
 * payload cannot grant a second player (or a second product).
 */
public interface ReceiptLedger {

    record RedeemRecord(
            String fingerprint,
            String sessionId,
            String productId,
            String storefront,
            long redeemedAtEpochMs
    ) {}

    Optional<RedeemRecord> find(String fingerprint);

    void record(RedeemRecord record);

    static String fingerprint(String storefront, String productId, String receipt) {
        String sf = storefront == null ? "" : storefront.trim().toLowerCase(Locale.ROOT);
        String pid = productId == null ? "" : productId.trim();
        String body = receipt == null ? "" : receipt.trim();
        String material = sf + "\n" + pid + "\n" + body;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            // Extremely unlikely — fall back to a weak stable form
            return Integer.toHexString(material.hashCode());
        }
    }
}
