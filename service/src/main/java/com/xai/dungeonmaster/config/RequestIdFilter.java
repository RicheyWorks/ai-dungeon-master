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

/**
 * Ensures every request has a correlation id: echoes {@code X-Request-Id} when
 * present, otherwise generates a UUID. Exposes it as a request attribute and
 * response header for filters and clients.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String ATTR = "dm.requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String id = req.getHeader(HEADER);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        } else {
            id = id.trim();
            if (id.length() > 128) {
                id = id.substring(0, 128);
            }
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
        return h == null ? "" : h.trim();
    }
}
