package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.AdminAudit;
import com.xai.dungeonmaster.auth.RateLimitFilter;
import com.xai.dungeonmaster.content.SessionPackService;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.dto.ErrorPayload;
import com.xai.dungeonmaster.entitlement.ReceiptLedger;
import jakarta.servlet.http.HttpServletRequest;
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
 * {@code game.admin.token} (or {@code game.admin.token.previous} during rotation).
 * Disabled when the primary token is blank.
 */
@RestController
@RequestMapping("/v2/admin")
public class AdminController {

    private final ReceiptLedger ledger;
    private final SessionPackService sessionPacks;
    private final String adminToken;
    private final String previousAdminToken;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminController(
            ReceiptLedger ledger,
            SessionPackService sessionPacks,
            @Value("${game.admin.token:}") String adminToken,
            @Value("${game.admin.token.previous:}") String previousAdminToken) {
        this.ledger = ledger;
        this.sessionPacks = sessionPacks;
        this.adminToken = adminToken == null ? "" : adminToken.trim();
        this.previousAdminToken = previousAdminToken == null ? "" : previousAdminToken.trim();
    }

    /** Back-compat for receipt-only tests. */
    public AdminController(ReceiptLedger ledger, String adminToken) {
        this(ledger, null, adminToken, "");
    }

    /** Test helper with session packs + dual token. */
    public AdminController(ReceiptLedger ledger, SessionPackService sessionPacks, String adminToken) {
        this(ledger, sessionPacks, adminToken, "");
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
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletRequest request) {

        ResponseEntity<Envelope<?>> denied = authorize(token, requestId, "/v2/admin/receipts", request);
        if (denied != null) return denied;

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
        AdminAudit.log("ok", "/v2/admin/receipts", RateLimitFilter.clientIp(request, false),
                requestId, "count=" + items.size() + " token=" + AdminAudit.tokenFingerprint(token));
        return ResponseEntity.ok(Envelope.of("admin.receipts", payload, requestId));
    }

    /**
     * List pack enable overrides for a session.
     * <pre>GET /v2/admin/session-packs?sessionId=…</pre>
     */
    @GetMapping("/session-packs")
    public ResponseEntity<Envelope<?>> listSessionPacks(
            @RequestParam("sessionId") String sessionId,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletRequest request) {
        ResponseEntity<Envelope<?>> denied = authorize(token, requestId, "/v2/admin/session-packs", request);
        if (denied != null) return denied;
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Envelope.of("error", new ErrorPayload("sessionId is required."), requestId));
        }
        if (sessionPacks == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    Envelope.of("error", new ErrorPayload("Session pack service unavailable."), requestId));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        String sid = sessionId.trim();
        payload.put("sessionId", sid);
        payload.put("enabledPackIds", List.copyOf(sessionPacks.enabledPackIds(sid)));
        payload.put("overrides", sessionPacks.overrides(sid));
        payload.put("sessionScoped", sessionPacks.isSessionScoped());
        AdminAudit.log("ok", "/v2/admin/session-packs", RateLimitFilter.clientIp(request, false),
                requestId, "sessionId=" + sid + " token=" + AdminAudit.tokenFingerprint(token));
        return ResponseEntity.ok(Envelope.of("admin.session-packs", payload, requestId));
    }

    private ResponseEntity<Envelope<?>> authorize(
            String token, String requestId, String path, HttpServletRequest request) {
        String ip = request == null ? "-" : RateLimitFilter.clientIp(request, false);
        if (adminToken.isEmpty()) {
            AdminAudit.log("disabled", path, ip, requestId, "token=" + AdminAudit.tokenFingerprint(token));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Envelope.of("error", new ErrorPayload("Admin API disabled (game.admin.token not set)."), requestId));
        }
        if (token == null || !tokenAccepted(token.trim())) {
            AdminAudit.log("unauthorized", path, ip, requestId, "token=" + AdminAudit.tokenFingerprint(token));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Envelope.of("error", new ErrorPayload("Invalid or missing X-Admin-Token."), requestId));
        }
        return null;
    }

    private boolean tokenAccepted(String presented) {
        if (constantTimeEquals(adminToken, presented)) return true;
        return !previousAdminToken.isEmpty() && constantTimeEquals(previousAdminToken, presented);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            MessageDigest.isEqual(a, a);
            return false;
        }
        return MessageDigest.isEqual(a, b);
    }
}
