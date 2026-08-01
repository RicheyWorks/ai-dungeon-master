package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.service.AuthDependencyProbe;
import com.xai.dungeonmaster.service.GameInstanceService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Locale;
import java.util.Map;

/**
 * Prometheus text exposition ({@code GET /metrics}) for load-balancer scrapers
 * and Grafana. Public, no auth — scrape from a private network or gateway.
 */
@RestController
public class MetricsController {

    private final SessionService sessions;
    private final GameInstanceService instances;
    private final AuthDependencyProbe dependencies;
    private final long startedAtMs;

    public MetricsController(
            SessionService sessions,
            GameInstanceService instances,
            AuthDependencyProbe dependencies) {
        this.sessions = sessions;
        this.instances = instances;
        this.dependencies = dependencies;
        this.startedAtMs = ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> metrics() {
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

        Runtime rt = Runtime.getRuntime();
        helpType(out, "jvm_threads_live", "gauge", "Approximate live thread count");
        sample(out, "jvm_threads_live", Thread.activeCount());

        helpType(out, "process_cpu_available_processors", "gauge", "Runtime.availableProcessors()");
        sample(out, "process_cpu_available_processors", rt.availableProcessors());

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                .body(out.toString());
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
