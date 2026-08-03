package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.RateLimitFilter;
import com.xai.dungeonmaster.auth.SecurityAudit;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.service.AuthDependencyProbe;
import com.xai.dungeonmaster.service.GameInstanceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Load-balancer health probes.
 *
 * <ul>
 *   <li>{@code GET /health} — liveness (always lean)</li>
 *   <li>{@code GET /health/ready} — readiness (lean by default; detail with scrape/admin token)</li>
 *   <li>{@code GET /v2/health} — versioned health (detail gated the same way)</li>
 * </ul>
 *
 * Unauthenticated callers only see status/probe (and uptime on v2). Session counts,
 * engine counts, dependency maps, and memory stats require {@code X-Metrics-Token},
 * Bearer metrics token, or {@code X-Admin-Token}.
 *
 * <p>Failed ops-token attempts (wrong {@code X-Metrics-Token} / {@code X-Admin-Token})
 * emit {@code security_audit} lines. Session JWT on {@code Authorization} is not treated
 * as a detail attempt.
 */
@RestController
public class HealthController {

    private final SessionService sessions;
    private final GameInstanceService instances;
    private final AuthDependencyProbe dependencies;
    private final String metricsScrapeToken;
    private final String adminToken;
    private final String previousAdminToken;
    private final long startedAtMs;

    @org.springframework.beans.factory.annotation.Autowired
    public HealthController(
            SessionService sessions,
            GameInstanceService instances,
            AuthDependencyProbe dependencies,
            @Value("${game.metrics.scrape-token:}") String metricsScrapeToken,
            @Value("${game.admin.token:}") String adminToken,
            @Value("${game.admin.token.previous:}") String previousAdminToken) {
        this.sessions = sessions;
        this.instances = instances;
        this.dependencies = dependencies;
        this.metricsScrapeToken = metricsScrapeToken == null ? "" : metricsScrapeToken.trim();
        this.adminToken = adminToken == null ? "" : adminToken.trim();
        this.previousAdminToken = previousAdminToken == null ? "" : previousAdminToken.trim();
        this.startedAtMs = ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    /** Test helper (no detail tokens). */
    public HealthController(
            SessionService sessions,
            GameInstanceService instances,
            AuthDependencyProbe dependencies) {
        this(sessions, instances, dependencies, "", "", "");
    }

    /** Liveness: 200 when the JVM is accepting HTTP. */
    @GetMapping("/health")
    public Map<String, Object> live() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("probe", "liveness");
        return body;
    }

    /**
     * Readiness: 200 when configured auth stores respond; 503 when a required
     * dependency is down (JDBC/Redis/file). Detail fields only with ops token.
     */
    @GetMapping({"/health/ready", "/ready"})
    public ResponseEntity<Map<String, Object>> ready(HttpServletRequest request) {
        AuthDependencyProbe.Result deps = dependencies.probe();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", deps.ready() ? "UP" : "DOWN");
        body.put("probe", "readiness");
        if (detailAllowed(request)) {
            body.put("sessions", sessions.activeCount());
            body.put("engines", instances.sessionCount());
            body.put("dependencies", deps.checks());
        } else {
            auditFailedDetailAttempt(request, pathOf(request, "/health/ready"), null);
        }
        HttpStatus code = deps.ready() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(code).body(body);
    }

    /** Versioned health. Public lean; ops token unlocks recon-style detail. */
    @GetMapping("/v2/health")
    public ResponseEntity<Envelope<Map<String, Object>>> healthV2(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            HttpServletRequest request) {
        AuthDependencyProbe.Result deps = dependencies.probe();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", deps.ready() ? "UP" : "DOWN");
        payload.put("uptimeSeconds", (System.currentTimeMillis() - startedAtMs) / 1000L);
        if (detailAllowed(request)) {
            payload.put("sessions", sessions.activeCount());
            payload.put("engines", instances.sessionCount());
            payload.put("dependencies", deps.checks());
            Runtime rt = Runtime.getRuntime();
            Map<String, Object> mem = new LinkedHashMap<>();
            mem.put("maxBytes", rt.maxMemory());
            mem.put("totalBytes", rt.totalMemory());
            mem.put("freeBytes", rt.freeMemory());
            payload.put("memory", mem);
            payload.put("detail", true);
        } else {
            payload.put("detail", false);
            auditFailedDetailAttempt(request, "/v2/health", requestId);
        }
        HttpStatus code = deps.ready() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(code).body(Envelope.of("health", payload, requestId));
    }

    private boolean detailAllowed(HttpServletRequest request) {
        if (request == null) return false;
        String metricsHeader = request.getHeader("X-Metrics-Token");
        if (metricsHeader != null && tokenOk(metricsScrapeToken, metricsHeader.trim())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)
                && tokenOk(metricsScrapeToken, auth.substring(7).trim())) {
            return true;
        }
        String admin = request.getHeader("X-Admin-Token");
        if (admin != null) {
            String t = admin.trim();
            if (tokenOk(adminToken, t) || tokenOk(previousAdminToken, t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Audit only explicit ops-token attempts that failed (not plain public probes,
     * and not session JWT on {@code Authorization}).
     */
    private void auditFailedDetailAttempt(HttpServletRequest request, String path, String requestId) {
        if (request == null) return;
        String metricsHeader = request.getHeader("X-Metrics-Token");
        if (metricsHeader != null && !metricsHeader.isBlank()
                && !tokenOk(metricsScrapeToken, metricsHeader.trim())) {
            SecurityAudit.log(
                    "unauthorized",
                    path,
                    RateLimitFilter.clientIp(request, false),
                    requestId,
                    "bad_metrics_token");
            return;
        }
        String admin = request.getHeader("X-Admin-Token");
        if (admin != null && !admin.isBlank()
                && !tokenOk(adminToken, admin.trim())
                && !tokenOk(previousAdminToken, admin.trim())) {
            SecurityAudit.log(
                    "unauthorized",
                    path,
                    RateLimitFilter.clientIp(request, false),
                    requestId,
                    "bad_admin_token");
        }
    }

    private static String pathOf(HttpServletRequest request, String fallback) {
        if (request == null) return fallback;
        String uri = request.getRequestURI();
        return uri == null || uri.isBlank() ? fallback : uri;
    }

    private static boolean tokenOk(String expected, String actual) {
        if (expected == null || expected.isEmpty() || actual == null) return false;
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            MessageDigest.isEqual(a, a);
            return false;
        }
        return MessageDigest.isEqual(a, b);
    }
}
