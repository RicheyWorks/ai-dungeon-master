package com.xai.dungeonmaster.auth;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-local counters for rate-limit outcomes, scraped via {@code GET /metrics}.
 */
@Component
public class RateLimitMetrics {

    private final ConcurrentHashMap<String, AtomicLong> rejected = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> allowed = new ConcurrentHashMap<>();

    public void rejected(String bucket) {
        bump(rejected, bucket);
    }

    public void allowed(String bucket) {
        bump(allowed, bucket);
    }

    /** Snapshot of rejected totals by bucket (stable order). */
    public Map<String, Long> rejectedSnapshot() {
        return snapshot(rejected);
    }

    /** Snapshot of allowed totals by bucket (stable order). */
    public Map<String, Long> allowedSnapshot() {
        return snapshot(allowed);
    }

    private static void bump(ConcurrentHashMap<String, AtomicLong> map, String bucket) {
        String b = (bucket == null || bucket.isBlank()) ? "unknown" : bucket.trim();
        map.computeIfAbsent(b, k -> new AtomicLong()).incrementAndGet();
    }

    private static Map<String, Long> snapshot(ConcurrentHashMap<String, AtomicLong> map) {
        Map<String, Long> out = new LinkedHashMap<>();
        map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.put(e.getKey(), e.getValue().get()));
        return out;
    }
}
