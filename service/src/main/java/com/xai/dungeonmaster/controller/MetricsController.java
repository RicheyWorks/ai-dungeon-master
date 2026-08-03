package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.RateLimitFilter;
import com.xai.dungeonmaster.auth.RateLimitMetrics;
import com.xai.dungeonmaster.auth.SecurityAudit;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.service.AuthDependencyProbe;
import com.xai.dungeonmaster.service.GameInstanceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;

/**
 * Prometheus text exposition ({@code GET /metrics}).
 *
 * <p>When {@code game.metrics.scrape-token} is set, scrapers must send
 * {@code Authorization: Bearer <token>} or {@code X-Metrics-Token: <token>}.
 * When blank, the endpoint stays open for private-network scrapes (dev default).
 */
@RestController
public class MetricsController {

    private final SessionService sessions;
    private final GameInstanceService instances;
    private final AuthDependencyProbe dependencies;
    private final RateLimitMetrics rateLimits;
    private final String scrapeToken;
    private final long startedAtMs;

    @org.springframework.beans.factory.annotation.Autowired
    public MetricsController(
            SessionService sessions,
            GameInstanceService instances,
            AuthDependencyProbe dependencies,
            RateLimitMetrics rateLimits,
            @Value("${game.metrics.scrape-token:}") String scrapeToken) {
        this.sessions = sessions;
        this.instances = instances;
        this.dependencies = dependencies;
        this.rateLimits = rateLimits;
        this.scrapeToken = scrapeToken == null ? "" : scrapeToken.trim();
        this.startedAtMs = ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    /** Test helper (open metrics). */
    public MetricsController(
            SessionService sessions,
            GameInstanceService instances,
            AuthDependencyProbe dependencies,
            RateLimitMetrics rateLimits) {
        this(sessions, instances, dependencies, rateLimits, "");
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> metrics(HttpServletRequest request) {
        if (!scrapeToken.isEmpty() && !tokenMatches(request)) {
            SecurityAudit.log(
                    "unauthorized",
                    "/metrics",
                    RateLimitFilter.clientIp(request, false),
                    request != null ? request.getHeader("X-Request-Id") : null,
                    "scrape_token_required");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("unauthorized\n");
        }
        StringBuilder out = new StringBuilder(1024);
        AuthDependencyProbe.Result deps = dependencies.probe();
        long uptimeSec = Math.max(0L, (System.currentTimeMillis() - startedAtMs) / 1000L);

        helpType(out, "dm_up", "gauge", "1 if this process is serving (always 1 when scraped)");
        sample(out, "dm_up", 1);

        helpType(out, "dm_ready", "gauge", "1 if configured auth backends are healthy");
        sample(out, "dm_ready", deps.ready() ? 1 : 0);

        helpType(out, "dm_uptime_seconds", "gauge", "Process uptime in seconds");
        sample(out, "dm_uptime_seconds", uptimeSec);

        helpType(out, "dm_sessions_active", "gauge", "Active guest/auth sessions in the session store");
        sample(out, "dm_sessions_active", sessions.activeCount());

        helpType(out, "dm_engines_active", "gauge", "Live per-session game engines (excludes default)");
        sample(out, "dm_engines_active", instances.sessionCount());

        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memBean.getNonHeapMemoryUsage();

        helpType(out, "jvm_memory_bytes", "gauge", "JVM memory in bytes");
        sampleLabeled(out, "jvm_memory_bytes", "area=\"heap\",id=\"used\"", heap.getUsed());
        sampleLabeled(out, "jvm_memory_bytes", "area=\"heap\",id=\"committed\"", heap.getCommitted());
        sampleLabeled(out, "jvm_memory_bytes", "area=\"heap\",id=\"max\"", Math.max(0, heap.getMax()));
        sampleLabeled(out, "jvm_memory_bytes", "area=\"nonheap\",id=\"used\"", nonHeap.getUsed());
        sampleLabeled(out, "jvm_memory_bytes", "area=\"nonheap\",id=\"committed\"", nonHeap.getCommitted());

        helpType(out, "dm_dependency_up", "gauge",
                "1 if dependency is UP, 0 if DOWN, absent when NOT_CONFIGURED");
        for (Map.Entry<String, Object> e : deps.checks().entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> check = (Map<String, Object>) e.getValue();
            Object status = check.get("status");
            if (status == null || "NOT_CONFIGURED".equals(status.toString())) {
                continue;
            }
            int up = "UP".equals(status.toString()) ? 1 : 0;
            sampleLabeled(out, "dm_dependency_up", "name=\"" + escapeLabel(e.getKey()) + "\"", up);
        }

        helpType(out, "dm_rate_limit_rejected_total", "counter",
                "Requests rejected by rate limiting (HTTP 429 or STOMP narrate deny)");
        for (Map.Entry<String, Long> e : rateLimits.rejectedSnapshot().entrySet()) {
            sampleLabeled(out, "dm_rate_limit_rejected_total",
                    "bucket=\"" + escapeLabel(e.getKey()) + "\"", e.getValue());
        }
        helpType(out, "dm_rate_limit_allowed_total", "counter",
                "Requests that passed a rate-limit bucket check");
        for (Map.Entry<String, Long> e : rateLimits.allowedSnapshot().entrySet()) {
            sampleLabeled(out, "dm_rate_limit_allowed_total",
                    "bucket=\"" + escapeLabel(e.getKey()) + "\"", e.getValue());
        }

        Runtime rt = Runtime.getRuntime();
        helpType(out, "jvm_threads_live", "gauge", "Approximate live thread count");
        sample(out, "jvm_threads_live", Thread.activeCount());

        helpType(out, "process_cpu_available_processors", "gauge", "Runtime.availableProcessors()");
        sample(out, "process_cpu_available_processors", rt.availableProcessors());

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(out.toString());
    }

    private boolean tokenMatches(HttpServletRequest request) {
        if (request == null) return false;
        String header = request.getHeader("X-Metrics-Token");
        if (header != null && constantTimeEquals(scrapeToken, header.trim())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return constantTimeEquals(scrapeToken, auth.substring(7).trim());
        }
        return false;
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

    private static void helpType(StringBuilder out, String name, String type, String help) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n');
        out.append("# TYPE ").append(name).append(' ').append(type).append('\n');
    }

    private static void sample(StringBuilder out, String name, long value) {
        out.append(name).append(' ').append(value).append('\n');
    }

    private static void sample(StringBuilder out, String name, int value) {
        out.append(name).append(' ').append(value).append('\n');
    }

    private static void sampleLabeled(StringBuilder out, String name, String labels, long value) {
        out.append(name).append('{').append(labels).append("} ").append(value).append('\n');
    }

    private static String escapeLabel(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "");
    }
}
