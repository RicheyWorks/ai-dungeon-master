package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.service.GameInstanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Load-balancer health probes (public, no auth).
 *
 * <ul>
 *   <li>{@code GET /health} — liveness (process up)</li>
 *   <li>{@code GET /health/ready} — readiness (can serve traffic)</li>
 *   <li>{@code GET /v2/health} — richer metrics envelope for dashboards</li>
 * </ul>
 */
@RestController
public class HealthController {

    private final SessionService sessions;
    private final GameInstanceService instances;
    private final long startedAtMs;

    public HealthController(SessionService sessions, GameInstanceService instances) {
        this.sessions = sessions;
        this.instances = instances;
        this.startedAtMs = ManagementFactory.getRuntimeMXBean().getStartTime();
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
     * Readiness: 200 when the process can handle game traffic.
     * Currently always ready after Spring context start; extend later for
     * Redis/JDBC ping if desired.
     */
    @GetMapping({"/health/ready", "/ready"})
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("probe", "readiness");
        body.put("sessions", sessions.activeCount());
        body.put("engines", instances.sessionCount());
        return ResponseEntity.ok(body);
    }

    /** Versioned health + lightweight metrics (public). */
    @GetMapping("/v2/health")
    public Envelope<Map<String, Object>> healthV2(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "UP");
        payload.put("uptimeSeconds", (System.currentTimeMillis() - startedAtMs) / 1000L);
        payload.put("sessions", sessions.activeCount());
        payload.put("engines", instances.sessionCount());
        Runtime rt = Runtime.getRuntime();
        Map<String, Object> mem = new LinkedHashMap<>();
        mem.put("maxBytes", rt.maxMemory());
        mem.put("totalBytes", rt.totalMemory());
        mem.put("freeBytes", rt.freeMemory());
        payload.put("memory", mem);
        return Envelope.of("health", payload, requestId);
    }
}
