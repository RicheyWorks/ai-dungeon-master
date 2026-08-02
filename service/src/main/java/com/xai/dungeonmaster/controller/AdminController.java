package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.dto.ErrorPayload;
import com.xai.dungeonmaster.entitlement.ReceiptLedger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ops inventory endpoints. Protected by {@code X-Admin-Token} matching
 * {@code game.admin.token} (disabled when blank).
 */
@RestController
@RequestMapping("/v2/admin")
public class AdminController {

    private final ReceiptLedger ledger;
    private final String adminToken;

    public AdminController(
            ReceiptLedger ledger,
            @Value("${game.admin.token:}") String adminToken) {
        this.ledger = ledger;
        this.adminToken = adminToken == null ? "" : adminToken.trim();
    }

    /**
     * List recent redeemed purchase receipts (fingerprints only — never raw receipts).
     *
     * <pre>GET /v2/admin/receipts?limit=50&productId=sku&storefront=dev&sessionId=…&since=…&until=…
     * Header: X-Admin-Token: <game.admin.token></pre>
     *
     * {@code since}/{@code until} accept epoch milliseconds.
     */
    @GetMapping("/receipts")
    public ResponseEntity<Envelope<?>> listReceipts(
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "productId", required = false) String productId,
            @RequestParam(value = "storefront", required = false) String storefront,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "since", required = false) Long since,
            @RequestParam(value = "until", required = false) Long until,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        if (adminToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Envelope.of("error", new ErrorPayload("Admin API disabled (game.admin.token not set)."), requestId));
        }
        if (token == null || !constantTimeEquals(adminToken, token.trim())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Envelope.of("error", new ErrorPayload("Invalid or missing X-Admin-Token."), requestId));
        }

        ReceiptLedger.ReceiptQuery query = new ReceiptLedger.ReceiptQuery(
                limit, productId, storefront, sessionId, since, until);
        List<ReceiptLedger.RedeemRecord> rows = ledger.list(query);
        List<Map<String, Object>> items = new ArrayList<>(rows.size());
        for (ReceiptLedger.RedeemRecord r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fingerprint", r.fingerprint());
            row.put("sessionId", r.sessionId());
            row.put("productId", r.productId());
            row.put("storefront", r.storefront());
            row.put("redeemedAtEpochMs", r.redeemedAtEpochMs());
            items.add(row);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", items.size());
        payload.put("limit", query.limit());
        if (productId != null && !productId.isBlank()) payload.put("productId", productId.trim());
        if (storefront != null && !storefront.isBlank()) payload.put("storefront", storefront.trim());
        if (sessionId != null && !sessionId.isBlank()) payload.put("sessionId", sessionId.trim());
        if (since != null) payload.put("since", since);
        if (until != null) payload.put("until", until);
        payload.put("receipts", items);
        return ResponseEntity.ok(Envelope.of("admin.receipts", payload, requestId));
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        // MessageDigest.isEqual is constant-time for equal-length arrays.
        if (a.length != b.length) {
            // still compare to avoid leaking length via timing on short paths
            MessageDigest.isEqual(a, a);
            return false;
        }
        return MessageDigest.isEqual(a, b);
    }
}
