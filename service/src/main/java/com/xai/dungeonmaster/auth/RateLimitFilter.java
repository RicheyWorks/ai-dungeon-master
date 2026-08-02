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
import java.util.regex.Pattern;

/**
 * Fixed-window per-IP rate limits for public / abuse-prone endpoints.
 *
 * <ul>
 *   <li>{@code POST /v2/session} — session minting</li>
 *   <li>{@code DELETE /v2/session} — logout</li>
 *   <li>{@code /v2/admin/**} — admin token brute-force protection</li>
 *   <li>{@code POST /v2/marketplace/{id}/install} — marketplace pack install</li>
 *   <li>{@code POST /v2/catalog/packs} — direct pack zip upload</li>
 *   <li>{@code POST /v2/narrate} — LLM narration (HTTP; STOMP uses {@link NarrationRateGuard})</li>
 *   <li>{@code POST /v2/action} — player actions (HTTP; STOMP uses {@link ActionRateGuard})</li>
 *   <li>{@code GET /metrics} — Prometheus scrapes (generous default)</li>
 *   <li>{@code POST /v2/entitlements/verify} — receipt verification</li>
 * </ul>
 *
 * Counters come from {@link RateLimitStore} — process-local memory or shared
 * Redis ({@code game.rate-limit.store=redis}) for multi-node clusters.
 * Disabled when {@code game.rate-limit.enabled=false}. Returns 429 +
 * {@code Retry-After} when a bucket is exhausted.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern MARKETPLACE_INSTALL =
            Pattern.compile("^/v2/marketplace/[^/]+/install$");

    private final boolean enabled;
    private final int sessionPerMinute;
    private final int logoutPerMinute;
    private final int adminPerMinute;
    private final int installPerMinute;
    private final int narratePerMinute;
    private final int actionPerMinute;
    private final int metricsPerMinute;
    private final int verifyPerMinute;
    private final RateLimitStore store;
    private final RateLimitMetrics metrics;

    public RateLimitFilter(
            RateLimitStore store,
            RateLimitMetrics metrics,
            @Value("${game.rate-limit.enabled:true}") boolean enabled,
            @Value("${game.rate-limit.session-per-minute:30}") int sessionPerMinute,
            @Value("${game.rate-limit.logout-per-minute:20}") int logoutPerMinute,
            @Value("${game.rate-limit.admin-per-minute:30}") int adminPerMinute,
            @Value("${game.rate-limit.install-per-minute:15}") int installPerMinute,
            @Value("${game.rate-limit.narrate-per-minute:20}") int narratePerMinute,
            @Value("${game.rate-limit.action-per-minute:60}") int actionPerMinute,
            @Value("${game.rate-limit.metrics-per-minute:120}") int metricsPerMinute,
            @Value("${game.rate-limit.verify-per-minute:60}") int verifyPerMinute,
            @Value("${game.rate-limit.default-per-minute:120}") int defaultPerMinute) {
        this.store = store;
        this.metrics = metrics != null ? metrics : new RateLimitMetrics();
        this.enabled = enabled;
        this.sessionPerMinute = Math.max(1, sessionPerMinute);
        this.logoutPerMinute = Math.max(1, logoutPerMinute);
        this.adminPerMinute = Math.max(1, adminPerMinute);
        this.installPerMinute = Math.max(1, installPerMinute);
        this.narratePerMinute = Math.max(1, narratePerMinute);
        this.actionPerMinute = Math.max(1, actionPerMinute);
        this.metricsPerMinute = Math.max(1, metricsPerMinute);
        this.verifyPerMinute = Math.max(1, verifyPerMinute);
        // defaultPerMinute reserved for future catch-all paths
    }

    /**
     * Test helper: memory store + limits.
     * Argument order: session, metrics, verify, default (legacy) — other buckets
     * reuse the session cap for simple tests.
     */
    public RateLimitFilter(boolean enabled, int sessionPerMinute, int metricsPerMinute,
                           int verifyPerMinute, int defaultPerMinute) {
        this(new MemoryRateLimitStore(), new RateLimitMetrics(), enabled, sessionPerMinute, sessionPerMinute,
                sessionPerMinute, sessionPerMinute, sessionPerMinute, sessionPerMinute, metricsPerMinute,
                verifyPerMinute, defaultPerMinute);
    }

    /** Test helper with distinct logout budget. */
    public RateLimitFilter(boolean enabled, int sessionPerMinute, int logoutPerMinute,
                           int metricsPerMinute, int verifyPerMinute, int defaultPerMinute) {
        this(new MemoryRateLimitStore(), new RateLimitMetrics(), enabled, sessionPerMinute, logoutPerMinute,
                sessionPerMinute, sessionPerMinute, sessionPerMinute, sessionPerMinute, metricsPerMinute,
                verifyPerMinute, defaultPerMinute);
    }

    /** Test helper including admin budget. */
    public RateLimitFilter(boolean enabled, int sessionPerMinute, int logoutPerMinute,
                           int adminPerMinute, int metricsPerMinute, int verifyPerMinute,
                           int defaultPerMinute) {
        this(new MemoryRateLimitStore(), new RateLimitMetrics(), enabled, sessionPerMinute, logoutPerMinute,
                adminPerMinute, adminPerMinute, adminPerMinute, adminPerMinute, metricsPerMinute,
                verifyPerMinute, defaultPerMinute);
    }

    /** Test helper including install budget. */
    public RateLimitFilter(boolean enabled, int sessionPerMinute, int logoutPerMinute,
                           int adminPerMinute, int installPerMinute, int metricsPerMinute,
                           int verifyPerMinute, int defaultPerMinute) {
        this(new MemoryRateLimitStore(), new RateLimitMetrics(), enabled, sessionPerMinute, logoutPerMinute,
                adminPerMinute, installPerMinute, installPerMinute, installPerMinute, metricsPerMinute,
                verifyPerMinute, defaultPerMinute);
    }

    /** Full test helper including narrate budget (action reuses narrate). */
    public RateLimitFilter(boolean enabled, int sessionPerMinute, int logoutPerMinute,
                           int adminPerMinute, int installPerMinute, int narratePerMinute,
                           int metricsPerMinute, int verifyPerMinute, int defaultPerMinute) {
        this(new MemoryRateLimitStore(), new RateLimitMetrics(), enabled, sessionPerMinute, logoutPerMinute,
                adminPerMinute, installPerMinute, narratePerMinute, narratePerMinute, metricsPerMinute,
                verifyPerMinute, defaultPerMinute);
    }

    /** Full test helper including action budget. */
    public RateLimitFilter(boolean enabled, int sessionPerMinute, int logoutPerMinute,
                           int adminPerMinute, int installPerMinute, int narratePerMinute,
                           int actionPerMinute, int metricsPerMinute, int verifyPerMinute,
                           int defaultPerMinute) {
        this(new MemoryRateLimitStore(), new RateLimitMetrics(), enabled, sessionPerMinute, logoutPerMinute,
                adminPerMinute, installPerMinute, narratePerMinute, actionPerMinute, metricsPerMinute,
                verifyPerMinute, defaultPerMinute);
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
        RateLimitStore.Result hit = store.hit(key);
        long n = hit.count();
        if (n > limit.maxPerMinute) {
            metrics.rejected(limit.bucket);
            long retryAfterSec = hit.retryAfterSeconds();
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
        metrics.allowed(limit.bucket);
        res.setHeader("X-RateLimit-Limit", Integer.toString(limit.maxPerMinute));
        res.setHeader("X-RateLimit-Remaining",
                Long.toString(Math.max(0, limit.maxPerMinute - n)));
        chain.doFilter(req, res);
    }

    private Limit limitFor(HttpServletRequest req) {
        String path = req.getRequestURI();
        if (path == null) return null;
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String method = req.getMethod() == null ? "" : req.getMethod().toUpperCase(Locale.ROOT);
        if ("POST".equals(method) && path.equals("/v2/session")) {
            return new Limit("session", sessionPerMinute);
        }
        if ("DELETE".equals(method) && path.equals("/v2/session")) {
            return new Limit("logout", logoutPerMinute);
        }
        if (path.startsWith("/v2/admin")) {
            return new Limit("admin", adminPerMinute);
        }
        if ("POST".equals(method) && (MARKETPLACE_INSTALL.matcher(path).matches()
                || path.equals("/v2/catalog/packs"))) {
            return new Limit("install", installPerMinute);
        }
        if ("POST".equals(method) && path.equals("/v2/narrate")) {
            // Same bucket prefix as NarrationRateGuard (HTTP keys by IP).
            return new Limit("narrate", narratePerMinute);
        }
        if ("POST".equals(method) && (path.equals("/v2/action") || path.equals("/api/game/action"))) {
            return new Limit("action", actionPerMinute);
        }
        if ("GET".equals(method) && path.equals("/metrics")) {
            return new Limit("metrics", metricsPerMinute);
        }
        if ("POST".equals(method) && path.equals("/v2/entitlements/verify")) {
            return new Limit("verify", verifyPerMinute);
        }
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

    private static String safeRequestId(HttpServletRequest req) {
        String id = req.getHeader("X-Request-Id");
        if (id == null || id.isBlank()) return "";
        return id.replace("\"", "").replace("\\", "");
    }

    private record Limit(String bucket, int maxPerMinute) {}
}
