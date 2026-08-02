package com.xai.dungeonmaster.entitlement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
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

    /**
     * Ops inventory query. Null/blank filters are ignored. Limit is clamped 1..500.
     */
    record ReceiptQuery(
            int limit,
            String productId,
            String storefront,
            String sessionId,
            Long sinceEpochMs,
            Long untilEpochMs
    ) {
        public ReceiptQuery {
            limit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 500));
            productId = blankToNull(productId);
            storefront = blankToNull(storefront);
            sessionId = blankToNull(sessionId);
        }

        public static ReceiptQuery ofLimit(int limit) {
            return new ReceiptQuery(limit, null, null, null, null, null);
        }

        private static String blankToNull(String s) {
            if (s == null) return null;
            String t = s.trim();
            return t.isEmpty() ? null : t;
        }

        public boolean matches(RedeemRecord r) {
            if (r == null) return false;
            if (productId != null && !productId.equals(r.productId())) return false;
            if (storefront != null && !storefront.equalsIgnoreCase(
                    r.storefront() == null ? "" : r.storefront())) return false;
            if (sessionId != null && !sessionId.equals(r.sessionId())) return false;
            if (sinceEpochMs != null && r.redeemedAtEpochMs() < sinceEpochMs) return false;
            if (untilEpochMs != null && r.redeemedAtEpochMs() > untilEpochMs) return false;
            return true;
        }
    }

    Optional<RedeemRecord> find(String fingerprint);

    void record(RedeemRecord record);

    /**
     * Recent redeems newest-first (ops inventory). Default empty when the
     * backend cannot enumerate.
     */
    default List<RedeemRecord> listRecent(int limit) {
        return list(ReceiptQuery.ofLimit(limit));
    }

    /**
     * Filtered inventory, newest-first. Default delegates to unfiltered
     * {@link #listRecent(int)} and applies {@link ReceiptQuery#matches} in memory.
     */
    default List<RedeemRecord> list(ReceiptQuery query) {
        ReceiptQuery q = query == null ? ReceiptQuery.ofLimit(50) : query;
        // Over-fetch when filters present so limit applies after filtering.
        int fetch = (q.productId() != null || q.storefront() != null
                || q.sessionId() != null || q.sinceEpochMs() != null || q.untilEpochMs() != null)
                ? Math.min(500, Math.max(q.limit() * 10, q.limit()))
                : q.limit();
        return listRecentUnfiltered(fetch).stream()
                .filter(q::matches)
                .limit(q.limit())
                .toList();
    }

    /**
     * Backend-specific unfiltered recent list. Prefer overriding {@link #list}
     * when the store can push filters into the query (e.g. JDBC).
     */
    default List<RedeemRecord> listRecentUnfiltered(int limit) {
        return List.of();
    }

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
