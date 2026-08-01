package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.dto.Envelope;
import com.xai.dungeonmaster.service.AuthDependencyProbe;
import com.xai.dungeonmaster.service.GameInstanceService;
import org.springframework.http.HttpStatus;
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
 *   <li>{@code GET /health/ready} — readiness (auth backends reachable)</li>
 *   <li>{@code GET /v2/health} — richer metrics + dependency checks</li>
 * </ul>
 */
@RestController
public class HealthController {

    private final SessionService sessions;
    private final GameInstanceService instances;
    private final AuthDependencyProbe dependencies;
    private final long startedAtMs;

    public HealthController(
            SessionService sessions,
            GameInstanceService instances,
            AuthDependencyProbe dependencies) {
        this.sessions = sessions;
        this.instances = instances;
        this.dependencies = dependencies;
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
     * Readiness: 200 when configured auth stores respond; 503 when a required
     * dependency is down (JDBC/Redis/file).
     */
    @GetMapping({"/health/ready", "/ready"})
    public ResponseEntity<Map<String, Object>> ready() {
        AuthDependencyProbe.Result deps = dependencies.probe();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", deps.ready() ? "UP" : "DOWN");
        body.put("probe", "readiness");
        body.put("sessions", sessions.activeCount());
        body.put("engines", instances.sessionCount());
        body.put("dependencies", deps.checks());
        HttpStatus code = deps.ready() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(code).body(body);
    }

    /** Versioned health + metrics + dependency snapshot (public). */
    @GetMapping("/v2/health")
    public ResponseEntity<Envelope<Map<String, Object>>> healthV2(
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        AuthDependencyProbe.Result deps = dependencies.probe();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", deps.ready() ? "UP" : "DOWN");
        payload.put("uptimeSeconds", (System.currentTimeMillis() - startedAtMs) / 1000L);
        payload.put("sessions", sessions.activeCount());
        payload.put("engines", instances.sessionCount());
        payload.put("dependencies", deps.checks());
        Runtime rt = Runtime.getRuntime();
        Map<String, Object> mem = new LinkedHashMap<>();
        mem.put("maxBytes", rt.maxMemory());
        mem.put("totalBytes", rt.totalMemory());
        mem.put("freeBytes", rt.freeMemory());
        payload.put("memory", mem);
        HttpStatus code = deps.ready() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(code).body(Envelope.of("health", payload, requestId));
    }
}
