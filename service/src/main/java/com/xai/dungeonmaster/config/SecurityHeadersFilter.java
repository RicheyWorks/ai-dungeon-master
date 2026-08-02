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

/**
 * Baseline browser security headers for API + static SPA responses.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final boolean hstsEnabled;
    private final long hstsMaxAgeSeconds;
    private final String frameOptions;
    private final String referrerPolicy;

    public SecurityHeadersFilter(
            @Value("${game.security.headers.enabled:true}") boolean enabled,
            @Value("${game.security.hsts.enabled:false}") boolean hstsEnabled,
            @Value("${game.security.hsts.max-age-seconds:31536000}") long hstsMaxAgeSeconds,
            @Value("${game.security.frame-options:DENY}") String frameOptions,
            @Value("${game.security.referrer-policy:no-referrer}") String referrerPolicy) {
        this.enabled = enabled;
        this.hstsEnabled = hstsEnabled;
        this.hstsMaxAgeSeconds = Math.max(0L, hstsMaxAgeSeconds);
        this.frameOptions = (frameOptions == null || frameOptions.isBlank()) ? "DENY" : frameOptions.trim();
        this.referrerPolicy = (referrerPolicy == null || referrerPolicy.isBlank())
                ? "no-referrer" : referrerPolicy.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", frameOptions);
        res.setHeader("Referrer-Policy", referrerPolicy);
        res.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        res.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        // API is JSON; keep CSP minimal so the SPA can still load its own assets.
        if (!res.containsHeader("Content-Security-Policy")) {
            res.setHeader("Content-Security-Policy",
                    "default-src 'self'; img-src 'self' data: blob:; "
                            + "style-src 'self' 'unsafe-inline'; script-src 'self'; "
                            + "connect-src 'self' ws: wss:; frame-ancestors 'none'");
        }
        if (hstsEnabled && isSecure(req)) {
            res.setHeader("Strict-Transport-Security",
                    "max-age=" + hstsMaxAgeSeconds + "; includeSubDomains");
        }
        chain.doFilter(req, res);
    }

    private static boolean isSecure(HttpServletRequest req) {
        if (req.isSecure()) return true;
        String proto = req.getHeader("X-Forwarded-Proto");
        return proto != null && "https".equalsIgnoreCase(proto.trim());
    }
}
