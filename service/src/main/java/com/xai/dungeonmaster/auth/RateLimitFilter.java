package com.xai.dungeonmaster.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window per-IP rate limits for public / abuse-prone endpoints.
 *
 * <ul>
 *   <li>{@code POST /v2/session} — session minting</li>
 *   <li>{@code GET /metrics} — Prometheus scrapes (generous default)</li>
 *   <li>{@code POST /v2/entitlements/verify} — receipt verification</li>
 * </ul>
 *
 * Disabled when {@code game.rate-limit.enabled=false}. Returns 429 +
 * {@code Retry-After} when a bucket is exhausted.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final int sessionPerMinute;
    private final int metricsPerMinute;
    private final int verifyPerMinute;
    private final int defaultPerMinute;

    /** key → window */
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${game.rate-limit.enabled:true}") boolean enabled,
            @Value("${game.rate-limit.session-per-minute:30}") int sessionPerMinute,
            @Value("${game.rate-limit.metrics-per-minute:120}") int metricsPerMinute,
            @Value("${game.rate-limit.verify-per-minute:60}") int verifyPerMinute,
            @Value("${game.rate-limit.default-per-minute:120}") int defaultPerMinute) {
        this.enabled = enabled;
        this.sessionPerMinute = Math.max(1, sessionPerMinute);
        this.metricsPerMinute = Math.max(1, metricsPerMinute);
        this.verifyPerMinute = Math.max(1, verifyPerMinute);
        this.defaultPerMinute = Math.max(1, defaultPerMinute);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) return true;
        return limitFor(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Limit limit = limitFor(req);
        if (limit == null) {
            chain.doFilter(req, res);
            return;
        }
        String ip = clientIp(req);
        String key = limit.bucket + "|" + ip;
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStartMs >= 60_000L) {
                return new Window(now, new AtomicInteger(0));
            }
            return existing;
        });
        int n = w.count.incrementAndGet();
        if (n > limit.maxPerMinute) {
            long retryAfterSec = Math.max(1L, (60_000L - (now - w.windowStartMs) + 999) / 1000L);
            res.setStatus(429);
            res.setHeader("Retry-After", Long.toString(retryAfterSec));
            res.setHeader("X-RateLimit-Limit", Integer.toString(limit.maxPerMinute));
            res.setHeader("X-RateLimit-Remaining", "0");
            res.setContentType("application/json");
            String requestId = safeRequestId(req);
            res.getWriter().write("{\"type\":\"error\",\"version\":1,\"payload\":{\"message\":"
                    + "\"Rate limit exceeded. Retry after " + retryAfterSec + "s.\"},"
                    + "\"requestId\":\"" + requestId + "\"}");
            return;
        }
        res.setHeader("X-RateLimit-Limit", Integer.toString(limit.maxPerMinute));
        res.setHeader("X-RateLimit-Remaining", Integer.toString(Math.max(0, limit.maxPerMinute - n)));
        chain.doFilter(req, res);

        // opportunistic prune of stale windows
        if (windows.size() > 10_000) {
            prune(now);
        }
    }

    private Limit limitFor(HttpServletRequest req) {
        String path = req.getRequestURI();
        if (path == null) return null;
        String method = req.getMethod() == null ? "" : req.getMethod().toUpperCase(Locale.ROOT);
        if ("POST".equals(method) && path.equals("/v2/session")) {
            return new Limit("session", sessionPerMinute);
        }
        if ("GET".equals(method) && path.equals("/metrics")) {
            return new Limit("metrics", metricsPerMinute);
        }
        if ("POST".equals(method) && path.equals("/v2/entitlements/verify")) {
            return new Limit("verify", verifyPerMinute);
        }
        // optional catch-all for /v2 when needed — leave off for game traffic
        return null;
    }

    static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            String first = (comma < 0 ? xff : xff.substring(0, comma)).trim();
            if (!first.isEmpty()) return first;
        }
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        String remote = req.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    private void prune(long now) {
        windows.entrySet().removeIf(e -> now - e.getValue().windowStartMs > 120_000L);
    }

    private static String safeRequestId(HttpServletRequest req) {
        String id = req.getHeader("X-Request-Id");
        if (id == null || id.isBlank()) return "";
        return id.replace("\"", "").replace("\\", "");
    }

    private record Limit(String bucket, int maxPerMinute) {}

    private static final class Window {
        final long windowStartMs;
        final AtomicInteger count;

        Window(long windowStartMs, AtomicInteger count) {
            this.windowStartMs = windowStartMs;
            this.count = count;
        }
    }
}
