package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.AdminAudit;
import com.xai.dungeonmaster.auth.RateLimitFilter;
import com.xai.dungeonmaster.auth.SecurityAudit;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.content.SessionPackService;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.dto.ErrorPayload;
import com.xai.dungeonmaster.dto.NarrationInfo;
import com.xai.dungeonmaster.entitlement.ReceiptLedger;
import com.xai.dungeonmaster.plugin.LLMProvider;
import com.xai.dungeonmaster.plugin.LLMProviderRegistry;
import com.xai.dungeonmaster.service.GameEngineFactory;
import com.xai.dungeonmaster.service.GameInstanceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final SessionService sessions;
    private final GameInstanceService instances;
    private final GameEngineFactory engineFactory;
    private final String adminToken;
    private final String previousAdminToken;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminController(
            ReceiptLedger ledger,
            SessionPackService sessionPacks,
            SessionService sessions,
            GameInstanceService instances,
            GameEngineFactory engineFactory,
            @Value("${game.admin.token:}") String adminToken,
            @Value("${game.admin.token.previous:}") String previousAdminToken) {
        this.ledger = ledger;
        this.sessionPacks = sessionPacks;
        this.sessions = sessions;
        this.instances = instances;
        this.engineFactory = engineFactory;
        this.adminToken = adminToken == null ? "" : adminToken.trim();
        this.previousAdminToken = previousAdminToken == null ? "" : previousAdminToken.trim();
    }

    /** Back-compat for receipt-only tests. */
    public AdminController(ReceiptLedger ledger, String adminToken) {
        this(ledger, null, null, null, null, adminToken, "");
    }

    /** Test helper with session packs + dual token. */
    public AdminController(ReceiptLedger ledger, SessionPackService sessionPacks, String adminToken) {
        this(ledger, sessionPacks, null, null, null, adminToken, "");
    }

    /** Test helper with dual admin tokens. */
    public AdminController(
            ReceiptLedger ledger,
            SessionPackService sessionPacks,
            String adminToken,
            String previousAdminToken) {
        this(ledger, sessionPacks, null, null, null, adminToken, previousAdminToken);
    }

    /** Test helper with sessions + engines. */
    public AdminController(
            ReceiptLedger ledger,
            SessionPackService sessionPacks,
            SessionService sessions,
            GameInstanceService instances,
            String adminToken) {
        this(ledger, sessionPacks, sessions, instances, null, adminToken, "");
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

    /**
     * List active sessions (identity only — no JWTs).
     * <pre>GET /v2/admin/sessions?limit=100</pre>
     */
    @GetMapping("/sessions")
    public ResponseEntity<Envelope<?>> listSessions(
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletRequest request) {
        ResponseEntity<Envelope<?>> denied = authorize(token, requestId, "/v2/admin/sessions", request);
        if (denied != null) return denied;
        if (sessions == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    Envelope.of("error", new ErrorPayload("Session service unavailable."), requestId));
        }
        int cap = Math.min(Math.max(limit, 1), 500);
        List<SessionService.Session> all = new ArrayList<>(sessions.allSessions());
        all.sort(Comparator.comparingLong(SessionService.Session::lastSeenEpoch).reversed());
        List<Map<String, Object>> items = new ArrayList<>();
        int i = 0;
        for (SessionService.Session s : all) {
            if (i++ >= cap) break;
            if (s == null || s.id() == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sessionId", s.id());
            row.put("displayName", s.displayName());
            row.put("createdAtEpochSeconds", s.createdAtEpoch());
            row.put("lastSeenEpochSeconds", s.lastSeenEpoch());
            row.put("hasEngine", instances != null && instances.hasSession(s.id()));
            items.add(row);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", items.size());
        payload.put("total", sessions.activeCount());
        payload.put("limit", cap);
        payload.put("sessions", items);
        AdminAudit.log("ok", "/v2/admin/sessions", RateLimitFilter.clientIp(request, false),
                requestId, "count=" + items.size() + " total=" + sessions.activeCount()
                        + " token=" + AdminAudit.tokenFingerprint(token));
        return ResponseEntity.ok(Envelope.of("admin.sessions", payload, requestId));
    }

    /**
     * Revoke a session (delete identity + destroy game engine). Client must re-auth.
     * <pre>DELETE /v2/admin/sessions/{sessionId}</pre>
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Envelope<?>> revokeSession(
            @PathVariable("sessionId") String sessionId,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletRequest request) {
        ResponseEntity<Envelope<?>> denied = authorize(token, requestId, "/v2/admin/sessions", request);
        if (denied != null) return denied;
        if (sessions == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    Envelope.of("error", new ErrorPayload("Session service unavailable."), requestId));
        }
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Envelope.of("error", new ErrorPayload("sessionId is required."), requestId));
        }
        String sid = sessionId.trim();
        boolean known = sessions.find(sid).isPresent();
        if (instances != null) {
            instances.destroy(sid, true);
        }
        sessions.delete(sid);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sid);
        payload.put("revoked", true);
        payload.put("existed", known);
        AdminAudit.log("ok", "/v2/admin/sessions/" + sid, RateLimitFilter.clientIp(request, false),
                requestId, "revoked existed=" + known + " token=" + AdminAudit.tokenFingerprint(token));
        return ResponseEntity.ok(Envelope.of("admin.session.revoked", payload, requestId));
    }

    /**
     * Purge idle sessions and optionally idle engines.
     * <pre>POST /v2/admin/sessions/purge-idle?idleTtlSeconds=86400&evictEngines=true</pre>
     */
    @PostMapping("/sessions/purge-idle")
    public ResponseEntity<Envelope<?>> purgeIdleSessions(
            @RequestParam(value = "idleTtlSeconds", defaultValue = "86400") long idleTtlSeconds,
            @RequestParam(value = "evictEngines", defaultValue = "true") boolean evictEngines,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletRequest request) {
        ResponseEntity<Envelope<?>> denied = authorize(token, requestId, "/v2/admin/sessions/purge-idle", request);
        if (denied != null) return denied;
        if (sessions == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    Envelope.of("error", new ErrorPayload("Session service unavailable."), requestId));
        }
        long ttl = Math.max(0L, idleTtlSeconds);
        int removedSessions = sessions.purgeIdle(ttl);
        int removedEngines = 0;
        if (evictEngines && instances != null) {
            removedEngines = instances.evictIdleWithTtl(ttl);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("idleTtlSeconds", ttl);
        payload.put("removedSessions", removedSessions);
        payload.put("removedEngines", removedEngines);
        payload.put("activeSessions", sessions.activeCount());
        payload.put("activeEngines", instances != null ? instances.sessionCount() : 0);
        AdminAudit.log("ok", "/v2/admin/sessions/purge-idle", RateLimitFilter.clientIp(request, false),
                requestId, "ttl=" + ttl + " sessions=" + removedSessions + " engines=" + removedEngines
                        + " token=" + AdminAudit.tokenFingerprint(token));
        return ResponseEntity.ok(Envelope.of("admin.sessions.purged", payload, requestId));
    }

    /**
     * Recent multi-tenant security audit events (process-local ring, newest first).
     *
     * <pre>GET /v2/admin/security-events?limit=50
     * Header: X-Admin-Token: …</pre>
     */
    @GetMapping("/security-events")
    public ResponseEntity<Envelope<?>> securityEvents(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletRequest request) {
        ResponseEntity<Envelope<?>> denied = authorize(token, requestId, "/v2/admin/security-events", request);
        if (denied != null) return denied;
        List<SecurityAudit.Event> events = SecurityAudit.recent(limit);
        List<Map<String, Object>> rows = new ArrayList<>(events.size());
        for (SecurityAudit.Event e : events) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.id());
            row.put("atEpochMs", e.atEpochMs());
            row.put("outcome", e.outcome());
            row.put("path", e.path());
            row.put("clientIp", e.clientIp());
            row.put("requestId", e.requestId());
            row.put("detail", e.detail());
            rows.add(row);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", rows.size());
        payload.put("limit", Math.min(200, Math.max(1, limit <= 0 ? 50 : limit)));
        payload.put("events", rows);
        AdminAudit.log("ok", "/v2/admin/security-events", RateLimitFilter.clientIp(request, false),
                requestId, "count=" + rows.size() + " token=" + AdminAudit.tokenFingerprint(token));
        return ResponseEntity.ok(Envelope.of("admin.security_events", payload, requestId));
    }

    /**
     * Recent admin ops audit events (process-local ring, newest first).
     *
     * <pre>GET /v2/admin/audit-events?limit=50</pre>
     */
    @GetMapping("/audit-events")
    public ResponseEntity<Envelope<?>> auditEvents(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletRequest request) {
        ResponseEntity<Envelope<?>> denied = authorize(token, requestId, "/v2/admin/audit-events", request);
        if (denied != null) return denied;
        List<AdminAudit.Event> events = AdminAudit.recent(limit);
        List<Map<String, Object>> rows = new ArrayList<>(events.size());
        for (AdminAudit.Event e : events) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.id());
            row.put("atEpochMs", e.atEpochMs());
            row.put("outcome", e.outcome());
            row.put("path", e.path());
            row.put("clientIp", e.clientIp());
            row.put("requestId", e.requestId());
            row.put("detail", e.detail());
            rows.add(row);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", rows.size());
        payload.put("limit", Math.min(200, Math.max(1, limit <= 0 ? 50 : limit)));
        payload.put("events", rows);
        // Do not AdminAudit.log this list call into the ring (noise); SLF4J still via authorize path only.
        return ResponseEntity.ok(Envelope.of("admin.audit_events", payload, requestId));
    }

    /**
     * Snapshot of the process-wide narration provider.
     *
     * <pre>GET /v2/admin/narration</pre>
     */
    @GetMapping("/narration")
    public ResponseEntity<Envelope<?>> getNarration(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletRequest request) {
        ResponseEntity<Envelope<?>> denied = authorize(token, requestId, "/v2/admin/narration", request);
        if (denied != null) return denied;
        NarrationInfo info = snapshotNarration();
        AdminAudit.log("ok", "/v2/admin/narration", RateLimitFilter.clientIp(request, false),
                requestId, "active=" + info.active() + " token=" + AdminAudit.tokenFingerprint(token));
        return ResponseEntity.ok(Envelope.of("admin.narration", info, requestId));
    }

    /**
     * Switch the process-wide active LLM narration provider (ops only).
     *
     * <pre>POST /v2/admin/narration/provider?id=local-stub</pre>
     */
    @PostMapping("/narration/provider")
    public ResponseEntity<Envelope<?>> setNarrationProvider(
            @RequestParam("id") String id,
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletRequest request) {
        ResponseEntity<Envelope<?>> denied = authorize(token, requestId, "/v2/admin/narration/provider", request);
        if (denied != null) return denied;
        String providerId = id == null ? "" : id.trim();
        if (providerId.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Envelope.of("error", new ErrorPayload("provider id required"), requestId));
        }
        boolean ok = LLMProviderRegistry.setActive(providerId);
        if (!ok) {
            AdminAudit.log("fail", "/v2/admin/narration/provider", RateLimitFilter.clientIp(request, false),
                    requestId, "unknown=" + providerId + " token=" + AdminAudit.tokenFingerprint(token));
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Envelope.of("error", new ErrorPayload("Unknown narration provider: " + providerId), requestId));
        }
        int rebound = 0;
        if (engineFactory != null && instances != null) {
            rebound = instances.rebindNarrators(engineFactory.buildNarrator());
        } else if (instances != null) {
            // Tests without factory: rebind raw active provider
            rebound = instances.rebindNarrators(LLMProviderRegistry.getActive());
        }
        NarrationInfo info = snapshotNarration();
        AdminAudit.log("ok", "/v2/admin/narration/provider", RateLimitFilter.clientIp(request, false),
                requestId, "active=" + info.active() + " engines=" + rebound
                        + " token=" + AdminAudit.tokenFingerprint(token));
        return ResponseEntity.ok(Envelope.of("admin.narration", info, requestId));
    }

    private static NarrationInfo snapshotNarration() {
        LLMProvider active = LLMProviderRegistry.getActive();
        List<String> available = new ArrayList<>(LLMProviderRegistry.registeredIds());
        available.sort(String.CASE_INSENSITIVE_ORDER);
        if (!available.contains(LLMProviderRegistry.FALLBACK_ID)
                && !available.stream().anyMatch(s -> s.equalsIgnoreCase(LLMProviderRegistry.FALLBACK_ID))) {
            available.add(0, LLMProviderRegistry.FALLBACK_ID);
            available.sort(String.CASE_INSENSITIVE_ORDER);
        }
        return new NarrationInfo(active.id(), active.health().name(), available);
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
