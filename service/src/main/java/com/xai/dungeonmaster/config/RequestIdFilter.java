package com.xai.dungeonmaster.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ensures every request has a correlation id: echoes {@code X-Request-Id} when
 * present and safe, otherwise generates a UUID. Rejects control characters to
 * prevent log injection via request ids.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String ATTR = "dm.requestId";

    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._\\-]{1,128}$");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String id = req.getHeader(HEADER);
        if (id == null || id.isBlank() || !SAFE.matcher(id.trim()).matches()) {
            id = UUID.randomUUID().toString();
        } else {
            id = id.trim();
        }
        req.setAttribute(ATTR, id);
        res.setHeader(HEADER, id);
        chain.doFilter(req, res);
    }

    public static String resolve(HttpServletRequest req) {
        if (req == null) return "";
        Object attr = req.getAttribute(ATTR);
        if (attr instanceof String s && !s.isBlank()) return s;
        String h = req.getHeader(HEADER);
        if (h == null || h.isBlank() || !SAFE.matcher(h.trim()).matches()) return "";
        return h.trim();
    }
}
