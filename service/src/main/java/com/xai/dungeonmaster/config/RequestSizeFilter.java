package com.xai.dungeonmaster.config;

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
import java.util.Set;

/**
 * Early reject of oversized non-multipart requests via {@code Content-Length}.
 * Multipart pack uploads keep their own Spring multipart limits.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestSizeFilter extends OncePerRequestFilter {

    private static final Set<String> SKIP_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final boolean enabled;
    private final long maxBytes;

    public RequestSizeFilter(
            @Value("${game.http.max-request-bytes:1048576}") long maxBytes,
            @Value("${game.http.max-request-enabled:true}") boolean enabled) {
        this.maxBytes = Math.max(1024L, maxBytes);
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) return true;
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        if (SKIP_METHODS.contains(method)) return true;
        String ct = request.getContentType();
        if (ct != null && ct.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
            return true; // handled by spring.servlet.multipart.*
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long len = req.getContentLengthLong();
        if (len > maxBytes) {
            res.setStatus(413);
            res.setContentType("application/json");
            String requestId = safeRequestId(req);
            res.getWriter().write("{\"type\":\"error\",\"version\":1,\"payload\":{\"message\":"
                    + "\"Request body too large (max " + maxBytes + " bytes).\"},"
                    + "\"requestId\":\"" + requestId + "\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    private static String safeRequestId(HttpServletRequest req) {
        String id = req.getHeader("X-Request-Id");
        if (id == null || id.isBlank()) return "";
        return id.replace("\"", "").replace("\\", "");
    }
}
