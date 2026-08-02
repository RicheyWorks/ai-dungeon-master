package com.xai.dungeonmaster.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 *   <li>{@code POST /v2/save|/load|/reset} — persistence / adventure restart</li>
 *   <li>{@code GET /metrics} — Prometheus scrapes (generous default)</li>
 *   <li>{@code POST /v2/entitlements/verify} — receipt verification</li>
 * </ul>
 *
 * Limits come from {@link RateLimitProperties}. Counters come from
 * {@link RateLimitStore} — process-local memory or shared Redis
 * ({@code game.rate-limit.store=redis}). Returns 429 + {@code Retry-After}
 * when a bucket is exhausted.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern MARKETPLACE_INSTALL =
            Pattern.compile("^/v2/marketplace/[^/]+/install$");

    private final RateLimitProperties props;
    private final RateLimitStore store;
    private final RateLimitMetrics metrics;

    public RateLimitFilter(RateLimitStore store, RateLimitMetrics metrics, RateLimitProperties props) {
        this.store = store;
        this.metrics = metrics != null ? metrics : new RateLimitMetrics();
        this.props = props != null ? props : RateLimitProperties.builder().build();
    }

    /** Memory-store test helper. */
    public RateLimitFilter(RateLimitProperties props) {
        this(new MemoryRateLimitStore(), new RateLimitMetrics(), props);
    }

    /** Memory-store test helper with shared metrics. */
    public RateLimitFilter(RateLimitProperties props, RateLimitMetrics metrics) {
        this(new MemoryRateLimitStore(), metrics, props);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.enabled()) return true;
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
        String ip = clientIp(req, props.trustForwardedHeaders());
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
            return new Limit("session", props.sessionPerMinute());
        }
        if ("DELETE".equals(method) && path.equals("/v2/session")) {
            return new Limit("logout", props.logoutPerMinute());
        }
        if (path.startsWith("/v2/admin")) {
            return new Limit("admin", props.adminPerMinute());
        }
        if ("POST".equals(method) && (MARKETPLACE_INSTALL.matcher(path).matches()
                || path.equals("/v2/catalog/packs"))) {
            return new Limit("install", props.installPerMinute());
        }
        if ("POST".equals(method) && path.equals("/v2/narrate")) {
            return new Limit("narrate", props.narratePerMinute());
        }
        if ("POST".equals(method) && (path.equals("/v2/action") || path.equals("/api/game/action"))) {
            return new Limit("action", props.actionPerMinute());
        }
        if ("POST".equals(method) && (
                path.equals("/v2/save") || path.equals("/v2/load") || path.equals("/v2/reset")
                || path.equals("/api/game/save") || path.equals("/api/game/load"))) {
            return new Limit("save", props.savePerMinute());
        }
        if ("GET".equals(method) && path.equals("/metrics")) {
            return new Limit("metrics", props.metricsPerMinute());
        }
        if ("POST".equals(method) && path.equals("/v2/entitlements/verify")) {
            return new Limit("verify", props.verifyPerMinute());
        }
        return null;
    }

    static String clientIp(HttpServletRequest req) {
        return clientIp(req, true);
    }

    static String clientIp(HttpServletRequest req, boolean trustForwarded) {
        if (trustForwarded) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                int comma = xff.indexOf(',');
                String first = (comma < 0 ? xff : xff.substring(0, comma)).trim();
                if (!first.isEmpty()) return first;
            }
            String real = req.getHeader("X-Real-IP");
            if (real != null && !real.isBlank()) return real.trim();
        }
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
